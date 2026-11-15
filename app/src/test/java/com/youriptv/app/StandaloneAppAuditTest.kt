package com.youriptv.app

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import java.io.File
import javax.xml.parsers.DocumentBuilderFactory

/**
 * Enforces the architecture requirement: YourIPTV is a standalone IPTV player APK, not a launcher,
 * OS replacement, or device-management app. A separate custom launcher (not part of this project)
 * handles device restrictions; YourIPTV must not depend on them.
 */
class StandaloneAppAuditTest {

    private val moduleDir = listOf(File("."), File("app")).first { File(it, "src/main/AndroidManifest.xml").exists() }
    private val mainDir = File(moduleDir, "src/main")
    private val manifest = File(mainDir, "AndroidManifest.xml").readText()

    @Test
    fun manifestDoesNotReplaceTheLauncher() {
        assertTrue("Must not declare HOME category", !manifest.contains("android.intent.category.HOME"))
        assertTrue("Must not declare DEFAULT home/launcher replacement", !manifest.contains("android.intent.category.SECONDARY_HOME"))
        assertTrue("Must be launchable on TV", manifest.contains("android.intent.category.LEANBACK_LAUNCHER"))
        assertTrue("Must be launchable on phones", manifest.contains("android.intent.category.LAUNCHER"))
    }

    @Test
    fun noDeviceAdminServicesOrReceivers() {
        for (bad in listOf("BIND_DEVICE_ADMIN", "DEVICE_ADMIN_ENABLED", "<receiver", "<provider",
                "BIND_ACCESSIBILITY_SERVICE", "lockTaskMode")) {
            assertTrue("Unexpected system-level component: $bad", !manifest.contains(bad))
        }
        // The approved refresh job is the sole service. It grants no device administration or public entry point.
        val document = DocumentBuilderFactory.newInstance().apply { isNamespaceAware = true }
            .newDocumentBuilder().parse(File(mainDir, "AndroidManifest.xml"))
        val services = document.getElementsByTagName("service")
        assertEquals("Only the approved refresh service is allowed", 1, services.length)
        val service = services.item(0) as org.w3c.dom.Element
        val android = "http://schemas.android.com/apk/res/android"
        assertEquals(".refresh.RefreshJobService", service.getAttributeNS(android, "name"))
        assertEquals("android.permission.BIND_JOB_SERVICE", service.getAttributeNS(android, "permission"))
        assertEquals("false", service.getAttributeNS(android, "exported"))
        assertEquals("No service intent filters are permitted", 0, service.getElementsByTagName("intent-filter").length)
        val hits = mainDir.walkTopDown().filter { it.isFile && it.extension in setOf("kt", "java") }.filter { f ->
            val t = f.readText()
            listOf("DevicePolicyManager", "DeviceAdminReceiver", "startLockTask", "setLockTaskPackages", "Runtime.getRuntime().exec(\"su")
                .any { t.contains(it) }
        }.toList()
        assertTrue("Device-management/root code found: $hits", hits.isEmpty())
    }

    @Test
    fun permissionsAreMinimal() {
        val perms = Regex("""uses-permission\s+android:name="([^"]+)"""").findAll(manifest).map { it.groupValues[1] }.toSet()
        assertEquals(setOf("android.permission.INTERNET", "android.permission.ACCESS_NETWORK_STATE",
            "android.permission.REQUEST_INSTALL_PACKAGES", "android.permission.UPDATE_PACKAGES_WITHOUT_USER_ACTION",
            "android.permission.SYSTEM_ALERT_WINDOW"), perms)
    }

    @Test
    fun refreshJobsPreserveTheApprovedInstallationPermissionBoundary() {
        val source = File(mainDir, "java/com/youriptv/app/refresh/RefreshJobScheduler.kt").readText()
        assertTrue("Refresh jobs must explicitly remain non-persisted across device reboot",
            Regex("""\.setPersisted\(\s*false\s*\)""").containsMatchIn(source))
        assertTrue("A persisted refresh job would add a boot permission and change upgrade compatibility",
            !Regex("""\.setPersisted\(\s*true\s*\)""").containsMatchIn(source))
        assertTrue("Refresh must not add boot permissions", !manifest.contains("android.permission.RECEIVE_BOOT_COMPLETED"))
    }
}
