package com.youriptv.app.ui

import android.os.Build
import android.os.Bundle
import android.view.KeyEvent
import android.widget.ScrollView
import android.widget.TextView
import com.youriptv.app.R

class AboutActivity : RemoteActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_about)
        findViewById<TextView>(R.id.about_title).text = "About ${Branding.displayName(this)}"
        val version = runCatching { packageManager.getPackageInfo(packageName, 0).versionName }.getOrNull().orEmpty()
        findViewById<TextView>(R.id.about_version).text = "Version $version · Android ${Build.VERSION.RELEASE} · ${Build.MODEL}"
        findViewById<TextView>(R.id.about_body).text = Branding.aboutBody(this)
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
}
