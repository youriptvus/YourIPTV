package com.youriptv.app

import android.app.Application
import com.youriptv.app.data.MediaItem
import com.youriptv.app.data.Repository
import com.youriptv.app.data.Settings
import com.youriptv.app.ui.LivePlaybackSession

class YourIptvApp : Application() {
    lateinit var repo: Repository
        private set
    lateinit var settings: Settings
        private set
    lateinit var livePlayback: LivePlaybackSession
        private set

    override fun onCreate() {
        super.onCreate()
        instance = this
        repo = Repository(this)
        settings = Settings(this)
        livePlayback = LivePlaybackSession(this)
        com.youriptv.app.data.Http.fallbackUserAgent = { settings.globalUserAgent }
        com.youriptv.app.ui.Scheduler.install(this)
    }

    companion object {
        lateinit var instance: YourIptvApp
            private set

        /** Current zapping list handed from the browser to the player. */
        var playQueue: List<MediaItem> = emptyList()
    }
}
