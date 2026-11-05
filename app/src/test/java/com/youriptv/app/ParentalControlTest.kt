package com.youriptv.app

import com.youriptv.app.data.ParentalRules
import com.youriptv.app.data.PinCheck
import com.youriptv.app.data.PinKind
import com.youriptv.app.data.PinVault
import com.youriptv.app.data.StringStore
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import java.io.File

/** Regression tests for the independent parental PIN / Settings PIN and for content-restriction rules. */
class ParentalControlTest {

    private class MapStore : StringStore {
        val map = HashMap<String, String>()
        override fun get(key: String) = map[key]
        override fun put(values: Map<String, String?>) { for ((k, v) in values) if (v == null) map.remove(k) else map[k] = v }
    }

    private var now = 1_000_000L
    private val store = MapStore()
    private val vault = PinVault(store) { now }

    // Test-only codes; never real user PINs.
    private val parental = "2468"
    private val settingsCode = "13579"

    private fun bothSet() { vault.set(PinKind.PARENTAL, parental); vault.set(PinKind.SETTINGS, settingsCode) }

    @Test
    fun eachPinOnlyUnlocksItsOwnProtection() {
        bothSet()
        assertEquals(PinCheck.Ok, vault.check(PinKind.PARENTAL, parental))
        assertEquals(PinCheck.Ok, vault.check(PinKind.SETTINGS, settingsCode))
        assertTrue(vault.check(PinKind.SETTINGS, parental) is PinCheck.Wrong)
        assertTrue(vault.check(PinKind.PARENTAL, settingsCode) is PinCheck.Wrong)
    }

    @Test
    fun pinsAreStoredSeparatelyAsSaltedHashes() {
        bothSet()
        assertTrue(store.map.keys.containsAll(listOf("pin_hash", "pin_salt", "settings_pin_hash", "settings_pin_salt")))
        assertFalse("A PIN must never be stored in clear", store.map.values.any { it == parental || it == settingsCode })
        assertTrue(store.map["pin_salt"] != store.map["settings_pin_salt"])
    }

    @Test
    fun identicalPinsAreRejectedInBothDirections() {
        vault.set(PinKind.PARENTAL, parental)
        assertNotNull(vault.validateNew(PinKind.SETTINGS, parental))
        assertNotNull(vault.screenNew(PinKind.SETTINGS, parental))
        assertTrue(runCatching { vault.set(PinKind.SETTINGS, parental) }.isFailure)
        assertFalse(vault.has(PinKind.SETTINGS))

        vault.set(PinKind.SETTINGS, settingsCode)
        assertNotNull(vault.validateNew(PinKind.PARENTAL, settingsCode))
        assertTrue(runCatching { vault.set(PinKind.PARENTAL, settingsCode) }.isFailure)
        assertEquals("Rejected change must keep the old parental PIN", PinCheck.Ok, vault.check(PinKind.PARENTAL, parental))
    }

    @Test
    fun emptyIncompleteAndNonNumericPinsAreRejected() {
        for (bad in listOf("", "1", "123", "123456789", "12a4", " 1234")) {
            assertNotNull(bad, vault.validateNew(PinKind.PARENTAL, bad))
            assertNotNull(bad, vault.screenNew(PinKind.SETTINGS, bad))
        }
        assertNull(vault.validateNew(PinKind.PARENTAL, "1234"))
        assertNull(vault.validateNew(PinKind.PARENTAL, "12345678"))
    }

    @Test
    fun wrongEmptyAndIncompleteEntriesNeverPass() {
        bothSet()
        for (bad in listOf("", "2", "246", "24680", "1357")) {
            assertFalse(bad, vault.check(PinKind.PARENTAL, bad) == PinCheck.Ok)
            now += PinVault.lockoutMs(20)
        }
    }

    @Test
    fun changingOrRemovingOnePinLeavesTheOtherUntouched() {
        bothSet()
        val settingsHash = store.map["settings_pin_hash"]
        vault.set(PinKind.PARENTAL, "8642")
        assertEquals(settingsHash, store.map["settings_pin_hash"])
        assertEquals(PinCheck.Ok, vault.check(PinKind.SETTINGS, settingsCode))
        assertTrue(vault.check(PinKind.PARENTAL, parental) is PinCheck.Wrong)

        vault.set(PinKind.PARENTAL, null)
        assertFalse(vault.has(PinKind.PARENTAL))
        assertTrue(vault.has(PinKind.SETTINGS))
        assertEquals(PinCheck.Ok, vault.check(PinKind.SETTINGS, settingsCode))

        vault.set(PinKind.PARENTAL, parental)
        vault.set(PinKind.SETTINGS, null)
        assertTrue(vault.has(PinKind.PARENTAL))
        assertEquals(PinCheck.Ok, vault.check(PinKind.PARENTAL, parental))
    }

    @Test
    fun repeatedFailuresLockOutEvenTheCorrectPinUntilTheTimeout() {
        bothSet()
        for (i in 1 until PinVault.MAX_ATTEMPTS) assertEquals(PinCheck.Wrong(PinVault.MAX_ATTEMPTS - i), vault.check(PinKind.PARENTAL, "0000"))
        assertTrue(vault.check(PinKind.PARENTAL, "0000") is PinCheck.LockedOut)
        assertTrue("Correct PIN refused during lockout", vault.check(PinKind.PARENTAL, parental) is PinCheck.LockedOut)
        assertEquals("Lockouts are per PIN", PinCheck.Ok, vault.check(PinKind.SETTINGS, settingsCode))

        now += PinVault.lockoutMs(PinVault.MAX_ATTEMPTS)
        assertEquals(PinCheck.Ok, vault.check(PinKind.PARENTAL, parental))
        assertEquals(0, vault.failures(PinKind.PARENTAL))
    }

    @Test
    fun lockoutSurvivesRestartAndGrows() {
        bothSet()
        repeat(PinVault.MAX_ATTEMPTS) { vault.check(PinKind.SETTINGS, "0000") }
        val restarted = PinVault(store) { now }
        assertTrue(restarted.check(PinKind.SETTINGS, settingsCode) is PinCheck.LockedOut)
        now += PinVault.lockoutMs(PinVault.MAX_ATTEMPTS)
        val r = restarted.check(PinKind.SETTINGS, "0000")
        assertTrue(r is PinCheck.LockedOut && r.remainingMs > PinVault.lockoutMs(PinVault.MAX_ATTEMPTS))
        assertTrue(PinVault.lockoutMs(100) <= 15 * 60_000L)
    }

    @Test
    fun choosingANewPinCannotBeUsedToGuessTheOtherPin() {
        bothSet()
        repeat(PinVault.MAX_ATTEMPTS) { vault.screenNew(PinKind.SETTINGS, "${1000 + it}") }
        assertTrue(vault.lockoutRemaining(PinKind.PARENTAL) > 0)
        assertNotNull("Probing is refused during the lockout", vault.screenNew(PinKind.SETTINGS, "9999"))
    }

    @Test
    fun locksNeedAParentalPin() {
        val locked = setOf("News")
        assertFalse(ParentalRules.isLocked(false, "News", locked, true))
        assertFalse(ParentalRules.isLocked(false, "For Adults", locked, true))
        assertTrue(ParentalRules.isLocked(true, "News", locked, false))
        assertTrue(ParentalRules.isLocked(true, "For Adults", emptySet(), true))
        assertFalse(ParentalRules.isLocked(true, "For Adults", emptySet(), false))
        assertFalse(ParentalRules.isLocked(true, "Sports", locked, true))
    }

    @Test
    fun blockedAdultGroupsAreAlwaysHiddenOtherLocksOnlyWhenHidingIsOn() {
        val locked = setOf("News")
        assertTrue(ParentalRules.isHidden(true, "XXX Movies", locked, blockAdult = true, hideLocked = false))
        assertFalse(ParentalRules.isHidden(true, "News", locked, blockAdult = true, hideLocked = false))
        assertTrue(ParentalRules.isHidden(true, "News", locked, blockAdult = true, hideLocked = true))
        assertFalse(ParentalRules.isHidden(true, "Adult Swim", locked, blockAdult = true, hideLocked = false))
        assertFalse(ParentalRules.isHidden(false, "XXX Movies", locked, blockAdult = true, hideLocked = true))
    }

    @Test
    fun legacySharedPinSettingsLockStaysLockedUntilMigrated() {
        assertTrue(ParentalRules.legacySettingsLockPending(legacyFlag = true, hasSettingsPin = false, hasParentalPin = true))
        assertFalse(ParentalRules.legacySettingsLockPending(legacyFlag = true, hasSettingsPin = true, hasParentalPin = true))
        assertFalse("No PIN existed, so the old lock never applied", ParentalRules.legacySettingsLockPending(true, false, false))
        assertFalse(ParentalRules.legacySettingsLockPending(false, false, true))
    }

    // ---- Source checks for wiring that unit tests cannot reach ----

    private val ui = listOf(File("src/main/java/com/youriptv/app/ui"), File("app/src/main/java/com/youriptv/app/ui")).first { it.exists() }
    private fun src(name: String) = File(ui, name).readText()

    @Test
    fun playerNeverUnlocksAGroupWithoutThePin() {
        val player = src("PlayerActivity.kt")
        assertFalse(player.contains("current?.let { unlockedGroups.add(it.group) }"))
        assertTrue(player.contains("EXTRA_UNLOCKED_GROUP") && player.contains("registerOnSharedPreferenceChangeListener").not())
        assertTrue(player.contains("app.settings.addListener(restrictionListener)"))
    }

    @Test
    fun settingsRelockWhenLeftAndOnlyOpenThroughTheUnlockPrompt() {
        val settings = src("SettingsActivity.kt")
        assertTrue(settings.contains("if (s.isSettingsLocked() && !isChangingConfigurations && !pickingFile) finish()"))
        assertTrue(settings.contains("!intent.getBooleanExtra(EXTRA_UNLOCKED, false)"))
        val main = src("MainActivity.kt")
        assertTrue(main.contains("\"Unlock Settings\""))
        assertTrue(main.contains("Dialogs.requireSettingsPin(this, \"unlock Settings\")"))
    }

    @Test
    fun allGroupCountExcludesHiddenGroups() {
        val main = src("MainActivity.kt")
        assertTrue(main.contains("GroupInfo(allLabel, visible.sumOf { it.count })"))
        assertFalse(main.contains("GroupInfo(allLabel, groups.sumOf { it.count })"))
    }

    @Test
    fun pinsAreNeverLogged() {
        val logLines = ui.parentFile.walkTopDown().filter { it.extension == "kt" }
            .flatMap { f -> f.readLines().filter { "Log." in it } }
            .filter { Regex("""\bpin\b|\bagain\b""", RegexOption.IGNORE_CASE).containsMatchIn(it) }
            .toList()
        assertTrue(logLines.joinToString("\n"), logLines.isEmpty())
    }
}
