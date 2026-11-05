package com.youriptv.app

import com.youriptv.app.data.Program
import com.youriptv.app.ui.GuideNavigation
import org.junit.Assert.assertEquals
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
}
