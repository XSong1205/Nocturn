# Handoff Report: Milestone 2 — Settings Persistence & UI Controls (UI State, ViewModel & LibraryScreen)

## 1. Observation

Direct investigation of the codebase and test suites revealed the following exact structures, paths, and line ranges:

### 1.1 `LibraryUiState.kt`
- **Path**: `app/src/main/kotlin/dev/nocturn/app/ui/state/LibraryUiState.kt`
- **Lines 26–42**:
  ```kotlin
  data class LibraryUiState(
      val loggedIn: Boolean = false,
      val loading: Boolean = true,
      val error: String? = null,
      val profile: AccountProfile? = null,
      val stats: AccountStats? = null,
      val playlists: List<PlaylistSummary> = emptyList(),
      val recentSongs: List<Song> = emptyList(),
      val likedCount: Int? = null,
      val likedSongIds: Set<Long> = emptySet(),
      val nowPlayingSongId: Long? = null,
      val quality: AudioQuality = AudioQuality.Exhigh,
      val themeMode: ThemeMode = ThemeMode.System,
      val dynamicColor: Boolean = true,
      val hapticsEnabled: Boolean = true,
      val login: LoginUiState = LoginUiState(),
  )
  ```
  All parameters have default values.

### 1.2 `SettingsUiState.kt`
- **Path**: `app/src/main/kotlin/dev/nocturn/app/ui/state/SettingsUiState.kt`
- **Lines 9–17**:
  ```kotlin
  data class SettingsUiState(
      val quality: AudioQuality = AudioQuality.Exhigh,
      val themeMode: ThemeMode = ThemeMode.System,
      val dynamicColor: Boolean = true,
      val hapticsEnabled: Boolean = true,
      val searchHistoryCount: Int = 0,
      val cacheSizeBytes: Long = 0L,
      val version: String = "0.1.28",
  )
  ```
  Also purely property-based with default values.

### 1.3 `NocturnViewModel.kt`
- **Path**: `app/src/main/kotlin/dev/nocturn/app/NocturnViewModel.kt`
- **Lines 70**: `private val settings: SettingsStore = container.settings`
- **Lines 83–91** (`mutableLibrary` init):
  ```kotlin
  private val mutableLibrary = MutableStateFlow(
      LibraryUiState(
          themeMode = settings.themeMode,
          dynamicColor = settings.dynamicColor,
          quality = settings.quality,
          hapticsEnabled = settings.hapticsEnabled,
      ),
  )
  ```
- **Lines 93–103** (`mutableSettings` init):
  ```kotlin
  private val mutableSettings = MutableStateFlow(
      SettingsUiState(
          quality = settings.quality,
          themeMode = settings.themeMode,
          dynamicColor = settings.dynamicColor,
          hapticsEnabled = settings.hapticsEnabled,
          searchHistoryCount = settings.searchHistory().size,
          version = appVersionName(),
      ),
  )
  ```
- **Lines 964–969** (`setHapticsEnabled` pattern):
  ```kotlin
  /** Turns the app-wide tactile layer on or off and persists the choice. */
  fun setHapticsEnabled(enabled: Boolean) {
      settings.hapticsEnabled = enabled
      mutableLibrary.update { it.copy(hapticsEnabled = enabled) }
      mutableSettings.update { it.copy(hapticsEnabled = enabled) }
  }
  ```

### 1.4 `LibraryScreen.kt` and `LibrarySettingsSection`
- **Path**: `app/src/main/kotlin/dev/nocturn/app/ui/screens/library/LibraryScreen.kt`
- **Lines 107–123** (`LibraryScreen` signature):
  Receives `state: LibraryUiState`, and callbacks `onQualityChange`, `onThemeModeChange`, `onDynamicColorChange`, `onHapticsChange`.
- **Lines 256–268** (`LibrarySettingsSection` call site in `LazyColumn` item `"settings"`):
  ```kotlin
  item(key = "settings") {
      LibrarySettingsSection(
          quality = state.quality,
          themeMode = state.themeMode,
          dynamicColor = state.dynamicColor,
          hapticsEnabled = state.hapticsEnabled,
          onSettingsClick = onSettingsClick,
          onQualityChange = onQualityChange,
          onThemeModeChange = onThemeModeChange,
          onDynamicColorChange = onDynamicColorChange,
          onHapticsChange = onHapticsChange,
      )
  }
  ```
- **Lines 646–805** (`LibrarySettingsSection` implementation):
  Renders settings cards inside a single `Surface(shape = MaterialTheme.shapes.large, color = MaterialTheme.colorScheme.surfaceContainerLow)`.
  - Lines 748–777: "动态取色" Row with `Switch(..., modifier = Modifier.testTag("library-dynamic-color-switch"))`.
  - Lines 778–804: "触感反馈" Row with `Switch(..., modifier = Modifier.testTag("library-haptics-switch"))`.
  - Both toggles call `switchHaptics.toggle(enabled)` before invoking their respective callbacks.

### 1.5 `NocturnApp.kt`
- **Path**: `app/src/main/kotlin/dev/nocturn/app/ui/NocturnApp.kt`
- **Lines 393–409** (`LibraryRoute`):
  Collects `model.library` and wires callbacks to `model::setQuality`, `model::setThemeMode`, `model::setDynamicColor`, `model::setHapticsEnabled`.

### 1.6 UI Test Suites
- **`SearchLibraryScreensTest.kt`** (`app/src/test/kotlin/dev/nocturn/app/ui/SearchLibraryScreensTest.kt` lines 320–348):
  ```kotlin
  @Test
  fun librarySettingsAreVisibleWithoutAnAccount() {
      setScreen { LibraryScreen(state = PreviewData.libraryAnonymous) }
      compose.waitForIdle()

      compose.onNodeWithTag("library-screen").performScrollToNode(hasTestTag("library-settings"))
      compose.onNodeWithTag("library-settings").assertExists()
      compose.onNodeWithTag("library-quality-Exhigh").assertExists()
      compose.onNodeWithTag("library-theme-system").assertExists()
      compose.onNodeWithTag("library-dynamic-color-switch").assertExists()
  }

  @Test
  fun libraryDynamicColorToggleInvokesCallback() {
      var toggled: Boolean? = null
      setScreen {
          LibraryScreen(
              state = PreviewData.libraryAnonymous.copy(dynamicColor = true),
              onDynamicColorChange = { toggled = it },
          )
      }
      compose.waitForIdle()

      compose.onNodeWithTag("library-screen")
          .performScrollToNode(hasTestTag("library-dynamic-color-switch"))
      compose.onNodeWithTag("library-dynamic-color-switch").performClick()
      compose.waitForIdle()

      assertEquals(false, toggled)
  }
  ```
- **`ScreenshotGalleryTest.kt`** (`app/src/test/kotlin/dev/nocturn/app/ui/ScreenshotGalleryTest.kt` lines 115–120):
  Scrolls to `library-haptics-switch` before capturing screenshot for `GalleryTarget.LibrarySettings`. Adding a lyricon switch below it preserves `library-haptics-switch` and will not break screenshot generation.
- **`SettingsViewModelTest.kt`** (`app/src/test/kotlin/dev/nocturn/app/SettingsViewModelTest.kt` lines 134–144):
  Directly tests that `setHapticsEnabled` updates both `settingsUi` and `library` states.

---

## 2. Logic Chain

1. **State Consistency (Observation 1.1, 1.2, 1.3)**:
   - `SettingsStore` provides `lyriconEnabled` (Explorer M2.1 confirmed `preferences.getBoolean("lyricon-enabled", true)`).
   - `NocturnViewModel` initializes both `LibraryUiState` and `SettingsUiState` from `settings`.
   - Adding `val lyriconEnabled: Boolean = true` to `LibraryUiState` and `SettingsUiState` ensures both the Library screen and secondary Settings screens have uniform access to the toggle state with zero breakage for existing calls.

2. **ViewModel Mutator Flow (Observation 1.3, 1.5)**:
   - When a user interacts with the UI toggle, `NocturnViewModel.setLyriconEnabled(enabled: Boolean)` must:
     a. Persist to `settings.lyriconEnabled = enabled`.
     b. Update `mutableLibrary.update { it.copy(lyriconEnabled = enabled) }`.
     c. Update `mutableSettings.update { it.copy(lyriconEnabled = enabled) }`.
   - In `NocturnApp.kt`, `LibraryRoute` connects `onLyriconChange = model::setLyriconEnabled`.

3. **Compose UI Placement & Styling (Observation 1.4)**:
   - In `LibraryScreen.kt` inside `LibrarySettingsSection`, the existing items are: Audio Quality -> Appearance (Theme) -> Dynamic Color -> Haptic Feedback.
   - Adding the "词幕状态栏歌词" Row right after "触感反馈" within the same container provides a natural grouping of system-level features.
   - Text label: `Text("词幕状态栏歌词", style = MaterialTheme.typography.titleSmall)`
   - Subtitle: `Text("向系统词幕服务同步播放信息与逐字/逐行歌词", style = MaterialTheme.typography.bodySmall)`
   - Tag: `Modifier.testTag("library-lyricon-switch")` as required by the dispatch task.
   - Haptics: Trigger `switchHaptics.toggle(enabled)` on toggle to match the tactile feedback of `dynamicColor` and `hapticsEnabled`.

4. **Test Verification & Safety (Observation 1.6)**:
   - `SearchLibraryScreensTest.kt` can test both node existence in `librarySettingsAreVisibleWithoutAnAccount` and click callback invocation in a new `libraryLyriconToggleInvokesCallback`.
   - `SettingsViewModelTest.kt` can verify that `setLyriconEnabled(false)` and `setLyriconEnabled(true)` properly update both `settingsUi` and `library`.
   - Neither existing tests nor `ScreenshotGalleryTest` are broken by this layout change.

---

## 3. Caveats

1. **LyriconManager in Milestone 3**:
   `LyriconManager` and its IPC provider lifecycle will be implemented in Milestone 3. In Milestone 2, `setLyriconEnabled` persists the setting in `SettingsStore` and updates UI state. In Milestone 3, `LyriconManager` will observe `settings.lyriconEnabled` (or be triggered by changes) to start/stop the provider push.
2. **SettingsPlaybackScreen / SettingsGeneralScreen**:
   The prompt specifically requested the Compose UI switch in `LibraryScreen` (`LibrarySettingsSection`) with `testTag("library-lyricon-switch")`. If desired in the future, a secondary toggle can easily be added to `SettingsPlaybackScreen` or `SettingsGeneralScreen` using the exposed `SettingsUiState.lyriconEnabled`.
3. **No Unintended Re-layouts**:
   Because `Switch` is wrapped in a standard `Row` with `verticalAlignment = Alignment.CenterVertically` identical to the preceding `haptics-switch`, it adheres strictly to Material 3 Expressive guidelines and existing app styling.

---

## 4. Conclusion & Precise Code Guidance for Worker

Worker must apply the following edits:

### File 1: `app/src/main/kotlin/dev/nocturn/app/ui/state/LibraryUiState.kt`
Add `lyriconEnabled` property (default `true`):
```kotlin
<<<<
    val themeMode: ThemeMode = ThemeMode.System,
    val dynamicColor: Boolean = true,
    val hapticsEnabled: Boolean = true,
    val login: LoginUiState = LoginUiState(),
)
====
    val themeMode: ThemeMode = ThemeMode.System,
    val dynamicColor: Boolean = true,
    val hapticsEnabled: Boolean = true,
    val lyriconEnabled: Boolean = true,
    val login: LoginUiState = LoginUiState(),
)
>>>>
```

### File 2: `app/src/main/kotlin/dev/nocturn/app/ui/state/SettingsUiState.kt`
Add `lyriconEnabled` property (default `true`):
```kotlin
<<<<
    val quality: AudioQuality = AudioQuality.Exhigh,
    val themeMode: ThemeMode = ThemeMode.System,
    val dynamicColor: Boolean = true,
    val hapticsEnabled: Boolean = true,
    val searchHistoryCount: Int = 0,
====
    val quality: AudioQuality = AudioQuality.Exhigh,
    val themeMode: ThemeMode = ThemeMode.System,
    val dynamicColor: Boolean = true,
    val hapticsEnabled: Boolean = true,
    val lyriconEnabled: Boolean = true,
    val searchHistoryCount: Int = 0,
>>>>
```

### File 3: `app/src/main/kotlin/dev/nocturn/app/NocturnViewModel.kt`
1. Initialize `mutableLibrary` (around line 89):
```kotlin
<<<<
    private val mutableLibrary = MutableStateFlow(
        LibraryUiState(
            themeMode = settings.themeMode,
            dynamicColor = settings.dynamicColor,
            quality = settings.quality,
            hapticsEnabled = settings.hapticsEnabled,
        ),
    )
====
    private val mutableLibrary = MutableStateFlow(
        LibraryUiState(
            themeMode = settings.themeMode,
            dynamicColor = settings.dynamicColor,
            quality = settings.quality,
            hapticsEnabled = settings.hapticsEnabled,
            lyriconEnabled = settings.lyriconEnabled,
        ),
    )
>>>>
```

2. Initialize `mutableSettings` (around line 99):
```kotlin
<<<<
    private val mutableSettings = MutableStateFlow(
        SettingsUiState(
            quality = settings.quality,
            themeMode = settings.themeMode,
            dynamicColor = settings.dynamicColor,
            hapticsEnabled = settings.hapticsEnabled,
            searchHistoryCount = settings.searchHistory().size,
            version = appVersionName(),
        ),
    )
====
    private val mutableSettings = MutableStateFlow(
        SettingsUiState(
            quality = settings.quality,
            themeMode = settings.themeMode,
            dynamicColor = settings.dynamicColor,
            hapticsEnabled = settings.hapticsEnabled,
            lyriconEnabled = settings.lyriconEnabled,
            searchHistoryCount = settings.searchHistory().size,
            version = appVersionName(),
        ),
    )
>>>>
```

3. Add mutator method `setLyriconEnabled` (after `setHapticsEnabled`, around line 970):
```kotlin
    /** Controls whether the Lyricon system status bar lyric service provider is enabled. */
    fun setLyriconEnabled(enabled: Boolean) {
        settings.lyriconEnabled = enabled
        mutableLibrary.update { it.copy(lyriconEnabled = enabled) }
        mutableSettings.update { it.copy(lyriconEnabled = enabled) }
    }
```

### File 4: `app/src/main/kotlin/dev/nocturn/app/ui/screens/library/LibraryScreen.kt`
1. Update `LibraryScreen` parameters (around line 122):
```kotlin
<<<<
    onThemeModeChange: (ThemeMode) -> Unit = {},
    onDynamicColorChange: (Boolean) -> Unit = {},
    onHapticsChange: (Boolean) -> Unit = {},
    onRetry: () -> Unit = {},
) {
====
    onThemeModeChange: (ThemeMode) -> Unit = {},
    onDynamicColorChange: (Boolean) -> Unit = {},
    onHapticsChange: (Boolean) -> Unit = {},
    onLyriconChange: (Boolean) -> Unit = {},
    onRetry: () -> Unit = {},
) {
>>>>
```

2. Pass to `LibrarySettingsSection` (around line 262):
```kotlin
<<<<
            item(key = "settings") {
                LibrarySettingsSection(
                    quality = state.quality,
                    themeMode = state.themeMode,
                    dynamicColor = state.dynamicColor,
                    hapticsEnabled = state.hapticsEnabled,
                    onSettingsClick = onSettingsClick,
                    onQualityChange = onQualityChange,
                    onThemeModeChange = onThemeModeChange,
                    onDynamicColorChange = onDynamicColorChange,
                    onHapticsChange = onHapticsChange,
                )
            }
====
            item(key = "settings") {
                LibrarySettingsSection(
                    quality = state.quality,
                    themeMode = state.themeMode,
                    dynamicColor = state.dynamicColor,
                    hapticsEnabled = state.hapticsEnabled,
                    lyriconEnabled = state.lyriconEnabled,
                    onSettingsClick = onSettingsClick,
                    onQualityChange = onQualityChange,
                    onThemeModeChange = onThemeModeChange,
                    onDynamicColorChange = onDynamicColorChange,
                    onHapticsChange = onHapticsChange,
                    onLyriconChange = onLyriconChange,
                )
            }
>>>>
```

3. Update `LibrarySettingsSection` signature (around line 652):
```kotlin
<<<<
@Composable
private fun LibrarySettingsSection(
    quality: AudioQuality,
    themeMode: ThemeMode,
    dynamicColor: Boolean,
    hapticsEnabled: Boolean,
    onSettingsClick: () -> Unit = {},
    onQualityChange: (AudioQuality) -> Unit = {},
    onThemeModeChange: (ThemeMode) -> Unit = {},
    onDynamicColorChange: (Boolean) -> Unit = {},
    onHapticsChange: (Boolean) -> Unit = {},
    modifier: Modifier = Modifier,
) {
====
@Composable
private fun LibrarySettingsSection(
    quality: AudioQuality,
    themeMode: ThemeMode,
    dynamicColor: Boolean,
    hapticsEnabled: Boolean,
    lyriconEnabled: Boolean,
    onSettingsClick: () -> Unit = {},
    onQualityChange: (AudioQuality) -> Unit = {},
    onThemeModeChange: (ThemeMode) -> Unit = {},
    onDynamicColorChange: (Boolean) -> Unit = {},
    onHapticsChange: (Boolean) -> Unit = {},
    onLyriconChange: (Boolean) -> Unit = {},
    modifier: Modifier = Modifier,
) {
>>>>
```

4. Add Lyricon Switch Row in `LibrarySettingsSection` after the haptics Row (around line 804):
```kotlin
                Spacer(Modifier.height(20.dp))
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Column(modifier = Modifier.weight(1f)) {
                        Text(
                            text = "词幕状态栏歌词",
                            style = MaterialTheme.typography.titleSmall,
                            color = MaterialTheme.colorScheme.onSurface,
                        )
                        Text(
                            text = "向系统词幕服务同步播放信息与逐字/逐行歌词",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                    }
                    Switch(
                        checked = lyriconEnabled,
                        onCheckedChange = { enabled ->
                            switchHaptics.toggle(enabled)
                            onLyriconChange(enabled)
                        },
                        modifier = Modifier.testTag("library-lyricon-switch"),
                    )
                }
```

### File 5: `app/src/main/kotlin/dev/nocturn/app/ui/NocturnApp.kt`
In `LibraryRoute` (around line 406):
```kotlin
<<<<
        onThemeModeChange = model::setThemeMode,
        onDynamicColorChange = model::setDynamicColor,
        onHapticsChange = model::setHapticsEnabled,
        onRetry = model::refreshLibrary,
    )
====
        onThemeModeChange = model::setThemeMode,
        onDynamicColorChange = model::setDynamicColor,
        onHapticsChange = model::setHapticsEnabled,
        onLyriconChange = model::setLyriconEnabled,
        onRetry = model::refreshLibrary,
    )
>>>>
```

### File 6: `app/src/test/kotlin/dev/nocturn/app/ui/SearchLibraryScreensTest.kt`
1. In `librarySettingsAreVisibleWithoutAnAccount`, append:
```kotlin
        compose.onNodeWithTag("library-screen").performScrollToNode(hasTestTag("library-lyricon-switch"))
        compose.onNodeWithTag("library-lyricon-switch").assertExists()
```
2. Add test method:
```kotlin
    @Test
    fun libraryLyriconToggleInvokesCallback() {
        var toggled: Boolean? = null
        setScreen {
            LibraryScreen(
                state = PreviewData.libraryAnonymous.copy(lyriconEnabled = true),
                onLyriconChange = { toggled = it },
            )
        }
        compose.waitForIdle()

        compose.onNodeWithTag("library-screen")
            .performScrollToNode(hasTestTag("library-lyricon-switch"))
        compose.onNodeWithTag("library-lyricon-switch").performClick()
        compose.waitForIdle()

        assertEquals(false, toggled)
    }
```

### File 7: `app/src/test/kotlin/dev/nocturn/app/SettingsViewModelTest.kt`
Add test method:
```kotlin
    @Test
    fun settingLyriconUpdatesSettingsStateAndLibraryState() {
        val model = createViewModel()

        model.setLyriconEnabled(false)
        assertFalse(model.settingsUi.value.lyriconEnabled)
        assertFalse(model.library.value.lyriconEnabled)

        model.setLyriconEnabled(true)
        assertTrue(model.settingsUi.value.lyriconEnabled)
        assertTrue(model.library.value.lyriconEnabled)
    }
```

---

## 5. Verification Method

To verify after Worker applies the changes:

1. **Run Unit Tests for Library Screens and ViewModel**:
   ```powershell
   .\gradlew.bat :app:testDebugUnitTest --tests "dev.nocturn.app.ui.SearchLibraryScreensTest"
   .\gradlew.bat :app:testDebugUnitTest --tests "dev.nocturn.app.SettingsViewModelTest"
   ```
2. **Run Screenshot Gallery Test to verify rendering stability**:
   ```powershell
   .\gradlew.bat :app:testDebugUnitTest --tests "dev.nocturn.app.ui.ScreenshotGalleryTest"
   ```
3. **Run Full Test Suite & Static Analysis**:
   ```powershell
   .\gradlew.bat :app:testDebugUnitTest :app:lintDebug
   ```

**Invalidation Conditions**:
- `LibraryScreen` or `LibrarySettingsSection` fails compilation due to mismatched parameter names or missing imports.
- `library-lyricon-switch` tag not found in Compose semantics tree during `SearchLibraryScreensTest`.
- `ScreenshotGalleryTest` fails to scroll to `library-haptics-switch`.
