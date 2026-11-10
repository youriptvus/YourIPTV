package com.youriptv.app.ui

import android.os.Parcelable
import android.view.KeyEvent
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.ImageView
import android.widget.TextView
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.RecyclerView
import com.youriptv.app.R
import com.youriptv.app.data.Episode

/** Each vertical season owns a horizontal rail and remembers its own selection and scroll position. */
internal class SeriesSeasonAdapter(
    private val state: SeriesBrowseState,
    private val selected: (SeriesSeasonRow, Int) -> Unit,
    private val play: (SeriesSeasonRow, Int) -> Unit,
    private val options: (SeriesSeasonRow, Int) -> Unit,
    private val vertical: (Int, Int) -> Unit,
    private val focused: (SeriesSeasonRow, Int) -> Unit,
    private val ready: () -> Unit,
) : RecyclerView.Adapter<SeriesSeasonAdapter.SeasonHolder>() {
    var rows: List<SeriesSeasonRow> = emptyList()
        private set
    val railStates = HashMap<Int, Parcelable>()
    private val pool = RecyclerView.RecycledViewPool()
    private var fallbackCover = ""

    class SeasonHolder(view: View) : RecyclerView.ViewHolder(view) {
        val title: TextView = view.findViewById(R.id.series_season_title)
        val rail: RecyclerView = view.findViewById(R.id.series_episode_rail)
        var season: Int? = null
    }

    fun submit(value: List<SeriesSeasonRow>, cover: String) {
        rows = value
        fallbackCover = cover
        notifyDataSetChanged()
    }

    override fun getItemCount() = rows.size

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): SeasonHolder =
        SeasonHolder(LayoutInflater.from(parent.context).inflate(R.layout.series_season_row, parent, false)).also { holder ->
            holder.rail.layoutManager = LinearLayoutManager(parent.context, RecyclerView.HORIZONTAL, false)
            holder.rail.setRecycledViewPool(pool)
            holder.rail.setHasFixedSize(true)
            holder.rail.isNestedScrollingEnabled = false
            holder.rail.itemAnimator = null
            holder.rail.addOnChildAttachStateChangeListener(object : RecyclerView.OnChildAttachStateChangeListener {
                override fun onChildViewAttachedToWindow(view: View) = ready()
                override fun onChildViewDetachedFromWindow(view: View) = Unit
            })
        }

    override fun onBindViewHolder(holder: SeasonHolder, position: Int) {
        saveRail(holder)
        val row = rows[position]
        holder.season = row.season
        holder.title.text = "Season ${row.season} · ${row.episodes.size} episodes"
        holder.rail.adapter = EpisodeAdapter(row, fallbackCover, state, selected, play, options, vertical, focused)
        val manager = holder.rail.layoutManager as LinearLayoutManager
        railStates[row.season]?.let(manager::onRestoreInstanceState)
            ?: manager.scrollToPositionWithOffset(state.indexFor(row).coerceAtLeast(0), 0)
    }

    fun saveRail(holder: SeasonHolder) {
        holder.season?.let { season ->
            holder.rail.layoutManager?.onSaveInstanceState()?.let { railStates[season] = it }
        }
    }

    override fun onViewRecycled(holder: SeasonHolder) {
        saveRail(holder)
        holder.rail.adapter = null
        holder.season = null
        super.onViewRecycled(holder)
    }

    private class EpisodeAdapter(
        private val row: SeriesSeasonRow,
        private val fallbackCover: String,
        private val state: SeriesBrowseState,
        private val selected: (SeriesSeasonRow, Int) -> Unit,
        private val play: (SeriesSeasonRow, Int) -> Unit,
        private val options: (SeriesSeasonRow, Int) -> Unit,
        private val vertical: (Int, Int) -> Unit,
        private val focused: (SeriesSeasonRow, Int) -> Unit,
    ) : RecyclerView.Adapter<EpisodeHolder>() {
        override fun getItemCount() = row.episodes.size

        override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): EpisodeHolder =
            EpisodeHolder(LayoutInflater.from(parent.context).inflate(R.layout.series_episode_card, parent, false))

        override fun onBindViewHolder(holder: EpisodeHolder, position: Int) {
            val episode: Episode = row.episodes[position]
            holder.title.text = "E${episode.episode}  ${episode.title.ifBlank { "Episode ${episode.episode}" }}"
            val artwork = episode.artwork.ifBlank { fallbackCover }
            ImageLoader.load(holder.art, artwork, R.drawable.series_episode_placeholder, maxPx = 360)
            val imageSource = if (episode.artwork.isNotBlank()) "provider episode image" else if (fallbackCover.isNotBlank()) "series artwork" else "no episode image"
            holder.itemView.contentDescription = "Season ${row.season}, episode ${episode.episode}, ${episode.title}, $imageSource"
            holder.itemView.setOnClickListener {
                holder.bindingAdapterPosition.takeIf { it != RecyclerView.NO_POSITION }?.let { index ->
                    state.remember(row, index)
                    selected(row, index)
                    play(row, index)
                }
            }
            holder.itemView.onOptions {
                holder.bindingAdapterPosition.takeIf { it != RecyclerView.NO_POSITION }?.let { options(row, it) }
            }
            holder.itemView.setOnFocusChangeListener { _, hasFocus ->
                val index = holder.bindingAdapterPosition
                if (hasFocus && index != RecyclerView.NO_POSITION) focused(row, index)
            }
            holder.itemView.setOnKeyListener { view, key, event ->
                val index = holder.bindingAdapterPosition
                if (index == RecyclerView.NO_POSITION) return@setOnKeyListener false
                if (event.action == KeyEvent.ACTION_UP && (key == KeyEvent.KEYCODE_MENU || key == KeyEvent.KEYCODE_INFO)) {
                    options(row, index)
                    return@setOnKeyListener true
                }
                if (event.action != KeyEvent.ACTION_DOWN) return@setOnKeyListener false
                when (key) {
                    KeyEvent.KEYCODE_DPAD_UP -> { vertical(row.season, -1); true }
                    KeyEvent.KEYCODE_DPAD_DOWN -> { vertical(row.season, 1); true }
                    KeyEvent.KEYCODE_DPAD_LEFT, KeyEvent.KEYCODE_DPAD_RIGHT -> {
                        val rtl = view.layoutDirection == View.LAYOUT_DIRECTION_RTL
                        val delta = if ((key == KeyEvent.KEYCODE_DPAD_RIGHT) != rtl) 1 else -1
                        index + delta !in row.episodes.indices
                    }
                    else -> false
                }
            }
        }

        override fun onViewRecycled(holder: EpisodeHolder) {
            ImageLoader.clear(holder.art)
            holder.itemView.setOnFocusChangeListener(null)
            super.onViewRecycled(holder)
        }
    }

    private class EpisodeHolder(view: View) : RecyclerView.ViewHolder(view) {
        val title: TextView = view.findViewById(R.id.series_episode_title)
        val art: ImageView = view.findViewById(R.id.series_episode_art)
    }
}
