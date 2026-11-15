package com.youriptv.app.refresh

import com.youriptv.app.data.Playlist
import com.youriptv.app.data.SourceType
import org.junit.Assert.*
import org.junit.Test

class RefreshSchedulingTest {
    private val hour = 3_600_000L
    private val now = 1_800_000_000_000L
    private fun playlist(id: Long, ageHours: Long, interval: Int = 16) =
        Playlist(id, "Fixture", SourceType.M3U, "http://fixture.invalid/list", lastUpdated = now - ageHours * hour, refreshHours = interval)

    @Test fun scheduleSelectsNearestPlaylistAndPreservesDisabledAndCustomIntervals() {
        val rows = listOf(playlist(1, 15), playlist(2, 6, 8), playlist(3, 99, 0))
        assertEquals(now + hour, RefreshJobScheduler.nextRunAt(rows, emptySet(), emptySet(), now))
        assertNull(RefreshPolicy.catalogDueAt(now - 99 * hour, 0, now))
    }

    @Test fun failedAttemptDoesNotMoveSuccessButBacksOffOverdueWork() {
        val old = now - 20 * hour
        val attempt = now - 5 * 60_000
        val due = RefreshPolicy.eligibleAt(old, 16, attempt, true, now)
        assertEquals(attempt + RefreshPolicy.RETRY_MS, due)
        assertEquals(old + 16 * hour, RefreshPolicy.catalogDueAt(old, 16, now))
    }

    @Test fun guideRecoveryCanRunWhenCatalogIsDisabledAndRemovedSourceDoesNotSpin() {
        val row = playlist(1, 4, 0).copy(epgLastAttempt = now - 10 * 60_000, epgLastUpdated = now - hour)
        assertEquals(now + 20 * 60_000, RefreshJobScheduler.nextRunAt(listOf(row), emptySet(), setOf(1L), now))
        assertNull(RefreshJobScheduler.nextRunAt(listOf(row), emptySet(), emptySet(), now))
    }

    @Test fun activeRefreshGetsRecoveryWakeupInsteadOfImmediateDuplicateJob() {
        assertEquals(now + RefreshPolicy.RETRY_MS,
            RefreshJobScheduler.nextRunAt(listOf(playlist(1, 99)), setOf(1L), emptySet(), now))
    }

    @Test fun clockRollbackAndRapidProcessStartsAreBounded() {
        assertEquals(now, RefreshPolicy.catalogDueAt(now + hour, 16, now))
        assertEquals(now, RefreshPolicy.retryAt(now + hour, now - hour, now))
        assertEquals(50_000L, RefreshPolicy.startupDelay(now - 10_000, now))
        assertEquals(0L, RefreshPolicy.startupDelay(now + hour, now))
        assertNull(RefreshPolicy.retryAt(now - hour, now, now))
    }

    @Test fun failureAfterClockRollbackStillBacksOffEvenWhenOldSuccessIsInFuture() {
        assertEquals(now + RefreshPolicy.RETRY_MS,
            RefreshPolicy.eligibleAt(now + hour, 16, now, true, now))
        val row = playlist(1, 1, 0).copy(epgLastUpdated = now + hour, epgLastAttempt = now, epgLastError = "Fixture failure")
        assertEquals(now + RefreshPolicy.RETRY_MS,
            RefreshJobScheduler.nextRunAt(listOf(row), emptySet(), setOf(1L), now))
    }
}
