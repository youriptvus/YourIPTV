package com.youriptv.app.data

import android.content.ContentValues
import android.database.sqlite.SQLiteDatabase
import org.json.JSONObject
import java.util.concurrent.Executors
import java.util.concurrent.TimeUnit

/** Process-local playback ownership; never stored in the database or exported with history. */
class WatchSessionToken internal constructor(
    internal val owner: Any,
    internal val scope: String,
    internal val key: String,
    internal val revision: Long,
)

/** App-owned, ordered writes survive an Activity finishing. Pending snapshots are visible immediately.
 * Nothing here opens a stream, modifies the catalogue or runs on the live playback recovery path. */
internal class WatchProgressStore(private val database: () -> SQLiteDatabase, private val playlist: (Long) -> Playlist?) {
    private val writer = Executors.newSingleThreadExecutor { task -> Thread(task, "YourIPTV-watch-progress").apply { isDaemon = true } }
    private val pending = LinkedHashMap<Pair<String, String>, WatchEntry>()
    private val clearing = HashSet<Long>()
    private data class Revision(val playlistId: Long, val value: Long, val lastObservedAt: Long? = null)
    private val revisions = HashMap<Pair<String, String>, Revision>()
    private var nextRevision = 0L

    private fun advance(scope: String, key: String, playlistId: Long): Long {
        val revision = ++nextRevision
        revisions[scope to key] = Revision(playlistId, revision)
        return revision
    }

    /** A fresh intentional play supersedes older players for this source and stable content item. */
    @Synchronized fun beginSession(item: MediaItem): WatchSessionToken? {
        if (!WatchPolicy.supports(item)) return null
        val source = playlist(item.playlistId) ?: return null
        val scope = WatchPolicy.scope(source)
        if (item.watchScope.isNotBlank() && item.watchScope != scope) return null
        val key = WatchPolicy.key(source, item)
        return WatchSessionToken(this, scope, key, advance(scope, key, item.playlistId))
    }

    /** Identity only: invalidation must not let automatic URL refresh mint a replacement playback session. */
    @Synchronized fun sameSessionItem(token: WatchSessionToken?, item: MediaItem): Boolean {
        if (token == null || token.owner !== this || !WatchPolicy.supports(item)) return false
        val source = playlist(item.playlistId) ?: return false
        val scope = WatchPolicy.scope(source)
        if (item.watchScope.isNotBlank() && item.watchScope != scope) return false
        return token.scope == scope && token.key == WatchPolicy.key(source, item)
    }

    @Synchronized fun save(item: MediaItem, position: Long, duration: Long, ended: Boolean = false,
                           watched: Boolean? = null, sessionToken: WatchSessionToken? = null,
                           observedAt: Long? = null) {
        // Preserve the playback observation's time even if its caller reaches storage later.
        val recordedAt = if (watched == null) observedAt ?: System.currentTimeMillis() else System.currentTimeMillis()
        if (!WatchPolicy.supports(item)) return
        val source = playlist(item.playlistId) ?: return
        val scope = WatchPolicy.scope(source)
        if (item.watchScope.isNotBlank() && item.watchScope != scope) return
        val key = WatchPolicy.key(source, item)
        val revision = revisions[scope to key]
        if (watched != null) advance(scope, key, item.playlistId)
        else if (sessionToken != null && (sessionToken.owner !== this || sessionToken.scope != scope ||
            sessionToken.key != key || revision?.value != sessionToken.revision)) return
        if (watched == null && sessionToken != null && revision?.lastObservedAt?.let { recordedAt < it } == true) return
        val previous = read(source, listOf(item))[item.url]
        val done = watched ?: (WatchPolicy.completed(position, duration, ended) ||
            (previous?.completed == true && duration > 0 && position >= duration - minOf(2_000L, duration / 100)))
        val value = WatchEntry(key, item.copy(watch = null), WatchProgress(position.coerceAtLeast(0), duration.coerceAtLeast(0), done,
            if (watched == false) 0 else recordedAt.coerceAtLeast(1L)))
        if (watched == null && sessionToken != null && revision != null)
            revisions[scope to key] = revision.copy(lastObservedAt = value.progress.updated)
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

    @Synchronized fun entries(source: Playlist, limit: Int? = 2000): List<WatchEntry> {
        val scope = WatchPolicy.scope(source)
        val values = LinkedHashMap<String, WatchEntry>()
        if (source.id !in clearing) database().rawQuery("SELECT content_key,media,position,duration,completed,updated FROM watch_progress WHERE playlist_id=? AND scope=? ORDER BY updated DESC" + (limit?.let { " LIMIT ${it.coerceAtLeast(0)}" } ?: ""),
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
        revisions.entries.removeAll { it.value.playlistId == id }
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
