package com.youriptv.app.ui

/** Separates a physical Back press from a hold, including remotes that send no key repeats. */
internal class BackHoldGesture(
    private val postDelayed: (Runnable, Long) -> Unit,
    private val remove: (Runnable) -> Unit,
    private val onShortPress: () -> Unit,
    private val onLongPress: () -> Unit,
) {
    var isPressed = false
        private set
    private var fired = false
    private val timeout = Runnable { fireLongPress() }

    fun press() {
        cancel()
        isPressed = true
        postDelayed(timeout, HOLD_MS)
    }

    fun repeat(elapsedMs: Long, longPress: Boolean) {
        if (isPressed && (longPress || elapsedMs >= HOLD_MS)) fireLongPress()
    }

    fun release(canceled: Boolean, elapsedMs: Long) {
        if (!isPressed) return
        remove(timeout)
        val held = fired || elapsedMs >= HOLD_MS
        val alreadyFired = fired
        isPressed = false
        fired = false
        if (canceled || alreadyFired) return
        if (held) onLongPress() else onShortPress()
    }

    fun cancel() {
        remove(timeout)
        isPressed = false
        fired = false
    }

    private fun fireLongPress() {
        if (!isPressed || fired) return
        fired = true
        remove(timeout)
        onLongPress()
    }

    companion object {
        const val HOLD_MS = 600L
    }
}
