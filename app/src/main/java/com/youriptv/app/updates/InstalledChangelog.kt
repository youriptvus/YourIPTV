package com.youriptv.app.updates

/** Bundled release notes, selected by the installed APK rather than a remote update manifest. */
internal object InstalledChangelog {
    private data class Release(val name: String, val changes: List<String>)

    // Add each release here when its version code/name changes. Keep older entries for reference.
    private val releases = mapOf(
        31L to Release("1.0.0", listOf(
            "Stable release of the accepted 0.5.6 player; no new playback changes.",
            "All player features remain free. Existing playlists and settings are retained during a normal update.",
        )),
        30L to Release("0.5.6", listOf(
            "Use the official youriptv.us backup update server when GitHub update checks or APK downloads fail.",
            "Verify backup downloads with the same checksum, package, version and signing checks before installation.",
        )),
        28L to Release("0.5.4", listOf(
            "Version-only release of the 0.5.3 features, with a new Android version code.",
            "Hold Back in the app or TV guide to return to the current stream in full screen; a short Back press keeps its usual action.",
            "After an in-app update, show the installed version and its changelog until acknowledged.",
        )),
        27L to Release("0.5.3", listOf(
            "Hold Back in the app or TV guide to return to the current stream in full screen; a short Back press keeps its usual action.",
            "After an in-app update, show the installed version and its changelog until acknowledged.",
        )),
    )

    fun changes(versionCode: Long, versionName: String): List<String> =
        releases[versionCode]?.takeIf { it.name == versionName }?.changes.orEmpty()
}
