package com.youriptv.app.data

/** A saved local choice wins; new installs leave ordinary Settings open. */
object SettingsLockPolicy {
    const val LOCAL_KEY = "settings_lock_enabled"
    const val PLAY_INITIALIZED_KEY = "play_security_initialized"

    fun enabled(saved: Boolean?): Boolean = saved ?: false

    /** Preserve a prior Settings PIN/legacy Settings restriction when no local switch exists. */
    fun initial(saved: Boolean?, hasSettingsPin: Boolean, legacySettingsProtected: Boolean = false): Boolean =
        saved ?: (hasSettingsPin || legacySettingsProtected)
}
