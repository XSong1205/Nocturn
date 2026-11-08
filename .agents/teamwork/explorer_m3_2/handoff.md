# Handoff Report: Milestone 3 — LyriconManager Playback Sync & AppContainer Wiring Design

## 1. Observation

### 1.1 Direct File Observations & Locations
1. **Lyricon Provider SDK API (`io.github.proify.lyricon:provider:0.1.70`)**:
   - As documented in `spec_miner_survey_1/handoff.md` (lines 71–128) from official documentation:
     - Factory: `LyriconFactory.createProvider(context: Context, ...): LyriconProvider`.
     - Lifecycle: `LyriconProvider.register(): Boolean`, `LyriconProvider.unregister()`, `LyriconProvider.destroy()`, and `LyriconProvider.autoSync: Boolean`.
     - Remote player interface (`provider.player: RemotePlayer`):
       - `setSong(song: io.github.proify.lyricon.lyric.model.Song?)`
       - `sendText(text: String?)`
       - `setPlaybackState(playing: Boolean)`
       - `setPosition(position: Long)`
       - `seekTo(position: Long)`
       - `setDisplayTranslation(display: Boolean)`
2. **LyriconModelMapper (`app/src/main/kotlin/dev/nocturn/app/playback/lyricon/LyriconModelMapper.kt`)**:
   - Lines 23–26:
     ```kotlin
     const val PURE_MUSIC_TEXT: String = "纯音乐，请欣赏"
     const val NO_LYRICS_TEXT: String = "暂无歌词"
     ```
   - Lines 45–61:
     ```kotlin
     fun toLyriconSong(
         id: Long,
         title: String,
         artist: String?,
         durationMs: Long,
         lyrics: NocturnLyrics = NocturnLyrics.EMPTY,
     ): LyriconSong
     ```
3. **PlaybackSnapshot & PlaybackEngine (`app/src/main/kotlin/dev/nocturn/app/playback/PlaybackContract.kt`)**:
   - Lines 32–48: `PlaybackSnapshot` contains:
     - `currentSongId: Long? = null`
     - `playing: Boolean = false`
     - `positionMs: Long = 0`
     - `durationMs: Long = 0`
     - `positionDiscontinuity: Long = 0L`
     - Enriched by M3.1 with `title: String? = null` and `artist: String? = null`.
   - Lines 55–56: `PlaybackEngine.snapshot: StateFlow<PlaybackSnapshot>`.
4. **Discontinuity and Progress Updates (`app/src/main/kotlin/dev/nocturn/app/playback/MediaSessionPlaybackEngine.kt`)**:
   - Lines 58–65: `playerListener.onPositionDiscontinuity` increments `positionDiscontinuity++`.
   - Lines 123–144: `startProgressUpdates()` updates `positionMs` every 500ms without incrementing `positionDiscontinuity`.
5. **SettingsStore (`app/src/main/kotlin/dev/nocturn/app/data/SettingsStore.kt`)**:
   - Lines 43–48:
     ```kotlin
     var lyriconEnabled: Boolean
         get() = preferences.getBoolean(KEY_LYRICON_ENABLED, true)
         set(value) {
             preferences.edit().putBoolean(KEY_LYRICON_ENABLED, value).apply()
         }
     ```
   - Line 72: `const val KEY_LYRICON_ENABLED = "lyricon-enabled"` in `internal companion object`.
   - Preferences file name: `"nocturn-settings"`.
6. **MusicRepository (`app/src/main/kotlin/dev/nocturn/app/data/MusicRepository.kt`)**:
   - Line 62: `suspend fun lyrics(songId: Long): Lyrics`.
7. **AppContainer (`app/src/main/kotlin/dev/nocturn/app/NocturnApplication.kt`)**:
   - Lines 15–21:
     ```kotlin
     open class AppContainer(context: Context) {
         open val settings: SettingsStore = SettingsStore(context)
         open val repository: MusicRepository by lazy { LocalMusicRepository(context.applicationContext) }
         open val playback: PlaybackEngine by lazy { MediaSessionPlaybackEngine(context.applicationContext) }
     }
     ```
   - Lines 23–36: `NocturnApplication` holds `var container: AppContainer`.
8. **Pure Music Parsing (`local-api/src/commonMain/kotlin/dev/nocturn/localapi/client/LyricParser.kt`)**:
   - Lines 38–40: NetEase marks instrumental tracks via:
     `lines.any { it.text.trim() == "纯音乐，请欣赏" || it.text.trim() == "纯音乐，请欣赏。" }`

---

## 2. Logic Chain

1. **Step 1 — Provider Lifecycle & AutoSync (R2)**:
   - `LyriconFactory.createProvider(context)` instantiates the provider.
   - Setting `provider.autoSync = true` ensures that when the Lyricon Central Service restarts (e.g. SystemUI reload), cached metadata and playback states are pushed automatically without client intervention.
   - Calling `provider.register()` binds to the central IPC service when enabled; calling `provider.unregister()` cleanly stops status bar updates when disabled or paused; calling `provider.destroy()` tears down binder resources on app shutdown.
2. **Step 2 — Testability Decoupling via Abstractions (R2, R5)**:
   - Directly instantiating `LyriconFactory` in unit tests fails or introduces flaky IPC dependencies.
   - Defining `LyriconPlayerClient` (for playback controls) and `LyriconProviderBridge` (for lifecycle management) with a factory parameter `(Context) -> LyriconProviderBridge` allows 100% offline JVM unit testing using test doubles (`FakeLyriconProviderBridge`), while `DefaultLyriconProviderBridge` wraps real SDK calls in production.
3. **Step 3 — Playback Synchronization Disambiguation (R2)**:
   - Progress ticks (500ms polling) update `snapshot.positionMs` while `snapshot.positionDiscontinuity` stays constant → invoke `player.setPosition(positionMs)`.
   - User scrub / seek triggers Media3 `onPositionDiscontinuity`, incrementing `snapshot.positionDiscontinuity` → invoke `player.seekTo(positionMs)`.
   - Play/pause state changes trigger `player.setPlaybackState(snapshot.playing)`.
4. **Step 4 — Concurrency & Async Lyric Push (R2, R3)**:
   - When `snapshot.currentSongId` changes to a non-null id:
     - Cancel any previous in-flight `lyricsJob`.
     - Push initial `player.setPlaybackState` and `player.setPosition`.
     - Launch coroutine on `coroutineScope`: fetch `repository.lyrics(songId)`.
     - If user skipped songs during fetch (`lastSongId != songId`), safely discard stale result.
     - If network/parsing throws, catch exception and fall back to `Lyrics.EMPTY`.
5. **Step 5 — Pure Music & Placeholder Handling (R3)**:
   - NetEase tracks indicate instrumental status either by title placeholder (`"纯音乐，请欣赏"`) or by lyric lines containing `"纯音乐，请欣赏"`.
   - If instrumental: call `player.sendText(LyriconModelMapper.PURE_MUSIC_TEXT)` (clears timeline and displays pure music text).
   - If normal song (including empty lyrics): map via `LyriconModelMapper.toLyriconSong(id, title, artist, durationMs, lyrics)` and call `player.setSong(lyriconSong)`.
6. **Step 6 — Settings Toggle & SharedPreferences Listener (R4)**:
   - Nocturn stores settings in `"nocturn-settings"` SharedPreferences under `KEY_LYRICON_ENABLED`.
   - `LyriconManager` retains a strong reference to `SharedPreferences.OnSharedPreferenceChangeListener` to avoid Android weak-reference garbage collection.
   - When toggled to `false`: invoke `bridge.unregister()`, cancel active `lyricsJob`, reset state.
   - When toggled to `true`: invoke `bridge.register()`, re-read current `playback.snapshot` and immediately synchronize.
7. **Step 7 — AppContainer Wiring**:
   - `AppContainer` exposes `open val lyriconManager: LyriconManager by lazy { ... }`.
   - `NocturnApplication.onCreate()` invokes `container.lyriconManager.start()` to eagerly begin observation.

---

## 3. Caveats

1. **`sendText` Side Effects in Lyricon SDK**:
   - Calling `sendText` transitions Lyricon into plain-text mode and clears cached `Song` metadata. When switching back to a lyrical track, `setSong(...)` must supply full metadata (`id`, `name`, `artist`, `duration`, `lyrics`), which `LyriconModelMapper.toLyriconSong` guarantees.
2. **Android Weak-Reference SharedPreferences Listener**:
   - `SharedPreferences.registerOnSharedPreferenceChangeListener` does not hold a strong reference. The listener MUST be an instance field in `LyriconManager` to prevent premature garbage collection.
3. **Robolectric / Unit Test Isolation**:
   - In unit tests, `bridgeFactory` must default to returning `FakeLyriconProviderBridge` or be explicitly passed to bypass Android IPC calls.

---

## 4. Conclusion & Concrete Code Specifications

### 4.1 Specification for `LyriconManager.kt`
Target path: `app/src/main/kotlin/dev/nocturn/app/playback/lyricon/LyriconManager.kt`

```kotlin
package dev.nocturn.app.playback.lyricon

import android.content.Context
import android.content.SharedPreferences
import dev.nocturn.app.data.MusicRepository
import dev.nocturn.app.data.SettingsStore
import dev.nocturn.app.data.model.Lyrics as NocturnLyrics
import dev.nocturn.app.playback.PlaybackEngine
import dev.nocturn.app.playback.PlaybackSnapshot
import io.github.proify.lyricon.lyric.model.Song as LyriconSong
import io.github.proify.lyricon.provider.LyriconFactory
import io.github.proify.lyricon.provider.LyriconProvider
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.flow.collect
import kotlinx.coroutines.launch
import java.util.concurrent.atomic.AtomicBoolean

/**
 * High-level bridge contract for controlling the Lyricon Provider integration.
 */
interface LyriconBridge {
    fun start()
    fun stop()
    fun destroy()
    fun setEnabled(enabled: Boolean)
    fun isEnabled(): Boolean
}

/**
 * Abstraction over Lyricon's player control, decoupling playback push from the SDK's RemotePlayer.
 * Allows pure unit testing without IPC or Android framework dependencies.
 */
interface LyriconPlayerClient {
    fun setSong(song: LyriconSong?)
    fun sendText(text: String?)
    fun setPlaybackState(playing: Boolean)
    fun setPosition(positionMs: Long)
    fun seekTo(positionMs: Long)
    fun setDisplayTranslation(display: Boolean)
}

/**
 * Abstraction over Lyricon Provider lifecycle and remote player access.
 */
interface LyriconProviderBridge {
    val player: LyriconPlayerClient
    var autoSync: Boolean
    fun register(): Boolean
    fun unregister()
    fun destroy()
}

/**
 * Production implementation wrapping the real [LyriconProvider].
 */
class DefaultLyriconProviderBridge(
    private val provider: LyriconProvider,
) : LyriconProviderBridge {
    override val player: LyriconPlayerClient = object : LyriconPlayerClient {
        override fun setSong(song: LyriconSong?) = provider.player.setSong(song)
        override fun sendText(text: String?) = provider.player.sendText(text)
        override fun setPlaybackState(playing: Boolean) = provider.player.setPlaybackState(playing)
        override fun setPosition(positionMs: Long) = provider.player.setPosition(positionMs)
        override fun seekTo(positionMs: Long) = provider.player.seekTo(positionMs)
        override fun setDisplayTranslation(display: Boolean) = provider.player.setDisplayTranslation(display)
    }

    override var autoSync: Boolean
        get() = provider.autoSync
        set(value) { provider.autoSync = value }

    override fun register(): Boolean = provider.register()

    override fun unregister() = provider.unregister()

    override fun destroy() = provider.destroy()
}

/**
 * Coordinates playback state, song metadata, and asynchronous lyrics loading with Lyricon status bar service.
 */
class LyriconManager(
    private val context: Context,
    private val playback: PlaybackEngine,
    private val repository: MusicRepository,
    private val settings: SettingsStore,
    private val coroutineScope: CoroutineScope = CoroutineScope(SupervisorJob() + Dispatchers.Main.immediate),
    private val bridgeFactory: (Context) -> LyriconProviderBridge = { ctx ->
        DefaultLyriconProviderBridge(LyriconFactory.createProvider(ctx))
    },
) : LyriconBridge {

    private val started = AtomicBoolean(false)
    private val destroyed = AtomicBoolean(false)

    @Volatile
    private var isRegistered = false

    private val bridge: LyriconProviderBridge by lazy {
        bridgeFactory(context).apply {
            autoSync = true
            player.setDisplayTranslation(true)
        }
    }

    private val sharedPreferences: SharedPreferences? by lazy {
        runCatching {
            context.applicationContext.getSharedPreferences("nocturn-settings", Context.MODE_PRIVATE)
        }.getOrNull()
    }

    private val preferenceListener = SharedPreferences.OnSharedPreferenceChangeListener { _, key ->
        if (key == SettingsStore.KEY_LYRICON_ENABLED || key == null) {
            handleEnabledChanged(settings.lyriconEnabled)
        }
    }

    private var observerJob: Job? = null
    private var lyricsJob: Job? = null

    // Tracked playback state across snapshot emissions
    private var lastSongId: Long? = null
    private var lastTitle: String? = null
    private var lastArtist: String? = null
    private var lastPlaying: Boolean? = null
    private var lastPositionMs: Long? = null
    private var lastDiscontinuity: Long = 0L

    override fun start() {
        if (!started.compareAndSet(false, true)) return
        if (destroyed.get()) return

        sharedPreferences?.registerOnSharedPreferenceChangeListener(preferenceListener)

        if (settings.lyriconEnabled) {
            registerBridge()
        }

        observerJob = coroutineScope.launch {
            playback.snapshot.collect { snapshot ->
                onSnapshotUpdated(snapshot)
            }
        }
    }

    override fun stop() {
        if (!started.compareAndSet(true, false)) return
        sharedPreferences?.unregisterOnSharedPreferenceChangeListener(preferenceListener)
        observerJob?.cancel()
        observerJob = null
        lyricsJob?.cancel()
        lyricsJob = null
        unregisterBridge()
    }

    override fun destroy() {
        if (!destroyed.compareAndSet(false, true)) return
        stop()
        bridge.destroy()
    }

    override fun setEnabled(enabled: Boolean) {
        if (settings.lyriconEnabled != enabled) {
            settings.lyriconEnabled = enabled
        }
        handleEnabledChanged(enabled)
    }

    override fun isEnabled(): Boolean = settings.lyriconEnabled

    private fun handleEnabledChanged(enabled: Boolean) {
        if (destroyed.get() || !started.get()) return
        if (enabled) {
            registerBridge()
            // Resynchronize immediately with the latest snapshot
            val currentSnapshot = playback.snapshot.value
            resetTrackedState()
            onSnapshotUpdated(currentSnapshot)
        } else {
            unregisterBridge()
            lyricsJob?.cancel()
            lyricsJob = null
            resetTrackedState()
        }
    }

    private fun registerBridge() {
        if (!isRegistered) {
            bridge.register()
            isRegistered = true
        }
    }

    private fun unregisterBridge() {
        if (isRegistered) {
            bridge.unregister()
            isRegistered = false
        }
    }

    private fun resetTrackedState() {
        lastSongId = null
        lastTitle = null
        lastArtist = null
        lastPlaying = null
        lastPositionMs = null
        lastDiscontinuity = 0L
    }

    private fun onSnapshotUpdated(snapshot: PlaybackSnapshot) {
        if (!isEnabled() || !isRegistered) return

        val songId = snapshot.currentSongId
        val songChanged = songId != lastSongId
        val metadataChanged = snapshot.title != lastTitle || snapshot.artist != lastArtist
        val playingChanged = snapshot.playing != lastPlaying
        val discontinuityChanged = snapshot.positionDiscontinuity != lastDiscontinuity
        val positionChanged = snapshot.positionMs != lastPositionMs

        if (songChanged) {
            lastSongId = songId
            lastTitle = snapshot.title
            lastArtist = snapshot.artist
            lastDiscontinuity = snapshot.positionDiscontinuity
            lastPositionMs = snapshot.positionMs
            lastPlaying = snapshot.playing

            if (songId == null) {
                lyricsJob?.cancel()
                lyricsJob = null
                bridge.player.setSong(null)
                bridge.player.setPlaybackState(false)
            } else {
                bridge.player.setPlaybackState(snapshot.playing)
                bridge.player.setPosition(snapshot.positionMs)
                loadLyricsAndPush(songId, snapshot)
            }
        } else if (songId != null) {
            if (metadataChanged) {
                lastTitle = snapshot.title
                lastArtist = snapshot.artist
            }

            if (playingChanged) {
                lastPlaying = snapshot.playing
                bridge.player.setPlaybackState(snapshot.playing)
            }

            if (discontinuityChanged) {
                lastDiscontinuity = snapshot.positionDiscontinuity
                lastPositionMs = snapshot.positionMs
                bridge.player.seekTo(snapshot.positionMs)
            } else if (positionChanged) {
                lastPositionMs = snapshot.positionMs
                bridge.player.setPosition(snapshot.positionMs)
            }
        }
    }

    private fun loadLyricsAndPush(songId: Long, snapshot: PlaybackSnapshot) {
        lyricsJob?.cancel()
        lyricsJob = coroutineScope.launch {
            val lyrics = try {
                repository.lyrics(songId)
            } catch (cancellation: CancellationException) {
                throw cancellation
            } catch (_: Exception) {
                NocturnLyrics.EMPTY
            }

            if (lastSongId == songId && isEnabled() && isRegistered) {
                applySongAndLyrics(songId, snapshot, lyrics)
            }
        }
    }

    private fun applySongAndLyrics(
        songId: Long,
        snapshot: PlaybackSnapshot,
        lyrics: NocturnLyrics,
    ) {
        if (isInstrumental(snapshot.title, lyrics)) {
            bridge.player.sendText(LyriconModelMapper.PURE_MUSIC_TEXT)
        } else {
            val title = snapshot.title?.ifBlank { null } ?: "未知曲目"
            val artist = snapshot.artist?.ifBlank { null }
            val durationMs = snapshot.durationMs.coerceAtLeast(0L)
            val lyriconSong = LyriconModelMapper.toLyriconSong(
                id = songId,
                title = title,
                artist = artist,
                durationMs = durationMs,
                lyrics = lyrics,
            )
            bridge.player.setSong(lyriconSong)
        }
    }

    companion object {
        internal fun isInstrumental(title: String?, lyrics: NocturnLyrics): Boolean {
            if (title?.trim() == LyriconModelMapper.PURE_MUSIC_TEXT) return true
            if (lyrics.isEmpty) return false
            val nonBlank = lyrics.lines.filter { it.text.isNotBlank() }
            if (nonBlank.isEmpty()) return false
            return nonBlank.all { line ->
                val text = line.text.trim()
                text == LyriconModelMapper.PURE_MUSIC_TEXT ||
                    text == "纯音乐，请欣赏。" ||
                    text == "纯音乐，请安静欣赏" ||
                    text.startsWith("纯音乐")
            }
        }
    }
}
```

### 4.2 Specification for `AppContainer` & `NocturnApplication.kt` Wiring
Target path: `app/src/main/kotlin/dev/nocturn/app/NocturnApplication.kt`

```kotlin
package dev.nocturn.app

import android.app.Application
import android.content.Context
import dev.nocturn.app.data.LocalMusicRepository
import dev.nocturn.app.data.MusicRepository
import dev.nocturn.app.data.SettingsStore
import dev.nocturn.app.playback.MediaSessionPlaybackEngine
import dev.nocturn.app.playback.PlaybackEngine
import dev.nocturn.app.playback.lyricon.LyriconManager

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
```

### 4.3 Specification for Test Doubles
For use in `app/src/test/kotlin/dev/nocturn/app/playback/lyricon/LyriconManagerTest.kt`:

```kotlin
class FakeLyriconPlayerClient : LyriconPlayerClient {
    var lastSong: LyriconSong? = null
    var lastSentText: String? = null
    var isPlaying: Boolean? = null
    var currentPositionMs: Long? = null
    val seekCalls = mutableListOf<Long>()
    var displayTranslation: Boolean? = null

    override fun setSong(song: LyriconSong?) {
        lastSong = song
        lastSentText = null
    }

    override fun sendText(text: String?) {
        lastSentText = text
        lastSong = null
    }

    override fun setPlaybackState(playing: Boolean) {
        isPlaying = playing
    }

    override fun setPosition(positionMs: Long) {
        currentPositionMs = positionMs
    }

    override fun seekTo(positionMs: Long) {
        seekCalls.add(positionMs)
        currentPositionMs = positionMs
    }

    override fun setDisplayTranslation(display: Boolean) {
        displayTranslation = display
    }
}

class FakeLyriconProviderBridge : LyriconProviderBridge {
    override val player = FakeLyriconPlayerClient()
    override var autoSync: Boolean = false
    var isRegistered: Boolean = false
    var isDestroyed: Boolean = false
    var registerCount: Int = 0
    var unregisterCount: Int = 0

    override fun register(): Boolean {
        isRegistered = true
        registerCount++
        return true
    }

    override fun unregister() {
        isRegistered = false
        unregisterCount++
    }

    override fun destroy() {
        isDestroyed = true
    }
}
```

---

## 5. Verification Method

To verify the implementation once executed by Worker:
1. **Compilation & Static Checks**:
   ```powershell
   .\gradlew.bat :app:assembleDebug :app:lintDebug
   ```
   Ensures `LyriconManager.kt` and `NocturnApplication.kt` compile cleanly with 0 lint violations.
2. **Unit Test Execution**:
   ```powershell
   .\gradlew.bat cleanTestDebugUnitTest :app:testDebugUnitTest --no-daemon --tests "dev.nocturn.app.playback.lyricon.LyriconManagerTest"
   ```
   Validates:
   - Initial registration and `autoSync = true` configuration.
   - Settings toggle unregistering / registering the bridge.
   - Play/pause state mirroring (`setPlaybackState`).
   - Progress tick position sync (`setPosition`).
   - Discontinuity seek scrub sync (`seekTo`).
   - Song change and async lyric loading.
   - Pure music placeholder routing to `sendText("纯音乐，请欣赏")`.
   - Empty lyrics graceful fallback without crash.
   - Resource cleanup on `destroy()`.
