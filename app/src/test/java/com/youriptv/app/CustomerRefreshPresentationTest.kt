package com.youriptv.app

import com.youriptv.app.data.Playlist
import com.youriptv.app.data.SourceType
import com.youriptv.app.ui.CustomerRefreshPresentation
import org.junit.Assert.*
import org.junit.Test

class CustomerRefreshPresentationTest {
    private val playlist = Playlist(id = 5, name = "YourIPTV", type = SourceType.M3U,
        url = "https://fixture.invalid/private-list", username = "private-user", password = "private-password")

    @Test
    fun showsEffectiveIntervalWithoutReplacingSavedCustomOrDisabledValues() {
        assertEquals("Every 16 hours", CustomerRefreshPresentation.from(playlist, false).interval)
        assertEquals("Every 24 hours", CustomerRefreshPresentation.from(playlist.copy(refreshHours = 24), false).interval)
        assertEquals("Off", CustomerRefreshPresentation.from(playlist.copy(refreshHours = 0), false).interval)
    }

    @Test
    fun failedAttemptDoesNotBecomeTheLastSuccessAndDisablesDuplicateRequestsWhileBusy() {
        val record = playlist.copy(lastUpdated = 1_000L, lastRefreshAttempt = 2_000L,
            lastRefreshError = "Playlist download failed. Cached channels were kept.", epgLastUpdated = 900L, epgLastAttempt = 1_900L)
        val failed = CustomerRefreshPresentation.from(record, false)
        assertEquals(1_000L, failed.playlistSuccess)
        assertEquals(2_000L, failed.playlistAttempt)
        assertEquals(900L, failed.epgSuccess)
        assertEquals(1_900L, failed.epgAttempt)
        assertEquals("Failed", failed.status)
        assertTrue(failed.canRequest)
        val running = CustomerRefreshPresentation.from(record, true, "Downloading guide")
        assertFalse(running.canRequest)
        assertTrue(running.busy)
        assertEquals("Updating · Downloading guide", running.status)
        assertEquals(1_000L, running.playlistSuccess)
    }

    @Test
    fun completionReflectsTheObservedOperationWithoutHidingAnEarlierIndependentError() {
        val record = playlist.copy(lastRefreshError = "Catalog request failed", epgLastUpdated = 3_000L)
        val view = CustomerRefreshPresentation.from(record, false, "Guide updated", lastResultSuccess = true)
        assertEquals("Completed · Guide updated", view.status)
        assertEquals("Catalog request failed", view.playlistError)
        assertEquals(3_000L, view.epgSuccess)
    }

    @Test
    fun missingPlaylistCannotRequestOrPretendToUpdate() {
        val view = CustomerRefreshPresentation.from(null, true, "Queued")
        assertFalse(view.canRequest)
        assertFalse(view.busy)
        assertEquals("No active playlist", view.status)
        assertEquals(0L, view.playlistSuccess)
    }

    @Test
    fun publicStatusNeverIncludesCredentialAddressesOrUnboundedDiagnosticText() {
        val unsafe = "Failed https://fixture.invalid/list?username=private-user&password=private-password user=private-user token=private-token\n" + "x".repeat(500)
        val view = CustomerRefreshPresentation.from(playlist.copy(lastRefreshError = unsafe), false, unsafe)
        for (text in listOf(view.status, view.playlistError)) {
            assertFalse(text.contains("https://"))
            assertFalse(text.contains("private-user"))
            assertFalse(text.contains("private-password"))
            assertFalse(text.contains("private-token"))
            assertFalse(text.contains("\n"))
        }
        assertTrue(view.playlistError.length <= 240)
        assertFalse(view.toString().contains("private-list"))
    }
}
