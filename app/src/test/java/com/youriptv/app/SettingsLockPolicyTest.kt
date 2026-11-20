package com.youriptv.app

import com.youriptv.app.data.SettingsLockPolicy
import org.junit.Assert.*
import org.junit.Test

class SettingsLockPolicyTest {
    @Test fun freshAndUnprotectedExistingInstallsDefaultOff() {
        assertFalse(SettingsLockPolicy.enabled(null))
        assertFalse(SettingsLockPolicy.initial(null, hasSettingsPin = false))
    }
    @Test fun previouslySetSettingsPinRemainsProtected() {
        assertTrue(SettingsLockPolicy.initial(null, hasSettingsPin = true))
    }
    @Test fun legacySettingsRestrictionRemainsProtected() {
        assertTrue(SettingsLockPolicy.initial(null, false, legacySettingsProtected = true))
    }
    @Test fun explicitOnAndOffAlwaysWinOverPinPresence() {
        for (pin in listOf(false, true)) for (legacy in listOf(false, true)) {
            assertTrue(SettingsLockPolicy.initial(true, pin, legacy))
            assertFalse(SettingsLockPolicy.initial(false, pin, legacy))
        }
    }
    @Test fun aPersistedDefaultOrExplicitChoiceSurvivesRestart() {
        val local = mutableMapOf(SettingsLockPolicy.LOCAL_KEY to SettingsLockPolicy.initial(null, false))
        fun reopened() = SettingsLockPolicy.enabled(local[SettingsLockPolicy.LOCAL_KEY])
        assertFalse(reopened())
        local["lock_settings"] = true
        assertFalse(reopened())
        local[SettingsLockPolicy.LOCAL_KEY] = true
        assertTrue(reopened())
        local[SettingsLockPolicy.LOCAL_KEY] = false
        assertFalse(reopened())
    }
}
