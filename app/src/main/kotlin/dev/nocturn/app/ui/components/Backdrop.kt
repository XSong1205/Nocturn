package dev.nocturn.app.ui.components

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxScope
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material3.ColorScheme
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.drawscope.DrawScope
import dev.nocturn.app.ui.theme.LocalIsDarkTheme

/**
 * Ambient background: two soft brand glows over the theme surface. Keeps the expressive
 * surfaces from feeling flat without painting images or gradients behind every card.
 */
@Composable
fun NocturnBackdrop(
    modifier: Modifier = Modifier,
    content: @Composable BoxScope.() -> Unit,
) {
    val dark = LocalIsDarkTheme.current
    val colors = MaterialTheme.colorScheme
    Box(modifier.fillMaxSize()) {
        Canvas(Modifier.fillMaxSize()) { drawNocturnBackdrop(colors, dark) }
        content()
    }
}

/**
 * Paints the ambient backdrop. Shared with blur captures, which repaint it under the recorded page
 * so frosted bars sample the same glow the page sits on instead of transparent pixels.
 */
fun DrawScope.drawNocturnBackdrop(colors: ColorScheme, dark: Boolean) {
    val glowPrimary = colors.primary.copy(alpha = if (dark) 0.16f else 0.10f)
    val glowSecondary = colors.secondary.copy(alpha = if (dark) 0.10f else 0.08f)
    val glowTertiary = colors.tertiary.copy(alpha = if (dark) 0.08f else 0.06f)
    drawRect(
        Brush.verticalGradient(
            listOf(colors.background, colors.surfaceContainerLowest),
        ),
    )
    drawCircle(
        brush = Brush.radialGradient(
            listOf(glowPrimary, Color.Transparent),
            center = Offset(size.width * 0.92f, size.height * 0.04f),
            radius = size.width * 0.85f,
        ),
        radius = size.width * 0.85f,
        center = Offset(size.width * 0.92f, size.height * 0.04f),
    )
    drawCircle(
        brush = Brush.radialGradient(
            listOf(glowSecondary, Color.Transparent),
            center = Offset(size.width * 0.06f, size.height * 0.55f),
            radius = size.width * 0.7f,
        ),
        radius = size.width * 0.7f,
        center = Offset(size.width * 0.06f, size.height * 0.55f),
    )
    drawCircle(
        brush = Brush.radialGradient(
            listOf(glowTertiary, Color.Transparent),
            center = Offset(size.width * 0.8f, size.height * 0.92f),
            radius = size.width * 0.75f,
        ),
        radius = size.width * 0.75f,
        center = Offset(size.width * 0.8f, size.height * 0.92f),
    )
}
