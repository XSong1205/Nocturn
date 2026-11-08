# Handoff Report: Milestone 2 — Unit Test & Robolectric Suite Design (SettingsStore & UI Reflection)

## 1. Observation

### 1.1 Existing Test Suite Structure & Conventions
We performed a comprehensive inspection of the test infrastructure in `app/src/test/kotlin/dev/nocturn/app/`:

1. **Robolectric Configuration & Conventions**:
   - `app/build.gradle.kts` (lines 124, 156–160):
     ```kotlin
     testOptions { unitTests.isIncludeAndroidResources = true }
     testImplementation("junit:junit:4.13.2")
     testImplementation("org.robolectric:robolectric:4.17")
     testImplementation("androidx.compose.ui:ui-test-junit4")
     robolectricSdk("org.robolectric:android-all-instrumented:16-robolectric-13921718-i7")
     ```
   - Standard annotations across Robolectric test suites (`SettingsViewModelTest.kt:41-42`, `DynamicColorTest.kt:19-20`, `HapticsTest.kt:34-35`, `AppNavigationTest.kt:43-44`):
     ```kotlin
     @RunWith(RobolectricTestRunner::class)
     @Config(sdk = [36])
     ```
   - Standard application context acquisition (`DynamicColorTest.kt:32`, `HapticsTest.kt:182`):
     ```kotlin
     val context = RuntimeEnvironment.getApplication()
     ```
     or typed as `NocturnApplication` (`SettingsViewModelTest.kt:46, 148, 164`):
     ```kotlin
     val app = RuntimeEnvironment.getApplication() as NocturnApplication
     ```

2. **Absence of Dedicated Data Layer Settings Test Suite**:
   - Under `app/src/test/kotlin/dev/nocturn/app/data/`, tests currently exist for `AccountAuthenticatorTest.kt`, `SessionCipherTest.kt`, `RepositoryErrorsTest.kt`, and NetEase JSON models (`NeteaseJson*Test.kt`).
   - Prior to Milestone 2, there was **no dedicated `SettingsStoreTest.kt`** in `dev.nocturn.app.data`. Fragmented preference tests only existed incidentally inside UI component tests:
     - `app/src/test/kotlin/dev/nocturn/app/ui/DynamicColorTest.kt` (lines 30–41):
       ```kotlin
       @Test
       fun settingsStorePersistsDynamicColorPreference() {
           val context = RuntimeEnvironment.getApplication()
           val settings = SettingsStore(context)
           assertTrue(settings.dynamicColor)

           settings.dynamicColor = false
           assertFalse(SettingsStore(context).dynamicColor)

           settings.dynamicColor = true
           assertTrue(SettingsStore(context).dynamicColor)
       }
       ```
     - `app/src/test/kotlin/dev/nocturn/app/ui/HapticsTest.kt` (lines 180–191):
       ```kotlin
       @Test
       fun settingsStorePersistsTheHapticsPreference() {
           val context = RuntimeEnvironment.getApplication()
           val settings = SettingsStore(context)
           assertTrue(settings.hapticsEnabled)

           settings.hapticsEnabled = false
           assertFalse(SettingsStore(context).hapticsEnabled)
       }
       ```

3. **ViewModel Testing Patterns in `SettingsViewModelTest.kt`**:
   - In `app/src/test/kotlin/dev/nocturn/app/SettingsViewModelTest.kt` (lines 45–53, 98–145):
     - `createViewModel()` overrides `AppContainer` with test doubles (`FakeMusicRepository`, `FakePlaybackEngine`) and instantiates `NocturnViewModel(app)` (lines 45–53).
     - State mutation and synchronization between `settingsUi` and `library` StateFlows are verified for each setting:
       - `settingQualityUpdatesSettingsStateAndLibraryState` (lines 98–105)
       - `settingThemeModeUpdatesSettingsStateAndLibraryState` (lines 108–119)
       - `settingDynamicColorUpdatesSettingsStateAndLibraryState` (lines 121–132)
       - `settingHapticsUpdatesSettingsStateAndLibraryState` (lines 134–145)

4. **Compose UI Testing Patterns in `SearchLibraryScreensTest.kt`**:
   - In `app/src/test/kotlin/dev/nocturn/app/ui/SearchLibraryScreensTest.kt` (lines 319–348):
     - `librarySettingsAreVisibleWithoutAnAccount()` verifies presence of settings chips and switches via `compose.onNodeWithTag(...).assertExists()`.
     - `libraryDynamicColorToggleInvokesCallback()` verifies scrolling to the switch and executing click:
       ```kotlin
       compose.onNodeWithTag("library-screen")
           .performScrollToNode(hasTestTag("library-dynamic-color-switch"))
       compose.onNodeWithTag("library-dynamic-color-switch").performClick()
       compose.waitForIdle()
       assertEquals(false, toggled)
       ```
   - In `app/src/test/kotlin/dev/nocturn/app/ui/HapticsTest.kt` (lines 135–152):
     - `librarySettingsSwitchReportsTheNewValue()` scrolls to `hasTestTag("library-haptics-switch")`, performs click, and asserts callback is invoked with `true`.

---

## 2. Logic Chain

1. **Need for a Dedicated `SettingsStoreTest.kt` in `dev.nocturn.app.data`**:
   - `SettingsStore` is the persistence foundation for all unencrypted user preferences.
   - Per Requirement R4 and Task 2 of Milestone 2, `lyriconEnabled` must default to `true`, persist `false` when modified, and revert to `true` correctly.
   - Relying solely on tests located in UI packages (`DynamicColorTest`, `HapticsTest`) violates module cohesion. Creating `app/src/test/kotlin/dev/nocturn/app/data/SettingsStoreTest.kt` provides an authoritative, isolated unit test suite covering `lyriconEnabled` and full `SettingsStore` contract behavior.

2. **Isolation and Determinism in Robolectric**:
   - In Robolectric, `context.applicationContext.getSharedPreferences("nocturn-settings", Context.MODE_PRIVATE)` persists across instances within the process lifecycle.
   - To guarantee tests do not leak mutated preference state into subsequent test cases:
     - Clear the `"nocturn-settings"` preferences in `@Before` setup: `context.getSharedPreferences("nocturn-settings", Context.MODE_PRIVATE).edit().clear().commit()`.
     - Verify cross-instance persistence by mutating property on `store1`, instantiating a fresh `store2 = SettingsStore(context)`, and asserting on `store2.lyriconEnabled`.
     - Directly inspect the underlying SharedPreferences file with key `"lyricon-enabled"` to verify the raw stored value.

3. **NocturnViewModel Reflection Verification**:
   - When `NocturnViewModel` starts, `mutableLibrary` and `mutableSettings` are initialized from `settings.lyriconEnabled`.
   - Calling `model.setLyriconEnabled(false)` must:
     a. Update `settings.lyriconEnabled = false`.
     b. Emit `false` in `model.library.value.lyriconEnabled`.
     c. Emit `false` in `model.settingsUi.value.lyriconEnabled`.
   - Calling `model.setLyriconEnabled(true)` must restore all three to `true`.
   - Initializing `NocturnViewModel` when `settings.lyriconEnabled = false` already exists must correctly propagate `false` to the initial UI states.

4. **Compose UI Verification**:
   - In `LibraryScreen.kt`, the Lyricon switch is decorated with `.testTag("library-lyricon-switch")`.
   - In `SearchLibraryScreensTest.kt`, adding assertions verifies that:
     a. `library-lyricon-switch` is rendered inside `library-settings`.
     b. Clicking `library-lyricon-switch` triggers the `onLyriconChange` callback with the inverted boolean value.

---

## 3. Caveats

1. **Android Context Requirement**:
   - `SettingsStore` relies on `Context.applicationContext.getSharedPreferences`. Therefore, tests cannot run as pure host JVM tests without Android stubs; they require `@RunWith(RobolectricTestRunner::class)` and `@Config(sdk = [36])`. This matches the rest of the project's Android component tests.
2. **Synchronous Commit vs Asynchronous Apply**:
   - In production code, `SettingsStore.kt` uses `.apply()`. In Robolectric's shadow SharedPreferences implementation, `.apply()` writes synchronously in-memory. However, in `@Before` cleanup, `.commit()` is recommended to guarantee immediate clean state before each test runs.
3. **NocturnApplication Casting**:
   - In tests using `RuntimeEnvironment.getApplication()`, casting to `NocturnApplication` is valid because `AndroidManifest.xml` configures `android:name=".NocturnApplication"`.

---

## 4. Conclusion & Precise Test Specifications

### 4.1 Specification 1: New Test Suite `SettingsStoreTest.kt`
Create new file: `app/src/test/kotlin/dev/nocturn/app/data/SettingsStoreTest.kt`

```kotlin
package dev.nocturn.app.data

import android.content.Context
import dev.nocturn.app.data.model.ThemeMode
import dev.nocturn.localapi.client.AudioQuality
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.RuntimeEnvironment
import org.robolectric.annotation.Config

/**
 * Robolectric unit test suite for [SettingsStore] persistence, verifying default values,
 * cross-instance persistence, preference mutations, and isolation for all user preferences
 * including status bar Lyricon provider synchronization.
 */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [36])
class SettingsStoreTest {

    private lateinit var context: Context

    @Before
    fun setUp() {
        context = RuntimeEnvironment.getApplication()
        context.getSharedPreferences("nocturn-settings", Context.MODE_PRIVATE)
            .edit()
            .clear()
            .commit()
    }

    // -----------------------------------------------------------------------------------
    // Lyricon Provider Toggle Verification (Requirement R4)
    // -----------------------------------------------------------------------------------

    @Test
    fun lyriconEnabledDefaultsToTrue() {
        val store = SettingsStore(context)
        assertTrue("lyriconEnabled must default to true when unconfigured", store.lyriconEnabled)
    }

    @Test
    fun lyriconEnabledPersistsFalseAcrossInstances() {
        val store1 = SettingsStore(context)
        store1.lyriconEnabled = false

        // Verify across independent SettingsStore instances backed by the same context
        val store2 = SettingsStore(context)
        assertFalse("lyriconEnabled=false must persist across separate SettingsStore instances", store2.lyriconEnabled)

        // Verify directly in the underlying SharedPreferences
        val rawPrefs = context.getSharedPreferences("nocturn-settings", Context.MODE_PRIVATE)
        assertFalse(
            "Underlying SharedPreferences must hold false for key 'lyricon-enabled'",
            rawPrefs.getBoolean("lyricon-enabled", true)
        )
    }

    @Test
    fun lyriconEnabledRevertsToTrueCorrectly() {
        val store = SettingsStore(context)
        store.lyriconEnabled = false
        assertFalse(SettingsStore(context).lyriconEnabled)

        // Revert back to true
        store.lyriconEnabled = true

        val freshStore = SettingsStore(context)
        assertTrue("lyriconEnabled must revert to true across separate SettingsStore instances", freshStore.lyriconEnabled)

        val rawPrefs = context.getSharedPreferences("nocturn-settings", Context.MODE_PRIVATE)
        assertTrue(
            "Underlying SharedPreferences must hold true for key 'lyricon-enabled'",
            rawPrefs.getBoolean("lyricon-enabled", false)
        )
    }

    @Test
    fun clearingPreferencesRestoresLyriconEnabledDefault() {
        val store = SettingsStore(context)
        store.lyriconEnabled = false
        assertFalse(store.lyriconEnabled)

        context.getSharedPreferences("nocturn-settings", Context.MODE_PRIVATE)
            .edit()
            .clear()
            .commit()

        val freshStore = SettingsStore(context)
        assertTrue("After clearing preferences, lyriconEnabled should fall back to default true", freshStore.lyriconEnabled)
    }

    @Test
    fun mutatingLyriconEnabledPreservesOtherPreferences() {
        val store = SettingsStore(context)
        store.dynamicColor = false
        store.hapticsEnabled = false
        store.themeMode = ThemeMode.Dark

        // Mutate lyriconEnabled
        store.lyriconEnabled = false

        val freshStore = SettingsStore(context)
        assertFalse(freshStore.lyriconEnabled)
        assertFalse(freshStore.dynamicColor)
        assertFalse(freshStore.hapticsEnabled)
        assertEquals(ThemeMode.Dark, freshStore.themeMode)

        freshStore.lyriconEnabled = true
        val freshStore2 = SettingsStore(context)
        assertTrue(freshStore2.lyriconEnabled)
        assertFalse(freshStore2.dynamicColor)
        assertFalse(freshStore2.hapticsEnabled)
        assertEquals(ThemeMode.Dark, freshStore2.themeMode)
    }

    // -----------------------------------------------------------------------------------
    // General SettingsStore Contract Sanity
    // -----------------------------------------------------------------------------------

    @Test
    fun defaultValuesForOtherSettingsMatchSpecifications() {
        val store = SettingsStore(context)
        assertEquals(ThemeMode.System, store.themeMode)
        assertTrue(store.dynamicColor)
        assertEquals(AudioQuality.Exhigh, store.quality)
        assertTrue(store.hapticsEnabled)
        assertTrue(store.searchHistory().isEmpty())
    }

    @Test
    fun searchHistoryRecordsAndClearsCorrectly() {
        val store = SettingsStore(context)
        store.recordSearch("First")
        store.recordSearch("Second")
        assertEquals(listOf("Second", "First"), store.searchHistory())

        store.clearSearchHistory()
        assertTrue(store.searchHistory().isEmpty())
    }
}
```

---

### 4.2 Specification 2: ViewModel Test Additions in `SettingsViewModelTest.kt`
File: `app/src/test/kotlin/dev/nocturn/app/SettingsViewModelTest.kt`
Insert the following test cases into `SettingsViewModelTest`:

```kotlin
    @Test
    fun settingLyriconUpdatesSettingsStateAndLibraryState() {
        val model = createViewModel()

        // Verify initial default is true
        assertTrue(model.settingsUi.value.lyriconEnabled)
        assertTrue(model.library.value.lyriconEnabled)

        // Mutate to false
        model.setLyriconEnabled(false)
        assertFalse(model.settingsUi.value.lyriconEnabled)
        assertFalse(model.library.value.lyriconEnabled)

        // Mutate back to true
        model.setLyriconEnabled(true)
        assertTrue(model.settingsUi.value.lyriconEnabled)
        assertTrue(model.library.value.lyriconEnabled)
    }

    @Test
    fun initialLyriconEnabledReflectsSettingsStore() {
        val app = RuntimeEnvironment.getApplication() as NocturnApplication
        val store = SettingsStore(app)
        store.lyriconEnabled = false

        val model = createViewModel()
        assertFalse(model.settingsUi.value.lyriconEnabled)
        assertFalse(model.library.value.lyriconEnabled)

        // Clean up
        store.lyriconEnabled = true
    }

    @Test
    fun settingLyriconEnabledPersistsToUnderlyingSettingsStore() {
        val app = RuntimeEnvironment.getApplication() as NocturnApplication
        val model = createViewModel()

        model.setLyriconEnabled(false)
        val freshStore = SettingsStore(app)
        assertFalse(freshStore.lyriconEnabled)

        model.setLyriconEnabled(true)
        assertTrue(SettingsStore(app).lyriconEnabled)
    }
```

---

### 4.3 Specification 3: UI Test Additions in `SearchLibraryScreensTest.kt`
File: `app/src/test/kotlin/dev/nocturn/app/ui/SearchLibraryScreensTest.kt`

1. **Verify switch visibility in `librarySettingsAreVisibleWithoutAnAccount()`**:
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
        compose.onNodeWithTag("library-screen").performScrollToNode(hasTestTag("library-lyricon-switch"))
        compose.onNodeWithTag("library-lyricon-switch").assertExists()
    }
```

2. **Add dedicated interaction test `libraryLyriconToggleInvokesCallback()`**:
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

---

## 5. Verification Method

### 5.1 Verification Commands
Once Worker implements the specifications:

1. **Execute New SettingsStore Test Suite**:
   ```powershell
   .\gradlew.bat :app:testDebugUnitTest --tests "dev.nocturn.app.data.SettingsStoreTest"
   ```
   *Expected*: All 7 tests pass with exit code 0.

2. **Execute ViewModel Settings Tests**:
   ```powershell
   .\gradlew.bat :app:testDebugUnitTest --tests "dev.nocturn.app.SettingsViewModelTest"
   ```
   *Expected*: All existing tests plus the 3 new `lyriconEnabled` tests pass with exit code 0.

3. **Execute Compose UI Library Tests**:
   ```powershell
   .\gradlew.bat :app:testDebugUnitTest --tests "dev.nocturn.app.ui.SearchLibraryScreensTest"
   ```
   *Expected*: All search and library tests including `libraryLyriconToggleInvokesCallback` pass with exit code 0.

4. **Execute Full App Unit Test Suite**:
   ```powershell
   .\gradlew.bat :app:testDebugUnitTest
   ```
   *Expected*: Complete suite execution passes.

### 5.2 Invalidation Conditions
- If `SettingsStore.lyriconEnabled` defaults to `false`, `lyriconEnabledDefaultsToTrue` fails.
- If `SettingsStore` writes to an in-memory variable instead of `SharedPreferences`, `lyriconEnabledPersistsFalseAcrossInstances` fails.
- If `NocturnViewModel.setLyriconEnabled` fails to notify both `library` and `settingsUi` StateFlows, `settingLyriconUpdatesSettingsStateAndLibraryState` fails.
- If `LibraryScreen.kt` misses `testTag("library-lyricon-switch")`, `libraryLyriconToggleInvokesCallback` fails with node not found.
