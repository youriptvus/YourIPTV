package com.youriptv.app.updates

import org.json.JSONObject
import java.io.IOException
import java.net.URI

internal class UpdateException(message: String) : IOException(message)

/**
 * Contents of `update.json`, published on GitHub and the official website mirror.
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

    /** A lagging mirror cannot erase a newer known release or rewrite its integrity/required-update policy. */
    fun preservingKnownRelease(known: UpdateRelease?): UpdateRelease {
        if (known == null || versionCode > known.versionCode) return this
        if (versionCode < known.versionCode) return known
        return if (versionName == known.versionName && size == known.size && sha256 == known.sha256 &&
            previousVersionCode == known.previousVersionCode) this else known
    }

    fun verifyDownload(actualSize: Long, actualSha256: String) {
        if (actualSize != size) throw UpdateException("The update download is incomplete.")
        if (!actualSha256.equals(sha256, ignoreCase = true)) throw UpdateException("The update checksum does not match.")
    }

    fun encode(): String = JSONObject()
        .put("versionCode", versionCode).put("versionName", versionName).put("apk", url)
        .put("size", size).put("sha256", sha256).put("previousVersionCode", previousVersionCode)
        .toString()

    /** Mirror bytes must match this release's checksum and size; never substitute a newer manifest. */
    fun downloadUrls(testOrigin: String = "", testBackupOrigin: String = ""): List<String> {
        val backup = when {
            isTestUrl(url, testBackupOrigin) -> null
            isTestUrl(url, testOrigin) -> testBackupOrigin.takeIf { it.isNotEmpty() }
                ?.let { "${it}YourIPTV-$versionName-release.apk" }
            url == githubApkUrl(versionName) -> websiteApkUrl(versionName)
            else -> null
        }
        return listOfNotNull(url, backup).distinct()
    }

    companion object {
        const val MAX_APK_BYTES = 100L * 1024 * 1024
        const val MAX_MANIFEST_BYTES = 64L * 1024
        private const val DOWNLOAD_PREFIX = "https://github.com/youriptvus/YourIPTV/releases/download/"
        private const val WEBSITE_PREFIX = "https://youriptv.us/updates/"
        private val GITHUB_HOSTS = setOf(
            "github.com", "release-assets.githubusercontent.com",
            "objects.githubusercontent.com", "github-releases.githubusercontent.com",
        )

        /** [testOrigin] is empty in published builds; only local device-test builds set it. */
        fun parse(text: String, testOrigin: String = "", testBackupOrigin: String = ""): UpdateRelease {
            val json = JSONObject(text)
            val code = json.getLong("versionCode")
            val name = json.getString("versionName")
            val url = json.getString("apk")
            val size = json.getLong("size")
            val sha = json.getString("sha256").lowercase()
            val previous = json.getLong("previousVersionCode")
            if (code < 1 || previous < 0 || previous >= code) throw UpdateException("Invalid update version numbers.")
            if (!name.matches(Regex("[0-9A-Za-z][0-9A-Za-z.+-]{0,31}"))) throw UpdateException("Invalid update version name.")
            val official = url == githubApkUrl(name) || url == websiteApkUrl(name)
            val local = isTestUrl(url, testOrigin) || isTestUrl(url, testBackupOrigin)
            if (!official && !local) throw UpdateException("The update APK is not from an official YourIPTV update server.")
            if (size !in 1..MAX_APK_BYTES) throw UpdateException("Invalid update APK size.")
            if (!sha.matches(Regex("[0-9a-f]{64}"))) throw UpdateException("Invalid update checksum.")
            return UpdateRelease(code, name, url, size, sha, previous)
        }

        fun allowedNetworkUrl(url: String, testOrigin: String = "", testBackupOrigin: String = ""): Boolean {
            if (isTestUrl(url, testOrigin) || isTestUrl(url, testBackupOrigin)) return true
            val uri = try { URI(url) } catch (_: Exception) { return false }
            if (uri.scheme != "https" || uri.userInfo != null || uri.port !in listOf(-1, 443)) return false
            return uri.host in GITHUB_HOSTS || uri.host == "youriptv.us" && uri.fragment == null &&
                uri.rawPath == uri.path && uri.normalize().path == uri.path && uri.path.startsWith("/updates/")
        }

        private fun githubApkUrl(name: String) = "${DOWNLOAD_PREFIX}v$name/YourIPTV.apk"
        private fun websiteApkUrl(name: String) = "${WEBSITE_PREFIX}YourIPTV-$name-release.apk"

        private fun isTestUrl(url: String, origin: String): Boolean {
            if (origin.isEmpty()) return false
            val uri = try { URI(url) } catch (_: Exception) { return false }
            val base = try { URI(origin) } catch (_: Exception) { return false }
            return base.scheme in setOf("http", "https") && base.host != null && base.userInfo == null &&
                base.rawQuery == null && base.fragment == null && base.path.endsWith("/") &&
                uri.scheme == base.scheme && uri.host == base.host && uri.port == base.port &&
                uri.userInfo == null && uri.fragment == null && uri.rawPath == uri.path &&
                uri.normalize().path == uri.path && uri.path.startsWith(base.path)
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
