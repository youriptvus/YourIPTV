package com.youriptv.app

import com.youriptv.app.data.ConnectionLimit
import com.youriptv.app.data.Playlist
import com.youriptv.app.data.SourceType
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class ConnectionLimitTest {

    private val xtream = Playlist(1, "X", SourceType.XTREAM, "http://panel.example:8080", "user1", "pw", maxConnections = 1)

    private fun m3u(url: String, max: Int = 0) = Playlist(2, "M", SourceType.M3U, url, maxConnections = max)

    @Test fun ownLimitWins() =
        assertEquals(3, ConnectionLimit.effective(m3u("http://panel.example:8080/get.php?username=user1", 3), listOf(xtream)))

    @Test fun borrowsFromMatchingXtreamAccount() =
        assertEquals(1, ConnectionLimit.effective(m3u("http://PANEL.example:8080/get.php?username=user1&password=pw&type=m3u_plus"), listOf(xtream)))

    @Test fun defaultPortMatches() {
        val x = xtream.copy(url = "http://panel.example/")
        assertEquals(1, ConnectionLimit.effective(m3u("http://panel.example:80/get.php?username=user1"), listOf(x)))
    }

    @Test fun differentUserHostOrPortIsUnknown() {
        assertEquals(0, ConnectionLimit.effective(m3u("http://panel.example:8080/get.php?username=other"), listOf(xtream)))
        assertEquals(0, ConnectionLimit.effective(m3u("http://other.example:8080/get.php?username=user1"), listOf(xtream)))
        assertEquals(0, ConnectionLimit.effective(m3u("http://panel.example/get.php?username=user1"), listOf(xtream)))
        assertEquals(0, ConnectionLimit.effective(m3u("http://panel.example:8080/list.m3u"), listOf(xtream)))
        assertEquals(0, ConnectionLimit.effective(m3u("not a url"), listOf(xtream)))
    }

    @Test fun identifiesTheSameProviderAccountAcrossPlaylistTypes() {
        assertTrue(ConnectionLimit.sameAccount(xtream, m3u("http://panel.example:8080/get.php?username=user1")))
        assertTrue(ConnectionLimit.sameAccount(xtream, xtream))
        assertFalse(ConnectionLimit.sameAccount(xtream, m3u("http://panel.example:8080/get.php?username=other")))
        assertFalse(ConnectionLimit.sameAccount(xtream, m3u("http://other.example:8080/get.php?username=user1")))
        assertFalse(ConnectionLimit.sameAccount(xtream, m3u("http://panel.example:8080/list.m3u")))
    }
}
