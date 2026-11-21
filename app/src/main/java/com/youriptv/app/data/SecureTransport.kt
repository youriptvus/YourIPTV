package com.youriptv.app.data

import java.io.IOException
import java.net.URI
import java.net.URL

/** User-selected HTTP/HTTPS sources retain their scheme; HTTPS is never silently downgraded. */
object SecureTransport {
    const val NETWORK_URL_REQUIRED = "Enter a valid HTTP or HTTPS address from your provider."
    const val SECURE_REDIRECT_REQUIRED = "The provider redirected an HTTPS connection to HTTP. Use the exact address supplied by your provider."
    private val scheme = Regex("^([A-Za-z][A-Za-z0-9+.-]*):")

    fun isHttpsUrl(value: String): Boolean = runCatching {
        val uri = URI(value)
        uri.scheme.equals("https", true) && !uri.host.isNullOrBlank()
    }.getOrDefault(false)

    fun isNetworkUrl(value: String): Boolean = runCatching {
        val uri = URI(value)
        uri.scheme.lowercase() in setOf("http", "https") && !uri.host.isNullOrBlank() &&
            uri.port in -1..65535 && value.none { it.code < 32 }
    }.getOrDefault(false)

    fun requireNetworkUrl(value: String) {
        if (!isNetworkUrl(value)) throw InsecureTransportException(NETWORK_URL_REQUIRED)
    }

    fun redirect(from: String, location: String): String {
        requireNetworkUrl(from)
        val previous = URL(from)
        val next = URL(previous, location)
        if (previous.protocol.equals("https", true) && !next.protocol.equals("https", true))
            throw InsecureTransportException(SECURE_REDIRECT_REQUIRED)
        requireNetworkUrl(next.toString())
        return next.toString()
    }

    private fun schemeOf(value: String): String? = scheme.find(value.trimStart())?.groupValues?.get(1)?.lowercase()

    fun canHandOffMedia(value: String): Boolean = isNetworkUrl(value) || schemeOf(value) in setOf("file", "content")

    fun canLoadMedia(value: String): Boolean = when (schemeOf(value)) {
        "http", "https" -> isNetworkUrl(value)
        "file", "content", "asset", "android.resource", "rawresource", "data" -> true
        null -> value.isNotBlank() && !value.trimStart().startsWith("//") && value.none { it.code < 32 }
        else -> false
    }
}

class InsecureTransportException(message: String) : IOException(message)
