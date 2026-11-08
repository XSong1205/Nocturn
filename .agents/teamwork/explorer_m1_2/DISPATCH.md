# Dispatch for M1 Explorer 2 (Mapper & Edge Cases)

**Milestone**: M1 - SDK Integration, Manifest Metadata & LyriconModelMapper
**Original Request**: `d:\Nocturn\.agents\teamwork\ORIGINAL_REQUEST.md` (MUST read first)
**Project Plan**: `d:\Nocturn\.agents\teamwork\orchestrator\PROJECT.md`
**Working Directory**: `d:\Nocturn\.agents\teamwork\explorer_m1_2`

**Objective**:
1. Explore `dev.nocturn.app.data.model.Song`, `LyricLine`, `LyricWord`, and `Lyrics` in `app/src/main/kotlin/dev/nocturn/app/data/model/Models.kt`.
2. Explore Lyricon SDK models: `io.github.proify.lyricon.lyric.model.Song`, `RichLyricLine`, and `LyricWord`.
3. Design complete, bulletproof implementation for `LyriconModelMapper.kt` in package `dev.nocturn.app.playback.lyricon`:
   - Import aliases to resolve `Song` and `LyricWord` name collisions.
   - Robust calculation of line `begin` and `end` (handle null `durationMs`, last line duration, boundary clamping).
   - Word timing transformation: `begin = word.startMs`, `end = word.startMs + word.durationMs`, clamped to line boundaries `[line.begin, line.end]`.
   - Handling of translations (`line.translation`) and empty/instrumental lyrics.
4. Provide the exact implementation design for the Worker. Do NOT write code files yourself.
5. Report in `d:\Nocturn\.agents\teamwork\explorer_m1_2\handoff.md`.


## 2026-11-07T13:30:31Z
You are teamwork_explorer for Milestone 1 (SDK Integration, Manifest Metadata & LyriconModelMapper).
Your working directory is: d:\Nocturn\.agents\teamwork\explorer_m1_2
You MUST read d:\Nocturn\.agents\teamwork\ORIGINAL_REQUEST.md before starting work.
Also read d:\Nocturn\.agents\teamwork\orchestrator\PROJECT.md and d:\Nocturn\.agents\teamwork\explorer_m1_2\DISPATCH.md.

Task:
1. Examine Nocturn's data models in app/src/main/kotlin/dev/nocturn/app/data/model/Models.kt (Song, Lyrics, LyricLine, LyricWord).
2. Examine Lyricon's models (Song, RichLyricLine, LyricWord).
3. Design the exact, complete LyriconModelMapper.kt implementation in package dev.nocturn.app.playback.lyricon:
   - Handle Kotlin import aliases (NocturnSong vs LyriconSong, NocturnLyricWord vs LyriconWord).
   - Line begin and end calculation with boundary enforcement and duration fallback.
   - Word begin and end conversion with parent line clamping.
   - Translation and romanization mapping.
   - Empty lyrics and pure music scenario handling.
4. Provide the exact implementation details for the Worker. Do NOT write source code files yourself.
5. Write handoff report in d:\Nocturn\.agents\teamwork\explorer_m1_2\handoff.md and send completion message.
