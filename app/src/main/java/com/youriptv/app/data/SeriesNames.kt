package com.youriptv.app.data

/**
 * M3U playlists list series as individual episode entries (e.g. "Show Name S01 E02").
 * These helpers group them back into series with season/episode numbers.
 */
object SeriesNames {
    private val pattern = Regex("""^(.*?)[\s._\-|:]*\bS(\d{1,3})[\s._-]*E(\d{1,4})\b""", RegexOption.IGNORE_CASE)

    data class Parsed(val series: String, val season: Int, val episode: Int)

    fun parse(title: String): Parsed {
        val m = pattern.find(title.trim())
        if (m != null) {
            val name = m.groupValues[1].trim().trimEnd('-', '|', ':', '.').trim()
            if (name.isNotEmpty()) {
                return Parsed(name, m.groupValues[2].toInt(), m.groupValues[3].toInt())
            }
        }
        return Parsed(title.trim(), 0, 0)
    }

    const val M3U_SERIES_PREFIX = "m3u-series:"

    fun pseudoUrl(group: String, series: String) = "$M3U_SERIES_PREFIX$group|$series"

    /** Collapses episode entries into one SERIES item per series name, keeping first-seen order. */
    fun groupEpisodes(episodes: List<MediaItem>): List<MediaItem> {
        val seen = LinkedHashMap<String, MediaItem>()
        for (e in episodes) {
            val name = parse(e.name).series
            val key = name.lowercase()
            if (key !in seen) {
                seen[key] = e.copy(kind = ItemKind.SERIES, name = name, url = pseudoUrl(e.group, name), remoteId = name)
            }
        }
        return seen.values.toList()
    }
}
