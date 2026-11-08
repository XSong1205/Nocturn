# Dispatch for M2 Explorer 1 (SettingsStore & Persistence)

**Milestone**: M2 - Settings Persistence & UI Controls
**Original Request**: `d:\Nocturn\.agents\teamwork\ORIGINAL_REQUEST.md` (MUST read first)
**Project Plan**: `d:\Nocturn\.agents\teamwork\orchestrator\PROJECT.md`
**Working Directory**: `d:\Nocturn\.agents\teamwork\explorer_m2_1`

**Objective**:
1. Inspect `app/src/main/kotlin/dev/nocturn/app/data/SettingsStore.kt`.
2. Inspect existing tests for settings (e.g. `app/src/test/kotlin/dev/nocturn/app/data/` or Robolectric tests).
3. Design the exact implementation for `lyriconEnabled: Boolean`:
   - Property with getter/setter using `KEY_LYRICON_ENABLED = "lyricon-enabled"`
   - Default value `true`
4. Provide recommendations and test specifications for Worker. Do NOT modify source code files.
5. Report in `d:\Nocturn\.agents\teamwork\explorer_m2_1\handoff.md`.

## 2026-11-07T13:49:23Z
You are teamwork_explorer for Milestone 2 (Settings Persistence & UI Controls).
Your working directory is: d:\Nocturn\.agents\teamwork\explorer_m2_1
You MUST read d:\Nocturn\.agents\teamwork\ORIGINAL_REQUEST.md before starting work.
Also read d:\Nocturn\.agents\teamwork\orchestrator\PROJECT.md and d:\Nocturn\.agents\teamwork\explorer_m2_1\DISPATCH.md.

Task:
1. Inspect app/src/main/kotlin/dev/nocturn/app/data/SettingsStore.kt.
2. Inspect existing tests for settings or SharedPreferences in app/src/test/kotlin/dev/nocturn/app/.
3. Design the exact code addition for lyriconEnabled in SettingsStore.kt (getter, setter, KEY_LYRICON_ENABLED, default true).
4. Provide precise guidance for Worker. Do NOT edit source files.
5. Write handoff report in d:\Nocturn\.agents\teamwork\explorer_m2_1\handoff.md and send completion message.
