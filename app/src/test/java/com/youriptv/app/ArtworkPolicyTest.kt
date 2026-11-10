package com.youriptv.app

import com.youriptv.app.ui.ArtworkPolicy
import java.io.ByteArrayInputStream
import java.io.IOException
import java.io.InputStream
import org.junit.Assert.*
import org.junit.Test

class ArtworkPolicyTest {
    @Test fun logosAndPostersDoNotShareAnUndersizedCacheEntry() {
        val url = "https://images.example.test/cover.jpg"
        assertNotEquals(ArtworkPolicy.request(url, 160), ArtworkPolicy.request(url, 360))
        assertEquals(ArtworkPolicy.request(url, 360), ArtworkPolicy.request(url, 600))
        assertEquals(1024, ArtworkPolicy.request(url, Int.MAX_VALUE)!!.maxPx)
    }

    @Test fun onlyWellFormedHttpArtworkCanBeRequested() {
        for (url in listOf("", "file:///data/private.jpg", "content://images/1", "data:image/png;base64,x",
            "ftp://images.example.test/a", "https:///missing-host.jpg", "https://images.example.test/a\n.jpg")) {
            assertNull(url, ArtworkPolicy.request(url, 160))
        }
        assertNotNull(ArtworkPolicy.request("HTTP://images.example.test/cover.jpg?size=big", 320))
    }

    @Test fun invalidAndDecompressionBombDimensionsAreRejectedBeforeDecode() {
        for ((w, h) in listOf(-1 to 100, 100 to 0, Int.MAX_VALUE to Int.MAX_VALUE, 9000 to 9000,
            32769 to 1)) assertNull(ArtworkPolicy.decodePlan(w, h, 640))
    }

    @Test fun portraitLandscapeAndExtremeAspectArtStayWithinPixelAndEdgeBudgets() {
        for ((w, h) in listOf(8000 to 8000, 3000 to 4500, 3840 to 2160, 32768 to 1, 1 to 32768, 8 to 8)) {
            for (edge in listOf(160, 320, 640, 1024)) {
                val plan = ArtworkPolicy.decodePlan(w, h, edge)!!
                val decodedW = (w.toLong() + plan.sample - 1) / plan.sample
                val decodedH = (h.toLong() + plan.sample - 1) / plan.sample
                assertTrue(decodedW * decodedH <= ArtworkPolicy.MAX_DECODE_PIXELS)
                assertTrue(plan.width in 1..edge && plan.height in 1..edge)
                assertTrue(plan.sample > 0 && plan.sample and (plan.sample - 1) == 0)
            }
        }
    }

    @Test fun smallArtworkIsNotUpscaled() {
        val plan = ArtworkPolicy.decodePlan(60, 90, 640)!!
        assertEquals(1, plan.sample)
        assertEquals(60, plan.width)
        assertEquals(90, plan.height)
    }

    @Test fun byteLimitAcceptsExactLengthAndRejectsOversizeContentWithoutLengthHeader() {
        assertArrayEquals(byteArrayOf(1, 2, 3), ArtworkPolicy.readBytes(ByteArrayInputStream(byteArrayOf(1, 2, 3)), 3))
        try {
            ArtworkPolicy.readBytes(ByteArrayInputStream(ByteArray(4)), 3)
            fail("Oversize artwork must not be retained")
        } catch (_: IOException) { }
    }

    @Test fun aZeroLengthBulkReadCannotCauseAnInfiniteLoopOrAvoidTheByteLimit() {
        val oddStream = object : InputStream() {
            var remaining = 4
            override fun read(b: ByteArray, off: Int, len: Int) = if (remaining > 0) 0 else -1
            override fun read() = if (remaining-- > 0) 7 else -1
        }
        try {
            ArtworkPolicy.readBytes(oddStream, 3)
            fail("Zero-length bulk reads still count bytes")
        } catch (_: IOException) { }
    }
}
