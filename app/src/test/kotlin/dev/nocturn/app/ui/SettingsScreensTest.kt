package dev.nocturn.app.ui

import androidx.compose.runtime.Composable
import androidx.compose.ui.test.assertIsEnabled
import androidx.compose.ui.test.assertIsNotEnabled
import androidx.compose.ui.test.hasTestTag
import androidx.compose.ui.test.hasText
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performScrollToNode
import dev.nocturn.app.data.model.ThemeMode
import dev.nocturn.app.ui.preview.PreviewData
import dev.nocturn.app.ui.screens.library.LibraryScreen
import dev.nocturn.app.ui.screens.settings.SettingsAboutScreen
import dev.nocturn.app.ui.screens.settings.SettingsAppearanceScreen
import dev.nocturn.app.ui.screens.settings.SettingsGeneralScreen
import dev.nocturn.app.ui.screens.settings.SettingsPlaybackScreen
import dev.nocturn.app.ui.screens.settings.SettingsScreen
import dev.nocturn.app.ui.screens.settings.SettingsStorageScreen
import dev.nocturn.app.ui.theme.NocturnTheme
import dev.nocturn.localapi.client.AudioQuality
import org.junit.Assert.assertEquals
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

/**
 * Robolectric Compose UI tests for the independent Settings screen and its secondary sub-menus.
 */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [36], qualifiers = "w411dp-h914dp")
class SettingsScreensTest {

    @get:Rule
    val compose = createComposeRule()

    private fun setScreen(content: @Composable () -> Unit) {
        compose.setContent {
            NocturnTheme(themeMode = ThemeMode.Dark) { content() }
        }
    }

    // ---------------------------------------------------------------------------------------
    // Root Settings Screen
    // ---------------------------------------------------------------------------------------

    @Test
    fun settingsScreenRendersAllSubMenuEntries() {
        setScreen {
            SettingsScreen(state = PreviewData.settingsState)
        }
        compose.waitForIdle()

        compose.onNodeWithTag("settings-screen").assertExists()
        compose.onNodeWithTag("settings-screen").performScrollToNode(hasTestTag("settings-menu-playback"))
        compose.onNodeWithTag("settings-menu-playback").assertExists()

        compose.onNodeWithTag("settings-screen").performScrollToNode(hasTestTag("settings-menu-appearance"))
        compose.onNodeWithTag("settings-menu-appearance").assertExists()

        compose.onNodeWithTag("settings-screen").performScrollToNode(hasTestTag("settings-menu-general"))
        compose.onNodeWithTag("settings-menu-general").assertExists()

        compose.onNodeWithTag("settings-screen").performScrollToNode(hasTestTag("settings-menu-storage"))
        compose.onNodeWithTag("settings-menu-storage").assertExists()

        compose.onNodeWithTag("settings-screen").performScrollToNode(hasTestTag("settings-menu-about"))
        compose.onNodeWithTag("settings-menu-about").assertExists()
    }

    @Test
    fun settingsScreenNavigationCallbacksAreInvoked() {
        var backed = 0
        var playbackNav = 0
        var appearanceNav = 0
        var generalNav = 0
        var storageNav = 0
        var aboutNav = 0

        setScreen {
            SettingsScreen(
                state = PreviewData.settingsState,
                onBack = { backed++ },
                onNavigatePlayback = { playbackNav++ },
                onNavigateAppearance = { appearanceNav++ },
                onNavigateGeneral = { generalNav++ },
                onNavigateStorage = { storageNav++ },
                onNavigateAbout = { aboutNav++ },
            )
        }
        compose.waitForIdle()

        compose.onNodeWithTag("settings-back").performClick()
        assertEquals(1, backed)

        compose.onNodeWithTag("settings-screen").performScrollToNode(hasTestTag("settings-menu-playback"))
        compose.onNodeWithTag("settings-menu-playback").performClick()
        assertEquals(1, playbackNav)

        compose.onNodeWithTag("settings-screen").performScrollToNode(hasTestTag("settings-menu-appearance"))
        compose.onNodeWithTag("settings-menu-appearance").performClick()
        assertEquals(1, appearanceNav)

        compose.onNodeWithTag("settings-screen").performScrollToNode(hasTestTag("settings-menu-general"))
        compose.onNodeWithTag("settings-menu-general").performClick()
        assertEquals(1, generalNav)

        compose.onNodeWithTag("settings-screen").performScrollToNode(hasTestTag("settings-menu-storage"))
        compose.onNodeWithTag("settings-menu-storage").performClick()
        assertEquals(1, storageNav)

        compose.onNodeWithTag("settings-screen").performScrollToNode(hasTestTag("settings-menu-about"))
        compose.onNodeWithTag("settings-menu-about").performClick()
        assertEquals(1, aboutNav)
    }

    // ---------------------------------------------------------------------------------------
    // Playback & Audio Quality Sub-Menu
    // ---------------------------------------------------------------------------------------

    @Test
    fun settingsPlaybackScreenRendersQualitiesAndInvokesCallback() {
        var backed = 0
        var selectedQuality: AudioQuality? = null

        setScreen {
            SettingsPlaybackScreen(
                state = PreviewData.settingsState.copy(quality = AudioQuality.Standard),
                onBack = { backed++ },
                onQualityChange = { selectedQuality = it },
            )
        }
        compose.waitForIdle()

        compose.onNodeWithTag("settings-playback-screen").assertExists()
        compose.onNodeWithTag("settings-playback-screen").performScrollToNode(hasTestTag("settings-playback-quality-Standard"))
        compose.onNodeWithTag("settings-playback-quality-Standard").assertExists()

        compose.onNodeWithTag("settings-playback-screen").performScrollToNode(hasTestTag("settings-playback-quality-Lossless"))
        compose.onNodeWithTag("settings-playback-quality-Lossless").performClick()
        assertEquals(AudioQuality.Lossless, selectedQuality)

        compose.onNodeWithTag("settings-playback-back").performClick()
        assertEquals(1, backed)
    }

    // ---------------------------------------------------------------------------------------
    // Appearance & Display Sub-Menu
    // ---------------------------------------------------------------------------------------

    @Test
    fun settingsAppearanceScreenRendersThemesAndTogglesDynamicColor() {
        var backed = 0
        var selectedTheme: ThemeMode? = null
        var dynamicColorToggled: Boolean? = null

        setScreen {
            SettingsAppearanceScreen(
                state = PreviewData.settingsState.copy(
                    themeMode = ThemeMode.System,
                    dynamicColor = true,
                ),
                onBack = { backed++ },
                onThemeModeChange = { selectedTheme = it },
                onDynamicColorChange = { dynamicColorToggled = it },
            )
        }
        compose.waitForIdle()

        compose.onNodeWithTag("settings-appearance-screen").assertExists()
        compose.onNodeWithTag("settings-appearance-screen").performScrollToNode(hasTestTag("settings-theme-dark"))
        compose.onNodeWithTag("settings-theme-dark").performClick()
        assertEquals(ThemeMode.Dark, selectedTheme)

        compose.onNodeWithTag("settings-appearance-screen").performScrollToNode(hasTestTag("settings-dynamic-color-switch"))
        compose.onNodeWithTag("settings-dynamic-color-switch").performClick()
        assertEquals(false, dynamicColorToggled)

        compose.onNodeWithTag("settings-appearance-back").performClick()
        assertEquals(1, backed)
    }

    // ---------------------------------------------------------------------------------------
    // General & Interaction Sub-Menu
    // ---------------------------------------------------------------------------------------

    @Test
    fun settingsGeneralScreenTogglesHaptics() {
        var backed = 0
        var hapticsToggled: Boolean? = null

        setScreen {
            SettingsGeneralScreen(
                state = PreviewData.settingsState.copy(hapticsEnabled = true),
                onBack = { backed++ },
                onHapticsChange = { hapticsToggled = it },
            )
        }
        compose.waitForIdle()

        compose.onNodeWithTag("settings-general-screen").assertExists()
        compose.onNodeWithTag("settings-general-screen").performScrollToNode(hasTestTag("settings-haptics-switch"))
        compose.onNodeWithTag("settings-haptics-switch").performClick()
        assertEquals(false, hapticsToggled)

        compose.onNodeWithTag("settings-general-back").performClick()
        assertEquals(1, backed)
    }

    @Test
    fun settingsGeneralScreenTogglesLyricon() {
        var lyriconToggled: Boolean? = null

        setScreen {
            SettingsGeneralScreen(
                state = PreviewData.settingsState.copy(lyriconEnabled = true),
                onLyriconChange = { lyriconToggled = it },
            )
        }
        compose.waitForIdle()

        compose.onNodeWithTag("settings-general-screen").assertExists()
        compose.onNodeWithTag("settings-general-screen").performScrollToNode(hasTestTag("settings-lyricon-switch"))
        compose.onNodeWithTag("settings-lyricon-switch").performClick()
        assertEquals(false, lyriconToggled)
    }

    // ---------------------------------------------------------------------------------------
    // Storage & Cache Sub-Menu
    // ---------------------------------------------------------------------------------------

    @Test
    fun settingsStorageScreenClearsHistoryAndCache() {
        var backed = 0
        var historyCleared = 0
        var cacheCleared = 0

        setScreen {
            SettingsStorageScreen(
                state = PreviewData.settingsState.copy(
                    searchHistoryCount = 5,
                    cacheSizeBytes = 10_485_760L,
                ),
                onBack = { backed++ },
                onClearSearchHistory = { historyCleared++ },
                onClearCache = { cacheCleared++ },
            )
        }
        compose.waitForIdle()

        compose.onNodeWithTag("settings-storage-screen").assertExists()
        compose.onNodeWithTag("settings-storage-screen").performScrollToNode(hasTestTag("settings-clear-history-button"))
        compose.onNodeWithText("已保存 5 条搜索关键词").assertExists()

        // Clear history with confirmation dialog
        compose.onNodeWithTag("settings-clear-history-button").performClick()
        compose.waitForIdle()
        compose.onNodeWithTag("settings-clear-history-confirm").performClick()
        assertEquals(1, historyCleared)

        // Clear cache
        compose.onNodeWithTag("settings-storage-screen").performScrollToNode(hasTestTag("settings-clear-cache-button"))
        compose.onNodeWithText("当前占用空间：10.0 MB").assertExists()
        compose.onNodeWithTag("settings-clear-cache-button").performClick()
        assertEquals(1, cacheCleared)

        compose.onNodeWithTag("settings-storage-back").performClick()
        assertEquals(1, backed)
    }

    // ---------------------------------------------------------------------------------------
    // About Sub-Menu
    // ---------------------------------------------------------------------------------------

    @Test
    fun settingsAboutScreenDisplaysVersionAndInfo() {
        var backed = 0

        setScreen {
            SettingsAboutScreen(
                state = PreviewData.settingsState.copy(version = "1.0.0-test"),
                onBack = { backed++ },
            )
        }
        compose.waitForIdle()

        compose.onNodeWithTag("settings-about-screen").assertExists()
        compose.onNodeWithTag("settings-about-screen").performScrollToNode(hasTestTag("settings-about-version"))
        compose.onNodeWithText("Nocturn").assertExists()
        compose.onNodeWithTag("settings-about-version").assertExists()
        compose.onNodeWithText("版本 1.0.0-test").assertExists()

        compose.onNodeWithTag("settings-about-screen").performScrollToNode(hasText("Kotlin Multiplatform"))
        compose.onNodeWithText("Kotlin Multiplatform").assertExists()

        compose.onNodeWithTag("settings-about-back").performClick()
        assertEquals(1, backed)
    }

    // ---------------------------------------------------------------------------------------
    // Library Screen Settings Entrances
    // ---------------------------------------------------------------------------------------

    @Test
    fun libraryScreenSettingsButtonsTriggerCallback() {
        var settingsOpened = 0

        setScreen {
            LibraryScreen(
                state = PreviewData.libraryAnonymous,
                onSettingsClick = { settingsOpened++ },
            )
        }
        compose.waitForIdle()

        // Top bar settings button
        compose.onNodeWithTag("library-settings-button").performClick()
        assertEquals(1, settingsOpened)
    }

    @Test
    fun settingsStorageScreenEmptyHistoryDisablesClearButton() {
        setScreen {
            SettingsStorageScreen(
                state = PreviewData.settingsState.copy(
                    searchHistoryCount = 0,
                    cacheSizeBytes = 0L,
                    isClearing = false,
                ),
            )
        }
        compose.waitForIdle()

        compose.onNodeWithTag("settings-storage-screen")
            .performScrollToNode(hasTestTag("settings-clear-history-button"))
        compose.onNodeWithText("暂无搜索记录").assertExists()
        compose.onNodeWithTag("settings-clear-history-button").assertIsNotEnabled()
    }

    @Test
    fun settingsStorageScreenDisablesClearButtonWhileClearing() {
        setScreen {
            SettingsStorageScreen(
                state = PreviewData.settingsState.copy(
                    searchHistoryCount = 2,
                    cacheSizeBytes = 1024L,
                    isClearing = true,
                ),
            )
        }
        compose.waitForIdle()

        compose.onNodeWithTag("settings-storage-screen")
            .performScrollToNode(hasTestTag("settings-clear-cache-button"))
        compose.onNodeWithText("清理中…").assertExists()
        compose.onNodeWithTag("settings-clear-cache-button").assertIsNotEnabled()
    }

    @Test
    fun settingsPlaybackScreenDisplaysVipBadges() {
        setScreen {
            SettingsPlaybackScreen(state = PreviewData.settingsState)
        }
        compose.waitForIdle()

        compose.onNodeWithTag("settings-playback-screen")
            .performScrollToNode(hasTestTag("settings-playback-quality-Lossless"))
        compose.onNodeWithText("VIP").assertExists()

        compose.onNodeWithTag("settings-playback-screen")
            .performScrollToNode(hasTestTag("settings-playback-quality-Hires"))
        compose.onNodeWithText("SVIP").assertExists()
    }

    @Test
    fun settingsAboutScreenDisplaysProjectRepositoryCard() {
        setScreen {
            SettingsAboutScreen(state = PreviewData.settingsState)
        }
        compose.waitForIdle()

        compose.onNodeWithTag("settings-about-screen")
            .performScrollToNode(hasText("GitHub 开源仓库"))
        compose.onNodeWithText("GitHub 开源仓库").assertExists()
        compose.onNodeWithText("https://github.com/Nocturn-App/Nocturn").assertExists()
        compose.onNodeWithTag("settings-about-github-card").assertExists()
        compose.onNodeWithTag("settings-about-github-card").performClick()

        compose.onNodeWithTag("settings-about-screen")
            .performScrollToNode(hasText("Lyricon"))
        compose.onNodeWithText("Lyricon").assertExists()
    }
}

