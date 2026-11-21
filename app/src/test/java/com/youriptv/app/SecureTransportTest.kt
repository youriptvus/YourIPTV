package com.youriptv.app

import com.youriptv.app.data.Http
import com.youriptv.app.data.InsecureTransportException
import com.youriptv.app.data.SecureTransport
import com.youriptv.app.data.XtreamUrls
import org.junit.Assert.*
import org.junit.Test
import com.sun.net.httpserver.HttpServer
import java.net.InetSocketAddress

class SecureTransportTest {
    @Test fun userProvidedHttpAndHttpsAddressesAreAcceptedWithoutRewriting() {
        for (url in listOf("http://provider.example:8080", "https://provider.example/list?password=synthetic", "HTTP://provider.example/list", "http://127.0.0.1/list")) {
            assertTrue(url, SecureTransport.isNetworkUrl(url))
            SecureTransport.requireNetworkUrl(url)
            assertTrue(url, SecureTransport.canLoadMedia(url))
            assertTrue(url, SecureTransport.canHandOffMedia(url))
        }
        assertEquals("http://provider.example:8080", XtreamUrls.normalizeBase("provider.example:8080"))
        assertEquals("http://provider.example", XtreamUrls.normalizeBase("http://provider.example/get.php?username=synthetic"))
        assertEquals("https://provider.example", XtreamUrls.normalizeBase("https://provider.example/player_api.php"))
    }

    @Test fun invalidSourcesAreRejectedWithoutExposingPrivateInput() {
        for (url in listOf("ftp://provider.example/list", "file:///storage/list", "https:///nohost", "http://", "//provider.example", "provider.example", "http://provider.example:99999", "https://provider.example/line\nbreak")) {
            assertFalse(url, SecureTransport.isNetworkUrl(url))
            val error = assertThrows(InsecureTransportException::class.java) { SecureTransport.requireNetworkUrl(url) }
            assertEquals(SecureTransport.NETWORK_URL_REQUIRED, error.message)
            assertFalse(error.message.orEmpty().contains(url))
        }
    }

    @Test fun redirectsPreserveHttpAndAllowAnHttpsUpgrade() {
        assertEquals("http://provider.example/next", SecureTransport.redirect("http://provider.example/list", "/next"))
        assertEquals("https://cdn.example/next", SecureTransport.redirect("http://provider.example/list", "https://cdn.example/next"))
        assertEquals("https://cdn.example/next", SecureTransport.redirect("https://provider.example/list", "//cdn.example/next"))
    }

    @Test fun httpsDowngradesAndNonNetworkRedirectsAreBlockedWithoutLeakingCredentials() {
        for (target in listOf("http://cdn.example/private?password=synthetic", "ftp://cdn.example/private", "file:///storage/private")) {
            val error = assertThrows(InsecureTransportException::class.java) {
                SecureTransport.redirect("https://provider.example/private?username=synthetic", target)
            }
            assertEquals(SecureTransport.SECURE_REDIRECT_REQUIRED, error.message)
            assertFalse(error.message.orEmpty().contains("synthetic"))
        }
        assertThrows(InsecureTransportException::class.java) {
            SecureTransport.redirect("http://provider.example/list", "file:///storage/private")
        }
    }

    @Test fun localMediaStillWorksAndUnsupportedProtocolsAreNotNetworkSources() {
        for (url in listOf("file:///storage/My recording.ts", "content://media/external/video/1", "asset:///demo.ts", "data:video/mp2t;base64,AA==", "/storage/My recording.ts"))
            assertTrue(url, SecureTransport.canLoadMedia(url))
        for (url in listOf("ftp://provider.example/file", "http:///nohost", "//provider.example/private.ts", ""))
            assertFalse(url, SecureTransport.canLoadMedia(url))
        assertFalse(SecureTransport.canHandOffMedia("ftp://provider.example/file"))
    }

    @Test fun actualHttpPlaylistAndGuideDownloadsFollowRelativeRedirects() {
        val server = HttpServer.create(InetSocketAddress("127.0.0.1", 0), 0)
        val playlist = "#EXTM3U\n#EXTINF:-1,Fixture\nhttp://127.0.0.1/live.ts\n"
        val guide = "<tv><channel id=\"fixture\"/></tv>"
        server.createContext("/redirect") { exchange ->
            exchange.responseHeaders.add("Location", "/playlist.m3u"); exchange.sendResponseHeaders(302, -1); exchange.close()
        }
        for ((path, value) in listOf("/playlist.m3u" to playlist, "/guide.xml" to guide)) {
            server.createContext(path) { exchange ->
                val bytes = value.toByteArray();exchange.sendResponseHeaders(200, bytes.size.toLong())
                exchange.responseBody.use { it.write(bytes) }
            }
        }
        server.start()
        try {
            val origin = "http://127.0.0.1:${server.address.port}"
            assertEquals(playlist, Http.readText("$origin/redirect"))
            assertEquals(guide, Http.readText("$origin/guide.xml"))
        } finally { server.stop(0) }
    }
}
