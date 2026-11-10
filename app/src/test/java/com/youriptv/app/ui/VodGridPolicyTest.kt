package com.youriptv.app.ui

import com.youriptv.app.data.ItemKind
import com.youriptv.app.data.MediaItem
import org.junit.Assert.*
import org.junit.Test
import kotlin.math.roundToInt

class VodGridPolicyTest {
    @Test fun responsiveColumnsKeepPostersReadable() {
        assertEquals(2, VodGridPolicy.columns(320))
        assertEquals(4, VodGridPolicy.columns(640))
        assertEquals(8, VodGridPolicy.columns(1280))
        assertEquals(8, VodGridPolicy.columns(3840))
        assertEquals(8, VodGridPolicy.columns(944, television = true))
        assertEquals(6, VodGridPolicy.columns(944, television = false))
    }

    @Test fun recordedLandscapePhoneCanShowOneCompletePosterAndCaption() {
        // Samsung SM-S166V / API36: 1080 physical height, density450, old grid viewport389px.
        val density = 450f / 160f
        val header = VodGridPolicy.header(360, television = false, portrait = false)
        assertTrue(header.compact)
        assertEquals(56, header.heightDp)
        assertFalse(header.showMetadata) // Count remains in the page status, leaving one readable selected-title line.
        assertFalse(header.showHint)
        val newViewport = 389 + ((156 - header.heightDp) * density).roundToInt() - (8 * density).roundToInt()
        val sizing = VodGridPolicy.posterSizing(newViewport, density, captionLineHeightPx = 48)
        val image = VodGridPolicy.posterImageHeight(437, sizing.maxImageHeightPx)
        val outside = 2 * (4 * density).roundToInt() + 2 * (5 * density).roundToInt()
        assertTrue("The previous natural image alone did not fit", VodGridPolicy.posterImageHeight(437, Int.MAX_VALUE) > 389)
        assertEquals(2, sizing.captionLines)
        assertTrue("Image and readable caption must fit together", image + sizing.captionHeightPx + outside <= newViewport)
        assertTrue("Keep substantial artwork after reserving its caption", image > (140 * density).roundToInt())
    }

    @Test fun normalTelevisionKeepsFullHeaderEightColumnsAndNaturalPosterHeight() {
        val header = VodGridPolicy.header(524, television = true, portrait = false)
        assertFalse(header.compact)
        assertEquals(156, header.heightDp)
        assertTrue(header.showHint)
        assertEquals(8, VodGridPolicy.columns(944, television = true))
        val sizing = VodGridPolicy.posterSizing(568, density = 2f, captionLineHeightPx = 34)
        // A 118dp cell has 100dp of image width after the unchanged card margins/padding.
        assertEquals(VodGridPolicy.posterImageHeight(200, Int.MAX_VALUE), VodGridPolicy.posterImageHeight(200, sizing.maxImageHeightPx))
        assertEquals(80, sizing.captionHeightPx)
        assertEquals(2, sizing.captionLines)
    }

    @Test fun tallPortraitWindowKeepsArtworkAndAdaptiveTwoColumnGrid() {
        val header = VodGridPolicy.header(650, television = false, portrait = true)
        assertFalse(header.compact)
        assertEquals(116, header.heightDp)
        assertFalse(header.showHint)
        assertEquals(2, VodGridPolicy.columns(352, television = false))
        val sizing = VodGridPolicy.posterSizing(450, 1f, 18)
        assertEquals(VodGridPolicy.posterImageHeight(158, Int.MAX_VALUE), VodGridPolicy.posterImageHeight(158, sizing.maxImageHeightPx))
    }

    @Test fun smallWindowUsesOneReadableCaptionLineWithoutNegativeArtworkBounds() {
        val header = VodGridPolicy.header(180, television = false, portrait = false)
        assertTrue(header.compact)
        assertEquals(36, header.heightDp)
        assertFalse(header.showMetadata) // The separate status count remains outside the compact title bar.
        val sizing = VodGridPolicy.posterSizing(95, 1.25f, 22)
        assertEquals(1, sizing.captionLines)
        assertTrue(sizing.captionHeightPx >= 30)
        assertTrue(VodGridPolicy.posterImageHeight(300, sizing.maxImageHeightPx) + sizing.captionHeightPx + 22 <= 95)
        assertTrue(VodGridPolicy.posterSizing(0, 0f, 0).maxImageHeightPx > 0)
    }

    @Test fun captionMetricsAndFractionalDensityAreReservedBeforeArtwork() {
        val density = 2.8125f
        val sizing = VodGridPolicy.posterSizing(650, density, captionLineHeightPx = 80, captionFontPaddingPx = 12)
        val outside = 2 * (4 * density).roundToInt() + 2 * (5 * density).roundToInt()
        assertTrue(sizing.captionHeightPx >= 2 * 80 + 12 + (4 * density).roundToInt())
        assertTrue(sizing.maxImageHeightPx + sizing.captionHeightPx + outside <= 650)
        assertEquals(146, VodGridPolicy.posterImageHeight(100, 500))
        assertEquals(80, VodGridPolicy.posterImageHeight(100, 80))
    }

    @Test fun compactHeaderGrowsForEnlargedTitleMetricsWithoutChangingFullTvHeader() {
        val density = 2.8125f
        val compact = VodGridPolicy.header(360, television = false, portrait = false)
        val requiredTitleHeight = 200 + 24 + 2 * (6 * density).roundToInt()
        assertFalse(compact.showMetadata)
        assertTrue(VodGridPolicy.headerHeightPx(compact, density, titleLineHeightPx = 200, titleFontPaddingPx = 24) >= requiredTitleHeight)
        val tiny = VodGridPolicy.header(180, television = false, portrait = false)
        assertTrue(VodGridPolicy.headerHeightPx(tiny, density, titleLineHeightPx = 130, titleFontPaddingPx = 16) >= 130 + 16 + 2 * (6 * density).roundToInt())
        val television = VodGridPolicy.header(524, television = true, portrait = false)
        assertEquals(312, VodGridPolicy.headerHeightPx(television, 2f, titleLineHeightPx = 300, titleFontPaddingPx = 40))
    }

    @Test fun horizontalEdgesDoNotWrapAndVerticalMovementKeepsColumn() {
        assertEquals(7, VodGridPolicy.move(7, 17, 8, horizontal = 1))
        assertEquals(8, VodGridPolicy.move(8, 17, 8, horizontal = -1))
        assertEquals(14, VodGridPolicy.move(6, 17, 8, vertical = 1))
        assertEquals(16, VodGridPolicy.move(15, 17, 8, vertical = 1))
        assertEquals(7, VodGridPolicy.move(15, 17, 8, vertical = -1))
        assertEquals(-1, VodGridPolicy.move(0, 0, 8, vertical = 1))
    }

    @Test fun restoresIdentityAfterReorderAndClampsDeletedSelection() {
        val a = item(1, "https://example.invalid/movie/account/private/1")
        val b = item(1, "https://example.invalid/movie/account/private/2")
        assertEquals(1, VodGridPolicy.restore(listOf(b, a), VodGridPolicy.key(a), 0))
        assertEquals(0, VodGridPolicy.restore(listOf(b), VodGridPolicy.key(a), 7))
        assertEquals(-1, VodGridPolicy.restore(emptyList(), VodGridPolicy.key(a), 7))
        assertFalse(VodGridPolicy.key(a).contains("private"))
        assertNotEquals(VodGridPolicy.key(a), VodGridPolicy.key(a.copy(playlistId = 2)))
    }

    private fun item(pid: Long, url: String) = MediaItem(playlistId = pid, kind = ItemKind.MOVIE, name = "Sample", group = "Movies", url = url)
}
