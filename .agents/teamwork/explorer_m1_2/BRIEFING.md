# BRIEFING — 2026-11-07T13:38:00Z

## Mission
Investigate Nocturn data models and Lyricon SDK models to design a robust, complete LyriconModelMapper implementation.

## 🔒 My Identity
- Archetype: explorer
- Roles: investigation, synthesis
- Working directory: d:\Nocturn\.agents\teamwork\explorer_m1_2
- Original parent: 7c985a65-7d83-475c-933c-cc1d23728416
- Milestone: M1 (SDK Integration, Manifest Metadata & LyriconModelMapper)

## 🔒 Key Constraints
- Read-only investigation — do NOT implement
- Do NOT write source code files yourself (source code changes belong to worker)
- Files for content delivery, Messages for coordination

## Current Parent
- Conversation ID: 7c985a65-7d83-475c-933c-cc1d23728416
- Updated: not yet

## Investigation State
- **Explored paths**:
  - `d:\Nocturn\.agents\teamwork\ORIGINAL_REQUEST.md`
  - `d:\Nocturn\.agents\teamwork\orchestrator\PROJECT.md`
  - `app/src/main/kotlin/dev/nocturn/app/data/model/Models.kt` (Nocturn domain models)
  - `app/src/main/kotlin/dev/nocturn/app/ui/screens/player/AccompanistLyrics.kt` (Nocturn lyrics conversion reference)
  - `app/src/test/kotlin/dev/nocturn/app/ui/AccompanistLyricsTest.kt` (existing lyrics test patterns)
  - `app/src/main/kotlin/dev/nocturn/app/data/NeteaseJson.kt` (instrumental parsing handling)
  - `app/src/main/kotlin/dev/nocturn/app/playback/PlaybackContract.kt` (PlaybackSnapshot and PlayableSong)
  - Bytecode of `model-0.1.70.jar` (`Song`, `RichLyricLine`, `LyricWord`) from Gradle cache via `javap`
  - Official Lyricon developer docs (`https://tomakino.github.io/lyricon/zh-cn/developer/provider/lyrics-model`)
- **Key findings**:
  - `Song`: fields `id: String?`, `name: String`, `artist: String?`, `duration: Long`, `lyrics: List<RichLyricLine>?`.
  - `RichLyricLine`: fields `begin: Long`, `end: Long`, `duration: Long`, `isAlignedRight: Boolean`, `metadata: LyricMetadata?`, `text: String?`, `words: List<LyricWord>?`, `secondary: String?`, `secondaryWords: List<LyricWord>?`, `translation: String?`, `translationWords: List<LyricWord>?`, `roma: String?`.
  - `LyricWord`: fields `begin: Long`, `end: Long`, `duration: Long`, `text: String?`, `metadata: LyricMetadata?`.
  - Both Nocturn and Lyricon declare `Song` and `LyricWord`, requiring Kotlin import aliases (`NocturnSong`, `NocturnLyricWord`, `NocturnLyricLine`, `NocturnLyrics` vs `LyriconSong`, `LyriconRichLine`, `LyriconWord`).
  - Pure music tracks return `Lyrics.EMPTY` in Nocturn; `LyriconModelMapper` should output `null` lyrics in `LyriconSong` and expose `PURE_MUSIC_TEXT` / `NO_LYRICS_TEXT` constants.
  - End timestamp calculation uses monotonic clamping and interpolates missing LRC durations to next line start or song duration fallback (5s).
  - Word timestamps are strictly clamped within parent line interval `[lineBegin, lineEnd]`.
- **Unexplored areas**:
  - None for M1-2 scope.

## Key Decisions Made
- Designed complete `LyriconModelMapper.kt` in package `dev.nocturn.app.playback.lyricon`.
- Decided on clean import aliasing strategy.
- Formulated monotonic line boundary calculation with arithmetic overflow safeguards.
- Formulated word boundary clamping to parent `[lineBegin, lineEnd]`.
- Mapped `LyricLine.translation` to `RichLyricLine.translation` and `LyricLine.romanization` to `RichLyricLine.roma`.

## Artifact Index
- `handoff.md` — Complete handoff report with exact model analysis, complete `LyriconModelMapper.kt` code blueprint, and verification guidance
- `progress.md` — Progress tracker
