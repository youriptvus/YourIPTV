package com.youriptv.app

import androidx.media3.common.C
import androidx.media3.common.audio.AudioProcessor
import com.youriptv.app.ui.AudioDelayProcessor
import org.junit.Assert.assertEquals
import org.junit.Test
import java.nio.ByteBuffer
import java.nio.ByteOrder

class AudioDelayProcessorTest {
    // 1000 Hz mono 16-bit: 1 ms = 1 frame = 2 bytes, which keeps the arithmetic readable.
    private val format = AudioProcessor.AudioFormat(1000, 1, C.ENCODING_PCM_16BIT)

    private fun processor(delay: Int) = AudioDelayProcessor().apply {
        delayMs = delay
        configure(format)
        flush()
    }

    private fun input(frames: Int): ByteBuffer =
        ByteBuffer.allocateDirect(frames * 2).order(ByteOrder.nativeOrder()).apply {
            repeat(frames) { putShort(1) }
            flip()
        }

    private fun outFrames(p: AudioDelayProcessor, frames: Int): Int {
        p.queueInput(input(frames))
        return p.output.remaining() / 2
    }

    @Test
    fun zeroDelayPassesThrough() = assertEquals(100, outFrames(processor(0), 100))

    @Test
    fun positiveDelayInsertsSilenceOnce() {
        val p = processor(50)
        assertEquals(150, outFrames(p, 100))
        assertEquals(100, outFrames(p, 100))
    }

    @Test
    fun negativeDelayDropsFramesAcrossBuffers() {
        val p = processor(-150)
        assertEquals(0, outFrames(p, 100))
        assertEquals(50, outFrames(p, 100))
        assertEquals(100, outFrames(p, 100))
    }

    @Test
    fun changeDuringPlaybackAppliesDifferenceAndFlushReapplies() {
        val p = processor(100)
        assertEquals(200, outFrames(p, 100))
        p.delayMs = 150
        assertEquals(150, outFrames(p, 100))
        p.flush()
        assertEquals(250, outFrames(p, 100))
    }
}
