package com.youriptv.app.updates

/** Bundled release notes, selected by the installed APK rather than a remote update manifest. */
internal object InstalledChangelog {
    private data class Release(val name: String, val changes: List<String>)

    // Add each release here when its version code/name changes. Keep older entries for reference.
    private val releases = mapOf(
        32L to Release("1.0.1", listOf(
            "Settings protection stays on for new and existing installations, with a permanent Settings master PIN.",
            "Existing custom Settings PINs still open Settings; the master is required to add, change or remove a custom PIN.",
            "Open About directly from the main menu without entering a PIN.",
            "Editing an existing playlist asks for authorization before showing connection details.",
        )),
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
