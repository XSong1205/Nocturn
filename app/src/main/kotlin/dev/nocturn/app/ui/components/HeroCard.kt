package dev.nocturn.app.ui.components

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyListLayoutInfo
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.PlayArrow
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.RectangleShape
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp

/**
 * Portrait feature card for the home hero row: full-bleed artwork, a bottom scrim carrying a bold
 * title and one-line subtitle, an optional [badge] and play affordance.
 *
 * [parallax] is the card's offset from the row centre in `-1..1`; the artwork drifts against the
 * card as the row scrolls. It is read inside the layer block, so scrolling only re-draws.
 */
@Composable
fun HeroCard(
    title: String,
    subtitle: String,
    imageUrl: String?,
    seed: Int,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    onPlay: (() -> Unit)? = null,
    badge: String? = null,
    parallax: () -> Float = { 0f },
) {
    val haptics = rememberHaptics()
    val shape = MaterialTheme.shapes.medium
    Box(
        modifier = modifier
            .pressScale()
            .clip(shape)
            .clickable {
                haptics.tap()
                onClick()
            },
    ) {
        CoverArt(
            imageUrl = imageUrl,
            seed = seed,
            shape = RectangleShape,
            contentDescription = null,
            modifier = Modifier
                .fillMaxSize()
                .graphicsLayer {
                    scaleX = 1.16f
                    scaleY = 1.16f
                    translationX = -parallax() * size.width * 0.07f
                },
        )
        Box(
            Modifier
                .fillMaxSize()
                .background(
                    Brush.verticalGradient(
                        0.42f to Color.Transparent,
                        0.72f to Color.Black.copy(alpha = 0.38f),
                        1f to Color.Black.copy(alpha = 0.78f),
                    ),
                ),
        )
        if (badge != null) {
            MetaChip(
                text = badge,
                containerColor = Color.Black.copy(alpha = 0.32f),
                contentColor = Color.White,
                modifier = Modifier
                    .align(Alignment.TopStart)
                    .padding(12.dp),
            )
        }
        Row(
            modifier = Modifier
                .align(Alignment.BottomStart)
                .fillMaxWidth()
                .padding(start = 16.dp, end = 6.dp, bottom = 12.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = title,
                    style = MaterialTheme.typography.titleLargeEmphasized,
                    color = Color.White,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
                Spacer(Modifier.height(2.dp))
                Text(
                    text = subtitle,
                    style = MaterialTheme.typography.bodySmall,
                    color = Color.White.copy(alpha = 0.78f),
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
            }
            if (onPlay != null) {
                IconButton(
                    onClick = {
                        haptics.confirm()
                        onPlay()
                    },
                ) {
                    Icon(
                        imageVector = Icons.Rounded.PlayArrow,
                        contentDescription = "播放$title",
                        tint = Color.White,
                        modifier = Modifier.size(32.dp),
                    )
                }
            }
        }
    }
}

/** Offset of the item with [key] from the viewport centre, normalised to `-1..1`. */
fun LazyListLayoutInfo.centreOffsetOf(key: Any): Float {
    val item = visibleItemsInfo.firstOrNull { it.key == key } ?: return 0f
    val viewport = (viewportEndOffset - viewportStartOffset).coerceAtLeast(1)
    val centre = (viewportStartOffset + viewportEndOffset) / 2f
    return ((item.offset + item.size / 2f - centre) / viewport).coerceIn(-1f, 1f)
}
