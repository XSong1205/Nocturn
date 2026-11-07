package dev.nocturn.app.ui

import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.Paint
import android.util.Base64
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.runtime.mutableStateOf
import androidx.compose.ui.Modifier
import androidx.compose.ui.test.hasTestTag
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.performScrollToNode
import dev.nocturn.app.data.model.QrChallenge
import dev.nocturn.app.data.model.QrLoginState
import dev.nocturn.app.data.model.ThemeMode
import dev.nocturn.app.ui.components.NocturnBackdrop
import dev.nocturn.app.ui.preview.PreviewData
import dev.nocturn.app.ui.screens.home.DailySongsScreen
import dev.nocturn.app.ui.screens.home.HomeScreen
import dev.nocturn.app.ui.screens.library.LibraryScreen
import dev.nocturn.app.ui.screens.library.LikedSongsScreen
import dev.nocturn.app.ui.screens.library.LoginSheetContent
import dev.nocturn.app.ui.screens.player.PlayerScreen
import dev.nocturn.app.ui.screens.playlist.AlbumScreen
import dev.nocturn.app.ui.screens.playlist.ArtistScreen
import dev.nocturn.app.ui.screens.playlist.PlaylistScreen
import dev.nocturn.app.ui.screens.search.SearchScreen
import dev.nocturn.app.ui.state.LoginUiState
import dev.nocturn.app.ui.theme.NocturnTheme
import java.io.ByteArrayOutputStream
import java.io.File
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode

/**
 * Renders every top-level surface with deterministic preview data and writes PNG screenshots to
 * `app/build/screenshots/`. These are host renders (Robolectric), not device screenshots, and are
 * used to review the Material 3 Expressive visual language without an attached device.
 */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [36], qualifiers = "w412dp-h915dp-xhdpi")
@GraphicsMode(GraphicsMode.Mode.NATIVE)
class ScreenshotGalleryTest {
    @get:Rule
    val compose = createComposeRule()

    private val target = mutableStateOf(GalleryTarget.Home)
    private val dark = mutableStateOf(false)
    private val loginState = LoginUiState(
        visible = true,
        loading = false,
        challenge = QrChallenge("gallery-demo-key", demoQrBase64()),
        state = QrLoginState.WaitingForScan,
    )

    @Test
    fun captureEveryScreen() {
        compose.setContent {
            NocturnTheme(themeMode = if (dark.value) ThemeMode.Dark else ThemeMode.Light) {
                NocturnBackdrop {
                    Box(Modifier.fillMaxSize()) {
                        when (target.value) {
                            GalleryTarget.Home -> HomeScreen(state = PreviewData.homeState)
                            GalleryTarget.HomeAnonymous -> HomeScreen(state = PreviewData.homeStateAnonymous)
                            GalleryTarget.Search -> SearchScreen(state = PreviewData.searchIdle)
                            GalleryTarget.SearchResults -> SearchScreen(
                                state = PreviewData.searchResults.copy(expanded = false),
                            )
                            GalleryTarget.Library -> LibraryScreen(state = PreviewData.libraryLoggedIn)
                            GalleryTarget.LibraryAnonymous -> LibraryScreen(state = PreviewData.libraryAnonymous)
                            GalleryTarget.LibrarySettings -> LibraryScreen(
                                state = PreviewData.libraryAnonymous.copy(hapticsEnabled = true),
                            )
                            GalleryTarget.Playlist -> PlaylistScreen(state = PreviewData.playlistState)
                            GalleryTarget.Album -> AlbumScreen(state = PreviewData.albumState)
                            GalleryTarget.Artist -> ArtistScreen(state = PreviewData.artistState)
                            GalleryTarget.LikedSongs -> LikedSongsScreen(state = PreviewData.likedSongsState)
                            GalleryTarget.DailySongs -> DailySongsScreen(state = PreviewData.homeState)
                            GalleryTarget.PlayerCover -> PlayerScreen(state = PreviewData.playerState)
                            GalleryTarget.PlayerLyrics -> PlayerScreen(
                                state = PreviewData.playerState.copy(positionMs = 46_000),
                                initialLyricsVisible = true,
                            )
                            GalleryTarget.LoginQr -> Surface(
                                modifier = Modifier.fillMaxSize(),
                                color = MaterialTheme.colorScheme.surfaceContainerLowest,
                            ) {
                                LoginSheetContent(
                                    state = loginState,
                                    onClose = {},
                                    onRefresh = {},
                                )
                            }
                        }
                    }
                }
            }
        }
        compose.waitForIdle()
        GalleryTarget.entries.forEach { entry ->
            compose.runOnIdle {
                dark.value = entry.dark
                target.value = entry
            }
            compose.waitForIdle()
            if (entry == GalleryTarget.PlayerLyrics) waitForLyricRow(compose, 6)
            if (entry == GalleryTarget.LibrarySettings) {
                // The settings block lives below the fold; scroll it into view so the screenshot
                // shows the quality chips, appearance toggle and the haptics switch.
                compose.onNodeWithTag("library-screen")
                    .performScrollToNode(hasTestTag("library-haptics-switch"))
                compose.waitForIdle()
            }
            capture(entry.fileName)
        }
    }

    private fun capture(name: String) {
        compose.waitForIdle()
        val file = File("build/screenshots/$name.png").apply { parentFile?.mkdirs() }
        val view = android.view.inspector.WindowInspector.getGlobalWindowViews()
            .lastOrNull { it.width > 0 && it.height > 0 && it.visibility == android.view.View.VISIBLE }
            ?: error("No visible window to capture")
        val bitmap = Bitmap.createBitmap(view.width, view.height, Bitmap.Config.ARGB_8888)
        view.draw(Canvas(bitmap))
        file.outputStream().use { bitmap.compress(Bitmap.CompressFormat.PNG, 100, it) }
        bitmap.recycle()
    }

    private enum class GalleryTarget(val fileName: String, val dark: Boolean) {
        Home("home-light", false),
        HomeAnonymous("home-anonymous-light", false),
        Search("search-idle-light", false),
        SearchResults("search-results-light", false),
        Library("library-light", false),
        LibraryAnonymous("library-anonymous-light", false),
        LibrarySettings("library-settings-light", false),
        Playlist("playlist-light", false),
        Album("album-light", false),
        Artist("artist-light", false),
        LikedSongs("liked-songs-light", false),
        DailySongs("daily-songs-light", false),
        PlayerCover("player-cover-dark", true),
        PlayerLyrics("player-lyrics-dark", true),
        LoginQr("login-qr-light", false),
    }

    /** Generates a deterministic placeholder QR bitmap so the login sheet render needs no network. */
    private fun demoQrBase64(): String {
        val size = 480
        val bitmap = Bitmap.createBitmap(size, size, Bitmap.Config.ARGB_8888)
        val canvas = Canvas(bitmap)
        canvas.drawColor(android.graphics.Color.WHITE)
        val paint = Paint().apply {
            color = android.graphics.Color.BLACK
            style = Paint.Style.FILL
        }
        val cells = 25
        val cell = size.toFloat() / cells
        val random = java.util.Random(42)
        for (row in 0 until cells) {
            for (column in 0 until cells) {
                val finder = (row < 7 && column < 7) || (row < 7 && column >= cells - 7) ||
                    (row >= cells - 7 && column < 7)
                if (finder || random.nextInt(100) < 46) {
                    canvas.drawRect(
                        column * cell,
                        row * cell,
                        (column + 1) * cell,
                        (row + 1) * cell,
                        paint,
                    )
                }
            }
        }
        val output = ByteArrayOutputStream()
        bitmap.compress(Bitmap.CompressFormat.PNG, 100, output)
        bitmap.recycle()
        return Base64.encodeToString(output.toByteArray(), Base64.NO_WRAP)
    }
}
