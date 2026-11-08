# Dispatch for M1 Explorer 3 (Unit Test Design)

**Milestone**: M1 - SDK Integration, Manifest Metadata & LyriconModelMapper
**Original Request**: `d:\Nocturn\.agents\teamwork\ORIGINAL_REQUEST.md` (MUST read first)
**Project Plan**: `d:\Nocturn\.agents\teamwork\orchestrator\PROJECT.md`
**Working Directory**: `d:\Nocturn\.agents\teamwork\explorer_m1_3`

**Objective**:
1. Explore existing unit test patterns in Nocturn (`app/src/test/kotlin/dev/nocturn/app/...`, e.g., `AccompanistLyricsTest`, `NeteaseJsonLyricsTest`, `PlaybackEngineTest`).
2. Identify test dependencies (`kotlin.test`, JUnit4, Robolectric) and annotations used.
3. Design a comprehensive unit test suite `LyriconModelMapperTest.kt` in `app/src/test/kotlin/dev/nocturn/app/playback/lyricon/`:
   - Basic mapping with line-only lyrics.
   - Syllable-level YRC lyrics with word timestamps.
   - Clamping of words that exceed line end.
   - Missing/null duration handling (extrapolation to next line or song duration).
   - Translation mapping and display flags.
   - Empty lyrics / instrumental scenarios.
4. Provide the exact test specifications and test code structure for the Worker. Do NOT write code files yourself.
5. Report in `d:\Nocturn\.agents\teamwork\explorer_m1_3\handoff.md`.

## 2026-11-07T13:30:37Z
[Message] timestamp=2026-11-07T13:30:37Z sender=7c985a65-7d83-475c-933c-cc1d23728416 priority=MESSAGE_PRIORITY_HIGH content=You are teamwork_explorer for Milestone 1 (SDK Integration, Manifest Metadata & LyriconModelMapper).
Your working directory is: d:\Nocturn\.agents\teamwork\explorer_m1_3
You MUST read d:\Nocturn\.agents\teamwork\ORIGINAL_REQUEST.md before starting work.
Also read d:\Nocturn\.agents\teamwork\orchestrator\PROJECT.md and d:\Nocturn\.agents\teamwork\explorer_m1_3\DISPATCH.md.

Task:
1. Examine existing unit test structures and style in app/src/test/kotlin/dev/nocturn/app/.
2. Design a comprehensive unit test suite LyriconModelMapperTest.kt in app/src/test/kotlin/dev/nocturn/app/playback/lyricon/.
3. Specify test cases for:
   - Line-by-line lyric mapping (timestamps, text).
   - Word/syllable (YRC) mapping with begin/end timestamps.
   - Word timing boundary clamping when words exceed line duration.
   - End timestamp calculation when durationMs is null or zero (extrapolating to next line or song duration).
   - Translation preservation and mapping.
   - Instrumental and empty lyrics handling.
   - Null or blank translation handling.
4. Provide the exact test file structure and assertions for the Worker. Do NOT write source code files yourself.
5. Write handoff report in d:\Nocturn\.agents\teamwork\explorer_m1_3\handoff.md and send completion message.
