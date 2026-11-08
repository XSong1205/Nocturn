# Dispatch for Milestone 1 Worker

**Milestone**: M1 - SDK Integration, Manifest Metadata & LyriconModelMapper
**Original Request**: `d:\Nocturn\.agents\teamwork\ORIGINAL_REQUEST.md` (MUST read first)
**Project Plan**: `d:\Nocturn\.agents\teamwork\orchestrator\PROJECT.md`
**Working Directory**: `d:\Nocturn\.agents\teamwork\worker_m1_1`
**Explorer Reports to Consult**:
- `d:\Nocturn\.agents\teamwork\spec_miner_m1_1\handoff.md`
- `d:\Nocturn\.agents\teamwork\explorer_m1_2\handoff.md`
- `d:\Nocturn\.agents\teamwork\explorer_m1_3\handoff.md`

## File Ownership
You exclusively own and may edit or create these files:
1. `app/build.gradle.kts`
2. `app/src/main/AndroidManifest.xml`
3. `app/src/main/res/values/arrays.xml`
4. `app/proguard-rules.pro`
5. `app/src/main/kotlin/dev/nocturn/app/playback/lyricon/LyriconModelMapper.kt`
6. `app/src/test/kotlin/dev/nocturn/app/playback/lyricon/LyriconModelMapperTest.kt`

## Mandatory Tasks
1. Add `implementation("io.github.proify.lyricon:provider:0.1.70")` to `app/build.gradle.kts`.
2. Add the 4 `<meta-data>` tags (`lyricon_module`, `lyricon_module_author`, `lyricon_module_description`, `lyricon_module_tags`) to `app/src/main/AndroidManifest.xml` under `<application>`.
3. Create `app/src/main/res/values/arrays.xml` defining string-array `lyricon_module_tags` containing `$syllable` and `$translation`.
4. Append keep rules for `io.github.proify.lyricon.**` to `app/proguard-rules.pro`.
5. Implement `LyriconModelMapper.kt` in `dev.nocturn.app.playback.lyricon` implementing all model mapping (NocturnSong to LyriconSong, NocturnLyricLine to RichLyricLine with duration fallback and YRC syllables clamped to line bounds, translation and romanization mapping, pure music placeholder).
6. Implement `LyriconModelMapperTest.kt` in `app/src/test/kotlin/dev/nocturn/app/playback/lyricon/` with the complete 25-test suite designed by Explorer 3.
7. Run build and tests:
   `.\gradlew.bat :app:testDebugUnitTest --tests "dev.nocturn.app.playback.lyricon.LyriconModelMapperTest"`
   `.\gradlew.bat :app:assembleDebug`
8. Write handoff report in `d:\Nocturn\.agents\teamwork\worker_m1_1\handoff.md` detailing changes, build commands and test outcomes.

## 2026-11-07T13:42:34Z
DO NOT CHEAT. All implementations must be genuine. DO NOT hardcode test results, create dummy/facade implementations, or circumvent the intended task. A teamwork_auditor will independently verify your work. Integrity violations WILL be detected and your work WILL be rejected.

You are teamwork_worker for Milestone 1 (SDK Integration, Manifest Metadata & LyriconModelMapper).
Your working directory is: d:\Nocturn\.agents\teamwork\worker_m1_1
You MUST read d:\Nocturn\.agents\teamwork\ORIGINAL_REQUEST.md before starting work.
Also read d:\Nocturn\.agents\teamwork\orchestrator\PROJECT.md and d:\Nocturn\.agents\teamwork\worker_m1_1\DISPATCH.md.

Read the 3 Explorer handoff reports:
- d:\Nocturn\.agents\teamwork\spec_miner_m1_1\handoff.md
- d:\Nocturn\.agents\teamwork\explorer_m1_2\handoff.md
- d:\Nocturn\.agents\teamwork\explorer_m1_3\handoff.md

Your File Ownership:
- app/build.gradle.kts
- app/src/main/AndroidManifest.xml
- app/src/main/res/values/arrays.xml
- app/proguard-rules.pro
- app/src/main/kotlin/dev/nocturn/app/playback/lyricon/LyriconModelMapper.kt
- app/src/test/kotlin/dev/nocturn/app/playback/lyricon/LyriconModelMapperTest.kt

Implementation Requirements:
1. In app/build.gradle.kts, add implementation("io.github.proify.lyricon:provider:0.1.70").
2. In app/src/main/AndroidManifest.xml, under <application>, declare:
   - lyricon_module (true)
   - lyricon_module_author ("Nocturn")
   - lyricon_module_description ("Nocturn NetEase Cloud Music Client")
   - lyricon_module_tags (@array/lyricon_module_tags)
3. Create app/src/main/res/values/arrays.xml containing string-array lyricon_module_tags with $syllable and $translation.
4. In app/proguard-rules.pro, append keep rules for io.github.proify.lyricon.**.
5. Implement dev.nocturn.app.playback.lyricon.LyriconModelMapper adhering strictly to the design in explorer_m1_2 and explorer_m1_3 handoffs.
6. Implement app/src/test/kotlin/dev/nocturn/app/playback/lyricon/LyriconModelMapperTest.kt with all 25 unit test cases from explorer_m1_3 handoff.
7. Run the tests and build to verify:
   .\gradlew.bat :app:testDebugUnitTest --tests "dev.nocturn.app.playback.lyricon.LyriconModelMapperTest"
   .\gradlew.bat :app:assembleDebug
8. Document all commands, file diffs, and test outcomes in d:\Nocturn\.agents\teamwork\worker_m1_1\handoff.md.
Follow Handoff Protocol.
When finished, send a message back with your report summary and path.

