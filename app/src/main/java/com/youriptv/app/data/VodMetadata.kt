package com.youriptv.app.data

import org.json.JSONArray
import org.json.JSONObject
import java.net.URI

/** Tolerates the missing/null/mixed optional fields returned by different Xtream providers. */
object VodMetadata {
    private val providerId = Regex("[A-Za-z0-9_-]{1,128}")
    private val extensionName = Regex("[A-Za-z0-9]{1,12}")
    fun series(json: JSONObject, urlFor: (String, String) -> String): SeriesDetails {
        val info = json.optJSONObject("info")
        val result = ArrayList<Episode>()
        fun addEpisodes(array: JSONArray, seasonKey: String = "") {
            for (i in 0 until array.length()) {
                val episode = array.optJSONObject(i) ?: continue
                val id = scalar(episode, "id").takeIf { it.matches(providerId) } ?: continue
                val extra = episode.optJSONObject("info")
                val extension = text(episode, "container_extension").takeIf { it.matches(extensionName) }.orEmpty()
                result.add(Episode(
                    id = id,
                    season = scalar(episode, "season").toIntOrNull()?.takeIf { it >= 0 }
                        ?: seasonKey.toIntOrNull()?.takeIf { it >= 0 } ?: 0,
                    episode = scalar(episode, "episode_num").toIntOrNull()?.takeIf { it > 0 } ?: (i + 1),
                    title = text(episode, "title", 512),
                    url = urlFor(id, extension),
                    plot = firstText(extra, "plot", "description"),
                    artwork = firstArtwork(extra, "movie_image", "episode_image", "cover", "stream_icon")
                        .ifBlank { firstArtwork(episode, "movie_image", "episode_image", "cover") },
                ))
            }
        }
        when (val episodes = json.opt("episodes")) {
            is JSONObject -> for (season in episodes.keys()) episodes.optJSONArray(season)?.let { addEpisodes(it, season) }
            is JSONArray -> addEpisodes(episodes)
        }
        return SeriesDetails(
            episodes = result.sortedWith(compareBy({ it.season }, { it.episode })),
            plot = firstText(info, "plot", "description"),
            cover = firstArtwork(info, "cover", "movie_image", "cover_big"),
            backdrop = firstArtwork(info, "backdrop_path", "backdrop"),
        )
    }

    fun movie(json: JSONObject): MovieDetails {
        val info = json.optJSONObject("info")
        val movie = json.optJSONObject("movie_data")
        return MovieDetails(
            plot = firstText(info, "plot", "description"),
            cover = firstArtwork(info, "movie_image", "cover_big", "cover", "stream_icon")
                .ifBlank { firstArtwork(movie, "stream_icon", "cover") },
            backdrop = firstArtwork(info, "backdrop_path", "backdrop"),
            genre = text(info, "genre", 256),
            year = firstText(info, "releasedate", "release_date", "year").ifBlank { scalar(info, "year") }.take(64),
            duration = scalar(info, "duration").take(64),
            rating = scalar(info, "rating").take(32),
        )
    }

    /** Empty stream_icon must not hide a useful cover. Only ordinary web images are accepted. */
    fun catalogueArtwork(streamIcon: String?, cover: String?): String =
        artwork(streamIcon).ifBlank { artwork(cover) }

    fun artwork(value: Any?): String {
        if (value is JSONArray) {
            for (i in 0 until value.length()) artwork(value.opt(i)).takeIf { it.isNotEmpty() }?.let { return it }
            return ""
        }
        val candidate = (value as? String)?.trim().orEmpty()
        if (candidate.isEmpty() || candidate.length > 4096 || candidate.any { it.isISOControl() }) return ""
        val uri = runCatching { URI(candidate) }.getOrNull() ?: return ""
        return candidate.takeIf {
            (uri.scheme.equals("https", true) || uri.scheme.equals("http", true)) &&
                !uri.host.isNullOrBlank() && uri.rawUserInfo == null
        }.orEmpty()
    }

    private fun firstArtwork(json: JSONObject?, vararg keys: String): String {
        for (key in keys) artwork(json?.opt(key)).takeIf { it.isNotEmpty() }?.let { return it }
        return ""
    }

    private fun firstText(json: JSONObject?, vararg keys: String): String {
        for (key in keys) text(json, key).takeIf { it.isNotEmpty() }?.let { return it }
        return ""
    }

    private fun text(json: JSONObject?, key: String, max: Int = 12_000): String =
        (json?.opt(key) as? String)?.trim()?.takeIf { !it.equals("null", true) }?.take(max).orEmpty()

    private fun scalar(json: JSONObject?, key: String): String = when (val value = json?.opt(key)) {
        is String -> value.trim().takeIf { !it.equals("null", true) }.orEmpty()
        is Number -> value.toString()
        else -> ""
    }
}
