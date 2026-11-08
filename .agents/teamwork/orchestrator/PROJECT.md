# Project: Nocturn Lyricon Provider Integration

## Architecture
Nocturn provides an Android NetEase Cloud Music client. The Lyricon Provider integration adapts the Lyricon Provider SDK (`io.github.proify.lyricon:provider:0.1.70`) to push track metadata, playback states, progress, seeks, and multi-tier lyrics (including YRC syllables and translations) to the Lyricon system status bar lyric service.

### Component Structure & Data Flow:
```
┌────────────────────────────────────────────────────────────────────────┐
│                          NocturnApplication                            │
│                                                                        │
│  ┌──────────────────────────────────────────────────────────────────┐  │
│  │                           AppContainer                           │  │
│  │                                                                  │  │
│  │  ┌──────────────┐     ┌────────────────┐    ┌─────────────────┐  │  │
│  │  │SettingsStore │     │ MusicRepository│    │ PlaybackEngine  │  │  │
│  │  └──────┬───────┘     └───────┬────────┘    └────────┬────────┘  │  │
│  │         │                     │                      │           │  │
│  │         ▼                     ▼                      ▼           │  │
│  │  ┌────────────────────────────────────────────────────────────┐  │  │
│  │  │                       LyriconManager                       │  │  │
│  │  │  - Observes snapshot: StateFlow<PlaybackSnapshot>          │  │  │
│  │  │  - Observes lyriconEnabled: Boolean                        │  │  │
│  │  │  - Loads Lyrics via repository.lyrics(songId)              │  │  │
│  │  │  - Converts via LyriconModelMapper                         │  │  │
│  │  │  - Manages LyriconProvider lifecycle (register/destroy)    │  │  │
│  │  └────────────────────────────┬───────────────────────────────┘  │  │
│  │                               │ IPC                              │  │
│  │                               ▼                                  │  │
│  │                   [ Lyricon Central Service ]                    │  │
│  └──────────────────────────────────────────────────────────────────┘  │
└────────────────────────────────────────────────────────────────────────┘
```

## Feature Inventory
| # | Feature | Description | Milestone | Source |
|---|---------|-------------|-----------|--------|
| 1 | SDK Dependency | Add `io.github.proify.lyricon:provider:0.1.70` to `app/build.gradle.kts` | M1 | Survey / R1 |
| 2 | Manifest Module Metadata | Declare `lyricon_module` as `true` in `<application>` | M1 | Survey / R1 |
| 3 | Manifest Author Metadata | Declare `lyricon_module_author` in `<application>` | M1 | Survey / R1 |
| 4 | Manifest Description Metadata | Declare `lyricon_module_description` in `<application>` | M1 | Survey / R1 |
| 5 | Manifest Capabilities Tags | Declare `lyricon_module_tags` resource pointing to `@array/lyricon_module_tags` | M1 | Survey / R1 |
| 6 | Capabilities Array Resource | Add `lyricon_module_tags` array in `res/values/arrays.xml` with `$syllable` and `$translation` | M1 | Survey / R1 |
| 7 | LyriconModelMapper Song Mapping | Map Nocturn `Song` (`id`, `name`, `artistText`, `durationMs`) to Lyricon `Song` | M1 | Survey / R3 |
| 8 | LyriconModelMapper Line Mapping | Map Nocturn `LyricLine` to Lyricon `RichLyricLine` (`begin`, `end`, `text`) | M1 | Survey / R3 |
| 9 | LyriconModelMapper Syllable Mapping | Map Nocturn `LyricWord` (`startMs`, `durationMs`) to Lyricon `LyricWord` (`begin`, `end`) | M1 | Survey / R3 |
| 10 | LyriconModelMapper Translation | Map Nocturn `LyricLine.translation` to Lyricon `RichLyricLine.translation` | M1 | Survey / R3 |
| 11 | Pure Music / Empty Handling | Handle empty lyrics and instrumental tracks gracefully (placeholder/sendText) | M1 | Survey / R3 |
| 12 | Settings Store Persistence | Add `lyriconEnabled` property in `SettingsStore` (persisted in SharedPreferences) | M2 | Survey / R4 |
| 13 | UI State Integration | Expose `lyriconEnabled` in `LibraryUiState` and `SettingsUiState` | M2 | Survey / R4 |
| 14 | ViewModel Control | Add `setLyriconEnabled(Boolean)` in `NocturnViewModel` | M2 | Survey / R4 |
| 15 | Compose Settings Toggle | Add M3 switch in `LibraryScreen` (and settings screens) with test tags | M2 | Survey / R4 |
| 16 | Snapshot Metadata Enrichment | Enrich `PlaybackSnapshot` with `title` and `artist` from `MediaMetadata` | M3 | Survey / R2 |
| 17 | LyriconManager Core | Manage `LyriconProvider` lifecycle (`createProvider`, `register`, `unregister`, `destroy`) | M3 | Survey / R2 |
| 18 | Playback State Sync | Synchronize `playing` state via `player.setPlaybackState` | M3 | Survey / R2 |
| 19 | Progress & Seek Sync | Synchronize position via `player.setPosition` and seeks via `player.seekTo` | M3 | Survey / R2 |
| 20 | Dynamic Song & Lyric Push | Load lyrics asynchronously on song change and push `player.setSong` / `sendText` | M3 | Survey / R2, R3 |
| 21 | Dynamic Settings Toggle | Stop/start provider push when settings toggle changes | M3 | Survey / R4 |
| 22 | AppContainer Wireup | Initialize and hold `LyriconManager` in `AppContainer` / `NocturnApplication` | M3 | Survey / R2 |
| 23 | Comprehensive Tests & Verification | Unit & Robolectric tests for Mapper, Settings, Manager, and full build/lint pass | M4 | Survey / R5 |

## Milestones
| # | Name | Scope | Dependencies | Status |
|---|------|-------|-------------|--------|
| 1 | M1: SDK Integration, Manifest & LyriconModelMapper | `app/build.gradle.kts`, `AndroidManifest.xml`, `arrays.xml`, `LyriconModelMapper.kt`, `LyriconModelMapperTest.kt` | none | DONE |
| 2 | M2: Settings Persistence & UI Switch | `SettingsStore.kt`, `LibraryUiState.kt`, `SettingsUiState.kt`, `NocturnViewModel.kt`, `LibraryScreen.kt`, `SettingsStoreTest.kt` | none | IN_PROGRESS |
| 3 | M3: LyriconManager Playback Sync & AppContainer Wiring | `PlaybackContract.kt`, `MediaSessionPlaybackEngine.kt`, `LyriconManager.kt`, `AppContainer` in `NocturnApplication.kt`, `LyriconManagerTest.kt` | M1, M2 | PLANNED |
| 4 | M4: Full E2E & Regression Verification | Comprehensive test execution (`:app:testDebugUnitTest`), assemble and lint verification (`:app:assembleDebug`, `:app:lintDebug`) | M1, M2, M3 | PLANNED |

## Interface Contracts

### 1. `LyriconModelMapper` Contract
```kotlin
package dev.nocturn.app.playback.lyricon

import dev.nocturn.app.data.model.Song as NocturnSong
import dev.nocturn.app.data.model.Lyrics as NocturnLyrics
import dev.nocturn.app.data.model.LyricLine as NocturnLyricLine
import dev.nocturn.app.data.model.LyricWord as NocturnLyricWord
import io.github.proify.lyricon.lyric.model.Song as LyriconSong
import io.github.proify.lyricon.lyric.model.RichLyricLine as LyriconRichLine
import io.github.proify.lyricon.lyric.model.LyricWord as LyriconWord

object LyriconModelMapper {
    fun toLyriconSong(song: NocturnSong, lyrics: NocturnLyrics = NocturnLyrics.EMPTY): LyriconSong
    fun toLyriconSong(id: Long, title: String, artist: String?, durationMs: Long, lyrics: NocturnLyrics = NocturnLyrics.EMPTY): LyriconSong
    fun toLyriconLines(lyrics: NocturnLyrics, songDurationMs: Long): List<LyriconRichLine>
}
```

### 2. `LyriconManager` Contract
```kotlin
package dev.nocturn.app.playback.lyricon

interface LyriconBridge {
    fun start()
    fun stop()
    fun setEnabled(enabled: Boolean)
    fun isEnabled(): Boolean
}
```

### 3. `SettingsStore` Extension Contract
```kotlin
// In dev.nocturn.app.data.SettingsStore
var lyriconEnabled: Boolean
```

## Code Layout
- `app/build.gradle.kts`: Dependency declaration
- `app/src/main/AndroidManifest.xml`: `<meta-data>` tags under `<application>`
- `app/src/main/res/values/arrays.xml`: Capability array `@array/lyricon_module_tags`
- `app/src/main/kotlin/dev/nocturn/app/playback/lyricon/`:
  - `LyriconModelMapper.kt`
  - `LyriconManager.kt`
  - `LyriconProviderWrapper.kt` (optional testable abstraction over `LyriconProvider` / `RemotePlayer`)
- `app/src/main/kotlin/dev/nocturn/app/data/SettingsStore.kt`: `lyriconEnabled`
- `app/src/main/kotlin/dev/nocturn/app/NocturnApplication.kt`: `AppContainer.lyriconManager`
- `app/src/main/kotlin/dev/nocturn/app/NocturnViewModel.kt`: `setLyriconEnabled`
- `app/src/main/kotlin/dev/nocturn/app/ui/screens/library/LibraryScreen.kt`: Settings switch UI
- `app/src/test/kotlin/dev/nocturn/app/playback/lyricon/`:
  - `LyriconModelMapperTest.kt`
  - `LyriconManagerTest.kt`
