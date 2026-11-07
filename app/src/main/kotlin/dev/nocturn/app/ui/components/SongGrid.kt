package dev.nocturn.app.ui.components

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.pager.HorizontalPager
import androidx.compose.foundation.pager.rememberPagerState
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.PlayArrow
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import dev.nocturn.app.data.model.Song

private val TileHeight = 64.dp
private val TileGap = 4.dp

/**
 * Paged song grid: columns of [rows] compact tiles, each page narrower than the screen so the next
 * column peeks in and invites a swipe. Every page has the same height, so swiping onto a shorter
 * last page never moves the content below. Tapping a tile reports that song.
 */
@Composable
fun SongGridPager(
    songs: List<Song>,
    onSongClick: (Song) -> Unit,
    modifier: Modifier = Modifier,
    rows: Int = 3,
    nowPlayingSongId: Long? = null,
) {
    val pages = remember(songs, rows) { songs.chunked(rows) }
    val pagerState = rememberPagerState { pages.size }
    HorizontalPager(
        state = pagerState,
        modifier = modifier.fillMaxWidth(),
        contentPadding = PaddingValues(start = 16.dp, end = 44.dp),
        pageSpacing = 12.dp,
        verticalAlignment = Alignment.Top,
    ) { page ->
        Column(
            modifier = Modifier.height(TileHeight * rows + TileGap * (rows - 1)),
            verticalArrangement = Arrangement.spacedBy(TileGap),
        ) {
            pages[page].forEach { song ->
                SongTile(
                    song = song,
                    onClick = { onSongClick(song) },
                    playing = song.id == nowPlayingSongId,
                    modifier = Modifier.testTag("song-tile-${song.id}"),
                )
            }
        }
    }
}

/** Compact song row for grids: cover, title, VIP tag and artist, and a play glyph. */
@Composable
fun SongTile(
    song: Song,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    playing: Boolean = false,
) {
    val haptics = rememberHaptics()
    Row(
        modifier = modifier
            .fillMaxWidth()
            .height(TileHeight)
            .clip(MaterialTheme.shapes.medium)
            .clickable {
                haptics.tap()
                onClick()
            }
            .padding(horizontal = 4.dp, vertical = 4.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        CoverArt(
            imageUrl = song.cover,
            modifier = Modifier.size(56.dp),
            shape = MaterialTheme.shapes.small,
            seed = song.id.hashCode(),
            contentDescription = null,
        )
        Spacer(Modifier.width(12.dp))
        Column(modifier = Modifier.weight(1f)) {
            Text(
                text = song.name,
                style = MaterialTheme.typography.titleMedium,
                color = if (playing) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurface,
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
                )
            }
        }
        if (playing) {
            PlaybackBars(playing = true, modifier = Modifier.size(width = 18.dp, height = 16.dp))
            Spacer(Modifier.width(8.dp))
        } else {
            Icon(
                imageVector = Icons.Rounded.PlayArrow,
                contentDescription = null,
                tint = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.size(26.dp),
            )
        }
    }
}
