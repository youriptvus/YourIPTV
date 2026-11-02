package com.youriptv.app

import com.youriptv.app.data.ItemKind
import com.youriptv.app.data.M3uParser
import org.junit.Assert.assertEquals
import org.junit.Test
import java.io.BufferedReader
import java.io.StringReader

class M3uParserTest {

    private fun parse(text: String): Pair<List<M3uParser.Entry>, String> {
        val out = ArrayList<M3uParser.Entry>()
        var epg = ""
        M3uParser.parse(BufferedReader(StringReader(text)), { epg = it.epgUrl }) { out += it }
        return out to epg
    }

    @Test
    fun parsesM3uPlusAttributesGroupsAndHeader() {
        val (entries, epg) = parse(
            """
            ﻿#EXTM3U url-tvg="http://epg.example/guide.xml,http://other" 
            #EXTINF:-1 tvg-id="news.us" tvg-name="News HD" tvg-logo="http://l/n.png" group-title="US | News",News HD
            http://s.example/live/u/p/1.ts
            #EXTINF:-1 tvg-type="movie" group-title="Movies",Some Film (2020)
            http://s.example/movie/u/p/2.mkv
            #EXTINF:-1 group-title="Shows",Show Name S01 E02
            http://s.example/series/u/p/3.mp4
            """.trimIndent()
        )
        assertEquals("http://epg.example/guide.xml", epg)
        assertEquals(3, entries.size)
        with(entries[0]) {
            assertEquals("News HD", name); assertEquals("US | News", group); assertEquals(ItemKind.LIVE, kind)
            assertEquals("news.us", attributes["tvg-id"]); assertEquals("http://l/n.png", attributes["tvg-logo"])
        }
        assertEquals(ItemKind.MOVIE, entries[1].kind)
        assertEquals(ItemKind.EPISODE, entries[2].kind)
    }

    @Test
    fun titleIgnoresCommasInsideQuotedAttributes() {
        assertEquals("Real, Title", M3uParser.extractTitle("""#EXTINF:-1 tvg-name="a,b" group-title="x,y",Real, Title"""))
    }

    @Test
    fun handlesExtgrpUserAgentMissingGroupAndOrphanUrls() {
        val (entries, _) = parse(
            """
            #EXTM3U
            http://orphan.example/ignored.ts
            #EXTINF:-1,Plain Channel
            #EXTGRP:Sports
            #EXTVLCOPT:http-user-agent=MyAgent/1.0
            http://a.example/1.m3u8

            #EXTINF:-1 tvg-name="Fallback",
            http://a.example/2.ts
            """.trimIndent()
        )
        assertEquals(2, entries.size)
        assertEquals("Sports", entries[0].group)
        assertEquals("MyAgent/1.0", entries[0].userAgent)
        assertEquals("Fallback", entries[1].name)
        assertEquals("Uncategorized", entries[1].group)
    }
}
