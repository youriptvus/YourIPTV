package com.youriptv.app.updates

import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.currentCoroutineContext
import kotlinx.coroutines.ensureActive
import org.json.JSONException
import java.io.ByteArrayOutputStream
import java.io.File
import java.io.IOException
import java.net.HttpURLConnection
import java.net.URL
import java.security.MessageDigest

/** Primary-first transport shared by automatic and manual checks, with the same validation on both servers. */
internal class UpdateTransport(
    private val manifestUrl: String,
    private val backupManifestUrl: String,
    private val testOrigin: String = "",
    private val testBackupOrigin: String = "",
) {
    suspend fun latest(): UpdateRelease = firstAvailable(listOf(manifestUrl, backupManifestUrl)) { url ->
        val output = ByteArrayOutputStream()
        transfer(url, UpdateRelease.MAX_MANIFEST_BYTES) { bytes, length -> output.write(bytes, 0, length) }
        UpdateRelease.parse(output.toString(Charsets.UTF_8.name()), testOrigin, testBackupOrigin)
    }

    suspend fun download(
        release: UpdateRelease,
        partial: File,
        progress: (Int) -> Unit,
        verifyArchive: (File) -> Unit,
    ) {
        firstAvailable(release.downloadUrls(testOrigin, testBackupOrigin)) { url ->
            // A retry starts a new file and digest; partial bytes from the primary must never enter the mirror APK.
            val digest = MessageDigest.getInstance("SHA-256")
            var total = 0L
            progress(0)
            partial.outputStream().use { output ->
                transfer(url, release.size) { bytes, length ->
                    output.write(bytes, 0, length)
                    digest.update(bytes, 0, length)
                    total += length
                    progress((total * 100 / release.size).toInt())
                }
            }
            release.verifyDownload(total, digest.digest().joinToString("") { "%02x".format(it) })
            verifyArchive(partial)
        }
    }

    private suspend fun <T> firstAvailable(urls: List<String>, action: suspend (String) -> T): T {
        var failure: Exception? = null
        for (url in urls.filter { it.isNotEmpty() }.distinct()) {
            currentCoroutineContext().ensureActive()
            try {
                return action(url)
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                // HTTP, TLS, malformed metadata and failed integrity/package checks may use the mirror.
                // Programming errors and user cancellation must not cause extra requests.
                if (e !is IOException && e !is JSONException) throw e
                failure?.let { e.addSuppressed(it) }
                failure = e
            }
        }
        throw failure ?: UpdateException("No update server is configured.")
    }

    // Do not use the provider HTTP helpers: every redirect must remain on a trusted update host.
    private suspend fun transfer(url: String, limit: Long, write: (ByteArray, Int) -> Unit) {
        var current = url
        repeat(6) {
            currentCoroutineContext().ensureActive()
            if (!UpdateRelease.allowedNetworkUrl(current, testOrigin, testBackupOrigin)) {
                throw UpdateException("The update server redirected outside the official YourIPTV update servers.")
            }
            val connection = URL(current).openConnection() as HttpURLConnection
            try {
                connection.connectTimeout = 15_000
                connection.readTimeout = 15_000
                connection.instanceFollowRedirects = false
                connection.useCaches = false
                connection.setRequestProperty("User-Agent", "YourIPTV-Updater")
                val code = connection.responseCode
                if (code in setOf(301, 302, 303, 307, 308)) {
                    val location = connection.getHeaderField("Location") ?: throw UpdateException("Missing update redirect.")
                    current = URL(URL(current), location).toString()
                    return@repeat
                }
                if (code == 404) throw UpdateException("No update information is published yet.")
                if (code !in 200..299) throw UpdateException("The update server returned HTTP $code.")
                val contentLength = connection.getHeaderField("Content-Length")?.toLongOrNull() ?: -1L
                if (contentLength > limit) throw UpdateException("The update response is too large.")
                connection.inputStream.use { input ->
                    val bytes = ByteArray(64 * 1024)
                    var total = 0L
                    while (true) {
                        currentCoroutineContext().ensureActive()
                        val n = input.read(bytes)
                        if (n < 0) break
                        total += n
                        if (total > limit) throw UpdateException("The update response is too large.")
                        write(bytes, n)
                    }
                }
                return
            } finally {
                connection.disconnect()
            }
        }
        throw UpdateException("Too many update redirects.")
    }
}
