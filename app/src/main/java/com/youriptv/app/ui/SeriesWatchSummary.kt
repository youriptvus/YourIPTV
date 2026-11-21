package com.youriptv.app.ui

import com.youriptv.app.data.MediaItem
import com.youriptv.app.data.WatchProgress

object SeriesWatchSummary {
    fun label(queue: List<MediaItem>, progress: Map<String, WatchProgress>, target: MediaItem?): String {
        val last = queue.filter { (progress[it.url]?.updated ?: 0) > 0 }
            .maxByOrNull { progress[it.url]?.updated ?: 0L }
        val current = target?.let { "S${it.season} E${it.episode} · ${progress[it.url]?.label ?: "Unwatched"}" }.orEmpty()
        return if (last == null) current else "Last watched: S${last.season} E${last.episode} · ${progress[last.url]?.label}" +
            if (target != null && target.url != last.url) "\nContinue: $current" else ""
    }
}
