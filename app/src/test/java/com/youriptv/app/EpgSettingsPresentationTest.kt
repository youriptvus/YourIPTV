package com.youriptv.app

import com.youriptv.app.ui.EpgSettingsPresentation
import org.junit.Assert.*
import org.junit.Test

class EpgSettingsPresentationTest {
    @Test fun wholeDayChoicesPreserveTheExistingHourStorageUnit() {
        for (days in 0..14) {
            val hours = EpgSettingsPresentation.hoursForPastDays(days)
            assertEquals(days * 24, hours)
            assertEquals(days, EpgSettingsPresentation.selectedPastDay(hours))
        }
        assertEquals("None", EpgSettingsPresentation.pastLabel(0))
        assertEquals("1 day", EpgSettingsPresentation.pastLabel(24))
        assertEquals("7 days", EpgSettingsPresentation.pastLabel(168))
        assertEquals("14 days", EpgSettingsPresentation.pastLabel(336))
    }

    @Test fun existingFractionalDayValuesAreShownExactlyWithoutSelectingARoundedChoice() {
        assertEquals("1 hour (custom)", EpgSettingsPresentation.pastLabel(1))
        assertEquals(-1, EpgSettingsPresentation.selectedPastDay(1))
        for (hours in listOf(12, 169, 200, 335)) {
            assertEquals("$hours hours (custom)", EpgSettingsPresentation.pastLabel(hours))
            assertEquals(-1, EpgSettingsPresentation.selectedPastDay(hours))
        }
    }

    @Test fun unsupportedDayChoicesCannotOverflowOrChangeStorage() {
        for (days in listOf(-1, 15, Int.MAX_VALUE))
            assertThrows(IllegalArgumentException::class.java) { EpgSettingsPresentation.hoursForPastDays(days) }
    }

    @Test fun futureCoverageIsLabelledSeparatelyFromPastRetention() {
        assertEquals("1 day", EpgSettingsPresentation.futureLabel(1))
        assertEquals("7 days", EpgSettingsPresentation.futureLabel(7))
        assertEquals("14 days", EpgSettingsPresentation.futureLabel(14))
    }

    @Test fun timeOffsetUsesSingularAndPluralHoursCorrectly() {
        assertEquals("-1 hour", EpgSettingsPresentation.offsetLabel(-1))
        assertEquals("1 hour", EpgSettingsPresentation.offsetLabel(1))
        assertEquals("0 hours", EpgSettingsPresentation.offsetLabel(0))
        assertEquals("-23 hours", EpgSettingsPresentation.offsetLabel(-23))
        assertEquals("23 hours", EpgSettingsPresentation.offsetLabel(23))
    }
}
