# Dispatch for M3 Explorer 3 (AppContainer Wiring & Unit Test Design)

**Milestone**: M3 - LyriconManager Playback Sync & AppContainer Wiring
**Original Request**: `d:\Nocturn\.agents\teamwork\ORIGINAL_REQUEST.md` (MUST read first)
**Project Plan**: `d:\Nocturn\.agents\teamwork\orchestrator\PROJECT.md`
**Working Directory**: `d:\Nocturn\.agents\teamwork\explorer_m3_3`

**Objective**:
1. Inspect `app/src/main/kotlin/dev/nocturn/app/NocturnApplication.kt` (`AppContainer` and `NocturnApplication.onCreate`).
2. Design the exact wiring of `LyriconManager` into `AppContainer`:
   - Property `val lyriconManager: LyriconManager by lazy { ... }` or eagerly started in `NocturnApplication.onCreate()`.
3. Design a comprehensive unit / Robolectric test suite `LyriconManagerTest.kt` in `app/src/test/kotlin/dev/nocturn/app/playback/lyricon/`:
   - Test registration when enabled vs unregister when disabled.
   - Test playback state synchronization (playing/paused).
   - Test song change & lyrics loading/pushing.
   - Test progress position sync and seekTo on discontinuity.
   - Test pure music / empty lyrics placeholder handling.
4. Provide complete code specification for Worker. Do NOT edit source files.
5. Report in `d:\Nocturn\.agents\teamwork\explorer_m3_3\handoff.md`.


## 2026-11-07T14:10:14Z
You are teamwork_explorer for Milestone 3 (LyriconManager Playback Sync & AppContainer Wiring).
Your working directory is: d:\Nocturn\.agents\teamwork\explorer_m3_3
You MUST read d:\Nocturn\.agents\teamwork\ORIGINAL_REQUEST.md before starting work.
Also read d:\Nocturn\.agents\teamwork\orchestrator\PROJECT.md and d:\Nocturn\.agents\teamwork\explorer_m3_3\DISPATCH.md.

Task:
1. Inspect AppContainer and NocturnApplication.onCreate in NocturnApplication.kt.
2. Design wiring of LyriconManager into AppContainer.
3. Design a comprehensive unit / Robolectric test suite LyriconManagerTest.kt covering:
   - Registration and lifecycle (auto registration when enabled, unregister when disabled).
   - Play/pause state synchronization.
   - Song change and lyrics fetch/push.
   - Progress position tick and seekTo synchronization on positionDiscontinuity.
   - Pure music placeholder handling.
4. Provide complete code specification for Worker. Do NOT edit source files.
5. Write handoff report in d:\Nocturn\.agents\teamwork\explorer_m3_3\handoff.md and send completion message.
