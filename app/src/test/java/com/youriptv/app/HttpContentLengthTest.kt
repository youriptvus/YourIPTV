package com.youriptv.app

import com.youriptv.app.data.ContentLengthInputStream
import com.youriptv.app.data.Http
import com.youriptv.app.data.M3uParser
import org.junit.Assert.*
import org.junit.Test
import java.io.ByteArrayInputStream
import java.io.ByteArrayOutputStream
import java.io.EOFException
import java.io.InputStream
import java.net.InetAddress
import java.net.ServerSocket
import java.util.concurrent.Executors
import java.util.concurrent.TimeUnit
import java.util.zip.GZIPOutputStream

class HttpContentLengthTest {
    private val playlist = "#EXTM3U\n#EXTINF:-1,Synthetic\nhttp://fixture.invalid/live/1.ts\n".toByteArray()

    private fun gzip(bytes: ByteArray): ByteArray = ByteArrayOutputStream().also { output ->
        GZIPOutputStream(output).use { it.write(bytes) }
    }.toByteArray()

    /** One synthetic HTTP response on loopback. Never connects to a provider. */
    private fun response(body: ByteArray, headers: String, check: (String) -> Unit) {
        ServerSocket(0, 1, InetAddress.getByName("127.0.0.1")).use { server ->
            server.soTimeout = 5000
            val executor = Executors.newSingleThreadExecutor()
            val sent = executor.submit {
                server.accept().use { socket ->
                    socket.soTimeout = 5000
                    val request = socket.getInputStream().bufferedReader(Charsets.US_ASCII)
                    while (!request.readLine().isNullOrEmpty()) Unit
                    socket.getOutputStream().apply {
                        write(("HTTP/1.1 200 OK\r\nConnection: close\r\n$headers\r\n").toByteArray(Charsets.US_ASCII))
                        write(body)
                        flush()
                    }
                    socket.shutdownOutput()
                }
            }
            try {
                check("http://127.0.0.1:${server.localPort}/synthetic")
                sent.get(5, TimeUnit.SECONDS)
            } finally {
                executor.shutdownNow()
                executor.awaitTermination(1, TimeUnit.SECONDS)
            }
        }
    }

    private fun parse(url: String): Int = Http.open(url, timeoutMs = 2000).use { input ->
        M3uParser.parse(input, strict = true) {}
    }

    @Test fun truncatedFixedLengthAtCompleteM3uEntryCannotSucceed() {
        response(playlist, "Content-Length: ${playlist.size + 4096}\r\n") { url ->
            val error = assertThrows(EOFException::class.java) { parse(url) }
            assertEquals("The response ended before its declared content length.", error.message)
            assertFalse(error.message.orEmpty().contains(url))
        }
    }

    @Test fun completeFixedLengthPlainBodyParses() {
        response(playlist, "Content-Length: ${playlist.size}\r\n") { assertEquals(1, parse(it)) }
    }

    @Test fun truncationDuringInitialGzipProbeIsRejectedBeforeReturningAStream() {
        response(byteArrayOf('#'.code.toByte()), "Content-Length: 4\r\n") { url ->
            assertThrows(EOFException::class.java) { Http.open(url, timeoutMs = 2000).close() }
        }
    }

    @Test fun completeGzipUsesEncodedLengthRatherThanDecodedLength() {
        val body = gzip(playlist)
        assertNotEquals(playlist.size, body.size)
        response(body, "Content-Length: ${body.size}\r\nContent-Encoding: gzip\r\n") { assertEquals(1, parse(it)) }
    }

    @Test fun validGzipTrailerDoesNotHideAnOverstatedEncodedLength() {
        val body = gzip(playlist)
        response(body, "Content-Length: ${body.size + 4096}\r\nContent-Encoding: gzip\r\n") { url ->
            assertThrows(EOFException::class.java) { parse(url) }
        }
    }

    @Test fun gzipCannotUseDecodedSizeAsContentLength() {
        val decoded = "#EXTM3U\n".toByteArray() + playlist.copyOfRange(8, playlist.size).let { entry ->
            ByteArrayOutputStream().also { output -> repeat(200) { output.write(entry) } }.toByteArray()
        }
        val body = gzip(decoded)
        assertTrue(decoded.size > body.size)
        response(body, "Content-Length: ${decoded.size}\r\nContent-Encoding: gzip\r\n") { url ->
            assertThrows(EOFException::class.java) { parse(url) }
        }
    }

    @Test fun truncatedGzipIsStillRejected() {
        val body = gzip(playlist)
        response(body.copyOf(body.size - 8), "Content-Length: ${body.size}\r\nContent-Encoding: gzip\r\n") { url ->
            assertThrows(EOFException::class.java) { parse(url) }
        }
    }

    @Test fun magicDetectedGzipAlsoChecksEncodedCompletion() {
        val body = gzip(playlist)
        response(body, "Content-Length: ${body.size + 4096}\r\n") { url ->
            assertThrows(EOFException::class.java) { parse(url) }
        }
    }

    @Test fun unknownLengthBodyRetainsOrdinaryEof() {
        response(playlist, "") { assertEquals(1, parse(it)) }
    }

    @Test fun chunkedFramingOverridesContentLength() {
        val body = (playlist.size.toString(16) + "\r\n").toByteArray() + playlist + "\r\n0\r\n\r\n".toByteArray()
        response(body, "Transfer-Encoding: chunked\r\nContent-Length: ${playlist.size + 4096}\r\n") {
            assertEquals(1, parse(it))
        }
    }

    @Test fun onlyValidUnambiguousDecimalLengthWithoutTransferEncodingIsUsed() {
        assertEquals(0L, Http.fixedContentLength("0", null))
        assertEquals(12L, Http.fixedContentLength(" 0012 ", null))
        assertEquals(Long.MAX_VALUE, Http.fixedContentLength(Long.MAX_VALUE.toString(), null))
        for (value in listOf(null, "", " ", "-1", "+1", "1,1", "1.0", "NaN", "9223372036854775808"))
            assertNull(value, Http.fixedContentLength(value, null))
        for (encoding in listOf("chunked", "identity", ""))
            assertNull(encoding, Http.fixedContentLength("12", encoding))
    }

    @Test fun singleAndBulkReadsCountTheSameBody() {
        ContentLengthInputStream(ByteArrayInputStream(byteArrayOf(1, 2, 3)), 3).use { input ->
            assertEquals(1, input.read())
            val rest = ByteArray(4)
            assertEquals(2, input.read(rest, 1, 3))
            assertArrayEquals(byteArrayOf(0, 2, 3, 0), rest)
            assertEquals(0, input.read(rest, 0, 0))
            assertEquals(-1, input.read())
        }
    }

    @Test fun skipCountsBytesAndDetectsEarlyEofOnTheNextReadOrSkip() {
        ContentLengthInputStream(ByteArrayInputStream(byteArrayOf(1, 2, 3)), 4).use { input ->
            assertEquals(2L, input.skip(2))
            assertEquals(3, input.read())
            assertThrows(EOFException::class.java) { input.skip(1) }
        }
    }

    @Test fun skipFallsBackToReadWhenTheSourceCannotSkip() {
        val source = object : ByteArrayInputStream(byteArrayOf(1, 2)) {
            override fun skip(count: Long): Long = 0
        }
        ContentLengthInputStream(source, 2).use { input ->
            assertEquals(1L, input.skip(2))
            assertEquals(2, input.read())
            assertEquals(-1, input.read())
        }
    }

    @Test fun earlyCloseDoesNotRequireTheWholeBody() {
        var closed = false
        val source = object : ByteArrayInputStream(byteArrayOf(1, 2)) {
            override fun close() { closed = true; super.close() }
        }
        ContentLengthInputStream(source, 100).use { assertEquals(1, it.read()) }
        assertTrue(closed)
        response(playlist, "Content-Length: ${playlist.size + 4096}\r\n") { url ->
            Http.open(url, timeoutMs = 2000).use { assertEquals('#'.code, it.read()) }
        }
    }

    @Test fun decodedSkipAlsoChecksCompletionAndPermitsEarlyClose() {
        val raw = ContentLengthInputStream(ByteArrayInputStream(byteArrayOf(1)), 2)
        val completeDecoded = ByteArrayInputStream(byteArrayOf(5))
        raw.checkCompletion(completeDecoded).use { input ->
            assertEquals(1L, input.skip(1))
            assertThrows(EOFException::class.java) { input.skip(1) }
        }
        ContentLengthInputStream(ByteArrayInputStream(byteArrayOf(1)), 2)
            .checkCompletion(ByteArrayInputStream(byteArrayOf(5))).use { assertEquals(5, it.read()) }
    }
}
