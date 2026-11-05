package com.youriptv.app

import com.youriptv.app.data.Catchup
import com.youriptv.app.data.StreamDiagnosis
import com.youriptv.app.data.StreamDiagnosis.Kind
import com.youriptv.app.data.XtreamUrls
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import java.net.SocketTimeoutException
import java.net.UnknownHostException

class CatchupNetworkTest {

    private val ts = ByteArray(376).also { it[0] = 0x47; it[188] = 0x47 }
    private val blockPage = "<html><head><title>Suspicious Site Blocked</title></head><body>blocked</body></html>".toByteArray()

    @Test fun mpegTsIsMedia() = assertEquals(Kind.MEDIA_OK, StreamDiagnosis.classify(200, "video/mp2t", ts, null, "h").kind)

    @Test fun hlsIsMedia() = assertEquals(Kind.MEDIA_OK, StreamDiagnosis.classify(200, "application/vnd.apple.mpegurl", "#EXTM3U\n".toByteArray(), null, "h").kind)

    @Test fun htmlInsteadOfVideoIsNetworkFilter() {
        val r = StreamDiagnosis.classify(200, "text/html", blockPage, null, "archive.example")
        assertEquals(Kind.NETWORK_FILTER, r.kind)
        assertEquals("Suspicious Site Blocked", r.detail)
        assertTrue(StreamDiagnosis.message(r, isArchive = true).contains("VPN"))
    }

    @Test fun unlabelledHtmlIsNetworkFilter() =
        assertEquals(Kind.NETWORK_FILTER, StreamDiagnosis.classify(200, null, blockPage, null, "h").kind)

    @Test fun dnsAndTimeoutAreUnreachable() {
        assertEquals(Kind.NETWORK_UNREACHABLE, StreamDiagnosis.classify(0, null, ByteArray(0), UnknownHostException("x"), "h").kind)
        assertEquals(Kind.NETWORK_UNREACHABLE, StreamDiagnosis.classify(0, null, ByteArray(0), SocketTimeoutException(), "h").kind)
        assertEquals(Kind.NETWORK_UNREACHABLE, StreamDiagnosis.classify(0, null, ByteArray(0), java.net.ConnectException("refused"), "h").kind)
    }

    @Test fun droppedConnectionIsNotReportedAsUnreachable() {
        val errors = listOf(
            java.io.EOFException("\\n not found: size=0"),
            java.net.SocketException("Connection reset"),
            java.net.ProtocolException("bad status line"),
            java.io.IOException("unexpected end of stream on http://h/..."),
        )
        for (e in errors) {
            val r = StreamDiagnosis.classify(0, null, ByteArray(0), e, "h")
            assertEquals(e.toString(), Kind.CONNECTION_DROPPED, r.kind)
            val msg = StreamDiagnosis.message(r, isArchive = false)
            assertTrue(msg.startsWith("Reached the stream server"))
            assertTrue(!msg.contains("Could not reach"))
        }
    }

    @Test fun httpCodesSeparateProviderFromNetwork() {
        assertEquals(Kind.ACCESS_DENIED, StreamDiagnosis.classify(401, null, ByteArray(0), null, "h").kind)
        assertEquals(Kind.ACCESS_DENIED, StreamDiagnosis.classify(509, null, ByteArray(0), null, "h").kind)
        assertEquals(Kind.NOT_AVAILABLE, StreamDiagnosis.classify(404, null, ByteArray(0), null, "h").kind)
        assertEquals(Kind.PROVIDER_ERROR, StreamDiagnosis.classify(502, null, ByteArray(0), null, "h").kind)
    }

    @Test fun archiveNotFoundMessageMentionsArchive() =
        assertTrue(StreamDiagnosis.message(StreamDiagnosis.Result(Kind.NOT_AVAILABLE, "h", 404), true).contains("archive"))

    @Test fun xtreamPanelM3uChannelGetsArchiveFromApi() {
        val archive = mapOf("71313" to 2)
        assertEquals(Triple(2, "xc", ""), Catchup.fromXtreamArchive("http://panel.example:8080/user1/pw/71313", archive))
        assertEquals(Triple(2, "xc", ""), Catchup.fromXtreamArchive("http://panel.example:8080/live/user1/pw/71313.ts", archive))
        assertNull(Catchup.fromXtreamArchive("http://panel.example:8080/user1/pw/99999", archive))
        assertNull(Catchup.fromXtreamArchive("http://panel.example:8080/user1/pw/71313", emptyMap()))
    }

    @Test fun xcUrlWithoutExtensionUsesTs() {
        val start = 1_793_380_000_000L // 2026-10-30 17:06:40 UTC
        val url = Catchup.m3uUrl("xc", "", "http://panel.example:8080/user1/pw/71313", start, start + 3_600_000L, start + 7_200_000L)
        assertEquals("http://panel.example:8080/timeshift/user1/pw/60/2026-10-30:17-06/71313.ts", url)
    }

    @Test fun pastWindowCoversArchive() {
        assertEquals(48, Catchup.pastWindowHours(12, 2))
        assertEquals(72, Catchup.pastWindowHours(72, 2))
        assertEquals(12, Catchup.pastWindowHours(12, 0))
        assertEquals(336, Catchup.pastWindowHours(12, 30))
    }

    @Test fun serverZoneResolvesKnownIdsAndFallsBackToUtc() {
        assertEquals("Europe/Berlin", XtreamUrls.serverZone("Europe/Berlin").id)
        assertEquals("UTC", XtreamUrls.serverZone("").id)
        assertEquals("UTC", XtreamUrls.serverZone("Not/AZone").id)
    }

    @Test fun timeshiftUsesServerLocalTime() {
        val start = 1_793_380_000_000L
        val url = XtreamUrls.timeshift("http://panel.example:8080", "user1", "pw", "5", start, 60, XtreamUrls.serverZone("Europe/Berlin"))
        assertTrue(url, url.contains("/60/2026-10-30:18-06/5.ts"))
    }
}
