package com.youriptv.app.data

import java.io.EOFException
import java.io.IOException
import java.net.ConnectException
import java.net.HttpURLConnection
import java.net.NoRouteToHostException
import java.net.ProtocolException
import java.net.SocketException
import java.net.SocketTimeoutException
import java.net.URL
import java.net.UnknownHostException
import javax.net.ssl.SSLException

/**
 * Explains why a stream failed after the player gave up, so network blocks (ISP filters, VPN needs)
 * are not reported as provider problems or app defects.
 */
object StreamDiagnosis {

    enum class Kind {
        /** The server sends media; the failure is in playback on the device. */
        MEDIA_OK,
        /** DNS, connection or TLS failure before any HTTP answer. */
        NETWORK_UNREACHABLE,
        /** A web page came back instead of media (ISP/router "site blocked" pages, captive portals). */
        NETWORK_FILTER,
        /** HTTP 401/403/456/458/509: credentials, account state or connection limit. */
        ACCESS_DENIED,
        /** HTTP 400/404/410/416: the stream or archive segment does not exist. */
        NOT_AVAILABLE,
        /** HTTP 5xx or other unexpected responses. */
        PROVIDER_ERROR,
        /** The server accepted the connection, then closed or reset it without an HTTP answer. */
        CONNECTION_DROPPED,
    }

    data class Result(val kind: Kind, val host: String, val httpCode: Int = 0, val detail: String = "")

    private val DENIED = setOf(401, 403, 456, 458, 509)
    private val MISSING = setOf(400, 404, 410, 416)

    /** Pure classification of a probe outcome. */
    fun classify(httpCode: Int, contentType: String?, head: ByteArray, error: Throwable?, host: String): Result {
        if (error != null) {
            when (error) {
                is EOFException -> return Result(Kind.CONNECTION_DROPPED, host, 0, "the server closed the connection without answering")
                is ProtocolException -> return Result(Kind.CONNECTION_DROPPED, host, 0, "the server sent an invalid answer")
                is SocketException -> if (error !is ConnectException && error !is NoRouteToHostException)
                    return Result(Kind.CONNECTION_DROPPED, host, 0, "the connection was reset by the server or network")
            }
            val msg = error.message.orEmpty()
            if (msg.contains("unexpected end of stream", true) || msg.contains("connection reset", true))
                return Result(Kind.CONNECTION_DROPPED, host, 0, "the server closed the connection without answering")
            val detail = when (error) {
                is UnknownHostException -> "the server name could not be resolved"
                is SocketTimeoutException -> "the connection timed out"
                is ConnectException, is NoRouteToHostException -> "the connection was refused or has no route"
                is SSLException -> "a secure connection could not be established"
                else -> error.javaClass.simpleName
            }
            return Result(Kind.NETWORK_UNREACHABLE, host, 0, detail)
        }
        return when {
            httpCode in DENIED -> Result(Kind.ACCESS_DENIED, host, httpCode)
            httpCode in MISSING -> Result(Kind.NOT_AVAILABLE, host, httpCode)
            httpCode !in 200..299 -> Result(Kind.PROVIDER_ERROR, host, httpCode)
            looksLikeMedia(head) -> Result(Kind.MEDIA_OK, host, httpCode)
            looksLikeHtml(contentType, head) -> Result(Kind.NETWORK_FILTER, host, httpCode, pageTitle(head))
            head.isEmpty() -> Result(Kind.PROVIDER_ERROR, host, httpCode, "the server sent no data")
            else -> Result(Kind.MEDIA_OK, host, httpCode)
        }
    }

    fun looksLikeMedia(head: ByteArray): Boolean {
        if (head.isEmpty()) return false
        if (head[0] == 0x47.toByte() && (head.size <= 188 || head[188] == 0x47.toByte())) return true
        val text = String(head, 0, minOf(head.size, 16), Charsets.ISO_8859_1)
        if (text.startsWith("#EXTM3U")) return true
        if (head.size >= 8 && String(head, 4, 4, Charsets.ISO_8859_1) in setOf("ftyp", "moov", "styp")) return true
        return head.size >= 4 && head[0] == 0x1A.toByte() && head[1] == 0x45.toByte() && head[2] == 0xDF.toByte() && head[3] == 0xA3.toByte()
    }

    private fun looksLikeHtml(contentType: String?, head: ByteArray): Boolean {
        if (contentType?.contains("html", true) == true) return true
        val t = String(head, 0, minOf(head.size, 512), Charsets.UTF_8).trimStart().lowercase()
        return t.startsWith("<!doctype html") || t.startsWith("<html") || t.contains("<head") || t.contains("<body")
    }

    private fun pageTitle(head: ByteArray): String {
        val t = String(head, Charsets.UTF_8)
        return Regex("<title[^>]*>([^<]{1,80})</title>", RegexOption.IGNORE_CASE).find(t)?.groupValues?.get(1)?.trim().orEmpty()
    }

    /**
     * Requests [url] like the player does, following redirects, and reads only the first bytes.
     * Uses one short provider connection; call it only after playback has stopped.
     */
    fun probe(url: String, userAgent: String, timeoutMs: Int = 10_000): Result {
        var current = url
        var host = hostOf(url)
        try {
            repeat(6) {
                host = hostOf(current)
                val conn = URL(current).openConnection() as HttpURLConnection
                try {
                    conn.connectTimeout = timeoutMs
                    conn.readTimeout = timeoutMs
                    conn.instanceFollowRedirects = false
                    conn.setRequestProperty("User-Agent", Http.effectiveUserAgent(userAgent))
                    val code = conn.responseCode
                    if (code in 300..399) {
                        val loc = conn.getHeaderField("Location") ?: return classify(code, null, ByteArray(0), null, host)
                        current = URL(URL(current), loc).toString()
                        return@repeat
                    }
                    if (code !in 200..299) return classify(code, conn.contentType, ByteArray(0), null, host)
                    val head = readHead(conn)
                    return classify(code, conn.contentType, head, null, host)
                } finally {
                    conn.disconnect()
                }
            }
            return Result(Kind.PROVIDER_ERROR, host, 0, "too many redirects")
        } catch (e: IOException) {
            return classify(0, null, ByteArray(0), e, host)
        }
    }

    private fun readHead(conn: HttpURLConnection): ByteArray {
        val buf = ByteArray(2048)
        var n = 0
        conn.inputStream.use { input ->
            while (n < buf.size) {
                val k = input.read(buf, n, buf.size - n)
                if (k <= 0) break
                n += k
            }
        }
        return buf.copyOf(n)
    }

    fun hostOf(url: String): String = runCatching { URL(url).host }.getOrDefault("")

    /** User-facing explanation. [isArchive] selects archive (catch-up) wording. */
    fun message(r: Result, isArchive: Boolean): String {
        val server = if (isArchive) "the provider's archive server" else "the stream server"
        val where = if (r.host.isNotBlank()) " (${r.host})" else ""
        return when (r.kind) {
            Kind.NETWORK_FILTER ->
                "Your network blocked $server$where: a web page" +
                    (if (r.detail.isNotBlank()) " (\"${r.detail}\")" else "") +
                    " was returned instead of video.\nThis is a network or ISP filter, not a YourIPTV or provider error. " +
                    "Allow the server in your router/ISP security settings, or connect this device through a VPN."
            Kind.NETWORK_UNREACHABLE ->
                "Could not reach $server$where: ${r.detail}.\nCheck this device's network. If other channels play, " +
                    "a network filter may be blocking this server; a VPN on this device may be required."
            Kind.ACCESS_DENIED ->
                "The provider refused the request (HTTP ${r.httpCode}). Check the account status or credentials, " +
                    "or whether the provider's connection limit is in use by another device, app or recording."
            Kind.NOT_AVAILABLE ->
                if (isArchive) "The provider has no archive for this programme (HTTP ${r.httpCode}). " +
                    "It may be outside the archive window or was not recorded by the provider."
                else "This stream is not available from the provider (HTTP ${r.httpCode})."
            Kind.PROVIDER_ERROR ->
                "The provider's server reported a problem" +
                    (if (r.httpCode != 0) " (HTTP ${r.httpCode})" else "") +
                    (if (r.detail.isNotBlank()) ": ${r.detail}" else "") + ". Try again later."
            Kind.CONNECTION_DROPPED ->
                "Reached $server$where, but ${r.detail}.\nThe channel may be offline, the provider's connection " +
                    "limit may still be in use, or a network filter may be interrupting the stream (a VPN on this " +
                    "device may be required)."
            Kind.MEDIA_OK ->
                "The server$where is sending video, so the network and account are fine; " +
                    "playback failed on this device."
        }
    }
}
