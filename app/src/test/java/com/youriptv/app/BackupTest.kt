package com.youriptv.app

import com.youriptv.app.data.BackupCodec
import com.youriptv.app.data.BackupData
import com.youriptv.app.data.BackupException
import com.youriptv.app.data.BackupFavorite
import com.youriptv.app.data.BackupKeys
import com.youriptv.app.data.BackupParental
import com.youriptv.app.data.BackupPlaylist
import com.youriptv.app.data.BackupRestore
import com.youriptv.app.data.Playlist
import com.youriptv.app.data.SourceType
import org.json.JSONObject
import org.junit.Assert.*
import org.junit.Test
import java.io.File

class BackupTest {

    private val sample = BackupData(
        created = 1_700_000_000_000, appVersion = "0.1.0",
        settings = mapOf(
            "buffer_ms" to 5000, "aspect" to 2, "clock_24h" to true, "live_format" to "m3u8",
            "user_agent" to "Custom/1.0", "epg_shift_h" to -3, "resume_last" to false,
        ),
        parental = BackupParental(blockAdult = true, hideLockedGroups = false, lockPlaylists = true, lockedGroups = setOf("News", "Kids")),
        playlists = listOf(
            BackupPlaylist(5, "Test M3U", SourceType.M3U, "http://example.invalid/list.m3u", favorites = listOf(BackupFavorite("http://example.invalid/1.ts", 42))),
            BackupPlaylist(4, "Test Xtream", SourceType.XTREAM, "http://example.invalid:8080", "user", "pass", epgUrl = "http://example.invalid/epg.xml"),
        ),
        activeRef = 5,
    )

    @Test
    fun roundTripKeepsPlaylistsFavoritesSettingsAndRestrictions() {
        val back = BackupCodec.decode(BackupCodec.encode(sample))
        assertEquals(sample.settings, back.settings)
        assertEquals(sample.parental, back.parental)
        assertEquals(sample.playlists, back.playlists)
        assertEquals(5L, back.activeRef)
        assertEquals(sample.created, back.created)
    }

    @Test
    fun backupNeverContainsPinsOrTheSettingsLock() {
        val withSecrets = sample.copy(settings = sample.settings + mapOf(
            "pin_hash" to "abc", "pin_salt" to "s", "settings_pin_hash" to "def", "lock_settings" to true, "pin_fails" to "3",
        ))
        val json = BackupCodec.encode(withSecrets)
        listOf("pin_hash", "pin_salt", "settings_pin", "lock_settings", "pin_fails").forEach { assertFalse(it, json.contains(it)) }
        // A crafted file cannot inject them either.
        val crafted = JSONObject(json).apply { getJSONObject("settings").put("pin_hash", "x").put("settings_pin_hash", "y").put("lock_settings", false) }
        val decoded = BackupCodec.decode(crafted.toString())
        assertTrue(decoded.settings.keys.none { BackupKeys.isSecret(it) })
        assertTrue(BackupKeys.SETTINGS.keys.none { BackupKeys.isSecret(it) })
    }

    @Test
    fun invalidOrOutOfRangeSettingsAreDropped() {
        val clean = BackupKeys.sanitize(mapOf(
            "buffer_ms" to 10, "aspect" to 7, "live_format" to "rtmp", "clock_24h" to "yes",
            "skip_sec" to 30.5, "epg_future_d" to 7, "unknown_key" to 1, "audio_delay_ms" to -100,
        ))
        assertEquals(mapOf("epg_future_d" to 7, "audio_delay_ms" to -100), clean)
    }

    @Test
    fun rejectsFilesThatAreNotBackups() {
        fun fails(text: String) = try { BackupCodec.decode(text); false } catch (_: BackupException) { true }
        assertTrue(fails("not json"))
        assertTrue(fails("{}"))
        assertTrue(fails("{\"format\":\"youriptv-backup\"}"))
        assertTrue(fails("{\"format\":\"youriptv-backup\",\"version\":99}"))
        assertFalse(fails("{\"format\":\"youriptv-backup\",\"version\":1}"))
    }

    @Test
    fun malformedPlaylistEntriesAreSkipped() {
        val root = JSONObject(BackupCodec.encode(sample))
        root.getJSONArray("playlists").put(JSONObject().put("name", "No url").put("type", "M3U"))
            .put(JSONObject().put("name", "Bad type").put("type", "FTP").put("url", "x"))
            .put(JSONObject().put("name", "Xtream no user").put("type", "XTREAM").put("url", "http://h"))
            .put("garbage")
        assertEquals(2, BackupCodec.decode(root.toString()).playlists.size)
    }

    @Test
    fun restoreSkipsPlaylistsAlreadyOnTheDeviceAndMapsIds() {
        val local = listOf(
            Playlist(id = 11, name = "Renamed", type = SourceType.M3U, url = "HTTP://example.invalid/list.m3u/"),
            Playlist(id = 12, name = "Other user", type = SourceType.XTREAM, url = "http://example.invalid:8080", username = "someone"),
        )
        val dup = sample.copy(playlists = sample.playlists + sample.playlists[1].copy(ref = 9))
        val plan = BackupRestore.plan(local, dup)
        assertEquals(mapOf(5L to 11L), plan.existing)
        assertEquals(listOf("Test Xtream"), plan.toAdd.map { it.name })
        // Restoring the same backup twice adds nothing the second time.
        val after = local + Playlist(id = 13, name = "Test Xtream", type = SourceType.XTREAM, url = "http://example.invalid:8080", username = "user")
        assertTrue(BackupRestore.plan(after, dup).toAdd.isEmpty())
    }

    @Test
    fun restoreOnlyAddsParentalRestrictions() {
        val current = BackupParental(blockAdult = true, hideLockedGroups = true, lockPlaylists = false, lockedGroups = setOf("Sports"))
        val weaker = BackupParental(blockAdult = false, hideLockedGroups = false, lockPlaylists = true, lockedGroups = setOf("News"))
        val merged = BackupRestore.mergeParental(current, weaker)
        assertTrue(merged.blockAdult)
        assertTrue(merged.hideLockedGroups)
        assertTrue(merged.lockPlaylists)
        assertEquals(setOf("Sports", "News"), merged.lockedGroups)
        assertEquals(current, BackupRestore.mergeParental(current, BackupParental()))
    }

    @Test
    fun settingsBackupCoversEveryUserSetting() {
        val settingsSrc = listOf(File("src/main/java/com/youriptv/app/data/Settings.kt"), File("app/src/main/java/com/youriptv/app/data/Settings.kt"))
            .first { it.exists() }.readText()
        val exported = Regex("\"([a-z0-9_]+)\" to ").findAll(settingsSrc.substringAfter("fun backupValues()").substringBefore("fun restoreValues"))
            .map { it.groupValues[1] }.toSet()
        assertEquals(BackupKeys.SETTINGS.keys, exported)
        // Every simple preference in Settings is either backed up, parental (merged separately), or deliberately excluded.
        val allKeys = Regex("sp\\.get(?:Boolean|Int|Long|String|StringSet)\\(\"([a-z0-9_]+)\"").findAll(settingsSrc).map { it.groupValues[1] }
            .filterNot { BackupKeys.isSecret(it) }.toSet()
        val parental = setOf("block_adult", "hide_locked", "lock_playlists", "locked_groups")
        // active_playlist is remapped to the restored playlist's new id; update keys are per-device technician state.
        val excluded = setOf("active_playlist", "automatic_app_updates", "last_update_info", "pending_update_code")
        assertEquals(emptySet<String>(), allKeys - BackupKeys.SETTINGS.keys - parental - excluded)
    }

    @Test
    fun restoreRequiresParentalPinWhenPlaylistsAreLockedAndKeepsLockedVisitOpenOnlyForPicker() {
        val ui = listOf(File("src/main/java/com/youriptv/app/ui/SettingsActivity.kt"), File("app/src/main/java/com/youriptv/app/ui/SettingsActivity.kt"))
            .first { it.exists() }.readText()
        assertTrue(ui.contains("if (s.isPlaylistsLocked()) Dialogs.requirePin(this, \"restore playlists\")"))
        assertTrue(ui.contains("withPlaylistPermission { loadAndConfirm"))
        assertTrue(ui.contains("withPlaylistPermission { pickingFile = true; importDoc.launch"))
        assertTrue(ui.contains("if (s.isSettingsLocked() && !isChangingConfigurations && !pickingFile) finish()"))
    }
}
