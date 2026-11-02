package com.youriptv.app.ui

import kotlin.math.abs
import kotlin.math.roundToInt

/** Chooses a display mode whose refresh rate is a whole multiple of the video frame rate (pure, unit-testable). */
object FrameRateMatcher {

    data class Mode(val id: Int, val width: Int, val height: Int, val refreshRate: Float)

    /** Relative tolerance; keeps 59.94 Hz and 60 Hz (0.1 % apart) distinct. */
    private const val TOLERANCE = 0.0005f

    fun matches(refreshRate: Float, fps: Float): Boolean {
        if (fps <= 0f || refreshRate <= 0f) return false
        val n = (refreshRate / fps).roundToInt()
        return n >= 1 && abs(refreshRate - n * fps) / refreshRate <= TOLERANCE
    }

    private val STANDARD_RATES = floatArrayOf(24000f / 1001f, 24f, 25f, 30000f / 1001f, 30f, 48f, 50f, 60000f / 1001f, 60f)

    /**
     * Estimates the frame rate from rendered-frame presentation timestamps (microseconds), for streams such as
     * MPEG-TS whose container does not declare one. Averages the frame intervals near the median (so seek gaps
     * and dropped frames are ignored, and millisecond-rounded timestamps such as Matroska's 41/42 ms still average
     * out) and snaps to a standard broadcast/film rate when within tolerance. Returns null with fewer than
     * [minFrames] usable frames.
     */
    fun estimateFps(timestampsUs: List<Long>, minFrames: Int = 12): Float? {
        val sorted = timestampsUs.distinct().sorted()
        val deltas = sorted.zipWithNext { a, b -> b - a }.filter { it in 1..200_000 }
        if (deltas.size < minFrames - 1) return null
        val median = deltas.sorted()[deltas.size / 2]
        val inliers = deltas.filter { abs(it - median) * 5 <= median }
        val fps = (1_000_000.0 * inliers.size / inliers.sum()).toFloat()
        return STANDARD_RATES.minByOrNull { abs(it - fps) / it }?.takeIf { abs(it - fps) / it <= TOLERANCE } ?: fps
    }

    /**
     * Returns the mode to switch to, or null when the current mode already suits [fps] or nothing matches.
     * Only modes with the current resolution are considered; among matches the refresh rate closest to the
     * current one wins (so 25 fps prefers 50 Hz over 25 Hz on a 60 Hz display).
     */
    fun pick(modes: List<Mode>, current: Mode, fps: Float): Mode? {
        if (fps <= 0f || matches(current.refreshRate, fps)) return null
        return modes
            .filter { it.width == current.width && it.height == current.height && matches(it.refreshRate, fps) }
            .minByOrNull { abs(it.refreshRate - current.refreshRate) }
    }
}
