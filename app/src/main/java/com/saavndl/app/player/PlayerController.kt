package com.saavndl.app.player

import android.content.Context
import android.media.AudioAttributes
import android.media.MediaPlayer
import android.net.Uri
import com.saavndl.app.data.Song
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.launch

data class Track(val id: String, val title: String, val artist: String, val image: String, val source: String)

fun Song.toTrack(): Track {
    val q = downloads.firstOrNull { it.quality == "160kbps" } ?: downloads.firstOrNull { it.quality == "96kbps" }
        ?: downloads.lastOrNull()
    return Track(id, name, artists, thumb, q?.url.orEmpty())
}

object PlayerController {
    private lateinit var app: Context
    private var mp: MediaPlayer? = null
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Main)
    private var queue: List<Track> = emptyList()
    private var index = 0

    private val _current = MutableStateFlow<Track?>(null)
    private val _playing = MutableStateFlow(false)
    private val _loading = MutableStateFlow(false)
    private val _position = MutableStateFlow(0)
    private val _duration = MutableStateFlow(0)
    val current: StateFlow<Track?> = _current
    val playing: StateFlow<Boolean> = _playing
    val loading: StateFlow<Boolean> = _loading
    val position: StateFlow<Int> = _position
    val duration: StateFlow<Int> = _duration

    fun init(ctx: Context) {
        app = ctx.applicationContext
        scope.launch {
            while (true) {
                if (_playing.value) _position.value = runCatching { mp?.currentPosition ?: 0 }.getOrDefault(0)
                delay(400)
            }
        }
    }

    fun playQueue(tracks: List<Track>, start: Int = 0) {
        val list = tracks.filter { it.source.isNotBlank() }
        if (list.isEmpty()) return
        val target = tracks.getOrNull(start)
        queue = list
        index = (list.indexOfFirst { it.id == target?.id && it.source == target?.source }).coerceAtLeast(0)
        startTrack(list[index])
    }

    fun playOne(t: Track) = playQueue(listOf(t), 0)

    private fun startTrack(t: Track) {
        release()
        _current.value = t
        _loading.value = true
        _playing.value = false
        _position.value = 0
        _duration.value = 0
        try {
            val p = MediaPlayer()
            p.setAudioAttributes(
                AudioAttributes.Builder()
                    .setContentType(AudioAttributes.CONTENT_TYPE_MUSIC)
                    .setUsage(AudioAttributes.USAGE_MEDIA).build()
            )
            p.setDataSource(app, Uri.parse(t.source))
            p.setOnPreparedListener {
                it.start()
                _duration.value = it.duration
                _loading.value = false
                _playing.value = true
            }
            p.setOnCompletionListener { next() }
            p.setOnErrorListener { _, _, _ ->
                _loading.value = false
                _playing.value = false
                true
            }
            mp = p
            p.prepareAsync()
        } catch (e: Exception) {
            _loading.value = false
            _playing.value = false
        }
    }

    fun toggle() {
        val p = mp ?: return
        runCatching {
            if (p.isPlaying) { p.pause(); _playing.value = false } else { p.start(); _playing.value = true }
        }
    }

    fun next() {
        if (queue.isEmpty()) return
        if (index + 1 < queue.size) { index++; startTrack(queue[index]) } else { _playing.value = false }
    }

    fun previous() {
        if (queue.isEmpty()) return
        if (_position.value > 3000) { seekTo(0); return }
        if (index > 0) { index--; startTrack(queue[index]) }
    }

    fun seekTo(ms: Int) {
        runCatching { mp?.seekTo(ms); _position.value = ms }
    }

    fun stop() {
        release()
        queue = emptyList()
        _current.value = null
        _playing.value = false
        _loading.value = false
    }

    private fun release() {
        runCatching { mp?.release() }
        mp = null
    }
}
