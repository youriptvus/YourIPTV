package com.youriptv.app.ui

import android.content.pm.ActivityInfo
import android.content.res.Configuration
import android.os.Bundle
import android.view.KeyEvent
import android.view.View
import androidx.appcompat.app.AppCompatActivity
import androidx.core.view.ViewCompat
import androidx.core.view.WindowCompat
import androidx.core.view.WindowInsetsCompat

/** Shared remote gesture runs before individual screens can act on Back. */
abstract class RemoteActivity : AppCompatActivity() {
    private var backKeys: RemoteBackKeys? = null
    private var insetPadding: IntArray? = null

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        val television = (resources.configuration.uiMode and Configuration.UI_MODE_TYPE_MASK) == Configuration.UI_MODE_TYPE_TELEVISION
        // One activity and player implementation: TV keeps its remote-oriented layout;
        // phones and tablets follow the user's rotation and multi-window preferences.
        if (television) requestedOrientation = ActivityInfo.SCREEN_ORIENTATION_SENSOR_LANDSCAPE
        WindowCompat.setDecorFitsSystemWindows(window, false)
    }

    override fun onContentChanged() {
        super.onContentChanged()
        val content = findViewById<View>(android.R.id.content) ?: return
        val initial = insetPadding ?: intArrayOf(content.paddingLeft, content.paddingTop,
            content.paddingRight, content.paddingBottom).also { insetPadding = it }
        ViewCompat.setOnApplyWindowInsetsListener(content) { view, insets ->
            val safe = insets.getInsets(WindowInsetsCompat.Type.systemBars() or
                WindowInsetsCompat.Type.displayCutout() or WindowInsetsCompat.Type.ime())
            view.setPadding(initial[0] + safe.left, initial[1] + safe.top,
                initial[2] + safe.right, initial[3] + safe.bottom)
            insets
        }
        ViewCompat.requestApplyInsets(content)
    }

    final override fun dispatchKeyEvent(event: KeyEvent): Boolean {
        val keys = backKeys ?: RemoteBackKeys(
            available = { RemoteBack.enabled(this) && Nav.hasCurrentStream(this) },
            dispatch = { dispatchAppKeyEvent(it) },
            held = { Nav.returnToCurrentStream(this) },
        ).also { backKeys = it }
        return keys.handle(event) || dispatchAppKeyEvent(event)
    }

    protected open fun dispatchAppKeyEvent(event: KeyEvent): Boolean = super.dispatchKeyEvent(event)

    override fun onPause() {
        backKeys?.cancel()
        super.onPause()
    }

    override fun onWindowFocusChanged(hasFocus: Boolean) {
        if (!hasFocus) backKeys?.cancel()
        super.onWindowFocusChanged(hasFocus)
    }
}
