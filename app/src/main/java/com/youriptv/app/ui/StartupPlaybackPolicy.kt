package com.youriptv.app.ui

import com.youriptv.app.data.ItemKind
import com.youriptv.app.data.MediaItem

/** Process-local launch marker distinguishes process restoration from a screen rotation. */
class MainLaunchTracker {
    private var claimed = false
    @Synchronized fun claim(): Boolean = (!claimed).also { claimed = true }
}

object StartupPlaybackPolicy {
    fun onCreate(enabled: Boolean, firstMainInProcess: Boolean, restoredState: Boolean, pending: Boolean,
                 guideRequest: Boolean = false) =
        enabled && !guideRequest && (firstMainInProcess || !restoredState || pending)
    fun onNewIntent(enabled: Boolean, launcher: Boolean, guideRequest: Boolean) = enabled && launcher && !guideRequest
    fun matches(item: MediaItem, url: String, playlistId: Long) = item.kind == ItemKind.LIVE &&
        !item.isCatchup && !item.isLocal && url.isNotBlank() && item.url == url &&
        (playlistId <= 0 || item.playlistId == playlistId)
}
