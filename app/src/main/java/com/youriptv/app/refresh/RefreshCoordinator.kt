package com.youriptv.app.refresh

import android.content.Context
import android.net.ConnectivityManager
import android.net.NetworkCapabilities
import android.os.SystemClock
import com.youriptv.app.data.Playlist
import com.youriptv.app.data.Repository
import com.youriptv.app.data.Settings
import com.youriptv.app.data.StaleRefreshData
import java.io.File
import java.util.concurrent.atomic.AtomicBoolean
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Deferred
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.currentCoroutineContext
import kotlinx.coroutines.delay
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.collect
import kotlinx.coroutines.withTimeout

/** Sole refresh owner for customer controls, legacy actions, foreground startup and OS jobs. */
class RefreshCoordinator(context: Context, private val repo: Repository, private val settings: Settings) {
    private val context = context.applicationContext
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)
    private val foregroundStarted = AtomicBoolean(false)
    private val recoveryLock = Any()
    private var recovered = false
    private val scheduling = Any()
    private val downloads = SharedEpgFiles(scope, File(this.context.cacheDir, "epg-refresh"))
    private val mutableGenerations = MutableStateFlow<Map<Long, RefreshGeneration>>(emptyMap())
    val generations: StateFlow<Map<Long, RefreshGeneration>> = mutableGenerations.asStateFlow()
    private val queue = RefreshQueue(scope, ::execute, { reconcileSchedule() })
    val states: StateFlow<Map<Long, RefreshState>> = queue.states

    fun requestPlaylist(id: Long, origin: RefreshOrigin = RefreshOrigin.MANUAL): Deferred<RefreshResult> =
        queue.request(id, catalog = true, epg = true, origin).also { reconcileSchedule() }

    fun requestEpg(id: Long, origin: RefreshOrigin = RefreshOrigin.MANUAL): Deferred<RefreshResult> =
        queue.request(id, catalog = false, epg = true, origin).also { reconcileSchedule() }

    /** Called after cached UI setup. A worker-only Application.onCreate never calls this. */
    fun onFirstForegroundLaunch() {
        if (!foregroundStarted.compareAndSet(false, true)) return
        scope.launch {
            try {
                recover()
                val now = System.currentTimeMillis()
                val playlists = repo.playlists()
                publishGenerations(playlists)
                for (p in playlists) {
                    if (catalogEligible(p, now)) requestPlaylist(p.id, RefreshOrigin.STARTUP)
                    else if (repo.epgSource(p).isNotBlank()) requestEpg(p.id, RefreshOrigin.STARTUP)
                }
                reconcileSchedule()
            } catch (_: Exception) { android.util.Log.w("YourIPTVRefresh", "Startup refresh could not be scheduled") }
        }
    }

    fun reconcileSchedule() {
        scope.launch {
            try {
                synchronized(scheduling) {
                    recover()
                    val playlists = repo.playlists()
                    publishGenerations(playlists)
                    val configuredEpg = playlists.filter { repo.epgSource(it).isNotBlank() }.map { it.id }.toSet()
                    RefreshJobScheduler.reconcile(context, playlists, queue.busyIds(), configuredEpg)
                }
            } catch (_: Exception) { android.util.Log.w("YourIPTVRefresh", "Background refresh could not be scheduled") }
        }
    }

    /** An Activity's progress collector is cancelled with its caller; the shared refresh is retained. */
    suspend fun awaitRequest(id: Long, request: Deferred<RefreshResult>, progress: (String) -> Unit): RefreshResult = coroutineScope {
        val observer = launch {
            states.collect { values -> values[id]?.let { progress(it.message) } }
        }
        try { request.await() } finally { observer.cancel() }
    }

    /** The OS job only requests due catalog work and pending guide recovery, through the same queue. */
    internal suspend fun runScheduled() {
        recover()
        val now = System.currentTimeMillis()
        val requests = repo.playlists().mapNotNull { p ->
            when {
                catalogEligible(p, now) -> requestPlaylist(p.id, RefreshOrigin.BACKGROUND)
                repo.epgSource(p).isNotBlank() && RefreshPolicy.retryAt(p.epgLastAttempt, p.epgLastUpdated, now, p.epgLastError.isNotBlank())?.let { it <= now } == true ->
                    requestEpg(p.id, RefreshOrigin.BACKGROUND)
                else -> null
            }
        }
        try {
            // Leave time for a blocked network read to observe cooperative cancellation before the OS execution limit.
            withTimeout(RefreshPolicy.OPERATION_BUDGET_MS) { requests.forEach { it.await() } }
        } finally { queue.cancelBackground() }
    }

    internal fun stopBackground() = queue.cancelBackground()

    private fun recover() = synchronized(recoveryLock) {
        if (!recovered) { downloads.removeAbandoned(); repo.recoverInterruptedRefreshes(); recovered = true }
    }

    private fun catalogEligible(p: Playlist, now: Long): Boolean =
        RefreshPolicy.eligibleAt(p.lastUpdated, p.refreshHours, p.lastRefreshAttempt, p.lastRefreshError.isNotBlank(), now)?.let { it <= now } == true

    private suspend fun execute(id: Long, work: RefreshWork, owners: Set<RefreshOrigin>, catalogAlso: Boolean, cancelled: () -> Boolean): RefreshStep {
        recover()
        val coroutine = currentCoroutineContext()
        val stopped = { cancelled() || !coroutine.isActive }
        val initial = repo.playlist(id) ?: return RefreshStep(false, message = "This playlist is no longer available.")
        val identity = repo.sourceIdentity(initial)
        val epg = work == RefreshWork.EPG
        if (epg && repo.epgSource(initial).isBlank()) return RefreshStep(catalogAlso, message = "No EPG source is configured for this playlist.")
        if (epg && owners == setOf(RefreshOrigin.STARTUP)) {
            while (!queue.manuallyRequested(id)) {
                val wait = RefreshPolicy.startupDelay(initial.epgLastAttempt, System.currentTimeMillis())
                if (wait <= 0) break
                delay(minOf(wait, 250))
            }
        }
        if (stopped()) throw CancellationException("Refresh stopped")
        repo.beginRefreshAttempt(id, epg, expectedIdentity = identity)
        // Schedule a recovery wake-up for this boot before entering potentially slow provider I/O.
        reconcileSchedule()
        return try {
            if (!networkAvailable()) throw RefreshNetworkUnavailable()
            val progress: (String) -> Unit = { queue.progress(id, work, safeProgress(work, it)) }
            if (epg) {
                val sourceKey = repo.sourceKey(initial)
                downloads.withFile(sourceKey, producer = { file, abandoned ->
                    val sourceDeadline = SystemClock.elapsedRealtime() + RefreshPolicy.OPERATION_BUDGET_MS
                    repo.stageEpg(initial, file) { abandoned() || SystemClock.elapsedRealtime() >= sourceDeadline }
                }) { file ->
                    if (stopped()) throw CancellationException("Refresh stopped")
                    val current = repo.playlist(id) ?: throw IllegalStateException("Playlist removed")
                    check(repo.sourceIdentity(current) == identity && repo.sourceKey(current) == sourceKey) { "Playlist source changed" }
                    val window = settings.epgWindow()
                    repo.refreshEpgFromFile(initial, window.first, window.second, file, progress, stopped, expectedWindow = window)
                }
            } else repo.refresh(initial, progress, stopped)
            publishGenerations(repo.playlists())
            RefreshStep(true, changed = true)
        } catch (cancel: CancellationException) {
            recordFailure(id, epg, identity, "Update interrupted. Cached data remains available.")
            throw cancel
        } catch (error: Exception) {
            android.util.Log.w("YourIPTVRefresh", "${work.name} refresh failed (${error.javaClass.simpleName})")
            val message = when (error) {
                is StaleRefreshData -> "Configuration changed during the update. Cached data remains available; the update will retry."
                is RefreshNetworkUnavailable -> "Network unavailable. Cached data remains available; the update will retry."
                is java.net.SocketTimeoutException -> "The provider timed out. Cached data remains available; the update will retry."
                else -> if (epg) "EPG update failed. Cached guide remains available; the update will retry."
                    else "Playlist update failed. Cached channels remain available; the update will retry."
            }
            recordFailure(id, epg, identity, message)
            RefreshStep(false, message = message)
        }
    }

    private fun recordFailure(id: Long, epg: Boolean, identity: String, message: String) {
        repo.failRefreshAttempt(id, epg, message, identity)
    }

    private fun publishGenerations(playlists: List<Playlist>) {
        synchronized(mutableGenerations) {
            val previous = mutableGenerations.value
            mutableGenerations.value = playlists.associate { p ->
                val old = previous[p.id]
                p.id to RefreshGeneration(maxOf(p.catalogGeneration, old?.catalog ?: 0), maxOf(p.epgGeneration, old?.epg ?: 0))
            }
        }
    }

    private fun safeProgress(work: RefreshWork, providerProgress: String): String {
        // Only fixed public strings are exposed; never forward provider URLs or exception text to UI/logs.
        val count = Regex("(?:Imported|Loaded) ([0-9]{1,9})").find(providerProgress)?.groupValues?.get(1)
        return if (count != null) "Processing $count ${if (work == RefreshWork.EPG) "programs" else "entries"}…"
            else if (work == RefreshWork.EPG) "Downloading and processing EPG…" else "Downloading and processing playlist…"
    }

    private fun networkAvailable(): Boolean {
        val manager = context.getSystemService(Context.CONNECTIVITY_SERVICE) as? ConnectivityManager ?: return false
        val network = manager.activeNetwork ?: return false
        return manager.getNetworkCapabilities(network)?.hasCapability(NetworkCapabilities.NET_CAPABILITY_INTERNET) == true
    }

    private class RefreshNetworkUnavailable : Exception()
}
