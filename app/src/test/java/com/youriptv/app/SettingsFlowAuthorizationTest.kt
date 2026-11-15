package com.youriptv.app

import com.youriptv.app.ui.SettingsFlowAuthorization
import com.youriptv.app.ui.SettingsFlowAuthorization.Destination.*
import com.youriptv.app.ui.SettingsFlowAuthorization.Route
import com.youriptv.app.ui.SettingsVisitAuthorization
import org.junit.Assert.*
import org.junit.Test

class SettingsFlowAuthorizationTest {
    @Test fun lockDefaultsOnAndOnlyExplicitOffCanAuthorizeWithoutPin() {
        val flow = SettingsFlowAuthorization()
        val owner = Any()
        assertFalse(flow.authorizeWithoutPin(owner, settings))
        assertFalse(flow.isAuthorized(owner))
        assertTrue(flow.synchronizePolicy(false))
        assertTrue(flow.authorizeWithoutPin(owner, settings))
        assertTrue(flow.isAuthorized(owner))
        assertFalse("An unlocked Settings visit must not grant parental permission", flow.hasParentalPermission(owner))
    }

    @Test fun policyChangeRevokesVisitsParentalGrantsAndPendingHandoffs() {
        val flow = SettingsFlowAuthorization()
        val owner = Any(); val child = Any()
        flow.synchronizePolicy(false)
        assertTrue(flow.authorizeWithoutPin(owner, settings))
        flow.allowParental(owner)
        val visitToken = flow.token(owner)!!
        val policyToken = flow.policyToken()
        val ticket = flow.forward(owner, editor)!!
        flow.paused(owner)
        assertTrue(flow.synchronizePolicy(true))
        assertFalse(flow.isCurrentPolicy(policyToken))
        assertFalse(flow.isAuthorized(owner, visitToken))
        assertFalse(flow.retains(owner))
        assertFalse(flow.enter(child, editor, ticket))
        assertFalse(flow.hasParentalPermission(child))
        assertFalse(flow.authorizeWithoutPin(child, editor))
    }

    @Test fun unchangedPolicyKeepsCurrentVisitButOffOnOffCannotReviveItsTokens() {
        val flow = SettingsFlowAuthorization()
        val owner = Any()
        flow.synchronizePolicy(false)
        flow.authorizeWithoutPin(owner, settings)
        val policy = flow.policyToken(); val visit = flow.token(owner)!!
        assertFalse(flow.synchronizePolicy(false))
        assertTrue(flow.isAuthorized(owner, visit))
        flow.synchronizePolicy(true); flow.synchronizePolicy(false)
        assertFalse(flow.isCurrentPolicy(policy))
        assertFalse(flow.isAuthorized(owner, visit))
        assertTrue(flow.authorizeWithoutPin(owner, settings))
        assertFalse(flow.isAuthorized(owner, visit))
    }

    @Test fun changedPolicyInvalidatesRetainedFilePickerVisitBeforeItCanReturn() {
        val flow = SettingsFlowAuthorization()
        val owner = Any()
        flow.synchronizePolicy(false)
        flow.authorizeWithoutPin(owner, settings)
        val pickerPolicy = flow.policyToken()
        flow.paused(owner); flow.revoke()
        flow.synchronizePolicy(true)
        assertFalse("An original RAM picker is insufficient after a policy change", flow.isCurrentPolicy(pickerPolicy))
        assertFalse(flow.enter(owner, settings))
        assertFalse(flow.authorizeWithoutPin(owner, settings))
    }
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

    @Test fun protectedDialogCannotUseARetainedLocalTokenAfterFlowRevocationOrANewVisit() {
        val flow = SettingsFlowAuthorization()
        val owner = Any()
        val visit = SettingsVisitAuthorization()
        val captured = visit.beginVisit()
        assertTrue(visit.authorize(captured))
        fun canUseCapturedDialog() = visit.isAuthorized(captured) && flow.isAuthorized(owner)
        assertFalse("A local visit alone must not open protected preferences", canUseCapturedDialog())
        flow.authenticate(owner, settings)
        assertTrue(canUseCapturedDialog())
        assertFalse(flow.hasParentalPermission(owner))
        flow.revoke() // Screen-off can revoke the flow before the Activity's pause callback.
        assertTrue(visit.isAuthorized(captured))
        assertFalse("The old dialog must reject reads, saves and update callbacks", canUseCapturedDialog())
        visit.endVisit()
        assertTrue(visit.authorize(visit.beginVisit()))
        flow.authenticate(owner, settings)
        assertFalse("A new unlock cannot revive a captured dialog token", canUseCapturedDialog())
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
