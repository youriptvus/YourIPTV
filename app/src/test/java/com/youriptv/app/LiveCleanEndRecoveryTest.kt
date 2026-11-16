package com.youriptv.app

import com.youriptv.app.data.ItemKind
import com.youriptv.app.data.MediaItem
import com.youriptv.app.ui.LiveBufferingRecovery
import com.youriptv.app.ui.LiveCleanEndRecovery
import org.junit.Assert.*
import org.junit.Test

class LiveCleanEndRecoveryTest {
    private val live = MediaItem(
        playlistId = 1L,
        kind = ItemKind.LIVE,
        name = "Synthetic live channel",
        group = "Synthetic",
        url = "http://example.invalid/live/synthetic/account/123.ts",
    )

    private fun eligible(
        item: MediaItem = live,
        fullScreen: Boolean = true,
        ended: Boolean = true,
        hasError: Boolean = false,
        streamUrl: String = item.url,
    ) = LiveCleanEndRecovery.eligible(item, fullScreen, ended, hasError, streamUrl)

    private class Clock {
        var now = 0L
        val tasks = linkedMapOf<Runnable, Long>()
        val restarts = mutableListOf<Int>()
        var failures = 0
        val recovery = LiveBufferingRecovery(
            postDelayed = { task, delay -> tasks[task] = now + delay },
            remove = { task -> tasks.remove(task) },
            onRestart = { attempt -> restarts.add(attempt) },
            onExhausted = { failures++ },
        )

        fun advance(ms: Long) {
            require(ms >= 0)
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

        fun scheduleEnd(fastEligible: Boolean = true, normalDelayMs: Long = 1500L): Long {
            val delay = recovery.cleanEndDelayMs(normalDelayMs, fastEligible)
            assertTrue(recovery.scheduleRestart(delay))
            return delay
        }
    }

    @Test fun fullscreenCleanRemoteTsIsEligibleWithoutOpeningAConnection() {
        assertTrue(eligible())
        assertTrue(eligible(streamUrl = "HTTPS://example.invalid/live/123.TS?format=ts"))
    }

    @Test fun previewBufferingAndPlaybackErrorsAreNotEligible() {
        assertFalse(eligible(fullScreen = false))
        assertFalse(eligible(ended = false))
        assertFalse(eligible(hasError = true))
    }

    @Test fun moviesSeriesEpisodesCatchupAndLocalItemsAreNotEligible() {
        for (kind in ItemKind.entries.filter { it != ItemKind.LIVE }) {
            assertFalse(eligible(item = live.copy(kind = kind)))
        }
        assertFalse(eligible(item = live.copy(isCatchup = true)))
        assertFalse(eligible(item = live.copy(isLocal = true)))
    }

    @Test fun hlsNonHttpNonTsAndMalformedSourcesRemainOrdinary() {
        for (url in listOf(
            "https://example.invalid/live/123.m3u8",
            "https://example.invalid/live/123.ts?format=.m3u8",
            "https://example.invalid/live/123.mp4",
            "rtsp://example.invalid/live/123.ts",
            "file:///recording.ts",
            "https:/missing-host/123.ts",
            "https://example.invalid/live/123.ts/",
            "not a valid URI",
            "",
        )) assertFalse(eligible(streamUrl = url))
    }

    @Test fun actualSourceOverridesTheStoredCatalogExtensionAndMissingSourceFailsClosed() {
        assertFalse(eligible(streamUrl = "https://example.invalid/live/123.m3u8"))
        assertTrue(eligible(
            item = live.copy(url = "https://example.invalid/live/123.m3u8"),
            streamUrl = "https://example.invalid/live/123.ts",
        ))
        assertFalse(eligible(streamUrl = ""))
    }

    @Test fun fastSelectionIsPureAndZeroDelayRestartIsStillAsynchronous() {
        val c = Clock()
        assertEquals(0L, c.recovery.cleanEndDelayMs(1500L, true))
        assertEquals(0L, c.recovery.cleanEndDelayMs(1500L, true))
        assertFalse(c.recovery.hasPendingRestart)
        assertTrue(c.tasks.isEmpty())
        assertEquals(0L, c.scheduleEnd())
        assertTrue(c.restarts.isEmpty())
        c.advance(0)
        assertEquals(listOf(1), c.restarts)
        assertEquals(1500L, c.recovery.cleanEndDelayMs(1500L, true))
    }

    @Test fun duplicateCallbacksCannotAddAnotherImmediateRestart() {
        val c = Clock()
        c.scheduleEnd()
        assertFalse(c.recovery.scheduleRestart(c.recovery.cleanEndDelayMs(1500L, true)))
        assertEquals(1, c.tasks.size)
        c.advance(0)
        assertEquals(listOf(1), c.restarts)
    }

    @Test fun ineligibleAndLaterAttemptsKeepTheExactRequestedFallback() {
        val c = Clock()
        assertEquals(1700L, c.scheduleEnd(fastEligible = false, normalDelayMs = 1700L))
        c.advance(1699)
        assertTrue(c.restarts.isEmpty())
        c.advance(1)
        assertEquals(listOf(1), c.restarts)
        assertEquals(3000L, c.recovery.cleanEndDelayMs(3000L, true))
        assertEquals(4500L, c.recovery.cleanEndDelayMs(4500L, false))
    }

    @Test fun rapidCleanEndsRetainLaterWaitsAndTheThirtySecondSlowBackoff() {
        val c = Clock()
        assertEquals(0L, c.scheduleEnd())
        c.advance(0)
        assertEquals(1500L, c.scheduleEnd())
        c.advance(1499)
        assertEquals(listOf(1), c.restarts)
        c.advance(1)
        assertEquals(1500L, c.scheduleEnd())
        c.advance(1500)
        assertEquals(listOf(1, 2, 3), c.restarts)
        assertEquals(1500L, c.scheduleEnd())
        c.advance(1500)
        assertEquals(listOf(1, 2, 3), c.restarts)
        assertEquals(1, c.failures)
        assertFalse(c.recovery.scheduleRestart(0))
        c.advance(LiveBufferingRecovery.SLOW_RETRY_MS - 1)
        assertEquals(listOf(1, 2, 3), c.restarts)
        c.advance(1)
        assertEquals(listOf(1, 2, 3, 4), c.restarts)
    }

    @Test fun tenHealthySecondsRestoreFastEligibilityButShorterPlaybackDoesNot() {
        val c = Clock()
        c.scheduleEnd()
        c.advance(0)
        c.recovery.update(buffering = false, playing = true)
        c.advance(LiveBufferingRecovery.STABLE_PLAYBACK_MS - 1)
        assertEquals(1500L, c.recovery.cleanEndDelayMs(1500L, true))
        c.recovery.update(buffering = false, playing = false)
        c.scheduleEnd()
        c.advance(1500)
        assertEquals(listOf(1, 2), c.restarts)
        c.recovery.update(buffering = false, playing = true)
        c.advance(LiveBufferingRecovery.STABLE_PLAYBACK_MS)
        assertEquals(0L, c.recovery.cleanEndDelayMs(1500L, true))
        c.recovery.update(buffering = false, playing = false)
        c.scheduleEnd()
        c.advance(0)
        assertEquals(listOf(1, 2, 1), c.restarts)
    }

    @Test fun userPauseInvalidatesEvenAnAlreadyDequeuedZeroDelayCallback() {
        val c = Clock()
        c.scheduleEnd()
        val stale = c.tasks.keys.single()
        c.recovery.update(buffering = false, playing = false, playWhenReady = false)
        stale.run()
        c.advance(60_000)
        assertTrue(c.restarts.isEmpty())
        assertTrue(c.tasks.isEmpty())
    }

    @Test fun channelResetRejectsAnOldImmediateCallbackWithoutConsumingTheNewOne() {
        val c = Clock()
        c.scheduleEnd()
        val oldChannelTask = c.tasks.keys.single()
        c.recovery.reset()
        c.scheduleEnd()
        oldChannelTask.run()
        assertTrue(c.restarts.isEmpty())
        assertTrue(c.recovery.hasPendingRestart)
        c.advance(0)
        assertEquals(listOf(1), c.restarts)
    }

    @Test fun playbackResumingBeforeTheImmediateCallbackCancelsTheRestart() {
        val c = Clock()
        c.scheduleEnd()
        val stale = c.tasks.keys.single()
        c.recovery.update(buffering = false, playing = true)
        stale.run()
        c.advance(0)
        assertTrue(c.restarts.isEmpty())
        assertEquals(0, c.failures)
    }

    @Test fun ordinaryErrorDelayAndBufferingWatchdogAreNotAccelerated() {
        val c = Clock()
        assertTrue(c.recovery.scheduleRestart(4500L))
        c.advance(4499)
        assertTrue(c.restarts.isEmpty())
        c.advance(1)
        assertEquals(listOf(1), c.restarts)
        c.recovery.update(buffering = true, playing = false)
        c.advance(LiveBufferingRecovery.BUFFERING_TIMEOUT_MS - 1)
        assertEquals(listOf(1), c.restarts)
        c.advance(1)
        assertEquals(listOf(1, 2), c.restarts)
    }

    @Test(expected = IllegalArgumentException::class)
    fun invalidFallbackIsRejectedEvenForAnEligibleFirstAttempt() {
        Clock().recovery.cleanEndDelayMs(-1L, true)
    }
}
