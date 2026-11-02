package com.youriptv.app

import com.youriptv.app.ui.LiveChannelKey
import com.youriptv.app.ui.PreviewSelection
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class PreviewSelectionTest {
    private val first = LiveChannelKey(1, "channel-a")
    private val second = LiveChannelKey(1, "channel-b")

    @Test fun firstSelectionAndDifferentChannelDoNotOpenFullscreen() {
        assertFalse(PreviewSelection.opensFullscreen(null, first, authorized = true, hasPlayer = false))
        assertFalse(PreviewSelection.opensFullscreen(first, second, authorized = true, hasPlayer = true))
    }

    @Test fun sameAuthorizedChannelOpensFullscreenWithoutATimeCondition() {
        repeat(5) {
            assertTrue(PreviewSelection.opensFullscreen(first, first, authorized = true, hasPlayer = true))
        }
        assertFalse(PreviewSelection.opensFullscreen(first, first, authorized = true, hasPlayer = false))
        assertFalse(PreviewSelection.opensFullscreen(first, first, authorized = false, hasPlayer = true))
    }

    @Test fun matchingUrlsInDifferentPlaylistsAreDifferentChannels() {
        assertFalse(PreviewSelection.opensFullscreen(first, first.copy(playlistId = 2), authorized = true, hasPlayer = true))
    }

    @Test fun aLockedGroupRequiresItsOwnCurrentAuthorization() {
        assertFalse(PreviewSelection.isAuthorized(true, "locked", null, 10, 10))
        assertFalse(PreviewSelection.isAuthorized(true, "locked", "other", 10, 10))
        assertFalse(PreviewSelection.isAuthorized(true, "locked", "locked", 10, 11))
        assertTrue(PreviewSelection.isAuthorized(true, "locked", "locked", 10, 10))
        assertTrue(PreviewSelection.isAuthorized(false, "open", null, 10, 11))
    }
}
