package com.youriptv.app.ui

import android.annotation.SuppressLint
import android.content.Intent
import android.view.View
import android.view.ViewGroup
import android.widget.Button
import android.widget.LinearLayout
import android.widget.ProgressBar
import android.widget.ScrollView
import android.widget.TextView
import androidx.appcompat.app.AlertDialog
import androidx.appcompat.app.AppCompatActivity
import androidx.lifecycle.DefaultLifecycleObserver
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleOwner
import androidx.lifecycle.lifecycleScope
import androidx.lifecycle.repeatOnLifecycle
import com.youriptv.app.R
import com.youriptv.app.YourIptvApp
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.collect
import kotlinx.coroutines.launch

/** Dialogs remain in Main's window: opening customer Settings does not stop its live player owner. */
object CustomerSettingsPanel {
    internal fun showRefreshControls(activity: AppCompatActivity, playlistId: Long) = showPlaylistSettings(activity, playlistId)

    fun show(activity: AppCompatActivity, playlistId: Long) {
        if (activity.isFinishing || activity.isDestroyed) return
        val dialog = AlertDialog.Builder(activity).setTitle("Settings")
            .setItems(arrayOf("Playlist Settings", "Parental Controls")) { _, index ->
                when (index) {
                    0 -> showPlaylistSettings(activity, playlistId)
                    1 -> activity.startActivity(Intent(activity, ParentalControlsActivity::class.java))
                }
            }
            .setNegativeButton("Close", null).show()
        RemoteBack.bind(dialog, activity)
        dialog.listView?.let { list -> list.post { list.requestFocus(); list.setSelection(0) } }
        dismissWhenStopped(activity, dialog)
    }

    // One collector belongs to each newly created dialog, rather than to onResume.
    // dismissWhenStopped cancels observerJob on dismissal, and the Activity scope
    // additionally cancels it on destruction; reopening cannot accumulate collectors.
    @SuppressLint("RepeatOnLifecycleWrongUsage")
    private fun showPlaylistSettings(activity: AppCompatActivity, playlistId: Long) {
        if (activity.isFinishing || activity.isDestroyed) return
        val app = YourIptvApp.instance
        val spacing = (12 * activity.resources.displayMetrics.density).toInt()
        val content = LinearLayout(activity).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(spacing * 2, spacing, spacing * 2, spacing)
        }
        fun text() = TextView(activity).apply {
            textSize = 15f
            setPadding(0, spacing / 2, 0, spacing / 2)
            content.addView(this, LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT))
        }
        val name = text()
        val interval = text()
        val playlistTimes = text()
        val playlistError = text()
        val epgTimes = text()
        val epgError = text()
        val status = text().apply { accessibilityLiveRegion = View.ACCESSIBILITY_LIVE_REGION_POLITE }
        val progress = ProgressBar(activity).apply {
            visibility = View.GONE
            content.addView(this, LinearLayout.LayoutParams(spacing * 3, spacing * 3))
        }
        fun button(label: String) = Button(activity, null, 0, R.style.Button_Tv).apply {
            id = View.generateViewId()
            text = label
            isFocusable = true
            content.addView(this, LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT))
        }
        val updatePlaylist = button("Update Playlist")
        val updateEpg = button("Update EPG")
        updatePlaylist.nextFocusDownId = updateEpg.id
        updateEpg.nextFocusUpId = updatePlaylist.id
        val scroll = ScrollView(activity).apply { isFillViewport = true; addView(content) }
        val dialog = AlertDialog.Builder(activity).setTitle("Playlist Settings").setView(scroll)
            .setNegativeButton("Close", null).create()
        var observerJob: Job? = null
        var requestJob: Job? = null
        var requesting = false

        fun time(value: Long) = if (value > 0L) Format.dayTime(value) else "Never"
        fun render() {
            val operation = app.refreshCoordinator.states.value[playlistId]
            val view = CustomerRefreshPresentation.from(app.repo.playlist(playlistId), requesting || operation?.busy == true,
                operation?.message, operation?.lastResult?.success)
            name.text = "Playlist: ${view.playlistName}"
            interval.text = "Automatic Playlist Update: ${view.interval}"
            playlistTimes.text = "Last Successful Playlist Update: ${time(view.playlistSuccess)}\nLast Playlist Update Attempt: ${time(view.playlistAttempt)}"
            epgTimes.text = "Last Successful EPG Update: ${time(view.epgSuccess)}\nLast EPG Update Attempt: ${time(view.epgAttempt)}"
            playlistError.text = "Last playlist error: ${view.playlistError}"
            playlistError.visibility = if (view.playlistError.isBlank()) View.GONE else View.VISIBLE
            epgError.text = "Last EPG error: ${view.epgError}"
            epgError.visibility = if (view.epgError.isBlank()) View.GONE else View.VISIBLE
            status.text = "Update Status: ${view.status}"
            progress.visibility = if (view.busy) View.VISIBLE else View.GONE
            val focusedAction = updatePlaylist.hasFocus() || updateEpg.hasFocus()
            updatePlaylist.isEnabled = view.canRequest
            updateEpg.isEnabled = view.canRequest
            updatePlaylist.alpha = if (view.canRequest) 1f else 0.5f
            updateEpg.alpha = if (view.canRequest) 1f else 0.5f
            if (focusedAction && !view.canRequest) dialog.getButton(AlertDialog.BUTTON_NEGATIVE)?.requestFocus()
        }

        fun request(epgOnly: Boolean) {
            if (!dialog.isShowing || activity.isFinishing || activity.isDestroyed) return
            if (requesting || app.refreshCoordinator.states.value[playlistId]?.busy == true) {
                Dialogs.toast(activity, "An update is already in progress")
                return
            }
            if (app.repo.playlist(playlistId) == null) { render(); return }
            requesting = true
            render()
            val operation = if (epgOnly) app.refreshCoordinator.requestEpg(playlistId) else app.refreshCoordinator.requestPlaylist(playlistId)
            requestJob = activity.lifecycleScope.launch {
                val result = operation.await()
                requesting = false
                if (dialog.isShowing && activity.lifecycle.currentState.isAtLeast(Lifecycle.State.STARTED)) {
                    render()
                    Dialogs.toast(activity, CustomerRefreshPresentation.safeMessage(result.message))
                }
            }
        }

        updatePlaylist.setOnClickListener { request(epgOnly = false) }
        updateEpg.setOnClickListener { request(epgOnly = true) }
        dialog.show()
        RemoteBack.bind(dialog, activity)
        dismissWhenStopped(activity, dialog) { observerJob?.cancel(); requestJob?.cancel() }
        render()
        if (updatePlaylist.isEnabled) updatePlaylist.requestFocus() else dialog.getButton(AlertDialog.BUTTON_NEGATIVE)?.requestFocus()
        observerJob = activity.lifecycleScope.launch {
            activity.repeatOnLifecycle(Lifecycle.State.STARTED) {
                app.refreshCoordinator.states.collect { render() }
            }
        }
    }

    private fun dismissWhenStopped(activity: AppCompatActivity, dialog: AlertDialog, onDismiss: () -> Unit = {}) {
        val observer = object : DefaultLifecycleObserver {
            override fun onStop(owner: LifecycleOwner) { dialog.dismiss() }
            override fun onDestroy(owner: LifecycleOwner) { dialog.dismiss() }
        }
        activity.lifecycle.addObserver(observer)
        dialog.setOnDismissListener {
            activity.lifecycle.removeObserver(observer)
            onDismiss()
        }
    }
}
