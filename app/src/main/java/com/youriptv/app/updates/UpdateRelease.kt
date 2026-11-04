package com.youriptv.app.updates

import org.json.JSONObject
import java.io.IOException
import java.net.URI

internal class UpdateException(message: String) : IOException(message)

/**
 * Contents of `update.json`, published next to `YourIPTV.apk` on every GitHub release.
 *
 * `previousVersionCode` is the release published before this one. A device below it is two or more
 * releases behind, so Ignore is no longer offered and the update is required.
 */
internal data class UpdateRelease(
    val versionCode: Long,
    val versionName: String,
    val url: String,
    val size: Long,
    val sha256: String,
    val previousVersionCode: Long,
) {
    fun isNewerThan(installedCode: Long): Boolean = versionCode > installedCode

    fun isRequiredFor(installedCode: Long): Boolean = isNewerThan(installedCode) && installedCode < previousVersionCode

    fun verifyDownload(actualSize: Long, actualSha256: String) {
        if (actualSize != size) throw UpdateException("The update download is incomplete.")
        if (!actualSha256.equals(sha256, ignoreCase = true)) throw UpdateException("The update checksum does not match.")
    }

    fun encode(): String = JSONObject()
        .put("versionCode", versionCode).put("versionName", versionName).put("apk", url)
        .put("size", size).put("sha256", sha256).put("previousVersionCode", previousVersionCode)
        .toString()

    companion object {
        const val MAX_APK_BYTES = 100L * 1024 * 1024
        const val MAX_MANIFEST_BYTES = 64L * 1024
        private const val DOWNLOAD_PREFIX = "https://github.com/comp6062/YourIPTV/releases/download/"
        private val GITHUB_HOSTS = setOf(
            "github.com", "release-assets.githubusercontent.com",
            "objects.githubusercontent.com", "github-releases.githubusercontent.com",
        )

        /** [testOrigin] is empty in published builds; only local device-test builds set it. */
        fun parse(text: String, testOrigin: String = ""): UpdateRelease {
            val json = JSONObject(text)
            val code = json.getLong("versionCode")
            val name = json.getString("versionName")
            val url = json.getString("apk")
            val size = json.getLong("size")
            val sha = json.getString("sha256").lowercase()
            val previous = json.getLong("previousVersionCode")
            if (code < 1 || previous < 0 || previous >= code) throw UpdateException("Invalid update version numbers.")
            if (!name.matches(Regex("[0-9A-Za-z][0-9A-Za-z.+-]{0,31}"))) throw UpdateException("Invalid update version name.")
            val official = url == "${DOWNLOAD_PREFIX}v$name/YourIPTV.apk"
            val local = testOrigin.isNotEmpty() && url.startsWith(testOrigin)
            if (!official && !local) throw UpdateException("The update APK is not from the YourIPTV GitHub releases.")
            if (size !in 1..MAX_APK_BYTES) throw UpdateException("Invalid update APK size.")
            if (!sha.matches(Regex("[0-9a-f]{64}"))) throw UpdateException("Invalid update checksum.")
            return UpdateRelease(code, name, url, size, sha, previous)
        }

        fun allowedNetworkUrl(url: String, testOrigin: String = ""): Boolean {
            if (testOrigin.isNotEmpty() && url.startsWith(testOrigin)) return true
            val uri = try { URI(url) } catch (_: Exception) { return false }
            return uri.scheme == "https" && uri.userInfo == null && uri.port in listOf(-1, 443) && uri.host in GITHUB_HOSTS
        }
    }
}

internal object UpdatePackagePolicy {
    /** A customer-installed update must be the same app, newer, officially signed, and request no new permissions. */
    fun verify(
        installedPackage: String, candidatePackage: String?,
        installedCode: Long, candidateCode: Long, expectedCode: Long,
        installedSigners: Set<String>, candidateSigners: Set<String>,
        installedPermissions: Set<String>, candidatePermissions: Set<String>,
        installedBrand: String, candidateBrand: String?,
        deviceSdk: Int, candidateMinSdk: Int,
    ) {
        if (candidatePackage != installedPackage) throw UpdateException("The APK belongs to a different app.")
        if (candidateCode <= installedCode) throw UpdateException("The APK is not newer than the installed app.")
        if (candidateCode != expectedCode) throw UpdateException("The APK version does not match the update information.")
        if (installedSigners.isEmpty() || candidateSigners != installedSigners) {
            throw UpdateException("The APK signing certificate does not match this app.")
        }
        if (candidatePermissions != installedPermissions) {
            throw UpdateException("This update changes Android permissions. A technician must install it.")
        }
        if (candidateBrand != installedBrand) throw UpdateException("This update changes the app branding. A technician must install it.")
        if (candidateMinSdk > deviceSdk) throw UpdateException("This update needs a newer Android version.")
    }
}

/** One check per app opening (foreground visit), not per screen. Ignore is never remembered. */
internal class UpdateForegroundGate {
    private var started = 0
    private var visitActive = false
    private var pending = false
    var generation = 0
        private set

    fun started() {
        if (started++ == 0 && !visitActive) {
            visitActive = true
            generation++
            pending = true
        }
    }

    fun stopped(changingConfiguration: Boolean) {
        started = (started - 1).coerceAtLeast(0)
        if (started == 0 && !changingConfiguration) {
            visitActive = false
            pending = false
            generation++
        }
    }

    fun takeCheck(): Boolean = pending.also { pending = false }
    fun isForeground(token: Int): Boolean = started > 0 && generation == token
}
