package com.youriptv.app.data

/** Recognises adult categories by name, so the block works on any playlist without manual setup. */
object AdultGroups {
    private val adult = Regex(
        "(?i)(?<![\\p{L}\\d])(adults?|xxx+|porn\\w*|erotic\\w*|playboy|hustler|nsfw|18\\s*\\+|\\+\\s*18|for\\s+adults|adults?\\s+only)(?![\\p{L}\\d])"
    )
    // Not adult content: a family cartoon brand and a teen-fiction genre.
    private val safe = Regex("(?i)adult\\s*swim|young\\s+adults?")

    fun isAdult(group: String?): Boolean {
        if (group.isNullOrBlank()) return false
        return adult.containsMatchIn(safe.replace(group, " "))
    }
}
