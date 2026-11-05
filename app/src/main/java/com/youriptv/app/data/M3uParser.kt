package com.youriptv.app.data

import java.io.BufferedReader
import java.io.InputStream
import java.io.InputStreamReader

/**
 * Streaming M3U / M3U Plus parser. Entries are emitted one at a time so very large
 * playlists (100k+ entries) never have to be held in memory.
 */
object M3uParser {

    data class Entry(
        val name: String,
        val url: String,
        val attributes: Map<String, String>,
        val group: String,
        val kind: ItemKind,
        val userAgent: String = "",
    )

    data class Header(val epgUrl: String, val attributes: Map<String, String> = emptyMap())

    private val attrRegex = Regex("""([A-Za-z0-9_-]+)="([^"]*)"""")

    fun parse(input: InputStream, onHeader: (Header) -> Unit = {}, onEntry: (Entry) -> Unit): Int {
        val reader = BufferedReader(InputStreamReader(input, Charsets.UTF_8), 64 * 1024)
        return parse(reader, onHeader, onEntry)
    }

    fun parse(reader: BufferedReader, onHeader: (Header) -> Unit = {}, onEntry: (Entry) -> Unit): Int {
        var count = 0
        var pendingInfo: String? = null
        var pendingGroup: String? = null
        var pendingUa = ""
        var first = true
        while (true) {
            val raw = reader.readLine() ?: break
            val line = if (first) raw.removePrefix("\uFEFF").trim() else raw.trim()
            first = false
            if (line.isEmpty()) continue
            when {
                line.startsWith("#EXTM3U") -> {
                    val attrs = parseAttributes(line)
                    val epg = attrs["url-tvg"] ?: attrs["x-tvg-url"] ?: ""
                    onHeader(Header(epg.split(',').firstOrNull()?.trim().orEmpty(), attrs))
                }
                line.startsWith("#EXTINF") -> {
                    pendingInfo = line
                    pendingGroup = null
                    pendingUa = ""
                }
                line.startsWith("#EXTGRP:") -> pendingGroup = line.substringAfter(':').trim()
                line.startsWith("#EXTVLCOPT:http-user-agent=") -> pendingUa = line.substringAfter('=').trim()
                line.startsWith("#") -> Unit
                else -> {
                    val info = pendingInfo
                    if (info != null) {
                        onEntry(buildEntry(info, line, pendingGroup, pendingUa))
                        count++
                    }
                    pendingInfo = null
                    pendingGroup = null
                    pendingUa = ""
                }
            }
        }
        return count
    }

    fun parseAttributes(line: String): Map<String, String> {
        val map = HashMap<String, String>()
        for (m in attrRegex.findAll(line)) map[m.groupValues[1].lowercase()] = m.groupValues[2]
        return map
    }

    /** Title is the text after the first comma that is outside quoted attribute values. */
    fun extractTitle(info: String): String {
        var inQuotes = false
        for (i in info.indices) {
            val c = info[i]
            if (c == '"') inQuotes = !inQuotes
            else if (c == ',' && !inQuotes) return info.substring(i + 1).trim()
        }
        return ""
    }

    fun classify(url: String, attributes: Map<String, String>): ItemKind {
        val type = attributes["tvg-type"]?.lowercase()
        if (type == "movie" || type == "movies") return ItemKind.MOVIE
        if (type == "series") return ItemKind.EPISODE
        val lower = url.lowercase()
        return when {
            lower.contains("/movie/") -> ItemKind.MOVIE
            lower.contains("/series/") -> ItemKind.EPISODE
            else -> ItemKind.LIVE
        }
    }

    private fun buildEntry(info: String, url: String, extGroup: String?, ua: String): Entry {
        val attrs = parseAttributes(info)
        var title = extractTitle(info)
        if (title.isEmpty()) title = attrs["tvg-name"].orEmpty()
        if (title.isEmpty()) title = url.substringAfterLast('/')
        val group = attrs["group-title"]?.takeIf { it.isNotBlank() } ?: extGroup ?: "Uncategorized"
        return Entry(title, url, attrs, group.trim(), classify(url, attrs), ua)
    }
}
