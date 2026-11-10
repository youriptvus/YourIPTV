package com.youriptv.app.ui

import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.os.Handler
import android.os.Looper
import android.os.SystemClock
import android.util.LruCache
import android.widget.ImageView
import com.youriptv.app.data.Http
import java.lang.ref.WeakReference
import java.util.concurrent.ArrayBlockingQueue
import java.util.concurrent.RejectedExecutionException
import java.util.concurrent.ThreadPoolExecutor
import java.util.concurrent.TimeUnit

/** Bounded background artwork loading. Provider images only; never opens videos to make thumbnails. */
object ImageLoader {
    private val cache = object : LruCache<ArtworkRequest, Bitmap>(ArtworkPolicy.CACHE_BYTES) {
        override fun sizeOf(key: ArtworkRequest, value: Bitmap) = value.byteCount
    }
    private val lock = Any()
    private val failed = LinkedHashMap<String, Long>()
    private val pending = HashMap<ArtworkRequest, MutableList<Listener>>()
    private val workQueue = ArrayBlockingQueue<Runnable>(48)
    private val pool = ThreadPoolExecutor(2, 2, 30, TimeUnit.SECONDS, workQueue)
    private val main = Handler(Looper.getMainLooper())

    private class ViewRequest(val image: WeakReference<ImageView>) {
        @Volatile var active = true
    }
    private class Listener(val wanted: () -> Boolean, val complete: (Bitmap?) -> Unit)

    private class ArtworkTask(val request: ArtworkRequest) : Runnable {
        override fun run() {
            val wanted = synchronized(lock) {
                val hasListener = pending[request]?.any { it.wanted() } == true
                // Remove the abandoned request in the same critical section as this decision.
                // A UI rebind must either join a live task or create a fresh task after removal.
                if (!hasListener) pending.remove(request)
                hasListener
            }
            if (!wanted) return
            // The UI may have populated the cache between its initial lookup and this task.
            val bitmap = cache.get(request) ?: fetch(request)
            val callbacks = synchronized(lock) { pending.remove(request).orEmpty() }
            main.post { callbacks.filter { it.wanted() }.forEach { it.complete(bitmap) } }
        }
    }

    /** Call on the UI thread. Size is in pixels, not dp; requests are capped at a 1024px edge. */
    fun load(view: ImageView, url: String, placeholder: Int, maxPx: Int = 160) {
        (view.tag as? ViewRequest)?.active = false
        val token = ViewRequest(WeakReference(view))
        view.tag = token
        view.setImageResource(placeholder)
        val request = ArtworkPolicy.request(url, maxPx) ?: return
        cache.get(request)?.let { view.setImageBitmap(it); return }
        enqueue(request, Listener(
            wanted = { token.active && token.image.get() != null },
            complete = { bitmap ->
                token.image.get()?.takeIf { token.active && it.tag === token }?.let {
                    if (bitmap != null) it.setImageBitmap(bitmap) else it.setImageResource(placeholder)
                }
            },
        ))
    }

    /** RecyclerView holders can release pending callbacks and the previous drawable when recycled. */
    fun clear(view: ImageView) {
        (view.tag as? ViewRequest)?.active = false
        view.tag = null
        view.setImageDrawable(null)
    }

    /** For views that draw logos themselves: returns the cached bitmap, or starts a download and calls [onLoaded] when it is ready. */
    fun cached(url: String, maxPx: Int = 160, onLoaded: () -> Unit): Bitmap? {
        val request = ArtworkPolicy.request(url, maxPx) ?: return null
        cache.get(request)?.let { return it }
        enqueue(request, Listener({ true }) { if (it != null) onLoaded() })
        return null
    }

    private fun enqueue(request: ArtworkRequest, listener: Listener) {
        val first = synchronized(lock) {
            val failedAt = failed[request.url]
            if (failedAt != null && SystemClock.elapsedRealtime() - failedAt < 60_000) return
            if (failedAt != null) failed.remove(request.url)
            val waiting = pending[request]
            if (waiting != null) {
                waiting.removeAll { !it.wanted() }
                // Repeated guide drawing must not append unbounded closures while one image loads.
                if (waiting.size < 64) waiting.add(listener)
                false
            } else {
                pending[request] = mutableListOf(listener)
                true
            }
        }
        if (!first) return
        try {
            synchronized(lock) {
                // Recycled posters should not occupy every queue slot after a quick scroll.
                for (task in WorkQueueSnapshot.take(workQueue)) {
                    if (task is ArtworkTask && pending[task.request]?.any { it.wanted() } != true && workQueue.remove(task)) {
                        pending.remove(task.request)
                    }
                }
            }
            pool.execute(ArtworkTask(request))
        } catch (_: RejectedExecutionException) {
            // A fast scroll cannot grow the worker queue. Rebinding a visible holder can retry.
            synchronized(lock) { pending.remove(request) }
        }
    }

    private fun fetch(request: ArtworkRequest): Bitmap? {
        val bitmap = try {
            val bytes = Http.open(request.url, timeoutMs = 8_000).use { ArtworkPolicy.readBytes(it) }
            val bounds = BitmapFactory.Options().apply { inJustDecodeBounds = true }
            BitmapFactory.decodeByteArray(bytes, 0, bytes.size, bounds)
            val plan = ArtworkPolicy.decodePlan(bounds.outWidth, bounds.outHeight, request.maxPx)
                ?: throw IllegalArgumentException("Invalid artwork dimensions")
            val decoded = BitmapFactory.decodeByteArray(bytes, 0, bytes.size, BitmapFactory.Options().apply {
                inSampleSize = plan.sample
                inPreferredConfig = Bitmap.Config.ARGB_8888
                inScaled = false
            })
            if (decoded == null) null else {
                val scale = minOf(1.0, request.maxPx.toDouble() / maxOf(decoded.width, decoded.height))
                if (scale == 1.0) decoded else {
                    val scaled = Bitmap.createScaledBitmap(decoded, maxOf(1, (decoded.width * scale).toInt()),
                        maxOf(1, (decoded.height * scale).toInt()), true)
                    if (scaled !== decoded) decoded.recycle()
                    scaled
                }
            }
        } catch (_: Exception) { null } catch (_: OutOfMemoryError) { null }
        if (bitmap == null) synchronized(lock) {
            failed[request.url] = SystemClock.elapsedRealtime()
            while (failed.size > 256) failed.remove(failed.entries.first().key)
        } else cache.put(request, bitmap)
        return bitmap
    }
}
