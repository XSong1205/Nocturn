package dev.nocturn.app.ui.components

import android.content.Context
import android.graphics.Bitmap
import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.Composable
import androidx.compose.runtime.Immutable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import coil3.BitmapImage
import coil3.imageLoader
import coil3.request.ImageRequest
import dev.nocturn.app.data.model.Song
import dev.nocturn.app.ui.theme.LocalIsDarkTheme
import dev.nocturn.app.ui.util.artworkSeed
import dev.nocturn.app.ui.util.resolvedImageUrl
import kotlin.math.abs
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

/**
 * Harmonized Material 3 Monet palette generated from a seed color.
 */
@Immutable
data class PlayerMonetColors(
    val primaryGlow: Color,
    val secondaryGlow: Color,
    val tertiaryGlow: Color,
    val surfaceTop: Color,
    val surfaceBottom: Color,
)

/** Curated seed palette for deterministic offline / fallback color generation. */
private val DefaultMonetSeeds = listOf(
    Color(0xFFE55934), // Coral Red
    Color(0xFF3B72D6), // Deep Sky Blue
    Color(0xFF9345D8), // Twilight Purple
    Color(0xFF1B8A5A), // Emerald Green
    Color(0xFFE67E22), // Amber Orange
    Color(0xFF00828A), // Cyan Lagoon
    Color(0xFFD83A56), // Rose Crimson
    Color(0xFF3A6073), // Ink Slate
    Color(0xFF6C5CE7), // Radiant Violet
    Color(0xFF009688), // Mint Teal
    Color(0xFFC0392B), // Ruby Garnet
    Color(0xFF2980B9), // Aegean Blue
)

/**
 * Derives a full ambient Monet palette from a given seed color.
 */
fun deriveMonetColors(seedColor: Color, dark: Boolean): PlayerMonetColors {
    val hsv = FloatArray(3)
    android.graphics.Color.colorToHSV(
        android.graphics.Color.argb(
            (seedColor.alpha * 255).toInt(),
            (seedColor.red * 255).toInt(),
            (seedColor.green * 255).toInt(),
            (seedColor.blue * 255).toInt(),
        ),
        hsv,
    )
    val h = hsv[0]
    val s = hsv[1].coerceIn(0.40f, 0.85f)

    if (dark) {
        // Night stage: deep aubergine-tinted background with glowing ambient mesh
        val primary = Color.hsv(h, s, 0.55f).copy(alpha = 0.28f)
        val secondary = Color.hsv((h + 35f) % 360f, (s * 0.9f).coerceIn(0.35f, 0.80f), 0.48f).copy(alpha = 0.22f)
        val tertiary = Color.hsv((h - 45f + 360f) % 360f, (s * 0.8f).coerceIn(0.30f, 0.75f), 0.42f).copy(alpha = 0.18f)

        val surfaceTop = Color.hsv(h, 0.16f, 0.10f)
        val surfaceBottom = Color.hsv(h, 0.12f, 0.05f)

        return PlayerMonetColors(
            primaryGlow = primary,
            secondaryGlow = secondary,
            tertiaryGlow = tertiary,
            surfaceTop = surfaceTop,
            surfaceBottom = surfaceBottom,
        )
    } else {
        // Light stage: soft porcelain surface with delicate pastels
        val primary = Color.hsv(h, (s * 0.7f).coerceIn(0.25f, 0.55f), 0.75f).copy(alpha = 0.22f)
        val secondary = Color.hsv((h + 30f) % 360f, (s * 0.6f).coerceIn(0.20f, 0.50f), 0.78f).copy(alpha = 0.18f)
        val tertiary = Color.hsv((h - 40f + 360f) % 360f, (s * 0.55f).coerceIn(0.20f, 0.45f), 0.82f).copy(alpha = 0.14f)

        val surfaceTop = Color.hsv(h, 0.06f, 0.98f)
        val surfaceBottom = Color.hsv(h, 0.08f, 0.93f)

        return PlayerMonetColors(
            primaryGlow = primary,
            secondaryGlow = secondary,
            tertiaryGlow = tertiary,
            surfaceTop = surfaceTop,
            surfaceBottom = surfaceBottom,
        )
    }
}

/**
 * Extracts the most representative dominant seed color from a downsampled Bitmap.
 */
internal fun extractDominantSeedColor(bitmap: Bitmap): Color {
    val width = bitmap.width
    val height = bitmap.height
    val pixels = IntArray(width * height)
    bitmap.getPixels(pixels, 0, width, 0, 0, width, height)

    val buckets = FloatArray(16)
    val bucketCounts = IntArray(16)
    val hsv = FloatArray(3)

    for (pixel in pixels) {
        val a = android.graphics.Color.alpha(pixel)
        if (a < 128) continue
        android.graphics.Color.colorToHSV(pixel, hsv)
        val s = hsv[1]
        val v = hsv[2]

        // Ignore muddy blacks, washed out whites, and desaturated greys
        if (v < 0.14f || v > 0.94f || s < 0.16f) continue

        val bucket = ((hsv[0] / 360f) * 16).toInt().coerceIn(0, 15)
        // Give higher weight to rich, well-saturated mid-tones
        val weight = s * (1f - abs(v - 0.55f) * 1.1f).coerceAtLeast(0.1f)
        buckets[bucket] += weight
        bucketCounts[bucket]++
    }

    var bestBucket = -1
    var maxWeight = -1f
    for (i in 0 until 16) {
        if (buckets[i] > maxWeight) {
            maxWeight = buckets[i]
            bestBucket = i
        }
    }

    return if (bestBucket != -1 && bucketCounts[bestBucket] > 0) {
        val hue = (bestBucket + 0.5f) * (360f / 16f)
        Color.hsv(hue, 0.72f, 0.62f)
    } else {
        // Fallback for monochrome or washed covers
        Color(0xFF5B6E85)
    }
}

/**
 * Loads the artwork thumbnail and extracts its dominant Monet seed color asynchronously.
 */
private suspend fun fetchCoverSeedColor(context: Context, imageUrl: String?): Color? {
    val resolved = resolvedImageUrl(imageUrl, widthPx = 96) ?: return null
    return withContext(Dispatchers.IO) {
        runCatching {
            val request = ImageRequest.Builder(context)
                .data(resolved)
                .size(64, 64)
                .build()
            val result = context.imageLoader.execute(request)
            val bitmap = (result.image as? BitmapImage)?.bitmap
            if (bitmap != null) {
                extractDominantSeedColor(bitmap)
            } else {
                null
            }
        }.getOrNull()
    }
}

/**
 * Ambient, dynamic Monet backdrop for the player sheet.
 *
 * Extracts dynamic color from the album artwork, derives a harmonized Material 3 Monet palette,
 * and renders an ambient radial mesh gradient with smooth crossfade transitions between tracks.
 */
@Composable
fun PlayerMonetBackdrop(
    song: Song?,
    modifier: Modifier = Modifier,
    dark: Boolean = LocalIsDarkTheme.current,
) {
    val context = LocalContext.current

    // 冷启动初始种子色：仅在播放器首次挂载且未提取到颜色时作为基础底色
    val initialSeedColor = remember {
        val seed = artworkSeed(song?.id, song?.name, song?.cover)
        DefaultMonetSeeds[abs(seed) % DefaultMonetSeeds.size]
    }

    // 活跃背景种子色：切歌时严格保持上一首歌的背景色，绝不跳变或闪烁中间色
    var activeSeedColor by remember { mutableStateOf(initialSeedColor) }

    LaunchedEffect(song?.cover) {
        val cover = song?.cover
        if (!cover.isNullOrBlank()) {
            val extracted = fetchCoverSeedColor(context, cover)
            if (extracted != null) {
                // 仅当新封面成功下载并解析出色相后，才平滑过渡到新颜色
                activeSeedColor = extracted
            }
            // 若网络未返回、加载中或加载失败，保持 activeSeedColor 不变，杜绝割裂
        }
    }

    val targetPalette = remember(activeSeedColor, dark) {
        deriveMonetColors(activeSeedColor, dark)
    }

    val animSpec = tween<Color>(durationMillis = 750, easing = FastOutSlowInEasing)
    val surfaceTop by animateColorAsState(targetPalette.surfaceTop, animSpec, label = "monet-surface-top")
    val surfaceBottom by animateColorAsState(targetPalette.surfaceBottom, animSpec, label = "monet-surface-bottom")
    val primaryGlow by animateColorAsState(targetPalette.primaryGlow, animSpec, label = "monet-primary-glow")
    val secondaryGlow by animateColorAsState(targetPalette.secondaryGlow, animSpec, label = "monet-secondary-glow")
    val tertiaryGlow by animateColorAsState(targetPalette.tertiaryGlow, animSpec, label = "monet-tertiary-glow")

    Box(modifier = modifier.fillMaxSize()) {
        Canvas(modifier = Modifier.fillMaxSize()) {
            val w = size.width
            val h = size.height

            // 1. Vertical tonal gradient foundation
            drawRect(
                brush = Brush.verticalGradient(
                    colors = listOf(surfaceTop, surfaceBottom),
                ),
            )

            if (android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.Q) {
                // 2. Primary ambient glow near top-right (behind cover stage)
                drawCircle(
                    brush = Brush.radialGradient(
                        colors = listOf(primaryGlow, Color.Transparent),
                        center = Offset(w * 0.90f, h * 0.16f),
                        radius = w * 0.95f,
                    ),
                    center = Offset(w * 0.90f, h * 0.16f),
                    radius = w * 0.95f,
                )

                // 3. Secondary ambient glow near mid-left
                drawCircle(
                    brush = Brush.radialGradient(
                        colors = listOf(secondaryGlow, Color.Transparent),
                        center = Offset(w * 0.08f, h * 0.58f),
                        radius = w * 0.85f,
                    ),
                    center = Offset(w * 0.08f, h * 0.58f),
                    radius = w * 0.85f,
                )

                // 4. Tertiary ambient glow near bottom-right (behind controls / lyrics footer)
                drawCircle(
                    brush = Brush.radialGradient(
                        colors = listOf(tertiaryGlow, Color.Transparent),
                        center = Offset(w * 0.82f, h * 0.88f),
                        radius = w * 0.75f,
                    ),
                    center = Offset(w * 0.82f, h * 0.88f),
                    radius = w * 0.75f,
                )

                // 5. Subtle vignette to guarantee foreground text & seek contrast
                drawRect(
                    brush = Brush.verticalGradient(
                        0.0f to Color.Transparent,
                        0.5f to Color.Transparent,
                        1.0f to Color.Black.copy(alpha = if (dark) 0.30f else 0.08f),
                    ),
                )
            }
        }
    }
}
