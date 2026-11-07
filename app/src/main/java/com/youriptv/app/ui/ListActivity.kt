package com.youriptv.app.ui

import android.os.Bundle
import android.view.View
import android.view.ViewGroup
import android.widget.Button
import android.widget.LinearLayout
import android.widget.ProgressBar
import android.widget.TextView
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.RecyclerView
import com.youriptv.app.R
import com.youriptv.app.YourIptvApp

/** Base for simple full-screen list screens (playlists, settings, episodes) sharing activity_list.xml. */
abstract class ListActivity : RemoteActivity() {
    protected val app get() = YourIptvApp.instance
    protected lateinit var listTitle: TextView
    protected lateinit var listSubtitle: TextView
    protected lateinit var actions: LinearLayout
    protected lateinit var list: RecyclerView
    protected lateinit var empty: TextView
    protected lateinit var progress: ProgressBar

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_list)
        listTitle = findViewById(R.id.list_title)
        listSubtitle = findViewById(R.id.list_subtitle)
        actions = findViewById(R.id.list_actions)
        list = findViewById(R.id.list)
        empty = findViewById(R.id.list_empty)
        progress = findViewById(R.id.list_progress)
        list.layoutManager = LinearLayoutManager(this)
    }

    protected fun addAction(label: String, onClick: () -> Unit): Button {
        val b = Button(this, null, 0, R.style.Button_Tv).apply {
            text = label
            isFocusable = true
            setOnClickListener { onClick() }
            layoutParams = LinearLayout.LayoutParams(ViewGroup.LayoutParams.WRAP_CONTENT, ViewGroup.LayoutParams.WRAP_CONTENT)
                .apply { marginEnd = (12 * resources.displayMetrics.density).toInt() }
        }
        actions.addView(b)
        return b
    }

    protected fun setBusy(busy: Boolean, message: String? = null) {
        progress.visibility = if (busy) View.VISIBLE else View.GONE
        if (message != null) listSubtitle.text = message
    }

    protected fun setEmpty(message: String?) {
        empty.text = message.orEmpty()
        empty.visibility = if (message == null) View.GONE else View.VISIBLE
    }
}
