package com.youriptv.app.data

/** Storage units stay hours for history and days for future coverage, including portable backups. */
object EpgRetentionPolicy {
    // Hold only while changing preferences or committing an already prepared SQLite transaction.
    internal val preferenceLock = Any()
    const val DEFAULT_PAST_HOURS = 7 * 24
    const val DEFAULT_FUTURE_DAYS = 7
    const val MAX_PAST_HOURS = 14 * 24
    const val MAX_FUTURE_DAYS = 14

    fun migratedPastHours(saved: Int) = saved.coerceIn(DEFAULT_PAST_HOURS, MAX_PAST_HOURS)
    fun migratedFutureDays(saved: Int) = saved.coerceIn(DEFAULT_FUTURE_DAYS, MAX_FUTURE_DAYS)
}
