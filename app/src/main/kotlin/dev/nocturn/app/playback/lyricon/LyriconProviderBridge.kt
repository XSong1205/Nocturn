package dev.nocturn.app.playback.lyricon

import android.content.Context
import android.media.session.PlaybackState
import android.util.Log
import io.github.proify.lyricon.lyric.model.Song
import io.github.proify.lyricon.provider.ConnectionListener
import io.github.proify.lyricon.provider.LyriconFactory
import io.github.proify.lyricon.provider.LyriconProvider

interface LyriconPlayerClient {
    fun setSong(song: Song?)
    fun sendText(text: String?)
    fun setPlaybackState(playing: Boolean)
    fun setPlaybackState(state: PlaybackState)
    fun setPosition(positionMs: Long)
    fun seekTo(positionMs: Long)
    fun setDisplayTranslation(display: Boolean)
}

interface LyriconProviderBridge {
    val player: LyriconPlayerClient
    var autoSync: Boolean
    fun setOnConnectedListener(listener: (() -> Unit)?)
    fun register(): Boolean
    fun unregister()
    fun destroy()
}

/** Wraps the optional SDK without hiding setup/connection failures from device diagnostics. */
class DefaultLyriconProviderBridge(private val provider: LyriconProvider?) : LyriconProviderBridge {
    constructor(context: Context) : this(
        runCatching { LyriconFactory.createProvider(context.applicationContext) }
            .onFailure { Log.w(TAG, "Unable to create provider", it) }.getOrNull(),
    )

    private var onConnected: (() -> Unit)? = null
    private val connectionListener = object : ConnectionListener {
        override fun onConnected(provider: LyriconProvider) {
            Log.i(TAG, "Connected to Lyricon")
            onConnected?.invoke()
        }

        override fun onReconnected(provider: LyriconProvider) {
            Log.i(TAG, "Reconnected to Lyricon")
            onConnected?.invoke()
        }

        override fun onDisconnected(provider: LyriconProvider) {
            Log.i(TAG, "Disconnected from Lyricon")
        }

        override fun onConnectTimeout(provider: LyriconProvider) {
            Log.w(TAG, "Connection timed out; check Lyricon and its LSPosed scope")
        }
    }

    init {
        provider?.service?.addConnectionListener(connectionListener)
    }

    override val player = object : LyriconPlayerClient {
        override fun setSong(song: Song?) = command { player.setSong(song) }
        override fun sendText(text: String?) = command { player.sendText(text) }
        override fun setPlaybackState(playing: Boolean) = command { player.setPlaybackState(playing) }
        override fun setPlaybackState(state: PlaybackState) = command { player.setPlaybackState(state) }
        override fun setPosition(positionMs: Long) = command { player.setPosition(positionMs) }
        override fun seekTo(positionMs: Long) = command { player.seekTo(positionMs) }
        override fun setDisplayTranslation(display: Boolean) = command { player.setDisplayTranslation(display) }
    }

    override var autoSync: Boolean
        get() = provider?.autoSync ?: false
        set(value) { command { autoSync = value } }

    override fun setOnConnectedListener(listener: (() -> Unit)?) {
        onConnected = listener
    }

    override fun register(): Boolean = runCatching { provider?.register() ?: false }
        .onFailure { Log.w(TAG, "Unable to request registration", it) }.getOrDefault(false)

    override fun unregister() = command { unregister() }

    override fun destroy() {
        onConnected = null
        command {
            service.removeConnectionListener(connectionListener)
            destroy()
        }
    }

    private fun command(block: LyriconProvider.() -> Unit) {
        runCatching { provider?.block() }.onFailure { Log.w(TAG, "Provider command failed", it) }
    }

    private companion object {
        const val TAG = "NocturnLyricon"
    }
}
