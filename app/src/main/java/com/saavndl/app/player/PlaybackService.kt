package com.saavndl.app.player

import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.app.Service
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.content.pm.ServiceInfo
import android.graphics.Bitmap
import android.graphics.drawable.BitmapDrawable
import android.graphics.drawable.Icon
import android.media.AudioManager
import android.media.MediaMetadata
import android.media.session.MediaSession
import android.media.session.PlaybackState
import android.os.IBinder
import coil.imageLoader
import coil.request.ImageRequest
import com.saavndl.app.MainActivity
import com.saavndl.app.R
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.launch

/** Foreground service: media notification, lock-screen controls (MediaSession) and widget updates. */
class PlaybackService : Service() {
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Main)
    private lateinit var nm: NotificationManager
    private lateinit var session: MediaSession
    private var art: Bitmap? = null
    private var artUrl = ""

    private val noisy = object : BroadcastReceiver() {
        override fun onReceive(c: Context?, i: Intent?) {
            if (i?.action == AudioManager.ACTION_AUDIO_BECOMING_NOISY && PlayerController.playing.value) PlayerController.toggle()
        }
    }

    override fun onBind(intent: Intent?): IBinder? = null

    override fun onCreate() {
        super.onCreate()
        nm = getSystemService(NOTIFICATION_SERVICE) as NotificationManager
        nm.createNotificationChannel(NotificationChannel(CH, "Playback", NotificationManager.IMPORTANCE_LOW))
        session = MediaSession(this, "SaavnDL").apply {
            setCallback(object : MediaSession.Callback() {
                override fun onPlay() { if (!PlayerController.playing.value) PlayerController.toggle() }
                override fun onPause() { if (PlayerController.playing.value) PlayerController.toggle() }
                override fun onSkipToNext() = PlayerController.next()
                override fun onSkipToPrevious() = PlayerController.previous()
                override fun onSeekTo(pos: Long) = PlayerController.seekTo(pos.toInt())
                override fun onStop() = PlayerController.stop()
            })
            isActive = true
        }
        startForeground(ID, build(null, false), ServiceInfo.FOREGROUND_SERVICE_TYPE_MEDIA_PLAYBACK)
        registerReceiver(noisy, IntentFilter(AudioManager.ACTION_AUDIO_BECOMING_NOISY))
        scope.launch {
            combine(PlayerController.current, PlayerController.playing, PlayerController.loading) { c, p, l -> Triple(c, p, l) }
                .collect { (t, p, l) -> render(t, p, l) }
        }
        scope.launch { PlayerController.position.collect { updateState() } }
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        when (intent?.action) {
            ACT_TOGGLE -> PlayerController.toggle()
            ACT_NEXT -> PlayerController.next()
            ACT_PREV -> PlayerController.previous()
            ACT_STOP -> PlayerController.stop()
        }
        return START_NOT_STICKY
    }

    private fun updateState() {
        val loading = PlayerController.loading.value
        val st = when {
            loading -> PlaybackState.STATE_BUFFERING
            PlayerController.playing.value -> PlaybackState.STATE_PLAYING
            else -> PlaybackState.STATE_PAUSED
        }
        session.setPlaybackState(
            PlaybackState.Builder()
                .setActions(
                    PlaybackState.ACTION_PLAY or PlaybackState.ACTION_PAUSE or PlaybackState.ACTION_PLAY_PAUSE or
                        PlaybackState.ACTION_SKIP_TO_NEXT or PlaybackState.ACTION_SKIP_TO_PREVIOUS or
                        PlaybackState.ACTION_SEEK_TO or PlaybackState.ACTION_STOP
                )
                .setState(st, PlayerController.position.value.toLong(), 1f).build()
        )
    }

    private fun render(t: Track?, playing: Boolean, loading: Boolean) {
        if (t == null) {
            PlayerWidget.push(this)
            stopForeground(STOP_FOREGROUND_REMOVE)
            stopSelf()
            return
        }
        val url = t.art.ifBlank { t.image }
        if (url != artUrl) {
            artUrl = url
            art = null
            if (url.isNotBlank()) scope.launch {
                val res = imageLoader.execute(ImageRequest.Builder(this@PlaybackService).data(url).allowHardware(false).build())
                art = (res.drawable as? BitmapDrawable)?.bitmap
                render(PlayerController.current.value, PlayerController.playing.value, PlayerController.loading.value)
            }
        }
        session.setMetadata(
            MediaMetadata.Builder()
                .putString(MediaMetadata.METADATA_KEY_TITLE, t.title)
                .putString(MediaMetadata.METADATA_KEY_ARTIST, t.artist)
                .putString(MediaMetadata.METADATA_KEY_ALBUM, t.album)
                .putLong(MediaMetadata.METADATA_KEY_DURATION, PlayerController.duration.value.toLong())
                .apply { art?.let { putBitmap(MediaMetadata.METADATA_KEY_ALBUM_ART, it) } }
                .build()
        )
        updateState()
        nm.notify(ID, build(t, playing))
        PlayerWidget.push(this)
    }

    private fun pi(action: String, code: Int): PendingIntent = PendingIntent.getForegroundService(
        this, code, Intent(this, PlaybackService::class.java).setAction(action),
        PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT
    )

    private fun action(icon: Int, title: String, a: String, code: Int) =
        Notification.Action.Builder(Icon.createWithResource(this, icon), title, pi(a, code)).build()

    private fun build(t: Track?, playing: Boolean): Notification {
        val open = PendingIntent.getActivity(
            this, 0, Intent(this, MainActivity::class.java).addFlags(Intent.FLAG_ACTIVITY_SINGLE_TOP),
            PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT
        )
        val b = Notification.Builder(this, CH)
            .setSmallIcon(R.drawable.ic_stat_download)
            .setContentTitle(t?.title ?: "SaavnDL")
            .setContentText(t?.artist ?: "")
            .setContentIntent(open)
            .setOnlyAlertOnce(true)
            .setOngoing(playing)
            .setVisibility(Notification.VISIBILITY_PUBLIC)
        art?.let { b.setLargeIcon(it) }
        if (t != null) {
            b.addAction(action(R.drawable.ic_prev, "Previous", ACT_PREV, 1))
            b.addAction(action(if (playing) R.drawable.ic_pause else R.drawable.ic_play, if (playing) "Pause" else "Play", ACT_TOGGLE, 2))
            b.addAction(action(R.drawable.ic_next, "Next", ACT_NEXT, 3))
            b.setStyle(Notification.MediaStyle().setMediaSession(session.sessionToken).setShowActionsInCompactView(0, 1, 2))
        }
        return b.build()
    }

    override fun onDestroy() {
        runCatching { unregisterReceiver(noisy) }
        session.release()
        scope.cancel()
        PlayerWidget.push(this)
        super.onDestroy()
    }

    companion object {
        const val CH = "playback"
        const val ID = 4712
        const val ACT_TOGGLE = "com.saavndl.app.TOGGLE"
        const val ACT_NEXT = "com.saavndl.app.NEXT"
        const val ACT_PREV = "com.saavndl.app.PREV"
        const val ACT_STOP = "com.saavndl.app.STOP"
    }
}
