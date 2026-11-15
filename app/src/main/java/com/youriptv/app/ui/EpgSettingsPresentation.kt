package com.youriptv.app.ui

/** Keep the stored hours exact while presenting ordinary whole-day choices. */
internal object EpgSettingsPresentation {
    fun pastLabel(hours: Int): String = when {
        hours == 0 -> "None"
        hours == 1 -> "1 hour (custom)"
        hours % 24 != 0 -> "$hours hours (custom)"
        hours == 24 -> "1 day"
        else -> "${hours / 24} days"
    }

    fun selectedPastDay(hours: Int): Int =
        (hours / 24).takeIf { hours % 24 == 0 && it in 0..14 } ?: -1

    fun hoursForPastDays(days: Int): Int {
        require(days in 0..14)
        return days * 24
    }

    fun futureLabel(days: Int) = if (days == 1) "1 day" else "$days days"

    fun offsetLabel(hours: Int) = if (hours == 1 || hours == -1) "$hours hour" else "$hours hours"
}
