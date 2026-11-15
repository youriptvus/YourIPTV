package com.youriptv.app

import com.youriptv.app.data.ItemKind
import com.youriptv.app.data.MediaItem
import com.youriptv.app.data.Program
import com.youriptv.app.data.GuideWindowResult
import com.youriptv.app.ui.GuideContinuity
import com.youriptv.app.ui.GuideProgramCache
import org.junit.Assert.*
import org.junit.Test

class GuideCommittedRefreshTest {
    private fun channel(url: String, playlist: Long = 1) =
        MediaItem(playlistId = playlist, kind = ItemKind.LIVE, name = url, group = "Fixture", url = url, tvgId = "id-$url")

    @Test fun committedInvalidationLoadsNewProgrammesAndSeparatesPlaylistIdentity() {
        var title = "cached title"
        var reads = 0
        val cache = GuideProgramCache({ ch, _, _ -> reads++; GuideWindowResult(listOf(Program(ch.tvgId, 1, 2, title, ""))) })
        val first = channel("a")
        assertEquals("cached title", cache.programsFor(first, 1, 2).single().title)
        title = "committed title"
        assertEquals("cached title", cache.programsFor(first, 1, 2).single().title)
        assertEquals(1, reads)
        assertEquals("committed title", cache.programsFor(first.copy(playlistId = 2), 1, 2).single().title)
        cache.clear()
        assertEquals("committed title", cache.programsFor(first, 1, 2).single().title)
        assertEquals(3, reads)
    }

    @Test fun channelReorderKeepsSelectedAndFirstVisibleUrlsAndGuideTime() {
        val snapshot = GuideContinuity(1L to "b", 1, 1L to "a", 0, -7, 7_200_000, 5_400_000)
        val updated = listOf(channel("c"), channel("a"), channel("b"))
        assertEquals(2, snapshot.focusedIn(updated))
        assertEquals(1, snapshot.firstIn(updated))
        assertEquals(-7, snapshot.firstOffset)
        assertEquals(7_200_000L, snapshot.focusTime)
        assertEquals(5_400_000L, snapshot.windowStart)
    }

    @Test fun removedSelectionClampsWithoutUsingAChannelFromAnotherPlaylist() {
        val snapshot = GuideContinuity(1L to "gone", 5, 1L to "same", 1, 0, 100, 50)
        val updated = listOf(channel("same", 2), channel("new"))
        assertEquals(1, snapshot.focusedIn(updated))
        assertEquals(1, snapshot.firstIn(updated))
        assertEquals(0, snapshot.focusedIn(emptyList()))
    }
}
