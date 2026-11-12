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
        assertEquals(10, SettingsCategory.entries.size)
        assertEquals(10, SettingsCategory.entries.map { it.title }.distinct().size)
        assertTrue("About belongs to the public main menu", SettingsCategory.entries.none { it.title == "About" })
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
            "Default user agent", "Keep past programmes", "Download future programmes",
            "Guide time shift", "24-hour clock", "Auto-update playlist and guide every",
            "Edit current playlist",
            "Update guide now", "Show channel numbers", "Clear viewing history",
            "Parental PIN", "Block adult categories", "Locked groups", "Hide locked groups",
            "Lock playlists", "Lock Settings",
            "Create backup", "Restore from backup",
        )
        val pageDefinitions = source.substringAfter("private fun entries(category:")
            .substringBefore("private fun guideStatus()")
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
        assertTrue(source.contains("if (isFinishing || !visit.authorize(token))"))
        assertFalse(source.contains("EXTRA_UNLOCKED"))
        assertTrue(source.contains("if (s.isSettingsLocked() && !isChangingConfigurations && !pickingFile && !flow.retains(this)) finish()"))
        assertTrue(source.contains("flow.paused(this)"))
        assertTrue(source.contains("if (!visit.isAuthorized) visit.authorize(visit.beginVisit())"))
    }

    @Test
    fun settingsStayProtectedAndOnlyMasterCanManageTheOptionalCustomPin() {
        val management = source.substringAfter("private fun settingsLockAction()")
            .substringBefore("private fun lockedGroups()")
        assertTrue(management.contains("Dialogs.requireSettingsMaster"))
        assertTrue(management.contains("val token = authorizedToken() ?: return"))
        assertTrue(management.contains("canSave = { hasAuthorization(token) }"))
        assertTrue(management.contains("s.pins.set(PinKind.SETTINGS, null)"))
        assertFalse(management.contains("Turn off Settings lock"))
        val data = listOf(File("src/main/java/com/youriptv/app/data/Settings.kt"), File("app/src/main/java/com/youriptv/app/data/Settings.kt"))
            .first { it.exists() }.readText()
        assertTrue(data.contains("fun isSettingsLocked() = true"))
        val dialogs = listOf(File("src/main/java/com/youriptv/app/ui/UiUtil.kt"), File("app/src/main/java/com/youriptv/app/ui/UiUtil.kt"))
            .first { it.exists() }.readText().substringAfter("fun createPin(")
        val guard = "if (a.isFinishing || a.isDestroyed || !canSave())"
        assertTrue("An expired PIN dialog must not consume parental attempts", dialogs.indexOf(guard) in 0 until dialogs.indexOf("vault.screenNew"))
        assertEquals("Both PIN-entry steps must reject expired authorization", 2, Regex(Regex.escape(guard)).findAll(dialogs).count())
    }
}
