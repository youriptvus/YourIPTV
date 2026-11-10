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
