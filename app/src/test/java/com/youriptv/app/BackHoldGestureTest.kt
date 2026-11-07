package com.youriptv.app

import com.youriptv.app.ui.BackHoldGesture
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

/** Physical Back gestures tested with a clock and scheduler, without Android key-event stubs. */
class BackHoldGestureTest {
    private class Harness {
        var now = 0L
        val actions = mutableListOf<String>()
        private val scheduled = linkedMapOf<Runnable, Long>()
        val gesture = BackHoldGesture(
            postDelayed = { task, delay -> scheduled[task] = now + delay },
            remove = { scheduled.remove(it) },
            onShortPress = { actions += "short" },
            onLongPress = { actions += "long" },
        )

        val pendingCount get() = scheduled.size

        fun advanceBy(duration: Long) {
            val target = now + duration
            while (true) {
                val next = scheduled.entries.minByOrNull { it.value } ?: break
                if (next.value > target) break
                now = next.value
                scheduled.remove(next.key)
                next.key.run()
            }
            now = target
        }

        fun delayedCallbacks() = scheduled.keys.toList()
    }

    @Test
    fun shortPressWaitsForReleaseAndDoesNotLeaveATimer() {
        val h = Harness()
        h.gesture.press()
        assertTrue(h.gesture.isPressed)
        h.advanceBy(BackHoldGesture.HOLD_MS - 1)
        assertTrue(h.actions.isEmpty())

        h.gesture.release(canceled = false, elapsedMs = h.now)
        assertEquals(listOf("short"), h.actions)
        assertFalse(h.gesture.isPressed)
        assertEquals(0, h.pendingCount)
        h.advanceBy(BackHoldGesture.HOLD_MS * 2)
        assertEquals(listOf("short"), h.actions)
    }

    @Test
    fun holdingWithoutRepeatsFiresAtTheThresholdOnceAndSuppressesShortRelease() {
        val h = Harness()
        h.gesture.press()
        h.advanceBy(BackHoldGesture.HOLD_MS - 1)
        assertTrue(h.actions.isEmpty())
        h.advanceBy(1)
        assertEquals(listOf("long"), h.actions)
        h.advanceBy(BackHoldGesture.HOLD_MS * 3)
        h.gesture.release(canceled = false, elapsedMs = h.now)
        assertEquals(listOf("long"), h.actions)
        assertFalse(h.gesture.isPressed)
    }

    @Test
    fun ordinaryRepeatsDoNotNavigateOrDelayTheHold() {
        val h = Harness()
        h.gesture.press()
        h.advanceBy(100)
        h.gesture.repeat(elapsedMs = h.now, longPress = false)
        h.advanceBy(100)
        h.gesture.repeat(elapsedMs = h.now, longPress = false)
        assertTrue(h.actions.isEmpty())
        h.advanceBy(BackHoldGesture.HOLD_MS - h.now)
        assertEquals(listOf("long"), h.actions)
        h.gesture.release(canceled = false, elapsedMs = h.now)
        assertEquals(listOf("long"), h.actions)
    }

    @Test
    fun longPressFlagAndFurtherRepeatsCannotFireTwice() {
        val h = Harness()
        h.gesture.press()
        h.gesture.repeat(elapsedMs = 100, longPress = true)
        assertEquals(listOf("long"), h.actions)
        h.gesture.repeat(elapsedMs = BackHoldGesture.HOLD_MS, longPress = true)
        h.gesture.repeat(elapsedMs = BackHoldGesture.HOLD_MS + 1, longPress = false)
        h.advanceBy(BackHoldGesture.HOLD_MS * 2)
        h.gesture.release(canceled = false, elapsedMs = h.now)
        assertEquals(listOf("long"), h.actions)
        assertEquals(0, h.pendingCount)
    }

    @Test
    fun elapsedRepeatCanRecognizeAHoldWhenTheTimerIsDelayed() {
        val h = Harness()
        h.gesture.press()
        h.now = BackHoldGesture.HOLD_MS
        h.gesture.repeat(elapsedMs = h.now, longPress = false)
        h.gesture.release(canceled = false, elapsedMs = h.now)
        h.advanceBy(BackHoldGesture.HOLD_MS)
        assertEquals(listOf("long"), h.actions)
    }

    @Test
    fun delayedReleaseRecognizesAHoldEvenBeforeItsTimerRuns() {
        val h = Harness()
        h.gesture.press()
        h.now = BackHoldGesture.HOLD_MS
        h.gesture.release(canceled = false, elapsedMs = h.now)
        assertEquals(listOf("long"), h.actions)
        assertEquals(0, h.pendingCount)
        h.advanceBy(BackHoldGesture.HOLD_MS)
        assertEquals(listOf("long"), h.actions)
    }

    @Test
    fun canceledReleaseNeverNavigatesEvenIfItsElapsedTimeExceedsTheThreshold() {
        for (duration in listOf(100L, BackHoldGesture.HOLD_MS + 1)) {
            val h = Harness()
            h.gesture.press()
            h.now = duration
            h.gesture.release(canceled = true, elapsedMs = duration)
            h.advanceBy(BackHoldGesture.HOLD_MS * 2)
            assertTrue(h.actions.isEmpty())
            assertFalse(h.gesture.isPressed)
            assertEquals(0, h.pendingCount)
        }
    }

    @Test
    fun focusLossCancellationPreventsTheTimerAndLateReleaseFromNavigating() {
        val h = Harness()
        h.gesture.press()
        h.advanceBy(100)
        val alreadyQueued = h.delayedCallbacks()
        h.gesture.cancel()
        assertFalse(h.gesture.isPressed)
        assertEquals(0, h.pendingCount)
        h.advanceBy(BackHoldGesture.HOLD_MS * 2)
        alreadyQueued.forEach { it.run() }
        h.gesture.release(canceled = false, elapsedMs = h.now)
        assertTrue(h.actions.isEmpty())
    }

    @Test
    fun rapidSeparatePressesEachHaveTheirOwnDurationAndOutcome() {
        val h = Harness()
        repeat(2) {
            h.gesture.press()
            h.advanceBy(50)
            h.gesture.release(canceled = false, elapsedMs = 50)
        }
        h.gesture.press()
        h.advanceBy(BackHoldGesture.HOLD_MS)
        h.gesture.release(canceled = false, elapsedMs = BackHoldGesture.HOLD_MS)
        h.gesture.press()
        h.advanceBy(10)
        h.gesture.release(canceled = false, elapsedMs = 10)
        h.advanceBy(BackHoldGesture.HOLD_MS * 2)
        assertEquals(listOf("short", "short", "long", "short"), h.actions)
        assertFalse(h.gesture.isPressed)
    }

    @Test
    fun releaseAndRepeatsWithoutAnInitialPressDoNothing() {
        val h = Harness()
        h.gesture.release(canceled = false, elapsedMs = BackHoldGesture.HOLD_MS)
        h.gesture.repeat(elapsedMs = BackHoldGesture.HOLD_MS, longPress = true)
        h.gesture.cancel()
        assertTrue(h.actions.isEmpty())
        assertFalse(h.gesture.isPressed)
        assertEquals(0, h.pendingCount)
    }
}
