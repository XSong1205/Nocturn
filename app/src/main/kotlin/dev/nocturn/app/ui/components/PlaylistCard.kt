@file:OptIn(androidx.compose.material3.ExperimentalMaterial3ExpressiveApi::class)

package dev.nocturn.app.ui.components

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Headphones
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material3.ExperimentalMaterial3ExpressiveApi
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import dev.nocturn.app.data.model.PlaylistSummary
import dev.nocturn.app.data.model.Toplist
import dev.nocturn.app.ui.theme.NocturnText
import dev.nocturn.app.ui.util.formatCount

/** Square playlist card used in horizontal carousels and grids. */
@Composable
fun PlaylistCard(
    playlist: PlaylistSummary,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    width: Dp = 152.dp,
    showCreator: Boolean = false,
) {
    val haptics = rememberHaptics()
    Column(
        modifier = modifier
            .width(width)
            .pressScale()
            .clip(MaterialTheme.shapes.large)
            .clickable(onClick = {
                haptics.tap()
                onClick()
            })
            .padding(bottom = 6.dp),
    ) {
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .aspectRatio(1f),
        ) {
            CoverArt(
                imageUrl = playlist.coverUrl,
                modifier = Modifier.fillMaxSize(),
                shape = MaterialTheme.shapes.large,
                seed = playlist.id.hashCode(),
                contentDescription = playlist.name,
            )
            Surface(
                modifier = Modifier
                    .align(Alignment.BottomEnd)
                    .padding(8.dp),
                shape = CircleShape,
                color = Color.Black.copy(alpha = 0.42f),
                contentColor = Color.White,
            ) {
                Row(
                    modifier = Modifier.padding(horizontal = 8.dp, vertical = 4.dp),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(4.dp),
                ) {
                    Icon(
                        Icons.Filled.Headphones,
                        contentDescription = null,
                        modifier = Modifier.size(12.dp),
                    )
                    Text(
                        text = formatCount(playlist.playCount),
                        style = NocturnText.numeric,
                    )
                }
            }
        }
        Spacer(Modifier.height(10.dp))
        Text(
            text = playlist.name,
            style = MaterialTheme.typography.titleSmall,
            color = MaterialTheme.colorScheme.onSurface,
            maxLines = 2,
            overflow = TextOverflow.Ellipsis,
            modifier = Modifier.padding(horizontal = 4.dp),
        )
        if (showCreator && !playlist.creatorName.isNullOrBlank()) {
            Spacer(Modifier.height(2.dp))
            Text(
                text = playlist.creatorName,
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
                modifier = Modifier.padding(horizontal = 4.dp),
            )
        }
    }
}

/** Ranking card: big cover, chart name and a preview of the top three songs. */
@Composable
fun ChartCard(
    toplist: Toplist,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val haptics = rememberHaptics()
    Surface(
        onClick = {
            haptics.tap()
            onClick()
        },
        modifier = modifier
            .width(280.dp)
            .pressScale(),
        shape = MaterialTheme.shapes.large,
        color = MaterialTheme.colorScheme.surfaceContainerLow,
    ) {
        Row(modifier = Modifier.padding(12.dp)) {
            CoverArt(
                imageUrl = toplist.coverUrl,
                modifier = Modifier.size(96.dp),
                shape = MaterialTheme.shapes.medium,
                seed = toplist.id.hashCode(),
                contentDescription = toplist.name,
            )
            Spacer(Modifier.width(12.dp))
            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = toplist.name,
                    style = MaterialTheme.typography.titleMedium,
                    color = MaterialTheme.colorScheme.onSurface,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
                toplist.updateFrequency?.let {
                    Text(
                        text = it,
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.primary,
                    )
                }
                Spacer(Modifier.height(6.dp))
                toplist.tracks.take(3).forEachIndexed { index, song ->
                    Text(
                        text = "${index + 1}. ${song.name}",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                    )
                }
                if (toplist.tracks.isEmpty() && toplist.trackCount > 0) {
                    Text(
                        text = "${toplist.trackCount} 首歌曲",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
            }
        }
    }
}