package com.youriptv.app.ui

import android.content.SharedPreferences
import android.os.Handler
import android.os.Looper
import androidx.annotation.OptIn
import androidx.appcompat.app.AppCompatActivity
import androidx.lifecycle.Lifecycle
import androidx.media3.common.PlaybackException
import androidx.media3.common.Player
import androidx.media3.common.util.UnstableApi
import androidx.media3.exoplayer.ExoPlayer
import androidx.media3.ui.PlayerView
import com.youriptv.app.YourIptvApp
import com.youriptv.app.data.ConnectionLimit
import com.youriptv.app.data.ItemKind
import com.youriptv.app.data.MediaItem
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.launch

private const val SLOW_PREVIEW_MESSAGE =
    "Preview is still loading. Choose another channel or select again for full screen."

/**
 * One live player is transferred between the TV list, guide, and full-screen player.
 * The player is released when none of those screens remains started.
 */
@OptIn(UnstableApi::class)
class LivePlaybackSession(private val app: YourIptvApp) {
    private val handler = Handler(Looper.getMainLooper())
    private val io = CoroutineScope(SupervisorJob() + Dispatchers.IO)
    private val owners = HashSet<Any>()
    private val observers = LinkedHashSet<() -> Unit>()
    private var target: PlayerView? = null
    private var fullScreen = false
    private var playerPlaylistId: Long? = null
    private var authorizationStamp = app.settings.restrictionStamp()
    private var authorizedGroup: String? = null
    private var generation = 0
    private var retries = 0
    private var pendingRetry: Runnable? = null
    private val loadingHint = PreviewLoadingHint(
        postDelayed = { task, delay -> handler.postDelayed(task, delay) },
        remove = { task -> handler.removeCallbacks(task) },
    ) {
        if (!fullScreen && current != null && player?.playbackState == Player.STATE_BUFFERING) {
            status = SLOW_PREVIEW_MESSAGE
            notifyObservers()
        }
    }

    var player: ExoPlayer? = null
        private set
    var audioDelay = AudioDelayProcessor()
        private set
    var userAgent = ""
        private set
    var current: MediaItem? = null
        private set
    var queue: List<MediaItem> = emptyList()
        private set
    var status = "Select a channel to preview."
        private set

    private val playerListener = object : Player.Listener {
        override fun onPlaybackStateChanged(state: Int) {
            if (fullScreen || current == null) return
            status = when (state) {
                Player.STATE_BUFFERING -> loadingPreviewStatus()
                Player.STATE_READY -> { loadingHint.reset(); retries = 0; cancelRetry(); "" }
                Player.STATE_ENDED -> { loadingHint.reset(); retryPreview("Preview ended"); return }
                else -> { loadingHint.reset(); status }
            }
            notifyObservers()
        }

        override fun onPlayerError(error: PlaybackException) {
            if (fullScreen || current == null) return
            retryPreview("Preview unavailable (${error.errorCodeName})")
        }
    }

    private fun loadingPreviewStatus(): String {
        loadingHint.start()
        return if (loadingHint.isSlow) SLOW_PREVIEW_MESSAGE else "Loading preview..."
    }

    private fun cancelRetry() {
        pendingRetry?.let { handler.removeCallbacks(it) }
        pendingRetry = null
    }

    private fun retryPreview(reason: String) {
        loadingHint.reset()
        val p = player ?: return
        if (pendingRetry != null) return
        if (retries >= 2) {
            status = "$reason. Choose another channel or open full screen to retry."
            notifyObservers()
            return
        }
        retries++
        status = "$reason. Reconnecting ($retries/2)..."
        val expected = generation
        val retry = Runnable {
            pendingRetry = null
            if (generation == expected && player === p && !fullScreen && current != null) {
                if (p.playbackState == Player.STATE_ENDED) p.seekToDefaultPosition()
                p.prepare()
                p.playWhenReady = true
            }
        }
        pendingRetry = retry
        handler.postDelayed(retry, 1500L * retries)
        notifyObservers()
    }

    private val restrictionListener = SharedPreferences.OnSharedPreferenceChangeListener { _, _ ->
        handler.post {
            val stamp = app.settings.restrictionStamp()
            if (stamp != authorizationStamp) {
                authorizationStamp = stamp
                authorizedGroup = null
                val item = current
                if (item != null && app.settings.isGroupLocked(item.group)) {
                    generation++
                    loadingHint.reset()
                    player?.stop()
                    player?.clearMediaItems()
                    current = null
                    queue = emptyList()
                    status = "This channel is locked. Select it to enter the parental PIN."
                }
                notifyObservers()
            }
        }
    }

    init {
        app.settings.addListener(restrictionListener)
    }

    fun ownerStarted(owner: Any) {
        owners.add(owner)
    }

    fun ownerStopped(owner: Any) {
        owners.remove(owner)
        if (owners.isEmpty()) stop()
    }

    fun observe(observer: () -> Unit) {
        observers.add(observer)
    }

    fun removeObserver(observer: () -> Unit) {
        observers.remove(observer)
    }

    fun attachPreview(view: PlayerView) = attach(view, false)

    fun attachFullscreen(view: PlayerView) = attach(view, true)

    private fun attach(view: PlayerView, isFullscreen: Boolean) {
        fullScreen = isFullscreen
        if (isFullscreen) {
            cancelRetry()
            loadingHint.reset()
        }
        val old = target
        target = view
        val p = player
        if (p != null && old !== view) PlayerView.switchTargetView(p, old, view)
        else if (p == null) view.player = null
        if (!isFullscreen && p != null && current != null) {
            status = when {
                p.playerError != null -> "Preview unavailable. Select the channel again to open the player."
                p.playbackState == Player.STATE_READY -> ""
                p.playbackState == Player.STATE_BUFFERING -> loadingPreviewStatus()
                else -> status
            }
        }
        notifyObservers()
    }

    fun detach(view: PlayerView) {
        if (target !== view) return
        view.player = null
        target = null
        fullScreen = false
    }

    fun owns(p: ExoPlayer) = player === p

    fun obtain(item: MediaItem): ExoPlayer {
        require(item.kind == ItemKind.LIVE && !item.isCatchup)
        val existing = player
        if (existing != null && playerPlaylistId == item.playlistId) return existing
        stop()
        audioDelay = AudioDelayProcessor()
        val created = PlaybackFactory.create(app, item, audioDelay)
        player = created.player
        userAgent = created.userAgent
        playerPlaylistId = item.playlistId
        created.player.addListener(playerListener)
        target?.player = created.player
        return created.player
    }

    fun isCurrentAuthorized(item: MediaItem): Boolean {
        val playing = current ?: return false
        return playing.playlistId == item.playlistId && playing.url == item.url &&
            player?.currentMediaItem?.mediaId == item.url && isAuthorized(item)
    }

    fun unlockedGroup(item: MediaItem): String? =
        item.group.takeIf { app.settings.isGroupLocked(it) && isAuthorized(item) }

    private fun isAuthorized(item: MediaItem): Boolean = PreviewSelection.isAuthorized(
        app.settings.isGroupLocked(item.group), item.group, authorizedGroup, authorizationStamp, app.settings.restrictionStamp(),
    )

    fun select(activity: AppCompatActivity, item: MediaItem, items: List<MediaItem>, openFullscreen: () -> Unit) {
        require(item.kind == ItemKind.LIVE && !item.isCatchup)
        if (Scheduler.recordingUsesOnlyConnection(item.playlistId)) {
            Dialogs.toast(activity, "This provider's only connection is being used by a recording.")
            return
        }
        val currentKey = current?.let { LiveChannelKey(it.playlistId, it.url) }
        val selectedKey = LiveChannelKey(item.playlistId, item.url)
        if (PreviewSelection.opensFullscreen(currentKey, selectedKey, isCurrentAuthorized(item), player != null)) {
            queue = liveQueue(item, items)
            openFullscreen()
            return
        }
        val start = {
            if (activity.lifecycle.currentState.isAtLeast(Lifecycle.State.STARTED)) {
                tune(item, items, if (app.settings.isGroupLocked(item.group)) item.group else null)
            }
        }
        if (app.settings.isGroupLocked(item.group)) Dialogs.requirePin(activity, "\"${item.group}\" is locked") { start() }
        else start()
    }

    private fun tune(item: MediaItem, items: List<MediaItem>, unlockedGroup: String?) {
        val p = obtain(item)
        generation++
        loadingHint.reset()
        cancelRetry()
        retries = 0
        current = item
        queue = liveQueue(item, items)
        authorizationStamp = app.settings.restrictionStamp()
        authorizedGroup = unlockedGroup
        status = "Loading preview..."
        p.stop()
        p.clearMediaItems()
        p.setMediaItem(PlaybackFactory.mediaItem(item))
        p.prepare()
        p.playWhenReady = true
        app.settings.lastChannelUrl = item.url
        io.launch { app.repo.recordHistory(item) }
        notifyObservers()
    }

    private fun liveQueue(item: MediaItem, items: List<MediaItem>): List<MediaItem> {
        val live = items.filter { it.kind == ItemKind.LIVE && !it.isCatchup }
        return if (live.any { it.playlistId == item.playlistId && it.url == item.url }) live else listOf(item)
    }

    fun noteFullscreenItem(item: MediaItem, items: List<MediaItem>, unlockedGroups: Set<String>, stamp: Int) {
        current = item
        queue = items
        authorizationStamp = stamp
        authorizedGroup = item.group.takeIf {
            app.settings.isGroupLocked(it) && stamp == app.settings.restrictionStamp() && it in unlockedGroups
        }
        notifyObservers()
    }

    fun stopForRecording(playlistId: Long): Boolean {
        val item = current ?: return false
        val playingPlaylist = app.repo.playlist(item.playlistId) ?: return false
        val recordingPlaylist = app.repo.playlist(playlistId) ?: return false
        if (!ConnectionLimit.sameAccount(playingPlaylist, recordingPlaylist)) return false
        stop()
        status = "Preview stopped while recording uses this provider's only connection."
        notifyObservers()
        return true
    }

    fun stop() {
        generation++
        loadingHint.reset()
        cancelRetry()
        target?.player = null
        player?.removeListener(playerListener)
        player?.release()
        player = null
        playerPlaylistId = null
        current = null
        queue = emptyList()
        authorizedGroup = null
        authorizationStamp = app.settings.restrictionStamp()
        status = "Select a channel to preview."
        retries = 0
        notifyObservers()
    }

    private fun notifyObservers() {
        observers.toList().forEach { it() }
    }
}
