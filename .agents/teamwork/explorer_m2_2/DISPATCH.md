# Dispatch for M2 Explorer 2 (UI State, ViewModel & LibraryScreen)

**Milestone**: M2 - Settings Persistence & UI Controls
**Original Request**: `d:\Nocturn\.agents\teamwork\ORIGINAL_REQUEST.md` (MUST read first)
**Project Plan**: `d:\Nocturn\.agents\teamwork\orchestrator\PROJECT.md`
**Working Directory**: `d:\Nocturn\.agents\teamwork\explorer_m2_2`

**Objective**:
1. Inspect `app/src/main/kotlin/dev/nocturn/app/ui/state/LibraryUiState.kt` and `SettingsUiState.kt`.
2. Inspect `app/src/main/kotlin/dev/nocturn/app/NocturnViewModel.kt` (lines 80-90, 897-918) for how settings are initialized and mutated.
3. Inspect `app/src/main/kotlin/dev/nocturn/app/ui/screens/library/LibraryScreen.kt` (lines 646-805, `LibrarySettingsSection`).
4. Inspect `SearchLibraryScreensTest.kt` or `ScreenshotGalleryTest.kt` to check if UI tests render `LibraryScreen` or verify settings switches.
5. Design exact modifications to expose `lyriconEnabled` in UI state, ViewModel mutator, and Compose UI switch with `testTag("library-lyricon-switch")`.
6. Report in `d:\Nocturn\.agents\teamwork\explorer_m2_2\handoff.md`.


## 2026-11-07T13:49:28Z
Task:
1. Inspect LibraryUiState.kt, SettingsUiState.kt, and NocturnViewModel.kt.
2. Inspect LibraryScreen.kt (LibrarySettingsSection) and existing UI screenshot/component tests (SearchLibraryScreensTest, ScreenshotGalleryTest).
3. Design exact additions for lyriconEnabled in UI state, NocturnViewModel mutator, and Compose UI switch in LibraryScreen with testTag("library-lyricon-switch").
4. Provide precise guidance for Worker. Do NOT edit source files.
5. Write handoff report in d:\Nocturn\.agents\teamwork\explorer_m2_2\handoff.md and send completion message.
