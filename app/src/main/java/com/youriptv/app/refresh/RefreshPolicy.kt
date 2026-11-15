package com.youriptv.app.refresh

/** Clock/due-time policy kept independent of Android scheduling and network operations. */
object RefreshPolicy {
    const val RETRY_MS = 30 * 60_000L
    const val STARTUP_COOLDOWN_MS = 60_000L
    const val OPERATION_BUDGET_MS = 6 * 60_000L

    fun catalogDueAt(lastSuccess: Long, hours: Int, now: Long): Long? {
        if (hours <= 0) return null
        // A corrected device clock must not defer refresh until an obsolete future timestamp catches up.
        if (lastSuccess <= 0 || lastSuccess > now) return now
        return lastSuccess + hours.toLong().coerceAtMost(168) * 3_600_000L
    }

    fun retryAt(lastAttempt: Long, lastSuccess: Long, now: Long, failed: Boolean = false): Long? {
        if (lastAttempt <= 0 || (!failed && lastAttempt <= lastSuccess)) return null
        return if (lastAttempt > now) now else lastAttempt + RETRY_MS
    }

    fun eligibleAt(lastSuccess: Long, hours: Int, lastAttempt: Long, failed: Boolean, now: Long): Long? {
        val due = catalogDueAt(lastSuccess, hours, now) ?: return null
        val retry = if (failed) retryAt(lastAttempt, lastSuccess, now, failed = true) else null
        return if (retry != null) maxOf(due, retry) else due
    }

    fun startupDelay(lastAttempt: Long, now: Long): Long =
        if (lastAttempt <= 0 || lastAttempt > now) 0 else (lastAttempt + STARTUP_COOLDOWN_MS - now).coerceAtLeast(0)
}
