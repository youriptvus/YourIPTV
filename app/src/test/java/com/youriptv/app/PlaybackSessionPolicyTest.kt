package com.youriptv.app

import com.youriptv.app.data.PlaybackSessionPolicy
import org.junit.Assert.*
import org.junit.Test

class PlaybackSessionPolicyTest {
    @Test fun archiveCanContinueOnlyUnderItsOriginalStrictSourceIdentity() {
        assertTrue(PlaybackSessionPolicy.sourceUnchanged("original", "original"))
        for (changed in listOf("new-account", "changed-password", "changed-server", "", null))
            assertFalse(PlaybackSessionPolicy.sourceUnchanged("original", changed))
    }

    @Test fun missingArchiveProvenanceNeverAuthorizesPlayback() {
        assertFalse(PlaybackSessionPolicy.sourceUnchanged("", "current"))
        assertFalse(PlaybackSessionPolicy.sourceUnchanged("", ""))
        assertFalse(PlaybackSessionPolicy.sourceUnchanged(" ", " "))
    }

    @Test fun finiteWindowRecoveryCannotRepeatEvenAfterThePlayerBecomesReady() {
        var attempts = 0
        var recoveries = 0
        repeat(20) {
            if (PlaybackSessionPolicy.canRecoverFiniteWindow(attempts)) {
                attempts++
                recoveries++
            }
            // READY resets the ordinary connection retry counter, never this per-item budget.
        }
        assertEquals(1, recoveries)
        assertFalse(PlaybackSessionPolicy.canRecoverFiniteWindow(-1))
        assertTrue(PlaybackSessionPolicy.canRecoverFiniteWindow(0)) // a new item/manual Retry gets a fresh budget
    }
}
