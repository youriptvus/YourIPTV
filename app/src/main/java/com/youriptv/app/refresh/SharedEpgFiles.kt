package com.youriptv.app.refresh

import java.io.File
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.CoroutineStart
import kotlinx.coroutines.Deferred
import kotlinx.coroutines.async

/** A private streamed download remains alive until its final importing playlist releases it. */
internal class SharedEpgFiles(private val scope: CoroutineScope, private val directory: File) {
    private class Entry(val file: File) {
        var references = 0
        @Volatile var cancelled = false
        lateinit var download: Deferred<File>
    }
    private val lock = Any()
    private val entries = mutableMapOf<String, Entry>()

    fun removeAbandoned() = synchronized(lock) {
        if (entries.isEmpty()) directory.listFiles()?.filter { it.name.startsWith("epg-") && it.extension == "xml" }?.forEach { it.delete() }
    }

    suspend fun <T> withFile(
        key: String,
        producer: suspend (File, () -> Boolean) -> Unit,
        use: suspend (File) -> T,
    ): T {
        val entry = synchronized(lock) {
            entries[key]?.also { it.references++ } ?: run {
                check(directory.isDirectory || directory.mkdirs()) { "Private guide storage is unavailable" }
                val next = Entry(File.createTempFile("epg-", ".xml", directory))
                next.references = 1
                next.download = scope.async(start = CoroutineStart.LAZY) {
                    try {
                        producer(next.file) { next.cancelled }
                        next.file
                    } finally {
                        if (next.cancelled) next.file.delete()
                    }
                }
                entries[key] = next
                next.download.start()
                next
            }
        }
        try {
            return use(entry.download.await())
        } finally {
            synchronized(lock) {
                entry.references--
                if (entry.references == 0) {
                    if (entries[key] === entry) entries.remove(key)
                    entry.cancelled = true
                    entry.download.cancel()
                    entry.file.delete()
                }
            }
        }
    }
}
