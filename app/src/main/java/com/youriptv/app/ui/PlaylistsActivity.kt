package com.youriptv.app.ui

import android.content.Intent
import android.os.Bundle
import android.view.View
import android.widget.Button
import androidx.lifecycle.lifecycleScope
import com.youriptv.app.data.Playlist
import com.youriptv.app.data.SourceType
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

/** Lists every configured playlist. There is no limit on how many playlists can be added. */
class PlaylistsActivity : ListActivity() {
    private lateinit var adapter: TextAdapter<Playlist>
    private lateinit var addPlaylist: Button
    private lateinit var editCurrent: Button
    private lateinit var deletePlaylist: Button
    private var busy = false
    private val visit = SettingsVisitAuthorization()
    private val flow get() = app.settingsFlow
    private val route = SettingsFlowAuthorization.Route(SettingsFlowAuthorization.Destination.PLAYLISTS)
    private var protectedVisit = false

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        listTitle.text = "Playlists"
        addPlaylist = addAction("Add playlist") { if (authorizedToken() != null) openEditor(0L) }
        editCurrent = addAction("Edit current") { editCurrentPlaylist() }
        deletePlaylist = addAction("Delete playlist") { chooseDeletePlaylist() }
        adapter = TextAdapter(
            bind = { p -> describe(p) },
            onClick = { p, _ -> activate(p) },
            onOptions = { p, _ -> options(p) },
            highlight = { it.id == app.settings.activePlaylistId },
        )
        list.adapter = adapter
        hideProtectedContent()
    }

    private fun authorizedToken(): Long? = if (isFinishing || isDestroyed ||
        (protectedVisit && !flow.isAuthorized(this))) null else visit.authorizedToken()
    private fun hasAuthorization(token: Long) = authorizedToken() == token && visit.isAuthorized(token)

    private fun hideProtectedContent() {
        list.visibility = View.INVISIBLE
        actions.visibility = View.INVISIBLE
        empty.visibility = View.GONE
        listSubtitle.text = "Checking playlist access…"
    }

    override fun onResume() {
        super.onResume()
        if (isFinishing) return
        protectedVisit = flow.enter(this, route)
        val token = visit.beginVisit()
        val allowed = allowed@{
            if (isFinishing || (protectedVisit && !flow.isAuthorized(this)) || !visit.authorize(token)) return@allowed
            if (protectedVisit && app.settings.isPlaylistsLocked()) flow.allowParental(this)
            list.visibility = View.VISIBLE
            actions.visibility = View.VISIBLE
            setBusy(busy)
            reload()
        }
        if (app.settings.isPlaylistsLocked() && !flow.hasParentalPermission(this)) {
            Dialogs.requirePin(this, "manage playlists", onCancel = { if (visit.isCurrent(token)) finish() }, onOk = allowed)
        } else allowed()
    }

    override fun onPause() {
        hideProtectedContent()
        flow.paused(this)
        visit.endVisit()
        super.onPause()
    }

    override fun onStop() {
        super.onStop()
        if (!isChangingConfigurations && !flow.retains(this) && (protectedVisit || app.settings.isPlaylistsLocked())) finish()
    }

    override fun finish() {
        flow.returning(this)
        super.finish()
    }

    private fun reload() {
        if (authorizedToken() == null) return
        val all = app.repo.playlists()
        adapter.submit(all)
        setEditCurrentEnabled(!busy && all.any { it.id == app.settings.activePlaylistId })
        setEnabled(deletePlaylist, !busy && all.isNotEmpty())
        setEnabled(addPlaylist, !busy)
        if (!busy) listSubtitle.text =
            "${PlaylistEditActivity.SETTINGS_LOCK_MESSAGE} OK selects a playlist; MENU or long-press OK shows more options."
        setEmpty(if (all.isEmpty()) "No playlists yet. Choose \"Add playlist\" for an M3U URL, Xtream Codes login, or YourIPTV username and password." else null)
        if (all.isEmpty()) addPlaylist.requestFocus() else list.post { if (authorizedToken() != null) list.getChildAt(0)?.requestFocus() }
    }

    private fun setEnabled(button: Button, enabled: Boolean) {
        button.isEnabled = enabled
        button.alpha = if (enabled) 1f else 0.5f
    }
    private fun setEditCurrentEnabled(enabled: Boolean) = setEnabled(editCurrent, enabled)

    private fun describe(p: Playlist): Pair<String, String> {
        val type = if (p.type == SourceType.XTREAM) "Xtream Codes" else "M3U"
        val parts = mutableListOf(type)
        if (p.lastUpdated > 0) {
            val seriesLabel = if (p.type == SourceType.XTREAM) "series" else "series episodes"
            parts += "${p.liveCount} channels · ${p.movieCount} movies · ${p.seriesCount} $seriesLabel"
            parts += "updated ${Format.dayTime(p.lastUpdated)}"
        } else parts += "not loaded yet"
        val acct = mutableListOf<String>()
        if (p.accountStatus.isNotBlank()) acct += "Provider account: ${p.accountStatus}"
        if (p.expiresAt > 0) acct += "provider expiry ${java.text.DateFormat.getDateInstance(java.text.DateFormat.MEDIUM).format(java.util.Date(p.expiresAt))}"
        if (p.maxConnections > 0) acct += "provider allows ${p.maxConnections} simultaneous stream${if (p.maxConnections == 1) "" else "s"}"
        val epg = app.settings.epgUpdated(p.id).let { if (it > 0) "guide updated ${Format.dayTime(it)}" else "guide not downloaded" }
        val active = if (p.id == app.settings.activePlaylistId) "ACTIVE · " else ""
        return p.name to (active + parts.joinToString(" · ") + "\n" + (acct + epg).joinToString(" · "))
    }

    private fun activate(p: Playlist) {
        if (busy || authorizedToken() == null || app.repo.playlist(p.id) == null) return
        app.settings.activePlaylistId = p.id
        Dialogs.toast(this, "\"${p.name}\" is now the active playlist")
        if (p.lastUpdated == 0L) refresh(p, full = true) else finish()
    }

    private fun editCurrentPlaylist() {
        if (busy || authorizedToken() == null) return
        val p = app.repo.playlist(app.settings.activePlaylistId)
        if (p == null) {
            Dialogs.toast(this, "No active playlist. Select one first.")
            reload()
        } else edit(p)
    }

    private fun openEditor(id: Long) {
        if (busy || authorizedToken() == null) return
        val intent = if (id == 0L) Intent(this, PlaylistEditActivity::class.java) else PlaylistEditActivity.editIntent(this, id)
        if (flow.isAuthorized(this)) flow.launch(this,
            SettingsFlowAuthorization.Route(SettingsFlowAuthorization.Destination.EDITOR, id), intent)
        else startActivity(intent)
    }

    private fun edit(p: Playlist) = openEditor(p.id)

    private fun options(p: Playlist) {
        val token = authorizedToken() ?: return
        if (busy) return
        val opts = listOf("Make active", "Edit", "Refresh playlist and guide", "Update guide (EPG) only", "Delete")
        Dialogs.choose(this, p.name, opts) { i ->
            if (!hasAuthorization(token) || busy) return@choose
            when (i) {
                0 -> activate(p)
                1 -> edit(p)
                2 -> refresh(p, full = true)
                3 -> refresh(p, full = false)
                4 -> requestDelete(p)
            }
        }
    }

    private fun chooseDeletePlaylist() {
        val token = authorizedToken() ?: return
        if (busy) return
        val choices = app.repo.playlists()
        if (choices.isEmpty()) return
        Dialogs.choose(this, "Delete which playlist?", choices.map { it.name }) { index ->
            if (hasAuthorization(token) && !busy) choices.getOrNull(index)?.let { requestDelete(it) }
        }
    }

    private fun requestDelete(selected: Playlist) {
        val token = authorizedToken() ?: return
        if (busy) return
        if (flow.isAuthorized(this)) confirmDelete(selected, token)
        else Dialogs.requireSettingsPin(this, "delete playlist") {
            if (!hasAuthorization(token) || busy) return@requireSettingsPin
            flow.authenticate(this, route)
            if (app.settings.isPlaylistsLocked()) flow.allowParental(this)
            protectedVisit = true
            confirmDelete(selected, token)
        }
    }

    private fun confirmDelete(selected: Playlist, visitToken: Long) {
        val settingsToken = flow.token(this) ?: return
        if (!hasAuthorization(visitToken) || busy) return
        if (app.repo.playlist(selected.id) != selected) { staleDelete(); return }
        Dialogs.confirm(this, "Delete playlist", "Delete \"${selected.name}\" and its catalog, guide, favorites, history, reminders and pending recordings from this device? Finished recordings are kept. This cannot be undone.") {
            val authorized = hasAuthorization(visitToken) && flow.isAuthorized(this, settingsToken)
            val current = if (authorized && !busy) app.repo.playlist(selected.id) else null
            if (!PlaylistDeletion.mayDelete(selected, current, busy, authorized)) { if (authorized && !busy) staleDelete(); return@confirm }
            val ownsLast = app.repo.playlistHasUrl(selected.id, app.settings.lastChannelUrl)
            app.repo.deletePlaylist(selected.id)
            app.settings.forgetPlaylist(selected.id, ownsLast)
            app.settings.activePlaylistId = PlaylistDeletion.nextActive(app.settings.activePlaylistId, selected.id, app.repo.playlists())
            reload()
        }
    }

    private fun staleDelete() {
        Dialogs.toast(this, "This playlist changed or no longer exists. Choose it again before deleting.")
        reload()
    }

    private fun refresh(p: Playlist, full: Boolean) {
        val token = authorizedToken() ?: return
        if (busy || app.repo.playlist(p.id) == null) return
        busy = true
        setEditCurrentEnabled(false)
        setEnabled(deletePlaylist, false)
        setEnabled(addPlaylist, false)
        setBusy(true, "Updating \"${p.name}\"…")
        val onProgress: (String) -> Unit = { msg -> runOnUiThread { if (hasAuthorization(token)) listSubtitle.text = msg } }
        lifecycleScope.launch {
            val result = withContext(Dispatchers.IO) {
                try {
                    Result.success(if (full) Refresher.refreshAll(p, onProgress) else Refresher.refreshEpg(p, onProgress))
                } catch (e: Throwable) { Result.failure(e) }
            }
            busy = false
            if (!hasAuthorization(token)) {
                // An old operation can release the busy indicator, never inherit a new visit's authority.
                if (authorizedToken() != null) { setBusy(false); reload() }
                return@launch
            }
            setBusy(false)
            result.onSuccess { warning ->
                listSubtitle.text = warning ?: "\"${p.name}\" updated."
                if (warning != null) Dialogs.toast(this@PlaylistsActivity, warning)
            }.onFailure { e ->
                listSubtitle.text = "Update failed: ${Nav.describeError(e)}"
                Dialogs.toast(this@PlaylistsActivity, Nav.describeError(e))
            }
            reload()
        }
    }
}
