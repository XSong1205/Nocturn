package dev.nocturn.app

import android.app.Application
import android.content.Context
import dev.nocturn.app.data.LocalMusicRepository
import dev.nocturn.app.data.MusicRepository
import dev.nocturn.app.data.SettingsStore
import dev.nocturn.app.playback.MediaSessionPlaybackEngine
import dev.nocturn.app.playback.PlaybackEngine
import dev.nocturn.app.playback.lyricon.LyriconManager

/**
 * Application-scoped dependency container. The embedded Node runtime is process-owned, so the
 * repository (and its HTTP client) and the media session connection live for the whole process.
 */
open class AppContainer(context: Context) {
    open val settings: SettingsStore = SettingsStore(context)

    open val repository: MusicRepository by lazy { LocalMusicRepository(context.applicationContext) }

    open val playback: PlaybackEngine by lazy { MediaSessionPlaybackEngine(context.applicationContext) }

    open val lyriconManager: LyriconManager by lazy {
        LyriconManager(
            context = context.applicationContext,
            playback = playback,
            repository = repository,
            settings = settings,
        )
    }
}

open class NocturnApplication : Application() {
    @Volatile
    private var customContainer: AppContainer? = null

    var container: AppContainer
        get() = customContainer ?: synchronized(this) {
            customContainer ?: AppContainer(this).also { customContainer = it }
        }
        set(value) {
            synchronized(this) {
                customContainer = value
            }
        }

    override fun onCreate() {
        super.onCreate()
        container.lyriconManager.start()
    }
}
