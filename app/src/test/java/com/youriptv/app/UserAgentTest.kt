package com.youriptv.app

import com.youriptv.app.data.Http
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Test

class UserAgentTest {

    @After fun reset() { Http.fallbackUserAgent = { "" } }

    @Test fun playlistAgentWins() {
        Http.fallbackUserAgent = { "Global/1" }
        assertEquals("Playlist/1", Http.effectiveUserAgent("Playlist/1"))
    }

    @Test fun globalAgentUsedWhenPlaylistBlank() {
        Http.fallbackUserAgent = { "Global/1" }
        assertEquals("Global/1", Http.effectiveUserAgent(""))
    }

    @Test fun builtInDefaultWhenBothBlank() =
        assertEquals(Http.DEFAULT_USER_AGENT, Http.effectiveUserAgent(""))
}
