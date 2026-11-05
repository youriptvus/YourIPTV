package com.youriptv.app.ui

internal class LiveBufferingRecovery(
    private val postDelayed: (Runnable, Long) -> Unit,
    private val remove: (Runnable) -> Unit,
    private val onRestart: (Int) -> Unit,
    private val onExhausted: () -> Unit,
    private val onStalled: () -> Unit = {},
) {
    companion object {
        const val BUFFERING_TIMEOUT_MS = 30_000L
        // A reopen that plays this long worked, so the next drop gets quick reopens again.
        const val STABLE_PLAYBACK_MS = 10_000L
        const val MAX_RESTARTS = 3
        const val SLOW_RETRY_MS = 30_000L
    }

    private var timeout: Runnable? = null
    private var stable: Runnable? = null
    private var pendingRestart: Runnable? = null
    private var attempts = 0
    private var waitingSlowly = false

    val hasPendingRestart get() = pendingRestart != null

    fun update(buffering: Boolean, playing: Boolean, playWhenReady: Boolean = true) {
        if (!playWhenReady) {
            timeout?.let(remove)
            stable?.let(remove)
            pendingRestart?.let(remove)
            timeout = null
            stable = null
            pendingRestart = null
            waitingSlowly = false
            return
        }
        if (!buffering) {
            timeout?.let(remove)
            timeout = null
        }
        if (!playing) {
            stable?.let(remove)
            stable = null
        }
        if (playing) {
            pendingRestart?.let(remove)
            pendingRestart = null
            waitingSlowly = false
        }
        if (waitingSlowly) return
        if (playing && stable == null) {
            lateinit var task: Runnable
            task = Runnable {
                if (stable === task) {
                    stable = null
                    attempts = 0
                }
            }
            stable = task
            postDelayed(task, STABLE_PLAYBACK_MS)
        }
        if (buffering && timeout == null && pendingRestart == null) {
            lateinit var task: Runnable
            task = Runnable {
                if (timeout === task) {
                    timeout = null
                    onStalled()
                    restart()
                }
            }
            timeout = task
            postDelayed(task, BUFFERING_TIMEOUT_MS)
        }
    }

    fun scheduleRestart(delayMs: Long): Boolean {
        require(delayMs >= 0)
        if (pendingRestart != null) return false
        timeout?.let(remove)
        stable?.let(remove)
        timeout = null
        stable = null
        lateinit var task: Runnable
        task = Runnable {
            if (pendingRestart === task) {
                pendingRestart = null
                restart()
            }
        }
        pendingRestart = task
        postDelayed(task, delayMs)
        return true
    }

    private fun restart() {
        timeout?.let(remove)
        stable?.let(remove)
        pendingRestart?.let(remove)
        timeout = null
        stable = null
        pendingRestart = null
        waitingSlowly = false
        if (attempts < MAX_RESTARTS) {
            onRestart(++attempts)
            return
        }
        // Quick restarts failed: release the source, then keep reopening it at a slower pace until it plays.
        waitingSlowly = true
        onExhausted()
        lateinit var task: Runnable
        task = Runnable {
            if (pendingRestart === task) {
                pendingRestart = null
                waitingSlowly = false
                onRestart(++attempts)
            }
        }
        pendingRestart = task
        postDelayed(task, SLOW_RETRY_MS)
    }

    fun reset() {
        timeout?.let(remove)
        stable?.let(remove)
        pendingRestart?.let(remove)
        timeout = null
        stable = null
        pendingRestart = null
        attempts = 0
        waitingSlowly = false
    }
}
