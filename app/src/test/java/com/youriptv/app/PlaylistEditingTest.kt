package com.youriptv.app

import org.junit.Assert.*
import org.junit.Test
import org.w3c.dom.Element
import java.io.File
import javax.xml.parsers.DocumentBuilderFactory

class PlaylistEditingTest {
    private fun source(path: String) = listOf(
        File("src/main/$path"),
        File("app/src/main/$path"),
    ).first { it.exists() }.readText()

    private fun ui(name: String) = source("java/com/youriptv/app/ui/$name")

    private fun layoutView(layout: String, id: String): Element {
        val xml = DocumentBuilderFactory.newInstance().newDocumentBuilder()
            .parse(source("res/layout/$layout").byteInputStream())
        val nodes = xml.getElementsByTagName("*")
        for (i in 0 until nodes.length) {
            val element = nodes.item(i)
            if (element is Element && element.getAttribute("android:id") == "@+id/$id") return element
        }
        throw AssertionError("Missing $id in $layout")
    }

    @Test
    fun currentPlaylistShortcutUsesTheActiveIdAndKeepsPerPlaylistEdit() {
        val playlists = ui("PlaylistsActivity.kt")
        assertTrue(playlists.contains("addAction(\"Edit current\") { editCurrentPlaylist() }"))
        assertTrue(playlists.contains("setEditCurrentEnabled(!busy && all.any { it.id == app.settings.activePlaylistId })"))
        val current = playlists.substringAfter("private fun editCurrentPlaylist()").substringBefore("private fun edit(")
        assertTrue(current.contains("app.repo.playlist(app.settings.activePlaylistId)"))
        assertTrue(current.contains("No active playlist. Select one first."))
        assertTrue(current.contains("edit(p)"))
        assertTrue(playlists.contains("1 -> edit(p)"))
        assertTrue(playlists.contains("private fun edit(p: Playlist) = openEditor(p.id)"))
        assertTrue(playlists.contains("PlaylistEditActivity.editIntent(this, id)"))
        assertTrue(playlists.contains("if (flow.isAuthorized(this)) flow.launch(this,"))
    }

    @Test
    fun settingsShortcutUsesAnAuthorizedVisitAndTargetEditorEnforcesBothPermissions() {
        val settings = ui("SettingsActivity.kt")
        val entries = settings.substringAfter("SettingsCategory.PLAYLISTS -> listOf(")
            .substringBefore("SettingsCategory.PLAYBACK")
        assertTrue(entries.contains("Entry(\"Edit current playlist\""))
        assertTrue(entries.contains("editCurrentPlaylist()"))
        val handler = settings.substringAfter("private fun editCurrentPlaylist()")
            .substringBefore("private fun restoreFromFolder()")
        assertTrue(handler.contains("if (!visit.isAuthorized) return"))
        assertTrue(handler.contains("PlaylistEditActivity.editIntent(this, current.id)"))
        assertTrue(handler.contains("flow.launch(this, SettingsFlowAuthorization.Route(SettingsFlowAuthorization.Destination.EDITOR, current.id)"))
        assertEquals(2, Regex("app\\.repo\\.playlist\\(s\\.activePlaylistId\\)").findAll(handler).count())
        assertTrue(settings.contains("if (s.isPlaylistsLocked()) Dialogs.requirePin(this, reason)"))
        val editor = ui("PlaylistEditActivity.kt")
        val authentication = editor.substringAfter("override fun onResume()").substringBefore("override fun onPause()")
        assertTrue(authentication.contains("app.settings.isPlaylistsLocked()"))
        assertTrue(authentication.contains("Dialogs.requirePin"))
        assertTrue(authentication.contains("Dialogs.requireSettingsPin"))
        assertTrue(authentication.contains("authorization.allowSettings(visit)"))
        assertTrue(authentication.contains("inheritedSettings = flow.enter(this, editorRoute)"))
        assertTrue(authentication.contains("if (!flow.isAuthorized(this)) return@afterParental"))
        assertTrue(authentication.contains("!flow.hasParentalPermission(this)"))
    }

    @Test
    fun editorLoadsAndSavesTheExistingPlaylistFields() {
        val editor = ui("PlaylistEditActivity.kt")
        assertTrue(editor.contains("name.setText(p.name); url.setText(p.url); user.setText(p.username); pass.setText(p.password)"))
        assertTrue(editor.contains("epg.setText(p.epgUrl); ua.setText(p.userAgent)"))
        assertTrue(editor.contains("id = base?.id ?: 0L"))
        assertTrue(editor.contains("username = if (t == SourceType.XTREAM) username else \"\""))
        assertTrue(editor.contains("password = if (t == SourceType.XTREAM) password else \"\""))
        assertTrue(editor.contains("base.url != p.url || base.username != p.username || base.password != p.password"))
        val layout = source("res/layout/activity_playlist_edit.xml")
        assertTrue(Regex("android:id=\"@\\+id/f_pass\"[^>]*android:inputType=\"textPassword\"").containsMatchIn(layout))
        val repository = source("java/com/youriptv/app/data/Repository.kt")
        listOf("url", "username", "password", "epg_url", "user_agent").forEach {
            assertTrue("The $it field must be persisted", repository.contains("put(\"$it\","))
        }
    }

    @Test
    fun missingOrDeletedPlaylistsDoNotBecomeNewOrSuccessfulEdits() {
        val editor = ui("PlaylistEditActivity.kt")
        val open = editor.substringAfter("private fun loadAuthorizedPlaylist()").substringBefore("editing?.let")
        assertTrue(open.indexOf("if (!authorization.isAuthorized") in 0 until open.indexOf("app.repo.playlist(id)"))
        assertTrue(open.contains("id != 0L && editing == null"))
        assertTrue(open.contains("This playlist no longer exists."))
        assertTrue(open.contains("finish()"))
        val save = editor.substringAfter("val id = app.repo.savePlaylist(p)").substringBefore("lifecycleScope.launch")
        assertTrue(save.contains("if (id <= 0)"))
        assertTrue(save.contains("if (saved == null)"))
        val repository = source("java/com/youriptv/app/data/Repository.kt")
        assertTrue(repository.contains("else if (db.update(\"playlists\", v, \"id=?\", arrayOf(p.id.toString())) > 0) p.id else -1L"))
    }

    @Test
    fun actionRowsCanScrollOnNarrowScreens() {
        val actions = layoutView("activity_list.xml", "list_actions")
        assertEquals("HorizontalScrollView", actions.parentNode.nodeName)
        listOf("type_xtream", "type_m3u", "type_youriptv", "btn_save", "btn_cancel").forEach { id ->
            val row = layoutView("activity_playlist_edit.xml", id).parentNode
            assertEquals("LinearLayout", row.nodeName)
            assertEquals("HorizontalScrollView", row.parentNode.nodeName)
        }
    }

    @Test
    fun addingChoicesHaveTheRequestedOrderAndPasswordStaysMasked() {
        val layout = source("res/layout/activity_playlist_edit.xml")
        assertTrue(layout.indexOf("@+id/type_m3u") < layout.indexOf("@+id/type_xtream"))
        assertTrue(layout.indexOf("@+id/type_xtream") < layout.indexOf("@+id/type_youriptv"))
        assertTrue(ui("PlaylistEditActivity.kt").contains("btnYourIptv.text = YourIptvLogin.PLAYLIST_NAME"))
        assertFalse(layoutView("activity_playlist_edit.xml", "type_youriptv").hasAttribute("android:text"))
        assertEquals("textPassword", layoutView("activity_playlist_edit.xml", "f_pass").getAttribute("android:inputType"))
        assertEquals("name_fields", layoutView("activity_playlist_edit.xml", "f_name").parentNode.attributes.getNamedItem("android:id").nodeValue.removePrefix("@+id/"))
        assertEquals("server_fields", layoutView("activity_playlist_edit.xml", "f_url").parentNode.attributes.getNamedItem("android:id").nodeValue.removePrefix("@+id/"))
        listOf("f_epg", "f_ua").forEach {
            assertEquals("@+id/optional_fields", layoutView("activity_playlist_edit.xml", it).parentNode.attributes.getNamedItem("android:id").nodeValue)
        }
    }

    @Test
    fun yourIptvIsAddingOnlyAndHidesEveryNonCredentialFieldTogetherWithItsLabel() {
        val editor = ui("PlaylistEditActivity.kt")
        assertTrue(editor.contains("btnYourIptv.setOnClickListener { setType(SourceType.XTREAM, simplified = true) }"))
        assertTrue(editor.contains("btnYourIptv.visibility = if (editing == null) View.VISIBLE else View.GONE"))
        assertTrue(editor.contains("yourIptvMode = simplified && editing == null && t == SourceType.XTREAM"))
        val selection = editor.substringAfter("private fun setType(").substringBefore("private fun save()")
        assertTrue(selection.contains("val fullFields = if (yourIptvMode) View.GONE else View.VISIBLE"))
        listOf("name_fields", "server_fields", "optional_fields").forEach {
            assertTrue(selection.contains("findViewById<View>(R.id.$it).visibility = fullFields"))
        }
        assertTrue(selection.contains("xtreamFields.visibility = if (t == SourceType.XTREAM) View.VISIBLE else View.GONE"))
        assertTrue(editor.contains("setType(type, simplified = yourIptvMode)"))
    }

    @Test
    fun shortcutExcludesHiddenValuesAndUsesTheSameAuthorizedSaveAndImportPath() {
        val editor = ui("PlaylistEditActivity.kt")
        val save = editor.substringAfter("private fun save()").substringBefore("private fun savePlaylist(")
        val shortcut = save.substringAfter("if (yourIptvMode)").substringBefore("var t = type")
        assertTrue(shortcut.contains("YourIptvLogin.newPlaylist(user.text.toString(), pass.text.toString())"))
        listOf("name", "url", "epg", "ua").forEach { assertFalse(shortcut.contains("$it.text")) }
        assertTrue(shortcut.contains("if (p == null)"))
        assertTrue(shortcut.contains("savePlaylist(p)"))
        val persist = editor.substringAfter("private fun savePlaylist(").substringBefore("companion object")
        assertTrue(persist.contains("if (busy || isFinishing || !authorization.isAuthorized) return"))
        assertTrue(persist.contains("val keepPrevious = previousActive != id && app.repo.playlist(previousActive) != null"))
        assertTrue(persist.contains("if (!keepPrevious) app.settings.activePlaylistId = id"))
        assertTrue(persist.contains("Refresher.refreshAll(saved)"))
    }
}
