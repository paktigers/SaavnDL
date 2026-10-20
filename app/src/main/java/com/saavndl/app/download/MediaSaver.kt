package com.saavndl.app.download

import android.content.ContentValues
import android.content.Context
import android.net.Uri
import android.provider.MediaStore
import com.saavndl.app.data.FileNames
import com.saavndl.app.data.Song
import java.io.File
import java.io.IOException
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

object MediaSaver {
    const val ROOT = "Music/SaavnDL"

    fun relPath(sub: String): String =
        ROOT + (if (sub.isBlank()) "" else "/" + FileNames.clean(sub)) + "/"

    fun exists(ctx: Context, name: String, sub: String): Boolean {
        val c = ctx.contentResolver.query(
            MediaStore.Audio.Media.EXTERNAL_CONTENT_URI,
            arrayOf(MediaStore.Audio.Media._ID),
            "${MediaStore.Audio.Media.DISPLAY_NAME}=? AND ${MediaStore.Audio.Media.RELATIVE_PATH}=?",
            arrayOf(name, relPath(sub)), null
        )
        return c?.use { it.count > 0 } ?: false
    }

    fun saveAudio(ctx: Context, src: File, name: String, mime: String, sub: String, m: Meta): Uri {
        val r = ctx.contentResolver
        val v = ContentValues().apply {
            put(MediaStore.MediaColumns.DISPLAY_NAME, name)
            put(MediaStore.MediaColumns.MIME_TYPE, mime)
            put(MediaStore.MediaColumns.RELATIVE_PATH, relPath(sub))
            put(MediaStore.MediaColumns.IS_PENDING, 1)
            put(MediaStore.Audio.Media.TITLE, m.title)
            put(MediaStore.Audio.Media.ARTIST, m.artist)
            put(MediaStore.Audio.Media.ALBUM, m.album)
        }
        val uri = r.insert(MediaStore.Audio.Media.getContentUri(MediaStore.VOLUME_EXTERNAL_PRIMARY), v)
            ?: throw IOException("Could not create file in Music/SaavnDL")
        try {
            r.openOutputStream(uri)!!.use { o -> src.inputStream().use { it.copyTo(o) } }
            r.update(uri, ContentValues().apply { put(MediaStore.MediaColumns.IS_PENDING, 0) }, null, null)
        } catch (e: Exception) {
            r.delete(uri, null, null)
            throw e
        }
        return uri
    }

    fun saveCover(ctx: Context, bytes: ByteArray, name: String, sub: String) {
        val r = ctx.contentResolver
        val v = ContentValues().apply {
            put(MediaStore.MediaColumns.DISPLAY_NAME, name)
            put(MediaStore.MediaColumns.MIME_TYPE, "image/jpeg")
            put(MediaStore.MediaColumns.RELATIVE_PATH, "Pictures/SaavnDL/")
            put(MediaStore.MediaColumns.IS_PENDING, 1)
        }
        val uri = r.insert(MediaStore.Images.Media.getContentUri(MediaStore.VOLUME_EXTERNAL_PRIMARY), v) ?: return
        try {
            r.openOutputStream(uri)?.use { it.write(bytes) }
            r.update(uri, ContentValues().apply { put(MediaStore.MediaColumns.IS_PENDING, 0) }, null, null)
        } catch (e: Exception) {
            r.delete(uri, null, null)
        }
    }

    fun saveToDownloads(ctx: Context, name: String, mime: String, bytes: ByteArray) {
        val r = ctx.contentResolver
        val v = ContentValues().apply {
            put(MediaStore.MediaColumns.DISPLAY_NAME, name)
            put(MediaStore.MediaColumns.MIME_TYPE, mime)
            put(MediaStore.MediaColumns.RELATIVE_PATH, "Download/SaavnDL/")
        }
        val uri = r.insert(MediaStore.Downloads.EXTERNAL_CONTENT_URI, v) ?: throw IOException("Cannot create $name")
        r.openOutputStream(uri)?.use { it.write(bytes) }
    }

    /** Exports a song list as CSV into Download/SaavnDL. Returns the file name. */
    fun exportCsv(ctx: Context, songs: List<Song>): String {
        val name = "saavn-export-" + SimpleDateFormat("yyyyMMdd-HHmmss", Locale.US).format(Date()) + ".csv"
        fun q(s: String) = "\"" + s.replace("\"", "\"\"") + "\""
        val sb = StringBuilder("Title,Artists,Album,Year,Duration,Language,Id,Url\n")
        songs.forEach {
            sb.append(listOf(it.name, it.artists, it.album, it.year, it.duration.toString(), it.language, it.id, it.url)
                .joinToString(",") { x -> q(x) }).append('\n')
        }
        val r = ctx.contentResolver
        val v = ContentValues().apply {
            put(MediaStore.MediaColumns.DISPLAY_NAME, name)
            put(MediaStore.MediaColumns.MIME_TYPE, "text/csv")
            put(MediaStore.MediaColumns.RELATIVE_PATH, "Download/SaavnDL/")
        }
        val uri = r.insert(MediaStore.Downloads.EXTERNAL_CONTENT_URI, v) ?: throw IOException("Cannot create CSV")
        r.openOutputStream(uri)?.use { it.write(("\uFEFF" + sb).toByteArray(Charsets.UTF_8)) }
        return name
    }
}
