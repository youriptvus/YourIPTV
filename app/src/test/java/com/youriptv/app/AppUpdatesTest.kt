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
    fun stable101CanBeOfferedDirectlyToEveryEarlierVersionCode() {
        val release = UpdateRelease.parse(json(code = 32, name = "1.0.1", previous = 31))
        for (installed in 1L..31L) {
            assertTrue("Direct update from code $installed", release.isNewerThan(installed))
            assertEquals(installed < 31L, release.isRequiredFor(installed))
        }
        assertFalse(release.isNewerThan(32))
        assertFalse(release.isNewerThan(33))
        assertEquals(listOf(
            "https://github.com/youriptvus/YourIPTV/releases/download/v1.0.1/YourIPTV.apk",
            "https://youriptv.us/updates/YourIPTV-1.0.1-release.apk",
        ), release.downloadUrls())
    }

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
    fun versionOnly042ReleaseOffers041AndRequires040() {
        val r = UpdateRelease.parse(json(code = 22, name = "0.4.2", previous = 21))
        assertEquals("https://github.com/youriptvus/YourIPTV/releases/download/v0.4.2/YourIPTV.apk", r.url)
        assertTrue(r.isNewerThan(21))
        assertFalse("0.4.1 must still offer Ignore", r.isRequiredFor(21))
        assertTrue("Code 20 is two releases behind", r.isRequiredFor(20))
        assertFalse(r.isNewerThan(22))
    }

    @Test
    fun versionOnly050ReleaseOffers043AndRequires042() {
        val r = UpdateRelease.parse(json(code = 24, name = "0.5.0", previous = 23))
        assertEquals("https://github.com/youriptvus/YourIPTV/releases/download/v0.5.0/YourIPTV.apk", r.url)
        assertTrue(r.isNewerThan(23))
        assertFalse("0.4.3 must still offer Ignore", r.isRequiredFor(23))
        assertTrue("Code 22 is two releases behind", r.isRequiredFor(22))
        assertFalse(r.isNewerThan(24))
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
        assertEquals(15L, UpdateRelease.parse(local, "", "http://127.0.0.1:8765/").versionCode)
        assertThrows(UpdateException::class.java) {
            UpdateRelease.parse(json(apk = "http://127.0.0.1:8765/primary/../private/YourIPTV.apk"), "http://127.0.0.1:8765/primary/")
        }
    }

    @Test
    fun networkStaysOnOfficialGithubOrWebsiteHttps() {
        assertTrue(UpdateRelease.allowedNetworkUrl("https://github.com/youriptvus/YourIPTV/releases/latest/download/update.json"))
        assertTrue(UpdateRelease.allowedNetworkUrl("https://release-assets.githubusercontent.com/x"))
        assertFalse(UpdateRelease.allowedNetworkUrl("http://github.com/x"))
        assertFalse(UpdateRelease.allowedNetworkUrl("https://github.com.evil.example/x"))
        assertFalse(UpdateRelease.allowedNetworkUrl("https://user@github.com/x"))
        assertFalse(UpdateRelease.allowedNetworkUrl("https://github.com:8443/x"))
        assertFalse(UpdateRelease.allowedNetworkUrl("http://127.0.0.1:8765/update.json"))
        assertTrue(UpdateRelease.allowedNetworkUrl("https://youriptv.us/updates/update.json"))
        assertTrue(UpdateRelease.allowedNetworkUrl("https://youriptv.us/updates/YourIPTV-0.5.6-release.apk"))
        for (url in listOf(
            "http://youriptv.us/updates/update.json", "https://youriptv.us.evil.example/updates/update.json",
            "https://user@youriptv.us/updates/update.json", "https://youriptv.us:8443/updates/update.json",
            "https://youriptv.us/private/update.json", "https://youriptv.us/updates/../private/update.json",
            "https://youriptv.us/updates/%2e%2e/private/update.json", "https://youriptv.us/updates%2fupdate.json",
            "https://youriptv.us/updates/update.json#private",
        )) assertFalse(url, UpdateRelease.allowedNetworkUrl(url))
    }

    @Test
    fun officialMirrorMustIdentifyTheExactReleaseAndSurvivesCaching() {
        val website = "https://youriptv.us/updates/YourIPTV-0.4.0-release.apk"
        val r = UpdateRelease.parse(json(apk = website))
        assertEquals(r, UpdateRelease.parse(r.encode()))
        assertEquals(listOf(website), r.downloadUrls())
        assertEquals(listOf(UpdateRelease.parse(json()).url, website), UpdateRelease.parse(json()).downloadUrls())
        for (url in listOf(
            "https://youriptv.us/updates/YourIPTV-0.3.9-release.apk",
            "https://youriptv.us/updates/YourIPTV.apk", "$website?download=1", "$website#anything",
            "https://youriptv.us/updates/../updates/YourIPTV-0.4.0-release.apk",
        )) assertThrows(url, UpdateException::class.java) { UpdateRelease.parse(json(apk = url)) }
    }

    @Test
    fun staleMirrorCannotLowerKnownVersionOrRequiredUpdateFloor() {
        val latest = UpdateRelease.parse(json(code = 30, name = "0.5.6", previous = 29))
        val stale = UpdateRelease.parse(json(code = 29, name = "0.5.5", previous = 27))
        val selected = stale.preservingKnownRelease(latest)
        assertEquals(latest, selected)
        assertTrue("A stale mirror must preserve the cached required update", selected.isRequiredFor(27))
        assertEquals(latest, UpdateRelease.parse(json(code = 27, previous = 26)).preservingKnownRelease(latest))
        assertEquals(latest, latest.preservingKnownRelease(stale))
        assertEquals(latest, latest.preservingKnownRelease(null))
        assertEquals(latest, latest.copy(sha256 = "b".repeat(64)).preservingKnownRelease(latest))
        assertEquals(latest, latest.copy(previousVersionCode = 0).preservingKnownRelease(latest))
        val sameReleaseOnMirror = latest.copy(url = "https://youriptv.us/updates/YourIPTV-0.5.6-release.apk")
        assertEquals(sameReleaseOnMirror, sameReleaseOnMirror.preservingKnownRelease(latest))
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

    @Test
    fun automaticUpdatesAreOnByDefaultAndIndependentOfOtherSettings() {
        val main = listOf(File("src/main"), File("app/src/main")).first { it.exists() }
        val settings = File(main, "java/com/youriptv/app/data/Settings.kt").readText()
        assertTrue(settings.contains("getBoolean(\"automatic_app_updates\", true)"))
        assertTrue("Existing boxes are switched on once", settings.contains("putBoolean(\"automatic_app_updates\", true).putBoolean(\"auto_updates_forced_on\", true)"))
        val screen = File(main, "java/com/youriptv/app/ui/SettingsActivity.kt").readText()
        val toggle = screen.substringAfter("Entry(\"Automatic updates\"").substringBefore("private fun openRestartAuthorization")
        assertFalse("Turning updates on must not need a Settings PIN", toggle.contains("isSettingsLocked"))
        assertFalse("Turning updates on must not need a prior install grant", toggle.contains("} else if (!AppUpdates.installationAuthorized"))
        val checks = File(main, "java/com/youriptv/app/updates/AppUpdateChecks.kt").readText()
        assertFalse(checks.contains("isSettingsLocked") || checks.contains("hasPin"))
        val installer = File(main, "java/com/youriptv/app/ui/AppUpdateActivity.kt").readText()
        assertFalse("Android asks for the install grant itself", installer.contains("installationAuthorized"))
    }
}
