package com.youriptv.app.data

/** Catch-up (archive) rules shared by the guide and the player (pure, unit-testable). */
object Catchup {
    const val MARK = "↺"

    /** True when a programme that has already started lies inside the channel's archive window. */
    fun available(catchupDays: Int, start: Long, end: Long, now: Long): Boolean =
        catchupDays > 0 && end > start && start < now && start >= now - catchupDays * 86_400_000L

    /** Past guide window in hours: the user's setting, widened to cover the longest archive (capped at 14 days). */
    fun pastWindowHours(settingHours: Int, maxArchiveDays: Int): Int =
        maxOf(settingHours, minOf(maxArchiveDays, 14) * 24)

    /** Archive length in whole minutes; a programme still airing is capped at "now". */
    fun durationMinutes(start: Long, end: Long, now: Long): Int =
        (((minOf(end, now) - start) + 59_999) / 60_000).toInt().coerceAtLeast(1)

    /** Catch-up modes that derive the archive URL from the live URL and need no `catchup-source`. */
    private val SELF_DESCRIBING = setOf("shift", "timeshift", "flussonic", "flussonic-hls", "flussonic-ts", "fs", "xc")

    /**
     * Reads M3U catch-up attributes (`catchup`, `catchup-source`, `catchup-days`, `timeshift`), letting
     * channel attributes override playlist-header defaults. A channel that declares catch-up without a
     * day count gets a one-day window.
     */
    fun m3uAttributes(entry: Map<String, String>, header: Map<String, String>): Triple<Int, String, String> {
        fun attr(k: String) = entry[k]?.takeIf { it.isNotBlank() } ?: header[k]?.takeIf { it.isNotBlank() }
        val type = attr("catchup")?.trim()?.lowercase().orEmpty()
        val source = attr("catchup-source")?.trim().orEmpty()
        val declared = type.isNotEmpty() || source.isNotEmpty()
        val days = (attr("catchup-days") ?: attr("timeshift"))?.trim()?.toIntOrNull()
            ?: if (declared) 1 else 0
        if (type == "disabled" || type == "none" || !m3uSupported(type, source)) return Triple(0, "", "")
        return Triple(days.coerceAtLeast(0), type, source)
    }

    /** True when an M3U channel carries enough information to build an archive URL. */
    fun m3uSupported(type: String, source: String): Boolean = source.isNotBlank() || type in SELF_DESCRIBING

    /** Numeric stream id at the end of an Xtream-style stream URL (`…/user/pass/123` or `…/123.ts`). */
    fun xtreamStreamId(url: String): String? =
        Regex("""/(\d+)(?:\.(?:ts|m3u8))?(?:\?.*)?$""", RegexOption.IGNORE_CASE).find(url)?.groupValues?.get(1)

    /** Catch-up for an Xtream-panel M3U channel, using the archive days the provider's API reports for its stream id. */
    fun fromXtreamArchive(url: String, archive: Map<String, Int>): Triple<Int, String, String>? {
        if (archive.isEmpty()) return null
        val days = xtreamStreamId(url)?.let { archive[it] } ?: return null
        return if (days > 0) Triple(days, "xc", "") else null
    }

    /**
     * Builds an archive URL for an M3U channel, or null when the channel has no usable catch-up rule.
     * Times are epoch milliseconds; placeholders use UTC.
     */
    fun m3uUrl(type: String, source: String, liveUrl: String, start: Long, end: Long, now: Long): String? {
        val t = type.lowercase()
        val template = when {
            t == "append" -> if (source.isBlank()) return null else liveUrl + source
            t == "shift" || t == "timeshift" -> liveUrl + (if (liveUrl.contains('?')) "&" else "?") + "utc={utc}&lutc={lutc}"
            t.startsWith("flussonic") || t == "fs" -> flussonic(liveUrl) ?: return null
            t == "xc" -> xtreamStyle(liveUrl) ?: return null
            source.isBlank() -> return null
            source.startsWith("http://", true) || source.startsWith("https://", true) -> source
            else -> liveUrl + source
        }
        return expand(template, start, end, now)
    }

    private fun flussonic(url: String): String? {
        Regex("""^(https?://[^/]+)/(.*)/([^/?]*)\.m3u8(\?.*)?$""", RegexOption.IGNORE_CASE).find(url)?.let { m ->
            val (host, path, name, query) = m.destructured
            return "$host/$path/$name-{utc}-{duration}.m3u8$query"
        }
        Regex("""^(https?://[^/]+)/(.*)/([^/?]*)(?:mpegts|\.ts)(\?.*)?$""", RegexOption.IGNORE_CASE).find(url)?.let { m ->
            val (host, path, _, query) = m.destructured
            return "$host/$path/timeshift_abs-{utc}.ts$query"
        }
        return null
    }

    private fun xtreamStyle(url: String): String? {
        val m = Regex("""^(https?://[^/]+)/(?:live/)?([^/]+)/([^/]+)/(\d+)(?:\.(m3u8|ts))?$""", RegexOption.IGNORE_CASE).find(url) ?: return null
        val (host, user, pass, id, rawExt) = m.destructured
        val ext = rawExt.ifEmpty { "ts" }
        return "$host/timeshift/$user/$pass/{duration:60}/{Y}-{m}-{d}:{H}-{M}/$id.$ext"
    }

    private val placeholder = Regex("""\$?\{([a-zA-Z-]+)(?::([^}]*))?\}""")

    /** Expands `{utc}`, `${start}`, `{duration:60}`, `{Y}`, `{utc:Y-m-d}` and similar placeholders. */
    fun expand(template: String, start: Long, end: Long, now: Long): String {
        val startS = start / 1000
        val endS = end / 1000
        val nowS = now / 1000
        val durS = ((minOf(end, now).takeIf { it > start } ?: end) - start).coerceAtLeast(60_000) / 1000
        return placeholder.replace(template) { m ->
            val name = m.groupValues[1].lowercase()
            val arg = m.groupValues[2]
            val divisor = arg.toLongOrNull()?.takeIf { it > 0 }
            when (name) {
                "utc", "start" -> if (arg.isNotEmpty() && divisor == null) format(start, arg) else startS.toString()
                "utcend", "end" -> if (arg.isNotEmpty() && divisor == null) format(end, arg) else endS.toString()
                "lutc", "now", "timestamp" -> if (arg.isNotEmpty() && divisor == null) format(now, arg) else nowS.toString()
                "duration" -> (durS / (divisor ?: 1)).toString()
                "offset" -> ((nowS - startS) / (divisor ?: 1)).toString()
                "y", "m", "d", "h", "s" -> field(start, m.groupValues[1])
                else -> m.value
            }
        }
    }

    private fun format(ms: Long, pattern: String): String =
        pattern.map { c -> if (c in "YmdHMS") field(ms, c.toString()) else c.toString() }.joinToString("")

    private fun field(ms: Long, f: String): String {
        val cal = java.util.Calendar.getInstance(java.util.TimeZone.getTimeZone("UTC")).apply { timeInMillis = ms }
        return when (f) {
            "Y" -> "%04d".format(cal.get(java.util.Calendar.YEAR))
            "m" -> "%02d".format(cal.get(java.util.Calendar.MONTH) + 1)
            "d" -> "%02d".format(cal.get(java.util.Calendar.DAY_OF_MONTH))
            "H" -> "%02d".format(cal.get(java.util.Calendar.HOUR_OF_DAY))
            "M" -> "%02d".format(cal.get(java.util.Calendar.MINUTE))
            "S" -> "%02d".format(cal.get(java.util.Calendar.SECOND))
            else -> f
        }
    }
}
