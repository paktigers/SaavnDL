package com.saavndl.app.player

import android.content.Context
import android.content.Intent
import android.media.AudioAttributes
import android.media.AudioFocusRequest
import android.media.AudioManager
import android.media.MediaPlayer
import android.net.Uri
import androidx.core.content.ContextCompat
import com.saavndl.app.data.Song
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.launch

data class Track(
    val id: String, val title: String, val artist: String, val image: String, val source: String,
    val album: String = "", val duration: Int = 0, val art: String = "", val song: Song? = null
)

fun Song.toTrack(): Track {
    val q = downloads.firstOrNull { it.quality == "160kbps" } ?: downloads.firstOrNull { it.quality == "96kbps" }
        ?: downloads.lastOrNull()
    return Track(id, name, artists, thumb, q?.url.orEmpty(), album, duration, image, this)
}

object PlayerController {
    const val REPEAT_OFF = 0
    const val REPEAT_ALL = 1
    const val REPEAT_ONE = 2

    private lateinit var app: Context
    private var mp: MediaPlayer? = null
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Main)
    private var original: List<Track> = emptyList()
    private var queue: List<Track> = emptyList()
    private var index = 0
    private var sleepJob: Job? = null
    private var resumeOnGain = false
    private var focusReq: AudioFocusRequest? = null

    private val _current = MutableStateFlow<Track?>(null)
    private val _playing = MutableStateFlow(false)
    private val _loading = MutableStateFlow(false)
    private val _position = MutableStateFlow(0)
    private val _duration = MutableStateFlow(0)
    private val _shuffle = MutableStateFlow(false)
    private val _repeat = MutableStateFlow(REPEAT_OFF)
    private val _sleepEnd = MutableStateFlow<Long?>(null)
    private val _sleepAfterTrack = MutableStateFlow(false)
    private val _queueSize = MutableStateFlow(0)
    val current: StateFlow<Track?> = _current
    val playing: StateFlow<Boolean> = _playing
    val loading: StateFlow<Boolean> = _loading
    val position: StateFlow<Int> = _position
    val duration: StateFlow<Int> = _duration
    val shuffle: StateFlow<Boolean> = _shuffle
    val repeat: StateFlow<Int> = _repeat
    val sleepEnd: StateFlow<Long?> = _sleepEnd
    val sleepAfterTrack: StateFlow<Boolean> = _sleepAfterTrack
    val queueSize: StateFlow<Int> = _queueSize

    private val attrs = AudioAttributes.Builder()
        .setContentType(AudioAttributes.CONTENT_TYPE_MUSIC).setUsage(AudioAttributes.USAGE_MEDIA).build()

    private val focusListener = AudioManager.OnAudioFocusChangeListener { change ->
        when (change) {
            AudioManager.AUDIOFOCUS_LOSS -> { resumeOnGain = false; pauseInternal() }
            AudioManager.AUDIOFOCUS_LOSS_TRANSIENT -> if (_playing.value) { resumeOnGain = true; pauseInternal() }
            AudioManager.AUDIOFOCUS_LOSS_TRANSIENT_CAN_DUCK -> runCatching { mp?.setVolume(0.3f, 0.3f) }
            AudioManager.AUDIOFOCUS_GAIN -> {
                runCatching { mp?.setVolume(1f, 1f) }
                if (resumeOnGain) { resumeOnGain = false; resumeInternal() }
            }
        }
    }

    fun init(ctx: Context) {
        app = ctx.applicationContext
        scope.launch {
            while (true) {
                if (_playing.value) _position.value = runCatching { mp?.currentPosition ?: 0 }.getOrDefault(0)
                delay(400)
            }
        }
    }

    // ------------------------------------------------------------------ queue
    fun playQueue(tracks: List<Track>, start: Int = 0) {
        val list = tracks.filter { it.source.isNotBlank() }
        if (list.isEmpty()) return
        val target = tracks.getOrNull(start)
        original = list
        val first = list.firstOrNull { it.id == target?.id && it.source == target?.source } ?: list[0]
        if (_shuffle.value) {
            queue = listOf(first) + (list - first).shuffled()
            index = 0
        } else {
            queue = list
            index = list.indexOf(first).coerceAtLeast(0)
        }
        _queueSize.value = queue.size
        startTrack(queue[index])
    }

    fun playOne(t: Track) = playQueue(listOf(t), 0)

    fun playNext(t: Track) {
        if (t.source.isBlank()) return
        if (queue.isEmpty()) { playOne(t); return }
        queue = queue.toMutableList().also { it.add(index + 1, t) }
        original = original + t
        _queueSize.value = queue.size
    }

    fun addToQueue(t: Track) {
        if (t.source.isBlank()) return
        if (queue.isEmpty()) { playOne(t); return }
        queue = queue + t
        original = original + t
        _queueSize.value = queue.size
    }

    fun toggleShuffle() {
        val on = !_shuffle.value
        _shuffle.value = on
        val cur = _current.value ?: return
        if (on) {
            queue = listOf(cur) + (original - cur).shuffled()
            index = 0
        } else {
            queue = original
            index = original.indexOf(cur).coerceAtLeast(0)
        }
    }

    fun cycleRepeat() { _repeat.value = (_repeat.value + 1) % 3 }

    // ------------------------------------------------------------------ playback
    private fun requestFocus() {
        runCatching {
            val am = app.getSystemService(AudioManager::class.java)
            val r = AudioFocusRequest.Builder(AudioManager.AUDIOFOCUS_GAIN)
                .setAudioAttributes(attrs).setOnAudioFocusChangeListener(focusListener).build()
            focusReq = r
            am.requestAudioFocus(r)
        }
    }

    private fun abandonFocus() {
        runCatching { focusReq?.let { app.getSystemService(AudioManager::class.java).abandonAudioFocusRequest(it) } }
    }

    private fun startTrack(t: Track) {
        release()
        _current.value = t
        _loading.value = true
        _playing.value = false
        _position.value = 0
        _duration.value = 0
        runCatching { ContextCompat.startForegroundService(app, Intent(app, PlaybackService::class.java)) }
        requestFocus()
        try {
            val p = MediaPlayer()
            p.setAudioAttributes(attrs)
            p.setDataSource(app, Uri.parse(t.source))
            p.setOnPreparedListener {
                it.start()
                _duration.value = it.duration
                _loading.value = false
                _playing.value = true
            }
            p.setOnCompletionListener { onCompleted() }
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

    private fun onCompleted() {
        if (_sleepAfterTrack.value) {
            _sleepAfterTrack.value = false
            _playing.value = false
            _position.value = 0
            return
        }
        if (_repeat.value == REPEAT_ONE) { seekTo(0); runCatching { mp?.start() }; return }
        advance()
    }

    private fun advance() {
        if (queue.isEmpty()) return
        if (index + 1 < queue.size) { index++; startTrack(queue[index]) }
        else if (_repeat.value == REPEAT_ALL) { index = 0; startTrack(queue[0]) }
        else { _playing.value = false; _position.value = 0 }
    }

    private fun pauseInternal() { runCatching { mp?.pause() }; _playing.value = false }
    private fun resumeInternal() { runCatching { mp?.start(); _playing.value = true } }

    fun toggle() {
        val p = mp ?: return
        if (_playing.value) pauseInternal()
        else { requestFocus(); runCatching { p.start(); _playing.value = true } }
    }

    fun next() = advance()

    fun previous() {
        if (queue.isEmpty()) return
        if (_position.value > 3000) { seekTo(0); return }
        if (index > 0) { index--; startTrack(queue[index]) } else seekTo(0)
    }

    fun seekTo(ms: Int) {
        runCatching { mp?.seekTo(ms); _position.value = ms }
    }

    fun stop() {
        sleepJob?.cancel()
        _sleepEnd.value = null
        _sleepAfterTrack.value = false
        release()
        abandonFocus()
        queue = emptyList(); original = emptyList(); index = 0
        _queueSize.value = 0
        _current.value = null
        _playing.value = false
        _loading.value = false
        _position.value = 0
    }

    // ------------------------------------------------------------------ sleep timer
    fun setSleep(minutes: Int?, afterTrack: Boolean = false) {
        sleepJob?.cancel()
        _sleepEnd.value = null
        _sleepAfterTrack.value = afterTrack
        if (minutes != null) {
            _sleepEnd.value = System.currentTimeMillis() + minutes * 60_000L
            sleepJob = scope.launch {
                delay(minutes * 60_000L)
                pauseInternal()
                _sleepEnd.value = null
            }
        }
    }

    private fun release() {
        runCatching { mp?.release() }
        mp = null
    }
}
