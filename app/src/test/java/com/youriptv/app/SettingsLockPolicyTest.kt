package com.youriptv.app

import com.youriptv.app.data.SettingsLockPolicy
import org.junit.Assert.*
import org.junit.Test

class SettingsLockPolicyTest {
    @Test fun missingLocalChoiceKeepsExistingAndNewInstallationsProtected() {
        assertTrue(SettingsLockPolicy.enabled(null))
    }

    @Test fun explicitChoiceSurvivesReopeningWithoutBeingDerivedFromAnOldFlag() {
        val local = mutableMapOf<String, Boolean>("lock_settings" to false)
        fun locked() = SettingsLockPolicy.enabled(local[SettingsLockPolicy.LOCAL_KEY])
        assertTrue("The obsolete disabled flag must not unlock an upgraded installation", locked())
        local[SettingsLockPolicy.LOCAL_KEY] = false
        assertFalse(locked())
        local["lock_settings"] = true
        assertFalse("The old flag must not override an explicit new choice", locked())
        local[SettingsLockPolicy.LOCAL_KEY] = true
        assertTrue(locked())
        local.remove(SettingsLockPolicy.LOCAL_KEY)
        assertTrue("Removing local state must fail closed", locked())
    }
}
