package com.youriptv.app

import com.youriptv.app.data.ItemKind
import com.youriptv.app.data.MediaItem
import com.youriptv.app.data.Playlist
import com.youriptv.app.data.SourceType
import com.youriptv.app.ui.LiveChannelKey
import com.youriptv.app.ui.LiveStreamMemory
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class LiveStreamMemoryTest {
    private val playlist = Playlist(id = 7, name = "Provider", type = SourceType.XTREAM, url = "https://provider.test",
        username = "test-user", password = "test-password")
    private val first = MediaItem(playlistId = 7, kind = ItemKind.LIVE, name = "First", group = "News", url = "first")
    private val playing = first.copy(name = "Playing", url = "playing")

    @Test fun retainsActuallyTunedChannelAndQueueOrderInsteadOfFirstOrHighlightedChannel() {
        val memory = LiveStreamMemory()
        memory.remember(playing, listOf(first, playing), playlist)
        assertEquals(LiveChannelKey(7, "playing"), memory.key)
        val fresh = playing.copy(name = "Updated name", group = "New group")
        assertEquals(fresh, memory.resolve(playlist.copy(lastUpdated = 123), fresh))
        assertEquals(listOf(first, fresh), memory.queue(fresh, listOf(fresh, first)))
    }

    @Test fun removedChannelClearsTheSnapshotInsteadOfFallingBackToAnotherChannel() {
        val memory = LiveStreamMemory()
        memory.remember(playing, listOf(first, playing), playlist)
        assertNull(memory.resolve(playlist, null))
        assertNull(memory.key)
    }

    @Test fun deletedOrChangedProviderCannotReviveAnOldStreamUrl() {
        for (changed in listOf(null, playlist.copy(url = "https://other.test"), playlist.copy(username = "changed"),
            playlist.copy(password = "changed"), playlist.copy(type = SourceType.M3U))) {
            val memory = LiveStreamMemory()
            memory.remember(playing, listOf(playing), playlist)
            assertNull(memory.resolve(changed, playing))
            assertNull(memory.key)
        }
    }

    @Test fun aMatchingUrlFromAnotherPlaylistDoesNotCountAsTheCurrentChannel() {
        val memory = LiveStreamMemory()
        memory.remember(playing, listOf(playing), playlist)
        assertNull(memory.resolve(playlist, playing.copy(playlistId = 8)))
    }

    @Test fun staleQueueRowsAreReplacedAndDeletedEntriesAreDropped() {
        val memory = LiveStreamMemory()
        memory.remember(playing, listOf(first, playing), playlist)
        val fresh = playing.copy(name = "Fresh metadata")
        assertEquals(listOf(fresh), memory.queue(fresh, listOf(fresh)))
        assertEquals(listOf(fresh), memory.queue(fresh, emptyList()))
    }

    @Test fun nonLiveItemsNeverReplaceTheRememberedTunedChannel() {
        val memory = LiveStreamMemory()
        memory.remember(playing, listOf(playing), playlist)
        memory.remember(first.copy(kind = ItemKind.MOVIE), listOf(first), playlist)
        assertEquals(LiveChannelKey(7, "playing"), memory.key)
    }
}
