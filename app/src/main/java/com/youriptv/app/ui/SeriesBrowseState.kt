package com.youriptv.app.ui

import com.youriptv.app.data.Episode
import com.youriptv.app.data.ItemKind
import com.youriptv.app.data.MediaItem
import java.security.MessageDigest

internal data class SeriesSeasonRow(val season: Int, val episodes: List<Episode>)

/** Navigation only: no stream URL, provider password or parental grant is saved in activity state. */
internal class SeriesBrowseState {
    data class Selection(val season: Int, val episodeKey: String, val index: Int)

    private val selections = LinkedHashMap<Int, Selection>()
    var focusedSeason: Int? = null
        private set

    fun remember(row: SeriesSeasonRow, index: Int) {
        val saved = selection(row, index) ?: return
        selections[row.season] = saved
        focusedSeason = row.season
    }

    fun indexFor(row: SeriesSeasonRow): Int = indexOf(row, selections[row.season])

    fun rowFor(rows: List<SeriesSeasonRow>): Int =
        rows.indexOfFirst { it.season == focusedSeason }.takeIf { it >= 0 } ?: if (rows.isEmpty()) -1 else 0

    fun adjacent(rows: List<SeriesSeasonRow>, season: Int, delta: Int): SeriesSeasonRow? {
        val current = rows.indexOfFirst { it.season == season }
        return if (current < 0) null else rows.getOrNull(current + delta)
    }

    fun snapshot(): List<Selection> = selections.values.toList()

    fun restore(values: List<Selection>, focused: Int?) {
        selections.clear()
        values.filter { it.index >= 0 && it.episodeKey.matches(Regex("[a-f0-9]{64}")) }
            .forEach { selections[it.season] = it }
        focusedSeason = focused
    }

    companion object {
        fun selection(row: SeriesSeasonRow, index: Int): Selection? = row.episodes.getOrNull(index)?.let {
            Selection(row.season, episodeKey(it), index)
        }

        fun indexOf(row: SeriesSeasonRow, saved: Selection?): Int {
            if (row.episodes.isEmpty()) return -1
            if (saved == null) return 0
            if (saved.index in row.episodes.indices && episodeKey(row.episodes[saved.index]) == saved.episodeKey) return saved.index
            val stable = row.episodes.indexOfFirst { episodeKey(it) == saved.episodeKey }
            return if (stable >= 0) stable else saved.index.coerceIn(row.episodes.indices)
        }

        fun rows(episodes: List<Episode>): List<SeriesSeasonRow> =
            episodes.sortedWith(compareBy({ it.season }, { it.episode }))
                .groupBy { it.season }.map { (season, eps) -> SeriesSeasonRow(season, eps) }

        fun episodeKey(episode: Episode): String = MessageDigest.getInstance("SHA-256")
            .digest("${episode.id}\u0000${episode.url}".toByteArray(Charsets.UTF_8))
            .joinToString("") { "%02x".format(it.toInt() and 0xff) }

        fun playbackQueue(series: MediaItem, row: SeriesSeasonRow): List<MediaItem> = row.episodes.map { episode ->
            MediaItem(
                playlistId = series.playlistId, kind = ItemKind.EPISODE,
                name = "${series.name} S${episode.season} E${episode.episode}" +
                    if (episode.title.isNotBlank() && !episode.title.startsWith(series.name)) " · ${episode.title}" else "",
                group = series.group, logo = episode.artwork.ifBlank { series.logo }, url = episode.url, remoteId = episode.id,
            )
        }
    }
}

/** A failed/early focus attempt must neither consume the target nor accept incidental rail focus. */
internal class SeriesFocusRestore {
    data class Ticket(val generation: Long, val target: SeriesBrowseState.Selection)

    private var generation = 0L
    private var waitingForPause = false
    var pending: Ticket? = null
        private set
    val canAttempt: Boolean get() = pending != null && !waitingForPause

    fun request(target: SeriesBrowseState.Selection, waitForPause: Boolean = false): Ticket {
        waitingForPause = waitForPause
        return Ticket(++generation, target).also { pending = it }
    }

    /** Retain the explicit episode while cancelling callbacks belonging to an older window/layout. */
    fun suspendAttempts() {
        generation++
        pending = pending?.let { Ticket(generation, it.target) }
    }

    fun paused() { waitingForPause = false; suspendAttempts() }

    /** A real touch/key in this screen wins over a pending automatic restoration. */
    fun userInput() { generation++; waitingForPause = false; pending = null }

    fun complete(ticket: Ticket, obtainedFocus: Boolean): Boolean {
        if (waitingForPause || !obtainedFocus || pending != ticket) return false
        pending = null
        return true
    }

    fun rememberFocus(state: SeriesBrowseState, row: SeriesSeasonRow, index: Int): Boolean {
        if (pending != null || index !in row.episodes.indices) return false
        state.remember(row, index)
        return true
    }
}

/** A launch grant is consumed once, and is deliberately unavailable after activity recreation. */
internal class SeriesEntryAccess(private val initialStamp: Int?, private val initialGroup: String, recreated: Boolean) {
    private var initialAvailable = !recreated
    private var grantedStamp: Int? = null
    private var grantedGroup: String? = null

    fun enter(stamp: Int, group: String, locked: Boolean, hidden: Boolean): Boolean {
        val initial = initialAvailable && initialStamp == stamp && initialGroup == group
        initialAvailable = false
        if (hidden) { grantedStamp = null; grantedGroup = null; return false }
        if (!locked || (grantedStamp == stamp && grantedGroup == group) || initial) { authorize(stamp, group); return true }
        grantedStamp = null
        grantedGroup = null
        return false
    }

    fun allows(stamp: Int, group: String, locked: Boolean, hidden: Boolean): Boolean =
        !hidden && (!locked || (grantedStamp == stamp && grantedGroup == group))

    fun authorize(stamp: Int, group: String) { grantedStamp = stamp; grantedGroup = group }
}
