package dev.nocturn.app.ui

import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.ui.test.junit4.createComposeRule
import dev.nocturn.app.data.SettingsStore
import dev.nocturn.app.ui.theme.NocturnTheme
import dev.nocturn.app.ui.theme.isDynamicColorSupported
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.RuntimeEnvironment
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [36])
class DynamicColorTest {
    @get:Rule
    val compose = createComposeRule()

    @Test
    fun dynamicColorIsSupportedOnApi36() {
        assertTrue(isDynamicColorSupported())
    }

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

    @Test
    fun themeRendersWithDynamicColorEnabled() {
        var primaryColor: androidx.compose.ui.graphics.Color? = null
        compose.setContent {
            NocturnTheme(dynamicColor = true) {
                primaryColor = MaterialTheme.colorScheme.primary
                Text("Dynamic Color Test")
            }
        }
        compose.waitForIdle()
        assertTrue(primaryColor != null)
    }

    @Test
    fun themeRendersWithDynamicColorDisabled() {
        var primaryColor: androidx.compose.ui.graphics.Color? = null
        compose.setContent {
            NocturnTheme(dynamicColor = false) {
                primaryColor = MaterialTheme.colorScheme.primary
                Text("Brand Color Test")
            }
        }
        compose.waitForIdle()
        assertTrue(primaryColor != null)
    }
}
