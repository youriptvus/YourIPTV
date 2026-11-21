package com.youriptv.app

import com.youriptv.app.data.*
import com.youriptv.app.ui.*
import org.junit.Assert.*
import org.junit.Test

class StartupPlaybackPolicyTest {
    private val live = MediaItem(playlistId=7, kind=ItemKind.LIVE, name="Fixture", group="Fixture", url="http://fixture/live.ts")
    @Test fun launcherReentryDoesNotDependOnWhichScreenWasLeft() {
        assertTrue(StartupPlaybackPolicy.onNewIntent(true,true,false))
        assertFalse(StartupPlaybackPolicy.onNewIntent(false,true,false))
        assertFalse(StartupPlaybackPolicy.onNewIntent(true,true,true))
        assertFalse(StartupPlaybackPolicy.onNewIntent(true,false,false))
    }
    @Test fun processRestorationResumesButRotationDoesNot() {
        val process = MainLaunchTracker()
        assertTrue(StartupPlaybackPolicy.onCreate(true,process.claim(),true,false))
        assertFalse(StartupPlaybackPolicy.onCreate(true,process.claim(),true,false))
        assertTrue(StartupPlaybackPolicy.onCreate(true,false,false,false))
        assertTrue(StartupPlaybackPolicy.onCreate(true,false,true,true))
        assertFalse(StartupPlaybackPolicy.onCreate(false,true,false,true))
    }
    @Test fun explicitGuideDestinationSuppressesColdAndRestoredStartupPlayback() {
        assertFalse(StartupPlaybackPolicy.onCreate(true,true,false,false,guideRequest=true))
        assertFalse(StartupPlaybackPolicy.onCreate(true,true,true,false,guideRequest=true))
        assertFalse(StartupPlaybackPolicy.onCreate(true,false,true,true,guideRequest=true))
        assertFalse(StartupPlaybackPolicy.onCreate(true,false,false,true,guideRequest=true))
        assertTrue(StartupPlaybackPolicy.onCreate(true,true,true,false,guideRequest=false))
        assertTrue(StartupPlaybackPolicy.onCreate(true,false,true,true,guideRequest=false))
    }
    @Test fun rememberedChannelStaysInItsPlaylistAndNeverUsesCatchupOrVod() {
        assertTrue(StartupPlaybackPolicy.matches(live,live.url,7))
        assertTrue(StartupPlaybackPolicy.matches(live,live.url,0))
        assertFalse(StartupPlaybackPolicy.matches(live,live.url,8))
        assertFalse(StartupPlaybackPolicy.matches(live.copy(isCatchup=true),live.url,7))
        assertFalse(StartupPlaybackPolicy.matches(live.copy(kind=ItemKind.MOVIE),live.url,7))
        assertFalse(StartupPlaybackPolicy.matches(live,"http://fixture/other.ts",7))
    }
    @Test fun summaryShowsActualLastEpisodeAndSeparateResumeTarget() {
        val first=live.copy(kind=ItemKind.EPISODE,season=1,episode=1)
        val second=first.copy(url="http://fixture/second.ts",episode=2)
        val text=SeriesWatchSummary.label(listOf(first,second),mapOf(first.url to WatchProgress(100,100,true,50)),second)
        assertTrue(text.contains("Last watched: S1 E1"));assertTrue(text.contains("Continue: S1 E2"))
    }
}
