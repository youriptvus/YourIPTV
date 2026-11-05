package com.youriptv.app

import com.youriptv.app.ui.H264Aud
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import java.io.File

/** Guards the live MPEG-TS extraction fix for choppy phone playback (one decoder input per picture). */
class LiveTsExtractionTest {
    private fun ui(name: String) = listOf(
        File("src/main/java/com/youriptv/app/ui/$name"),
        File("app/src/main/java/com/youriptv/app/ui/$name"),
    ).first { it.exists() }.readText()

    private fun bytes(vararg v: Int) = ByteArray(v.size) { v[it].toByte() }

    @Test
    fun findsAccessUnitDelimiterAfterPesHeader() {
        // PES start code (stream 0xE0), header bytes, then 00 00 00 01 09 F0 (AUD) and an SPS.
        val pes = bytes(0, 0, 1, 0xE0, 0, 0, 0x80, 0x80, 5, 0x21, 0, 1, 0, 1, 0, 0, 0, 1, 0x09, 0xF0, 0, 0, 0, 1, 0x67)
        assertTrue(H264Aud.contains(pes, 0, pes.size))
    }

    @Test
    fun streamWithoutDelimiterIsNotMistakenForOne() {
        // SPS, PPS and IDR slice only; the PES start code 00 00 01 E0 must not count as an AUD.
        val pes = bytes(0, 0, 1, 0xE0, 0, 0, 0x80, 0x80, 5, 0x21, 0, 1, 0, 1, 0, 0, 0, 1, 0x67, 0x64, 0, 0, 1, 0x68, 0xEE, 0, 0, 1, 0x65, 0x88)
        assertFalse(H264Aud.contains(pes, 0, pes.size))
    }

    @Test
    fun scanRespectsBoundsAndTruncatedStartCodes() {
        val data = bytes(0, 0, 1, 9, 0, 0, 1)
        assertFalse("AUD before the scanned range", H264Aud.contains(data, 1, data.size))
        assertFalse("start code cut off at the limit", H264Aud.contains(data, 0, 3))
        assertTrue(H264Aud.contains(data, 0, 4))
    }

    @Test
    fun liveTsUsesSliceDetectionOnlyForStreamsWithoutDelimiters() {
        val factory = ui("PlaybackFactory.kt")
        assertTrue(factory.contains("LiveTsExtractorsFactory("))
        assertFalse("TS flags must not force slice-based access unit detection for every stream",
            factory.contains("DefaultExtractorsFactory().setTsExtractorFlags("))
        val ts = ui("LiveTsExtractors.kt")
        assertTrue(ts.contains("tsFlags and DefaultTsPayloadReaderFactory.FLAG_DETECT_ACCESS_UNITS.inv()"))
        assertTrue(ts.contains("(if (aud) withAud else withoutAud)"))
        assertTrue("subtitle configuration must still reach the TS extractor", ts.contains("subtitleParserFactory,"))
    }
}
