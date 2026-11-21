package dev.nocturn.app.ui.components

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxScope
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material3.ColorScheme
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.drawscope.DrawScope
import dev.nocturn.app.ui.theme.LocalIsDarkTheme

/**
 * Ambient background: clean solid Monet surface base without gradients or ambient glows.
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
 * Paints the solid Monet backdrop. Shared with blur captures, which repaint it under the recorded page
 * so frosted bars sample the clean theme surface the page sits on instead of transparent pixels.
 */
fun DrawScope.drawNocturnBackdrop(colors: ColorScheme, dark: Boolean) {
    drawRect(colors.surface)
}
