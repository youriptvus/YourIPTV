package com.youriptv.app.ui

import android.os.Build
import android.os.Bundle
import android.view.KeyEvent
import android.widget.ScrollView
import android.widget.TextView
import androidx.appcompat.app.AppCompatActivity
import com.youriptv.app.R

class AboutActivity : AppCompatActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_about)
        val version = runCatching { packageManager.getPackageInfo(packageName, 0).versionName }.getOrNull().orEmpty()
        findViewById<TextView>(R.id.about_version).text = "Version $version · Android ${Build.VERSION.RELEASE} · ${Build.MODEL}"
        findViewById<TextView>(R.id.about_body).text = BODY.replace("{free}", getString(R.string.free_notice))
        findViewById<ScrollView>(R.id.about_scroll).requestFocus()
    }

    override fun onKeyDown(keyCode: Int, event: KeyEvent): Boolean {
        val scroll = findViewById<ScrollView>(R.id.about_scroll)
        when (keyCode) {
            KeyEvent.KEYCODE_DPAD_DOWN -> { scroll.smoothScrollBy(0, 200); return true }
            KeyEvent.KEYCODE_DPAD_UP -> { scroll.smoothScrollBy(0, -200); return true }
        }
        return super.onKeyDown(keyCode, event)
    }

    private companion object {
        const val BODY = """{free}

FREE FOR EVERYONE
• Every feature in YourIPTV is available to every user.
• No YourIPTV account, registration, purchase, activation code, licence key, trial, or subscription exists or is required.
• There are no premium screens, locked settings, or artificial limits on playlists, favorites, or devices.

WHAT STILL APPLIES (NOT APP LICENSING)
• IPTV provider: YourIPTV contains no channels. You need a playlist URL or Xtream Codes login from your own provider, and the provider's account status, expiry date, and maximum simultaneous connections apply. If a provider allows one connection, another app or device using the account can block playback here.
• Device: available codecs, resolution, frame rate, HDR, and picture-in-picture depend on your device and Android version.
• Android: some features (such as picture-in-picture) require Android 8.0 or newer.
• Parental PIN: if you set a PIN, locked groups require it. "Block adult categories" hides adult groups in every playlist, and playlist management can be locked too. Settings has its own, separate Settings PIN. PINs are stored only as salted hashes on this device.

PRIVACY
• Playlists, credentials, favorites, history, and guide data are stored only on this device and sent only to the provider servers you configure.
• YourIPTV has no analytics, advertising, or accounts.

OPEN-SOURCE LIBRARIES (Apache License 2.0)
• AndroidX (AppCompat, Core, RecyclerView, Lifecycle)
• AndroidX Media3 / ExoPlayer
• Kotlin and kotlinx.coroutines
"""
    }
}
