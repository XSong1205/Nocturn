package com.nocturn.music

import android.app.Application
import android.content.Context

class NocturnApp : Application() {
    companion object {
        lateinit var instance: NocturnApp
            private set

        val appContext: Context
            get() = instance.applicationContext
    }

    override fun onCreate() {
        super.onCreate()
        instance = this
        com.nocturn.music.data.api.EmbeddedHttpServer.start()
    }
}
