package com.youriptv.app.ui

import com.youriptv.app.YourIptvApp
import com.youriptv.app.data.Catchup
import com.youriptv.app.data.MediaItem
import com.youriptv.app.data.Program
import com.youriptv.app.data.GuideWindowResult

/** Small LRU of the visible timeline plus adjacent programmes, never a playlist's whole retained history. */
class GuideProgramCache(
    private val load: (MediaItem, Long, Long) -> GuideWindowResult,
    private val maxChannels: Int = 48,
    private val maxProgrammes: Int = 4096,
    private val maxCharacters: Long = 2_000_000,
) {
    constructor(app: YourIptvApp) : this(shiftedLoader(
        { channel, from, to -> app.repo.guidePrograms(channel.playlistId, channel.tvgId, from, to) },
        { app.settings.epgShiftHours * 3_600_000L },
    ))
    private data class Entry(val from: Long, val to: Long, val result: GuideWindowResult, val characters: Long)
    private val cache = LinkedHashMap<Pair<Long, String>, Entry>(16, 0.75f, true)
    private var programmeCount = 0
    private var characterCount = 0L

    fun clear() { cache.clear(); programmeCount = 0; characterCount = 0 }

    fun programsFor(channel: MediaItem, visibleFrom: Long, visibleTo: Long): List<Program> = windowFor(channel, visibleFrom, visibleTo).programs

    fun windowFor(channel: MediaItem, visibleFrom: Long, visibleTo: Long): GuideWindowResult {
        if (channel.tvgId.isBlank() || visibleTo <= visibleFrom) return GuideWindowResult(emptyList())
        val key = channel.playlistId to channel.tvgId
        cache[key]?.let { if (visibleFrom >= it.from && visibleTo <= it.to) return it.result }
        val bucket = GuideNavigation.STEP_MS
        val prefetch = 2 * 3_600_000L
        val from = bucketStart(visibleFrom, bucket) - prefetch
        val to = bucketStart(visibleTo - 1, bucket) + bucket + prefetch
        val loaded = load(channel, from, to)
        val programmes = loaded.programs.filter { it.end > from && it.start < to }
        val result = loaded.copy(programs = programmes)
        cache.remove(key)?.let { programmeCount -= it.result.programs.size; characterCount -= it.characters }
        val characters = programmes.sumOf { it.title.length.toLong() + it.description.length }
        // SQL already bounds compact rows/text. The LRU additionally bounds their aggregate retained weight.
        if (maxChannels > 0 && programmes.size <= maxProgrammes && characters <= maxCharacters) {
            cache[key] = Entry(from, to, result, characters)
            programmeCount += programmes.size
            characterCount += characters
            val iterator = cache.entries.iterator()
            while (cache.size > maxChannels || programmeCount > maxProgrammes || characterCount > maxCharacters) {
                val old = iterator.next().value
                programmeCount -= old.result.programs.size; characterCount -= old.characters
                iterator.remove()
            }
        }
        return result
    }

    companion object {
        internal fun shiftedLoader(
            raw: (MediaItem, Long, Long) -> GuideWindowResult, shiftMs: () -> Long,
        ): (MediaItem, Long, Long) -> GuideWindowResult = { channel, from, to ->
            val shift = shiftMs()
            val result = raw(channel, from - shift, to - shift)
            result.copy(programs = result.programs.map { it.copy(start = it.start + shift, end = it.end + shift) })
        }

        private fun bucketStart(value: Long, bucket: Long): Long {
            val quotient = value / bucket
            return (if (value < 0 && value % bucket != 0L) quotient - 1 else quotient) * bucket
        }
    }
}

/** Captures only navigation state. Reloading guide data never requests playback or resets time. */
internal data class GuideContinuity(
    val focusedKey: Pair<Long, String>?, val focusedRow: Int,
    val firstKey: Pair<Long, String>?, val firstRow: Int, val firstOffset: Int,
    val focusTime: Long, val windowStart: Long,
) {
    fun focusedIn(items: List<MediaItem>) = find(items, focusedKey, focusedRow)
    fun firstIn(items: List<MediaItem>) = find(items, firstKey, firstRow)
    private fun find(items: List<MediaItem>, key: Pair<Long, String>?, fallback: Int): Int {
        val found = items.indexOfFirst { key != null && it.playlistId == key.first && it.url == key.second }
        return if (found >= 0) found else fallback.coerceIn(0, (items.size - 1).coerceAtLeast(0))
    }
}

data class GuideTimePosition(val focusTime: Long, val windowStart: Long)

object GuideNavigation {
    const val STEP_MS = 30 * 60_000L

    /** Explicit guide entry selects the current programme, independently of a previous browsing window. */
    fun openingPosition(now: Long) = GuideTimePosition(now, now - now % STEP_MS)

    fun programAt(programs: List<Program>, time: Long): Program? =
        programs.firstOrNull { time >= it.start && time < it.end }

    fun targetTime(focusTime: Long, programs: List<Program>, forward: Boolean, minTime: Long, maxTime: Long): Long {
        val program = programAt(programs, focusTime)
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
