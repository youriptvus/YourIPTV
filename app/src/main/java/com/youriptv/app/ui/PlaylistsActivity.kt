package com.youriptv.app.ui

import android.content.Intent
import android.os.Bundle
import androidx.lifecycle.lifecycleScope
import com.youriptv.app.data.Playlist
import com.youriptv.app.data.SourceType
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

/** Lists every configured playlist. There is no limit on how many playlists can be added. */
class PlaylistsActivity : ListActivity() {

    private lateinit var adapter: TextAdapter<Playlist>
    private var busy = false

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        listTitle.text = "Playlists"
        addAction("Add playlist") { startActivity(Intent(this, PlaylistEditActivity::class.java)) }
        adapter = TextAdapter(
            bind = { p -> describe(p) },
            onClick = { p, _ -> activate(p) },
            onOptions = { p, _ -> options(p) },
            highlight = { it.id == app.settings.activePlaylistId },
        )
        list.adapter = adapter
    }

    override fun onResume() {
        super.onResume()
        reload()
    }

    private fun reload() {
        val all = app.repo.playlists()
        adapter.submit(all)
        if (!busy) listSubtitle.text = "OK selects the active playlist. MENU or long-press OK for edit, refresh, guide update, and delete."
        setEmpty(if (all.isEmpty()) "No playlists yet. Choose \"Add playlist\" to enter an M3U URL or Xtream Codes login." else null)
        if (all.isEmpty()) actions.getChildAt(0)?.requestFocus() else list.post { list.getChildAt(0)?.requestFocus() }
    }

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
        app.settings.activePlaylistId = p.id
        Dialogs.toast(this, "\"${p.name}\" is now the active playlist")
        if (p.lastUpdated == 0L) refresh(p, full = true) else finish()
    }

    private fun options(p: Playlist) {
        val opts = listOf("Make active", "Edit", "Refresh playlist and guide", "Update guide (EPG) only", "Delete")
        Dialogs.choose(this, p.name, opts) { i ->
            when (i) {
                0 -> activate(p)
                1 -> startActivity(Intent(this, PlaylistEditActivity::class.java).putExtra(PlaylistEditActivity.EXTRA_ID, p.id))
                2 -> refresh(p, full = true)
                3 -> refresh(p, full = false)
                4 -> Dialogs.confirm(this, "Delete playlist", "Delete \"${p.name}\" with its favorites, history, and guide data from this device?") {
                    val ownsLast = app.repo.playlistHasUrl(p.id, app.settings.lastChannelUrl)
                    app.repo.deletePlaylist(p.id)
                    app.settings.forgetPlaylist(p.id, ownsLast)
                    if (app.settings.activePlaylistId == p.id) app.settings.activePlaylistId = app.repo.playlists().firstOrNull()?.id ?: -1L
                    reload()
                }
            }
        }
    }

    private fun refresh(p: Playlist, full: Boolean) {
        if (busy) return
        busy = true
        setBusy(true, "Updating \"${p.name}\"…")
        val onProgress: (String) -> Unit = { msg -> runOnUiThread { listSubtitle.text = msg } }
        lifecycleScope.launch {
            val result = withContext(Dispatchers.IO) {
                try {
                    Result.success(if (full) Refresher.refreshAll(p, onProgress) else Refresher.refreshEpg(p, onProgress))
                } catch (e: Throwable) {
                    Result.failure(e)
                }
            }
            busy = false
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
