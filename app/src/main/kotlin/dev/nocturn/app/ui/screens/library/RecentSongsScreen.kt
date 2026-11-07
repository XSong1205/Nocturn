@file:OptIn(androidx.compose.material3.ExperimentalMaterial3ExpressiveApi::class)

package dev.nocturn.app.ui.screens.library

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.PlayArrow
import androidx.compose.material.icons.rounded.History
import androidx.compose.material3.Button
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.unit.dp
import dev.nocturn.app.data.model.Song
import dev.nocturn.app.ui.components.BarBackButton
import dev.nocturn.app.ui.components.BarTitle
import dev.nocturn.app.ui.components.EmptyState
import dev.nocturn.app.ui.components.GlassScaffold
import dev.nocturn.app.ui.components.GlassTopBar
import dev.nocturn.app.ui.components.SongRow
import dev.nocturn.app.ui.components.bottomContentPadding
import dev.nocturn.app.ui.components.rememberHaptics
import dev.nocturn.app.ui.components.scrolledPast
import dev.nocturn.app.ui.state.LibraryUiState

/** Recently played songs, opened from the library entry. */
@Composable
fun RecentSongsScreen(
    state: LibraryUiState,
    modifier: Modifier = Modifier,
    onBack: () -> Unit = {},
    onPlayAll: () -> Unit = {},
    onSongClick: (Song, List<Song>) -> Unit = { _, _ -> },
    onLikeClick: (Song) -> Unit = {},
) {
    val listState = rememberLazyListState()
    val collapsed by listState.scrolledPast(200)
    GlassScaffold(
        modifier = modifier,
        topBar = {
            GlassTopBar(scrolled = collapsed) {
                BarBackButton(onBack = onBack, testTag = "recent-back")
                BarTitle(text = "最近播放", visible = collapsed)
            }
        },
    ) { topPadding ->
        when {
            state.recentSongs.isEmpty() -> Column(Modifier.padding(top = topPadding)) {
                RecentHero(count = 0, onPlayAll = null)
                EmptyState(
                    title = "还没有播放记录",
                    message = "播放过的歌曲会出现在这里",
                    modifier = Modifier.testTag("recent-empty"),
                )
            }

            else -> LazyColumn(
                state = listState,
                modifier = Modifier
                    .fillMaxSize()
                    .testTag("recent-songs-list"),
                contentPadding = PaddingValues(top = topPadding, bottom = bottomContentPadding()),
            ) {
                item(key = "hero") {
                    RecentHero(count = state.recentSongs.size, onPlayAll = onPlayAll)
                }
                itemsIndexed(state.recentSongs, key = { _, song -> song.id }) { index, song ->
                    SongRow(
                        song = song,
                        onClick = { onSongClick(song, state.recentSongs) },
                        modifier = Modifier
                            .padding(horizontal = 8.dp)
                            .testTag("recent-song-row-${song.id}"),
                        index = index + 1,
                        isPlaying = song.id == state.nowPlayingSongId,
                        isLiked = state.likedSongIds.contains(song.id),
                        onLikeClick = { onLikeClick(song) },
                    )
                }
            }
        }
    }
}

/** History heading, song count and play-all action. */
@Composable
private fun RecentHero(count: Int, onPlayAll: (() -> Unit)?) {
    val haptics = rememberHaptics()
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .padding(start = 20.dp, end = 20.dp, top = 12.dp, bottom = 16.dp),
    ) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Icon(
                Icons.Rounded.History,
                contentDescription = null,
                modifier = Modifier.size(80.dp),
                tint = MaterialTheme.colorScheme.primary,
            )
            Spacer(Modifier.width(18.dp))
            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = "最近播放",
                    style = MaterialTheme.typography.headlineSmallEmphasized,
                    color = MaterialTheme.colorScheme.onSurface,
                )
                Spacer(Modifier.height(4.dp))
                Text(
                    text = if (count > 0) "$count 首歌曲" else "播放记录",
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
        }
        if (onPlayAll != null) {
            Spacer(Modifier.height(18.dp))
            Button(
                onClick = {
                    haptics.confirm()
                    onPlayAll()
                },
                modifier = Modifier
                    .fillMaxWidth()
                    .height(48.dp)
                    .testTag("recent-play-all"),
            ) {
                Icon(Icons.Rounded.PlayArrow, contentDescription = null, modifier = Modifier.size(20.dp))
                Spacer(Modifier.width(8.dp))
                Text("播放全部")
            }
        }
    }
}

