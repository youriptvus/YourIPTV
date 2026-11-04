package com.youriptv.app

import com.youriptv.app.ui.LiveBufferingRecovery
import org.junit.Assert.*
import org.junit.Test

class LiveBufferingRecoveryTest {
    private class Clock {
        var now = 0L
        val tasks = mutableMapOf<Runnable, Long>()
        val restarts = mutableListOf<Int>()
        var failures = 0
        val recovery = LiveBufferingRecovery(
            postDelayed = { task, delay -> tasks[task] = now + delay },
            remove = { tasks.remove(it) },
            onRestart = { restarts.add(it) },
            onExhausted = { failures++ },
        )

        fun advance(ms: Long) {
            val until = now + ms
            while (true) {
                val next = tasks.minByOrNull { it.value } ?: break
                if (next.value > until) break
                now = next.value
                tasks.remove(next.key)
                next.key.run()
            }
            now = until
        }
    }

    @Test fun ordinaryBufferingDoesNotRestartOrExtendTheDeadline() {
        val c = Clock()
        c.recovery.update(buffering = true, playing = false)
        c.advance(20_000)
        c.recovery.update(buffering = true, playing = false)
        c.advance(9_999)
        assertTrue(c.restarts.isEmpty())
        c.advance(1)
        assertEquals(listOf(1), c.restarts)
    }

    @Test fun shortBufferingAndPauseCancelTheTimeout() {
        val c = Clock()
        c.recovery.update(true, false)
        c.advance(29_000)
        c.recovery.update(false, true)
        c.advance(2_000)
        assertTrue(c.restarts.isEmpty())
        c.recovery.update(true, false)
        c.advance(10_000)
        c.recovery.update(false, false)
        c.advance(60_000)
        assertTrue(c.restarts.isEmpty())
    }

    @Test fun repeatedStallsAfterQuickRestartsKeepRetryingAtTheSlowPace() {
        val c = Clock()
        repeat(3) {
            c.recovery.update(true, false)
            c.advance(30_000)
            c.recovery.update(false, true)
            c.advance(5_000)
        }
        c.recovery.update(true, false)
        c.advance(30_000)
        assertEquals(listOf(1, 2, 3), c.restarts)
        assertEquals(1, c.failures)
        c.recovery.update(false, false)
        c.advance(LiveBufferingRecovery.SLOW_RETRY_MS - 1)
        assertEquals(3, c.restarts.size)
        c.advance(1)
        assertEquals(listOf(1, 2, 3, 4), c.restarts)
    }

    @Test fun aStreamStuckForeverIsNeverLeftWithoutAnotherReload() {
        val c = Clock()
        // Plays normally for a long time with a few short buffering events.
        repeat(5) {
            c.recovery.update(false, true)
            c.advance(600_000)
            c.recovery.update(true, false)
            c.advance(8_000)
        }
        c.recovery.update(false, true)
        c.advance(600_000)
        assertTrue(c.restarts.isEmpty())
        // Then it stops delivering data and never plays again; each reopened source buffers again.
        c.recovery.update(true, false)
        repeat(1_800) {
            c.advance(1_000)
            if (c.tasks.isEmpty()) c.recovery.update(true, false)
        }
        assertTrue("expected continued reloads, got ${c.restarts}", c.restarts.size >= 25)
        assertEquals((1..c.restarts.size).toList(), c.restarts)
        assertTrue(c.tasks.isNotEmpty())
    }

    @Test fun sustainedPlaybackResetsTheBudgetButInterruptedPlaybackDoesNot() {
        val c = Clock()
        c.recovery.update(true, false)
        c.advance(30_000)
        c.recovery.update(false, true)
        c.advance(LiveBufferingRecovery.STABLE_PLAYBACK_MS - 1)
        c.recovery.update(true, false)
        c.advance(30_000)
        assertEquals(listOf(1, 2), c.restarts)
        c.recovery.update(false, true)
        c.advance(LiveBufferingRecovery.STABLE_PLAYBACK_MS)
        c.recovery.update(true, false)
        c.advance(30_000)
        assertEquals(listOf(1, 2, 1), c.restarts)
    }

    @Test fun aStreamThatKeepsDroppingAfterWorkingReopensIsReopenedQuicklyEveryTime() {
        val c = Clock()
        repeat(20) {
            c.recovery.update(false, true)
            c.advance(20_000)
            c.recovery.update(false, false)
            assertTrue(c.recovery.scheduleRestart(1500))
            c.advance(1500)
        }
        assertEquals(List(20) { 1 }, c.restarts)
        assertEquals(0, c.failures)
    }

    @Test fun resetInvalidatesCallbacksFromPreviousChannelOrReleasedPlayer() {
        val c = Clock()
        c.recovery.update(true, false)
        val old = c.tasks.keys.single()
        c.recovery.reset()
        c.recovery.update(true, false)
        old.run()
        assertTrue(c.restarts.isEmpty())
        c.advance(30_000)
        assertEquals(listOf(1), c.restarts)
        c.recovery.reset()
        c.advance(1_800_000)
        assertEquals(1, c.restarts.size)
    }

    @Test fun liveRecoveryUsesTheSamePlayerAndReplacesTheSource() {
        val source = listOf(
            java.io.File("src/main/java/com/youriptv/app/ui/PlaybackFactory.kt"),
            java.io.File("app/src/main/java/com/youriptv/app/ui/PlaybackFactory.kt"),
        ).first { it.exists() }.readText().substringAfter("fun restartLive(")
        assertTrue(source.contains("require(item.kind == ItemKind.LIVE && !item.isCatchup)"))
        assertTrue(source.indexOf("player.stop()") < source.indexOf("player.setMediaItem(mediaItem(item))"))
        assertTrue(source.indexOf("player.setMediaItem(mediaItem(item))") < source.indexOf("player.prepare()"))
        assertFalse(source.contains("ExoPlayer.Builder"))
    }

    @Test fun endedAndErrorRetriesShareTheBufferingBudget() {
        val c = Clock()
        c.recovery.update(true, false)
        c.advance(30_000)
        c.recovery.update(false, true)
        c.advance(5_000)
        c.recovery.update(false, false)
        assertTrue(c.recovery.scheduleRestart(1500))
        c.advance(1500)
        c.recovery.update(true, false)
        c.advance(30_000)
        c.recovery.update(false, true)
        c.advance(5_000)
        c.recovery.update(false, false)
        assertTrue(c.recovery.scheduleRestart(0))
        c.advance(0)
        assertEquals(listOf(1, 2, 3), c.restarts)
        assertEquals(1, c.failures)
        assertFalse("the slow retry is already queued", c.recovery.scheduleRestart(0))
        c.advance(LiveBufferingRecovery.SLOW_RETRY_MS)
        assertEquals(listOf(1, 2, 3, 4), c.restarts)
    }

    @Test fun duplicateErrorsDoNotScheduleAdditionalSourceRestarts() {
        val c = Clock()
        assertTrue(c.recovery.scheduleRestart(1500))
        assertTrue(c.recovery.hasPendingRestart)
        assertFalse(c.recovery.scheduleRestart(1500))
        c.advance(1499)
        assertTrue(c.restarts.isEmpty())
        c.advance(1)
        assertEquals(listOf(1), c.restarts)
        assertFalse(c.recovery.hasPendingRestart)
    }

    @Test fun aScheduledErrorRetryInvalidatesTheBufferingTimer() {
        val c = Clock()
        c.recovery.update(true, false)
        val oldTimeout = c.tasks.keys.single()
        c.advance(29_000)
        assertTrue(c.recovery.scheduleRestart(1500))
        c.advance(1000)
        oldTimeout.run()
        assertTrue(c.restarts.isEmpty())
        c.advance(500)
        assertEquals(listOf(1), c.restarts)
        c.advance(30_000)
        assertEquals(listOf(1), c.restarts)
    }

    @Test fun resumedPlaybackAndUserPauseCancelDelayedRetries() {
        val c = Clock()
        c.recovery.scheduleRestart(1500)
        val oldRetry = c.tasks.keys.single()
        c.recovery.update(false, true)
        oldRetry.run()
        c.advance(1500)
        assertTrue(c.restarts.isEmpty())
        c.recovery.update(false, false)
        c.recovery.scheduleRestart(1500)
        c.recovery.update(false, false, playWhenReady = false)
        c.advance(60_000)
        assertTrue(c.restarts.isEmpty())
        assertTrue(c.tasks.isEmpty())
    }

    @Test fun manualRetryResetsAnExhaustedBudgetAndInvalidatesQueuedFailures() {
        val c = Clock()
        repeat(4) {
            c.recovery.scheduleRestart(0)
            c.advance(0)
        }
        assertEquals(1, c.failures)
        c.recovery.reset()
        c.recovery.scheduleRestart(1500)
        val stale = c.tasks.keys.single()
        c.recovery.reset()
        stale.run()
        assertEquals(listOf(1, 2, 3), c.restarts)
        c.recovery.scheduleRestart(0)
        c.advance(0)
        assertEquals(listOf(1, 2, 3, 1), c.restarts)
    }

    @Test fun everyAutomaticLiveRetryUsesTheSharedCoordinator() {
        val player = listOf(
            java.io.File("src/main/java/com/youriptv/app/ui/PlayerActivity.kt"),
            java.io.File("app/src/main/java/com/youriptv/app/ui/PlayerActivity.kt"),
        ).first { it.exists() }.readText()
        val ended = player.substringAfter("private fun onEnded()").substringBefore("private fun savePosition()")
        val errors = player.substringAfter("private fun handleError(").substringBefore("private fun showMessage(")
        assertTrue(ended.contains("retryAutomatically(p, item, 1500L)"))
        assertTrue(errors.contains("retryAutomatically(p, item, 0L)"))
        assertTrue(errors.contains("retryAutomatically(p, failedItem, 1500L * retries)"))
        assertTrue(errors.contains("retryAutomatically(it, item, 0L)"))
        assertFalse(ended.contains("PlaybackFactory.restartLive"))
        assertFalse(errors.contains("PlaybackFactory.restartLive"))
    }
}
