# Investigation Report: Lyrics Data Models, Parsing Pipeline & Settings Architecture for Lyricon Integration

**Target**: Nocturn Android Client (`dev.nocturn.app`)  
**Working Directory**: `d:\Nocturn\.agents\teamwork\explorer_survey_3`  
**Date**: 2026-11-07  
**Author**: teamwork_explorer (Survey Agent 3)

---

## 1. Observation

Direct observations from examining the Nocturn codebase, test suites, test fixtures, and official Lyricon SDK documentation:

### 1.1 Lyrics Data Models in Nocturn
Nocturn uses a two-tier data architecture for lyrics: a shared Multiplatform parser layer (`dev.nocturn.localapi.client`) and an application domain model layer (`dev.nocturn.app.data.model`).

#### 1.1.1 LocalAPI Shared Model (`local-api/src/commonMain/kotlin/dev/nocturn/localapi/client/LyricParser.kt`)
Lines 8–17:
```kotlin
data class LyricWord(val startTimeMs: Long, val durationMs: Long, val text: String)

data class LyricLine(
    val startTimeMs: Long,
    val durationMs: Long?,
    val text: String,
    val words: List<LyricWord> = emptyList(),
    val translation: String? = null,
    val romanization: String? = null,
)

data class SongLyrics(val lines: List<LyricLine>, val wordSynced: Boolean, val instrumental: Boolean)
```

#### 1.1.2 Application Domain Model (`app/src/main/kotlin/dev/nocturn/app/data/model/Models.kt`)
Lines 180–221:
```kotlin
data class LyricWord(
    val startMs: Long,
    val durationMs: Long,
    val text: String,
)

data class LyricLine(
    val startMs: Long,
    val text: String,
    val translation: String? = null,
    val durationMs: Long? = null,
    val words: List<LyricWord> = emptyList(),
    val romanization: String? = null,
)

data class Lyrics(
    val lines: List<LyricLine> = emptyList(),
) {
    val isEmpty: Boolean get() = lines.isEmpty()

    /** Index of the last line whose start time is at or before [positionMs]. */
    fun indexAt(positionMs: Long): Int {
        if (lines.isEmpty()) return -1
        var low = 0
        var high = lines.size - 1
        var result = -1
        while (low <= high) {
            val mid = (low + high).ushr(1)
            if (lines[mid].startMs <= positionMs) {
                result = mid
                low = mid + 1
            } else {
                high = mid - 1
            }
        }
        return result
    }

    companion object {
        val EMPTY = Lyrics()
    }
}
```

### 1.2 Parsing Pipeline: LRC, YRC, Syllables, and Translations

#### 1.2.1 Upstream Raw Payload Structure
From `app/src/test/resources/fixtures/lyric_new.json`:
```json
{
  "code": 200,
  "lrc": {
    "version": 1,
    "lyric": "[00:16.21]还没有\n[00:20.50]结束"
  },
  "tlyric": {
    "version": 1,
    "lyric": "[00:16.21]Not yet\n[00:20.50]Finished"
  },
  "yrc": {
    "version": 1,
    "lyric": "[16210,3460](16210,670,0)还(16880,410,0)没(17290,320,0)有\n[20500,1000](20500,500,0)结(21000,500,0)束"
  },
  "ytlrc": {
    "version": 1,
    "lyric": "[16210,3460](16210,670,0)Not yet\n[20500,1000](20500,500,0)Finished"
  }
}
```

#### 1.2.2 `LyricParser` Pipeline (`local-api/.../LyricParser.kt:20-98`)
- **LRC Parser (`parseLrc`)**:
  - Matches `lrcTime = Regex("\\[(\\d{1,6}):([0-5]\\d)(?:[.:](\\d{1,3}))?]")`.
  - Supports `[offset:+-ms]`.
  - Supports repeated timestamp headers on a single line (e.g. `[00:01.00][00:05.00]Chorus`).
  - Automatically calculates line `durationMs` as `lines[index + 1].startTimeMs - line.startTimeMs`. For the last line, `durationMs` is `null`.
  - Filters out JSON credit metadata blocks starting with `{` (e.g. `{"t":0,"c":[{"tx":"作曲"}]}`).
- **YRC Parser (`parseYrc`)**:
  - Matches line header `yrcLine = Regex("^\\[(\\d+),(\\d+)](.*)$")` representing `[startMs, durationMs]lineContent`.
  - Matches word markers `yrcWord = Regex("\\((\\d+),(\\d+),\\d+\\)")` representing `(wordStartMs, wordDurationMs, flag)wordText`.
  - Word start timestamps are **absolute millisecond timestamps**, not relative offsets.
  - Full line text is derived by joining word texts: `words.joinToString("") { it.text }`.
  - Validates that `wordStart >= lineStart` and `wordStart <= lineStart + lineDuration`.
- **Parsing Orchestration (`parse`)**:
  - Checks `val yrc = parseYrc(text(response, "yrc"))`.
  - If `yrc.isNotEmpty()`, `wordSynced = true` and original lines use YRC.
  - If YRC is empty or malformed, falls back to `parseLrc(text(response, "lrc"))`, `wordSynced = false`.
  - **Translation selection**: If `wordSynced`, reads `ytlrc` (falls back to `tlyric`); else reads `tlyric`.
  - **Romanization selection**: If `wordSynced`, reads `yromalrc` (falls back to `romalrc`); else reads `romalrc`.
  - **Timestamp alignment**: Uses binary search `nearest(lines, time)` with a tolerance of `|t_trans - t_orig| <= 500ms`.
  - **Instrumental check**: True if `response["nolyric"] == true` or any line text equals `"纯音乐，请欣赏"` or `"纯音乐，请欣赏。"`.

#### 1.2.3 Data Layer Normalization (`app/.../data/NeteaseJson.kt:455-472`)
```kotlin
fun parseLyrics(body: JsonObject): Lyrics {
    val parsed = LyricParser.parse(body)
    if (parsed.instrumental) return Lyrics.EMPTY
    val lines = parsed.lines.mapNotNull { line ->
        val text = line.text.trim()
        if (text.isEmpty()) return@mapNotNull null
        LyricLine(
            startMs = line.startTimeMs.coerceAtLeast(0L),
            text = text,
            translation = line.translation?.trim()?.takeIf { it.isNotEmpty() },
            durationMs = line.durationMs,
            words = line.words.map { LyricWord(it.startTimeMs, it.durationMs, it.text) },
            romanization = line.romanization?.trim()?.takeIf { it.isNotEmpty() },
        )
    }.sortedBy { it.startMs }
    return if (lines.isEmpty()) Lyrics.EMPTY else Lyrics(lines)
}
```

### 1.3 Repository & ViewModel Lyrics Retrieval Flow

#### 1.3.1 `LocalMusicRepository.lyrics(songId)` (`app/.../data/LocalMusicRepository.kt:406-432`)
- Prepares request with `id = songId.toString()` and optional user session cookie.
- Tries `LocalApiEndpoint.LyricNew` (primary).
- If primary result is null or empty (`primaryLyrics.isEmpty`), falls back to legacy `LocalApiEndpoint.Lyric`.
- Returns `Lyrics`.

#### 1.3.2 `NocturnViewModel` Lyrics Trigger & UI Observation
- `loadLyrics(song: Song)` (`NocturnViewModel.kt:1128-1146`):
  ```kotlin
  private fun loadLyrics(song: Song) {
      currentLyricsSongId = song.id
      lyricsRequest?.cancel()
      mutablePlayer.update { it.copy(lyrics = Lyrics.EMPTY, lyricsLoading = true) }
      lyricsRequest = viewModelScope.launch {
          try {
              val lyrics = repository.lyrics(song.id)
              if (mutablePlayer.value.song?.id == song.id) {
                  mutablePlayer.update { it.copy(lyrics = lyrics, lyricsLoading = false) }
              }
          } catch (cancelled: CancellationException) {
              throw cancelled
          } catch (_: Exception) {
              if (mutablePlayer.value.song?.id == song.id) {
                  mutablePlayer.update { it.copy(lyrics = Lyrics.EMPTY, lyricsLoading = false) }
              }
          }
      }
  }
  ```
- Called in 3 scenarios:
  1. `startPlayback` (`NocturnViewModel.kt:975`): When user manually starts playback of a song.
  2. `applyPlaybackSnapshot` (`NocturnViewModel.kt:1105-1110`): When `engine.snapshot` notifies that the current track changed (`songChanged || currentLyricsSongId != song.id`).
  3. `openPlayer` (`NocturnViewModel.kt:218-220`): When expanding full player sheet if lyrics were not yet loaded.
- `PlayerUiState` (`app/.../ui/state/PlayerUiState.kt:14-41`) contains:
  `val lyrics: Lyrics = Lyrics.EMPTY`, `val lyricsLoading: Boolean = false`.
- `LyricsView.kt` and `AccompanistLyrics.kt:11-48` convert `Lyrics` to `SyncedLyrics` using `toAccompanistLyrics(durationMs)` and track position via `interpolatedLyricsPosition(...)`.

### 1.4 Nocturn Settings Architecture & UI

#### 1.4.1 Storage Mechanism (`app/.../data/SettingsStore.kt`)
- `SettingsStore` uses private Android `SharedPreferences("nocturn-settings", Context.MODE_PRIVATE)`.
- Explicit separation:
  - Account credentials & cookies -> `SessionStore` (encrypted with AES-256-GCM via Android KeyStore).
  - App preferences (theme, dynamic color, quality, haptics, search history) -> `SettingsStore` (unencrypted SharedPreferences).
- Currently persists:
  - `themeMode: ThemeMode` (KEY: `"theme-mode"`)
  - `dynamicColor: Boolean` (KEY: `"dynamic-color"`, default `true`)
  - `quality: AudioQuality` (KEY: `"audio-quality"`, default `AudioQuality.Exhigh`)
  - `hapticsEnabled: Boolean` (KEY: `"haptics-enabled"`, default `true`)
  - `searchHistory: List<String>` (KEY: `"search-history"`)

#### 1.4.2 State Propagation & UI Exposure
- `AppContainer` (`NocturnApplication.kt:16`) holds singleton `val settings: SettingsStore = SettingsStore(context)`.
- `NocturnViewModel.kt:80-87` initializes `LibraryUiState` with settings values:
  ```kotlin
  private val mutableLibrary = MutableStateFlow(
      LibraryUiState(
          themeMode = settings.themeMode,
          dynamicColor = settings.dynamicColor,
          quality = settings.quality,
          hapticsEnabled = settings.hapticsEnabled,
      ),
  )
  ```
- Mutator methods in `NocturnViewModel.kt:897-918`:
  `setQuality`, `setThemeMode`, `setDynamicColor`, `setHapticsEnabled`.
  Each mutator writes to `settings.*` and updates `mutableLibrary`.
- Settings UI Surfaces in Nocturn:
  1. **Primary in-app UI (`LibraryScreen.kt:646-805`)**:
     `LibrarySettingsSection` renders directly on the "我的" (Library) tab with Material 3 Expressive components:
     - `FilterChip`s for Audio Quality.
     - `SingleChoiceSegmentedButtonRow` for Theme Mode.
     - `Switch` for Dynamic Color (`testTag("library-dynamic-color-switch")`).
     - `Switch` for Tactile Haptics (`testTag("library-haptics-switch")`).
  2. **Detailed Secondary Sub-pages (`ui/screens/settings/`)**:
     - `SettingsScreen.kt`: Root settings destination with menus (`menu-playback`, `menu-appearance`, `menu-general`, `menu-storage`, `menu-about`).
     - `SettingsPlaybackScreen.kt`: Audio quality preference cards.
     - `SettingsGeneralScreen.kt`: Tactile feedback switch (`testTag("settings-haptics-switch")`) and gesture details.
     - `SettingsUiState.kt`: Encapsulates `quality`, `themeMode`, `dynamicColor`, `hapticsEnabled`, `cacheSizeBytes`, `version`.

### 1.5 Lyricon Provider SDK Data Models
From `https://tomakino.github.io/lyricon/zh-cn/developer/provider/lyrics-model`:
- **`Song` (`io.github.proify.lyricon.lyric.model.Song`)**:
  - `id: String?`: Unique stable song identifier.
  - `name: String`: Song title.
  - `artist: String?`: Artist name.
  - `duration: Long?`: Song duration in milliseconds.
  - `lyrics: List<RichLyricLine>?`: List of lyric lines.
- **`RichLyricLine` (`io.github.proify.lyricon.lyric.model.RichLyricLine`)**:
  - `begin: Long`: Line start timestamp in milliseconds.
  - `end: Long`: Line end timestamp in milliseconds.
  - `text: String`: Main lyric line text.
  - `secondary: String?`: Optional secondary/background text.
  - `translation: String?`: Optional translated lyric text.
  - `words: List<LyricWord>?`: Optional syllable/word slices for dynamic sweep.
- **`LyricWord` (`io.github.proify.lyricon.lyric.model.LyricWord`)**:
  - `text: String`: Syllable or word text.
  - `begin: Long`: Start timestamp in milliseconds.
  - `end: Long`: End timestamp in milliseconds.
- **`RemotePlayer` (`provider.player`)**:
  - `setSong(Song?)`: Sets active song and full lyrics.
  - `sendText(String?)`: Clears structured song, enters plain text mode (pure music).
  - `setPosition(Long)`: Synchronizes playback position (ms).
  - `seekTo(Long)`: Emits scrub / jump event (ms).
  - `setPlaybackState(Boolean)`: Synchronizes play/pause state.
  - `setDisplayTranslation(Boolean)`: Controls translation visibility on status bar.

---

## 2. Logic Chain

From the direct observations above, we establish the following chain of deduction:

### 2.1 Critical Data Model Differences and Mapping Derivation

1. **Word-Level Timing**:
   - *Observation*: Nocturn's `LyricWord` stores `startMs: Long` and `durationMs: Long`. Lyricon's `LyricWord` stores `begin: Long` and `end: Long`.
   - *Deduction*: Lyricon requires absolute timestamps for both begin and end.
     $$\text{begin} = \text{startMs}$$
     $$\text{end} = \text{startMs} + \text{durationMs}$$
   - *Safety Constraint*: If upstream data contains a malformed duration (e.g. `durationMs <= 0`), `end` must be coerced to at least `begin + 1L`. Furthermore, `end` must not exceed parent `RichLyricLine.end`.

2. **Line-Level Timing**:
   - *Observation*: In Nocturn's `LyricLine`, `durationMs: Long?` is nullable because ordinary LRC lines do not specify line lengths; only YRC or post-processed lines have explicit durations.
   - *Observation*: In Lyricon's `RichLyricLine`, `begin` and `end` are both required non-null `Long` values.
   - *Observation*: `AccompanistLyrics.kt:13-20` solves this by calculating:
     ```kotlin
     val start = line.startMs.coerceAtLeast(0L)
     val nextStart = sorted.getOrNull(index + 1)?.startMs
     val length = line.durationMs?.takeIf { it > 0L }
         ?: nextStart?.let { (it - start).coerceAtLeast(1L) }
         ?: (songDurationMs - start).takeIf { it > 0L }
         ?: 5_000L
     val end = start + length
     ```
   - *Deduction*: The exact same robust formula should be used for Lyricon `RichLyricLine(begin = start, end = end)`.

3. **Translation & Romanization Handling**:
   - *Observation*: Nocturn's `LyricParser` extracts and aligns translations (`ytlrc` / `tlyric`) and romanizations (`yromalrc` / `romalrc`) into `LyricLine.translation` and `LyricLine.romanization`.
   - *Observation*: Lyricon's `RichLyricLine` has a dedicated `translation: String?` property, and `RemotePlayer` has `setDisplayTranslation(Boolean)`.
   - *Observation*: Lyricon also has `secondary: String?` and `setDisplayRoma(Boolean)`.
   - *Deduction*:
     - Map `line.translation?.takeIf { it.isNotBlank() }` directly to `RichLyricLine.translation`.
     - `line.romanization?.takeIf { it.isNotBlank() }` can be mapped to `RichLyricLine.secondary` or reserved for romanization support.
     - Call `player.setDisplayTranslation(true)` so Lyricon enables translation rendering when translation lines exist.

4. **Pure Music & Instrumental Tracks**:
   - *Observation*: Nocturn's `NeteaseJson.parseLyrics` returns `Lyrics.EMPTY` when `instrumental == true` or when no lines exist.
   - *Observation*: In `LyricParser.kt:38-39`, instrumental is detected by `nolyric == true` or text `"纯音乐，请欣赏"`.
   - *Observation*: Lyricon's documentation states that calling `player.sendText(text)` clears previous song structure and renders plain text without timeline on the status bar.
   - *Deduction*:
     - When `song` is instrumental or `lyrics.isEmpty`:
       Call `player.sendText("纯音乐，请欣赏")` or push `Song(..., lyrics = emptyList())`.
       `sendText("纯音乐，请欣赏")` is the idiomatic Lyricon approach for non-lyric audio.

5. **Type Name Collision & Packaging**:
   - *Observation*: Both Nocturn (`dev.nocturn.app.data.model`) and Lyricon (`io.github.proify.lyricon.lyric.model`) define top-level classes named `Song` and `LyricWord`.
   - *Deduction*: Any mapper class or file importing both models must use type aliases to prevent ambiguous reference compiler errors:
     ```kotlin
     import dev.nocturn.app.data.model.Song as NocturnSong
     import dev.nocturn.app.data.model.Lyrics as NocturnLyrics
     import dev.nocturn.app.data.model.LyricLine as NocturnLyricLine
     import dev.nocturn.app.data.model.LyricWord as NocturnLyricWord
     import io.github.proify.lyricon.lyric.model.Song as LyriconSong
     import io.github.proify.lyricon.lyric.model.RichLyricLine as LyriconRichLine
     import io.github.proify.lyricon.lyric.model.LyricWord as LyriconWord
     ```

### 2.2 Settings Toggle & Persistence Architecture

1. **Storage Choice**:
   - *Observation*: `SettingsStore` is already the unified home for non-encrypted user preferences in Nocturn.
   - *Deduction*: `KEY_LYRICON_ENABLED = "lyricon-enabled"` should be added to `SettingsStore`, defaulting to `true` (or `false` based on product preference; `true` allows seamless discovery when Lyricon is installed).

2. **Reactive UI State Flow**:
   - *Observation*: `LibraryScreen` is Nocturn's main user-facing surface for instant settings changes (Theme, Dynamic Color, Haptics).
   - *Observation*: `SearchLibraryScreensTest` and `ScreenshotGalleryTest` directly test and render `LibraryScreen`'s settings section.
   - *Deduction*:
     - Add `lyriconEnabled: Boolean = true` to `LibraryUiState` and `SettingsUiState`.
     - In `NocturnViewModel`, add `fun setLyriconEnabled(enabled: Boolean)`.
     - In `LibraryScreen.kt`, add a `Switch` row in `LibrarySettingsSection` with test tag `testTag("library-lyricon-switch")`.
     - In `SettingsPlaybackScreen.kt` (or `SettingsGeneralScreen.kt`), expose a matching switch with `testTag("settings-playback-lyricon-switch")`.

3. **Background Lifecycle Independence**:
   - *Observation*: `NocturnViewModel` is destroyed when `MainActivity` is destroyed (e.g. user presses Back to exit), while audio playback continues in background `PlaybackService`.
   - *Observation*: Survey 2 established that `AppContainer` in `NocturnApplication` is process-scoped and hosts `LyriconManager`.
   - *Deduction*:
     - `SettingsStore.lyriconEnabled` change should be observed by `LyriconManager` (e.g. via a `StateFlow<Boolean>` or a direct callback `lyriconManager.setEnabled(enabled)` called from `NocturnViewModel`).
     - When toggled `OFF`: `lyriconManager` calls `provider.unregister()`, cancels progress loops, and stops pushing data.
     - When toggled `ON`: `lyriconManager` calls `provider.register()` and resynchronizes the current song and position.

---

## 3. Caveats

1. **Dual Lyrics Consumption (ViewModel vs Background Manager)**:
   - When the user opens Nocturn, `NocturnViewModel.loadLyrics` fetches `Lyrics` for the on-screen karaoke renderer.
   - If the user leaves Nocturn and background song transitions occur, `NocturnViewModel` is inactive. The process-level `LyriconManager` in `AppContainer` must be able to call `repository.lyrics(songId)` directly.
   - To avoid redundant network requests when both the ViewModel and `LyriconManager` request lyrics for the same song, `LocalMusicRepository` would benefit from a simple in-memory cache of the latest parsed `Lyrics`.
2. **Lyricon SDK Threading**:
   - `RemotePlayer` IPC calls (`setSong`, `setPosition`, `seekTo`, `setPlaybackState`) are thread-safe and non-blocking AIDL calls, but should preferably be invoked from the main dispatcher or a dedicated playback scope to preserve chronological call order.
3. **Missing Word Timing in Partial YRC**:
   - Occasionally, a NetEase YRC line might contain plain text without markers (e.g. spoken interlude or un-sliced Chinese characters). If `line.words` is empty, `RichLyricLine.words` must be set to `null` so Lyricon falls back to standard line-level highlight rather than failing validation.
4. **Platform Compatibility**:
   - On Android versions below 8.1 (API 27), Lyricon Provider SDK returns a no-op implementation. Because Nocturn pins `minSdk = 29` (Android 10), this platform limitation will never affect Nocturn users.

---

## 4. Conclusion

### 4.1 Detailed Mapping Specification

Here is the exact mapping from Nocturn's domain models to Lyricon's Provider models:

#### Mapping Matrix

| Nocturn Source (`dev.nocturn.app.data.model`) | Lyricon Target (`io.github.proify.lyricon.lyric.model`) | Transformation / Derivation Logic |
|---|---|---|
| `Song.id: Long` | `Song.id: String?` | `song.id.toString()` |
| `Song.name: String` | `Song.name: String` | `song.name` |
| `Song.artistText: String` | `Song.artist: String?` | `song.artistText` |
| `Song.durationMs: Long` | `Song.duration: Long?` | `song.durationMs` |
| `Lyrics.lines: List<LyricLine>` | `Song.lyrics: List<RichLyricLine>?` | `toLyriconLines(lyrics, song.durationMs).takeIf { it.isNotEmpty() }` |
| `LyricLine.startMs: Long` | `RichLyricLine.begin: Long` | `line.startMs.coerceAtLeast(0L)` |
| `LyricLine.durationMs: Long?` | `RichLyricLine.end: Long` | Line duration or fallback: `startMs + (line.durationMs ?: (nextStart - startMs) ?: (songDuration - startMs) ?: 5000L)` |
| `LyricLine.text: String` | `RichLyricLine.text: String` | `line.text` |
| `LyricLine.translation: String?` | `RichLyricLine.translation: String?` | `line.translation?.takeIf { it.isNotBlank() }` |
| `LyricLine.romanization: String?` | `RichLyricLine.secondary: String?` | `line.romanization?.takeIf { it.isNotBlank() }` (or `null`) |
| `LyricLine.words: List<LyricWord>` | `RichLyricLine.words: List<LyricWord>?` | `line.words.map { ... }.takeIf { it.isNotEmpty() }` |
| `LyricWord.text: String` | `LyricWord.text: String` | `word.text` |
| `LyricWord.startMs: Long` | `LyricWord.begin: Long` | `word.startMs.coerceIn(lineBegin, lineEnd)` |
| `LyricWord.durationMs: Long` | `LyricWord.end: Long` | `(word.startMs + word.durationMs.coerceAtLeast(1L)).coerceIn(wordBegin, lineEnd)` |

#### Complete Production-Ready Mapper Implementation: `LyriconModelMapper.kt`

```kotlin
package dev.nocturn.app.playback.lyricon

import dev.nocturn.app.data.model.LyricLine as NocturnLyricLine
import dev.nocturn.app.data.model.LyricWord as NocturnLyricWord
import dev.nocturn.app.data.model.Lyrics as NocturnLyrics
import dev.nocturn.app.data.model.Song as NocturnSong
import io.github.proify.lyricon.lyric.model.LyricWord as LyriconWord
import io.github.proify.lyricon.lyric.model.RichLyricLine as LyriconRichLine
import io.github.proify.lyricon.lyric.model.Song as LyriconSong

object LyriconModelMapper {

    private const val DEFAULT_LINE_DURATION_MS = 5_000L

    /**
     * Converts Nocturn's domain [NocturnSong] and parsed [NocturnLyrics] into Lyricon's [LyriconSong].
     * If [lyrics] is empty or has no valid lines, [LyriconSong.lyrics] is null.
     */
    fun toLyriconSong(
        song: NocturnSong,
        lyrics: NocturnLyrics = NocturnLyrics.EMPTY,
    ): LyriconSong {
        val lines = toLyriconLines(lyrics, song.durationMs)
        return LyriconSong(
            id = song.id.toString(),
            name = song.name,
            artist = song.artistText,
            duration = song.durationMs,
            lyrics = lines.ifEmpty { null },
        )
    }

    /**
     * Converts Nocturn's [NocturnLyrics] into a list of Lyricon [LyriconRichLine]s.
     * Computes line end boundaries and transforms syllable-level timing.
     */
    fun toLyriconLines(
        lyrics: NocturnLyrics,
        songDurationMs: Long,
    ): List<LyriconRichLine> {
        val sorted = lyrics.lines.sortedBy { it.startMs }
        return sorted.mapIndexed { index, line ->
            val start = line.startMs.coerceAtLeast(0L)
            val nextStart = sorted.getOrNull(index + 1)?.startMs
            val length = line.durationMs?.takeIf { it > 0L }
                ?: nextStart?.let { (it - start).coerceAtLeast(1L) }
                ?: (songDurationMs - start).takeIf { it > 0L }
                ?: DEFAULT_LINE_DURATION_MS
            val end = (start + length).coerceAtLeast(start + 1L)

            val words = if (line.words.isNotEmpty()) {
                line.words.sortedBy { it.startMs }.map { word ->
                    val wordBegin = word.startMs.coerceIn(start, end)
                    val rawEnd = word.startMs + word.durationMs.coerceAtLeast(1L)
                    val wordEnd = rawEnd.coerceIn(wordBegin, end)
                    LyriconWord(
                        text = word.text,
                        begin = wordBegin,
                        end = wordEnd,
                    )
                }
            } else {
                null
            }

            LyriconRichLine(
                begin = start,
                end = end,
                text = line.text,
                secondary = line.romanization?.takeIf { it.isNotBlank() },
                translation = line.translation?.takeIf { it.isNotBlank() },
                words = words,
            )
        }
    }
}
```

### 4.2 Settings Storage & UI Integration Plan

#### 1. In `SettingsStore.kt` (`app/src/main/kotlin/dev/nocturn/app/data/SettingsStore.kt`)
Add property:
```kotlin
/** Whether status bar lyrics push via Lyricon Provider is enabled. On by default. */
var lyriconEnabled: Boolean
    get() = preferences.getBoolean(KEY_LYRICON_ENABLED, true)
    set(value) {
        preferences.edit().putBoolean(KEY_LYRICON_ENABLED, value).apply()
    }

private companion object {
    ...
    const val KEY_LYRICON_ENABLED = "lyricon-enabled"
}
```

#### 2. In `LibraryUiState.kt` and `SettingsUiState.kt`
- In `LibraryUiState`:
  ```kotlin
  val lyriconEnabled: Boolean = true,
  ```
- In `SettingsUiState`:
  ```kotlin
  val lyriconEnabled: Boolean = true,
  ```

#### 3. In `NocturnViewModel.kt`
- In `mutableLibrary` initialization:
  ```kotlin
  lyriconEnabled = settings.lyriconEnabled,
  ```
- Add mutator:
  ```kotlin
  fun setLyriconEnabled(enabled: Boolean) {
      settings.lyriconEnabled = enabled
      mutableLibrary.update { it.copy(lyriconEnabled = enabled) }
      container.lyriconManager.setEnabled(enabled)
  }
  ```

#### 4. In `LibraryScreen.kt` (`LibrarySettingsSection`)
Add a Switch item:
```kotlin
Spacer(Modifier.height(20.dp))
Row(
    modifier = Modifier.fillMaxWidth(),
    verticalAlignment = Alignment.CenterVertically,
) {
    Column(modifier = Modifier.weight(1f)) {
        Text(
            text = "词幕歌词",
            style = MaterialTheme.typography.titleSmall,
            color = MaterialTheme.colorScheme.onSurface,
        )
        Text(
            text = "向系统状态栏词幕应用推送歌曲信息与逐字歌词",
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
    }
    Switch(
        checked = lyriconEnabled,
        onCheckedChange = { enabled ->
            switchHaptics.toggle(enabled)
            onLyriconEnabledChange(enabled)
        },
        modifier = Modifier.testTag("library-lyricon-switch"),
    )
}
```

---

## 5. Verification Method

To independently verify all findings and models:

### 5.1 Verification Commands
1. **Robolectric & Unit Test Suite**:
   ```powershell
   .\gradlew.bat :app:testDebugUnitTest --tests "dev.nocturn.app.data.NeteaseJsonLyricsTest"
   .\gradlew.bat :app:testDebugUnitTest --tests "dev.nocturn.app.ui.AccompanistLyricsTest"
   .\gradlew.bat :app:testDebugUnitTest --tests "dev.nocturn.app.ui.HapticsTest"
   ```
2. **KMP Host Test Suite**:
   ```powershell
   .\gradlew.bat :local-api:hostTest --tests "dev.nocturn.localapi.client.LyricParserTest"
   ```
3. **Compilation & Static Lint**:
   ```powershell
   .\gradlew.bat :app:assembleDebug :app:lintDebug
   ```

### 5.2 Verification Files to Inspect
- `local-api/src/commonMain/kotlin/dev/nocturn/localapi/client/LyricParser.kt`: Inspect lines 8–17 and 26–82 for shared data structures and timestamp parsing regexes.
- `app/src/main/kotlin/dev/nocturn/app/data/model/Models.kt`: Inspect lines 180–221 for domain `LyricWord`, `LyricLine`, and `Lyrics`.
- `app/src/main/kotlin/dev/nocturn/app/data/SettingsStore.kt`: Inspect lines 11–67 for `SharedPreferences` preference storage.
- `app/src/main/kotlin/dev/nocturn/app/ui/screens/library/LibraryScreen.kt`: Inspect lines 646–805 for settings switch layout pattern.

### 5.3 Invalidation Conditions
- If Lyricon SDK changes `LyricWord` from `begin`/`end` to `startTime`/`duration` (invalidation checked: `io.github.proify.lyricon:provider:0.1.70` uses `begin: Long` and `end: Long`).
- If Nocturn's `minSdk` is lower than 27 (invalidation checked: `app/build.gradle.kts:85` specifies `minSdk = 29`).
- If `SettingsStore` is replaced with DataStore or Room (invalidation checked: `SettingsStore` is standard `SharedPreferences`).
