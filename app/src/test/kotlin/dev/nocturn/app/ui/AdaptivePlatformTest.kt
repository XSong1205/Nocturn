package dev.nocturn.app.ui

import androidx.compose.material3.Text
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.unit.dp
import dev.nocturn.app.ui.adaptive.AppPlatform
import dev.nocturn.app.ui.adaptive.LocalAppPlatform
import dev.nocturn.app.ui.adaptive.LocalDesktopUiEnabled
import dev.nocturn.app.ui.adaptive.LocalWindowWidthClass
import dev.nocturn.app.ui.adaptive.WindowWidthClass
import dev.nocturn.app.ui.adaptive.isDesktopUi
import dev.nocturn.app.ui.adaptive.shouldUseDesktopUi
import dev.nocturn.app.ui.adaptive.windowWidthClassOf
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [36])
class AdaptivePlatformTest {

    @get:Rule
    val compose = createComposeRule()

    @Test
    fun windowsPlatformForcesDesktopUiAcrossAllWidthClasses() {
        assertTrue(shouldUseDesktopUi(AppPlatform.Windows, WindowWidthClass.Compact))
        assertTrue(shouldUseDesktopUi(AppPlatform.Windows, WindowWidthClass.Medium))
        assertTrue(shouldUseDesktopUi(AppPlatform.Windows, WindowWidthClass.Expanded))
    }

    @Test
    fun androidPlatformForcesDisableDesktopUiEvenOnHighResolutionTablets() {
        // High resolution tablets (>= 840dp) must not be misjudged as desktop
        assertFalse(shouldUseDesktopUi(AppPlatform.Android, WindowWidthClass.Compact))
        assertFalse(shouldUseDesktopUi(AppPlatform.Android, WindowWidthClass.Medium))
        assertFalse(shouldUseDesktopUi(AppPlatform.Android, WindowWidthClass.Expanded))
    }

    @Test
    fun otherPlatformUsesExpandedBreakpoint() {
        assertFalse(shouldUseDesktopUi(AppPlatform.Other, WindowWidthClass.Compact))
        assertFalse(shouldUseDesktopUi(AppPlatform.Other, WindowWidthClass.Medium))
        assertTrue(shouldUseDesktopUi(AppPlatform.Other, WindowWidthClass.Expanded))
    }

    @Test
    fun platformPropertiesAreCorrect() {
        assertTrue(AppPlatform.Windows.isWindows)
        assertTrue(AppPlatform.Windows.isDesktop)
        assertFalse(AppPlatform.Windows.isMobile)
        assertFalse(AppPlatform.Windows.isAndroid)

        assertTrue(AppPlatform.Android.isAndroid)
        assertTrue(AppPlatform.Android.isMobile)
        assertFalse(AppPlatform.Android.isDesktop)
        assertFalse(AppPlatform.Android.isWindows)

        assertTrue(AppPlatform.Other.isDesktop)
        assertFalse(AppPlatform.Other.isMobile)
        assertFalse(AppPlatform.Other.isWindows)
        assertFalse(AppPlatform.Other.isAndroid)
    }

    @Test
    fun windowWidthClassCalculationRemainsAccurate() {
        assertEquals(WindowWidthClass.Compact, windowWidthClassOf(360.dp))
        assertEquals(WindowWidthClass.Compact, windowWidthClassOf(599.dp))
        assertEquals(WindowWidthClass.Medium, windowWidthClassOf(600.dp))
        assertEquals(WindowWidthClass.Medium, windowWidthClassOf(839.dp))
        assertEquals(WindowWidthClass.Expanded, windowWidthClassOf(840.dp))
        assertEquals(WindowWidthClass.Expanded, windowWidthClassOf(1280.dp))
    }

    @Test
    fun isDesktopUiComposableRespectsPlatformAndExplicitOverride() {
        var observedDesktopUi: Boolean? = null
        var platform by androidx.compose.runtime.mutableStateOf(AppPlatform.Android)
        var widthClass by androidx.compose.runtime.mutableStateOf(WindowWidthClass.Expanded)
        var desktopOverride by androidx.compose.runtime.mutableStateOf<Boolean?>(null)

        compose.setContent {
            CompositionLocalProvider(
                LocalAppPlatform provides platform,
                LocalWindowWidthClass provides widthClass,
                LocalDesktopUiEnabled provides desktopOverride,
            ) {
                observedDesktopUi = isDesktopUi()
                Text("Content")
            }
        }
        // Android + Expanded (tablet) -> false
        assertEquals(false, observedDesktopUi)

        // Windows platform: forces true even on compact
        platform = AppPlatform.Windows
        widthClass = WindowWidthClass.Compact
        compose.waitForIdle()
        assertEquals(true, observedDesktopUi)

        // Explicit override overrides platform decision
        desktopOverride = false
        compose.waitForIdle()
        assertEquals(false, observedDesktopUi)

        platform = AppPlatform.Android
        desktopOverride = true
        compose.waitForIdle()
        assertEquals(true, observedDesktopUi)
    }
}
