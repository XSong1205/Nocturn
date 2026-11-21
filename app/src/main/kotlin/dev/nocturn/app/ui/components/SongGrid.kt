package dev.nocturn.app.ui.components

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.BoxWithConstraints
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
import androidx.compose.foundation.pager.PageSize
import androidx.compose.foundation.pager.rememberPagerState
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.MusicNote
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
    BoxWithConstraints(modifier = modifier.fillMaxWidth()) {
        val isWide = maxWidth >= 600.dp
        val pageSize = if (maxWidth >= 840.dp) {
            PageSize.Fixed(320.dp)
        } else if (maxWidth >= 600.dp) {
            PageSize.Fixed(280.dp)
        } else {
            PageSize.Fill
        }
        val contentPadding = if (isWide) {
            PaddingValues(horizontal = 20.dp)
        } else {
            PaddingValues(start = 16.dp, end = 44.dp)
        }
        HorizontalPager(
            state = pagerState,
            pageSize = pageSize,
            modifier = Modifier.fillMaxWidth(),
            contentPadding = contentPadding,
            pageSpacing = if (isWide) 16.dp else 12.dp,
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
            placeholderIcon = Icons.Rounded.MusicNote,
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
