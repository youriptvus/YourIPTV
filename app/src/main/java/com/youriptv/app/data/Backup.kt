package com.youriptv.app.data

import org.json.JSONArray
import org.json.JSONException
import org.json.JSONObject

/**
 * Local backup of playlists, favorites, and settings.
 *
 * PIN hashes, salts, and attempt counters are never written: a 4–8 digit PIN hash is trivial to brute-force,
 * and a backup file must not be able to replace or remove either PIN. Restoring parental restrictions only
 * ever adds restrictions (see [BackupRestore.mergeParental]).
 */
data class BackupFavorite(val url: String, val added: Long)

data class BackupPlaylist(
    val ref: Long,
    val name: String,
    val type: SourceType,
    val url: String,
    val username: String = "",
    val password: String = "",
    val epgUrl: String = "",
    val userAgent: String = "",
    val favorites: List<BackupFavorite> = emptyList(),
)

data class BackupParental(
    val blockAdult: Boolean = false,
    val hideLockedGroups: Boolean = false,
    val lockPlaylists: Boolean = false,
    val lockedGroups: Set<String> = emptySet(),
)

data class BackupData(
    val created: Long,
    val appVersion: String,
    val settings: Map<String, Any>,
    val parental: BackupParental,
    val playlists: List<BackupPlaylist>,
    val activeRef: Long,
)

class BackupException(message: String) : Exception(message)

/** Every backed-up setting with its type and allowed range; anything else in a file is ignored. */
object BackupKeys {
    sealed class Spec
    object Bool : Spec()
    class IntRange(val min: Int, val max: Int) : Spec()
    class Text(val maxLength: Int, val allowed: Set<String>? = null) : Spec()

    val SETTINGS: Map<String, Spec> = linkedMapOf(
        "resume_last" to Bool,
        "last_channel" to Text(4096),
        "buffer_ms" to IntRange(500, 30_000),
        "aspect" to IntRange(0, 2),
        "skip_sec" to IntRange(5, 600),
        "audio_delay_ms" to IntRange(-2000, 2000),
        "auto_frame_rate" to Bool,
        "live_format" to Text(8, setOf("ts", "m3u8")),
        "sw_decode" to Bool,
        "user_agent" to Text(512),
        "epg_past_h" to IntRange(0, 168),
        "epg_future_d" to IntRange(1, 14),
        "epg_shift_h" to IntRange(-23, 23),
        "clock_24h" to Bool,
        "show_numbers" to Bool,
        "invert_zap" to Bool,
        "auto_refresh_h" to IntRange(0, 168),
    )

    /** Keys that must never appear in a backup, whatever a future Settings change adds. */
    fun isSecret(key: String) = key.contains("pin") || key == "lock_settings"

    fun sanitize(raw: Map<String, Any?>): Map<String, Any> = buildMap {
        for ((key, spec) in SETTINGS) {
            val v = raw[key] ?: continue
            when (spec) {
                is Bool -> if (v is Boolean) put(key, v)
                is IntRange -> (v as? Number)?.toInt()?.takeIf { it in spec.min..spec.max && v.toDouble() == it.toDouble() }?.let { put(key, it) }
                is Text -> if (v is String && v.length <= spec.maxLength && (spec.allowed == null || v in spec.allowed)) put(key, v)
            }
        }
    }
}

object BackupCodec {
    const val FORMAT = "youriptv-backup"
    const val VERSION = 1
    const val MAX_BYTES = 8L * 1024 * 1024

    fun encode(b: BackupData): String {
        val settings = JSONObject()
        BackupKeys.sanitize(b.settings).forEach { (k, v) -> if (!BackupKeys.isSecret(k)) settings.put(k, v) }
        val parental = JSONObject()
            .put("block_adult", b.parental.blockAdult)
            .put("hide_locked", b.parental.hideLockedGroups)
            .put("lock_playlists", b.parental.lockPlaylists)
            .put("locked_groups", JSONArray(b.parental.lockedGroups.sorted()))
        val playlists = JSONArray()
        b.playlists.forEach { p ->
            playlists.put(JSONObject()
                .put("ref", p.ref).put("name", p.name).put("type", p.type.name).put("url", p.url)
                .put("username", p.username).put("password", p.password)
                .put("epg_url", p.epgUrl).put("user_agent", p.userAgent)
                .put("favorites", JSONArray().apply {
                    p.favorites.forEach { put(JSONObject().put("url", it.url).put("added", it.added)) }
                }))
        }
        return JSONObject()
            .put("format", FORMAT).put("version", VERSION)
            .put("created", b.created).put("app_version", b.appVersion)
            .put("active_playlist_ref", b.activeRef)
            .put("settings", settings).put("parental", parental).put("playlists", playlists)
            .toString(2)
    }

    fun decode(text: String): BackupData {
        val root = try { JSONObject(text) } catch (_: JSONException) {
            throw BackupException("This file is not a YourIPTV backup (it is not valid JSON).")
        }
        if (root.optString("format") != FORMAT) throw BackupException("This file is not a YourIPTV backup.")
        val version = root.optInt("version", -1)
        if (version < 1) throw BackupException("This backup file is damaged (missing version).")
        if (version > VERSION) throw BackupException("This backup was made by a newer YourIPTV. Update the app, then restore it.")
        try {
            val s = root.optJSONObject("settings") ?: JSONObject()
            val raw = buildMap<String, Any?> { s.keys().forEach { put(it, s.opt(it)) } }
            val par = root.optJSONObject("parental") ?: JSONObject()
            val groups = par.optJSONArray("locked_groups")
            val parental = BackupParental(
                blockAdult = par.optBoolean("block_adult", false),
                hideLockedGroups = par.optBoolean("hide_locked", false),
                lockPlaylists = par.optBoolean("lock_playlists", false),
                lockedGroups = buildSet { if (groups != null) for (i in 0 until groups.length()) groups.optString(i).takeIf { it.isNotEmpty() }?.let(::add) },
            )
            val arr = root.optJSONArray("playlists") ?: JSONArray()
            val playlists = (0 until arr.length()).mapNotNull { i ->
                val o = arr.optJSONObject(i) ?: return@mapNotNull null
                val type = SourceType.entries.firstOrNull { it.name == o.optString("type") } ?: return@mapNotNull null
                val url = o.optString("url").trim()
                val name = o.optString("name").trim()
                if (url.isEmpty() || name.isEmpty()) return@mapNotNull null
                if (type == SourceType.XTREAM && o.optString("username").isEmpty()) return@mapNotNull null
                val favs = o.optJSONArray("favorites") ?: JSONArray()
                BackupPlaylist(
                    ref = o.optLong("ref", -(i + 1L)), name = name, type = type, url = url,
                    username = o.optString("username"), password = o.optString("password"),
                    epgUrl = o.optString("epg_url"), userAgent = o.optString("user_agent"),
                    favorites = (0 until favs.length()).mapNotNull { j ->
                        val f = favs.optJSONObject(j) ?: return@mapNotNull null
                        f.optString("url").takeIf { it.isNotEmpty() }?.let { BackupFavorite(it, f.optLong("added", 0)) }
                    },
                )
            }
            return BackupData(
                created = root.optLong("created", 0), appVersion = root.optString("app_version"),
                settings = BackupKeys.sanitize(raw), parental = parental,
                playlists = playlists, activeRef = root.optLong("active_playlist_ref", 0),
            )
        } catch (e: JSONException) {
            throw BackupException("This backup file is damaged and cannot be restored.")
        }
    }
}

object BackupRestore {
    /** A restored playlist is the same as an existing one when it has the same source and login. */
    fun sameSource(a: Playlist, b: BackupPlaylist) =
        a.type == b.type && a.url.trimEnd('/').equals(b.url.trimEnd('/'), ignoreCase = true) && a.username == b.username

    class Plan(
        /** Backup ref → existing local playlist id. */
        val existing: Map<Long, Long>,
        val toAdd: List<BackupPlaylist>,
    )

    fun plan(local: List<Playlist>, backup: BackupData): Plan {
        val existing = LinkedHashMap<Long, Long>()
        val toAdd = ArrayList<BackupPlaylist>()
        for (p in backup.playlists) {
            val match = local.firstOrNull { sameSource(it, p) }
            if (match != null) existing[p.ref] = match.id
            else if (toAdd.none { sameSourceBackup(it, p) }) toAdd += p
        }
        return Plan(existing, toAdd)
    }

    private fun sameSourceBackup(a: BackupPlaylist, b: BackupPlaylist) =
        sameSource(Playlist(name = a.name, type = a.type, url = a.url, username = a.username), b)

    /** Restoring can add restrictions from the backup but never removes one that is active on this device. */
    fun mergeParental(current: BackupParental, backup: BackupParental) = BackupParental(
        blockAdult = current.blockAdult || backup.blockAdult,
        hideLockedGroups = current.hideLockedGroups || backup.hideLockedGroups,
        lockPlaylists = current.lockPlaylists || backup.lockPlaylists,
        lockedGroups = current.lockedGroups + backup.lockedGroups,
    )

    fun summary(b: BackupData, plan: Plan): String {
        val favs = b.playlists.sumOf { it.favorites.size }
        return buildString {
            append("${b.playlists.size} playlist(s): ${plan.toAdd.size} new, ${plan.existing.size} already on this device")
            append("\n$favs favorite(s), ${b.settings.size} setting(s)")
            if (b.parental.lockedGroups.isNotEmpty() || b.parental.blockAdult || b.parental.lockPlaylists) append("\nParental restrictions (added to current ones, never removed)")
        }
    }
}
