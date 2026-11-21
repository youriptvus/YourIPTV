package com.youriptv.app.data

import java.net.URI
import java.net.URLDecoder
import java.security.MessageDigest

/** Shared by TV and touch screens. Titles and catalogue row ids are never content identities. */
object WatchPolicy {
    fun hash(value: String): String {
        val hex = "0123456789abcdef"
        return buildString(64) { MessageDigest.getInstance("SHA-256").digest(value.toByteArray(Charsets.UTF_8)).forEach {
            val n = it.toInt() and 255; append(hex[n ushr 4]); append(hex[n and 15])
        } }
    }

    private val transient = setOf("password", "token", "access_token", "auth_token", "signature", "sig", "expires", "expiry")
    private val contentIds = setOf("id", "stream_id", "stream", "vod_id", "video_id", "episode_id", "channel_id")
    private fun parameters(uri: URI): List<String> = uri.rawQuery.orEmpty().split('&').filter { it.isNotBlank() }
    private fun parameterKey(value: String) = URLDecoder.decode(value.substringBefore('='), "UTF-8").lowercase()
    private fun canonicalUrl(value: String, omitted: Set<String>): String = runCatching {
        val uri = URI(value)
        val query = parameters(uri).filter { parameterKey(it) !in omitted }.sorted().joinToString("&")
        val rawUser = uri.rawUserInfo.orEmpty()
        val user = if ("password" in omitted) rawUser.substringBefore(':') else rawUser
        val authority = (if (user.isNotBlank()) "$user@" else "") + uri.rawAuthority?.substringAfterLast('@')?.lowercase().orEmpty()
        "${uri.scheme.orEmpty().lowercase()}://$authority${uri.rawPath.orEmpty()}" + if (query.isBlank()) "" else "?$query"
    }.getOrElse { value }

    fun stableUrl(value: String): String = runCatching {
        val uri = URI(value)
        val file = uri.path.orEmpty().substringAfterLast('/')
        val stem = file.substringBeforeLast('.')
        val contentLocator = parameters(uri).any { parameterKey(it) in contentIds && it.substringAfter('=', "").isNotBlank() } ||
            (file.contains('.') && stem.lowercase() !in setOf("index", "master", "playlist", "manifest", "get", "stream", "video", "watch", "play", "live"))
        // An opaque token may identify the content itself. Never merge token-only streams by their shared path.
        canonicalUrl(value, if (contentLocator) transient else emptySet())
    }.getOrElse { value }

    fun scope(playlist: Playlist): String {
        val uri = runCatching { URI(playlist.url) }.getOrNull()
        val basicUser = uri?.rawUserInfo.orEmpty().substringBefore(':')
        val knownAccount = playlist.type == SourceType.XTREAM || playlist.username.isNotBlank() || basicUser.isNotBlank() ||
            uri?.let { parameters(it).any { p -> parameterKey(p) in setOf("username", "user", "account", "profile_id") && p.substringAfter('=', "").isNotBlank() } } == true
        // Token-only M3U accounts cannot safely be distinguished from credential rotation. Keep their full source identity.
        val source = canonicalUrl(playlist.url, if (knownAccount) transient else emptySet())
        return hash("${playlist.id}\u0000${playlist.type}\u0000$source\u0000${playlist.username}\u0000$basicUser")
    }
    fun key(playlist: Playlist, item: MediaItem): String = hash("${item.kind}\u0000" +
        if (playlist.type == SourceType.XTREAM && item.remoteId.isNotBlank()) "provider:${item.remoteId}" else stableUrl(item.url))
    fun supports(item: MediaItem) = !item.isCatchup && !item.isLocal && item.kind in setOf(ItemKind.MOVIE, ItemKind.EPISODE)

    /** Catalogue refresh may replace row ids and labels, but not the source that owns active playback. */
    fun refreshed(old: MediaItem, fresh: MediaItem): MediaItem = when (old.kind) {
        ItemKind.EPISODE -> fresh.copy(seriesKey = old.seriesKey, seriesName = old.seriesName, seriesUrl = old.seriesUrl,
            seriesRemoteId = old.seriesRemoteId, season = old.season, episode = old.episode, watchScope = old.watchScope)
        ItemKind.MOVIE -> fresh.copy(watchScope = old.watchScope)
        else -> fresh
    }

    /** Completion requires the player's END event; stopping during credits always remains resumable.
     * At most two seconds of tolerance, and only 1% for short clips. Unknown durations stay unfinished. */
    fun completed(position: Long, duration: Long, ended: Boolean): Boolean =
        ended && duration > 0 && position >= duration - minOf(2_000L, duration / 100)

    fun continuation(rows: List<WatchEntry>): List<WatchEntry> = rows.sortedByDescending { it.progress.updated }
        .filter { !it.progress.completed && it.progress.position > 0 }
        .distinctBy { if (it.item.seriesKey.isNotBlank()) "series:${it.item.seriesKey}" else it.key }

    fun continueIndex(queue: List<MediaItem>, progress: Map<String, WatchProgress>): Int {
        if (queue.isEmpty()) return -1
        val started = queue.indices.filter { (progress[queue[it].url]?.updated ?: 0) > 0 }
        // Resume the most recently watched unfinished episode, even if a newer episode was completed.
        started.filter { progress[queue[it].url]?.completed != true }
            .maxByOrNull { progress[queue[it].url]?.updated ?: 0L }?.let { return it }
        val latest = started.maxByOrNull { progress[queue[it].url]?.updated ?: 0L } ?: return 0
        // Completion may advance across seasons, but must never wrap to an earlier episode.
        return (latest + 1 until queue.size).firstOrNull { progress[queue[it].url]?.completed != true } ?: latest
    }
}

data class WatchProgress(val position: Long = 0, val duration: Long = 0, val completed: Boolean = false, val updated: Long = 0) {
    val resume: Long get() = if (completed) 0 else position.coerceAtLeast(0)
    val permille: Int get() = if (completed) 1000 else if (duration > 0) ((position.toDouble() / duration) * 1000).toInt().coerceIn(0, 1000) else 0
    val label: String get() = when {
        completed -> "Watched"
        position > 0 && duration > position -> if (duration - position < 60_000)
            "Continue watching · ${(duration - position + 999) / 1000}s remaining"
            else "Continue watching · ${(duration - position + 59_999) / 60_000} min remaining"
        position > 0 -> "Continue watching"
        else -> "Unwatched"
    }
}

data class WatchEntry(val key: String, val item: MediaItem, val progress: WatchProgress)
