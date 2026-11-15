package com.youriptv.app.ui

import com.youriptv.app.data.Playlist

/** A public refresh view contains no source URL, credentials or mutation capability. */
internal data class CustomerRefreshPresentation(
    val playlistName: String,
    val interval: String,
    val playlistSuccess: Long,
    val playlistAttempt: Long,
    val epgSuccess: Long,
    val epgAttempt: Long,
    val playlistError: String,
    val epgError: String,
    val status: String,
    val busy: Boolean,
    val canRequest: Boolean,
) {
    companion object {
        fun from(playlist: Playlist?, busy: Boolean, message: String? = null, lastResultSuccess: Boolean? = null): CustomerRefreshPresentation {
            val exists = playlist != null
            val playlistError = safeMessage(playlist?.lastRefreshError.orEmpty())
            val epgError = safeMessage(playlist?.epgLastError.orEmpty())
            val description = safeMessage(message.orEmpty())
            val phase = when {
                !exists -> "No active playlist"
                busy -> "Updating"
                lastResultSuccess == false || (lastResultSuccess == null && (playlistError.isNotBlank() || epgError.isNotBlank())) -> "Failed"
                lastResultSuccess == true -> "Completed"
                else -> "Idle"
            }
            return CustomerRefreshPresentation(
                playlistName = playlist?.name ?: "No active playlist",
                interval = when (val hours = playlist?.refreshHours) {
                    null -> "Unavailable"
                    0 -> "Off"
                    1 -> "Every 1 hour"
                    else -> "Every $hours hours"
                },
                playlistSuccess = playlist?.lastUpdated ?: 0L,
                playlistAttempt = playlist?.lastRefreshAttempt ?: 0L,
                epgSuccess = playlist?.epgLastUpdated ?: 0L,
                epgAttempt = playlist?.epgLastAttempt ?: 0L,
                playlistError = playlistError,
                epgError = epgError,
                status = phase + if (description.isNotBlank() && exists) " · $description" else "",
                busy = busy && exists,
                canRequest = exists && !busy,
            )
        }

        /** Defense in depth: network diagnostics must never render credential-bearing addresses. */
        fun safeMessage(message: String): String = message
            .replace(Regex("https?://\\S+", RegexOption.IGNORE_CASE), "[address hidden]")
            .replace(Regex("(?:username|user|password|passwd|pass|token|api[_-]?key|authorization)\\s*[:=]\\s*[^\\s,;]+", RegexOption.IGNORE_CASE), "[private value hidden]")
            .replace(Regex("\\s+"), " ").trim().take(240)
    }
}
