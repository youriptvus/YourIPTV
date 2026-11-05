package com.youriptv.app.data

import android.content.ContentValues
import android.content.Context
import android.database.sqlite.SQLiteDatabase
import android.util.JsonReader
import android.util.JsonToken
import android.util.Xml
import org.json.JSONObject
import java.io.InputStreamReader

class Repository(context: Context) {

    private val helper = Db(context.applicationContext)
    private val db: SQLiteDatabase get() = helper.writableDatabase

    // ---------------- Playlists ----------------

    fun playlists(): List<Playlist> = db.rawQuery("SELECT * FROM playlists ORDER BY name COLLATE NOCASE", null).use { c ->
        buildList { while (c.moveToNext()) add(Db.readPlaylist(c)) }
    }

    fun playlist(id: Long): Playlist? = db.rawQuery("SELECT * FROM playlists WHERE id=?", arrayOf(id.toString())).use { c ->
        if (c.moveToFirst()) Db.readPlaylist(c) else null
    }

    /** Provider stream limit for [id]; 0 when unknown. */
    fun connectionLimit(id: Long): Int {
        val p = playlist(id) ?: return 0
        return ConnectionLimit.effective(p, if (p.maxConnections > 0) emptyList() else playlists())
    }

    fun savePlaylist(p: Playlist): Long {
        val v = ContentValues().apply {
            put("name", p.name); put("type", p.type.name); put("url", p.url)
            put("username", p.username); put("password", p.password)
            put("epg_url", p.epgUrl); put("user_agent", p.userAgent)
        }
        return if (p.id == 0L) db.insert("playlists", null, v)
        else if (db.update("playlists", v, "id=?", arrayOf(p.id.toString())) > 0) p.id else -1L
    }

    fun playlistHasUrl(id: Long, url: String): Boolean = url.isNotEmpty() &&
        db.rawQuery("SELECT 1 FROM items WHERE playlist_id=? AND url=? LIMIT 1", arrayOf(id.toString(), url)).use { it.moveToFirst() }

    fun deletePlaylist(id: Long) {
        val a = arrayOf(id.toString())
        db.beginTransaction()
        try {
            for (t in listOf("items", "programs", "favorites", "history", "reminders")) db.delete(t, "playlist_id=?", a)
            // Finished recordings stay playable, but no stream URL (which may embed credentials) is kept.
            db.delete("recordings", "playlist_id=? AND status IN ('SCHEDULED','MISSED')", a)
            db.update("recordings", ContentValues().apply { put("channel_url", "") }, "playlist_id=?", a)
            db.delete("playlists", "id=?", a)
            db.setTransactionSuccessful()
        } finally { db.endTransaction() }
    }

    // ---------------- Refresh ----------------

    fun refresh(p: Playlist, progress: (String) -> Unit) {
        when (p.type) {
            SourceType.M3U -> refreshM3u(p, progress)
            SourceType.XTREAM -> refreshXtream(p, progress)
        }
    }

    private fun refreshM3u(p: Playlist, progress: (String) -> Unit) {
        val archive = xtreamArchiveDays(p, progress)
        progress("Downloading playlist…")
        var headerEpg = ""
        var headerAttrs: Map<String, String> = emptyMap()
        val counts = IntArray(ItemKind.entries.size)
        Http.open(p.url, p.userAgent, 60_000).use { input ->
            replaceItems(p.id) { insert ->
                var liveNum = 0
                M3uParser.parse(input, onHeader = { headerEpg = it.epgUrl; headerAttrs = it.attributes }) { e ->
                    val num = if (e.kind == ItemKind.LIVE) {
                        e.attributes["tvg-chno"]?.toIntOrNull() ?: ++liveNum
                    } else 0
                    val (days, cType, cSource) =
                        if (e.kind == ItemKind.LIVE) Catchup.m3uAttributes(e.attributes, headerAttrs)
                            .let { attrs -> if (attrs.first > 0) attrs else Catchup.fromXtreamArchive(e.url, archive) ?: attrs }
                        else Triple(0, "", "")
                    insert(
                        MediaItem(
                            playlistId = p.id, kind = e.kind, name = e.name, group = e.group,
                            logo = e.attributes["tvg-logo"].orEmpty(), url = e.url,
                            tvgId = e.attributes["tvg-id"].orEmpty(), number = num,
                            catchupDays = days, catchupType = cType, catchupSource = cSource,
                        )
                    )
                    counts[e.kind.ordinal]++
                    val total = counts.sum()
                    if (total % 5000 == 0) progress("Loaded $total entries…")
                }
            }
        }
        val v = ContentValues().apply {
            put("last_updated", System.currentTimeMillis())
            put("live_count", counts[ItemKind.LIVE.ordinal])
            put("movie_count", counts[ItemKind.MOVIE.ordinal])
            put("series_count", counts[ItemKind.EPISODE.ordinal])
            if (p.epgUrl.isBlank() && headerEpg.isNotBlank()) put("epg_url", headerEpg)
        }
        db.update("playlists", v, "id=?", arrayOf(p.id.toString()))
    }

    /**
     * For an M3U link generated by an Xtream panel, asks the same account's API which live streams
     * have a provider archive (`tv_archive`) and for how many days. Empty when not applicable or on error;
     * the playlist still loads without catch-up in that case.
     */
    private fun xtreamArchiveDays(p: Playlist, progress: (String) -> Unit): Map<String, Int> {
        val login = XtreamUrls.parseLogin(p.url) ?: return emptyMap()
        progress("Checking the provider's catch-up archive…")
        val out = HashMap<String, Int>()
        return try {
            streamArray(XtreamUrls.api(login.base, login.username, login.password, "get_live_streams"), p.userAgent) { o ->
                val id = o["stream_id"].orEmpty()
                val days = if (o["tv_archive"] == "1") o["tv_archive_duration"]?.toIntOrNull() ?: 0 else 0
                if (id.isNotEmpty() && days > 0) out[id] = days
            }
            out
        } catch (e: Exception) {
            android.util.Log.w("YourIPTV", "Archive info unavailable: ${e.javaClass.simpleName}")
            emptyMap()
        }
    }

    private fun refreshXtream(p: Playlist, progress: (String) -> Unit) {
        progress("Signing in to provider…")
        val auth = JSONObject(Http.readText(XtreamUrls.api(p.url, p.username, p.password), p.userAgent))
        val user = auth.optJSONObject("user_info")
            ?: throw IllegalStateException("Provider did not return account information. Check server URL and credentials.")
        if (user.optInt("auth", 1) == 0) throw IllegalStateException("Provider rejected the username or password.")
        val status = user.optString("status")
        val maxConn = user.optString("max_connections").toIntOrNull() ?: 0
        val expires = (user.optString("exp_date").toLongOrNull() ?: 0L) * 1000L
        val serverTz = auth.optJSONObject("server_info")?.optString("timezone").orEmpty().trim()

        val counts = IntArray(ItemKind.entries.size)
        replaceItems(p.id) { insert ->
            for ((kind, catAction, listAction) in listOf(
                Triple(ItemKind.LIVE, "get_live_categories", "get_live_streams"),
                Triple(ItemKind.MOVIE, "get_vod_categories", "get_vod_streams"),
                Triple(ItemKind.SERIES, "get_series_categories", "get_series"),
            )) {
                progress("Loading ${kind.name.lowercase()} categories…")
                val cats = HashMap<String, String>()
                streamArray(XtreamUrls.api(p.url, p.username, p.password, catAction), p.userAgent) { o ->
                    cats[o["category_id"].orEmpty()] = o["category_name"].orEmpty().trim()
                }
                progress("Loading ${kind.name.lowercase()} list…")
                var n = 0
                streamArray(XtreamUrls.api(p.url, p.username, p.password, listAction), p.userAgent) { o ->
                    val id = (if (kind == ItemKind.SERIES) o["series_id"] else o["stream_id"]).orEmpty()
                    if (id.isEmpty()) return@streamArray
                    val ext = o["container_extension"].orEmpty()
                    val url = when (kind) {
                        ItemKind.LIVE -> XtreamUrls.live(p.url, p.username, p.password, id)
                        ItemKind.MOVIE -> XtreamUrls.movie(p.url, p.username, p.password, id, ext)
                        else -> "xtream-series:$id"
                    }
                    n++
                    insert(
                        MediaItem(
                            playlistId = p.id, kind = kind, name = o["name"].orEmpty().trim(),
                            group = cats[o["category_id"].orEmpty()] ?: "Uncategorized",
                            logo = (o["stream_icon"] ?: o["cover"]).orEmpty(), url = url,
                            tvgId = o["epg_channel_id"].orEmpty(),
                            number = if (kind == ItemKind.LIVE) o["num"]?.toIntOrNull() ?: n else 0,
                            remoteId = id, extension = ext,
                            catchupDays = if (o["tv_archive"] == "1") o["tv_archive_duration"]?.toIntOrNull() ?: 0 else 0,
                        )
                    )
                    counts[kind.ordinal]++
                    if (n % 5000 == 0) progress("Loaded $n ${kind.name.lowercase()} entries…")
                }
            }
        }
        val v = ContentValues().apply {
            put("last_updated", System.currentTimeMillis())
            put("live_count", counts[ItemKind.LIVE.ordinal])
            put("movie_count", counts[ItemKind.MOVIE.ordinal])
            put("series_count", counts[ItemKind.SERIES.ordinal])
            put("max_conn", maxConn); put("status", status); put("expires", expires); put("server_tz", serverTz)
        }
        db.update("playlists", v, "id=?", arrayOf(p.id.toString()))
    }

    /** Streams a JSON array of flat objects; nested values are skipped. */
    private fun streamArray(url: String, ua: String, onObject: (Map<String, String?>) -> Unit) {
        Http.open(url, ua, 60_000).use { input ->
            JsonReader(InputStreamReader(input, Charsets.UTF_8)).use { r ->
                if (r.peek() != JsonToken.BEGIN_ARRAY) { r.skipValue(); return }
                r.beginArray()
                val map = HashMap<String, String?>()
                while (r.hasNext()) {
                    if (r.peek() != JsonToken.BEGIN_OBJECT) { r.skipValue(); continue }
                    map.clear()
                    r.beginObject()
                    while (r.hasNext()) {
                        val key = r.nextName()
                        when (r.peek()) {
                            JsonToken.STRING, JsonToken.NUMBER -> map[key] = r.nextString()
                            JsonToken.BOOLEAN -> map[key] = if (r.nextBoolean()) "1" else "0"
                            JsonToken.NULL -> { r.nextNull(); map[key] = null }
                            else -> r.skipValue()
                        }
                    }
                    r.endObject()
                    onObject(map)
                }
                r.endArray()
            }
        }
    }

    private fun replaceItems(playlistId: Long, block: ((MediaItem) -> Unit) -> Unit) {
        val database = db
        database.beginTransaction()
        try {
            database.delete("items", "playlist_id=?", arrayOf(playlistId.toString()))
            val stmt = database.compileStatement(
                "INSERT INTO items(playlist_id,kind,name,name_lc,grp,logo,url,tvg_id,num,remote_id,ext,catchup_days,catchup_type,catchup_src) VALUES(?,?,?,?,?,?,?,?,?,?,?,?,?,?)"
            )
            block { i ->
                stmt.clearBindings()
                stmt.bindLong(1, i.playlistId); stmt.bindLong(2, i.kind.ordinal.toLong())
                stmt.bindString(3, i.name); stmt.bindString(4, i.name.lowercase()); stmt.bindString(5, i.group)
                stmt.bindString(6, i.logo); stmt.bindString(7, i.url); stmt.bindString(8, i.tvgId)
                stmt.bindLong(9, i.number.toLong()); stmt.bindString(10, i.remoteId); stmt.bindString(11, i.extension)
                stmt.bindLong(12, i.catchupDays.toLong())
                stmt.bindString(13, i.catchupType); stmt.bindString(14, i.catchupSource)
                stmt.executeInsert()
            }
            database.setTransactionSuccessful()
        } finally { database.endTransaction() }
    }

    // ---------------- EPG ----------------

    fun epgSource(p: Playlist): String = when {
        p.epgUrl.isNotBlank() -> p.epgUrl
        p.type == SourceType.XTREAM -> XtreamUrls.xmltv(p.url, p.username, p.password)
        // An M3U link from an Xtream panel (get.php with login) has the provider guide at xmltv.php.
        else -> XtreamUrls.parseLogin(p.url)?.let { XtreamUrls.xmltv(it.base, it.username, it.password) }.orEmpty()
    }

    /** Builds a seekable archive item for [prog] on [ch], or null when the provider offers no catch-up for it. */
    fun catchupItem(ch: MediaItem, prog: Program, now: Long = System.currentTimeMillis()): MediaItem? {
        if (!Catchup.available(ch.catchupDays, prog.start, prog.end, now)) return null
        val p = playlist(ch.playlistId) ?: return null
        val url = when (p.type) {
            SourceType.XTREAM -> if (ch.remoteId.isBlank()) return null else
                XtreamUrls.timeshift(
                    p.url, p.username, p.password, ch.remoteId, prog.start, Catchup.durationMinutes(prog.start, prog.end, now),
                    XtreamUrls.serverZone(p.serverTimeZone),
                )
            SourceType.M3U -> Catchup.m3uUrl(ch.catchupType, ch.catchupSource, ch.url, prog.start, prog.end, now) ?: return null
        }
        return ch.copy(id = 0, kind = ItemKind.MOVIE, name = "${prog.title} · ${ch.name}", url = url, isCatchup = true)
    }

    /** Downloads XMLTV and stores programmes for this playlist's live channels. Returns programme count. */
    fun refreshEpg(p: Playlist, pastHours: Int, futureDays: Int, progress: (String) -> Unit): Int {
        val src = epgSource(p)
        if (src.isBlank()) throw IllegalStateException("No EPG source is configured for this playlist.")
        progress("Downloading guide data…")
        val pid = p.id.toString()
        val known = HashSet<String>()
        db.rawQuery("SELECT DISTINCT tvg_id FROM items WHERE playlist_id=? AND kind=0 AND tvg_id<>''", arrayOf(pid)).use { c ->
            while (c.moveToNext()) known.add(c.getString(0))
        }
        val nameToId = HashMap<String, String>()
        val now = System.currentTimeMillis()
        // Keep past programmes for the whole provider archive so every catch-up programme can be picked in the guide.
        val archiveDays = db.rawQuery("SELECT MAX(catchup_days) FROM items WHERE playlist_id=? AND kind=0", arrayOf(pid))
            .use { c -> if (c.moveToFirst()) c.getInt(0) else 0 }
        val from = now - Catchup.pastWindowHours(pastHours, archiveDays) * 3_600_000L
        val to = now + futureDays * 86_400_000L
        var matched = false
        var count = 0
        val database = db
        Http.open(src, p.userAgent, 60_000).use { input ->
            database.beginTransaction()
            try {
                database.delete("programs", "playlist_id=?", arrayOf(pid))
                val stmt = database.compileStatement("INSERT INTO programs(playlist_id,channel_id,start,stop,title,descr) VALUES(?,?,?,?,?,?)")
                val parser = Xml.newPullParser()
                parser.setInput(input, null)
                XmltvParser.parse(
                    parser,
                    onChannel = { ch -> if (ch.displayName.isNotBlank()) nameToId.putIfAbsent(normalize(ch.displayName), ch.id) },
                    acceptChannel = { id ->
                        if (!matched) { matched = true; matchByName(p.id, nameToId, known); progress("Importing guide…") }
                        id in known
                    },
                ) { prog ->
                    if (prog.end >= from && prog.start <= to) {
                        stmt.clearBindings()
                        stmt.bindLong(1, p.id); stmt.bindString(2, prog.channelId)
                        stmt.bindLong(3, prog.start); stmt.bindLong(4, prog.end)
                        stmt.bindString(5, prog.title); stmt.bindString(6, prog.description)
                        stmt.executeInsert()
                        count++
                        if (count % 20000 == 0) progress("Imported $count programmes…")
                    }
                }
                database.setTransactionSuccessful()
            } finally { database.endTransaction() }
        }
        return count
    }

    /** Assigns EPG ids to channels without tvg-id when their name matches an XMLTV display name. */
    private fun matchByName(playlistId: Long, nameToId: Map<String, String>, known: MutableSet<String>) {
        if (nameToId.isEmpty()) return
        val updates = ArrayList<Pair<Long, String>>()
        db.rawQuery("SELECT id,name FROM items WHERE playlist_id=? AND kind=0 AND (tvg_id IS NULL OR tvg_id='')", arrayOf(playlistId.toString())).use { c ->
            while (c.moveToNext()) {
                nameToId[normalize(c.getString(1))]?.let { updates.add(c.getLong(0) to it) }
            }
        }
        for ((id, epg) in updates) {
            db.execSQL("UPDATE items SET tvg_id=? WHERE id=?", arrayOf<Any>(epg, id))
            known.add(epg)
        }
    }

    fun nowAndNext(playlistId: Long, channelId: String, now: Long = System.currentTimeMillis()): List<Program> {
        if (channelId.isBlank()) return emptyList()
        return programsQuery("playlist_id=? AND channel_id=? AND stop>?", arrayOf(playlistId.toString(), channelId, now.toString()), 2)
    }

    fun programs(playlistId: Long, channelId: String, from: Long, to: Long): List<Program> {
        if (channelId.isBlank()) return emptyList()
        return programsQuery(
            "playlist_id=? AND channel_id=? AND stop>? AND start<?",
            arrayOf(playlistId.toString(), channelId, from.toString(), to.toString()), 500,
        )
    }

    fun programCount(playlistId: Long): Int =
        db.rawQuery("SELECT COUNT(*) FROM programs WHERE playlist_id=?", arrayOf(playlistId.toString())).use { c -> if (c.moveToFirst()) c.getInt(0) else 0 }

    private fun programsQuery(where: String, args: Array<String>, limit: Int): List<Program> =
        db.rawQuery("SELECT channel_id,start,stop,title,descr FROM programs WHERE $where ORDER BY start LIMIT $limit", args).use { c ->
            buildList { while (c.moveToNext()) add(Program(c.getString(0), c.getLong(1), c.getLong(2), c.getString(3).orEmpty(), c.getString(4).orEmpty())) }
        }

    // ---------------- Browsing ----------------

    fun groups(playlistId: Long, kind: ItemKind): List<GroupInfo> =
        db.rawQuery(
            "SELECT grp, COUNT(*), MIN(id) AS f FROM items WHERE playlist_id=? AND kind=? GROUP BY grp ORDER BY f",
            arrayOf(playlistId.toString(), kind.ordinal.toString()),
        ).use { c -> buildList { while (c.moveToNext()) add(GroupInfo(c.getString(0), c.getInt(1))) } }

    fun items(playlistId: Long, kind: ItemKind, group: String?): List<MediaItem> {
        val sql = StringBuilder("SELECT * FROM items WHERE playlist_id=? AND kind=?")
        val args = arrayListOf(playlistId.toString(), kind.ordinal.toString())
        if (group != null) { sql.append(" AND grp=?"); args.add(group) }
        sql.append(" ORDER BY id")
        return query(sql.toString(), args.toTypedArray())
    }

    fun search(playlistId: Long, text: String, kinds: List<ItemKind>, limit: Int = 300): List<MediaItem> {
        val q = text.trim().lowercase()
        if (q.isEmpty()) return emptyList()
        val kindList = kinds.joinToString(",") { it.ordinal.toString() }
        val escaped = q.replace("\\", "\\\\").replace("%", "\\%").replace("_", "\\_")
        return query(
            "SELECT * FROM items WHERE playlist_id=? AND kind IN ($kindList) AND name_lc LIKE ? ESCAPE '\\' ORDER BY kind, id LIMIT $limit",
            arrayOf(playlistId.toString(), "%$escaped%"),
        )
    }

    fun itemByUrl(playlistId: Long, url: String): MediaItem? =
        query("SELECT * FROM items WHERE playlist_id=? AND url=? LIMIT 1", arrayOf(playlistId.toString(), url)).firstOrNull()

    fun channelByNumber(playlistId: Long, number: Int): MediaItem? =
        query("SELECT * FROM items WHERE playlist_id=? AND kind=0 AND num=? ORDER BY id LIMIT 1", arrayOf(playlistId.toString(), number.toString())).firstOrNull()

    /** Live channels whose current or upcoming guide programmes match [text]. */
    fun searchPrograms(playlistId: Long, text: String, limit: Int = 100): List<MediaItem> {
        val q = text.trim()
        if (q.isEmpty()) return emptyList()
        val escaped = q.replace("\\", "\\\\").replace("%", "\\%").replace("_", "\\_")
        return query(
            "SELECT i.* FROM programs p JOIN items i ON i.playlist_id=p.playlist_id AND i.kind=0 AND i.tvg_id=p.channel_id " +
                "WHERE p.playlist_id=? AND p.stop>? AND p.title LIKE ? ESCAPE '\\' GROUP BY i.id ORDER BY MIN(p.start) LIMIT $limit",
            arrayOf(playlistId.toString(), System.currentTimeMillis().toString(), "%$escaped%"),
        )
    }

    private fun query(sql: String, args: Array<String>): List<MediaItem> = db.rawQuery(sql, args).use { c ->
        buildList { while (c.moveToNext()) add(Db.readItem(c)) }
    }

    // ---------------- Favorites & history ----------------

    fun isFavorite(item: MediaItem): Boolean =
        db.rawQuery("SELECT 1 FROM favorites WHERE playlist_id=? AND url=?", arrayOf(item.playlistId.toString(), item.url)).use { it.moveToFirst() }

    fun setFavorite(item: MediaItem, fav: Boolean) {
        if (fav) db.insertWithOnConflict("favorites", null, ContentValues().apply {
            put("playlist_id", item.playlistId); put("url", item.url); put("added", System.currentTimeMillis())
        }, SQLiteDatabase.CONFLICT_REPLACE)
        else db.delete("favorites", "playlist_id=? AND url=?", arrayOf(item.playlistId.toString(), item.url))
    }

    /** Favorite URLs as stored, including ones whose channel is not loaded yet (used by backups). */
    fun favoriteUrls(playlistId: Long): List<BackupFavorite> =
        db.rawQuery("SELECT url, added FROM favorites WHERE playlist_id=? ORDER BY added", arrayOf(playlistId.toString())).use { c ->
            buildList { while (c.moveToNext()) add(BackupFavorite(c.getString(0), c.getLong(1))) }
        }

    /** Adds favorites without replacing existing ones; they appear once the playlist's channels are loaded. */
    fun addFavoriteUrls(playlistId: Long, favs: List<BackupFavorite>): Int {
        var added = 0
        db.beginTransaction()
        try {
            for (f in favs) {
                if (db.insertWithOnConflict("favorites", null, ContentValues().apply {
                        put("playlist_id", playlistId); put("url", f.url); put("added", if (f.added > 0) f.added else System.currentTimeMillis())
                    }, SQLiteDatabase.CONFLICT_IGNORE) != -1L) added++
            }
            db.setTransactionSuccessful()
        } finally { db.endTransaction() }
        return added
    }

    fun favorites(playlistId: Long, kind: ItemKind? = null): List<MediaItem> = query(
        "SELECT i.* FROM favorites f JOIN items i ON i.playlist_id=f.playlist_id AND i.url=f.url WHERE f.playlist_id=?" +
            (if (kind != null) " AND i.kind=${kind.ordinal}" else "") + " GROUP BY i.url ORDER BY f.added",
        arrayOf(playlistId.toString()),
    )

    fun recordHistory(item: MediaItem, position: Long = 0, duration: Long = 0) {
        if (item.isCatchup || item.isLocal) return
        db.insertWithOnConflict("history", null, ContentValues().apply {
            put("playlist_id", item.playlistId); put("url", item.url); put("watched", System.currentTimeMillis())
            put("position", position); put("duration", duration)
            put("name", item.name); put("kind", item.kind.ordinal); put("grp", item.group); put("logo", item.logo)
        }, SQLiteDatabase.CONFLICT_REPLACE)
    }

    fun resumePosition(item: MediaItem): Long = if (item.isCatchup || item.isLocal) 0 else
        db.rawQuery("SELECT position, duration FROM history WHERE playlist_id=? AND url=?", arrayOf(item.playlistId.toString(), item.url)).use { c ->
            if (c.moveToFirst()) {
                val pos = c.getLong(0); val dur = c.getLong(1)
                if (dur > 0 && pos > 10_000 && pos < dur - 30_000) pos else 0
            } else 0
        }

    fun history(playlistId: Long, limit: Int = 100): List<MediaItem> = db.rawQuery(
        "SELECT h.url AS h_url, h.name AS h_name, h.kind AS h_kind, h.grp AS h_grp, h.logo AS h_logo, i.* " +
            "FROM history h LEFT JOIN items i ON i.playlist_id=h.playlist_id AND i.url=h.url " +
            "WHERE h.playlist_id=? GROUP BY h.url ORDER BY MAX(h.watched) DESC LIMIT $limit",
        arrayOf(playlistId.toString()),
    ).use { c ->
        val idCol = c.getColumnIndexOrThrow("id")
        buildList {
            while (c.moveToNext()) {
                when {
                    !c.isNull(idCol) -> add(Db.readItem(c))
                    !c.isNull(c.getColumnIndexOrThrow("h_name")) && !c.isNull(c.getColumnIndexOrThrow("h_kind")) -> add(
                        MediaItem(
                            playlistId = playlistId,
                            kind = ItemKind.entries.getOrElse(c.getInt(c.getColumnIndexOrThrow("h_kind"))) { ItemKind.MOVIE },
                            name = c.getString(c.getColumnIndexOrThrow("h_name")),
                            group = c.getString(c.getColumnIndexOrThrow("h_grp")).orEmpty(),
                            logo = c.getString(c.getColumnIndexOrThrow("h_logo")).orEmpty(),
                            url = c.getString(c.getColumnIndexOrThrow("h_url")),
                        )
                    )
                }
            }
        }
    }

    fun clearHistory(playlistId: Long) { db.delete("history", "playlist_id=?", arrayOf(playlistId.toString())) }

    // ---------------- Reminders ----------------

    fun addReminder(r: Reminder) {
        db.insertWithOnConflict("reminders", null, ContentValues().apply {
            put("playlist_id", r.playlistId); put("channel_url", r.channelUrl); put("channel_name", r.channelName)
            put("title", r.title); put("start", r.start); put("stop", r.end)
        }, SQLiteDatabase.CONFLICT_REPLACE)
    }

    fun deleteReminder(playlistId: Long, channelUrl: String, start: Long) {
        db.delete("reminders", "playlist_id=? AND channel_url=? AND start=?", arrayOf(playlistId.toString(), channelUrl, start.toString()))
    }

    fun hasReminder(playlistId: Long, channelUrl: String, start: Long): Boolean = db.rawQuery(
        "SELECT 1 FROM reminders WHERE playlist_id=? AND channel_url=? AND start=?", arrayOf(playlistId.toString(), channelUrl, start.toString()),
    ).use { it.moveToFirst() }

    fun reminders(playlistId: Long? = null): List<Reminder> = db.rawQuery(
        "SELECT * FROM reminders" + (if (playlistId != null) " WHERE playlist_id=$playlistId" else "") + " ORDER BY start", null,
    ).use { c -> buildList { while (c.moveToNext()) add(Db.readReminder(c)) } }

    // ---------------- Recordings ----------------

    fun addRecording(r: Recording): Long = db.insert("recordings", null, ContentValues().apply {
        put("playlist_id", r.playlistId); put("channel_url", r.channelUrl); put("channel_name", r.channelName)
        put("title", r.title); put("start", r.start); put("stop", r.end); put("file", r.file)
        put("status", r.status.name); put("bytes", r.bytes); put("error", r.error)
    })

    fun updateRecording(id: Long, status: RecordingStatus? = null, file: String? = null, bytes: Long? = null, error: String? = null, end: Long? = null) {
        val v = ContentValues()
        status?.let { v.put("status", it.name) }
        file?.let { v.put("file", it) }
        bytes?.let { v.put("bytes", it) }
        error?.let { v.put("error", it) }
        end?.let { v.put("stop", it) }
        if (v.size() > 0) db.update("recordings", v, "id=?", arrayOf(id.toString()))
    }

    fun deleteRecording(id: Long) { db.delete("recordings", "id=?", arrayOf(id.toString())) }

    fun recording(id: Long): Recording? =
        db.rawQuery("SELECT * FROM recordings WHERE id=?", arrayOf(id.toString())).use { c -> if (c.moveToFirst()) Db.readRecording(c) else null }

    fun recordings(): List<Recording> = db.rawQuery("SELECT * FROM recordings ORDER BY start DESC", null).use { c ->
        buildList { while (c.moveToNext()) add(Db.readRecording(c)) }
    }

    fun scheduledRecording(playlistId: Long, channelUrl: String, start: Long): Recording? = db.rawQuery(
        "SELECT * FROM recordings WHERE playlist_id=? AND channel_url=? AND start=? AND status IN ('SCHEDULED','RECORDING')",
        arrayOf(playlistId.toString(), channelUrl, start.toString()),
    ).use { c -> if (c.moveToFirst()) Db.readRecording(c) else null }

    // ---------------- Series ----------------

    fun seriesEpisodes(p: Playlist, series: MediaItem): List<Episode> {
        val json = JSONObject(Http.readText(XtreamUrls.api(p.url, p.username, p.password, "get_series_info", mapOf("series_id" to series.remoteId)), p.userAgent))
        return parseSeriesEpisodes(json) { id, ext -> XtreamUrls.episode(p.url, p.username, p.password, id, ext) }
    }

    companion object {
        fun normalize(name: String): String = name.lowercase().filter { it.isLetterOrDigit() }

        fun parseSeriesEpisodes(json: JSONObject, urlFor: (String, String) -> String): List<Episode> {
            val result = ArrayList<Episode>()
            val episodes = json.optJSONObject("episodes") ?: return result
            for (seasonKey in episodes.keys()) {
                val arr = episodes.optJSONArray(seasonKey) ?: continue
                for (i in 0 until arr.length()) {
                    val e = arr.optJSONObject(i) ?: continue
                    val id = e.optString("id")
                    if (id.isEmpty()) continue
                    result.add(
                        Episode(
                            id = id,
                            season = e.optString("season").toIntOrNull() ?: seasonKey.toIntOrNull() ?: 0,
                            episode = e.optString("episode_num").toIntOrNull() ?: (i + 1),
                            title = e.optString("title"),
                            url = urlFor(id, e.optString("container_extension")),
                            plot = e.optJSONObject("info")?.optString("plot").orEmpty(),
                        )
                    )
                }
            }
            result.sortWith(compareBy({ it.season }, { it.episode }))
            return result
        }
    }
}
