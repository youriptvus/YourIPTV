package com.youriptv.app

import com.youriptv.app.ui.CatalogViewToken
import org.junit.Assert.*
import org.junit.Test
import java.io.File

class MainRefreshPublicationTest {
    private val source = listOf(File("src/main/java/com/youriptv/app/ui/MainActivity.kt"),
        File("app/src/main/java/com/youriptv/app/ui/MainActivity.kt")).first { it.exists() }.readText()
    private val requested = CatalogViewToken(7, "TV", "group:Family", 4, 12)

    @Test
    fun olderReadCannotOverwriteNavigationEvenWhenUserReturnsToTheSameCategory() {
        val navigatedAway = requested.copy(group = "group:Sports", loadGeneration = 5)
        val navigatedBack = requested.copy(loadGeneration = 6)
        assertFalse(requested.mayPublishInto(navigatedAway))
        assertFalse(requested.mayPublishInto(navigatedBack))
        assertTrue(navigatedBack.mayPublishInto(navigatedBack))
    }

    @Test
    fun changedPlaylistSectionOrParentalStateRejectsDeferredRows() {
        assertFalse(requested.mayPublishInto(requested.copy(playlistId = 8)))
        assertFalse(requested.mayPublishInto(requested.copy(section = "MOVIES")))
        assertFalse(requested.mayPublishInto(requested.copy(restrictionStamp = 13)))
        assertTrue(requested.mayPublishInto(requested.copy()))
    }

    @Test
    fun refreshKeepsLockedPlaceholderAndNeverTurnsARefreshIntoParentalAuthorization() {
        val refresh = source.substringAfter("private suspend fun refreshCatalogInPlace(").substringBefore("private suspend fun refreshGuideInPlace(")
        assertTrue(refresh.contains("loadedGroup != selectedGroup || shownStamp != app.settings.restrictionStamp()"))
        assertTrue(refresh.contains("val rows = if (keepLockedPlaceholder) emptyList()"))
        assertTrue(refresh.contains("loadedGroup = if (keepLockedPlaceholder) -1 else next"))
        assertTrue(refresh.contains("showLockedGroup(next, entries[next])"))
        for (forbidden in listOf("requirePin(", "flow.authenticate", "allowParental", "Nav.play(", "livePlayback."))
            assertFalse("A data publication cannot acquire authority or change playback: $forbidden", refresh.contains(forbidden))
    }

    @Test
    fun committedPublicationJoinsOrdinaryReadsRetriesDiscardedResultsAndMarksOnlyAppliedGenerations() {
        val observer = source.substringAfter("private fun observeRefreshes()").substringBefore("private suspend fun refreshCatalogInPlace(")
        assertTrue(observer.contains("while (loadJob?.isActive == true) loadJob?.join()"))
        assertTrue(observer.contains("while (playlist?.id == id)"))
        assertTrue(observer.contains("if (applied) observedRefreshGenerations[id] = generation"))
        val refresh = source.substringAfter("private suspend fun refreshCatalogInPlace(").substringBefore("private suspend fun refreshGuideInPlace(")
        assertTrue(refresh.contains("!token.mayPublishInto(catalogViewToken())"))
        assertTrue(refresh.contains("app.refreshCoordinator.generations.value[p.id] != generation"))
        val ordinary = source.substringAfter("private fun loadItems(").substringBefore("private fun refreshRecordingsInPlace()")
        assertTrue(ordinary.contains("val requestGeneration = ++listLoadGeneration"))
        assertTrue(ordinary.contains("requestGeneration != listLoadGeneration"))
    }

    @Test
    fun parentalCallbackResolvesItsOriginalCategoryAfterProviderReordering() {
        val select = source.substringAfter("private fun selectGroup(").substringBefore("private fun loadItems(")
        assertTrue(select.contains("val currentPosition = groupEntries.indexOfFirst { groupKey(it) == selectedKey }"))
        assertTrue(select.contains("selectionGeneration != groupSelectionGeneration"))
        assertTrue(select.contains("restrictionStamp != app.settings.restrictionStamp()"))
        assertTrue(select.contains("loadedGroup = currentPosition"))
        assertFalse(select.contains("loadedGroup = pos"))
    }
}
