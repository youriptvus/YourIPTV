package com.youriptv.app.ui

import android.content.Context
import android.graphics.Canvas
import android.graphics.Paint
import android.graphics.RectF
import android.graphics.Typeface
import android.text.TextPaint
import android.text.TextUtils
import android.util.AttributeSet
import android.util.TypedValue
import android.view.View
import androidx.core.content.ContextCompat
import com.youriptv.app.R
import com.youriptv.app.data.Catchup
import com.youriptv.app.data.MediaItem
import com.youriptv.app.data.Program

/** Shared geometry and selection state for the guide grid. Times are already shifted for display. */
class GuideState(context: Context, channelWidthDp: Float = 260f) {
    var windowStart = 0L
    /** About two hours across the timeline, the same programme density as TiviMate's guide at 1080p. */
    val span = 120 * 60_000L
    var focusTime = 0L
    var focusedRow = 0
    var showNumbers = true
    /** Programme markers keyed by "channelUrl|start": reminder and recording symbols. */
    var marks: Map<String, String> = emptyMap()
    var favorites: Set<String> = emptySet()
    var playing: LiveChannelKey? = null
    val channelWidth = TypedValue.applyDimension(TypedValue.COMPLEX_UNIT_DIP, channelWidthDp, context.resources.displayMetrics)
}

private fun sp(v: View, value: Float) = TypedValue.applyDimension(TypedValue.COMPLEX_UNIT_SP, value, v.resources.displayMetrics)
private fun dp(v: View, value: Float) = TypedValue.applyDimension(TypedValue.COMPLEX_UNIT_DIP, value, v.resources.displayMetrics)

class GuideTimelineView @JvmOverloads constructor(context: Context, attrs: AttributeSet? = null) : View(context, attrs) {
    var state: GuideState? = null
    private val text = TextPaint(Paint.ANTI_ALIAS_FLAG).apply { color = ContextCompat.getColor(context, R.color.text_dim); textSize = sp(this@GuideTimelineView, 14f) }
    private val accent = Paint(Paint.ANTI_ALIAS_FLAG).apply { color = ContextCompat.getColor(context, R.color.accent) }

    override fun onDraw(canvas: Canvas) {
        val s = state ?: return
        val x0 = s.channelWidth
        val pxPerMs = (width - x0) / s.span
        val y = height / 2f + text.textSize / 3f
        canvas.drawText(Format.dayTime(s.windowStart).substringBeforeLast(' ').substringBeforeLast(' '), dp(this, 16f), y, text)
        // Narrow layouts label every hour (or less often) so time labels never run into each other.
        var step = 30 * 60_000L
        val minGap = text.measureText(Format.time(s.windowStart)) + dp(this, 24f)
        while (step * pxPerMs < minGap && step < s.span) step *= 2
        var t = s.windowStart
        while (t < s.windowStart + s.span) {
            canvas.drawText(Format.time(t), x0 + (t - s.windowStart) * pxPerMs + dp(this, 6f), y, text)
            t += step
        }
        val now = System.currentTimeMillis()
        if (now in s.windowStart until s.windowStart + s.span) {
            val x = x0 + (now - s.windowStart) * pxPerMs
            canvas.drawRect(x - 2f, height - dp(this, 8f), x + 2f, height.toFloat(), accent)
        }
    }
}

class GuideRowView(context: Context, private val state: GuideState) : View(context) {
    var channel: MediaItem? = null
    var programs: List<Program> = emptyList()
    var rowIndex = -1

    private val bg = Paint().apply { color = ContextCompat.getColor(context, R.color.panel) }
    private val bgNow = Paint().apply { color = ContextCompat.getColor(context, R.color.panel_alt) }
    private val bgSel = Paint().apply { color = ContextCompat.getColor(context, R.color.accent) }
    private val channelBgSel = Paint().apply { color = ContextCompat.getColor(context, R.color.accent_dim) }
    private val nowLine = Paint().apply { color = ContextCompat.getColor(context, R.color.accent) }
    private val title = TextPaint(Paint.ANTI_ALIAS_FLAG).apply { color = ContextCompat.getColor(context, R.color.text); textSize = sp(this@GuideRowView, 14f) }
    private val name = TextPaint(title).apply { typeface = Typeface.create("sans-serif-medium", Typeface.NORMAL) }
    private val dim = TextPaint(Paint.ANTI_ALIAS_FLAG).apply { color = ContextCompat.getColor(context, R.color.text_dim); textSize = sp(this@GuideRowView, 14f) }
    private val selText = TextPaint(Paint.ANTI_ALIAS_FLAG).apply { color = 0xFF0B0E12.toInt(); textSize = sp(this@GuideRowView, 14f); isFakeBoldText = true }
    private val logoPaint = Paint(Paint.ANTI_ALIAS_FLAG or Paint.FILTER_BITMAP_FLAG)
    private val rect = RectF()
    private val logoBox = RectF()

    override fun onDraw(canvas: Canvas) {
        val ch = channel ?: return
        val focused = rowIndex == state.focusedRow
        val x0 = state.channelWidth
        val gap = dp(this, 1.5f)
        val pad = dp(this, 8f)
        val h = height.toFloat()
        val ty = h / 2f + title.textSize / 3f

        if (focused) { rect.set(0f, gap, x0 - gap, h - gap); canvas.drawRoundRect(rect, 8f, 8f, channelBgSel) }
        var x = pad
        if (state.showNumbers && ch.number > 0) {
            canvas.drawText(ch.number.toString(), x, ty, dim)
            x += dp(this, 40f)
        }
        // Logos sit in a fixed slot so channel names line up whether or not a logo has loaded.
        val logoW = dp(this, 44f)
        val logoH = h - 2 * dp(this, 6f)
        val url = ch.logo
        ImageLoader.cached(url, maxPx = 160) { if (channel?.logo == url) invalidate() }?.let { bmp ->
            val scale = minOf(logoW / bmp.width, logoH / bmp.height)
            val w = bmp.width * scale
            val bh = bmp.height * scale
            logoBox.set(x + (logoW - w) / 2f, (h - bh) / 2f, x + (logoW + w) / 2f, (h + bh) / 2f)
            canvas.drawBitmap(bmp, null, logoBox, logoPaint)
        }
        x += logoW + dp(this, 10f)
        var nameRight = x0 - pad
        if (state.playing == LiveChannelKey(ch.playlistId, ch.url)) {
            val mark = "▶"
            val width = title.measureText(mark)
            canvas.drawText(mark, nameRight - width, ty, title)
            nameRight -= width + dp(this, 8f)
        }
        if (ch.url in state.favorites) {
            val mark = "★"
            val width = title.measureText(mark)
            canvas.drawText(mark, nameRight - width, ty, title)
            nameRight -= width + dp(this, 8f)
        }
        if (ch.catchupDays > 0) {
            val mw = title.measureText(Catchup.MARK)
            canvas.drawText(Catchup.MARK, nameRight - mw, ty, dim)
            nameRight -= mw + dp(this, 8f)
        }
        canvas.drawText(TextUtils.ellipsize(ch.name, name, (nameRight - x).coerceAtLeast(0f), TextUtils.TruncateAt.END).toString(), x, ty, name)

        val pxPerMs = (width - x0) / state.span
        val ws = state.windowStart
        val we = ws + state.span
        val now = System.currentTimeMillis()
        var drewAny = false
        var drewSelection = false
        for (p in programs) {
            if (p.end <= ws || p.start >= we) continue
            drewAny = true
            val l = x0 + (maxOf(p.start, ws) - ws) * pxPerMs
            val r = x0 + (minOf(p.end, we) - ws) * pxPerMs
            val selected = focused && state.focusTime >= p.start && state.focusTime < p.end
            if (selected) drewSelection = true
            val paint = when { selected -> bgSel; now >= p.start && now < p.end -> bgNow; else -> bg }
            rect.set(l + gap, gap, r - gap, h - gap)
            canvas.drawRoundRect(rect, 6f, 6f, paint)
            val avail = r - l - 2 * pad
            if (avail > 12) {
                val tp = if (selected) selText else title
                val base = if (p.end <= now && Catchup.available(ch.catchupDays, p.start, p.end, now)) "${Catchup.MARK} ${p.title}" else p.title
                val label = state.marks["${ch.url}|${p.start}"]?.let { "$it $base" } ?: base
                canvas.drawText(TextUtils.ellipsize(label, tp, avail, TextUtils.TruncateAt.END).toString(), l + pad, ty, tp)
            }
        }
        if (!drewAny) {
            rect.set(x0 + gap, gap, width - gap, h - gap)
            canvas.drawRoundRect(rect, 6f, 6f, if (focused) bgSel else bg)
            canvas.drawText("No information", x0 + pad, ty, if (focused) selText else dim)
        } else if (focused && !drewSelection) {
            val gapStart = programs.filter { it.end <= state.focusTime }.maxOfOrNull { it.end } ?: ws
            val gapEnd = programs.filter { it.start > state.focusTime }.minOfOrNull { it.start } ?: we
            val l = x0 + (maxOf(gapStart, ws) - ws) * pxPerMs
            val r = x0 + (minOf(gapEnd, we) - ws) * pxPerMs
            if (r > l) {
                rect.set(l + gap, gap, r - gap, h - gap)
                canvas.drawRoundRect(rect, 6f, 6f, bgSel)
                val available = r - l - 2 * pad
                if (available > 12) {
                    canvas.drawText(TextUtils.ellipsize("No information", selText, available, TextUtils.TruncateAt.END).toString(), l + pad, ty, selText)
                }
            }
        }
        if (now in ws until we) {
            val nx = x0 + (now - ws) * pxPerMs
            canvas.drawRect(nx - 1f, 0f, nx + 1f, h, nowLine)
        }
    }
}
