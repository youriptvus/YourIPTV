package com.youriptv.app.data

import android.content.ContentValues
import android.content.Context
import android.database.sqlite.SQLiteDatabase
import android.util.JsonReader
import android.util.JsonToken
import android.util.Xml
import org.json.JSONObject
import java.io.ByteArrayOutputStream
import java.io.InputStreamReader
import java.io.File
import java.io.DataInputStream
import java.io.DataOutputStream
import java.util.concurrent.CancellationException

class Repository(context: Context) : java.io.Closeable {

    private val helper = Db(context.applicationContext)
    private val settings = Settings(context.applicationContext)
    private val stageDir = File(context.applicationContext.cacheDir, "refresh-stage").apply { mkdirs() }
    private val db: SQLiteDatabase get() = helper.writableDatabase
    private val watchStore = WatchProgressStore({ db }, ::playlist)
    override fun close() { watchStore.close(); helper.close() }

    fun watchProgress(items: List<MediaItem>): Map<String, WatchProgress> {
        val result = HashMap<String, WatchProgress>()
        items.groupBy { it.playlistId }.forEach { (id, rows) ->
            val source = playlist(id) ?: return@forEach
            result.putAll(watchStore.read(source, rows))
            if (watchStore.isClearing(id)) return@forEach
            // Bounded legacy migration, including old episode history absent from the catalogue.
            for (chunk in rows.filter { WatchPolicy.supports(it) && it.url !in result }.chunked(500)) {
                db.rawQuery("SELECT url,position,duration,watched FROM history WHERE playlist_id=? AND (scope='' OR scope=?) AND url IN (${chunk.joinToString(",") { "?" }})",
                    arrayOf(id.toString(), WatchPolicy.scope(source), *chunk.map { it.url }.toTypedArray())).use { c -> while (c.moveToNext()) {
                    result[c.getString(0)] = WatchProgress(c.getLong(1), c.getLong(2), false, c.getLong(3))
                } }
            }
        }
        return result
    }

    fun watchProgress(item: MediaItem): WatchProgress? = watchProgress(listOf(item))[item.url]

    fun saveWatchProgress(item: MediaItem, position: Long, duration: Long, ended: Boolean = false) =
        watchStore.save(item, position, duration, ended)

    fun markWatched(item: MediaItem, watched: Boolean) {
        val old = watchProgress(item)
        watchStore.save(item, if (watched) old?.duration ?: 0 else 0, old?.duration ?: 0, watched = watched)
    }

    fun clearWatchProgress(item: MediaItem) {
        watchStore.clear(item)
    }

    fun withWatchProgress(items: List<MediaItem>): List<MediaItem> {
        val saved = watchProgress(items)
        return items.map { it.copy(watch = saved[it.url]) }
    }

    fun continueWatching(id: Long, kind: ItemKind): List<MediaItem> {
        val source = playlist(id) ?: return emptyList()
        val entries = WatchPolicy.continuation(watchStore.entries(source)).filter {
            if (kind == ItemKind.MOVIE) it.item.kind == ItemKind.MOVIE else it.item.kind == ItemKind.EPISODE
        }
        val catalog = itemsByUrls(id, entries.flatMap { listOf(it.item.url, it.item.seriesUrl) })
        val remoteIds = entries.map { it.item.remoteId }.filter { it.isNotBlank() }.distinct()
        val byRemote = HashMap<Pair<ItemKind, String>, MediaItem>()
        if (source.type == SourceType.XTREAM) for (chunk in remoteIds.chunked(500)) {
            db.rawQuery("SELECT * FROM items WHERE playlist_id=? AND remote_id IN (${chunk.joinToString(",") { "?" }})",
                arrayOf(id.toString(), *chunk.toTypedArray())).use { c -> while (c.moveToNext()) {
                val fresh = Db.readItem(c); byRemote[fresh.kind to fresh.remoteId] = fresh
            } }
        }
        val byStableKey = HashMap<String, MediaItem>()
        if (source.type == SourceType.M3U) {
            val missing = entries.filter { it.item.url !in catalog }.mapTo(HashSet()) { it.key }
            if (missing.isNotEmpty()) db.rawQuery("SELECT * FROM items WHERE playlist_id=? AND kind IN (?,?)",
                arrayOf(id.toString(), ItemKind.MOVIE.ordinal.toString(), ItemKind.EPISODE.ordinal.toString())).use { c ->
                // Stream the existing catalogue only when signed URLs changed. Retain just matching recent items.
                while (missing.isNotEmpty() && c.moveToNext()) {
                    val fresh = Db.readItem(c); val key = WatchPolicy.key(source, fresh)
                    if (missing.remove(key)) byStableKey[key] = fresh
                }
            }
        }
        return entries.take(100).mapNotNull { entry ->
            val item = entry.item
            val parent = catalog[item.seriesUrl]
            val fresh = byRemote[item.kind to item.remoteId] ?: catalog[item.url] ?: byStableKey[entry.key]
            // Missing movies/M3U episodes are no longer playable. Xtream episode rows live in detail metadata.
            if (item.kind == ItemKind.EPISODE && item.seriesUrl.isNotBlank() && source.type == SourceType.XTREAM && parent == null) null
            else if (fresh == null && (item.kind == ItemKind.MOVIE || source.type == SourceType.M3U)) null
            else (fresh?.copy(seriesKey = item.seriesKey, seriesName = item.seriesName, seriesUrl = item.seriesUrl,
                seriesRemoteId = item.seriesRemoteId, season = item.season, episode = item.episode) ?: item)
                .let { if (parent != null) it.copy(group = parent.group, seriesName = parent.name) else it }.copy(watch = entry.progress)
        }
    }

    // ---------------- Playlists ----------------

    fun playlists(): List<Playlist> = db.rawQuery("SELECT * FROM playlists ORDER BY name COLLATE NOCASE", null).use { c ->
        buildList { while (c.moveToNext()) add(Db.readPlaylist(c)) }
    }

    fun playlist(id: Long): Playlist? = db.rawQuery("SELECT * FROM playlists WHERE id=?", arrayOf(id.toString())).use { c ->
        if (c.moveToFirst()) Db.readPlaylist(c) else null
    }

    fun itemsByUrls(playlistId: Long, urls: List<String>): Map<String, MediaItem> {
        val result = HashMap<String, MediaItem>()
        for (chunk in urls.distinct().chunked(800)) {
            val arguments = arrayOf(playlistId.toString(), *chunk.toTypedArray())
            val placeholders = chunk.joinToString(",") { "?" }
            db.rawQuery("SELECT * FROM items WHERE playlist_id=? AND url IN ($placeholders)", arguments).use { cursor ->
                while (cursor.moveToNext()) Db.readItem(cursor).let { result[it.url] = it }
            }
        }
        return result
    }

    /** Provider stream limit for [id]; 0 when unknown. */
    fun connectionLimit(id: Long): Int {
        val p = playlist(id) ?: return 0
        return ConnectionLimit.effective(p, if (p.maxConnections > 0) emptyList() else playlists())
    }

    fun savePlaylist(p: Playlist): Long {
        // A server/account edit keeps the playlist id but must not reuse details from the previous account.
        invalidateDetails()
        if (p.id != 0L) playlist(p.id)?.let { old ->
            db.execSQL("UPDATE history SET scope=? WHERE playlist_id=? AND scope=''", arrayOf(WatchPolicy.scope(old), p.id))
        }
        val v = ContentValues().apply {
            put("name", p.name); put("type", p.type.name); put("url", p.url)
            put("username", p.username); put("password", p.password)
            put("epg_url", p.epgUrl); put("user_agent", p.userAgent)
            if (p.id == 0L) put("refresh_hours", RefreshPolicy.migratedHours(p.refreshHours))
        }
        return if (p.id == 0L) db.insert("playlists", null, v)
        else if (db.update("playlists", v, "id=?", arrayOf(p.id.toString())) > 0) p.id else -1L
    }

    fun setRefreshHours(id: Long, hours: Int) {
        require(hours in 0..168)
        db.update("playlists", ContentValues().apply { put("refresh_hours", hours) }, "id=?", arrayOf(id.toString()))
    }

    fun initializeRefreshIntervals(legacyExplicit: Int?) {
        db.execSQL("UPDATE playlists SET refresh_hours=? WHERE refresh_hours IS NULL", arrayOf<Any>(RefreshPolicy.migratedHours(legacyExplicit)))
    }

    fun beginRefreshAttempt(id: Long, epg: Boolean, time: Long = System.currentTimeMillis(), expectedIdentity: String? = null) {
        updateRefreshStatus(id, ContentValues().apply {
            put(if (epg) "epg_attempt" else "catalog_attempt", time)
            put(if (epg) "epg_error" else "catalog_error", "")
        }, expectedIdentity)
    }

    /** Callers provide fixed safe text; exception messages can contain credential-bearing URLs. */
    fun failRefreshAttempt(id: Long, epg: Boolean, error: String, expectedIdentity: String? = null) {
        updateRefreshStatus(id, ContentValues().apply { put(if (epg) "epg_error" else "catalog_error", error.take(300)) }, expectedIdentity)
    }

    private fun updateRefreshStatus(id: Long, values: ContentValues, expectedIdentity: String?) {
        val database = db
        database.beginTransaction()
        try {
            val current = playlist(id) ?: return
            if (expectedIdentity != null && sourceIdentity(current) != expectedIdentity) return
            database.update("playlists", values, "id=?", arrayOf(id.toString()))
            database.setTransactionSuccessful()
        } finally { database.endTransaction() }
    }

    fun recoverInterruptedRefreshes() {
        for ((attempt, success, error) in listOf(Triple("catalog_attempt", "last_updated", "catalog_error"), Triple("epg_attempt", "epg_updated", "epg_error"))) {
            db.execSQL("UPDATE playlists SET $error='The previous update was interrupted. Try again.' WHERE $attempt>$success AND $error=''")
        }
        // Only run at process initialization, before any coordinator begins a staged import.
        stageDir.listFiles()?.filter { it.isFile }?.forEach { it.delete() }
    }

    fun sourceIdentity(p: Playlist): String = RefreshPolicy.fingerprint(p.type.name, p.url, p.username,
        p.password, p.epgUrl, Http.effectiveUserAgent(p.userAgent))

    fun sourceKey(p: Playlist): String = RefreshPolicy.fingerprint(epgSource(p), Http.effectiveUserAgent(p.userAgent))

    private fun requireCurrent(p: Playlist, expectedIdentity: String = sourceIdentity(p)): Playlist {
        val current = playlist(p.id) ?: throw StaleRefreshData()
        if (sourceIdentity(current) != expectedIdentity) throw StaleRefreshData()
        return current
    }

    fun playlistHasUrl(id: Long, url: String): Boolean = url.isNotEmpty() &&
        db.rawQuery("SELECT 1 FROM items WHERE playlist_id=? AND url=? LIMIT 1", arrayOf(id.toString(), url)).use { it.moveToFirst() }

    fun deletePlaylist(id: Long) {
        invalidateDetails()
        val a = arrayOf(id.toString())
        db.beginTransaction()
        try {
            for (t in listOf("items", "programs", "favorites", "history", "watch_progress", "reminders")) db.delete(t, "playlist_id=?", a)
            // Finished recordings stay playable, but no stream URL (which may embed credentials) is kept.
            db.delete("recordings", "playlist_id=? AND status IN ('SCHEDULED','MISSED')", a)
            db.update("recordings", ContentValues().apply { put("channel_url", "") }, "playlist_id=?", a)
            db.delete("playlists", "id=?", a)
            db.setTransactionSuccessful()
        } finally { db.endTransaction() }
    }

    // ---------------- Refresh ----------------

    fun refresh(p: Playlist, progress: (String) -> Unit, isCancelled: () -> Boolean = { false }) {
        RefreshPolicy.checkCancelled(isCancelled)
        requireCurrent(p)
        when (p.type) {
            SourceType.M3U -> refreshM3u(p, progress, isCancelled)
            SourceType.XTREAM -> refreshXtream(p, progress, isCancelled)
        }
    }

    private fun refreshM3u(p: Playlist, progress: (String) -> Unit, isCancelled: () -> Boolean) {
        val expectedIdentity = sourceIdentity(p)
        val archive = xtreamArchiveDays(p, progress, isCancelled)
        progress("Downloading playlist…")
        var headerEpg = ""
        var headerAttrs: Map<String, String> = emptyMap()
        val counts = IntArray(ItemKind.entries.size)
        val staged = stageItems(isCancelled) { insert ->
            Http.open(p.url, p.userAgent, 15_000).use { input ->
                var liveNum = 0
                M3uParser.parse(input, onHeader = { headerEpg = it.epgUrl; headerAttrs = it.attributes }, strict = true, isCancelled = isCancelled) { e ->
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
        try { publishCatalog(p, staged, counts.sum(), v, isCancelled, expectedIdentity) } finally { staged.delete() }
    }

    /**
     * For an M3U link generated by an Xtream panel, asks the same account's API which live streams
     * have a provider archive (`tv_archive`) and for how many days. Empty when not applicable or on error;
     * the playlist still loads without catch-up in that case.
     */
    private fun xtreamArchiveDays(p: Playlist, progress: (String) -> Unit, isCancelled: () -> Boolean): Map<String, Int> {
        val login = XtreamUrls.parseLogin(p.url) ?: return emptyMap()
        progress("Checking the provider's catch-up archive…")
        val out = HashMap<String, Int>()
        return try {
            streamArray(XtreamUrls.api(login.base, login.username, login.password, "get_live_streams"), p.userAgent) { o ->
                RefreshPolicy.checkCancelled(isCancelled)
                val id = o["stream_id"].orEmpty()
                val days = if (o["tv_archive"] == "1") o["tv_archive_duration"]?.toIntOrNull() ?: 0 else 0
                if (id.isNotEmpty() && days > 0) out[id] = days
            }
            out
        } catch (e: Exception) {
            if (e is CancellationException) throw e
            android.util.Log.w("YourIPTV", "Archive info unavailable: ${e.javaClass.simpleName}")
            emptyMap()
        }
    }

    private fun refreshXtream(p: Playlist, progress: (String) -> Unit, isCancelled: () -> Boolean) {
        val expectedIdentity = sourceIdentity(p)
        progress("Signing in to provider…")
        val auth = JSONObject(Http.open(XtreamUrls.api(p.url, p.username, p.password), p.userAgent, 15_000).use { input ->
            val bytes = ByteArrayOutputStream()
            val buffer = ByteArray(16 * 1024)
            while (true) {
                RefreshPolicy.checkCancelled(isCancelled)
                val n = input.read(buffer)
                if (n < 0) break
                if (bytes.size() + n > 2 * 1024 * 1024) throw InvalidRefreshData("The provider account response is too large.")
                bytes.write(buffer, 0, n)
            }
            bytes.toString("UTF-8")
        })
        val user = auth.optJSONObject("user_info")
            ?: throw IllegalStateException("Provider did not return account information. Check server URL and credentials.")
        if (user.optInt("auth", 1) == 0) throw IllegalStateException("Provider rejected the username or password.")
        val status = user.optString("status")
        val maxConn = user.optString("max_connections").toIntOrNull() ?: 0
        val expires = (user.optString("exp_date").toLongOrNull() ?: 0L) * 1000L
        val serverTz = auth.optJSONObject("server_info")?.optString("timezone").orEmpty().trim()

        val counts = IntArray(ItemKind.entries.size)
        val staged = stageItems(isCancelled) { insert ->
            for ((kind, catAction, listAction) in listOf(
                Triple(ItemKind.LIVE, "get_live_categories", "get_live_streams"),
                Triple(ItemKind.MOVIE, "get_vod_categories", "get_vod_streams"),
                Triple(ItemKind.SERIES, "get_series_categories", "get_series"),
            )) {
                RefreshPolicy.checkCancelled(isCancelled)
                progress("Loading ${kind.name.lowercase()} categories…")
                val cats = HashMap<String, String>()
                streamArray(XtreamUrls.api(p.url, p.username, p.password, catAction), p.userAgent) { o ->
                    RefreshPolicy.checkCancelled(isCancelled)
                    cats[o["category_id"].orEmpty()] = o["category_name"].orEmpty().trim()
                }
                progress("Loading ${kind.name.lowercase()} list…")
                var n = 0
                streamArray(XtreamUrls.api(p.url, p.username, p.password, listAction), p.userAgent) { o ->
                    RefreshPolicy.checkCancelled(isCancelled)
                    val id = (if (kind == ItemKind.SERIES) o["series_id"] else o["stream_id"]).orEmpty()
                    if (id.isEmpty()) throw InvalidRefreshData("The provider returned an incomplete catalog entry.")
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
                            logo = VodMetadata.catalogueArtwork(o["stream_icon"], o["cover"]), url = url,
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
        try { publishCatalog(p, staged, counts.sum(), v, isCancelled, expectedIdentity) } finally { staged.delete() }
    }

    /** Streams a JSON array of flat objects; nested values are skipped. */
    private fun streamArray(url: String, ua: String, onObject: (Map<String, String?>) -> Unit) {
        Http.open(url, ua, 15_000).use { input ->
            JsonReader(InputStreamReader(input, Charsets.UTF_8)).use { r ->
                if (r.peek() != JsonToken.BEGIN_ARRAY) throw InvalidRefreshData("The provider returned an invalid catalog response.")
                r.beginArray()
                val map = HashMap<String, String?>()
                while (r.hasNext()) {
                    if (r.peek() != JsonToken.BEGIN_OBJECT) throw InvalidRefreshData("The provider returned an invalid catalog entry.")
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
                if (r.peek() != JsonToken.END_DOCUMENT) throw InvalidRefreshData("The provider returned trailing catalog data.")
            }
        }
    }

    private fun stageItems(isCancelled: () -> Boolean, block: ((MediaItem) -> Unit) -> Unit): File {
        val file = File.createTempFile("catalog-", ".bin", stageDir)
        try {
            DataOutputStream(file.outputStream().buffered()).use { out ->
                block { item ->
                    RefreshPolicy.checkCancelled(isCancelled)
                    if (item.name.isBlank() || item.url.isBlank()) throw InvalidRefreshData("The provider returned an incomplete channel.")
                    RefreshStage.writeItem(out, item)
                    RefreshStage.checkSize(file)
                }
            }
            RefreshStage.checkSize(file)
            return file
        } catch (e: Throwable) { file.delete(); throw e }
    }

    private fun publishCatalog(p: Playlist, staged: File, count: Int, metadata: ContentValues, isCancelled: () -> Boolean, expectedIdentity: String) {
        if (count <= 0) throw InvalidRefreshData("The playlist contained no valid channels. The previous playlist was kept.")
        val playlistId = p.id
        val database = db
        database.beginTransaction()
        try {
            RefreshPolicy.checkCancelled(isCancelled)
            val current = requireCurrent(p, expectedIdentity)
            // Inferred guide IDs belong to stable stream URLs, not regenerated item row IDs.
            val mappings = HashMap<String, String>()
            database.rawQuery("SELECT url,tvg_id FROM items WHERE playlist_id=? AND kind=0 AND tvg_id<>''", arrayOf(playlistId.toString())).use { c ->
                while (c.moveToNext()) mappings[c.getString(0)] = c.getString(1)
            }
            database.delete("items", "playlist_id=?", arrayOf(playlistId.toString()))
            val stmt = database.compileStatement(
                "INSERT INTO items(playlist_id,kind,name,name_lc,grp,logo,url,tvg_id,num,remote_id,ext,catchup_days,catchup_type,catchup_src) VALUES(?,?,?,?,?,?,?,?,?,?,?,?,?,?)"
            )
            stmt.use { statement -> DataInputStream(staged.inputStream().buffered()).use { input -> repeat(count) {
                RefreshPolicy.checkCancelled(isCancelled)
                val loaded = RefreshStage.readItem(input, playlistId)
                val i = if (loaded.tvgId.isBlank() && loaded.kind == ItemKind.LIVE) loaded.copy(tvgId = mappings[loaded.url].orEmpty()) else loaded
                statement.clearBindings()
                stmt.bindLong(1, i.playlistId); stmt.bindLong(2, i.kind.ordinal.toLong())
                stmt.bindString(3, i.name); stmt.bindString(4, i.name.lowercase()); stmt.bindString(5, i.group)
                stmt.bindString(6, i.logo); stmt.bindString(7, i.url); stmt.bindString(8, i.tvgId)
                stmt.bindLong(9, i.number.toLong()); stmt.bindString(10, i.remoteId); stmt.bindString(11, i.extension)
                stmt.bindLong(12, i.catchupDays.toLong())
                stmt.bindString(13, i.catchupType); stmt.bindString(14, i.catchupSource)
                stmt.executeInsert()
            }; if (input.read() != -1) throw InvalidRefreshData("The staged playlist is invalid.") } }
            RefreshPolicy.checkCancelled(isCancelled)
            metadata.put("last_updated", System.currentTimeMillis())
            metadata.put("catalog_generation", current.catalogGeneration + 1)
            metadata.put("catalog_error", "")
            database.update("playlists", metadata, "id=?", arrayOf(playlistId.toString()))
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

    /** Downloads only; the coordinator may share this private file across matching sources. */
    fun stageEpg(p: Playlist, destination: File, isCancelled: () -> Boolean = { false }) {
        val expectedIdentity = sourceIdentity(p)
        requireCurrent(p, expectedIdentity)
        val source = epgSource(p)
        if (source.isBlank()) throw InvalidRefreshData("No EPG source is configured for this playlist.")
        try {
            Http.open(source, p.userAgent, 15_000).use { input -> destination.outputStream().buffered().use { output ->
                val buffer = ByteArray(64 * 1024)
                var size = 0L
                while (true) {
                    RefreshPolicy.checkCancelled(isCancelled)
                    val n = input.read(buffer)
                    if (n < 0) break
                    size += n
                    if (size > RefreshStage.MAX_BYTES) throw InvalidRefreshData("The guide is too large for this device.")
                    output.write(buffer, 0, n)
                }
                if (size == 0L) throw InvalidRefreshData("The provider returned an empty guide. The previous guide was kept.")
            } }
            requireCurrent(p, expectedIdentity)
        } catch (e: Throwable) { destination.delete(); throw e }
    }

    fun refreshEpg(p: Playlist, pastHours: Int, futureDays: Int, progress: (String) -> Unit,
                   isCancelled: () -> Boolean = { false }): Int {
        val source = File.createTempFile("source-", ".xml", stageDir)
        return try {
            progress("Downloading guide data…")
            stageEpg(p, source, isCancelled)
            refreshEpgFromFile(p, pastHours, futureDays, source, progress, isCancelled)
        } finally { source.delete() }
    }

    /** Parse/validate off-database, then publish programmes, inferred associations and success together. */
    fun refreshEpgFromFile(p: Playlist, pastHours: Int, futureDays: Int, file: File,
                          progress: (String) -> Unit, isCancelled: () -> Boolean = { false },
                          expectedWindow: Pair<Int, Int>? = null): Int {
        val windowSnapshot = expectedWindow ?: settings.epgWindow()
        if (settings.epgWindow() != windowSnapshot) throw StaleRefreshData()
        val shiftSnapshot = settings.epgShiftHours
        val expectedIdentity = sourceIdentity(p)
        val snapshot = requireCurrent(p, expectedIdentity)
        val pid = p.id.toString()
        val known = HashSet<String>()
        val blankNames = HashMap<String, MutableList<String>>()
        db.rawQuery("SELECT url,name,tvg_id FROM items WHERE playlist_id=? AND kind=0", arrayOf(pid)).use { c ->
            while (c.moveToNext()) {
                val guideId = c.getString(2).orEmpty()
                if (guideId.isNotBlank()) known.add(guideId)
                else blankNames.getOrPut(normalize(c.getString(1))) { ArrayList() }.add(c.getString(0))
            }
        }
        val mappings = LinkedHashMap<String, String>()
        val now = System.currentTimeMillis()
        val archiveDays = db.rawQuery("SELECT MAX(catchup_days) FROM items WHERE playlist_id=? AND kind=0", arrayOf(pid))
            .use { c -> if (c.moveToFirst()) c.getInt(0) else 0 }
        // XMLTV stores provider times; the selection window is expressed in displayed guide time.
        val rawNow = now - shiftSnapshot * 3_600_000L
        val from = rawNow - Catchup.pastWindowHours(pastHours.coerceIn(0, EpgRetentionPolicy.MAX_PAST_HOURS), archiveDays) * 3_600_000L
        val to = rawNow + futureDays.coerceIn(1, EpgRetentionPolicy.MAX_FUTURE_DAYS) * 86_400_000L
        val staged = File.createTempFile("programs-", ".bin", stageDir)
        var count = 0
        try {
            progress("Importing guide…")
            DataOutputStream(staged.outputStream().buffered()).use { out -> file.inputStream().buffered().use { input ->
                val parser = Xml.newPullParser().apply { setInput(input, null) }
                XmltvParser.parse(parser, onChannel = { channel ->
                    if (channel.id.isNotBlank() && channel.displayName.isNotBlank()) {
                        blankNames[normalize(channel.displayName)]?.forEach { url ->
                            val selected = mappings.getOrPut(url) { channel.id }
                            known.add(selected)
                        }
                    }
                }, acceptChannel = { it in known }, isCancelled = isCancelled) { program ->
                    RefreshPolicy.checkCancelled(isCancelled)
                    if (program.end > from && program.start < to && program.title.isNotBlank()) {
                        RefreshStage.writeProgram(out, program)
                        count++
                        RefreshStage.checkSize(staged)
                        if (count % 20000 == 0) progress("Imported $count programs…")
                    }
                }
            } }
            RefreshStage.checkSize(staged)
            if (count == 0) throw InvalidRefreshData("The guide contained no matching programs. The previous guide was kept.")
            val database = db
            database.beginTransaction()
            var readyToCommit = false
            try {
                RefreshPolicy.checkCancelled(isCancelled)
                val current = requireCurrent(p, expectedIdentity)
                if (current.catalogGeneration != snapshot.catalogGeneration) throw StaleRefreshData()
                val committedSource = database.rawQuery("SELECT epg_source_key FROM playlists WHERE id=?", arrayOf(pid))
                    .use { cursor -> if (cursor.moveToFirst()) cursor.getString(0).orEmpty() else "" }
                val incomingSource = sourceKey(current)
                if (committedSource.isBlank() || committedSource != incomingSource) {
                    // A different/unknown source is replaced only after the new feed is validated.
                    database.delete("programs", "playlist_id=?", arrayOf(pid))
                } else {
                    // Omitted channels and days survive rolling/partial responses inside this window.
                    database.delete("programs", "playlist_id=? AND (stop<=? OR start>=?)", arrayOf(pid, from.toString(), to.toString()))
                }
                database.compileStatement("DELETE FROM programs WHERE playlist_id=? AND channel_id=? AND start<? AND stop>?").use { replace ->
                    database.compileStatement("INSERT INTO programs(playlist_id,channel_id,start,stop,title,descr) VALUES(?,?,?,?,?,?)").use { statement ->
                        DataInputStream(staged.inputStream().buffered()).use { input ->
                            repeat(count) {
                                RefreshPolicy.checkCancelled(isCancelled)
                                val program = RefreshStage.readProgram(input)
                                // Newer feed records win both exact duplicates and overlapping corrections.
                                // This also deduplicates records repeated inside one response (last wins).
                                replace.clearBindings()
                                replace.bindLong(1, p.id); replace.bindString(2, program.channelId)
                                replace.bindLong(3, program.end); replace.bindLong(4, program.start)
                                replace.executeUpdateDelete()
                                RefreshPolicy.checkCancelled(isCancelled)
                                statement.clearBindings()
                                statement.bindLong(1, p.id); statement.bindString(2, program.channelId)
                                statement.bindLong(3, program.start); statement.bindLong(4, program.end)
                                statement.bindString(5, program.title); statement.bindString(6, program.description)
                                statement.executeInsert()
                            }
                            if (input.read() != -1) throw InvalidRefreshData("The staged guide is invalid.")
                        }
                    }
                }
                for ((url, guideId) in mappings) database.execSQL(
                    "UPDATE items SET tvg_id=? WHERE playlist_id=? AND url=? AND kind=0 AND (tvg_id IS NULL OR tvg_id='')",
                    arrayOf<Any>(guideId, p.id, url))
                RefreshPolicy.checkCancelled(isCancelled)
                database.update("playlists", ContentValues().apply {
                    put("epg_updated", System.currentTimeMillis()); put("epg_generation", current.epgGeneration + 1); put("epg_error", "")
                    put("epg_source_key", incomingSource)
                }, "id=?", arrayOf(pid))
                readyToCommit = true
            } finally {
                // Preference setters wait for the final commit only, never for parsing or bulk writes.
                // A mid-import edit rolls back pruning, corrections, status and source provenance.
                synchronized(EpgRetentionPolicy.preferenceLock) {
                    try {
                        if (readyToCommit) {
                            RefreshPolicy.checkCancelled(isCancelled)
                            if (settings.epgWindow() != windowSnapshot || settings.epgShiftHours != shiftSnapshot) throw StaleRefreshData()
                            database.setTransactionSuccessful()
                        }
                    } finally { database.endTransaction() }
                }
            }
            return count
        } finally { staged.delete() }
    }

    fun nowAndNext(playlistId: Long, channelId: String, now: Long = System.currentTimeMillis()): List<Program> {
        if (channelId.isBlank()) return emptyList()
        return programsQuery("playlist_id=? AND channel_id=? AND stop>?", arrayOf(playlistId.toString(), channelId, now.toString()), 2)
    }

    fun programs(playlistId: Long, channelId: String, from: Long, to: Long): List<Program> {
        if (channelId.isBlank() || from >= to) return emptyList()
        return programsQuery(
            "playlist_id=? AND channel_id=? AND stop>? AND start<?",
            arrayOf(playlistId.toString(), channelId, from.toString(), to.toString()),
        )
    }

    /** Compact drawing rows; an over-dense window is explicit, never a misleading oldest-N tail. */
    fun guidePrograms(playlistId: Long, channelId: String, from: Long, to: Long): GuideWindowResult {
        if (channelId.isBlank() || from >= to) return GuideWindowResult(emptyList())
        val rows = compactProgramsQuery("playlist_id=? AND channel_id=? AND stop>? AND start<?",
            arrayOf(playlistId.toString(), channelId, from.toString(), to.toString()), "start", 513, 0)
        return if (rows.size > 512) GuideWindowResult(emptyList(), dense = true) else GuideWindowResult(rows)
    }

    /** Real nearby programmes for actions/details even when a drawing window is too dense. */
    fun guideNeighbours(playlistId: Long, channelId: String, time: Long, from: Long, to: Long): List<Program> {
        if (channelId.isBlank() || from >= to) return emptyList()
        val where = "playlist_id=? AND channel_id=? AND stop>? AND start<?"
        val args = arrayOf(playlistId.toString(), channelId, from.toString(), to.toString())
        val current = compactProgramsQuery("$where AND start<=? AND stop>?", args + arrayOf(time.toString(), time.toString()), "start DESC", 1, 16_384).firstOrNull()
        val previous = compactProgramsQuery("$where AND stop<=?", args + (current?.start ?: time).toString(), "stop DESC", 1, 16_384).firstOrNull()
        val next = compactProgramsQuery("$where AND start>?", args + time.toString(), "start", 1, 16_384).firstOrNull()
        return listOfNotNull(previous, current, next).distinctBy { it.start }.sortedBy { it.start }
    }

    private fun compactProgramsQuery(where: String, args: Array<String>, order: String, limit: Int, descriptionChars: Int): List<Program> =
        // Every compact query binds one known channel as args[1]; share that string instead of
        // materialising another potentially large XMLTV identifier for every returned row.
        db.rawQuery("SELECT start,stop,substr(title,1,512),substr(descr,1,$descriptionChars) FROM programs WHERE $where ORDER BY $order LIMIT $limit", args).use { c ->
            buildList { while (c.moveToNext()) add(Program(args[1], c.getLong(0), c.getLong(1), c.getString(2).orEmpty(), c.getString(3).orEmpty())) }
        }

    /** Search one channel without loading its entire retained guide into the display cache. */
    fun findProgram(playlistId: Long, channelId: String, text: String, from: Long, to: Long): Program? {
        val value = text.trim()
        if (channelId.isBlank() || value.isEmpty() || from >= to) return null
        val escaped = value.replace("\\", "\\\\").replace("%", "\\%").replace("_", "\\_")
        return compactProgramsQuery("playlist_id=? AND channel_id=? AND stop>? AND start<? AND title LIKE ? ESCAPE '\\'",
            arrayOf(playlistId.toString(), channelId, from.toString(), to.toString(), "%$escaped%"), "start", 1, 16_384).firstOrNull()
    }

    fun programCount(playlistId: Long): Int =
        db.rawQuery("SELECT COUNT(*) FROM programs WHERE playlist_id=?", arrayOf(playlistId.toString())).use { c -> if (c.moveToFirst()) c.getInt(0) else 0 }

    private fun programsQuery(where: String, args: Array<String>, limit: Int? = null): List<Program> =
        db.rawQuery("SELECT channel_id,start,stop,title,descr FROM programs WHERE $where ORDER BY start" + (limit?.let { " LIMIT $it" } ?: ""), args).use { c ->
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
            put("scope", playlist(item.playlistId)?.let(WatchPolicy::scope).orEmpty())
            put("position", position); put("duration", duration)
            put("name", item.name); put("kind", item.kind.ordinal); put("grp", item.group); put("logo", item.logo)
        }, SQLiteDatabase.CONFLICT_REPLACE)
    }

    fun resumePosition(item: MediaItem): Long = if (WatchPolicy.supports(item)) watchProgress(item)?.resume ?: 0 else 0

    fun history(playlistId: Long, limit: Int = 100): List<MediaItem> = if (watchStore.isClearing(playlistId)) emptyList() else db.rawQuery(
        "SELECT h.url AS h_url, h.name AS h_name, h.kind AS h_kind, h.grp AS h_grp, h.logo AS h_logo, i.* " +
            "FROM history h LEFT JOIN items i ON i.playlist_id=h.playlist_id AND i.url=h.url " +
            "WHERE h.playlist_id=? AND (h.scope='' OR h.scope=?) GROUP BY h.url ORDER BY MAX(h.watched) DESC LIMIT $limit",
        arrayOf(playlistId.toString(), playlist(playlistId)?.let(WatchPolicy::scope).orEmpty()),
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

    fun clearHistory(playlistId: Long) {
        watchStore.clearPlaylist(playlistId)
    }

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

    private data class DetailsKey(val playlist: Long, val refreshed: Long, val remoteId: String, val generation: Long)
    private data class DetailsEntry<T>(val at: Long, val value: T)
    private val seriesDetailsCache = LinkedHashMap<DetailsKey, DetailsEntry<SeriesDetails>>()
    private val movieDetailsCache = LinkedHashMap<DetailsKey, DetailsEntry<MovieDetails>>()
    private val detailsGeneration = java.util.concurrent.atomic.AtomicLong()

    private fun invalidateDetails() {
        detailsGeneration.incrementAndGet()
        synchronized(seriesDetailsCache) { seriesDetailsCache.clear() }
        synchronized(movieDetailsCache) { movieDetailsCache.clear() }
    }

    fun seriesDetails(p: Playlist, series: MediaItem): SeriesDetails {
        val key = DetailsKey(p.id, p.lastUpdated, series.remoteId, detailsGeneration.get())
        return cachedDetails(seriesDetailsCache, key, 4, 2_000, { it.episodes.size.coerceAtLeast(1) }) {
            val json = readDetails(p, "get_series_info", "series_id", series.remoteId)
            parseSeriesDetails(json) { id, ext -> XtreamUrls.episode(p.url, p.username, p.password, id, ext) }
        }
    }

    fun seriesEpisodes(p: Playlist, series: MediaItem): List<Episode> = seriesDetails(p, series).episodes

    fun movieDetails(p: Playlist, movie: MediaItem): MovieDetails {
        if (p.type != SourceType.XTREAM || movie.remoteId.isBlank()) return MovieDetails()
        val key = DetailsKey(p.id, p.lastUpdated, movie.remoteId, detailsGeneration.get())
        return cachedDetails(movieDetailsCache, key, 32) {
            VodMetadata.movie(readDetails(p, "get_vod_info", "vod_id", movie.remoteId))
        }
    }

    private fun readDetails(p: Playlist, action: String, idKey: String, id: String): JSONObject {
        // Metadata only: never open a video stream while browsing. Bound decompressed provider data.
        val url = XtreamUrls.api(p.url, p.username, p.password, action, mapOf(idKey to id))
        val bytes = Http.open(url, p.userAgent, timeoutMs = 10_000).use { input ->
            val out = ByteArrayOutputStream()
            val buffer = ByteArray(16 * 1024)
            while (true) {
                val count = input.read(buffer)
                if (count < 0) break
                if (out.size() + count > 8 * 1024 * 1024) throw IllegalArgumentException("Provider details exceed the supported size.")
                out.write(buffer, 0, count)
            }
            out.toByteArray()
        }
        return JSONObject(bytes.toString(Charsets.UTF_8))
    }

    private fun <T> cachedDetails(
        cache: LinkedHashMap<DetailsKey, DetailsEntry<T>>, key: DetailsKey, capacity: Int,
        maxWeight: Int = capacity, weight: (T) -> Int = { 1 }, load: () -> T,
    ): T {
        val now = android.os.SystemClock.elapsedRealtime()
        synchronized(cache) {
            cache[key]?.takeIf { now - it.at < 10 * 60_000L }?.let { return it.value }
            cache.remove(key)
        }
        val value = load()
        synchronized(cache) {
            // A very large series stays usable on this screen without occupying a long-lived cache.
            if (weight(value) > maxWeight || key.generation != detailsGeneration.get()) return value
            cache.remove(key)
            cache[key] = DetailsEntry(now, value)
            while (cache.size > capacity || cache.values.sumOf { weight(it.value) } > maxWeight) cache.remove(cache.keys.first())
        }
        return value
    }

    companion object {
        fun normalize(name: String): String = name.lowercase().filter { it.isLetterOrDigit() }

        fun parseSeriesDetails(json: JSONObject, urlFor: (String, String) -> String): SeriesDetails = VodMetadata.series(json, urlFor)

        fun parseSeriesEpisodes(json: JSONObject, urlFor: (String, String) -> String): List<Episode> =
            parseSeriesDetails(json, urlFor).episodes
    }
}
