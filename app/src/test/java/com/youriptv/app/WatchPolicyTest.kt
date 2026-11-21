package com.youriptv.app

import com.youriptv.app.data.*
import com.youriptv.app.ui.SeriesBrowseState
import org.junit.Assert.*
import org.junit.Test

class WatchPolicyTest {
    private val source = Playlist(id = 4, name = "Source", type = SourceType.XTREAM, url = "https://example.test", username = "one")
    private fun movie(url: String = "https://example.test/movie/one/secret/8.mp4", id: String = "8") =
        MediaItem(playlistId = source.id, kind = ItemKind.MOVIE, name = "Same title", group = "Movies", url = url, remoteId = id)

    @Test fun accountAndPlaylistBoundariesAreIndependentOfDisplayLabelsAndPasswordRotation() {
        assertEquals(WatchPolicy.scope(source), WatchPolicy.scope(source.copy(name = "Renamed", password = "rotated")))
        assertNotEquals(WatchPolicy.scope(source), WatchPolicy.scope(source.copy(username = "two")))
        assertNotEquals(WatchPolicy.scope(source), WatchPolicy.scope(source.copy(id = 5)))
        assertNotEquals(WatchPolicy.scope(source), WatchPolicy.scope(source.copy(url = "https://other.test")))
    }
    @Test fun providerIdentitySurvivesRefreshRowAndStreamTokenChanges() {
        val first = movie()
        assertEquals(WatchPolicy.key(source, first), WatchPolicy.key(source, first.copy(id = 90, name = "Renamed", url = "https://cdn.test/new-token/8.mkv")))
        assertNotEquals(WatchPolicy.key(source, first), WatchPolicy.key(source, movie(id = "9")))
        assertNotEquals(WatchPolicy.key(source, first), WatchPolicy.key(source, first.copy(kind = ItemKind.EPISODE)))
    }
    @Test fun m3uIdentityKeepsMeaningfulQueryAndSeparatesSameTitles() {
        val m3u = source.copy(type = SourceType.M3U)
        val first = movie("https://cdn.test/video?id=8&token=old&password=old")
        assertEquals(WatchPolicy.key(m3u, first), WatchPolicy.key(m3u, first.copy(url = "https://cdn.test/video?password=new&token=new&id=8")))
        assertNotEquals(WatchPolicy.key(m3u, first), WatchPolicy.key(m3u, first.copy(url = "https://cdn.test/video?id=9")))
        assertFalse(WatchPolicy.key(m3u, first).contains("old"))
    }
    @Test fun stoppingInCreditsNeverMarksCompleted() {
        assertFalse(WatchPolicy.completed(3_599_000, 3_600_000, false))
        assertTrue(WatchPolicy.completed(3_599_000, 3_600_000, true))
        assertFalse(WatchPolicy.completed(3_500_000, 3_600_000, true))
    }
    @Test fun shortAndUnknownDurationNeedReliableEndEvidence() {
        assertFalse(WatchPolicy.completed(800, 1000, true))
        assertFalse(WatchPolicy.completed(980, 1000, true))
        assertTrue(WatchPolicy.completed(995, 1000, true))
        assertFalse(WatchPolicy.completed(100_000, -1, true))
        assertFalse(WatchPolicy.completed(0, 0, true))
    }
    @Test fun stoppedNearEndAndShortClipRemainResumable() {
        assertEquals(3599000L, WatchProgress(3599000, 3600000).resume)
        assertEquals(500L, WatchProgress(500, 1000).resume)
        assertEquals(0L, WatchProgress(500, 1000, true).resume)
        assertEquals(0, WatchProgress(1000, 0).permille)
    }
    @Test fun acrossSeasonQueueIsOrderedAndCarriesExactParentAndEpisode() {
        val parent = movie().copy(kind = ItemKind.SERIES, name = "Series")
        fun ep(s: Int, e: Int) = Episode("$s-$e", s, e, "Episode", "https://example.test/$s-$e.mp4")
        val queue = SeriesBrowseState.playbackQueue(parent, listOf(ep(2, 2), ep(1, 2), ep(2, 1), ep(1, 1)))
        assertEquals(listOf(1 to 1, 1 to 2, 2 to 1, 2 to 2), queue.map { it.season to it.episode })
        assertEquals(1, queue.map { it.seriesKey }.distinct().size)
        assertTrue(queue.all { it.seriesName == "Series" && it.seriesRemoteId == "8" && it.seriesUrl == parent.url })
    }
    @Test fun resumeChoosesActualEpisodeAndCompletedChoosesNextAcrossSeasons() {
        val queue = (1..4).map { movie(id = "$it").copy(url = "https://example.test/$it", kind = ItemKind.EPISODE) }
        val saved = mapOf(queue[2].url to WatchProgress(30_000, 90_000, false, 10))
        assertEquals(2, WatchPolicy.continueIndex(queue, saved))
        assertEquals(3, WatchPolicy.continueIndex(queue, saved.mapValues { it.value.copy(completed = true) }))
        assertEquals(-1, WatchPolicy.continueIndex(emptyList(), emptyMap()))
    }
    @Test fun continueWatchingDeduplicatesSeriesAndOmitsFinishedMovies() {
        val rows = listOf(
            WatchEntry("a", movie().copy(seriesKey = "series"), WatchProgress(5000, 10000, false, 5)),
            WatchEntry("b", movie().copy(seriesKey = "series"), WatchProgress(6000, 10000, false, 6)),
            WatchEntry("c", movie(id = "9"), WatchProgress(10000, 10000, true, 7)),
            WatchEntry("d", movie(id = "10"), WatchProgress(0, 10000, false, 8)))
        assertEquals(listOf("b"), WatchPolicy.continuation(rows).map { it.key })
    }
    @Test fun liveCatchupAndLocalFilesDoNotEnterVodProgress() {
        assertTrue(WatchPolicy.supports(movie()))
        assertFalse(WatchPolicy.supports(movie().copy(kind = ItemKind.LIVE)))
        assertFalse(WatchPolicy.supports(movie().copy(isCatchup = true)))
        assertFalse(WatchPolicy.supports(movie().copy(isLocal = true)))
    }
    @Test fun episodeStartedAtZeroIsRememberedButExplicitlyClearedHistoryIsNot() {
        val queue = listOf(movie().copy(url = "a"), movie().copy(url = "b"))
        assertEquals(1, WatchPolicy.continueIndex(queue, mapOf("b" to WatchProgress(0, 6000, false, 10))))
        assertEquals(0, WatchPolicy.continueIndex(queue, mapOf("b" to WatchProgress(0, 0, false, 0))))
    }
    @Test fun refreshKeepsPlaybackAccountAndEpisodeContextButUsesCurrentCategory() {
        val old = movie().copy(kind = ItemKind.EPISODE, watchScope = "old-account", seriesKey = "parent", season = 2, episode = 4)
        val fresh = old.copy(id = 999, watchScope = "", seriesKey = "", season = 0, episode = 0, group = "New category")
        val merged = WatchPolicy.refreshed(old, fresh)
        assertEquals("old-account", merged.watchScope)
        assertEquals("parent", merged.seriesKey)
        assertEquals(2, merged.season); assertEquals(4, merged.episode)
        assertEquals("New category", merged.group); assertEquals(999L, merged.id)
    }
    @Test fun episodeFocusSurvivesSignedUrlRotationAndInsertedEpisodes() {
        val old = Episode("21", 1, 2, "Episode", "https://cdn.test/old.mp4?token=old")
        val key = SeriesBrowseState.episodeKey(old)
        assertEquals(key, SeriesBrowseState.episodeKey(old.copy(url = "https://cdn.test/new.mp4?token=new")))
        assertNotEquals(key, SeriesBrowseState.episodeKey(old.copy(id = "22")))
    }
    @Test fun opaqueTokenOnlyMediaAndAccountsAreNeverMerged() {
        val m3u = source.copy(type = SourceType.M3U, username = "", url = "https://example.test/list?token=account-one")
        assertNotEquals(WatchPolicy.scope(m3u), WatchPolicy.scope(m3u.copy(url = "https://example.test/list?token=account-two")))
        val first = movie("https://example.test/watch?token=media-one", "")
        assertNotEquals(WatchPolicy.key(m3u, first), WatchPolicy.key(m3u, first.copy(url = "https://example.test/watch?token=media-two")))
    }
    @Test fun m3uPasswordRotationRequiresAnIdentifiableAccount() {
        val m3u = source.copy(type = SourceType.M3U, username = "", url = "https://example.test/get.php?username=one&password=old")
        assertEquals(WatchPolicy.scope(m3u), WatchPolicy.scope(m3u.copy(url = "https://example.test/get.php?password=new&username=one")))
        assertNotEquals(WatchPolicy.scope(m3u), WatchPolicy.scope(m3u.copy(url = "https://example.test/get.php?username=two&password=old")))
    }
    @Test fun sameNamedM3uSeriesInDifferentCategoriesKeepSeparateParents() {
        val first = movie().copy(kind = ItemKind.SERIES, name = "Show", remoteId = "Show", group = "A", url = "m3u-series:A|Show")
        val second = first.copy(group = "B", url = "m3u-series:B|Show")
        val episode = Episode("https://example.test/1.mp4", 1, 1, "Episode", "https://example.test/1.mp4")
        assertNotEquals(SeriesBrowseState.playbackQueue(first, listOf(episode)).single().seriesKey,
            SeriesBrowseState.playbackQueue(second, listOf(episode)).single().seriesKey)
    }
    @Test fun mediaBasicAuthUsersAreDistinctAndKnownContentSurvivesPasswordRotation() {
        val m3u = source.copy(type = SourceType.M3U)
        val first = movie("https://one:old@example.test/8.mp4", "")
        assertEquals(WatchPolicy.key(m3u, first), WatchPolicy.key(m3u, first.copy(url = "https://one:new@example.test/8.mp4")))
        assertNotEquals(WatchPolicy.key(m3u, first), WatchPolicy.key(m3u, first.copy(url = "https://two:old@example.test/8.mp4")))
    }
    @Test fun mostRecentUnfinishedWinsEvenWhenANewerEpisodeWasCompleted() {
        val queue = (1..4).map { movie(id = "$it").copy(url = "episode-$it", kind = ItemKind.EPISODE) }
        val progress = mapOf(queue[1].url to WatchProgress(20_000, 90_000, false, 10),
            queue[2].url to WatchProgress(90_000, 90_000, true, 20))
        assertEquals(1, WatchPolicy.continueIndex(queue, progress))
    }
    @Test fun completedFinalEpisodeNeverWrapsToEarlierUnwatchedEpisode() {
        val queue = (1..3).map { movie(id = "$it").copy(url = "episode-$it", kind = ItemKind.EPISODE) }
        val progress = mapOf(queue[2].url to WatchProgress(90_000, 90_000, true, 20))
        assertEquals(2, WatchPolicy.continueIndex(queue, progress))
    }

}
