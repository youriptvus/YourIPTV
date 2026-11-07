package com.youriptv.app

import com.youriptv.app.data.ItemKind
import com.youriptv.app.data.MediaItem
import com.youriptv.app.ui.CurrentStreamReturn
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class CurrentStreamReturnTest {
    private val live = MediaItem(playlistId = 7, kind = ItemKind.LIVE, name = "Live", group = "News", url = "live")
    private val movie = live.copy(kind = ItemKind.MOVIE, url = "movie")

    @Test fun aDisposedVodActivityCannotOutrankANewLivePreview() {
        assertFalse(CurrentStreamReturn.canReusePlayer(movie, null, false, live, live))
        assertFalse(CurrentStreamReturn.canReusePlayer(movie, live.url, false, live, live))
        assertTrue(CurrentStreamReturn.canReusePlayer(movie, movie.url, false, live, live))
    }

    @Test fun aRetainedArchiveStreamWinsOverThePreviouslyTunedLiveChannel() {
        val archive = live.copy(url = "archive", isCatchup = true)
        assertTrue(CurrentStreamReturn.canReusePlayer(archive, archive.url, false, live, live))
        assertFalse(CurrentStreamReturn.canReusePlayer(archive, live.url, false, live, live))
    }

    @Test fun theAttachedSharedChannelWinsOverTheActivitysStaleQueueSelection() {
        val stale = live.copy(url = "old-channel")
        assertTrue(CurrentStreamReturn.canReusePlayer(stale, live.url, true, live, live))
        assertFalse(CurrentStreamReturn.canReusePlayer(stale, live.url, false, live, live))
    }

    @Test fun changedCategoryRequiresTheFreshParentalCheckInsteadOfReusingOldMetadata() {
        val reclassified = live.copy(group = "Locked category")
        assertFalse(CurrentStreamReturn.canReusePlayer(live, live.url, true, live, reclassified))
    }

    @Test fun identicalUrlsInOtherPlaylistsNeverAuthorizeAnExistingLivePlayer() {
        assertFalse(CurrentStreamReturn.canReusePlayer(live, live.url, true, live, live.copy(playlistId = 8)))
    }
}
