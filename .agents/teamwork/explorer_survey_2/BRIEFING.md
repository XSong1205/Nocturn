# BRIEFING — 2026-11-07T13:20:00Z

## Mission
Investigate Nocturn's playback lifecycle, MediaSession / Media3 integration, state management, song change flow, and evaluate integration architecture for Lyricon Provider.

## 🔒 My Identity
- Archetype: explorer
- Roles: Teamwork explorer
- Working directory: d:\Nocturn\.agents\teamwork\explorer_survey_2
- Original parent: 7c985a65-7d83-475c-933c-cc1d23728416
- Milestone: Survey & Architecture Discovery

## 🔒 Key Constraints
- Read-only investigation — do NOT implement
- Follow Handoff Protocol (Observation, Logic Chain, Caveats, Conclusion, Verification Method)
- Keep .agents/teamwork/ metadata clean (no source code, tests, or data)

## Current Parent
- Conversation ID: 7c985a65-7d83-475c-933c-cc1d23728416
- Updated: not yet

## Investigation State
- **Explored paths**:
  - `app/src/main/kotlin/dev/nocturn/app/playback/` (all 5 files: `PlaybackContract.kt`, `PlaybackService.kt`, `MediaItemMapping.kt`, `PlaybackSnapshotMapping.kt`, `MediaSessionPlaybackEngine.kt`)
  - `app/src/main/kotlin/dev/nocturn/app/NocturnViewModel.kt`
  - `app/src/main/kotlin/dev/nocturn/app/NocturnApplication.kt` & `AppContainer`
  - `app/src/main/kotlin/dev/nocturn/app/data/SettingsStore.kt`
  - `app/src/main/kotlin/dev/nocturn/app/data/LocalMusicRepository.kt` & `MusicRepository.kt`
  - `app/src/main/kotlin/dev/nocturn/app/data/model/Models.kt` (`Lyrics`, `LyricLine`, `LyricWord`)
  - Lyricon developer documentation (`quick-start`, `player-control`, `connection`, `manifest`, `lyrics-model`)
- **Key findings**:
  - `PlaybackService` is purely an audio host owning `ExoPlayer` and Media3 `MediaSession`. It has no knowledge of lyrics or NetEase network repository.
  - `NocturnViewModel` is activity-scoped (`by viewModels()` in `MainActivity`). If user closes the app (back button), `MainActivity` finishes and `viewModelScope` is cancelled, making it inadequate to host status bar lyrics for background playback.
  - `AppContainer` in `NocturnApplication` is process-scoped and holds `SettingsStore`, `MusicRepository`, and `PlaybackEngine`. When playback is running, Android keeps the process alive via `FOREGROUND_SERVICE_MEDIA_PLAYBACK`.
  - Recommended architecture: A dedicated `LyriconManager` (or `LyriconProviderBridge`) class owned by `AppContainer` (or attached/detached by `PlaybackService`), observing `PlaybackEngine.snapshot` and `SettingsStore.lyriconEnabled`, fetching `Lyrics` from `MusicRepository`.
  - Progress updates run every 500ms (`PROGRESS_UPDATE_INTERVAL_MS = 500L`) in `MediaSessionPlaybackEngine`, matching Lyricon's expected position sync cadence.
  - Media3 position discontinuities (`positionDiscontinuity`) distinguish seek events from normal progression.
- **Unexplored areas**: None remaining for the playback lifecycle investigation.

## Key Decisions Made
- Recommend dedicated `LyriconManager` bridging `PlaybackEngine`, `MusicRepository`, and `SettingsStore`.
- Suggest enriching `PlaybackSnapshot` with `title: String?` and `artist: String?` from `player.currentMediaItem?.mediaMetadata` for full decoupling from `NocturnViewModel`.
- Hook `LyriconManager` lifecycle cleanly to `SettingsStore.lyriconEnabled` and `PlaybackEngine.snapshot.currentSongId != null` (with optional lifecycle tie to `PlaybackService`).

## Artifact Index
- d:\Nocturn\.agents\teamwork\explorer_survey_2\DISPATCH.md — Dispatch instructions
- d:\Nocturn\.agents\teamwork\explorer_survey_2\progress.md — Liveness heartbeat
- d:\Nocturn\.agents\teamwork\explorer_survey_2\handoff.md — Final investigation report
