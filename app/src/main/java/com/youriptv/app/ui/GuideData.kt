package com.youriptv.app.ui

import com.youriptv.app.YourIptvApp
import com.youriptv.app.data.Catchup
import com.youriptv.app.data.MediaItem
import com.youriptv.app.data.Program

class GuideProgramCache(private val app: YourIptvApp, private val pastMs: () -> Long) {
    private val cache = HashMap<Pair<Long, String>, List<Program>>()

    fun clear() = cache.clear()

    fun programsFor(channel: MediaItem): List<Program> {
        if (channel.tvgId.isBlank()) return emptyList()
        return cache.getOrPut(channel.playlistId to channel.tvgId) {
            val now = System.currentTimeMillis()
            val shift = app.settings.epgShiftHours * 3_600_000L
            app.repo.programs(
                channel.playlistId, channel.tvgId, now - pastMs() - shift,
                now + app.settings.epgFutureDays * 86_400_000L - shift,
            ).map { it.copy(start = it.start + shift, end = it.end + shift) }
        }
    }
}

object GuideNavigation {
    const val STEP_MS = 30 * 60_000L

    fun targetTime(focusTime: Long, programs: List<Program>, forward: Boolean, minTime: Long, maxTime: Long): Long {
        val program = programs.firstOrNull { focusTime >= it.start && focusTime < it.end }
        val target = if (forward) {
            program?.end ?: programs.firstOrNull { it.start > focusTime }?.start?.coerceAtMost(focusTime + STEP_MS)
                ?: (focusTime + STEP_MS)
        } else {
            val previous = if (program != null) programs.lastOrNull { it.end <= program.start }
                else programs.lastOrNull { it.end <= focusTime }
            when {
                program != null && previous != null && previous.end == program.start ->
                    previous.start.coerceAtLeast(program.start - 6 * STEP_MS)
                program != null -> program.start - 1
                previous != null -> maxOf(previous.start, focusTime - STEP_MS)
                else -> focusTime - STEP_MS
            }
        }
        return target.coerceIn(minTime, maxTime)
    }

    fun windowFor(windowStart: Long, focusTime: Long, span: Long): Long {
        var start = windowStart
        while (focusTime >= start + span - STEP_MS / 3) start += STEP_MS
        while (focusTime < start) start -= STEP_MS
        return start
    }
}

data class GuideHeader(val title: String, val meta: String, val description: String)

fun guideHeader(
    channel: MediaItem, program: Program?, now: Long, marks: Map<String, String>, showArchiveHint: Boolean = false,
): GuideHeader {
    if (program == null) {
        val meta = if (channel.number > 0) "${channel.number}  ·  No programme information" else "No programme information"
        val description = if (channel.tvgId.isBlank()) "This channel has no guide id (tvg-id) in the playlist." else ""
        return GuideHeader(channel.name, meta, description)
    }
    val relative = when {
        now >= program.start && now < program.end -> "  ·  ${(program.end - now) / 60_000} min left"
        program.start > now -> ((program.start - now + 59_999) / 60_000).let { minutes ->
            "  ·  starts in " + if (minutes >= 60) "${minutes / 60} h ${minutes % 60} min" else "$minutes min"
        }
        Catchup.available(channel.catchupDays, program.start, program.end, now) ->
            "  ·  ended  ·  ${Catchup.MARK} in archive" + if (showArchiveHint) " (OK to play)" else ""
        else -> "  ·  ended"
    }
    val mark = when (marks["${channel.url}|${program.start}"]) {
        Scheduler.REMINDER_MARK -> "  ·  ${Scheduler.REMINDER_MARK} reminder set"
        Scheduler.REC_MARK -> "  ·  ${Scheduler.REC_MARK} " + if (program.start > now) "recording scheduled" else "recording"
        else -> ""
    }
    return GuideHeader(
        program.title,
        "${Format.dayTime(program.start)} – ${Format.time(program.end)}  ·  ${channel.name}$relative$mark",
        program.description,
    )
}
