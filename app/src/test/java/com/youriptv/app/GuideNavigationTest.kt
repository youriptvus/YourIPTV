package com.youriptv.app

import com.youriptv.app.data.Program
import com.youriptv.app.ui.GuideNavigation
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class GuideNavigationTest {
    private val minute = 60_000L
    private val programs = listOf(
        Program("channel", 0, 60 * minute, "First"),
        Program("channel", 60 * minute, 120 * minute, "Second"),
        Program("channel", 150 * minute, 180 * minute, "Third"),
    )

    @Test fun movesToAdjacentProgrammeOrGap() {
        assertEquals(120 * minute, GuideNavigation.targetTime(90 * minute, programs, true, 0, 240 * minute))
        assertEquals(0, GuideNavigation.targetTime(90 * minute, programs, false, 0, 240 * minute))
        assertEquals(150 * minute, GuideNavigation.targetTime(120 * minute, programs, true, 0, 240 * minute))
    }

    @Test fun clampsGuideBoundsAndPagesWindow() {
        assertEquals(0, GuideNavigation.targetTime(0, emptyList(), false, 0, 240 * minute))
        assertEquals(240 * minute, GuideNavigation.targetTime(240 * minute, emptyList(), true, 0, 240 * minute))
        assertEquals(30 * minute, GuideNavigation.windowFor(0, 150 * minute, 150 * minute))
        assertEquals(0, GuideNavigation.windowFor(60 * minute, 0, 150 * minute))
    }

    @Test fun explicitOpeningSelectsNowAndItsCurrentProgramme() {
        val now = 97 * minute + 15_123
        val opening = GuideNavigation.openingPosition(now)
        assertEquals(now, opening.focusTime)
        assertEquals(90 * minute, opening.windowStart)
        assertEquals("Second", GuideNavigation.programAt(programs, opening.focusTime)?.title)
    }

    @Test fun openingAtAProgrammeBoundaryDoesNotSelectTheFinishedProgramme() {
        val boundary = GuideNavigation.openingPosition(60 * minute)
        assertEquals(60 * minute, boundary.windowStart)
        assertEquals("Second", GuideNavigation.programAt(programs, boundary.focusTime)?.title)
        assertNull(GuideNavigation.programAt(programs, GuideNavigation.openingPosition(120 * minute).focusTime))
    }

    @Test fun openingUsesTheNewClockAfterAnOvernightAbsenceWithoutChangingBrowsingPaging() {
        val original = GuideNavigation.openingPosition(97 * minute)
        val nextDay = GuideNavigation.openingPosition(24 * 60 * minute + 158 * minute)
        assertEquals(24 * 60 * minute + 158 * minute, nextDay.focusTime)
        assertEquals(24 * 60 * minute + 150 * minute, nextDay.windowStart)
        // Ordinary refresh/navigation retains a browsed future window; only explicit entry uses openingPosition.
        val futureWindow = original.windowStart + 6 * 60 * minute
        assertEquals(futureWindow, GuideNavigation.windowFor(futureWindow, futureWindow + minute, 120 * minute))
    }
}
