package com.youriptv.app.ui

import android.view.KeyEvent
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.ImageView
import android.widget.ProgressBar
import android.widget.TextView
import androidx.recyclerview.widget.RecyclerView
import com.youriptv.app.R
import com.youriptv.app.YourIptvApp
import com.youriptv.app.data.GroupInfo
import com.youriptv.app.data.ItemKind
import com.youriptv.app.data.MediaItem
import com.youriptv.app.data.Program

/** Treats long-press, MENU, and the info key as the "options" action so remotes and touch behave alike. */
fun View.onOptions(action: () -> Unit) {
    setOnLongClickListener { action(); true }
    setOnKeyListener { _, keyCode, event ->
        if (event.action == KeyEvent.ACTION_UP && (keyCode == KeyEvent.KEYCODE_MENU || keyCode == KeyEvent.KEYCODE_INFO)) {
            action(); true
        } else false
    }
}

class ItemAdapter(
    private val onClick: (MediaItem, Int) -> Unit,
    private val onOptions: (MediaItem, Int) -> Unit,
) : RecyclerView.Adapter<ItemAdapter.VH>() {

    var items: List<MediaItem> = emptyList()
        private set
    var favorites: Set<String> = emptySet()
    private val nowCache = HashMap<String, Pair<Long, List<Program>>>()

    fun submit(list: List<MediaItem>, favs: Set<String>) {
        items = list
        favorites = favs
        nowCache.clear()
        notifyDataSetChanged()
    }

    class VH(v: View) : RecyclerView.ViewHolder(v) {
        val number: TextView = v.findViewById(R.id.number)
        val logo: ImageView = v.findViewById(R.id.logo)
        val name: TextView = v.findViewById(R.id.name)
        val subtitle: TextView = v.findViewById(R.id.subtitle)
        val progress: ProgressBar = v.findViewById(R.id.now_progress)
        val fav: ImageView = v.findViewById(R.id.fav)
    }

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int) =
        VH(LayoutInflater.from(parent.context).inflate(R.layout.row_item, parent, false))

    override fun getItemCount() = items.size

    override fun onBindViewHolder(h: VH, position: Int) {
        val item = items[position]
        val app = YourIptvApp.instance
        h.name.text = item.name
        h.number.visibility = if (item.kind == ItemKind.LIVE && app.settings.showChannelNumbers) View.VISIBLE else View.GONE
        h.number.text = if (item.number > 0) item.number.toString() else ""
        ImageLoader.load(h.logo, item.logo, R.drawable.ic_channel)
        h.fav.visibility = if (item.url in favorites) View.VISIBLE else View.GONE
        h.progress.visibility = View.GONE
        if (item.kind == ItemKind.LIVE) {
            val now = System.currentTimeMillis()
            val cached = nowCache[item.tvgId]
            val progs = if (cached != null && now - cached.first < 60_000) cached.second
            else app.repo.nowAndNext(item.playlistId, item.tvgId, now - shiftMs()).also { nowCache[item.tvgId] = now to it }
            val current = progs.firstOrNull { it.start <= now - shiftMs() }
            if (current != null) {
                h.subtitle.text = "${Format.time(current.start + shiftMs())}  ${current.title}"
                h.progress.visibility = View.VISIBLE
                h.progress.progress = (((now - shiftMs() - current.start) * 1000) / (current.end - current.start).coerceAtLeast(1)).toInt()
            } else {
                h.subtitle.text = item.group
            }
        } else {
            h.subtitle.text = item.group
        }
        h.itemView.setOnClickListener { onClick(item, h.bindingAdapterPosition) }
        h.itemView.onOptions { onOptions(item, h.bindingAdapterPosition) }
        h.itemView.contentDescription = item.name
    }

    private fun shiftMs() = YourIptvApp.instance.settings.epgShiftHours * 3_600_000L
}

class GroupAdapter(
    private val horizontal: Boolean,
    private val onClick: (GroupInfo, Int) -> Unit,
    private val onOptions: (GroupInfo, Int) -> Unit,
    private val onFocus: ((Int) -> Unit)? = null,
) : RecyclerView.Adapter<GroupAdapter.VH>() {

    var groups: List<GroupInfo> = emptyList()
        private set
    var selected = -1
    var locked: Set<String> = emptySet()

    fun submit(list: List<GroupInfo>, sel: Int, lockedGroups: Set<String>) {
        groups = list; selected = sel; locked = lockedGroups
        notifyDataSetChanged()
    }

    /** Moves the highlight without rebinding every row, so the focused row keeps its focus. */
    fun select(sel: Int) {
        val old = selected
        selected = sel
        if (old in groups.indices) notifyItemChanged(old, SELECTION)
        if (sel in groups.indices && sel != old) notifyItemChanged(sel, SELECTION)
    }

    class VH(v: View) : RecyclerView.ViewHolder(v) {
        val name: TextView = v.findViewById(R.id.name)
        val count: TextView = v.findViewById(R.id.count)
        val lock: ImageView = v.findViewById(R.id.lock)
    }

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): VH {
        val v = LayoutInflater.from(parent.context).inflate(R.layout.row_group, parent, false)
        if (horizontal) {
            v.layoutParams = RecyclerView.LayoutParams(ViewGroup.LayoutParams.WRAP_CONTENT, ViewGroup.LayoutParams.MATCH_PARENT)
            v.findViewById<TextView>(R.id.name).apply {
                layoutParams = (layoutParams as ViewGroup.LayoutParams).also { it.width = ViewGroup.LayoutParams.WRAP_CONTENT }
                maxLines = 1
            }
        }
        return VH(v)
    }

    override fun getItemCount() = groups.size

    override fun onBindViewHolder(h: VH, position: Int) {
        val g = groups[position]
        h.name.text = g.name
        h.count.text = if (g.count >= 0) g.count.toString() else ""
        h.lock.visibility = if (g.name in locked) View.VISIBLE else View.GONE
        h.itemView.isActivated = position == selected
        h.itemView.setOnClickListener { onClick(g, h.bindingAdapterPosition) }
        h.itemView.onOptions { onOptions(g, h.bindingAdapterPosition) }
        h.itemView.setOnFocusChangeListener { _, hasFocus ->
            val position = h.bindingAdapterPosition
            if (hasFocus && position != RecyclerView.NO_POSITION) onFocus?.invoke(position)
        }
    }

    override fun onBindViewHolder(h: VH, position: Int, payloads: MutableList<Any>) {
        if (payloads.isNotEmpty() && payloads.all { it == SELECTION }) h.itemView.isActivated = position == selected
        else onBindViewHolder(h, position)
    }

    private companion object { const val SELECTION = "selection" }
}

/** Generic two-line list adapter used for playlists, episodes, and guide programmes. */
class TextAdapter<T>(
    private val bind: (T) -> Pair<String, String>,
    private val onClick: (T, Int) -> Unit,
    private val onOptions: ((T, Int) -> Unit)? = null,
    private val highlight: (T) -> Boolean = { false },
    private val selectable: (T) -> Boolean = { true },
) : RecyclerView.Adapter<ItemAdapter.VH>() {
    var data: List<T> = emptyList()
        private set

    fun submit(list: List<T>) { data = list; notifyDataSetChanged() }

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int) =
        ItemAdapter.VH(LayoutInflater.from(parent.context).inflate(R.layout.row_item, parent, false))

    override fun getItemCount() = data.size

    override fun onBindViewHolder(h: ItemAdapter.VH, position: Int) {
        val d = data[position]
        val (title, sub) = bind(d)
        h.number.visibility = View.GONE
        h.logo.visibility = View.GONE
        h.fav.visibility = View.GONE
        h.progress.visibility = View.GONE
        h.name.text = title
        h.subtitle.text = sub
        h.subtitle.maxLines = 3
        h.itemView.isActivated = highlight(d)
        h.itemView.isFocusable = selectable(d)
        h.itemView.setOnClickListener { onClick(d, h.bindingAdapterPosition) }
        onOptions?.let { cb -> h.itemView.onOptions { cb(d, h.bindingAdapterPosition) } }
    }
}
