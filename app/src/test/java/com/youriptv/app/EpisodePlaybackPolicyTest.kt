package com.youriptv.app

import com.youriptv.app.data.EpisodePlaybackPolicy
import com.youriptv.app.data.ItemKind
import com.youriptv.app.data.MediaItem
import com.youriptv.app.data.WatchProgress
import org.junit.Assert.*
import org.junit.Test

class EpisodePlaybackPolicyTest {
    private fun episode(season: Int, number: Int) = MediaItem(playlistId = 7, kind = ItemKind.EPISODE,
        name = "Episode", group = "Series", url = "https://example.test/$season-$number.mp4",
        seriesKey = "stable-series", season = season, episode = number, watchScope = "account")

    @Test fun adjacentEpisodeCrossesSeasonWithoutLoopingAtEitherEnd() {
        val queue = listOf(episode(1, 8), episode(2, 1), episode(2, 2))
        assertEquals(1, EpisodePlaybackPolicy.adjacentIndex(queue, 0, 1))
        assertEquals(0, EpisodePlaybackPolicy.adjacentIndex(queue, 1, -1))
        assertEquals(-1, EpisodePlaybackPolicy.adjacentIndex(queue, 2, 1))
        assertEquals(-1, EpisodePlaybackPolicy.adjacentIndex(queue, 0, -1))
    }

    @Test fun queueCannotAdvanceIntoAnotherSeriesPlaylistOrAccount() {
        val first = episode(1, 1)
        for (other in listOf(episode(1, 2).copy(seriesKey = "other"), episode(1, 2).copy(playlistId = 8),
            episode(1, 2).copy(watchScope = "other"), episode(1, 2).copy(kind = ItemKind.MOVIE)))
            assertEquals(-1, EpisodePlaybackPolicy.adjacentIndex(listOf(first, other), 0, 1))
        assertEquals(-1, EpisodePlaybackPolicy.adjacentIndex(listOf(first.copy(seriesKey = ""), episode(1, 2).copy(seriesKey = "")), 0, 1))
    }

    @Test fun nonEpisodeOrInvalidQueueNeverUsesEpisodeStepping() {
        for (kind in listOf(ItemKind.LIVE, ItemKind.MOVIE, ItemKind.SERIES)) {
            val queue = listOf(episode(1, 1).copy(kind = kind), episode(1, 2))
            assertEquals(-1, EpisodePlaybackPolicy.adjacentIndex(queue, 0, 1))
        }
        assertEquals(-1, EpisodePlaybackPolicy.adjacentIndex(emptyList(), 0, 1))
        assertEquals(-1, EpisodePlaybackPolicy.adjacentIndex(listOf(episode(1, 1)), -1, 1))
        assertEquals(-1, EpisodePlaybackPolicy.adjacentIndex(listOf(episode(1, 1)), 0, 2))
    }

    @Test fun manualNextDoesNotSkipTheSuccessorAlreadySelectedAfterCompletion() {
        val queue = listOf(episode(1, 8), episode(2, 1), episode(2, 2))
        val completed = mapOf(queue[0].url to WatchProgress(100, 100, true, updated = 1))
        assertEquals(1, EpisodePlaybackPolicy.nextFromHistory(queue, completed))
        val unfinished = mapOf(queue[0].url to WatchProgress(50, 100, updated = 1))
        assertEquals(1, EpisodePlaybackPolicy.nextFromHistory(queue, unfinished))
        assertEquals(1, EpisodePlaybackPolicy.nextFromHistory(queue, emptyMap()))
        assertEquals(-1, EpisodePlaybackPolicy.nextFromHistory(emptyList(), emptyMap()))
    }

    @Test fun manualNextHasNoSuccessorAfterFinalCompletedEpisodeAndCannotCrossSources() {
        val queue = listOf(episode(1, 1), episode(1, 2))
        assertEquals(-1, EpisodePlaybackPolicy.nextFromHistory(queue,
            queue.associate { it.url to WatchProgress(100, 100, true, updated = it.episode.toLong()) }))
        assertEquals(-1, EpisodePlaybackPolicy.nextFromHistory(listOf(queue[0], queue[1].copy(watchScope = "other")), emptyMap()))
        assertEquals(-1, EpisodePlaybackPolicy.nextFromHistory(listOf(queue[0], queue[1].copy(watchScope = "other"), episode(1, 3).copy(watchScope = "other")),
            mapOf(queue[0].url to WatchProgress(100, 100, true, updated = 1))))
    }

    @Test fun completeEpisodeCountsDownOnlyWhenAutoplayAndNextAreBothAvailable() {
        assertEquals(EpisodePlaybackPolicy.EndAction.COUNTDOWN, EpisodePlaybackPolicy.endAction(true, true, true))
        assertEquals(EpisodePlaybackPolicy.EndAction.RETURN, EpisodePlaybackPolicy.endAction(true, false, true))
        assertEquals(EpisodePlaybackPolicy.EndAction.RETURN, EpisodePlaybackPolicy.endAction(true, true, false))
        assertEquals(EpisodePlaybackPolicy.EndAction.RETURN, EpisodePlaybackPolicy.endAction(true, false, false))
    }

    @Test fun prematureEndNeverAutoplaysOrSilentlyMarksCompletion() {
        for (enabled in listOf(false, true)) for (next in listOf(false, true))
            assertEquals(EpisodePlaybackPolicy.EndAction.PROMPT, EpisodePlaybackPolicy.endAction(false, enabled, next))
    }

    @Test fun episodeProgressDisplayRepresentsActualFractionAndCompletedState() {
        assertEquals(0, WatchProgress().permille)
        assertEquals(600, WatchProgress(60_000, 100_000).permille)
        assertEquals(1000, WatchProgress(100_000, 100_000, completed = true).permille)
        assertEquals(1000, WatchProgress(completed = true).permille)
        assertEquals(0, WatchProgress(60_000, 0).permille)
    }
}
