package com.youriptv.app.data

import org.xmlpull.v1.XmlPullParser
import java.util.Calendar
import java.util.TimeZone

/** Streaming XMLTV parser; the caller supplies a configured [XmlPullParser]. */
object XmltvParser {

    data class Channel(val id: String, val displayName: String, val icon: String)

    fun parse(
        parser: XmlPullParser,
        onChannel: (Channel) -> Unit = {},
        acceptChannel: (String) -> Boolean = { true },
        onProgram: (Program) -> Unit,
    ): Int {
        var programs = 0
        var event = parser.eventType
        while (event != XmlPullParser.END_DOCUMENT) {
            if (event == XmlPullParser.START_TAG) {
                when (parser.name) {
                    "channel" -> readChannel(parser)?.let(onChannel)
                    "programme" -> {
                        val channel = parser.getAttributeValue(null, "channel").orEmpty()
                        if (channel.isNotEmpty() && acceptChannel(channel)) {
                            readProgramme(parser, channel)?.let { onProgram(it); programs++ }
                        } else {
                            skip(parser)
                        }
                    }
                }
            }
            event = parser.next()
        }
        return programs
    }

    private fun readChannel(parser: XmlPullParser): Channel? {
        val id = parser.getAttributeValue(null, "id") ?: return null
        var name = ""
        var icon = ""
        val depth = parser.depth
        while (true) {
            val ev = parser.next()
            if (ev == XmlPullParser.END_DOCUMENT) break
            if (ev == XmlPullParser.END_TAG && parser.depth == depth) break
            if (ev == XmlPullParser.START_TAG) {
                when (parser.name) {
                    "display-name" -> if (name.isEmpty()) name = readText(parser) else skip(parser)
                    "icon" -> icon = parser.getAttributeValue(null, "src").orEmpty()
                }
            }
        }
        return Channel(id, name, icon)
    }

    private fun readProgramme(parser: XmlPullParser, channel: String): Program? {
        val start = parseTime(parser.getAttributeValue(null, "start"))
        val stop = parseTime(parser.getAttributeValue(null, "stop"))
        var title = ""
        var desc = ""
        val depth = parser.depth
        while (true) {
            val ev = parser.next()
            if (ev == XmlPullParser.END_DOCUMENT) break
            if (ev == XmlPullParser.END_TAG && parser.depth == depth) break
            if (ev == XmlPullParser.START_TAG) {
                when (parser.name) {
                    "title" -> if (title.isEmpty()) title = readText(parser) else skip(parser)
                    "desc" -> if (desc.isEmpty()) desc = readText(parser) else skip(parser)
                    else -> skip(parser)
                }
            }
        }
        if (start <= 0L) return null
        val end = if (stop > start) stop else start + 30 * 60_000L
        return Program(channel, start, end, title.trim(), desc.trim())
    }

    private fun readText(parser: XmlPullParser): String {
        val sb = StringBuilder()
        val depth = parser.depth
        while (true) {
            val ev = parser.next()
            if (ev == XmlPullParser.TEXT) sb.append(parser.text)
            if (ev == XmlPullParser.END_DOCUMENT) break
            if (ev == XmlPullParser.END_TAG && parser.depth == depth) break
        }
        return sb.toString()
    }

    private fun skip(parser: XmlPullParser) {
        if (parser.eventType != XmlPullParser.START_TAG) return
        var depth = 1
        while (depth != 0) {
            when (parser.next()) {
                XmlPullParser.END_TAG -> depth--
                XmlPullParser.START_TAG -> depth++
                XmlPullParser.END_DOCUMENT -> return
            }
        }
    }

    /** Parses XMLTV timestamps such as `20261029200000 +0000` into epoch millis (UTC if no offset). */
    fun parseTime(value: String?): Long {
        if (value == null) return 0
        val v = value.trim()
        if (v.length < 12) return 0
        return try {
            val year = v.substring(0, 4).toInt()
            val month = v.substring(4, 6).toInt()
            val day = v.substring(6, 8).toInt()
            val hour = v.substring(8, 10).toInt()
            val minute = v.substring(10, 12).toInt()
            val second = if (v.length >= 14 && v[12].isDigit()) v.substring(12, 14).toInt() else 0
            val cal = Calendar.getInstance(TimeZone.getTimeZone("UTC"))
            cal.clear()
            cal.set(year, month - 1, day, hour, minute, second)
            var millis = cal.timeInMillis
            val offset = v.substringAfter(' ', "").trim()
            if (offset.length >= 5 && (offset[0] == '+' || offset[0] == '-')) {
                val sign = if (offset[0] == '-') -1 else 1
                val oh = offset.substring(1, 3).toInt()
                val om = offset.substring(3, 5).toInt()
                millis -= sign * (oh * 3_600_000L + om * 60_000L)
            }
            millis
        } catch (_: Exception) {
            0
        }
    }
}
