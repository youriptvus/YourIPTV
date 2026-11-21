package com.youriptv.app.data

/** Episode navigation has finite, same-series bounds; Live TV keeps its independent zapping rules. */
object EpisodePlaybackPolicy {
    enum class EndAction { COUNTDOWN, RETURN, PROMPT }

    fun adjacentIndex(queue: List<MediaItem>, index: Int, delta: Int): Int {
        if (delta != -1 && delta != 1) return -1
        val current = queue.getOrNull(index) ?: return -1
        if (current.kind != ItemKind.EPISODE || current.seriesKey.isBlank()) return -1
        val targetIndex = index + delta
        val target = queue.getOrNull(targetIndex) ?: return -1
        return if (target.kind == ItemKind.EPISODE && target.playlistId == current.playlistId &&
            target.seriesKey == current.seriesKey && target.watchScope == current.watchScope) targetIndex else -1
    }

    /** Continue may already point at the unwatched successor of a completed episode. Do not skip it. */
    fun nextFromHistory(queue: List<MediaItem>, progress: Map<String, WatchProgress>): Int {
        val at = WatchPolicy.continueIndex(queue, progress)
        val item = queue.getOrNull(at) ?: return -1
        val selectedWasStarted = (progress[item.url]?.updated ?: 0L) > 0L
        val hasHistory = queue.any { (progress[it.url]?.updated ?: 0L) > 0L }
        return if (!selectedWasStarted && hasHistory) {
            if (adjacentIndex(queue, at - 1, 1) == at) at else -1
        } else adjacentIndex(queue, at, 1)
    }

    /** An early/truncated END remains visible to the user and must not advance to another episode. */
    fun endAction(confirmedComplete: Boolean, autoplay: Boolean, hasNext: Boolean): EndAction = when {
        !confirmedComplete -> EndAction.PROMPT
        autoplay && hasNext -> EndAction.COUNTDOWN
        else -> EndAction.RETURN
    }
}
