package com.youriptv.app.ui

import androidx.media3.common.C
import androidx.media3.common.audio.AudioProcessor
import androidx.media3.common.audio.BaseAudioProcessor
import java.nio.ByteBuffer

/**
 * Shifts audio relative to video. A positive delay inserts silence (audio later); a negative delay
 * drops PCM frames (audio earlier). Because the audio track drives the playback clock, adding or
 * removing output frames moves video against audio by the same amount. Changes made during playback
 * apply the difference only; a flush (seek/channel change) re-applies the full delay.
 */
class AudioDelayProcessor : BaseAudioProcessor() {
    @Volatile var delayMs: Int = 0

    private var appliedMs = 0
    private var pendingFrames = 0L

    override fun onConfigure(inputAudioFormat: AudioProcessor.AudioFormat): AudioProcessor.AudioFormat {
        when (inputAudioFormat.encoding) {
            C.ENCODING_PCM_16BIT, C.ENCODING_PCM_24BIT, C.ENCODING_PCM_32BIT, C.ENCODING_PCM_FLOAT -> return inputAudioFormat
            else -> throw AudioProcessor.UnhandledAudioFormatException(inputAudioFormat)
        }
    }

    override fun queueInput(inputBuffer: ByteBuffer) {
        val target = delayMs
        if (target != appliedMs) {
            pendingFrames += (target - appliedMs).toLong() * inputAudioFormat.sampleRate / 1000
            appliedMs = target
        }
        val frameBytes = inputAudioFormat.bytesPerFrame
        if (pendingFrames < 0) {
            val drop = minOf(-pendingFrames, (inputBuffer.remaining() / frameBytes).toLong())
            inputBuffer.position(inputBuffer.position() + (drop * frameBytes).toInt())
            pendingFrames += drop
        }
        val silenceBytes = if (pendingFrames > 0) (pendingFrames * frameBytes).toInt() else 0
        val remaining = inputBuffer.remaining()
        if (silenceBytes == 0 && remaining == 0) return
        val out = replaceOutputBuffer(silenceBytes + remaining)
        repeat(silenceBytes) { out.put(0) }
        out.put(inputBuffer)
        out.flip()
        if (silenceBytes > 0) pendingFrames = 0
    }

    override fun onFlush() {
        appliedMs = 0
        pendingFrames = 0
    }

    override fun onReset() = onFlush()

    companion object {
        const val MAX_MS = 2000
        const val STEP_MS = 50
    }
}
