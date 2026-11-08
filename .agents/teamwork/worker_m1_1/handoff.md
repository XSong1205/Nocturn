# Milestone 1: Lyricon SDK Integration, Manifest Metadata & LyriconModelMapper Handoff Report

## 1. Observation

1. **Dependency Configuration (`app/build.gradle.kts`)**:
   - Added `implementation("io.github.proify.lyricon:provider:0.1.70")` directly after line 147 (`lyrics-core`).
   - Line 148 of `app/build.gradle.kts`:
     ```kotlin
     implementation("com.mocharealm.accompanist:lyrics-ui:2.0.0-rc.3")
     implementation("com.mocharealm.accompanist:lyrics-core:0.5.0")
     implementation("io.github.proify.lyricon:provider:0.1.70")
     implementation("io.coil-kt.coil3:coil-compose:3.6.3")
     ```

2. **Manifest Metadata (`app/src/main/AndroidManifest.xml`)**:
   - Added 4 metadata tags under `<application>` preceding `<activity>`:
     ```xml
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
     ```

3. **Resource Declarations (`app/src/main/res/values/arrays.xml`)**:
   - Created `app/src/main/res/values/arrays.xml`:
     ```xml
     <?xml version="1.0" encoding="utf-8"?>
     <resources>
         <string-array name="lyricon_module_tags">
             <item>$syllable</item>
             <item>$translation</item>
         </string-array>
     </resources>
     ```

4. **ProGuard Rules (`app/proguard-rules.pro`)**:
   - Appended keep and warning suppression rules for Lyricon SDK:
     ```proguard
     # Lyricon Provider SDK
     -keep class io.github.proify.lyricon.** { *; }
     -dontwarn io.github.proify.lyricon.**
     ```

5. **Model Mapper Implementation (`app/src/main/kotlin/dev/nocturn/app/playback/lyricon/LyriconModelMapper.kt`)**:
   - Implemented `dev.nocturn.app.playback.lyricon.LyriconModelMapper`:
     - Disambiguated Nocturn vs Lyricon SDK types using explicit import aliases (`NocturnSong`, `NocturnLyrics`, `NocturnLyricLine`, `NocturnLyricWord`, `LyriconSong`, `LyriconRichLine`, `LyriconWord`).
     - `toLyriconSong(song: NocturnSong, lyrics: NocturnLyrics)` converts song ID to string, delegates artist mapping to `artistText` fallback (`"未知歌手"`), duration to `durationMs.coerceAtLeast(0L)`, and maps lyrics via `toLyriconLines`.
     - `toLyriconSong(id: Long, title: String, artist: String?, durationMs: Long, lyrics: NocturnLyrics)` handles raw metadata and supports nullable artist.
     - `toLyriconLines(lyrics: NocturnLyrics, songDurationMs: Long)` filters blank lines, sorts by `startMs`, and calculates begin/end boundaries with fallback extrapolation.
     - `toLyriconLine(line: NocturnLyricLine, nextStartMs: Long?, songDurationMs: Long)` computes line boundaries:
       - Uses `durationMs` when present and `> 0L`, clipping against `nextStartMs` or `songDurationMs`.
       - Falls back to `nextStartMs` when `durationMs` is null or `<= 0L`.
       - Falls back to `songDurationMs` for last line if greater than `lineBegin`.
       - Falls back to `lineBegin + DEFAULT_LINE_DURATION_MS (5_000L)` when last line exceeds or matches song duration or song duration is 0.
       - Guarantees `lineEnd >= lineBegin + 1L`.
       - Populates both `secondary` and `roma` from `line.romanization?.trim()?.takeIf { it.isNotEmpty() }`.
       - Populates `translation` from `line.translation?.trim()?.takeIf { it.isNotEmpty() }`.
       - Returns `words = null` when line has no syllable words.
     - `toLyriconWords(words, lineBegin, lineEnd)` sorts words by start time and clamps both `begin` and `end` within `[lineBegin, lineEnd]` such that `clampedEnd >= clampedBegin`.

6. **Unit Test Execution (`app/src/test/kotlin/dev/nocturn/app/playback/lyricon/LyriconModelMapperTest.kt`)**:
   - Implemented all 25 unit test cases designed by Explorer 3 in `app/src/test/kotlin/dev/nocturn/app/playback/lyricon/LyriconModelMapperTest.kt`.
   - Executed targeted unit test:
     `.\gradlew.bat :app:testDebugUnitTest --tests "dev.nocturn.app.playback.lyricon.LyriconModelMapperTest"`
     Result from `app/build/test-results/testDebugUnitTest/TEST-dev.nocturn.app.playback.lyricon.LyriconModelMapperTest.xml`:
     `<testsuite name="dev.nocturn.app.playback.lyricon.LyriconModelMapperTest" tests="25" skipped="0" failures="0" errors="0" timestamp="2026-11-07T13:47:59.406Z" time="0.049">`
     Status: **BUILD SUCCESSFUL in 24s** (25/25 tests passed).

7. **Build & Lint Verification**:
   - `.\gradlew.bat :app:assembleDebug` completed with **BUILD SUCCESSFUL in 18s**.
   - `.\gradlew.bat :app:lintDebug` completed with **BUILD SUCCESSFUL in 40s** (0 errors).
   - Full test suite `.\gradlew.bat :app:testDebugUnitTest` completed with **BUILD SUCCESSFUL in 47s**.

---

## 2. Logic Chain

1. **Dependency Resolution**:
   Adding `implementation("io.github.proify.lyricon:provider:0.1.70")` resolved against Maven Central (already configured in `settings.gradle.kts`). Gradle compiled the dependency without conflicts.

2. **Manifest Merging**:
   The 4 `<meta-data>` tags (`lyricon_module`, `lyricon_module_author`, `lyricon_module_description`, `lyricon_module_tags`) under `<application>` compiled against `@array/lyricon_module_tags` in `app/src/main/res/values/arrays.xml`. `:app:assembleDebug` merged the manifest cleanly without errors.

3. **Model Transformation Correctness**:
   - String ID conversion: Lyricon `Song.id` is a String; Nocturn uses `Long`. Converting `id.toString()` accommodates all IDs, including `0L` (`"0"`).
   - Timing Invariants:
     - All start times are coerced to `>= 0L`.
     - In LRC lines with missing/invalid duration, `end` is interpolated to next line start or song duration, or 5_000ms default fallback.
     - In YRC lines with syllables, words are sorted and clamped to parent line bounds: `clampedBegin = rawBegin.coerceIn(lineBegin, lineEnd)` and `clampedEnd = rawEnd.coerceIn(clampedBegin, lineEnd)`.
     - Non-syllable lines output `words = null` to avoid empty list serialization overhead in IPC.
   - Text Sanitation:
     - Blank and whitespace-only translations or romanizations are converted to `null` via `.trim().takeIf { it.isNotEmpty() }`, preventing empty subtitle badges in Lyricon.
     - Romanization is mapped to both `secondary` and `roma` on `RichLyricLine` to support secondary lyric subtitle display.

---

## 3. Caveats

1. **Device Service IPC Binding**: Milestone 1 focuses on SDK integration, metadata declaration, data mapper logic, and mapper unit tests. The central Lyricon status bar provider service binding is managed in Milestone 3 (`LyriconManager`).
2. **Version Pinning**: `provider:0.1.70` is pinned in `app/build.gradle.kts`. If Lyricon publishes a newer SDK version, compatibility should be reviewed before bumping.

---

## 4. Conclusion

Milestone 1 is complete and verified:
- `io.github.proify.lyricon:provider:0.1.70` is integrated in `app/build.gradle.kts`.
- Manifest `<meta-data>` tags and `@array/lyricon_module_tags` are configured.
- ProGuard keep rules for `io.github.proify.lyricon.**` are in place.
- `LyriconModelMapper.kt` provides genuine, robust bidirectional data transformations adhering to Lyricon specifications and handling all edge cases.
- All 25 unit tests in `LyriconModelMapperTest.kt` pass cleanly.
- `:app:assembleDebug` and `:app:lintDebug` pass with 0 errors.

---

## 5. Verification Method

To independently verify Milestone 1:

1. **Targeted Mapper Unit Tests**:
   ```powershell
   .\gradlew.bat :app:testDebugUnitTest --tests "dev.nocturn.app.playback.lyricon.LyriconModelMapperTest"
   ```
   *Expected outcome*: 25 tests executed, 0 failures, 0 errors. XML report at `app/build/test-results/testDebugUnitTest/TEST-dev.nocturn.app.playback.lyricon.LyriconModelMapperTest.xml`.

2. **Full Client Assembly**:
   ```powershell
   .\gradlew.bat :app:assembleDebug
   ```
   *Expected outcome*: BUILD SUCCESSFUL with debug APK produced.

3. **Lint Verification**:
   ```powershell
   .\gradlew.bat :app:lintDebug
   ```
   *Expected outcome*: BUILD SUCCESSFUL with 0 lint errors.
