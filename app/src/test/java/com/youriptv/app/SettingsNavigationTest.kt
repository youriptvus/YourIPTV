package com.youriptv.app

import com.youriptv.app.ui.SettingsCategory
import org.junit.Assert.*
import org.junit.Test
import java.io.File

class SettingsNavigationTest {
    private val source = listOf(
        File("src/main/java/com/youriptv/app/ui/SettingsActivity.kt"),
        File("app/src/main/java/com/youriptv/app/ui/SettingsActivity.kt"),
    ).first { it.exists() }.readText()

    @Test
    fun categoriesAreNamedUniqueAndNonEmpty() {
        assertEquals(9, SettingsCategory.entries.size)
        assertEquals(9, SettingsCategory.entries.map { it.title }.distinct().size)
        assertTrue("About belongs to the public main menu", SettingsCategory.entries.none { it.title == "About" })
        assertTrue("Parental management has its independent destination", SettingsCategory.entries.none { it.title == "Parental Controls" })
        SettingsCategory.entries.forEach {
            assertTrue(it.title.isNotBlank())
            assertTrue(it.description.isNotBlank())
            assertTrue(source.contains("SettingsCategory.${it.name} -> listOf("))
        }
    }

    @Test
    fun everyOriginalSettingAppearsExactlyOnce() {
        val titles = listOf(
            "Buffer size", "Aspect ratio", "Seek step for movies and series",
            "Audio delay (+ = audio later)", "Auto frame rate", "Live stream format (Xtream)",
            "Prefer software video decoding", "Invert channel up/down", "Start with last channel",
            "Default user agent", "Open EPG Settings",
            "24-hour clock", "Auto-update playlist and guide every",
            "Edit current playlist",
            "Show channel numbers", "Clear viewing history",
            "Parental PIN", "Block adult categories", "Locked groups", "Hide locked groups",
            "Lock playlists", "Lock Settings",
            "Create backup", "Restore from backup",
        )
        val parental = listOf(File("src/main/java/com/youriptv/app/ui/ParentalControlsActivity.kt"),
            File("app/src/main/java/com/youriptv/app/ui/ParentalControlsActivity.kt")).first { it.exists() }.readText()
        val pageDefinitions = source.substringAfter("private fun entries(category:")
            .substringBefore("/** Technician-only:") + parental.substringAfter("private fun entries()")
            .substringBefore("private fun lockToggle")
        titles.forEach { title ->
            assertEquals(title, 1, Regex(Regex.escape("\"$title\"")).findAll(pageDefinitions).count())
        }
        assertFalse(pageDefinitions.contains("header("))
    }

    @Test
    fun backNavigationAndFocusRestorationStayInsideTheProtectedVisit() {
        assertTrue(source.contains("if (category != null) showPage(null, categoryFocus) else finish()"))
        assertTrue(source.contains("list.findViewHolderForAdapterPosition(target)?.itemView?.requestFocus()"))
        assertTrue(source.contains("outState.putString(STATE_CATEGORY, category?.name)"))
        assertTrue(source.contains("Dialogs.requireSettingsPin(this, \"unlock Settings\""))
        assertTrue(source.contains("if (isFinishing || !flow.isCurrentPolicy(policy) || !visit.authorize(token))"))
        assertFalse(source.contains("EXTRA_UNLOCKED"))
        assertTrue(source.contains("if (s.isSettingsLocked() && !isChangingConfigurations && !pickingFile && !flow.retains(this)) finish()"))
        assertTrue(source.contains("flow.paused(this)"))
        assertTrue(source.contains("if (!visit.isAuthorized) visit.authorize(visit.beginVisit())"))
    }

    @Test
    fun onlyFreshMasterCanChangeTheLocalLockPolicyOrManageCustomPins() {
        val management = source.substringAfter("private fun settingsLockAction()")
            .substringBefore("companion object")
        assertTrue(management.contains("Dialogs.requireSettingsMaster"))
        assertTrue(management.contains("val token = authorizedToken() ?: return"))
        assertTrue(management.contains("canSave = { hasAuthorization(token) }"))
        assertTrue(management.contains("s.pins.set(PinKind.SETTINGS, null)"))
        assertTrue(management.contains("Turn off Settings lock"))
        assertTrue(management.contains("Turn on Settings lock"))
        assertTrue(management.indexOf("Dialogs.requireSettingsMaster") < management.indexOf("s.setSettingsLockEnabled(!locked)"))
        assertTrue(management.contains("canUse = { hasAuthorization(token) }"))
        assertTrue(management.contains("visit.endVisit()"))
        val data = listOf(File("src/main/java/com/youriptv/app/data/Settings.kt"), File("app/src/main/java/com/youriptv/app/data/Settings.kt"))
            .first { it.exists() }.readText()
        assertTrue(data.contains("fun isSettingsLocked() = settingsLockEnabled"))
        assertTrue(data.contains("SettingsLockPolicy.enabled("))
        val dialogs = listOf(File("src/main/java/com/youriptv/app/ui/UiUtil.kt"), File("app/src/main/java/com/youriptv/app/ui/UiUtil.kt"))
            .first { it.exists() }.readText().substringAfter("fun createPin(")
        val guard = "if (a.isFinishing || a.isDestroyed || !canSave())"
        assertTrue("An expired PIN dialog must not consume parental attempts", dialogs.indexOf(guard) in 0 until dialogs.indexOf("vault.screenNew"))
        assertEquals("Both PIN-entry steps must reject expired authorization", 2, Regex(Regex.escape(guard)).findAll(dialogs).count())
    }

    @Test
    fun unlockSettingsKeepsTheOriginalGateAndOwnsEpgPreferences() {
        assertTrue(source.contains("listTitle.text = selected?.title ?: if (s.isSettingsLocked()) \"Unlock Settings\" else \"Settings\""))
        assertTrue(source.contains("flow.authenticate(this, settingsRoute)"))
        assertTrue(source.contains("Dialogs.requireSettingsPin(this, \"unlock Settings\""))
        assertTrue(source.contains("Entry(\"Create backup\""))
        assertTrue(source.contains("Entry(\"Restore from backup\""))
        assertFalse(source.contains("s.pins.set(PinKind.PARENTAL"))
        assertFalse(source.contains("SettingsCategory.PARENTAL"))
        assertTrue(source.contains("app.repo.setRefreshHours(s.activePlaylistId, hours)"))
        assertTrue(source.contains("app.refreshCoordinator.reconcileSchedule()"))
        val epg = source.substringAfter("SettingsCategory.EPG -> listOf(").substringBefore("SettingsCategory.APPEARANCE")
        assertTrue(epg.contains("val token = authorizedToken() ?: return@entry"))
        assertTrue(epg.contains("if (canUseEpgSettings(token)) EpgSettingsPanel.show(this, s.activePlaylistId, token)"))
    }
}
