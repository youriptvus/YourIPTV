package com.youriptv.app.refresh

import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Deferred
import kotlinx.coroutines.Job
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.launch
import kotlinx.coroutines.withTimeout
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

enum class RefreshPhase { IDLE, QUEUED, CATALOG, EPG, COMPLETED, FAILED }
enum class RefreshOrigin { MANUAL, STARTUP, BACKGROUND }

data class RefreshResult(
    val playlistId: Long,
    val success: Boolean,
    val message: String,
    val catalogUpdated: Boolean = false,
    val epgUpdated: Boolean = false,
)

data class RefreshState(
    val phase: RefreshPhase = RefreshPhase.IDLE,
    val message: String = "Idle",
    val lastResult: RefreshResult? = null,
) {
    val busy: Boolean get() = phase == RefreshPhase.QUEUED || phase == RefreshPhase.CATALOG || phase == RefreshPhase.EPG
}

data class RefreshGeneration(val catalog: Long = 0, val epg: Long = 0)
internal enum class RefreshWork { CATALOG, EPG }
internal data class RefreshStep(val success: Boolean, val changed: Boolean = false, val message: String = "")

/** One operation per playlist. Awaiters never own the operation's coroutine lifetime. */
internal class RefreshQueue(
    private val scope: CoroutineScope,
    private val execute: suspend (Long, RefreshWork, Set<RefreshOrigin>, Boolean, () -> Boolean) -> RefreshStep,
    private val finished: (Long) -> Unit = {},
    private val budgetMs: Long = RefreshPolicy.OPERATION_BUDGET_MS,
) {
    private class Operation(val id: Long, val deadlineNanos: Long) {
        val result = CompletableDeferred<RefreshResult>()
        val owners = mutableSetOf<RefreshOrigin>()
        var catalogWanted = false
        var catalogDone = false
        var epgWanted = false
        var epgDone = false
        @Volatile var cancelled = false
        var job: Job? = null
        val outcomes = mutableMapOf<RefreshWork, RefreshStep>()
    }

    private val lock = Any()
    private val operations = mutableMapOf<Long, Operation>()
    private val mutableStates = MutableStateFlow<Map<Long, RefreshState>>(emptyMap())
    val states: StateFlow<Map<Long, RefreshState>> = mutableStates.asStateFlow()

    fun request(id: Long, catalog: Boolean, epg: Boolean, origin: RefreshOrigin): Deferred<RefreshResult> = synchronized(lock) {
        val existing = operations[id]
        if (existing != null) {
            existing.owners.add(origin)
            existing.catalogWanted = existing.catalogWanted || catalog
            existing.epgWanted = existing.epgWanted || epg
            return@synchronized existing.result
        }
        val op = Operation(id, System.nanoTime() + budgetMs * 1_000_000L).also {
            it.owners.add(origin)
            it.catalogWanted = catalog
            it.epgWanted = epg
        }
        operations[id] = op
        setState(id, RefreshState(RefreshPhase.QUEUED, "Update queued"))
        op.job = scope.launch { run(op) }
        op.result
    }

    fun progress(id: Long, work: RefreshWork, message: String) = synchronized(lock) {
        if (operations[id]?.cancelled != false) return@synchronized
        setState(id, RefreshState(if (work == RefreshWork.CATALOG) RefreshPhase.CATALOG else RefreshPhase.EPG, message))
    }

    /** A stopped OS job cannot cancel a refresh also requested by an open app/customer. */
    fun cancelBackground() = synchronized(lock) {
        operations.values.filter { it.owners == setOf(RefreshOrigin.BACKGROUND) }.forEach {
            it.cancelled = true
            it.job?.cancel()
        }
    }

    fun busyIds(): Set<Long> = synchronized(lock) { operations.keys.toSet() }
    fun manuallyRequested(id: Long): Boolean = synchronized(lock) { operations[id]?.owners?.contains(RefreshOrigin.MANUAL) == true }

    private suspend fun run(op: Operation) {
        try {
            var completed = false
            while (!completed) {
                val work = synchronized(lock) {
                    when {
                        op.cancelled -> throw CancellationException("Background refresh stopped")
                        op.catalogWanted && !op.catalogDone -> RefreshWork.CATALOG
                        op.epgWanted && !op.epgDone -> RefreshWork.EPG
                        else -> null
                    }
                }
                if (work == null) {
                    synchronized(lock) {
                        if ((!op.catalogWanted || op.catalogDone) && (!op.epgWanted || op.epgDone)) {
                            val failures = op.outcomes.values.filter { !it.success }
                            val success = failures.isEmpty()
                            val notices = op.outcomes.values.map { it.message }.filter { it.isNotBlank() }
                            complete(op, RefreshResult(
                                op.id, success,
                                if (success) notices.joinToString(" ").ifBlank { "Update completed" }
                                else failures.joinToString(" ") { it.message }.ifBlank { "Update failed. Cached data is unchanged." },
                                op.outcomes[RefreshWork.CATALOG]?.changed == true,
                                op.outcomes[RefreshWork.EPG]?.changed == true,
                            ))
                            completed = true
                        }
                    }
                    continue
                }
                progress(op.id, work, if (work == RefreshWork.CATALOG) "Updating playlist…" else "Updating EPG…")
                val owners = synchronized(lock) { op.owners.toSet() }
                val catalogAlso = synchronized(lock) { op.catalogWanted }
                val remaining = ((op.deadlineNanos - System.nanoTime()) / 1_000_000L).coerceAtLeast(1)
                val outcome = withTimeout(remaining) {
                    execute(op.id, work, owners, catalogAlso) {
                        op.cancelled || op.job?.isActive == false || System.nanoTime() >= op.deadlineNanos
                    }
                }
                synchronized(lock) {
                    op.outcomes[work] = outcome
                    if (work == RefreshWork.CATALOG) {
                        op.catalogDone = true
                        // A catalog request arriving during an EPG import needs another mapping pass afterwards.
                        op.epgDone = false
                    } else op.epgDone = true
                }
            }
        } catch (_: CancellationException) {
            synchronized(lock) { complete(op, interrupted(op, "Update interrupted. Cached data remains available.")) }
        } catch (_: Exception) {
            synchronized(lock) { complete(op, interrupted(op, "Update failed. Cached data remains available.")) }
        } finally {
            finished(op.id)
        }
    }

    private fun complete(op: Operation, result: RefreshResult) {
        if (operations[op.id] === op) {
            operations.remove(op.id)
            setState(op.id, RefreshState(if (result.success) RefreshPhase.COMPLETED else RefreshPhase.FAILED, result.message, result))
        }
        op.result.complete(result)
    }

    private fun interrupted(op: Operation, message: String) = RefreshResult(op.id, false, message,
        op.outcomes[RefreshWork.CATALOG]?.changed == true, op.outcomes[RefreshWork.EPG]?.changed == true)

    private fun setState(id: Long, state: RefreshState) { mutableStates.value = mutableStates.value + (id to state) }
}
