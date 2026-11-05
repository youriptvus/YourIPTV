package com.youriptv.app.data

import java.io.BufferedInputStream
import java.io.IOException
import java.io.InputStream
import java.net.HttpURLConnection
import java.net.URL
import java.util.zip.GZIPInputStream

class HttpException(val code: Int, message: String) : IOException(message)

object Http {
    const val DEFAULT_USER_AGENT = "YourIPTV/0.1 (Android)"

    /** The app-wide "Default user agent" setting, used when a playlist has no user agent of its own. */
    @Volatile var fallbackUserAgent: () -> String = { "" }

    fun effectiveUserAgent(userAgent: String): String =
        userAgent.ifBlank { fallbackUserAgent() }.ifBlank { DEFAULT_USER_AGENT }

    fun open(url: String, userAgent: String = "", timeoutMs: Int = 30_000): InputStream {
        var current = url
        repeat(6) {
            val conn = URL(current).openConnection() as HttpURLConnection
            conn.connectTimeout = timeoutMs
            conn.readTimeout = timeoutMs * 2
            conn.instanceFollowRedirects = false
            conn.setRequestProperty("User-Agent", effectiveUserAgent(userAgent))
            conn.setRequestProperty("Accept-Encoding", "gzip")
            val code = conn.responseCode
            if (code in 300..399) {
                val loc = conn.getHeaderField("Location") ?: throw HttpException(code, "Redirect without location")
                current = URL(URL(current), loc).toString()
                conn.disconnect()
                return@repeat
            }
            if (code !in 200..299) {
                conn.disconnect()
                throw HttpException(code, describe(code))
            }
            val raw = BufferedInputStream(conn.inputStream, 64 * 1024)
            return maybeGunzip(raw, conn.contentEncoding)
        }
        throw IOException("Too many redirects")
    }

    fun readText(url: String, userAgent: String = ""): String =
        open(url, userAgent).use { it.readBytes().toString(Charsets.UTF_8) }

    /** Transparently decompresses gzip content even when the server does not label it. */
    fun maybeGunzip(input: InputStream, encoding: String?): InputStream {
        val buffered = if (input.markSupported()) input else BufferedInputStream(input)
        buffered.mark(2)
        val b1 = buffered.read()
        val b2 = buffered.read()
        buffered.reset()
        val gz = encoding.equals("gzip", true) || (b1 == 0x1f && b2 == 0x8b)
        return if (gz) BufferedInputStream(GZIPInputStream(buffered, 64 * 1024), 64 * 1024) else buffered
    }

    fun describe(code: Int): String = when (code) {
        401, 403 -> "The provider rejected the request (HTTP $code). Check the username/password, " +
            "account status, or whether the provider's connection limit is already in use."
        404 -> "The provider returned 'not found' (HTTP 404). Check the URL."
        429 -> "The provider is rate limiting requests (HTTP 429). Try again later."
        in 500..599 -> "The provider server reported an error (HTTP $code)."
        else -> "Unexpected HTTP $code from provider."
    }
}
