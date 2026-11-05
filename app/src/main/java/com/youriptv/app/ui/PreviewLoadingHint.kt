package com.youriptv.app.ui

internal class PreviewLoadingHint(
    private val postDelayed: (Runnable, Long) -> Unit,
    private val remove: (Runnable) -> Unit,
    private val onSlow: () -> Unit,
) {
    companion object {
        const val DELAY_MS = 20_000L
    }

    private var scheduled: Runnable? = null
    var isSlow = false
        private set

    fun start() {
        if (scheduled != null || isSlow) return
        lateinit var task: Runnable
        task = Runnable {
            if (scheduled === task) {
                scheduled = null
                isSlow = true
                onSlow()
            }
        }
        scheduled = task
        postDelayed(task, DELAY_MS)
    }

    fun reset() {
        scheduled?.let(remove)
        scheduled = null
        isSlow = false
    }
}
