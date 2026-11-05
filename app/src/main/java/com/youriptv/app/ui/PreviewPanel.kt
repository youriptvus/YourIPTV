package com.youriptv.app.ui

import android.content.Context
import android.util.AttributeSet
import android.view.View
import android.widget.FrameLayout
import android.widget.TextView
import androidx.media3.ui.PlayerView
import com.youriptv.app.R

class PreviewFrame @JvmOverloads constructor(context: Context, attrs: AttributeSet? = null) : FrameLayout(context, attrs) {
    override fun onMeasure(widthMeasureSpec: Int, heightMeasureSpec: Int) {
        val width = MeasureSpec.getSize(widthMeasureSpec)
        val height = width * 9 / 16
        super.onMeasure(
            MeasureSpec.makeMeasureSpec(width, MeasureSpec.EXACTLY),
            MeasureSpec.makeMeasureSpec(height, MeasureSpec.EXACTLY),
        )
    }
}

class PreviewPanel(root: View) {
    val playerView: PlayerView = root.findViewById(R.id.preview_player)
    private val status: TextView = root.findViewById(R.id.preview_status)
    private val label: TextView = root.findViewById(R.id.preview_label)

    fun render(session: LivePlaybackSession) {
        status.text = session.status
        status.visibility = if (session.status.isBlank()) View.GONE else View.VISIBLE
        val item = session.current
        label.text = item?.let { "Preview: ${it.name}" }.orEmpty()
        label.visibility = if (item == null) View.GONE else View.VISIBLE
    }
}
