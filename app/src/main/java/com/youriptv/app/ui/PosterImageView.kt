package com.youriptv.app.ui

import android.content.Context
import android.util.AttributeSet
import androidx.appcompat.widget.AppCompatImageView

/** Portrait artwork remains proportional when the grid expands or panels open. */
class PosterImageView @JvmOverloads constructor(context: Context, attrs: AttributeSet? = null) : AppCompatImageView(context, attrs) {
    var maxArtworkHeightPx: Int = Int.MAX_VALUE
        set(value) {
            val bounded = value.coerceAtLeast(1)
            if (field != bounded) { field = bounded; requestLayout() }
        }

    override fun onMeasure(widthMeasureSpec: Int, heightMeasureSpec: Int) {
        val width = MeasureSpec.getSize(widthMeasureSpec)
        val height = VodGridPolicy.posterImageHeight(width, maxArtworkHeightPx)
        val measuredHeight = resolveSize(height, heightMeasureSpec)
        val naturalHeight = VodGridPolicy.posterImageHeight(width, Int.MAX_VALUE)
        val desiredScale = if (measuredHeight < naturalHeight) ScaleType.FIT_CENTER else ScaleType.CENTER_CROP
        if (scaleType != desiredScale) scaleType = desiredScale
        setMeasuredDimension(width, measuredHeight)
    }
}
