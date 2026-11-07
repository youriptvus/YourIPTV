package com.youriptv.app.ui

import android.annotation.SuppressLint
import android.app.Activity
import android.app.Dialog
import android.os.Handler
import android.os.Looper
import android.view.KeyEvent
import android.view.View
import android.view.Window
import com.youriptv.app.YourIptvApp

/** Physical remote Back holds; Android's touch/predictive Back dispatch remains unchanged. */
@SuppressLint("GestureBackNavigation")
object RemoteBack {
    private var consumedHold: Long? = null

    internal fun enabled(activity: Activity) =
        YourIptvApp.instance.isTrustedBuild && activity !is AppUpdateActivity

    internal fun consumeHold(downTime: Long) { consumedHold = downTime }

    // A captured press can finish in another window after navigation or focus loss.
    internal fun consumeContinuation(event: KeyEvent): Boolean {
        if (event.keyCode != KeyEvent.KEYCODE_BACK) return false
        if (event.action == KeyEvent.ACTION_DOWN && event.repeatCount == 0) {
            consumedHold = null
            return false
        }
        if (consumedHold != event.downTime) return false
        if (event.action == KeyEvent.ACTION_UP) consumedHold = null
        return true
    }

    /** Call after show(): dialogs receive key events in their own window, ahead of the Activity. */
    fun bind(dialog: Dialog, activity: Activity) {
        if (!enabled(activity)) return
        val window = dialog.window ?: return
        val original = window.callback ?: return
        if (original is BackWindowCallback) return
        val keys = RemoteBackKeys(
            available = { enabled(activity) && !activity.isFinishing && Nav.hasCurrentStream(activity) },
            dispatch = { original.dispatchKeyEvent(it) },
            held = {
                // Cancel never activates a Save/Install/PIN confirmation button.
                dialog.cancel()
                if (!activity.isFinishing) Nav.returnToCurrentStream(activity)
            },
        )
        window.callback = BackWindowCallback(original, keys)
        window.decorView.addOnAttachStateChangeListener(object : View.OnAttachStateChangeListener {
            override fun onViewAttachedToWindow(v: View) = Unit
            override fun onViewDetachedFromWindow(v: View) { keys.cancel() }
        })
    }

    private class BackWindowCallback(
        private val original: Window.Callback,
        private val keys: RemoteBackKeys,
    ) : Window.Callback by original {
        override fun dispatchKeyEvent(event: KeyEvent) = keys.handle(event) || original.dispatchKeyEvent(event)
        override fun onWindowFocusChanged(hasFocus: Boolean) {
            if (!hasFocus) keys.cancel()
            original.onWindowFocusChanged(hasFocus)
        }
    }
}

// This handles physical remote keys only; gesture Back still uses the existing AndroidX callbacks.
@SuppressLint("GestureBackNavigation")
internal class RemoteBackKeys(
    private val available: () -> Boolean,
    private val dispatch: (KeyEvent) -> Boolean,
    private val held: () -> Unit,
) {
    private val handler = Handler(Looper.getMainLooper())
    private var down: KeyEvent? = null
    private var up: KeyEvent? = null
    private val gesture = BackHoldGesture(
        postDelayed = { task, delay -> handler.postDelayed(task, delay) },
        remove = { handler.removeCallbacks(it) },
        onShortPress = {
            down?.let { dispatch(it) }
            up?.let { dispatch(it) }
        },
        onLongPress = {
            down?.let { RemoteBack.consumeHold(it.downTime) }
            held()
        },
    )

    fun handle(event: KeyEvent): Boolean {
        if (event.keyCode != KeyEvent.KEYCODE_BACK) return false
        if (RemoteBack.consumeContinuation(event)) { cancel(swallowRelease = false); return true }
        when (event.action) {
            KeyEvent.ACTION_DOWN -> {
                if (gesture.isPressed) {
                    gesture.repeat(event.eventTime - (down?.eventTime ?: event.eventTime), event.isLongPress)
                    return true
                }
                if (event.repeatCount != 0 || !available()) return false
                down = KeyEvent(event)
                gesture.press()
                return true
            }
            KeyEvent.ACTION_UP -> {
                if (!gesture.isPressed) return false
                up = KeyEvent(event)
                try {
                    gesture.release(event.isCanceled, event.eventTime - (down?.eventTime ?: event.eventTime))
                } finally {
                    clearEvents()
                }
                return true
            }
        }
        return false
    }

    fun cancel(swallowRelease: Boolean = true) {
        if (swallowRelease && gesture.isPressed) down?.let { RemoteBack.consumeHold(it.downTime) }
        gesture.cancel()
        clearEvents()
    }

    private fun clearEvents() {
        down = null
        up = null
    }
}
