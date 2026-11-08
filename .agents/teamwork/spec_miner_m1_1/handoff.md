# Milestone 1 Specification Mining Handoff Report

## Features Discovered

| # | Category | Feature | Description | Inputs | Outputs | Error Behavior | Discovered Via |
|---|----------|---------|-------------|--------|---------|----------------|----------------|
| 1 | Build & Dependency | Lyricon SDK Dependency | Gradle dependency `io.github.proify.lyricon:provider:0.1.70` via Maven Central | `app/build.gradle.kts` `dependencies { ... }` | Provides Lyricon Provider runtime, IPC interfaces and Lyricon model classes | Gradle sync failure if repository missing | `app/build.gradle.kts`, `https://tomakino.github.io/lyricon/zh-cn/developer/provider/quick-start` |
| 2 | Android Manifest | Module Recognition Flag | Declares `lyricon_module` as `"true"` under `<application>` | Manifest `<meta-data>` | Lyricon recognizes Nocturn as a provider module | If omitted, Lyricon central service ignores the application | `https://tomakino.github.io/lyricon/zh-cn/developer/provider/manifest`, `app/src/main/AndroidManifest.xml` |
| 3 | Android Manifest | Author Metadata | Declares `lyricon_module_author` under `<application>` | Manifest `<meta-data android:value="Nocturn"/>` | Displayed in Lyricon's provider listing | None (defaults to empty/unknown if missing) | `https://tomakino.github.io/lyricon/zh-cn/developer/provider/manifest` |
| 4 | Android Manifest | Description Metadata | Declares `lyricon_module_description` under `<application>` | Manifest `<meta-data android:value="Nocturn NetEase Cloud Music Client"/>` | Module summary in Lyricon UI | None (defaults to empty if missing) | `https://tomakino.github.io/lyricon/zh-cn/developer/provider/manifest` |
| 5 | Android Manifest | Capabilities Resource | Points `lyricon_module_tags` to `@array/lyricon_module_tags` | Manifest `<meta-data android:resource="@array/..."/>` | Capability badges displayed in Lyricon | Missing resource causes build/packaging error | `https://tomakino.github.io/lyricon/zh-cn/developer/provider/manifest` |
| 6 | Android Resources | Capabilities Array | String-array declaring `$syllable` and `$translation` | `app/src/main/res/values/arrays.xml` | Declares support for syllable/YRC lyrics and translated lyrics | Build error if resource name or format is incorrect | `https://tomakino.github.io/lyricon/zh-cn/developer/provider/manifest`, `app/src/main/res/values/` |
| 7 | ProGuard / R8 | IPC & Serialization Keep Rules | Keep rules for `io.github.proify.lyricon.**` | `app/proguard-rules.pro` | Prevents AIDL interfaces, Parcelable models and serializers from being stripped or obfuscated | NullPointerException or ClassCastException in release APK across IPC | `provider-0.1.70.aar` inspection (`proguard.txt` was empty) |
| 8 | Model Mapping | Song Model Mapping | Maps Nocturn `Song` (Long id, name, artistText, durationMs) to Lyricon `Song` | Nocturn `Song`, optional `Lyrics` | Lyricon `Song(id, name, artist, duration, lyrics)` | Missing fields produce fallback defaults | `app/src/main/kotlin/dev/nocturn/app/data/model/Models.kt`, `model-0.1.70.jar` (`Song.class`) |
| 9 | Model Mapping | Syllable / YRC Word Mapping | Maps Nocturn `LyricWord` (startMs, durationMs, text) to Lyricon `LyricWord` (begin, end, text) | Nocturn `LyricWord` list | Lyricon `List<LyricWord>` | Clamped so `end >= begin` | `model-0.1.70.jar` (`LyricWord.class`, `RichLyricLine.class`) |
| 10 | Model Mapping | Line Timing & Fallback | Maps Nocturn `LyricLine` to Lyricon `RichLyricLine` calculating `begin` and `end` | Nocturn `LyricLine`, `songDurationMs` | Lyricon `RichLyricLine(begin, end, text, ...)` | Missing duration falls back to next line begin or song duration | `app/src/test/kotlin/dev/nocturn/app/data/NeteaseJsonLyricsTest.kt`, Lyricon docs |
| 11 | Model Mapping | Translation & Roma Mapping | Maps `LyricLine.translation` and `LyricLine.romanization` to `RichLyricLine.translation` and `RichLyricLine.roma` | `LyricLine.translation`, `LyricLine.romanization` | Non-blank Strings or null | Blank or whitespace strings mapped to null | `https://tomakino.github.io/lyricon/zh-cn/developer/provider/lyrics-model` |
| 12 | Model Mapping | Empty / Instrumental Handling | Returns empty list or null lyrics when track is pure music or has no lyrics | `NocturnLyrics.EMPTY` / `lyrics.isEmpty` | `emptyList()` / `null` in `Song.lyrics` | Pure music does not crash or emit zero-duration dummy lines | `NeteaseJsonLyricsTest.kt`, `LyriconModelMapper` contract |

## Edge Cases

| # | Feature | Input | Observed Behavior |
|---|---------|-------|-------------------|
| 1 | Line Timing | Line without `durationMs` mid-song | Calculated `end` must equal the next line's `startMs` |
| 2 | Line Timing | Last line of song without `durationMs` | Calculated `end` must equal `maxOf(startMs, songDurationMs)` |
| 3 | Line Timing | Line with corrupted/negative duration (`startMs + durationMs < startMs`) | Calculated `end` must be clamped to `maxOf(begin, computedEnd)` so `end >= begin` |
| 4 | Syllable Words | Line with empty `words` list | `RichLyricLine.words` should be set to `null` to avoid overhead in Lyricon renderer |
| 5 | Syllable Words | Syllable with `durationMs == 0` | Word `end` equals `word.startMs` (`end >= begin`) |
| 6 | Lyrics | Instrumental track or empty lyrics (`Lyrics.EMPTY`) | `toLyriconLines` returns `emptyList()`; `Song.lyrics` is `null` (or empty) |
| 7 | Translation / Roma | Blank or whitespace-only translation (`"   "`) | Filtered via `.takeIf { it.isNotBlank() }` resulting in `null`, avoiding blank badges |
| 8 | Song ID | Song ID is `Long` in Nocturn (e.g. `123456L`) | Converted via `.toString()` to String `"123456"` required by Lyricon `Song.id` |
| 9 | Artist | Song with empty `artists` list | `song.artistText` provides `"未知歌手"` fallback, ensuring non-blank display |

---

## 1. Observation

1. **`app/build.gradle.kts` Structure**:
   Lines 144–154 contain existing media and lyrics dependencies:
   ```kotlin
   implementation("androidx.media3:media3-exoplayer:1.11.1")
   implementation("androidx.media3:media3-session:1.11.1")
   implementation("com.mocharealm.accompanist:lyrics-ui:2.0.0-rc.3")
   implementation("com.mocharealm.accompanist:lyrics-core:0.5.0")
   implementation("io.coil-kt.coil3:coil-compose:3.6.3")
   implementation("io.coil-kt.coil3:coil-network-okhttp:3.6.3")
   // Backdrop / progressive blur materials (HyperOS style). Effects need API 33+ (RuntimeShader);
   // the library turns them off below that and callers fall back to opaque tinted surfaces.
   implementation("top.yukonga.miuix.kmp:miuix-blur:0.9.4")
   ```
   Repository configuration in `settings.gradle.kts` already declares `mavenCentral()`:
   ```kotlin
   dependencyResolutionManagement {
       repositoriesMode.set(RepositoriesMode.FAIL_ON_PROJECT_REPOS)
       repositories {
           google()
           mavenCentral()
       }
   }
   ```
   No additional repository configuration is required.

2. **`app/src/main/AndroidManifest.xml` Structure**:
   Lines 14–26 show `<application>` contents:
   ```xml
   <application
       android:name=".NocturnApplication"
       android:allowBackup="false"
       android:enableOnBackInvokedCallback="true"
       tools:targetApi="33"
       android:icon="@drawable/ic_nocturn"
       android:label="Nocturn"
       android:supportsRtl="true"
       android:theme="@style/Theme.Nocturn">
       <activity
           android:name=".MainActivity"
           android:exported="true">
   ```
   There are currently no `<meta-data>` tags under `<application>`. The optimal insertion point is directly under `<application>` immediately preceding line 23 (`<activity android:name=".MainActivity"...>`).

3. **`app/src/main/res/values/` Status**:
   Inspected directory `app/src/main/res/values/` via `list_dir`. The only existing file is `themes.xml` (551 bytes). `arrays.xml` does NOT exist and must be created.

4. **`provider-0.1.70.aar` ProGuard Analysis**:
   Inspected the cached artifact `provider-0.1.70.aar` at `C:\Users\XSong\.gradle\caches\modules-2\files-2.1\io.github.proify.lyricon\provider\0.1.70\ed05757e20cbc61e008d1961b91d1d7674b65b40\provider-0.1.70.aar`.
   - The bundled `proguard.txt` is 0 bytes (empty).
   - `classes.jar` contains AIDL IPC stubs and proxies (`IProviderBinder`, `IProviderService`, `IRemotePlayer`, `IRemoteService`) and Kotlinx Serialization serializers (`ProviderInfo$$serializer`, `Song$$serializer`, etc.).
   - Current `app/proguard-rules.pro` does not contain keep rules for `io.github.proify.lyricon.**`.

5. **Data Models in `app/src/main/kotlin/dev/nocturn/app/data/model/Models.kt`**:
   - `Song`: `id: Long`, `name: String`, `artists: List<ArtistRef>`, `durationMs: Long`, `artistText: String`.
   - `LyricWord`: `startMs: Long`, `durationMs: Long`, `text: String`.
   - `LyricLine`: `startMs: Long`, `text: String`, `translation: String? = null`, `durationMs: Long? = null`, `words: List<LyricWord> = emptyList()`, `romanization: String? = null`.
   - `Lyrics`: `lines: List<LyricLine>`, `isEmpty: Boolean`, companion `EMPTY = Lyrics()`.

6. **Bytecode Inspection of Lyricon SDK (`model-0.1.70.jar`)**:
   - `io.github.proify.lyricon.lyric.model.Song`:
     - Constructor: `Song(id: String?, name: String, artist: String?, duration: Long, metadata: LyricMetadata?, lyrics: List<RichLyricLine>?)`
     - Has Kotlin default parameters constructor.
   - `io.github.proify.lyricon.lyric.model.RichLyricLine`:
     - Constructor: `RichLyricLine(begin: Long, end: Long, duration: Long, isAlignedRight: Boolean, metadata: LyricMetadata?, text: String, words: List<LyricWord>?, secondary: String?, secondaryWords: List<LyricWord>?, translation: String?, translationWords: List<LyricWord>?, roma: String?)`
     - Has Kotlin default parameters constructor (allowing omission of `duration`, `isAlignedRight`, `metadata`, `secondary`, etc.).
   - `io.github.proify.lyricon.lyric.model.LyricWord`:
     - Constructor: `LyricWord(begin: Long, end: Long, duration: Long, text: String, metadata: LyricMetadata?)`
     - Has Kotlin default parameters constructor.

---

## 2. Logic Chain

1. **SDK Dependency**:
   Because `io.github.proify.lyricon:provider:0.1.70` is on Maven Central, declaring `implementation("io.github.proify.lyricon:provider:0.1.70")` in `app/build.gradle.kts` resolves immediately without modifying `settings.gradle.kts`. Positioning it alongside `lyrics-ui` and `lyrics-core` maintains module organization.

2. **Manifest Metadata**:
   The Lyricon Central Service uses Android `PackageManager` to query applications with `<meta-data android:name="lyricon_module" android:value="true" />`. Placing the 4 metadata tags under `<application>` before `<activity>` ensures standard manifest structure and passes manifest merging cleanly.

3. **Resource Declarations**:
   The tag `lyricon_module_tags` references `@array/lyricon_module_tags`. Because `arrays.xml` is absent, creating `app/src/main/res/values/arrays.xml` with items `$syllable` and `$translation` enables Lyricon to show syllable and translation capability tags for Nocturn.

4. **ProGuard / R8 Protection**:
   Because `provider-0.1.70.aar` provides no consumer rules in `proguard.txt`, release builds with R8 shrinking enabled (`isMinifyEnabled = true` in `app/build.gradle.kts`) could obfuscate AIDL interface class names or strip Kotlinx Serialization descriptors. Adding `-keep class io.github.proify.lyricon.** { *; }` and `-dontwarn io.github.proify.lyricon.**` to `app/proguard-rules.pro` protects IPC and serialization across processes.

5. **Model Mapper Architecture**:
   - Nocturn uses `Long` for `Song.id`, while Lyricon uses `String` -> Convert via `id.toString()`.
   - Nocturn uses `durationMs` for syllables and lines; Lyricon uses absolute millisecond range `begin` and `end`.
   - For syllable `LyricWord`: `begin = word.startMs`, `end = word.startMs + word.durationMs`.
   - For line `RichLyricLine`:
     - If `line.durationMs != null`, `end = line.startMs + line.durationMs`.
     - Else if subsequent line exists, `end = nextLine.startMs`.
     - Else, `end = maxOf(line.startMs, songDurationMs)`.
     - Ensure invariant `end >= begin` using `maxOf(begin, computedEnd)`.
   - Syllables list: If `line.words` is empty, set `words = null` to avoid allocating empty lists in IPC.
   - Translation and Romanization: Map `line.translation` and `line.romanization` using `.takeIf { it.isNotBlank() }` to omit empty/whitespace strings.

---

## 3. Caveats

1. **Android 8.1 / API 27 Restriction**: As documented in the Lyricon developer guide, `LyriconFactory.createProvider` returns an `EmptyProvider` on Android versions below 8.1 (API 27). Nocturn's `minSdk` is 29, so all supported devices will receive the active implementation (`ProviderV27Impl`).
2. **Lyricon Central Service Availability**: In test environments (Robolectric / offline unit tests), the central Lyricon service APK is not installed. Unit tests should therefore focus on the `LyriconModelMapper` conversion logic, and tests should avoid making live IPC binder calls unless mocked or shielded.

---

## 4. Conclusion & Precise Worker Implementation Guidance

The Worker for Milestone 1 should execute the following 6 concrete changes:

### Task 1: Update `app/build.gradle.kts`
Insert the Lyricon SDK dependency at line 148 (immediately after `lyrics-core`):
```kotlin
    implementation("com.mocharealm.accompanist:lyrics-ui:2.0.0-rc.3")
    implementation("com.mocharealm.accompanist:lyrics-core:0.5.0")
    implementation("io.github.proify.lyricon:provider:0.1.70")
```

### Task 2: Update `app/src/main/AndroidManifest.xml`
Insert the 4 `<meta-data>` entries directly under `<application>` before `<activity>`:
```xml
    <application
        android:name=".NocturnApplication"
        android:allowBackup="false"
        android:enableOnBackInvokedCallback="true"
        tools:targetApi="33"
        android:icon="@drawable/ic_nocturn"
        android:label="Nocturn"
        android:supportsRtl="true"
        android:theme="@style/Theme.Nocturn">

        <!-- Lyricon Provider module metadata -->
        <meta-data
            android:name="lyricon_module"
            android:value="true" />
        <meta-data
            android:name="lyricon_module_author"
            android:value="Nocturn" />
        <meta-data
            android:name="lyricon_module_description"
            android:value="Nocturn NetEase Cloud Music Client" />
        <meta-data
            android:name="lyricon_module_tags"
            android:resource="@array/lyricon_module_tags" />

        <activity
            android:name=".MainActivity"
```

### Task 3: Create `app/src/main/res/values/arrays.xml`
Create `app/src/main/res/values/arrays.xml`:
```xml
<?xml version="1.0" encoding="utf-8"?>
<resources>
    <string-array name="lyricon_module_tags">
        <item>$syllable</item>
        <item>$translation</item>
    </string-array>
</resources>
```

### Task 4: Update `app/proguard-rules.pro`
Append keep rules at the end of `app/proguard-rules.pro`:
```proguard
# Lyricon Provider SDK
-keep class io.github.proify.lyricon.** { *; }
-dontwarn io.github.proify.lyricon.**
```

### Task 5: Create `app/src/main/kotlin/dev/nocturn/app/playback/lyricon/LyriconModelMapper.kt`
Create `app/src/main/kotlin/dev/nocturn/app/playback/lyricon/LyriconModelMapper.kt`:
```kotlin
package dev.nocturn.app.playback.lyricon

import dev.nocturn.app.data.model.Song as NocturnSong
import dev.nocturn.app.data.model.Lyrics as NocturnLyrics
import dev.nocturn.app.data.model.LyricLine as NocturnLyricLine
import dev.nocturn.app.data.model.LyricWord as NocturnLyricWord
import io.github.proify.lyricon.lyric.model.Song as LyriconSong
import io.github.proify.lyricon.lyric.model.RichLyricLine as LyriconRichLine
import io.github.proify.lyricon.lyric.model.LyricWord as LyriconWord

/**
 * Maps Nocturn domain song and lyric models to Lyricon SDK provider models.
 */
object LyriconModelMapper {

    /**
     * Converts a Nocturn domain [NocturnSong] and optional [NocturnLyrics]
     * to a Lyricon [LyriconSong] model.
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
     * Converts raw song metadata and optional [NocturnLyrics] to a Lyricon [LyriconSong] model.
     */
    fun toLyriconSong(
        id: Long,
        title: String,
        artist: String?,
        durationMs: Long,
        lyrics: NocturnLyrics = NocturnLyrics.EMPTY,
    ): LyriconSong {
        val lines = toLyriconLines(lyrics, durationMs)
        return LyriconSong(
            id = id.toString(),
            name = title,
            artist = artist,
            duration = durationMs,
            lyrics = lines.takeIf { it.isNotEmpty() },
        )
    }

    /**
     * Converts Nocturn [NocturnLyrics] to a list of Lyricon [LyriconRichLine].
     * Handles syllable slicing (YRC), translations, romanization, and timing boundaries.
     */
    fun toLyriconLines(
        lyrics: NocturnLyrics,
        songDurationMs: Long,
    ): List<LyriconRichLine> {
        val rawLines = lyrics.lines
        if (rawLines.isEmpty()) return emptyList()

        return rawLines.mapIndexed { index, line ->
            val begin = line.startMs
            val explicitEnd = line.durationMs?.let { begin + it }
            val nextBegin = rawLines.getOrNull(index + 1)?.startMs
            val rawEnd = explicitEnd ?: nextBegin ?: maxOf(begin, songDurationMs)
            val end = maxOf(begin, rawEnd)

            val words = if (line.words.isNotEmpty()) {
                line.words.map { word ->
                    val wordBegin = word.startMs
                    val wordEnd = maxOf(wordBegin, wordBegin + word.durationMs)
                    LyriconWord(
                        begin = wordBegin,
                        end = wordEnd,
                        text = word.text,
                    )
                }
            } else {
                null
            }

            LyriconRichLine(
                begin = begin,
                end = end,
                text = line.text,
                words = words,
                translation = line.translation?.takeIf { it.isNotBlank() },
                roma = line.romanization?.takeIf { it.isNotBlank() },
            )
        }
    }
}
```

### Task 6: Create `app/src/test/kotlin/dev/nocturn/app/playback/lyricon/LyriconModelMapperTest.kt`
Create unit test suite covering:
1. NocturnSong to LyriconSong conversion (id stringification, artistText, duration).
2. YRC syllable word mapping (`LyricWord(startMs, durationMs, text)` -> `LyriconWord(begin, end, text)`).
3. LRC fallback line timing calculation (end calculated from next line begin or song duration).
4. Translation & romanization mapping (`translation` and `roma`).
5. Empty / instrumental lyrics handling (returns empty list / null lyrics).
6. Negative or inverted duration protection (`end >= begin`).

---

## 5. Verification Method

To verify Milestone 1 implementation:

1. **Gradle Build Verification**:
   ```powershell
   .\gradlew.bat :app:assembleDebug
   ```
   *Expected result*: BUILD SUCCESSFUL. Validates dependency resolution, Manifest merging, and resource compilation.

2. **Unit Test Execution**:
   ```powershell
   .\gradlew.bat :app:testDebugUnitTest --tests "dev.nocturn.app.playback.lyricon.LyriconModelMapperTest"
   ```
   *Expected result*: All mapper unit tests pass.

3. **Lint Verification**:
   ```powershell
   .\gradlew.bat :app:lintDebug
   ```
   *Expected result*: No lint errors introduced.
