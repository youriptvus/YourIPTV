package com.youriptv.app

import com.youriptv.app.data.Program
import com.youriptv.app.data.XmltvParser
import org.junit.Assert.assertEquals
import org.junit.Test
import org.kxml2.io.KXmlParser
import java.io.StringReader

class XmltvParserTest {

    @Test
    fun parsesTimesWithOffsets() {
        // 2026-10-29 20:00:00 UTC
        val utc = 1_793_304_000_000L
        assertEquals(utc, XmltvParser.parseTime("20261029200000 +0000"))
        assertEquals(utc, XmltvParser.parseTime("20261029220000 +0200"))
        assertEquals(utc, XmltvParser.parseTime("20261029150000 -0500"))
        assertEquals(utc, XmltvParser.parseTime("202610292000"))
        assertEquals(0L, XmltvParser.parseTime("bad"))
        assertEquals(0L, XmltvParser.parseTime(null))
    }

    @Test
    fun parsesProgrammesAndFiltersChannels() {
        val xml = """<?xml version="1.0" encoding="UTF-8"?>
            <tv>
              <channel id="a"><display-name>Channel A</display-name><icon src="http://i/a.png"/></channel>
              <programme start="20261029200000 +0000" stop="20261029210000 +0000" channel="a">
                <title lang="en">Evening News</title><desc>Headlines &amp; weather</desc><category>News</category>
              </programme>
              <programme start="20261029210000 +0000" channel="a"><title>No Stop</title></programme>
              <programme start="20261029200000 +0000" stop="20261029210000 +0000" channel="b"><title>Skipped</title></programme>
            </tv>"""
        val parser = KXmlParser().apply { setInput(StringReader(xml)) }
        val channels = ArrayList<XmltvParser.Channel>()
        val progs = ArrayList<Program>()
        val n = XmltvParser.parse(parser, { channels += it }, { it == "a" }) { progs += it }
        assertEquals(2, n)
        assertEquals(XmltvParser.Channel("a", "Channel A", "http://i/a.png"), channels.single())
        assertEquals("Evening News", progs[0].title)
        assertEquals("Headlines & weather", progs[0].description)
        assertEquals(60 * 60_000L, progs[0].end - progs[0].start)
        assertEquals(30 * 60_000L, progs[1].end - progs[1].start)
    }
}
