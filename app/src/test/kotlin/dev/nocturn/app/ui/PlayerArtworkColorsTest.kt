package dev.nocturn.app.ui

import android.graphics.Bitmap
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.luminance
import androidx.compose.ui.graphics.toArgb
import com.materialkolor.hct.Hct
import com.materialkolor.dynamiccolor.ColorSpec.SpecVersion
import com.materialkolor.scheme.SchemeExpressive
import com.materialkolor.scheme.SchemeTonalSpot
import dev.nocturn.app.data.model.ColorSchemeKey
import dev.nocturn.app.data.model.DynamicVariant
import dev.nocturn.app.ui.theme.M3BaselineDarkColors
import dev.nocturn.app.ui.theme.M3BaselineLightColors
import dev.nocturn.app.ui.theme.M3eDarkColors
import dev.nocturn.app.ui.theme.M3eLightColors
import dev.nocturn.app.ui.theme.NocturnDarkColors
import dev.nocturn.app.ui.theme.NocturnLightColors
import dev.nocturn.app.ui.theme.derivePlayerBackgroundColor
import dev.nocturn.app.ui.theme.extractDominantSeedColor
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNotEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [36])
@GraphicsMode(GraphicsMode.Mode.NATIVE)
class PlayerArtworkColorsTest {
    @Test
    fun brightSaturatedArtworkRetainsItsActualColor() {
        for (argb in listOf(0xFFFF0000.toInt(), 0xFF00FF00.toInt(), 0xFF0000FF.toInt())) {
            val bitmap = solidBitmap(argb)
            try {
                assertEquals(Color(argb), extractDominantSeedColor(bitmap))
            } finally {
                bitmap.recycle()
            }
        }
    }

    @Test
    fun neutralArtworkStaysNeutralAndTransparentArtworkHasNoSeed() {
        for (argb in listOf(0xFF000000.toInt(), 0xFF888888.toInt(), 0xFFFFFFFF.toInt())) {
            val bitmap = solidBitmap(argb)
            try {
                val seed = extractDominantSeedColor(bitmap)
                assertEquals(Color(argb), seed)
                val background = derivePlayerBackgroundColor(seed!!, dark = true)
                assertTrue(Hct.fromInt(background.toArgb()).chroma < 5)
            } finally {
                bitmap.recycle()
            }
        }
        val transparent = solidBitmap(android.graphics.Color.TRANSPARENT)
        try {
            assertNull(extractDominantSeedColor(transparent))
        } finally {
            transparent.recycle()
        }
    }

    @Test
    fun aColoredSubjectIsSelectedAgainstANeutralBorder() {
        val bitmap = solidBitmap(android.graphics.Color.WHITE)
        try {
            for (y in 8 until 24) {
                for (x in 8 until 24) bitmap.setPixel(x, y, android.graphics.Color.RED)
            }
            val seed = extractDominantSeedColor(bitmap)
            assertNotNull(seed)
            assertEquals(Color.Red, seed)
        } finally {
            bitmap.recycle()
        }
    }

    @Test
    fun complexArtworkRetainsItsColoredSubjectAfterQuantization() {
        val bitmap = Bitmap.createBitmap(128, 128, Bitmap.Config.ARGB_8888)
        try {
            for (y in 0 until bitmap.height) {
                for (x in 0 until bitmap.width) {
                    val color = if (x in 32 until 96 && y in 32 until 96) {
                        android.graphics.Color.rgb(192 + x % 64, y % 32, x % 16)
                    } else {
                        val grey = 128 + (x + y) % 128
                        android.graphics.Color.rgb(grey, grey, grey)
                    }
                    bitmap.setPixel(x, y, color)
                }
            }
            val seed = extractDominantSeedColor(bitmap)!!
            val hct = Hct.fromInt(seed.toArgb())
            assertTrue("red subject hue: ${hct.hue}", hct.hue in 10.0..40.0)
            assertTrue("red subject chroma: ${hct.chroma}", hct.chroma > 60.0)
            assertEquals(seed, extractDominantSeedColor(bitmap))
        } finally {
            bitmap.recycle()
        }
    }

    @Test
    fun aLargeNonSquareCoverCanBeSampledWithoutChangingItsDominantColor() {
        val bitmap = Bitmap.createBitmap(2048, 512, Bitmap.Config.ARGB_8888)
        bitmap.eraseColor(android.graphics.Color.BLUE)
        try {
            assertEquals(Color.Blue, extractDominantSeedColor(bitmap))
            assertTrue(!bitmap.isRecycled)
        } finally {
            bitmap.recycle()
        }
    }

    @Test
    fun solidBackgroundPreservesTextContrastAcrossHuesAndAppearanceStyles() {
        for (dark in listOf(true, false)) {
            val themes = if (dark) {
                listOf(NocturnDarkColors, M3BaselineDarkColors, M3eDarkColors)
            } else {
                listOf(NocturnLightColors, M3BaselineLightColors, M3eLightColors)
            }
            for (hue in 0 until 360 step 30) {
                val seed = Color(Hct.from(hue.toDouble(), 80.0, 50.0).toInt())
                for (key in listOf(ColorSchemeKey.M3eDynamic, ColorSchemeKey.M3Dynamic)) {
                    for (variant in DynamicVariant.entries) {
                        val background = derivePlayerBackgroundColor(seed, dark, key, variant)
                        assertEquals(1f, background.alpha)
                        for (theme in themes) {
                            val label = "dark=$dark hue=$hue key=$key variant=$variant"
                            assertTrue("onSurface $label", contrast(theme.onSurface, background) >= 4.5f)
                            assertTrue("onSurfaceVariant $label", contrast(theme.onSurfaceVariant, background) >= 4.5f)
                        }
                    }
                }
            }
        }
    }

    @Test
    fun artworkTintIsStrongerThanTheDefaultSurfaceAndFollowsTheSelectedScheme() {
        for (dark in listOf(true, false)) {
            for (seed in listOf(Color.Red, Color.Green, Color.Blue)) {
                val source = Hct.fromInt(seed.toArgb())
                val expressiveSurface = SchemeExpressive(source, dark, 0.0, SpecVersion.SPEC_2025).surface
                val standardSurface = SchemeTonalSpot(source, dark, 0.0, SpecVersion.SPEC_2021).surface
                val expressive = derivePlayerBackgroundColor(seed, dark, ColorSchemeKey.M3eDynamic)
                val standard = derivePlayerBackgroundColor(seed, dark, ColorSchemeKey.M3Dynamic)
                assertTrue(Hct.fromInt(expressive.toArgb()).chroma > Hct.fromInt(expressiveSurface).chroma + 3)
                assertTrue(Hct.fromInt(standard.toArgb()).chroma > Hct.fromInt(standardSurface).chroma + 3)
                assertNotEquals(expressive, standard)
            }
            val expressive = derivePlayerBackgroundColor(Color.Red, dark)
            assertNotEquals(expressive, derivePlayerBackgroundColor(Color.Blue, dark))
            val monochrome = derivePlayerBackgroundColor(Color.Red, dark, variant = DynamicVariant.Monochrome)
            assertTrue(Hct.fromInt(monochrome.toArgb()).chroma < 5)
        }
    }

    private fun solidBitmap(argb: Int): Bitmap = Bitmap.createBitmap(32, 32, Bitmap.Config.ARGB_8888).apply {
        eraseColor(argb)
    }

    private fun contrast(first: Color, second: Color): Float {
        val a = first.luminance()
        val b = second.luminance()
        return (maxOf(a, b) + 0.05f) / (minOf(a, b) + 0.05f)
    }
}
