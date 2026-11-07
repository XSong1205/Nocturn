package dev.nocturn.app.ui.components

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Favorite
import androidx.compose.material3.Icon
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp

/** Warm red-to-magenta used by the "我喜欢的音乐" collection in every theme. */
private val LikedGradient = listOf(Color(0xFFFF5F6D), Color(0xFFE5326F), Color(0xFFB2246B))

/**
 * Cover for the liked-songs collection: an app-icon style tile with a deep gradient, a soft top
 * light and a white heart, sized by [size]. Corner radius scales with the tile so it reads the
 * same in a list row and in the page hero.
 */
@Composable
fun LikedCollectionCover(
    modifier: Modifier = Modifier,
    size: Dp = 56.dp,
) {
    val shape = RoundedCornerShape(size * 0.28f)
    Box(
        modifier = modifier
            .size(size)
            .clip(shape),
        contentAlignment = Alignment.Center,
    ) {
        Canvas(Modifier.fillMaxSize()) {
            drawRect(
                Brush.linearGradient(
                    LikedGradient,
                    start = Offset(0f, 0f),
                    end = Offset(this.size.width, this.size.height),
                ),
            )
            // Top light: a broad, faint highlight that gives the tile some depth.
            drawCircle(
                brush = Brush.radialGradient(
                    listOf(Color.White.copy(alpha = 0.32f), Color.Transparent),
                    center = Offset(this.size.width * 0.28f, this.size.height * 0.12f),
                    radius = this.size.width * 0.75f,
                ),
                radius = this.size.width * 0.75f,
                center = Offset(this.size.width * 0.28f, this.size.height * 0.12f),
            )
        }
        Icon(
            imageVector = Icons.Rounded.Favorite,
            contentDescription = null,
            tint = Color.White,
            modifier = Modifier.size(size * 0.46f),
        )
    }
}
