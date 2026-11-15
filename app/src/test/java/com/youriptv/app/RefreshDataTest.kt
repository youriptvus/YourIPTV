package com.youriptv.app

import com.youriptv.app.data.*
import org.junit.Assert.*
import org.junit.Test
import org.kxml2.io.KXmlParser
import java.io.*
import java.util.concurrent.CancellationException

class RefreshDataTest {
    @Test fun guideWindowMigrationRaisesShorterValuesOnceAndPreservesLongerChoicesInExistingUnits() {
        for (hours in listOf(0, 12, 24, 167, 168)) assertEquals(168, EpgRetentionPolicy.migratedPastHours(hours))
        for (hours in listOf(169, 192, 336)) assertEquals(hours, EpgRetentionPolicy.migratedPastHours(hours))
        for (days in listOf(1, 2, 6, 7)) assertEquals(7, EpgRetentionPolicy.migratedFutureDays(days))
        for (days in listOf(8, 14)) assertEquals(days, EpgRetentionPolicy.migratedFutureDays(days))
        assertEquals(336, EpgRetentionPolicy.migratedPastHours(Int.MAX_VALUE))
        assertEquals(14, EpgRetentionPolicy.migratedFutureDays(Int.MAX_VALUE))
    }

    @Test fun backupsKeepGuideHistoryInHoursAndAcceptTheFullFourteenDayRange() {
        assertEquals(mapOf("epg_past_h" to 336, "epg_future_d" to 14),
            BackupKeys.sanitize(mapOf("epg_past_h" to 336, "epg_future_d" to 14)))
        assertEquals(mapOf("epg_past_h" to 0, "epg_future_d" to 1),
            BackupKeys.sanitize(mapOf("epg_past_h" to 0, "epg_future_d" to 1)))
        assertTrue(BackupKeys.sanitize(mapOf("epg_past_h" to 337, "epg_future_d" to 15)).isEmpty())
        val backup = BackupData(1, "fixture", mapOf("epg_past_h" to 240, "epg_future_d" to 10), BackupParental(), emptyList(), 0)
        assertEquals(backup, BackupCodec.decode(BackupCodec.encode(backup)))
    }

    @Test fun migrationDistinguishesAbsentPreferenceFromExplicitDefaultsAndDisabled() {
        assertEquals(16, RefreshPolicy.migratedHours(null))
        for (hours in listOf(0, 1, 16, 24, 72, 168)) assertEquals(hours, RefreshPolicy.migratedHours(hours))
        assertEquals(16, RefreshPolicy.migratedHours(-1))
        assertEquals(16, RefreshPolicy.migratedHours(169))
    }

    @Test fun dueTimeUsesSuccessAndHandlesClockRollback() {
        val now = 200_000_000L
        assertNull(RefreshPolicy.dueAt(0, 0, now))
        assertEquals(now, RefreshPolicy.dueAt(0, 16, now))
        assertEquals(now, RefreshPolicy.dueAt(now + 1000, 16, now))
        assertEquals(now + 16 * 3_600_000L, RefreshPolicy.dueAt(now, 16, now))
        assertTrue(RefreshPolicy.dueAt(now - 17 * 3_600_000L, 16, now)!! <= now)
    }

    @Test fun fingerprintsHaveUnambiguousFieldBoundariesWithoutExposingValues() {
        val first = RefreshPolicy.fingerprint("ab", "c")
        assertEquals(64, first.length)
        assertNotEquals(first, RefreshPolicy.fingerprint("a", "bc"))
        assertEquals(first, RefreshPolicy.fingerprint("ab", "c"))
        assertNotEquals(first, RefreshPolicy.fingerprint("ab", "changed"))
    }

    @Test(expected = CancellationException::class) fun cancellationStopsWork() {
        RefreshPolicy.checkCancelled { true }
    }

    @Test fun catalogStagePreservesStableUrlsAndProviderMetadata() {
        val item = MediaItem(playlistId = 7, kind = ItemKind.LIVE, name = "News", group = "World", logo = "http://fixture/logo",
            url = "http://fixture/live/5.ts", tvgId = "news.a", number = 14, remoteId = "5", extension = "ts",
            catchupDays = 3, catchupType = "append", catchupSource = "?start={utc}")
        val bytes = ByteArrayOutputStream().also { bytes -> DataOutputStream(bytes).use { RefreshStage.writeItem(it, item) } }.toByteArray()
        DataInputStream(ByteArrayInputStream(bytes)).use { input ->
            assertEquals(item, RefreshStage.readItem(input, 7))
            assertEquals(-1, input.read())
        }
    }

    @Test fun programmeStageSupportsDescriptionsLongerThanWriteUtfLimit() {
        val program = Program("a", 1000, 2000, "Programme", "x".repeat(80_000))
        val bytes = ByteArrayOutputStream().also { bytes -> DataOutputStream(bytes).use { RefreshStage.writeProgram(it, program) } }.toByteArray()
        DataInputStream(ByteArrayInputStream(bytes)).use { assertEquals(program, RefreshStage.readProgram(it)) }
    }

    @Test(expected = EOFException::class) fun truncatedStageCannotPublishACompleteItem() {
        val bytes = ByteArrayOutputStream().also { bytes -> DataOutputStream(bytes).use {
            RefreshStage.writeItem(it, MediaItem(playlistId = 1, kind = ItemKind.LIVE, name = "A", group = "G", url = "http://fixture/a"))
        } }.toByteArray()
        DataInputStream(ByteArrayInputStream(bytes.copyOf(bytes.size - 1))).use { RefreshStage.readItem(it, 1) }
    }

    private fun m3u(value: String, cancel: () -> Boolean = { false }): Int =
        M3uParser.parse(BufferedReader(StringReader(value)), strict = true, isCancelled = cancel) {}

    @Test fun strictPlaylistAllowsCompleteHeaderlessEntriesAndRejectsIncompleteTail() {
        assertEquals(1, m3u("#EXTINF:-1,News\nhttp://fixture/live\n"))
        assertThrows(InvalidRefreshData::class.java) { m3u("#EXTM3U\n#EXTINF:-1,News\nhttp://fixture/live\n#EXTINF:-1,Tail\n") }
        assertThrows(InvalidRefreshData::class.java) { m3u("#EXTINF:-1,News\n<html>error</html>\n") }
        assertThrows(InvalidRefreshData::class.java) { m3u("#EXTINF:-1,A\n#EXTINF:-1,B\nhttp://fixture/live") }
        assertThrows(InvalidRefreshData::class.java) { m3u("#EXTINF:" + "x".repeat(1024 * 1024)) }
    }

    @Test fun parsingHonorsCancellationEvenBeforeFirstRecord() {
        assertThrows(CancellationException::class.java) { m3u("#EXTM3U\n", { true }) }
        assertThrows(CancellationException::class.java) {
            XmltvParser.parse(KXmlParser().apply { setInput(StringReader("<tv/>")) }, isCancelled = { true }) {}
        }
    }

    @Test fun xmltvRejectsErrorDocumentsAndTruncation() {
        assertThrows(InvalidRefreshData::class.java) {
            XmltvParser.parse(KXmlParser().apply { setInput(StringReader("<html><title>Error</title></html>")) }) {}
        }
        assertThrows(Exception::class.java) {
            XmltvParser.parse(KXmlParser().apply { setInput(StringReader("<tv><programme channel='a' start='20261114120000 +0000'><title>News</title>")) }) {}
        }
        assertEquals(0, XmltvParser.parse(KXmlParser().apply { setInput(StringReader("<tv/>")) }) {})
        assertEquals(0L, XmltvParser.parseTime("20260230120000 +0000"))
        assertEquals(0L, XmltvParser.parseTime("20261114120000 +2460"))
    }

    @Test fun backupRoundTripPreservesPerPlaylistIntervalsIncludingDisabled() {
        val playlists = listOf(0, 16, 24, 72).mapIndexed { index, hours ->
            BackupPlaylist(index.toLong(), "Fixture $index", SourceType.M3U, "http://fixture/$index", refreshHours = hours)
        }
        val backup = BackupData(1, "fixture", emptyMap(), BackupParental(), playlists, 0)
        assertEquals(backup, BackupCodec.decode(BackupCodec.encode(backup)))
        val old = backup.copy(playlists = listOf(playlists.first().copy(refreshHours = null)))
        assertNull(BackupCodec.decode(BackupCodec.encode(old)).playlists.single().refreshHours)
    }
}
