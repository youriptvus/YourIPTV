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
        assertEquals(11, SettingsCategory.entries.size)
        assertEquals(11, SettingsCategory.entries.map { it.title }.distinct().size)
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
            "Lock playlists", "Lock Settings", "About \${Branding.displayName(this)}",
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
        assertTrue(source.contains("!intent.getBooleanExtra(EXTRA_UNLOCKED, false)"))
        assertTrue(source.contains("if (s.isSettingsLocked() && !isChangingConfigurations && !pickingFile) finish()"))
    }
}
