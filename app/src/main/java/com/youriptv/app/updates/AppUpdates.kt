package com.youriptv.app.updates

import android.app.PendingIntent
import android.content.Context
import android.content.pm.PackageInfo
import android.content.pm.PackageInstaller
import android.content.pm.PackageManager
import android.os.Build
import android.provider.Settings
import android.util.Log
import com.youriptv.app.BuildConfig
import kotlinx.coroutines.currentCoroutineContext
import kotlinx.coroutines.ensureActive
import org.json.JSONException
import java.io.ByteArrayOutputStream
import java.io.File
import java.io.IOException
import java.net.HttpURLConnection
import java.net.URL
import java.security.MessageDigest

internal object AppUpdates {
    private const val TAG = "YourIPTV-Updates"
    private const val FOLDER = "app-updates"

    val installedCode: Long get() = BuildConfig.VERSION_CODE.toLong()

    /** Set once by a technician (Android "Install unknown apps" for this app); customers never see the prompt. */
    fun installationAuthorized(context: Context): Boolean =
        Build.VERSION.SDK_INT < 26 || context.packageManager.canRequestPackageInstalls()

    /** Android 12+ lets an app allowed to install packages update itself without confirmation (13+ also needs UPDATE_PACKAGES_WITHOUT_USER_ACTION). */
    val silentInstallSupported: Boolean get() = Build.VERSION.SDK_INT >= 31

    /**
     * Installing an update closes the app. Android only lets it reopen itself afterwards when a technician granted
     * "Display over other apps"; Android 15+ no longer honours that for a closed app, so customers reopen it.
     */
    fun restartsAutomatically(context: Context): Boolean =
        Build.VERSION.SDK_INT < 29 || Build.VERSION.SDK_INT < 35 && Settings.canDrawOverlays(context)

    suspend fun latest(): UpdateRelease {
        val output = ByteArrayOutputStream()
        transfer(BuildConfig.UPDATE_MANIFEST_URL, UpdateRelease.MAX_MANIFEST_BYTES) { bytes, length -> output.write(bytes, 0, length) }
        return UpdateRelease.parse(output.toString(Charsets.UTF_8.name()), BuildConfig.UPDATE_TEST_ORIGIN)
    }

    suspend fun download(context: Context, release: UpdateRelease, progress: (Int) -> Unit): File {
        val folder = File(context.cacheDir, FOLDER)
        if (!folder.exists() && !folder.mkdirs()) throw UpdateException("Could not create the private update folder.")
        val partial = File(folder, "download.part")
        val apk = File(folder, "YourIPTV.apk")
        try {
            val digest = MessageDigest.getInstance("SHA-256")
            var total = 0L
            partial.outputStream().use { output ->
                transfer(release.url, release.size) { bytes, length ->
                    output.write(bytes, 0, length)
                    digest.update(bytes, 0, length)
                    total += length
                    progress((total * 100 / release.size).toInt())
                }
            }
            release.verifyDownload(total, digest.digest().joinToString("") { "%02x".format(it) })
            verifyArchive(context, partial, release)
            if (apk.exists() && !apk.delete()) throw UpdateException("Could not replace the old cached update.")
            if (!partial.renameTo(apk)) throw UpdateException("Could not save the verified update.")
            return apk
        } finally {
            if (partial.exists() && !partial.delete()) Log.w(TAG, "Could not remove the partial update download.")
        }
    }

    /** Streams the verified APK into an Android install session and commits it; [status] receives the result. */
    suspend fun install(context: Context, apk: File, status: PendingIntent, progress: (Int) -> Unit) {
        val installer = context.packageManager.packageInstaller
        val params = PackageInstaller.SessionParams(PackageInstaller.SessionParams.MODE_FULL_INSTALL).apply {
            setAppPackageName(context.packageName)
            setSize(apk.length())
            if (Build.VERSION.SDK_INT >= 31) setRequireUserAction(PackageInstaller.SessionParams.USER_ACTION_NOT_REQUIRED)
        }
        val id = installer.createSession(params)
        var committed = false
        try {
            installer.openSession(id).use { session ->
                session.openWrite("YourIPTV.apk", 0, apk.length()).use { out ->
                    apk.inputStream().use { input ->
                        val bytes = ByteArray(64 * 1024)
                        var total = 0L
                        while (true) {
                            currentCoroutineContext().ensureActive()
                            val n = input.read(bytes)
                            if (n < 0) break
                            out.write(bytes, 0, n)
                            total += n
                            val fraction = total.toFloat() / apk.length()
                            session.setStagingProgress(fraction)
                            progress((fraction * 100).toInt())
                        }
                    }
                    session.fsync(out)
                }
                session.commit(status.intentSender)
                committed = true
            }
        } finally {
            if (!committed) runCatching { installer.abandonSession(id) }
        }
    }

    fun clearDownloads(context: Context) {
        File(context.cacheDir, FOLDER).deleteRecursively()
    }

    @Suppress("DEPRECATION")
    private fun verifyArchive(context: Context, file: File, release: UpdateRelease) {
        val flags = PackageManager.GET_PERMISSIONS or
            if (Build.VERSION.SDK_INT >= 28) PackageManager.GET_SIGNING_CERTIFICATES else PackageManager.GET_SIGNATURES
        val pm = context.packageManager
        val installed = pm.getPackageInfo(context.packageName, flags)
        val candidate = pm.getPackageArchiveInfo(file.absolutePath, flags)
            ?: throw UpdateException("The download is not a readable Android APK.")
        val candidateApp = candidate.applicationInfo ?: throw UpdateException("The APK has no application information.")
        candidateApp.sourceDir = file.absolutePath
        candidateApp.publicSourceDir = file.absolutePath
        UpdatePackagePolicy.verify(
            context.packageName, candidate.packageName,
            versionCode(installed), versionCode(candidate), release.versionCode,
            signerHashes(installed), signerHashes(candidate),
            installed.requestedPermissions.orEmpty().toSet(), candidate.requestedPermissions.orEmpty().toSet(),
            pm.getApplicationLabel(context.applicationInfo).toString(), pm.getApplicationLabel(candidateApp).toString(),
            Build.VERSION.SDK_INT, if (Build.VERSION.SDK_INT >= 24) candidateApp.minSdkVersion else 0,
        )
    }

    @Suppress("DEPRECATION")
    private fun versionCode(info: PackageInfo): Long =
        if (Build.VERSION.SDK_INT >= 28) info.longVersionCode else info.versionCode.toLong()

    @Suppress("DEPRECATION")
    private fun signerHashes(info: PackageInfo): Set<String> {
        val signatures = if (Build.VERSION.SDK_INT >= 28) info.signingInfo?.apkContentsSigners else info.signatures
        return signatures.orEmpty().map {
            MessageDigest.getInstance("SHA-256").digest(it.toByteArray()).joinToString("") { b -> "%02x".format(b) }
        }.toSet()
    }

    // Provider HTTP helpers permit arbitrary redirects; updates must stay on GitHub HTTPS.
    private suspend fun transfer(url: String, limit: Long, write: (ByteArray, Int) -> Unit) {
        var current = url
        repeat(6) {
            currentCoroutineContext().ensureActive()
            if (!UpdateRelease.allowedNetworkUrl(current, BuildConfig.UPDATE_TEST_ORIGIN)) {
                throw UpdateException("The update server redirected outside GitHub HTTPS.")
            }
            val connection = URL(current).openConnection() as HttpURLConnection
            try {
                connection.connectTimeout = 15_000
                connection.readTimeout = 15_000
                connection.instanceFollowRedirects = false
                connection.useCaches = false
                connection.setRequestProperty("User-Agent", "YourIPTV-Updater")
                val code = connection.responseCode
                if (code in setOf(301, 302, 303, 307, 308)) {
                    val location = connection.getHeaderField("Location") ?: throw UpdateException("Missing update redirect.")
                    current = URL(URL(current), location).toString()
                    return@repeat
                }
                if (code == 404) throw UpdateException("No update information is published yet.")
                if (code !in 200..299) throw UpdateException("The update server returned HTTP $code.")
                val contentLength = if (Build.VERSION.SDK_INT >= 24) connection.contentLengthLong else connection.contentLength.toLong()
                if (contentLength > limit) throw UpdateException("The update response is too large.")
                connection.inputStream.use { input ->
                    val bytes = ByteArray(64 * 1024)
                    var total = 0L
                    while (true) {
                        currentCoroutineContext().ensureActive()
                        val n = input.read(bytes)
                        if (n < 0) break
                        total += n
                        if (total > limit) throw UpdateException("The update response is too large.")
                        write(bytes, n)
                    }
                }
                return
            } finally {
                connection.disconnect()
            }
        }
        throw UpdateException("Too many update redirects.")
    }

    fun errorMessage(error: Throwable): String {
        Log.e(TAG, "App update failed.", error)
        return when (error) {
            is UpdateException -> error.message ?: "Update failed."
            is JSONException -> "The update information is invalid."
            is IOException -> "Could not reach the update server. Check the internet connection."
            is SecurityException -> "Android blocked the installation. Contact your technician."
            else -> "Could not prepare the update."
        }
    }
}
