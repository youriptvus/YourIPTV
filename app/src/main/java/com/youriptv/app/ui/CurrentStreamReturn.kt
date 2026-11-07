package com.youriptv.app.ui

import com.youriptv.app.data.ItemKind
import com.youriptv.app.data.MediaItem

/** Determines whether an activity still represents the attached stream rather than a stale playback queue. */
internal object CurrentStreamReturn {
    fun canReusePlayer(
        item: MediaItem?, attachedMediaId: String?, ownsLivePlayer: Boolean,
        liveItem: MediaItem?, freshLiveItem: MediaItem?,
    ): Boolean {
        if (item == null || attachedMediaId == null) return false
        if (item.kind != ItemKind.LIVE || item.isCatchup) return attachedMediaId == item.url
        return ownsLivePlayer && liveItem != null && freshLiveItem != null &&
            attachedMediaId == liveItem.url && freshLiveItem.playlistId == liveItem.playlistId &&
            freshLiveItem.url == liveItem.url && freshLiveItem.group == liveItem.group
    }
}
