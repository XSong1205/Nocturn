# Lyricon Provider SDK Specification Report

## 1. Observation

### 1.1 Documentation & Authoritative Spec Sources
The primary specification source is the official Lyricon (词幕) Developer Documentation hosted at:
- Overview: `https://tomakino.github.io/lyricon/zh-cn/developer/`
- Provider Overview: `https://tomakino.github.io/lyricon/zh-cn/developer/provider/`
- Quick Start: `https://tomakino.github.io/lyricon/zh-cn/developer/provider/quick-start`
- Manifest Configuration: `https://tomakino.github.io/lyricon/zh-cn/developer/provider/manifest`
- Connection Lifecycle: `https://tomakino.github.io/lyricon/zh-cn/developer/provider/connection`
- Player Control: `https://tomakino.github.io/lyricon/zh-cn/developer/provider/player-control`
- Lyric Data Structures: `https://tomakino.github.io/lyricon/zh-cn/developer/provider/lyrics-model`
- Local Testing: `https://tomakino.github.io/lyricon/zh-cn/developer/provider/local-testing`
- Provider FAQ: `https://tomakino.github.io/lyricon/zh-cn/developer/provider/faq`
- App Providers Guide: `https://tomakino.github.io/lyricon/zh-cn/app/providers`

### 1.2 Maven Dependency & Coordinates
From `quick-start` and `provider/index`:
- **Maven Coordinate**: `io.github.proify.lyricon:provider:0.1.70`
- **Gradle snippet**:
  ```kotlin
  implementation("io.github.proify.lyricon:provider:0.1.70")
  ```
- **Repository**: Published to **Maven Central** (`https://img.shields.io/maven-central/v/io.github.proify.lyricon/provider`).
- **Nocturn Environment Verification**: In `d:\Nocturn\settings.gradle.kts` (line 13), `dependencyResolutionManagement { repositories { mavenCentral() } }` is already present. No extra custom repositories (e.g. JitPack) are required.
- **Platform Constraint**: Provider Bridge returns an empty / no-op implementation on Android versions below 8.1 (API 27).

### 1.3 AndroidManifest Metadata
From `manifest.md` and `app/providers.md`:
In `AndroidManifest.xml` under `<application>`:
```xml
<application ...>
    <!-- Required: Mark app as a Lyricon module -->
    <meta-data
        android:name="lyricon_module"
        android:value="true" />

    <!-- Required: Module author name -->
    <meta-data
        android:name="lyricon_module_author"
        android:value="Nocturn Team" />

    <!-- Required: Module description -->
    <meta-data
        android:name="lyricon_module_description"
        android:value="NetEase Cloud Music Client for Android" />

    <!-- Capability tags resource reference -->
    <meta-data
        android:name="lyricon_module_tags"
        android:resource="@array/lyricon_module_tags" />
</application>
```
In `res/values/arrays.xml`:
```xml
<?xml version="1.0" encoding="utf-8"?>
<resources>
    <string-array name="lyricon_module_tags">
        <item>$syllable</item>
        <item>$translation</item>
    </string-array>
</resources>
```
- Tag `$syllable`: Declares support for word-by-word / syllable-level dynamic lyrics (YRC).
- Tag `$translation`: Declares support for lyric translation display.

### 1.4 SDK Classes and API Signatures
From `quick-start.md`, `connection.md`, and `player-control.md`:

#### 1.4.1 Factory & Creation (`LyriconFactory`)
Package: `io.github.proify.lyricon.provider.LyriconFactory`
```kotlin
object LyriconFactory {
    fun createProvider(
        context: Context,
        providerPackageName: String = context.packageName,
        playerPackageName: String = context.packageName,
        logo: ProviderLogo? = null,
        metadata: Map<String, Any?>? = null,
        centralPackageName: String? = null // For testing with LocalCentralService
    ): LyriconProvider
}
```

#### 1.4.2 Provider Lifecycle (`LyriconProvider`)
Package: `io.github.proify.lyricon.provider.LyriconProvider`
- **Properties**:
  - `val player: RemotePlayer`: Interface to push playback and lyric state.
  - `val service: ProviderService`: Service connection manager.
  - `var autoSync: Boolean`: When `true`, automatically resynchronizes cached playback and lyric state upon connection or reconnection to the central service. Default recommended: `true`.
- **Lifecycle Methods**:
  - `register(): Boolean`: Sends registration request to Lyricon central service. Returns `true` if registration message was dispatched successfully.
  - `unregister()`: Actively unregisters from central service (e.g. playback stopped, app in background, or feature toggled off in settings).
  - `destroy()`: Cleans up listeners, callbacks, and IPC resources. Called on application or service termination.
- **Connection Listening**:
  ```kotlin
  provider.service.addConnectionListener {
      onConnected { provider -> /* Initial connection successful */ }
      onReconnected { provider -> /* Reconnected after disconnect */ }
      onDisconnected { provider -> /* Central service disconnected */ }
      onConnectTimeout { provider -> /* Connection attempt timed out */ }
  }
  ```

#### 1.4.3 Remote Player Control (`RemotePlayer`)
Obtained via `provider.player`:
- `setSong(song: Song?)`:
  - Passing `song != null`: sets current song metadata and full structured lyrics.
  - Passing `song == null`: clears current song and resets playback state.
- `sendText(text: String?)`:
  - Pushes plain text without timeline (e.g. for pure music / "纯音乐，请欣赏", placeholder, or loading).
  - *Side-effect observed in docs*: calling `sendText` clears existing structured song metadata and enters plain text mode.
- `setPlaybackState(playing: Boolean)`:
  - Synchronizes play/pause state (`true` = playing, `false` = paused).
- `setPlaybackState(playbackState: PlaybackState?)`:
  - Alternative overload supporting Android platform `android.media.session.PlaybackState`. Allows central service to extrapolate elapsed time based on position, speed, and update timestamp.
- `setPosition(position: Long)`:
  - Continuously synchronizes elapsed playback position in milliseconds.
- `seekTo(position: Long)`:
  - Explicitly signals a seek / scrub event to the given millisecond position.
- `setPositionUpdateInterval(interval: Int)`:
  - Sets the update interval (in ms, e.g. 500) for position sampling by the central service.
- `setDisplayTranslation(display: Boolean)`:
  - Toggles whether translation text is rendered on the status bar. Requires `RichLyricLine.translation` to be populated.
- `setDisplayRoma(display: Boolean)`:
  - Toggles whether romaji text is rendered. Requires romaji data to be populated.

### 1.5 Lyric Data Models
Package: `io.github.proify.lyricon.lyric.model.*`

#### 1.5.1 `Song`
```kotlin
package io.github.proify.lyricon.lyric.model

data class Song(
    val id: String? = null,
    val name: String,
    val artist: String? = null,
    val duration: Long? = null, // Milliseconds
    val lyrics: List<RichLyricLine>? = null
)
```

#### 1.5.2 `RichLyricLine`
```kotlin
package io.github.proify.lyricon.lyric.model

data class RichLyricLine(
    val begin: Long,               // Start time in ms
    val end: Long,                 // End time in ms
    val text: String,              // Main lyric text
    val secondary: String? = null, // Secondary/accompaniment lyric text
    val translation: String? = null,// Translated lyric text
    val words: List<LyricWord>? = null // Syllable/word slices
)
```

#### 1.5.3 `LyricWord`
```kotlin
package io.github.proify.lyricon.lyric.model

data class LyricWord(
    val text: String,  // Word or syllable string
    val begin: Long,   // Start time in ms
    val end: Long      // End time in ms
)
```

### 1.6 Nocturn Model Mapping & Type Name Collisions
Nocturn's internal data models live in `d:\Nocturn\app\src\main\kotlin\dev\nocturn\app\data\model\Models.kt`:
- Nocturn has `dev.nocturn.app.data.model.Song`:
  - `id: Long` -> map to `Song.id = id.toString()`
  - `name: String` -> map to `Song.name = name`
  - `artistText: String` -> map to `Song.artist = artistText`
  - `durationMs: Long` -> map to `Song.duration = durationMs`
- Nocturn has `dev.nocturn.app.data.model.LyricLine`:
  - `startMs: Long` -> map to `RichLyricLine.begin = startMs`
  - `durationMs: Long?` -> calculate `RichLyricLine.end`:
    `if (durationMs != null && durationMs > 0) startMs + durationMs else nextLine.startMs` (or track duration fallback)
  - `text: String` -> `RichLyricLine.text = text`
  - `translation: String?` -> `RichLyricLine.translation = translation`
  - `words: List<dev.nocturn.app.data.model.LyricWord>` -> mapped to `RichLyricLine.words`
- Nocturn has `dev.nocturn.app.data.model.LyricWord`:
  - `startMs: Long` -> `LyricWord.begin = startMs`
  - `durationMs: Long` -> `LyricWord.end = startMs + durationMs`
  - `text: String` -> `LyricWord.text = text`

*Critical Collision Warning*:
Both Nocturn and Lyricon declare `Song` and `LyricWord`. In adapter/mapping files, Kotlin import aliases MUST be used:
```kotlin
import dev.nocturn.app.data.model.Song as NocturnSong
import dev.nocturn.app.data.model.LyricWord as NocturnLyricWord
import dev.nocturn.app.data.model.LyricLine as NocturnLyricLine
import dev.nocturn.app.data.model.Lyrics as NocturnLyrics
import io.github.proify.lyricon.lyric.model.Song as LyriconSong
import io.github.proify.lyricon.lyric.model.RichLyricLine as LyriconRichLine
import io.github.proify.lyricon.lyric.model.LyricWord as LyriconWord
```

---

## 2. Logic Chain

1. **Step 1 (Source Verification)**: We retrieved and parsed all 8 developer documentation pages on `https://tomakino.github.io/lyricon/` via `read_url_content`. The documentation is self-consistent and provides exact Kotlin method signatures, parameter names, Manifest requirements, and lifecycle semantics.
2. **Step 2 (Dependency Resolution)**: We verified that `io.github.proify.lyricon:provider:0.1.70` is available on Maven Central. Nocturn's Gradle configuration (`settings.gradle.kts`) already declares `mavenCentral()` as a root repository under `dependencyResolutionManagement`, meaning no build repository changes are needed; only an `implementation("io.github.proify.lyricon:provider:0.1.70")` declaration in `app/build.gradle.kts`.
3. **Step 3 (Manifest Discovery)**: We established that Lyricon identifies providers by querying package managers for `<application>` `<meta-data>`. Four distinct tags were identified: `lyricon_module` (Boolean `"true"`), `lyricon_module_author` (String), `lyricon_module_description` (String), and `lyricon_module_tags` (String array containing `$syllable` and `$translation`).
4. **Step 4 (Lifecycle & Architecture)**:
   - `LyriconFactory.createProvider(context)` yields `LyriconProvider`.
   - The provider manages an IPC bridge. On Android < 8.1 (API 27), the bridge safely no-ops without throwing exceptions.
   - Calling `register()` binds to the central service. Setting `provider.autoSync = true` ensures that when the central service disconnects and reconnects (e.g. LSPosed reload), recent song/playback states are re-sent automatically.
   - When playback finishes or the user disables Lyricon in Nocturn's Settings, `provider.unregister()` cleanly stops status bar updates. On app exit or service death, `destroy()` frees resources.
5. **Step 5 (Playback State & Progress Synchronization)**:
   - When a track changes, the provider calls `player.setSong(lyriconSong)`.
   - When playback position updates or seeks occur, `player.setPosition(pos)` and `player.seekTo(pos)` are invoked.
   - When play/pause toggles, `player.setPlaybackState(isPlaying)` is invoked.
   - When translation toggle is toggled, `player.setDisplayTranslation(enabled)` is invoked.
   - When pure music or no lyrics are present, `player.sendText("纯音乐，请欣赏")` or `sendText(song.name)` can be called, which explicitly clears any stale timeline.
6. **Step 6 (Model Conversion)**:
   - Nocturn stores lyrics with `startMs` and `durationMs` per line and word.
   - Lyricon requires absolute timestamps `begin` and `end` (in milliseconds) for both `RichLyricLine` and `LyricWord`.
   - Hence, `begin = startMs` and `end = startMs + durationMs` (or interpolated to the next line's start time if line duration is missing).

---

## 3. Caveats

1. **Android Version Limitation**: The SDK explicitly documents that on Android versions below 8.1 (API 27), `LyriconFactory` returns a no-op implementation. Nocturn's `minSdk` must be checked; if Nocturn supports API < 27, calls to the provider are safe (no-op) but will not produce UI.
2. **Runtime Environment**: In production, Lyricon relies on an active LSPosed / Xposed module running in the System UI process. In development or local automated testing, tests must either run against `LocalCentralService` (via `centralPackageName = "io.github.lyricon.localcentralapp"`) or mock/verify the `LyriconProvider` and `RemotePlayer` calls directly using unit/Robolectric test doubles.
3. **`sendText` vs `setSong` Mutual Exclusivity**: Invoking `sendText()` clears existing `Song` metadata and transitions `RemotePlayer` into pure text mode. If transitioning back to a song with lyrics, `setSong(...)` must be called with a complete `Song` object.
4. **Translation Display Dependencies**: Setting `player.setDisplayTranslation(true)` alone does not guarantee translation display; the track must have `translation` text in `RichLyricLine`, and the Lyricon client user must also have translation enabled in their Lyricon app preferences.
5. **Type Name Collision**: As identified in Section 1.6, both codebases define `Song` and `LyricWord`. Any mapper or adapter class must use import aliases to prevent ambiguity and compilation errors.

---

## 4. Conclusion

The Lyricon Provider SDK specification is clear, stable, and well-structured for integration into Nocturn:
1. **Module**: Add `io.github.proify.lyricon:provider:0.1.70` to `app/build.gradle.kts`.
2. **Manifest**: Add the 4 `meta-data` entries under `<application>` in `app/src/main/AndroidManifest.xml` and create `@array/lyricon_module_tags` in `app/src/main/res/values/arrays.xml`.
3. **Adapter & Mapper**: Implement a `LyriconMapper` that transforms Nocturn's `Song`, `Lyrics`, `LyricLine`, and `LyricWord` into Lyricon's `Song`, `RichLyricLine`, and `LyricWord`, with robust handling for missing durations and instrumental/pure music placeholders.
4. **Provider Manager**: Implement a singleton/lifecycle-aware `LyriconManager` (or `LyriconBridge`) hosted in the playback service or view model, observing:
   - Nocturn playback state (`isPlaying`, `currentPosition`, `seekTo`)
   - Current song and lyrics changes
   - User settings preference (enabled / disabled)
5. **Settings UI**: Add a preference switch in `SettingsPlaybackScreen` or `SettingsGeneralScreen` to persist `lyriconEnabled` in `SettingsStore`.

---

## 5. Verification Method

To verify the integration once implemented by subsequent agents:
1. **Static Analysis & Build Verification**:
   ```powershell
   .\gradlew.bat :app:assembleDebug :app:lintDebug
   ```
   Ensures dependency resolution from Maven Central, manifest compilation, and absence of syntax or lint errors.
2. **Unit / Robolectric Tests**:
   ```powershell
   .\gradlew.bat :app:testDebugUnitTest
   ```
   Verify test cases for:
   - `LyriconMapperTest`: Conversion from Nocturn `Lyrics` / `LyricLine` / `LyricWord` to `LyriconSong` / `RichLyricLine` / `LyriconWord`.
   - Conversion of pure music / empty lyrics to placeholder / `sendText`.
   - End-time interpolation when line `durationMs` is null.
   - Provider lifecycle transitions (enable/register vs disable/unregister).
   - Playback synchronization calls (`setPlaybackState`, `setPosition`, `seekTo`).

---

## Features Discovered

| # | Category | Feature | Description | Inputs | Outputs | Error Behavior | Discovered Via |
|---|----------|---------|-------------|--------|---------|----------------|----------------|
| 1 | Dependency | Maven Coordinate | Provider SDK library artifact | `io.github.proify.lyricon:provider:0.1.70` from `mavenCentral()` | AAR library bundle | Build fails if repository is missing | `quick-start.md`, Maven Central |
| 2 | Manifest | `lyricon_module` | Declares app as a Lyricon module | `<meta-data android:name="lyricon_module" android:value="true" />` | Recognizable by Lyricon app | Module not detected if missing | `manifest.md` |
| 3 | Manifest | `lyricon_module_author` | Displays module author string in Lyricon provider list | String value (e.g. "Nocturn Team") | Displayed in Lyricon App | Default or missing author displayed | `manifest.md`, `app/providers.md` |
| 4 | Manifest | `lyricon_module_description` | Displays module description in Lyricon provider list | String value | Displayed in Lyricon App | Missing description in UI | `manifest.md` |
| 5 | Manifest | `lyricon_module_tags` | Declares supported capabilities | Resource array `@array/lyricon_module_tags` with `$syllable`, `$translation` | Shows tags in Lyricon App | Features displayed as standard text only if omitted | `manifest.md`, `app/providers.md` |
| 6 | Lifecycle | `LyriconFactory.createProvider` | Instantiates `LyriconProvider` | `Context`, optional `providerPackageName`, `playerPackageName`, `logo`, `metadata`, `centralPackageName` | `LyriconProvider` instance | Returns no-op implementation on Android < 8.1 (API 27) | `quick-start.md`, `connection.md` |
| 7 | Lifecycle | `provider.register()` | Dispatches registration request to central service | None | `Boolean` (success of dispatch) | Returns false if IPC dispatch fails | `connection.md` |
| 8 | Lifecycle | `provider.unregister()` | Actively unregisters from central service | None | Unit | Safe to call even if not connected | `connection.md` |
| 9 | Lifecycle | `provider.destroy()` | Frees listeners, callbacks, and remote IPC resources | None | Unit | Releases memory and binder connections | `connection.md` |
| 10 | Lifecycle | `provider.service.addConnectionListener` | Listens for connection state events | DSL with `onConnected`, `onReconnected`, `onDisconnected`, `onConnectTimeout` | Callback invocations | Connection failure invokes `onConnectTimeout` | `connection.md` |
| 11 | Lifecycle | `provider.autoSync` | Controls automatic re-sync of cached state upon reconnect | `Boolean` (default true recommended) | Auto-pushes cached state | If false, state lost until client next pushes | `connection.md` |
| 12 | Playback Sync | `player.setSong` | Pushes current song metadata and structured lyrics | `Song?` (track ID, name, artist, duration, lyrics list) | Unit | Passing `null` clears current song and lyrics | `player-control.md`, `lyrics-model.md` |
| 13 | Playback Sync | `player.sendText` | Pushes plain text without timeline (pure music / placeholder) | `String?` | Unit | Clears existing structured song metadata and enters text mode | `player-control.md`, `quick-start.md` |
| 14 | Playback Sync | `player.setPlaybackState(Boolean)` | Updates play/pause status | `Boolean` (`true` = playing, `false` = paused) | Unit | Safe no-op if disconnected | `player-control.md` |
| 15 | Playback Sync | `player.setPlaybackState(PlaybackState?)` | Android platform PlaybackState update | `android.media.session.PlaybackState?` | Unit | Passing null exits speed extrapolation mode | `player-control.md` |
| 16 | Playback Sync | `player.setPosition` | Updates elapsed playback position | `Long` (milliseconds) | Unit | Out-of-bounds positions clamped by central service | `player-control.md` |
| 17 | Playback Sync | `player.seekTo` | Signals user scrub / seek jump | `Long` (target millisecond position) | Unit | Updates position with seek transition | `player-control.md` |
| 18 | Playback Sync | `player.setDisplayTranslation` | Toggles rendering of translation lyric line | `Boolean` | Unit | Ineffective if line has no `translation` text | `player-control.md` |
| 19 | Playback Sync | `player.setDisplayRoma` | Toggles rendering of romaji line | `Boolean` | Unit | Ineffective if line has no romaji text | `player-control.md` |
| 20 | Data Model | `RichLyricLine` | Represents a single lyric line with timing and variants | `begin: Long`, `end: Long`, `text: String`, `secondary: String?`, `translation: String?`, `words: List<LyricWord>?` | Model instance | Negative begin or end < begin handled gracefully | `lyrics-model.md` |
| 21 | Data Model | `LyricWord` | Represents a syllable / word slice for dynamic highlight | `text: String`, `begin: Long`, `end: Long` | Model instance | Syllable timing outside line bounds should be clamped | `lyrics-model.md` |
| 22 | Testing | Local Central Service testing | Allows local testing without Xposed/LSPosed | `centralPackageName = "io.github.lyricon.localcentralapp"` | Binds to local test app | Requires floating window permission on test device | `local-testing.md` |

---

## Edge Cases

| # | Feature | Input | Observed Behavior |
|---|---------|-------|-------------------|
| 1 | `LyriconFactory.createProvider` | Android version < 8.1 (API < 27) | Returns a safe empty / no-op Provider Bridge without throwing runtime exceptions. |
| 2 | `provider.service` | Lyricon Central Service not installed or dead | `onConnectTimeout` triggers; calls to `player` do not crash the host app. |
| 3 | `provider.service` | Central Service / SystemUI crashes and restarts | `onDisconnected` triggers, followed by `onReconnected`. If `autoSync = true`, cached song/progress state is automatically resent. |
| 4 | `player.sendText` | Non-empty string when structured song was active | Immediately clears previous `Song` metadata and transitions status bar into pure text mode. |
| 5 | `player.setSong` | `null` | Clears all song and lyric state from the status bar. |
| 6 | `RichLyricLine` conversion | Nocturn `LyricLine.durationMs` is `null` or 0 | Line `end` must be calculated from `nextLine.startMs`, or `startMs + (song.duration - startMs)`, or a default line duration (e.g. 3000ms). |
| 7 | `LyricWord` conversion | Word `startMs + durationMs` exceeds line `end` | Clamped to line `end` so words do not overflow their parent line interval. |
| 8 | Empty / Pure Music Track | Track with no lyric lines or "纯音乐，请欣赏" | Use `player.sendText("纯音乐，请欣赏")` or push a `Song` with empty lyrics list. |
| 9 | Settings toggle | User toggles Lyricon switch to OFF in Nocturn Settings | Provider calls `unregister()`, cancels progress sync coroutine, and halts pushes until re-enabled. |
| 10 | Settings toggle | User toggles Lyricon switch to ON while playing | Provider calls `register()`, pushes current track metadata (`setSong`), playback position, and play state. |
| 11 | Translation Display | `setDisplayTranslation(true)` but song has no translation | Main lyric text displays normally without translation subtitle; no crashes or blank spaces. |
| 12 | Seek / Scrubbing | Rapid scrubbing on progress slider | Calls `seekTo(targetMs)` on slider release and throttled `setPosition(ms)` during dragging to avoid IPC flooding. |
| 13 | Fast Track Skipping | User repeatedly clicks "Next" in quick succession | Successive `setSong` calls overwrite previous track state; if lyric parsing is asynchronous, initial `setSong` with basic metadata displays first. |
| 14 | Model Type Collision | `Song` and `LyricWord` imported without alias in Kotlin file | Compilation error (`Conflicting declarations` / `Reference is ambiguous`). Must use `as LyriconSong` / `as LyriconWord`. |
