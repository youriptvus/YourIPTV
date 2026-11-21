package com.youriptv.app.data

/** Finite playback must keep its original source and stop repeated window recovery. */
object PlaybackSessionPolicy {
    fun sourceUnchanged(expectedIdentity: String, currentIdentity: String?): Boolean =
        expectedIdentity.isNotBlank() && currentIdentity == expectedIdentity

    // A READY event is not proof that a malformed dynamic VOD manifest has recovered.
    fun canRecoverFiniteWindow(attempts: Int): Boolean = attempts == 0
}
