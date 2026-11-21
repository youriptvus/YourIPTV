package com.youriptv.app.ui

import android.annotation.SuppressLint
import android.util.TypedValue
import android.view.KeyEvent
import android.view.MotionEvent
import android.view.View
import android.view.ViewGroup
import android.view.ViewTreeObserver
import android.widget.TextView
import androidx.appcompat.app.AlertDialog
import androidx.appcompat.app.AppCompatActivity
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.RecyclerView
import com.youriptv.app.R
import com.youriptv.app.YourIptvApp
import com.youriptv.app.data.Catchup
import com.youriptv.app.data.MediaItem
import com.youriptv.app.data.Program
import com.youriptv.app.data.RecordingStatus
import androidx.lifecycle.lifecycleScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Job
import kotlinx.coroutines.currentCoroutineContext
import kotlinx.coroutines.ensureActive
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

/**
 * The Live TV guide: channel rows with a programme timeline, embedded in MainActivity next to the categories.
 * OK on the current programme previews the channel (OK again goes full screen); OK on an ended programme plays it
 * from the provider archive and on a future one shows its details. Short LEFT at "now" returns to the categories,
 * a long LEFT (or LEFT once already in the past) steps back through earlier programmes.
 */
class LiveGuideGrid(
    private val activity: AppCompatActivity,
    private val rows: RecyclerView,
    private val timeline: GuideTimelineView,
    private val title: TextView,
    private val meta: TextView,
    private val description: TextView,
    private val select: (MediaItem, List<MediaItem>) -> Unit,
    private val channelOptions: (MediaItem) -> List<Pair<String, () -> Unit>>,
    private val exitLeft: () -> Unit,
    private val interactionGeneration: () -> Long = { 0L },
) {
    private val app get() = YourIptvApp.instance
    // Each actionable guide row also provides a full 48dp phone touch target.
    private val state = GuideState(activity, channelWidthDp = if (activity.resources.getBoolean(R.bool.is_portrait)) 180f else 260f)
    private val cache = GuideProgramCache(app)
    private val rowHeight = TypedValue.applyDimension(
        TypedValue.COMPLEX_UNIT_DIP, 48f, activity.resources.displayMetrics,
    ).toInt()
    private var channels: List<MediaItem> = emptyList()
    private var archiveDays = 0
    private var settingsSnapshot = guideSettings()
    val adapter = RowAdapter()
    val focusedRow get() = state.focusedRow
    val items: List<MediaItem> get() = channels
    private var leftHeld = false
    private var pendingRowFocus: RowFocusRequest? = null
    private var navigationGeneration = 0L
        set(value) {
            if (field != value) {
                field = value
                pendingRowFocus?.cancel()
            }
        }
    private var guideSearchJob: Job? = null
    private var focusedLookup: Pair<Triple<Long, String, Long>, List<Program>>? = null

    init {
        timeline.state = state
        resetTime()
    }

    fun submit(items: List<MediaItem>, favorites: Set<String>, focused: Int) {
        navigationGeneration++
        guideSearchJob?.cancel()
        channels = items
        archiveDays = items.maxOfOrNull { it.catchupDays } ?: 0
        state.focusedRow = focused.coerceIn(0, (items.size - 1).coerceAtLeast(0))
        state.favorites = favorites
        state.showNumbers = app.settings.showChannelNumbers
        settingsSnapshot = guideSettings()
        cache.clear()
        focusedLookup = null
        resetTime()
        adapter.notifyDataSetChanged()
        refreshMarks()
    }

    /** Called only after a committed generation. Keeps the current guide position and live session. */
    fun reloadCommitted(items: List<MediaItem>? = null) {
        val navigation = ++navigationGeneration
        guideSearchJob?.cancel()
        val interaction = interactionGeneration()
        val layout = rows.layoutManager as? LinearLayoutManager
        val first = layout?.findFirstVisibleItemPosition()?.coerceAtLeast(0) ?: 0
        val snapshot = GuideContinuity(
            channels.getOrNull(state.focusedRow)?.let { it.playlistId to it.url }, state.focusedRow,
            channels.getOrNull(first)?.let { it.playlistId to it.url }, first,
            layout?.findViewByPosition(first)?.let { it.top - rows.paddingTop } ?: 0,
            state.focusTime, state.windowStart,
        )
        val hadFocus = rows.hasFocus() && rows.hasWindowFocus()
        if (items != null) channels = items
        archiveDays = channels.maxOfOrNull { it.catchupDays } ?: 0
        state.focusedRow = snapshot.focusedIn(channels)
        state.focusTime = snapshot.focusTime
        state.windowStart = snapshot.windowStart
        val committedChannels = channels
        val targetRow = state.focusedRow
        cache.clear()
        focusedLookup = null
        adapter.notifyDataSetChanged()
        if (channels.isNotEmpty()) {
            layout?.scrollToPositionWithOffset(snapshot.firstIn(channels), snapshot.firstOffset)
            if (hadFocus) rows.post {
                // A late layout callback must never pull focus back after customer navigation/input.
                if (navigationGeneration == navigation && interactionGeneration() == interaction &&
                    rows.hasWindowFocus() && rows.hasFocus() && rows.adapter === adapter &&
                    channels === committedChannels && state.focusTime == snapshot.focusTime &&
                    state.windowStart == snapshot.windowStart) {
                    state.focusedRow = targetRow
                    rows.findViewHolderForAdapterPosition(targetRow)?.itemView?.requestFocus()
                }
            }
        }
        refreshMarks()
    }

    /** Reminder and recording markers; they change in dialogs and in the background. */
    fun refreshMarks() {
        val playlistId = channels.firstOrNull()?.playlistId
        if (playlistId == null) { refresh(); return }
        val m = HashMap<String, String>()
        for (r in app.repo.reminders(playlistId)) m["${r.channelUrl}|${r.start}"] = Scheduler.REMINDER_MARK
        for (r in app.repo.recordings()) {
            if (r.playlistId == playlistId && (r.status == RecordingStatus.SCHEDULED || r.status == RecordingStatus.RECORDING)) {
                m["${r.channelUrl}|${r.start}"] = Scheduler.REC_MARK
            }
        }
        state.marks = m
        refresh()
    }

    fun refreshSettings() {
        refresh()
    }

    fun setFavorites(favorites: Set<String>) {
        state.favorites = favorites
        refresh()
    }

    fun setPlaying(item: MediaItem?) {
        state.playing = item?.let { LiveChannelKey(it.playlistId, it.url) }
        refresh()
    }

    fun focus(position: Int) {
        val navigation = ++navigationGeneration
        val interaction = interactionGeneration()
        val expectedChannels = channels
        if (channels.isEmpty()) return
        val target = position.coerceIn(0, channels.lastIndex)
        state.focusedRow = target
        (rows.layoutManager as LinearLayoutManager).scrollToPositionWithOffset(target, 0)
        RowFocusRequest(target, navigation, interaction, expectedChannels).also {
            pendingRowFocus = it
            it.start()
        }
        refresh()
    }

    /** Explicit navigation can cancel readiness waiting even when accessibility sends no physical input event. */
    fun cancelPendingFocus() { pendingRowFocus?.cancel() }

    /** Cold loads can finish before the first layout or window focus; wait without replaying stale navigation. */
    private inner class RowFocusRequest(
        private val target: Int,
        private val navigation: Long,
        private val interaction: Long,
        private val expectedChannels: List<MediaItem>,
    ) {
        private var cancelled = false
        private var observer: ViewTreeObserver? = null
        private val initial = Runnable { tryFocus() }
        private val timeout = Runnable { cancel() }
        private val preDraw = ViewTreeObserver.OnPreDrawListener { tryFocus(); true }
        private val windowFocus = ViewTreeObserver.OnWindowFocusChangeListener { hasFocus ->
            if (hasFocus) tryFocus()
        }
        private val attachment = object : View.OnAttachStateChangeListener {
            override fun onViewAttachedToWindow(view: View) { observeWindow(); rows.post(initial) }
            override fun onViewDetachedFromWindow(view: View) { cancel() }
        }

        fun start() {
            rows.addOnAttachStateChangeListener(attachment)
            if (rows.isAttachedToWindow) observeWindow()
            rows.post(initial)
            rows.postDelayed(timeout, 5_000L)
        }

        private fun observeWindow() {
            if (cancelled) return
            val current = rows.viewTreeObserver
            if (observer === current) return
            observer = current
            current.addOnPreDrawListener(preDraw)
            current.addOnWindowFocusChangeListener(windowFocus)
        }

        private fun tryFocus() {
            if (cancelled) return
            if (pendingRowFocus !== this || navigationGeneration != navigation ||
                interactionGeneration() != interaction || channels !== expectedChannels || rows.adapter !== adapter ||
                activity.isFinishing || activity.isDestroyed) {
                cancel()
                return
            }
            if (!rows.isAttachedToWindow || !rows.hasWindowFocus() || rows.isLayoutRequested) return
            val row = rows.findViewHolderForAdapterPosition(target)?.itemView as? GuideRowView ?: return
            if (!row.isLaidOut || !row.isShown || row.channel != expectedChannels.getOrNull(target)) return
            if (row.hasFocus() || row.requestFocus()) cancel()
        }

        fun cancel() {
            if (cancelled) return
            cancelled = true
            rows.removeCallbacks(initial)
            rows.removeCallbacks(timeout)
            rows.removeOnAttachStateChangeListener(attachment)
            observer?.takeIf { it.isAlive }?.let {
                it.removeOnPreDrawListener(preDraw)
                it.removeOnWindowFocusChangeListener(windowFocus)
            }
            observer = null
            if (pendingRowFocus === this) pendingRowFocus = null
        }
    }

    fun handleKey(event: KeyEvent): Boolean {
        if (event.action == KeyEvent.ACTION_DOWN) navigationGeneration++
        if (channels.isEmpty()) return false
        val down = event.action == KeyEvent.ACTION_DOWN
        when (event.keyCode) {
            KeyEvent.KEYCODE_DPAD_RIGHT -> {
                if (down) moveTime(true)
                return true
            }
            // Decided on release so a held LEFT can mean "earlier programmes" while a short press leaves the guide.
            KeyEvent.KEYCODE_DPAD_LEFT -> {
                if (down) {
                    if (event.repeatCount == 0) leftHeld = false
                    else if (event.repeatCount == 1 || event.repeatCount % 6 == 0) { leftHeld = true; moveTime(false) }
                } else {
                    val held = leftHeld
                    leftHeld = false
                    if (!held && !event.isCanceled) { if (isAtNow()) exitLeft() else moveTime(false) }
                }
                return true
            }
            KeyEvent.KEYCODE_MEDIA_FAST_FORWARD, KeyEvent.KEYCODE_MEDIA_REWIND -> {
                if (down) jumpBy(if (event.keyCode == KeyEvent.KEYCODE_MEDIA_FAST_FORWARD) 3 * 3_600_000L else -3 * 3_600_000L)
                return true
            }
            KeyEvent.KEYCODE_MENU, KeyEvent.KEYCODE_INFO -> {
                if (!down) channels.getOrNull(state.focusedRow)?.let { options(it) }
                return true
            }
            KeyEvent.KEYCODE_CHANNEL_UP, KeyEvent.KEYCODE_PAGE_UP,
            KeyEvent.KEYCODE_CHANNEL_DOWN, KeyEvent.KEYCODE_PAGE_DOWN -> {
                if (down) {
                    val delta = (rows.height / rowHeight).coerceAtLeast(1)
                    focus(state.focusedRow + if (event.keyCode == KeyEvent.KEYCODE_CHANNEL_UP ||
                        event.keyCode == KeyEvent.KEYCODE_PAGE_UP) -delta else delta)
                }
                return true
            }
            KeyEvent.KEYCODE_PROG_RED -> {
                if (down) openAtNow()
                return true
            }
        }
        return false
    }

    fun refresh() {
        val updated = guideSettings()
        if (updated != settingsSnapshot) {
            settingsSnapshot = updated
            state.showNumbers = app.settings.showChannelNumbers
            cache.clear()
            focusedLookup = null
            guideSearchJob?.cancel()
            val boundedTime = state.focusTime.coerceIn(minTime(), maxTime())
            if (boundedTime != state.focusTime) {
                state.focusTime = boundedTime
                state.windowStart = GuideNavigation.windowFor(state.windowStart, boundedTime, state.span)
            }
        }
        timeline.invalidate()
        // Row programme lists must follow horizontal time navigation; repainting an old bounded list is insufficient.
        for (i in 0 until rows.childCount) {
            val row = rows.getChildAt(i) as? GuideRowView ?: continue
            row.channel?.let { row.programs = programsFor(it) }
            row.invalidate()
        }
        val channel = channels.getOrNull(state.focusedRow)
        if (channel == null) {
            title.text = ""; meta.text = ""; description.text = ""
            return
        }
        val program = focusedProgram(channel)
        val header = guideHeader(channel, program, System.currentTimeMillis(), state.marks, showArchiveHint = true)
        title.text = header.title
        meta.text = header.meta
        description.text = header.description
    }

    private fun resetTime() {
        state.openAtNow(System.currentTimeMillis())
    }

    /** True when the focused cell is the programme on air now (or "now" itself on a channel without guide data). */
    fun isAtNow(): Boolean {
        val now = System.currentTimeMillis()
        val channel = channels.getOrNull(state.focusedRow) ?: return true
        val program = focusedProgram(channel)
        return if (program != null) now >= program.start && now < program.end
        else state.focusTime > now - GuideNavigation.STEP_MS && state.focusTime <= now + 60_000L
    }

    /** Explicit opening resets time; refresh and committed updates keep the user's current browsing position. */
    fun openAtNow() {
        navigationGeneration++
        guideSearchJob?.cancel()
        focusedLookup = null
        leftHeld = false
        resetTime()
        refresh()
    }

    fun jumpToNow() = openAtNow()

    private fun minTime() = System.currentTimeMillis() - pastMs()
    private fun maxTime() = System.currentTimeMillis() + app.settings.epgFutureDays * 86_400_000L

    private fun jumpBy(deltaMs: Long) {
        val t = (state.focusTime + deltaMs).coerceIn(minTime(), maxTime())
        state.windowStart = t - t % GuideNavigation.STEP_MS
        state.focusTime = t
        refresh()
    }

    private fun pastMs() = Catchup.pastWindowHours(app.settings.epgPastHours, archiveDays) * 3_600_000L

    private fun guideSettings() = listOf(
        app.settings.epgShiftHours, app.settings.epgPastHours, app.settings.epgFutureDays, app.settings.showChannelNumbers,
    )

    private fun focusedProgram(channel: MediaItem): Program? =
        GuideNavigation.programAt(selectedProgrammes(channel), state.focusTime)

    private fun programsFor(channel: MediaItem): List<Program> {
        val result = cache.windowFor(channel, state.windowStart, state.windowStart + state.span)
        return if (!result.dense) result.programs else listOf(Program(channel.tvgId,
            state.windowStart, state.windowStart + state.span, "Many programmes — use arrows for details"))
    }

    /** Actual bounded metadata, separate from compact/density-only row representations. */
    private fun selectedProgrammes(channel: MediaItem): List<Program> {
        if (channel.tvgId.isBlank()) return emptyList()
        val key = Triple(channel.playlistId, channel.tvgId, state.focusTime)
        focusedLookup?.let { if (it.first == key) return it.second }
        val shift = app.settings.epgShiftHours * 3_600_000L
        val programmes = app.repo.guideNeighbours(channel.playlistId, channel.tvgId, state.focusTime - shift,
            minTime() - shift, maxTime() - shift).map { it.copy(start = it.start + shift, end = it.end + shift) }
        focusedLookup = key to programmes
        return programmes
    }

    private fun moveTime(forward: Boolean) {
        val channel = channels.getOrNull(state.focusedRow) ?: return
        val target = GuideNavigation.targetTime(state.focusTime, selectedProgrammes(channel), forward, minTime(), maxTime())
        state.windowStart = GuideNavigation.windowFor(state.windowStart, target, state.span)
        state.focusTime = maxOf(target, state.windowStart)
        refresh()
    }

    private fun activate(position: Int) {
        val channel = channels.getOrNull(position) ?: return
        val program = focusedProgram(channel)
        val now = System.currentTimeMillis()
        when {
            program == null || (now >= program.start && now < program.end) -> select(channel, channels)
            program.end <= now && playArchive(channel, program, quiet = true) -> Unit
            else -> details(channel, program)
        }
    }

    /** Plays [program] from the provider archive; returns false (with a message unless [quiet]) when unavailable. */
    private fun playArchive(channel: MediaItem, program: Program, quiet: Boolean = false): Boolean {
        val item = app.repo.catchupItem(channel, program)
        if (item == null) {
            if (!quiet) Dialogs.toast(activity, "This programme is not available in the provider's archive.")
            return false
        }
        Nav.open(activity, item, listOf(item))
        return true
    }

    private fun details(channel: MediaItem, program: Program) {
        val now = System.currentTimeMillis()
        val message = buildString {
            append(Format.dayTime(program.start)).append(" – ").append(Format.time(program.end)).append('\n')
            append(channel.name).append("\n\n")
            append(program.description.ifBlank { "No description available." })
        }
        val builder = AlertDialog.Builder(activity).setTitle(program.title).setMessage(message)
            .setPositiveButton("Watch channel") { _, _ -> select(channel, channels) }
            .setNegativeButton("Close", null)
        if (Catchup.available(channel.catchupDays, program.start, program.end, now)) {
            builder.setNeutralButton(if (program.end <= now) "Play from archive" else "Play from start") { _, _ -> playArchive(channel, program) }
        } else if (program.start > now) {
            val has = app.repo.hasReminder(channel.playlistId, channel.url, program.start)
            builder.setNeutralButton(if (has) "Cancel reminder" else "Remind me") { _, _ ->
                Scheduler.toggleReminder(activity, channel, program); refreshMarks()
            }
        }
        val dialog = builder.show()
        RemoteBack.bind(dialog, activity)
        dialog.getButton(AlertDialog.BUTTON_POSITIVE)?.requestFocus()
    }

    /** MENU on a guide cell: programme actions first, then the channel's own options. */
    private fun options(channel: MediaItem) {
        val program = focusedProgram(channel)
        val now = System.currentTimeMillis()
        val opts = ArrayList<Pair<String, () -> Unit>>()
        opts.add("Watch ${channel.name}" to { select(channel, channels) })
        if (program != null) {
            if (Catchup.available(channel.catchupDays, program.start, program.end, now)) {
                opts.add((if (program.end <= now) "Play from archive" else "Play from start") to { playArchive(channel, program) })
            }
            opts.add("Programme details" to { details(channel, program) })
            if (program.start > now) {
                val has = app.repo.hasReminder(channel.playlistId, channel.url, program.start)
                opts.add((if (has) "Cancel reminder" else "Remind me") to { Scheduler.toggleReminder(activity, channel, program); refreshMarks() })
            }
            if (program.end > now) {
                val rec = app.repo.scheduledRecording(channel.playlistId, channel.url, program.start)
                val label = when {
                    rec?.status == RecordingStatus.RECORDING -> "Stop recording"
                    rec != null -> "Cancel scheduled recording"
                    program.start > now -> "Record programme"
                    else -> "Record until programme ends"
                }
                opts.add(label to { Scheduler.toggleProgramRecording(activity, channel, program) { refreshMarks() } })
            }
        }
        opts.add("Jump to now" to { jumpToNow() })
        if (pastMs() > 0) opts.add("Jump back one day" to { jumpBy(-86_400_000L) })
        opts.add("Jump forward one day" to { jumpBy(86_400_000L) })
        opts.add("Search this guide" to { searchGuide() })
        opts.addAll(channelOptions(channel))
        Dialogs.choose(activity, channel.name, opts.map { it.first }) { opts[it].second() }
    }

    private fun searchGuide() {
        Dialogs.input(activity, "Find channel or programme", "") { text ->
            val query = text.trim().lowercase()
            if (query.isEmpty()) return@input
            val now = System.currentTimeMillis()
            val index = channels.indexOfFirst { it.name.lowercase().contains(query) }
            if (index >= 0) { focus(index); return@input }
            guideSearchJob?.cancel()
            val navigation = ++navigationGeneration
            val interaction = interactionGeneration()
            val searchedChannels = channels
            val searchedSettings = guideSettings()
            val shift = app.settings.epgShiftHours * 3_600_000L
            val to = now + app.settings.epgFutureDays * 86_400_000L
            guideSearchJob = activity.lifecycleScope.launch {
                val match = try { withContext(Dispatchers.IO) {
                    var found: Pair<Int, Program>? = null
                    for ((i, channel) in searchedChannels.withIndex()) {
                        currentCoroutineContext().ensureActive()
                        val program = app.repo.findProgram(channel.playlistId, channel.tvgId, query, now - shift, to - shift) ?: continue
                        found = i to program.copy(start = program.start + shift, end = program.end + shift)
                        break
                    }
                    found
                } } catch (cancelled: CancellationException) { throw cancelled }
                catch (_: Exception) {
                    if (navigationGeneration == navigation && interactionGeneration() == interaction &&
                        channels === searchedChannels && rows.adapter === adapter && rows.hasWindowFocus()) {
                        Dialogs.toast(activity, "Guide search is unavailable. Please try again.")
                    }
                    return@launch
                }
                if (navigationGeneration != navigation || interactionGeneration() != interaction ||
                    channels !== searchedChannels || guideSettings() != searchedSettings ||
                    rows.adapter !== adapter || !rows.hasWindowFocus()) return@launch
                if (match == null) Dialogs.toast(activity, "Nothing found for \"$text\" in this view")
                else {
                    focus(match.first)
                    state.windowStart = maxOf(match.second.start, now).let { it - it % GuideNavigation.STEP_MS }
                    state.focusTime = maxOf(match.second.start, now)
                    refresh()
                }
            }
        }
    }

    inner class RowAdapter : RecyclerView.Adapter<RowAdapter.VH>() {
        inner class VH(val row: GuideRowView) : RecyclerView.ViewHolder(row)

        @SuppressLint("ClickableViewAccessibility")
        override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): VH {
            val row = GuideRowView(parent.context, state).apply {
                layoutParams = RecyclerView.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, rowHeight)
                isFocusable = true
                isFocusableInTouchMode = true
            }
            val holder = VH(row)
            row.setOnFocusChangeListener { _, hasFocus ->
                val position = holder.bindingAdapterPosition
                if (hasFocus && position != RecyclerView.NO_POSITION) {
                    state.focusedRow = position
                    refresh()
                }
            }
            row.setOnTouchListener { view, event ->
                if (event.action == MotionEvent.ACTION_DOWN) navigationGeneration++
                if (event.action == MotionEvent.ACTION_UP) {
                    val position = holder.bindingAdapterPosition
                    if (position != RecyclerView.NO_POSITION) {
                        state.focusedRow = position
                        if (event.x > state.channelWidth && view.width > state.channelWidth) {
                            val pxPerMs = (view.width - state.channelWidth) / state.span
                            state.focusTime = state.windowStart + ((event.x - state.channelWidth) / pxPerMs).toLong()
                        } else {
                            state.focusTime = System.currentTimeMillis()
                            state.windowStart = GuideNavigation.windowFor(state.windowStart, state.focusTime, state.span)
                        }
                        refresh()
                    }
                }
                false
            }
            row.setOnClickListener {
                navigationGeneration++
                val position = holder.bindingAdapterPosition
                if (position != RecyclerView.NO_POSITION) activate(position)
            }
            row.onOptions {
                navigationGeneration++
                val position = holder.bindingAdapterPosition
                channels.getOrNull(position)?.let { options(it) }
            }
            return holder
        }

        override fun getItemCount() = channels.size

        override fun onBindViewHolder(holder: VH, position: Int) {
            val channel = channels[position]
            holder.row.rowIndex = position
            holder.row.channel = channel
            holder.row.programs = programsFor(channel)
            holder.row.contentDescription = channel.name
            holder.row.invalidate()
        }
    }
}
