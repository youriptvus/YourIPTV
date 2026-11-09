package com.youriptv.app

import com.youriptv.app.ui.SettingsVisitAuthorization
import org.junit.Assert.*
import org.junit.Test

class SettingsVisitAuthorizationTest {
    @Test fun aNewOrRecreatedActivityNeverStartsAuthorized() {
        assertFalse(SettingsVisitAuthorization().isAuthorized)
        val first = SettingsVisitAuthorization()
        assertTrue(first.authorize(first.beginVisit()))
        assertFalse(SettingsVisitAuthorization().isAuthorized)
    }

    @Test fun onlyTheCurrentPromptCanAuthorizeAVisit() {
        val visit = SettingsVisitAuthorization()
        val old = visit.beginVisit()
        val current = visit.beginVisit()
        assertFalse(visit.authorize(old))
        assertFalse(visit.isAuthorized)
        assertTrue(visit.authorize(current))
        assertTrue(visit.isAuthorized)
    }

    @Test fun leavingRevokesAccessAndLatePinCallbacksCannotRestoreIt() {
        val visit = SettingsVisitAuthorization()
        val token = visit.beginVisit()
        assertTrue(visit.authorize(token))
        visit.endVisit()
        assertFalse(visit.isAuthorized)
        assertFalse(visit.isCurrent(token))
        assertFalse(visit.authorize(token))
        assertTrue(visit.authorize(visit.beginVisit()))
    }

    @Test fun anAuthorizationTokenIsUnavailableBeforeAuthentication() {
        val visit = SettingsVisitAuthorization()
        assertNull(visit.authorizedToken())
        val pending = visit.beginVisit()
        assertNull(visit.authorizedToken())
        assertFalse(visit.isAuthorized(pending))
        assertTrue(visit.authorize(pending))
        assertEquals(pending, visit.authorizedToken())
        assertTrue(visit.isAuthorized(pending))
    }

    @Test fun reauthenticationDoesNotRevivePriorMasterOrConfirmationCallbacks() {
        val visit = SettingsVisitAuthorization()
        val old = visit.beginVisit()
        assertTrue(visit.authorize(old))
        val masterApprovedVisit = visit.authorizedToken()!!
        visit.endVisit()
        val new = visit.beginVisit()
        assertTrue(visit.authorize(new))
        assertTrue(visit.isAuthorized)
        assertFalse("Old master approval must not save into a later authenticated visit", visit.isAuthorized(masterApprovedVisit))
        assertFalse("A stale confirmation must not regain authority", visit.isAuthorized(old))
        assertTrue(visit.isAuthorized(new))
    }

    @Test fun restartingAuthenticationImmediatelyInvalidatesEarlierWork() {
        val visit = SettingsVisitAuthorization()
        val first = visit.beginVisit()
        assertTrue(visit.authorize(first))
        val second = visit.beginVisit()
        assertFalse(visit.isAuthorized(first))
        assertFalse(visit.isAuthorized(second))
        assertNull(visit.authorizedToken())
        assertTrue(visit.authorize(second))
        assertFalse(visit.isAuthorized(first))
        assertTrue(visit.isAuthorized(second))
    }
}
