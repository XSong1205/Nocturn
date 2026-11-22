package dev.nocturn.app.ui

import android.graphics.Bitmap
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.requiredSize
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.mutableStateOf
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.asAndroidBitmap
import androidx.compose.ui.graphics.toArgb
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.test.captureToImage
import androidx.compose.ui.test.junit4.v2.createComposeRule
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onNodeWithContentDescription
import androidx.compose.ui.test.onRoot
import androidx.compose.ui.test.performClick
import androidx.compose.ui.unit.dp
import coil3.ImageLoader
import coil3.SingletonImageLoader
import coil3.annotation.DelicateCoilApi
import coil3.asImage
import coil3.intercept.Interceptor
import coil3.request.SuccessResult
import dev.nocturn.app.data.model.ColorSchemeKey
import dev.nocturn.app.data.model.DynamicVariant
import dev.nocturn.app.data.model.ThemeMode
import dev.nocturn.app.ui.adaptive.LocalWindowWidthClass
import dev.nocturn.app.ui.adaptive.WindowWidthClass
import dev.nocturn.app.ui.components.PlayerArtworkBackground
import dev.nocturn.app.ui.preview.PreviewData
import dev.nocturn.app.ui.screens.home.HomeScreen
import dev.nocturn.app.ui.screens.player.PlayerScreen
import dev.nocturn.app.ui.theme.NocturnTheme
import dev.nocturn.app.ui.theme.derivePlayerBackgroundColor
import java.io.File
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotEquals
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.RuntimeEnvironment
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode

/** Captures the production composable, including the actual theme settings and color animation. */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [36], qualifiers = "w1800dp-h1100dp-mdpi")
@GraphicsMode(GraphicsMode.Mode.NATIVE)
class PlayerMonetRenderingTest {
    @get:Rule
    val compose = createComposeRule()

    private val current = mutableStateOf(RenderCase())

    @Test
    fun bothThemesPaintOneOpaqueColorAcrossPhoneTabletAndWideWindows() {
        setContent()
        for (dark in listOf(true, false)) {
            for ((width, height) in listOf(412 to 915, 1024 to 768, 1600 to 600)) {
                val case = RenderCase(width = width, height = height, dark = dark)
                compose.runOnIdle { current.value = case }
                val bitmap = captureSolidBackground(case)
                try {
                    val file = File("build/screenshots/player-solid-${if (dark) "dark" else "light"}-${width}x$height.png")
                    file.parentFile?.mkdirs()
                    file.outputStream().use { bitmap.compress(Bitmap.CompressFormat.PNG, 100, it) }
                } finally {
                    bitmap.recycle()
                }
            }
        }
    }

    @Test
    fun backgroundRespondsToArtworkAndLiveAppearanceSettings() {
        setContent()
        val cases = listOf(
            RenderCase(),
            RenderCase(seed = Color.Blue),
            RenderCase(seed = Color.Blue, key = ColorSchemeKey.M3Dynamic),
            RenderCase(seed = Color.Blue, variant = DynamicVariant.Monochrome),
            RenderCase(seed = Color.Blue, dark = false),
        )
        var previous: Int? = null
        for (case in cases) {
            compose.runOnIdle { current.value = case }
            val bitmap = captureSolidBackground(case)
            try {
                val color = bitmap.getPixel(0, 0)
                if (previous != null) assertNotEquals("appearance change $case", previous, color)
                previous = color
            } finally {
                bitmap.recycle()
            }
        }
    }

    @Test
    @Config(qualifiers = "w1024dp-h768dp-mdpi")
    @OptIn(DelicateCoilApi::class)
    fun switchingArtworkInThePlayerLeavesTheHomeFrameIdenticalAfterCollapse() {
        val red = PreviewData.songs[0].copy(coverUrl = "https://artwork.test/red")
        val blue = PreviewData.songs[1].copy(coverUrl = "https://artwork.test/blue")
        val visible = mutableStateOf(false)
        val song = mutableStateOf(red)
        val scheme = ColorSchemeKey.M3eDynamic
        val variant = DynamicVariant.Expressive
        val loader = ImageLoader.Builder(RuntimeEnvironment.getApplication())
            .memoryCache(null)
            .diskCache(null)
            .components {
                add(Interceptor { chain ->
                    val seed = if (chain.request.data.toString().contains("/red")) Color.Red else Color.Blue
                    val bitmap = Bitmap.createBitmap(32, 32, Bitmap.Config.ARGB_8888)
                    bitmap.eraseColor(seed.toArgb())
                    SuccessResult(image = bitmap.asImage(), request = chain.request)
                })
            }
            .build()
        SingletonImageLoader.setUnsafe(loader)
        try {
            compose.setContent {
                NocturnTheme(themeMode = ThemeMode.Dark, colorSchemeKey = scheme, dynamicVariant = variant) {
                    CompositionLocalProvider(LocalWindowWidthClass provides WindowWidthClass.Expanded) {
                        val sheet = rememberPlayerSheetState()
                        Box(Modifier.fillMaxSize().background(MaterialTheme.colorScheme.surfaceContainerLowest)) {
                            PlayerNavigationHost(
                                visible = visible.value,
                                onCollapse = { visible.value = false },
                                state = sheet,
                                shell = { HomeScreen(state = PreviewData.homeState) },
                                player = {
                                    PlayerScreen(
                                        state = PreviewData.playerState.copy(song = song.value, playing = false),
                                        onCollapse = { visible.value = false },
                                        onNext = { song.value = blue },
                                        sheetDrag = sheet,
                                        artworkColorSchemeKey = scheme,
                                        artworkDynamicVariant = variant,
                                    )
                                },
                            )
                        }
                    }
                }
            }
            compose.onNodeWithTag("home-greeting").assertExists()
            val before = captureFrame()
            try {
                compose.runOnIdle { visible.value = true }
                awaitPlayerColor(derivePlayerBackgroundColor(Color.Red, true, scheme, variant))
                compose.onNodeWithContentDescription("下一首").performClick()
                awaitPlayerColor(derivePlayerBackgroundColor(Color.Blue, true, scheme, variant))
                compose.onNodeWithTag("player-collapse").performClick()
                compose.onNodeWithTag("player-collapse").assertDoesNotExist()
                val after = captureFrame()
                try {
                    assertTrue("The Home frame changed after closing the player", before.sameAs(after))
                } finally {
                    after.recycle()
                }
            } finally {
                before.recycle()
            }
        } finally {
            SingletonImageLoader.reset()
            loader.shutdown()
        }
    }

    private fun captureFrame(): Bitmap = compose.onRoot().captureToImage().asAndroidBitmap()

    private fun awaitPlayerColor(expected: Color) {
        val sampled = mutableSetOf<Int>()
        try {
            compose.waitUntil(timeoutMillis = 5_000) {
                compose.waitForIdle()
                val frame = captureFrame()
                try {
                    // The player's controls are inset from the edge in both panes.
                    val pixel = frame.getPixel(1, frame.height / 2)
                    sampled += pixel
                    pixel == expected.toArgb()
                } finally {
                    frame.recycle()
                }
            }
        } catch (failure: androidx.compose.ui.test.ComposeTimeoutException) {
            throw AssertionError("Expected $expected on the player's edge, saw ${sampled.map { Color(it) }}", failure)
        }
    }

    private fun setContent() {
        compose.setContent {
            val case = current.value
            NocturnTheme(
                themeMode = if (case.dark) ThemeMode.Dark else ThemeMode.Light,
                colorSchemeKey = case.key,
                dynamicVariant = case.variant,
            ) {
                PlayerArtworkBackground(
                    seedColor = case.seed,
                    modifier = Modifier.requiredSize(case.width.dp, case.height.dp).testTag("artwork-background"),
                    colorSchemeKey = case.key,
                    variant = case.variant,
                )
            }
        }
    }

    private fun captureSolidBackground(case: RenderCase): Bitmap {
        compose.waitForIdle()
        val bitmap = compose.onNodeWithTag("artwork-background").captureToImage().asAndroidBitmap()
        assertEquals(case.width, bitmap.width)
        assertEquals(case.height, bitmap.height)
        val expected = derivePlayerBackgroundColor(case.seed, case.dark, case.key, case.variant).toArgb()
        val pixels = IntArray(bitmap.width * bitmap.height)
        bitmap.getPixels(pixels, 0, bitmap.width, 0, 0, bitmap.width, bitmap.height)
        assertEquals("opaque $case", 255, expected ushr 24)
        assertTrue("uniform semantic surface $case", pixels.all { it == expected })
        return bitmap
    }

    private data class RenderCase(
        val width: Int = 412,
        val height: Int = 915,
        val dark: Boolean = true,
        val seed: Color = Color.Red,
        val key: ColorSchemeKey = ColorSchemeKey.M3eDynamic,
        val variant: DynamicVariant = DynamicVariant.Expressive,
    )
}
