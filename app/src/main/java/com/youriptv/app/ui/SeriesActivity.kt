package com.youriptv.app.ui

import android.content.SharedPreferences
import android.os.Bundle
import android.os.Parcelable
import android.view.KeyEvent
import android.view.MotionEvent
import android.view.View
import android.view.ViewTreeObserver
import android.widget.Button
import android.widget.ImageView
import android.widget.ProgressBar
import android.widget.TextView
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.lifecycleScope
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.RecyclerView
import com.youriptv.app.R
import com.youriptv.app.YourIptvApp
import com.youriptv.app.data.Episode
import com.youriptv.app.data.ItemKind
import com.youriptv.app.data.MediaItem
import com.youriptv.app.data.SeriesDetails
import com.youriptv.app.data.SeriesNames
import com.youriptv.app.data.SourceType
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

/** Vertically stacked seasons, each with its own horizontal episode rail. */
class SeriesActivity : RemoteActivity() {
    private val app get() = YourIptvApp.instance
    private lateinit var series: MediaItem
    private lateinit var access: SeriesEntryAccess
    private val browse = SeriesBrowseState()
    private val focusRestore = SeriesFocusRestore()
    private lateinit var content: View
    private lateinit var seasonRows: RecyclerView
    private lateinit var rowsAdapter: SeriesSeasonAdapter
    private lateinit var favorite: Button
    private lateinit var retry: Button
    private lateinit var selected: TextView
    private lateinit var plot: TextView
    private lateinit var cover: ImageView
    private lateinit var backdrop: ImageView
    private lateinit var empty: TextView
    private lateinit var progress: ProgressBar
    private var details = SeriesDetails()
    private var openedStamp = 0
    private var authenticating = false
    private var promptGeneration = 0L
    private var inputGeneration = 0L
    private var loadJob: Job? = null
    private var loaded = false
    private var entered = false
    private var restoredOuterState: Parcelable? = null
    private var foreground = false
    private var focusPosted = false
    private val restoreFocus = Runnable { focusPosted = false; attemptFocusRestore() }
    private val layoutReady = ViewTreeObserver.OnGlobalLayoutListener { scheduleFocusRestore() }
    private val restrictionListener = SharedPreferences.OnSharedPreferenceChangeListener { _, _ ->
        runOnUiThread {
            if (::series.isInitialized && !isFinishing && !allowed()) {
                hideProtectedContent()
                if (lifecycle.currentState.isAtLeast(Lifecycle.State.RESUMED)) authenticateEntry()
            }
        }
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        if (!app.isTrustedBuild) { finish(); return }
        setContentView(R.layout.series_activity)
        series = MediaItem(
            playlistId = intent.getLongExtra(EXTRA_PLAYLIST, 0L), kind = ItemKind.SERIES,
            name = intent.getStringExtra(EXTRA_NAME).orEmpty(), group = intent.getStringExtra(EXTRA_GROUP).orEmpty(),
            logo = intent.getStringExtra(EXTRA_LOGO).orEmpty(), url = intent.getStringExtra(EXTRA_URL).orEmpty(),
            remoteId = intent.getStringExtra(EXTRA_REMOTE_ID).orEmpty(),
        )
        val initialStamp = if (intent.hasExtra(EXTRA_AUTHORIZATION_STAMP)) intent.getIntExtra(EXTRA_AUTHORIZATION_STAMP, 0) else null
        access = SeriesEntryAccess(initialStamp, series.group, recreated = savedInstanceState != null)
        content = findViewById(R.id.series_content)
        content.isSaveFromParentEnabled = false
        seasonRows = findViewById(R.id.series_seasons)
        selected = findViewById(R.id.series_selected)
        plot = findViewById(R.id.series_plot)
        cover = findViewById(R.id.series_cover)
        backdrop = findViewById(R.id.series_backdrop)
        empty = findViewById(R.id.series_empty)
        progress = findViewById(R.id.series_progress)
        favorite = findViewById(R.id.series_favorite)
        retry = findViewById(R.id.series_retry)
        seasonRows.layoutManager = LinearLayoutManager(this)
        seasonRows.itemAnimator = null
        rowsAdapter = SeriesSeasonAdapter(browse, ::showSelection, ::play, ::episodeOptions, ::moveSeason,
            ::onEpisodeFocused, ::scheduleFocusRestore)
        seasonRows.adapter = rowsAdapter
        seasonRows.viewTreeObserver.addOnGlobalLayoutListener(layoutReady)
        seasonRows.addOnChildAttachStateChangeListener(object : RecyclerView.OnChildAttachStateChangeListener {
            override fun onChildViewAttachedToWindow(view: View) = scheduleFocusRestore()
            override fun onChildViewDetachedFromWindow(view: View) = Unit
        })
        favorite.setOnClickListener { withSeriesPermission { toggleFavorite() } }
        retry.setOnClickListener { withSeriesPermission { load() } }
        listOf(favorite, retry).forEach { button ->
            button.setOnKeyListener { _, key, event ->
                if (event.action == KeyEvent.ACTION_DOWN && key == KeyEvent.KEYCODE_DPAD_DOWN && rowsAdapter.rows.isNotEmpty()) {
                    focusSeason(rowsAdapter.rows[browse.rowFor(rowsAdapter.rows)].season)
                    true
                } else false
            }
        }
        restoreNavigation(savedInstanceState)
        hideProtectedContent()
        app.settings.addListener(restrictionListener)
    }

    override fun onRestoreInstanceState(savedInstanceState: Bundle) {
        super.onRestoreInstanceState(Bundle(savedInstanceState).apply { remove("android:viewHierarchyState") })
    }

    override fun onResume() {
        super.onResume()
        foreground = true
        if (!::series.isInitialized || isFinishing) return
        refreshCatalogItem()
        if (!allowed()) authenticateEntry()
        else {
            content.visibility = View.VISIBLE
            backdrop.visibility = View.VISIBLE
            updateFavorite()
            scheduleFocusRestore()
        }
    }

    override fun onPause() {
        foreground = false
        stopFocusAttempt()
        focusRestore.paused()
        saveVisibleRails()
        if (authenticating) {
            promptGeneration++
            authenticating = false
            if (!isChangingConfigurations) finish()
        }
        super.onPause()
    }

    override fun onDestroy() {
        stopFocusAttempt()
        if (::seasonRows.isInitialized) seasonRows.viewTreeObserver.let { observer ->
            if (observer.isAlive) observer.removeOnGlobalLayoutListener(layoutReady)
        }
        if (::series.isInitialized) app.settings.removeListener(restrictionListener)
        if (::cover.isInitialized) { ImageLoader.clear(cover); ImageLoader.clear(backdrop) }
        super.onDestroy()
    }

    override fun onWindowFocusChanged(hasFocus: Boolean) {
        super.onWindowFocusChanged(hasFocus)
        if (hasFocus) scheduleFocusRestore()
        else { stopFocusAttempt(); focusRestore.suspendAttempts() }
    }

    override fun dispatchTouchEvent(event: MotionEvent): Boolean {
        if (event.actionMasked == MotionEvent.ACTION_DOWN) noteUserInput()
        return super.dispatchTouchEvent(event)
    }

    override fun dispatchAppKeyEvent(event: KeyEvent): Boolean {
        if (event.action == KeyEvent.ACTION_DOWN) noteUserInput()
        return super.dispatchAppKeyEvent(event)
    }

    private fun noteUserInput() {
        inputGeneration++
        stopFocusAttempt()
        focusRestore.userInput()
    }

    private fun refreshCatalogItem() {
        app.repo.itemByUrl(series.playlistId, series.url)?.takeIf { it.kind == ItemKind.SERIES }?.let { series = it }
    }

    private fun allowed(): Boolean = entered && ::access.isInitialized && access.allows(
        app.settings.restrictionStamp(), series.group, app.settings.isGroupLocked(series.group), app.settings.isGroupHidden(series.group),
    )

    private fun hideProtectedContent() {
        if (::content.isInitialized) content.visibility = View.INVISIBLE
        if (::backdrop.isInitialized) backdrop.visibility = View.INVISIBLE
    }

    private fun authenticateEntry(after: (() -> Unit)? = null) {
        if (authenticating || isFinishing) return
        refreshCatalogItem()
        val settings = app.settings
        val group = series.group
        if (settings.isGroupHidden(group)) { finish(); return }
        val stamp = settings.restrictionStamp()
        if (access.enter(stamp, group, settings.isGroupLocked(group), hidden = false)) {
            onAuthorized(stamp)
            after?.invoke()
            return
        }
        hideProtectedContent()
        authenticating = true
        val generation = ++promptGeneration
        Dialogs.requirePin(this, "\"$group\" is locked", onCancel = {
            if (generation == promptGeneration) { authenticating = false; finish() }
        }) {
            if (generation != promptGeneration || isFinishing) return@requirePin
            authenticating = false
            refreshCatalogItem()
            if (settings.isGroupHidden(series.group)) { finish(); return@requirePin }
            if (settings.restrictionStamp() != stamp || series.group != group) { authenticateEntry(after); return@requirePin }
            access.authorize(stamp, group)
            onAuthorized(stamp)
            after?.invoke()
        }
    }

    private fun onAuthorized(stamp: Int) {
        entered = true
        openedStamp = stamp
        content.visibility = View.VISIBLE
        backdrop.visibility = View.VISIBLE
        findViewById<TextView>(R.id.series_title).text = series.name
        updateFavorite()
        if (!loaded && loadJob?.isActive != true) load()
        else if (loaded) renderDetails()
    }

    private fun withSeriesPermission(action: () -> Unit) {
        if (!foreground || isFinishing) return
        refreshCatalogItem()
        if (allowed()) action() else authenticateEntry(action)
    }

    private fun updateFavorite() {
        favorite.text = if (app.repo.isFavorite(series)) "Remove from favorites" else "Add to favorites"
    }

    private fun toggleFavorite() {
        app.repo.setFavorite(series, !app.repo.isFavorite(series))
        updateFavorite()
    }

    private fun load() {
        if (!allowed() || loadJob?.isActive == true) return
        val playlist = app.repo.playlist(series.playlistId) ?: run { showError("Playlist not found."); return }
        progress.visibility = View.VISIBLE
        empty.visibility = View.GONE
        retry.visibility = View.GONE
        plot.text = "Loading episodes…"
        ImageLoader.load(cover, series.logo, R.drawable.series_episode_placeholder, maxPx = 320)
        val inputAtStart = inputGeneration
        loadJob = lifecycleScope.launch {
            val result = withContext(Dispatchers.IO) {
                try {
                    Result.success(if (playlist.type == SourceType.XTREAM && !series.url.startsWith(SeriesNames.M3U_SERIES_PREFIX)) {
                        app.repo.seriesDetails(playlist, series)
                    } else SeriesDetails(episodes = m3uEpisodes(), cover = series.logo))
                } catch (e: CancellationException) { throw e }
                catch (e: Exception) { Result.failure(e) }
            }
            progress.visibility = View.GONE
            result.onSuccess {
                details = it
                loaded = true
                if (allowed()) renderDetails(takeFocus = inputGeneration == inputAtStart)
            }.onFailure {
                if (allowed()) showError("Could not load episodes. Check your connection and try again.",
                    takeFocus = inputGeneration == inputAtStart)
            }
        }
    }

    private fun m3uEpisodes(): List<Episode> {
        val key = series.name.lowercase()
        return app.repo.items(series.playlistId, ItemKind.EPISODE, series.group.ifBlank { null })
            .mapNotNull { item ->
                val parsed = SeriesNames.parse(item.name)
                if (parsed.series.lowercase() != key) null
                else Episode(id = item.url, season = parsed.season, episode = parsed.episode, title = item.name, url = item.url, artwork = item.logo)
            }.sortedWith(compareBy({ it.season }, { it.episode }))
    }

    private fun renderDetails(takeFocus: Boolean = true) {
        if (!allowed()) return
        val rows = SeriesBrowseState.rows(details.episodes)
        // Episode cards are wide; use the series backdrop before cropping a portrait cover as their fallback.
        rowsAdapter.submit(rows, details.backdrop.ifBlank { details.cover.ifBlank { series.logo } })
        ImageLoader.load(cover, details.cover.ifBlank { series.logo }, R.drawable.series_episode_placeholder, maxPx = 320)
        if (details.backdrop.isBlank()) ImageLoader.clear(backdrop)
        else ImageLoader.load(backdrop, details.backdrop, R.drawable.series_episode_placeholder, maxPx = 1280)
        plot.text = details.plot.ifBlank { series.group }
        empty.visibility = if (rows.isEmpty()) View.VISIBLE else View.GONE
        empty.text = "The provider returned no episodes for this series."
        restoredOuterState?.let { seasonRows.layoutManager?.onRestoreInstanceState(it) }
        restoredOuterState = null
        if (takeFocus) {
            if (rows.isNotEmpty()) focusSeason(rows[browse.rowFor(rows)].season)
            else favorite.requestFocus()
        }
    }

    private fun showError(message: String, takeFocus: Boolean = true) {
        progress.visibility = View.GONE
        empty.text = message
        empty.visibility = View.VISIBLE
        plot.text = series.group
        retry.visibility = View.VISIBLE
        if (takeFocus && window.decorView.hasWindowFocus()) retry.requestFocus()
    }

    private fun showSelection(row: SeriesSeasonRow, index: Int) {
        if (!allowed()) return
        val episode = row.episodes.getOrNull(index) ?: return
        selected.text = "Season ${row.season} · Episode ${episode.episode} · ${episode.title}"
        plot.text = episode.plot.ifBlank { details.plot.ifBlank { series.group } }
    }

    private fun onEpisodeFocused(row: SeriesSeasonRow, index: Int) {
        if (foreground && allowed() && focusRestore.rememberFocus(browse, row, index)) showSelection(row, index)
    }

    private fun moveSeason(season: Int, delta: Int) {
        if (!allowed()) return
        val next = browse.adjacent(rowsAdapter.rows, season, delta)
        if (next != null) focusSeason(next.season)
        else if (delta < 0) favorite.requestFocus()
    }

    private fun focusSeason(season: Int) {
        val rowIndex = rowsAdapter.rows.indexOfFirst { it.season == season }
        val row = rowsAdapter.rows.getOrNull(rowIndex) ?: return
        val episodeIndex = browse.indexFor(row).coerceAtLeast(0)
        beginFocusRestore(row, episodeIndex)
        scheduleFocusRestore()
    }

    private fun beginFocusRestore(row: SeriesSeasonRow, index: Int, waitForPlaybackPause: Boolean = false) {
        val selection = SeriesBrowseState.selection(row, index) ?: return
        stopFocusAttempt()
        browse.remember(row, index)
        focusRestore.request(selection, waitForPause = waitForPlaybackPause)
        showSelection(row, index)
    }

    private fun canRestoreFocus(): Boolean =
        foreground && ::seasonRows.isInitialized && ::access.isInitialized && allowed() && !isFinishing && !isDestroyed &&
            lifecycle.currentState.isAtLeast(Lifecycle.State.STARTED) && window.decorView.hasWindowFocus() &&
            seasonRows.isAttachedToWindow && seasonRows.isShown && seasonRows.windowVisibility == View.VISIBLE &&
            seasonRows.width > 0 && seasonRows.height > 0

    private fun scheduleFocusRestore() {
        if (focusPosted || !focusRestore.canAttempt || !canRestoreFocus()) return
        focusPosted = true
        seasonRows.postOnAnimation(restoreFocus)
    }

    private fun stopFocusAttempt() {
        if (::seasonRows.isInitialized) seasonRows.removeCallbacks(restoreFocus)
        focusPosted = false
    }

    /** Retry on window/layout/child readiness, never consume an early or failed requestFocus. */
    private fun attemptFocusRestore() {
        val ticket = focusRestore.pending ?: return
        if (!focusRestore.canAttempt || !canRestoreFocus() || seasonRows.isComputingLayout || seasonRows.isLayoutRequested) return
        val rowIndex = rowsAdapter.rows.indexOfFirst { it.season == ticket.target.season }
        val row = rowsAdapter.rows.getOrNull(rowIndex) ?: return
        val index = SeriesBrowseState.indexOf(row, ticket.target)
        if (index < 0) return
        val holder = seasonRows.findViewHolderForAdapterPosition(rowIndex) as? SeriesSeasonAdapter.SeasonHolder
        if (holder == null || holder.season != row.season || !holder.itemView.isLaidOut) {
            seasonRows.scrollToPosition(rowIndex)
            seasonRows.requestLayout()
            return // Attachment/global layout schedules the next attempt, even at unchanged bounds.
        }
        val rail = holder.rail
        if (rail.isComputingLayout || rail.isLayoutRequested || !rail.isLaidOut) return
        val target = rail.findViewHolderForAdapterPosition(index)?.itemView
        if (target == null || !target.isLaidOut) {
            rail.scrollToPosition(index)
            rail.requestLayout()
            return // Attachment/global layout schedules the next attempt, even at unchanged bounds.
        }
        val focused = target.isFocused || target.requestFocus() || (target.isInTouchMode && target.requestFocusFromTouch())
        if (focusRestore.complete(ticket, focused && target.isFocused)) {
            browse.remember(row, index)
            showSelection(row, index)
        }
    }

    private fun episodeOptions(row: SeriesSeasonRow, index: Int) {
        withSeriesPermission {
            val episode = row.episodes.getOrNull(index) ?: return@withSeriesPermission
            browse.remember(row, index)
            Dialogs.choose(this, episode.title, listOf("Play", "Play in external player")) { choice ->
                if (choice == 0) play(row, index) else externalEpisode(row, index)
            }
        }
    }

    private fun play(row: SeriesSeasonRow, index: Int) {
        withSeriesPermission {
            if (index !in row.episodes.indices) return@withSeriesPermission
            beginFocusRestore(row, index, waitForPlaybackPause = true)
            val queue = SeriesBrowseState.playbackQueue(series, row)
            val unlocked = series.group.takeIf { app.settings.isGroupLocked(it) && app.settings.restrictionStamp() == openedStamp }
            Nav.play(this, queue, index, unlocked)
        }
    }

    private fun externalEpisode(row: SeriesSeasonRow, index: Int) {
        val episode = row.episodes.getOrNull(index) ?: return
        refreshCatalogItem()
        val group = series.group
        if (app.settings.isGroupHidden(group)) { finish(); return }
        val openExternal = {
            if (Scheduler.recordingUsesOnlyConnection(series.playlistId)) {
                Dialogs.toast(this, "This provider's only connection is being used by a recording.")
            } else {
                beginFocusRestore(row, index, waitForPlaybackPause = true)
                Nav.externalPlayer(this, episode.url, episode.title)
            }
        }
        if (app.settings.isGroupLocked(group) && app.settings.restrictionStamp() != openedStamp) {
            authenticateEntry { if (allowed()) openExternal() }
        } else withSeriesPermission(openExternal)
    }

    private fun saveVisibleRails() {
        if (!::seasonRows.isInitialized) return
        for (index in 0 until seasonRows.childCount) {
            (seasonRows.getChildViewHolder(seasonRows.getChildAt(index)) as? SeriesSeasonAdapter.SeasonHolder)?.let(rowsAdapter::saveRail)
        }
    }

    override fun onSaveInstanceState(outState: Bundle) {
        if (!::seasonRows.isInitialized) { super.onSaveInstanceState(outState); return }
        saveVisibleRails()
        val selections = browse.snapshot()
        outState.putIntArray(STATE_SEASONS, selections.map { it.season }.toIntArray())
        outState.putStringArray(STATE_EPISODES, selections.map { it.episodeKey }.toTypedArray())
        outState.putIntArray(STATE_INDICES, selections.map { it.index }.toIntArray())
        browse.focusedSeason?.let { outState.putInt(STATE_FOCUSED_SEASON, it) }
        outState.putParcelable(STATE_OUTER, seasonRows.layoutManager?.onSaveInstanceState())
        outState.putBundle(STATE_RAILS, Bundle().apply { rowsAdapter.railStates.forEach { (season, value) -> putParcelable(season.toString(), value) } })
        super.onSaveInstanceState(outState)
        outState.remove("android:viewHierarchyState")
    }

    private fun restoreNavigation(saved: Bundle?) {
        if (saved == null) return
        val seasons = saved.getIntArray(STATE_SEASONS) ?: IntArray(0)
        val keys = saved.getStringArray(STATE_EPISODES).orEmpty()
        val indices = saved.getIntArray(STATE_INDICES) ?: IntArray(0)
        browse.restore((0 until minOf(seasons.size, keys.size, indices.size)).map { SeriesBrowseState.Selection(seasons[it], keys[it], indices[it]) },
            if (saved.containsKey(STATE_FOCUSED_SEASON)) saved.getInt(STATE_FOCUSED_SEASON) else null)
        @Suppress("DEPRECATION")
        restoredOuterState = saved.getParcelable(STATE_OUTER)
        saved.getBundle(STATE_RAILS)?.let { rails ->
            rails.keySet().forEach { key ->
                @Suppress("DEPRECATION")
                val value: Parcelable? = rails.getParcelable(key)
                val season = key.toIntOrNull()
                if (season != null && value != null) rowsAdapter.railStates[season] = value
            }
        }
    }

    companion object {
        const val EXTRA_PLAYLIST = "playlist"
        const val EXTRA_URL = "url"
        const val EXTRA_NAME = "name"
        const val EXTRA_REMOTE_ID = "remote_id"
        const val EXTRA_GROUP = "group"
        const val EXTRA_LOGO = "logo"
        const val EXTRA_AUTHORIZATION_STAMP = "authorization_stamp"
        private const val STATE_SEASONS = "series_season_selections"
        private const val STATE_EPISODES = "series_episode_selections"
        private const val STATE_INDICES = "series_episode_indices"
        private const val STATE_FOCUSED_SEASON = "series_focused_season"
        private const val STATE_OUTER = "series_outer_layout"
        private const val STATE_RAILS = "series_rail_layouts"
    }
}
