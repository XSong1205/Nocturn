package dev.nocturn.app.ui.components

import androidx.compose.animation.animateColorAsState
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.MoreHoriz
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import dev.nocturn.app.data.model.Song
import dev.nocturn.app.ui.theme.NocturnText
import dev.nocturn.app.ui.util.formatCount
import dev.nocturn.app.ui.util.formatDuration

/**
 * The workhorse song row: index/playing indicator, cover, title and artist, duration, like
 * action and an overflow slot. Rows are rounded and ripple-clipped to match the expressive
 * shape language.
 */
@Composable
fun SongRow(
    song: Song,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    index: Int? = null,
    isPlaying: Boolean = false,
    isLiked: Boolean? = null,
    onLikeClick: (() -> Unit)? = null,
    onMoreClick: (() -> Unit)? = null,
    showCover: Boolean = true,
    showDuration: Boolean = true,
    trailing: @Composable (() -> Unit)? = null,
) {
    // The playing row sits on a soft primary tint that fades in and out as the track changes.
    val highlight by animateColorAsState(
        targetValue = if (isPlaying) {
            MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.42f)
        } else {
            Color.Transparent
        },
        animationSpec = MaterialTheme.motionScheme.defaultEffectsSpec(),
        label = "song-row-highlight",
    )
    val haptics = rememberHaptics()
    Row(
        modifier = modifier
            .fillMaxWidth()
            .clip(MaterialTheme.shapes.large)
            .background(highlight)
            .clickable(onClick = {
                haptics.tap()
                onClick()
            })
            .padding(horizontal = 12.dp, vertical = 8.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        if (index != null) {
            // Wide enough for three digits so long playlists keep their titles aligned.
            Box(modifier = Modifier.width(28.dp), contentAlignment = Alignment.Center) {
                if (isPlaying) {
                    PlaybackBars(playing = true, modifier = Modifier.size(width = 18.dp, height = 16.dp))
                } else {
                    Text(
                        text = index.toString(),
                        style = NocturnText.numeric,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        textAlign = TextAlign.Center,
                        maxLines = 1,
                    )
                }
            }
            Spacer(Modifier.width(8.dp))
        }
        if (showCover) {
            CoverArt(
                imageUrl = song.cover,
                modifier = Modifier.size(48.dp),
                shape = MaterialTheme.shapes.small,
                seed = song.id.hashCode(),
                contentDescription = null,
            )
            Spacer(Modifier.width(12.dp))
        }
        Column(modifier = Modifier.weight(1f)) {
            Text(
                text = song.name,
                style = MaterialTheme.typography.titleSmall,
                color = if (isPlaying) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurface,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
            Spacer(Modifier.height(2.dp))
            Row(verticalAlignment = Alignment.CenterVertically) {
                if (song.isVipOnly) {
                    VipTag()
                    Spacer(Modifier.width(6.dp))
                }
                Text(
                    text = song.artistText,
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                    modifier = Modifier.weight(1f, fill = false),
                )
            }
        }
        if (trailing != null) {
            trailing()
        } else {
            if (showDuration) {
                Text(
                    text = formatDuration(song.durationMs),
                    style = NocturnText.numeric,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.padding(horizontal = 8.dp),
                )
            }
            if (isLiked != null && onLikeClick != null) {
                LikeButton(liked = isLiked, onToggle = onLikeClick)
            }
            if (onMoreClick != null) {
                IconButton(onClick = {
                    haptics.tap()
                    onMoreClick()
                }) {
                    Icon(
                        Icons.Filled.MoreHoriz,
                        contentDescription = "更多",
                        tint = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
            }
        }
    }
}

/** Compact row for queue sheets and horizontal "recent" strips. */
@Composable
fun CompactSongRow(
    song: Song,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    isPlaying: Boolean = false,
    seasonText: String? = null,
) {
    val haptics = rememberHaptics()
    Row(
        modifier = modifier
            .fillMaxWidth()
            .clip(MaterialTheme.shapes.medium)
            .clickable(onClick = {
                haptics.tap()
                onClick()
            })
            .padding(horizontal = 10.dp, vertical = 8.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        CoverArt(
            imageUrl = song.cover,
            modifier = Modifier.size(44.dp),
            shape = MaterialTheme.shapes.small,
            seed = song.id.hashCode(),
            contentDescription = null,
        )
        Spacer(Modifier.width(12.dp))
        Column(modifier = Modifier.weight(1f)) {
            Text(
                text = song.name,
                style = MaterialTheme.typography.titleSmall,
                color = if (isPlaying) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurface,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
            Text(
                text = seasonText ?: song.artistText,
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
        }
        if (isPlaying) {
            PlaybackBars(playing = true, modifier = Modifier.size(width = 18.dp, height = 16.dp))
        }
    }
}

@Composable
fun VipTag() {
    Surface(
        shape = CircleShape,
        color = MaterialTheme.colorScheme.tertiaryContainer,
        contentColor = MaterialTheme.colorScheme.onTertiaryContainer,
    ) {
        Text(
            text = "VIP",
            style = NocturnText.eyebrow,
            modifier = Modifier.padding(horizontal = 6.dp, vertical = 1.dp),
        )
    }
}

/** Number + unit label used under covers in lists and grids. */
@Composable
fun MetricLabel(value: String, label: String, modifier: Modifier = Modifier) {
    Column(modifier = modifier, horizontalAlignment = Alignment.CenterHorizontally) {
        Text(
            text = value,
            style = MaterialTheme.typography.titleMedium,
            color = MaterialTheme.colorScheme.onSurface,
        )
        Text(
            text = label,
            style = MaterialTheme.typography.labelSmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
    }
}

/** A horizontal count label such as "12.3万 次播放". */
@Composable
fun CountLabel(count: Long, modifier: Modifier = Modifier, suffix: String = "次播放") {
    Row(modifier = modifier, horizontalArrangement = Arrangement.spacedBy(4.dp)) {
        Text(
            text = formatCount(count),
            style = MaterialTheme.typography.labelMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
        Text(
            text = suffix,
            style = MaterialTheme.typography.labelMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
    }
}
