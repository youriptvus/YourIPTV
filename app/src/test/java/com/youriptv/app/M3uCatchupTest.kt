package com.youriptv.app

import com.youriptv.app.data.Catchup
import com.youriptv.app.data.M3uParser
import com.youriptv.app.data.Recorder
import com.youriptv.app.ui.FrameRateMatcher
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import java.io.BufferedReader
import java.io.StringReader

/** M3U catch-up templates, auto frame-rate mode selection and HLS recording parsing (mock data only). */
class M3uCatchupTest {
    private val start = 1_800_000_000_000L // 2027-01-15 08:00:00 UTC
    private val end = start + 3_600_000L
    private val now = start + 7_200_000L

    @Test
    fun defaultAbsoluteSource() {
        assertEquals(
            "http://arch.example/ch1/1800000000/1800007200/3600",
            Catchup.m3uUrl("default", "http://arch.example/ch1/{utc}/{lutc}/{duration}", "http://h/live/1.ts", start, end, now),
        )
    }

    @Test
    fun appendAndRelativeSource() {
        assertEquals(
            "http://h/live/1.ts?start=1800000000&end=1800003600",
            Catchup.m3uUrl("append", "?start=\${start}&end=\${end}", "http://h/live/1.ts", start, end, now),
        )
        assertEquals(
            "http://h/live/1.ts?utc={x}&from=1800000000",
            Catchup.m3uUrl("", "?utc={x}&from={utc}", "http://h/live/1.ts", start, end, now),
        )
    }

    @Test
    fun shiftMode() {
        assertEquals(
            "http://h/live/1.ts?x=1&utc=1800000000&lutc=1800007200",
            Catchup.m3uUrl("shift", "", "http://h/live/1.ts?x=1", start, end, now),
        )
        assertEquals("http://h/1.m3u8?utc=1800000000&lutc=1800007200", Catchup.m3uUrl("timeshift", "", "http://h/1.m3u8", start, end, now))
    }

    @Test
    fun flussonicModes() {
        assertEquals(
            "http://h:8080/ch/index-1800000000-3600.m3u8?token=a",
            Catchup.m3uUrl("flussonic", "", "http://h:8080/ch/index.m3u8?token=a", start, end, now),
        )
        assertEquals(
            "http://h/ch/timeshift_abs-1800000000.ts?token=a",
            Catchup.m3uUrl("fs", "", "http://h/ch/mpegts?token=a", start, end, now),
        )
    }

    @Test
    fun xtreamStyleMode() {
        assertEquals(
            "http://h:80/timeshift/user/pass/60/2027-01-15:08-00/123.ts",
            Catchup.m3uUrl("xc", "", "http://h:80/user/pass/123.ts", start, end, now),
        )
        assertEquals(
            "http://h/timeshift/u/p/60/2027-01-15:08-00/9.m3u8",
            Catchup.m3uUrl("xc", "", "http://h/live/u/p/9.m3u8", start, end, now),
        )
    }

    @Test
    fun dateAndDivisorPlaceholders() {
        assertEquals("20270115-080000", Catchup.expand("{Y}{m}{d}-{H}{M}{S}", start, end, now))
        assertEquals("2027-01-15 08:00:00", Catchup.expand("{utc:Y-m-d H:M:S}", start, end, now))
        assertEquals("2027-01-15T09", Catchup.expand("\${end:Y-m-d}T{utcend:H}", start, end, now))
        assertEquals("60/120", Catchup.expand("{duration:60}/{offset:60}", start, end, now))
        // A programme still airing is archived up to "now".
        assertEquals("1800", Catchup.expand("{duration}", start, start + 3_600_000L, start + 1_800_000L))
    }

    @Test
    fun noUsableRule() {
        assertNull(Catchup.m3uUrl("", "", "http://h/live/1.ts", start, end, now))
        assertNull(Catchup.m3uUrl("append", "", "http://h/live/1.ts", start, end, now))
        assertNull(Catchup.m3uUrl("flussonic", "", "http://h/stream", start, end, now))
        assertNull(Catchup.m3uUrl("xc", "", "http://h/notxtream", start, end, now))
    }

    @Test
    fun attributesWithHeaderDefaults() {
        val header = mapOf("catchup" to "shift", "catchup-days" to "3")
        assertEquals(Triple(3, "shift", ""), Catchup.m3uAttributes(emptyMap(), header))
        assertEquals(Triple(7, "shift", ""), Catchup.m3uAttributes(mapOf("catchup-days" to "7"), header))
        assertEquals(Triple(0, "", ""), Catchup.m3uAttributes(mapOf("catchup" to "disabled"), header))
        assertEquals(Triple(0, "", ""), Catchup.m3uAttributes(mapOf("catchup-days" to "5"), emptyMap()))
        assertEquals(Triple(1, "default", "?a={utc}"), Catchup.m3uAttributes(mapOf("catchup" to "default", "catchup-source" to "?a={utc}"), emptyMap()))
        assertEquals(Triple(2, "flussonic", ""), Catchup.m3uAttributes(mapOf("catchup" to "Flussonic", "timeshift" to "2"), emptyMap()))
    }

    @Test
    fun parserExposesHeaderAttributes() {
        val text = "#EXTM3U url-tvg=\"http://e/x.xml\" catchup=\"append\" catchup-source=\"?utc={utc}\" catchup-days=\"2\"\n" +
            "#EXTINF:-1 tvg-id=\"a\" group-title=\"G\",A\nhttp://h/a.ts\n"
        var header: M3uParser.Header? = null
        val entries = ArrayList<M3uParser.Entry>()
        M3uParser.parse(BufferedReader(StringReader(text)), { header = it }) { entries.add(it) }
        assertEquals("http://e/x.xml", header!!.epgUrl)
        assertEquals(Triple(2, "append", "?utc={utc}"), Catchup.m3uAttributes(entries.single().attributes, header!!.attributes))
    }

    // ---------------- Auto frame rate ----------------

    private fun m(id: Int, hz: Float, w: Int = 1920, h: Int = 1080) = FrameRateMatcher.Mode(id, w, h, hz)
    private val modes = listOf(m(1, 60f), m(2, 59.94006f), m(3, 50f), m(4, 30f), m(5, 25f), m(6, 24f), m(7, 23.976025f), m(8, 50f, 3840, 2160))

    @Test
    fun framePicksClosestMultiple() {
        val cur = modes[0]
        assertEquals(3, FrameRateMatcher.pick(modes, cur, 25f)?.id)
        assertEquals(2, FrameRateMatcher.pick(modes, cur, 29.97003f)?.id)
        assertEquals(7, FrameRateMatcher.pick(modes, cur, 23.976f)?.id)
        assertEquals(6, FrameRateMatcher.pick(modes, cur, 24f)?.id)
        assertNull("60 Hz already suits 30 fps", FrameRateMatcher.pick(modes, cur, 30f))
        assertNull("unknown frame rate", FrameRateMatcher.pick(modes, cur, -1f))
        assertEquals("50 Hz back to 60 Hz", 1, FrameRateMatcher.pick(modes, modes[2], 60f)?.id)
    }

    @Test
    fun frameRateToleranceKeepsNtscDistinct() {
        assertTrue(FrameRateMatcher.matches(59.94006f, 29.97003f))
        assertTrue(!FrameRateMatcher.matches(60f, 29.97003f))
        assertTrue(!FrameRateMatcher.matches(59.94006f, 30f))
        assertNull("other resolutions are ignored", FrameRateMatcher.pick(listOf(m(1, 60f), m(8, 50f, 3840, 2160)), m(1, 60f), 25f))
    }

    @Test
    fun frameRateEstimatedFromTimestamps() {
        // 90 kHz MPEG-TS clock: 3003 ticks = 29.97 fps, 3600 = 25 fps, 3753.75 = 23.976 fps
        val ntsc = (0 until 30).map { 1_000_000L + it * 33_367L - (it % 3) }
        assertEquals(30000f / 1001f, FrameRateMatcher.estimateFps(ntsc)!!, 0.0001f)
        assertEquals(25f, FrameRateMatcher.estimateFps((0 until 30).map { it * 40_000L }.shuffled())!!, 0.0001f)
        assertEquals(24000f / 1001f, FrameRateMatcher.estimateFps((0 until 30).map { it * 41_708L })!!, 0.0001f)
        val withGap = (0 until 30).map { it * 20_000L } + listOf(5_000_000L)
        assertEquals("seek gaps are ignored", 50f, FrameRateMatcher.estimateFps(withGap)!!, 0.0001f)
        assertNull("too few frames", FrameRateMatcher.estimateFps(listOf(0L, 40_000L, 80_000L)))
        assertEquals("non-standard rates are kept", 12.5f, FrameRateMatcher.estimateFps((0 until 30).map { it * 80_000L })!!, 0.001f)
        // Matroska-style millisecond timestamps over ~5 s (41/42 ms and 16/17 ms alternation)
        val mkv23976 = (7 until 128).map { Math.round(it * 1001.0 / 24.0) * 1000L }
        assertEquals(24000f / 1001f, FrameRateMatcher.estimateFps(mkv23976)!!, 0.0001f)
        val mkv24 = (7 until 128).map { Math.round(it * 1000.0 / 24.0) * 1000L }
        assertEquals(24f, FrameRateMatcher.estimateFps(mkv24)!!, 0.0001f)
        val mkv5994 = (7 until 307).map { Math.round(it * 1001.0 / 60.0) * 1000L }
        assertEquals(60000f / 1001f, FrameRateMatcher.estimateFps(mkv5994)!!, 0.0001f)
    }

    // ---------------- Recording (HLS) ----------------

    @Test
    fun hlsMasterAndMediaPlaylists() {
        val master = Recorder.parseHls("#EXTM3U\n#EXT-X-STREAM-INF:BANDWIDTH=1\nlow/index.m3u8\n", "http://h/ch/master.m3u8?t=1")
        assertEquals(listOf("http://h/ch/low/index.m3u8"), master.variants)
        val media = Recorder.parseHls(
            "#EXTM3U\n#EXT-X-TARGETDURATION:4\n#EXTINF:4,\nseg1.ts\n#EXTINF:4,\nhttp://cdn/seg2.ts\n#EXT-X-ENDLIST\n", "http://h/ch/low/index.m3u8",
        )
        assertEquals(listOf("http://h/ch/low/seg1.ts", "http://cdn/seg2.ts"), media.segments)
        assertEquals(4, media.targetDuration)
        assertTrue(media.ended)
    }

    @Test
    fun recordingFileNameIsSafe() {
        val name = Recorder.fileName(
            com.youriptv.app.data.Recording(id = 7, playlistId = 1, channelUrl = "http://h/u/p/1.ts", channelName = "BBC One HD", title = "News: 10/11 @ 9?", start = start, end = end),
        )
        assertTrue(name, name.matches(Regex("""\d{8}_\d{4}_7_BBC_One_HD_News_10_11_9\.ts""")))
        val same = Recorder.fileName(
            com.youriptv.app.data.Recording(id = 8, playlistId = 1, channelUrl = "u", channelName = "A&E HD", title = "A&E HD", start = start, end = end),
        )
        assertTrue(same, same.endsWith("_8_A_E_HD.ts"))
    }
}
