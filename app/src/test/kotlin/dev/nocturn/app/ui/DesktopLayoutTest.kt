package dev.nocturn.app.ui

import android.graphics.Bitmap
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Text
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.asAndroidBitmap
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.captureToImage
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onRoot
import androidx.compose.ui.test.performClick
import androidx.compose.ui.unit.dp
import dev.nocturn.app.data.model.AccountProfile
import dev.nocturn.app.data.model.ThemeMode
import dev.nocturn.app.ui.adaptive.LocalWindowWidthClass
import dev.nocturn.app.ui.adaptive.WindowWidthClass
import dev.nocturn.app.ui.desktop.DesktopLyricsPanel
import dev.nocturn.app.ui.desktop.DesktopPlayerBar
import dev.nocturn.app.ui.desktop.DesktopScaffold
import dev.nocturn.app.ui.desktop.DesktopSidebar
import dev.nocturn.app.ui.preview.PreviewData
import dev.nocturn.app.ui.screens.home.HomeScreen
import dev.nocturn.app.ui.state.AppTab
import dev.nocturn.app.ui.theme.NocturnTheme
import java.io.File
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode

/**
 * Robolectric Compose UI test suite validating desktop / PC (>= 1024dp)
 * UI components: [DesktopScaffold], [DesktopPlayerBar], [DesktopSidebar], and [DesktopLyricsPanel].
 */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [36], qualifiers = "w1280dp-h800dp-xhdpi")
@GraphicsMode(GraphicsMode.Mode.NATIVE)
class DesktopLayoutTest {

    @get:Rule
    val compose = createComposeRule()

    private fun saveScreenshot(name: String) {
        val dir = File("build/screenshots")
        dir.mkdirs()
        val bitmap = compose.onRoot().captureToImage().asAndroidBitmap()
        File(dir, "$name.png").outputStream().use { out ->
            bitmap.compress(Bitmap.CompressFormat.PNG, 100, out)
        }
    }

    @Test
    fun desktopPlayerBarControlsAndInteractionsWork() {
        var playToggled = false
        var nextClicked = false
        var prevClicked = false
        var likeToggled = false
        var shuffleToggled = false
        var repeatCycled = false
        var volumeChangedTo = -1f

        val state = PreviewData.bilingualPlayerState.copy(
            positionMs = 115_000,
            durationMs = 240_000,
        )

        compose.setContent {
            NocturnTheme(themeMode = ThemeMode.Dark) {
                var vol by remember { mutableFloatStateOf(0.8f) }
                DesktopPlayerBar(
                    state = state,
                    onTogglePlay = { playToggled = true },
                    onPrevious = { prevClicked = true },
                    onNext = { nextClicked = true },
                    onSeek = {},
                    onToggleLike = { likeToggled = true },
                    onToggleShuffle = { shuffleToggled = true },
                    onCycleRepeat = { repeatCycled = true },
                    onOpenPlayer = {},
                    onToggleLyricsPanel = {},
                    onToggleQueuePanel = {},
                    volume = vol,
                    onVolumeChange = {
                        vol = it
                        volumeChangedTo = it
                    },
                )
            }
        }

        // Verify elements rendered
        compose.onNodeWithTag("desktop-player-bar").assertIsDisplayed()
        compose.onNodeWithTag("desktop-player-cover").assertIsDisplayed()
        compose.onNodeWithTag("desktop-player-seek").assertIsDisplayed()
        compose.onNodeWithTag("desktop-volume-slider").assertIsDisplayed()

        // Test button actions
        compose.onNodeWithTag("desktop-player-toggle-play").assertIsDisplayed().performClick()
        assertTrue(playToggled)

        compose.onNodeWithTag("desktop-player-next").assertIsDisplayed().performClick()
        assertTrue(nextClicked)

        compose.onNodeWithTag("desktop-player-previous").assertIsDisplayed().performClick()
        assertTrue(prevClicked)

        compose.onNodeWithTag("desktop-player-like").assertIsDisplayed().performClick()
        assertTrue(likeToggled)

        compose.onNodeWithTag("desktop-player-shuffle").assertIsDisplayed().performClick()
        assertTrue(shuffleToggled)

        compose.onNodeWithTag("desktop-player-repeat").assertIsDisplayed().performClick()
        assertTrue(repeatCycled)

        // Test volume mute toggle button
        compose.onNodeWithTag("desktop-volume-button").assertIsDisplayed().performClick()
        assertEquals(0f, volumeChangedTo, 0.01f)
    }

    @Test
    fun desktopSidebarNavigationAndItemsWork() {
        var selectedTab: AppTab? = null
        var likedClicked = false
        var recentClicked = false
        var settingsClicked = false

        val testProfile = AccountProfile(
            id = 99999L,
            nickname = "夜曲极客",
            avatarUrl = null,
            signature = "热爱代码与音乐",
            vipLevel = 1,
        )

        compose.setContent {
            NocturnTheme(themeMode = ThemeMode.Light) {
                DesktopSidebar(
                    selectedTab = AppTab.Home,
                    onSelectTab = { selectedTab = it },
                    onOpenLikedSongs = { likedClicked = true },
                    onOpenRecentSongs = { recentClicked = true },
                    onOpenSettings = { settingsClicked = true },
                    onOpenLogin = {},
                    userProfile = testProfile,
                )
            }
        }

        compose.onNodeWithTag("desktop-sidebar").assertIsDisplayed()
        compose.onNodeWithTag("desktop-sidebar-profile").assertIsDisplayed()

        compose.onNodeWithTag("desktop-nav-search").assertIsDisplayed().performClick()
        assertEquals(AppTab.Search, selectedTab)

        compose.onNodeWithTag("desktop-nav-liked").assertIsDisplayed().performClick()
        assertTrue(likedClicked)

        compose.onNodeWithTag("desktop-nav-recent").assertIsDisplayed().performClick()
        assertTrue(recentClicked)

        compose.onNodeWithTag("desktop-nav-settings").assertIsDisplayed().performClick()
        assertTrue(settingsClicked)
    }

    @Test
    fun desktopScaffoldRendersCompletePcWorkspace() {
        var selectedTab by mutableStateOf(AppTab.Home)
        var lyricsOpen by mutableStateOf(true)

        val state = PreviewData.bilingualPlayerState.copy(
            positionMs = 115_000,
            durationMs = 240_000,
            lyrics = dev.nocturn.app.data.model.Lyrics(
                listOf(
                    dev.nocturn.app.data.model.LyricLine(
                        startMs = 100_000,
                        durationMs = 5_000,
                        text = "夜曲在电脑大屏上回响",
                        translation = "Nocturne echoes on desktop display",
                    ),
                    dev.nocturn.app.data.model.LyricLine(
                        startMs = 110_000,
                        durationMs = 8_000,
                        text = "沉浸式三栏工作台",
                        translation = "Immersive three-pane workspace",
                    )
                )
            )
        )

        compose.setContent {
            NocturnTheme(themeMode = ThemeMode.Dark) {
                CompositionLocalProvider(LocalWindowWidthClass provides WindowWidthClass.Expanded) {
                    DesktopScaffold(
                        selectedTab = selectedTab,
                        onSelectTab = { selectedTab = it },
                        onOpenLikedSongs = {},
                        onOpenRecentSongs = {},
                        onOpenSettings = {},
                        onOpenLogin = {},
                        userProfile = null,
                        playerState = state,
                        onTogglePlay = {},
                        onPrevious = {},
                        onNext = {},
                        onSeek = {},
                        onToggleLike = {},
                        onToggleShuffle = {},
                        onCycleRepeat = {},
                        onOpenPlayer = {},
                        lyricsPanelOpen = lyricsOpen,
                        onToggleLyricsPanel = { lyricsOpen = !lyricsOpen },
                        queuePanelOpen = false,
                        onToggleQueuePanel = {},
                    ) {
                        HomeScreen(
                            state = PreviewData.homeState,
                            modifier = Modifier.fillMaxSize(),
                        )
                    }
                }
            }
        }

        // Verify full 3-column / docked-bottom experience
        compose.onNodeWithTag("desktop-scaffold").assertIsDisplayed()
        compose.onNodeWithTag("desktop-sidebar").assertIsDisplayed()
        compose.onNodeWithTag("desktop-player-bar").assertIsDisplayed()
        compose.onNodeWithTag("desktop-lyrics-panel").assertIsDisplayed()
        compose.onNodeWithTag("desktop-lyrics-list").assertIsDisplayed()

        // Toggle lyrics panel closed and verify
        compose.onNodeWithTag("desktop-lyrics-close").assertIsDisplayed().performClick()
        assertEquals(false, lyricsOpen)

        saveScreenshot("desktop-pc-layout")
    }
}
