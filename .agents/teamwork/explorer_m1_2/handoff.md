# LyriconModelMapper Investigation & Design Report

## 1. Observation

### 1.1 Nocturn Domain Models
Observed in `app/src/main/kotlin/dev/nocturn/app/data/model/Models.kt`:
- **`Song`** (lines 22–49):
  ```kotlin
  data class Song(
      val id: Long,
      val name: String,
      val artists: List<ArtistRef>,
      val album: AlbumRef?,
      val durationMs: Long,
      val fee: Int? = null,
      val coverUrl: String? = null,
      val alias: String? = null,
  ) {
      val artistText: String
          get() = artists.joinToString(" / ") { it.name }.ifBlank { "未知歌手" }
      val cover: String?
          get() = coverUrl ?: album?.coverUrl
  }
  ```
- **`LyricWord`** (lines 180–184):
  ```kotlin
  data class LyricWord(
      val startMs: Long,
      val durationMs: Long,
      val text: String,
  )
  ```
- **`LyricLine`** (lines 186–193):
  ```kotlin
  data class LyricLine(
      val startMs: Long,
      val text: String,
      val translation: String? = null,
      val durationMs: Long? = null,
      val words: List<LyricWord> = emptyList(),
      val romanization: String? = null,
  )
  ```
- **`Lyrics`** (lines 195–221):
  ```kotlin
  data class Lyrics(
      val lines: List<LyricLine> = emptyList(),
  ) {
      val isEmpty: Boolean get() = lines.isEmpty()
      companion object {
          val EMPTY = Lyrics()
      }
  }
  ```
- **`PlayableSong`** and **`PlaybackSnapshot`** in `app/src/main/kotlin/dev/nocturn/app/playback/PlaybackContract.kt` (lines 9–48):
  - `PlayableSong(id: Long, title: String, artist: String, album: String?, coverUrl: String?, durationMs: Long, url: String)`
  - `PlaybackSnapshot(currentSongId: Long?, playing: Boolean, positionMs: Long, durationMs: Long, ...)`

### 1.2 Lyricon SDK Models Bytecode Verification
From Gradle cached artifact `io.github.proify.lyricon.lyric:model:0.1.70` (`model-0.1.70.jar`), decompiled via `javap -c -p`:
- **`io.github.proify.lyricon.lyric.model.Song`**:
  ```kotlin
  public final class Song(
      var id: String? = null,
      var name: String? = null,
      var artist: String? = null,
      var duration: Long = 0L,
      var metadata: LyricMetadata? = null,
      var lyrics: List<RichLyricLine>? = null
  )
  ```
  - Constructor supports all default parameters.
  - `id` is `String?`.
  - `name` is `String` (nullable in constructor with default null, but required non-null for display).
  - `artist` is `String?`.
  - `duration` is primitive `long` (ms).
  - `lyrics` is `List<RichLyricLine>?`.
- **`io.github.proify.lyricon.lyric.model.RichLyricLine`**:
  ```kotlin
  public final class RichLyricLine(
      var begin: Long = 0L,
      var end: Long = 0L,
      var duration: Long = 0L,
      var isAlignedRight: Boolean = false,
      var metadata: LyricMetadata? = null,
      var text: String? = null,
      var words: List<LyricWord>? = null,
      var secondary: String? = null,
      var secondaryWords: List<LyricWord>? = null,
      var translation: String? = null,
      var translationWords: List<LyricWord>? = null,
      var roma: String? = null
  )
  ```
  - `begin` and `end` are in milliseconds.
  - Automatically calculates `duration = end - begin` in its `init` block when `duration == 0L && end > begin`.
  - Has explicit `roma: String?` property corresponding to `player.setDisplayRoma(true)`.
  - Has explicit `translation: String?` property corresponding to `player.setDisplayTranslation(true)`.
  - `words` holds `List<LyricWord>?` for syllable/YRC slicing.
- **`io.github.proify.lyricon.lyric.model.LyricWord`**:
  ```kotlin
  public final class LyricWord(
      var begin: Long = 0L,
      var end: Long = 0L,
      var duration: Long = 0L,
      var text: String? = null,
      var metadata: LyricMetadata? = null
  )
  ```
  - `begin` and `end` in milliseconds.
  - Automatically sets `duration = end - begin` if `duration == 0L && end > begin`.
  - `text` is the word or syllable string.

### 1.3 Pure Music / Instrumental Handling in Nocturn
Observed in `app/src/main/kotlin/dev/nocturn/app/data/NeteaseJson.kt` (lines 456–472):
```kotlin
fun parseLyrics(body: JsonObject): Lyrics {
    val parsed = LyricParser.parse(body)
    if (parsed.instrumental) return Lyrics.EMPTY
    ...
    return if (lines.isEmpty()) Lyrics.EMPTY else Lyrics(lines)
}
```
When a track is instrumental (pure music), Nocturn's repository returns `Lyrics.EMPTY`.

### 1.4 Reference Conversion Implementation in Nocturn
Observed in `app/src/main/kotlin/dev/nocturn/app/ui/screens/player/AccompanistLyrics.kt` (lines 11–48):
- Sorts lines by `it.startMs`.
- Fallbacks for line length: `line.durationMs?.takeIf { it > 0L } ?: nextStart?.let { (it - start).coerceAtLeast(1L) } ?: (durationMs - start).takeIf { it > 0L } ?: 5_000L`.
- Word timing conversion: `word.startMs` coerced to parent line interval.
- Words only attached when `words.isNotEmpty()`.

---

## 2. Logic Chain

1. **Import Aliasing**: Both Nocturn (`dev.nocturn.app.data.model`) and Lyricon (`io.github.proify.lyricon.lyric.model`) define classes named `Song` and `LyricWord`. To prevent compiler confusion, all mapper and test files must use explicit Kotlin import aliases:
   ```kotlin
   import dev.nocturn.app.data.model.Song as NocturnSong
   import dev.nocturn.app.data.model.Lyrics as NocturnLyrics
   import dev.nocturn.app.data.model.LyricLine as NocturnLyricLine
   import dev.nocturn.app.data.model.LyricWord as NocturnLyricWord
   import io.github.proify.lyricon.lyric.model.Song as LyriconSong
   import io.github.proify.lyricon.lyric.model.RichLyricLine as LyriconRichLine
   import io.github.proify.lyricon.lyric.model.LyricWord as LyriconWord
   ```

2. **Line Boundary & Duration Fallback Calculation**:
   - `lineBegin = line.startMs.coerceAtLeast(0L)` prevents negative start times.
   - For LRC (where `line.durationMs` is null or `<= 0`):
     - If next line exists (`nextStartMs != null && nextStartMs > lineBegin`): `lineEnd = nextStartMs`.
     - Else if song has known duration (`songDurationMs > lineBegin`): `lineEnd = songDurationMs`.
     - Else: fallback to `lineBegin + DEFAULT_LINE_DURATION_MS (5_000L)`.
   - For YRC (where `line.durationMs != null && line.durationMs > 0`):
     - `rawEnd = lineBegin + line.durationMs` (safeguarded against `Long.MAX_VALUE` overflow).
     - If next line exists: `lineEnd = minOf(rawEnd, nextStartMs)` so lyrics never overlap subsequent lines while preserving gaps (e.g., guitar interludes).
     - If last line: `lineEnd = if (songDurationMs > lineBegin) minOf(rawEnd, songDurationMs) else rawEnd`.
   - In all cases, `lineEnd = maxOf(lineBegin + 1L, calculatedEnd)` guarantees that `lineEnd > lineBegin` strictly, satisfying Lyricon duration requirements.

3. **Word Syllable Conversion and Boundary Clamping**:
   - Each Nocturn word has `startMs` and `durationMs`.
   - Raw word timing: `rawWordBegin = word.startMs.coerceAtLeast(0L)`, `rawWordEnd = rawWordBegin + word.durationMs.coerceAtLeast(0L)`.
   - Clamping to parent line:
     `wordBegin = rawWordBegin.coerceIn(lineBegin, lineEnd)`
     `wordEnd = rawWordEnd.coerceIn(wordBegin, lineEnd)`
   - This ensures words never start before `lineBegin` or overshoot `lineEnd`.
   - If a line has no words (`line.words.isEmpty()`), `RichLyricLine.words` is set to `null` (not an empty list), aligning with Lyricon's line-only LRC rendering.

4. **Translation and Romanization Mapping**:
   - `line.translation?.trim()?.takeIf { it.isNotEmpty() }` maps directly to `RichLyricLine.translation`.
   - `line.romanization?.trim()?.takeIf { it.isNotEmpty() }` maps directly to `RichLyricLine.roma`.
   - If blank or null, both resolve to `null`, ensuring Lyricon doesn't render empty translation bubbles.

5. **Pure Music & Empty Lyrics Handling**:
   - When `lyrics.isEmpty`, `toLyriconLines` returns `emptyList()`.
   - `toLyriconSong` returns `LyriconSong(..., lyrics = null)`.
   - Constants `PURE_MUSIC_TEXT = "纯音乐，请欣赏"` and `NO_LYRICS_TEXT = "暂无歌词"` are defined for `LyriconManager` to use with `player.sendText(...)` when entering text mode.

---

## 3. Caveats

1. **Interval Semantics (Half-Open vs Inclusive)**:
   - Accompanist lyrics UI requires inclusive `end - 1` because adjacent lines share boundaries in its internal model.
   - Lyricon SDK uses continuous standard timestamps `[begin, end)` where `line1.end == line2.begin` (e.g. 0-1000, 1000-2000). Do NOT subtract 1 from `end` for Lyricon lines or words.
2. **`Song.lyrics` Nullability**:
   - When lyrics are empty, `LyriconSong.lyrics` should be `null` rather than `emptyList()`, matching the official SDK example `Song(name = "...", artist = "...")`.
3. **Nocturn Song ID Range**:
   - Nocturn song IDs are positive `Long` values. When `id <= 0L` (e.g. invalid or placeholder), `LyriconSong.id` should be `null`.

---

## 4. Conclusion & Complete Implementation Design

The file `app/src/main/kotlin/dev/nocturn/app/playback/lyricon/LyriconModelMapper.kt` should be implemented as follows:

```kotlin
package dev.nocturn.app.playback.lyricon

import dev.nocturn.app.data.model.LyricLine as NocturnLyricLine
import dev.nocturn.app.data.model.LyricWord as NocturnLyricWord
import dev.nocturn.app.data.model.Lyrics as NocturnLyrics
import dev.nocturn.app.data.model.Song as NocturnSong
import io.github.proify.lyricon.lyric.model.LyricWord as LyriconWord
import io.github.proify.lyricon.lyric.model.RichLyricLine as LyriconRichLine
import io.github.proify.lyricon.lyric.model.Song as LyriconSong

/**
 * Transforms Nocturn data models ([NocturnSong], [NocturnLyrics], [NocturnLyricLine], [NocturnLyricWord])
 * into Lyricon Provider SDK models ([LyriconSong], [LyriconRichLine], [LyriconWord]).
 *
 * Timestamps are uniformly handled in milliseconds.
 */
object LyriconModelMapper {

    /** Default duration in ms for an LRC line when next line and song duration are unavailable. */
    const val DEFAULT_LINE_DURATION_MS: Long = 5_000L

    /** Standard placeholder text for pure music / instrumental tracks. */
    const val PURE_MUSIC_TEXT: String = "纯音乐，请欣赏"

    /** Standard placeholder text when lyrics are not available. */
    const val NO_LYRICS_TEXT: String = "暂无歌词"

    /**
     * Converts a [NocturnSong] and optional [NocturnLyrics] into a [LyriconSong].
     */
    fun toLyriconSong(
        song: NocturnSong,
        lyrics: NocturnLyrics = NocturnLyrics.EMPTY,
    ): LyriconSong = toLyriconSong(
        id = song.id,
        title = song.name,
        artist = song.artistText,
        durationMs = song.durationMs,
        lyrics = lyrics,
    )

    /**
     * Converts raw song metadata parameters and optional [NocturnLyrics] into a [LyriconSong].
     */
    fun toLyriconSong(
        id: Long,
        title: String,
        artist: String?,
        durationMs: Long,
        lyrics: NocturnLyrics = NocturnLyrics.EMPTY,
    ): LyriconSong {
        val duration = durationMs.coerceAtLeast(0L)
        val lines = toLyriconLines(lyrics, duration).takeIf { it.isNotEmpty() }
        return LyriconSong(
            id = id.takeIf { it > 0L }?.toString(),
            name = title.ifBlank { "未知曲目" },
            artist = artist?.ifBlank { null },
            duration = duration,
            lyrics = lines,
        )
    }

    /**
     * Transforms [NocturnLyrics] into a list of [LyriconRichLine].
     *
     * - Filters out blank lines and sorts by start time.
     * - Calculates line begin/end timestamps with boundary clamping and fallbacks.
     * - Maps syllable-level [NocturnLyricWord] into [LyriconWord] with parent line clamping.
     * - Preserves translations and romanizations.
     * - Returns an empty list if lyrics are empty.
     */
    fun toLyriconLines(
        lyrics: NocturnLyrics,
        songDurationMs: Long,
    ): List<LyriconRichLine> {
        if (lyrics.isEmpty) return emptyList()

        val validLines = lyrics.lines
            .filter { it.text.isNotBlank() }
            .sortedBy { it.startMs }

        if (validLines.isEmpty()) return emptyList()

        val safeSongDuration = songDurationMs.coerceAtLeast(0L)

        return validLines.mapIndexed { index, line ->
            toLyriconLine(
                line = line,
                nextStartMs = validLines.asSequence()
                    .drop(index + 1)
                    .map { it.startMs.coerceAtLeast(0L) }
                    .firstOrNull { it > line.startMs.coerceAtLeast(0L) },
                songDurationMs = safeSongDuration,
            )
        }
    }

    /**
     * Converts a single [NocturnLyricLine] into a [LyriconRichLine].
     */
    fun toLyriconLine(
        line: NocturnLyricLine,
        nextStartMs: Long?,
        songDurationMs: Long,
    ): LyriconRichLine {
        val lineBegin = line.startMs.coerceAtLeast(0L)

        val calculatedEnd = if (line.durationMs != null && line.durationMs > 0L) {
            val duration = line.durationMs
            val rawEnd = if (Long.MAX_VALUE - duration < lineBegin) Long.MAX_VALUE else lineBegin + duration
            if (nextStartMs != null && nextStartMs > lineBegin) {
                minOf(rawEnd, nextStartMs)
            } else if (nextStartMs == null && songDurationMs > lineBegin) {
                minOf(rawEnd, songDurationMs)
            } else {
                rawEnd
            }
        } else {
            // LRC mode: interpolate to next line start, or song duration, or fallback
            if (nextStartMs != null && nextStartMs > lineBegin) {
                nextStartMs
            } else if (songDurationMs > lineBegin) {
                songDurationMs
            } else {
                if (Long.MAX_VALUE - DEFAULT_LINE_DURATION_MS < lineBegin) {
                    Long.MAX_VALUE
                } else {
                    lineBegin + DEFAULT_LINE_DURATION_MS
                }
            }
        }

        val lineEnd = maxOf(lineBegin + 1L, calculatedEnd)

        val words = if (line.words.isNotEmpty()) {
            toLyriconWords(line.words, lineBegin, lineEnd)
        } else {
            null
        }

        return LyriconRichLine(
            begin = lineBegin,
            end = lineEnd,
            text = line.text.trim(),
            translation = line.translation?.trim()?.takeIf { it.isNotEmpty() },
            words = words,
            roma = line.romanization?.trim()?.takeIf { it.isNotEmpty() },
        )
    }

    /**
     * Converts a list of [NocturnLyricWord] into clamped [LyriconWord] elements.
     * All words are guaranteed to lie within `[lineBegin, lineEnd]`.
     */
    fun toLyriconWords(
        words: List<NocturnLyricWord>,
        lineBegin: Long,
        lineEnd: Long,
    ): List<LyriconWord> {
        val sortedWords = words.sortedBy { it.startMs }
        if (sortedWords.isEmpty()) return emptyList()

        return sortedWords.map { word ->
            val rawWordBegin = word.startMs.coerceAtLeast(0L)
            val duration = word.durationMs.coerceAtLeast(0L)
            val rawWordEnd = if (Long.MAX_VALUE - duration < rawWordBegin) {
                Long.MAX_VALUE
            } else {
                rawWordBegin + duration
            }

            val clampedBegin = rawWordBegin.coerceIn(lineBegin, lineEnd)
            val clampedEnd = rawWordEnd.coerceIn(clampedBegin, lineEnd)

            LyriconWord(
                begin = clampedBegin,
                end = clampedEnd,
                text = word.text,
            )
        }
    }
}
```

---

## 5. Verification Method

Once implemented by the Worker:
1. **Compilation Check**:
   ```powershell
   .\gradlew.bat :app:compileDebugKotlin
   ```
2. **Unit Test Execution**:
   ```powershell
   .\gradlew.bat :app:testDebugUnitTest --tests "dev.nocturn.app.playback.lyricon.LyriconModelMapperTest"
   ```
3. **Key Test Assertions to Validate**:
   - `toLyriconSong` correctly converts `Song` fields (`id = "123"`, `name = "Song"`, `artist = "Artist"`, `duration = 180000L`).
   - Line timing with LRC mode correctly sets `end` to `nextStartMs` (e.g. line 1: `0 -> 3000`, line 2: `3000 -> 10000`).
   - Line timing with YRC mode respects `line.durationMs` (e.g. line 1: `0 -> 1500`, interlude to 3000).
   - YRC words are clamped to line boundaries (e.g. word extending to 3500 is clamped to line end 3000).
   - Missing translation or blank string results in `translation == null`.
   - Non-blank romanization maps to `RichLyricLine.roma`.
   - Pure music / `Lyrics.EMPTY` produces `lyrics == null` in `LyriconSong` and `emptyList()` from `toLyriconLines`.
