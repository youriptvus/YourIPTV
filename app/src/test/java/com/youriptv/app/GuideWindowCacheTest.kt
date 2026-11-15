package com.youriptv.app

import com.youriptv.app.data.ItemKind
import com.youriptv.app.data.MediaItem
import com.youriptv.app.data.Program
import com.youriptv.app.data.GuideWindowResult
import com.youriptv.app.ui.GuideProgramCache
import org.junit.Assert.*
import org.junit.Test

class GuideWindowCacheTest {
    private val hour = 3_600_000L
    private val day = 24 * hour
    private val base = 1_800_000_000_000L.let { it - it % (30 * 60_000L) }
    private fun channel(id: String) = MediaItem(playlistId = 1, kind = ItemKind.LIVE, name = id,
        group = "Fixture", url = "http://fixture.invalid/$id", tvgId = id)

    @Test fun farFutureWindowIsRetrievedWithoutLoadingFourteenDaysOfHistory() {
        val fullGuide = (0 until 672).map { i ->
            Program("a", base - 7 * day + i * hour / 2, base - 7 * day + (i + 1) * hour / 2, "Programme $i")
        }
        val queries = mutableListOf<Pair<Long, Long>>()
        val cache = GuideProgramCache({ _, from, to ->
            queries += from to to
            GuideWindowResult(fullGuide.filter { it.end > from && it.start < to })
        })
        val visibleFrom = base + 6 * day
        val programmes = cache.programsFor(channel("a"), visibleFrom, visibleFrom + 2 * hour)
        assertTrue(programmes.any { it.title == "Programme 624" })
        assertTrue(programmes.any { it.start == visibleFrom + 3 * hour / 2 })
        assertEquals(6 * hour, queries.single().second - queries.single().first)
        assertTrue(programmes.size < 20)
    }

    @Test fun neighbouringTimeNavigationReusesPrefetchAndLaterPageLoadsANewWindow() {
        var queries = 0
        val cache = GuideProgramCache({ ch, from, to -> queries++; GuideWindowResult(listOf(Program(ch.tvgId, from, to, "Fixture"))) })
        val ch = channel("a")
        cache.programsFor(ch, base, base + 2 * hour)
        cache.programsFor(ch, base + hour / 2, base + 5 * hour / 2)
        assertEquals(1, queries)
        val later = cache.programsFor(ch, base + day, base + day + 2 * hour)
        assertEquals(2, queries)
        assertTrue(later.single().start > base)
    }

    @Test fun channelLruEvictsOldestWhileRecentlyVisibleChannelRemainsWarm() {
        val reads = mutableMapOf<String, Int>()
        val cache = GuideProgramCache({ ch, from, to ->
            reads[ch.tvgId] = (reads[ch.tvgId] ?: 0) + 1
            GuideWindowResult(listOf(Program(ch.tvgId, from, to, "Fixture")))
        }, maxChannels = 2)
        val a = channel("a"); val b = channel("b"); val c = channel("c")
        cache.programsFor(a, base, base + hour)
        cache.programsFor(b, base, base + hour)
        cache.programsFor(a, base, base + hour)
        cache.programsFor(c, base, base + hour)
        cache.programsFor(a, base, base + hour)
        cache.programsFor(b, base, base + hour)
        assertEquals(1, reads["a"])
        assertEquals(2, reads["b"])
    }

    @Test fun denseWindowExplicitlySignalsFallbackRatherThanAnOldestProgrammeTail() {
        var queries = 0
        val cache = GuideProgramCache({ _, _, _ -> queries++; GuideWindowResult(emptyList(), dense = true) })
        val first = cache.windowFor(channel("a"), base, base + 2 * hour)
        assertTrue(first.dense)
        assertTrue(first.programs.isEmpty())
        assertTrue(cache.windowFor(channel("a"), base + hour, base + 2 * hour).dense)
        assertEquals(1, queries)
    }

    @Test fun largeDescriptionsDoNotAccumulateInLru() {
        var queries = 0
        val cache = GuideProgramCache({ ch, from, to ->
            queries++; GuideWindowResult(listOf(Program(ch.tvgId, from, to, "Title", "x".repeat(101))))
        }, maxCharacters = 100)
        cache.programsFor(channel("a"), base, base + hour)
        cache.programsFor(channel("a"), base, base + hour)
        assertEquals(2, queries)
    }

    @Test fun aggregateProgrammeBudgetEvictsEvenWhenChannelCountIsSmall() {
        val reads = mutableMapOf<String, Int>()
        val cache = GuideProgramCache({ ch, from, to ->
            reads[ch.tvgId] = (reads[ch.tvgId] ?: 0) + 1
            GuideWindowResult(listOf(Program(ch.tvgId, from, to, "One"), Program(ch.tvgId, from, to, "Two")))
        }, maxChannels = 48, maxProgrammes = 3)
        cache.programsFor(channel("a"), base, base + hour)
        cache.programsFor(channel("b"), base, base + hour)
        cache.programsFor(channel("a"), base, base + hour)
        assertEquals(2, reads["a"])
    }

    @Test fun timeShiftConvertsWindowForProviderAndProgrammesBackForDisplay() {
        var requested: Pair<Long, Long>? = null
        val shift = 3 * hour
        val loader = GuideProgramCache.shiftedLoader({ ch, from, to ->
            requested = from to to
            GuideWindowResult(listOf(Program(ch.tvgId, base - shift, base - shift + hour, "Shifted")))
        }, { shift })
        val programmes = loader(channel("a"), base, base + 2 * hour)
        assertEquals(base - shift to base + 2 * hour - shift, requested)
        assertEquals(base, programmes.programs.single().start)
        assertEquals(base + hour, programmes.programs.single().end)
    }

    @Test fun programmeCrossingWindowBoundaryIsNotLost() {
        val longProgramme = Program("a", base - 8 * hour, base + 8 * hour, "Long programme")
        val cache = GuideProgramCache({ _, _, _ -> GuideWindowResult(listOf(longProgramme)) })
        assertEquals(longProgramme, cache.programsFor(channel("a"), base, base + hour).single())
    }
}
