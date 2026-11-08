# Dispatch for M3 Explorer 2 (LyriconManager Core Engine & Sync Logic)

**Milestone**: M3 - LyriconManager Playback Sync & AppContainer Wiring
**Original Request**: `d:\Nocturn\.agents\teamwork\ORIGINAL_REQUEST.md` (MUST read first)
**Project Plan**: `d:\Nocturn\.agents\teamwork\orchestrator\PROJECT.md`
**Working Directory**: `d:\Nocturn\.agents\teamwork\explorer_m3_2`

**Objective**:
1. Review Lyricon SDK API contracts from `spec_miner_survey_1` and `LyriconModelMapper`.
2. Design `dev.nocturn.app.playback.lyricon.LyriconManager`:
   - Instantiation via `LyriconFactory.createProvider(context)`.
   - Provider lifecycle: `register()`, `unregister()`, `destroy()`, `autoSync = true`.
   - State observation: Observe `PlaybackEngine.snapshot` and `SettingsStore.lyriconEnabled`.
   - On song change (`currentSongId` changes): Load lyrics asynchronously via `MusicRepository.lyrics(songId)`, map to Lyricon model via `LyriconModelMapper.toLyriconSong`, push to `player.setSong(lyriconSong)` or `player.sendText("纯音乐，请欣赏")` if instrumental.
   - On playback state change: Call `player.setPlaybackState(snapshot.playing)`.
   - On position tick: Call `player.setPosition(snapshot.positionMs)`.
   - On seek (`positionDiscontinuity` changes): Call `player.seekTo(snapshot.positionMs)`.
   - On settings toggle: If disabled -> `unregister()`, pause updates; if enabled -> `register()`, push current state.
3. Design testable abstraction/interfaces if needed to allow mock verification in unit tests.
4. Provide complete code specification for Worker. Do NOT edit source files.
5. Report in `d:\Nocturn\.agents\teamwork\explorer_m3_2\handoff.md`.

## 2026-11-07T14:10:08Z
[Message] timestamp=2026-11-07T14:10:08Z sender=7c985a65-7d83-475c-933c-cc1d23728416 priority=MESSAGE_PRIORITY_HIGH
content=You are teamwork_explorer for Milestone 3 (LyriconManager Playback Sync & AppContainer Wiring).
Your working directory is: d:\Nocturn\.agents\teamwork\explorer_m3_2
You MUST read d:\Nocturn\.agents\teamwork\ORIGINAL_REQUEST.md before starting work.
Also read d:\Nocturn\.agents\teamwork\orchestrator\PROJECT.md and d:\Nocturn\.agents\teamwork\explorer_m3_2\DISPATCH.md.

Task:
1. Design LyriconManager.kt in dev.nocturn.app.playback.lyricon:
   - Provider lifecycle: createProvider, register, unregister, destroy, autoSync = true.
   - Observe snapshot (playing, positionMs, positionDiscontinuity, songId) and SettingsStore.lyriconEnabled.
   - Synchronize song changes with async lyrics loading, playback state, position, and seekTo.
   - Pure music placeholder support.
   - Settings toggle start/pause push.
2. Design testable interfaces/abstraction for LyriconProvider and RemotePlayer to allow robust unit testing.
3. Provide complete code specification for Worker. Do NOT edit source files.
4. Write handoff report in d:\Nocturn\.agents\teamwork\explorer_m3_2\handoff.md and send completion message.
