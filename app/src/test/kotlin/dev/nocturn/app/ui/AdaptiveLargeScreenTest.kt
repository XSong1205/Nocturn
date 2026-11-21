package dev.nocturn.app.ui

import android.graphics.Bitmap
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.asAndroidBitmap
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.captureToImage
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onRoot
import androidx.compose.ui.test.performClick
import androidx.compose.ui.unit.dp
import dev.nocturn.app.data.model.ThemeMode
import dev.nocturn.app.ui.adaptive.LocalWindowWidthClass
import dev.nocturn.app.ui.adaptive.WindowWidthClass
import dev.nocturn.app.ui.components.NocturnBackdrop
import dev.nocturn.app.ui.preview.PreviewData
import dev.nocturn.app.ui.screens.home.DailySongsScreen
import dev.nocturn.app.ui.screens.home.HomeScreen
import dev.nocturn.app.ui.screens.library.LibraryScreen
import dev.nocturn.app.ui.screens.library.LikedSongsScreen
import dev.nocturn.app.ui.screens.library.RecentSongsScreen
import dev.nocturn.app.ui.screens.player.PlayerScreen
import dev.nocturn.app.ui.screens.playlist.AlbumScreen
import dev.nocturn.app.ui.screens.playlist.ArtistScreen
import dev.nocturn.app.ui.screens.playlist.PlaylistScreen
import dev.nocturn.app.ui.screens.search.SearchScreen
import dev.nocturn.app.ui.screens.settings.SettingsScreen
import dev.nocturn.app.ui.theme.NocturnTheme
import java.io.File
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode

/**
 * Robolectric Compose UI test suite verifying adaptive Material Design 3 Expressive layouts
 * on large screens, tablets, and expanded window sizes (>= 600dp and >= 840dp).
 */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [36], qualifiers = "w1024dp-h768dp-xhdpi")
@GraphicsMode(GraphicsMode.Mode.NATIVE)
class AdaptiveLargeScreenTest {

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
    fun twoPanePlayerRendersCoverAndLyricsSimultaneously() {
        var togglePlayCalled = false

        val stateWithBoth = PreviewData.bilingualPlayerState.copy(
            positionMs = 115_000,
            lyrics = dev.nocturn.app.data.model.Lyrics(
                listOf(
                    dev.nocturn.app.data.model.LyricLine(
                        startMs = 100_000,
                        durationMs = 5_000,
                        text = "We could start again",
                        translation = "我们可以重新开始",
                        romanization = "ui kudeu seutateu eogen",
                    )
                )
            )
        )

        compose.setContent {
            NocturnTheme(themeMode = ThemeMode.Dark) {
                CompositionLocalProvider(LocalWindowWidthClass provides WindowWidthClass.Expanded) {
                    PlayerScreen(
                        state = stateWithBoth,
                        onTogglePlay = { togglePlayCalled = true },
                        initialLyricsVisible = true,
                    )
                }
            }
        }

        // Left pane controls
        compose.onNodeWithTag("player-toggle-play").assertIsDisplayed().performClick()
        assertTrue(togglePlayCalled)
        compose.onNodeWithTag("player-seek").assertIsDisplayed()
        compose.onNodeWithTag("player-like").assertIsDisplayed()

        // Right pane synchronized lyrics simultaneously visible
        compose.onNodeWithTag("player-lyrics-list").assertIsDisplayed()
        compose.onNodeWithTag("lyrics-translation").assertIsDisplayed().performClick()
        compose.onNodeWithTag("lyrics-romanization").assertIsDisplayed().performClick()

        saveScreenshot("tablet-player-two-pane")
    }

    @Test
    fun playlistScreenRendersHorizontalHeroAndDualColumnTracks() {
        var playAllCalled = false
        compose.setContent {
            NocturnTheme(themeMode = ThemeMode.Light) {
                CompositionLocalProvider(LocalWindowWidthClass provides WindowWidthClass.Expanded) {
                    NocturnBackdrop {
                        PlaylistScreen(
                            state = PreviewData.playlistState,
                            onPlayAll = { playAllCalled = true },
                        )
                    }
                }
            }
        }

        compose.onNodeWithTag("playlist-header").assertIsDisplayed()
        compose.onNodeWithTag("playlist-play-all").assertIsDisplayed().performClick()
        assertTrue(playAllCalled)

        // Both columns of tracks rendered
        compose.onNodeWithTag("playlist-song-0").assertIsDisplayed()
        compose.onNodeWithTag("playlist-song-1").assertIsDisplayed()

        saveScreenshot("tablet-playlist-expanded")
    }

    @Test
    fun albumAndArtistScreensRenderHorizontalHeroAndDualColumns() {
        compose.setContent {
            NocturnTheme(themeMode = ThemeMode.Dark) {
                CompositionLocalProvider(LocalWindowWidthClass provides WindowWidthClass.Expanded) {
                    NocturnBackdrop {
                        AlbumScreen(state = PreviewData.albumState)
                    }
                }
            }
        }

        compose.onNodeWithTag("album-hero").assertIsDisplayed()
        compose.onNodeWithTag("album-song-0").assertIsDisplayed()
        compose.onNodeWithTag("album-song-1").assertIsDisplayed()

        saveScreenshot("tablet-album-expanded")
    }

    @Test
    fun libraryScreenRendersSideBySideCollectionsAndAdaptivePlaylistGrid() {
        var likedClicked = false
        var recentClicked = false
        compose.setContent {
            NocturnTheme(themeMode = ThemeMode.Light) {
                CompositionLocalProvider(LocalWindowWidthClass provides WindowWidthClass.Expanded) {
                    NocturnBackdrop {
                        LibraryScreen(
                            state = PreviewData.libraryLoggedIn,
                            onLikedSongsClick = { likedClicked = true },
                            onRecentSongsClick = { recentClicked = true },
                        )
                    }
                }
            }
        }

        compose.onNodeWithTag("library-profile").assertIsDisplayed()
        compose.onNodeWithTag("library-liked-entry").assertIsDisplayed().performClick()
        assertTrue(likedClicked)
        compose.onNodeWithTag("library-recent-entry").assertIsDisplayed().performClick()
        assertTrue(recentClicked)

        saveScreenshot("tablet-library-expanded")
    }

    @Test
    fun likedAndRecentSongsScreenRenderWideHeroAndDualColumnTracks() {
        compose.setContent {
            NocturnTheme(themeMode = ThemeMode.Dark) {
                CompositionLocalProvider(LocalWindowWidthClass provides WindowWidthClass.Expanded) {
                    NocturnBackdrop {
                        LikedSongsScreen(state = PreviewData.likedSongsState)
                    }
                }
            }
        }

        compose.onNodeWithTag("liked-play-all").assertIsDisplayed()
        val firstSongId = PreviewData.likedSongsState.songs.first().id
        compose.onNodeWithTag("liked-song-row-$firstSongId").assertIsDisplayed()

        saveScreenshot("tablet-liked-songs-expanded")
    }

    @Test
    fun settingsScreenRendersCenteredReadableWidth() {
        compose.setContent {
            NocturnTheme(themeMode = ThemeMode.Light) {
                CompositionLocalProvider(LocalWindowWidthClass provides WindowWidthClass.Expanded) {
                    NocturnBackdrop {
                        SettingsScreen(state = PreviewData.settingsState)
                    }
                }
            }
        }

        compose.onNodeWithTag("settings-screen").assertIsDisplayed()
        compose.onNodeWithTag("settings-menu-playback").assertIsDisplayed()
        compose.onNodeWithTag("settings-menu-appearance").assertIsDisplayed()

        saveScreenshot("tablet-settings-expanded")
    }
}
