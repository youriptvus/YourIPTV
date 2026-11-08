package com.youriptv.app.updates

import android.app.Activity
import android.app.Application
import android.content.Intent
import android.os.Bundle
import android.util.Log
import androidx.appcompat.app.AlertDialog
import com.youriptv.app.BuildConfig
import com.youriptv.app.YourIptvApp
import com.youriptv.app.ui.AppUpdateActivity
import com.youriptv.app.ui.Branding
import com.youriptv.app.ui.RemoteBack
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.lang.ref.WeakReference

/**
 * Checks update.json each time the app is opened while automatic updates are on (the default).
 * Optional updates offer Install/Ignore; Ignore lasts only until the next opening. Updates two or more
 * releases ahead are required and block the player until installed.
 */
internal class AppUpdateChecks(private val app: YourIptvApp) : Application.ActivityLifecycleCallbacks {
    private val gate = UpdateForegroundGate()
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Main.immediate)
    private var resumed = WeakReference<Activity>(null)
    private var check: Job? = null
    private var offer: UpdateRelease? = null
    private var required: UpdateRelease? = null
    private var dialog: AlertDialog? = null
    private val receipt = UpdateReceipt(AppUpdates.installedCode, BuildConfig.VERSION_NAME, object : UpdateReceipt.Store {
        override var pendingCode: Long
            get() = app.settings.pendingUpdateCode
            set(value) { app.settings.pendingUpdateCode = value }
    })

    override fun onActivityStarted(activity: Activity) { gate.started() }

    override fun onActivityResumed(activity: Activity) {
        resumed = WeakReference(activity)
        if (activity is AppUpdateActivity) return
        if (!app.settings.automaticAppUpdates) {
            offer = null
            required = null
        } else if (gate.takeCheck()) {
            beginCheck()
        }
        present(activity)
    }

    private fun beginCheck() {
        cached()?.let { decide(it) }
        val token = gate.generation
        check?.cancel()
        check = scope.launch {
            try {
                val fetched = withContext(Dispatchers.IO) { AppUpdates.latest() }
                val release = fetched.preservingKnownRelease(cached())
                app.settings.lastUpdateInfo = release.encode()
                if (!gate.isForeground(token) || !app.settings.automaticAppUpdates) return@launch
                decide(release)
                resumed.get()?.let { present(it) }
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                // Customers are not interrupted when both servers are unreachable; a cached required update still applies.
                Log.w("YourIPTV-Updates", "Update check failed: ${AppUpdates.errorMessage(e)}")
            }
        }
    }

    private fun cached(): UpdateRelease? = app.settings.lastUpdateInfo?.let {
        runCatching { UpdateRelease.parse(it, BuildConfig.UPDATE_TEST_ORIGIN, BuildConfig.UPDATE_TEST_BACKUP_ORIGIN) }.getOrNull()
    }

    private fun decide(release: UpdateRelease) {
        val installed = AppUpdates.installedCode
        when {
            release.isRequiredFor(installed) -> { required = release; offer = null }
            release.isNewerThan(installed) -> { if (required == null) offer = release }
            else -> { required = null; offer = null }
        }
    }

    private fun present(activity: Activity) {
        if (activity.isFinishing || activity.isDestroyed || activity is AppUpdateActivity) return
        required?.let {
            dialog?.dismiss()
            dialog = null
            activity.startActivity(updateIntent(activity, it, required = true))
            return
        }
        if (dialog?.isShowing == true) return
        receipt.current()?.let {
            showReceipt(activity, it)
            return
        }
        val release = offer ?: return
        val name = Branding.displayName(activity)
        dialog = AlertDialog.Builder(activity)
            .setTitle("$name update available")
            .setMessage("Version ${release.versionName} is ready to install (this device has ${BuildConfig.VERSION_NAME}).\n\n" +
                "Your playlists, favorites and settings are kept. " +
                if (AppUpdates.restartsAutomatically(activity)) "The app restarts after the update." else "The app closes after the update; open it again from the home screen.")
            .setPositiveButton("Install") { _, _ ->
                offer = null
                activity.startActivity(updateIntent(activity, release, required = false).putExtra(AppUpdateActivity.EXTRA_START, true))
            }
            .setNegativeButton("Ignore") { _, _ -> offer = null }
            .setOnCancelListener { offer = null }
            .show().also {
                RemoteBack.bind(it, activity)
                it.getButton(AlertDialog.BUTTON_POSITIVE).requestFocus()
            }
    }

    private fun showReceipt(activity: Activity, confirmation: UpdateReceipt.Confirmation) {
        var acknowledged = false
        val changes = confirmation.changes.takeIf { it.isNotEmpty() }
            ?.joinToString("\n\n") { "\u2022 $it" }
            ?: "Release notes are unavailable for this version."
        val shown = AlertDialog.Builder(activity)
            .setTitle("${Branding.displayName(activity)} updated to ${confirmation.installedName}")
            .setMessage("Update installed successfully.\n\nWhat's new in ${confirmation.installedName}:\n\n$changes")
            .setCancelable(false)
            .setPositiveButton("Got it") { _, _ ->
                receipt.acknowledge(confirmation)
                acknowledged = true
            }
            .create()
        shown.setOnDismissListener {
            if (dialog === shown) dialog = null
            // Pausing keeps the receipt pending; only acknowledgement lets an optional offer follow.
            if (acknowledged) resumed.get()?.let { present(it) }
        }
        dialog = shown
        shown.show()
        RemoteBack.bind(shown, activity)
        shown.getButton(AlertDialog.BUTTON_POSITIVE).requestFocus()
    }

    private fun updateIntent(activity: Activity, release: UpdateRelease, required: Boolean) =
        Intent(activity, AppUpdateActivity::class.java)
            .putExtra(AppUpdateActivity.EXTRA_RELEASE, release.encode())
            .putExtra(AppUpdateActivity.EXTRA_REQUIRED, required)

    override fun onActivityPaused(activity: Activity) {
        if (resumed.get() === activity) resumed.clear()
        // Keep the pending offer so it reappears on the next screen of this visit (launch switches screens).
        dialog?.dismiss()
        dialog = null
    }

    override fun onActivityStopped(activity: Activity) {
        gate.stopped(activity.isChangingConfigurations)
        if (!gate.isForeground(gate.generation)) {
            check?.cancel()
            offer = null
            required = null
        }
    }

    override fun onActivityCreated(activity: Activity, savedInstanceState: Bundle?) = Unit
    override fun onActivitySaveInstanceState(activity: Activity, outState: Bundle) = Unit
    override fun onActivityDestroyed(activity: Activity) = Unit
}
