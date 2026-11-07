package com.youriptv.app.ui

import android.app.PictureInPictureParams
import android.content.Intent
import android.content.pm.PackageManager
import android.content.res.Configuration
import android.os.Build
import android.os.Bundle
import android.os.Handler
import android.os.Looper
import android.util.Rational
import android.view.KeyEvent
import android.view.View
import android.view.WindowManager
import android.widget.ImageView
import android.widget.LinearLayout
import android.widget.ProgressBar
import android.widget.TextView
import androidx.annotation.OptIn
import androidx.appcompat.app.AlertDialog
import androidx.core.view.WindowCompat
import androidx.core.view.WindowInsetsCompat
import androidx.core.view.WindowInsetsControllerCompat
import androidx.lifecycle.lifecycleScope
import androidx.media3.common.C
import androidx.media3.common.ForwardingPlayer
import androidx.media3.common.Format as ExoFormat
import androidx.media3.common.PlaybackException
import androidx.media3.common.Player
import androidx.media3.common.TrackSelectionOverride
import androidx.media3.common.Tracks
import androidx.media3.common.util.UnstableApi
import androidx.media3.datasource.HttpDataSource
import androidx.media3.exoplayer.ExoPlayer
import androidx.media3.exoplayer.video.VideoFrameMetadataListener
import androidx.media3.ui.AspectRatioFrameLayout
import androidx.media3.ui.DefaultTimeBar
import androidx.media3.ui.PlayerView
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.RecyclerView
import com.youriptv.app.R
import com.youriptv.app.YourIptvApp
import com.youriptv.app.data.Http
import com.youriptv.app.data.Catchup
import com.youriptv.app.data.StreamDiagnosis
import com.youriptv.app.data.ItemKind
import com.youriptv.app.data.MediaItem
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import kotlin.math.abs
import java.util.Locale

@OptIn(UnstableApi::class)
class PlayerActivity : RemoteActivity() {

    private val app get() = YourIptvApp.instance
    private var player: ExoPlayer? = null
    private var sharedPlayer = false
    private var audioDelay = AudioDelayProcessor()
    private var queue: List<MediaItem> = emptyList()
    private var index = 0
    private var previousIndex = -1
    private var retries = 0
    private var probeRetried = false
    private var errorShown = false
    private var playingUrl = ""
    private var playerUa = Http.DEFAULT_USER_AGENT
    private val unlockedGroups = HashSet<String>()
    private val handler = Handler(Looper.getMainLooper())

    private lateinit var playerView: PlayerView
    private lateinit var message: TextView
    private lateinit var numberEntry: TextView
    private lateinit var banner: View
    private lateinit var overlay: LinearLayout
    private lateinit var overlayList: RecyclerView
    private lateinit var overlayAdapter: ItemAdapter

    private var okDownAt = 0L
    private var longPressFired = false
    private var hasStartedOnce = false
    private val digits = StringBuilder()

    private val current: MediaItem? get() = queue.getOrNull(index)
    private val isLive get() = current?.kind == ItemKind.LIVE

    private val hideBanner = Runnable { banner.visibility = View.GONE }
    private val recoveryObserver: () -> Unit = {
        val text = app.livePlayback.recoveryMessage
        if (text != null) showMessage(text, error = app.livePlayback.recoveryFailed)
        else if (player?.isPlaying == true && message.visibility == View.VISIBLE) {
            errorShown = false
            message.visibility = View.GONE
        }
    }
    private val commitNumber = Runnable { commitDigits() }
    private val savePositionTick = object : Runnable {
        override fun run() { savePosition(); handler.postDelayed(this, 15_000) }
    }
    private val playbackListener = object : Player.Listener {
        override fun onPlaybackStateChanged(state: Int) {
            when (state) {
                Player.STATE_READY -> { retries = 0; if (!errorShown) message.visibility = View.GONE }
                Player.STATE_ENDED -> onEnded()
                else -> Unit
            }
        }
        override fun onPlayerError(error: PlaybackException) = handleError(error)
        override fun onTracksChanged(tracks: Tracks) { updateBadges(); applyFrameRate() }
        override fun onRenderedFirstFrame() { applyFrameRate() }
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_player)
        window.addFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON)
        WindowCompat.getInsetsController(window, window.decorView).apply {
            hide(WindowInsetsCompat.Type.systemBars())
            systemBarsBehavior = WindowInsetsControllerCompat.BEHAVIOR_SHOW_TRANSIENT_BARS_BY_SWIPE
        }
        playerView = findViewById(R.id.player_view)
        message = findViewById(R.id.message)
        numberEntry = findViewById(R.id.number_entry)
        banner = findViewById(R.id.banner)
        overlay = findViewById(R.id.channel_overlay)
        overlayList = findViewById(R.id.overlay_list)
        overlayAdapter = ItemAdapter(
            { _, pos -> hideOverlay(); if (pos != index) switchTo(pos) },
            { item, _ -> toggleFavorite(item) },
        )
        overlayList.layoutManager = LinearLayoutManager(this)
        overlayList.adapter = overlayAdapter
        playerView.resizeMode = resizeModeFor(app.settings.aspectMode)
        findViewById<View>(R.id.root).setOnClickListener { if (isLive) toggleBanner() }
        findViewById<View>(R.id.root).setOnLongClickListener { showOptions(); true }
        app.settings.addListener(restrictionListener)
        // A player left in picture-in-picture lives in its own task, so a new launch creates a second instance.
        // Retire it first: two players would exceed single-connection providers and play two audio streams.
        active?.get()?.takeIf { it !== this }?.retire()
        active = java.lang.ref.WeakReference(this)
        loadIntent(intent)
    }

    override fun onDestroy() {
        app.settings.removeListener(restrictionListener)
        if (active?.get() === this) active = null
        super.onDestroy()
    }

    private fun retire() {
        handler.removeCallbacksAndMessages(null)
        savePosition()
        disposePlayer()
        finish()
    }

    override fun onNewIntent(intent: Intent) {
        super.onNewIntent(intent)
        if (intent.getBooleanExtra(EXTRA_RETURN_TO_STREAM, false)) {
            // Returning to an existing player must not reload the global queue or reopen its media source.
            if (isLive && sharedPlayer) adoptLiveSession()
            showCurrentStreamFullscreen()
            return
        }
        setIntent(intent)
        savePosition()
        loadIntent(intent)
        val p = player
        if (p != null) {
            val shared = current?.let { it.kind == ItemKind.LIVE && !it.isCatchup } == true
            if (app.livePlayback.owns(p) != shared) {
                disposePlayer()
                if (shared) attachLivePlayer() else { app.livePlayback.stop(); initPlayer() }
            }
            refreshAuthorization()
            startItem()
        }
    }

    private fun loadIntent(i: Intent) {
        queue = YourIptvApp.playQueue
        if (queue.isEmpty()) { finish(); return }
        index = i.getIntExtra(EXTRA_INDEX, 0).coerceIn(0, queue.size - 1)
        previousIndex = -1
        unlockedGroups.clear()
        authorizedStamp = app.settings.restrictionStamp()
        if (i.hasExtra(EXTRA_AUTHORIZATION_STAMP) &&
            i.getIntExtra(EXTRA_AUTHORIZATION_STAMP, 0) == authorizedStamp) {
            i.getStringExtra(EXTRA_UNLOCKED_GROUP)?.let { unlockedGroups.add(it) }
        }
    }

    // ---------------- Parental control ----------------

    private var authorizedStamp = 0
    private var pinPromptShown = false

    private val restrictionListener = android.content.SharedPreferences.OnSharedPreferenceChangeListener { _, _ ->
        handler.post { enforceRestrictions() }
    }

    /**
     * Re-checks the playing item after any restriction change (a new or removed PIN, locked groups, adult block),
     * e.g. while it keeps playing in picture-in-picture: earlier unlocks are dropped and a now-locked item stops at once.
     */
    private fun enforceRestrictions() {
        if (!refreshAuthorization()) return
        if (!lifecycle.currentState.isAtLeast(androidx.lifecycle.Lifecycle.State.STARTED)) return
        val item = current ?: return
        if (!app.settings.isGroupLocked(item.group)) return
        stopForLock(item)
        val inPip = Build.VERSION.SDK_INT >= Build.VERSION_CODES.N && isInPictureInPictureMode
        if (inPip) finish() else startItem()
    }

    /** Drops every earlier unlock once a restriction has changed; returns true if it had. */
    private fun refreshAuthorization(): Boolean {
        val stamp = app.settings.restrictionStamp()
        if (stamp == authorizedStamp) return false
        authorizedStamp = stamp
        unlockedGroups.clear()
        return true
    }

    private fun stopForLock(item: MediaItem) {
        player?.stop()
        playingUrl = ""
        message.text = "\"${item.group}\" is locked by parental control."
        message.visibility = View.VISIBLE
    }

    /** Returns true when [item] may play now; otherwise stops playback and asks for the parental PIN. */
    private fun allowedOrPrompt(item: MediaItem): Boolean {
        if (!app.settings.isGroupLocked(item.group) || item.group in unlockedGroups) return true
        stopForLock(item)
        if (pinPromptShown) return false
        pinPromptShown = true
        Dialogs.requirePin(this, "\"${item.group}\" is locked", onCancel = {
            pinPromptShown = false
            // Nothing locked keeps playing: go back to the previous allowed channel, or leave the player.
            val prev = queue.getOrNull(previousIndex)
            if (current == item && prev != null && prev != item &&
                (!app.settings.isGroupLocked(prev.group) || prev.group in unlockedGroups)) { index = previousIndex; previousIndex = -1; startItem() }
            else if (current == item) finish()
        }) {
            pinPromptShown = false
            unlockedGroups.add(item.group)
            if (current == item) startItem()
        }
        return false
    }

    override fun onStart() {
        super.onStart()
        app.livePlayback.ownerStarted(this)
        setLeftWhileWatchingLive(this, false)
        if (queue.isEmpty()) return
        if (hasStartedOnce && isLive) adoptLiveSession()
        hasStartedOnce = true
        val item = current ?: return
        val reuse = if (item.kind == ItemKind.LIVE && !item.isCatchup) attachLivePlayer() else {
            app.livePlayback.stop()
            initPlayer()
            false
        }
        refreshAuthorization()
        if (reuse) {
            if (allowedOrPrompt(item)) {
                playingUrl = PlaybackFactory.streamUrl(item)
                playerView.useController = false
                val p = player
                val error = p?.playerError
                if (app.livePlayback.recoveryFailed) recoveryObserver()
                else if (error != null) handleError(error)
                else if (p != null && (p.playbackState == Player.STATE_ENDED || p.playbackState == Player.STATE_IDLE)) {
                    showMessage("Reconnecting live stream…", error = false)
                    app.livePlayback.retryAutomatically(p, item, 0L)
                }
                showBanner()
            }
        } else startItem()
        handler.postDelayed(savePositionTick, 15_000)
    }

    private fun adoptLiveSession() {
        val session = app.livePlayback
        val item = session.current ?: return
        if (!session.isCurrentAuthorized(item)) return
        val at = session.queue.indexOfFirst { it.playlistId == item.playlistId && it.url == item.url }
        if (at < 0) return
        queue = session.queue
        index = at
        previousIndex = -1
        unlockedGroups.clear()
        session.unlockedGroup(item)?.let { unlockedGroups.add(it) }
        authorizedStamp = app.settings.restrictionStamp()
    }

    override fun onStop() {
        super.onStop()
        // HOME (not BACK) while a live channel plays: lets MainActivity restart it if a launcher clears this player.
        val inPip = Build.VERSION.SDK_INT >= Build.VERSION_CODES.N && isInPictureInPictureMode
        setLeftWhileWatchingLive(this, !isFinishing && !isChangingConfigurations && !inPip && isLive)
        handler.removeCallbacksAndMessages(null)
        savePosition()
        disposePlayer()
        resetFrameRate()
        app.livePlayback.ownerStopped(this)
    }

    // ---------------- Auto frame rate ----------------

    private var appliedFps = 0f
    private val frameTimes = ArrayList<Long>()
    @Volatile private var estimatedFps = 0f
    @Volatile private var sampleFrames = false

    // MPEG-TS does not declare a frame rate, so it is estimated from the first rendered frames (playback thread).
    private val frameSampler = VideoFrameMetadataListener { presentationTimeUs, _, _, _ ->
        if (!sampleFrames) return@VideoFrameMetadataListener
        val est = synchronized(frameTimes) {
            frameTimes.add(presentationTimeUs)
            // ~5 s of frames keeps millisecond timestamp rounding below the 23.976/24 distinction
            if (frameTimes.size < 600 && (frameTimes.size < 30 || abs(presentationTimeUs - frameTimes.first()) < 5_000_000L)) null
            else FrameRateMatcher.estimateFps(frameTimes).also { frameTimes.clear() }
        }
        if (est != null) { sampleFrames = false; estimatedFps = est; handler.post { applyFrameRate() } }
    }

    private fun restartFrameSampling() {
        synchronized(frameTimes) { frameTimes.clear() }
        estimatedFps = 0f
        sampleFrames = app.settings.autoFrameRate
    }

    @Suppress("DEPRECATION")
    private fun currentDisplay(): android.view.Display? =
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) display else windowManager.defaultDisplay

    private fun applyFrameRate() {
        if (!app.settings.autoFrameRate) return
        val declared = player?.videoFormat?.frameRate ?: return
        val fps = if (declared > 0f) declared else estimatedFps
        if (fps <= 0f || fps == appliedFps) return
        appliedFps = fps
        val d = currentDisplay() ?: return
        val toMode = { m: android.view.Display.Mode -> FrameRateMatcher.Mode(m.modeId, m.physicalWidth, m.physicalHeight, m.refreshRate) }
        val target = FrameRateMatcher.pick(d.supportedModes.map(toMode), toMode(d.mode), fps)
        android.util.Log.i("YourIPTV-AFR", "video ${fps} fps; display ${d.mode.refreshRate} Hz; modes ${d.supportedModes.joinToString { "${it.physicalWidth}x${it.physicalHeight}@${it.refreshRate}" }}; switch to ${target?.refreshRate ?: "none"}")
        if (target == null) return
        window.attributes = window.attributes.apply { preferredDisplayModeId = target.id }
        Dialogs.toast(this, "Display: ${"%.2f".format(target.refreshRate).trimEnd('0').trimEnd('.')} Hz for ${"%.3f".format(fps).trimEnd('0').trimEnd('.')} fps video")
    }

    private fun resetFrameRate() {
        appliedFps = 0f
        if (window.attributes.preferredDisplayModeId != 0) window.attributes = window.attributes.apply { preferredDisplayModeId = 0 }
    }

    // ---------------- Player setup ----------------

    private fun initPlayer() {
        audioDelay = AudioDelayProcessor()
        val created = PlaybackFactory.create(this, current, audioDelay)
        playerUa = created.userAgent
        attachPlayer(created.player, shared = false)
    }

    private fun attachLivePlayer(): Boolean {
        val item = current ?: return false
        val session = app.livePlayback
        val p = session.obtain(item)
        val reuse = session.isCurrentAuthorized(item)
        audioDelay = session.audioDelay
        playerUa = session.userAgent
        attachPlayer(p, shared = true)
        return reuse
    }

    private fun attachPlayer(p: ExoPlayer, shared: Boolean) {
        p.addListener(playbackListener)
        player = p
        sharedPlayer = shared
        p.setVideoFrameMetadataListener(frameSampler)
        if (shared) {
            app.livePlayback.observe(recoveryObserver)
            app.livePlayback.attachFullscreen(playerView)
            recoveryObserver()
        }
        else playerView.player = EpisodeAwarePlayer(p)
    }

    private fun disposePlayer() {
        val p = player ?: return
        if (sharedPlayer) app.livePlayback.removeObserver(recoveryObserver)
        if (!sharedPlayer || app.livePlayback.owns(p)) {
            p.removeListener(playbackListener)
            p.clearVideoFrameMetadataListener(frameSampler)
            if (sharedPlayer) {
                app.livePlayback.detach(playerView)
            }
            else { playerView.player = null; p.release() }
        }
        player = null
        sharedPlayer = false
    }

    fun onRecordingTookConnection() {
        if (!sharedPlayer) return
        disposePlayer()
        showMessage("Playback stopped while recording uses this provider's only connection.\n\nOK: retry after recording   BACK: exit", error = true)
    }

    private fun canStepEpisode(delta: Int) = current?.kind == ItemKind.EPISODE && (index + delta) in queue.indices

    private fun stepEpisode(delta: Int): Boolean {
        if (!canStepEpisode(delta)) return false
        switchTo(index + delta)
        return true
    }

    // Exposes the episode queue to the on-screen controller so its Previous/Next buttons change episode.
    private inner class EpisodeAwarePlayer(p: Player) : ForwardingPlayer(p) {
        override fun getAvailableCommands(): Player.Commands {
            val b = super.getAvailableCommands().buildUpon()
            if (canStepEpisode(1)) b.addAll(Player.COMMAND_SEEK_TO_NEXT, Player.COMMAND_SEEK_TO_NEXT_MEDIA_ITEM)
            if (canStepEpisode(-1)) b.addAll(Player.COMMAND_SEEK_TO_PREVIOUS, Player.COMMAND_SEEK_TO_PREVIOUS_MEDIA_ITEM)
            return b.build()
        }
        override fun isCommandAvailable(command: Int) = availableCommands.contains(command)
        override fun hasNextMediaItem() = canStepEpisode(1) || super.hasNextMediaItem()
        override fun hasPreviousMediaItem() = canStepEpisode(-1) || super.hasPreviousMediaItem()
        override fun seekToNext() { if (!stepEpisode(1)) super.seekToNext() }
        override fun seekToNextMediaItem() { if (!stepEpisode(1)) super.seekToNextMediaItem() }
        override fun seekToPreviousMediaItem() { if (!stepEpisode(-1)) super.seekToPreviousMediaItem() }
        override fun seekToPrevious() {
            if (currentPosition > 3000 || !stepEpisode(-1)) super.seekToPrevious()
        }
    }

    private fun startItem() {
        val item = current ?: return
        if (!allowedOrPrompt(item)) return
        if (item.kind == ItemKind.LIVE && Scheduler.recordingUsesOnlyConnection(item.playlistId)) {
            showMessage("This provider's only connection is being used by a recording.\n\nOK: retry after recording   BACK: exit", error = true)
            return
        }
        val p = player ?: return
        errorShown = false
        message.visibility = View.GONE
        retries = 0
        probeRetried = false
        restartFrameSampling()
        val url = PlaybackFactory.streamUrl(item)
        playingUrl = url
        if (app.livePlayback.owns(p)) app.livePlayback.noteFullscreenItem(item, queue, unlockedGroups, authorizedStamp)
        if (app.livePlayback.owns(p)) app.livePlayback.resetRecovery()
        android.util.Log.i("YourIPTV", "Start ${item.kind} stream as ${if (url.contains(".m3u8", true)) "HLS" else "progressive/TS"}; buffer ${app.settings.bufferMs} ms; sw decode ${app.settings.preferSoftwareDecoding}")
        lifecycleScope.launch {
            val resumeAt = if (item.kind == ItemKind.LIVE) 0L else withContext(Dispatchers.IO) { app.repo.resumePosition(item) }
            if (player !== p || current != item) return@launch
            p.setMediaItem(PlaybackFactory.mediaItem(item), resumeAt)
            p.prepare()
            p.playWhenReady = true
            if (resumeAt > 0) Dialogs.toast(this@PlayerActivity, "Resuming from ${Format.duration(resumeAt)}")
        }
        playerView.useController = item.kind != ItemKind.LIVE
        playerView.controllerShowTimeoutMs = 4000
        timeBar()?.setKeyTimeIncrement(app.settings.skipSeconds * 1000L)
        if (item.kind == ItemKind.LIVE) {
            app.settings.lastChannelUrl = item.url
            lifecycleScope.launch(Dispatchers.IO) { app.repo.recordHistory(item) }
        }
        showBanner()
    }

    private fun switchTo(newIndex: Int) {
        if (newIndex !in queue.indices) return
        savePosition()
        previousIndex = index
        index = newIndex
        startItem()
    }

    private fun zap(delta: Int) {
        if (queue.size < 2) return
        switchTo(((index + delta) % queue.size + queue.size) % queue.size)
    }

    private fun onEnded() {
        val item = current ?: return
        if (item.kind == ItemKind.LIVE) {
            val p = player
            if (!item.isCatchup && (app.livePlayback.hasPendingRestart || app.livePlayback.recoveryFailed)) return
            if (p != null && !item.isCatchup) {
                showMessage("Live stream ended. Reconnecting…", error = false)
                app.livePlayback.retryAutomatically(p, item, 1500L)
            } else if (p != null && retries < 3) {
                retries++
                showMessage("Live stream ended. Reconnecting… ($retries/3)", error = false)
                handler.postDelayed({
                    if (player === p && current == item) {
                        p.seekToDefaultPosition()
                        p.prepare()
                        p.playWhenReady = true
                    }
                }, 1500)
            } else {
                showMessage("The live stream ended and could not be restarted.\n\nOK: retry   ▲▼: change channel   BACK: exit", error = true)
            }
            return
        }
        player?.let { lifecycleScope.launch(Dispatchers.IO) { app.repo.recordHistory(item, it.duration, it.duration) } }
        if (item.kind == ItemKind.EPISODE && index + 1 < queue.size) {
            Dialogs.toast(this, "Playing next episode")
            previousIndex = index; index++; startItem()
        } else finish()
    }

    private fun savePosition() {
        val item = current ?: return
        val p = player ?: return
        if (item.kind == ItemKind.LIVE) return
        val pos = p.currentPosition
        val dur = p.duration
        if (dur <= 0 || pos <= 0) return
        lifecycleScope.launch(Dispatchers.IO) { app.repo.recordHistory(item, pos, dur) }
    }

    // ---------------- Errors ----------------

    private fun handleError(e: PlaybackException) {
        val p = player ?: return
        if (app.livePlayback.owns(p) && (app.livePlayback.hasPendingRestart || app.livePlayback.recoveryFailed)) return
        if (e.errorCode == PlaybackException.ERROR_CODE_BEHIND_LIVE_WINDOW) {
            val item = current
            if (item != null && item.kind == ItemKind.LIVE && !item.isCatchup) app.livePlayback.retryAutomatically(p, item, 0L)
            else { p.seekToDefaultPosition(); p.prepare() }
            return
        }
        var cause: Throwable? = e.cause
        var httpCode = 0
        while (cause != null) {
            if (cause is HttpDataSource.InvalidResponseCodeException) { httpCode = cause.responseCode; break }
            cause = cause.cause
        }
        val decoderProblem = e.errorCode in listOf(
            PlaybackException.ERROR_CODE_DECODER_INIT_FAILED,
            PlaybackException.ERROR_CODE_DECODER_QUERY_FAILED,
            PlaybackException.ERROR_CODE_DECODING_FAILED,
            PlaybackException.ERROR_CODE_DECODING_FORMAT_EXCEEDS_CAPABILITIES,
            PlaybackException.ERROR_CODE_DECODING_FORMAT_UNSUPPORTED,
        )
        val accessDenied = httpCode in listOf(401, 403, 456, 458, 509)
        // A refused request right after zapping usually means the previous stream still holds the provider's line.
        val maxRetries = if (accessDenied) 2 else if (httpCode != 0) 1 else if (decoderProblem) 0 else 3
        if (retries < maxRetries) {
            retries++
            showMessage("Reconnecting… ($retries/$maxRetries)", error = false)
            val failedItem = current
            if (failedItem != null && failedItem.kind == ItemKind.LIVE && !failedItem.isCatchup) {
                app.livePlayback.retryAutomatically(p, failedItem, 1500L * retries)
            } else handler.postDelayed({
                if (player === p && current == failedItem) p.prepare()
            }, 1500L * retries)
            return
        }
        val maxConn = current?.let { app.repo.connectionLimit(it.playlistId) } ?: 0
        val limitHint = (if (maxConn > 0) "Your provider allows $maxConn simultaneous connection(s). " else "") +
            (if (com.youriptv.app.data.Recorder.activeCount() > 0) "A recording in progress is using a provider connection. " else "")
        val text = when {
            httpCode in listOf(401, 403, 456, 458, 509) ->
                Http.describe(httpCode) + "\n" + limitHint + "If another device or app is already watching, stop it and retry."
            httpCode == 404 -> "This stream is not available from the provider (HTTP 404)."
            httpCode != 0 -> Http.describe(httpCode)
            decoderProblem -> "This device cannot decode this stream (${e.errorCodeName}).\n" +
                "Try changing \"Prefer software decoding\" in Settings > Playback."
            e.errorCode == PlaybackException.ERROR_CODE_PARSING_CONTAINER_UNSUPPORTED ||
                e.errorCode == PlaybackException.ERROR_CODE_PARSING_CONTAINER_MALFORMED ->
                "The stream format is not supported or the data is damaged (${e.errorCodeName})." + when {
                    isLive -> "\nTry the other live stream format (TS / HLS) in Settings > Playback."
                    current?.isCatchup == true -> "\nThe provider's archive returned no video. The programme may not be recorded yet, " +
                        "or a network filter may be blocking the provider's archive server."
                    else -> ""
                }
            e.errorCode == PlaybackException.ERROR_CODE_IO_NETWORK_CONNECTION_FAILED ||
                e.errorCode == PlaybackException.ERROR_CODE_IO_NETWORK_CONNECTION_TIMEOUT ->
                "Could not connect to the stream. Check your network connection.\n$limitHint"
            else -> "Playback failed (${e.errorCodeName})."
        }
        val footer = "\n\nOK: retry" + if (isLive) "   ▲▼: change channel   BACK: exit" else "   BACK: exit"
        val item = current
        val url = playingUrl
        // A live channel keeps reconnecting in the background while the explanation is shown.
        if (!decoderProblem && item != null && item.kind == ItemKind.LIVE && !item.isCatchup) {
            app.livePlayback.retryAutomatically(p, item, 1500L)
        }
        if (decoderProblem || item == null || item.isLocal || !url.startsWith("http", true)) {
            showMessage(text + footer, error = true)
            return
        }
        // Find out whether the network, the provider or the device is at fault before explaining the failure.
        showMessage("Playback failed. Checking the connection to the server…", error = true)
        val ua = playerUa
        lifecycleScope.launch {
            val result = withContext(Dispatchers.IO) { StreamDiagnosis.probe(url, ua) }
            if (current !== item || !errorShown) return@launch
            android.util.Log.i("YourIPTV", "Stream diagnosis: ${result.kind} http=${result.httpCode} host=${result.host}")
            if (accessDenied && result.kind == StreamDiagnosis.Kind.MEDIA_OK) {
                // The provider now serves the stream, so the earlier refusal was a busy connection slot, not the account.
                if (!probeRetried) {
                    probeRetried = true
                    showMessage("The provider connection is free again. Reconnecting…", error = false)
                    player?.let {
                        if (item.kind == ItemKind.LIVE && !item.isCatchup) app.livePlayback.retryAutomatically(it, item, 0L)
                        else { it.prepare(); it.playWhenReady = true }
                    }
                    return@launch
                }
                showMessage("The provider refused the player (HTTP $httpCode) but answered a connection check normally. " +
                    "The provider's connection limit was probably still held by the previous stream.\n" + limitHint +
                    "Wait a moment and retry." + footer, error = true)
                return@launch
            }
            val explained = StreamDiagnosis.message(result, item.isCatchup)
            val extra = when (result.kind) {
                StreamDiagnosis.Kind.MEDIA_OK -> "\n$text"
                StreamDiagnosis.Kind.ACCESS_DENIED, StreamDiagnosis.Kind.CONNECTION_DROPPED -> if (limitHint.isNotBlank()) "\n$limitHint" else ""
                else -> ""
            }
            showMessage(explained + extra + footer, error = true)
        }
    }

    private fun showMessage(text: String, error: Boolean) {
        errorShown = error
        message.text = text
        message.visibility = View.VISIBLE
    }

    private fun retry() {
        val item = current
        if (item != null && item.kind == ItemKind.LIVE && Scheduler.recordingUsesOnlyConnection(item.playlistId)) {
            showMessage("This provider's only connection is being used by a recording.\n\nOK: retry after recording   BACK: exit", error = true)
            return
        }
        if (player == null && item != null && item.kind == ItemKind.LIVE && !item.isCatchup) {
            attachLivePlayer()
            startItem()
            return
        }
        errorShown = false
        message.visibility = View.GONE
        retries = 0
        player?.let {
            if (item != null && item.kind == ItemKind.LIVE && !item.isCatchup) {
                if (allowedOrPrompt(item)) sessionRestart(it, item)
            } else {
                if (it.playbackState == Player.STATE_ENDED) it.seekToDefaultPosition()
                it.prepare()
                it.playWhenReady = true
            }
        }
    }

    private fun sessionRestart(p: ExoPlayer, item: MediaItem) {
        app.livePlayback.resetRecovery()
        PlaybackFactory.restartLive(p, item)
    }

    // ---------------- Overlays ----------------

    private fun showBanner() {
        val item = current ?: return
        handler.removeCallbacks(hideBanner)
        val logo = findViewById<ImageView>(R.id.banner_logo)
        ImageLoader.load(logo, item.logo, R.drawable.ic_channel)
        findViewById<TextView>(R.id.banner_channel).text = when {
            item.isCatchup -> "${Catchup.MARK} Catch-up  ·  ${item.group}"
            item.kind == ItemKind.LIVE && item.number > 0 -> "${item.number}  ${item.name}"
            else -> item.group
        }
        findViewById<TextView>(R.id.banner_clock).text = Format.clock()
        val title = findViewById<TextView>(R.id.banner_title)
        val time = findViewById<TextView>(R.id.banner_time)
        val progress = findViewById<ProgressBar>(R.id.banner_progress)
        val remaining = findViewById<TextView>(R.id.banner_remaining)
        val desc = findViewById<TextView>(R.id.banner_desc)
        val next = findViewById<TextView>(R.id.banner_next)
        findViewById<View>(R.id.banner_hint).visibility = if (item.kind == ItemKind.LIVE) View.VISIBLE else View.GONE
        title.text = if (item.kind == ItemKind.LIVE) item.name else item.name
        time.text = ""; remaining.text = ""; desc.text = ""; next.text = ""
        progress.visibility = View.GONE
        updateBadges()
        banner.visibility = View.VISIBLE
        handler.postDelayed(hideBanner, if (item.kind == ItemKind.LIVE) 6000 else 3500)
        if (item.kind != ItemKind.LIVE) return
        lifecycleScope.launch {
            val shift = app.settings.epgShiftHours * 3_600_000L
            val now = System.currentTimeMillis()
            val progs = withContext(Dispatchers.IO) { app.repo.nowAndNext(item.playlistId, item.tvgId, now - shift) }
            if (current != item) return@launch
            val cur = progs.firstOrNull { it.start <= now - shift }
            val nxt = progs.firstOrNull { it.start > now - shift }
            if (cur != null) {
                title.text = cur.title
                time.text = "${Format.time(cur.start + shift)} – ${Format.time(cur.end + shift)}"
                progress.visibility = View.VISIBLE
                progress.progress = (((now - shift - cur.start) * 1000) / (cur.end - cur.start).coerceAtLeast(1)).toInt()
                remaining.text = "${((cur.end - (now - shift)) / 60_000).coerceAtLeast(0)} min left"
                desc.text = cur.description
            } else {
                time.text = "No guide information"
            }
            if (nxt != null) next.text = "Next: ${Format.time(nxt.start + shift)}  ${nxt.title}"
        }
    }

    private fun toggleBanner() {
        if (banner.visibility == View.VISIBLE) { handler.removeCallbacks(hideBanner); banner.visibility = View.GONE } else showBanner()
    }

    private fun updateBadges() {
        val p = player ?: return
        val badges = ArrayList<String>()
        p.videoFormat?.let { f ->
            val h = minOf(f.height, f.width).takeIf { it > 0 } ?: f.height
            when {
                h >= 2000 -> badges.add("4K")
                h >= 1000 -> badges.add("FULL HD")
                h >= 700 -> badges.add("HD")
                h > 0 -> badges.add("SD")
            }
            if (f.frameRate > 0) badges.add("${Math.round(f.frameRate)} FPS")
        }
        p.audioFormat?.let { f ->
            when (f.channelCount) {
                1 -> badges.add("MONO"); 2 -> badges.add("STEREO"); 6 -> badges.add("5.1"); 8 -> badges.add("7.1")
            }
        }
        findViewById<TextView>(R.id.banner_badges).text = badges.joinToString("  ")
    }

    private fun showOverlay() {
        val item = current ?: return
        banner.visibility = View.GONE
        findViewById<TextView>(R.id.overlay_title).text = if (queue.size > 1) item.group else item.name
        lifecycleScope.launch {
            val favs = withContext(Dispatchers.IO) { app.repo.favorites(item.playlistId).map { it.url }.toHashSet() }
            overlayAdapter.submit(queue, favs)
            overlay.visibility = View.VISIBLE
            (overlayList.layoutManager as LinearLayoutManager).scrollToPositionWithOffset(index, 200)
            overlayList.post { overlayList.findViewHolderForAdapterPosition(index)?.itemView?.requestFocus() }
        }
    }

    private fun hideOverlay() { overlay.visibility = View.GONE }

    private fun refreshCurrentMetadataForReturn() {
        val item = current ?: return
        if (item.isLocal || item.isCatchup || item.kind == ItemKind.LIVE) return
        val fresh = app.repo.itemByUrl(item.playlistId, item.url) ?: return
        if (fresh.kind != item.kind || fresh.group == item.group) return
        // A provider refresh can move a movie/episode into a locked category without changing the settings stamp.
        unlockedGroups.clear()
        queue = queue.toMutableList().apply { this[index] = item.copy(group = fresh.group) }
    }

    private fun showCurrentStreamFullscreen() {
        refreshCurrentMetadataForReturn()
        handler.removeCallbacks(hideBanner)
        handler.removeCallbacks(commitNumber)
        digits.clear()
        numberEntry.visibility = View.GONE
        banner.visibility = View.GONE
        hideOverlay()
        playerView.hideController()
        if (lifecycle.currentState.isAtLeast(androidx.lifecycle.Lifecycle.State.STARTED)) {
            refreshAuthorization()
            current?.let { allowedOrPrompt(it) }
        }
    }

    // ---------------- Options ----------------

    private fun showOptions() {
        val item = current ?: return
        val p = player ?: return
        val opts = ArrayList<Pair<String, () -> Unit>>()
        if (canStepEpisode(1)) opts.add("Next episode: ${queue[index + 1].name}" to { stepEpisode(1) })
        if (canStepEpisode(-1)) opts.add("Previous episode: ${queue[index - 1].name}" to { stepEpisode(-1) })
        opts.add("Audio track" to { trackChooser(C.TRACK_TYPE_AUDIO, "Audio track", allowOff = false) })
        opts.add("Subtitles" to { trackChooser(C.TRACK_TYPE_TEXT, "Subtitles", allowOff = true) })
        opts.add("Video quality" to { trackChooser(C.TRACK_TYPE_VIDEO, "Video quality", allowOff = false) })
        opts.add("Audio delay: ${formatDelay(audioDelay.delayMs)}" to { adjustAudioDelay() })
        opts.add("Aspect ratio: ${ASPECTS[app.settings.aspectMode.coerceIn(0, 2)]}" to { chooseAspect() })
        opts.add("Picture-in-picture" to { enterPip() })
        if (item.kind == ItemKind.LIVE) {
            opts.add("Channel list" to { showOverlay() })
            opts.add("TV guide" to { openGuide(item) })
            if (previousIndex in queue.indices) opts.add("Previous channel: ${queue[previousIndex].name}" to { switchTo(previousIndex) })
            if (!item.isCatchup) opts.add("Record this channel" to { recordFromPlayer(item) })
        }
        if (!item.isCatchup && !item.isLocal) {
            val fav = app.repo.isFavorite(item)
            opts.add((if (fav) "Remove from favorites" else "Add to favorites") to { toggleFavorite(item) })
        }
        if (!item.isLocal) opts.add("Open in external player" to { Nav.externalPlayer(this, item.url, item.name) })
        opts.add("Stream information" to { streamInfo(p) })
        opts.add("Reload stream" to { retry() })
        Dialogs.choose(this, item.name, opts.map { it.first }) { opts[it].second() }
    }

    /** With a one-connection account, playback stops so the recording can use the provider connection. */
    private fun recordFromPlayer(item: MediaItem) {
        val shift = app.settings.epgShiftHours * 3_600_000L
        val now = System.currentTimeMillis()
        val cur = app.repo.nowAndNext(item.playlistId, item.tvgId, now - shift).firstOrNull { it.start <= now - shift }
            ?.let { it.copy(start = it.start + shift, end = it.end + shift) }
        val single = app.repo.connectionLimit(item.playlistId) == 1
        Scheduler.recordNow(this, item, cur) {
            if (single) { player?.stop(); finish() }
        }
    }

    private fun toggleFavorite(item: MediaItem) {
        lifecycleScope.launch {
            val nowFav = withContext(Dispatchers.IO) {
                val f = !app.repo.isFavorite(item); app.repo.setFavorite(item, f); f
            }
            Dialogs.toast(this@PlayerActivity, if (nowFav) "Added to favorites" else "Removed from favorites")
            if (overlay.visibility == View.VISIBLE) {
                overlayAdapter.favorites = if (nowFav) overlayAdapter.favorites + item.url else overlayAdapter.favorites - item.url
                overlayAdapter.notifyDataSetChanged()
            }
        }
    }

    private fun trackChooser(type: Int, title: String, allowOff: Boolean) {
        val p = player ?: return
        val entries = ArrayList<Pair<String, () -> Unit>>()
        val params = p.trackSelectionParameters
        if (allowOff) entries.add(("Off" + if (params.disabledTrackTypes.contains(type)) " ✓" else "") to {
            p.trackSelectionParameters = p.trackSelectionParameters.buildUpon().setTrackTypeDisabled(type, true).build()
        })
        if (type == C.TRACK_TYPE_VIDEO) entries.add("Auto" to {
            p.trackSelectionParameters = p.trackSelectionParameters.buildUpon().clearOverridesOfType(type).build()
        })
        var n = 0
        for (g in p.currentTracks.groups) {
            if (g.type != type) continue
            for (i in 0 until g.length) {
                if (!g.isTrackSupported(i)) continue
                n++
                val label = trackLabel(g.getTrackFormat(i), type, n) + if (g.isTrackSelected(i)) " ✓" else ""
                entries.add(label to {
                    p.trackSelectionParameters = p.trackSelectionParameters.buildUpon()
                        .setTrackTypeDisabled(type, false)
                        .setOverrideForType(TrackSelectionOverride(g.mediaTrackGroup, i))
                        .build()
                })
            }
        }
        if (n == 0) { Dialogs.toast(this, "This stream has no selectable ${title.lowercase()} tracks."); return }
        Dialogs.choose(this, title, entries.map { it.first }) { entries[it].second() }
    }

    private fun trackLabel(f: ExoFormat, type: Int, n: Int): String {
        val parts = ArrayList<String>()
        f.label?.takeIf { it.isNotBlank() }?.let { parts.add(it) }
        f.language?.takeIf { it.isNotBlank() && it != "und" }?.let { parts.add(Locale.forLanguageTag(it).displayLanguage.ifBlank { it }) }
        when (type) {
            C.TRACK_TYPE_VIDEO -> if (f.width > 0) parts.add("${f.width} × ${f.height}")
            C.TRACK_TYPE_AUDIO -> if (f.channelCount > 0) parts.add("${f.channelCount} ch")
        }
        if (f.bitrate > 0) parts.add("${f.bitrate / 1000} kbps")
        f.sampleMimeType?.let { parts.add(it.substringAfter('/')) }
        return parts.ifEmpty { listOf("Track $n") }.joinToString(" · ")
    }

    private fun formatDelay(ms: Int) = if (ms > 0) "+$ms ms" else "$ms ms"

    /** LEFT/RIGHT change the offset live in steps of [AudioDelayProcessor.STEP_MS]; OK or BACK closes. */
    private fun adjustAudioDelay() {
        fun text() = "${formatDelay(audioDelay.delayMs)}\n\n◀ ▶ adjust by ${AudioDelayProcessor.STEP_MS} ms  ·  ▼ reset  ·  OK done\n" +
            "Positive values play audio later. Not applied to passthrough (bitstream) audio."
        val d = AlertDialog.Builder(this).setTitle("Audio delay").setMessage(text())
            .setPositiveButton("Done", null).create()
        d.setOnKeyListener { _, keyCode, event ->
            if (event.action != KeyEvent.ACTION_DOWN) return@setOnKeyListener false
            val step = when (keyCode) {
                KeyEvent.KEYCODE_DPAD_LEFT -> -AudioDelayProcessor.STEP_MS
                KeyEvent.KEYCODE_DPAD_RIGHT -> AudioDelayProcessor.STEP_MS
                KeyEvent.KEYCODE_DPAD_DOWN -> -audioDelay.delayMs
                else -> return@setOnKeyListener false
            }
            val v = (audioDelay.delayMs + step).coerceIn(-AudioDelayProcessor.MAX_MS, AudioDelayProcessor.MAX_MS)
            audioDelay.delayMs = v
            app.settings.audioDelayMs = v
            d.setMessage(text())
            true
        }
        d.show()
        RemoteBack.bind(d, this)
    }

    private fun chooseAspect() {
        Dialogs.choose(this, "Aspect ratio", ASPECTS, checked = app.settings.aspectMode) { i ->
            app.settings.aspectMode = i
            playerView.resizeMode = resizeModeFor(i)
        }
    }

    private fun resizeModeFor(mode: Int) = when (mode) {
        1 -> AspectRatioFrameLayout.RESIZE_MODE_ZOOM
        2 -> AspectRatioFrameLayout.RESIZE_MODE_FILL
        else -> AspectRatioFrameLayout.RESIZE_MODE_FIT
    }

    private fun streamInfo(p: ExoPlayer) {
        val v = p.videoFormat
        val a = p.audioFormat
        val lines = listOfNotNull(
            v?.let { "Video: ${it.sampleMimeType ?: "?"}  ${it.width}×${it.height}" + (if (it.frameRate > 0) "  ${"%.2f".format(it.frameRate)} fps" else "") },
            a?.let { "Audio: ${it.sampleMimeType ?: "?"}  ${it.channelCount} ch  ${it.sampleRate} Hz" },
            "Buffered: ${(p.totalBufferedDuration / 1000)} s",
            current?.let { "Type: ${it.kind.name.lowercase()}   Group: ${it.group}" },
        )
        Dialogs.message(this, "Stream information", lines.joinToString("\n"))
    }

    private fun enterPip() {
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.O || !packageManager.hasSystemFeature(PackageManager.FEATURE_PICTURE_IN_PICTURE)) {
            Dialogs.toast(this, "Picture-in-picture is not supported on this device.")
            return
        }
        banner.visibility = View.GONE
        hideOverlay()
        playerView.hideController()
        try {
            enterPictureInPictureMode(PictureInPictureParams.Builder().setAspectRatio(Rational(16, 9)).build())
        } catch (e: IllegalStateException) {
            Dialogs.toast(this, "Picture-in-picture is not available: ${e.message}")
        }
    }

    override fun onPictureInPictureModeChanged(isInPictureInPictureMode: Boolean, newConfig: Configuration) {
        super.onPictureInPictureModeChanged(isInPictureInPictureMode, newConfig)
        if (isInPictureInPictureMode) {
            banner.visibility = View.GONE; hideOverlay(); numberEntry.visibility = View.GONE
            playerView.useController = false
        } else {
            playerView.useController = !isLive
        }
    }

    // ---------------- Number entry ----------------

    private fun addDigit(d: Int) {
        if (digits.length >= 5) digits.clear()
        digits.append(d)
        numberEntry.text = digits
        numberEntry.visibility = View.VISIBLE
        handler.removeCallbacks(commitNumber)
        handler.postDelayed(commitNumber, 2000)
    }

    private fun commitDigits() {
        val n = digits.toString().toIntOrNull()
        digits.clear()
        numberEntry.visibility = View.GONE
        if (n == null) return
        val inQueue = queue.indexOfFirst { it.kind == ItemKind.LIVE && it.number == n }
        if (inQueue >= 0) { switchTo(inQueue); return }
        val pid = current?.playlistId ?: return
        lifecycleScope.launch {
            val found = withContext(Dispatchers.IO) {
                // Channels in hidden groups (blocked adult categories, hidden locked groups) cannot be reached by number.
                app.repo.channelByNumber(pid, n)?.takeIf { !app.settings.isGroupHidden(it.group) }
                    ?.let { ch -> ch to app.repo.items(pid, ItemKind.LIVE, ch.group) }
            }
            if (found == null) { Dialogs.toast(this@PlayerActivity, "No channel number $n"); return@launch }
            savePosition()
            queue = found.second
            YourIptvApp.playQueue = queue
            previousIndex = -1
            index = queue.indexOfFirst { it.url == found.first.url }.coerceAtLeast(0)
            startItem()
        }
    }

    // ---------------- Keys ----------------

    protected override fun dispatchAppKeyEvent(event: KeyEvent): Boolean {
        val code = event.keyCode
        val down = event.action == KeyEvent.ACTION_DOWN
        val up = event.action == KeyEvent.ACTION_UP

        if (overlay.visibility == View.VISIBLE) {
            if (code == KeyEvent.KEYCODE_BACK || code == KeyEvent.KEYCODE_DPAD_RIGHT) {
                if (up) hideOverlay()
                return true
            }
            return super.dispatchAppKeyEvent(event)
        }

        digitOf(code)?.let { d ->
            if (isLive) { if (down && event.repeatCount == 0) addDigit(d); return true }
        }

        if (code == KeyEvent.KEYCODE_MENU) {
            if (up) showOptions()
            return true
        }

        if (code == KeyEvent.KEYCODE_DPAD_CENTER || code == KeyEvent.KEYCODE_ENTER || code == KeyEvent.KEYCODE_NUMPAD_ENTER) {
            if (!isLive && !errorShown && playerView.isControllerFullyVisible) return super.dispatchAppKeyEvent(event)
            if (down) {
                if (event.repeatCount == 0) { okDownAt = event.eventTime; longPressFired = false }
                else if (!longPressFired && (event.isLongPress || event.eventTime - okDownAt >= 600)) { longPressFired = true; showOptions() }
                return true
            }
            if (up && !longPressFired) {
                when {
                    errorShown -> retry()
                    isLive -> toggleBanner()
                    else -> { player?.let { it.playWhenReady = !it.playWhenReady }; playerView.showController() }
                }
            }
            return true
        }

        if (code == KeyEvent.KEYCODE_BACK) {
            if (up) {
                when {
                    numberEntry.visibility == View.VISIBLE -> { handler.removeCallbacks(commitNumber); digits.clear(); numberEntry.visibility = View.GONE }
                    errorShown -> finish()
                    banner.visibility == View.VISIBLE && isLive && !intent.getBooleanExtra(EXTRA_FROM_PREVIEW, false) -> {
                        handler.removeCallbacks(hideBanner); banner.visibility = View.GONE
                    }
                    !isLive && playerView.isControllerFullyVisible -> playerView.hideController()
                    else -> finish()
                }
            }
            return true
        }

        return if (isLive) liveKey(event, code, down, up) || super.dispatchAppKeyEvent(event)
        else vodKey(event, code, down) || super.dispatchAppKeyEvent(event)
    }

    /** The guide lives in Live TV: return there with this channel's category and row, as BACK from a preview would. */
    private fun openGuide(item: MediaItem) {
        startActivity(
            Intent(this, MainActivity::class.java)
                .putExtra(MainActivity.EXTRA_GUIDE_GROUP, item.group)
                .putExtra(MainActivity.EXTRA_GUIDE_CHANNEL, item.url)
                .addFlags(Intent.FLAG_ACTIVITY_CLEAR_TOP or Intent.FLAG_ACTIVITY_SINGLE_TOP)
        )
    }

    private fun liveKey(event: KeyEvent, code: Int, down: Boolean, up: Boolean): Boolean {
        val invert = app.settings.invertZap
        when (code) {
            KeyEvent.KEYCODE_DPAD_UP -> { if (down) zap(if (invert) 1 else -1); return true }
            KeyEvent.KEYCODE_DPAD_DOWN -> { if (down) zap(if (invert) -1 else 1); return true }
            KeyEvent.KEYCODE_CHANNEL_UP, KeyEvent.KEYCODE_PAGE_UP -> { if (down) zap(1); return true }
            KeyEvent.KEYCODE_CHANNEL_DOWN, KeyEvent.KEYCODE_PAGE_DOWN -> { if (down) zap(-1); return true }
            KeyEvent.KEYCODE_DPAD_LEFT -> { if (up) showOverlay(); return true }
            KeyEvent.KEYCODE_DPAD_RIGHT, KeyEvent.KEYCODE_INFO -> { if (up) showBanner(); return true }
            KeyEvent.KEYCODE_LAST_CHANNEL -> { if (up && previousIndex in queue.indices) switchTo(previousIndex); return true }
            KeyEvent.KEYCODE_GUIDE -> {
                if (up) current?.let { openGuide(it) }
                return true
            }
            KeyEvent.KEYCODE_MEDIA_PLAY_PAUSE, KeyEvent.KEYCODE_MEDIA_PLAY, KeyEvent.KEYCODE_MEDIA_PAUSE -> {
                if (up) player?.let {
                    it.playWhenReady = when (code) {
                        KeyEvent.KEYCODE_MEDIA_PLAY -> true
                        KeyEvent.KEYCODE_MEDIA_PAUSE -> false
                        else -> !it.playWhenReady
                    }
                }
                return true
            }
        }
        return false
    }

    private fun vodKey(event: KeyEvent, code: Int, down: Boolean): Boolean {
        if (current?.kind == ItemKind.EPISODE) {
            val delta = when (code) {
                KeyEvent.KEYCODE_MEDIA_NEXT, KeyEvent.KEYCODE_CHANNEL_UP, KeyEvent.KEYCODE_PAGE_UP -> 1
                KeyEvent.KEYCODE_MEDIA_PREVIOUS, KeyEvent.KEYCODE_CHANNEL_DOWN, KeyEvent.KEYCODE_PAGE_DOWN -> -1
                else -> 0
            }
            if (delta != 0) {
                if (down && event.repeatCount == 0 && !stepEpisode(delta))
                    Dialogs.toast(this, if (delta > 0) "This is the last episode" else "This is the first episode")
                return true
            }
        }
        if (playerView.isControllerFullyVisible) {
            val focused = currentFocus
            val horizontal = code == KeyEvent.KEYCODE_DPAD_LEFT || code == KeyEvent.KEYCODE_DPAD_RIGHT
            val onTransport = focused == null || focused.id == androidx.media3.ui.R.id.exo_play_pause
            if (!(horizontal && onTransport)) return playerView.dispatchKeyEvent(event)
        }
        val p = player ?: return false
        val skip = app.settings.skipSeconds * 1000L
        when (code) {
            KeyEvent.KEYCODE_DPAD_LEFT, KeyEvent.KEYCODE_MEDIA_REWIND -> {
                if (down) { p.seekTo((p.currentPosition - skip).coerceAtLeast(0)); showControllerOnTimeBar() }
                return true
            }
            KeyEvent.KEYCODE_DPAD_RIGHT, KeyEvent.KEYCODE_MEDIA_FAST_FORWARD -> {
                if (down) {
                    val target = p.currentPosition + skip
                    p.seekTo(if (p.duration > 0) target.coerceAtMost(p.duration - 1000) else target)
                    showControllerOnTimeBar()
                }
                return true
            }
            KeyEvent.KEYCODE_DPAD_UP, KeyEvent.KEYCODE_DPAD_DOWN, KeyEvent.KEYCODE_INFO -> {
                if (down) { playerView.showController(); showBanner() }
                return true
            }
        }
        return playerView.dispatchKeyEvent(event)
    }

    private fun timeBar(): DefaultTimeBar? = playerView.findViewById(androidx.media3.ui.R.id.exo_progress)

    // Keeps repeated LEFT/RIGHT presses seeking: the focused time bar steps by the skip interval.
    private fun showControllerOnTimeBar() {
        playerView.showController()
        timeBar()?.let { it.isFocusable = true; it.requestFocus() }
    }

    private fun digitOf(code: Int): Int? = when (code) {
        in KeyEvent.KEYCODE_0..KeyEvent.KEYCODE_9 -> code - KeyEvent.KEYCODE_0
        in KeyEvent.KEYCODE_NUMPAD_0..KeyEvent.KEYCODE_NUMPAD_9 -> code - KeyEvent.KEYCODE_NUMPAD_0
        else -> null
    }

    companion object {
        const val EXTRA_INDEX = "index"
        const val EXTRA_UNLOCKED_GROUP = "unlocked_group"
        const val EXTRA_AUTHORIZATION_STAMP = "authorization_stamp"
        const val EXTRA_FROM_PREVIEW = "from_preview"
        const val EXTRA_RETURN_TO_STREAM = "return_to_stream"
        private val ASPECTS = listOf("Fit", "Zoom (crop)", "Stretch")
        private var active: java.lang.ref.WeakReference<PlayerActivity>? = null
        private const val KEY_LEFT_LIVE = "left_while_watching_live"

        private fun returnTarget(): PlayerActivity? = active?.get()?.takeIf { target ->
            if (target.isFinishing || target.isDestroyed) return@takeIf false
            val item = target.current ?: return@takeIf false
            val p = target.player ?: return@takeIf false
            val mediaId = p.currentMediaItem?.mediaId ?: return@takeIf false
            if (item.kind != ItemKind.LIVE || item.isCatchup) {
                return@takeIf CurrentStreamReturn.canReusePlayer(item, mediaId, false, null, null)
            }
            val session = target.app.livePlayback
            val fresh = session.rememberedStream() ?: return@takeIf false
            // Metadata changed by a refresh needs the normal launch/PIN path, not an old activity's grant.
            CurrentStreamReturn.canReusePlayer(item, mediaId, target.sharedPlayer && session.owns(p), session.current, fresh)
        }

        fun hasCurrentStream(): Boolean = returnTarget() != null

        fun returnToCurrentStream(a: android.app.Activity): Boolean {
            val target = returnTarget() ?: return false
            if (target.isLive && target.sharedPlayer) target.adoptLiveSession()
            target.refreshCurrentMetadataForReturn()
            if (target === a) {
                target.showCurrentStreamFullscreen()
            } else {
                // Extras also make an unexpected new instance safe if Android has removed the old activity.
                YourIptvApp.playQueue = target.queue
                a.startActivity(Intent(a, PlayerActivity::class.java)
                    .putExtra(EXTRA_INDEX, target.index)
                    .putExtra(EXTRA_RETURN_TO_STREAM, true)
                    .addFlags(Intent.FLAG_ACTIVITY_CLEAR_TOP or Intent.FLAG_ACTIVITY_SINGLE_TOP))
            }
            return true
        }

        // Kept in preferences so it survives the process being killed in the background.
        private fun session(c: android.content.Context) =
            c.applicationContext.getSharedPreferences("session", android.content.Context.MODE_PRIVATE)

        private fun setLeftWhileWatchingLive(c: android.content.Context, value: Boolean) =
            session(c).edit().putBoolean(KEY_LEFT_LIVE, value).apply()

        /** True (once) if the app was left with HOME while a live channel was playing. */
        fun takeLeftWhileWatchingLive(c: android.content.Context): Boolean {
            val left = session(c).getBoolean(KEY_LEFT_LIVE, false)
            if (left) setLeftWhileWatchingLive(c, false)
            return left
        }
    }
}
