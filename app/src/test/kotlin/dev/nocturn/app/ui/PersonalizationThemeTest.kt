package dev.nocturn.app.ui

import androidx.compose.material3.ColorScheme
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.test.assertExists
import androidx.compose.ui.test.assertIsSelected
import androidx.compose.ui.test.hasText
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithContentDescription
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import dev.nocturn.app.data.SettingsStore
import dev.nocturn.app.data.model.ColorSchemeKey
import dev.nocturn.app.data.model.DynamicVariant
import dev.nocturn.app.data.model.ThemeMode
import dev.nocturn.app.data.model.description
import dev.nocturn.app.data.model.displayName
import dev.nocturn.app.data.model.isDynamic
import dev.nocturn.app.ui.preview.PreviewData
import dev.nocturn.app.ui.screens.settings.SettingsAppearanceScreen
import dev.nocturn.app.ui.theme.AmberDarkColors
import dev.nocturn.app.ui.theme.AmberLightColors
import dev.nocturn.app.ui.theme.M3BaselineDarkColors
import dev.nocturn.app.ui.theme.M3BaselineLightColors
import dev.nocturn.app.ui.theme.M3eDarkColors
import dev.nocturn.app.ui.theme.M3eLightColors
import dev.nocturn.app.ui.theme.MintDarkColors
import dev.nocturn.app.ui.theme.MintLightColors
import dev.nocturn.app.ui.theme.NocturnDarkColors
import dev.nocturn.app.ui.theme.NocturnLightColors
import dev.nocturn.app.ui.theme.NocturnTheme
import dev.nocturn.app.ui.theme.OceanDarkColors
import dev.nocturn.app.ui.theme.OceanLightColors
import dev.nocturn.app.ui.theme.RoseDarkColors
import dev.nocturn.app.ui.theme.RoseLightColors
import dev.nocturn.app.ui.theme.generateCustomColorScheme
import dev.nocturn.app.ui.theme.resolveColorScheme
import dev.nocturn.app.ui.theme.resolvePalettePreview
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.RuntimeEnvironment
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [36])
class PersonalizationThemeTest {

    @get:Rule
    val compose = createComposeRule()

    @Test
    fun colorSchemeKeysAndVariantsExposeExpectedDisplayMetadata() {
        for (key in ColorSchemeKey.entries) {
            assertTrue(key.displayName().isNotBlank())
            assertTrue(key.description().isNotBlank())
        }

        assertTrue(ColorSchemeKey.M3eDynamic.isDynamic)
        assertTrue(ColorSchemeKey.M3Dynamic.isDynamic)
        assertFalse(ColorSchemeKey.M3eOfficial.isDynamic)
        assertFalse(ColorSchemeKey.M3Baseline.isDynamic)
        assertFalse(ColorSchemeKey.Amber.isDynamic)
        assertFalse(ColorSchemeKey.NocturnClassic.isDynamic)

        for (variant in DynamicVariant.entries) {
            assertTrue(variant.displayName().isNotBlank())
            assertTrue(variant.description().isNotBlank())
        }
    }

    @Test
    fun settingsStorePersistsColorSchemeAndVariantPreferences() {
        val context = RuntimeEnvironment.getApplication()
        val store = SettingsStore(context)

        store.colorSchemeKey = ColorSchemeKey.M3eOfficial
        assertEquals(ColorSchemeKey.M3eOfficial, SettingsStore(context).colorSchemeKey)
        assertFalse(store.dynamicColor)

        store.colorSchemeKey = ColorSchemeKey.M3eDynamic
        assertTrue(store.dynamicColor)

        store.dynamicVariant = DynamicVariant.Vibrant
        assertEquals(DynamicVariant.Vibrant, SettingsStore(context).dynamicVariant)

        store.customSeedColor = 0xFF1E88E5L
        assertEquals(0xFF1E88E5L, SettingsStore(context).customSeedColor)
    }

    @Test
    fun resolveColorSchemeProducesValidPalettesForAllKeys() {
        val context = RuntimeEnvironment.getApplication()

        for (key in ColorSchemeKey.entries) {
            val lightScheme: ColorScheme = resolveColorScheme(
                key = key,
                dark = false,
                customSeed = 0xFF6750A4L,
                dynamicVariant = DynamicVariant.Expressive,
                context = context,
                isEditMode = false,
            )
            assertNotNull(lightScheme)
            assertTrue(lightScheme.primary != Color.Unspecified)
            assertTrue(lightScheme.surface != Color.Unspecified)

            val darkScheme: ColorScheme = resolveColorScheme(
                key = key,
                dark = true,
                customSeed = 0xFF6750A4L,
                dynamicVariant = DynamicVariant.Expressive,
                context = context,
                isEditMode = false,
            )
            assertNotNull(darkScheme)
            assertTrue(darkScheme.primary != Color.Unspecified)
            assertTrue(darkScheme.surface != Color.Unspecified)
        }
    }

    @Test
    fun customSeedGeneratorProducesValidLightAndDarkPalettes() {
        val seed = Color(0xFFE91E63)
        val light = generateCustomColorScheme(seed, dark = false)
        val dark = generateCustomColorScheme(seed, dark = true)

        assertEquals(seed, light.primary)
        assertNotNull(light.surface)
        assertNotNull(dark.primary)
        assertNotNull(dark.surface)
    }

    @Test
    fun palettePreviewResolutionWorksForAllKeys() {
        val context = RuntimeEnvironment.getApplication()
        for (key in ColorSchemeKey.entries) {
            val preview = resolvePalettePreview(key, 0xFF00ACC1L, dark = false, context = context)
            assertNotNull(preview.primary)
            assertNotNull(preview.secondary)
            assertNotNull(preview.tertiary)
            assertNotNull(preview.surface)
        }
    }

    @Test
    fun personalizationScreenRendersHeaderSwatchesAndDialogs() {
        var chosenScheme: ColorSchemeKey? = null
        var chosenVariant: DynamicVariant? = null
        var resetCount = 0

        val state = PreviewData.settingsState.copy(
            colorSchemeKey = ColorSchemeKey.M3eDynamic,
            dynamicVariant = DynamicVariant.Content,
        )

        compose.setContent {
            NocturnTheme {
                SettingsAppearanceScreen(
                    state = state,
                    onColorSchemeChange = { key, _ -> chosenScheme = key },
                    onDynamicVariantChange = { chosenVariant = it },
                    onResetColorScheme = { resetCount++ },
                )
            }
        }
        compose.waitForIdle()

        // 1. Verify "主题色彩" header and "内容主题" Chip
        compose.onNodeWithText("主题色彩").assertExists()
        compose.onNodeWithText("内容主题").assertExists()

        // 2. Click "内容主题" Chip to open Dynamic Variant Dialog
        compose.onNodeWithText("内容主题").performClick()
        compose.waitForIdle()
        compose.onNodeWithText("选择取色风格").assertExists()
        compose.onNodeWithText("表现力").assertExists()
        compose.onNodeWithText("表现力").performClick()
        assertEquals(DynamicVariant.Expressive, chosenVariant)

        // 3. Reset Button works
        compose.onNodeWithContentDescription("恢复默认配色").performClick()
        assertEquals(1, resetCount)

        // 4. Click a color swatch (e.g. M3 Expressive)
        compose.onNodeWithContentDescription("M3 Expressive").assertExists()
        compose.onNodeWithContentDescription("M3 Expressive").performClick()
        assertEquals(ColorSchemeKey.M3eOfficial, chosenScheme)

        // 5. Click "+" button to open Custom Color Dialog
        compose.onNodeWithContentDescription("自定义种子色彩").assertExists()
        compose.onNodeWithContentDescription("自定义种子色彩").performClick()
        compose.waitForIdle()
        compose.onNodeWithText("自定义色彩").assertExists()
        compose.onNodeWithText("应用").assertExists()
    }
}
