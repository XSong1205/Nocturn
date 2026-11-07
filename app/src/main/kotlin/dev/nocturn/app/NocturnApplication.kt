package dev.nocturn.app

import android.app.Application
import android.content.Context
import dev.nocturn.app.data.LocalMusicRepository
import dev.nocturn.app.data.MusicRepository
import dev.nocturn.app.data.SettingsStore
import dev.nocturn.app.playback.MediaSessionPlaybackEngine
import dev.nocturn.app.playback.PlaybackEngine

/**
 * Application-scoped dependency container. The embedded Node runtime is process-owned, so the
 * repository (and its HTTP client) and the media session connection live for the whole process.
 */
class AppContainer(context: Context) {
    val settings: SettingsStore = SettingsStore(context)

    val repository: MusicRepository by lazy { LocalMusicRepository(context.applicationContext) }

    val playback: PlaybackEngine by lazy { MediaSessionPlaybackEngine(context.applicationContext) }
}

class NocturnApplication : Application() {
    val container: AppContainer by lazy { AppContainer(this) }
}
