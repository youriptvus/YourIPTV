package com.youriptv.app

import com.youriptv.app.data.Catchup
import com.youriptv.app.data.XtreamUrls
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class CatchupTest {
    private val hour = 3_600_000L
    private val now = 1_800_000_000_000L

    @Test
    fun availabilityRespectsArchiveWindow() {
        assertTrue(Catchup.available(2, now - 3 * hour, now - 2 * hour, now))
        assertTrue("currently airing can play from start", Catchup.available(2, now - hour, now + hour, now))
        assertFalse("no archive flag", Catchup.available(0, now - 3 * hour, now - 2 * hour, now))
        assertFalse("future programme", Catchup.available(2, now + hour, now + 2 * hour, now))
        assertFalse("older than archive", Catchup.available(2, now - 49 * hour, now - 48 * hour, now))
        assertTrue(Catchup.available(3, now - 49 * hour, now - 48 * hour, now))
    }

    @Test
    fun durationRoundsUpAndCapsAtNow() {
        assertEquals(60, Catchup.durationMinutes(now - 2 * hour, now - hour, now))
        assertEquals(31, Catchup.durationMinutes(now - 2 * hour, now - 2 * hour + 30 * 60_000 + 1, now))
        assertEquals(30, Catchup.durationMinutes(now - 30 * 60_000, now + hour, now))
    }

    @Test
    fun timeshiftUrlUsesServerTime() {
        // 2027-01-15 12:45 UTC
        val start = 1_800_017_100_000L
        assertEquals("http://h/timeshift/u/p/45/2027-01-15:12-45/71.ts", XtreamUrls.timeshift("h", "u", "p", "71", start, 45))
    }
}
