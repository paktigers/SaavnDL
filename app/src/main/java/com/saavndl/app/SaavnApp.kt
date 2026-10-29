package com.saavndl.app

import android.app.Application
import android.database.ContentObserver
import android.os.Handler
import android.os.Looper
import android.provider.MediaStore
import com.saavndl.app.data.LibrarySync
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import com.saavndl.app.data.AppLog
import com.saavndl.app.data.Recents
import com.saavndl.app.data.SettingsStore
import com.saavndl.app.data.SpotifyAuth
import com.saavndl.app.data.Stores
import com.saavndl.app.player.PlayerController

class SaavnApp : Application() {
    override fun onCreate() {
        super.onCreate()
        instance = this
        AppLog.init(this)
        SettingsStore.init(this)
        SpotifyAuth.init(this)
        Recents.init(this)
        Stores.init(this)
        PlayerController.init(this)
        watchMedia()
    }

    /** When a song is deleted from the phone's storage, drop it from the in-app library automatically. */
    private fun watchMedia() {
        val handler = Handler(Looper.getMainLooper())
        val scope = CoroutineScope(Dispatchers.IO)
        val run = Runnable { scope.launch { runCatching { LibrarySync.removeMissing(this@SaavnApp) } } }
        val obs = object : ContentObserver(handler) {
            override fun onChange(selfChange: Boolean) {
                handler.removeCallbacks(run)
                handler.postDelayed(run, 1500)
            }
        }
        contentResolver.registerContentObserver(MediaStore.Audio.Media.EXTERNAL_CONTENT_URI, true, obs)
    }

    companion object {
        lateinit var instance: SaavnApp
            private set
    }
}
