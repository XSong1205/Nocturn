package dev.nocturn.app.ui

import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.ui.hapticfeedback.HapticFeedback
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.test.hasTestTag
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithContentDescription
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performScrollToNode
import dev.nocturn.app.data.SettingsStore
import dev.nocturn.app.data.model.ThemeMode
import dev.nocturn.app.ui.components.LikeButton
import dev.nocturn.app.ui.components.LocalHapticsEnabled
import dev.nocturn.app.ui.components.NocturnHaptics
import dev.nocturn.app.ui.preview.PreviewData
import dev.nocturn.app.ui.screens.settings.SettingsGeneralScreen
import dev.nocturn.app.ui.state.SettingsUiState
import dev.nocturn.app.ui.theme.NocturnTheme
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.RuntimeEnvironment
import org.robolectric.annotation.Config

/**
 * The app-wide tactile layer: the semantic vocabulary maps onto distinct platform kinds, the global
 * switch silences every call site, and the preference survives a new [SettingsStore] instance.
 */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [36])
class HapticsTest {

    @get:Rule
    val compose = createComposeRule()

    /** Records what the platform would have felt, without touching the device vibrator. */
    private class RecordingHaptics : HapticFeedback {
        val performed = mutableListOf<HapticFeedbackType>()

        override fun performHapticFeedback(hapticFeedbackType: HapticFeedbackType) {
            performed += hapticFeedbackType
        }
    }

    @Test
    fun semanticVocabularyMapsToDistinctPlatformFeedback() {
        val recorder = RecordingHaptics()
        val haptics = NocturnHaptics(recorder, enabled = true)

        haptics.tap()
        haptics.confirm()
        haptics.toggle(on = true)
        haptics.toggle(on = false)
        haptics.step()
        haptics.threshold()
        haptics.release()

        assertEquals(
            listOf(
                HapticFeedbackType.ContextClick,
                HapticFeedbackType.Confirm,
                HapticFeedbackType.ToggleOn,
                HapticFeedbackType.ToggleOff,
                HapticFeedbackType.SegmentTick,
                HapticFeedbackType.GestureThresholdActivate,
                HapticFeedbackType.GestureEnd,
            ),
            recorder.performed,
        )
    }

    @Test
    fun disabledHapticsPerformNothing() {
        val recorder = RecordingHaptics()
        val haptics = NocturnHaptics(recorder, enabled = false)

        haptics.tap()
        haptics.confirm()
        haptics.toggle(on = true)
        haptics.step()
        haptics.threshold()
        haptics.release()

        assertTrue(recorder.performed.isEmpty())
        assertFalse(haptics.enabled)
    }

    @Test
    fun globalSwitchSilencesComponentFeedback() {
        val recorder = RecordingHaptics()
        compose.setContent {
            NocturnTheme(themeMode = ThemeMode.Dark) {
                CompositionLocalProvider(
                    LocalHapticFeedback provides recorder,
                    LocalHapticsEnabled provides false,
                ) {
                    LikeButton(liked = false, onToggle = {})
                }
            }
        }

        compose.onNodeWithContentDescription("喜欢").performClick()
        compose.waitForIdle()

        assertTrue(recorder.performed.isEmpty())
    }

    @Test
    fun enabledComponentsStillConfirmToggles() {
        val recorder = RecordingHaptics()
        compose.setContent {
            NocturnTheme(themeMode = ThemeMode.Dark) {
                CompositionLocalProvider(
                    LocalHapticFeedback provides recorder,
                    LocalHapticsEnabled provides true,
                ) {
                    LikeButton(liked = false, onToggle = {})
                }
            }
        }

        compose.onNodeWithContentDescription("喜欢").performClick()
        compose.waitForIdle()

        assertEquals(listOf(HapticFeedbackType.ToggleOn), recorder.performed)
    }

    @Test
    fun settingsGeneralHapticsSwitchReportsTheNewValue() {
        var reported: Boolean? = null
        compose.setContent {
            NocturnTheme(themeMode = ThemeMode.Dark) {
                SettingsGeneralScreen(
                    state = PreviewData.settingsState.copy(hapticsEnabled = false),
                    onHapticsChange = { reported = it },
                )
            }
        }
        compose.waitForIdle()

        compose.onNodeWithTag("settings-general-screen").performScrollToNode(hasTestTag("settings-haptics-switch"))
        compose.onNodeWithTag("settings-haptics-switch").performClick()

        assertEquals(true, reported)
    }

    @Test
    fun enablingTheSwitchIsFeelableEvenWhileTheLayerIsOff() {
        val recorder = RecordingHaptics()
        compose.setContent {
            NocturnTheme(themeMode = ThemeMode.Dark) {
                // The persisted preference is still "off" in this composition; the switch itself
                // must pulse so the user feels the pulse they just enabled.
                CompositionLocalProvider(
                    LocalHapticFeedback provides recorder,
                    LocalHapticsEnabled provides false,
                ) {
                    SettingsGeneralScreen(
                        state = PreviewData.settingsState.copy(hapticsEnabled = false),
                        onHapticsChange = {},
                    )
                }
            }
        }
        compose.waitForIdle()

        compose.onNodeWithTag("settings-general-screen").performScrollToNode(hasTestTag("settings-haptics-switch"))
        compose.onNodeWithTag("settings-haptics-switch").performClick()

        assertEquals(listOf(HapticFeedbackType.ToggleOn), recorder.performed)
    }

    @Test
    fun settingsStorePersistsTheHapticsPreference() {
        val context = RuntimeEnvironment.getApplication()

        val settings = SettingsStore(context)
        assertTrue(settings.hapticsEnabled)

        settings.hapticsEnabled = false

        assertFalse(SettingsStore(context).hapticsEnabled)
    }
}
