package com.youriptv.app.ui

import android.app.ActivityOptions
import android.app.PendingIntent
import android.content.ActivityNotFoundException
import android.content.Intent
import android.content.pm.PackageInstaller
import android.os.Build
import android.os.Bundle
import android.view.View
import android.widget.Button
import androidx.activity.OnBackPressedCallback
import androidx.lifecycle.lifecycleScope
import com.youriptv.app.BuildConfig
import com.youriptv.app.updates.AppUpdates
import com.youriptv.app.updates.UpdateException
import com.youriptv.app.updates.UpdateRelease
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

/** Downloads, verifies and installs an app update in place. A required update offers only Install/Retry. */
class AppUpdateActivity : ListActivity() {
    private var release: UpdateRelease? = null
    private var required = false
    private var job: Job? = null
    private var waitingForAndroid = false
    private lateinit var installButton: Button
    private var laterButton: Button? = null

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        if (!app.isTrustedBuild) { finish(); return }
        list.visibility = View.GONE
        onBackPressedDispatcher.addCallback(this, object : OnBackPressedCallback(true) {
            override fun handleOnBackPressed() {
                when {
                    job?.isActive == true || waitingForAndroid -> Unit
                    required -> finishAffinity()
                    else -> finish()
                }
            }
        })
        if (intent.action == ACTION_STATUS) {
            listTitle.text = "${Branding.displayName(this)} update"
            installButton = addAction("Retry") { start() }
            hideButtons()
            handleStatus(intent)
            return
        }
        release = intent.getStringExtra(EXTRA_RELEASE)?.let { runCatching { UpdateRelease.parse(it, BuildConfig.UPDATE_TEST_ORIGIN) }.getOrNull() }
        required = intent.getBooleanExtra(EXTRA_REQUIRED, false)
        val r = release ?: run { finish(); return }
        listTitle.text = "${Branding.displayName(this)} ${r.versionName}"
        installButton = addAction("Install update") { start() }
        if (!required) laterButton = addAction("Not now") { finish() }
        installButton.requestFocus()
        if (required) {
            setBusy(false, "This version is no longer supported. Install update ${r.versionName} to keep watching. " +
                "Your playlists, favorites and settings are kept.")
        }
        if (savedInstanceState == null && intent.getBooleanExtra(EXTRA_START, false)) start()
    }

    override fun onNewIntent(intent: Intent) {
        super.onNewIntent(intent)
        if (intent.action == ACTION_STATUS) handleStatus(intent)
    }

    private fun start() {
        if (job?.isActive == true || waitingForAndroid) return
        val r = release ?: run {
            setBusy(false, "Update failed. Close and reopen the app to check again.")
            return
        }
        // Without the technician's "Install unknown apps" grant, Android itself asks once during installation.
        hideButtons()
        setBusy(true, "Downloading update… 0%")
        job = lifecycleScope.launch {
            try {
                var shown = -1
                val apk = withContext(Dispatchers.IO) {
                    AppUpdates.download(this@AppUpdateActivity, r) { percent ->
                        if (percent != shown) {
                            shown = percent
                            runOnUiThread { if (!isFinishing) listSubtitle.text = "Downloading update… $percent%" }
                        }
                    }
                }
                setBusy(true, "Installing update… 0%")
                var staged = -1
                app.settings.pendingUpdateCode = r.versionCode
                withContext(Dispatchers.IO) {
                    AppUpdates.install(this@AppUpdateActivity, apk, statusIntent()) { percent ->
                        if (percent != staged) {
                            staged = percent
                            runOnUiThread { if (!isFinishing) listSubtitle.text = "Installing update… $percent%" }
                        }
                    }
                }
                waitingForAndroid = true
                setBusy(true, if (AppUpdates.restartsAutomatically(this@AppUpdateActivity)) {
                    "Installing update… The app closes briefly and restarts automatically when finished."
                } else {
                    "Installing update… The app closes when finished. Open ${Branding.displayName(this@AppUpdateActivity)} again from the home screen."
                })
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                fail(AppUpdates.errorMessage(e))
            }
        }
    }

    private fun statusIntent(): PendingIntent {
        val intent = Intent(this, AppUpdateActivity::class.java).setAction(ACTION_STATUS)
            .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_SINGLE_TOP)
        release?.let { intent.putExtra(EXTRA_RELEASE, it.encode()).putExtra(EXTRA_REQUIRED, required) }
        // The installer adds the result extras, so the explicit intent must be mutable.
        val mutable = if (Build.VERSION.SDK_INT >= 31) PendingIntent.FLAG_MUTABLE else 0
        // Android 14+ only lets the result reopen this closed app if its creator opts in (needs "Display over other apps").
        val options = if (Build.VERSION.SDK_INT >= 34) {
            ActivityOptions.makeBasic()
                .setPendingIntentCreatorBackgroundActivityStartMode(ActivityOptions.MODE_BACKGROUND_ACTIVITY_START_ALLOWED)
                .toBundle()
        } else null
        return PendingIntent.getActivity(this, 0, intent, PendingIntent.FLAG_UPDATE_CURRENT or mutable, options)
    }

    private fun handleStatus(intent: Intent) {
        if (release == null) {
            release = intent.getStringExtra(EXTRA_RELEASE)?.let { runCatching { UpdateRelease.parse(it, BuildConfig.UPDATE_TEST_ORIGIN) }.getOrNull() }
            required = intent.getBooleanExtra(EXTRA_REQUIRED, false)
        }
        when (val status = intent.getIntExtra(PackageInstaller.EXTRA_STATUS, PackageInstaller.STATUS_FAILURE)) {
            PackageInstaller.STATUS_PENDING_USER_ACTION -> {
                // Android 11 and older always ask once to confirm an update; Android 12+ only if not authorized.
                @Suppress("DEPRECATION")
                val confirm = intent.getParcelableExtra<Intent>(Intent.EXTRA_INTENT)
                if (confirm == null) { fail("Android did not provide the installation step."); return }
                try {
                    waitingForAndroid = true
                    setBusy(true, "Installing update… Follow Android's prompt to continue.")
                    startActivity(confirm)
                } catch (e: ActivityNotFoundException) {
                    fail("This device has no package installer. Contact your technician.")
                }
            }
            PackageInstaller.STATUS_SUCCESS -> restartApp()
            else -> {
                val detail = intent.getStringExtra(PackageInstaller.EXTRA_STATUS_MESSAGE)
                fail(when (status) {
                    PackageInstaller.STATUS_FAILURE_ABORTED -> "The installation was cancelled."
                    PackageInstaller.STATUS_FAILURE_STORAGE -> "Not enough storage space on this device."
                    PackageInstaller.STATUS_FAILURE_BLOCKED -> "Android blocked the installation. Contact your technician."
                    PackageInstaller.STATUS_FAILURE_CONFLICT, PackageInstaller.STATUS_FAILURE_INCOMPATIBLE ->
                        "The update is not compatible with this device. Contact your technician."
                    else -> detail?.takeIf { it.isNotBlank() }?.let { "Android reported: $it" } ?: "Android could not install the update."
                })
            }
        }
    }

    private fun restartApp() {
        setBusy(true, "Update installed. Restarting…")
        AppUpdates.clearDownloads(this)
        startActivity(Intent(this, MainActivity::class.java).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TASK))
        finish()
    }

    private fun fail(message: String) {
        waitingForAndroid = false
        app.settings.pendingUpdateCode = 0L
        AppUpdates.clearDownloads(this)
        setBusy(false, "Update failed. $message")
        showRetry()
    }

    private fun hideButtons() {
        installButton.visibility = View.GONE
        laterButton?.visibility = View.GONE
    }

    private fun showRetry() {
        installButton.text = "Retry"
        installButton.visibility = View.VISIBLE
        laterButton?.let { it.text = "Close"; it.visibility = View.VISIBLE }
        if (laterButton == null && !required && release != null) laterButton = addAction("Close") { finish() }
        installButton.requestFocus()
    }

    companion object {
        internal const val EXTRA_RELEASE = "update_release"
        internal const val EXTRA_REQUIRED = "update_required"
        internal const val EXTRA_START = "update_start"
        private const val ACTION_STATUS = "com.youriptv.app.action.UPDATE_STATUS"
    }
}
