# Dispatch for Milestone 3 Worker

**Milestone**: M3 - LyriconManager Playback Sync & AppContainer Wiring
**Original Request**: `d:\Nocturn\.agents\teamwork\ORIGINAL_REQUEST.md` (MUST read first)
**Project Plan**: `d:\Nocturn\.agents\teamwork\orchestrator\PROJECT.md`
**Working Directory**: `d:\Nocturn\.agents\teamwork\worker_m3_1`
**Explorer Reports to Consult**:
- `d:\Nocturn\.agents\teamwork\explorer_m3_1\handoff.md`
- `d:\Nocturn\.agents\teamwork\explorer_m3_2\handoff.md`
- `d:\Nocturn\.agents\teamwork\explorer_m3_3\handoff.md`

## File Ownership
You exclusively own and may edit or create these files:
1. `app/src/main/kotlin/dev/nocturn/app/playback/PlaybackContract.kt`
2. `app/src/main/kotlin/dev/nocturn/app/playback/PlaybackSnapshotMapping.kt`
3. `app/src/main/kotlin/dev/nocturn/app/playback/MediaSessionPlaybackEngine.kt`
4. `app/src/main/kotlin/dev/nocturn/app/playback/lyricon/LyriconManager.kt`
5. `app/src/main/kotlin/dev/nocturn/app/NocturnApplication.kt`
6. `app/src/test/kotlin/dev/nocturn/app/playback/PlaybackSnapshotMappingTest.kt`
7. `app/src/test/kotlin/dev/nocturn/app/playback/lyricon/LyriconManagerTest.kt`

## Mandatory Tasks
1. In `PlaybackContract.kt`, enrich `PlaybackSnapshot` with `val title: String? = null` and `val artist: String? = null`.
2. In `PlaybackSnapshotMapping.kt`, update `fromPlayer` to accept `title: String? = null` and `artist: String? = null`, retaining metadata across transient nulls for the same song.
3. In `MediaSessionPlaybackEngine.kt`, extract `title` and `artist` from `currentMediaItem?.mediaMetadata` in `publishSnapshot`.
4. In `PlaybackSnapshotMappingTest.kt`, add unit tests for title/artist mirroring and retention.
5. Create `LyriconManager.kt` in `dev.nocturn.app.playback.lyricon` implementing:
   - `LyriconBridge`, `LyriconPlayerClient`, `LyriconProviderBridge`, `DefaultLyriconProviderBridge`
   - `LyriconManager`: manages provider lifecycle (`register`, `unregister`, `destroy`, `autoSync = true`, `setDisplayTranslation(true)`). Observes `PlaybackEngine.snapshot` and `SettingsStore.lyriconEnabled` (via `OnSharedPreferenceChangeListener`). Handles song transitions, async lyrics fetch via `repository.lyrics(songId)`, `LyriconModelMapper.toLyriconSong`, pure music / instrumental placeholder via `player.sendText("纯音乐，请欣赏")`, play/pause synchronization, position synchronization, and seekTo on `positionDiscontinuity`.
6. In `NocturnApplication.kt`, add `open val lyriconManager: LyriconManager by lazy { ... }` in `AppContainer`, and in `NocturnApplication.onCreate()`, call `container.lyriconManager.start()`.
7. Create `LyriconManagerTest.kt` with comprehensive unit and Robolectric tests (using `FakeLyriconProviderBridge` / `FakeLyriconPlayerClient`) covering registration, unregistering on disable, play/pause state sync, song change & async lyric push, position progress & seekTo, and pure music handling.
8. Verify all tests and builds:
   `.\gradlew.bat :app:testDebugUnitTest --tests "dev.nocturn.app.playback.PlaybackSnapshotMappingTest"`
   `.\gradlew.bat :app:testDebugUnitTest --tests "dev.nocturn.app.playback.lyricon.LyriconManagerTest"`
   `.\gradlew.bat :app:assembleDebug`
   `.\gradlew.bat :app:lintDebug`
9. Write handoff report in `d:\Nocturn\.agents\teamwork\worker_m3_1\handoff.md`.

## 2026-11-07T14:19:15Z
DO NOT CHEAT. All implementations must be genuine. DO NOT hardcode test results, create dummy/facade implementations, or circumvent the intended task. A teamwork_auditor will independently verify your work. Integrity violations WILL be detected and your work WILL be rejected.

You are teamwork_worker for Milestone 3 (LyriconManager Playback Sync & AppContainer Wiring).
Your working directory is: d:\Nocturn\.agents\teamwork\worker_m3_1
You MUST read d:\Nocturn\.agents\teamwork\ORIGINAL_REQUEST.md before starting work.
Also read d:\Nocturn\.agents\teamwork\orchestrator\PROJECT.md and d:\Nocturn\.agents\teamwork\worker_m3_1\DISPATCH.md.

Read the 3 Explorer handoff reports:
- d:\Nocturn\.agents\teamwork\explorer_m3_1\handoff.md
- d:\Nocturn\.agents\teamwork\explorer_m3_2\handoff.md
- d:\Nocturn\.agents\teamwork\explorer_m3_3\handoff.md

Your File Ownership:
1. app/src/main/kotlin/dev/nocturn/app/playback/PlaybackContract.kt
2. app/src/main/kotlin/dev/nocturn/app/playback/PlaybackSnapshotMapping.kt
3. app/src/main/kotlin/dev/nocturn/app/playback/MediaSessionPlaybackEngine.kt
4. app/src/main/kotlin/dev/nocturn/app/playback/lyricon/LyriconManager.kt
5. app/src/main/kotlin/dev/nocturn/app/NocturnApplication.kt
6. app/src/test/kotlin/dev/nocturn/app/playback/PlaybackSnapshotMappingTest.kt
7. app/src/test/kotlin/dev/nocturn/app/playback/lyricon/LyriconManagerTest.kt

## 2026-11-07T14:25:11Z
**Context**: Milestone 3 Implementation Strategy Adjustment
**Content**: 用户最高紧急指令：严禁各专员频繁或在开发阶段执行完整的 `./gradlew` 编译构建与测试，坚决避免将用户本地 CPU/电脑资源占满！请调整策略：专心完成代码编写（PlaybackContract.kt, PlaybackSnapshotMapping.kt, MediaSessionPlaybackEngine.kt, PlaybackSnapshotMappingTest.kt, LyriconManager.kt, NocturnApplication.kt, LyriconManagerTest.kt），编写完成后进行静态语法与逻辑检查，不要在中间频繁执行 gradlew assemble 或测试，等待 M4 最终节点进行单次集中验证！
**Action**: 停止所有中间构建与测试，专注代码与测试用例的编写交付。
