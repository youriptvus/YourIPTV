package com.youriptv.app

import com.youriptv.app.data.BackupCodec
import com.youriptv.app.data.BackupData
import com.youriptv.app.data.BackupParental
import com.youriptv.app.data.BackupPlaylist
import com.youriptv.app.data.ConnectionLimit
import com.youriptv.app.data.Playlist
import com.youriptv.app.data.SourceType
import com.youriptv.app.data.XtreamUrls
import com.youriptv.app.ui.YourIptvLogin
import org.junit.Assert.*
import org.junit.Test

class YourIptvLoginTest {
    @Test fun createsANewOrdinaryXtreamPlaylistWithFixedDefaults() {
        val p = requireNotNull(YourIptvLogin.newPlaylist("sample-user", "sample-password"))
        assertEquals(0L, p.id)
        assertEquals("YourIPTV", p.name)
        assertEquals("http://cw-25.com", p.url)
        assertEquals(SourceType.XTREAM, p.type)
        assertEquals("", p.epgUrl)
        assertEquals("", p.userAgent)
        assertEquals(0L, p.lastUpdated)
    }

    @Test fun rejectsEmptyOrWhitespaceOnlyUsernameBeforeSaving() {
        listOf("", " ", "\t\r\n").forEach { assertNull(YourIptvLogin.newPlaylist(it, "sample-password")) }
    }

    @Test fun rejectsAnEmptyPasswordBeforeSaving() {
        assertNull(YourIptvLogin.newPlaylist("sample-user", ""))
        assertNull(YourIptvLogin.newPlaylist("", ""))
    }

    @Test fun trimsUsernameWhilePreservingPasswordExactlyLikeXtreamLogin() {
        val password = " \tPa ss+&/=?#\t "
        val p = requireNotNull(YourIptvLogin.newPlaylist(" \t sample-user \n", password))
        assertEquals("sample-user", p.username)
        assertEquals(password, p.password)
        // A provider may use spaces in a password; do not silently trim them.
        assertEquals("  ", requireNotNull(YourIptvLogin.newPlaylist("sample-user", "  ")).password)
    }

    @Test fun enteredSpecialCharactersUseTheExistingXtreamEncoding() {
        val p = requireNotNull(YourIptvLogin.newPlaylist(" user+&@/ ", " p+&/=?# "))
        assertEquals(
            "http://cw-25.com/player_api.php?username=user%2B%26%40%2F&password=+p%2B%26%2F%3D%3F%23+&action=get_live_streams",
            XtreamUrls.api(p.url, p.username, p.password, "get_live_streams"),
        )
    }

    @Test fun defaultProviderGuideUsesTheSameFixedServerAndCredentials() {
        val p = requireNotNull(YourIptvLogin.newPlaylist("user+name", "p&word"))
        assertEquals(
            "http://cw-25.com/xmltv.php?username=user%2Bname&password=p%26word",
            XtreamUrls.xmltv(p.url, p.username, p.password),
        )
        assertEquals("", p.epgUrl)
    }

    @Test fun accountMatchingAndLimitsUseOrdinaryProviderRules() {
        val p = requireNotNull(YourIptvLogin.newPlaylist("sample-user", "sample-password"))
        val existing = Playlist(9L, "Existing provider", SourceType.XTREAM, "http://CW-25.com:80/", "sample-user", "sample-password")
        assertTrue(ConnectionLimit.sameAccount(p, existing))
        assertFalse(ConnectionLimit.sameAccount(p, existing.copy(username = "someone-else")))
        assertEquals(0, ConnectionLimit.effective(p, emptyList()))
        assertEquals(1, ConnectionLimit.effective(p.copy(maxConnections = 1), emptyList()))
        assertEquals(4, ConnectionLimit.effective(p.copy(maxConnections = 4), emptyList()))
    }

    @Test fun existingBackupFormatRoundTripsTheOrdinaryXtreamConnection() {
        val p = requireNotNull(YourIptvLogin.newPlaylist("sample-user", " sample-password "))
        val entry = BackupPlaylist(
            ref = 7L, name = p.name, type = p.type, url = p.url,
            username = p.username, password = p.password, epgUrl = p.epgUrl, userAgent = p.userAgent,
        )
        val data = BackupData(123L, "1.0.4", emptyMap(), BackupParental(), listOf(entry), 7L)
        val restored = BackupCodec.decode(BackupCodec.encode(data))
        assertEquals(listOf(entry), restored.playlists)
        assertEquals(7L, restored.activeRef)
        assertEquals(SourceType.XTREAM, restored.playlists.single().type)
    }
}
