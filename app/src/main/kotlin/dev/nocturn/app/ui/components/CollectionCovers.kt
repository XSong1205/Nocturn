package dev.nocturn.app.ui.components

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Favorite
import androidx.compose.material.icons.rounded.History
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp

/**
 * Cover for the liked-songs collection: a Material 3 Monet tile with a solid container
 * background and centered heart icon.
 */
@Composable
fun LikedCollectionCover(
    modifier: Modifier = Modifier,
    size: Dp = 56.dp,
) {
    val shape = RoundedCornerShape(size * 0.28f)
    val colors = MaterialTheme.colorScheme
    Box(
        modifier = modifier
            .size(size)
            .clip(shape)
            .background(colors.primaryContainer),
        contentAlignment = Alignment.Center,
    ) {
        Icon(
            imageVector = Icons.Rounded.Favorite,
            contentDescription = null,
            tint = colors.onPrimaryContainer,
            modifier = Modifier.size(size * 0.48f),
        )
    }
}

/**
 * Cover for the recent-songs collection: a Material 3 Monet tile with a solid container
 * background and centered history icon.
 */
@Composable
fun RecentCollectionCover(
    modifier: Modifier = Modifier,
    size: Dp = 56.dp,
) {
    val shape = RoundedCornerShape(size * 0.28f)
    val colors = MaterialTheme.colorScheme
    Box(
        modifier = modifier
            .size(size)
            .clip(shape)
            .background(colors.tertiaryContainer),
        contentAlignment = Alignment.Center,
    ) {
        Icon(
            imageVector = Icons.Rounded.History,
            contentDescription = null,
            tint = colors.onTertiaryContainer,
            modifier = Modifier.size(size * 0.48f),
        )
    }
}
