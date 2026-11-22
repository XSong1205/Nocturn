package dev.nocturn.app.ui

import androidx.compose.material3.ColorScheme
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.test.hasContentDescription
import androidx.compose.ui.test.hasText
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithContentDescription
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performScrollToNode
import dev.nocturn.app.data.SettingsStore
import dev.nocturn.app.data.model.ColorSchemeKey
import dev.nocturn.app.data.model.DynamicVariant
import dev.nocturn.app.data.model.description
import dev.nocturn.app.data.model.displayName
import dev.nocturn.app.data.model.isDynamic
import dev.nocturn.app.ui.preview.PreviewData
import dev.nocturn.app.ui.screens.settings.CustomSeedColorContent
import dev.nocturn.app.ui.screens.settings.SettingsAppearanceScreen
import dev.nocturn.app.ui.theme.NocturnTheme
import dev.nocturn.app.ui.theme.generateCustomColorScheme
import dev.nocturn.app.ui.theme.generateM3eDynamicColorScheme
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
@Config(sdk = [36], qualifiers = "w411dp-h914dp")
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
    fun customSeedColorDialogRendersDirectly() {
        var confirmed: Color? = null
        compose.setContent {
            NocturnTheme {
                CustomSeedColorContent(
                    initialColor = Color(0xFF6750A4),
                    onDismiss = {},
                    onConfirm = { confirmed = it },
                    asDialog = false,
                )
            }
        }
        compose.onNodeWithText("自定义色彩").assertExists()
        compose.onNodeWithText("应用").assertExists()
        compose.onNodeWithText("应用").performClick()
        assertEquals(Color(0xFF6750A4), confirmed)
    }

    @Test
    fun personalizationScreenRendersHeaderSwatchesAndInteractions() {
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
        compose.waitForIdle()
        assertEquals(DynamicVariant.Expressive, chosenVariant)

        // 3. Reset Button works
        compose.onNodeWithContentDescription("恢复默认配色").performClick()
        compose.waitForIdle()
        assertEquals(1, resetCount)

        // 4. Click a color swatch (e.g. M3 Expressive)
        compose.onNodeWithTag("settings-appearance-screen")
            .performScrollToNode(hasContentDescription("M3 Expressive"))
        compose.onNodeWithContentDescription("M3 Expressive").assertExists()
        compose.onNodeWithContentDescription("M3 Expressive").performClick()
        assertEquals(ColorSchemeKey.M3eOfficial, chosenScheme)
    }

    @Test
    fun m3eAndM3BadgesAreDisplayedOnDynamicSwatches() {
        val state = PreviewData.settingsState.copy(
            colorSchemeKey = ColorSchemeKey.M3eDynamic,
        )
        compose.setContent {
            NocturnTheme {
                SettingsAppearanceScreen(
                    state = state,
                    onColorSchemeChange = { _, _ -> },
                    onDynamicVariantChange = {},
                    onResetColorScheme = {},
                )
            }
        }
        compose.waitForIdle()

        compose.onNodeWithText("M3E").assertExists()
        compose.onNodeWithText("M3").assertExists()
    }

    @Test
    fun m3eDynamicGeneratesDistinctExpressiveColors() {
        val seed = Color(0xFF6750A4)
        val expressiveLight = generateM3eDynamicColorScheme(seed, dark = false, DynamicVariant.Expressive)
        val tonalSpotLight = generateM3eDynamicColorScheme(seed, dark = false, DynamicVariant.TonalSpot)

        assertTrue(expressiveLight.secondary != tonalSpotLight.secondary)
        assertTrue(expressiveLight.tertiary != tonalSpotLight.tertiary)
    }

    @Test
    fun resolveColorSchemeReactsToDynamicVariantAcrossSchemes() {
        val context = RuntimeEnvironment.getApplication()

        // 1. M3eDynamic reacts to variant
        val m3eExpressive = resolveColorScheme(
            key = ColorSchemeKey.M3eDynamic,
            dark = false,
            customSeed = 0xFF6750A4L,
            dynamicVariant = DynamicVariant.Expressive,
            context = context,
        )
        val m3eMonochrome = resolveColorScheme(
            key = ColorSchemeKey.M3eDynamic,
            dark = false,
            customSeed = 0xFF6750A4L,
            dynamicVariant = DynamicVariant.Monochrome,
            context = context,
        )
        assertTrue(m3eExpressive.primary != m3eMonochrome.primary)

        // 2. M3Dynamic reacts to variant
        val m3TonalSpot = resolveColorScheme(
            key = ColorSchemeKey.M3Dynamic,
            dark = false,
            customSeed = 0xFF6750A4L,
            dynamicVariant = DynamicVariant.TonalSpot,
            context = context,
        )
        val m3Monochrome = resolveColorScheme(
            key = ColorSchemeKey.M3Dynamic,
            dark = false,
            customSeed = 0xFF6750A4L,
            dynamicVariant = DynamicVariant.Monochrome,
            context = context,
        )
        assertTrue(m3TonalSpot.primary != m3Monochrome.primary)

        // 3. Preset (Amber) reacts to variant
        val amberExpressive = resolveColorScheme(
            key = ColorSchemeKey.Amber,
            dark = false,
            customSeed = 0xFF6750A4L,
            dynamicVariant = DynamicVariant.Expressive,
            context = context,
        )
        val amberMonochrome = resolveColorScheme(
            key = ColorSchemeKey.Amber,
            dark = false,
            customSeed = 0xFF6750A4L,
            dynamicVariant = DynamicVariant.Monochrome,
            context = context,
        )
        assertTrue(amberExpressive.primary != amberMonochrome.primary)

        // 4. Custom reacts to variant
        val customVibrant = resolveColorScheme(
            key = ColorSchemeKey.Custom,
            dark = false,
            customSeed = 0xFF2196F3L,
            dynamicVariant = DynamicVariant.Vibrant,
            context = context,
        )
        val customMonochrome = resolveColorScheme(
            key = ColorSchemeKey.Custom,
            dark = false,
            customSeed = 0xFF2196F3L,
            dynamicVariant = DynamicVariant.Monochrome,
            context = context,
        )
        assertTrue(customVibrant.primary != customMonochrome.primary)
    }

    @Test
    fun palettePreviewsUpdateWhenDynamicVariantChanges() {
        val context = RuntimeEnvironment.getApplication()

        val previewExpressive = resolvePalettePreview(
            key = ColorSchemeKey.M3eDynamic,
            customSeed = 0xFF6750A4L,
            dark = false,
            context = context,
            dynamicVariant = DynamicVariant.Expressive,
        )
        val previewMonochrome = resolvePalettePreview(
            key = ColorSchemeKey.M3eDynamic,
            customSeed = 0xFF6750A4L,
            dark = false,
            context = context,
            dynamicVariant = DynamicVariant.Monochrome,
        )

        assertTrue(previewExpressive.primary != previewMonochrome.primary)
        assertEquals(Color.Black, previewMonochrome.primary)
    }
}
