package dev.nocturn.app.ui

import android.graphics.Bitmap
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.asAndroidBitmap
import androidx.compose.ui.test.captureToImage
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onRoot
import androidx.compose.ui.test.performClick
import dev.nocturn.app.data.model.ThemeMode
import dev.nocturn.app.ui.components.LocalShellBackdrop
import dev.nocturn.app.ui.components.NocturnBackdrop
import dev.nocturn.app.ui.components.blurAvailable
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
import top.yukonga.miuix.kmp.blur.layerBackdrop
import top.yukonga.miuix.kmp.blur.rememberLayerBackdrop

/**
 * Renders the shell's frosted chrome (bottom strip, glass mini player) over a real page with its own
 * frosted top bar, the way [NocturnApp] composes it, so the RuntimeShader blur path actually runs
 * on the host.
 * Writes `glass-chrome-light.png` / `glass-chrome-dark.png` to `app/build/screenshots/`.
 */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [36], qualifiers = "w412dp-h915dp-xhdpi")
@GraphicsMode(GraphicsMode.Mode.NATIVE)
class GlassChromeTest {

    @get:Rule
    val compose = createComposeRule()

    private fun setShell(dark: Boolean, onExpand: () -> Unit = {}) {
        compose.setContent {
            NocturnTheme(themeMode = if (dark) ThemeMode.Dark else ThemeMode.Light) {
                val backdrop = rememberLayerBackdrop()
                CompositionLocalProvider(LocalShellBackdrop provides backdrop) {
                    Box(Modifier.fillMaxSize()) {
                        NocturnBackdrop(Modifier.layerBackdrop(backdrop)) {
                            HomeScreen(state = PreviewData.homeState)
                        }
                        Box(Modifier.align(Alignment.BottomCenter)) {
                            BottomChrome(
                                miniPlayer = PreviewData.miniPlayerState,
                                showNavigationBar = true,
                                selectedTab = AppTab.Home,
                                onSelectTab = {},
                                onExpandPlayer = onExpand,
                                onTogglePlay = {},
                                onNext = {},
                                onPrevious = {},
                            )
                        }
                    }
                }
            }
        }
        compose.waitForIdle()
    }

    @Test
    fun blurMaterialsAreActiveOnModernAndroid() {
        assertTrue("API 36 supports RuntimeShader, so glass must not fall back", blurAvailable)
    }

    @Test
    fun frostedMiniPlayerStillOpensThePlayer() {
        var expanded = 0
        setShell(dark = false, onExpand = { expanded++ })

        compose.onNodeWithTag("mini-player").performClick()

        assertEquals(1, expanded)
    }

    @Test
    fun captureGlassChromeForReview() {
        setShell(dark = false)
        capture("glass-chrome-light.png")
    }

    @Test
    fun captureGlassChromeDarkForReview() {
        setShell(dark = true)
        capture("glass-chrome-dark.png")
    }

    private fun capture(name: String) {
        val dir = File("build/screenshots").apply { mkdirs() }
        val bitmap = compose.onRoot().captureToImage().asAndroidBitmap()
        File(dir, name).outputStream().use { bitmap.compress(Bitmap.CompressFormat.PNG, 100, it) }
    }
}
