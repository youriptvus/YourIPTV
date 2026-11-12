package com.youriptv.app.ui

import android.content.ActivityNotFoundException
import android.content.Intent
import android.net.Uri
import android.os.Bundle
import android.view.View
import androidx.activity.OnBackPressedCallback
import androidx.activity.result.contract.ActivityResultContracts
import androidx.core.view.doOnNextLayout
import androidx.lifecycle.lifecycleScope
import androidx.recyclerview.widget.LinearLayoutManager
import com.youriptv.app.BuildConfig
import com.youriptv.app.data.BackupData
import com.youriptv.app.data.BackupException
import com.youriptv.app.data.BackupManager
import com.youriptv.app.data.BackupRestore
import com.youriptv.app.data.ItemKind
import com.youriptv.app.data.PinKind
import com.youriptv.app.updates.AppUpdates
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

internal enum class SettingsCategory(val title: String, val description: String) {
    GENERAL("General", "Startup and viewing history"),
    PLAYLISTS("Playlists", "Edit the current playlist and automatic updates"),
    EPG("TV Guide / EPG", "Programme storage, time shift, and guide updates"),
    PLAYBACK("Playback", "Buffer, video, audio, stream format, and user agent"),
    APPEARANCE("Appearance", "Channel numbers and clock format"),
    REMOTE("Remote Controls", "Channel up/down direction"),
    PARENTAL("Parental Controls", "Parental PIN, adult categories, group and playlist locks"),
    SETTINGS_LOCK("Settings Lock", "Always-on protection and optional custom Settings PIN"),
    BACKUP("Backup & Restore", "Save playlists, favorites, and settings to a local file, or restore them"),
    UPDATES("App Updates", "Automatic GitHub update checks"),
}

/** Category pages and explicitly authorized playlist navigation share one in-memory Settings visit. */
class SettingsActivity : ListActivity() {

    private class Entry(val title: String, val value: () -> String, val action: (Entry) -> Unit)

    private lateinit var adapter: TextAdapter<Entry>
    private val s get() = app.settings
    private var category: SettingsCategory? = null
    private var categoryFocus = 0
    private val visit = SettingsVisitAuthorization()
    private val flow get() = app.settingsFlow
    private val settingsRoute = SettingsFlowAuthorization.Route(SettingsFlowAuthorization.Destination.SETTINGS)
    private var authenticating = false
    private var restoredCategory: SettingsCategory? = null
    private var restoredRowFocus = 0

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        // Authenticate here: an old/restored Intent must never grant Settings access.
        adapter = TextAdapter(
            bind = { e -> e.title to e.value() },
            onClick = { e, _ -> if (authorizedToken() != null) e.action(e) },
        )
        list.adapter = adapter
        list.preserveFocusAfterLayout = false
        categoryFocus = savedInstanceState?.getInt(STATE_CATEGORY_FOCUS) ?: 0
        restoredCategory = savedInstanceState?.getString(STATE_CATEGORY)
            ?.let { name -> SettingsCategory.entries.firstOrNull { it.name == name } }
        restoredRowFocus = savedInstanceState?.getInt(STATE_ROW_FOCUS) ?: 0
        hideProtectedContent()
        onBackPressedDispatcher.addCallback(this, object : OnBackPressedCallback(true) {
            override fun handleOnBackPressed() {
                if (!visit.isAuthorized) finish()
                else if (category != null) showPage(null, categoryFocus) else finish()
            }
        })
    }

    override fun onSaveInstanceState(outState: Bundle) {
        outState.putString(STATE_CATEGORY, category?.name)
        outState.putInt(STATE_CATEGORY_FOCUS, categoryFocus)
        val focused = list.focusedChild?.let { list.getChildAdapterPosition(it) } ?: 0
        outState.putInt(STATE_ROW_FOCUS, focused.coerceAtLeast(0))
        super.onSaveInstanceState(outState)
    }

    private fun showPage(selected: SettingsCategory?, focus: Int = 0) {
        if (!visit.isAuthorized) return
        category = selected
        listTitle.text = selected?.title ?: "Settings"
        listSubtitle.text = subtitle()
        list.clearFocus()
        adapter.submit(if (selected == null) {
            SettingsCategory.entries.mapIndexed { index, c ->
                Entry(c.title, { c.description }, {
                    categoryFocus = index
                    showPage(c)
                })
            }
        } else entries(selected))
        val target = focus.coerceIn(0, adapter.itemCount - 1)
        list.doOnNextLayout { list.findViewHolderForAdapterPosition(target)?.itemView?.requestFocus() }
        (list.layoutManager as LinearLayoutManager).scrollToPositionWithOffset(target, 0)
        list.requestLayout()
    }

    // A payload keeps the same row views so D-pad focus stays on the edited setting.
    private fun refresh() {
        if (!visit.isAuthorized) return
        listSubtitle.text = subtitle()
        adapter.notifyItemRangeChanged(0, adapter.itemCount, "refresh")
    }

    private fun subtitle() = "Unlocked for this visit only. Settings protection always stays on. About is available from the main menu without a PIN."

    private fun authorizedToken(): Long? = if (isFinishing || isDestroyed || (!pickingFile && !flow.isAuthorized(this))) null else visit.authorizedToken()

    private fun hasAuthorization(token: Long) = !isFinishing && !isDestroyed && visit.isAuthorized(token) && (pickingFile || flow.isAuthorized(this))

    private fun hideProtectedContent() {
        list.visibility = View.INVISIBLE
        listTitle.text = "Settings locked"
        listSubtitle.text = "Enter the Settings PIN to continue. About is available from the main menu without a PIN."
    }

    private fun authenticateSettings() {
        if (authenticating || isFinishing) return
        val token = visit.beginVisit()
        authenticating = true
        Dialogs.requireSettingsPin(this, "unlock Settings", onCancel = {
            if (visit.isCurrent(token)) { authenticating = false; finish() }
        }) {
            if (isFinishing || !visit.authorize(token)) return@requireSettingsPin
            flow.authenticate(this, settingsRoute)
            authenticating = false
            list.visibility = View.VISIBLE
            showPage(restoredCategory, restoredRowFocus)
        }
    }

    /** Row values can change outside Settings (backup files, guide updates); show current ones on return. */
    override fun onResume() {
        super.onResume()
        val returningFromPicker = pickingFile
        pickingFile = false
        if (!::adapter.isInitialized || isFinishing) return
        if (flow.enter(this, settingsRoute)) {
            if (!visit.isAuthorized) visit.authorize(visit.beginVisit())
            authenticating = false
            list.visibility = View.VISIBLE
            showPage(category ?: restoredCategory, if (category != null) 0 else restoredRowFocus)
            return
        }
        // Retain the pre-existing, narrowly scoped system-file-picker operation, never an Intent grant.
        if (returningFromPicker && visit.isAuthorized) flow.authenticate(this, settingsRoute)
        if (!visit.isAuthorized) authenticateSettings()
        else { list.visibility = View.VISIBLE; refresh() }
    }

    override fun onPause() {
        hideProtectedContent()
        flow.paused(this)
        if (!pickingFile) {
            visit.endVisit()
            authenticating = false
            filePickerToken = null
        }
        super.onPause()
    }

    /** Retain only a hidden parent inside the authenticated playlist flow; other departures close Settings. */
    override fun onStop() {
        super.onStop()
        if (s.isSettingsLocked() && !isChangingConfigurations && !pickingFile && !flow.retains(this)) finish()
    }

    override fun onDestroy() {
        visit.endVisit()
        super.onDestroy()
    }

    override fun finish() {
        flow.returning(this)
        super.finish()
    }

    // ---------------- Backup & Restore ----------------

    private val backups by lazy { BackupManager(this, s, app.repo) }

    /** True while the system file picker is open, so a locked Settings visit is not closed underneath it. */
    private var pickingFile = false
    private var filePickerToken: Long? = null

    /** Only the original still-live picker operation can return; saved state never restores its ticket. */
    private fun completeDocumentPicker(): Long? {
        val token = filePickerToken
        filePickerToken = null
        pickingFile = false
        if (token == null || isFinishing || isDestroyed || !visit.isAuthorized(token)) return null
        // Activity results may arrive at STARTED, before onResume, after the system picker hid this app.
        flow.authenticate(this, settingsRoute)
        return token
    }

    private val exportDoc = registerForActivityResult(ActivityResultContracts.CreateDocument("application/json")) { uri ->
        val token = completeDocumentPicker() ?: return@registerForActivityResult
        if (uri == null || !hasAuthorization(token)) return@registerForActivityResult
        runBackupTask("Saving backup…") {
            val text = backups.encode()
            contentResolver.openOutputStream(uri, "wt")?.use { it.write(text.toByteArray(Charsets.UTF_8)) }
                ?: throw BackupException("Could not write to the chosen location.")
            "Backup saved. It contains your playlist logins; keep it private."
        }
    }

    private val importDoc = registerForActivityResult(ActivityResultContracts.OpenDocument()) { uri ->
        val token = completeDocumentPicker() ?: return@registerForActivityResult
        if (uri == null || !hasAuthorization(token)) return@registerForActivityResult
        loadAndConfirm { contentResolver.openInputStream(uri) ?: throw BackupException("Could not open the chosen file.") }
    }

    private fun exportBackup() {
        filePickerToken = authorizedToken() ?: return
        pickingFile = true
        exportDoc.launch(backupFileName())
    }

    private fun importBackup() {
        withPlaylistPermission {
            filePickerToken = authorizedToken() ?: return@withPlaylistPermission
            pickingFile = true
            importDoc.launch(arrayOf("application/json", "application/octet-stream", "text/plain"))
        }
    }

    private fun backupFileName() = "youriptv-backup-" + SimpleDateFormat("yyyyMMdd-HHmmss", Locale.US).format(Date()) + ".json"

    /** Android TV boxes often ship only a stub instead of a real file picker; hide those entries there. */
    private fun hasDocumentPicker(): Boolean {
        val i = Intent(Intent.ACTION_CREATE_DOCUMENT).addCategory(Intent.CATEGORY_OPENABLE).setType("application/json")
        return packageManager.queryIntentActivities(i, 0).any { !it.activityInfo.packageName.endsWith(".frameworkpackagestubs") }
    }

    private fun backupCount(): String {
        val n = backups.backups().size
        return if (n == 0) "No backups saved on this device yet" else "$n backup(s) on this device"
    }

    private fun createBackup() {
        runBackupTask("Creating backup…") {
            val f = backups.writeToFolder()
            "Backup saved: ${f.name}\nFolder: ${f.parentFile?.absolutePath}\nIt contains your playlist logins (not your PINs). Uninstalling the app deletes this folder."
        }
    }

    /** Editing or restoring playlists needs the parental PIN while "Lock playlists" is on. */
    private fun withPlaylistPermission(reason: String = "restore playlists", then: () -> Unit) {
        val token = authorizedToken() ?: return
        if (s.isPlaylistsLocked()) Dialogs.requirePin(this, reason) { if (hasAuthorization(token)) then() }
        else if (hasAuthorization(token)) then()
    }

    private fun editCurrentPlaylist() {
        if (!visit.isAuthorized) return
        if (app.repo.playlist(s.activePlaylistId) == null) {
            Dialogs.toast(this, "No active playlist. Select one in Playlists first.")
            return
        }
        val current = app.repo.playlist(s.activePlaylistId)
        if (current == null) Dialogs.toast(this, "No active playlist. Select one in Playlists first.")
        else flow.launch(this, SettingsFlowAuthorization.Route(SettingsFlowAuthorization.Destination.EDITOR, current.id),
            PlaylistEditActivity.editIntent(this, current.id))
    }

    private fun managePlaylists() {
        if (authorizedToken() == null) return
        flow.launch(this, SettingsFlowAuthorization.Route(SettingsFlowAuthorization.Destination.PLAYLISTS),
            Intent(this, PlaylistsActivity::class.java))
    }

    private fun restoreFromFolder() {
        val token = authorizedToken() ?: return
        val files = backups.backups()
        if (files.isEmpty()) { Dialogs.toast(this, "No backups saved on this device yet. Use \"Create backup\" first."); return }
        val labels = files.map { "${Format.dayTime(it.lastModified())} · ${(it.length() + 1023) / 1024} KB\n${it.name}" }
        Dialogs.choose(this, "Choose a backup", labels) { i ->
            if (!hasAuthorization(token)) return@choose
            val f = files[i]
            Dialogs.choose(this, f.name, listOf("Restore this backup", "Delete this backup")) choice@ { action ->
                if (!hasAuthorization(token)) return@choice
                if (action == 0) withPlaylistPermission { loadAndConfirm { f.inputStream() } }
                else Dialogs.confirm(this, "Delete backup", "Delete ${f.name}? This cannot be undone.") {
                    if (!hasAuthorization(token)) return@confirm
                    if (f.delete()) Dialogs.toast(this, "Backup deleted") else Dialogs.toast(this, "Could not delete the backup")
                    refresh()
                }
            }
        }
    }

    private fun loadAndConfirm(open: () -> java.io.InputStream) {
        val token = authorizedToken() ?: return
        setBusy(true, "Reading backup…")
        lifecycleScope.launch {
            val loaded = withContext(Dispatchers.IO) {
                runCatching { backups.read(open()).let { it to backups.plan(it) } }
            }
            if (!hasAuthorization(token)) return@launch
            setBusy(false, subtitle())
            loaded.onFailure { Dialogs.toast(this@SettingsActivity, (it as? BackupException)?.message ?: "Could not read the backup file.") }
            loaded.onSuccess { (data, plan) ->
                val made = if (data.created > 0) "Backup from ${Format.dayTime(data.created)}\n" else ""
                Dialogs.confirm(
                    this@SettingsActivity, "Restore backup",
                    made + BackupRestore.summary(data, plan) +
                        "\n\nExisting playlists and favorites are kept; settings are replaced by the backup's values. PINs are not changed.",
                ) { if (hasAuthorization(token)) applyRestore(data, token) }
            }
        }
    }

    private fun applyRestore(data: BackupData, token: Long) {
        if (!hasAuthorization(token)) return
        setBusy(true, "Restoring…")
        lifecycleScope.launch {
            val outcome = withContext(Dispatchers.IO) {
                runCatching {
                    val r = backups.restore(data)
                    val failed = r.addedPlaylists.filter { p ->
                        runCatching { Refresher.refreshAll(p) { m -> runOnUiThread {
                            if (hasAuthorization(token)) listSubtitle.text = "\"${p.name}\": $m"
                        } } }.isFailure
                    }
                    r to failed
                }
            }
            if (!hasAuthorization(token)) return@launch
            setBusy(false, subtitle())
            refresh()
            outcome.onFailure { Dialogs.toast(this@SettingsActivity, (it as? BackupException)?.message ?: "Restore failed: ${Nav.describeError(it)}") }
            outcome.onSuccess { (r, failed) ->
                val msg = buildString {
                    append("Restored ${r.settingsApplied} setting(s), ${r.addedPlaylists.size} new playlist(s), ${r.favoritesAdded} favorite(s).")
                    if (r.matchedPlaylists > 0) append(" ${r.matchedPlaylists} playlist(s) were already on this device.")
                    if (failed.isNotEmpty()) append(" Could not load: ${failed.joinToString { it.name }} — refresh it in Playlists.")
                }
                Dialogs.message(this@SettingsActivity, "Restore complete", msg)
            }
        }
    }

    private fun runBackupTask(busy: String, work: () -> String) {
        val token = authorizedToken() ?: return
        setBusy(true, busy)
        lifecycleScope.launch {
            val r = withContext(Dispatchers.IO) { runCatching(work) }
            if (!hasAuthorization(token)) return@launch
            setBusy(false, subtitle())
            refresh()
            val msg = r.getOrElse { (it as? BackupException)?.message ?: "Backup failed: ${Nav.describeError(it)}" }
            Dialogs.message(this@SettingsActivity, if (r.isSuccess) "Backup" else "Backup failed", msg)
        }
    }

    private fun toggle(t: String, get: () -> Boolean, set: (Boolean) -> Unit, note: String = "") =
        Entry(t, { (if (get()) "On" else "Off") + if (note.isNotEmpty()) " · $note" else "" }, {
            if (authorizedToken() != null) { set(!get()); refresh() }
        })

    private fun choice(t: String, labels: List<String>, index: () -> Int, set: (Int) -> Unit) =
        Entry(t, { labels.getOrElse(index()) { "" } }, entry@ {
            val token = authorizedToken() ?: return@entry
            Dialogs.choose(this, t, labels, checked = index()) {
                if (hasAuthorization(token)) { set(it); refresh() }
            }
        })

    private fun number(t: String, unit: String, get: () -> Int, min: Int, max: Int, set: (Int) -> Unit) =
        Entry(t, { "${get()} $unit" }, entry@ {
            val token = authorizedToken() ?: return@entry
            Dialogs.input(this, "$t ($min–$max $unit)", get().toString(), numeric = true) { txt ->
                if (!hasAuthorization(token)) return@input
                val v = txt.trim().toIntOrNull()
                if (v == null || v < min || v > max) Dialogs.toast(this, "Enter a number from $min to $max") else { set(v); refresh() }
            }
        })

    private fun entries(category: SettingsCategory): List<Entry> = when (category) {
        SettingsCategory.GENERAL -> listOf(
            toggle("Start with last channel", { s.resumeLastChannel }, { s.resumeLastChannel = it }),
            Entry("Clear viewing history", { "Active playlist" }, entry@ {
                val token = authorizedToken() ?: return@entry
                Dialogs.confirm(this, "Clear history", "Clear viewing history and resume points for the active playlist?") {
                    if (!hasAuthorization(token)) return@confirm
                    app.repo.clearHistory(s.activePlaylistId); Dialogs.toast(this, "History cleared")
                }
            }),
        )
        SettingsCategory.PLAYLISTS -> listOf(
            Entry("Manage playlists", { "Add, update, edit or delete playlists during this unlocked visit" }, { managePlaylists() }),
            Entry("Edit current playlist", {
                app.repo.playlist(s.activePlaylistId)?.name?.let { "$it · uses this Settings unlock" } ?: "No active playlist"
            }, { editCurrentPlaylist() }),
            number("Auto-update playlist and guide every", "hours (0 = off)", { s.autoRefreshHours }, 0, 168) { s.autoRefreshHours = it },
        )
        SettingsCategory.PLAYBACK -> listOf(
            number("Buffer size", "ms", { s.bufferMs }, 500, 30_000) { s.bufferMs = it },
            choice("Aspect ratio", listOf("Fit", "Zoom / crop", "Stretch"), { s.aspectMode }) { s.aspectMode = it },
            number("Seek step for movies and series", "s", { s.skipSeconds }, 5, 600) { s.skipSeconds = it },
            number("Audio delay (+ = audio later)", "ms", { s.audioDelayMs }, -2000, 2000) { s.audioDelayMs = it },
            toggle("Auto frame rate", { s.autoFrameRate }, { s.autoFrameRate = it }, "switch display refresh rate to match the video"),
            choice("Live stream format (Xtream)", listOf("MPEG-TS (.ts)", "HLS (.m3u8)"), { if (s.liveFormat == "m3u8") 1 else 0 }) {
                s.liveFormat = if (it == 1) "m3u8" else "ts"
            },
            toggle("Prefer software video decoding", { s.preferSoftwareDecoding }, { s.preferSoftwareDecoding = it }, "try if a channel shows a black or corrupted picture"),
            Entry("Default user agent", { s.globalUserAgent.ifBlank { "YourIPTV default" } }, entry@ {
                val token = authorizedToken() ?: return@entry
                Dialogs.input(this, "User agent (empty = default)", s.globalUserAgent) {
                    if (hasAuthorization(token)) { s.globalUserAgent = it.trim(); refresh() }
                }
            }),
        )
        SettingsCategory.EPG -> listOf(
            number("Keep past programmes", "hours", { s.epgPastHours }, 0, 168) { s.epgPastHours = it },
            number("Download future programmes", "days", { s.epgFutureDays }, 1, 14) { s.epgFutureDays = it },
            number("Guide time shift", "hours", { s.epgShiftHours }, -23, 23) { s.epgShiftHours = it },
            Entry("Update guide now", { guideStatus() }, { updateGuideNow() }),
        )
        SettingsCategory.APPEARANCE -> listOf(
            toggle("Show channel numbers", { s.showChannelNumbers }, { s.showChannelNumbers = it }),
            toggle("24-hour clock", { s.use24h }, { s.use24h = it }),
        )
        SettingsCategory.REMOTE -> listOf(
            Entry("Invert channel up/down", {
                if (s.invertZap) "On · in the player, UP selects the next channel number"
                else "Off · in the player, UP selects the previous channel number"
            }, { s.invertZap = !s.invertZap; refresh() }),
        )
        SettingsCategory.PARENTAL -> listOf(
            Entry("Parental PIN", { if (s.hasPin()) "Set · change or remove" else "Not set" }, { pinAction() }),
            lockToggle("Block adult categories", "hides adult groups in every playlist (live, movies, series); uses the parental PIN", { s.blockAdult }) { s.blockAdult = it },
            Entry("Locked groups", { if (s.lockedGroups.isEmpty()) "None" else "${s.lockedGroups.size} locked" }, { lockedGroups() }),
            toggle("Hide locked groups", { s.hideLockedGroups }, change@ { v ->
                val token = authorizedToken() ?: return@change
                Dialogs.requirePin(this, "change visibility") {
                    if (hasAuthorization(token)) { s.hideLockedGroups = v; refresh() }
                }
            }),
            lockToggle("Lock playlists", "PIN needed to add, edit, switch, or delete playlists", { s.lockPlaylists }) { s.lockPlaylists = it },
        )
        SettingsCategory.SETTINGS_LOCK -> listOf(
            Entry("Lock Settings", {
                if (s.pins.has(PinKind.SETTINGS)) "Always on · master and custom Settings PIN · master required to manage the custom PIN"
                else "Always on · permanent Settings master PIN · optional custom PIN can be added"
            }, { settingsLockAction() }),
        )
        SettingsCategory.BACKUP -> listOf(
            Entry("Create backup", { "Saves to this device · includes playlist logins, never PINs" }, { createBackup() }),
            Entry("Restore from backup", { backupCount() }, { restoreFromFolder() }),
        ) + if (hasDocumentPicker()) listOf(
            Entry("Save backup to another location…", { "USB drive, cloud, or Downloads" }, { exportBackup() }),
            Entry("Restore from another location…", { "Choose a YourIPTV backup file" }, { importBackup() }),
        ) else emptyList()
        SettingsCategory.UPDATES -> listOf(
            Entry("Allow update installs (technician)", {
                if (AppUpdates.installationAuthorized(this)) "Allowed · updates install without asking the customer"
                else "Not allowed · opens Android's “Install unknown apps” page for this app only"
            }, { openInstallAuthorization() }),
            Entry("Allow automatic restart after updates (technician)", {
                when {
                    android.os.Build.VERSION.SDK_INT >= 35 -> "Not available on this Android version · customers reopen the app after an update"
                    AppUpdates.restartsAutomatically(this) -> "Allowed · the app reopens itself after an update"
                    else -> "Not allowed · opens Android's “Display over other apps” page"
                }
            }, { openRestartAuthorization() }),
            Entry("Automatic updates", {
                if (s.automaticAppUpdates) "On · checks every app opening · customer chooses Install or Ignore (required after 2 releases)"
                else "Off · version ${BuildConfig.VERSION_NAME}"
            }, {
                if (s.automaticAppUpdates) {
                    s.automaticAppUpdates = false
                    s.lastUpdateInfo = null
                    refresh()
                } else {
                    s.automaticAppUpdates = true
                    refresh()
                    Dialogs.message(this, "Automatic updates enabled",
                        "Every time the app opens it checks the latest ${Branding.displayName(this)} release on GitHub, using youriptv.us if GitHub is unavailable. Customers choose Install or Ignore; " +
                            "once the box is two releases behind, the update is required. " +
                            (if (!AppUpdates.installationAuthorized(this)) "Android asks once to allow installs from this app unless “Allow update installs” is set here first."
                            else if (!AppUpdates.silentInstallSupported) "This Android version shows one Install confirmation for each update."
                            else if (AppUpdates.restartsAutomatically(this)) "Updates install without permission prompts and the app restarts afterwards."
                            else "Updates install without permission prompts; the app then closes and the customer opens it again."))
                }
            }),
        )
    }

    /** Technician-only: "Display over other apps" lets the app reopen after an update (Android 10-14). */
    private fun openRestartAuthorization() {
        if (authorizedToken() == null) return
        if (android.os.Build.VERSION.SDK_INT >= 35) {
            Dialogs.message(this, "Automatic restart", "Android 15 and newer do not let an app reopen itself after updating. Customers open the app again from the home screen.")
            return
        }
        if (AppUpdates.restartsAutomatically(this)) {
            Dialogs.message(this, "Automatic restart allowed", "The app reopens itself after an update.")
            return
        }
        try {
            startActivity(Intent(android.provider.Settings.ACTION_MANAGE_OVERLAY_PERMISSION, Uri.parse("package:$packageName")))
        } catch (e: ActivityNotFoundException) {
            Dialogs.message(this, "Technician setup",
                "This device has no “Display over other apps” screen. Without it, customers open the app again after an update.")
        }
    }

    /** Technician-only: Android's per-app "Install unknown apps" switch. Customers are never sent here. */
    private fun openInstallAuthorization() {
        if (authorizedToken() == null) return
        if (AppUpdates.installationAuthorized(this)) {
            Dialogs.message(this, "Update installs allowed", "This app is already allowed to install its own updates.")
            return
        }
        try {
            startActivity(Intent(android.provider.Settings.ACTION_MANAGE_UNKNOWN_APP_SOURCES, Uri.parse("package:$packageName")))
        } catch (e: ActivityNotFoundException) {
            Dialogs.message(this, "Technician setup",
                "This device has no “Install unknown apps” screen. Use Android Settings → Apps → Special app access → Install unknown apps → ${Branding.displayName(this)} → Allow.")
        }
    }

    private fun guideStatus(): String {
        val t = s.epgUpdated(s.activePlaylistId)
        return if (t > 0) "Last updated ${Format.dayTime(t)}" else "Not downloaded yet"
    }

    private fun updateGuideNow() {
        val token = authorizedToken() ?: return
        val p = app.repo.playlist(s.activePlaylistId) ?: run { Dialogs.toast(this, "No active playlist"); return }
        setBusy(true, "Updating guide…")
        lifecycleScope.launch {
            val warning = withContext(Dispatchers.IO) { Refresher.refreshEpg(p) { m -> runOnUiThread {
                if (hasAuthorization(token)) listSubtitle.text = m
            } } }
            if (!hasAuthorization(token)) return@launch
            setBusy(false, warning ?: "Guide updated.")
            refresh()
        }
    }

    private fun lockToggle(t: String, note: String, get: () -> Boolean, set: (Boolean) -> Unit) =
        Entry(t, { (if (get() && s.hasPin()) "On" else "Off") + " · $note" }, entry@ {
            val token = authorizedToken() ?: return@entry
            val v = !(get() && s.hasPin())
            val apply = { if (hasAuthorization(token)) { set(v); refresh() } }
            if (!s.hasPin()) {
                if (!v) apply() else { Dialogs.toast(this, "Set a parental PIN first"); newPin { apply() } }
            } else Dialogs.requirePin(this, "change \"$t\"") { apply() }
        })

    private fun pinAction() {
        val token = authorizedToken() ?: return
        if (!s.hasPin()) { newPin(); return }
        Dialogs.choose(this, "Parental PIN", listOf("Change parental PIN", "Remove parental PIN")) { i ->
            if (!hasAuthorization(token)) return@choose
            Dialogs.requirePin(this, "confirm current parental PIN") {
                if (!hasAuthorization(token)) return@requirePin
                if (i == 0) newPin() else {
                    s.pins.set(PinKind.PARENTAL, null)
                    Dialogs.toast(this, "Parental PIN removed; locked groups, adult categories, and playlists are now unlocked")
                    refresh()
                }
            }
        }
    }

    private fun newPin(after: (() -> Unit)? = null) {
        val token = authorizedToken() ?: return
        Dialogs.createPin(this, PinKind.PARENTAL, canSave = { hasAuthorization(token) }) {
            if (hasAuthorization(token)) { refresh(); after?.invoke() }
        }
    }

    /** The master manages only the optional custom PIN. Settings protection cannot be disabled. */
    private fun settingsLockAction() {
        val token = authorizedToken() ?: return
        val options = if (s.pins.has(PinKind.SETTINGS)) listOf("Change custom Settings PIN", "Remove custom Settings PIN") else listOf("Add custom Settings PIN")
        Dialogs.choose(this, "Settings protection is always on", options) { i ->
            if (!hasAuthorization(token)) return@choose
            Dialogs.requireSettingsMaster(this, "manage the custom Settings PIN") {
                if (!hasAuthorization(token)) return@requireSettingsMaster
                if (i == 0) Dialogs.createPin(this, PinKind.SETTINGS, canSave = { hasAuthorization(token) }) { refresh() }
                else {
                    s.pins.set(PinKind.SETTINGS, null)
                    s.clearLegacySettingsLock()
                    Dialogs.toast(this, "Custom Settings PIN removed. Master protection remains on.")
                    refresh()
                }
            }
        }
    }

    private fun lockedGroups() {
        val token = authorizedToken() ?: return
        val p = app.repo.playlist(s.activePlaylistId) ?: run { Dialogs.toast(this, "No active playlist"); return }
        if (!s.hasPin()) { Dialogs.toast(this, "Set a parental PIN first"); newPin { lockedGroups() }; return }
        Dialogs.requirePin(this, "edit locked groups") {
            if (!hasAuthorization(token)) return@requirePin
            lifecycleScope.launch {
                val groups = withContext(Dispatchers.IO) {
                    listOf(ItemKind.LIVE, ItemKind.MOVIE, ItemKind.SERIES, ItemKind.EPISODE).flatMap { app.repo.groups(p.id, it) }.map { it.name }.distinct()
                }
                if (!hasAuthorization(token)) return@launch
                if (groups.isEmpty()) { Dialogs.toast(this@SettingsActivity, "The active playlist has no groups yet"); return@launch }
                val checked = BooleanArray(groups.size) { groups[it] in s.lockedGroups }
                val dialog = androidx.appcompat.app.AlertDialog.Builder(this@SettingsActivity).setTitle("Locked groups")
                    .setMultiChoiceItems(groups.toTypedArray(), checked) { _, i, v -> if (hasAuthorization(token)) checked[i] = v }
                    .setPositiveButton("Save") { _, _ ->
                        if (hasAuthorization(token)) {
                            val other = s.lockedGroups - groups.toSet()
                            s.lockedGroups = other + groups.filterIndexed { i, _ -> checked[i] }
                            refresh()
                        }
                    }.setNegativeButton("Cancel", null).show()
                RemoteBack.bind(dialog, this@SettingsActivity)
            }
        }
    }

    companion object {
        // Navigation state only; no authentication is restored from saved state.
        private const val STATE_CATEGORY = "settings_category"
        private const val STATE_CATEGORY_FOCUS = "settings_category_focus"
        private const val STATE_ROW_FOCUS = "settings_row_focus"
    }
}
