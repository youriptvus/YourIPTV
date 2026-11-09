package com.youriptv.app.data

import android.content.Context

/** All user settings. Every option is available to every user; there are no gated features. */
class Settings(context: Context) {
    private val sp = context.applicationContext.getSharedPreferences("settings", Context.MODE_PRIVATE)

    init {
        // 0.5.1: automatic updates became default-on; switch them on once for boxes that had them off.
        if (!sp.contains("auto_updates_forced_on")) {
            sp.edit().putBoolean("automatic_app_updates", true).putBoolean("auto_updates_forced_on", true).apply()
        }
    }

    var activePlaylistId: Long
        get() = sp.getLong("active_playlist", 0)
        set(v) = sp.edit().putLong("active_playlist", v).apply()

    var lastChannelUrl: String
        get() = sp.getString("last_channel", "").orEmpty()
        set(v) = sp.edit().putString("last_channel", v).apply()

    var resumeLastChannel: Boolean
        get() = sp.getBoolean("resume_last", true)
        set(v) = sp.edit().putBoolean("resume_last", v).apply()

    var bufferMs: Int
        get() = sp.getInt("buffer_ms", 3000)
        set(v) = sp.edit().putInt("buffer_ms", v).apply()

    /** 0 = fit, 1 = fill/zoom, 2 = stretch. */
    var aspectMode: Int
        get() = sp.getInt("aspect", 0)
        set(v) = sp.edit().putInt("aspect", v).apply()

    var skipSeconds: Int
        get() = sp.getInt("skip_sec", 10)
        set(v) = sp.edit().putInt("skip_sec", v).apply()

    /** Audio offset in ms; positive plays audio later than video. */
    var audioDelayMs: Int
        get() = sp.getInt("audio_delay_ms", 0)
        set(v) = sp.edit().putInt("audio_delay_ms", v).apply()

    /** Switch the display refresh rate to match the video frame rate while playing. */
    var autoFrameRate: Boolean
        get() = sp.getBoolean("auto_frame_rate", false)
        set(v) = sp.edit().putBoolean("auto_frame_rate", v).apply()

    var liveFormat: String
        get() = sp.getString("live_format", "ts").orEmpty()
        set(v) = sp.edit().putString("live_format", v).apply()

    var preferSoftwareDecoding: Boolean
        get() = sp.getBoolean("sw_decode", false)
        set(v) = sp.edit().putBoolean("sw_decode", v).apply()

    var globalUserAgent: String
        get() = sp.getString("user_agent", "").orEmpty()
        set(v) = sp.edit().putString("user_agent", v).apply()

    var epgPastHours: Int
        get() = sp.getInt("epg_past_h", 12)
        set(v) = sp.edit().putInt("epg_past_h", v).apply()

    var epgFutureDays: Int
        get() = sp.getInt("epg_future_d", 2)
        set(v) = sp.edit().putInt("epg_future_d", v).apply()

    /** Shifts displayed guide times, in hours, for providers with wrong time zones. */
    var epgShiftHours: Int
        get() = sp.getInt("epg_shift_h", 0)
        set(v) = sp.edit().putInt("epg_shift_h", v).apply()

    var use24h: Boolean
        get() = sp.getBoolean("clock_24h", false)
        set(v) = sp.edit().putBoolean("clock_24h", v).apply()

    var showChannelNumbers: Boolean
        get() = sp.getBoolean("show_numbers", true)
        set(v) = sp.edit().putBoolean("show_numbers", v).apply()

    /** When true, UP selects the next (higher-numbered) channel instead of the previous one. */
    var invertZap: Boolean
        get() = sp.getBoolean("invert_zap", false)
        set(v) = sp.edit().putBoolean("invert_zap", v).apply()

    /** Refresh the active playlist and guide on start when older than this many hours (0 = never). */
    var autoRefreshHours: Int
        get() = sp.getInt("auto_refresh_h", 24)
        set(v) = sp.edit().putInt("auto_refresh_h", v).apply()

    /** On by default and independent of every other setting; excluded from portable backup/restore settings. */
    var automaticAppUpdates: Boolean
        get() = sp.getBoolean("automatic_app_updates", true)
        set(v) = sp.edit().putBoolean("automatic_app_updates", v).apply()

    /** Last update.json seen, so a required update is still enforced when the update server is unreachable. */
    var lastUpdateInfo: String?
        get() = sp.getString("last_update_info", null)
        set(v) = sp.edit().putString("last_update_info", v).apply()

    /** Install target retained after success until the installed-version changelog is acknowledged. */
    var pendingUpdateCode: Long
        get() = sp.getLong("pending_update_code", 0L)
        set(v) { sp.edit().putLong("pending_update_code", v).commit() } // synchronous: the update kills the process

    /** Effective values (defaults included) of every setting listed in [BackupKeys.SETTINGS]. */
    fun backupValues(): Map<String, Any> = mapOf(
        "resume_last" to resumeLastChannel, "last_channel" to lastChannelUrl, "buffer_ms" to bufferMs,
        "aspect" to aspectMode, "skip_sec" to skipSeconds, "audio_delay_ms" to audioDelayMs,
        "auto_frame_rate" to autoFrameRate, "live_format" to liveFormat, "sw_decode" to preferSoftwareDecoding,
        "user_agent" to globalUserAgent, "epg_past_h" to epgPastHours, "epg_future_d" to epgFutureDays,
        "epg_shift_h" to epgShiftHours, "clock_24h" to use24h, "show_numbers" to showChannelNumbers,
        "invert_zap" to invertZap, "auto_refresh_h" to autoRefreshHours,
    )

    /** Writes only validated, non-secret settings; PINs and the Settings lock are never touched. */
    fun restoreValues(values: Map<String, Any>) {
        val e = sp.edit()
        for ((k, v) in BackupKeys.sanitize(values)) {
            if (BackupKeys.isSecret(k)) continue
            when (v) {
                is Boolean -> e.putBoolean(k, v)
                is Int -> e.putInt(k, v)
                is String -> e.putString(k, v)
            }
        }
        e.commit()
    }

    var parentalRestrictions: BackupParental
        get() = BackupParental(blockAdult, hideLockedGroups, lockPlaylists, lockedGroups)
        set(v) {
            sp.edit().putBoolean("block_adult", v.blockAdult).putBoolean("hide_locked", v.hideLockedGroups)
                .putBoolean("lock_playlists", v.lockPlaylists).putStringSet("locked_groups", v.lockedGroups).commit()
        }

    fun epgUpdated(playlistId: Long): Long = sp.getLong("epg_updated_$playlistId", 0)
    fun setEpgUpdated(playlistId: Long, time: Long) = sp.edit().putLong("epg_updated_$playlistId", time).apply()

    /** Drops per-playlist state so a deleted provider leaves no stream URL (with credentials) behind. */
    fun forgetPlaylist(playlistId: Long, ownsLastChannel: Boolean) {
        val e = sp.edit().remove("epg_updated_$playlistId")
        if (ownsLastChannel) e.remove("last_channel")
        e.apply()
    }

    var hideLockedGroups: Boolean
        get() = sp.getBoolean("hide_locked", false)
        set(v) = sp.edit().putBoolean("hide_locked", v).apply()

    var lockedGroups: Set<String>
        get() = sp.getStringSet("locked_groups", emptySet()).orEmpty().toSet()
        set(v) = sp.edit().putStringSet("locked_groups", v).apply()

    /** Adult categories in every playlist (live, movies, series) are hidden and need the parental PIN. */
    var blockAdult: Boolean
        get() = sp.getBoolean("block_adult", false)
        set(v) = sp.edit().putBoolean("block_adult", v).apply()

    fun isAdultBlocked(group: String?) = ParentalRules.isAdultBlocked(hasPin(), group, blockAdult)

    fun isGroupLocked(group: String) = ParentalRules.isLocked(hasPin(), group, lockedGroups, blockAdult)

    /** Blocked adult groups are always hidden; other locked groups only when "Hide locked groups" is on. */
    fun isGroupHidden(group: String) = ParentalRules.isHidden(hasPin(), group, lockedGroups, blockAdult, hideLockedGroups)

    var lockPlaylists: Boolean
        get() = sp.getBoolean("lock_playlists", false)
        set(v) = sp.edit().putBoolean("lock_playlists", v).apply()

    /** Parental locks only take effect while a parental PIN exists, so removing it can never lock the user out. */
    fun isPlaylistsLocked() = hasPin() && lockPlaylists

    /** Changes whenever a content restriction changes; screens compare it to notice new restrictions. */
    fun restrictionStamp(): Int = listOf(sp.getString("pin_hash", null), lockedGroups.sorted(), blockAdult, hideLockedGroups).hashCode()

    fun addListener(l: android.content.SharedPreferences.OnSharedPreferenceChangeListener) = sp.registerOnSharedPreferenceChangeListener(l)
    fun removeListener(l: android.content.SharedPreferences.OnSharedPreferenceChangeListener) = sp.unregisterOnSharedPreferenceChangeListener(l)

    // ---- PINs ----
    // The parental PIN and the Settings PIN are independent and must differ (see PinVault).
    // Settings protection is permanent. The stored Settings PIN is an optional additional credential.

    val pins = PinVault(object : StringStore {
        override fun get(key: String): String? = sp.getString(key, null)
        override fun put(values: Map<String, String?>) {
            val e = sp.edit()
            for ((k, v) in values) if (v == null) e.remove(k) else e.putString(k, v)
            e.commit()
        }
    })

    fun hasPin() = pins.has(PinKind.PARENTAL)

    fun isSettingsLocked() = true

    /** True for an install upgraded from the build that locked Settings with the parental PIN; see [ParentalRules.legacySettingsLockPending]. */
    fun isLegacySettingsLock(): Boolean {
        val flag = sp.getBoolean("lock_settings", false)
        if (!flag) return false
        if (ParentalRules.legacySettingsLockPending(flag, pins.has(PinKind.SETTINGS), hasPin())) return true
        clearLegacySettingsLock()
        return false
    }

    fun clearLegacySettingsLock() = sp.edit().remove("lock_settings").apply()
}
