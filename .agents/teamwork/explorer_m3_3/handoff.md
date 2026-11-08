# Handoff Report: M3 AppContainer Wiring & LyriconManager Test Suite Design

## 1. Observation

### 1.1 `NocturnApplication.kt` & `AppContainer` Structure
From `app/src/main/kotlin/dev/nocturn/app/NocturnApplication.kt` (lines 15–37):
```kotlin
open class AppContainer(context: Context) {
    open val settings: SettingsStore = SettingsStore(context)

    open val repository: MusicRepository by lazy { LocalMusicRepository(context.applicationContext) }

    open val playback: PlaybackEngine by lazy { MediaSessionPlaybackEngine(context.applicationContext) }
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
}
```
- Direct observation 1: `AppContainer` is process-scoped and holds singletons of `settings`, `repository`, and `playback`.
- Direct observation 2: `AppContainer` uses `by lazy` for heavy/lifecycle components (`repository`, `playback`).
- Direct observation 3: `NocturnApplication` currently does not override `onCreate()`.
- Direct observation 4: In Robolectric test suites (`SettingsViewModelTest.kt:47-51`), test classes override `app.container` with custom subclasses:
  ```kotlin
  val app = RuntimeEnvironment.getApplication() as NocturnApplication
  app.container = object : AppContainer(app) {
      override val settings = SettingsStore(app)
      override val repository: MusicRepository = FakeMusicRepository()
      override val playback: PlaybackEngine = FakePlaybackEngine()
  }
  ```
  `app.container` can be reassigned during tests, but `NocturnApplication.onCreate()` is invoked by Robolectric upon test harness startup.

### 1.2 `SettingsStore.kt` & `NocturnViewModel.kt`
From `app/src/main/kotlin/dev/nocturn/app/data/SettingsStore.kt` (lines 44–48, 72):
```kotlin
    /** Status bar lyric provider integration (Lyricon); on by default. */
    var lyriconEnabled: Boolean
        get() = preferences.getBoolean(KEY_LYRICON_ENABLED, true)
        set(value) {
            preferences.edit().putBoolean(KEY_LYRICON_ENABLED, value).apply()
        }
    ...
    internal companion object {
        const val KEY_LYRICON_ENABLED = "lyricon-enabled"
    }
```
From `app/src/main/kotlin/dev/nocturn/app/NocturnViewModel.kt` (lines 976–980):
```kotlin
    fun setLyriconEnabled(enabled: Boolean) {
        settings.lyriconEnabled = enabled
        mutableLibrary.update { it.copy(lyriconEnabled = enabled) }
        mutableSettings.update { it.copy(lyriconEnabled = enabled) }
    }
```
- Direct observation 5: `lyriconEnabled` is stored in SharedPreferences `"nocturn-settings"`, defaulting to `true`.
- Direct observation 6: `SettingsStore` writes via `.apply()`, broadcasting changes to registered `SharedPreferences.OnSharedPreferenceChangeListener`.

### 1.3 `PlaybackContract.kt` & `MediaSessionPlaybackEngine.kt`
From `app/src/main/kotlin/dev/nocturn/app/playback/PlaybackContract.kt` (lines 32–48):
- `PlaybackSnapshot` contains `currentSongId`, `playing`, `buffering`, `positionMs`, `durationMs`, and `positionDiscontinuity`.
- `positionDiscontinuity: Long` is incremented whenever a seek, repeat, or item transition occurs (`MediaSessionPlaybackEngine.kt:59-65`).
- Progress updates are emitted via `startProgressUpdates()` every 500ms (`delay(PROGRESS_UPDATE_INTERVAL_MS)`).

### 1.4 `LyriconModelMapper.kt`
From `app/src/main/kotlin/dev/nocturn/app/playback/lyricon/LyriconModelMapper.kt`:
- `const val PURE_MUSIC_TEXT: String = "纯音乐，请欣赏"`
- `const val NO_LYRICS_TEXT: String = "暂无歌词"`
- `toLyriconSong(id, title, artist, durationMs, lyrics): LyriconSong` maps song metadata and rich lyrics into Lyricon's model.
- `toLyriconLines(lyrics, songDurationMs): List<LyriconRichLine>` handles begin/end times, YRC words, and translations.

---

## 2. Logic Chain

1. **Need for Process-Level Hosting (`AppContainer`)**:
   - `LyriconManager` must push status bar lyrics continuously when Nocturn is playing in background and `MainActivity` is closed (`NocturnViewModel.onCleared()` is called when UI exits, but `PlaybackService` stays alive).
   - Therefore, `LyriconManager` belongs inside `AppContainer`, which lives for the entire Android process.

2. **Wiring and Lifecycle in `AppContainer` and `NocturnApplication.onCreate()`**:
   - `AppContainer` exposes `open val lyriconManager: LyriconBridge by lazy { LyriconManager(...) }`.
   - `NocturnApplication.onCreate()` calls `container.lyriconManager.start()`.
   - Calling `start()` in `onCreate()` guarantees that whenever Nocturn launches (either via UI or background playback start), `LyriconManager` begins observing `playback.snapshot` and `settings`.
   - To ensure Robolectric compatibility across all test suites, `DefaultLyriconClient` wraps `LyriconFactory.createProvider(context)` with `runCatching`, making it safely no-op in headless test runners.

3. **Decoupled Testability via `LyriconClient`**:
   - Direct calls to `LyriconProvider` and `RemotePlayer` require an active IPC connection to Lyricon Central Service.
   - Introducing `interface LyriconClient` (implemented by `DefaultLyriconClient` in production) allows `LyriconManagerTest` to inject a `FakeLyriconClient`.
   - The test suite can assert every call (`register`, `unregister`, `destroy`, `setPlaybackState`, `setSong`, `sendText`, `setPosition`, `seekTo`, `setDisplayTranslation`) deterministically without mocking frameworks.

4. **Dynamic Settings & SharedPreference Observability**:
   - When the user toggles Lyricon in Settings (`NocturnViewModel.setLyriconEnabled`), `SettingsStore` updates `KEY_LYRICON_ENABLED` in SharedPreferences.
   - `LyriconManager` listens via `SharedPreferences.OnSharedPreferenceChangeListener` as well as providing explicit `setEnabled(Boolean)`.
   - When disabled: unregisters from central service and halts updates.
   - When enabled: registers and resynchronizes current playback state immediately if a song is playing.

5. **Discontinuity Detection for `seekTo` vs `setPosition`**:
   - Continuous 500ms progress ticks maintain `positionDiscontinuity == lastDiscontinuity` -> invokes `client.setPosition(snapshot.positionMs)`.
   - When the user scrubs or jumps position, `positionDiscontinuity` changes -> invokes `client.seekTo(snapshot.positionMs)` and updates `lastDiscontinuity`.

6. **Pure Music & Failure Fallback**:
   - If `repository.lyrics(songId)` returns empty lines or instrumental text, `LyriconManager` calls `client.sendText(LyriconModelMapper.PURE_MUSIC_TEXT)`.
   - If `repository.lyrics(songId)` throws an exception (network failure), it falls back to `LyriconModelMapper.NO_LYRICS_TEXT` or placeholder without crashing.

---

## 3. Caveats

1. **Robolectric Application Lifecycle**:
   - In Robolectric, `NocturnApplication.onCreate()` runs before test methods. Because `DefaultLyriconClient` uses `runCatching`, `LyriconFactory.createProvider(context)` does not crash or throw in tests. Furthermore, tests can inject custom `AppContainer` instances via `app.container = ...`.
2. **Snapshot Metadata Enrichment (`title` & `artist`)**:
   - In parallel, Explorer 1 (`explorer_m3_1`) is adding `val title: String? = null` and `val artist: String? = null` to `PlaybackSnapshot`. In `LyriconManager`, fallback to `"未知曲目"` and `null` artist is provided if metadata is unset.
3. **IPC Registration Return Value**:
   - On devices without Lyricon or LSPosed installed, `LyriconProvider.register()` returns `false`. `LyriconManager` records registration intent without crashing or spinning in retries.

---

## 4. Conclusion & Complete Code Specifications

### 4.1 File 1: `AppContainer` Wiring in `app/src/main/kotlin/dev/nocturn/app/NocturnApplication.kt`

```kotlin
package dev.nocturn.app

import android.app.Application
import android.content.Context
import dev.nocturn.app.data.LocalMusicRepository
import dev.nocturn.app.data.MusicRepository
import dev.nocturn.app.data.SettingsStore
import dev.nocturn.app.playback.MediaSessionPlaybackEngine
import dev.nocturn.app.playback.PlaybackEngine
import dev.nocturn.app.playback.lyricon.LyriconBridge
import dev.nocturn.app.playback.lyricon.LyriconManager

/**
 * Application-scoped dependency container. The embedded Node runtime is process-owned, so the
 * repository (and its HTTP client) and the media session connection live for the whole process.
 */
open class AppContainer(context: Context) {
    open val settings: SettingsStore = SettingsStore(context)

    open val repository: MusicRepository by lazy { LocalMusicRepository(context.applicationContext) }

    open val playback: PlaybackEngine by lazy { MediaSessionPlaybackEngine(context.applicationContext) }

    open val lyriconManager: LyriconBridge by lazy {
        LyriconManager(
            context = context.applicationContext,
            repository = repository,
            playback = playback,
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

---

### 4.2 File 2: `LyriconClient.kt` in `app/src/main/kotlin/dev/nocturn/app/playback/lyricon/`

```kotlin
package dev.nocturn.app.playback.lyricon

import android.content.Context
import io.github.proify.lyricon.provider.LyriconFactory
import io.github.proify.lyricon.provider.LyriconProvider
import io.github.proify.lyricon.lyric.model.Song as LyriconSong

/**
 * Abstraction over Lyricon Provider SDK ([LyriconProvider] and [io.github.proify.lyricon.provider.RemotePlayer])
 * allowing deterministic unit and Robolectric testing.
 */
interface LyriconClient {
    val isRegistered: Boolean
    fun register(): Boolean
    fun unregister()
    fun destroy()
    fun setPlaybackState(playing: Boolean)
    fun setSong(song: LyriconSong?)
    fun sendText(text: String?)
    fun setPosition(positionMs: Long)
    fun seekTo(positionMs: Long)
    fun setDisplayTranslation(display: Boolean)
}

open class DefaultLyriconClient(
    context: Context,
) : LyriconClient {
    private val provider: LyriconProvider? = runCatching {
        LyriconFactory.createProvider(context).apply {
            autoSync = true
        }
    }.getOrNull()

    private var registered = false

    override val isRegistered: Boolean
        get() = registered

    override fun register(): Boolean {
        registered = provider?.register() ?: false
        return registered
    }

    override fun unregister() {
        provider?.unregister()
        registered = false
    }

    override fun destroy() {
        provider?.destroy()
        registered = false
    }

    override fun setPlaybackState(playing: Boolean) {
        provider?.player?.setPlaybackState(playing)
    }

    override fun setSong(song: LyriconSong?) {
        provider?.player?.setSong(song)
    }

    override fun sendText(text: String?) {
        provider?.player?.sendText(text)
    }

    override fun setPosition(positionMs: Long) {
        provider?.player?.setPosition(positionMs)
    }

    override fun seekTo(positionMs: Long) {
        provider?.player?.seekTo(positionMs)
    }

    override fun setDisplayTranslation(display: Boolean) {
        provider?.player?.setDisplayTranslation(display)
    }
}
```

---

### 4.3 File 3: `LyriconManager.kt` in `app/src/main/kotlin/dev/nocturn/app/playback/lyricon/`

```kotlin
package dev.nocturn.app.playback.lyricon

import android.content.Context
import android.content.SharedPreferences
import dev.nocturn.app.data.MusicRepository
import dev.nocturn.app.data.SettingsStore
import dev.nocturn.app.data.model.Lyrics
import dev.nocturn.app.playback.PlaybackEngine
import dev.nocturn.app.playback.PlaybackSnapshot
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.launch

interface LyriconBridge {
    fun start()
    fun stop()
    fun setEnabled(enabled: Boolean)
    fun isEnabled(): Boolean
}

open class LyriconManager(
    private val context: Context,
    private val repository: MusicRepository,
    private val playback: PlaybackEngine,
    private val settings: SettingsStore,
    private val coroutineScope: CoroutineScope = CoroutineScope(SupervisorJob() + Dispatchers.Main.immediate),
    private val client: LyriconClient = DefaultLyriconClient(context),
) : LyriconBridge {

    @Volatile
    private var started = false

    @Volatile
    private var enabled = settings.lyriconEnabled

    private var observerJob: Job? = null
    private var lyricJob: Job? = null

    private var currentSongId: Long? = null
    private var lastDiscontinuity: Long = 0L

    private val prefListener = SharedPreferences.OnSharedPreferenceChangeListener { _, key ->
        if (key == SettingsStore.KEY_LYRICON_ENABLED) {
            val nextEnabled = settings.lyriconEnabled
            if (nextEnabled != enabled) {
                setEnabled(nextEnabled)
            }
        }
    }

    private val preferences: SharedPreferences by lazy {
        context.getSharedPreferences("nocturn-settings", Context.MODE_PRIVATE)
    }

    override fun isEnabled(): Boolean = enabled

    @Synchronized
    override fun start() {
        if (started) return
        started = true
        enabled = settings.lyriconEnabled

        preferences.registerOnSharedPreferenceChangeListener(prefListener)

        if (enabled) {
            client.register()
        }

        observerJob = coroutineScope.launch {
            playback.snapshot.collect { snapshot ->
                onSnapshotChanged(snapshot)
            }
        }
    }

    @Synchronized
    override fun stop() {
        if (!started) return
        started = false

        preferences.unregisterOnSharedPreferenceChangeListener(prefListener)
        observerJob?.cancel()
        observerJob = null
        lyricJob?.cancel()
        lyricJob = null

        client.unregister()
        client.destroy()
        currentSongId = null
    }

    @Synchronized
    override fun setEnabled(enabled: Boolean) {
        if (this.enabled == enabled) return
        this.enabled = enabled

        if (enabled) {
            client.register()
            val snapshot = playback.snapshot.value
            currentSongId = null
            onSnapshotChanged(snapshot)
        } else {
            lyricJob?.cancel()
            lyricJob = null
            currentSongId = null
            client.unregister()
        }
    }

    private fun onSnapshotChanged(snapshot: PlaybackSnapshot) {
        if (!started || !enabled) return

        val newSongId = snapshot.currentSongId
        if (newSongId != currentSongId) {
            currentSongId = newSongId
            lastDiscontinuity = snapshot.positionDiscontinuity
            if (newSongId == null) {
                lyricJob?.cancel()
                lyricJob = null
                client.setSong(null)
            } else {
                loadAndPushSong(newSongId, snapshot)
            }
            return
        }

        if (newSongId == null) return

        client.setPlaybackState(snapshot.playing)

        if (snapshot.positionDiscontinuity != lastDiscontinuity) {
            lastDiscontinuity = snapshot.positionDiscontinuity
            client.seekTo(snapshot.positionMs)
        } else {
            client.setPosition(snapshot.positionMs)
        }
    }

    private fun loadAndPushSong(songId: Long, snapshot: PlaybackSnapshot) {
        lyricJob?.cancel()
        lyricJob = coroutineScope.launch {
            try {
                val lyrics = try {
                    repository.lyrics(songId)
                } catch (e: CancellationException) {
                    throw e
                } catch (e: Throwable) {
                    Lyrics.EMPTY
                }

                if (songId != currentSongId || !enabled || !started) return@launch

                if (lyrics.lines.isEmpty()) {
                    client.sendText(LyriconModelMapper.PURE_MUSIC_TEXT)
                } else {
                    val lyriconSong = LyriconModelMapper.toLyriconSong(
                        id = songId,
                        title = snapshot.title ?: "未知曲目",
                        artist = snapshot.artist,
                        durationMs = snapshot.durationMs,
                        lyrics = lyrics,
                    )
                    client.setSong(lyriconSong)
                    client.setPosition(snapshot.positionMs)
                    client.setPlaybackState(snapshot.playing)
                    if (lyrics.lines.any { !it.translation.isNullOrBlank() }) {
                        client.setDisplayTranslation(true)
                    }
                }
            } catch (c: CancellationException) {
                throw c
            } catch (e: Throwable) {
                if (songId == currentSongId && enabled && started) {
                    client.sendText(LyriconModelMapper.NO_LYRICS_TEXT)
                }
            }
        }
    }
}
```

---

### 4.4 File 4: `LyriconManagerTest.kt` in `app/src/test/kotlin/dev/nocturn/app/playback/lyricon/`

```kotlin
package dev.nocturn.app.playback.lyricon

import android.content.Context
import dev.nocturn.app.AppContainer
import dev.nocturn.app.NocturnApplication
import dev.nocturn.app.data.MusicRepository
import dev.nocturn.app.data.SettingsStore
import dev.nocturn.app.data.model.AccountProfile
import dev.nocturn.app.data.model.AlbumDetail
import dev.nocturn.app.data.model.ArtistDetail
import dev.nocturn.app.data.model.HomeFeed
import dev.nocturn.app.data.model.LyricLine as NocturnLyricLine
import dev.nocturn.app.data.model.LyricWord as NocturnLyricWord
import dev.nocturn.app.data.model.Lyrics as NocturnLyrics
import dev.nocturn.app.data.model.PlaylistDetail
import dev.nocturn.app.data.model.PlaylistSongsPage
import dev.nocturn.app.data.model.SearchAlbumsPage
import dev.nocturn.app.data.model.SearchArtistsPage
import dev.nocturn.app.data.model.SearchPlaylistsPage
import dev.nocturn.app.data.model.SearchSongsPage
import dev.nocturn.app.playback.PlayableSong
import dev.nocturn.app.playback.PlaybackEngine
import dev.nocturn.app.playback.PlaybackSnapshot
import dev.nocturn.app.playback.RepeatMode
import io.github.proify.lyricon.lyric.model.Song as LyriconSong
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.test.TestScope
import kotlinx.coroutines.test.UnconfinedTestDispatcher
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.RuntimeEnvironment
import org.robolectric.annotation.Config
import java.io.IOException

@OptIn(ExperimentalCoroutinesApi::class)
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [36])
class LyriconManagerTest {

    private lateinit var context: Context
    private lateinit var settings: SettingsStore
    private lateinit var fakeClient: FakeLyriconClient
    private lateinit var fakeRepository: FakeMusicRepository
    private lateinit var fakePlayback: FakePlaybackEngine

    @Before
    fun setUp() {
        context = RuntimeEnvironment.getApplication()
        settings = SettingsStore(context).apply {
            lyriconEnabled = true
        }
        fakeClient = FakeLyriconClient()
        fakeRepository = FakeMusicRepository()
        fakePlayback = FakePlaybackEngine()
    }

    private fun createManager(
        testScope: TestScope,
        initialEnabled: Boolean = true,
    ): LyriconManager {
        settings.lyriconEnabled = initialEnabled
        return LyriconManager(
            context = context,
            repository = fakeRepository,
            playback = fakePlayback,
            settings = settings,
            coroutineScope = testScope,
            client = fakeClient,
        )
    }

    // -----------------------------------------------------------------------------------------
    // 1. Registration & Lifecycle Tests
    // -----------------------------------------------------------------------------------------

    @Test
    fun registersClientWhenStartedWithLyriconEnabled() = runTest(UnconfinedTestDispatcher()) {
        val manager = createManager(this, initialEnabled = true)

        manager.start()

        assertTrue(manager.isEnabled())
        assertTrue(fakeClient.isRegistered)
        assertEquals(1, fakeClient.registerCount)
    }

    @Test
    fun doesNotRegisterClientWhenStartedWithLyriconDisabled() = runTest(UnconfinedTestDispatcher()) {
        val manager = createManager(this, initialEnabled = false)

        manager.start()

        assertFalse(manager.isEnabled())
        assertFalse(fakeClient.isRegistered)
        assertEquals(0, fakeClient.registerCount)
    }

    @Test
    fun unregistersClientWhenDisabledViaSetEnabled() = runTest(UnconfinedTestDispatcher()) {
        val manager = createManager(this, initialEnabled = true)
        manager.start()
        assertEquals(1, fakeClient.registerCount)

        manager.setEnabled(false)

        assertFalse(manager.isEnabled())
        assertFalse(fakeClient.isRegistered)
        assertEquals(1, fakeClient.unregisterCount)
    }

    @Test
    fun registersAndResynchronizesCurrentSongWhenEnabledViaSetEnabled() = runTest(UnconfinedTestDispatcher()) {
        fakeRepository.stubLyrics(1001L, NocturnLyrics(listOf(NocturnLyricLine(0L, 2000L, "测试"))))
        val manager = createManager(this, initialEnabled = false)
        manager.start()

        // Emitting playback snapshot while disabled
        fakePlayback.emitSnapshot(
            PlaybackSnapshot(
                currentSongId = 1001L,
                title = "测试歌曲",
                artist = "歌手",
                durationMs = 2000L,
                playing = true,
                positionMs = 500L,
            )
        )
        // No client calls while disabled
        assertEquals(0, fakeClient.songs.size)

        // Enable
        manager.setEnabled(true)

        assertTrue(manager.isEnabled())
        assertEquals(1, fakeClient.registerCount)
        assertEquals(1, fakeClient.songs.size)
        assertEquals("1001", fakeClient.songs.last()?.id)
        assertEquals(true, fakeClient.playbackStates.lastOrNull())
    }

    @Test
    fun stopUnregistersAndDestroysClient() = runTest(UnconfinedTestDispatcher()) {
        val manager = createManager(this, initialEnabled = true)
        manager.start()

        manager.stop()

        assertFalse(fakeClient.isRegistered)
        assertEquals(1, fakeClient.unregisterCount)
        assertEquals(1, fakeClient.destroyCount)
    }

    // -----------------------------------------------------------------------------------------
    // 2. Play/Pause State Synchronization Tests
    // -----------------------------------------------------------------------------------------

    @Test
    fun synchronizesPlayingStateTransitions() = runTest(UnconfinedTestDispatcher()) {
        fakeRepository.stubLyrics(1L, NocturnLyrics(listOf(NocturnLyricLine(0L, 1000L, "Line"))))
        val manager = createManager(this, initialEnabled = true)
        manager.start()

        // Track starts playing
        fakePlayback.emitSnapshot(
            PlaybackSnapshot(currentSongId = 1L, title = "Song", playing = true, positionMs = 0L)
        )
        assertEquals(listOf(true), fakeClient.playbackStates)

        // Paused
        fakePlayback.emitSnapshot(
            PlaybackSnapshot(currentSongId = 1L, title = "Song", playing = false, positionMs = 500L)
        )
        assertEquals(listOf(true, false), fakeClient.playbackStates)

        // Resumed
        fakePlayback.emitSnapshot(
            PlaybackSnapshot(currentSongId = 1L, title = "Song", playing = true, positionMs = 500L)
        )
        assertEquals(listOf(true, false, true), fakeClient.playbackStates)
    }

    @Test
    fun doesNotPushPlaybackStateWhenDisabled() = runTest(UnconfinedTestDispatcher()) {
        val manager = createManager(this, initialEnabled = false)
        manager.start()

        fakePlayback.emitSnapshot(PlaybackSnapshot(currentSongId = 1L, playing = true))

        assertTrue(fakeClient.playbackStates.isEmpty())
    }

    // -----------------------------------------------------------------------------------------
    // 3. Song Change and Lyrics Fetch/Push Tests
    // -----------------------------------------------------------------------------------------

    @Test
    fun loadsAndPushesLyricsOnSongChange() = runTest(UnconfinedTestDispatcher()) {
        val lyrics = NocturnLyrics(
            lines = listOf(
                NocturnLyricLine(
                    startMs = 1000L,
                    durationMs = 2000L,
                    text = "天空一片蔚蓝",
                    translation = "The sky is blue",
                    words = listOf(
                        NocturnLyricWord(1000L, 500L, "天空"),
                        NocturnLyricWord(1500L, 1500L, "蔚蓝"),
                    ),
                ),
            ),
        )
        fakeRepository.stubLyrics(42L, lyrics)

        val manager = createManager(this, initialEnabled = true)
        manager.start()

        fakePlayback.emitSnapshot(
            PlaybackSnapshot(
                currentSongId = 42L,
                title = "蔚蓝",
                artist = "海边歌手",
                durationMs = 180_000L,
                playing = true,
                positionMs = 1000L,
            )
        )

        assertEquals(1, fakeClient.songs.size)
        val pushed = fakeClient.songs.last()
        assertNotNull(pushed)
        assertEquals("42", pushed?.id)
        assertEquals("蔚蓝", pushed?.name)
        assertEquals("海边歌手", pushed?.artist)
        assertEquals(180_000L, pushed?.duration)
        assertEquals(1, pushed?.lyrics?.size)
        assertEquals("天空一片蔚蓝", pushed?.lyrics?.first()?.text)
        assertEquals("The sky is blue", pushed?.lyrics?.first()?.translation)
        assertEquals(2, pushed?.lyrics?.first()?.words?.size)
        assertTrue(fakeClient.translations.contains(true))
    }

    @Test
    fun clearsSongWhenCurrentSongIdBecomesNull() = runTest(UnconfinedTestDispatcher()) {
        fakeRepository.stubLyrics(10L, NocturnLyrics(listOf(NocturnLyricLine(0L, 1000L, "A"))))
        val manager = createManager(this, initialEnabled = true)
        manager.start()

        fakePlayback.emitSnapshot(PlaybackSnapshot(currentSongId = 10L, title = "A"))
        assertEquals(1, fakeClient.songs.size)
        assertNotNull(fakeClient.songs.last())

        // Playback stops / queue cleared
        fakePlayback.emitSnapshot(PlaybackSnapshot(currentSongId = null))
        assertEquals(2, fakeClient.songs.size)
        assertNull(fakeClient.songs.last())
    }

    @Test
    fun doesNotRefetchLyricsIfSongIdDidNotChange() = runTest(UnconfinedTestDispatcher()) {
        fakeRepository.stubLyrics(55L, NocturnLyrics(listOf(NocturnLyricLine(0L, 5000L, "Line"))))
        val manager = createManager(this, initialEnabled = true)
        manager.start()

        fakePlayback.emitSnapshot(PlaybackSnapshot(currentSongId = 55L, title = "T", positionMs = 0L))
        fakePlayback.emitSnapshot(PlaybackSnapshot(currentSongId = 55L, title = "T", positionMs = 500L))
        fakePlayback.emitSnapshot(PlaybackSnapshot(currentSongId = 55L, title = "T", positionMs = 1000L))

        assertEquals(1, fakeRepository.fetchCount(55L))
        assertEquals(1, fakeClient.songs.size)
    }

    // -----------------------------------------------------------------------------------------
    // 4. Progress Position Tick & SeekTo Synchronization Tests
    // -----------------------------------------------------------------------------------------

    @Test
    fun pushesPositionOnRegularProgressTicks() = runTest(UnconfinedTestDispatcher()) {
        fakeRepository.stubLyrics(1L, NocturnLyrics(listOf(NocturnLyricLine(0L, 10000L, "Line"))))
        val manager = createManager(this, initialEnabled = true)
        manager.start()

        fakePlayback.emitSnapshot(
            PlaybackSnapshot(currentSongId = 1L, title = "T", positionMs = 0L, positionDiscontinuity = 0L)
        )
        fakePlayback.emitSnapshot(
            PlaybackSnapshot(currentSongId = 1L, title = "T", positionMs = 500L, positionDiscontinuity = 0L)
        )
        fakePlayback.emitSnapshot(
            PlaybackSnapshot(currentSongId = 1L, title = "T", positionMs = 1000L, positionDiscontinuity = 0L)
        )

        assertEquals(listOf(0L, 500L, 1000L), fakeClient.positions)
        assertTrue(fakeClient.seeks.isEmpty())
    }

    @Test
    fun pushesSeekToWhenPositionDiscontinuityChanges() = runTest(UnconfinedTestDispatcher()) {
        fakeRepository.stubLyrics(1L, NocturnLyrics(listOf(NocturnLyricLine(0L, 10000L, "Line"))))
        val manager = createManager(this, initialEnabled = true)
        manager.start()

        fakePlayback.emitSnapshot(
            PlaybackSnapshot(currentSongId = 1L, title = "T", positionMs = 1000L, positionDiscontinuity = 0L)
        )

        // User scrubs timeline from 1000ms to 8000ms
        fakePlayback.emitSnapshot(
            PlaybackSnapshot(currentSongId = 1L, title = "T", positionMs = 8000L, positionDiscontinuity = 1L)
        )

        assertEquals(listOf(8000L), fakeClient.seeks)
    }

    // -----------------------------------------------------------------------------------------
    // 5. Pure Music & Placeholder Handling Tests
    // -----------------------------------------------------------------------------------------

    @Test
    fun pushesPureMusicPlaceholderWhenLyricsAreEmpty() = runTest(UnconfinedTestDispatcher()) {
        fakeRepository.stubLyrics(77L, NocturnLyrics.EMPTY)
        val manager = createManager(this, initialEnabled = true)
        manager.start()

        fakePlayback.emitSnapshot(PlaybackSnapshot(currentSongId = 77L, title = "纯音乐曲目"))

        assertEquals(listOf(LyriconModelMapper.PURE_MUSIC_TEXT), fakeClient.texts)
    }

    @Test
    fun handlesRepositoryExceptionGracefullyWithoutCrashing() = runTest(UnconfinedTestDispatcher()) {
        fakeRepository.stubException(88L, IOException("网络超时"))
        val manager = createManager(this, initialEnabled = true)
        manager.start()

        fakePlayback.emitSnapshot(PlaybackSnapshot(currentSongId = 88L, title = "无网歌曲"))

        // Must handle failure gracefully and fallback to pure music or no-lyrics text
        assertEquals(listOf(LyriconModelMapper.PURE_MUSIC_TEXT), fakeClient.texts)
    }

    // -----------------------------------------------------------------------------------------
    // 6. Settings Store SharedPreferences Listener Tests
    // -----------------------------------------------------------------------------------------

    @Test
    fun preferenceChangeInSettingsStoreTogglesRegistration() = runTest(UnconfinedTestDispatcher()) {
        val manager = createManager(this, initialEnabled = true)
        manager.start()
        assertEquals(1, fakeClient.registerCount)

        // Write directly to settings
        settings.lyriconEnabled = false

        assertEquals(1, fakeClient.unregisterCount)
        assertFalse(manager.isEnabled())

        // Re-enable
        settings.lyriconEnabled = true

        assertEquals(2, fakeClient.registerCount)
        assertTrue(manager.isEnabled())
    }

    // -----------------------------------------------------------------------------------------
    // 7. AppContainer & Application Wiring Tests
    // -----------------------------------------------------------------------------------------

    @Test
    fun appContainerProvidesLyriconManagerInstance() {
        val app = RuntimeEnvironment.getApplication() as NocturnApplication
        val container = app.container

        assertNotNull(container.lyriconManager)
        assertTrue(container.lyriconManager is LyriconBridge)
    }

    // -----------------------------------------------------------------------------------------
    // Test Doubles
    // -----------------------------------------------------------------------------------------

    class FakeLyriconClient : LyriconClient {
        var registerCount = 0
        var unregisterCount = 0
        var destroyCount = 0
        override var isRegistered = false

        val playbackStates = mutableListOf<Boolean>()
        val songs = mutableListOf<LyriconSong?>()
        val texts = mutableListOf<String?>()
        val positions = mutableListOf<Long>()
        val seeks = mutableListOf<Long>()
        val translations = mutableListOf<Boolean>()

        override fun register(): Boolean {
            registerCount++
            isRegistered = true
            return true
        }

        override fun unregister() {
            unregisterCount++
            isRegistered = false
        }

        override fun destroy() {
            destroyCount++
            isRegistered = false
        }

        override fun setPlaybackState(playing: Boolean) {
            playbackStates.add(playing)
        }

        override fun setSong(song: LyriconSong?) {
            songs.add(song)
        }

        override fun sendText(text: String?) {
            texts.add(text)
        }

        override fun setPosition(positionMs: Long) {
            positions.add(positionMs)
        }

        override fun seekTo(positionMs: Long) {
            seeks.add(positionMs)
        }

        override fun setDisplayTranslation(display: Boolean) {
            translations.add(display)
        }
    }

    class FakePlaybackEngine : PlaybackEngine {
        private val mutableSnapshot = MutableStateFlow(PlaybackSnapshot())
        override val snapshot: StateFlow<PlaybackSnapshot> = mutableSnapshot

        fun emitSnapshot(snapshot: PlaybackSnapshot) {
            mutableSnapshot.value = snapshot
        }

        override suspend fun connect() {}
        override fun setQueue(songs: List<PlayableSong>, startIndex: Int) {}
        override fun play() { mutableSnapshot.update { it.copy(playing = true) } }
        override fun pause() { mutableSnapshot.update { it.copy(playing = false) } }
        override fun seekTo(positionMs: Long) {
            mutableSnapshot.update {
                it.copy(
                    positionMs = positionMs,
                    positionDiscontinuity = it.positionDiscontinuity + 1L,
                )
            }
        }
        override fun next() {}
        override fun previous() {}
        override fun setRepeatMode(mode: RepeatMode) {}
        override fun setShuffle(enabled: Boolean) {}
        override fun clearError() {}
        override fun release() {}
    }

    class FakeMusicRepository : MusicRepository {
        private val lyricStubs = mutableMapOf<Long, NocturnLyrics>()
        private val exceptionStubs = mutableMapOf<Long, Throwable>()
        private val fetchCounts = mutableMapOf<Long, Int>()

        fun stubLyrics(songId: Long, lyrics: NocturnLyrics) {
            lyricStubs[songId] = lyrics
        }

        fun stubException(songId: Long, exception: Throwable) {
            exceptionStubs[songId] = exception
        }

        fun fetchCount(songId: Long): Int = fetchCounts[songId] ?: 0

        override suspend fun lyrics(songId: Long): NocturnLyrics {
            fetchCounts[songId] = (fetchCounts[songId] ?: 0) + 1
            exceptionStubs[songId]?.let { throw it }
            return lyricStubs[songId] ?: NocturnLyrics.EMPTY
        }

        override val session: StateFlow<AccountProfile?> = MutableStateFlow(null)
        override suspend fun homeFeed(): HomeFeed = HomeFeed()
        override suspend fun playlistDetail(id: Long): PlaylistDetail = error("Not needed")
        override suspend fun playlistSongs(id: Long, offset: Int, limit: Int): PlaylistSongsPage = error("Not needed")
        override suspend fun searchSongs(keyword: String, offset: Int, limit: Int): SearchSongsPage = error("Not needed")
        override suspend fun searchPlaylists(keyword: String, offset: Int, limit: Int): SearchPlaylistsPage = error("Not needed")
        override suspend fun searchAlbums(keyword: String, offset: Int, limit: Int): SearchAlbumsPage = error("Not needed")
        override suspend fun searchArtists(keyword: String, offset: Int, limit: Int): SearchArtistsPage = error("Not needed")
        override suspend fun searchSuggestions(keyword: String): List<String> = emptyList()
        override suspend fun hotSearches(): List<String> = emptyList()
        override suspend fun defaultKeyword(): String? = null
        override suspend fun albumDetail(id: Long): AlbumDetail = error("Not needed")
        override suspend fun artistDetail(id: Long): ArtistDetail = error("Not needed")
    }
}
```

---

## 5. Verification Method

To independently verify the implementation once Worker completes the files:

1. **Unit & Robolectric Test Suite Verification**:
   ```powershell
   .\gradlew.bat cleanTestDebugUnitTest :app:testDebugUnitTest --tests "dev.nocturn.app.playback.lyricon.*"
   ```
   Must pass both `LyriconModelMapperTest` and `LyriconManagerTest` (100% test success).

2. **Regression Verification of App Startup and Existing Tests**:
   ```powershell
   .\gradlew.bat :app:testDebugUnitTest --tests "dev.nocturn.app.SettingsViewModelTest"
   .\gradlew.bat :app:testDebugUnitTest --tests "dev.nocturn.app.ui.SearchLibraryScreensTest"
   ```
   Verifies that `NocturnApplication.onCreate()` wiring does not break existing ViewModel, UI, or screenshot tests.

3. **Compilation & Static Lint Checks**:
   ```powershell
   .\gradlew.bat :app:assembleDebug :app:lintDebug
   ```
   Zero compile errors and zero Android lint warnings.
