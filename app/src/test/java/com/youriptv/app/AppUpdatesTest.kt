package com.youriptv.app

import com.youriptv.app.updates.UpdateException
import com.youriptv.app.updates.UpdateForegroundGate
import com.youriptv.app.updates.UpdatePackagePolicy
import com.youriptv.app.updates.UpdateRelease
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertThrows
import org.junit.Assert.assertTrue
import org.junit.Test
import java.io.File

class AppUpdatesTest {
    private val sha = "a".repeat(64)
    private fun json(code: Long = 15, name: String = "0.4.0", previous: Long = 13, size: Long = 1000,
                     apk: String = "https://github.com/youriptvus/YourIPTV/releases/download/v$name/YourIPTV.apk", hash: String = sha) =
        """{"versionCode":$code,"versionName":"$name","apk":"$apk","size":$size,"sha256":"$hash","previousVersionCode":$previous}"""

    @Test
    fun parsesOfficialReleaseAndRoundTrips() {
        val r = UpdateRelease.parse(json())
        assertEquals(15L, r.versionCode)
        assertEquals("0.4.0", r.versionName)
        assertEquals(r, UpdateRelease.parse(r.encode()))
    }

    @Test
    fun optionalWhenOneReleaseBehindAndRequiredWhenTwoBehind() {
        val r = UpdateRelease.parse(json(code = 17, name = "0.4.2", previous = 16))
        assertFalse(r.isNewerThan(17))
        assertFalse(r.isRequiredFor(17))
        assertTrue(r.isNewerThan(16))
        assertFalse("One release behind: Install or Ignore", r.isRequiredFor(16))
        assertTrue("Two releases behind: required", r.isRequiredFor(15))
        assertTrue(r.isRequiredFor(13))
    }

    @Test
    fun rejectsApksOutsideTheOfficialReleaseOrBadData() {
        assertThrows(UpdateException::class.java) { UpdateRelease.parse(json(apk = "https://evil.example/YourIPTV.apk")) }
        assertThrows(UpdateException::class.java) { UpdateRelease.parse(json(apk = "https://github.com/someone/YourIPTV/releases/download/v0.4.0/YourIPTV.apk")) }
        assertThrows(UpdateException::class.java) { UpdateRelease.parse(json(apk = "https://github.com/youriptvus/YourIPTV/releases/download/v0.3.9/YourIPTV.apk")) }
        assertThrows(UpdateException::class.java) { UpdateRelease.parse(json(hash = "xyz")) }
        assertThrows(UpdateException::class.java) { UpdateRelease.parse(json(size = 0)) }
        assertThrows(UpdateException::class.java) { UpdateRelease.parse(json(previous = 15)) }
        assertThrows(UpdateException::class.java) { UpdateRelease.parse(json(name = "../x")) }
    }

    @Test
    fun localTestOriginOnlyWhenConfigured() {
        val local = json(apk = "http://127.0.0.1:8765/YourIPTV.apk")
        assertThrows(UpdateException::class.java) { UpdateRelease.parse(local) }
        assertEquals(15L, UpdateRelease.parse(local, "http://127.0.0.1:8765/").versionCode)
    }

    @Test
    fun networkStaysOnGithubHttps() {
        assertTrue(UpdateRelease.allowedNetworkUrl("https://github.com/youriptvus/YourIPTV/releases/latest/download/update.json"))
        assertTrue(UpdateRelease.allowedNetworkUrl("https://release-assets.githubusercontent.com/x"))
        assertFalse(UpdateRelease.allowedNetworkUrl("http://github.com/x"))
        assertFalse(UpdateRelease.allowedNetworkUrl("https://github.com.evil.example/x"))
        assertFalse(UpdateRelease.allowedNetworkUrl("https://user@github.com/x"))
        assertFalse(UpdateRelease.allowedNetworkUrl("https://github.com:8443/x"))
        assertFalse(UpdateRelease.allowedNetworkUrl("http://127.0.0.1:8765/update.json"))
    }

    @Test
    fun downloadMustMatchSizeAndChecksum() {
        val r = UpdateRelease.parse(json())
        r.verifyDownload(1000, sha.uppercase())
        assertThrows(UpdateException::class.java) { r.verifyDownload(999, sha) }
        assertThrows(UpdateException::class.java) { r.verifyDownload(1000, "b".repeat(64)) }
    }

    private fun policy(pkg: String? = "com.youriptv.app", code: Long = 15, signers: Set<String> = setOf("s"),
                       perms: Set<String> = setOf("p"), brand: String? = "YourIPTV", minSdk: Int = 23) =
        UpdatePackagePolicy.verify("com.youriptv.app", pkg, 14, code, 15, setOf("s"), signers,
            setOf("p"), perms, "YourIPTV", brand, 34, minSdk)

    @Test
    fun packagePolicyKeepsPermissionsSignerAndBrand() {
        policy()
        assertThrows(UpdateException::class.java) { policy(pkg = "com.other") }
        assertThrows(UpdateException::class.java) { policy(code = 14) }
        assertThrows(UpdateException::class.java) { policy(code = 16) }
        assertThrows(UpdateException::class.java) { policy(signers = setOf("x")) }
        assertThrows(UpdateException::class.java) { policy(perms = setOf("p", "android.permission.CAMERA")) }
        assertThrows(UpdateException::class.java) { policy(brand = "Other") }
        assertThrows(UpdateException::class.java) { policy(minSdk = 35) }
    }

    @Test
    fun checksOncePerOpeningNotPerScreen() {
        val g = UpdateForegroundGate()
        g.started()
        assertTrue(g.takeCheck())
        g.started(); g.stopped(false)
        assertFalse("Switching screens is the same opening", g.takeCheck())
        g.stopped(true); g.started()
        assertFalse("Rotation is the same opening", g.takeCheck())
        g.stopped(false)
        g.started()
        assertTrue("Reopening checks again, even after Ignore", g.takeCheck())
    }

    @Test
    fun updaterNeverOpensDeviceSettingsOrRequestsPermissionsForCustomers() {
        val main = listOf(File("src/main"), File("app/src/main")).first { it.exists() }
        val files = File(main, "java/com/youriptv/app/updates").listFiles().orEmpty().toList() +
            File(main, "java/com/youriptv/app/ui/AppUpdateActivity.kt")
        for (f in files) {
            val t = f.readText()
            for (bad in listOf("MANAGE_UNKNOWN_APP_SOURCES", "MANAGE_OVERLAY_PERMISSION", "requestPermissions",
                    "ACTION_APPLICATION_DETAILS_SETTINGS", "ACTION_INSTALL_PACKAGE", "startActivity(Intent(Settings.")) {
                assertFalse("${f.name} must not use $bad", t.contains(bad))
            }
        }
        assertTrue(files.any { it.readText().contains("USER_ACTION_NOT_REQUIRED") })
    }
}
