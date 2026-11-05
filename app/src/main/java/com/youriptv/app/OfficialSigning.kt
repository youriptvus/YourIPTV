package com.youriptv.app

import android.content.Context
import android.content.pm.PackageManager
import android.os.Build
import android.util.Log
import java.security.MessageDigest

internal enum class SigningStatus { TRUSTED, UNTRUSTED, UNAVAILABLE }

internal object OfficialSigning {
    const val RELEASE_CERTIFICATE_SHA256 = "4aef8798d4038bb8b20486b96b50af0870bca9d9b244a541a8973f76cecd0539"
    private const val TAG = "YourIPTV-Signing"
    private val expectedDigest = RELEASE_CERTIFICATE_SHA256.chunked(2).map { it.toInt(16).toByte() }.toByteArray()

    fun check(context: Context): SigningStatus {
        if (BuildConfig.DEBUG) return SigningStatus.TRUSTED
        return try {
            val pm = context.packageManager
            val trusted = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.P) {
                pm.hasSigningCertificate(context.packageName, expectedDigest, PackageManager.CERT_INPUT_SHA256)
            } else {
                @Suppress("DEPRECATION")
                val info = pm.getPackageInfo(context.packageName, PackageManager.GET_SIGNATURES)
                @Suppress("DEPRECATION")
                val signers = info.signatures?.map { it.toByteArray() }.orEmpty()
                matchesSingleSigner(signers, expectedDigest)
            }
            if (trusted) SigningStatus.TRUSTED else {
                Log.e(TAG, "The app signing certificate is not trusted.")
                SigningStatus.UNTRUSTED
            }
        } catch (e: PackageManager.NameNotFoundException) {
            Log.e(TAG, "The app signing certificate could not be found.", e)
            SigningStatus.UNAVAILABLE
        } catch (e: SecurityException) {
            Log.e(TAG, "The app signing certificate could not be inspected.", e)
            SigningStatus.UNAVAILABLE
        }
    }

    internal fun matchesSingleSigner(signers: List<ByteArray>, expected: ByteArray): Boolean =
        signers.size == 1 && MessageDigest.isEqual(MessageDigest.getInstance("SHA-256").digest(signers[0]), expected)
}
