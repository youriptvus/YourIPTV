package com.youriptv.app.ui

import com.youriptv.app.data.ItemKind
import com.youriptv.app.data.MediaItem
import java.net.URI

/** Pure eligibility for first clean-ended recovery. Never opens or logs the stream URL. */
internal object LiveCleanEndRecovery {
    fun eligible(
        item: MediaItem,
        fullScreen: Boolean,
        ended: Boolean,
        hasError: Boolean,
        streamUrl: String = item.url,
    ): Boolean {
        if (!fullScreen || !ended || hasError ||
            item.kind != ItemKind.LIVE || item.isCatchup || item.isLocal
        ) return false
        // PlaybackFactory explicitly selects HLS for this hint, including hints outside the path.
        if (streamUrl.contains(".m3u8", ignoreCase = true)) return false
        return runCatching {
            val uri = URI(streamUrl)
            !uri.isOpaque && !uri.host.isNullOrBlank() &&
                (uri.scheme.equals("http", ignoreCase = true) || uri.scheme.equals("https", ignoreCase = true)) &&
                uri.path?.endsWith(".ts", ignoreCase = true) == true
        }.getOrDefault(false)
    }
}
