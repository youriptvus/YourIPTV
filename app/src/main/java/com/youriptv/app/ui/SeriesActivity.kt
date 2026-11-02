package com.youriptv.app.ui

import android.os.Bundle
import android.widget.Button
import androidx.lifecycle.lifecycleScope
import com.youriptv.app.data.Episode
import com.youriptv.app.data.ItemKind
import com.youriptv.app.data.MediaItem
import com.youriptv.app.data.SeriesNames
import com.youriptv.app.data.SourceType
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

/** Seasons and episodes of one series (Xtream get_series_info, or M3U episode entries grouped by name). */
class SeriesActivity : ListActivity() {

    private var episodes: List<Episode> = emptyList()
    private var seasons: List<Int> = emptyList()
    private var season = 0
    private lateinit var seasonButton: Button
    private lateinit var favButton: Button
    private lateinit var adapter: TextAdapter<Episode>
    private lateinit var series: MediaItem
    private var openedStamp = 0

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        openedStamp = app.settings.restrictionStamp()
        val pid = intent.getLongExtra(EXTRA_PLAYLIST, 0L)
        series = MediaItem(
            playlistId = pid, kind = ItemKind.SERIES,
            name = intent.getStringExtra(EXTRA_NAME).orEmpty(),
            group = intent.getStringExtra(EXTRA_GROUP).orEmpty(),
            logo = intent.getStringExtra(EXTRA_LOGO).orEmpty(),
            url = intent.getStringExtra(EXTRA_URL).orEmpty(),
            remoteId = intent.getStringExtra(EXTRA_REMOTE_ID).orEmpty(),
        )
        listTitle.text = series.name
        listSubtitle.text = series.group
        seasonButton = addAction("Season") { chooseSeason() }
        favButton = addAction("") { toggleFavorite() }
        updateFavButton()
        adapter = TextAdapter(
            bind = { e -> "E${e.episode}  ${e.title.ifBlank { "Episode ${e.episode}" }}" to e.plot.ifBlank { "Season ${e.season}" } },
            onClick = { _, pos -> play(pos) },
            onOptions = { e, _ -> Dialogs.choose(this, e.title, listOf("Play", "Play in external player")) { i ->
                if (i == 0) play(adapter.data.indexOf(e)) else externalEpisode(e.url, e.title)
            } },
        )
        list.adapter = adapter
        load()
    }

    private fun updateFavButton() {
        favButton.text = if (app.repo.isFavorite(series)) "Remove from favorites" else "Add to favorites"
    }

    private fun toggleFavorite() {
        app.repo.setFavorite(series, !app.repo.isFavorite(series))
        updateFavButton()
    }

    private fun load() {
        val p = app.repo.playlist(series.playlistId) ?: run { setEmpty("Playlist not found."); return }
        setBusy(true, "Loading episodes…")
        lifecycleScope.launch {
            val result = withContext(Dispatchers.IO) {
                runCatching {
                    if (p.type == SourceType.XTREAM && !series.url.startsWith(SeriesNames.M3U_SERIES_PREFIX)) app.repo.seriesEpisodes(p, series)
                    else m3uEpisodes()
                }
            }
            setBusy(false, series.group)
            result.onSuccess { eps ->
                episodes = eps
                seasons = eps.map { it.season }.distinct().sorted()
                if (eps.isEmpty()) { setEmpty("The provider returned no episodes for this series."); return@onSuccess }
                showSeason(seasons.first())
                list.post { list.getChildAt(0)?.requestFocus() }
            }.onFailure { e -> setEmpty("Could not load episodes: ${Nav.describeError(e)}") }
        }
    }

    private fun m3uEpisodes(): List<Episode> {
        val key = series.name.lowercase()
        return app.repo.items(series.playlistId, ItemKind.EPISODE, series.group.ifBlank { null })
            .mapNotNull { item ->
                val parsed = SeriesNames.parse(item.name)
                if (parsed.series.lowercase() != key) null
                else Episode(id = item.url, season = parsed.season, episode = parsed.episode, title = item.name, url = item.url)
            }
            .sortedWith(compareBy({ it.season }, { it.episode }))
    }

    private fun showSeason(s: Int) {
        season = s
        seasonButton.text = if (seasons.size > 1) "Season $s  ▾  (${seasons.size} seasons)" else "Season $s"
        adapter.submit(episodes.filter { it.season == s })
    }

    private fun chooseSeason() {
        if (seasons.size <= 1) return
        Dialogs.choose(this, "Season", seasons.map { "Season $it  (${episodes.count { e -> e.season == it }} episodes)" }) { i ->
            showSeason(seasons[i])
            list.post { list.getChildAt(0)?.requestFocus() }
        }
    }

    private fun play(index: Int) {
        if (index < 0) return
        val queue = adapter.data.map { e ->
            MediaItem(
                playlistId = series.playlistId, kind = ItemKind.EPISODE,
                name = "${series.name} S${e.season} E${e.episode}" + if (e.title.isNotBlank() && !e.title.startsWith(series.name)) " · ${e.title}" else "",
                group = series.group, logo = series.logo, url = e.url, remoteId = e.id,
            )
        }
        // This screen was opened through the parental check; a restriction changed since then asks again in the player.
        val unlocked = series.group.takeIf { app.settings.isGroupLocked(it) && app.settings.restrictionStamp() == openedStamp }
        Nav.play(this, queue, index, unlocked)
    }

    private fun externalEpisode(url: String, title: String) {
        val g = series.group
        if (app.settings.isGroupLocked(g) && app.settings.restrictionStamp() != openedStamp) {
            Dialogs.requirePin(this, "\"$g\" is locked") { Nav.externalPlayer(this, url, title) }
        } else Nav.externalPlayer(this, url, title)
    }

    companion object {
        const val EXTRA_PLAYLIST = "playlist"
        const val EXTRA_URL = "url"
        const val EXTRA_NAME = "name"
        const val EXTRA_REMOTE_ID = "remote_id"
        const val EXTRA_GROUP = "group"
        const val EXTRA_LOGO = "logo"
    }
}
