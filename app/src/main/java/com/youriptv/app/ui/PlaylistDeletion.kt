package com.youriptv.app.ui

import com.youriptv.app.data.Playlist

/** A named confirmation may delete only the unchanged selected account, never a replacement row. */
internal object PlaylistDeletion {
    fun mayDelete(selected: Playlist, current: Playlist?, busy: Boolean, authorized: Boolean): Boolean =
        authorized && !busy && selected.id > 0L && current == selected

    fun nextActive(activeId: Long, deletedId: Long, remaining: List<Playlist>): Long =
        if (activeId != deletedId) activeId else remaining.firstOrNull()?.id ?: -1L
}
