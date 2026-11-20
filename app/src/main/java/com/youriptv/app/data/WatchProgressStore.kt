package com.youriptv.app.data

import android.content.ContentValues
import android.database.sqlite.SQLiteDatabase
import org.json.JSONObject
import java.util.concurrent.Executors
import java.util.concurrent.TimeUnit

/** App-owned, ordered writes survive an Activity finishing. Pending snapshots are visible immediately.
 * Nothing here opens a stream, modifies the catalogue or runs on the live playback recovery path. */
internal class WatchProgressStore(private val database: () -> SQLiteDatabase, private val playlist: (Long) -> Playlist?) {
    private val writer = Executors.newSingleThreadExecutor { task -> Thread(task, "YourIPTV-watch-progress").apply { isDaemon = true } }
    private val pending = LinkedHashMap<Pair<String, String>, WatchEntry>()
    private val clearing = HashSet<Long>()

    @Synchronized fun save(item: MediaItem, position: Long, duration: Long, ended: Boolean = false, watched: Boolean? = null) {
        if (!WatchPolicy.supports(item)) return
        val source = playlist(item.playlistId) ?: return
        val scope = WatchPolicy.scope(source)
        if (item.watchScope.isNotBlank() && item.watchScope != scope) return
        val key = WatchPolicy.key(source, item)
        val previous = read(source, listOf(item))[item.url]
        val done = watched ?: (WatchPolicy.completed(position, duration, ended) ||
            (previous?.completed == true && duration > 0 && position >= duration - minOf(2_000L, duration / 100)))
        val value = WatchEntry(key, item.copy(watch = null), WatchProgress(position.coerceAtLeast(0), duration.coerceAtLeast(0), done,
            if (watched == false) 0 else System.currentTimeMillis()))
        pending[scope to key] = value
        writer.execute {
            try {
                val db = database()
                // Do not hold the pending-snapshot monitor while waiting for a catalogue transaction.
                // The transaction also makes the account/deletion check atomic with its writes.
                db.beginTransaction()
                try {
                    // A deleted playlist or changed account must not receive a late playback write.
                    if (playlist(item.playlistId)?.let(WatchPolicy::scope) == scope) {
                        db.insertWithOnConflict("watch_progress", null, ContentValues().apply {
                            put("playlist_id", item.playlistId); put("scope", scope); put("content_key", key)
                            put("media", encode(item)); put("position", value.progress.position); put("duration", value.progress.duration)
                            put("completed", if (value.progress.completed) 1 else 0); put("updated", value.progress.updated)
                        }, SQLiteDatabase.CONFLICT_REPLACE).also { check(it != -1L) }
                        if (watched == false) db.delete("history", "playlist_id=? AND url=?", arrayOf(item.playlistId.toString(), item.url))
                        else db.insertWithOnConflict("history", null, ContentValues().apply {
                            put("playlist_id", item.playlistId); put("url", item.url); put("watched", value.progress.updated); put("scope", scope)
                            put("position", value.progress.position); put("duration", value.progress.duration)
                            put("name", item.name); put("kind", item.kind.ordinal); put("grp", item.group); put("logo", item.logo)
                        }, SQLiteDatabase.CONFLICT_REPLACE)
                    }
                    db.setTransactionSuccessful()
                } finally { db.endTransaction() }
                synchronized(this) { if (pending[scope to key] === value) pending.remove(scope to key) }
            } catch (_: Exception) {
                // Never log private media URLs/credentials. Keep the pending snapshot for the next save.
                android.util.Log.w("YourIPTV", "Watch progress could not be persisted")
            }
        }
    }

    @Synchronized fun entries(source: Playlist): List<WatchEntry> {
        val scope = WatchPolicy.scope(source)
        val values = LinkedHashMap<String, WatchEntry>()
        if (source.id !in clearing) database().rawQuery("SELECT content_key,media,position,duration,completed,updated FROM watch_progress WHERE playlist_id=? AND scope=? ORDER BY updated DESC LIMIT 2000",
            arrayOf(source.id.toString(), scope)).use { c -> while (c.moveToNext()) {
            runCatching { WatchEntry(c.getString(0), decode(source.id, c.getString(1)), WatchProgress(c.getLong(2), c.getLong(3), c.getInt(4) != 0, c.getLong(5))) }
                .getOrNull()?.let { values[it.key] = it }
        } }
        pending.filterKeys { it.first == scope }.values.forEach { values[it.key] = it }
        return values.values.toList()
    }

    @Synchronized fun read(source: Playlist, items: List<MediaItem>): Map<String, WatchProgress> {
        if (items.isEmpty()) return emptyMap()
        val scope = WatchPolicy.scope(source)
        val byKey = items.filter(WatchPolicy::supports).groupBy { WatchPolicy.key(source, it) }
        val result = HashMap<String, WatchProgress>()
        for (chunk in if (source.id in clearing) emptyList() else byKey.keys.chunked(500)) {
            val params = arrayOf(scope, *chunk.toTypedArray())
            database().rawQuery("SELECT content_key,position,duration,completed,updated FROM watch_progress WHERE scope=? AND content_key IN (${chunk.joinToString(",") { "?" }})", params).use { c ->
                while (c.moveToNext()) byKey[c.getString(0)]?.forEach { item ->
                    result[item.url] = WatchProgress(c.getLong(1), c.getLong(2), c.getInt(3) != 0, c.getLong(4))
                }
            }
        }
        pending.filterKeys { it.first == scope }.forEach { (key, value) -> byKey[key.second]?.forEach { result[it.url] = value.progress } }
        return result
    }

    @Synchronized fun clear(item: MediaItem) = save(item, 0, 0, watched = false)
    @Synchronized fun isClearing(id: Long) = id in clearing

    @Synchronized fun clearPlaylist(id: Long) {
        // Ordered after queued writes: a late disk write cannot restore cleared history.
        pending.entries.removeAll { it.value.item.playlistId == id }
        clearing.add(id)
        writer.execute {
            try {
                val db = database()
                db.beginTransaction()
                try {
                    db.delete("watch_progress", "playlist_id=?", arrayOf(id.toString()))
                    db.delete("history", "playlist_id=?", arrayOf(id.toString()))
                    db.setTransactionSuccessful()
                } finally { db.endTransaction() }
                synchronized(this) { clearing.remove(id) }
            } catch (_: Exception) {
                android.util.Log.w("YourIPTV", "Watch history could not be cleared")
            }
        }
    }

    fun close() { writer.shutdown(); check(writer.awaitTermination(10, TimeUnit.SECONDS)) { "Watch writes still pending" } }

    private fun encode(item: MediaItem): String = JSONObject().apply {
        put("kind", item.kind.ordinal); put("name", item.name); put("group", item.group); put("logo", item.logo)
        put("url", item.url); put("remote", item.remoteId); put("series", item.seriesKey); put("seriesName", item.seriesName)
        put("seriesUrl", item.seriesUrl); put("seriesRemote", item.seriesRemoteId); put("season", item.season); put("episode", item.episode)
    }.toString()

    private fun decode(id: Long, value: String): MediaItem = JSONObject(value).let { o -> MediaItem(
        playlistId = id, kind = ItemKind.entries[o.getInt("kind")], name = o.getString("name"), group = o.getString("group"),
        logo = o.optString("logo"), url = o.getString("url"), remoteId = o.optString("remote"), seriesKey = o.optString("series"),
        seriesName = o.optString("seriesName"), seriesUrl = o.optString("seriesUrl"), seriesRemoteId = o.optString("seriesRemote"),
        season = o.optInt("season"), episode = o.optInt("episode")) }
}
