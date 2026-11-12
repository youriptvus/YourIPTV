package com.youriptv.app

import com.youriptv.app.ui.SettingsFlowAuthorization
import com.youriptv.app.ui.SettingsFlowAuthorization.Destination.*
import com.youriptv.app.ui.SettingsFlowAuthorization.Route
import com.youriptv.app.ui.SettingsVisitAuthorization
import org.junit.Assert.*
import org.junit.Test

class SettingsFlowAuthorizationTest {
    private val settings = Route(SETTINGS)
    private val manager = Route(PLAYLISTS)
    private val editor = Route(EDITOR, 7L)

    @Test fun routesAndNewActivityInstancesNeverGrantSettingsAccess() {
        val flow = SettingsFlowAuthorization()
        val owner = Any()
        assertFalse(flow.enter(owner, settings))
        assertFalse(flow.enter(owner, editor, "invented"))
        assertNull(flow.forward(owner, editor))
        assertNull(flow.token(owner))
    }

    @Test fun completedPinGrantsOnlyItsCurrentOwner() {
        val flow = SettingsFlowAuthorization()
        val owner = Any()
        flow.authenticate(owner, settings)
        assertTrue(flow.isAuthorized(owner))
        assertFalse(flow.isAuthorized(Any()))
        assertFalse(flow.hasParentalPermission(owner))
    }

    @Test fun exactForwardTicketSurvivesOnlyThePreparedInternalPauseAndIsOneUse() {
        val flow = SettingsFlowAuthorization()
        val owner = Any(); val child = Any()
        flow.authenticate(owner, settings)
        val ticket = flow.forward(owner, editor)!!
        assertNull(flow.forward(owner, manager))
        flow.paused(owner)
        assertFalse(flow.isAuthorized(owner))
        assertTrue(flow.enter(child, editor, ticket))
        assertTrue(flow.isAuthorized(child))
        assertFalse(flow.enter(Any(), editor, ticket))
        assertFalse(flow.isAuthorized(owner))
    }

    @Test fun ticketCannotAuthorizeWrongDestinationOrDifferentPlaylist() {
        val flow = SettingsFlowAuthorization()
        val owner = Any()
        flow.authenticate(owner, settings)
        val ticket = flow.forward(owner, editor)!!
        flow.paused(owner)
        assertFalse(flow.enter(Any(), manager, ticket))
        assertFalse(flow.enter(Any(), Route(EDITOR, 8L), ticket))
        assertFalse(flow.enter(Any(), editor, "not-$ticket"))
    }

    @Test fun nestedManagerEditorAndReturnsKeepOnlyTheSameProtectedVisit() {
        val flow = SettingsFlowAuthorization()
        val owner = Any(); val list = Any(); val edit = Any()
        flow.authenticate(owner, settings)
        val toList = flow.forward(owner, manager)!!
        flow.paused(owner)
        assertTrue(flow.enter(list, manager, toList))
        val toEdit = flow.forward(list, editor)!!
        flow.paused(list)
        assertTrue(flow.enter(edit, editor, toEdit))
        flow.returning(edit); flow.paused(edit)
        assertFalse(flow.enter(owner, settings))
        assertTrue(flow.enter(list, manager))
        assertFalse(flow.enter(edit, editor, toEdit))
        flow.returning(list); flow.paused(list)
        assertTrue(flow.enter(owner, settings))
        flow.returning(owner)
        assertFalse(flow.retains(owner))
        assertFalse(flow.enter(owner, settings))
    }

    @Test fun homePauseAndScreenOffRevokeTheVisitAndAllPendingTickets() {
        val flow = SettingsFlowAuthorization()
        val owner = Any()
        flow.authenticate(owner, settings)
        flow.paused(owner)
        assertFalse(flow.enter(owner, settings))
        flow.authenticate(owner, settings)
        val ticket = flow.forward(owner, editor)!!
        flow.revoke()
        assertFalse(flow.enter(Any(), editor, ticket))
        assertFalse(flow.retains(owner))
    }

    @Test fun activityRecreationAndProcessRestartCannotRestoreGrants() {
        val flow = SettingsFlowAuthorization()
        val owner = Any()
        flow.authenticate(owner, settings)
        val ticket = flow.forward(owner, editor)!!
        flow.destroyed(owner)
        assertFalse(flow.enter(Any(), editor, ticket))
        assertFalse(SettingsFlowAuthorization().enter(Any(), editor, ticket))
    }

    @Test fun destroyingAHiddenParentRevokesItsDescendants() {
        val flow = SettingsFlowAuthorization()
        val owner = Any(); val child = Any()
        flow.authenticate(owner, settings)
        val ticket = flow.forward(owner, editor)!!
        flow.paused(owner); assertTrue(flow.enter(child, editor, ticket))
        flow.destroyed(owner)
        assertFalse(flow.isAuthorized(child))
        assertFalse(flow.retains(child))
    }

    @Test fun normalChildDestructionCanCompleteItsExactPreparedReturn() {
        val flow = SettingsFlowAuthorization()
        val owner = Any(); val child = Any()
        flow.authenticate(owner, settings)
        val ticket = flow.forward(owner, editor)!!
        flow.paused(owner); assertTrue(flow.enter(child, editor, ticket))
        flow.returning(child); flow.paused(child); flow.destroyed(child)
        assertTrue(flow.enter(owner, settings))
        assertFalse(flow.enter(Any(), settings))
    }

    @Test fun independentParentalApprovalIsReusedOnlyWithinItsSettingsVisit() {
        val flow = SettingsFlowAuthorization()
        val owner = Any(); val child = Any()
        flow.authenticate(owner, settings)
        assertFalse(flow.hasParentalPermission(owner))
        assertTrue(flow.allowParental(owner))
        val ticket = flow.forward(owner, editor)!!
        flow.paused(owner); assertTrue(flow.enter(child, editor, ticket))
        assertTrue(flow.hasParentalPermission(child))
        assertFalse(flow.allowParental(owner))
        flow.revoke(); flow.authenticate(child, editor)
        assertFalse(flow.hasParentalPermission(child))
    }

    @Test fun staleConfirmationTokenDoesNotBecomeValidAfterReauthentication() {
        val flow = SettingsFlowAuthorization()
        val owner = Any()
        flow.authenticate(owner, manager)
        val token = flow.token(owner)!!
        flow.revoke(); flow.authenticate(owner, manager)
        assertFalse(flow.isAuthorized(owner, token))
        assertTrue(flow.isAuthorized(owner, flow.token(owner)))
    }

    @Test fun failedLaunchCanCancelOnlyItsOwnPendingHandoff() {
        val flow = SettingsFlowAuthorization()
        val owner = Any()
        flow.authenticate(owner, settings)
        val ticket = flow.forward(owner, editor)!!
        flow.cancelForward(Any())
        assertNull(flow.forward(owner, manager))
        flow.cancelForward(owner)
        assertTrue(flow.isAuthorized(owner))
        flow.paused(owner)
        assertFalse(flow.enter(Any(), editor, ticket))
    }

    @Test fun startedPickerResultThenResumeKeepsTheOriginalLiveOperationToken() {
        val flow = SettingsFlowAuthorization()
        val owner = Any()
        val visit = SettingsVisitAuthorization()
        val pickerToken = visit.beginVisit()
        assertTrue(visit.authorize(pickerToken))
        flow.authenticate(owner, settings)
        flow.paused(owner); flow.revoke() // the system document picker backgrounds the app
        assertTrue(visit.isAuthorized(pickerToken)) // only the original RAM picker exception was retained
        flow.authenticate(owner, settings) // exact validated result returns at STARTED
        assertTrue(flow.enter(owner, settings)) // onResume must not begin a new local visit
        if (!visit.isAuthorized) visit.authorize(visit.beginVisit())
        assertTrue("Asynchronous export/import completion keeps the captured visit", visit.isAuthorized(pickerToken))
        visit.endVisit()
        assertFalse("A stale picker callback is rejected after leaving", visit.isAuthorized(pickerToken))
        val recreated = SettingsVisitAuthorization()
        assertFalse(recreated.isAuthorized(pickerToken))
    }
}
