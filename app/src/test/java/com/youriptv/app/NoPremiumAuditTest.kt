package com.youriptv.app

import org.junit.Assert.assertTrue
import org.junit.Test
import java.io.File

/**
 * Enforces the mandatory NO-PREMIUM requirement: YourIPTV must contain no paywall, billing,
 * licensing, activation, trial, or premium-gating code, and must tell users it is free.
 */
class NoPremiumAuditTest {

    private val moduleDir = listOf(File("."), File("app")).first { File(it, "src/main/AndroidManifest.xml").exists() }
    private val mainDir = File(moduleDir, "src/main")

    private fun sources() = mainDir.walkTopDown()
        .filter { it.isFile && it.extension in setOf("kt", "java", "xml") }
        .toList()

    /** Mechanisms (identifiers, APIs, UI phrases) that would implement a paid tier or feature lock. */
    private val forbidden = listOf(
        Regex("""com\.android\.billingclient"""),
        Regex("""\bBillingClient\b"""),
        Regex("""com\.android\.vending\.(BILLING|CHECK_LICENSE)"""),
        Regex("""\bLicenseChecker\b"""),
        Regex("""\b(is|has|require[sd]?|check)(Premium|Pro|Paid|License|Licence|Subscription|Trial|Activation)\w*""", RegexOption.IGNORE_CASE),
        Regex("""\bpremium(Only|Feature|User|Unlocked|Required)\b""", RegexOption.IGNORE_CASE),
        Regex("""\btrial(Expir|End|Days|Remaining)\w*""", RegexOption.IGNORE_CASE),
        Regex("""\bactivationCode\b""", RegexOption.IGNORE_CASE),
        Regex("""unlock\s+premium|go\s+premium|upgrade\s+to\s+(premium|pro)|buy\s+premium|get\s+premium|premium\s+version""", RegexOption.IGNORE_CASE),
    )

    @Test
    fun sourcesContainNoPremiumGatingOrBilling() {
        val hits = sources().flatMap { f ->
            f.readLines().mapIndexedNotNull { i, line ->
                forbidden.firstOrNull { it.containsMatchIn(line) }?.let { "${f.relativeTo(mainDir)}:${i + 1}: ${line.trim()}" }
            }
        }
        assertTrue("Premium/billing/licensing mechanisms found:\n" + hits.joinToString("\n"), hits.isEmpty())
    }

    @Test
    fun auditPatternsDetectTypicalPaywallCode() {
        val samples = listOf("if (!isPremium) showUpgrade()", "billingClient = BillingClient.newBuilder(ctx)", "val trialExpiresAt = 0L",
            "Unlock Premium", "<uses-permission android:name=\"com.android.vending.BILLING\"/>", "checkLicense()")
        for (s in samples) assertTrue("Audit pattern missed: $s", forbidden.any { it.containsMatchIn(s) })
        for (ok in listOf("Dialogs.requirePin(this)", "fun checkPin(pin: String)", "provider allows 1 connection")) {
            assertTrue("False positive: $ok", forbidden.none { it.containsMatchIn(ok) })
        }
    }

    @Test
    fun buildHasNoBillingOrLicensingDependency() {
        val gradle = File(moduleDir, "build.gradle.kts").readText()
        for (bad in listOf("billing", "play-licensing", "licensing", "iap", "revenuecat", "purchases")) {
            assertTrue("Unexpected payment/licensing dependency '$bad'", !Regex("""["']\S*$bad\S*["']""", RegexOption.IGNORE_CASE).containsMatchIn(gradle))
        }
        val manifest = File(mainDir, "AndroidManifest.xml").readText()
        assertTrue(!manifest.contains("BILLING") && !manifest.contains("CHECK_LICENSE"))
    }

    @Test
    fun appTellsUsersEverythingIsFree() {
        val strings = File(mainDir, "res/values/strings.xml").readText()
        assertTrue(strings.contains("free_notice"))
        assertTrue(strings.contains("no account, purchase, activation code, trial, or subscription"))
        assertTrue("Provider limits must still be explained", strings.contains("provider connection limits"))
    }

    @Test
    fun settingsAndPlaylistsHaveNoArtificialLimits() {
        val settings = File(mainDir, "java/com/youriptv/app/data/Settings.kt").readText()
        val repo = File(mainDir, "java/com/youriptv/app/data/Repository.kt").readText()
        for (src in listOf(settings, repo)) {
            assertTrue(!Regex("""max(Playlists|Favorites|Devices)""", RegexOption.IGNORE_CASE).containsMatchIn(src))
        }
        // Parental PIN must remain a real gate (not removed as part of de-gating).
        val parental = File(mainDir, "java/com/youriptv/app/data/Parental.kt").readText()
        assertTrue(parental.contains("fun check(kind: PinKind, pin: String): PinCheck"))
        assertTrue(settings.contains("fun isGroupLocked"))
    }
}
