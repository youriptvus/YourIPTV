package com.youriptv.app

import org.junit.Assert.*
import org.junit.Test
import java.io.File

/** Source wiring complements the executable PIN-policy and refresh-coordination tests. */
class CustomerSettingsSecurityTest {
    private val ui = listOf(File("src/main/java/com/youriptv/app/ui"), File("app/src/main/java/com/youriptv/app/ui")).first { it.exists() }

    @Test
    fun publicPanelUsesOnlyRefreshCapabilitiesAndStaysInTheCurrentActivity() {
        val source = File(ui, "CustomerSettingsPanel.kt").readText()
        assertTrue(source.contains("setTitle(\"Settings\")"))
        assertTrue(source.contains("setTitle(\"Playlist Settings\")"))
        assertTrue(source.contains("button(\"Update Playlist\")"))
        assertTrue(source.contains("button(\"Update EPG\")"))
        assertTrue(source.contains("requestPlaylist(playlistId)"))
        assertTrue(source.contains("requestEpg(playlistId)"))
        assertTrue(source.contains("states.collect { render() }"))
        assertTrue(source.contains("isFocusable = true"))
        assertTrue(source.contains("updatePlaylist.nextFocusDownId = updateEpg.id"))
        val refreshPanel = source.substringAfter("private fun showPlaylistSettings(").substringBefore("private fun dismissWhenStopped(")
        for (forbidden in listOf("requireSettingsPin", "requirePin(", "flow.authenticate", "allowParental", "startActivity(",
            "PlaylistEditActivity", "setRefreshHours", "deletePlaylist", "livePlayback.", "password", "username", "epgUrl")) {
            assertFalse("Public refresh must not acquire the capability: $forbidden", refreshPanel.contains(forbidden))
        }
    }

    @Test
    fun customerMenuOwnsParentalNavigationWhileProtectedConfigurationKeepsItsExactLabel() {
        val menu = File(ui, "CustomerSettingsPanel.kt").readText().substringBefore("private fun showPlaylistSettings(")
        assertTrue(menu.contains("arrayOf(\"Playlist Settings\", \"Parental Controls\", \"Playback\")"))
        assertFalse("EPG preferences belong to the protected Settings visit", menu.contains("EpgSettingsPanel"))
        assertTrue(menu.contains("activity.startActivity(Intent(activity, ParentalControlsActivity::class.java))"))
        for (forbidden in listOf("SettingsActivity::class.java", "flow.authenticate", "allowParental", "requireSettingsPin"))
            assertFalse("Choosing a customer destination must not unlock protected settings", menu.contains(forbidden))
        val main = File(ui, "MainActivity.kt").readText()
        assertFalse(main.contains("Section.PARENTAL"))
        assertTrue(main.contains("ADMIN(\"Unlock Settings\")"))
        assertTrue(main.contains("navViews[Section.ADMIN]?.text = \"Unlock Settings\""))
        assertTrue(main.contains("Section.ADMIN -> unlockSettings()"))
    }

    @Test fun publicPlaybackControlsOnlyChangeAutoplayAndDoNotAcquireProtectedCapabilities() {
        val source = File(ui, "CustomerSettingsPanel.kt").readText()
        val playback = source.substringAfter("private fun showPlaybackControls(")
            .substringBefore("// One collector")
        assertTrue(playback.contains("arrayOf(\"Autoplay Next Episode\")"))
        assertTrue(playback.contains("settings.autoPlayNextEpisode = enabled"))
        for (forbidden in listOf("requireSettingsPin", "requireMasterPin", "flow.authenticate", "allowParental",
            "setSettingsLockEnabled", "setCustomPin", "isGroupLocked", "activePlaylistId", "deletePlaylist", "startActivity("))
            assertFalse("Public viewing preference must not acquire $forbidden", playback.contains(forbidden))
    }

    @Test
    fun epgPreferencesRequireTheCurrentProtectedVisitForEveryReadSaveAndUpdate() {
        val page = File(ui, "EpgSettingsPanel.kt").readText()
        assertTrue(page.contains("internal object EpgSettingsPanel"))
        assertTrue(page.contains("fun show(activity: SettingsActivity, playlistId: Long, token: Long)"))
        assertFalse("A public Activity must not open EPG preferences", page.contains("AppCompatActivity"))
        assertTrue(page.contains("if (!activity.canUseEpgSettings(token)) return"))
        val active = page.substringAfter("fun active(): Boolean").substringBefore("fun time(")
        assertTrue(active.contains("activity.canUseEpgSettings(token)"))
        assertTrue(active.contains("if (!allowed && dialog.isShowing) dialog.dismiss()"))
        val render = page.substringAfter("fun render()").substringBefore("fun changed()")
        assertTrue("Authorization must precede status and preference reads",
            render.indexOf("if (!active()) return") in 0 until render.indexOf("app.refreshCoordinator.states.value"))
        assertTrue(page.contains("setTitle(\"EPG Settings\")"))
        assertTrue(page.contains("if (active()) { settings.epgPastHours = EpgSettingsPresentation.hoursForPastDays(days)"))
        assertTrue(page.contains("if (active()) { settings.epgFutureDays = index + 1"))
        assertTrue(page.contains("if (active()) { settings.epgShiftHours = index - 23"))
        assertTrue(page.contains("if (active()) onPick(index)"))
        assertTrue(page.contains("if (!active() || requesting) return@setOnClickListener"))
        assertTrue(page.contains("if (!active()) return@launch"))
        assertTrue(page.contains("Update on app start: Enabled"))
        assertTrue(page.contains("Program descriptions: Stored when supplied by your provider"))
        assertFalse("EPG controls use consistent US spelling", page.contains("programme", ignoreCase = true))
        assertTrue(page.contains("requestEpg(playlistId)"))
        assertTrue(page.contains("states.collect { render() }"))
        assertTrue(page.contains("isFocusable = true"))
        assertTrue(page.contains("override fun onPause(owner: LifecycleOwner) { dialog.dismiss() }"))
        assertTrue(page.contains("picker?.dismiss()"))
        assertTrue("Idle revoked dialogs must close too", page.contains("while (active()) delay(250)"))
        val settings = File(ui, "SettingsActivity.kt").readText()
        assertTrue(settings.contains("internal fun canUseEpgSettings(token: Long) = !pickingFile && hasAuthorization(token)"))
        assertTrue(settings.contains("lifecycle.currentState.isAtLeast(Lifecycle.State.RESUMED)"))
        assertTrue(settings.contains("if (canUseEpgSettings(token)) EpgSettingsPanel.show(this, s.activePlaylistId, token)"))
        for (forbidden in listOf("requirePin(", "requireSettingsPin", "flow.authenticate", "allowParental", "startActivity(",
            "PlaylistEditActivity", "epgUrl", "deletePrograms", "clearEpg", "password", "username"))
            assertFalse("EPG preferences must not create their own grant or source editor: $forbidden", page.contains(forbidden))
    }

    @Test
    fun parentalEntryAndEveryMutationUseTheExistingParentalCheckerWithoutAdminAuthority() {
        val source = File(ui, "ParentalControlsActivity.kt").readText()
        assertTrue(source.contains("Dialogs.requirePin(this, \"open Parental Controls\""))
        assertTrue(source.contains("if (isFinishing || isDestroyed || !visit.authorize(token)) return@requirePin"))
        assertTrue(source.contains("Dialogs.requirePin(this, \"confirm current parental PIN\")"))
        assertTrue(source.contains("Dialogs.requirePin(this, \"edit locked groups\")"))
        assertTrue(source.contains("Dialogs.requirePin(this, \"change visibility\")"))
        assertTrue(source.contains("else Dialogs.requirePin(this, \"change \\\"$" + "t\\\"\") { apply() }"))
        assertTrue(source.contains("canSave = { hasAuthorization(token) }"))
        assertTrue(source.contains("if (hasAuthorization(token)) { s.hideLockedGroups = value; refresh() }"))
        assertTrue(source.contains("visit.endVisit()"))
        assertTrue(source.contains("list.visibility = View.INVISIBLE"))
        for (forbidden in listOf("settingsFlow", "flow.authenticate", "allowParental", "requireSettingsPin", "checkAdmin", "PinKind.SETTINGS")) {
            assertFalse("Parental UI must remain independent of Admin: $forbidden", source.contains(forbidden))
        }
    }
}
