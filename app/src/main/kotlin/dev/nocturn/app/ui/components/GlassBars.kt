package dev.nocturn.app.ui.components

import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.slideInVertically
import androidx.compose.animation.slideOutVertically
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.RowScope
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.statusBars
import androidx.compose.foundation.layout.windowInsetsPadding
import androidx.compose.foundation.lazy.LazyListState
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.ArrowBack
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LocalContentColor
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.State
import androidx.compose.runtime.derivedStateOf
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.RectangleShape
import androidx.compose.ui.layout.SubcomposeLayout
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Constraints
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import dev.nocturn.app.ui.theme.LocalIsDarkTheme
import top.yukonga.miuix.kmp.blur.Backdrop
import top.yukonga.miuix.kmp.blur.layerBackdrop
import top.yukonga.miuix.kmp.blur.rememberLayerBackdrop

/** The page layer a [GlassTopBar] frosts; provided by [GlassScaffold]. */
private val LocalBarBackdrop = staticCompositionLocalOf<Backdrop?> { null }

/**
 * A page whose content scrolls underneath a frosted [topBar].
 *
 * The content is recorded into a page-local blur layer (painted over the ambient backdrop, so the
 * glass samples the same glow the page sits on) and the bar, drawn outside that layer, blurs it
 * uniformly across its whole height. [content] receives the bar's measured height; scroll
 * containers pass it as top content padding, immersive pages may instead draw under it.
 */
@Composable
fun GlassScaffold(
    topBar: @Composable () -> Unit,
    modifier: Modifier = Modifier,
    content: @Composable (topPadding: Dp) -> Unit,
) {
    val colors = MaterialTheme.colorScheme
    val dark = LocalIsDarkTheme.current
    val backdrop = if (blurAvailable) {
        rememberLayerBackdrop {
            drawNocturnBackdrop(colors, dark)
            drawContent()
        }
    } else {
        null
    }
    val density = LocalDensity.current
    SubcomposeLayout(modifier.fillMaxSize()) { constraints ->
        val loose = constraints.copy(minWidth = 0, minHeight = 0)
        val bar = subcompose("bar") {
            CompositionLocalProvider(LocalBarBackdrop provides backdrop) {
                topBar()
            }
        }.map { it.measure(loose.copy(maxHeight = Constraints.Infinity)) }
        val barHeight = bar.maxOfOrNull { it.height } ?: 0
        val topPadding = with(density) { barHeight.toDp() }
        val body = subcompose("content") {
            Box(
                Modifier
                    .fillMaxSize()
                    .then(if (backdrop != null) Modifier.layerBackdrop(backdrop) else Modifier),
            ) {
                content(topPadding)
            }
        }.map { it.measure(constraints) }
        layout(constraints.maxWidth, constraints.maxHeight) {
            body.forEach { it.place(0, 0) }
            bar.forEach { it.place(0, 0) }
        }
    }
}

/**
 * Frosted bar for [GlassScaffold]: uniform backdrop blur with a tint and saturation lift across the
 * status bar and the bar row, plus a hairline that fades in once [scrolled]. Without blur
 * (API < 33, previews) it is an opaque surface.
 */
@Composable
fun GlassTopBar(
    modifier: Modifier = Modifier,
    scrolled: Boolean = true,
    windowInsets: WindowInsets = WindowInsets.statusBars,
    content: @Composable RowScope.() -> Unit,
) {
    val material = GlassMaterials.bar()
    val outline = MaterialTheme.colorScheme.outlineVariant
    val hairline by animateFloatAsState(
        targetValue = if (scrolled) 1f else 0f,
        animationSpec = MaterialTheme.motionScheme.fastEffectsSpec(),
        label = "glass-bar-hairline",
    )
    val contentColor = MaterialTheme.colorScheme.onSurface
    CompositionLocalProvider(LocalContentColor provides contentColor) {
        Row(
            modifier = modifier
                .fillMaxWidth()
                .glass(LocalBarBackdrop.current, RectangleShape, material)
                .drawBehind {
                    if (hairline > 0f) {
                        drawLine(
                            color = outline.copy(alpha = 0.6f * hairline),
                            start = Offset(0f, size.height - 0.5f),
                            end = Offset(size.width, size.height - 0.5f),
                            strokeWidth = 1f,
                        )
                    }
                }
                .windowInsetsPadding(windowInsets)
                .heightIn(min = 64.dp)
                .padding(horizontal = 4.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.Start,
            content = content,
        )
    }
}

/** Back arrow for detail bars. */
@Composable
fun BarBackButton(onBack: () -> Unit, testTag: String) {
    val haptics = rememberHaptics()
    IconButton(
        onClick = {
            haptics.tap()
            onBack()
        },
        modifier = Modifier.testTag(testTag),
    ) {
        Icon(Icons.AutoMirrored.Rounded.ArrowBack, contentDescription = "返回")
    }
}

/**
 * Compact bar title that slides in once the page's own large title has scrolled under the bar.
 * Hidden titles leave the composition, so they never duplicate the hero title for tests or
 * accessibility.
 */
@Composable
fun RowScope.BarTitle(text: String, visible: Boolean) {
    Box(
        modifier = Modifier
            .weight(1f)
            .padding(horizontal = 8.dp),
    ) {
        // Fully qualified: inside RowScope the bare name resolves to the RowScope overload.
        androidx.compose.animation.AnimatedVisibility(
            visible = visible,
            enter = fadeIn() + slideInVertically { it / 2 },
            exit = fadeOut() + slideOutVertically { it / 2 },
        ) {
            Text(
                text = text,
                style = MaterialTheme.typography.titleMediumEmphasized,
                color = MaterialTheme.colorScheme.onSurface,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
        }
    }
}

/** True once the list has scrolled past [threshold] px of its first item (or beyond it). */
@Composable
fun LazyListState.scrolledPast(threshold: Int): State<Boolean> = remember(this, threshold) {
    derivedStateOf { firstVisibleItemIndex > 0 || firstVisibleItemScrollOffset > threshold }
}
