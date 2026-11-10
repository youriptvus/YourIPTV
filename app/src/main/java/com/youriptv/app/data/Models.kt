package com.youriptv.app.data

enum class SourceType { M3U, XTREAM }

enum class ItemKind { LIVE, MOVIE, SERIES, EPISODE }

data class Playlist(
    val id: Long = 0,
    val name: String,
    val type: SourceType,
    /** M3U: playlist URL. Xtream: server base URL. */
    val url: String,
    val username: String = "",
    val password: String = "",
    val epgUrl: String = "",
    val userAgent: String = "",
    val lastUpdated: Long = 0,
    val liveCount: Int = 0,
    val movieCount: Int = 0,
    val seriesCount: Int = 0,
    val maxConnections: Int = 0,
    val accountStatus: String = "",
    val expiresAt: Long = 0,
    /** Xtream `server_info.timezone`; timeshift URLs use the server's local time. */
    val serverTimeZone: String = "",
)

data class MediaItem(
    val id: Long = 0,
    val playlistId: Long,
    val kind: ItemKind,
    val name: String,
    val group: String,
    val logo: String = "",
    val url: String,
    val tvgId: String = "",
    val number: Int = 0,
    /** Provider id (Xtream stream_id / series_id) used for detail lookups. */
    val remoteId: String = "",
    val extension: String = "",
    val catchupDays: Int = 0,
    /** M3U catch-up mode (`catchup` attribute: default, append, shift, flussonic, xc). */
    val catchupType: String = "",
    /** M3U `catchup-source` template. */
    val catchupSource: String = "",
    /** Set only for in-memory archive playback items; these are never written to history. */
    val isCatchup: Boolean = false,
    /** Set for local recordings; these are never written to history or favorites. */
    val isLocal: Boolean = false,
)

data class Reminder(
    val playlistId: Long,
    val channelUrl: String,
    val channelName: String,
    val title: String,
    val start: Long,
    val end: Long,
)

enum class RecordingStatus { SCHEDULED, RECORDING, DONE, FAILED, MISSED }

data class Recording(
    val id: Long = 0,
    val playlistId: Long,
    val channelUrl: String,
    val channelName: String,
    val title: String,
    val start: Long,
    val end: Long,
    val file: String = "",
    val status: RecordingStatus = RecordingStatus.SCHEDULED,
    val bytes: Long = 0,
    val error: String = "",
)

data class Program(
    val channelId: String,
    val start: Long,
    val end: Long,
    val title: String,
    val description: String = "",
)

data class GroupInfo(val name: String, val count: Int)

data class Episode(
    val id: String,
    val season: Int,
    val episode: Int,
    val title: String,
    val url: String,
    val plot: String = "",
    /** Optional provider/M3U artwork. Browsing never opens the video to manufacture a thumbnail. */
    val artwork: String = "",
)

/** On-demand information; none of it changes persisted playlist identities or playback URLs. */
data class SeriesDetails(
    val episodes: List<Episode> = emptyList(),
    val plot: String = "",
    val cover: String = "",
    val backdrop: String = "",
)

data class MovieDetails(
    val plot: String = "",
    val cover: String = "",
    val backdrop: String = "",
    val genre: String = "",
    val year: String = "",
    val duration: String = "",
    val rating: String = "",
)
