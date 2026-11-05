package com.youriptv.app.ui

import android.content.Intent
import android.os.Bundle
import android.os.Handler
import android.os.Looper
import android.text.Editable
import android.text.TextWatcher
import android.view.KeyEvent
import android.view.MotionEvent
import android.view.View
import android.view.ViewGroup
import android.view.inputmethod.EditorInfo
import android.view.inputmethod.InputMethodManager
import android.widget.Button
import android.widget.EditText
import android.widget.LinearLayout
import android.widget.ProgressBar
import android.widget.TextView
import androidx.activity.OnBackPressedCallback
import android.transition.ChangeBounds
import android.transition.TransitionManager
import androidx.appcompat.app.AppCompatActivity
import androidx.core.view.doOnNextLayout
import androidx.core.widget.doAfterTextChanged
import androidx.lifecycle.lifecycleScope
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.RecyclerView
import com.youriptv.app.R
import com.youriptv.app.SigningStatus
import com.youriptv.app.YourIptvApp
import com.youriptv.app.data.GroupInfo
import com.youriptv.app.data.ItemKind
import com.youriptv.app.data.MediaItem
import com.youriptv.app.data.PinKind
import com.youriptv.app.data.Playlist
import com.youriptv.app.data.Recorder
import com.youriptv.app.data.Recording
import com.youriptv.app.data.RecordingStatus
import com.youriptv.app.data.Reminder
import com.youriptv.app.data.SeriesNames
import com.youriptv.app.data.SourceType
import java.io.File
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

class MainActivity : AppCompatActivity() {

    enum class Section(val label: String) {
        SEARCH("Search"), TV("TV"), MOVIES("Movies"), SHOWS("Shows"),
        FAVORITES("Favorites"), HISTORY("History"), RECORDINGS("Recordings"), PLAYLISTS("Playlists"), SETTINGS("Settings"),
    }

    private data class GroupEntry(val info: GroupInfo, val special: String? = null)

    /**
     * Live TV on a TV screen slides between three levels, as in TiviMate: MENU (rail, categories and guide side by side),
     * CATEGORIES (rail folded away, the guide follows the focused category) and GUIDE (the guide fills the screen).
     * BACK climbs GUIDE -> CATEGORIES -> MENU. Other sections always use MENU.
     */
    enum class LiveLevel { MENU, CATEGORIES, GUIDE }

    private val app get() = YourIptvApp.instance
    private var playlist: Playlist? = null
    private var section = Section.TV
    private var groupEntries: List<GroupEntry> = emptyList()
    private var selectedGroup = 0
    private var loadJob: Job? = null
    private var searchJob: Job? = null
    private val navViews = HashMap<Section, TextView>()

    private lateinit var groupsView: RecyclerView
    private lateinit var itemsView: RecyclerView
    private lateinit var title: TextView
    private lateinit var status: TextView
    private lateinit var searchBox: EditText
    private lateinit var stateBox: View
    private lateinit var stateText: TextView
    private lateinit var stateProgress: ProgressBar
    private lateinit var stateAction: Button
    private lateinit var clock: TextView
    private lateinit var liveHeader: View
    private lateinit var liveTimeline: GuideTimelineView
    private lateinit var liveGrid: LiveGuideGrid
    private lateinit var previewPanel: PreviewPanel
    private var previewAttached = false
    private var portrait = false
    private var liveLevel = LiveLevel.MENU
    private var root: ViewGroup? = null
    private var navRail: View? = null
    private var contentColumn: View? = null
    /** Section heading shown above the channel details while the guide fills the screen without the page header. */
    private var liveHeading: TextView? = null
    /** Category whose channels the guide currently shows; -1 while loading or while a locked category waits for its PIN. */
    private var loadedGroup = -1
    private var focusedGroup = -1
    private val loadFocusedGroup = Runnable { showFocusedGroup() }
    /** Category name and channel URL to open in the guide once the TV categories have loaded. */
    private var pendingGuide: Pair<String?, String?>? = null
    private var returningFromPreviewPlayer = false
    private val previewObserver = {
        previewPanel.render(app.livePlayback)
        liveGrid.setPlaying(app.livePlayback.current)
    }
    private val handler = Handler(Looper.getMainLooper())
    private val clockTick = object : Runnable {
        override fun run() {
            clock.text = Format.clock()
            if (section == Section.TV) liveGrid.refresh()
            handler.postDelayed(this, 20_000)
        }
    }

    private lateinit var groupAdapter: GroupAdapter
    private lateinit var itemAdapter: ItemAdapter

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        if (!app.isTrustedBuild) {
            setContentView(R.layout.activity_untrusted_signer)
            findViewById<TextView>(R.id.signing_error_message).setText(
                when {
                    app.brandStatus != com.youriptv.app.BrandIntegrity.Result.OK -> R.string.integrity_rebrand_message
                    app.signingStatus == SigningStatus.UNTRUSTED -> R.string.signing_untrusted_message
                    else -> R.string.signing_unavailable_message
                }
            )
            findViewById<Button>(R.id.signing_error_close).apply {
                setOnClickListener { finish() }
                requestFocus()
            }
            return
        }
        setContentView(R.layout.activity_main)
        portrait = resources.getBoolean(R.bool.is_portrait)
        root = findViewById(R.id.main_root)
        navRail = findViewById(R.id.nav_rail)
        contentColumn = findViewById(R.id.content_column)
        groupsView = findViewById(R.id.groups)
        itemsView = findViewById(R.id.items)
        title = findViewById(R.id.section_title)
        status = findViewById(R.id.status)
        liveHeading = findViewById(R.id.live_heading)
        val syncHeading = { _: android.text.Editable? -> updateLiveHeading() }
        title.doAfterTextChanged(syncHeading)
        status.doAfterTextChanged(syncHeading)
        searchBox = findViewById(R.id.search_box)
        stateBox = findViewById(R.id.state_container)
        stateText = findViewById(R.id.state_text)
        stateProgress = findViewById(R.id.progress)
        stateAction = findViewById(R.id.state_action)
        clock = findViewById(R.id.clock)
        liveHeader = findViewById(R.id.live_header)
        liveTimeline = findViewById(R.id.live_timeline)
        previewPanel = PreviewPanel(findViewById(R.id.preview_frame))

        groupAdapter = GroupAdapter(
            portrait,
            { _, pos -> if (tvLevels()) openGroupGuide(pos) else selectGroup(pos, focusItems = true) },
            { g, pos -> groupOptions(groupEntries[pos], g) },
            { pos -> onGroupFocused(pos) },
        )
        groupsView.layoutManager = LinearLayoutManager(this, if (portrait) RecyclerView.HORIZONTAL else RecyclerView.VERTICAL, false)
        groupsView.adapter = groupAdapter
        itemAdapter = ItemAdapter(
            { item, _ -> if (section == Section.RECORDINGS) recordingClick(item) else Nav.open(this, item, itemAdapter.items) },
            { item, _ -> if (section == Section.RECORDINGS) recordingOptions(item) else itemOptions(item) },
        )
        itemsView.layoutManager = LinearLayoutManager(this)
        itemsView.adapter = itemAdapter
        itemsView.setHasFixedSize(true)
        liveGrid = LiveGuideGrid(
            this, itemsView, liveTimeline, findViewById(R.id.live_title), findViewById(R.id.live_meta),
            findViewById(R.id.live_desc),
            { item, channels ->
                app.livePlayback.select(this, item, channels) {
                    returningFromPreviewPlayer = Nav.fullscreenPreview(this)
                }
            },
            { item -> itemOptionList(item) },
            { showCategories() },
        )

        buildNav(findViewById(R.id.nav_container), portrait)
        setupSearch()

        if (!portrait) window.decorView.viewTreeObserver.addOnGlobalFocusChangeListener { _, now -> followFocus(now) }

        onBackPressedDispatcher.addCallback(this, object : OnBackPressedCallback(true) {
            override fun handleOnBackPressed() {
                val f = currentFocus
                when {
                    // Guide: first back to "now" if browsing other times, then to the categories; categories: to the menu.
                    section == Section.TV && playlist != null -> when (liveLevel) {
                        LiveLevel.GUIDE -> if (!liveGrid.isAtNow()) liveGrid.jumpToNow() else showCategories()
                        LiveLevel.CATEGORIES -> showMenu()
                        LiveLevel.MENU -> confirmExit()
                    }
                    f != null && isInside(f, itemsView) && groupsView.visibility == View.VISIBLE && groupAdapter.itemCount > 0 -> focusGroups()
                    f != null && (isInside(f, itemsView) || isInside(f, groupsView) || f === searchBox) -> navViews[section]?.requestFocus()
                    f == null || navViews.values.none { it === f } -> { if (navViews[section]?.requestFocus() != true) confirmExit() }
                    else -> confirmExit()
                }
            }
        })

        if (savedInstanceState == null) {
            PlayerActivity.takeLeftWhileWatchingLive(this)
            if (app.settings.resumeLastChannel) pendingResume = true
        }
        takeGuideRequest(intent)
    }

    // Launchers such as Projectivity relaunch with an intent that differs from the task's root intent, so singleTask
    // removes the player above this screen. Restart the live channel that was playing, as a cold start would.
    override fun onNewIntent(intent: Intent) {
        super.onNewIntent(intent)
        setIntent(intent)
        if (!app.isTrustedBuild) return
        if (intent.action == Intent.ACTION_MAIN && PlayerActivity.takeLeftWhileWatchingLive(this) &&
            app.settings.resumeLastChannel) pendingResume = true
        takeGuideRequest(intent)
    }

    /** The player's "TV guide" action reopens Live TV at the playing channel's category and row. */
    private fun takeGuideRequest(intent: Intent) {
        val url = intent.getStringExtra(EXTRA_GUIDE_CHANNEL) ?: return
        val group = intent.getStringExtra(EXTRA_GUIDE_GROUP)
        intent.removeExtra(EXTRA_GUIDE_CHANNEL)
        intent.removeExtra(EXTRA_GUIDE_GROUP)
        openGuide(group, url)
    }

    private var pendingResume = false
    private var lastBackAt = 0L
    private var shownStamp: Int? = null
    private var userInputGeneration = 0L

    override fun dispatchTouchEvent(event: MotionEvent): Boolean {
        if (event.actionMasked == MotionEvent.ACTION_DOWN) userInputGeneration++
        return super.dispatchTouchEvent(event)
    }

    // Entering the nav rail with LEFT lands on the active section, not the geometrically nearest item.
    override fun dispatchKeyEvent(event: KeyEvent): Boolean {
        if (!app.isTrustedBuild) return super.dispatchKeyEvent(event)
        if (event.action == KeyEvent.ACTION_DOWN) userInputGeneration++
        val focused = currentFocus
        if (section == Section.TV && focused != null && isInside(focused, itemsView) && liveGrid.handleKey(event)) {
            return true
        }
        if (tvLevels() && focused != null && event.action == KeyEvent.ACTION_DOWN && tvLevelKey(event, focused)) return true
        if (section == Section.TV && event.action == KeyEvent.ACTION_DOWN && event.keyCode == KeyEvent.KEYCODE_DPAD_RIGHT &&
            focused != null && isInside(focused, groupsView) && itemAdapter.itemCount > 0) {
            focusItemAt(liveGrid.focusedRow)
            return true
        }
        if (event.action == KeyEvent.ACTION_DOWN && event.keyCode == KeyEvent.KEYCODE_DPAD_LEFT &&
            !resources.getBoolean(R.bool.is_portrait)) {
            val f = currentFocus
            if (f != null && f !is EditText && navViews.values.none { it === f }) {
                val next = f.focusSearch(View.FOCUS_LEFT)
                if (next != null && navViews.values.any { it === next }) {
                    navViews[section]?.let { if (it.requestFocus()) return true }
                }
                // Likewise, leaving the item list lands on the selected group rather than the nearest row.
                if (next != null && groupsView.visibility == View.VISIBLE && isInside(next, groupsView) && !isInside(f, groupsView)) {
                    focusGroups(); return true
                }
            }
        }
        // UP/DOWN at either end of the groups or items list stays there instead of jumping into the nav rail.
        if (event.action == KeyEvent.ACTION_DOWN && !resources.getBoolean(R.bool.is_portrait) &&
            (event.keyCode == KeyEvent.KEYCODE_DPAD_DOWN || event.keyCode == KeyEvent.KEYCODE_DPAD_UP)) {
            val f = currentFocus
            if (f != null && (isInside(f, groupsView) || isInside(f, itemsView))) {
                val next = f.focusSearch(if (event.keyCode == KeyEvent.KEYCODE_DPAD_DOWN) View.FOCUS_DOWN else View.FOCUS_UP)
                if (next != null && navViews.values.any { it === next }) return true
            }
        }
        return super.dispatchKeyEvent(event)
    }

    // ---------------- Live TV levels (menu / categories / guide) ----------------

    private fun tvLevels() = section == Section.TV && !portrait && playlist != null

    private fun tvLevelKey(event: KeyEvent, focused: View): Boolean {
        val inGroups = isInside(focused, groupsView)
        when (event.keyCode) {
            KeyEvent.KEYCODE_DPAD_LEFT -> if (inGroups) { showMenu(); return true }
            KeyEvent.KEYCODE_DPAD_RIGHT -> {
                if (focused === navViews[Section.TV] && groupEntries.isNotEmpty()) { showCategories(); return true }
                if (inGroups) {
                    groupsView.findContainingViewHolder(focused)?.bindingAdapterPosition
                        ?.takeIf { it != RecyclerView.NO_POSITION }?.let { openGroupGuide(it) }
                    return true
                }
            }
            // The category list wraps around at either end, as TiviMate's does.
            KeyEvent.KEYCODE_DPAD_UP, KeyEvent.KEYCODE_DPAD_DOWN -> if (inGroups && groupAdapter.itemCount > 1) {
                val pos = groupsView.findContainingViewHolder(focused)?.bindingAdapterPosition ?: return false
                val last = groupAdapter.itemCount - 1
                val up = event.keyCode == KeyEvent.KEYCODE_DPAD_UP
                if ((up && pos == 0) || (!up && pos == last)) {
                    if (event.repeatCount == 0) focusGroupAt(if (up) last else 0)
                    return true
                }
            }
        }
        return false
    }

    /** Keeps the visible level in step with wherever focus lands (remote, touch, or a list reload). */
    private fun followFocus(view: View?) {
        if (view == null || !tvLevels()) return
        when {
            navViews.values.any { it === view } -> setLiveLevel(LiveLevel.MENU)
            isInside(view, groupsView) -> setLiveLevel(LiveLevel.CATEGORIES)
            isInside(view, itemsView) -> setLiveLevel(LiveLevel.GUIDE)
        }
    }

    private fun setLiveLevel(level: LiveLevel) {
        val target = if (section == Section.TV) level else LiveLevel.MENU
        if (target == liveLevel) return
        liveLevel = target
        if (portrait) return
        root?.let { TransitionManager.beginDelayedTransition(it, ChangeBounds().setDuration(220)) }
        navRail?.visibility = if (target == LiveLevel.MENU) View.VISIBLE else View.GONE
        sizeContentColumn()
        if (section == Section.TV) groupsView.visibility = if (target == LiveLevel.GUIDE) View.GONE else View.VISIBLE
        // Like TiviMate, the categories and guide drop the page header; its heading moves beside the fixed-size preview.
        val headerVisibility = if (target == LiveLevel.MENU) View.VISIBLE else View.GONE
        title.visibility = headerVisibility
        status.visibility = headerVisibility
        updateLiveHeading()
    }

    /**
     * In the categories and guide levels the guide keeps its full-screen size, so opening the categories slides it
     * right (partly off screen) as in TiviMate instead of squeezing the timeline. The menu level fits the space left.
     */
    private fun sizeContentColumn() {
        val column = contentColumn ?: return
        val lp = column.layoutParams as? LinearLayout.LayoutParams ?: return
        val fixed = section == Section.TV && liveLevel != LiveLevel.MENU
        val width = if (fixed) root?.width?.takeIf { it > 0 } ?: resources.displayMetrics.widthPixels else 0
        val weight = if (fixed) 0f else 1f
        if (lp.width != width || lp.weight != weight) {
            lp.width = width
            lp.weight = weight
            column.layoutParams = lp
        }
    }

    private fun updateLiveHeading() {
        val heading = liveHeading ?: return
        heading.text = listOf(title.text, status.text).filter { it.isNotBlank() }.joinToString("  ·  ")
        heading.visibility = if (liveLevel != LiveLevel.MENU && heading.text.isNotBlank()) View.VISIBLE else View.GONE
    }

    private fun showCategories() {
        if (groupEntries.isEmpty()) return
        setLiveLevel(LiveLevel.CATEGORIES)
        focusGroups()
    }

    private fun showMenu() {
        handler.removeCallbacks(loadFocusedGroup)
        setLiveLevel(LiveLevel.MENU)
        navViews[Section.TV]?.requestFocus()
    }

    private fun focusGroupAt(pos: Int) {
        val lm = groupsView.layoutManager as LinearLayoutManager
        val v = lm.findViewByPosition(pos)
        if (v != null && v.isLaidOut && !groupsView.isLayoutRequested && v.requestFocus()) return
        // The list may have been hidden behind the guide since it was filled, so focus its row once it has been laid out.
        groupsView.scrollToPosition(pos)
        groupsView.doOnNextLayout { lm.findViewByPosition(pos)?.requestFocus() }
        groupsView.requestLayout()
    }

    /** Moving through the categories re-filters the guide beside them after a short pause; it never tunes a channel. */
    private fun onGroupFocused(pos: Int) {
        if (!tvLevels()) return
        handler.removeCallbacks(loadFocusedGroup)
        focusedGroup = pos
        if (pos != selectedGroup) handler.postDelayed(loadFocusedGroup, 250)
    }

    private fun showFocusedGroup() {
        val pos = focusedGroup
        if (!tvLevels() || pos == selectedGroup) return
        val entry = groupEntries.getOrNull(pos) ?: return
        if (entry.special == null && app.settings.isGroupLocked(entry.info.name)) showLockedGroup(pos, entry)
        else selectGroup(pos, focusItems = false)
    }

    /** A locked category only says so while browsing; its channel names appear after OK and the parental PIN. */
    private fun showLockedGroup(pos: Int, entry: GroupEntry) {
        loadJob?.cancel()
        selectedGroup = pos
        loadedGroup = -1
        groupAdapter.select(pos)
        title.text = "${Section.TV.label}  ·  ${entry.info.name}"
        itemAdapter.submit(emptyList(), emptySet())
        liveGrid.submit(emptyList(), emptySet(), 0)
        status.text = ""
        showState("\"${entry.info.name}\" is locked.\n\nPress OK and enter the parental PIN to open it.", progress = false)
    }

    /**
     * OK or RIGHT on a category slides to its guide at the first channel in playlist order, as in TiviMate.
     * Only Back from full screen ([openGuide]) returns to the playing channel.
     */
    private fun openGroupGuide(pos: Int) {
        handler.removeCallbacks(loadFocusedGroup)
        if (pos == loadedGroup && loadJob?.isActive != true && liveGrid.items.isNotEmpty()) focusItemAt(0)
        else selectGroup(pos, focusItems = true)
    }

    private fun guideGroupPos(group: String?): Int =
        groupEntries.indexOfFirst { it.special == null && it.info.name == group }.takeIf { it >= 0 }
            ?: groupEntries.indexOfFirst { it.special == SPECIAL_ALL }

    /** Opens the Live TV guide on [url] in [group] (or All channels when that category is hidden or missing). */
    private fun openGuide(group: String?, url: String?) {
        if (playlist == null) return
        if (section == Section.TV && loadJob?.isActive != true) {
            val idx = liveGrid.items.indexOfFirst { it.url == url }
            if (idx >= 0) { focusItemAt(idx); return }
            val pos = guideGroupPos(group)
            if (pos >= 0) { selectGroup(pos, focusItems = true, focusUrl = url); return }
        }
        pendingGuide = group to url
        showSection(Section.TV)
    }

    private fun confirmExit() {
        val now = android.os.SystemClock.uptimeMillis()
        if (now - lastBackAt < 2500) finish()
        else { lastBackAt = now; Dialogs.toast(this, "Press BACK again to exit") }
    }

    override fun onStart() {
        super.onStart()
        if (app.isTrustedBuild && section == Section.TV) attachPreview()
    }

    override fun onStop() {
        detachPreview()
        super.onStop()
    }

    private fun attachPreview() {
        if (previewAttached) return
        previewAttached = true
        app.livePlayback.ownerStarted(this)
        app.livePlayback.observe(previewObserver)
        app.livePlayback.attachPreview(previewPanel.playerView)
        previewObserver()
    }

    private fun detachPreview() {
        if (!previewAttached) return
        previewAttached = false
        app.livePlayback.removeObserver(previewObserver)
        app.livePlayback.detach(previewPanel.playerView)
        app.livePlayback.ownerStopped(this)
    }

    override fun onResume() {
        super.onResume()
        if (!app.isTrustedBuild) return
        handler.post(clockTick)
        val active = app.repo.playlist(app.settings.activePlaylistId) ?: app.repo.playlists().firstOrNull()
        val changed = active?.id != playlist?.id || active?.lastUpdated != playlist?.lastUpdated
        playlist = active
        if (active != null) app.settings.activePlaylistId = active.id
        findViewById<TextView>(R.id.playlist_name).text = active?.name ?: "No playlist"
        updateSettingsLabel()
        // A changed restriction (PIN, locked groups, adult block, hiding) rebuilds the lists and drops the group unlocked earlier.
        val stamp = app.settings.restrictionStamp()
        val restrictionsChanged = shownStamp != null && stamp != shownStamp
        shownStamp = stamp
        if (restrictionsChanged) lastSection = null
        if (changed || restrictionsChanged || itemAdapter.itemCount == 0) showSection(section, requestFocus = itemAdapter.itemCount == 0)
        else if (section == Section.RECORDINGS) refreshRecordingsInPlace()
        else if (section == Section.TV) liveGrid.refreshSettings()
        else itemAdapter.notifyItemRangeChanged(0, itemAdapter.itemCount)
        val before = channelAtPause
        channelAtPause = null
        val lastUrl = app.settings.lastChannelUrl
        if (!returningFromPreviewPlayer && active != null && !changed && !restrictionsChanged &&
            (revealAfterResume || (before != null && before != lastUrl))) {
            revealChannel(active, lastUrl)
        }
        // BACK from full screen lands on the channel now playing, even after zapping there.
        if (returningFromPreviewPlayer && section == Section.TV && !changed && !restrictionsChanged) {
            val current = app.livePlayback.current
            val idx = liveGrid.items.indexOfFirst { current != null && it.playlistId == current.playlistId && it.url == current.url }
            if (idx >= 0 && (idx != liveGrid.focusedRow || currentFocus?.let { isInside(it, itemsView) } != true)) focusItemAt(idx)
        }
        returningFromPreviewPlayer = false
        revealAfterResume = false
        if (active != null) {
            maybeResumeLastChannel(active)
            maybeAutoRefresh(active)
        }
    }

    override fun onPause() {
        super.onPause()
        handler.removeCallbacks(clockTick)
        if (app.isTrustedBuild) channelAtPause = app.settings.lastChannelUrl
    }

    private var channelAtPause: String? = null
    private var revealAfterResume = false

    /** After returning from the player, puts focus on the channel that was last playing instead of the stale list position. */
    private fun revealChannel(p: Playlist, url: String) {
        if (url.isBlank()) return
        val idx = itemAdapter.items.indexOfFirst { it.url == url }
        if (idx >= 0) { focusItemAt(idx); return }
        if (section != Section.TV) return
        lifecycleScope.launch {
            val item = withContext(Dispatchers.IO) { app.repo.itemByUrl(p.id, url) } ?: return@launch
            val s = app.settings
            if (item.kind != ItemKind.LIVE || s.isGroupHidden(item.group) || s.isGroupLocked(item.group)) return@launch
            val pos = groupEntries.indexOfFirst { it.special == null && it.info.name == item.group }
            if (pos >= 0) selectGroup(pos, focusItems = true, focusUrl = url)
        }
    }

    private fun focusItemAt(idx: Int) {
        if (section == Section.TV) { setLiveLevel(LiveLevel.GUIDE); liveGrid.focus(idx); return }
        (itemsView.layoutManager as? LinearLayoutManager)?.scrollToPositionWithOffset(idx, 0)
        itemsView.post { itemsView.findViewHolderForAdapterPosition(idx)?.itemView?.requestFocus() }
    }

    // ---------------- Navigation rail ----------------

    private fun buildNav(container: LinearLayout, portrait: Boolean) {
        for (s in Section.entries) {
            val tv = TextView(this, null, 0, R.style.NavItem).apply {
                text = s.label
                id = View.generateViewId()
                layoutParams = LinearLayout.LayoutParams(
                    if (portrait) ViewGroup.LayoutParams.WRAP_CONTENT else ViewGroup.LayoutParams.MATCH_PARENT,
                    ViewGroup.LayoutParams.WRAP_CONTENT,
                ).apply { bottomMargin = 2; marginEnd = if (portrait) 4 else 0 }
                setOnClickListener { onNav(s) }
            }
            navViews[s] = tv
            container.addView(tv)
        }
        // Keep vertical rail focus from escaping into the group list at either end.
        if (!portrait) {
            navViews.getValue(Section.entries.first()).let { it.nextFocusUpId = it.id }
            navViews.getValue(Section.entries.last()).let { it.nextFocusDownId = it.id }
        }
    }

    private fun onNav(s: Section) {
        when (s) {
            Section.TV -> if (tvLevels() && groupEntries.isNotEmpty() && loadJob?.isActive != true) showCategories()
                else showSection(s, requestFocus = true)
            Section.PLAYLISTS -> openLocked(app.settings.isPlaylistsLocked(), "Playlists are locked", PlaylistsActivity::class.java)
            Section.SETTINGS -> unlockSettings()
            else -> showSection(s, requestFocus = true)
        }
    }

    /**
     * The rail's Settings item reads "Unlock Settings" while the Settings lock is on and always stays reachable with the remote.
     * Only the separate Settings PIN opens it, for one visit; the lock itself stays on until turned off inside Settings.
     */
    private fun unlockSettings() {
        val s = app.settings
        val open = { startActivity(Intent(this, SettingsActivity::class.java).putExtra(SettingsActivity.EXTRA_UNLOCKED, true)) }
        when {
            !s.isSettingsLocked() -> startActivity(Intent(this, SettingsActivity::class.java))
            s.isLegacySettingsLock() -> Dialogs.requirePin(this, "Settings are locked with the old shared PIN") {
                // One-time upgrade: the old shared PIN must be replaced by a separate Settings PIN (or the lock turned off).
                Dialogs.choose(this, "Settings lock now has its own PIN", listOf("Create a separate Settings PIN", "Turn off Settings lock")) { i ->
                    if (i == 0) Dialogs.createPin(this, PinKind.SETTINGS) { s.clearLegacySettingsLock(); updateSettingsLabel(); open() }
                    else { s.clearLegacySettingsLock(); updateSettingsLabel(); Dialogs.toast(this, "Settings lock turned off"); open() }
                }
            }
            else -> Dialogs.requireSettingsPin(this, "unlock Settings") { open() }
        }
    }

    private fun updateSettingsLabel() {
        navViews[Section.SETTINGS]?.text = if (app.settings.isSettingsLocked()) "Unlock Settings" else Section.SETTINGS.label
    }

    private fun openLocked(locked: Boolean, reason: String, target: Class<*>) {
        val go = { startActivity(Intent(this, target)) }
        if (locked) Dialogs.requirePin(this, reason) { go() } else go()
    }

    // ---------------- Sections ----------------

    private fun showSection(s: Section, requestFocus: Boolean = false) {
        if (section == Section.TV && s != Section.TV) detachPreview()
        handler.removeCallbacks(loadFocusedGroup)
        loadedGroup = -1
        section = s
        if (s != Section.TV) setLiveLevel(LiveLevel.MENU)
        if (s == Section.TV && lifecycle.currentState.isAtLeast(androidx.lifecycle.Lifecycle.State.STARTED)) attachPreview()
        val desiredAdapter = if (s == Section.TV) liveGrid.adapter else itemAdapter
        if (itemsView.adapter !== desiredAdapter) itemsView.adapter = desiredAdapter
        navViews.forEach { (k, v) -> v.isActivated = k == s }
        title.text = s.label
        searchBox.visibility = if (s == Section.SEARCH) View.VISIBLE else View.GONE
        status.text = ""
        val p = playlist
        liveHeader.visibility = if (s == Section.TV && p != null) View.VISIBLE else View.GONE
        liveTimeline.visibility = if (s == Section.TV && p != null) View.VISIBLE else View.GONE
        if (p == null) {
            groupsView.visibility = View.GONE
            itemAdapter.submit(emptyList(), emptySet())
            showState(
                "Welcome to ${Branding.displayName(this)}.\n\nAdd your IPTV provider's playlist (M3U URL or Xtream Codes login) to start watching.\n\n" +
                    Branding.freeNotice(this),
                progress = false, action = "Add playlist",
            ) { openLocked(app.settings.isPlaylistsLocked(), "Playlists are locked", PlaylistEditActivity::class.java) }
            if (requestFocus) stateAction.requestFocus()
            return
        }
        when (s) {
            Section.TV -> loadGroups(p, ItemKind.LIVE, "All channels", requestFocus)
            Section.MOVIES -> loadGroups(p, ItemKind.MOVIE, "All movies", requestFocus)
            Section.SHOWS -> if (p.type == SourceType.M3U) loadGroups(p, ItemKind.EPISODE, null, requestFocus)
                else loadGroups(p, ItemKind.SERIES, "All shows", requestFocus)
            Section.FAVORITES -> { groupsView.visibility = View.GONE; loadItems(requestFocus) { app.repo.favorites(p.id) } }
            Section.HISTORY -> { groupsView.visibility = View.GONE; loadItems(requestFocus) { app.repo.history(p.id) } }
            Section.RECORDINGS -> { groupsView.visibility = View.GONE; loadItems(requestFocus) { recordingRows() } }
            Section.SEARCH -> {
                groupsView.visibility = View.GONE
                itemAdapter.submit(emptyList(), emptySet())
                // Re-entering Search keeps the typed query, so show its results rather than the empty-query hint.
                val query = searchBox.text.toString()
                if (query.trim().length >= 2) scheduleSearch(query, 0) else showState(SEARCH_HINT, progress = false)
                if (requestFocus) searchBox.requestFocus()
            }
            else -> Unit
        }
    }

    private fun loadGroups(p: Playlist, kind: ItemKind, allLabel: String?, requestFocus: Boolean) {
        val targetSection = section
        groupsView.visibility = View.VISIBLE
        loadJob?.cancel()
        showState("Loading…", progress = true)
        loadJob = lifecycleScope.launch {
            val groups = withContext(Dispatchers.IO) { app.repo.groups(p.id, kind) }
            if (section != targetSection || playlist?.id != p.id) return@launch
            val s = app.settings
            val entries = ArrayList<GroupEntry>()
            entries.add(GroupEntry(GroupInfo("Favorites", -1), SPECIAL_FAV))
            if (kind != ItemKind.LIVE) entries.add(GroupEntry(GroupInfo("History", -1), SPECIAL_HISTORY))
            val visible = groups.filter { !s.isGroupHidden(it.name) }
            if (allLabel != null) entries.add(GroupEntry(GroupInfo(allLabel, visible.sumOf { it.count }), SPECIAL_ALL))
            visible.forEach { entries.add(GroupEntry(it)) }
            groupEntries = entries
            val guide = pendingGuide
            pendingGuide = null
            if (guide != null && kind == ItemKind.LIVE) {
                lastSection = section
                selectGroup(guideGroupPos(guide.first), focusItems = true, focusUrl = guide.second)
                return@launch
            }
            val initial = if (kind == ItemKind.LIVE && allLabel != null) 1 else entries.indexOfFirst { it.special == null }.coerceAtLeast(0)
            selectGroup(if (section == lastSection && selectedGroup in entries.indices) selectedGroup else initial, focusItems = false, onCancel = {
                if (section == targetSection && playlist?.id == p.id) {
                    val fallback = groupEntries.indexOfFirst { it.special == SPECIAL_ALL }
                        .takeIf { it >= 0 } ?: groupEntries.indexOfFirst { it.special == SPECIAL_FAV }
                    selectGroup(fallback, focusItems = false)
                    groupsView.post { focusGroups() }
                }
            })
            lastSection = section
            if (requestFocus) groupsView.post { focusGroups() }
        }
    }

    private var lastSection: Section? = null

    private fun currentKind(): ItemKind = when (section) {
        Section.MOVIES -> ItemKind.MOVIE
        Section.SHOWS -> if (playlist?.type == SourceType.M3U) ItemKind.EPISODE else ItemKind.SERIES
        else -> ItemKind.LIVE
    }

    private fun selectGroup(pos: Int, focusItems: Boolean, focusUrl: String? = null, onCancel: (() -> Unit)? = null) {
        val p = playlist ?: return
        val entry = groupEntries.getOrNull(pos) ?: return
        val doSelect = {
            selectedGroup = pos
            loadedGroup = pos
            val locks = if (app.settings.hasPin()) app.settings.lockedGroups else emptySet()
            if (groupAdapter.itemCount == groupEntries.size && groupAdapter.locked == locks) groupAdapter.select(pos)
            else groupAdapter.submit(groupEntries.map { it.info }, pos, locks)
            if (section == Section.TV) title.text = "${Section.TV.label}  ·  ${entry.info.name}"
            val kind = currentKind()
            val m3uShows = kind == ItemKind.EPISODE
            loadItems(focusItems, focusUrl) {
                val list = when (entry.special) {
                    SPECIAL_FAV -> app.repo.favorites(p.id, kind)
                    SPECIAL_HISTORY -> app.repo.history(p.id).filter { it.kind == kind || (kind == ItemKind.SERIES && it.kind == ItemKind.EPISODE) }
                    SPECIAL_ALL -> app.repo.items(p.id, kind, null)
                    else -> app.repo.items(p.id, kind, entry.info.name)
                }
                if (m3uShows && entry.special == null) SeriesNames.groupEpisodes(list) else list
            }
        }
        if (entry.special == null && app.settings.isGroupLocked(entry.info.name)) {
            Dialogs.requirePin(this, "\"${entry.info.name}\" is locked", onCancel = onCancel) { doSelect() }
        } else doSelect()
    }

    private fun loadItems(requestFocus: Boolean, focusUrl: String? = null, query: () -> List<MediaItem>) {
        val p = playlist ?: return
        val targetSection = section
        val hadListFocus = currentFocus?.let { isInside(it, itemsView) } == true
        loadJob?.cancel()
        showState("Loading…", progress = true)
        loadJob = lifecycleScope.launch {
            val (items, favs) = withContext(Dispatchers.IO) {
                val list = query().filter { !app.settings.isGroupHidden(it.group) }
                list to app.repo.favorites(p.id).map { it.url }.toHashSet()
            }
            if (section != targetSection || playlist?.id != p.id) return@launch
            itemAdapter.submit(items, favs)
            val at = focusUrl?.let { u -> items.indexOfFirst { it.url == u } }?.takeIf { it >= 0 } ?: 0
            if (section == Section.TV) liveGrid.submit(items, favs, at)
            (itemsView.layoutManager as? LinearLayoutManager)?.scrollToPositionWithOffset(at, 0)
            status.text = "${items.size} items"
            if (items.isEmpty()) showState(emptyText(), progress = false) else hideState()
            // An emptied list (e.g. the last favorite removed) would drop focus onto the first rail item.
            if (items.isEmpty() && (hadListFocus || currentFocus == null)) {
                if (tvLevels() && groupEntries.isNotEmpty()) showCategories() else navViews[section]?.requestFocus()
            }
            if (requestFocus && items.isNotEmpty()) focusItemAt(at)
        }
    }

    /** Reloads the Recordings list (statuses and sizes change in the background) while keeping the D-pad focus on the same row. */
    private fun refreshRecordingsInPlace() {
        if (section != Section.RECORDINGS) return
        loadJob?.cancel()
        loadJob = lifecycleScope.launch {
            val rows = withContext(Dispatchers.IO) { recordingRows() }
            if (section != Section.RECORDINGS) return@launch
            val focus = currentFocus
            val keepListFocus = focus?.let { isInside(it, itemsView) } == true
            val focused = focus?.let { itemsView.findContainingViewHolder(it)?.bindingAdapterPosition }
                ?: RecyclerView.NO_POSITION
            val focusedUrl = itemAdapter.items.getOrNull(focused)?.url
            val inputGeneration = userInputGeneration
            itemAdapter.submit(rows, emptySet())
            status.text = "${rows.size} items"
            if (rows.isEmpty()) {
                showState(emptyText(), progress = false)
                if (keepListFocus || focus == null) navViews[section]?.requestFocus()
                return@launch
            }
            hideState()
            if (keepListFocus) {
                val pos = focusedUrl?.let { url -> rows.indexOfFirst { it.url == url }.takeIf { it >= 0 } }
                    ?: focused.coerceIn(0, rows.size - 1)
                itemsView.doOnNextLayout {
                    val eligible = section == Section.RECORDINGS && itemAdapter.items === rows &&
                        userInputGeneration == inputGeneration && window.decorView.hasWindowFocus()
                    val target = itemsView.findViewHolderForAdapterPosition(pos)?.itemView
                    if (eligible) target?.requestFocus()
                }
                itemsView.scrollToPosition(pos)
                itemsView.requestLayout()
            }
        }
    }

    internal fun onRecordingFinished() {
        refreshRecordingsInPlace()
    }

    private fun emptyText() = when (section) {
        Section.FAVORITES -> "No favorites yet. Press MENU (or long-press OK) on a channel or movie and choose \"Add to favorites\"."
        Section.HISTORY -> "Nothing watched yet."
        Section.RECORDINGS -> "No recordings or reminders.\n\nIn TV, open a category's guide and press MENU (or long-press OK) on a programme to record it or set a reminder. " +
            "Recordings are saved on this device."
        Section.SEARCH -> "No results."
        else -> if ((playlist?.lastUpdated ?: 0L) == 0L) "This playlist has not been loaded yet. Open Playlists and refresh it." else "This group is empty."
    }

    // ---------------- Search ----------------

    private fun setupSearch() {
        searchBox.addTextChangedListener(object : TextWatcher {
            override fun beforeTextChanged(s: CharSequence?, start: Int, count: Int, after: Int) = Unit
            override fun onTextChanged(s: CharSequence?, start: Int, before: Int, count: Int) = Unit
            override fun afterTextChanged(s: Editable?) { scheduleSearch(s?.toString().orEmpty(), 450) }
        })
        searchBox.setOnEditorActionListener { _, actionId, event ->
            if (actionId == EditorInfo.IME_ACTION_SEARCH || event?.keyCode == KeyEvent.KEYCODE_ENTER) {
                scheduleSearch(searchBox.text.toString(), 0, focusResults = true)
                (getSystemService(INPUT_METHOD_SERVICE) as InputMethodManager).hideSoftInputFromWindow(searchBox.windowToken, 0)
                true
            } else false
        }
    }

    private fun scheduleSearch(text: String, delayMs: Long, focusResults: Boolean = false) {
        val p = playlist ?: return
        searchJob?.cancel()
        searchJob = lifecycleScope.launch {
            delay(delayMs)
            if (text.trim().length < 2) {
                itemAdapter.submit(emptyList(), emptySet()); status.text = ""
                showState(SEARCH_HINT, progress = false)
                return@launch
            }
            val (res, favs) = withContext(Dispatchers.IO) {
                val names = app.repo.search(p.id, text, listOf(ItemKind.LIVE, ItemKind.MOVIE, ItemKind.SERIES, ItemKind.EPISODE))
                val progs = app.repo.searchPrograms(p.id, text)
                val seen = names.map { it.url }.toHashSet()
                val all = (names + progs.filter { it.url !in seen })
                    .filter { !app.settings.isGroupHidden(it.group) }
                all to app.repo.favorites(p.id).map { it.url }.toHashSet()
            }
            itemAdapter.submit(res, favs)
            status.text = "${res.size} results"
            if (res.isEmpty()) showState("No results for \"${text.trim()}\".", progress = false) else {
                hideState()
                if (focusResults) focusItemAt(0)
            }
        }
    }

    // ---------------- Options ----------------

    private fun itemOptions(item: MediaItem) {
        val opts = itemOptionList(item)
        Dialogs.choose(this, item.name, opts.map { it.first }) { opts[it].second() }
    }

    private fun itemOptionList(item: MediaItem): ArrayList<Pair<String, () -> Unit>> {
        val opts = ArrayList<Pair<String, () -> Unit>>()
        val isPseudoSeries = item.url.startsWith(SeriesNames.M3U_SERIES_PREFIX)
        if (!isPseudoSeries) {
            val fav = item.url in itemAdapter.favorites
            opts.add((if (fav) "Remove from favorites" else "Add to favorites") to {
                app.repo.setFavorite(item, !fav)
                itemAdapter.favorites = if (fav) itemAdapter.favorites - item.url else itemAdapter.favorites + item.url
                if (section == Section.FAVORITES || groupEntries.getOrNull(selectedGroup)?.special == SPECIAL_FAV) showSection(section)
                else if (section == Section.TV) liveGrid.setFavorites(itemAdapter.favorites)
                else itemAdapter.notifyItemRangeChanged(0, itemAdapter.itemCount)
            })
        }
        if (item.kind == ItemKind.LIVE) {
            if (section != Section.TV) opts.add("Show in TV guide" to { openGuide(item.group, item.url) })
            opts.add("Record channel now" to {
                val now = System.currentTimeMillis()
                val shift = app.settings.epgShiftHours * 3_600_000L
                val cur = app.repo.nowAndNext(item.playlistId, item.tvgId, now - shift).firstOrNull { it.start <= now - shift }
                    ?.let { it.copy(start = it.start + shift, end = it.end + shift) }
                Scheduler.recordNow(this, item, cur)
            })
        }
        if (item.kind != ItemKind.SERIES) opts.add("Open in external player" to {
            // The external app receives the stream URL, so a locked group needs the parental PIN first.
            if (app.settings.isGroupLocked(item.group)) Dialogs.requirePin(this, "\"${item.group}\" is locked") { Nav.externalPlayer(this, item.url, item.name) }
            else Nav.externalPlayer(this, item.url, item.name)
        })
        opts.add((if (item.group in app.settings.lockedGroups && app.settings.hasPin()) "Unlock group \"${item.group}\"" else "Lock group \"${item.group}\"") to {
            toggleLock(item.group)
        })
        if (section == Section.HISTORY) opts.add("Clear watch history" to {
            Dialogs.confirm(this, "Clear history", "Remove all watch history and resume positions for this playlist?") {
                playlist?.let { app.repo.clearHistory(it.id) }; showSection(Section.HISTORY)
            }
        })
        return opts
    }

    private fun groupOptions(entry: GroupEntry, g: GroupInfo) {
        if (entry.special != null) return
        val locked = app.settings.isGroupLocked(g.name)
        val opts = arrayListOf(if (locked) "Unlock group" else "Lock group (parental PIN)")
        Dialogs.choose(this, g.name, opts) { toggleLock(g.name) }
    }

    private fun toggleLock(group: String) {
        val s = app.settings
        val apply = {
            s.lockedGroups = if (s.isGroupLocked(group)) s.lockedGroups - group else s.lockedGroups + group
            shownStamp = s.restrictionStamp()
            showSection(section)
        }
        if (!s.hasPin()) {
            Dialogs.toast(this, "Create a parental PIN to lock groups")
            Dialogs.createPin(this, PinKind.PARENTAL) { apply() }
        } else Dialogs.requirePin(this, "change group lock") { apply() }
    }

    // ---------------- Recordings & reminders ----------------

    @Volatile private var reminderRows: List<Reminder> = emptyList()

    private fun recordingRows(): List<MediaItem> {
        val rows = ArrayList<MediaItem>()
        val s = app.settings
        // Recordings and reminders follow the parental rules of the channel they came from.
        val hidden = { pid: Long, url: String -> app.repo.itemByUrl(pid, url)?.let { s.isGroupHidden(it.group) } == true }
        for (r in app.repo.recordings()) {
            if (hidden(r.playlistId, r.channelUrl)) continue
            val size = android.text.format.Formatter.formatShortFileSize(this, r.bytes)
            val sub = when (r.status) {
                RecordingStatus.RECORDING -> "${Scheduler.REC_MARK} Recording now · ${r.channelName} · until ${Format.time(r.end)} · $size"
                RecordingStatus.SCHEDULED -> "Scheduled · ${r.channelName} · ${Format.dayTime(r.start)} – ${Format.time(r.end)}"
                RecordingStatus.DONE -> "${r.channelName} · ${Format.dayTime(r.start)} · $size" + if (r.error.isNotBlank()) " · ${r.error}" else ""
                RecordingStatus.FAILED -> "Failed · ${r.channelName} · ${r.error}"
                RecordingStatus.MISSED -> "Missed · ${r.channelName} · ${Format.dayTime(r.start)}"
            }
            rows.add(MediaItem(playlistId = r.playlistId, kind = ItemKind.MOVIE, name = r.title.ifBlank { r.channelName }, group = sub, url = "rec:${r.id}", isLocal = true))
        }
        val rems = app.repo.reminders().filter { !hidden(it.playlistId, it.channelUrl) }
        reminderRows = rems
        rems.forEachIndexed { i, r ->
            rows.add(MediaItem(playlistId = r.playlistId, kind = ItemKind.MOVIE, name = "${Scheduler.REMINDER_MARK} ${r.title}",
                group = "Reminder · ${r.channelName} · ${Format.dayTime(r.start)}", url = "rem:$i", isLocal = true))
        }
        return rows
    }

    private fun recordingOf(item: MediaItem) = item.url.removePrefix("rec:").toLongOrNull()?.takeIf { item.url.startsWith("rec:") }?.let { app.repo.recording(it) }
    private fun reminderOf(item: MediaItem) = item.url.removePrefix("rem:").toIntOrNull()?.takeIf { item.url.startsWith("rem:") }?.let { reminderRows.getOrNull(it) }

    private fun recordingClick(item: MediaItem) {
        val r = recordingOf(item)
        if (r != null && (r.status == RecordingStatus.DONE || r.status == RecordingStatus.RECORDING) && File(r.file).exists()) playRecording(r)
        else recordingOptions(item)
    }

    private fun playRecording(r: Recording) {
        val file = File(r.file)
        // A recording of a locked channel keeps that channel's group, so the player enforces the same parental lock.
        val source = app.repo.itemByUrl(r.playlistId, r.channelUrl)?.group
        val locked = source != null && app.settings.isGroupLocked(source)
        val local = MediaItem(
            playlistId = r.playlistId, kind = ItemKind.MOVIE, name = r.title.ifBlank { r.channelName },
            group = if (locked) source!! else "${Scheduler.REC_MARK} Recording · ${r.channelName}",
            url = android.net.Uri.fromFile(file).toString(), isLocal = true,
        )
        if (locked) Dialogs.requirePin(this, "\"$source\" is locked") { Nav.play(this, listOf(local), 0, unlockedGroup = source) }
        else Nav.play(this, listOf(local), 0)
    }

    private fun recordingOptions(item: MediaItem) {
        val opts = ArrayList<Pair<String, () -> Unit>>()
        val r = recordingOf(item)
        val rem = reminderOf(item)
        val reload = { refreshRecordingsInPlace() }
        if (r != null) {
            val hasFile = r.file.isNotBlank() && File(r.file).exists()
            when (r.status) {
                RecordingStatus.RECORDING -> {
                    if (hasFile) opts.add("Play (recorded so far)" to { playRecording(r) })
                    opts.add("Stop recording" to { Recorder.stop(r.id); Dialogs.toast(this, "Stopping recording…") })
                }
                RecordingStatus.SCHEDULED -> opts.add("Cancel scheduled recording" to { app.repo.deleteRecording(r.id); reload() })
                else -> {
                    if (hasFile) opts.add("Play" to { playRecording(r) })
                    opts.add((if (hasFile) "Delete recording" else "Remove from list") to {
                        Dialogs.confirm(this, "Delete recording", "Delete \"${item.name}\" from this device?") {
                            if (r.file.isNotBlank()) File(r.file).delete()
                            app.repo.deleteRecording(r.id); reload()
                        }
                    })
                }
            }
        } else if (rem != null) {
            opts.add("Watch ${rem.channelName}" to { Scheduler.watch(this, rem.playlistId, rem.channelUrl) })
            opts.add("Cancel reminder" to { app.repo.deleteReminder(rem.playlistId, rem.channelUrl, rem.start); reload() })
        }
        if (opts.isEmpty()) return
        Dialogs.choose(this, item.name, opts.map { it.first }) { opts[it].second() }
    }

    // ---------------- Startup behaviour ----------------

    private fun maybeResumeLastChannel(p: Playlist) {
        if (!pendingResume) return
        pendingResume = false
        val url = app.settings.lastChannelUrl
        if (url.isBlank()) return
        lifecycleScope.launch {
            val queue = withContext(Dispatchers.IO) {
                val item = app.repo.itemByUrl(p.id, url) ?: return@withContext null
                if (app.settings.isGroupLocked(item.group)) return@withContext null
                app.repo.items(p.id, ItemKind.LIVE, item.group)
            } ?: return@launch
            val idx = queue.indexOfFirst { it.url == url }
            if (idx >= 0) { revealAfterResume = true; Nav.play(this@MainActivity, queue, idx) }
        }
    }

    private fun maybeAutoRefresh(p: Playlist) {
        val hours = app.settings.autoRefreshHours
        if (hours <= 0 || autoRefreshStarted) return
        val stale = { t: Long -> System.currentTimeMillis() - t > hours * 3_600_000L }
        val needList = p.lastUpdated == 0L || stale(p.lastUpdated)
        val needEpg = !needList && stale(app.settings.epgUpdated(p.id)) && app.repo.epgSource(p).isNotBlank()
        if (!needList && !needEpg) return
        autoRefreshStarted = true
        lifecycleScope.launch {
            val warning = withContext(Dispatchers.IO) {
                try {
                    val progress: (String) -> Unit = { msg -> runOnUiThread { status.text = msg } }
                    if (needList) Refresher.refreshAll(p, progress) else Refresher.refreshEpg(p, progress)
                } catch (e: Throwable) {
                    "Playlist update failed: ${Nav.describeError(e)}"
                }
            }
            status.text = warning ?: "Playlist updated"
            playlist = app.repo.playlist(p.id)
            if (itemAdapter.itemCount == 0 || needList) showSection(section) else itemAdapter.notifyDataSetChanged()
        }
    }

    // ---------------- Helpers ----------------

    private fun focusGroups() {
        if (section == Section.TV) setLiveLevel(LiveLevel.CATEGORIES)
        focusGroupAt(selectedGroup)
    }

    private fun isInside(v: View, parent: View): Boolean {
        var cur: View? = v
        while (cur != null) {
            if (cur === parent) return true
            cur = cur.parent as? View
        }
        return false
    }

    private fun showState(text: String, progress: Boolean, action: String? = null, onAction: (() -> Unit)? = null) {
        stateBox.visibility = View.VISIBLE
        stateText.text = text
        stateProgress.visibility = if (progress) View.VISIBLE else View.GONE
        if (action != null) {
            stateAction.visibility = View.VISIBLE
            stateAction.text = action
            stateAction.setOnClickListener { onAction?.invoke() }
        } else stateAction.visibility = View.GONE
    }

    private fun hideState() { stateBox.visibility = View.GONE }

    companion object {
        private const val SPECIAL_FAV = "fav"
        private const val SPECIAL_ALL = "all"
        private const val SPECIAL_HISTORY = "history"
        const val EXTRA_GUIDE_GROUP = "guide_group"
        const val EXTRA_GUIDE_CHANNEL = "guide_channel"
        const val SEARCH_HINT = "Type to search channel names, guide programmes, movies, and series."
        private var autoRefreshStarted = false
    }
}
