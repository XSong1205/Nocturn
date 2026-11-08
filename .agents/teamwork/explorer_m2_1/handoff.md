# Handoff Report: Milestone 2 — Settings Persistence & UI Controls (SettingsStore Investigation)

## 1. Observation

### 1.1 `SettingsStore.kt` Architecture and Implementation
Direct inspection of `app/src/main/kotlin/dev/nocturn/app/data/SettingsStore.kt` (lines 1–68) revealed:
- **Package**: `dev.nocturn.app.data`
- **Class**: `class SettingsStore(context: Context)`
- **Storage**: Unencrypted `SharedPreferences` instance named `"nocturn-settings"` obtained via `context.applicationContext.getSharedPreferences("nocturn-settings", Context.MODE_PRIVATE)` (line 12–13).
- **Boolean Property Pattern**:
  - `dynamicColor` (lines 23–27):
    ```kotlin
    /** Android 12+ dynamic color (Material You / Monet); on by default. */
    var dynamicColor: Boolean
        get() = preferences.getBoolean(KEY_DYNAMIC_COLOR, true)
        set(value) {
            preferences.edit().putBoolean(KEY_DYNAMIC_COLOR, value).apply()
        }
    ```
  - `hapticsEnabled` (lines 37–41):
    ```kotlin
    /** Global tactile feedback switch; on by default to match the platform. */
    var hapticsEnabled: Boolean
        get() = preferences.getBoolean(KEY_HAPTICS, true)
        set(value) {
            preferences.edit().putBoolean(KEY_HAPTICS, value).apply()
        }
    ```
- **Companion Object Constants** (lines 60–66):
  ```kotlin
  private companion object {
      const val KEY_THEME = "theme-mode"
      const val KEY_DYNAMIC_COLOR = "dynamic-color"
      const val KEY_QUALITY = "audio-quality"
      const val KEY_HAPTICS = "haptics-enabled"
      const val KEY_HISTORY = "search-history"
  }
  ```

### 1.2 Surrounding Architecture and Callers
- `AppContainer` in `app/src/main/kotlin/dev/nocturn/app/NocturnApplication.kt` line 16 exposes:
  `open val settings: SettingsStore = SettingsStore(context)`
- `NocturnViewModel.kt` lines 70, 83–103 binds `SettingsStore` to `LibraryUiState` and `SettingsUiState`:
  ```kotlin
  private val settings: SettingsStore = container.settings
  ```
- Mutator methods in `NocturnViewModel.kt` (lines 958–969) update both the store and UI states:
  ```kotlin
  fun setDynamicColor(enabled: Boolean) {
      settings.dynamicColor = enabled
      mutableLibrary.update { it.copy(dynamicColor = enabled) }
      mutableSettings.update { it.copy(dynamicColor = enabled) }
  }
  ```

### 1.3 Existing Settings Tests
Inspected existing tests in `app/src/test/kotlin/dev/nocturn/app/`:
- `dev/nocturn/app/ui/DynamicColorTest.kt` (lines 30–41):
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
- `dev/nocturn/app/ui/HapticsTest.kt` (lines 180–191):
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
- Running `./gradlew.bat :app:testDebugUnitTest --tests "dev.nocturn.app.ui.DynamicColorTest"` executed successfully in 26s (`BUILD SUCCESSFUL`, exit code 0).
- Currently, there is no standalone `SettingsStoreTest.kt` in `app/src/test/kotlin/dev/nocturn/app/data/`.

---

## 2. Logic Chain

1. **Preference Key and Storage**:
   - `DISPATCH.md` and `PROJECT.md` require key name `KEY_LYRICON_ENABLED = "lyricon-enabled"` with default `true`.
   - `SettingsStore` already uses `Context.MODE_PRIVATE` SharedPreferences named `"nocturn-settings"`.
   - Following the identical pattern of `dynamicColor` and `hapticsEnabled`, `lyriconEnabled` will read `preferences.getBoolean(KEY_LYRICON_ENABLED, true)` and write using `preferences.edit().putBoolean(KEY_LYRICON_ENABLED, value).apply()`.

2. **Companion Object Visibility**:
   - `SettingsStore.kt` currently declares `private companion object`.
   - If changed to `internal companion object` (or `companion object`), `SettingsStore.KEY_LYRICON_ENABLED` can be referenced directly in `SettingsStoreTest.kt` without leaking outside the Gradle module.
   - If kept as `private companion object`, `SettingsStoreTest.kt` can either test `settings.lyriconEnabled` directly across instances or verify `sp.getBoolean("lyricon-enabled", true)`.
   - Recommendation: Use `internal companion object` (or `companion object`) to allow `SettingsStore.KEY_LYRICON_ENABLED` to be cleanly tested while keeping implementation scoped to the module.

3. **No External Dependencies / Zero Breaking Changes**:
   - `Boolean` is a built-in Kotlin type. No additional imports required in `SettingsStore.kt`.
   - Adding `var lyriconEnabled: Boolean` to `SettingsStore` is purely additive and backward-compatible.

---

## 3. Caveats

- **Asynchronous persistence with `.apply()`**: `.apply()` writes in-memory immediately and commits to disk asynchronously. In Android and Robolectric tests, `SettingsStore(context)` reads the in-memory SharedPreferences cache immediately, ensuring deterministic test assertion across fresh instances in the same process.
- **Migration / First Launch**: Because `preferences.getBoolean(KEY_LYRICON_ENABLED, true)` defaults to `true`, existing app installations upgrading to this version will automatically have Lyricon enabled without needing a database or preference migration.

---

## 4. Conclusion & Worker Implementation Guidance

### 4.1 Exact Code Changes for `app/src/main/kotlin/dev/nocturn/app/data/SettingsStore.kt`

#### Change 1: Add Property Definition (Insert after line 41)
```kotlin
    /** Status bar lyric provider integration (Lyricon); on by default. */
    var lyriconEnabled: Boolean
        get() = preferences.getBoolean(KEY_LYRICON_ENABLED, true)
        set(value) {
            preferences.edit().putBoolean(KEY_LYRICON_ENABLED, value).apply()
        }
```

#### Change 2: Add Constant to Companion Object (Update lines 60–66)
```kotlin
    internal companion object {
        const val KEY_THEME = "theme-mode"
        const val KEY_DYNAMIC_COLOR = "dynamic-color"
        const val KEY_QUALITY = "audio-quality"
        const val KEY_HAPTICS = "haptics-enabled"
        const val KEY_LYRICON_ENABLED = "lyricon-enabled"
        const val KEY_HISTORY = "search-history"
    }
```

### 4.2 Unified Diff for `SettingsStore.kt`
```diff
--- a/app/src/main/kotlin/dev/nocturn/app/data/SettingsStore.kt
+++ b/app/src/main/kotlin/dev/nocturn/app/data/SettingsStore.kt
@@ -41,6 +41,13 @@ class SettingsStore(context: Context) {
             preferences.edit().putBoolean(KEY_HAPTICS, value).apply()
         }
 
+    /** Status bar lyric provider integration (Lyricon); on by default. */
+    var lyriconEnabled: Boolean
+        get() = preferences.getBoolean(KEY_LYRICON_ENABLED, true)
+        set(value) {
+            preferences.edit().putBoolean(KEY_LYRICON_ENABLED, value).apply()
+        }
+
     fun searchHistory(limit: Int = 10): List<String> {
         val raw = preferences.getString(KEY_HISTORY, null) ?: return emptyList()
         return raw.split('\n').map { it.trim() }.filter { it.isNotEmpty() }.take(limit)
@@ -57,11 +64,12 @@ class SettingsStore(context: Context) {
         preferences.edit().remove(KEY_HISTORY).apply()
     }
 
-    private companion object {
+    internal companion object {
         const val KEY_THEME = "theme-mode"
         const val KEY_DYNAMIC_COLOR = "dynamic-color"
         const val KEY_QUALITY = "audio-quality"
         const val KEY_HAPTICS = "haptics-enabled"
+        const val KEY_LYRICON_ENABLED = "lyricon-enabled"
         const val KEY_HISTORY = "search-history"
     }
 }
```

### 4.3 Recommended Unit Test Specification for Worker
Target file: `app/src/test/kotlin/dev/nocturn/app/data/SettingsStoreTest.kt`
```kotlin
package dev.nocturn.app.data

import android.content.Context
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.RuntimeEnvironment
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [36])
class SettingsStoreTest {

    @Test
    fun lyriconEnabledDefaultsToTrue() {
        val context = RuntimeEnvironment.getApplication()
        val settings = SettingsStore(context)
        assertTrue("Lyricon should be enabled by default", settings.lyriconEnabled)
    }

    @Test
    fun lyriconEnabledPersistsAcrossInstances() {
        val context = RuntimeEnvironment.getApplication()
        val settings = SettingsStore(context)

        // Disable
        settings.lyriconEnabled = false
        assertFalse("Lyricon state should be false on first instance", settings.lyriconEnabled)
        assertFalse(
            "Lyricon state false should persist across a fresh SettingsStore instance",
            SettingsStore(context).lyriconEnabled,
        )

        // Verify underlying SharedPreferences entry
        val prefs = context.getSharedPreferences("nocturn-settings", Context.MODE_PRIVATE)
        assertEquals(false, prefs.getBoolean(SettingsStore.KEY_LYRICON_ENABLED, true))

        // Re-enable
        settings.lyriconEnabled = true
        assertTrue("Lyricon state should revert to true", settings.lyriconEnabled)
        assertTrue(
            "Lyricon state true should persist across a fresh SettingsStore instance",
            SettingsStore(context).lyriconEnabled,
        )
    }
}
```

---

## 5. Verification Method

1. **Static Analysis & Lint Check**:
   ```powershell
   .\gradlew.bat :app:lintDebug
   ```
   *Expected result*: No lint errors in `SettingsStore.kt` or `SettingsStoreTest.kt`.

2. **Unit Test Execution**:
   ```powershell
   .\gradlew.bat :app:testDebugUnitTest --tests "dev.nocturn.app.data.SettingsStoreTest"
   ```
   *Expected result*: `BUILD SUCCESSFUL`, 2/2 tests pass.

3. **Full Regression Test**:
   ```powershell
   .\gradlew.bat :app:testDebugUnitTest
   ```
   *Expected result*: All unit and Robolectric tests pass.

4. **Invalidation Conditions**:
   - If `KEY_LYRICON_ENABLED` is misnamed or not `"lyricon-enabled"`.
   - If default value is `false` instead of `true`.
   - If SharedPreferences `.apply()` is omitted on the setter.
