# Investigation Report: Nocturn Playback Lifecycle, MediaSession/Media3 Architecture & Lyricon Provider Integration

**Working Directory**: `d:\Nocturn\.agents\teamwork\explorer_survey_2`  
**Date**: 2026-11-07  
**Author**: teamwork_explorer (Survey Agent 2)

---

## 1. Observation

Direct observations from examining the Nocturn codebase and Lyricon official documentation:

### 1.1 Playback Subsystem Architecture & Responsibilities
- **`app/src/main/kotlin/dev/nocturn/app/playback/PlaybackService.kt`** (Lines 26–96):
  - Extends `androidx.media3.session.MediaSessionService`.
  - Runs in the main app process `dev.nocturn.app` with `android:foregroundServiceType="mediaPlayback"` (`AndroidManifest.xml:32-39`).
  - `onCreate()` initializes a single `ExoPlayer` instance configured with:
    - Audio attributes: `setAudioAttributes(..., handleAudioFocus = true)` (Line 46).
    - `setHandleAudioBecomingNoisy(true)` (Line 49) — automatically pauses when audio output (e.g., headphones) is unplugged.
    - `setWakeMode(C.WAKE_MODE_NETWORK)` (Line 51) — keeps CPU alive for streaming during screen-off.
    - `DefaultMediaNotificationProvider` with icon `R.drawable.ic_nocturn` (Lines 34–38).
    - `MediaSession.Builder(this, exoPlayer).setSessionActivity(sessionActivity).build()` (Lines 64–66).
  - `onGetSession(controllerInfo)` (Line 69) exposes the `MediaSession`.
  - `onTaskRemoved(rootIntent)` (Lines 71–81):
    ```kotlin
    val sessionPlayer = player
    val isIdle = sessionPlayer == null ||
        sessionPlayer.mediaItemCount == 0 ||
        !sessionPlayer.playWhenReady
    if (isIdle) {
        stopSelf()
    }
    ```
    If music is actively playing (`playWhenReady == true` and `mediaItemCount > 0`), `stopSelf()` is **not** called. The foreground service and audio playback continue when the user swipes away Nocturn from Recents.
  - `onDestroy()` (Lines 83–91): releases `player` and `mediaSession`.
  - **Crucial Observation**: `PlaybackService` has **no** reference to `MusicRepository`, **no** network access to NetEase APIs, and **no** knowledge of lyrics (`Lyrics`).

- **`app/src/main/kotlin/dev/nocturn/app/playback/PlaybackContract.kt`** (Lines 1–82):
  - `PlayableSong` (Lines 9–29): encapsulates `id: Long`, `title: String`, `artist: String`, `album: String?`, `coverUrl: String?`, `durationMs: Long`, `url: String`.
  - `PlaybackSnapshot` (Lines 32–48):
    ```kotlin
    data class PlaybackSnapshot(
        val currentSongId: Long? = null,
        val playing: Boolean = false,
        val buffering: Boolean = false,
        val positionMs: Long = 0,
        val durationMs: Long = 0,
        val repeatMode: RepeatMode = RepeatMode.Off,
        val shuffle: Boolean = false,
        val queueIds: List<Long> = emptyList(),
        val currentIndex: Int = -1,
        val error: String? = null,
        val positionSampledAtNanos: Long = 0L,
        val positionDiscontinuity: Long = 0L,
        val playbackSpeed: Float = 1f,
    )
    ```
  - `PlaybackEngine` interface (Lines 55–81): exposes `val snapshot: StateFlow<PlaybackSnapshot>`, plus `connect()`, `setQueue()`, `play()`, `pause()`, `seekTo(positionMs)`, `next()`, `previous()`, `setRepeatMode()`, `setShuffle()`, `clearError()`, `release()`.

- **`app/src/main/kotlin/dev/nocturn/app/playback/MediaSessionPlaybackEngine.kt`** (Lines 39–282):
  - Production implementation of `PlaybackEngine`. Connects to `PlaybackService` asynchronously via Media3 `MediaController.Builder(appContext, sessionToken).buildAsync()` (Lines 97–121).
  - Holds `appContext = context.applicationContext` and a `scope = CoroutineScope(SupervisorJob() + Dispatchers.Main.immediate)`.
  - Implements `Player.Listener` (Lines 58–80):
    - `onPositionDiscontinuity`: increments `positionDiscontinuity++` (Line 64). Discontinuities occur on seeks, repeats, and item transitions.
    - `onEvents`: calls `publishSnapshot(player)` (Line 68).
    - `onPlayerError`: updates snapshot error copy (Lines 71–79).
  - Progress polling loop in `startProgressUpdates()` (Lines 123–144):
    ```kotlin
    progressJob = scope.launch {
        while (isActive) {
            val current = controller
            if (current == null) break
            val positionMs = current.currentPosition
            val sampledAtNanos = SystemClock.elapsedRealtimeNanos()
            mutableSnapshot.update { previous ->
                PlaybackSnapshotMapping.withProgress(
                    previous = previous,
                    positionMs = positionMs,
                    durationMs = current.duration,
                    positionSampledAtNanos = if (current.isPlaying || positionMs != previous.positionMs) {
                        sampledAtNanos
                    } else previous.positionSampledAtNanos,
                )
            }
            delay(PROGRESS_UPDATE_INTERVAL_MS) // 500L
        }
    }
    ```
    Snapshot progress is refreshed every 500ms while connected.
  - `publishSnapshot(player: Player)` (Lines 146–169):
    Extracts `mediaIds` from `player.getMediaItemAt(index).mediaId`, `currentIndex = player.currentMediaItemIndex`, `isPlaying`, `playbackState`, `positionMs`, `durationMs`, `repeatMode`, `shuffleModeEnabled`.
  - In `setQueue(songs: List<PlayableSong>, startIndex: Int)` (Lines 171–208):
    Translates songs to `MediaItem`s via `MediaItemMapping.resolveQueue`, calls `current.setMediaItems(...)` and `current.prepare()`.

- **`app/src/main/kotlin/dev/nocturn/app/playback/MediaItemMapping.kt`** (Lines 44–62):
  - Builds `MediaMetadata` with `setTitle(song.title)`, `setArtist(song.artist)`, `setArtworkUri(...)`, `setDurationMs(song.durationMs)`.
  - Sets `mediaId = song.id.toString()`.
  - **Observation**: Media3 `player.currentMediaItem?.mediaMetadata` contains the song title and artist at any time during playback.

### 1.2 Application Scope & Container Lifecycle
- **`app/src/main/kotlin/dev/nocturn/app/NocturnApplication.kt`** (Lines 15–26):
  ```kotlin
  class AppContainer(context: Context) {
      val settings: SettingsStore = SettingsStore(context)
      val repository: MusicRepository by lazy { LocalMusicRepository(context.applicationContext) }
      val playback: PlaybackEngine by lazy { MediaSessionPlaybackEngine(context.applicationContext) }
  }
  class NocturnApplication : Application() {
      val container: AppContainer by lazy { AppContainer(this) }
  }
  ```
  `AppContainer` is strictly **process-scoped**. The single `MusicRepository` and `PlaybackEngine` persist for the entire lifespan of the Android process.
- **`app/src/main/kotlin/dev/nocturn/app/NocturnViewModel.kt`** (Lines 63–1188):
  - Instantiated in `MainActivity.kt:16` via `private val model: NocturnViewModel by viewModels()`.
  - Scoped to `MainActivity`'s ViewModelStoreOwner.
  - In `onCleared()` (Lines 1183–1187):
    ```kotlin
    override fun onCleared() {
        super.onCleared()
        // The container is process-scoped: closing the repository or releasing the media session
        // here would break recreated activities, so ownership stays with NocturnApplication.
    }
    ```
  - Observes `engine.snapshot` (Lines 1060–1064):
    Calls `applyPlaybackSnapshot(snapshot)`.
  - Detects song changes (Lines 1070–1110):
    `val songChanged = song != null && song.id != previousSong?.id`
    When `songChanged || currentLyricsSongId != song.id`, calls `loadLyrics(song)`.
  - `loadLyrics(song)` (Lines 1128–1146):
    Launches a coroutine in `viewModelScope`, fetches `repository.lyrics(song.id)`, and updates `mutablePlayer.lyrics`.
  - **Crucial Observation**: If `MainActivity` is destroyed (e.g. user presses Back to exit UI while audio continues in background `PlaybackService`), `NocturnViewModel.onCleared()` is invoked and `viewModelScope` is **cancelled**. If lyric loading or status bar updates rely solely on `NocturnViewModel`, subsequent background song changes will **not** fetch lyrics or update status bar lyrics!

### 1.3 Settings Persistence & Observability
- **`app/src/main/kotlin/dev/nocturn/app/data/SettingsStore.kt`** (Lines 11–67):
  - Wraps private `SharedPreferences("nocturn-settings", Context.MODE_PRIVATE)`.
  - Manages `themeMode`, `dynamicColor`, `quality`, `hapticsEnabled`, `searchHistory`.
  - Easily extensible with `var lyriconEnabled: Boolean`.

### 1.4 Lyricon SDK Contracts (from Developer Documentation)
- **Dependency**: `io.github.proify.lyricon:provider:0.1.70`.
- **Factory & Lifecycle**:
  - `val provider = LyriconFactory.createProvider(context)`
  - `provider.register()` — registers provider with Lyricon Central Service.
  - `provider.unregister()` — unregisters connection.
  - `provider.destroy()` — cleans up listeners, callbacks, and remote IPC resources.
  - `provider.autoSync = true` — automatically syncs cached state on reconnection.
- **Playback Control APIs (`provider.player: RemotePlayer`)**:
  - `setPlaybackState(playing: Boolean)` (or `setPlaybackState(playbackState: PlaybackState)`).
  - `setSong(song: Song?)` — pushes `Song(id, name, artist, duration, lyrics: List<RichLyricLine>)`. Passing `null` clears current playback.
  - `setPosition(position: Long)` — periodic progress synchronization in milliseconds.
  - `seekTo(position: Long)` — active position jumps (user seeking, scrubbing).
  - `sendText(text: String?)` — plain text mode (for pure music / placeholder).
  - `setDisplayTranslation(display: Boolean)` — controls translation visibility.
- **Recommended invocation sequence from docs**:
  ```kotlin
  player.setSong(song)
  player.setPosition(currentPosition)
  player.setPlaybackState(isPlaying)
  player.setDisplayTranslation(displayTranslation)
  ```

---

## 2. Logic Chain

From these direct observations, we deduce the following architectural analysis:

### 2.1 Why `PlaybackService` Alone Cannot Manage Lyricon Provider Directly
1. *Observation*: `PlaybackService` is a Media3 `MediaSessionService` that manages `ExoPlayer` and system notifications. It does not reference `MusicRepository` or NetEase data sources.
2. *Observation*: Media3 `MediaItem` only contains audio streaming URLs and basic `MediaMetadata` (`title`, `artist`, `durationMs`). It contains no lyric data (`Lyrics`), syllable/word timestamps, or translations.
3. *Deduction*: Placing `LyriconFactory.createProvider` directly inside `PlaybackService` without an abstraction layer would force `PlaybackService` to take a direct dependency on `MusicRepository`, lyric parsers, and HTTP client infrastructure. This would violate the clear separation of concerns in Nocturn (where `PlaybackService` is an isolated audio engine host) and make unit testing difficult.

### 2.2 Why `NocturnViewModel` Cannot Own Lyricon Provider
1. *Observation*: `NocturnViewModel` is an `AndroidViewModel` scoped to `MainActivity`.
2. *Observation*: When a user starts playback and navigates away or presses Back, `MainActivity` finishes while `PlaybackService` stays alive in the foreground (`isIdle == false` in `onTaskRemoved`).
3. *Observation*: When `MainActivity` finishes, `NocturnViewModel.onCleared()` runs, and its `viewModelScope` is cancelled.
4. *Observation*: Lyricon is a system-wide status bar lyric display tool. Its primary use case is displaying lyrics while the user is outside Nocturn (browsing social media, playing games, or on the home screen).
5. *Deduction*: If `NocturnViewModel` owned the Lyricon Provider:
   - When the user exits the app UI, background song transitions (next song in queue) would no longer trigger `loadLyrics()`.
   - The Lyricon status bar would freeze on the previous song or stop updating progress.
   - Therefore, `NocturnViewModel` is the **wrong** lifecycle owner for Lyricon Provider.

### 2.3 Why a Dedicated `LyriconManager` in `AppContainer` (Coordinated with `PlaybackEngine`) is the Optimal Architecture
1. *Observation*: `AppContainer` in `NocturnApplication` is process-scoped and holds singletons of `SettingsStore`, `MusicRepository`, and `PlaybackEngine`.
2. *Observation*: Whenever `PlaybackService` is running in the foreground, Android keeps the application process alive (`FOREGROUND_SERVICE_MEDIA_PLAYBACK`).
3. *Observation*: `PlaybackEngine` (`MediaSessionPlaybackEngine`) has an active coroutine scope (`SupervisorJob() + Dispatchers.Main.immediate`) and publishes `snapshot: StateFlow<PlaybackSnapshot>`.
4. *Observation*: `PlaybackSnapshot` contains `currentSongId`, `playing`, `positionMs`, `durationMs`, and `positionDiscontinuity`. Every 500ms, progress updates are emitted.
5. *Observation*: Media3 `player.currentMediaItem?.mediaMetadata` in `MediaSessionPlaybackEngine` already has `title` and `artist`. Adding `val title: String? = null` and `val artist: String? = null` to `PlaybackSnapshot` makes the snapshot completely self-contained.
6. *Deduction*: A dedicated `LyriconManager` (or `LyriconProviderBridge`) class:
   - Resides in `AppContainer` (process-scoped).
   - Observes `PlaybackEngine.snapshot` and `SettingsStore.lyriconEnabled`.
   - When a song starts or changes (`currentSongId` changes):
     - It autonomously fetches parsed lyrics from `repository.lyrics(currentSongId)`.
     - Converts `Lyrics` to Lyricon's `RichLyricLine` / `LyricWord` models.
     - Pushes `Song` to `provider.player.setSong(...)`.
   - When playback state changes (`playing` changes):
     - Calls `provider.player.setPlaybackState(snapshot.playing)`.
   - When seeking occurs (`positionDiscontinuity` increments for the same song):
     - Calls `provider.player.seekTo(snapshot.positionMs)`.
   - On periodic progress updates (every 500ms):
     - Calls `provider.player.setPosition(snapshot.positionMs)`.
   - When Settings toggle `lyriconEnabled` is turned OFF:
     - Calls `provider.unregister()` and `provider.destroy()`.
   - When Settings toggle `lyriconEnabled` is turned ON:
     - Creates provider, registers, and syncs current state if playing.
   - When playback ends or service is destroyed:
     - Calls `provider.unregister()`.

---

## 3. Caveats

1. **Lyricon Central Service Availability**:
   - The Lyricon Provider SDK communicates via IPC with the Lyricon Central Service (e.g. through the Lyricon app / LSPosed / LocalCentralService). If Lyricon is not installed or enabled on the device, `provider.register()` fails silently or triggers `onConnectTimeout`. The implementation must handle connection listener callbacks gracefully without crashing.
2. **Android 8.1 API Limitation**:
   - Lyricon developer docs note: *"Provider Bridge 当前在 Android 8.1 以下会返回空实现。"*. Nocturn's `minSdk` is API 26 (Android 8.0) or higher; on Android 8.0 devices, the SDK returns a no-op stub.
3. **Pure Music / Missing Lyrics Handling**:
   - Some tracks (instrumentals, DJ sets, or uncataloged songs) return empty lyrics (`Lyrics.isEmpty`). In this case, `LyriconManager` must call `provider.player.sendText("纯音乐，请欣赏")` or push a placeholder `Song` without lines to avoid leaving stale lyrics from the previous song.
4. **Duplicate Lyric Requests**:
   - Both `NocturnViewModel` (for the in-app `LyricsView`) and `LyriconManager` (for status bar lyrics) require `repository.lyrics(songId)`. Adding an in-memory cache in `LocalMusicRepository` (or sharing a repository-level `StateFlow`/LRU cache) ensures `lyrics(songId)` is fetched only once over the network/LocalAPI per song.

---

## 4. Conclusion

### 4.1 Recommended Architecture & Component Hierarchy

```
┌────────────────────────────────────────────────────────────────────────┐
│                          NocturnApplication                            │
│                                                                        │
│  ┌──────────────────────────────────────────────────────────────────┐  │
│  │                           AppContainer                           │  │
│  │                                                                  │  │
│  │  ┌──────────────┐     ┌────────────────┐    ┌─────────────────┐  │  │
│  │  │SettingsStore │     │ MusicRepository│    │ PlaybackEngine  │  │  │
│  │  └──────┬───────┘     └───────┬────────┘    └────────┬────────┘  │  │
│  │         │                     │                      │           │  │
│  │         ▼                     ▼                      ▼           │  │
│  │  ┌────────────────────────────────────────────────────────────┐  │  │
│  │  │                       LyriconManager                       │  │  │
│  │  │  - Observes snapshot: StateFlow<PlaybackSnapshot>          │  │  │
│  │  │  - Observes lyriconEnabled: Boolean                        │  │  │
│  │  │  - Fetches Lyrics via repository.lyrics(songId)            │  │  │
│  │  │  - Converts Nocturn Lyrics -> Lyricon RichLyricLine        │  │  │
│  │  │  - Manages LyriconProvider lifecycle (register/destroy)    │  │  │
│  │  └────────────────────────────┬───────────────────────────────┘  │  │
│  │                               │ IPC                              │  │
│  │                               ▼                                  │  │
│  │                   [ Lyricon Central Service ]                    │  │
│  └──────────────────────────────────────────────────────────────────┘  │
└────────────────────────────────────────────────────────────────────────┘
```

### 4.2 Lifecycle Tying Matrix

| Event / Trigger | Trigger Source | LyriconManager Action |
|---|---|---|
| **App Starts** | `NocturnApplication.onCreate()` | Initializes `LyriconManager`. If `lyriconEnabled` is true, registers provider. |
| **Playback Starts** | `PlaybackEngine.snapshot` (`currentSongId != null`, `playing = true`) | Fetches lyrics, converts models, calls `player.setSong(...)`, `player.setPosition(...)`, `player.setPlaybackState(true)`. |
| **Track Transition (Auto / Next / Prev)** | Media3 ExoPlayer track advance | Detects `songId` change. Cancels previous lyric job, loads new lyrics, calls `player.setSong(newSong)`, `player.setPosition(0)`. |
| **User Seeks / Discontinuity** | User scrubs bar in App, Notification, or Lockscreen | Detects `positionDiscontinuity` increment with same `currentSongId`. Calls `player.seekTo(snapshot.positionMs)`. |
| **500ms Progress Polling** | `MediaSessionPlaybackEngine.startProgressUpdates` | Calls `player.setPosition(snapshot.positionMs)`. |
| **Play / Pause Toggle** | User taps play/pause, or Audio Focus loss, or headphones unplugged | Calls `player.setPlaybackState(snapshot.playing)`. |
| **Audio Becoming Noisy** | Headphones unplugged (`setHandleAudioBecomingNoisy(true)`) | ExoPlayer auto-pauses -> snapshot emits `playing = false` -> calls `player.setPlaybackState(false)`. |
| **App Backgrounded / Screen Off** | User presses Home / phone locks | No disruption. Foreground service keeps process alive; progress and track transitions continue syncing to status bar. |
| **App UI Closed (Back to exit)** | `MainActivity.onDestroy()` / `onCleared()` | No disruption. `LyriconManager` in `AppContainer` is unaffected by Activity teardown. |
| **Playback Stops / Queue Cleared** | Queue ends or user stops playback | Calls `player.setSong(null)`, `player.setPlaybackState(false)`. Optionally `provider.unregister()`. |
| **Service Destroyed** | `PlaybackService.onDestroy()` | Calls `provider.unregister()`, `provider.destroy()`. |
| **Settings Toggled OFF** | User toggles switch in Settings screen | Calls `provider.unregister()`, `provider.destroy()`. |
| **Settings Toggled ON** | User toggles switch in Settings screen | Creates `provider`, calls `register()`, pushes current playing state if active. |

### 4.3 Proposed Snapshot Enrichment
In `PlaybackContract.kt`:
```kotlin
data class PlaybackSnapshot(
    val currentSongId: Long? = null,
    val title: String? = null,          // NEW: current song title from MediaMetadata
    val artist: String? = null,         // NEW: current song artist from MediaMetadata
    val playing: Boolean = false,
    val buffering: Boolean = false,
    val positionMs: Long = 0,
    val durationMs: Long = 0,
    val repeatMode: RepeatMode = RepeatMode.Off,
    val shuffle: Boolean = false,
    val queueIds: List<Long> = emptyList(),
    val currentIndex: Int = -1,
    val error: String? = null,
    val positionSampledAtNanos: Long = 0L,
    val positionDiscontinuity: Long = 0L,
    val playbackSpeed: Float = 1f,
)
```
In `PlaybackSnapshotMapping.fromPlayer`:
```kotlin
val currentItem = if (currentIndex in 0 until player.mediaItemCount) player.getMediaItemAt(currentIndex) else null
val title = currentItem?.mediaMetadata?.title?.toString()
val artist = currentItem?.mediaMetadata?.artist?.toString()
```
This guarantees full independence from `NocturnViewModel` while retaining 100% backward compatibility.

---

## 5. Verification Method

1. **Codebase Inspection**:
   - Inspect `app/src/main/kotlin/dev/nocturn/app/playback/PlaybackService.kt:71-81` to verify `onTaskRemoved` behavior during playback.
   - Inspect `app/src/main/kotlin/dev/nocturn/app/playback/MediaSessionPlaybackEngine.kt:123-144` to verify the 500ms progress loop and `onPositionDiscontinuity` tracking.
   - Inspect `app/src/main/kotlin/dev/nocturn/app/NocturnApplication.kt:15-26` to verify process-scoped `AppContainer`.
2. **Build & Unit Test Verification**:
   - Execute existing playback test suite:
     ```powershell
     .\gradlew.bat :app:testDebugUnitTest --tests "dev.nocturn.app.playback.*"
     ```
   - Invalidation conditions: If `PlaybackSnapshot` modifications break any existing constructor callers (verified to be non-breaking due to default parameter values), or if `MediaController` callbacks execute on worker threads (verified: runs on main looper via `appContext.mainExecutor`).

---
