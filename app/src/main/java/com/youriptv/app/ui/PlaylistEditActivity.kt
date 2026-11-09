package com.youriptv.app.ui

import android.content.Context
import android.content.Intent
import android.os.Bundle
import android.view.View
import android.widget.Button
import android.widget.EditText
import android.widget.TextView
import androidx.lifecycle.lifecycleScope
import com.youriptv.app.R
import com.youriptv.app.YourIptvApp
import com.youriptv.app.data.Playlist
import com.youriptv.app.data.SourceType
import com.youriptv.app.data.XtreamUrls
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

/** Adds or edits an M3U / Xtream Codes playlist. Credentials stay on this device only. */
class PlaylistEditActivity : RemoteActivity() {

    private val app get() = YourIptvApp.instance
    private var editing: Playlist? = null
    private var type = SourceType.XTREAM
    private var busy = false
    private lateinit var editor: View
    private var editorVisit = 0L
    private val playlistId get() = intent.getLongExtra(EXTRA_ID, 0L)
    private val authorization by lazy { PlaylistEditAuthorization(existingPlaylist = playlistId != 0L) }
    /** Set once the user has chosen to keep a get.php link with embedded login as a plain M3U playlist. */
    private var keepAsM3u = false

    private lateinit var name: EditText
    private lateinit var url: EditText
    private lateinit var user: EditText
    private lateinit var pass: EditText
    private lateinit var epg: EditText
    private lateinit var ua: EditText
    private lateinit var status: TextView
    private lateinit var urlLabel: TextView
    private lateinit var xtreamFields: View
    private lateinit var btnXtream: Button
    private lateinit var btnM3u: Button
    private lateinit var btnSave: Button

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_playlist_edit)
        editor = findViewById(android.R.id.content)
        // Provider credentials must never reappear from Android's saved view hierarchy before a PIN check.
        editor.isSaveFromParentEnabled = false
        editor.visibility = View.INVISIBLE
        findViewById<android.widget.TextView>(R.id.edit_free_notice).text = Branding.freeNotice(this)
        name = findViewById(R.id.f_name)
        url = findViewById(R.id.f_url)
        user = findViewById(R.id.f_user)
        pass = findViewById(R.id.f_pass)
        epg = findViewById(R.id.f_epg)
        ua = findViewById(R.id.f_ua)
        status = findViewById(R.id.edit_status)
        urlLabel = findViewById(R.id.l_url)
        xtreamFields = findViewById(R.id.xtream_fields)
        btnXtream = findViewById(R.id.type_xtream)
        btnM3u = findViewById(R.id.type_m3u)
        btnSave = findViewById(R.id.btn_save)
        listOf(name, url, user, pass, epg, ua).forEach { it.isSaveEnabled = false }

        btnXtream.setOnClickListener { setType(SourceType.XTREAM) }
        btnM3u.setOnClickListener { setType(SourceType.M3U) }
        btnSave.setOnClickListener { save() }
        findViewById<Button>(R.id.btn_cancel).setOnClickListener { finish() }
    }

    override fun onRestoreInstanceState(savedInstanceState: Bundle) {
        super.onRestoreInstanceState(Bundle(savedInstanceState).apply { remove("android:viewHierarchyState") })
    }

    private fun loadAuthorizedPlaylist() {
        if (!authorization.isAuthorized || isFinishing) return
        val id = playlistId
        editing = if (id != 0L) app.repo.playlist(id) else null
        if (id != 0L && editing == null) {
            Dialogs.toast(this, "This playlist no longer exists.")
            finish()
            return
        }
        editing?.let { p ->
            findViewById<TextView>(R.id.edit_title).text = "Edit playlist"
            name.setText(p.name); url.setText(p.url); user.setText(p.username); pass.setText(p.password)
            epg.setText(p.epgUrl); ua.setText(p.userAgent)
            type = p.type
        }
        setType(type)
        btnSave.isEnabled = !busy
        editor.visibility = View.VISIBLE
        (if (editing == null) btnXtream else name).requestFocus()
    }

    override fun onResume() {
        super.onResume()
        if (isFinishing) return
        val visit = authorization.beginVisit().also { editorVisit = it }
        val afterParental = afterParental@{
            if (!authorization.allowParental(visit) || isFinishing) return@afterParental
            if (playlistId == 0L) loadAuthorizedPlaylist()
            else Dialogs.requireSettingsPin(this, "edit playlist connection", onCancel = {
                if (authorization.isCurrent(visit)) finish()
            }) {
                if (authorization.allowSettings(visit)) loadAuthorizedPlaylist()
            }
        }
        if (app.settings.isPlaylistsLocked()) {
            Dialogs.requirePin(this, "add or edit playlists", onCancel = {
                if (authorization.isCurrent(visit)) finish()
            }, onOk = afterParental)
        } else afterParental()
    }

    override fun onPause() {
        authorization.endVisit()
        if (::editor.isInitialized) {
            editor.visibility = View.INVISIBLE
            if (playlistId != 0L || app.settings.isPlaylistsLocked()) {
                listOf(name, url, user, pass, epg, ua).forEach { it.text.clear() }
                editing = null
                keepAsM3u = false
                type = SourceType.XTREAM
                status.text = ""
                // Close a protected visit rather than retaining an old PIN dialog underneath a new one.
                if (!isChangingConfigurations) finish()
            }
        }
        super.onPause()
    }

    private fun setType(t: SourceType) {
        type = t
        btnXtream.isActivated = t == SourceType.XTREAM
        btnM3u.isActivated = t == SourceType.M3U
        xtreamFields.visibility = if (t == SourceType.XTREAM) View.VISIBLE else View.GONE
        urlLabel.text = if (t == SourceType.XTREAM) "Server URL (for example http://provider.example:8080)" else "M3U / M3U Plus playlist URL"
        url.hint = if (t == SourceType.XTREAM) "http://provider.example:8080" else "http://provider.example/playlist.m3u"
    }

    private fun save() {
        if (busy || isFinishing || !authorization.isAuthorized) return
        var t = type
        var server = url.text.toString().trim()
        var username = user.text.toString().trim()
        var password = pass.text.toString()
        if (server.isEmpty()) { status.text = "Enter the ${if (t == SourceType.XTREAM) "server" else "playlist"} URL."; url.requestFocus(); return }

        // A pasted get.php link with embedded login can be loaded as an Xtream account (richer data: VOD info,
        // series, account status, catch-up) or kept as a plain M3U playlist; the user decides once.
        if (t == SourceType.M3U && !keepAsM3u && (editing?.type != SourceType.M3U || editing?.url != server)) {
            if (XtreamUrls.parseLogin(server) != null) {
                val visit = editorVisit
                Dialogs.choose(this, "This link contains an Xtream Codes login", listOf(
                    "Use as Xtream Codes (recommended: series, account status, catch-up)",
                    "Keep as M3U playlist",
                )) { i ->
                    if (!authorization.isCurrent(visit) || !authorization.isAuthorized) return@choose
                    if (i == 0) setType(SourceType.XTREAM) else keepAsM3u = true
                    save()
                }
                return
            }
        }
        if (t == SourceType.XTREAM) {
            XtreamUrls.parseLogin(server)?.let { login -> server = login.base; username = login.username; password = login.password }
            server = XtreamUrls.normalizeBase(server)
            if (username.isEmpty() || password.isEmpty()) { status.text = "Enter the username and password from your IPTV provider."; return }
        } else if (!server.startsWith("http://", true) && !server.startsWith("https://", true)) {
            server = "http://$server"
        }
        val displayName = name.text.toString().trim().ifEmpty {
            runCatching { java.net.URI(server).host }.getOrNull() ?: "Playlist"
        }
        val base = editing
        val p = Playlist(
            id = base?.id ?: 0L, name = displayName, type = t, url = server,
            username = if (t == SourceType.XTREAM) username else "", password = if (t == SourceType.XTREAM) password else "",
            epgUrl = epg.text.toString().trim(), userAgent = ua.text.toString().trim(),
        )
        val sourceChanged = base == null || base.type != p.type || base.url != p.url || base.username != p.username || base.password != p.password
        val previousActive = app.settings.activePlaylistId
        val id = app.repo.savePlaylist(p)
        if (id <= 0) { status.text = "Could not save the playlist."; return }
        val saved = app.repo.playlist(id)
        if (saved == null) { status.text = "Playlist no longer exists. Return to Playlists and try again."; return }
        if (!sourceChanged && base?.epgUrl == p.epgUrl) { app.settings.activePlaylistId = id; finish(); return }
        // Keep the current working playlist active until the saved one actually loads.
        val keepPrevious = previousActive != id && app.repo.playlist(previousActive) != null
        if (!keepPrevious) app.settings.activePlaylistId = id

        busy = true
        btnSave.isEnabled = false
        status.text = "Connecting to provider…"
        lifecycleScope.launch {
            val result = withContext(Dispatchers.IO) {
                runCatching { Refresher.refreshAll(saved) { msg -> runOnUiThread { status.text = msg } } }
            }
            busy = false
            btnSave.isEnabled = true
            result.onSuccess { warning ->
                app.settings.activePlaylistId = id
                val fresh = app.repo.playlist(id)
                val summary = fresh?.let { "Loaded ${it.liveCount} channels, ${it.movieCount} movies, ${it.seriesCount} series." }.orEmpty()
                Dialogs.toast(this@PlaylistEditActivity, listOfNotNull(summary, warning).joinToString("\n"))
                finish()
            }.onFailure { e ->
                val kept = if (keepPrevious) " Your active playlist was not changed." else ""
                status.text = "Saved, but loading failed: ${Nav.describeError(e)}$kept\nCheck the details and choose Save again, or refresh later from Playlists."
            }
        }
    }

    companion object {
        const val EXTRA_ID = "playlist_id"
        const val SETTINGS_LOCK_MESSAGE = "Editing an existing playlist requires the Settings PIN."

        fun editIntent(context: Context, id: Long): Intent =
            Intent(context, PlaylistEditActivity::class.java).putExtra(EXTRA_ID, id)
    }
}
