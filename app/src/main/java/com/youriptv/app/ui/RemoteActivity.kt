package com.youriptv.app.ui

import android.view.KeyEvent
import androidx.appcompat.app.AppCompatActivity

/** Shared remote gesture runs before individual screens can act on Back. */
abstract class RemoteActivity : AppCompatActivity() {
    private var backKeys: RemoteBackKeys? = null

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
