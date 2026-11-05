package com.youriptv.app

import android.content.Context
import android.content.pm.ApplicationInfo
import android.os.Build
import android.provider.Settings
import android.util.Log
import java.io.File

/**
 * Centralized anti-rebranding gate.
 *
 * The real anti-repackaging anchor is [OfficialSigning]: on Android any edit to an installed APK's
 * code, resources or icons forces the attacker to re-sign, which fails the signing-certificate check
 * and stops the app from starting. This object layers cheap, reliable brand locks on top of that
 * anchor (package allow-list and display-name lock) and exposes advisory root/debug signals.
 *
 * It deliberately does NOT self-hash classes.dex at runtime: R8 rewrites the dex on every build, so a
 * hardcoded dex hash is brittle and adds nothing over the signature check. See docs/RELEASE_NOTES.md.
 */
internal object BrandIntegrity {
    private const val TAG = "YourIPTV-Integrity"

    /** Application IDs allowed to run this build. */
    val ALLOWED_PACKAGES = setOf("com.youriptv.app")

    /** Display name that must match res/values/strings.xml app_name. */
    const val EXPECTED_BRAND_NAME = "YourIPTV"

    /**
     * The single sanctioned exception to the package gate, reserved for a first-party tool you will
     * build later. Set it to that tool's applicationId so the tool is allowed through without
     * reopening the whole scheme; when the tool exists, also pin its signer in [OfficialSigning].
     * Leave null to keep the exception closed.
     */
    val CUSTOM_TOOL_EXCEPTION: String? = null

    enum class Result { OK, WRONG_PACKAGE, WRONG_BRAND }

    /** Package + brand lock used at startup. Debug builds always pass so development is unaffected. */
    fun verify(context: Context): Result {
        if (BuildConfig.DEBUG) return Result.OK
        if (!isAllowedPackage(context.packageName)) {
            Log.e(TAG, "Package ${context.packageName} is not on the allow-list.")
            return Result.WRONG_PACKAGE
        }
        val brand = runCatching { context.getString(R.string.app_name) }.getOrDefault("")
        if (brand != EXPECTED_BRAND_NAME) {
            Log.e(TAG, "The app display name was changed.")
            return Result.WRONG_BRAND
        }
        return Result.OK
    }

    /** True when [packageName] is the official package or the single custom-tool exception. */
    fun isAllowedPackage(packageName: String): Boolean =
        packageName in ALLOWED_PACKAGES || packageName == CUSTOM_TOOL_EXCEPTION

    // Advisory signals only. Rooted devices and active debug bridges are common and legitimate, so the
    // app logs these rather than killing itself; callers may harden behaviour for their own threat model.

    data class Signals(val rooted: Boolean, val adbEnabled: Boolean, val debuggable: Boolean, val beingTraced: Boolean)

    fun securitySignals(context: Context): Signals {
        val rooted = isDeviceRooted({ File(it).exists() }, Build.TAGS.orEmpty())
        val adb = runCatching {
            Settings.Global.getInt(context.contentResolver, Settings.Global.ADB_ENABLED, 0) == 1
        }.getOrDefault(false)
        val debuggable = (context.applicationInfo.flags and ApplicationInfo.FLAG_DEBUGGABLE) != 0
        val traced = runCatching { tracerPidFromStatus(File("/proc/self/status").readText()) }.getOrDefault(0) != 0
        if (rooted || debuggable || traced) {
            Log.w(TAG, "Security signals: rooted=$rooted adbEnabled=$adb debuggable=$debuggable traced=$traced")
        }
        return Signals(rooted, adb, debuggable, traced)
    }

    /** Pure, unit-testable root heuristic: test-keys build or any known su binary present. */
    fun isDeviceRooted(fileExists: (String) -> Boolean, buildTags: String): Boolean {
        val suPaths = listOf(
            "/system/bin/su", "/system/xbin/su", "/sbin/su", "/su/bin/su",
            "/system/app/Superuser.apk", "/data/local/su", "/data/local/bin/su", "/data/local/xbin/su",
        )
        return buildTags.contains("test-keys") || suPaths.any(fileExists)
    }

    /** Pure, unit-testable TracerPid parse of /proc/self/status; non-zero means a debugger/tracer is attached. */
    fun tracerPidFromStatus(statusText: String): Int =
        statusText.lineSequence().firstOrNull { it.startsWith("TracerPid:") }
            ?.substringAfter(":")?.trim()?.toIntOrNull() ?: 0
}
