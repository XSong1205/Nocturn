package dev.nocturn.app.playback.lyricon

import android.content.Context
import android.content.SharedPreferences
import android.media.session.PlaybackState
import android.os.SystemClock
import dev.nocturn.app.data.MusicRepository
import dev.nocturn.app.data.SettingsStore
import dev.nocturn.app.data.model.Lyrics as NocturnLyrics
import dev.nocturn.app.playback.PlaybackEngine
import dev.nocturn.app.playback.PlaybackSnapshot
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.delay
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch

interface LyriconBridge {
    fun start()
    fun stop()
    fun destroy()
    fun setEnabled(enabled: Boolean)
    fun isEnabled(): Boolean
}

/** Application-owned bridge. All state changes run on the playback/main thread. */
open class LyriconManager(
    private val context: Context,
    private val playback: PlaybackEngine,
    private val repository: MusicRepository,
    private val settings: SettingsStore,
    private val coroutineScope: CoroutineScope = CoroutineScope(SupervisorJob() + Dispatchers.Main.immediate),
    private val bridgeFactory: (Context) -> LyriconProviderBridge = { DefaultLyriconProviderBridge(it) },
) : LyriconBridge {
    private var started = false
    private var destroyed = false
    // register() acknowledges a request, not an established connection. The SDK caches commands
    // while connecting and autoSync replays them when the remote player becomes available.
    private var registrationRequested = false
    private val bridgeDelegate = lazy {
        bridgeFactory(context.applicationContext).apply {
            autoSync = true
            player.setDisplayTranslation(true)
            setOnConnectedListener {
                coroutineScope.launch {
                    if (started && !destroyed && isEnabled() && registrationRequested) {
                        synchronize(playback.snapshot.value)
                    }
                }
            }
        }
    }
    private val bridge by bridgeDelegate
    private val preferences: SharedPreferences by lazy {
        context.applicationContext.getSharedPreferences("nocturn-settings", Context.MODE_PRIVATE)
    }
    private val preferenceListener = SharedPreferences.OnSharedPreferenceChangeListener { _, key ->
        if (key == SettingsStore.KEY_LYRICON_ENABLED || key == null) {
            handleEnabledChanged(settings.lyriconEnabled)
        }
    }
    private var observerJob: Job? = null
    private var lyricsJob: Job? = null
    private var registrationRetryJob: Job? = null
    private var lastSnapshot: PlaybackSnapshot? = null
    // null means loading; EMPTY means the lookup completed without lyrics.
    private var currentLyrics: NocturnLyrics? = null
    private var playbackAnchor: PlaybackState? = null

    override fun start() {
        if (destroyed || started) return
        started = true
        preferences.registerOnSharedPreferenceChangeListener(preferenceListener)
        observerJob = coroutineScope.launch {
            playback.snapshot.collect { snapshot ->
                if (isEnabled()) {
                    requestRegistration()
                    onSnapshotUpdated(snapshot)
                }
            }
        }
    }

    override fun stop() {
        if (!started) return
        started = false
        preferences.unregisterOnSharedPreferenceChangeListener(preferenceListener)
        observerJob?.cancel()
        observerJob = null
        stopPublishing()
    }

    override fun destroy() {
        if (destroyed) return
        stop()
        destroyed = true
        if (bridgeDelegate.isInitialized()) {
            bridge.setOnConnectedListener(null)
            bridge.destroy()
        }
    }

    override fun setEnabled(enabled: Boolean) {
        settings.lyriconEnabled = enabled
        handleEnabledChanged(enabled)
    }

    override fun isEnabled(): Boolean = settings.lyriconEnabled

    private fun handleEnabledChanged(enabled: Boolean) {
        if (destroyed || !started) return
        if (enabled) {
            requestRegistration()
            onSnapshotUpdated(playback.snapshot.value)
        } else {
            stopPublishing()
        }
    }

    private fun requestRegistration() {
        if (registrationRequested || registrationRetryJob?.isActive == true) return
        registrationRequested = bridge.register()
        if (!registrationRequested) {
            // The SDK handles connection timeouts itself; retry here only if no request was sent.
            registrationRetryJob = coroutineScope.launch {
                while (isActive && started && isEnabled() && !registrationRequested) {
                    delay(5_000L)
                    registrationRequested = bridge.register()
                }
                if (registrationRequested) onSnapshotUpdated(playback.snapshot.value)
            }
        }
    }

    private fun stopPublishing() {
        registrationRetryJob?.cancel()
        registrationRetryJob = null
        lyricsJob?.cancel()
        lyricsJob = null
        if (bridgeDelegate.isInitialized() && registrationRequested) {
            // Clear both the remote display and the SDK replay cache before disconnecting.
            bridge.player.setSong(null)
            bridge.player.setPlaybackState(false)
            bridge.unregister()
        }
        registrationRequested = false
        lastSnapshot = null
        currentLyrics = null
        playbackAnchor = null
    }

    private fun onSnapshotUpdated(snapshot: PlaybackSnapshot) {
        if (!started || destroyed || !isEnabled() || !registrationRequested) return
        val previous = lastSnapshot
        lastSnapshot = snapshot
        if (previous == null && snapshot.currentSongId == null) return
        if (previous == null || snapshot.currentSongId != previous.currentSongId) {
            lyricsJob?.cancel()
            lyricsJob = null
            currentLyrics = null
            playbackAnchor = null
            // Publish metadata immediately so old lyrics cannot survive a slow lookup.
            publishContent(snapshot)
            publishPlayback(snapshot)
            snapshot.currentSongId?.let(::loadLyrics)
        } else if (snapshot.currentSongId != null) {
            if (snapshot.title != previous.title || snapshot.artist != previous.artist ||
                snapshot.durationMs != previous.durationMs
            ) {
                publishContent(snapshot)
                publishPlayback(snapshot)
            } else {
                when {
                    snapshot.positionDiscontinuity != previous.positionDiscontinuity -> {
                        bridge.player.seekTo(snapshot.positionMs.coerceAtLeast(0L))
                        publishPlaybackState(snapshot, force = true)
                    }
                    snapshot.positionMs != previous.positionMs ->
                        bridge.player.setPosition(snapshot.positionMs.coerceAtLeast(0L))
                }
                if (snapshot.positionDiscontinuity == previous.positionDiscontinuity) {
                    publishPlaybackState(snapshot)
                }
            }
        }
    }

    private fun loadLyrics(songId: Long) {
        lyricsJob = coroutineScope.launch {
            val lyrics = try {
                repository.lyrics(songId)
            } catch (cancellation: CancellationException) {
                throw cancellation
            } catch (_: Exception) {
                NocturnLyrics.EMPTY
            }
            val snapshot = playback.snapshot.value
            if (isActive && started && !destroyed && isEnabled() && registrationRequested &&
                lastSnapshot?.currentSongId == songId && snapshot.currentSongId == songId
            ) {
                currentLyrics = lyrics
                // A seek, pause or metadata update may have happened during the lookup.
                synchronize(snapshot)
            }
        }
    }

    private fun synchronize(snapshot: PlaybackSnapshot) {
        if (snapshot.currentSongId != lastSnapshot?.currentSongId) {
            onSnapshotUpdated(snapshot)
            return
        }
        publishContent(snapshot)
        publishPlayback(snapshot)
    }

    private fun publishContent(snapshot: PlaybackSnapshot) {
        val songId = snapshot.currentSongId
        val lyrics = currentLyrics
        when {
            songId == null -> bridge.player.setSong(null)
            lyrics != null && isInstrumental(snapshot.title, lyrics) ->
                bridge.player.sendText(LyriconModelMapper.PURE_MUSIC_TEXT)
            lyrics != null && lyrics.lines.none { it.text.isNotBlank() } ->
                bridge.player.sendText(LyriconModelMapper.NO_LYRICS_TEXT)
            else -> bridge.player.setSong(
                LyriconModelMapper.toLyriconSong(
                    id = songId,
                    title = snapshot.title.orEmpty(),
                    artist = snapshot.artist,
                    durationMs = snapshot.durationMs,
                    lyrics = lyrics ?: NocturnLyrics.EMPTY,
                ),
            )
        }
    }

    private fun publishPlayback(snapshot: PlaybackSnapshot) {
        // Shared memory keeps the manual fallback/cache current. PlaybackState drives continuous
        // remote progress between Media3's 500 ms samples, rather than repeating a staircase.
        bridge.player.setPosition(snapshot.positionMs.coerceAtLeast(0L))
        publishPlaybackState(snapshot, force = true)
        bridge.player.setDisplayTranslation(true)
    }

    private fun publishPlaybackState(snapshot: PlaybackSnapshot, force: Boolean = false) {
        val now = SystemClock.elapsedRealtime()
        val sampledAt = (snapshot.positionSampledAtNanos / 1_000_000L)
            .takeIf { it > 0L && it <= now } ?: now
        val playing = snapshot.currentSongId != null && snapshot.playing && !snapshot.buffering
        val state = if (playing) PlaybackState.STATE_PLAYING else PlaybackState.STATE_PAUSED
        val speed = snapshot.playbackSpeed.takeIf { it.isFinite() && it > 0f } ?: 1f
        val position = snapshot.positionMs.coerceAtLeast(0L)
        val anchor = playbackAnchor
        if (!force && anchor != null && anchor.state == state && anchor.playbackSpeed == speed) {
            val elapsed = if (playing) (sampledAt - anchor.lastPositionUpdateTime).coerceAtLeast(0L) else 0L
            val predicted = anchor.position + (elapsed * speed).toLong()
            // Keep a stable remote clock across normal polling jitter. Large drift is corrected;
            // explicit seeks always bypass this threshold and may move backwards immediately.
            if (kotlin.math.abs(position - predicted) <= 750L) return
        }
        val next = PlaybackState.Builder().setState(state, position, speed, sampledAt).build()
        playbackAnchor = next
        bridge.player.setPlaybackState(next)
    }

    companion object {
        internal fun isInstrumental(title: String?, lyrics: NocturnLyrics): Boolean {
            if (title?.trim() == LyriconModelMapper.PURE_MUSIC_TEXT) return true
            if (title?.contains("纯音乐") == true) return true
            val nonBlank = lyrics.lines.filter { it.text.isNotBlank() }
            return nonBlank.isNotEmpty() && nonBlank.all { it.text.trim().startsWith("纯音乐") }
        }
    }
}
