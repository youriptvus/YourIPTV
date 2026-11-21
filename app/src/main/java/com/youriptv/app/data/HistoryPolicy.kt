package com.youriptv.app.data

/** Presentation-only history grouping. Episode progress rows are never rewritten or discarded. */
object HistoryPolicy {
    /** Resolve a saved episode to a routable parent while retaining its last-watched episode summary.
     * Missing catalogue parents retain the saved parent context; unidentified legacy episodes remain recoverable. */
    fun parent(item: MediaItem, current: MediaItem? = null): MediaItem {
        if (item.kind != ItemKind.EPISODE) return item
        val catalogueParent = current?.takeIf { it.kind == ItemKind.SERIES && it.playlistId == item.playlistId }
        val parentUrl = catalogueParent?.url?.takeIf { it.isNotBlank() } ?: item.seriesUrl
        if (parentUrl.isBlank()) return item
        val parentName = catalogueParent?.name?.takeIf { it.isNotBlank() }
            ?: item.seriesName.ifBlank { SeriesNames.parse(item.name).series }
        val parentId = catalogueParent?.remoteId?.takeIf { it.isNotBlank() } ?: item.seriesRemoteId
        return (catalogueParent ?: item).copy(
            kind = ItemKind.SERIES, name = parentName, url = parentUrl, remoteId = parentId,
            seriesKey = item.seriesKey.ifBlank { WatchPolicy.hash("${item.playlistId}\u0000" +
                if (parentUrl.startsWith(SeriesNames.M3U_SERIES_PREFIX)) parentUrl else parentId.ifBlank { parentUrl }) },
            seriesName = parentName, seriesUrl = parentUrl, seriesRemoteId = parentId,
            season = item.season, episode = item.episode, watch = item.watch,
        )
    }

    /** Limit is applied to distinct titles, so one heavily watched series cannot bury older movies. */
    fun grouped(entries: Sequence<WatchEntry>, limit: Int): List<MediaItem> {
        if (limit <= 0) return emptyList()
        val latest = LinkedHashMap<String, WatchEntry>()
        for (entry in entries) {
            if (entry.progress.updated <= 0) continue
            val item = parent(entry.item.copy(watch = entry.progress))
            val identity = when (item.kind) {
                ItemKind.SERIES -> "series:${item.playlistId}:" +
                    if (item.url.startsWith(SeriesNames.M3U_SERIES_PREFIX)) item.url
                    else item.remoteId.ifBlank { item.seriesKey.ifBlank { item.url } }
                else -> "${item.kind}:${item.playlistId}:${entry.key}"
            }
            val previous = latest[identity]
            if (previous == null || entry.progress.updated > previous.progress.updated)
                latest[identity] = entry.copy(item = item)
        }
        return latest.values.sortedByDescending { it.progress.updated }.take(limit).map { it.item }
    }
}
