package com.youriptv.app

import com.youriptv.app.updates.UpdateReceipt
import org.junit.Assert.*
import org.junit.Test

class UpdateReceiptTest {
    private class Store(override var pendingCode: Long = 0L) : UpdateReceipt.Store

    private fun receipt(store: Store, code: Long = 27, name: String = "0.5.3") = UpdateReceipt(code, name, store)

    @Test
    fun firstInstallNeverClaimsThatAnUpdateCompleted() {
        assertNull(receipt(Store()).current())
    }

    @Test
    fun failedOrCancelledInstallDoesNotShowASuccessReceipt() {
        val store = Store(27)
        assertNull(receipt(store, code = 26, name = "0.5.2").current())
        // The installer clears the existing target when it reports failure/cancellation.
        store.pendingCode = 0
        assertNull(receipt(store, code = 26, name = "0.5.2").current())
    }

    @Test
    fun legacyUpdaterTargetSurvivesProcessRecreationUntilAcknowledged() {
        val store = Store(27) // The same target key was already written by the 0.5.2 updater.
        val firstProcess = receipt(store)
        val shown = firstProcess.current()!!
        assertEquals(27L, store.pendingCode)
        assertEquals(shown, firstProcess.current()) // Presenting again after pause does not consume it.
        val restartedProcess = receipt(store)
        assertEquals(shown, restartedProcess.current())
        restartedProcess.acknowledge(shown)
        assertEquals(0L, store.pendingCode)
        assertNull(receipt(store).current())
    }

    @Test
    fun acknowledgementCannotClearANewerInstallAttempt() {
        val store = Store(27)
        val current = receipt(store)
        val shown = current.current()!!
        store.pendingCode = 28
        current.acknowledge(shown)
        assertEquals(28L, store.pendingCode)
        assertNull(current.current())
        assertEquals(28L, receipt(store, code = 28, name = "0.5.4").current()!!.installedCode)
    }

    @Test
    fun stable100ReceiptShowsInstalledVersionAndItsOwnNotes() {
        val confirmation = receipt(Store(31), code = 31, name = "1.0.0").current()!!
        assertEquals("1.0.0", confirmation.installedName)
        assertTrue(confirmation.changes.any { it.contains("Stable release") })
        assertTrue(confirmation.changes.any { it.contains("free") })
    }

    @Test
    fun revised109ReceiptIncludesLastChannelAlongsideRecoveryAndKeepsItsIdentity() {
        val store = Store(42)
        val updated = receipt(store, code = 44, name = "1.0.9")
        val shown = updated.current()!!
        assertEquals(44L, shown.installedCode)
        assertEquals(42L, shown.completedTargetCode)
        assertEquals(listOf(
            "Reconnect sooner after a fullscreen live TS stream ends.",
            "Keep later retry delays and existing playback, parental and connection-limit protections.",
            "Press Right during fullscreen live TV to return to the last channel watched.",
        ), shown.changes)
        assertNull(shown.credit)
        assertEquals(shown, receipt(store, code = 44, name = "1.0.9").current())
        assertTrue(receipt(Store(42), code = 44, name = "1.0.9-test").current()!!.changes.isEmpty())
        updated.acknowledge(shown)
        assertNull(updated.current())
    }

    @Test
    fun release109ShowsItsOwnRecoveryNotesAcrossDirectUpgradesWithoutACredit() {
        val notes = listOf(
            "Reconnect sooner after a fullscreen live TS stream ends.",
            "Keep later retry delays and existing playback, parental and connection-limit protections.",
        )
        for (priorTarget in listOf(27L, 39L, 41L)) {
            val store = Store(priorTarget)
            val updated = receipt(store, code = 42, name = "1.0.9")
            val shown = updated.current()!!
            assertEquals(42L, shown.installedCode)
            assertEquals("1.0.9", shown.installedName)
            assertEquals(priorTarget, shown.completedTargetCode)
            assertEquals(notes, shown.changes)
            assertNull(shown.credit)
            assertEquals(shown, receipt(store, code = 42, name = "1.0.9").current())
            assertTrue(receipt(Store(priorTarget), code = 42, name = "1.0.9-test").current()!!.changes.isEmpty())
            updated.acknowledge(shown)
            assertNull(updated.current())
        }
    }

    @Test
    fun maintenance108ReceiptKeepsItsOwnNotesUntilAcknowledged() {
        val store = Store(38)
        val updated = receipt(store, code = 39, name = "1.0.8")
        val confirmation = updated.current()!!
        assertEquals(39L, confirmation.installedCode)
        assertEquals("1.0.8", confirmation.installedName)
        assertEquals(38L, confirmation.completedTargetCode)
        assertEquals(listOf("Maintenance release based on 1.0.7."), confirmation.changes)
        assertNull(confirmation.credit)
        assertEquals(38L, store.pendingCode)
        assertEquals(confirmation, receipt(store, code = 39, name = "1.0.8").current())
        val futureRelease = receipt(Store(38), code = 40, name = "1.0.9").current()!!
        assertTrue(futureRelease.changes.isEmpty())
        assertNull(futureRelease.credit)
        updated.acknowledge(confirmation)
        assertEquals(0L, store.pendingCode)
        assertNull(receipt(store, code = 39, name = "1.0.8").current())
    }

    @Test
    fun directUpgradeShows105PlaylistNotesAndCreditUntilAcknowledged() {
        val store = Store(30)
        val updated = receipt(store, code = 36, name = "1.0.5")
        val confirmation = updated.current()!!
        assertEquals(36L, confirmation.installedCode)
        assertEquals("1.0.5", confirmation.installedName)
        assertEquals(listOf(
            "Manage playlists from Settings without another Settings PIN during the same protected visit.",
            "Use Delete playlist to choose a playlist and confirm its removal.",
            "Show or hide the playlist password; it starts masked and is hidden again when leaving.",
            "Keep parental playlist protection, the YourIPTV login shortcut and all playback features free.",
        ), confirmation.changes)
        assertEquals("Thanks to Dylan B. for the suggestions for the current build.", confirmation.credit)
        assertEquals(confirmation, receipt(store, code = 36, name = "1.0.5").current())
        val differentlyNamed = receipt(Store(36), code = 36, name = "1.0.5-test").current()!!
        assertTrue(differentlyNamed.changes.isEmpty())
        assertNull(differentlyNamed.credit)
        updated.acknowledge(confirmation)
        assertNull(receipt(store, code = 36, name = "1.0.5").current())
    }

    @Test
    fun directUpgradeShows104LoginNotesAndCreditUntilAcknowledged() {
        val store = Store(30)
        val updated = receipt(store, code = 35, name = "1.0.4")
        val confirmation = updated.current()!!
        assertEquals(35L, confirmation.installedCode)
        assertEquals("1.0.4", confirmation.installedName)
        assertEquals(listOf(
            "Add a YourIPTV playlist option after M3U and Xtream Codes.",
            "Sign in to YourIPTV with only your provider username and password.",
            "Use the preset YourIPTV server and playlist name while keeping normal Xtream editing.",
            "Keep all playback features free and preserve existing playlists, Settings and parental protections.",
        ), confirmation.changes)
        assertEquals("Thanks to Dylan B. for the suggestions for the current build.", confirmation.credit)
        assertEquals(confirmation, receipt(store, code = 35, name = "1.0.4").current())
        val differentlyNamed = receipt(Store(35), code = 35, name = "1.0.4-test").current()!!
        assertTrue(differentlyNamed.changes.isEmpty())
        assertNull(differentlyNamed.credit)
        updated.acknowledge(confirmation)
        assertNull(receipt(store, code = 35, name = "1.0.4").current())
    }

    @Test
    fun directUpgradeShows103PhoneNotesAndCreditUntilAcknowledged() {
        val store = Store(30)
        val updated = receipt(store, code = 34, name = "1.0.3")
        val confirmation = updated.current()!!
        assertEquals(34L, confirmation.installedCode)
        assertEquals("1.0.3", confirmation.installedName)
        assertEquals(listOf(
            "Fit complete movie and show posters and captions on short phone screens.",
            "Use a compact browsing header on short phone screens while retaining the full TV layout.",
            "Restore the selected series episode and focus after playback with touch or remote controls.",
            "Keep playback features free, subtitles off by default, and existing Settings and parental protections.",
        ), confirmation.changes)
        assertEquals("Thanks to Dylan B. for the suggestions for the current build.", confirmation.credit)
        assertEquals(confirmation, receipt(store, code = 34, name = "1.0.3").current())
        val differentlyNamed = receipt(Store(34), code = 34, name = "1.0.3-test").current()!!
        assertTrue(differentlyNamed.changes.isEmpty())
        assertNull(differentlyNamed.credit)
        updated.acknowledge(confirmation)
        assertNull(receipt(store, code = 34, name = "1.0.3").current())
    }

    @Test
    fun directUpgradeShows102NotesAndCreditUntilAcknowledged() {
        val store = Store(30)
        val updated = receipt(store, code = 33, name = "1.0.2")
        val confirmation = updated.current()!!
        assertEquals("1.0.2", confirmation.installedName)
        assertEquals("Thanks to Dylan B. for the suggestions for the current build.", confirmation.credit)
        assertTrue(confirmation.changes.any { it.contains("seasons up/down") })
        assertNull(receipt(Store(33), code = 33, name = "1.0.2-test").current()!!.credit)
        assertEquals(confirmation, receipt(store, code = 33, name = "1.0.2").current())
        updated.acknowledge(confirmation)
        assertNull(receipt(store, code = 33, name = "1.0.2").current())
    }

    @Test
    fun confirmationDescribesTheActualInstalledVersionAndBundledChanges() {
        val confirmation = receipt(Store(26)).current()!!
        assertEquals(27L, confirmation.installedCode)
        assertEquals("0.5.3", confirmation.installedName)
        assertEquals(26L, confirmation.completedTargetCode)
        assertTrue(confirmation.changes.any { it.contains("Hold Back") })
        assertTrue(confirmation.changes.any { it.contains("changelog") })
        val skippedRelease = receipt(Store(28), code = 28, name = "0.5.4").current()!!
        assertTrue(skippedRelease.changes.any { it.contains("Hold Back") })
        assertTrue(skippedRelease.changes.any { it.contains("changelog") })
        val mirroredRelease = receipt(Store(30), code = 30, name = "0.5.6").current()!!
        assertTrue(mirroredRelease.changes.any { it.contains("youriptv.us") })
        assertTrue(mirroredRelease.changes.any { it.contains("checksum") })
        // A future or differently named build must not borrow another release's notes.
        assertTrue(receipt(Store(31), code = 31, name = "0.5.7").current()!!.changes.isEmpty())
        assertTrue(receipt(Store(27), name = "0.5.3-test").current()!!.changes.isEmpty())
    }
}
