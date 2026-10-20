package com.saavndl.app.download

import android.content.ContentValues
import android.content.Context
import android.net.Uri
import android.provider.MediaStore
import com.saavndl.app.data.LibItem
import com.saavndl.app.data.LyricsApi
import com.saavndl.app.data.SettingsStore
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.File

/** Rewrites the tags of an already downloaded MP3/M4A in place. */
object TagEditor {
    suspend fun edit(
        ctx: Context, item: LibItem, title: String, artist: String, album: String, year: String, refreshCover: Boolean
    ): LibItem = withContext(Dispatchers.IO) {
        val uri = Uri.parse(item.uri)
        val r = ctx.contentResolver
        val stamp = System.nanoTime()
        val src = File(ctx.cacheDir, "te_${stamp}_in")
        val dst = File(ctx.cacheDir, "te_${stamp}_out")
        val cov = File(ctx.cacheDir, "te_${stamp}_cov")
        try {
            (r.openInputStream(uri) ?: throw java.io.IOException("File not found")).use { i -> src.outputStream().use { i.copyTo(it) } }
            val bytes = src.readBytes()
            val isMp3 = item.format.equals("MP3", true)
            val info = if (isMp3) Id3Writer.parse(bytes) else null
            var cover: ByteArray? = if (isMp3) info?.cover else Mp4Tagger.extractCover(bytes)
            if (refreshCover) {
                val url = item.image.ifBlank { item.thumb }
                if (url.isNotBlank()) runCatching { Http.download(url, cov); cover = cov.readBytes() }
            }
            var lyrics = ""
            if (SettingsStore.state.value.embedLyrics) {
                lyrics = LyricsApi.fetch(title, artist, album, 0)?.plain.orEmpty()
            }
            val meta = Meta(
                title, artist, album, item.albumArtist.ifBlank { artist }, year, item.track, item.total,
                item.copyright, item.label, item.language, item.url, lyrics
            )
            if (isMp3) {
                val off = info?.bodyOffset ?: 0
                dst.outputStream().use { o ->
                    o.write(Id3Writer.build(meta, cover))
                    o.write(bytes, off, bytes.size - off)
                }
            } else {
                Mp4Tagger.tag(src, dst, meta, cover)
            }
            (r.openOutputStream(uri, "wt") ?: throw java.io.IOException("Cannot write file")).use { o -> dst.inputStream().use { it.copyTo(o) } }
            r.update(uri, ContentValues().apply {
                put(MediaStore.Audio.Media.TITLE, title)
                put(MediaStore.Audio.Media.ARTIST, artist)
                put(MediaStore.Audio.Media.ALBUM, album)
            }, null, null)
            item.copy(title = title, artist = artist, album = album, year = year, bytes = dst.length())
        } finally {
            src.delete(); dst.delete(); cov.delete()
        }
    }
}
