package dev.nocturn.app.ui.theme

import android.graphics.Bitmap
import android.os.Build
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.toArgb
import androidx.core.graphics.scale
import com.materialkolor.hct.Hct
import com.materialkolor.dynamiccolor.ColorSpec.SpecVersion
import com.materialkolor.dynamiccolor.DynamicScheme
import com.materialkolor.scheme.SchemeContent
import com.materialkolor.scheme.SchemeExpressive
import com.materialkolor.scheme.SchemeFruitSalad
import com.materialkolor.scheme.SchemeMonochrome
import com.materialkolor.scheme.SchemeTonalSpot
import com.materialkolor.scheme.SchemeVibrant
import com.materialkolor.score.Score
import com.materialkolor.utils.ColorUtils
import dev.nocturn.app.data.model.ColorSchemeKey
import dev.nocturn.app.data.model.DynamicVariant
import kotlin.math.roundToInt

/**
 * One tinted surface from an MCU scheme, using the artwork as its seed and the app's
 * selected generation style. Standard M3 defaults to Tonal Spot; M3E defaults to Expressive.
 */
internal fun derivePlayerBackgroundColor(
    seedColor: Color,
    dark: Boolean,
    colorSchemeKey: ColorSchemeKey = ColorSchemeKey.M3eDynamic,
    variant: DynamicVariant = DynamicVariant.Expressive,
): Color {
    val source = Hct.fromInt(seedColor.toArgb())
    val standard = colorSchemeKey == ColorSchemeKey.M3Dynamic || colorSchemeKey == ColorSchemeKey.M3Baseline
    val selected = when {
        source.chroma < 5.0 -> DynamicVariant.Monochrome
        standard && variant == DynamicVariant.Expressive -> DynamicVariant.TonalSpot
        else -> variant
    }
    val spec = if (standard) SpecVersion.SPEC_2021 else SpecVersion.SPEC_2025
    val scheme: DynamicScheme = when (selected) {
        DynamicVariant.Expressive -> SchemeExpressive(source, dark, 0.0, spec)
        DynamicVariant.Content -> SchemeContent(source, dark, 0.0, spec)
        DynamicVariant.TonalSpot -> SchemeTonalSpot(source, dark, 0.0, spec)
        DynamicVariant.Vibrant -> SchemeVibrant(source, dark, 0.0, spec)
        DynamicVariant.FruitSalad -> SchemeFruitSalad(source, dark, 0.0, spec)
        DynamicVariant.Monochrome -> SchemeMonochrome(source, dark, 0.0, spec)
    }
    if (selected == DynamicVariant.Monochrome) return Color(scheme.surface)

    // Add chroma to the scheme's neutral palette. Move slightly away from white and black so the
    // tint is visible in both themes, while keeping a surface tone for the player text.
    val palette = scheme.neutralPalette
    val tone = if (dark) 10.0 else 94.0
    val chroma = palette.chroma + minOf(8.0, source.chroma / 2)
    return Color(Hct.from(palette.hue, chroma, tone).toInt())
}

/**
 * Bounded Lab quantization followed by Monet's population/chroma/hue scoring. Bright artwork
 * remains eligible, and achromatic covers use their dominant neutral rather than a blue fallback.
 * The caller runs this CPU work away from the UI thread and retains ownership of [bitmap].
 */
internal fun extractDominantSeedColor(bitmap: Bitmap): Color? {
    val readable = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O && bitmap.config == Bitmap.Config.HARDWARE) {
        bitmap.copy(Bitmap.Config.ARGB_8888, false) ?: return null
    } else {
        bitmap
    }
    val scale = (128f / maxOf(readable.width, readable.height)).coerceAtMost(1f)
    val sampled = if (scale < 1f) {
        readable.scale(
            width = (readable.width * scale).roundToInt().coerceAtLeast(1),
            height = (readable.height * scale).roundToInt().coerceAtLeast(1),
            filter = true,
        )
    } else {
        readable
    }
    val pixels = try {
        IntArray(sampled.width * sampled.height).also {
            sampled.getPixels(it, 0, sampled.width, 0, 0, sampled.width, sampled.height)
        }
    } finally {
        if (sampled !== readable) sampled.recycle()
        if (readable !== bitmap) readable.recycle()
    }
    var count = 0
    for (pixel in pixels) {
        if (android.graphics.Color.alpha(pixel) >= 128) {
            pixels[count++] = pixel or (0xFF shl 24)
        }
    }
    if (count == 0) return null
    val populations = quantizeArtwork(pixels.copyOf(count))
    val dominant = populations.maxByOrNull { it.value }?.key ?: return null
    val seed = Score.score(populations, desired = 1, fallbackColorArgb = dominant).firstOrNull() ?: dominant
    return Color(seed)
}

private fun quantizeArtwork(pixels: IntArray): Map<Int, Int> {
    val histogram = linkedMapOf<Int, Int>()
    for (pixel in pixels) histogram[pixel] = (histogram[pixel] ?: 0) + 1
    val maxColors = 64
    // Exact small palettes need no quantization, especially a colored subject on a neutral cover.
    if (histogram.size <= maxColors) return histogram

    val colors = histogram.keys.toIntArray()
    val populations = histogram.values.toIntArray()
    val points = Array(colors.size) { ColorUtils.labFromArgb(colors[it]) }
    val distances = DoubleArray(colors.size) { Double.POSITIVE_INFINITY }
    val clusters = IntArray(maxColors)
    var next = populations.indices.maxBy { populations[it] }
    // Wu's Int-squared moments in MaterialKolor 6.0.0 overflow on ordinary 128px covers.
    // Seed MCU's weighted Lab k-means with deterministic, population-weighted farthest colors
    // instead. This retains both common shades and distinct artwork accents without averaging
    // the entire image into one color before Monet's scoring gets to see it.
    for (cluster in clusters.indices) {
        clusters[cluster] = colors[next]
        val center = points[next]
        var best = -1.0
        for (index in colors.indices) {
            val point = points[index]
            val l = point[0] - center[0]
            val a = point[1] - center[1]
            val b = point[2] - center[2]
            distances[index] = minOf(distances[index], l * l + a * a + b * b)
            val weight = distances[index] * populations[index]
            if (weight > best) {
                best = weight
                next = index
            }
        }
    }
    return ArtworkQuantizer.refine(pixels, clusters, maxColors)
}
