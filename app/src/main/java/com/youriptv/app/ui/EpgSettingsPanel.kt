package com.youriptv.app.ui

import android.view.View
import android.view.ViewGroup
import android.widget.Button
import android.widget.LinearLayout
import android.widget.ProgressBar
import android.widget.ScrollView
import android.widget.TextView
import androidx.appcompat.app.AlertDialog
import androidx.lifecycle.DefaultLifecycleObserver
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleOwner
import androidx.lifecycle.lifecycleScope
import androidx.lifecycle.repeatOnLifecycle
import com.youriptv.app.R
import com.youriptv.app.YourIptvApp
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.collect
import kotlinx.coroutines.launch

/** Guide preferences belong only to the current authenticated Settings visit. */
internal object EpgSettingsPanel {
    fun show(activity: SettingsActivity, playlistId: Long, token: Long) {
        if (!activity.canUseEpgSettings(token)) return
        val app = YourIptvApp.instance
        val settings = app.settings
        val padding = (12 * activity.resources.displayMetrics.density).toInt()
        val content = LinearLayout(activity).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(padding * 2, padding, padding * 2, padding)
        }
        fun text() = TextView(activity).apply {
            textSize = 15f
            setPadding(0, padding / 2, 0, padding / 2)
            content.addView(this, LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT))
        }
        fun button() = Button(activity, null, 0, R.style.Button_Tv).apply {
            id = View.generateViewId()
            isFocusable = true
            content.addView(this, LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT))
        }
        val name = text()
        val past = button()
        val future = button()
        val offset = button()
        val interval = text()
        text().text = "Update on app start: Enabled · runs in the background on a fresh app start"
        text().text = "Program descriptions: Stored when supplied by your provider"
        val times = text()
        val error = text()
        val status = text().apply { accessibilityLiveRegion = View.ACCESSIBILITY_LIVE_REGION_POLITE }
        val progress = ProgressBar(activity).apply {
            visibility = View.GONE
            content.addView(this, LinearLayout.LayoutParams(padding * 3, padding * 3))
        }
        val update = button().apply { text = "Update EPG" }
        text().text = "Past and future settings apply to all playlists. A successful EPG update applies the selected storage window. " +
            "Available programs and descriptions depend on your provider. Missing or previously deleted history cannot be restored. " +
            "Provider catch-up may retain a longer supported history. Edit sources under Playlists in full Settings."
        past.nextFocusDownId = future.id
        future.nextFocusUpId = past.id
        future.nextFocusDownId = offset.id
        offset.nextFocusUpId = future.id
        offset.nextFocusDownId = update.id
        update.nextFocusUpId = offset.id
        val scroll = ScrollView(activity).apply { isFillViewport = true; addView(content) }
        val dialog = AlertDialog.Builder(activity).setTitle("EPG Settings").setView(scroll).setNegativeButton("Close", null).create()
        var picker: AlertDialog? = null
        var observerJob: Job? = null
        var authorizationJob: Job? = null
        var requestJob: Job? = null
        var requesting = false
        fun active(): Boolean {
            val allowed = dialog.isShowing && activity.canUseEpgSettings(token)
            if (!allowed && dialog.isShowing) dialog.dismiss()
            return allowed
        }
        fun time(value: Long) = if (value > 0L) Format.dayTime(value) else "Never"
        fun render() {
            if (!active()) return
            val operation = app.refreshCoordinator.states.value[playlistId]
            val view = CustomerRefreshPresentation.from(app.repo.playlist(playlistId), requesting || operation?.busy == true,
                operation?.message, operation?.lastResult?.success)
            name.text = "Playlist: ${view.playlistName}"
            past.text = "Past guide data to keep: ${EpgSettingsPresentation.pastLabel(settings.epgPastHours)}"
            future.text = "Future guide coverage: ${EpgSettingsPresentation.futureLabel(settings.epgFutureDays)}"
            offset.text = "Guide time offset: ${EpgSettingsPresentation.offsetLabel(settings.epgShiftHours)}"
            interval.text = "Automatic playlist and EPG update interval: ${view.interval}"
            times.text = "Last successful EPG update: ${time(view.epgSuccess)}\nLast EPG update attempt: ${time(view.epgAttempt)}"
            error.text = "Last EPG error: ${view.epgError}"
            error.visibility = if (view.epgError.isBlank()) View.GONE else View.VISIBLE
            status.text = "Update status: ${view.status}"
            progress.visibility = if (view.busy) View.VISIBLE else View.GONE
            val hadUpdateFocus = update.hasFocus()
            update.isEnabled = view.canRequest
            update.alpha = if (view.canRequest) 1f else 0.5f
            if (hadUpdateFocus && !view.canRequest) dialog.getButton(AlertDialog.BUTTON_NEGATIVE)?.requestFocus()
        }
        fun changed() {
            if (!active()) return
            // Main refreshes its guide preferences when this protected Activity returns to it.
            app.refreshCoordinator.reconcileSchedule()
            render()
            Dialogs.toast(activity, "Saved. The next successful EPG update will apply this storage window.")
        }
        fun choose(title: String, options: List<String>, checked: Int, onPick: (Int) -> Unit) {
            if (!active()) return
            picker?.dismiss()
            val builder = AlertDialog.Builder(activity).setTitle(title).setNegativeButton("Cancel", null)
            val select = android.content.DialogInterface.OnClickListener { selected, index ->
                selected.dismiss()
                if (active()) onPick(index)
            }
            if (checked in options.indices) builder.setSingleChoiceItems(options.toTypedArray(), checked, select)
            else builder.setItems(options.toTypedArray(), select)
            val selected = builder.create()
            picker = selected
            selected.setOnDismissListener { if (picker === selected) picker = null }
            selected.show()
            RemoteBack.bind(selected, activity)
            if (checked in options.indices) selected.listView?.let { list ->
                list.setSelection(checked)
                list.post { if (active() && selected.isShowing) { list.requestFocus(); list.setSelection(checked) } }
            }
        }
        past.setOnClickListener {
            if (active()) choose("Past guide data to keep", (0..14).map { EpgSettingsPresentation.pastLabel(it * 24) },
                EpgSettingsPresentation.selectedPastDay(settings.epgPastHours)) { days ->
                if (active()) { settings.epgPastHours = EpgSettingsPresentation.hoursForPastDays(days); changed() }
            }
        }
        future.setOnClickListener {
            if (active()) choose("Future guide coverage", (1..14).map(EpgSettingsPresentation::futureLabel),
                (settings.epgFutureDays - 1).takeIf { it in 0..13 } ?: -1) { index ->
                if (active()) { settings.epgFutureDays = index + 1; changed() }
            }
        }
        offset.setOnClickListener {
            if (active()) choose("Guide time offset", (-23..23).map(EpgSettingsPresentation::offsetLabel),
                settings.epgShiftHours + 23) { index ->
                if (active()) { settings.epgShiftHours = index - 23; render() }
            }
        }
        update.setOnClickListener {
            if (!active() || requesting) return@setOnClickListener
            if (app.refreshCoordinator.states.value[playlistId]?.busy == true) {
                Dialogs.toast(activity, "An update is already in progress")
                return@setOnClickListener
            }
            if (app.repo.playlist(playlistId) == null) { render(); return@setOnClickListener }
            requesting = true
            render()
            val operation = app.refreshCoordinator.requestEpg(playlistId)
            requestJob = activity.lifecycleScope.launch {
                if (!active()) return@launch
                val result = operation.await()
                if (active()) {
                    requesting = false
                    render()
                    Dialogs.toast(activity, CustomerRefreshPresentation.safeMessage(result.message))
                }
            }
        }
        val lifecycleObserver = object : DefaultLifecycleObserver {
            override fun onPause(owner: LifecycleOwner) { dialog.dismiss() }
            override fun onStop(owner: LifecycleOwner) { dialog.dismiss() }
            override fun onDestroy(owner: LifecycleOwner) { dialog.dismiss() }
        }
        dialog.setOnDismissListener {
            picker?.dismiss()
            activity.lifecycle.removeObserver(lifecycleObserver)
            observerJob?.cancel()
            authorizationJob?.cancel()
            requestJob?.cancel()
        }
        if (!activity.canUseEpgSettings(token)) return
        dialog.show()
        RemoteBack.bind(dialog, activity)
        activity.lifecycle.addObserver(lifecycleObserver)
        render()
        past.requestFocus()
        observerJob = activity.lifecycleScope.launch {
            activity.repeatOnLifecycle(Lifecycle.State.RESUMED) {
                app.refreshCoordinator.states.collect { render() }
            }
        }
        authorizationJob = activity.lifecycleScope.launch {
            // Revoke an idle dialog too, even if no operation emits or customer input follows.
            while (active()) delay(250)
        }
    }
}
