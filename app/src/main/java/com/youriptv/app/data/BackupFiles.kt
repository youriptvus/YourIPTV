package com.youriptv.app.data

import android.content.Context
import java.io.File
import java.io.InputStream
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

/** Saves and restores [BackupData] files. Never logs file contents: they include playlist logins. */
class BackupManager(private val context: Context, private val settings: Settings, private val repo: Repository) {

    class Result(val addedPlaylists: List<Playlist>, val matchedPlaylists: Int, val favoritesAdded: Int, val settingsApplied: Int)

    /** App-specific folder; needs no storage permission and is removed only when the app is uninstalled. */
    fun folder(): File = (context.getExternalFilesDir("backups") ?: File(context.filesDir, "backups")).apply { mkdirs() }

    fun snapshot(now: Long = System.currentTimeMillis()): BackupData {
        val version = runCatching { context.packageManager.getPackageInfo(context.packageName, 0).versionName }.getOrNull().orEmpty()
        return BackupData(
            created = now, appVersion = version, settings = settings.backupValues(),
            parental = settings.parentalRestrictions,
            playlists = repo.playlists().map { p ->
                BackupPlaylist(p.id, p.name, p.type, p.url, p.username, p.password, p.epgUrl, p.userAgent, repo.favoriteUrls(p.id))
            },
            activeRef = settings.activePlaylistId,
        )
    }

    fun writeToFolder(now: Long = System.currentTimeMillis()): File {
        val name = "youriptv-backup-" + SimpleDateFormat("yyyyMMdd-HHmmss", Locale.US).format(Date(now)) + ".json"
        val target = File(folder(), name)
        val tmp = File(folder(), "$name.tmp")
        tmp.writeText(BackupCodec.encode(snapshot(now)), Charsets.UTF_8)
        if (!tmp.renameTo(target)) { tmp.delete(); throw BackupException("Could not save the backup file.") }
        return target
    }

    fun encode(): String = BackupCodec.encode(snapshot())

    fun backups(): List<File> =
        folder().listFiles { f -> f.isFile && f.name.endsWith(".json") }.orEmpty().sortedByDescending { it.lastModified() }

    fun read(input: InputStream): BackupData {
        val bytes = input.use { s ->
            val buf = s.readNBytesCompat(BackupCodec.MAX_BYTES + 1)
            if (buf.size > BackupCodec.MAX_BYTES) throw BackupException("This file is too large to be a YourIPTV backup.")
            buf
        }
        return BackupCodec.decode(String(bytes, Charsets.UTF_8))
    }

    fun plan(b: BackupData) = BackupRestore.plan(repo.playlists(), b)

    /** Merges the backup into this device: adds missing playlists and favorites, applies settings, and only adds parental restrictions. */
    fun restore(b: BackupData): Result {
        val plan = plan(b)
        val ids = HashMap(plan.existing)
        val added = plan.toAdd.map { p ->
            val pl = Playlist(name = p.name, type = p.type, url = p.url, username = p.username, password = p.password, epgUrl = p.epgUrl, userAgent = p.userAgent)
            val id = repo.savePlaylist(pl)
            if (id <= 0) throw BackupException("Could not add playlist \"${p.name}\".")
            ids[p.ref] = id
            pl.copy(id = id)
        }
        var favs = 0
        for (p in b.playlists) ids[p.ref]?.let { favs += repo.addFavoriteUrls(it, p.favorites) }

        val values = HashMap(b.settings)
        val active = ids[b.activeRef]
        // The last channel belongs to the backup's active playlist; keep it only if that playlist is now active.
        if (active == null) values.remove("last_channel")
        settings.restoreValues(values)
        if (active != null) settings.activePlaylistId = active
        settings.parentalRestrictions = BackupRestore.mergeParental(settings.parentalRestrictions, b.parental)
        return Result(added, plan.existing.size, favs, values.size)
    }

    private fun InputStream.readNBytesCompat(limit: Long): ByteArray {
        val out = java.io.ByteArrayOutputStream()
        val buf = ByteArray(16 * 1024)
        var total = 0L
        while (total < limit) {
            val n = read(buf, 0, minOf(buf.size.toLong(), limit - total).toInt())
            if (n < 0) break
            out.write(buf, 0, n); total += n
        }
        return out.toByteArray()
    }
}
