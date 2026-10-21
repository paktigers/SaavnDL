package com.saavndl.app.data

import android.Manifest
import android.content.ContentUris
import android.content.Context
import android.content.pm.PackageManager
import android.graphics.Bitmap
import android.net.Uri
import android.os.Build
import android.provider.MediaStore
import android.util.Size
import androidx.core.content.ContextCompat
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.File

data class DeviceAudio(
    val id: Long, val uri: String, val title: String, val artist: String, val album: String, val year: String,
    val durationMs: Long, val size: Long, val mime: String, val dateAdded: Long, val name: String
)

/** Keeps the in-app library in sync with the files on the phone, and imports songs already on the device. */
object LibrarySync {
    fun permissionName(): String =
        if (Build.VERSION.SDK_INT >= 33) Manifest.permission.READ_MEDIA_AUDIO else Manifest.permission.READ_EXTERNAL_STORAGE

    fun hasPermission(ctx: Context) =
        ContextCompat.checkSelfPermission(ctx, permissionName()) == PackageManager.PERMISSION_GRANTED

    private val collection: Uri get() = MediaStore.Audio.Media.getContentUri(MediaStore.VOLUME_EXTERNAL_PRIMARY)

    fun idOf(uri: String): Long? = uri.substringAfterLast('/').toLongOrNull()

    private fun formatOf(mime: String, name: String): String {
        val ext = name.substringAfterLast('.', "").uppercase()
        return when {
            mime == "audio/mpeg" -> "MP3"
            mime == "audio/mp4" || mime == "audio/x-m4a" || ext == "M4A" -> "M4A"
            ext.isNotBlank() -> ext
            else -> "AUDIO"
        }
    }

    fun queryAudio(ctx: Context, onlySaavn: Boolean): List<DeviceAudio> {
        val out = ArrayList<DeviceAudio>()
        val proj = arrayOf(
            MediaStore.Audio.Media._ID, MediaStore.Audio.Media.TITLE, MediaStore.Audio.Media.ARTIST,
            MediaStore.Audio.Media.ALBUM, MediaStore.Audio.Media.YEAR, MediaStore.Audio.Media.DURATION,
            MediaStore.Audio.Media.SIZE, MediaStore.Audio.Media.MIME_TYPE, MediaStore.Audio.Media.DATE_ADDED,
            MediaStore.Audio.Media.DISPLAY_NAME
        )
        val sel: String
        val args: Array<String>?
        if (onlySaavn) {
            sel = "${MediaStore.Audio.Media.RELATIVE_PATH} LIKE ?"; args = arrayOf("Music/SaavnDL%")
        } else {
            sel = "${MediaStore.Audio.Media.IS_MUSIC} != 0"; args = null
        }
        ctx.contentResolver.query(collection, proj, sel, args, "${MediaStore.Audio.Media.DATE_ADDED} DESC")?.use { c ->
            while (c.moveToNext()) {
                val id = c.getLong(0)
                fun s(i: Int) = c.getString(i)?.takeIf { it != "<unknown>" }.orEmpty()
                val name = s(9)
                out.add(
                    DeviceAudio(
                        id, ContentUris.withAppendedId(collection, id).toString(),
                        s(1).ifBlank { name.substringBeforeLast('.') }, s(2), s(3),
                        c.getInt(4).let { if (it > 0) it.toString() else "" },
                        c.getLong(5), c.getLong(6), s(7), c.getLong(8), name
                    )
                )
            }
        }
        return out
    }

    private fun thumbFor(ctx: Context, uri: String, key: String): String {
        return try {
            val dir = File(ctx.filesDir, "covers").apply { mkdirs() }
            val f = File(dir, "$key.jpg")
            if (!f.exists()) {
                val bmp = ctx.contentResolver.loadThumbnail(Uri.parse(uri), Size(300, 300), null)
                f.outputStream().use { bmp.compress(Bitmap.CompressFormat.JPEG, 85, it) }
            }
            "file://" + f.absolutePath
        } catch (e: Exception) {
            ""
        }
    }

    private fun toItem(ctx: Context, a: DeviceAudio, prefix: String) = LibItem(
        songId = "$prefix${a.id}", title = a.title, artist = a.artist, album = a.album, uri = a.uri,
        format = formatOf(a.mime, a.name), thumb = thumbFor(ctx, a.uri, "$prefix${a.id}"), bytes = a.size,
        time = if (prefix == "scan_") a.dateAdded * 1000 else System.currentTimeMillis(), year = a.year
    )

    private fun knownIds(): Set<Long> = Stores.library.items.value.mapNotNull { idOf(it.uri) }.toSet()

    /** Adds files found in Music/SaavnDL that are not yet in the library (restores downloads after a reinstall). */
    suspend fun scanSaavnFolder(ctx: Context): Int = withContext(Dispatchers.IO) {
        val known = knownIds()
        val fresh = queryAudio(ctx, true).filter { it.id !in known }
        if (fresh.isEmpty()) return@withContext 0
        val items = fresh.map { toItem(ctx, it, "scan_") }
        Stores.library.update { l -> items + l }
        items.size
    }

    suspend fun importDevice(ctx: Context, list: List<DeviceAudio>, onProgress: (Int) -> Unit = {}): Int = withContext(Dispatchers.IO) {
        val known = knownIds()
        val fresh = list.filter { it.id !in known }
        val items = ArrayList<LibItem>()
        fresh.forEachIndexed { i, a -> items.add(toItem(ctx, a, "local_")); onProgress(i + 1) }
        if (items.isNotEmpty()) Stores.library.update { l -> items + l }
        items.size
    }

    /** Removes library entries whose file no longer exists (e.g. deleted from the phone's storage). */
    fun removeMissing(ctx: Context): Int {
        val items = Stores.library.items.value
        if (items.isEmpty()) return 0
        val perm = hasPermission(ctx)
        val installTime = runCatching { ctx.packageManager.getPackageInfo(ctx.packageName, 0).firstInstallTime }.getOrDefault(0L)
        val visible = HashSet<Long>()
        try {
            ctx.contentResolver.query(collection, arrayOf(MediaStore.Audio.Media._ID), null, null, null)?.use { c ->
                while (c.moveToNext()) visible.add(c.getLong(0))
            } ?: return 0
        } catch (e: Exception) {
            return 0
        }
        val gone = items.filter { it.uri.contains("/audio/media/") }.filter {
            val id = idOf(it.uri)
            // Files from a previous install are invisible without the audio permission, so only trust "missing" when we can really see them.
            val trust = if (it.songId.startsWith("local_")) perm else (perm || it.time >= installTime)
            id != null && id !in visible && trust
        }
        if (gone.isEmpty()) return 0
        val uris = gone.map { it.uri }.toSet()
        Stores.library.update { l -> l.filter { it.uri !in uris } }
        return gone.size
    }
}
