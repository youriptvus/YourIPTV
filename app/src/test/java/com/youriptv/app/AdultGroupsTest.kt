package com.youriptv.app

import com.youriptv.app.data.AdultGroups
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class AdultGroupsTest {
    @Test
    fun recognisesAdultCategories() {
        listOf("For Adults", "ADULT", "Adults Only", "XXX Movies", "VOD | 18+", "+18 Channels", "EN - Porn", "Erotic", "Playboy TV")
            .forEach { assertTrue(it, AdultGroups.isAdult(it)) }
    }

    @Test
    fun ignoresOrdinaryCategories() {
        listOf(
            "AM | USA | ENTERTAINMENT", "VOD | EN - KIDS & FAMILY", "Adult Swim", "AM | USA | ADULT SWIM", "Young Adult Books",
            "VOD | MULTI-LANG 2010 TO 2019", "Channel 180", "Sussex News", "", "Uncategorized",
        ).forEach { assertFalse(it, AdultGroups.isAdult(it)) }
        assertFalse(AdultGroups.isAdult(null))
    }
}
