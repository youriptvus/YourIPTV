package com.youriptv.app.data

import java.io.EOFException
import java.io.FilterInputStream
import java.io.InputStream

/** Counts encoded HTTP body bytes, before buffering or gzip decompression. */
internal class ContentLengthInputStream(private val source: InputStream, expected: Long) : InputStream() {
    private var remaining = expected.also { require(it >= 0) }

    private fun requireComplete() {
        if (remaining != 0L) throw EOFException("The response ended before its declared content length.")
    }

    override fun read(): Int {
        if (remaining == 0L) return -1
        val value = source.read()
        if (value < 0) requireComplete() else remaining--
        return value
    }

    override fun read(buffer: ByteArray, offset: Int, length: Int): Int {
        if (offset < 0 || length < 0 || offset > buffer.size - length) throw IndexOutOfBoundsException()
        if (length == 0) return 0
        if (remaining == 0L) return -1
        val count = source.read(buffer, offset, minOf(length.toLong(), remaining).toInt())
        if (count < 0) requireComplete() else remaining -= count
        return count
    }

    override fun skip(count: Long): Long {
        if (count <= 0 || remaining == 0L) return 0
        val skipped = source.skip(minOf(count, remaining))
        if (skipped > 0) {
            remaining -= skipped
            return skipped
        }
        // A zero skip can mean either EOF or an input that cannot skip.
        return if (read() < 0) 0 else 1
    }

    override fun available(): Int = minOf(source.available().toLong(), remaining).toInt()

    // Some callers intentionally inspect only a prefix. Closing it is not a failed download.
    override fun close() = source.close()

    /** Gzip can finish its trailer without asking the encoded input for EOF. */
    fun checkCompletion(decoded: InputStream): InputStream = object : FilterInputStream(decoded) {
        override fun read(): Int = decoded.read().also { if (it < 0) requireComplete() }

        override fun read(buffer: ByteArray, offset: Int, length: Int): Int =
            decoded.read(buffer, offset, length).also { if (it < 0) requireComplete() }

        override fun skip(count: Long): Long {
            if (count <= 0) return 0
            val skipped = decoded.skip(count)
            return if (skipped > 0) skipped else if (read() < 0) 0 else 1
        }
    }
}
