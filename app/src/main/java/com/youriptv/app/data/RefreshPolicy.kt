package com.youriptv.app.data

import java.security.MessageDigest
import java.util.concurrent.CancellationException

/** Pure refresh decisions shared by migration, scheduling and fixture tests. */
object RefreshPolicy {
    const val DEFAULT_HOURS = 16
    fun migratedHours(saved: Int?): Int = saved?.takeIf { it in 0..168 } ?: DEFAULT_HOURS

    fun dueAt(success: Long, hours: Int, now: Long): Long? {
        if (hours <= 0) return null
        val interval = hours.coerceAtMost(168) * 3_600_000L
        // A clock rollback must not defer an update for an arbitrarily distant future date.
        return if (success <= 0L || success > now) now else if (success > Long.MAX_VALUE - interval) Long.MAX_VALUE else success + interval
    }

    fun fingerprint(vararg parts: String): String {
        val digest = MessageDigest.getInstance("SHA-256")
        for (part in parts) {
            val bytes = part.toByteArray(Charsets.UTF_8)
            digest.update(byteArrayOf((bytes.size ushr 24).toByte(), (bytes.size ushr 16).toByte(), (bytes.size ushr 8).toByte(), bytes.size.toByte()))
            digest.update(bytes)
        }
        return digest.digest().joinToString("") { "%02x".format(it) }
    }

    fun checkCancelled(isCancelled: () -> Boolean) {
        if (isCancelled() || Thread.currentThread().isInterrupted) throw CancellationException("Update cancelled")
    }
}

class InvalidRefreshData(message: String) : IllegalStateException(message)
class StaleRefreshData : IllegalStateException("The playlist changed during the update. Try again.")
