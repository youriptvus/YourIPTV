package com.youriptv.app

import com.youriptv.app.data.ItemKind
import com.youriptv.app.data.MediaItem
import com.youriptv.app.data.Repository
import com.youriptv.app.data.SeriesNames
import com.youriptv.app.data.PinVault
import com.youriptv.app.data.XtreamUrls
import org.json.JSONObject
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class DataLogicTest {

    @Test
    fun xtreamBaseNormalisation() {
        assertEquals("http://host:8080", XtreamUrls.normalizeBase("host:8080/"))
        assertEquals("http://host:8080", XtreamUrls.normalizeBase("http://host:8080/player_api.php"))
        assertEquals("https://h", XtreamUrls.normalizeBase(" https://h/get.php?x=1 "))
    }

    @Test
    fun xtreamStreamUrlsEncodeCredentials() {
        assertEquals("http://h/live/u%40x/p%26w/5.ts", XtreamUrls.live("h", "u@x", "p&w", "5"))
        assertEquals("http://h/movie/u/p/7.mkv", XtreamUrls.movie("http://h/", "u", "p", "7", "mkv"))
        assertEquals("http://h/series/u/p/9.mp4", XtreamUrls.episode("http://h", "u", "p", "9", ""))
        assertTrue(XtreamUrls.api("h", "u", "p", "get_live_streams").endsWith("&action=get_live_streams"))
    }

    @Test
    fun liveFormatSwitchOnlyTouchesLiveUrls() {
        assertEquals("http://h/live/u/p/1.m3u8", XtreamUrls.applyLiveFormat("http://h/live/u/p/1.ts", "m3u8"))
        assertEquals("http://h/live/u/p/1.ts", XtreamUrls.applyLiveFormat("http://h/live/u/p/1.m3u8", "ts"))
        assertEquals("http://h/movie/u/p/1.ts", XtreamUrls.applyLiveFormat("http://h/movie/u/p/1.ts", "m3u8"))
        assertEquals("http://h/live/u/p/1.mp4", XtreamUrls.applyLiveFormat("http://h/live/u/p/1.mp4", "m3u8"))
    }

    @Test
    fun parsesLoginFromPastedM3uLink() {
        val login = XtreamUrls.parseLogin("http://h.example:80/get.php?username=me&pass" + "word=s%26cret&type=m3u_plus&output=ts")
        assertEquals(XtreamUrls.Login("http://h.example:80", "me", "s&cret"), login)
        assertNull(XtreamUrls.parseLogin("http://h.example/list.m3u"))
        assertNull(XtreamUrls.parseLogin("http://h.example/get.php?username=me"))
    }

    @Test
    fun seriesNamesParseAndGroup() {
        assertEquals(SeriesNames.Parsed("Show Name", 1, 2), SeriesNames.parse("Show Name S01 E02"))
        assertEquals(SeriesNames.Parsed("The Show (2019)", 3, 14), SeriesNames.parse("The Show (2019) - s03e14"))
        assertEquals(SeriesNames.Parsed("Movie Title", 0, 0), SeriesNames.parse("Movie Title"))
        fun ep(name: String) = MediaItem(playlistId = 1, kind = ItemKind.EPISODE, name = name, group = "G", url = "u/$name")
        val grouped = SeriesNames.groupEpisodes(listOf(ep("A S01 E01"), ep("B S01 E01"), ep("A S01 E02"), ep("a S02E01")))
        assertEquals(listOf("A", "B"), grouped.map { it.name })
        assertTrue(grouped.all { it.kind == ItemKind.SERIES && it.url.startsWith(SeriesNames.M3U_SERIES_PREFIX) })
    }

    @Test
    fun parsesXtreamSeriesInfo() {
        val json = JSONObject(
            """{"episodes":{"2":[{"id":"21","episode_num":"1","title":"S2E1","container_extension":"mkv","info":{"plot":"p"}}],
               "1":[{"id":"12","episode_num":"2","title":"S1E2","container_extension":"mp4"},{"id":"11","episode_num":"1","title":"S1E1"}]}}"""
        )
        val eps = Repository.parseSeriesEpisodes(json) { id, ext -> "x/$id.$ext" }
        assertEquals(listOf("11", "12", "21"), eps.map { it.id })
        assertEquals("x/21.mkv", eps[2].url)
        assertEquals("p", eps[2].plot)
        assertEquals(2, eps[2].season)
    }

    @Test
    fun pinHashIsSaltedAndDeterministic() {
        val a = PinVault.hash("1234", "salt1")
        assertEquals(a, PinVault.hash("1234", "salt1"))
        assertNotEquals(a, PinVault.hash("1234", "salt2"))
        assertNotEquals(a, PinVault.hash("1235", "salt1"))
        assertEquals(64, a.length)
    }
}
