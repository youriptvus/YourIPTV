package com.youriptv.app

import com.youriptv.app.ui.PreviewLoadingHint
import org.junit.Assert.*
import org.junit.Test

class PreviewLoadingHintTest {
    private class Scheduler {
        val posted = mutableListOf<Pair<Runnable, Long>>()
        val removed = mutableListOf<Runnable>()
        var notifications = 0
        val hint = PreviewLoadingHint(
            postDelayed = { task, delay -> posted.add(task to delay) },
            remove = { task -> removed.add(task) },
            onSlow = { notifications++ },
        )
    }

    @Test
    fun bufferingSchedulesOneDelayedHint() {
        val s = Scheduler()
        s.hint.start()
        s.hint.start()
        assertEquals(1, s.posted.size)
        assertEquals(20_000L, s.posted.single().second)
        assertFalse(s.hint.isSlow)
        s.posted.single().first.run()
        assertTrue(s.hint.isSlow)
        assertEquals(1, s.notifications)
        s.hint.start()
        s.posted.single().first.run()
        assertEquals(1, s.posted.size)
        assertEquals(1, s.notifications)
    }

    @Test
    fun resetCancelsAndInvalidatesAnOldCallback() {
        val s = Scheduler()
        s.hint.start()
        val old = s.posted.single().first
        s.hint.reset()
        assertEquals(listOf(old), s.removed)
        s.hint.start()
        old.run()
        assertFalse(s.hint.isSlow)
        assertEquals(0, s.notifications)
        s.posted.last().first.run()
        assertTrue(s.hint.isSlow)
        assertEquals(1, s.notifications)
    }

    @Test
    fun resetAfterSlowLoadingStartsAFreshInterval() {
        val s = Scheduler()
        s.hint.start()
        s.posted.single().first.run()
        s.hint.reset()
        assertFalse(s.hint.isSlow)
        s.hint.start()
        assertEquals(2, s.posted.size)
        s.posted.last().first.run()
        assertEquals(2, s.notifications)
    }
}
