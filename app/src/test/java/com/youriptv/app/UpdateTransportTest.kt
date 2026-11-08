package com.youriptv.app

import com.sun.net.httpserver.HttpServer
import com.youriptv.app.updates.UpdateException
import com.youriptv.app.updates.UpdateRelease
import com.youriptv.app.updates.UpdateTransport
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.runBlocking
import org.junit.After
import org.junit.Assert.*
import org.junit.Before
import org.junit.Test
import java.io.File
import java.net.InetSocketAddress
import java.nio.file.Files
import java.security.MessageDigest
import java.util.Collections
import java.util.concurrent.ConcurrentHashMap

/** Real loopback HTTP exercises retries and redirect policy without contacting public update servers. */
class UpdateTransportTest {
    private data class Response(val status: Int = 200, val body: ByteArray = byteArrayOf(), val location: String? = null)
    private lateinit var server: HttpServer
    private lateinit var folder: File
    private lateinit var origin: String
    private val responses = ConcurrentHashMap<String, Response>()
    private val requests = Collections.synchronizedList(mutableListOf<String>())
    private val apk = "verified-apk-content".toByteArray()
    private val primaryManifest = "/primary/update.json"
    private val backupManifest = "/backup/update.json"
    private val primaryApk = "/primary/YourIPTV.apk"
    private val backupApk = "/backup/YourIPTV-0.5.6-release.apk"

    @Before
    fun startServer() {
        folder = Files.createTempDirectory("youriptv-update-test").toFile()
        server = HttpServer.create(InetSocketAddress("127.0.0.1", 0), 0)
        server.createContext("/") { exchange ->
            val path = exchange.requestURI.path
            requests.add(path)
            val response = responses[path] ?: Response(404)
            response.location?.let { exchange.responseHeaders.set("Location", it) }
            exchange.sendResponseHeaders(response.status, if (response.body.isEmpty()) -1 else response.body.size.toLong())
            exchange.responseBody.use { it.write(response.body) }
            exchange.close()
        }
        server.start()
        origin = "http://127.0.0.1:${server.address.port}"
    }

    @After
    fun stopServer() {
        server.stop(0)
        folder.deleteRecursively()
    }

    private fun transport(backup: Boolean = true) = UpdateTransport(
        "$origin$primaryManifest", if (backup) "$origin$backupManifest" else "",
        "$origin/primary/", if (backup) "$origin/backup/" else "",
    )

    private fun release(path: String = primaryApk) = UpdateRelease(
        30, "0.5.6", "$origin$path", apk.size.toLong(),
        MessageDigest.getInstance("SHA-256").digest(apk).joinToString("") { "%02x".format(it) }, 29,
    )

    private fun publish(path: String, release: UpdateRelease = release()) {
        responses[path] = Response(body = release.encode().toByteArray())
    }

    @Test
    fun healthyPrimaryChecksAndDownloadsWithoutRequestingMirror() = runBlocking {
        publish(primaryManifest)
        responses[primaryApk] = Response(body = apk)
        val update = transport().latest()
        var verified = 0
        val file = folder.resolve("download.part")
        transport().download(update, file, {}) { verified++; assertArrayEquals(apk, it.readBytes()) }
        assertEquals(1, verified)
        assertEquals(listOf(primaryManifest, primaryApk), requests)
    }

    @Test
    fun unavailableOrInvalidPrimaryMetadataFallsBackToWebsiteManifest() = runBlocking {
        publish(backupManifest, release(backupApk))
        val failures = listOf(
            Response(503), Response(404), Response(body = "not JSON".toByteArray()),
            Response(body = release().encode().replace(release().sha256, "bad-hash").toByteArray()),
            Response(body = ByteArray((UpdateRelease.MAX_MANIFEST_BYTES + 1).toInt())),
            Response(302, location = "$origin/outside-the-allowed-origins"),
            Response(302, location = "$origin$primaryManifest"),
        )
        for (failure in failures) {
            requests.clear()
            responses[primaryManifest] = failure
            assertEquals(release(backupApk), transport().latest())
            assertEquals(primaryManifest, requests.first())
            assertEquals(backupManifest, requests.last())
            assertFalse(requests.contains("/outside-the-allowed-origins"))
        }
    }

    @Test
    fun trustedRelativeRedirectStillWorksWithoutFallback() = runBlocking {
        responses[primaryManifest] = Response(302, location = "release.json")
        publish("/primary/release.json")
        assertEquals(release(), transport().latest())
        assertEquals(listOf(primaryManifest, "/primary/release.json"), requests)
    }

    @Test
    fun primaryTestWithoutBackupNeverTouchesAnUnconfiguredMirror() {
        responses[primaryManifest] = Response(503)
        val failure = assertThrows(UpdateException::class.java) { runBlocking { transport(false).latest() } }
        assertTrue(failure.message!!.contains("503"))
        assertEquals(listOf(primaryManifest), requests)
        assertEquals(listOf(release().url), release().downloadUrls("$origin/primary/"))
    }

    @Test
    fun bothServerFailuresPreserveUsefulCauses() {
        responses[primaryManifest] = Response(503)
        responses[backupManifest] = Response(404)
        val failure = assertThrows(UpdateException::class.java) { runBlocking { transport().latest() } }
        assertTrue(failure.message!!.contains("No update information"))
        assertEquals(1, failure.suppressed.size)
        assertTrue(failure.suppressed.single().message!!.contains("503"))
        assertEquals(listOf(primaryManifest, backupManifest), requests)
    }

    @Test
    fun failingCorruptTruncatedOrOversizedPrimaryApkRetriesWithFreshMirrorBytes() = runBlocking {
        responses[backupApk] = Response(body = apk)
        val failures = listOf(
            Response(503), Response(body = ByteArray(apk.size) { 'x'.code.toByte() }),
            Response(body = apk.copyOf(4)), Response(body = apk + byteArrayOf(1)),
            Response(302, location = "$origin/other/YourIPTV.apk"),
        )
        for (failure in failures) {
            requests.clear()
            responses[primaryApk] = failure
            val file = folder.resolve("download.part")
            file.writeText("old partial contents that must be discarded")
            var verified = 0
            val progress = mutableListOf<Int>()
            transport().download(release(), file, { progress.add(it) }) {
                verified++
                assertArrayEquals(apk, it.readBytes())
            }
            assertEquals(1, verified)
            assertArrayEquals(apk, file.readBytes())
            assertEquals(listOf(primaryApk, backupApk), requests)
            assertEquals(0, progress.first())
            assertEquals(100, progress.last())
        }
    }

    @Test
    fun backupMetadataCanDownloadDirectlyWhilePrimaryIsDown() = runBlocking {
        responses[primaryManifest] = Response(503)
        publish(backupManifest, release(backupApk))
        responses[backupApk] = Response(body = apk)
        val update = transport().latest()
        transport().download(update, folder.resolve("download.part"), {}) { assertArrayEquals(apk, it.readBytes()) }
        assertEquals(listOf(primaryManifest, backupManifest, backupApk), requests)
        assertEquals(update, UpdateRelease.parse(update.encode(), "$origin/primary/", "$origin/backup/"))
    }

    @Test
    fun backupMustMatchTheOriginallySelectedReleaseWithoutFetchingDifferentMetadata() {
        responses[primaryApk] = Response(503)
        responses[backupApk] = Response(body = "some-other-apk-file".toByteArray())
        publish(backupManifest, release(backupApk).copy(versionCode = 31, versionName = "0.5.7"))
        var verified = 0
        assertThrows(UpdateException::class.java) {
            runBlocking { transport().download(release(), folder.resolve("download.part"), {}) { verified++ } }
        }
        assertEquals(0, verified)
        assertEquals(listOf(primaryApk, backupApk), requests)
    }

    @Test
    fun packagePolicyStillRunsAfterEverySuccessfulChecksum() = runBlocking<Unit> {
        responses[primaryApk] = Response(body = apk)
        responses[backupApk] = Response(body = apk)
        var checks = 0
        transport().download(release(), folder.resolve("download.part"), {}) {
            checks++
            if (checks == 1) throw UpdateException("The APK signing certificate does not match this app.")
        }
        assertEquals(2, checks)
        assertEquals(listOf(primaryApk, backupApk), requests)
        assertThrows(UpdateException::class.java) {
            runBlocking {
                transport().download(release(backupApk), folder.resolve("download.part"), {}) {
                    throw UpdateException("The APK signing certificate does not match this app.")
                }
            }
        }
    }

    @Test
    fun cancellationDuringPrimaryDownloadDoesNotRequestTheMirror() {
        responses[primaryApk] = Response(body = apk)
        responses[backupApk] = Response(body = apk)
        assertThrows(CancellationException::class.java) {
            runBlocking {
                transport().download(release(), folder.resolve("download.part"), { percent ->
                    if (percent > 0) throw CancellationException("User left the updater")
                }) { fail("Cancelled download must not reach APK validation") }
            }
        }
        assertEquals(listOf(primaryApk), requests)
    }
}
