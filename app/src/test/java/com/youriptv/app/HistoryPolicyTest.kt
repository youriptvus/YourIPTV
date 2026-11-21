package com.youriptv.app

import com.youriptv.app.data.*
import org.junit.Assert.*
import org.junit.Test

class HistoryPolicyTest {
    private fun episode(parent: String = "10", number: Int = 1, playlist: Long = 4) = MediaItem(
        playlistId = playlist, kind = ItemKind.EPISODE, name = "Show S1 E$number", group = "Drama",
        logo = "episode-art", url = "https://example.test/$parent/$number.mp4", remoteId = "$parent-$number",
        seriesKey = "series-$parent", seriesName = "Show", seriesUrl = "https://example.test/series/$parent",
        seriesRemoteId = parent, season = 1, episode = number,
    )
    private fun row(item: MediaItem, updated: Long, completed: Boolean = false, position: Long = 30_000) =
        WatchEntry("${item.kind}:${item.remoteId}:${item.url}", item, WatchProgress(position, 120_000, completed, updated))

    @Test fun completedAndPartialEpisodesBecomeOneParentWithLastEpisodeSummary() {
        val first = episode(number = 1)
        val latest = episode(number = 2)
        val values = HistoryPolicy.grouped(sequenceOf(row(first, 10, true), row(latest, 20)), 100)
        assertEquals(1, values.size)
        val parent = values.single()
        assertEquals(ItemKind.SERIES, parent.kind)
        assertEquals("Show", parent.name)
        assertEquals(latest.seriesUrl, parent.url)
        assertEquals("10", parent.remoteId)
        assertEquals(1, parent.season); assertEquals(2, parent.episode)
        assertEquals(30_000L, parent.watch?.position)
        assertFalse(parent.watch!!.completed)
        assertEquals(ItemKind.EPISODE, latest.kind)
    }

    @Test fun currentParentArtworkAndRoutingWinWithoutLosingEpisodeProgress() {
        val old = episode().copy(watch = WatchProgress(45_000, 120_000, false, 20))
        val current = old.copy(kind = ItemKind.SERIES, name = "Updated show", logo = "series-art", group = "Current category",
            url = "https://example.test/new-series/10", remoteId = "10")
        val parent = HistoryPolicy.parent(old, current)
        assertEquals(current.url, parent.url); assertEquals("Updated show", parent.name)
        assertEquals("series-art", parent.logo); assertEquals("Current category", parent.group)
        assertEquals(45_000L, parent.watch?.position); assertEquals(old.episode, parent.episode)
    }

    @Test fun suppliedParentCannotMoveHistoryToAnotherPlaylistOrContentKind() {
        val item = episode().copy(watch = WatchProgress(45_000, 120_000, false, 20))
        val candidate = item.copy(kind = ItemKind.SERIES, name = "Unrelated parent", url = "https://example.test/unrelated")
        val expected = HistoryPolicy.parent(item)
        assertEquals(expected, HistoryPolicy.parent(item, candidate.copy(playlistId = 5)))
        assertEquals(expected, HistoryPolicy.parent(item, candidate.copy(kind = ItemKind.MOVIE)))
        assertEquals(expected, HistoryPolicy.parent(item, candidate.copy(kind = ItemKind.EPISODE)))
        val unidentified = item.copy(seriesUrl = "", seriesKey = "", seriesRemoteId = "")
        assertEquals(unidentified, HistoryPolicy.parent(unidentified, candidate.copy(playlistId = 5)))
    }

    @Test fun heavilyWatchedSeriesDoesNotConsumeTheDistinctTitleLimit() {
        val many = (1..300).asSequence().map { row(episode(number = it), (1000 + it).toLong(), true) }
        val movie = MediaItem(playlistId = 4, kind = ItemKind.MOVIE, name = "Film", group = "Movies", url = "https://example.test/movie/1", remoteId = "1")
        val values = HistoryPolicy.grouped(many + sequenceOf(row(movie, 50)), 2)
        assertEquals(listOf(ItemKind.SERIES, ItemKind.MOVIE), values.map { it.kind })
        assertEquals(300, values.first().episode)
    }

    @Test fun duplicateDisplayTitlesDoNotMergeDifferentSeriesOrMovies() {
        val movie = MediaItem(playlistId = 4, kind = ItemKind.MOVIE, name = "Same title", group = "Movies", url = "https://example.test/film1", remoteId = "1")
        val rows = sequenceOf(row(episode("10"), 10), row(episode("11"), 20), row(movie, 30), row(movie.copy(remoteId = "2", url = "https://example.test/film2"), 40))
        assertEquals(4, HistoryPolicy.grouped(rows, 100).size)
    }

    @Test fun playlistsAndM3uCategoriesRemainSeparateEvenWithMatchingNames() {
        fun m3u(group: String, playlist: Long) = episode(playlist = playlist).copy(
            group = group, seriesUrl = SeriesNames.pseudoUrl(group, "Show"), seriesRemoteId = "Show")
        val rows = sequenceOf(row(m3u("A", 4), 10), row(m3u("B", 4), 20), row(m3u("A", 5), 30))
        assertEquals(3, HistoryPolicy.grouped(rows, 100).size)
    }

    @Test fun missingCatalogueParentRetainsSavedContextAndClearedEntriesStayOut() {
        val item = episode()
        val rows = HistoryPolicy.grouped(sequenceOf(row(item, 20, true), row(episode("11"), 0)), 100)
        assertEquals(1, rows.size)
        assertEquals(item.seriesUrl, rows.single().url)
        assertEquals("10", rows.single().remoteId)
        assertTrue(rows.single().watch!!.completed)
    }

    @Test fun equalTimestampKeepsPreciseProgressBeforeLegacyFallback() {
        val item = episode()
        val precise = row(item, 20, true)
        val legacy = row(item, 20, false)
        assertTrue(HistoryPolicy.grouped(sequenceOf(precise, legacy), 100).single().watch!!.completed)
        assertTrue(HistoryPolicy.grouped(sequenceOf(precise), 0).isEmpty())
    }

    @Test fun unidentifiedLegacyEpisodeIsRetainedInsteadOfAssignedToWrongSameTitleSeries() {
        val legacy = episode().copy(seriesUrl = "", seriesKey = "", seriesName = "", seriesRemoteId = "")
        assertEquals(legacy, HistoryPolicy.parent(legacy))
    }
}
