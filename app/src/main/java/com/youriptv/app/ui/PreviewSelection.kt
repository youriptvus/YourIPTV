package com.youriptv.app.ui

data class LiveChannelKey(val playlistId: Long, val url: String)

object PreviewSelection {
    fun opensFullscreen(current: LiveChannelKey?, selected: LiveChannelKey, authorized: Boolean, hasPlayer: Boolean): Boolean =
        hasPlayer && authorized && current == selected

    fun isAuthorized(locked: Boolean, group: String, grantedGroup: String?, grantedStamp: Int, currentStamp: Int): Boolean =
        !locked || (grantedGroup == group && grantedStamp == currentStamp)
}
