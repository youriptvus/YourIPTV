package com.youriptv.app.data

import android.content.ContentValues
import android.content.Context
import android.database.Cursor
import android.database.sqlite.SQLiteDatabase
import android.database.sqlite.SQLiteOpenHelper

class Db(context: Context) : SQLiteOpenHelper(context, "youriptv.db", null, 5) {

    override fun onConfigure(db: SQLiteDatabase) {
        db.enableWriteAheadLogging()
    }

    override fun onCreate(db: SQLiteDatabase) {
        db.execSQL(
            """CREATE TABLE playlists(
                id INTEGER PRIMARY KEY AUTOINCREMENT, name TEXT NOT NULL, type TEXT NOT NULL, url TEXT NOT NULL,
                username TEXT, password TEXT, epg_url TEXT, user_agent TEXT, last_updated INTEGER DEFAULT 0,
                live_count INTEGER DEFAULT 0, movie_count INTEGER DEFAULT 0, series_count INTEGER DEFAULT 0,
                max_conn INTEGER DEFAULT 0, status TEXT, expires INTEGER DEFAULT 0)"""
        )
        db.execSQL(
            """CREATE TABLE items(
                id INTEGER PRIMARY KEY AUTOINCREMENT, playlist_id INTEGER NOT NULL, kind INTEGER NOT NULL,
                name TEXT NOT NULL, name_lc TEXT NOT NULL, grp TEXT NOT NULL, logo TEXT, url TEXT NOT NULL,
                tvg_id TEXT, num INTEGER DEFAULT 0, remote_id TEXT, ext TEXT, catchup_days INTEGER DEFAULT 0)"""
        )
        db.execSQL("CREATE INDEX idx_items_group ON items(playlist_id, kind, grp)")
        db.execSQL("CREATE INDEX idx_items_url ON items(playlist_id, url)")
        db.execSQL(
            """CREATE TABLE programs(
                playlist_id INTEGER NOT NULL, channel_id TEXT NOT NULL, start INTEGER NOT NULL, stop INTEGER NOT NULL,
                title TEXT, descr TEXT)"""
        )
        db.execSQL("CREATE INDEX idx_programs ON programs(playlist_id, channel_id, start)")
        db.execSQL(
            """CREATE TABLE favorites(playlist_id INTEGER NOT NULL, url TEXT NOT NULL, added INTEGER NOT NULL,
                PRIMARY KEY(playlist_id, url))"""
        )
        db.execSQL(
            """CREATE TABLE history(playlist_id INTEGER NOT NULL, url TEXT NOT NULL, watched INTEGER NOT NULL,
                position INTEGER DEFAULT 0, duration INTEGER DEFAULT 0, PRIMARY KEY(playlist_id, url))"""
        )
        addHistoryMeta(db)
        addV3(db)
        addV4(db)
        addV5(db)
    }

    override fun onUpgrade(db: SQLiteDatabase, oldVersion: Int, newVersion: Int) {
        if (oldVersion < 2) addHistoryMeta(db)
        if (oldVersion < 3) addV3(db)
        if (oldVersion < 4) addV4(db)
        if (oldVersion < 5) addV5(db)
    }

    // v5: Xtream server timezone for catch-up (timeshift) URLs.
    private fun addV5(db: SQLiteDatabase) {
        db.execSQL("ALTER TABLE playlists ADD COLUMN server_tz TEXT")
    }

    // v4: user channel customisation that survives playlist refreshes (hidden channels/groups, manual guide channel)
    // and the XMLTV channel list used to pick a guide channel.
    private fun addV4(db: SQLiteDatabase) {
        db.execSQL(
            """CREATE TABLE channel_prefs(playlist_id INTEGER NOT NULL, url TEXT NOT NULL, hidden INTEGER DEFAULT 0,
                epg_id TEXT, PRIMARY KEY(playlist_id, url))"""
        )
        db.execSQL("CREATE TABLE hidden_groups(playlist_id INTEGER NOT NULL, kind INTEGER NOT NULL, grp TEXT NOT NULL, PRIMARY KEY(playlist_id, kind, grp))")
        db.execSQL("CREATE TABLE epg_channels(playlist_id INTEGER NOT NULL, id TEXT NOT NULL, name TEXT, PRIMARY KEY(playlist_id, id))")
    }

    // v3: M3U catch-up templates, programme reminders and local recordings.
    private fun addV3(db: SQLiteDatabase) {
        db.execSQL("ALTER TABLE items ADD COLUMN catchup_type TEXT")
        db.execSQL("ALTER TABLE items ADD COLUMN catchup_src TEXT")
        db.execSQL(
            """CREATE TABLE reminders(playlist_id INTEGER NOT NULL, channel_url TEXT NOT NULL, channel_name TEXT,
                title TEXT, start INTEGER NOT NULL, stop INTEGER NOT NULL, PRIMARY KEY(playlist_id, channel_url, start))"""
        )
        db.execSQL(
            """CREATE TABLE recordings(id INTEGER PRIMARY KEY AUTOINCREMENT, playlist_id INTEGER NOT NULL,
                channel_url TEXT NOT NULL, channel_name TEXT, title TEXT, start INTEGER NOT NULL, stop INTEGER NOT NULL,
                file TEXT, status TEXT NOT NULL, bytes INTEGER DEFAULT 0, error TEXT)"""
        )
    }

    // v2: history keeps display data so items that are not in the catalogue (Xtream episodes) still appear.
    private fun addHistoryMeta(db: SQLiteDatabase) {
        db.execSQL("ALTER TABLE history ADD COLUMN name TEXT")
        db.execSQL("ALTER TABLE history ADD COLUMN kind INTEGER")
        db.execSQL("ALTER TABLE history ADD COLUMN grp TEXT")
        db.execSQL("ALTER TABLE history ADD COLUMN logo TEXT")
    }

    companion object {
        fun itemValues(item: MediaItem) = ContentValues().apply {
            put("playlist_id", item.playlistId)
            put("kind", item.kind.ordinal)
            put("name", item.name)
            put("name_lc", item.name.lowercase())
            put("grp", item.group)
            put("logo", item.logo)
            put("url", item.url)
            put("tvg_id", item.tvgId)
            put("num", item.number)
            put("remote_id", item.remoteId)
            put("ext", item.extension)
            put("catchup_days", item.catchupDays)
            put("catchup_type", item.catchupType)
            put("catchup_src", item.catchupSource)
        }

        fun readItem(c: Cursor) = MediaItem(
            id = c.getLong(c.getColumnIndexOrThrow("id")),
            playlistId = c.getLong(c.getColumnIndexOrThrow("playlist_id")),
            kind = ItemKind.entries[c.getInt(c.getColumnIndexOrThrow("kind"))],
            name = c.getString(c.getColumnIndexOrThrow("name")),
            group = c.getString(c.getColumnIndexOrThrow("grp")),
            logo = c.getString(c.getColumnIndexOrThrow("logo")).orEmpty(),
            url = c.getString(c.getColumnIndexOrThrow("url")),
            tvgId = c.getString(c.getColumnIndexOrThrow("tvg_id")).orEmpty(),
            number = c.getInt(c.getColumnIndexOrThrow("num")),
            remoteId = c.getString(c.getColumnIndexOrThrow("remote_id")).orEmpty(),
            extension = c.getString(c.getColumnIndexOrThrow("ext")).orEmpty(),
            catchupDays = c.getInt(c.getColumnIndexOrThrow("catchup_days")),
            catchupType = c.getColumnIndex("catchup_type").let { if (it >= 0) c.getString(it).orEmpty() else "" },
            catchupSource = c.getColumnIndex("catchup_src").let { if (it >= 0) c.getString(it).orEmpty() else "" },
        )

        fun readRecording(c: Cursor) = Recording(
            id = c.getLong(c.getColumnIndexOrThrow("id")),
            playlistId = c.getLong(c.getColumnIndexOrThrow("playlist_id")),
            channelUrl = c.getString(c.getColumnIndexOrThrow("channel_url")),
            channelName = c.getString(c.getColumnIndexOrThrow("channel_name")).orEmpty(),
            title = c.getString(c.getColumnIndexOrThrow("title")).orEmpty(),
            start = c.getLong(c.getColumnIndexOrThrow("start")),
            end = c.getLong(c.getColumnIndexOrThrow("stop")),
            file = c.getString(c.getColumnIndexOrThrow("file")).orEmpty(),
            status = runCatching { RecordingStatus.valueOf(c.getString(c.getColumnIndexOrThrow("status"))) }.getOrDefault(RecordingStatus.FAILED),
            bytes = c.getLong(c.getColumnIndexOrThrow("bytes")),
            error = c.getString(c.getColumnIndexOrThrow("error")).orEmpty(),
        )

        fun readReminder(c: Cursor) = Reminder(
            playlistId = c.getLong(c.getColumnIndexOrThrow("playlist_id")),
            channelUrl = c.getString(c.getColumnIndexOrThrow("channel_url")),
            channelName = c.getString(c.getColumnIndexOrThrow("channel_name")).orEmpty(),
            title = c.getString(c.getColumnIndexOrThrow("title")).orEmpty(),
            start = c.getLong(c.getColumnIndexOrThrow("start")),
            end = c.getLong(c.getColumnIndexOrThrow("stop")),
        )

        fun readPlaylist(c: Cursor) = Playlist(
            id = c.getLong(c.getColumnIndexOrThrow("id")),
            name = c.getString(c.getColumnIndexOrThrow("name")),
            type = SourceType.valueOf(c.getString(c.getColumnIndexOrThrow("type"))),
            url = c.getString(c.getColumnIndexOrThrow("url")),
            username = c.getString(c.getColumnIndexOrThrow("username")).orEmpty(),
            password = c.getString(c.getColumnIndexOrThrow("password")).orEmpty(),
            epgUrl = c.getString(c.getColumnIndexOrThrow("epg_url")).orEmpty(),
            userAgent = c.getString(c.getColumnIndexOrThrow("user_agent")).orEmpty(),
            lastUpdated = c.getLong(c.getColumnIndexOrThrow("last_updated")),
            liveCount = c.getInt(c.getColumnIndexOrThrow("live_count")),
            movieCount = c.getInt(c.getColumnIndexOrThrow("movie_count")),
            seriesCount = c.getInt(c.getColumnIndexOrThrow("series_count")),
            maxConnections = c.getInt(c.getColumnIndexOrThrow("max_conn")),
            accountStatus = c.getString(c.getColumnIndexOrThrow("status")).orEmpty(),
            expiresAt = c.getLong(c.getColumnIndexOrThrow("expires")),
            serverTimeZone = c.getColumnIndex("server_tz").let { if (it >= 0) c.getString(it).orEmpty() else "" },
        )
    }
}
