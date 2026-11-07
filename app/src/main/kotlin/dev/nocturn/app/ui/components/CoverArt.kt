package dev.nocturn.app.ui.components

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.Shape
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.drawscope.rotate
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import coil3.compose.AsyncImage
import coil3.request.ImageRequest
import coil3.request.crossfade
import dev.nocturn.app.ui.util.artworkSeed
import dev.nocturn.app.ui.util.resolvedImageUrl
import kotlin.math.abs
import kotlin.math.min

/**
 * Cover artwork that always renders something on-brand: a deterministic expressive gradient is
 * painted first and the remote image fades in above it once loaded. That keeps offline states,
 * loading states and Robolectric screenshots free of blank placeholders.
 */
@Composable
fun CoverArt(
    imageUrl: String?,
    modifier: Modifier = Modifier,
    shape: Shape = androidx.compose.material3.MaterialTheme.shapes.medium,
    seed: Int = artworkSeed(imageUrl),
    contentDescription: String? = null,
) {
    val context = LocalContext.current
    val resolved = remember(imageUrl) { resolvedImageUrl(imageUrl, widthPx = 480) }
    val request = remember(resolved, context) {
        resolved?.let {
            ImageRequest.Builder(context).data(it).crossfade(220).build()
        }
    }
    Box(
        modifier = modifier
            .clip(shape)
            .semantics {
                if (contentDescription != null) this.contentDescription = contentDescription
            },
    ) {
        GeneratedCover(seed = seed, modifier = Modifier.fillMaxSize())
        if (request != null) {
            AsyncImage(
                model = request,
                contentDescription = null,
                contentScale = ContentScale.Crop,
                modifier = Modifier.fillMaxSize(),
            )
        }
    }
}

/** Original, resolution-independent cover illustration used when no artwork is available. */
@Composable
fun GeneratedCover(seed: Int, modifier: Modifier = Modifier) {
    val variant = abs(seed) % 6
    val palette = remember(seed) { coverPalette(seed) }
    Canvas(modifier) {
        val w = size.width
        val h = size.height
        drawRect(Brush.linearGradient(palette, start = Offset(0f, 0f), end = Offset(w, h)))
        when (variant) {
            0 -> {
                drawCircle(
                    color = Color.White.copy(alpha = 0.22f),
                    radius = min(w, h) * 0.34f,
                    center = Offset(w * 0.72f, h * 0.3f),
                )
                val hill = Path().apply {
                    moveTo(0f, h * 0.72f)
                    cubicTo(w * 0.28f, h * 0.5f, w * 0.55f, h * 0.94f, w, h * 0.62f)
                    lineTo(w, h)
                    lineTo(0f, h)
                    close()
                }
                drawPath(hill, Color.Black.copy(alpha = 0.22f))
                drawPath(
                    Path().apply {
                        moveTo(0f, h * 0.86f)
                        cubicTo(w * 0.3f, h * 0.66f, w * 0.62f, h * 1.02f, w, h * 0.78f)
                        lineTo(w, h)
                        lineTo(0f, h)
                        close()
                    },
                    Color.Black.copy(alpha = 0.26f),
                )
                drawCircle(Color.White.copy(alpha = 0.85f), w * 0.035f, Offset(w * 0.18f, h * 0.2f))
            }
            1 -> {
                val center = Offset(w * 0.5f, h * 0.5f)
                val radius = min(w, h) * 0.33f
                drawCircle(Color.Black.copy(alpha = 0.3f), radius * 1.16f, center)
                drawCircle(Color.White.copy(alpha = 0.5f), radius, center, style = Stroke(w * 0.012f))
                repeat(4) { ring ->
                    drawCircle(
                        Color.White.copy(alpha = 0.22f - ring * 0.04f),
                        radius * (0.82f - ring * 0.16f),
                        center,
                        style = Stroke(w * 0.006f),
                    )
                }
                drawCircle(palette.first().copy(alpha = 0.9f), radius * 0.28f, center)
                drawCircle(Color.White.copy(alpha = 0.95f), radius * 0.1f, center)
            }
            2 -> {
                repeat(3) { layer ->
                    val inset = w * (0.16f + layer * 0.1f)
                    drawRect(
                        color = Color.White.copy(alpha = 0.14f + layer * 0.06f),
                        topLeft = Offset(inset, h * 0.24f),
                        size = Size(w - inset * 2, h * 0.58f),
                    )
                }
                drawCircle(Color.White.copy(alpha = 0.5f), w * 0.06f, Offset(w * 0.8f, h * 0.2f))
            }
            3 -> {
                rotate(18f, Offset(w * 0.5f, h * 0.5f)) {
                    repeat(5) { row ->
                        repeat(5) { column ->
                            drawCircle(
                                color = Color.White.copy(alpha = 0.16f + ((row + column) % 3) * 0.12f),
                                radius = w * 0.035f,
                                center = Offset(w * (0.16f + column * 0.17f), h * (0.2f + row * 0.16f)),
                            )
                        }
                    }
                }
            }
            4 -> {
                val wave = Path().apply {
                    moveTo(0f, h * 0.58f)
                    cubicTo(w * 0.25f, h * 0.2f, w * 0.5f, h * 0.96f, w, h * 0.42f)
                }
                drawPath(wave, Color.White.copy(alpha = 0.35f), style = Stroke(w * 0.03f))
                drawPath(wave, Color.Black.copy(alpha = 0.18f), style = Stroke(w * 0.012f))
                drawCircle(Color.White.copy(alpha = 0.25f), w * 0.24f, Offset(w * 0.1f, h * 0.18f))
            }
            else -> {
                val barWidth = w * 0.12f
                repeat(6) { index ->
                    val barHeight = h * (0.18f + ((abs(seed / 7) + index * 37) % 60) / 100f)
                    drawRoundRect(
                        color = Color.White.copy(alpha = 0.12f + (index % 3) * 0.08f),
                        topLeft = Offset(w * (0.12f + index * 0.13f), (h - barHeight) * 0.72f),
                        size = Size(barWidth, barHeight),
                        cornerRadius = androidx.compose.ui.geometry.CornerRadius(barWidth / 2),
                    )
                }
                drawCircle(Color.White.copy(alpha = 0.28f), w * 0.2f, Offset(w * 0.7f, h * 0.22f))
            }
        }
    }
}

private fun coverPalette(seed: Int): List<Color> {
    val palettes = listOf(
        listOf(Color(0xFFFF6B4A), Color(0xFF8E2DE2)),
        listOf(Color(0xFF00C2A8), Color(0xFF2C3E91)),
        listOf(Color(0xFFFFD166), Color(0xFFEF476F)),
        listOf(Color(0xFF3A86FF), Color(0xFF00E5FF)),
        listOf(Color(0xFFB6F09C), Color(0xFF386641)),
        listOf(Color(0xFFFF9E00), Color(0xFF992600)),
        listOf(Color(0xFF9D4EDD), Color(0xFF240046)),
        listOf(Color(0xFF00B4D8), Color(0xFF03045E)),
    )
    val base = palettes[abs(seed) % palettes.size]
    return if (abs(seed / 3) % 2 == 0) base else base.reversed()
}
