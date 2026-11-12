package com.youriptv.app.ui

import com.youriptv.app.data.Playlist
import com.youriptv.app.data.SourceType

/** An adding-only shortcut. The stored connection remains an ordinary Xtream playlist. */
internal object YourIptvLogin {
    const val SERVER_URL = "http://cw-25.com"
    const val PLAYLIST_NAME = "YourIPTV"

    /** Accepts only credentials so hidden fields from another setup choice cannot affect this connection. */
    fun newPlaylist(username: String, password: String): Playlist? {
        val user = username.trim()
        if (user.isEmpty() || password.isEmpty()) return null
        return Playlist(
            name = PLAYLIST_NAME,
            type = SourceType.XTREAM,
            url = SERVER_URL,
            username = user,
            password = password,
        )
    }
}
