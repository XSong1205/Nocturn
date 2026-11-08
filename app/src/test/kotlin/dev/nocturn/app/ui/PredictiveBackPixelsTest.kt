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

/** Verify that native slides reveal two opaque pages without gaps, fading or stale overlays. */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [36], qualifiers = "w411dp-h914dp-xhdpi")
@GraphicsMode(GraphicsMode.Mode.NATIVE)
class PredictiveBackPixelsTest {
    @get:Rule
    val compose = createComposeRule()

    private var navigation by mutableStateOf(NavState(stack = listOf(Screen.Album(1))))
    private var pops = 0
    private lateinit var dispatcher: OnBackPressedDispatcher
    private val foreground = Color(0xFFE53935)
    private val background = Color(0xFF3949AB)

    @Test
    fun nativePredictiveSlideKeepsBothPagesOpaqueWithoutAGap() {
        start()
        compose.mainClock.autoAdvance = false
        try {
            dragTo(0.5f)
            val bitmap = frame()
            File("build/screenshots").apply { mkdirs() }
                .resolve("predictive-back-preview.png")
                .outputStream().use { bitmap.compress(Bitmap.CompressFormat.PNG, 100, it) }
            assertColor(background, bitmap, 2, bitmap.height / 2)
            assertColor(foreground, bitmap, bitmap.width - 3, bitmap.height / 2)
            // Allow the rasterizer's one-pixel antialiasing at the moving seam.
            var seamPixels = 0
            for (x in 0 until bitmap.width) {
                val pixel = Color(bitmap.getPixel(x, bitmap.height / 2))
                val expected = if (pixel.red > 0.5f) foreground else background
                if (kotlin.math.abs(pixel.red - expected.red) > 0.02f ||
                    kotlin.math.abs(pixel.green - expected.green) > 0.02f ||
                    kotlin.math.abs(pixel.blue - expected.blue) > 0.02f
                ) seamPixels++
            }
            assertTrue("Only the seam may blend, got $seamPixels pixels", seamPixels <= 2)
            assertEquals(0, pops)
        } finally {
            compose.mainClock.autoAdvance = true
            compose.runOnIdle { dispatcher.dispatchOnBackCancelled() }
        }
    }

    @Test
    fun commitUpdatesStackImmediatelyAndRevealsThePreviousPage() {
        start()
        compose.mainClock.autoAdvance = false
        try {
            dragTo(0.5f)
            compose.runOnIdle {
                dispatcher.onBackPressed()
                assertEquals("No delayed pop or second commit animation", 1, pops)
            }
            advanceFrames(30)
            val bitmap = frame()
            assertColor(background, bitmap, bitmap.width / 2, bitmap.height / 2)
            assertColor(background, bitmap, 2, 2)
            assertColor(background, bitmap, bitmap.width - 3, bitmap.height - 3)
            assertEquals(1, pops)
        } finally {
            compose.mainClock.autoAdvance = true
        }
    }

    @Test
    fun cancellationRestoresAnOpaquePageWithoutResidualTransforms() {
        start()
        compose.mainClock.autoAdvance = false
        try {
            dragTo(0.6f)
            compose.runOnIdle { dispatcher.dispatchOnBackCancelled() }
            advanceFrames(30)
            val bitmap = frame()
            assertColor(foreground, bitmap, 2, 2)
            assertColor(foreground, bitmap, bitmap.width / 2, bitmap.height / 2)
            assertColor(foreground, bitmap, bitmap.width - 3, bitmap.height - 3)
            assertEquals(0, pops)
        } finally {
            compose.mainClock.autoAdvance = true
        }
    }

    private fun start() {
        compose.setContent {
            dispatcher = LocalOnBackPressedDispatcherOwner.current!!.onBackPressedDispatcher
            NocturnTheme {
                ScreenNavigationHost(navigation, true, {
                    pops++
                    navigation = navigation.copy(stack = navigation.stack.dropLast(1))
                }) { screen ->
                    Box(Modifier.fillMaxSize().background(if (screen == Screen.Home) background else foreground))
                }
            }
        }
        compose.waitForIdle()
    }

    private fun frame(): Bitmap = compose.onRoot().captureToImage().asAndroidBitmap()

    private fun assertColor(expected: Color, bitmap: Bitmap, x: Int, y: Int) {
        val actual = Color(bitmap.getPixel(x, y))
        assertEquals("Red at ($x,$y)", expected.red, actual.red, 0.02f)
        assertEquals("Green at ($x,$y)", expected.green, actual.green, 0.02f)
        assertEquals("Blue at ($x,$y)", expected.blue, actual.blue, 0.02f)
    }

    private fun dragTo(target: Float) {
        compose.runOnIdle {
            dispatcher.dispatchOnBackStarted(BackEventCompat(0f, 100f, 0f, BackEventCompat.EDGE_LEFT))
            dispatcher.dispatchOnBackProgressed(BackEventCompat(0f, 100f, 0f, BackEventCompat.EDGE_LEFT))
        }
        advanceFrames(1)
        var progress = 0f
        while (progress < target - 1e-4f) {
            progress = minOf(progress + 0.02f, target)
            compose.runOnIdle {
                dispatcher.dispatchOnBackProgressed(BackEventCompat(0f, 100f, progress, BackEventCompat.EDGE_LEFT))
            }
            advanceFrames(1)
        }
        advanceFrames(3)
    }

    private fun advanceFrames(count: Int) {
        repeat(count) {
            compose.mainClock.advanceTimeByFrame()
            compose.runOnIdle { }
        }
    }
}
