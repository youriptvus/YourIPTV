package com.youriptv.app.ui

import com.youriptv.app.data.MediaItem
import java.security.MessageDigest
import kotlin.math.max
import kotlin.math.roundToInt

/** Layout and navigation rules shared by touch and remote poster browsing. */
object VodGridPolicy {
    fun columns(widthDp: Int, television: Boolean = false): Int = (widthDp / if (television) 116 else 148).coerceIn(2, 8)

    data class HeaderPresentation(val compact: Boolean, val heightDp: Int, val showMetadata: Boolean, val showHint: Boolean)

    /** The measured content column excludes navigation rows/window chrome; the display's long side is irrelevant. */
    fun header(usableColumnHeightDp: Int, television: Boolean, portrait: Boolean): HeaderPresentation {
        val compact = usableColumnHeightDp < if (television) 360 else 480
        val tiny = compact && usableColumnHeightDp < 220
        return HeaderPresentation(compact, if (compact) { if (tiny) 36 else 56 } else if (portrait) 116 else 156,
            showMetadata = !compact, showHint = television && !compact)
    }

    fun headerHeightPx(presentation: HeaderPresentation, density: Float, titleLineHeightPx: Int, titleFontPaddingPx: Int): Int {
        val scale = density.takeIf { it.isFinite() && it > 0f } ?: 1f
        val nominal = (presentation.heightDp * scale).roundToInt()
        if (!presentation.compact) return nominal
        return max(nominal, titleLineHeightPx.coerceAtLeast(1) + titleFontPaddingPx.coerceAtLeast(0) + 2 * (6 * scale).roundToInt())
    }

    data class PosterSizing(val maxImageHeightPx: Int, val captionHeightPx: Int, val captionLines: Int)

    /**
     * Reserve the real caption and the poster layout's 4dp margins/5dp padding before sizing artwork.
     * The favorite indicator overlays the artwork, so it never changes a row's measured height.
     */
    fun posterSizing(viewportHeightPx: Int, density: Float, captionLineHeightPx: Int, captionFontPaddingPx: Int = 0): PosterSizing {
        val scale = density.takeIf { it.isFinite() && it > 0f } ?: 1f
        fun dp(value: Int) = (value * scale).roundToInt()
        val outside = 2 * dp(4) + 2 * dp(5)
        val line = captionLineHeightPx.coerceAtLeast(1)
        val fontPadding = captionFontPaddingPx.coerceAtLeast(0)
        val twoLines = max(dp(40), line * 2 + fontPadding + dp(4))
        val oneLine = max(dp(24), line + fontPadding + dp(4))
        val lines = if (viewportHeightPx >= outside + twoLines + dp(72)) 2 else 1
        val caption = if (lines == 2) twoLines else oneLine
        // Two physical pixels allow for XML dimension/RecyclerView rounding at fractional densities.
        return PosterSizing((viewportHeightPx - outside - caption - 2).coerceAtLeast(1), caption, lines)
    }

    fun posterImageHeight(widthPx: Int, maxHeightPx: Int): Int =
        minOf((widthPx.coerceAtLeast(0) * 1.46f).toInt().coerceAtLeast(1), maxHeightPx.coerceAtLeast(1))

    /** A row edge never wraps into the next row. UP/DOWN retain the column where possible. */
    fun move(position: Int, count: Int, columns: Int, horizontal: Int = 0, vertical: Int = 0): Int {
        if (count <= 0) return -1
        val at = position.coerceIn(0, count - 1)
        val span = columns.coerceAtLeast(1)
        if (horizontal < 0) return if (at % span > 0) at - 1 else at
        if (horizontal > 0) return if (at % span < span - 1 && at + 1 < count) at + 1 else at
        if (vertical < 0) return if (at >= span) at - span else at
        if (vertical > 0) return if (at / span < (count - 1) / span) (at + span).coerceAtMost(count - 1) else at
        return at
    }

    /** Saved view state must not contain credential-bearing stream URLs. */
    fun key(item: MediaItem): String = MessageDigest.getInstance("SHA-256")
        .digest("${item.playlistId}\u0000${item.kind}\u0000${item.url}".toByteArray(Charsets.UTF_8))
        .let { bytes ->
            val hex = "0123456789abcdef"
            buildString(bytes.size * 2) { for (b in bytes) { val n = b.toInt() and 255; append(hex[n ushr 4]); append(hex[n and 15]) } }
        }

    fun restore(items: List<MediaItem>, key: String?, fallback: Int): Int {
        if (items.isEmpty()) return -1
        if (key != null && fallback in items.indices && key(items[fallback]) == key) return fallback
        return key?.let { selected -> items.indexOfFirst { key(it) == selected }.takeIf { it >= 0 } }
            ?: fallback.coerceIn(0, items.lastIndex)
    }
}
