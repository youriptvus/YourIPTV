package com.youriptv.app.ui

import android.content.Context
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import com.youriptv.app.R
import com.youriptv.app.data.Http
import org.json.JSONObject

/**
 * Brand surface resolver with server-first precedence.
 *
 * Resolution order for the display name and logo:
 *   1. a remote brand descriptor fetched over HTTPS, if [REMOTE_BRAND_ENDPOINT] is configured;
 *   2. the value bundled in the signed APK (strings.xml / mipmap).
 *
 * Serving branding from a server you control means that swapping files in res/ does not change what
 * users see: to rebrand, an attacker must also patch this code at runtime and defeat the signing check.
 * This is the branding-specific layer; the app-wide anchor is the signing-certificate check.
 *
 * Disabled by default (empty endpoint) so the app never contacts the network until you point it at
 * your own HTTPS endpoint. Networking reuses the app's [Http] helper, so no extra dependency is added.
 *
 * Expected server response (application/json):
 *   { "name": "YourIPTV", "logo": "https://.../logo.png" }
 */
object BrandAssets {
    /** Your HTTPS brand descriptor, e.g. "https://api.youriptv.example/brand". Empty = bundled values only. */
    const val REMOTE_BRAND_ENDPOINT = ""

    data class Brand(val name: String, val logoUrl: String)

    @Volatile private var cached: Brand? = null

    /** The brand display name to show: the server value when fetched, otherwise the bundled app name. */
    fun displayName(context: Context): String = cached?.name ?: context.getString(R.string.app_name)

    /**
     * Fetch and cache the remote descriptor once. Call off the main thread (e.g. on first launch).
     * Returns false when remote branding is disabled or the server is unreachable, leaving the bundled
     * values in effect.
     */
    fun refreshFromServer(): Boolean {
        if (REMOTE_BRAND_ENDPOINT.isBlank()) return false
        return try {
            val body = Http.open(REMOTE_BRAND_ENDPOINT, timeoutMs = 8_000).use { it.readBytes().toString(Charsets.UTF_8) }
            val json = JSONObject(body)
            val name = json.optString("name").ifBlank { return false }
            cached = Brand(name, json.optString("logo"))
            true
        } catch (_: Throwable) {
            false
        }
    }

    /** The server-defined logo bitmap, or null when remote branding is disabled/unavailable (fall back to a bundled drawable). */
    fun loadLogo(maxPx: Int = 256): Bitmap? {
        val url = cached?.logoUrl.orEmpty()
        if (url.isBlank()) return null
        return try {
            val bytes = Http.open(url, timeoutMs = 10_000).use { it.readBytes() }
            val bounds = BitmapFactory.Options().apply { inJustDecodeBounds = true }
            BitmapFactory.decodeByteArray(bytes, 0, bytes.size, bounds)
            var sample = 1
            while (bounds.outWidth / (sample * 2) >= maxPx) sample *= 2
            BitmapFactory.decodeByteArray(bytes, 0, bytes.size, BitmapFactory.Options().apply { inSampleSize = sample })
        } catch (_: Throwable) {
            null
        }
    }
}
