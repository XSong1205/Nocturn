package dev.nocturn.app.ui

import android.graphics.Bitmap
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.asAndroidBitmap
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.assertIsOn
import androidx.compose.ui.test.captureToImage
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.onRoot
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performTouchInput
import androidx.compose.ui.test.swipe
import androidx.compose.ui.test.swipeLeft
import androidx.compose.ui.test.swipeRight
import androidx.compose.ui.unit.dp
import dev.nocturn.app.data.model.Lyrics
import dev.nocturn.app.data.model.ThemeMode
import dev.nocturn.app.ui.preview.PreviewData
import dev.nocturn.app.ui.screens.player.MiniPlayer
import dev.nocturn.app.ui.screens.player.PlayerScreen
import dev.nocturn.app.ui.state.PlayerUiState
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
 * Robolectric + Compose semantics tests for the player surfaces.
 *
 * The tests render the real [PlayerScreen]/[MiniPlayer] against [PreviewData] (no network, null
 * artwork URLs) at a phone-sized window and assert both the visible copy and the callback wiring.
 */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [36], qualifiers = "w412dp-h915dp")
@GraphicsMode(GraphicsMode.Mode.NATIVE)
class PlayerScreensTest {

    @get:Rule
    val compose = createComposeRule()

    private fun setPlayer(
        state: PlayerUiState = PreviewData.playerState,
        onCollapse: () -> Unit = {},
        onTogglePlay: () -> Unit = {},
        onNext: () -> Unit = {},
        onPrevious: () -> Unit = {},
        onSeek: (Float) -> Unit = {},
        onToggleLike: () -> Unit = {},
        onToggleShuffle: () -> Unit = {},
        onCycleRepeat: () -> Unit = {},
        onQueueSongClick: (Int) -> Unit = {},
    ) {
        compose.setContent {
            NocturnTheme(themeMode = ThemeMode.Dark) {
                PlayerScreen(
                    state = state,
                    onCollapse = onCollapse,
                    onTogglePlay = onTogglePlay,
                    onNext = onNext,
                    onPrevious = onPrevious,
                    onSeek = onSeek,
                    onToggleLike = onToggleLike,
                    onToggleShuffle = onToggleShuffle,
                    onCycleRepeat = onCycleRepeat,
                    onQueueSongClick = onQueueSongClick,
                )
            }
        }
    }

    @Test
    fun fullPlayerShowsSongDetails() {
        setPlayer()

        compose.onNodeWithText("晴天").assertExists()
        compose.onNodeWithText("周杰伦").assertExists()
        compose.onNodeWithText("极高").assertDoesNotExist()
    }

    @Test
    fun transportAndLikeButtonsDispatchCallbacks() {
        var togglePlayCount = 0
        var nextCount = 0
        var previousCount = 0
        var likeCount = 0
        setPlayer(
            onTogglePlay = { togglePlayCount++ },
            onNext = { nextCount++ },
            onPrevious = { previousCount++ },
            onToggleLike = { likeCount++ },
        )

        compose.onNodeWithTag("player-toggle-play").performClick()
        compose.onNodeWithTag("player-next").performClick()
        compose.onNodeWithTag("player-previous").performClick()
        compose.onNodeWithTag("player-like").performClick()

        assertEquals(1, togglePlayCount)
        assertEquals(1, nextCount)
        assertEquals(1, previousCount)
        assertEquals(1, likeCount)
    }

    @Test
    fun lyricsToggleShowsCurrentSyncedLine() {
        setPlayer()

        compose.onNodeWithTag("player-toggle-lyrics").performClick()
        compose.waitForIdle()

        // PreviewData.positionMs = 74s -> the "我怎么看不见" line (70s) is the active line.
        compose.onNodeWithText("我怎么看不见").assertExists()

        waitForLyricRow(compose, 10)
        // Accompanist anchors the row's top at 38% to leave room for the following lyrics.
        val listBounds = compose.onNodeWithTag("player-lyrics-list").fetchSemanticsNode().boundsInRoot
        val lineBounds = compose.onNode(lyricRow(10), useUnmergedTree = true).fetchSemanticsNode().boundsInRoot
        val anchorY = listBounds.top + listBounds.height * 0.38f
        assertTrue(
            "current lyric should sit at the reading anchor: line=${lineBounds.top} anchor=$anchorY",
            kotlin.math.abs(lineBounds.top - anchorY) <= lineBounds.height,
        )
    }

    @Test
    fun lyricsPlaceholderShowsForInstrumentalSongs() {
        setPlayer(state = PreviewData.playerState.copy(lyrics = Lyrics.EMPTY))

        compose.onNodeWithTag("player-toggle-lyrics").performClick()

        compose.onNodeWithText("暂无歌词").assertExists()
    }

    @Test
    fun switchingSongsInLyricsModeUpdatesLyricsAndAnimatesTransition() {
        val songA = PreviewData.songs[0]
        val songB = PreviewData.songs[1]
        val lyricsA = PreviewData.lyrics
        val lyricsB = Lyrics(listOf(
            dev.nocturn.app.data.model.LyricLine(startMs = 0, text = "新歌的第一行歌词"),
            dev.nocturn.app.data.model.LyricLine(startMs = 5_000, text = "新歌的第二行歌词"),
        ))
        val playerState = mutableStateOf(
            PreviewData.playerState.copy(
                song = songA,
                lyrics = lyricsA,
                positionMs = 74_000,
                durationMs = 269_000,
            )
        )
        compose.setContent {
            NocturnTheme(themeMode = ThemeMode.Dark) {
                PlayerScreen(
                    state = playerState.value,
                    initialLyricsVisible = true,
                )
            }
        }
        waitForLyricRow(compose, 10)
        compose.onNodeWithText("我怎么看不见").assertExists()

        // Switch song while lyrics page is visible
        compose.runOnIdle {
            playerState.value = playerState.value.copy(
                song = songB,
                lyrics = lyricsB,
                positionMs = 1_000,
                durationMs = 180_000,
            )
        }
        waitForLyricRow(compose, 0)
        compose.onNodeWithText("新歌的第一行歌词").assertExists()
    }

    @Test
    fun switchingSongShowsLoadingBeforeNewLyricsArriveWithoutDisruption() {
        val songA = PreviewData.songs[0]
        val songB = PreviewData.songs[1]
        val lyricsA = PreviewData.lyrics
        val playerState = mutableStateOf(
            PreviewData.playerState.copy(
                song = songA,
                lyrics = lyricsA,
                positionMs = 74_000,
            )
        )
        compose.setContent {
            NocturnTheme(themeMode = ThemeMode.Dark) {
                PlayerScreen(
                    state = playerState.value,
                    initialLyricsVisible = true,
                )
            }
        }
        waitForLyricRow(compose, 10)
        compose.onNodeWithText("我怎么看不见").assertExists()

        // Song switches, lyrics are loading
        compose.runOnIdle {
            playerState.value = playerState.value.copy(
                song = songB,
                lyrics = Lyrics.EMPTY,
                lyricsLoading = true,
                positionMs = 0,
            )
        }
        compose.onNodeWithText("正在加载歌词").assertExists()

        // Lyrics arrive
        compose.runOnIdle {
            playerState.value = playerState.value.copy(
                lyrics = Lyrics(listOf(dev.nocturn.app.data.model.LyricLine(0, "加载完成的歌词"))),
                lyricsLoading = false,
            )
        }
        waitForLyricRow(compose, 0)
        compose.onNodeWithText("加载完成的歌词").assertExists()
    }

    @Test
    fun lyricToggleLivesInDockAndCaptionButtonSitsAboveSeekBar() {
        compose.setContent {
            NocturnTheme(themeMode = ThemeMode.Dark) {
                PlayerScreen(state = PreviewData.bilingualPlayerState, initialLyricsVisible = true)
            }
        }
        waitForLyricRow(compose, 2)
        val dock = compose.onNodeWithTag("player-dock").fetchSemanticsNode().boundsInRoot
        val toggle = compose.onNodeWithTag("player-toggle-lyrics").assertIsOn().fetchSemanticsNode().boundsInRoot
        assertTrue("lyrics toggle belongs inside dock", toggle.top >= dock.top && toggle.bottom <= dock.bottom)
        val translation = compose.onNodeWithTag("lyrics-translation").fetchSemanticsNode().boundsInRoot
        val seek = compose.onNodeWithTag("player-seek").fetchSemanticsNode().boundsInRoot
        assertTrue("compact translation control should sit above seek on the right", translation.bottom <= seek.top && translation.center.x > seek.center.x)
        compose.onNodeWithText("译").assertExists()
        compose.onNodeWithText("译文").assertDoesNotExist()
        compose.onNodeWithText("Hi-Res").assertDoesNotExist()
        compose.onNodeWithText("倘若失去", substring = true).assertExists()
        compose.onNodeWithTag("lyrics-translation").performClick()
        compose.onNodeWithText("倘若失去", substring = true).assertDoesNotExist()
        compose.onNodeWithTag("player-toggle-lyrics").performClick()
        compose.onNodeWithTag("lyrics-translation").assertDoesNotExist()
        compose.onNodeWithTag("player-toggle-lyrics").performClick()
        waitForLyricRow(compose, 2)
        compose.onNodeWithText("倘若失去", substring = true).assertDoesNotExist()
    }

    @Test
    fun captureBilingualLyricsInBothThemes() {
        val theme = mutableStateOf(ThemeMode.Dark)
        compose.setContent {
            NocturnTheme(themeMode = theme.value) {
                PlayerScreen(state = PreviewData.bilingualPlayerState, initialLyricsVisible = true)
            }
        }
        waitForLyricRow(compose, 2)
        capture("player-lyrics-bilingual-dark.png")
        compose.runOnIdle { theme.value = ThemeMode.Light }
        waitForLyricRow(compose, 2)
        capture("player-lyrics-bilingual-light.png")
    }

    @Test
    fun seekSliderDispatchesFractionOnRelease() {
        val seeked = mutableListOf<Float>()
        setPlayer(onSeek = { seeked.add(it) })

        compose.onNodeWithTag("player-seek").performTouchInput {
            swipe(start = center, end = centerRight, durationMillis = 200)
        }
        compose.waitForIdle()

        assertTrue("seek callback should fire on release", seeked.isNotEmpty())
        assertTrue("dragging right should seek forward", seeked.last() > 0.4f)
    }

    @Test
    fun playbackErrorIsSurfaced() {
        setPlayer(state = PreviewData.playerState.copy(error = "获取播放地址失败"))

        compose.onNodeWithText("获取播放地址失败").assertExists()
    }

    @Test
    fun queueSheetOpensFromToolbar() {
        setPlayer()

        compose.onNodeWithTag("player-queue").performClick()

        compose.onNodeWithTag("player-queue-sheet").assertExists()
        compose.onNodeWithText("播放队列").assertExists()
    }

    @Test
    fun miniPlayerShowsTrackAndDispatchesActions() {
        var expandCount = 0
        var toggleCount = 0
        compose.setContent {
            NocturnTheme(themeMode = ThemeMode.Dark) {
                MiniPlayer(
                    state = PreviewData.miniPlayerState,
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 16.dp, vertical = 6.dp),
                    onExpand = { expandCount++ },
                    onTogglePlay = { toggleCount++ },
                )
            }
        }

        compose.onNodeWithText("晴天").assertExists()
        compose.onNodeWithText("周杰伦").assertExists()

        compose.onNodeWithTag("mini-player").performClick()
        compose.onNodeWithTag("mini-player-toggle-play").performClick()

        assertEquals(1, expandCount)
        assertEquals(1, toggleCount)
    }

    @Test
    fun miniPlayerSwipesSkipTracksWithoutExpanding() {
        var expandCount = 0
        var nextCount = 0
        var previousCount = 0
        compose.setContent {
            NocturnTheme(themeMode = ThemeMode.Dark) {
                MiniPlayer(
                    state = PreviewData.miniPlayerState,
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 16.dp, vertical = 6.dp),
                    onExpand = { expandCount++ },
                    onNext = { nextCount++ },
                    onPrevious = { previousCount++ },
                )
            }
        }

        compose.onNodeWithTag("mini-player").performTouchInput { swipeLeft() }
        compose.waitForIdle()
        compose.onNodeWithTag("mini-player").performTouchInput { swipeRight() }
        compose.waitForIdle()

        assertEquals(1, nextCount)
        assertEquals(1, previousCount)
        assertEquals("a horizontal swipe must not open the full player", 0, expandCount)
    }

    @Test
    fun miniPlayerSwipeUpdatesSongSmoothly() {
        var currentSong by mutableStateOf(PreviewData.songs[0])
        compose.setContent {
            NocturnTheme(themeMode = ThemeMode.Dark) {
                MiniPlayer(
                    state = PreviewData.miniPlayerState.copy(song = currentSong),
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 16.dp, vertical = 6.dp),
                    onNext = { currentSong = PreviewData.songs[1] },
                )
            }
        }

        compose.onNodeWithText("晴天").assertExists()

        compose.onNodeWithTag("mini-player").performTouchInput { swipeLeft() }
        compose.waitForIdle()

        compose.onNodeWithText("起风了").assertExists()
    }

    @Test
    fun capturePlayerScreenshotsForReview() {
        compose.setContent {
            NocturnTheme(themeMode = ThemeMode.Dark) {
                PlayerScreen(state = PreviewData.playerState)
            }
        }
        compose.waitForIdle()
        capture("player-cover-dark.png")
        compose.onNodeWithTag("player-toggle-lyrics").performClick()
        compose.waitForIdle()
        compose.mainClock.advanceTimeBy(1_500)
        compose.waitForIdle()
        waitForLyricRow(compose, 10)
        compose.onNodeWithText("我怎么看不见").assertIsDisplayed()
        capture("player-lyrics-dark.png")
    }

    private fun capture(name: String) {
        val dir = File("build/screenshots").apply { mkdirs() }
        val bitmap = compose.onRoot().captureToImage().asAndroidBitmap()
        File(dir, name).outputStream().use { bitmap.compress(Bitmap.CompressFormat.PNG, 100, it) }
    }
}
