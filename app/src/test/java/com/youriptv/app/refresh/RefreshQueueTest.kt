package com.youriptv.app.refresh

import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.CoroutineStart
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.cancelAndJoin
import kotlinx.coroutines.launch
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.withTimeout
import org.junit.Assert.*
import org.junit.Test

class RefreshQueueTest {
    @Test fun duplicateRequestsJoinOneOperationAndCallerCancellationDoesNotOwnIt() = runBlocking {
        val scope = CoroutineScope(SupervisorJob() + Dispatchers.Unconfined)
        try {
            val release = CompletableDeferred<Unit>()
            var calls = 0
            val queue = RefreshQueue(scope, { _, _, _, _, _ -> calls++; release.await(); RefreshStep(true, true) })
            val original = queue.request(7, false, true, RefreshOrigin.STARTUP)
            val observer = launch(start = CoroutineStart.UNDISPATCHED) { original.await() }
            observer.cancelAndJoin()
            val manual = queue.request(7, false, true, RefreshOrigin.MANUAL)
            assertSame(original, manual)
            assertEquals(1, calls)
            assertTrue(queue.states.value[7]?.busy == true)
            release.complete(Unit)
            assertTrue(withTimeout(2_000) { manual.await() }.epgUpdated)
            assertFalse(queue.states.value[7]!!.busy)
        } finally { scope.cancel() }
    }

    @Test fun catalogRequestDuringGuideImportRunsCatalogThenRemapsGuide() = runBlocking {
        val scope = CoroutineScope(SupervisorJob() + Dispatchers.Unconfined)
        try {
            val release = CompletableDeferred<Unit>()
            val sequence = mutableListOf<RefreshWork>()
            val queue = RefreshQueue(scope, { _, kind, _, _, _ ->
                sequence += kind
                if (sequence.size == 1) release.await()
                RefreshStep(true, true)
            })
            val guide = queue.request(3, false, true, RefreshOrigin.STARTUP)
            val playlist = queue.request(3, true, true, RefreshOrigin.MANUAL)
            assertSame(guide, playlist)
            release.complete(Unit)
            val result = withTimeout(2_000) { playlist.await() }
            assertEquals(listOf(RefreshWork.EPG, RefreshWork.CATALOG, RefreshWork.EPG), sequence)
            assertTrue(result.catalogUpdated && result.epgUpdated && result.success)
        } finally { scope.cancel() }
    }

    @Test fun stoppingOsJobPreservesAnOperationJoinedByCustomer() = runBlocking {
        val scope = CoroutineScope(SupervisorJob() + Dispatchers.Unconfined)
        try {
            val release = CompletableDeferred<Unit>()
            val queue = RefreshQueue(scope, { _, _, _, _, stopped ->
                release.await(); assertFalse(stopped()); RefreshStep(true, true)
            })
            val background = queue.request(9, false, true, RefreshOrigin.BACKGROUND)
            queue.request(9, false, true, RefreshOrigin.MANUAL)
            queue.cancelBackground()
            release.complete(Unit)
            assertTrue(withTimeout(2_000) { background.await() }.success)
        } finally { scope.cancel() }
    }

    @Test fun stoppingBackgroundOnlyWorkCompletesObserversWithInterruptedState() = runBlocking {
        val scope = CoroutineScope(SupervisorJob() + Dispatchers.Unconfined)
        try {
            val blocked = CompletableDeferred<Unit>()
            val queue = RefreshQueue(scope, { _, _, _, _, _ -> blocked.await(); RefreshStep(true) })
            val request = queue.request(9, false, true, RefreshOrigin.BACKGROUND)
            queue.cancelBackground()
            val result = withTimeout(2_000) { request.await() }
            assertFalse(result.success)
            assertEquals(RefreshPhase.FAILED, queue.states.value[9]!!.phase)
            assertTrue(queue.busyIds().isEmpty())
        } finally { scope.cancel() }
    }

    @Test fun catalogSuccessAndGuideFailureRetainSeparateCommitResults() = runBlocking {
        val scope = CoroutineScope(SupervisorJob() + Dispatchers.Unconfined)
        try {
            val queue = RefreshQueue(scope, { _, work, _, _, _ ->
                if (work == RefreshWork.CATALOG) RefreshStep(true, true)
                else RefreshStep(false, message = "EPG update failed. Cached guide remains available.")
            })
            val result = queue.request(1, true, true, RefreshOrigin.MANUAL).await()
            assertFalse(result.success)
            assertTrue(result.catalogUpdated)
            assertFalse(result.epgUpdated)
            assertTrue(result.message.contains("Cached guide"))
        } finally { scope.cancel() }
    }

    @Test fun executionBudgetStopsBlockedWorkAndReportsAlreadyCommittedCatalog() = runBlocking {
        val scope = CoroutineScope(SupervisorJob() + Dispatchers.Unconfined)
        try {
            val blocked = CompletableDeferred<Unit>()
            val queue = RefreshQueue(scope, { _, work, _, _, _ ->
                if (work == RefreshWork.EPG) blocked.await()
                RefreshStep(true, true)
            }, budgetMs = 30)
            val result = withTimeout(2_000) { queue.request(4, true, true, RefreshOrigin.BACKGROUND).await() }
            assertFalse(result.success)
            assertTrue(result.catalogUpdated)
            assertFalse(result.epgUpdated)
            assertTrue(queue.busyIds().isEmpty())
        } finally { scope.cancel() }
    }
}
