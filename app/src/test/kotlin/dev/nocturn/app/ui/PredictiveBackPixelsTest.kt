package dev.nocturn.app.ui

import android.graphics.Bitmap
import androidx.activity.BackEventCompat
import androidx.activity.OnBackPressedDispatcher
import androidx.activity.compose.LocalOnBackPressedDispatcherOwner
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.asAndroidBitmap
import androidx.compose.ui.test.captureToImage
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onRoot
import dev.nocturn.app.ui.state.NavState
import dev.nocturn.app.ui.state.Screen
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
 * Pixel-level checks of the predictive back preview: bounds cannot show a clip or a scrim, so
 * these read the rendered frame.
 */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [36], qualifiers = "w411dp-h914dp-xhdpi")
@GraphicsMode(GraphicsMode.Mode.NATIVE)
class PredictiveBackPixelsTest {
    @get:Rule
    val compose = createComposeRule()

    private var nav by mutableStateOf(NavState(stack = listOf(Screen.Album(1))))
    private var pops = 0
    private lateinit var dispatcher: OnBackPressedDispatcher

    private val foreground = Color(0xFFE53935)
    private val background = Color(0xFF3949AB)

    private fun start() {
        compose.setContent {
            dispatcher = LocalOnBackPressedDispatcherOwner.current!!.onBackPressedDispatcher
            NocturnTheme {
                ScreenNavigationHost(nav, true, {
                    pops++
                    nav = nav.copy(stack = nav.stack.dropLast(1))
                }) { screen ->
                    Box(
                        Modifier
                            .fillMaxSize()
                            .background(if (screen == Screen.Home) background else foreground),
                    )
                }
            }
        }
        compose.waitForIdle()
    }

    private fun frame(): Bitmap = compose.onRoot().captureToImage().asAndroidBitmap()

    private fun Bitmap.color(x: Int, y: Int) = Color(getPixel(x, y))

    private fun dragTo(target: Float, edge: Int = BackEventCompat.EDGE_LEFT) {
        compose.runOnIdle {
            dispatcher.dispatchOnBackStarted(BackEventCompat(0f, 100f, 0f, edge))
            dispatcher.dispatchOnBackProgressed(BackEventCompat(0f, 100f, 0f, edge))
        }
        compose.mainClock.advanceTimeByFrame()
        var progress = 0f
        while (progress < target - 1e-4f) {
            progress = minOf(progress + 0.02f, target)
            compose.runOnIdle {
                dispatcher.dispatchOnBackProgressed(BackEventCompat(0f, 100f, progress, edge))
            }
            compose.mainClock.advanceTimeByFrame()
            compose.runOnIdle { }
        }
        repeat(3) {
            compose.mainClock.advanceTimeByFrame()
            compose.runOnIdle { }
        }
    }

    @Test
    fun theForegroundPageStaysOpaqueWhileTheFingerIsStillDown() {
        start()
        compose.mainClock.autoAdvance = false
        try {
            dragTo(0.9f)
            val bitmap = frame()
            val centre = bitmap.color(bitmap.width / 2, bitmap.height / 2)
            assertEquals("No fade before release: red", foreground.red, centre.red, 0.02f)
            assertEquals("No fade before release: blue", foreground.blue, centre.blue, 0.02f)
            assertEquals("The page is still on the stack", 0, pops)
        } finally {
            compose.mainClock.autoAdvance = true
            compose.runOnIdle { dispatcher.dispatchOnBackCancelled() }
        }
    }

    @Test
    fun releasingShrinksAndFadesThePageBeforeThePop() {
        start()
        compose.mainClock.autoAdvance = false
        try {
            dragTo(0.5f)
            compose.runOnIdle { dispatcher.onBackPressed() }
            compose.runOnIdle { }
            assertEquals("The stack is untouched while the commit plays", 0, pops)

            repeat(8) {
                compose.mainClock.advanceTimeByFrame()
                compose.runOnIdle { }
            }
            val mid = frame().color(frame().width / 2, frame().height / 2)
            assertTrue(
                "Mid-commit the page is partly faded: red=${mid.red} blue=${mid.blue}",
                mid.red < foreground.red - 0.05f && mid.red > background.red - 0.2f,
            )
            assertEquals("Still not popped mid-commit", 0, pops)

            repeat(40) {
                compose.mainClock.advanceTimeByFrame()
                compose.runOnIdle { }
            }
            assertEquals("The stack pops once, after the commit", 1, pops)
            val end = frame().color(frame().width / 2, frame().height / 2)
            assertEquals("The previous page is revealed undimmed", background.blue, end.blue, 0.02f)
        } finally {
            compose.mainClock.autoAdvance = true
        }
    }

    @Test
    fun theShrunkenForegroundPageKeepsRoundedCornersOverADimmedBackground() {
        start()
        compose.mainClock.autoAdvance = false
        try {
            dragTo(0.6f)
            val bitmap = frame()
            val width = bitmap.width
            val height = bitmap.height
            File("build/screenshots").apply { mkdirs() }
                .resolve("predictive-back-preview.png")
                .outputStream().use { bitmap.compress(Bitmap.CompressFormat.PNG, 100, it) }

            // The page is scaled to 90% and shifted toward the swiping side, so its centre is
            // foreground colour and the corner of the shrunken page is cut away.
            val centre = bitmap.color(width / 2, height / 2)
            assertEquals("Page content is drawn at the centre", foreground.red, centre.red, 0.02f)

            // Find the shrunken page's top-left corner on its edge row and column.
            val midY = height / 2
            var left = 0
            while (left < width && bitmap.color(left, midY).red < 0.7f) left++
            var top = 0
            while (top < height && bitmap.color(width / 2, top).red < 0.7f) top++
            assertTrue("The page no longer fills the window: left=$left top=$top", left > 4 && top > 4)

            // Rounded: the pixel diagonally inside the page's corner is not page colour.
            val inset = 3
            val corner = bitmap.color(left + inset, top + inset)
            assertTrue(
                "The corner of the shrunken page is clipped, got $corner",
                corner.red < 0.7f,
            )

            // Dimmed: the revealed page is darker than its own colour.
            val revealed = bitmap.color(2, height / 2)
            assertTrue(
                "The page behind is dimmed: ${revealed.blue} vs ${background.blue}",
                revealed.blue < background.blue * 0.85f && revealed.blue > 0f,
            )
        } finally {
            compose.mainClock.autoAdvance = true
            compose.runOnIdle { dispatcher.dispatchOnBackCancelled() }
        }
    }
}
