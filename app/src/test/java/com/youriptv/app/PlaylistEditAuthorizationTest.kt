package com.youriptv.app

import com.youriptv.app.ui.PlaylistEditAuthorization
import org.junit.Assert.*
import org.junit.Test

class PlaylistEditAuthorizationTest {
    @Test fun existingConnectionNeedsBothParentalPermissionAndSettingsAuthentication() {
        val auth = PlaylistEditAuthorization(existingPlaylist = true)
        val visit = auth.beginVisit()
        assertFalse(auth.isAuthorized)
        assertFalse(auth.allowSettings(visit))
        assertTrue(auth.allowParental(visit))
        assertFalse(auth.isAuthorized)
        assertTrue(auth.allowSettings(visit))
        assertTrue(auth.isAuthorized)
    }

    @Test fun addingPlaylistRemainsAvailableAfterItsParentalPermission() {
        val auth = PlaylistEditAuthorization(existingPlaylist = false)
        val visit = auth.beginVisit()
        assertFalse(auth.isAuthorized)
        assertTrue(auth.allowParental(visit))
        assertTrue(auth.isAuthorized)
    }

    @Test fun leavingEditorRevokesReadingAndSavingAndRejectsLatePinCallbacks() {
        val auth = PlaylistEditAuthorization(existingPlaylist = true)
        val visit = auth.beginVisit()
        auth.allowParental(visit)
        auth.allowSettings(visit)
        assertTrue(auth.isAuthorized)
        auth.endVisit()
        assertFalse(auth.isAuthorized)
        assertFalse(auth.allowParental(visit))
        assertFalse(auth.allowSettings(visit))
    }

    @Test fun resumedVisitCannotUseAnEarlierVisitsSuccessfulPrompt() {
        val auth = PlaylistEditAuthorization(existingPlaylist = true)
        val earlier = auth.beginVisit()
        auth.allowParental(earlier)
        val resumed = auth.beginVisit()
        assertFalse(auth.allowSettings(earlier))
        assertFalse(auth.allowParental(earlier))
        assertTrue(auth.allowParental(resumed))
        assertFalse(auth.isAuthorized)
        assertTrue(auth.allowSettings(resumed))
        assertTrue(auth.isAuthorized)
    }

    @Test fun recreatedEditorDoesNotInheritAFormerInstancesPermission() {
        val original = PlaylistEditAuthorization(existingPlaylist = true)
        val visit = original.beginVisit()
        original.allowParental(visit)
        original.allowSettings(visit)
        val recreated = PlaylistEditAuthorization(existingPlaylist = true)
        recreated.beginVisit()
        assertFalse(recreated.isAuthorized)
    }
}
