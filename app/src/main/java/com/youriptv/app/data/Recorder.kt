package com.youriptv.app.data

import java.io.File
import java.io.FileOutputStream
import java.io.IOException
import java.io.InputStream
import java.io.OutputStream
import java.net.URL
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import java.util.concurrent.ConcurrentHashMap

/**
 * Records live channels to app-specific storage on plain background threads (no service and no extra
 * permissions). MPEG-TS streams are copied as-is; HLS streams have their segments appended in order.
 * The recording uses its own provider connection, so provider connection limits apply.
 */
object Recorder {

    private class Job(val thread: Thread) {
        @Volatile var stopped = false
        @Volatile var input: InputStream? = null
    }

    private val jobs = ConcurrentHashMap<Long, Job>()

    /** Recording stops when free space on the recordings volume drops below this. */
    const val MIN_FREE_BYTES = 200L * 1024 * 1024

    fun isActive(id: Long) = jobs.containsKey(id)
    fun activeCount() = jobs.size

    fun start(rec: Recording, dir: File, userAgent: String, repo: Repository, onFinished: (Recording) -> Unit = {}) {
        if (jobs.containsKey(rec.id)) return
        dir.mkdirs()
        val file = File(dir, fileName(rec))
        lateinit var job: Job
        val t = Thread({
            try {
                run(rec, file, userAgent, repo, job)
            } catch (e: Exception) {
                val size = file.length()
                repo.updateRecording(
                    rec.id,
                    status = if (size > 0) RecordingStatus.DONE else RecordingStatus.FAILED,
                    bytes = size,
                    error = e.message ?: e.javaClass.simpleName,
                    end = if (job.stopped) minOf(rec.end, System.currentTimeMillis()) else null,
                )
                if (size == 0L) file.delete()
            } finally {
                jobs.remove(rec.id)
                repo.recording(rec.id)?.let(onFinished)
            }
        }, "rec-${rec.id}")
        job = Job(t)
        jobs[rec.id] = job
        repo.updateRecording(rec.id, status = RecordingStatus.RECORDING, file = file.absolutePath)
        t.start()
    }

    // Android's buffered HTTP stream can throw while another thread closes it during a user-requested stop.
    internal inline fun readRecordingInput(input: InputStream, buffer: ByteArray, stopped: () -> Boolean): Int =
        try {
            input.read(buffer)
        } catch (e: NullPointerException) {
            if (!stopped()) throw e
            android.util.Log.w("YourIPTV", "Recording input interrupted while stopping: ${e.javaClass.simpleName}")
            -1
        }

    fun stop(id: Long) {
        val j = jobs[id] ?: return
        j.stopped = true
        runCatching { j.input?.close() }
        j.thread.interrupt()
    }

    private fun run(rec: Recording, file: File, ua: String, repo: Repository, job: Job) {
        val url = XtreamUrls.applyLiveFormat(rec.channelUrl, "ts")
        var bytes = 0L
        var error = ""
        var lastReport = 0L
        val alive = { !job.stopped && System.currentTimeMillis() < rec.end }
        val counted = object : OutputStream() {
            val out = FileOutputStream(file, true)
            override fun write(b: Int) { out.write(b); bytes++ }
            override fun write(b: ByteArray, off: Int, len: Int) {
                out.write(b, off, len); bytes += len
                val now = System.currentTimeMillis()
                if (now - lastReport > 5_000) {
                    lastReport = now
                    repo.updateRecording(rec.id, bytes = bytes)
                    if (file.parentFile?.usableSpace?.let { it < MIN_FREE_BYTES } == true) {
                        error = "Storage is almost full."; job.stopped = true
                    }
                }
            }
            override fun close() = out.close()
        }
        counted.use { out ->
            while (alive()) {
                try {
                    if (url.substringBefore('?').endsWith(".m3u8", true)) recordHls(url, ua, out, job, alive)
                    else Http.open(url, ua, 15_000).use { input -> job.input = input; copy(input, out, job, alive) }
                    if (alive()) Thread.sleep(1_000)
                } catch (_: InterruptedException) {
                    break
                } catch (e: IOException) {
                    if (!alive()) break
                    error = (e as? HttpException)?.message ?: e.message ?: e.javaClass.simpleName
                    try { Thread.sleep(3_000) } catch (_: InterruptedException) { break }
                } finally {
                    job.input = null
                }
            }
        }
        val ok = bytes > 0
        repo.updateRecording(
            rec.id,
            status = if (ok) RecordingStatus.DONE else RecordingStatus.FAILED,
            bytes = bytes,
            error = if (ok) error else error.ifBlank { "The provider sent no data." },
            end = if (job.stopped) minOf(rec.end, System.currentTimeMillis()) else null,
        )
        if (!ok) file.delete()
    }

    private fun copy(input: InputStream, out: OutputStream, job: Job, alive: () -> Boolean) {
        val buf = ByteArray(64 * 1024)
        while (alive()) {
            val n = readRecordingInput(input, buf) { job.stopped }
            if (n < 0) return
            out.write(buf, 0, n)
        }
    }

    private fun recordHls(url: String, ua: String, out: OutputStream, job: Job, alive: () -> Boolean) {
        var media = url
        val seen = LinkedHashSet<String>()
        while (alive()) {
            val text = Http.readText(media, ua)
            val pl = parseHls(text, media)
            if (pl.variants.isNotEmpty()) { media = pl.variants.first(); continue }
            for (seg in pl.segments) {
                if (!alive()) return
                if (!seen.add(seg)) continue
                Http.open(seg, ua, 15_000).use { input -> job.input = input; copy(input, out, job, alive) }
            }
            while (seen.size > 500) seen.remove(seen.first())
            if (pl.ended) return
            Thread.sleep((pl.targetDuration * 500L).coerceIn(1_000, 10_000))
        }
    }

    data class Hls(val variants: List<String>, val segments: List<String>, val targetDuration: Int, val ended: Boolean)

    /** Minimal HLS playlist reader: variant streams (master) or media segment URLs, resolved against [base]. */
    fun parseHls(text: String, base: String): Hls {
        val variants = ArrayList<String>()
        val segments = ArrayList<String>()
        var target = 6
        var ended = false
        var nextIsVariant = false
        for (raw in text.lineSequence()) {
            val line = raw.trim()
            when {
                line.isEmpty() -> Unit
                line.startsWith("#EXT-X-STREAM-INF") -> nextIsVariant = true
                line.startsWith("#EXT-X-TARGETDURATION:") -> target = line.substringAfter(':').trim().toIntOrNull() ?: target
                line.startsWith("#EXT-X-ENDLIST") -> ended = true
                line.startsWith("#") -> Unit
                else -> {
                    val abs = URL(URL(base), line).toString()
                    if (nextIsVariant) variants.add(abs) else segments.add(abs)
                    nextIsVariant = false
                }
            }
        }
        return Hls(variants, segments, target, ended)
    }

    fun fileName(rec: Recording): String {
        val stamp = SimpleDateFormat("yyyyMMdd_HHmm", Locale.US).format(Date(rec.start))
        val raw = if (rec.title.isBlank() || rec.title == rec.channelName) rec.channelName else "${rec.channelName}_${rec.title}"
        val label = raw.replace(Regex("[^A-Za-z0-9._-]+"), "_").trim('_').take(60)
        return "${stamp}_${rec.id}_$label.ts"
    }
}
