package com.youriptv.app.ui

import java.io.ByteArrayOutputStream
import java.io.IOException
import java.io.InputStream
import java.net.URI

/** Memory and input limits shared by logo, poster, backdrop and episode artwork requests. */
internal object ArtworkPolicy {
    const val MAX_DOWNLOAD_BYTES = 4 * 1024 * 1024
    const val MAX_DECODE_PIXELS = 1_048_576L
    const val MAX_SOURCE_PIXELS = 64_000_000L
    const val MAX_SOURCE_EDGE = 32_768
    const val MAX_EDGE = 1024
    const val CACHE_BYTES = 16 * 1024 * 1024

    /** Distinct size buckets prevent a cached channel logo from becoming a blurry full-size poster. */
    fun request(url: String, maxPx: Int): ArtworkRequest? {
        if (url.isBlank() || url.length > 8192 || url.any { it.isISOControl() || it.isWhitespace() }) return null
        val uri = try { URI(url) } catch (_: Exception) { return null }
        if (!(uri.scheme.equals("http", true) || uri.scheme.equals("https", true)) || uri.host.isNullOrEmpty()) return null
        val edge = when {
            maxPx <= 160 -> 160
            maxPx <= 320 -> 320
            maxPx <= 640 -> 640
            else -> MAX_EDGE
        }
        return ArtworkRequest(url, edge)
    }

    /** Reject decompression bombs before allocating pixels and keep the intermediate decode bounded. */
    fun decodePlan(width: Int, height: Int, maxPx: Int): ArtworkDecodePlan? {
        if (width <= 0 || height <= 0 || width > MAX_SOURCE_EDGE || height > MAX_SOURCE_EDGE ||
            width.toLong() * height > MAX_SOURCE_PIXELS) return null
        val edge = maxPx.coerceIn(1, MAX_EDGE)
        var sample = 1
        fun sampled(n: Int) = (n.toLong() + sample - 1) / sample
        while (sampled(width) * sampled(height) > MAX_DECODE_PIXELS ||
            sampled(width) > edge * 2L || sampled(height) > edge * 2L) sample *= 2
        val decodedWidth = sampled(width).toInt()
        val decodedHeight = sampled(height).toInt()
        val scale = minOf(1.0, edge.toDouble() / maxOf(decodedWidth, decodedHeight))
        return ArtworkDecodePlan(sample, maxOf(1, (decodedWidth * scale).toInt()), maxOf(1, (decodedHeight * scale).toInt()))
    }

    /** The limit applies after HTTP decompression, including when Content-Length is missing or false. */
    fun readBytes(input: InputStream, limit: Int = MAX_DOWNLOAD_BYTES): ByteArray {
        require(limit > 0)
        val output = ByteArrayOutputStream(minOf(limit, 32 * 1024))
        val buffer = ByteArray(minOf(limit, 16 * 1024))
        while (true) {
            val count = input.read(buffer, 0, minOf(buffer.size, limit - output.size() + 1))
            if (count < 0) return output.toByteArray()
            if (count == 0) {
                val next = input.read()
                if (next < 0) return output.toByteArray()
                if (output.size() == limit) throw IOException("Artwork exceeds the download limit")
                output.write(next)
            } else {
                if (count > limit - output.size()) throw IOException("Artwork exceeds the download limit")
                output.write(buffer, 0, count)
            }
        }
    }
}

internal data class ArtworkRequest(val url: String, val maxPx: Int)
internal data class ArtworkDecodePlan(val sample: Int, val width: Int, val height: Int)
