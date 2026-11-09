package com.youriptv.app.ui

/** In-memory authorization for a single Settings visit; never restored from an Intent or saved state. */
internal class SettingsVisitAuthorization {
    private var generation = 0L
    private var active = false
    var isAuthorized = false
        private set

    fun beginVisit(): Long {
        generation++
        active = true
        isAuthorized = false
        return generation
    }

    fun isCurrent(token: Long) = active && generation == token

    /** Capture only a completed authorization, so deferred work cannot inherit a later visit's access. */
    fun authorizedToken(): Long? = generation.takeIf { active && isAuthorized }

    fun isAuthorized(token: Long) = isAuthorized && isCurrent(token)

    fun authorize(token: Long): Boolean {
        if (!isCurrent(token)) return false
        isAuthorized = true
        return true
    }

    fun endVisit() {
        generation++
        active = false
        isAuthorized = false
    }
}
