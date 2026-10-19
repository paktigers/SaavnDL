package com.saavndl.app.download

import android.content.Context
import android.content.Intent
import androidx.core.content.ContextCompat
import com.saavndl.app.SaavnApp
import com.saavndl.app.data.AudioFormat
import com.saavndl.app.data.FileNames
import com.saavndl.app.data.LibItem
import com.saavndl.app.data.SaavnApi
import com.saavndl.app.data.SettingsStore
import com.saavndl.app.data.Song
import com.saavndl.app.data.Stores
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import kotlinx.coroutines.sync.Semaphore
import kotlinx.coroutines.sync.withPermit
import java.io.File
import java.io.IOException
import java.util.concurrent.ConcurrentHashMap
import java.util.concurrent.atomic.AtomicLong

enum class DlStatus { QUEUED, DOWNLOADING, PROCESSING, DONE, FAILED, CANCELLED, SKIPPED }

data class DlTask(
    val id: String,
    val song: Song,
    val sub: String,
    val track: Int,
    val total: Int,
    val status: DlStatus = DlStatus.QUEUED,
    val progress: Float = 0f,
    val message: String = ""
) {
    val active get() = status == DlStatus.QUEUED || status == DlStatus.DOWNLOADING || status == DlStatus.PROCESSING
}

object Downloads {
    private val _tasks = MutableStateFlow<List<DlTask>>(emptyList())
    val tasks: StateFlow<List<DlTask>> = _tasks

    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)
    private val jobs = ConcurrentHashMap<String, Job>()
    private var sem = Semaphore(2)
    private var semSize = 2
    private val counter = AtomicLong()

    /** positions: 1-based track number per song (for albums/playlists), or null. */
    fun enqueue(ctx: Context, songs: List<Song>, sub: String = "", positions: List<Int>? = null, total: Int = 0) {
        if (songs.isEmpty()) return
        val cfg = SettingsStore.state.value
        if (cfg.concurrent != semSize) { sem = Semaphore(cfg.concurrent); semSize = cfg.concurrent }
        val sm = sem
        val fresh = songs.mapIndexed { i, s ->
            DlTask("${System.nanoTime()}_${counter.incrementAndGet()}", s, sub, positions?.getOrNull(i) ?: 0, if (positions != null) total else 0)
        }
        _tasks.update { it + fresh }
        ContextCompat.startForegroundService(ctx, Intent(ctx, DownloadService::class.java))
        fresh.forEach { launchTask(it.id, sm) }
    }

    private fun launchTask(id: String, sm: Semaphore) {
        jobs[id] = scope.launch { sm.withPermit { run(id) } }
    }

    private fun patch(id: String, f: (DlTask) -> DlTask) =
        _tasks.update { l -> l.map { if (it.id == id) f(it) else it } }

    fun cancel(id: String) {
        jobs[id]?.cancel()
        patch(id) { if (it.active) it.copy(status = DlStatus.CANCELLED, message = "Cancelled") else it }
    }

    fun cancelAll() = _tasks.value.filter { it.active }.forEach { cancel(it.id) }

    fun retry(ctx: Context, id: String) {
        patch(id) { it.copy(status = DlStatus.QUEUED, progress = 0f, message = "") }
        ContextCompat.startForegroundService(ctx, Intent(ctx, DownloadService::class.java))
        launchTask(id, sem)
    }

    fun retryFailed(ctx: Context) =
        _tasks.value.filter { it.status == DlStatus.FAILED || it.status == DlStatus.CANCELLED }.forEach { retry(ctx, it.id) }

    fun clearFinished() = _tasks.update { l -> l.filter { it.active } }

    // ------------------------------------------------------------------ worker
    private fun pick(song: Song, quality: String) =
        song.downloads.firstOrNull { it.quality == quality } ?: song.downloads.lastOrNull()

    private suspend fun run(id: String) {
        val t = _tasks.value.firstOrNull { it.id == id } ?: return
        if (t.status == DlStatus.CANCELLED) return
        val cfg = SettingsStore.state.value
        val ctx = SaavnApp.instance
        val tmpDir = ctx.cacheDir
        val raw = File(tmpDir, "dl_$id.m4a")
        val cov = File(tmpDir, "dl_$id.jpg")
        val outTagged = File(tmpDir, "out_$id.m4a")
        val outMp3 = File(tmpDir, "out_$id.mp3")
        try {
            patch(id) { it.copy(status = DlStatus.DOWNLOADING, message = "Connecting…") }
            var song = t.song
            val prefix = if (t.track > 0) "%02d - ".format(t.track) else ""
            val base = FileNames.clean(prefix + FileNames.style(cfg.nameStyle, song))
            val wantMp3 = cfg.format == AudioFormat.MP3 && Mp3Encoder.isAvailable()
            val ext = if (wantMp3) "mp3" else "m4a"
            if (cfg.skipExisting && MediaSaver.exists(ctx, "$base.$ext", t.sub)) {
                patch(id) { it.copy(status = DlStatus.SKIPPED, progress = 1f, message = "Already downloaded") }
                return
            }

            var q = pick(song, cfg.quality) ?: throw IOException("No download link for this song")
            var last = 0f
            val onProgress: (Float) -> Unit = { p ->
                if (p - last >= 0.02f || p >= 0.999f) {
                    last = p
                    patch(id) { it.copy(progress = p, message = "Downloading ${(p * 100).toInt()}%") }
                }
            }
            try {
                Http.download(q.url, raw, onProgress)
            } catch (e: IOException) {
                // link may have expired (e.g. from favorites): refresh song info once
                song = SaavnApi.songById(song.id)
                q = pick(song, cfg.quality) ?: throw IOException("No download link")
                Http.download(q.url, raw, onProgress)
            }

            var coverBytes: ByteArray? = null
            if (cfg.format != AudioFormat.ORIGINAL || cfg.saveCover) {
                if (song.image.isNotBlank()) {
                    runCatching { Http.download(song.image, cov); coverBytes = cov.readBytes() }
                }
            }

            patch(id) { it.copy(status = DlStatus.PROCESSING, progress = 1f, message = "Writing metadata…") }
            val meta = Meta(
                title = song.name, artist = song.artists, album = song.album, albumArtist = song.firstArtist,
                year = song.year, track = t.track, total = t.total, copyright = song.copyright,
                label = song.label, language = song.language, comment = song.url
            )
            var finalFile = raw
            var finalExt = "m4a"
            var mime = "audio/mp4"
            var note = ""

            fun tagM4a(): Boolean = try {
                Mp4Tagger.tag(raw, outTagged, meta, coverBytes); finalFile = outTagged; true
            } catch (e: Exception) { false }

            when (cfg.format) {
                AudioFormat.ORIGINAL -> {}
                AudioFormat.M4A_TAGS -> if (!tagM4a()) note = "Tagging failed, saved original"
                AudioFormat.MP3 -> {
                    var ok = false
                    if (wantMp3) {
                        val kbps = Regex("\\d+").find(q.quality)?.value?.toIntOrNull()?.coerceIn(128, 320) ?: 320
                        patch(id) { it.copy(message = "Converting to MP3…") }
                        ok = try {
                            Mp3Encoder.encode(raw, outMp3, kbps, meta, coverBytes)
                            finalFile = outMp3; finalExt = "mp3"; mime = "audio/mpeg"; true
                        } catch (e: CancellationException) { throw e } catch (e: Throwable) { false }
                    }
                    if (!ok) {
                        note = if (tagM4a()) "MP3 encoder unavailable, saved as tagged M4A" else "MP3 failed, saved original"
                    }
                }
            }

            val displayName = "${FileNames.clean(prefix + FileNames.style(cfg.nameStyle, song))}.$finalExt"
            val uri = MediaSaver.saveAudio(ctx, finalFile, displayName, mime, t.sub, meta)
            if (cfg.saveCover && coverBytes != null) {
                MediaSaver.saveCover(ctx, coverBytes!!, displayName.substringBeforeLast('.') + ".jpg", t.sub)
            }
            Stores.library.update { l ->
                listOf(LibItem(song.id, song.name, song.artists, song.album, uri.toString(), finalExt.uppercase(), song.thumb, finalFile.length(), System.currentTimeMillis())) + l
            }
            patch(id) { it.copy(status = DlStatus.DONE, progress = 1f, message = note.ifBlank { "Saved as ${finalExt.uppercase()}" }) }
        } catch (e: CancellationException) {
            patch(id) { it.copy(status = DlStatus.CANCELLED, message = "Cancelled") }
            throw e
        } catch (e: Exception) {
            patch(id) { it.copy(status = DlStatus.FAILED, message = e.message ?: "Failed") }
        } finally {
            raw.delete(); cov.delete(); outTagged.delete(); outMp3.delete()
            jobs.remove(id)
        }
    }
}
