# BRIEFING — 2026-11-07T14:17:00Z

## Mission
Investigate and design LyriconManager.kt, provider lifecycle, playback synchronization, testable abstractions, and complete code specifications for Worker in Milestone 3.

## 🔒 My Identity
- Archetype: explorer
- Roles: investigation, synthesis
- Working directory: d:\Nocturn\.agents\teamwork\explorer_m3_2
- Original parent: 7c985a65-7d83-475c-933c-cc1d23728416
- Milestone: M3 (LyriconManager Playback Sync & AppContainer Wiring)

## 🔒 Key Constraints
- Read-only investigation — do NOT implement
- Do NOT edit source files
- Design testable interfaces/abstraction for LyriconProvider & RemotePlayer
- Provide complete code specification for Worker

## Current Parent
- Conversation ID: 7c985a65-7d83-475c-933c-cc1d23728416
- Updated: 2026-11-07T14:10:08Z

## Investigation State
- **Explored paths**:
  - `dev.nocturn.app.playback.PlaybackContract.kt` (PlaybackSnapshot, positionDiscontinuity)
  - `dev.nocturn.app.playback.MediaSessionPlaybackEngine.kt` (Media3 controller, progressJob, discontinuity increment)
  - `dev.nocturn.app.playback.lyricon.LyriconModelMapper.kt` (M1 mapper, pure music constants, toLyriconSong)
  - `dev.nocturn.app.data.SettingsStore.kt` (M2 lyriconEnabled SharedPreferences key)
  - `dev.nocturn.app.data.MusicRepository.kt` (suspend fun lyrics(songId: Long): Lyrics)
  - `dev.nocturn.app.NocturnApplication.kt` (AppContainer structure)
  - `dev.nocturn.localapi.client.LyricParser.kt` (instrumental/pure music parsing heuristics)
- **Key findings**:
  - `LyriconProviderBridge` and `LyriconPlayerClient` abstractions allow 100% unit test coverage without Android IPC or central service dependencies.
  - Media3 position discontinuity increments reliably distinguish scrubbing/seeks from 500ms progress ticks.
  - NetEase instrumental tracks are identified by `"纯音乐，请欣赏"` / `"纯音乐，请欣赏。"` / `"纯音乐，请安静欣赏"` lines or placeholder title, routing to `player.sendText("纯音乐，请欣赏")`.
  - SettingsStore changes are observed via `SharedPreferences.OnSharedPreferenceChangeListener` with strong reference retention, ensuring dynamic register/unregister.
- **Unexplored areas**: None for M3.2 scope.

## Key Decisions Made
- Define `LyriconPlayerClient` and `LyriconProviderBridge` abstractions with default production wrapper `DefaultLyriconProviderBridge`.
- `LyriconManager` constructor accepts injectable `bridgeFactory` and `CoroutineScope` for pure offline unit testing.
- Disambiguate song change vs scrub seek: song change calls `setPosition` and triggers async lyrics load; position discontinuity on same song calls `seekTo`.
- Instrument tracks route to `sendText(LyriconModelMapper.PURE_MUSIC_TEXT)`. Normal tracks (including empty lyrics) map via `toLyriconSong` and call `setSong(lyriconSong)`.

## Artifact Index
- `d:\Nocturn\.agents\teamwork\explorer_m3_2\handoff.md` — Complete specification and handoff report for Worker
