package com.youriptv.app

import com.youriptv.app.data.Episode
import com.youriptv.app.data.ItemKind
import com.youriptv.app.data.MediaItem
import com.youriptv.app.ui.SeriesBrowseState
import com.youriptv.app.ui.SeriesEntryAccess
import com.youriptv.app.ui.SeriesFocusRestore
import com.youriptv.app.ui.SeriesSeasonRow
import org.junit.Assert.*
import org.junit.Test

class SeriesBrowseStateTest {
    private fun episode(season: Int, number: Int, id: String = "$season-$number") =
        Episode(id = id, season = season, episode = number, title = "Episode $number", url = "https://example.test/series/$id.mp4")

    @Test fun seasonsAreVerticalSectionsWithEpisodesOrderedWithinEachRail() {
        val rows = SeriesBrowseState.rows(listOf(episode(3, 2), episode(1, 3), episode(3, 1), episode(1, 1)))
        assertEquals(listOf(1, 3), rows.map { it.season })
        assertEquals(listOf(1, 3), rows[0].episodes.map { it.episode })
        assertEquals(listOf(1, 2), rows[1].episodes.map { it.episode })
    }

    @Test fun eachSeasonRemembersItsOwnHorizontalSelection() {
        val rows = SeriesBrowseState.rows((1..2).flatMap { s -> (1..5).map { episode(s, it) } })
        val state = SeriesBrowseState()
        state.remember(rows[0], 3)
        state.remember(rows[1], 1)
        assertEquals(3, state.indexFor(rows[0]))
        assertEquals(1, state.indexFor(rows[1]))
        assertEquals(1, state.rowFor(rows))
        assertEquals(rows[0], state.adjacent(rows, 2, -1))
        assertNull(state.adjacent(rows, 2, 1))
        assertNull(state.adjacent(rows, 1, -1))
    }

    @Test fun recreationRestoresSelectionByIdentityEvenIfProviderInsertsAnEpisode() {
        val old = SeriesSeasonRow(2, listOf(episode(2, 1), episode(2, 3)))
        val state = SeriesBrowseState()
        state.remember(old, 1)
        val recreated = SeriesBrowseState()
        recreated.restore(state.snapshot(), state.focusedSeason)
        assertEquals(2, recreated.indexFor(SeriesSeasonRow(2, listOf(episode(2, 1), episode(2, 2), episode(2, 3)))))
        assertEquals(2, recreated.focusedSeason)
    }

    @Test fun removedSelectionClampsSafelyAndEmptyCatalogHasNoFocusTarget() {
        val state = SeriesBrowseState()
        state.remember(SeriesSeasonRow(1, (1..4).map { episode(1, it) }), 3)
        assertEquals(0, state.indexFor(SeriesSeasonRow(1, listOf(episode(1, 8)))))
        assertEquals(-1, state.indexFor(SeriesSeasonRow(1, emptyList())))
        assertEquals(-1, state.rowFor(emptyList()))
        assertNull(state.adjacent(emptyList(), 1, 1))
    }

    @Test fun savedNavigationContainsOpaqueKeysRatherThanM3uProviderUrls() {
        val credentialUrl = "https://example.test/series/test-user/test-password/42.mp4"
        val row = SeriesSeasonRow(1, listOf(episode(1, 1, credentialUrl).copy(url = credentialUrl)))
        val state = SeriesBrowseState()
        state.remember(row, 0)
        val value = state.snapshot().single()
        assertTrue(value.episodeKey.matches(Regex("[a-f0-9]{64}")))
        assertFalse(value.toString().contains("test-user"))
        assertFalse(value.toString().contains("test-password"))
        assertEquals(0, state.indexFor(row))
    }

    @Test fun malformedSavedSelectionCannotChooseAnArbitraryEpisodeKey() {
        val state = SeriesBrowseState()
        state.restore(listOf(SeriesBrowseState.Selection(1, "raw-provider-url", 8)), 1)
        assertTrue(state.snapshot().isEmpty())
        assertEquals(0, state.indexFor(SeriesSeasonRow(1, listOf(episode(1, 1)))))
    }

    @Test fun playbackQueueKeepsSelectedSeasonUrlsOrderAndProviderIdentity() {
        val series = MediaItem(playlistId = 7, kind = ItemKind.SERIES, name = "Example", group = "Drama", logo = "cover", url = "series:7")
        val row = SeriesSeasonRow(2, listOf(episode(2, 1).copy(artwork = "episode-art"), episode(2, 2)))
        val queue = SeriesBrowseState.playbackQueue(series, row)
        assertEquals(row.episodes.map { it.url }, queue.map { it.url })
        assertEquals(row.episodes.map { it.id }, queue.map { it.remoteId })
        assertTrue(queue.all { it.playlistId == 7L && it.group == "Drama" && it.kind == ItemKind.EPISODE })
        assertEquals(listOf("episode-art", "cover"), queue.map { it.logo })
    }

    @Test fun onlyAFreshMatchingParentGrantCanOpenALockedSeries() {
        val access = SeriesEntryAccess(10, "Locked", recreated = false)
        assertTrue(access.enter(10, "Locked", locked = true, hidden = false))
        assertTrue(access.allows(10, "Locked", locked = true, hidden = false))
        assertFalse(access.allows(11, "Locked", locked = true, hidden = false))
        assertFalse(access.enter(11, "Locked", locked = true, hidden = false))
    }

    @Test fun restoredIntentDoesNotRestoreParentalAuthorization() {
        val recreated = SeriesEntryAccess(10, "Locked", recreated = true)
        assertFalse(recreated.enter(10, "Locked", locked = true, hidden = false))
        recreated.authorize(10, "Locked")
        assertTrue(recreated.allows(10, "Locked", locked = true, hidden = false))
    }

    @Test fun hiddenContentNeverOpensEvenWithACurrentGrant() {
        val access = SeriesEntryAccess(10, "Locked", recreated = false)
        assertFalse(access.enter(10, "Locked", locked = true, hidden = true))
        access.authorize(10, "Locked")
        assertFalse(access.allows(10, "Locked", locked = true, hidden = true))
    }

    @Test fun providerMovingASeriesToAnotherLockedGroupNeedsANewGrant() {
        val access = SeriesEntryAccess(10, "Original", recreated = false)
        assertFalse(access.enter(10, "Different", locked = true, hidden = false))
        access.authorize(10, "Original")
        assertFalse(access.allows(10, "Different", locked = true, hidden = false))
        assertFalse(access.enter(10, "Different", locked = true, hidden = false))
    }

    @Test fun explicitPlaybackEpisodeCannotBeReplacedByIncidentalRailFocusDuringReturn() {
        val row = SeriesSeasonRow(1, (1..5).map { episode(1, it) })
        val state = SeriesBrowseState().apply { remember(row, 2) }
        val restore = SeriesFocusRestore()
        val original = SeriesBrowseState.selection(row, 2)!!
        restore.request(original, waitForPause = true)
        assertFalse(restore.rememberFocus(state, row, 1)) // Previously observed stale E2 focus.
        restore.paused()
        restore.suspendAttempts() // A later window-focus transition must preserve the same explicit E3.
        assertFalse(restore.rememberFocus(state, row, 0))
        assertEquals(original, restore.pending!!.target)
        assertEquals(2, state.indexFor(row))
        assertEquals(1, state.focusedSeason)
    }

    @Test fun failedFocusAndWindowLossKeepTargetButInvalidateOldCallbacks() {
        val row = SeriesSeasonRow(2, (1..3).map { episode(2, it) })
        val restore = SeriesFocusRestore()
        val first = restore.request(SeriesBrowseState.selection(row, 1)!!)
        assertFalse(restore.complete(first, obtainedFocus = false))
        assertEquals(first, restore.pending)
        restore.suspendAttempts()
        val ready = restore.pending!!
        assertEquals(first.target, ready.target)
        assertNotEquals(first.generation, ready.generation)
        assertFalse(restore.complete(first, obtainedFocus = true))
        assertEquals(ready, restore.pending)
        assertTrue(restore.complete(ready, obtainedFocus = true))
        assertNull(restore.pending)
    }

    @Test fun playbackLaunchDoesNotConsumeReturnTargetBeforeTheActivityActuallyPauses() {
        val row = SeriesSeasonRow(1, listOf(episode(1, 3)))
        val restore = SeriesFocusRestore()
        val launching = restore.request(SeriesBrowseState.selection(row, 0)!!, waitForPause = true)
        assertFalse(restore.canAttempt)
        assertFalse(restore.complete(launching, obtainedFocus = true)) // The episode is still focused before launch.
        restore.suspendAttempts() // Dialog/window loss alone is not a return from playback.
        assertFalse(restore.canAttempt)
        restore.paused()
        assertTrue(restore.canAttempt)
        assertEquals(launching.target, restore.pending!!.target)
        assertTrue(restore.complete(restore.pending!!, obtainedFocus = true))
    }

    @Test fun newTouchOrRemoteInputWinsOverAQueuedReturnCallback() {
        val row = SeriesSeasonRow(1, (1..4).map { episode(1, it) })
        val state = SeriesBrowseState().apply { remember(row, 2) }
        val restore = SeriesFocusRestore()
        val returning = restore.request(SeriesBrowseState.selection(row, 2)!!)
        restore.userInput()
        assertTrue(restore.rememberFocus(state, row, 3))
        assertFalse(restore.complete(returning, obtainedFocus = true))
        assertEquals(3, state.indexFor(row))
        assertNull(restore.pending)
    }

    @Test fun aNewSeasonRequestCannotBeCompletedByAnOlderSeasonCallback() {
        val rows = SeriesBrowseState.rows((1..2).flatMap { season -> (1..4).map { episode(season, it) } })
        val restore = SeriesFocusRestore()
        val old = restore.request(SeriesBrowseState.selection(rows[0], 2)!!)
        val latest = restore.request(SeriesBrowseState.selection(rows[1], 1)!!)
        assertFalse(restore.complete(old, obtainedFocus = true))
        assertEquals(2, restore.pending!!.target.season)
        assertEquals(1, SeriesBrowseState.indexOf(rows[1], latest.target))
        assertTrue(restore.complete(latest, obtainedFocus = true))
    }

    @Test fun immutableReturnTargetFindsSameEpisodeAfterOptionalMetadataReordersTheRail() {
        val old = SeriesSeasonRow(1, listOf(episode(1, 1), episode(1, 3)))
        val restore = SeriesFocusRestore()
        val chosen = SeriesBrowseState.selection(old, 1)!!
        restore.request(chosen)
        val refreshed = SeriesSeasonRow(1, listOf(episode(1, 1), episode(1, 2), episode(1, 3)))
        assertEquals(2, SeriesBrowseState.indexOf(refreshed, restore.pending!!.target))
        assertEquals(chosen, restore.pending!!.target)
        assertEquals(1, chosen.index) // Resolving a new position never mutates the captured identity/index.
    }
}
