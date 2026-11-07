package dev.nocturn.app.ui

import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.mutableLongStateOf
import androidx.compose.runtime.IntState
import androidx.compose.runtime.SideEffect
import androidx.compose.ui.Modifier
import androidx.compose.ui.semantics.SemanticsProperties
import androidx.compose.ui.semantics.getOrNull
import androidx.compose.ui.test.SemanticsMatcher
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.hasAnyAncestor
import androidx.compose.ui.test.hasClickAction
import androidx.compose.ui.test.junit4.ComposeContentTestRule
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onFirst
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performTouchInput
import androidx.compose.ui.test.swipeUp
import dev.nocturn.app.data.model.LyricLine
import dev.nocturn.app.data.model.LyricWord
import dev.nocturn.app.data.model.Lyrics
import dev.nocturn.app.data.model.ThemeMode
import dev.nocturn.app.ui.preview.PreviewData
import dev.nocturn.app.ui.screens.player.LyricsView
import dev.nocturn.app.ui.screens.player.rememberLyricsPosition
import dev.nocturn.app.ui.theme.NocturnTheme
import org.junit.Assert.assertEquals
import org.junit.Assert.assertSame
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode

internal fun lyricRow(index: Int) = SemanticsMatcher("lyric row $index") {
    it.config.getOrNull(SemanticsProperties.CollectionItemInfo)?.rowIndex == index
}

internal fun waitForLyricRow(compose: ComposeContentTestRule, index: Int) {
    // Text preparation and rasterisation run on Dispatchers.Default in the published library.
    compose.waitUntil(10_000) {
        compose.onAllNodes(lyricRow(index), useUnmergedTree = true).fetchSemanticsNodes().isNotEmpty()
    }
    compose.waitForIdle()
}

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [36], qualifiers = "w412dp-h915dp")
@GraphicsMode(GraphicsMode.Mode.NATIVE)
class LyricsViewTest {
    @get:Rule val compose = createComposeRule()

    @Test
    fun tappingRenderedLineSeeksToItsOriginalTimestamp() {
        var selected: LyricLine? = null
        compose.setContent {
            NocturnTheme(themeMode = ThemeMode.Dark) {
                LyricsView(PreviewData.lyrics, 74_000, false, Modifier.fillMaxSize(), { selected = it }, 269_000)
            }
        }
        waitForLyricRow(compose, 10)
        compose.onAllNodes(hasClickAction() and hasAnyAncestor(lyricRow(10)), useUnmergedTree = true)
            .onFirst().performClick()
        assertEquals(70_000L, selected?.startMs)
    }

    @Test
    fun captionsCanBeToggledAndSongChangeResetsTheContent() {
        val showTranslation = mutableStateOf(true)
        val showRomanization = mutableStateOf(false)
        val lyrics = mutableStateOf(Lyrics(listOf(LyricLine(
            1_000, "你好", "Hello", 2_000,
            listOf(LyricWord(1_000, 1_000, "你"), LyricWord(2_000, 1_000, "好")), "ni hao",
        ))))
        compose.setContent {
            NocturnTheme(themeMode = ThemeMode.Light) {
                LyricsView(
                    lyrics.value, 1_500, false, Modifier.fillMaxSize(), durationMs = 10_000,
                    showTranslation = showTranslation.value, showRomanization = showRomanization.value,
                )
            }
        }
        waitForLyricRow(compose, 0)
        compose.onNodeWithText("Hello", substring = true).assertExists()
        compose.onNodeWithText("ni hao", substring = true).assertDoesNotExist()
        compose.runOnIdle { showRomanization.value = true }
        compose.onNodeWithText("ni hao", substring = true).assertExists()
        compose.runOnIdle { showTranslation.value = false }
        compose.onNodeWithText("Hello", substring = true).assertDoesNotExist()
        compose.runOnIdle { lyrics.value = Lyrics(listOf(LyricLine(0, "下一首"))) }
        waitForLyricRow(compose, 0)
        compose.onNodeWithText("下一首").assertExists()
        compose.onNodeWithTag("lyrics-translation").assertDoesNotExist()
        compose.onNodeWithTag("lyrics-romanization").assertDoesNotExist()
    }

    @Test
    fun manualBrowsingOffersImmediateReturnToPlayback() {
        compose.setContent {
            NocturnTheme(themeMode = ThemeMode.Dark) {
                LyricsView(PreviewData.lyrics, 74_000, false, Modifier.fillMaxSize(), durationMs = 269_000)
            }
        }
        waitForLyricRow(compose, 10)
        compose.mainClock.autoAdvance = false
        compose.onNodeWithTag("player-lyrics-list").performTouchInput { swipeUp(durationMillis = 300) }
        compose.mainClock.advanceTimeBy(350)
        compose.onNodeWithTag("lyrics-resume-follow").assertIsDisplayed().performClick()
        compose.mainClock.advanceTimeBy(1_000)
        compose.onNodeWithTag("lyrics-resume-follow").assertDoesNotExist()
        compose.onNode(lyricRow(10), useUnmergedTree = true).assertIsDisplayed()
    }

    @Test
    fun loadingAndEmptyStatesDoNotCreateARenderer() {
        val loading = mutableStateOf(true)
        compose.setContent {
            NocturnTheme {
                LyricsView(Lyrics.EMPTY, 0, loading.value)
            }
        }
        compose.onNodeWithText("正在加载歌词").assertExists()
        compose.onNodeWithTag("player-lyrics-list").assertDoesNotExist()
        compose.runOnIdle { loading.value = false }
        compose.onNodeWithText("暂无歌词").assertExists()
    }

    @Test
    fun frameClockKeepsItsIdentityAcrossSamplesPauseAndBackwardSeek() {
        val sample = mutableLongStateOf(1_000L)
        val playing = mutableStateOf(false)
        lateinit var clock: IntState
        compose.setContent {
            val position = rememberLyricsPosition(sample.longValue, 10_000, playing.value)
            SideEffect { clock = position }
        }
        compose.waitForIdle()
        val originalClock = clock
        compose.runOnIdle { sample.longValue = 2_000 }
        compose.runOnIdle { assertSame(originalClock, clock); assertEquals(2_000, clock.intValue) }
        compose.mainClock.autoAdvance = false
        compose.runOnIdle { playing.value = true }
        compose.mainClock.advanceTimeByFrame()
        compose.waitForIdle()
        compose.mainClock.advanceTimeBy(250)
        compose.runOnIdle { assertTrue("interpolated position = ${clock.intValue}", clock.intValue in 2_100..2_300) }
        compose.runOnIdle { playing.value = false; sample.longValue = 500 }
        compose.mainClock.advanceTimeBy(32)
        compose.runOnIdle { assertSame(originalClock, clock); assertEquals(500, clock.intValue) }
        compose.mainClock.advanceTimeBy(1_000)
        compose.runOnIdle { assertEquals(500, clock.intValue) }
    }

    @Test
    fun changingTheSungLineAnimatesToTheReadingAnchor() {
        val sample = mutableLongStateOf(24_000L)
        compose.setContent {
            NocturnTheme {
                LyricsView(PreviewData.lyrics, sample.longValue, false, Modifier.fillMaxSize(), durationMs = 269_000)
            }
        }
        waitForLyricRow(compose, 2)
        val initialTop = compose.onNode(lyricRow(5), useUnmergedTree = true).fetchSemanticsNode().boundsInRoot.top
        compose.mainClock.autoAdvance = false
        compose.runOnIdle { sample.longValue = 40_000 }
        compose.mainClock.advanceTimeBy(64)
        val carriedTop = compose.onNode(lyricRow(5), useUnmergedTree = true).fetchSemanticsNode().boundsInRoot.top
        compose.mainClock.advanceTimeBy(1_500)
        val list = compose.onNodeWithTag("player-lyrics-list").fetchSemanticsNode().boundsInRoot
        val settled = compose.onNode(lyricRow(5), useUnmergedTree = true).fetchSemanticsNode().boundsInRoot
        assertTrue("line should move upwards", initialTop > settled.top)
        assertTrue("line should carry towards its anchor over time", carriedTop > settled.top + 1f)
        assertEquals(list.top + list.height * 0.38f, settled.top, 2f)
    }
}
