package com.youriptv.app.ui

import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.os.Handler
import android.os.Looper
import android.util.LruCache
import android.widget.ImageView
import com.youriptv.app.data.Http
import java.util.concurrent.Executors

/** Minimal logo loader: background download, downsampling, memory cache, recycled-view safe. */
object ImageLoader {
    private val cache = object : LruCache<String, Bitmap>(24 * 1024 * 1024) {
        override fun sizeOf(key: String, value: Bitmap) = value.byteCount
    }
    private val failed = HashSet<String>()
    private val pool = Executors.newFixedThreadPool(4)
    private val main = Handler(Looper.getMainLooper())

    fun load(view: ImageView, url: String, placeholder: Int, maxPx: Int = 160) {
        view.tag = url
        if (url.isBlank() || synchronized(failed) { url in failed }) { view.setImageResource(placeholder); return }
        cache.get(url)?.let { view.setImageBitmap(it); return }
        view.setImageResource(placeholder)
        pool.execute {
            val bmp = fetch(url, maxPx)
            main.post { if (view.tag == url) { if (bmp != null) view.setImageBitmap(bmp) else view.setImageResource(placeholder) } }
        }
    }

    private val pending = HashMap<String, MutableList<() -> Unit>>()

    /** For views that draw logos themselves: returns the cached bitmap, or starts a download and calls [onLoaded] when it is ready. */
    fun cached(url: String, maxPx: Int = 160, onLoaded: () -> Unit): Bitmap? {
        if (url.isBlank()) return null
        cache.get(url)?.let { return it }
        if (synchronized(failed) { url in failed }) return null
        val first = synchronized(pending) {
            val waiting = pending[url]
            if (waiting != null) { waiting.add(onLoaded); false } else { pending[url] = mutableListOf(onLoaded); true }
        }
        if (first) pool.execute {
            val bmp = fetch(url, maxPx)
            val callbacks = synchronized(pending) { pending.remove(url).orEmpty() }
            if (bmp != null) main.post { callbacks.forEach { it() } }
        }
        return null
    }

    private fun fetch(url: String, maxPx: Int): Bitmap? {
        cache.get(url)?.let { return it }
        val bmp = try {
            val bytes = Http.open(url, timeoutMs = 10_000).use { it.readBytes() }
            val bounds = BitmapFactory.Options().apply { inJustDecodeBounds = true }
            BitmapFactory.decodeByteArray(bytes, 0, bytes.size, bounds)
            var sample = 1
            while (bounds.outWidth / (sample * 2) >= maxPx && bounds.outHeight / (sample * 2) >= maxPx / 2) sample *= 2
            BitmapFactory.decodeByteArray(bytes, 0, bytes.size, BitmapFactory.Options().apply { inSampleSize = sample })
        } catch (_: Throwable) { null }
        if (bmp == null) synchronized(failed) { failed.add(url) } else cache.put(url, bmp)
        return bmp
    }
}
