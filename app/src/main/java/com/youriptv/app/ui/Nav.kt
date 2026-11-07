package com.youriptv.app.ui

import android.app.Activity
import android.content.ActivityNotFoundException
import android.content.ComponentName
import android.content.Intent
import android.net.Uri
import com.youriptv.app.YourIptvApp
import com.youriptv.app.data.HttpException
import com.youriptv.app.data.ItemKind
import com.youriptv.app.data.MediaItem
import com.youriptv.app.data.Playlist
import org.json.JSONException
import java.io.FileNotFoundException
import java.net.ConnectException
import java.net.SocketTimeoutException
import java.net.UnknownHostException
import javax.net.ssl.SSLException

object Nav {
    /** The shortcut follows playback state; the currently highlighted guide row is never consulted. */
    fun hasCurrentStream(a: Activity): Boolean {
        val app = YourIptvApp.instance
        if (!app.isTrustedBuild || a.isFinishing || a.isDestroyed) return false
        if (PlayerActivity.hasCurrentStream()) return true
        val item = app.livePlayback.rememberedStream() ?: return false
        return !app.settings.isGroupHidden(item.group)
    }

    fun returnToCurrentStream(a: Activity): Boolean {
        val app = YourIptvApp.instance
        if (!app.isTrustedBuild || a.isFinishing || a.isDestroyed) return false
        if (PlayerActivity.returnToCurrentStream(a)) {
            markStreamReturn(a)
            return true
        }
        val session = app.livePlayback
        val item = session.rememberedStream() ?: return false
        if (app.settings.isGroupHidden(item.group)) return false
        if (Scheduler.recordingUsesOnlyConnection(item.playlistId)) {
            Dialogs.toast(a, "This provider's only connection is being used by a recording.")
            return true
        }
        val playing = session.current
        if (playing?.playlistId == item.playlistId && playing.url == item.url && session.isCurrentAuthorized(item)) {
            return fullscreenPreview(a).also { if (it) markStreamReturn(a) }
        }
        // A released player needs a normal launch and a fresh parental check; remembered PIN grants are not kept.
        open(a, item, session.rememberedQueue(item)) { markStreamReturn(a) }
        return true
    }

    private fun markStreamReturn(a: Activity) {
        if (a is MainActivity) a.markReturningFromCurrentStream()
    }

    /** Opens an item: series go to the episode screen, everything else plays. Locked groups ask for the PIN. */
    fun open(a: Activity, item: MediaItem, list: List<MediaItem>, onOpened: (() -> Unit)? = null) {
        val s = YourIptvApp.instance.settings
        val locked = s.isGroupLocked(item.group)
        // Only a group whose PIN was just entered counts as unlocked in the player.
        val unlocked = if (locked) item.group else null
        val go = {
            when (item.kind) {
                ItemKind.SERIES -> a.startActivity(
                    Intent(a, SeriesActivity::class.java)
                        .putExtra(SeriesActivity.EXTRA_PLAYLIST, item.playlistId)
                        .putExtra(SeriesActivity.EXTRA_URL, item.url)
                        .putExtra(SeriesActivity.EXTRA_NAME, item.name)
                        .putExtra(SeriesActivity.EXTRA_REMOTE_ID, item.remoteId)
                        .putExtra(SeriesActivity.EXTRA_GROUP, item.group)
                        .putExtra(SeriesActivity.EXTRA_LOGO, item.logo)
                )
                ItemKind.LIVE -> {
                    val live = list.filter { it.kind == ItemKind.LIVE }.ifEmpty { listOf(item) }
                    play(a, live, live.indexOfFirst { it.url == item.url }.coerceAtLeast(0), unlocked)
                }
                else -> play(a, listOf(item), 0, unlocked)
            }
            onOpened?.invoke()
        }
        if (locked) Dialogs.requirePin(a, "\"${item.group}\" is locked") { go() } else go()
    }

    /** [unlockedGroup] is a locked group the user has just entered the parental PIN for; every other locked group asks again. */
    fun play(a: Activity, queue: List<MediaItem>, index: Int, unlockedGroup: String? = null) {
        YourIptvApp.playQueue = queue
        a.startActivity(
            Intent(a, PlayerActivity::class.java)
                .putExtra(PlayerActivity.EXTRA_INDEX, index)
                .putExtra(PlayerActivity.EXTRA_UNLOCKED_GROUP, unlockedGroup)
                .putExtra(PlayerActivity.EXTRA_AUTHORIZATION_STAMP, YourIptvApp.instance.settings.restrictionStamp())
                .addFlags(Intent.FLAG_ACTIVITY_CLEAR_TOP or Intent.FLAG_ACTIVITY_SINGLE_TOP)
        )
    }

    fun fullscreenPreview(a: Activity): Boolean {
        val session = YourIptvApp.instance.livePlayback
        val item = session.current
        if (item == null || !session.isCurrentAuthorized(item)) {
            Dialogs.toast(a, "Select a channel to start its preview first.")
            return false
        }
        val queue = session.queue
        val index = queue.indexOfFirst { it.playlistId == item.playlistId && it.url == item.url }
        if (index < 0) {
            Dialogs.toast(a, "The preview channel is no longer in the playback list.")
            return false
        }
        YourIptvApp.playQueue = queue
        a.startActivity(
            Intent(a, PlayerActivity::class.java)
                .putExtra(PlayerActivity.EXTRA_INDEX, index)
                .putExtra(PlayerActivity.EXTRA_UNLOCKED_GROUP, session.unlockedGroup(item))
                .putExtra(PlayerActivity.EXTRA_AUTHORIZATION_STAMP, YourIptvApp.instance.settings.restrictionStamp())
                .putExtra(PlayerActivity.EXTRA_FROM_PREVIEW, true)
        )
        return true
    }

    fun externalPlayer(a: Activity, url: String, title: String) {
        val intent = Intent(Intent.ACTION_VIEW).setDataAndType(Uri.parse(url), "video/*").putExtra("title", title)
        // The chooser never throws when nothing can play the stream, and Android TV adds a framework stub that only
        // says "You don't have an app that can do this". Ignore stubs and check first (needs <queries> in the manifest).
        val handlers = a.packageManager.queryIntentActivities(intent, 0).map { it.activityInfo }
        val stubs = handlers.filter { it.packageName.endsWith(".frameworkpackagestubs") }
        val scheme = Uri.parse(url).scheme?.lowercase()
        if (scheme in setOf("http", "https", "rtmp", "rtsp") && handlers.size == stubs.size) {
            Dialogs.toast(a, "No external video player is installed on this device.")
            return
        }
        val chooser = Intent.createChooser(intent, "Open with")
        if (stubs.isNotEmpty()) {
            chooser.putExtra(Intent.EXTRA_EXCLUDE_COMPONENTS, stubs.map { ComponentName(it.packageName, it.name) }.toTypedArray())
        }
        try {
            a.startActivity(chooser)
        } catch (_: ActivityNotFoundException) {
            Dialogs.toast(a, "No external video player is installed on this device.")
        }
    }

    fun describeError(e: Throwable): String = when (e) {
        is HttpException -> e.message.orEmpty()
        is UnknownHostException -> "Cannot reach the server (unknown host). Check the URL and your network connection."
        is SocketTimeoutException -> "The provider did not respond in time. Try again later."
        is ConnectException -> "Could not connect to the provider. Check the URL, port, and your network connection."
        is SSLException -> "Secure connection to the provider failed (${e.message})."
        is FileNotFoundException -> "The provider returned 'not found'. Check the URL."
        is JSONException -> "The provider returned unexpected data. Check the server URL and credentials."
        is OutOfMemoryError -> "The playlist is too large for this device's memory."
        else -> e.message ?: e.javaClass.simpleName
    }
}

object Refresher {
    /**
     * Refreshes a playlist and then its guide. Returns a warning when only the guide failed.
     * Runs on the calling (background) thread.
     */
    fun refreshAll(p: Playlist, progress: (String) -> Unit): String? {
        val app = YourIptvApp.instance
        app.repo.refresh(p, progress)
        val fresh = app.repo.playlist(p.id) ?: return null
        return refreshEpg(fresh, progress)
    }

    fun refreshEpg(p: Playlist, progress: (String) -> Unit): String? {
        val app = YourIptvApp.instance
        if (app.repo.epgSource(p).isBlank()) return "No guide (EPG) URL is configured for this playlist."
        return try {
            val n = app.repo.refreshEpg(p, app.settings.epgPastHours, app.settings.epgFutureDays, progress)
            app.settings.setEpgUpdated(p.id, System.currentTimeMillis())
            if (n == 0) "The guide loaded but no programmes matched these channels." else null
        } catch (e: Throwable) {
            "Guide update failed: ${Nav.describeError(e)}"
        }
    }
}
