package com.youriptv.app.refresh

import android.app.job.JobInfo
import android.app.job.JobScheduler
import android.content.ComponentName
import android.content.Context
import android.os.PersistableBundle
import com.youriptv.app.data.Playlist

/**
 * One network-constrained wake-up for the current boot, without adding installation permissions.
 * After reboot, the first foreground launch rebuilds it from the unchanged committed due dates.
 */
object RefreshJobScheduler {
    internal const val JOB_ID = 0x595047
    private const val DUE_KEY = "refresh_due_at"
    private val lock = Any()
    private var running = false

    internal fun started() = synchronized(lock) { running = true }
    internal fun stopped() = synchronized(lock) { running = false }

    internal fun reconcile(context: Context, playlists: List<Playlist>, busy: Set<Long>, configuredEpg: Set<Long>) = synchronized(lock) {
        if (running) return@synchronized
        val scheduler = context.getSystemService(Context.JOB_SCHEDULER_SERVICE) as? JobScheduler ?: return@synchronized
        val now = System.currentTimeMillis()
        val next = nextRunAt(playlists, busy, configuredEpg, now)
        if (next == null) {
            scheduler.cancel(JOB_ID)
            return@synchronized
        }
        val target = maxOf(now + 1_000, next)
        val pending = scheduler.allPendingJobs.firstOrNull { it.id == JOB_ID }
        if (pending != null && !pending.isPersisted && kotlin.math.abs(pending.extras.getLong(DUE_KEY) - target) < 2_000) return@synchronized
        val job = JobInfo.Builder(JOB_ID, ComponentName(context, RefreshJobService::class.java))
            .setRequiredNetworkType(JobInfo.NETWORK_TYPE_ANY)
            .setMinimumLatency((target - now).coerceAtLeast(1_000))
            .setBackoffCriteria(RefreshPolicy.RETRY_MS, JobInfo.BACKOFF_POLICY_EXPONENTIAL)
            .setPersisted(false)
            .setExtras(PersistableBundle().apply { putLong(DUE_KEY, target) })
            .build()
        if (scheduler.schedule(job) != JobScheduler.RESULT_SUCCESS) {
            android.util.Log.w("YourIPTVRefresh", "Android could not schedule the refresh job")
        }
    }

    internal fun nextRunAt(playlists: List<Playlist>, busy: Set<Long>, configuredEpg: Set<Long>, now: Long): Long? = playlists.mapNotNull { p ->
        if (p.id in busy) return@mapNotNull now + RefreshPolicy.RETRY_MS
        val catalog = RefreshPolicy.eligibleAt(p.lastUpdated, p.refreshHours, p.lastRefreshAttempt, p.lastRefreshError.isNotBlank(), now)
        val epg = if (p.id in configuredEpg) RefreshPolicy.retryAt(p.epgLastAttempt, p.epgLastUpdated, now, p.epgLastError.isNotBlank()) else null
        listOfNotNull(catalog, epg).minOrNull()
    }.minOrNull()
}
