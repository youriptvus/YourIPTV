package com.youriptv.app

import org.junit.Assert.*
import org.junit.Test
import java.io.File
import javax.xml.parsers.DocumentBuilderFactory
import org.w3c.dom.Element

/** Integration guards complement the behavioral RAM-capability and deletion-policy fixtures. */
class PlaylistManagementNavigationTest {
    private fun source(path: String) = listOf(File("src/main/$path"), File("app/src/main/$path")).first { it.exists() }.readText()
    private fun ui(name: String) = source("java/com/youriptv/app/ui/$name.kt")

    @Test fun settingsManagementRoutesUseCapabilitiesAndActivitiesRemainPrivate() {
        val settings = ui("SettingsActivity")
        assertTrue(settings.contains("Entry(\"Manage playlists\""))
        assertTrue(settings.contains("flow.launch(this, SettingsFlowAuthorization.Route(SettingsFlowAuthorization.Destination.PLAYLISTS)"))
        val flow = ui("SettingsFlow")
        assertTrue(flow.contains("activity.intent.removeExtra(EXTRA_HANDOFF)"))
        assertTrue(flow.contains("authorization.forward(activity, route) ?: return false"))
        assertFalse(flow.contains("getBooleanExtra"))
        assertFalse(flow.contains("getSharedPreferences"))
        assertFalse(flow.contains("putString(EXTRA_HANDOFF"))
        val manifest = source("AndroidManifest.xml")
        for (name in listOf("SettingsActivity", "PlaylistsActivity", "PlaylistEditActivity")) {
            val activity = Regex("<activity[^>]*android:name=\"\\.ui\\.$name\"[^>]*/>").find(manifest)?.value
            assertNotNull(name, activity)
            assertTrue(name, activity!!.contains("android:exported=\"false\""))
        }
    }

    @Test fun backgroundScreenOffAndNonProtectedScreensRevokeRamAuthorization() {
        val flow = ui("SettingsFlow")
        assertTrue(flow.contains("Intent.ACTION_SCREEN_OFF) authorization.revoke()"))
        assertTrue(flow.contains("if (started == 0) authorization.revoke()"))
        assertTrue(flow.contains("activity !is SettingsActivity && activity !is PlaylistsActivity && activity !is PlaylistEditActivity"))
        for (name in listOf("SettingsActivity", "PlaylistsActivity", "PlaylistEditActivity")) {
            val pause = ui(name).substringAfter("override fun onPause()").substringBefore("super.onPause()")
            assertTrue(name, pause.contains("flow.paused(this)"))
        }
    }

    @Test fun managerOwnsItsParentalGateIncludingRestorationAndDoesNotDuplicateMainGate() {
        val main = ui("MainActivity")
        assertTrue(main.contains("Section.PLAYLISTS -> startActivity(Intent(this, PlaylistsActivity::class.java))"))
        val manager = ui("PlaylistsActivity")
        val resume = manager.substringAfter("override fun onResume()").substringBefore("override fun onPause()")
        assertTrue(resume.contains("app.settings.isPlaylistsLocked() && !flow.hasParentalPermission(this)"))
        assertTrue(resume.contains("Dialogs.requirePin"))
        assertTrue(resume.contains("visit.authorize(token)"))
        assertTrue(resume.indexOf("visit.authorize(token)") < resume.indexOf("list.visibility = View.VISIBLE"))
    }

    @Test fun visibleAndContextDeletionShareRevalidatedSettingsProtectedConfirmation() {
        val manager = ui("PlaylistsActivity")
        assertTrue(manager.contains("addAction(\"Delete playlist\") { chooseDeletePlaylist() }"))
        assertTrue(manager.contains("4 -> requestDelete(p)"))
        assertTrue(manager.contains("choices.getOrNull(index)?.let { requestDelete(it) }"))
        assertTrue(manager.contains("Dialogs.requireSettingsPin(this, \"delete playlist\")"))
        assertTrue(manager.contains("flow.isAuthorized(this, settingsToken)"))
        assertTrue(manager.contains("PlaylistDeletion.mayDelete(selected, current, busy, authorized)"))
        assertTrue(manager.contains("app.repo.deletePlaylist(selected.id)"))
        assertTrue(manager.contains("app.settings.forgetPlaylist(selected.id, ownsLast)"))
        assertTrue(manager.contains("PlaylistDeletion.nextActive"))
        assertTrue(manager.contains("setEnabled(deletePlaylist, false)"))
        val confirmation = ui("UiUtil").substringAfter("fun confirm(").substringBefore("fun input(")
        assertTrue(confirmation.contains("setNegativeButton(\"Cancel\", null)"))
        assertTrue(confirmation.contains("getButton(AlertDialog.BUTTON_NEGATIVE)?.requestFocus()"))
    }

    @Test fun passwordVisibilityChangesDisplayOnlyAndRemasksOnPauseAndModeSwitch() {
        val editor = ui("PlaylistEditActivity")
        val toggle = editor.substringAfter("private fun setPasswordVisible(").substringBefore("private fun setType(")
        assertTrue(toggle.contains("pass.transformationMethod = if (visible) null else PasswordTransformationMethod.getInstance()"))
        assertTrue(toggle.contains("val start = pass.selectionStart"))
        assertTrue(toggle.contains("val end = pass.selectionEnd"))
        assertTrue(toggle.contains("pass.setSelection("))
        assertFalse(toggle.contains("pass.setText("))
        assertFalse(toggle.contains("pass.text.clear()"))
        assertTrue(toggle.contains("\"Hide password\" else \"Show password\""))
        val pause = editor.substringAfter("override fun onPause()").substringBefore("override fun finish()")
        assertTrue(pause.contains("setPasswordVisible(false)"))
        assertTrue(editor.substringAfter("private fun setType(").substringBefore("private fun save()").contains("setPasswordVisible(false)"))
        val xml = DocumentBuilderFactory.newInstance().newDocumentBuilder().parse(source("res/layout/activity_playlist_edit.xml").byteInputStream())
        val elements = xml.getElementsByTagName("*")
        val views = (0 until elements.length).map { elements.item(it) }.filterIsInstance<Element>()
        val password = views.single { it.getAttribute("android:id") == "@+id/f_pass" }
        assertEquals("textPassword", password.getAttribute("android:inputType"))
        val button = views.single { it.getAttribute("android:id") == "@+id/btn_password_visibility" }
        assertEquals("Show password", button.getAttribute("android:text"))
        assertEquals("Show password", button.getAttribute("android:contentDescription"))
        assertTrue(editor.contains("listOf(name, url, user, pass, epg, ua).forEach { it.isSaveEnabled = false }"))
    }
}
