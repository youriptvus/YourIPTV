package com.youriptv.app.data

import java.io.DataInputStream
import java.io.DataOutputStream
import java.io.File

/** Private disk staging avoids retaining an entire provider catalog or guide in RAM. */
internal object RefreshStage {
    const val MAX_BYTES = 512L * 1024 * 1024
    private const val MAX_FIELD_BYTES = 8 * 1024 * 1024

    fun DataOutputStream.text(value: String) {
        val bytes = value.toByteArray(Charsets.UTF_8)
        if (bytes.size > MAX_FIELD_BYTES) throw InvalidRefreshData("An update field is too large.")
        writeInt(bytes.size); write(bytes)
    }

    fun DataInputStream.text(): String {
        val size = readInt()
        if (size !in 0..MAX_FIELD_BYTES) throw InvalidRefreshData("The staged update is invalid.")
        return ByteArray(size).also { readFully(it) }.toString(Charsets.UTF_8)
    }

    fun writeItem(out: DataOutputStream, item: MediaItem) = with(out) {
        writeInt(item.kind.ordinal)
        text(item.name); text(item.group); text(item.logo); text(item.url); text(item.tvgId)
        writeInt(item.number); text(item.remoteId); text(item.extension); writeInt(item.catchupDays)
        text(item.catchupType); text(item.catchupSource)
    }

    fun readItem(input: DataInputStream, playlistId: Long): MediaItem = with(input) {
        val kind = ItemKind.entries.getOrNull(readInt()) ?: throw InvalidRefreshData("The staged update is invalid.")
        MediaItem(playlistId = playlistId, kind = kind, name = text(), group = text(), logo = text(),
            url = text(), tvgId = text(), number = readInt(), remoteId = text(), extension = text(),
            catchupDays = readInt(), catchupType = text(), catchupSource = text())
    }

    fun writeProgram(out: DataOutputStream, program: Program) = with(out) {
        text(program.channelId); writeLong(program.start); writeLong(program.end); text(program.title); text(program.description)
    }

    fun readProgram(input: DataInputStream): Program = with(input) {
        Program(text(), readLong(), readLong(), text(), text())
    }

    fun checkSize(file: File) {
        if (file.length() > MAX_BYTES) throw InvalidRefreshData("The update is too large for this device.")
    }
}
