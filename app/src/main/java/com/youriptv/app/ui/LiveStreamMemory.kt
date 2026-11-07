package com.youriptv.app.ui

import com.youriptv.app.data.ItemKind
import com.youriptv.app.data.MediaItem
import com.youriptv.app.data.Playlist

/** Remembers a tuned channel, independently of the guide's focus and the lifetime of its player. */
class LiveStreamMemory {
    var key: LiveChannelKey? = null
        private set
    private var source: Playlist? = null
    private var queueKeys: List<LiveChannelKey> = emptyList()

    fun remember(item: MediaItem, queue: List<MediaItem>, playlist: Playlist?) {
        if (item.kind != ItemKind.LIVE || item.isCatchup) return
        if (playlist?.id != item.playlistId) {
            clear()
            return
        }
        key = LiveChannelKey(item.playlistId, item.url)
        source = playlist
        queueKeys = queue.filter { it.kind == ItemKind.LIVE && !it.isCatchup && it.playlistId == item.playlistId }
            .map { LiveChannelKey(it.playlistId, it.url) }.distinct()
    }

    /** A deleted channel or changed provider must never reopen an old credential-bearing URL. */
    fun resolve(playlist: Playlist?, item: MediaItem?): MediaItem? {
        val remembered = key ?: return null
        val original = source
        val sameSource = original != null && playlist != null && original.id == playlist.id &&
            original.type == playlist.type && original.url == playlist.url &&
            original.username == playlist.username && original.password == playlist.password
        if (!sameSource || item == null || item.kind != ItemKind.LIVE || item.isCatchup ||
            item.playlistId != remembered.playlistId || item.url != remembered.url) {
            clear()
            return null
        }
        return item
    }

    /** Keeps the tuned queue's order, using only fresh database rows. */
    fun queue(item: MediaItem, freshItems: List<MediaItem>): List<MediaItem> {
        val available = freshItems.filter { it.kind == ItemKind.LIVE && !it.isCatchup }
            .associateBy { LiveChannelKey(it.playlistId, it.url) }
        val result = queueKeys.mapNotNull { available[it] }
        return if (result.any { it.playlistId == item.playlistId && it.url == item.url }) result else listOf(item)
    }

    fun clear() {
        key = null
        source = null
        queueKeys = emptyList()
    }
}
