package com.youriptv.app.updates

/** Bundled release notes, selected by the installed APK rather than a remote update manifest. */
internal object InstalledChangelog {
    private data class Release(val name: String, val changes: List<String>)

    // Add each release here when its version code/name changes. Keep older entries for reference.
    private val releases = mapOf(
        27L to Release("0.5.3", listOf(
            "Hold Back in the app or TV guide to return to the current stream in full screen; a short Back press keeps its usual action.",
            "After an in-app update, show the installed version and its changelog until acknowledged.",
        )),
    )

    fun changes(versionCode: Long, versionName: String): List<String> =
        releases[versionCode]?.takeIf { it.name == versionName }?.changes.orEmpty()
}
