package com.saavndl.app

import android.app.Application
import com.saavndl.app.data.AppLog
import com.saavndl.app.data.Recents
import com.saavndl.app.data.SettingsStore
import com.saavndl.app.data.Stores
import com.saavndl.app.player.PlayerController

class SaavnApp : Application() {
    override fun onCreate() {
        super.onCreate()
        instance = this
        AppLog.init(this)
        SettingsStore.init(this)
        Recents.init(this)
        Stores.init(this)
        PlayerController.init(this)
    }

    companion object {
        lateinit var instance: SaavnApp
            private set
    }
}
