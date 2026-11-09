package com.youriptv.app.ui

/** In-memory permission for one foreground editor visit; never persisted or accepted from an Intent. */
internal class PlaylistEditAuthorization(private val existingPlaylist: Boolean) {
    private var generation = 0L
    private var active = false
    private var parentalPermission = false
    private var settingsPermission = false

    val isAuthorized: Boolean
        get() = active && parentalPermission && (!existingPlaylist || settingsPermission)

    fun beginVisit(): Long {
        endVisit()
        active = true
        return generation
    }

    fun isCurrent(visit: Long): Boolean = active && visit == generation

    fun allowParental(visit: Long): Boolean {
        if (!isCurrent(visit)) return false
        parentalPermission = true
        return true
    }

    fun allowSettings(visit: Long): Boolean {
        if (!isCurrent(visit) || !parentalPermission) return false
        settingsPermission = true
        return true
    }

    fun endVisit() {
        generation++
        active = false
        parentalPermission = false
        settingsPermission = false
    }
}
