package com.youriptv.app

import android.app.Application
import android.app.Activity
import android.os.Bundle
import com.youriptv.app.data.MediaItem
import com.youriptv.app.data.Repository
import com.youriptv.app.data.Settings
import com.youriptv.app.refresh.RefreshCoordinator
import com.youriptv.app.ui.LivePlaybackSession
import com.youriptv.app.ui.SettingsFlow
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.flow.collect
import kotlinx.coroutines.launch

class YourIptvApp : Application() {
    internal var signingStatus = SigningStatus.UNAVAILABLE
        private set
    internal var brandStatus = BrandIntegrity.Result.OK
        private set
    internal val isTrustedBuild get() = signingStatus == SigningStatus.TRUSTED && brandStatus == BrandIntegrity.Result.OK
    lateinit var repo: Repository
        private set
    lateinit var settings: Settings
        private set
    lateinit var livePlayback: LivePlaybackSession
        private set
    lateinit var refreshCoordinator: RefreshCoordinator
        private set
    internal lateinit var settingsFlow: SettingsFlow
        private set
    private val mainLaunch = com.youriptv.app.ui.MainLaunchTracker()
    internal fun claimFirstMainLaunch() = mainLaunch.claim()
    private val refreshObservers = CoroutineScope(SupervisorJob() + Dispatchers.Main.immediate)

    override fun onCreate() {
        super.onCreate()
        instance = this
        signingStatus = OfficialSigning.check(this)
        brandStatus = BrandIntegrity.verify(this)
        BrandIntegrity.securitySignals(this)
        if (!isTrustedBuild) return
        repo = Repository(this)
        settings = Settings(this)
        livePlayback = LivePlaybackSession(this)
        settingsFlow = SettingsFlow(this)
        com.youriptv.app.data.Http.fallbackUserAgent = { settings.globalUserAgent }
        refreshCoordinator = RefreshCoordinator(this, repo, settings)
        refreshObservers.launch {
            val seenCatalogs = HashMap<Long, Long>()
            refreshCoordinator.generations.collect { generations ->
                generations.forEach { (id, generation) ->
                    if (seenCatalogs.put(id, generation.catalog) != generation.catalog)
                        livePlayback.onCatalogCommitted(id)
                }
            }
        }
        com.youriptv.app.ui.Scheduler.install(this)
        registerActivityLifecycleCallbacks(com.youriptv.app.updates.AppUpdateChecks(this))
        // A restored Player/Settings screen can be the first foreground screen of a new
        // process. Background JobService-only startup must never trigger this hook.
        registerActivityLifecycleCallbacks(object : ActivityLifecycleCallbacks {
            override fun onActivityResumed(activity: Activity) = refreshCoordinator.onFirstForegroundLaunch()
            override fun onActivityCreated(activity: Activity, state: Bundle?) = Unit
            override fun onActivityStarted(activity: Activity) = Unit
            override fun onActivityPaused(activity: Activity) = Unit
            override fun onActivityStopped(activity: Activity) = Unit
            override fun onActivitySaveInstanceState(activity: Activity, state: Bundle) = Unit
            override fun onActivityDestroyed(activity: Activity) = Unit
        })
    }

    companion object {
        lateinit var instance: YourIptvApp
            private set

        /** Current zapping list handed from the browser to the player. */
        var playQueue: List<MediaItem> = emptyList()
    }
}
