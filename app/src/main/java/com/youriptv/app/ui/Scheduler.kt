package com.youriptv.app.ui

import android.app.Activity
import android.app.Application
import android.os.Bundle
import android.os.Handler
import android.os.Looper
import androidx.appcompat.app.AlertDialog
import com.youriptv.app.YourIptvApp
import com.youriptv.app.data.ConnectionLimit
import com.youriptv.app.data.ItemKind
import com.youriptv.app.data.MediaItem
import com.youriptv.app.data.Program
import com.youriptv.app.data.Recorder
import com.youriptv.app.data.Recording
import com.youriptv.app.data.RecordingStatus
import com.youriptv.app.data.Reminder
import java.io.File
import java.lang.ref.WeakReference

/**
 * In-app scheduler for programme reminders and recordings. It runs while YourIPTV's process is alive
 * (no alarms, services, or notification permission). Reminders appear over whichever YourIPTV screen is
 * in front; recordings start and stop on time in the background.
 */
object Scheduler : Application.ActivityLifecycleCallbacks {

    const val REMINDER_LEAD_MS = 60_000L
    const val REMINDER_MARK = "\uD83D\uDD14"
    const val REC_MARK = "\u25CF"
    private const val TICK_MS = 10_000L

    private val handler = Handler(Looper.getMainLooper())
    private var resumed: WeakReference<Activity>? = null
    private var reminderDialog: AlertDialog? = null
    private var reminderOwner: WeakReference<Activity>? = null
    private val app get() = YourIptvApp.instance

    private val tick = object : Runnable {
        override fun run() {
            runCatching { check() }
            handler.postDelayed(this, TICK_MS)
        }
    }

    fun install(application: Application) {
        application.registerActivityLifecycleCallbacks(this)
        recoverInterrupted()
        handler.post(tick)
    }

    fun recordingsDir(): File = app.getExternalFilesDir("recordings") ?: File(app.filesDir, "recordings")

    /** Re-runs the schedule now (after the user adds a recording or reminder). */
    fun kick() { handler.removeCallbacks(tick); handler.post(tick) }

    private fun recoverInterrupted() {
        for (r in app.repo.recordings()) {
            if (r.status == RecordingStatus.RECORDING && !Recorder.isActive(r.id)) {
                val size = File(r.file).takeIf { r.file.isNotBlank() && it.exists() }?.length() ?: 0L
                app.repo.updateRecording(
                    r.id, status = if (size > 0) RecordingStatus.DONE else RecordingStatus.FAILED, bytes = size,
                    error = "YourIPTV was closed while recording.",
                )
            }
        }
    }

    private fun check() {
        val now = System.currentTimeMillis()
        for (r in app.repo.recordings()) {
            if (r.status != RecordingStatus.SCHEDULED) continue
            when {
                r.end <= now -> app.repo.updateRecording(r.id, status = RecordingStatus.MISSED, error = "YourIPTV was not running at the scheduled time.")
                r.start <= now -> startRecording(r)
            }
        }
        val a = resumed?.get() ?: return
        if (a.isFinishing || reminderDialog?.isShowing == true) return
        val due = app.repo.reminders().firstOrNull { it.start - REMINDER_LEAD_MS <= now } ?: return
        app.repo.deleteReminder(due.playlistId, due.channelUrl, due.start)
        if (due.end > now) showReminder(a, due)
        else check()
    }

    private fun startRecording(r: Recording) {
        if (app.repo.connectionLimit(r.playlistId) == 1 && app.livePlayback.stopForRecording(r.playlistId)) {
            val activity = resumed?.get()
            if (activity is PlayerActivity) activity.onRecordingTookConnection()
            else if (activity != null) Dialogs.toast(activity, "Live preview stopped: recording uses the provider's only connection.")
        }
        val ua = app.repo.playlist(r.playlistId)?.userAgent.orEmpty().ifBlank { app.settings.globalUserAgent }
        Recorder.start(r, recordingsDir(), ua, app.repo) { done ->
            handler.post {
                val a = resumed?.get() ?: return@post
                if (a is MainActivity) a.onRecordingFinished()
                val label = done.title.ifBlank { done.channelName }
                Dialogs.toast(a, if (done.status == RecordingStatus.DONE) "Recording finished: $label" else "Recording failed: $label (${done.error})")
            }

        }
    }

    fun recordingUsesOnlyConnection(playlistId: Long): Boolean {
        if (app.repo.connectionLimit(playlistId) != 1) return false
        val playlist = app.repo.playlist(playlistId) ?: return false
        return app.repo.recordings().any { recording ->
            recording.status == RecordingStatus.RECORDING && Recorder.isActive(recording.id) &&
                app.repo.playlist(recording.playlistId)?.let { ConnectionLimit.sameAccount(playlist, it) } == true
        }
    }

    private fun showReminder(a: Activity, r: Reminder) {
        val starts = if (r.start > System.currentTimeMillis()) "starts at ${Format.time(r.start)}" else "has started"
        reminderDialog = AlertDialog.Builder(a)
            .setTitle("Reminder")
            .setMessage("\"${r.title}\" on ${r.channelName} $starts.")
            .setPositiveButton("Watch now") { _, _ -> watch(a, r.playlistId, r.channelUrl) }
            .setNegativeButton("Dismiss", null)
            .show()
        reminderOwner = WeakReference(a)
        reminderDialog?.getButton(AlertDialog.BUTTON_POSITIVE)?.requestFocus()
    }

    fun watch(a: Activity, playlistId: Long, channelUrl: String) {
        val item = app.repo.itemByUrl(playlistId, channelUrl)
        if (item == null) { Dialogs.toast(a, "This channel is no longer in the playlist."); return }
        app.settings.activePlaylistId = playlistId
        Nav.open(a, item, app.repo.items(playlistId, ItemKind.LIVE, item.group))
    }

    // ---------------- Actions shared by the guide, channel lists and player ----------------

    fun toggleReminder(a: Activity, ch: MediaItem, prog: Program) {
        val has = app.repo.hasReminder(ch.playlistId, ch.url, prog.start)
        if (has) app.repo.deleteReminder(ch.playlistId, ch.url, prog.start)
        else app.repo.addReminder(Reminder(ch.playlistId, ch.url, ch.name, prog.title, prog.start, prog.end))
        Dialogs.toast(a, if (has) "Reminder removed" else "Reminder set for ${Format.dayTime(prog.start)}. YourIPTV must be open to show it.")
        kick()
    }

    /** Schedules (or starts, when already airing) a recording of [prog]; toggles off an existing one. */
    fun toggleProgramRecording(a: Activity, ch: MediaItem, prog: Program, onChanged: () -> Unit = {}) {
        val existing = app.repo.scheduledRecording(ch.playlistId, ch.url, prog.start)
        if (existing != null) {
            if (existing.status == RecordingStatus.RECORDING) Recorder.stop(existing.id) else app.repo.deleteRecording(existing.id)
            Dialogs.toast(a, if (existing.status == RecordingStatus.RECORDING) "Recording stopped" else "Scheduled recording cancelled")
            onChanged(); return
        }
        requireRecordingPin(a, ch) {
            confirmConnection(a, ch) {
                app.repo.addRecording(Recording(playlistId = ch.playlistId, channelUrl = ch.url, channelName = ch.name, title = prog.title, start = prog.start, end = prog.end))
                Dialogs.toast(a, if (prog.start <= System.currentTimeMillis()) "Recording started" else "Recording scheduled for ${Format.dayTime(prog.start)}")
                kick(); onChanged()
            }
        }
    }

    /** Records [ch] from now for a user-chosen length (or until the current programme ends). */
    fun recordNow(a: Activity, ch: MediaItem, current: Program?, beforeStart: () -> Unit = {}) {
        requireRecordingPin(a, ch) {
            val now = System.currentTimeMillis()
            val choices = ArrayList<Pair<String, Long>>()
            val airing = current?.takeIf { it.end > now }
            if (airing != null) choices.add("Until \"${airing.title}\" ends (${Format.time(airing.end)})" to airing.end)
            for (m in listOf(30, 60, 120, 180)) choices.add((if (m < 60) "$m minutes" else "${m / 60} hour" + if (m > 60) "s" else "") to now + m * 60_000L)
            Dialogs.choose(a, "Record ${ch.name}", choices.map { it.first }) { i ->
                confirmConnection(a, ch) {
                    beforeStart()
                    val title = if (airing != null && i == 0) airing.title else ch.name
                    app.repo.addRecording(Recording(playlistId = ch.playlistId, channelUrl = ch.url, channelName = ch.name, title = title, start = now, end = choices[i].second))
                    Dialogs.toast(a, "Recording ${ch.name} until ${Format.time(choices[i].second)}")
                    kick()
                }
            }
        }
    }

    private fun requireRecordingPin(a: Activity, ch: MediaItem, go: () -> Unit) {
        if (app.settings.isGroupLocked(ch.group)) Dialogs.requirePin(a, "\"${ch.group}\" is locked") { go() }
        else go()
    }

    /** Warns that a recording needs its own provider connection when the account allows only one. */
    private fun confirmConnection(a: Activity, ch: MediaItem, go: () -> Unit) {
        val max = app.repo.connectionLimit(ch.playlistId)
        if (max == 1) {
            Dialogs.confirm(
                a, "One connection only",
                "Your provider allows 1 connection. While recording, watching another live channel from this provider " +
                    "will fail (and starting playback may stop the recording). Continue?",
            ) { go() }
        } else go()
    }

    // ---------------- Lifecycle tracking ----------------

    override fun onActivityResumed(activity: Activity) { resumed = WeakReference(activity); kick() }
    override fun onActivityPaused(activity: Activity) { if (resumed?.get() === activity) resumed = null }
    override fun onActivityCreated(activity: Activity, savedInstanceState: Bundle?) = Unit
    override fun onActivityStarted(activity: Activity) = Unit
    override fun onActivityStopped(activity: Activity) = Unit
    override fun onActivitySaveInstanceState(activity: Activity, outState: Bundle) = Unit
    override fun onActivityDestroyed(activity: Activity) {
        if (reminderOwner?.get() === activity) { reminderDialog?.dismiss(); reminderDialog = null; reminderOwner = null }
    }
}
