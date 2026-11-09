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
        assertTrue(playlists.contains("PlaylistEditActivity.editIntent(this, p.id)"))
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
        assertEquals(2, Regex("app\\.repo\\.playlist\\(s\\.activePlaylistId\\)").findAll(handler).count())
        assertTrue(settings.contains("if (s.isPlaylistsLocked()) Dialogs.requirePin(this, reason)"))
        val editor = ui("PlaylistEditActivity.kt")
        val authentication = editor.substringAfter("override fun onResume()").substringBefore("override fun onPause()")
        assertTrue(authentication.contains("app.settings.isPlaylistsLocked()"))
        assertTrue(authentication.contains("Dialogs.requirePin"))
        assertTrue(authentication.contains("Dialogs.requireSettingsPin"))
        assertTrue(authentication.contains("authorization.allowSettings(visit)"))
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
        listOf("type_xtream", "type_m3u", "btn_save", "btn_cancel").forEach { id ->
            val row = layoutView("activity_playlist_edit.xml", id).parentNode
            assertEquals("LinearLayout", row.nodeName)
            assertEquals("HorizontalScrollView", row.parentNode.nodeName)
        }
    }
}
