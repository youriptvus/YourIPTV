package com.youriptv.app.data

/** An explicit local choice; neither a custom PIN nor the obsolete shared-lock flag controls it. */
object SettingsLockPolicy {
    const val LOCAL_KEY = "settings_lock_enabled"

    fun enabled(saved: Boolean?): Boolean = saved ?: true
}
