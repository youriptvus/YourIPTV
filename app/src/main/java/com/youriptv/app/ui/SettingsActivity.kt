package com.youriptv.app.ui

import android.content.Intent
import android.os.Bundle
import androidx.activity.OnBackPressedCallback
import androidx.activity.result.contract.ActivityResultContracts
import androidx.core.view.doOnNextLayout
import androidx.lifecycle.lifecycleScope
import androidx.recyclerview.widget.LinearLayoutManager
import com.youriptv.app.data.BackupData
import com.youriptv.app.data.BackupException
import com.youriptv.app.data.BackupManager
import com.youriptv.app.data.BackupRestore
import com.youriptv.app.data.ItemKind
import com.youriptv.app.data.PinKind
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

internal enum class SettingsCategory(val title: String, val description: String) {
    GENERAL("General", "Startup and viewing history"),
    PLAYLISTS("Playlists", "Automatic playlist and guide updates"),
    EPG("TV Guide / EPG", "Programme storage, time shift, and guide updates"),
    PLAYBACK("Playback", "Buffer, video, audio, stream format, and user agent"),
    APPEARANCE("Appearance", "Channel numbers and clock format"),
    REMOTE("Remote Controls", "Channel up/down direction"),
    PARENTAL("Parental Controls", "Parental PIN, adult categories, group and playlist locks"),
    SETTINGS_LOCK("Settings Lock", "Separate Settings PIN and temporary unlocking"),
    BACKUP("Backup & Restore", "Save playlists, favorites, and settings to a local file, or restore them"),
    ABOUT("About", "App information and free-use notice"),
}

/** Category pages share one Settings visit and its existing unlock lifetime. */
class SettingsActivity : ListActivity() {

    private class Entry(val title: String, val value: () -> String, val action: (Entry) -> Unit)

    private lateinit var adapter: TextAdapter<Entry>
    private val s get() = app.settings
    private var category: SettingsCategory? = null
    private var categoryFocus = 0

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        // Settings only open through the unlock prompt while the Settings lock is on.
        if (s.isSettingsLocked() && !intent.getBooleanExtra(EXTRA_UNLOCKED, false)) { finish(); return }
        adapter = TextAdapter(
            bind = { e -> e.title to e.value() },
            onClick = { e, _ -> e.action(e) },
        )
        list.adapter = adapter
        list.preserveFocusAfterLayout = false
        categoryFocus = savedInstanceState?.getInt(STATE_CATEGORY_FOCUS) ?: 0
        val restoredCategory = savedInstanceState?.getString(STATE_CATEGORY)
            ?.let { name -> SettingsCategory.entries.firstOrNull { it.name == name } }
        showPage(restoredCategory, savedInstanceState?.getInt(STATE_ROW_FOCUS) ?: 0)
        onBackPressedDispatcher.addCallback(this, object : OnBackPressedCallback(true) {
            override fun handleOnBackPressed() {
                if (category != null) showPage(null, categoryFocus) else finish()
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
        listSubtitle.text = subtitle()
        adapter.notifyItemRangeChanged(0, adapter.itemCount, "refresh")
    }

    private fun subtitle() =
        if (s.isSettingsLocked()) "Unlocked for this Settings visit only — Settings lock again when you exit Settings. To remove the lock: Settings Lock → Lock Settings → Turn off Settings lock."
        else if (category == null) "Choose a category. Every setting is available to every user."
        else "Changes apply immediately. Back returns to Settings categories."

    /** Row values can change outside Settings (backup files, guide updates); show current ones on return. */
    override fun onResume() {
        super.onResume()
        if (::adapter.isInitialized) refresh()
    }

    /** A temporary unlock lasts one visit: leaving Settings (BACK, HOME, About, screen off) closes it, so it needs the Settings PIN again. */
    override fun onStop() {
        super.onStop()
        if (s.isSettingsLocked() && !isChangingConfigurations && !pickingFile) finish()
    }

    // ---------------- Backup & Restore ----------------

    private val backups by lazy { BackupManager(this, s, app.repo) }

    /** True while the system file picker is open, so a locked Settings visit is not closed underneath it. */
    private var pickingFile = false

    private val exportDoc = registerForActivityResult(ActivityResultContracts.CreateDocument("application/json")) { uri ->
        pickingFile = false
        if (uri == null) return@registerForActivityResult
        runBackupTask("Saving backup…") {
            val text = backups.encode()
            contentResolver.openOutputStream(uri, "wt")?.use { it.write(text.toByteArray(Charsets.UTF_8)) }
                ?: throw BackupException("Could not write to the chosen location.")
            "Backup saved. It contains your playlist logins; keep it private."
        }
    }

    private val importDoc = registerForActivityResult(ActivityResultContracts.OpenDocument()) { uri ->
        pickingFile = false
        if (uri == null) return@registerForActivityResult
        loadAndConfirm { contentResolver.openInputStream(uri) ?: throw BackupException("Could not open the chosen file.") }
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

    /** Restoring can add or switch playlists, so it needs the parental PIN while "Lock playlists" is on. */
    private fun withPlaylistPermission(then: () -> Unit) {
        if (s.isPlaylistsLocked()) Dialogs.requirePin(this, "restore playlists") { then() } else then()
    }

    private fun restoreFromFolder() {
        val files = backups.backups()
        if (files.isEmpty()) { Dialogs.toast(this, "No backups saved on this device yet. Use \"Create backup\" first."); return }
        val labels = files.map { "${Format.dayTime(it.lastModified())} · ${(it.length() + 1023) / 1024} KB\n${it.name}" }
        Dialogs.choose(this, "Choose a backup", labels) { i ->
            val f = files[i]
            Dialogs.choose(this, f.name, listOf("Restore this backup", "Delete this backup")) { action ->
                if (action == 0) withPlaylistPermission { loadAndConfirm { f.inputStream() } }
                else Dialogs.confirm(this, "Delete backup", "Delete ${f.name}? This cannot be undone.") {
                    if (f.delete()) Dialogs.toast(this, "Backup deleted") else Dialogs.toast(this, "Could not delete the backup")
                    refresh()
                }
            }
        }
    }

    private fun loadAndConfirm(open: () -> java.io.InputStream) {
        setBusy(true, "Reading backup…")
        lifecycleScope.launch {
            val loaded = withContext(Dispatchers.IO) {
                runCatching { backups.read(open()).let { it to backups.plan(it) } }
            }
            setBusy(false, subtitle())
            loaded.onFailure { Dialogs.toast(this@SettingsActivity, (it as? BackupException)?.message ?: "Could not read the backup file.") }
            loaded.onSuccess { (data, plan) ->
                val made = if (data.created > 0) "Backup from ${Format.dayTime(data.created)}\n" else ""
                Dialogs.confirm(
                    this@SettingsActivity, "Restore backup",
                    made + BackupRestore.summary(data, plan) +
                        "\n\nExisting playlists and favorites are kept; settings are replaced by the backup's values. PINs are not changed.",
                ) { applyRestore(data) }
            }
        }
    }

    private fun applyRestore(data: BackupData) {
        setBusy(true, "Restoring…")
        lifecycleScope.launch {
            val outcome = withContext(Dispatchers.IO) {
                runCatching {
                    val r = backups.restore(data)
                    val failed = r.addedPlaylists.filter { p ->
                        runCatching { Refresher.refreshAll(p) { m -> runOnUiThread { listSubtitle.text = "\"${p.name}\": $m" } } }.isFailure
                    }
                    r to failed
                }
            }
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
        setBusy(true, busy)
        lifecycleScope.launch {
            val r = withContext(Dispatchers.IO) { runCatching(work) }
            setBusy(false, subtitle())
            refresh()
            val msg = r.getOrElse { (it as? BackupException)?.message ?: "Backup failed: ${Nav.describeError(it)}" }
            Dialogs.message(this@SettingsActivity, if (r.isSuccess) "Backup" else "Backup failed", msg)
        }
    }

    private fun toggle(t: String, get: () -> Boolean, set: (Boolean) -> Unit, note: String = "") =
        Entry(t, { (if (get()) "On" else "Off") + if (note.isNotEmpty()) " · $note" else "" }, { set(!get()); refresh() })

    private fun choice(t: String, labels: List<String>, index: () -> Int, set: (Int) -> Unit) =
        Entry(t, { labels.getOrElse(index()) { "" } }, { Dialogs.choose(this, t, labels, checked = index()) { set(it); refresh() } })

    private fun number(t: String, unit: String, get: () -> Int, min: Int, max: Int, set: (Int) -> Unit) =
        Entry(t, { "${get()} $unit" }, {
            Dialogs.input(this, "$t ($min–$max $unit)", get().toString(), numeric = true) { txt ->
                val v = txt.trim().toIntOrNull()
                if (v == null || v < min || v > max) Dialogs.toast(this, "Enter a number from $min to $max") else { set(v); refresh() }
            }
        })

    private fun entries(category: SettingsCategory): List<Entry> = when (category) {
        SettingsCategory.GENERAL -> listOf(
            toggle("Start with last channel", { s.resumeLastChannel }, { s.resumeLastChannel = it }),
            Entry("Clear viewing history", { "Active playlist" }, {
                Dialogs.confirm(this, "Clear history", "Clear viewing history and resume points for the active playlist?") {
                    app.repo.clearHistory(s.activePlaylistId); Dialogs.toast(this, "History cleared")
                }
            }),
        )
        SettingsCategory.PLAYLISTS -> listOf(
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
            Entry("Default user agent", { s.globalUserAgent.ifBlank { "YourIPTV default" } }, {
                Dialogs.input(this, "User agent (empty = default)", s.globalUserAgent) { s.globalUserAgent = it.trim(); refresh() }
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
            toggle("Hide locked groups", { s.hideLockedGroups }, { v -> Dialogs.requirePin(this, "change visibility") { s.hideLockedGroups = v; refresh() } }),
            lockToggle("Lock playlists", "PIN needed to add, edit, switch, or delete playlists", { s.lockPlaylists }) { s.lockPlaylists = it },
        )
        SettingsCategory.SETTINGS_LOCK -> listOf(
            Entry("Lock Settings", {
                if (s.isSettingsLocked()) "On · unlocked for this visit · own Settings PIN, separate from the parental PIN"
                else "Off · uses its own Settings PIN, which must differ from the parental PIN"
            }, { settingsLockAction() }),
        )
        SettingsCategory.BACKUP -> listOf(
            Entry("Create backup", { "Saves to this device · includes playlist logins, never PINs" }, { createBackup() }),
            Entry("Restore from backup", { backupCount() }, { restoreFromFolder() }),
        ) + if (hasDocumentPicker()) listOf(
            Entry("Save backup to another location…", { "USB drive, cloud, or Downloads" }, { pickingFile = true; exportDoc.launch(backupFileName()) }),
            Entry("Restore from another location…", { "Choose a YourIPTV backup file" }, {
                withPlaylistPermission { pickingFile = true; importDoc.launch(arrayOf("application/json", "application/octet-stream", "text/plain")) }
            }),
        ) else emptyList()
        SettingsCategory.ABOUT -> listOf(
            Entry("About ${Branding.displayName(this)}", { "Free · no account · no purchase · no activation" }, { startActivity(Intent(this, AboutActivity::class.java)) }),
        )
    }

    private fun guideStatus(): String {
        val t = s.epgUpdated(s.activePlaylistId)
        return if (t > 0) "Last updated ${Format.dayTime(t)}" else "Not downloaded yet"
    }

    private fun updateGuideNow() {
        val p = app.repo.playlist(s.activePlaylistId) ?: run { Dialogs.toast(this, "No active playlist"); return }
        setBusy(true, "Updating guide…")
        lifecycleScope.launch {
            val warning = withContext(Dispatchers.IO) { Refresher.refreshEpg(p) { m -> runOnUiThread { listSubtitle.text = m } } }
            setBusy(false, warning ?: "Guide updated.")
            refresh()
        }
    }

    private fun lockToggle(t: String, note: String, get: () -> Boolean, set: (Boolean) -> Unit) =
        Entry(t, { (if (get() && s.hasPin()) "On" else "Off") + " · $note" }, {
            val v = !(get() && s.hasPin())
            val apply = { set(v); refresh() }
            if (!s.hasPin()) {
                if (!v) apply() else { Dialogs.toast(this, "Set a parental PIN first"); newPin { apply() } }
            } else Dialogs.requirePin(this, "change \"$t\"") { apply() }
        })

    private fun pinAction() {
        if (!s.hasPin()) { newPin(); return }
        Dialogs.choose(this, "Parental PIN", listOf("Change parental PIN", "Remove parental PIN")) { i ->
            Dialogs.requirePin(this, "confirm current parental PIN") {
                if (i == 0) newPin() else {
                    s.pins.set(PinKind.PARENTAL, null)
                    Dialogs.toast(this, "Parental PIN removed; locked groups, adult categories, and playlists are now unlocked")
                    refresh()
                }
            }
        }
    }

    private fun newPin(after: (() -> Unit)? = null) = Dialogs.createPin(this, PinKind.PARENTAL) { refresh(); after?.invoke() }

    /** Unlocking Settings never turns the lock off; this is the only place to create, change, or permanently remove the Settings PIN. */
    private fun settingsLockAction() {
        if (!s.isSettingsLocked()) {
            Dialogs.createPin(this, PinKind.SETTINGS) {
                refresh()
                Dialogs.toast(this, "Settings lock is on. This visit stays unlocked until you exit Settings.")
            }
            return
        }
        Dialogs.choose(this, "Settings lock", listOf("Change Settings PIN", "Turn off Settings lock")) { i ->
            Dialogs.requireSettingsPin(this, "confirm current Settings PIN") {
                if (i == 0) Dialogs.createPin(this, PinKind.SETTINGS) { s.clearLegacySettingsLock(); refresh() }
                else { s.pins.set(PinKind.SETTINGS, null); s.clearLegacySettingsLock(); Dialogs.toast(this, "Settings lock turned off"); refresh() }
            }
        }
    }

    private fun lockedGroups() {
        val p = app.repo.playlist(s.activePlaylistId) ?: run { Dialogs.toast(this, "No active playlist"); return }
        if (!s.hasPin()) { Dialogs.toast(this, "Set a parental PIN first"); newPin { lockedGroups() }; return }
        Dialogs.requirePin(this, "edit locked groups") {
            lifecycleScope.launch {
                val groups = withContext(Dispatchers.IO) {
                    listOf(ItemKind.LIVE, ItemKind.MOVIE, ItemKind.SERIES, ItemKind.EPISODE).flatMap { app.repo.groups(p.id, it) }.map { it.name }.distinct()
                }
                if (groups.isEmpty()) { Dialogs.toast(this@SettingsActivity, "The active playlist has no groups yet"); return@launch }
                val checked = BooleanArray(groups.size) { groups[it] in s.lockedGroups }
                androidx.appcompat.app.AlertDialog.Builder(this@SettingsActivity).setTitle("Locked groups")
                    .setMultiChoiceItems(groups.toTypedArray(), checked) { _, i, v -> checked[i] = v }
                    .setPositiveButton("Save") { _, _ ->
                        val other = s.lockedGroups - groups.toSet()
                        s.lockedGroups = other + groups.filterIndexed { i, _ -> checked[i] }
                        refresh()
                    }.setNegativeButton("Cancel", null).show()
            }
        }
    }

    companion object {
        /** Set only by the Settings unlock prompt in [MainActivity]. */
        const val EXTRA_UNLOCKED = "settings_unlocked"
        private const val STATE_CATEGORY = "settings_category"
        private const val STATE_CATEGORY_FOCUS = "settings_category_focus"
        private const val STATE_ROW_FOCUS = "settings_row_focus"
    }
}
