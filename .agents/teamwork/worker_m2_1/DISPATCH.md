# Dispatch for Milestone 2 Worker

**Milestone**: M2 - Settings Persistence & UI Controls
**Original Request**: `d:\Nocturn\.agents\teamwork\ORIGINAL_REQUEST.md` (MUST read first)
**Project Plan**: `d:\Nocturn\.agents\teamwork\orchestrator\PROJECT.md`
**Working Directory**: `d:\Nocturn\.agents\teamwork\worker_m2_1`
**Explorer Reports to Consult**:
- `d:\Nocturn\.agents\teamwork\explorer_m2_1\handoff.md`
- `d:\Nocturn\.agents\teamwork\explorer_m2_2\handoff.md`
- `d:\Nocturn\.agents\teamwork\explorer_m2_3\handoff.md`

## File Ownership
You exclusively own and may edit or create these files:
1. `app/src/main/kotlin/dev/nocturn/app/data/SettingsStore.kt`
2. `app/src/main/kotlin/dev/nocturn/app/ui/state/LibraryUiState.kt`
3. `app/src/main/kotlin/dev/nocturn/app/ui/state/SettingsUiState.kt`
4. `app/src/main/kotlin/dev/nocturn/app/NocturnViewModel.kt`
5. `app/src/main/kotlin/dev/nocturn/app/ui/screens/library/LibraryScreen.kt`
6. `app/src/main/kotlin/dev/nocturn/app/ui/NocturnApp.kt`
7. `app/src/test/kotlin/dev/nocturn/app/data/SettingsStoreTest.kt`
8. `app/src/test/kotlin/dev/nocturn/app/SettingsViewModelTest.kt`
9. `app/src/test/kotlin/dev/nocturn/app/ui/SearchLibraryScreensTest.kt`

## Mandatory Tasks
1. In `app/src/main/kotlin/dev/nocturn/app/data/SettingsStore.kt`, add `var lyriconEnabled: Boolean` reading `KEY_LYRICON_ENABLED = "lyricon-enabled"` (default `true`). Change companion object to `internal companion object`.
2. In `app/src/main/kotlin/dev/nocturn/app/ui/state/LibraryUiState.kt` and `SettingsUiState.kt`, add `val lyriconEnabled: Boolean = true`.
3. In `app/src/main/kotlin/dev/nocturn/app/NocturnViewModel.kt`:
   - Initialize `mutableLibrary` with `lyriconEnabled = settings.lyriconEnabled`.
   - Initialize `mutableSettings` with `lyriconEnabled = settings.lyriconEnabled`.
   - Add mutator `fun setLyriconEnabled(enabled: Boolean)` updating `settings.lyriconEnabled`, `mutableLibrary`, and `mutableSettings`.
4. In `app/src/main/kotlin/dev/nocturn/app/ui/screens/library/LibraryScreen.kt`, add `onLyriconChange: (Boolean) -> Unit = {}` to `LibraryScreen` and `LibrarySettingsSection`. In `LibrarySettingsSection`, add the "词幕状态栏歌词" Switch row with `modifier = Modifier.testTag("library-lyricon-switch")`.
5. In `app/src/main/kotlin/dev/nocturn/app/ui/NocturnApp.kt`, pass `onLyriconChange = model::setLyriconEnabled` in `LibraryRoute`.
6. Implement `app/src/test/kotlin/dev/nocturn/app/data/SettingsStoreTest.kt` with the complete 7-test suite designed by Explorer M2.3.
7. Update `app/src/test/kotlin/dev/nocturn/app/SettingsViewModelTest.kt` adding the 3 ViewModel test cases.
8. Update `app/src/test/kotlin/dev/nocturn/app/ui/SearchLibraryScreensTest.kt` adding the switch node assertion and `libraryLyriconToggleInvokesCallback` test.
9. Run build and tests:
   `.\gradlew.bat :app:testDebugUnitTest --tests "dev.nocturn.app.data.SettingsStoreTest"`
   `.\gradlew.bat :app:testDebugUnitTest --tests "dev.nocturn.app.SettingsViewModelTest"`
   `.\gradlew.bat :app:testDebugUnitTest --tests "dev.nocturn.app.ui.SearchLibraryScreensTest"`
   `.\gradlew.bat :app:lintDebug`
10. Write handoff report in `d:\Nocturn\.agents\teamwork\worker_m2_1\handoff.md`.


## 2026-11-07T13:56:31Z
DO NOT CHEAT. All implementations must be genuine. DO NOT hardcode test results, create dummy/facade implementations, or circumvent the intended task. A teamwork_auditor will independently verify your work. Integrity violations WILL be detected and your work WILL be rejected.

You are teamwork_worker for Milestone 2 (Settings Persistence & UI Controls).
Your working directory is: d:\Nocturn\.agents\teamwork\worker_m2_1
You MUST read d:\Nocturn\.agents\teamwork\ORIGINAL_REQUEST.md before starting work.
Also read d:\Nocturn\.agents\teamwork\orchestrator\PROJECT.md and d:\Nocturn\.agents\teamwork\worker_m2_1\DISPATCH.md.

Read the 3 Explorer handoff reports:
- d:\Nocturn\.agents\teamwork\explorer_m2_1\handoff.md
- d:\Nocturn\.agents\teamwork\explorer_m2_2\handoff.md
- d:\Nocturn\.agents\teamwork\explorer_m2_3\handoff.md
