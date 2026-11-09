package com.youriptv.app

import org.junit.Assert.*
import org.junit.Test
import java.io.File

/** Navigation contracts whose Android activity launch wiring is not executable in the local JVM. */
class PublicAboutNavigationTest {
    private val source = listOf(
        File("src/main/java/com/youriptv/app/ui/MainActivity.kt"),
        File("app/src/main/java/com/youriptv/app/ui/MainActivity.kt"),
    ).first { it.exists() }.readText()

    @Test
    fun aboutHasAnUnconditionalPublicRouteSeparateFromSettingsAndPlaylistGuards() {
        val routes = source.substringAfter("private fun onNav(s: Section)")
            .substringBefore("private fun unlockSettings()")
        val about = Regex("(?m)^\\s*Section\\.ABOUT -> (.+)$").find(routes)?.groupValues?.get(1)
        assertNotNull("About needs its own public main-navigation action", about)
        assertEquals("startActivity(Intent(this, AboutActivity::class.java))", about?.trim())
        assertFalse("Public About must not grant Settings authorization", about.orEmpty().contains("putExtra"))
        assertTrue("Parentally protected playlist navigation remains separate", routes.contains("Section.PLAYLISTS -> openLocked(app.settings.isPlaylistsLocked()"))
    }

    @Test
    fun aboutIsIncludedInTheSharedPhoneAndTvNavigationRail() {
        val sections = source.substringAfter("enum class Section(val label: String)").substringBefore("private data class GroupEntry")
        assertTrue(sections.contains("ABOUT(\"About\")"))
        val rail = source.substringAfter("private fun buildNav(").substringBefore("private fun onNav(")
        assertTrue("Both layouts must build every declared navigation entry", rail.contains("for (s in Section.entries)"))
        assertTrue(rail.contains("setOnClickListener { onNav(s) }"))
        assertFalse("About must not be hidden for either phone or TV", rail.contains("Section.ABOUT"))
    }

    @Test
    fun enteringSettingsDoesNotIssueAnIntentAuthorizationGrant() {
        val settings = source.substringAfter("private fun unlockSettings()").substringBefore("private fun updateSettingsLabel()")
        assertTrue(settings.contains("startActivity(Intent(this, SettingsActivity::class.java))"))
        assertFalse(settings.contains("putExtra") || settings.contains("EXTRA_UNLOCKED"))
        assertFalse("Settings must authenticate inside the target activity", settings.contains("Dialogs."))
        assertFalse("The legacy disable-lock migration must be gone", source.contains("Turn off Settings lock"))
        assertTrue(source.contains("navViews[Section.SETTINGS]?.text = \"Unlock Settings\""))
    }

    @Test
    fun aboutActivityHasNoPinGateAndExplainsFreeFeaturesAndSettingsProtection() {
        fun ui(name: String) = listOf(File("src/main/java/com/youriptv/app/ui/$name"), File("app/src/main/java/com/youriptv/app/ui/$name"))
            .first { it.exists() }.readText()
        val about = ui("AboutActivity.kt")
        assertFalse(about.contains("requireSettings") || about.contains("requirePin") || about.contains("isSettingsLocked"))
        assertTrue(about.contains("Branding.aboutBody(this)"))
        val body = ui("Branding.kt").substringAfter("fun aboutBody(")
        assertFalse(body.contains("no premium screens, locked settings", ignoreCase = true))
        assertTrue(body.contains("no paid feature locks"))
        assertTrue(body.contains("About is available without a PIN from the main menu."))
        assertTrue(body.contains("OPEN-SOURCE LIBRARIES"))
    }
}
