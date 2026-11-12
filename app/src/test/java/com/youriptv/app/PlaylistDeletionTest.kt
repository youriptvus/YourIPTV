package com.youriptv.app

import com.youriptv.app.data.Playlist
import com.youriptv.app.data.SourceType
import com.youriptv.app.ui.PlaylistDeletion
import org.junit.Assert.*
import org.junit.Test

class PlaylistDeletionTest {
    private fun playlist(id: Long, name: String = "Sample $id") = Playlist(id, name, SourceType.M3U, "https://fixture.invalid/list.m3u")

    @Test fun onlyTheUnchangedConfirmedPlaylistCanBeDeleted() {
        val selected = playlist(7)
        assertTrue(PlaylistDeletion.mayDelete(selected, selected.copy(), false, true))
        assertFalse(PlaylistDeletion.mayDelete(selected, playlist(8), false, true))
        assertFalse(PlaylistDeletion.mayDelete(selected, null, false, true))
        assertFalse(PlaylistDeletion.mayDelete(selected, selected.copy(name = "Changed"), false, true))
        assertFalse(PlaylistDeletion.mayDelete(selected, selected.copy(url = "https://fixture.invalid/other.m3u"), false, true))
    }

    @Test fun noAuthorizationOrAnOngoingRefreshPreventsDeletion() {
        val selected = playlist(7)
        assertFalse(PlaylistDeletion.mayDelete(selected, selected, false, false))
        assertFalse(PlaylistDeletion.mayDelete(selected, selected, true, true))
        assertFalse(PlaylistDeletion.mayDelete(playlist(0), playlist(0), false, true))
    }

    @Test fun deletingAnInactivePlaylistKeepsTheCurrentActiveAccount() {
        assertEquals(7L, PlaylistDeletion.nextActive(7, 8, listOf(playlist(7), playlist(9))))
    }

    @Test fun deletingTheActivePlaylistUsesTheFirstRemainingAccount() {
        assertEquals(8L, PlaylistDeletion.nextActive(7, 7, listOf(playlist(8), playlist(9))))
    }

    @Test fun deletingTheLastPlaylistReturnsTheEmptyStateId() {
        assertEquals(-1L, PlaylistDeletion.nextActive(7, 7, emptyList()))
    }
}
