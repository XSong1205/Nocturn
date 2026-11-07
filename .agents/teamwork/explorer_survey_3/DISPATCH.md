# Dispatch for Explorer 3 (Lyrics Data Models & Settings Architecture)

**Task**: Investigate Nocturn's lyrics data models, parsing pipeline, and Settings persistence / UI.
**Original Request**: `d:\Nocturn\.agents\teamwork\ORIGINAL_REQUEST.md` (MUST read first)
**Working Directory**: `d:\Nocturn\.agents\teamwork\explorer_survey_3`

**Objective**:
1. Explore how lyrics are represented and parsed in Nocturn (e.g. in `local-api/src/commonMain/.../lyrics`, `app/src/main/kotlin/dev/nocturn/app/data/`, etc.). Check models for lines, timestamps, YRC/syllable/word timestamps, and translations.
2. Explore Nocturn's Settings storage and UI (how preferences are stored, e.g., EncryptedSessionStore, DataStore, or SharedPreferences; how SettingsScreen or Preferences are configured in Compose UI and ViewModel).
3. Identify how the new Lyricon Provider setting (toggle on/off) should be stored, exposed in Settings UI, and observed reactively.
4. Detail the mapping from Nocturn's lyric data models to Lyricon's expected formats.
5. Report findings in `d:\Nocturn\.agents\teamwork\explorer_survey_3/handoff.md`.

## 2026-11-07T13:10:32Z
From: 7c985a65-7d83-475c-933c-cc1d23728416
Task:
Investigate Nocturn's lyrics data models, parsing pipeline, and Settings persistence / UI.

Examine:
1. Nocturn's lyrics handling: Check `local-api/src/commonMain/.../lyrics` and `app/src/main/kotlin/dev/nocturn/app/data/`, etc.
   - What data models represent parsed lyrics?
   - How are LRC, YRC, syllable/word timing, and translation lyrics parsed and stored?
   - How does the UI/ViewModel obtain lyrics when a song is playing?
2. Nocturn's Settings storage and UI:
   - Where and how are user preferences stored (DataStore, SharedPreferences, EncryptedSessionStore)?
   - How are settings exposed to Compose UI (`SettingsScreen.kt` or similar)?
   - How can we add a new switch for Lyricon Provider enabled/disabled, persist it, and observe changes reactively?
3. Mapping details:
   - Detailed mapping between Nocturn lyric types (e.g. `ParsedLyrics`, `LyricLine`, `LyricWord`, etc.) and Lyricon's expected structure.

Deliverable:
Write a comprehensive report in d:\Nocturn\.agents\teamwork\explorer_survey_3\handoff.md.
Follow Handoff Protocol.
When finished, send a message back with your report summary and path.
