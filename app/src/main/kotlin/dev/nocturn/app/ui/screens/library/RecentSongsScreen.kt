@file:OptIn(androidx.compose.material3.ExperimentalMaterial3ExpressiveApi::class)

package dev.nocturn.app.ui.screens.library

import androidx.compose.foundation.layout.Arrangement
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
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.PlayArrow
import androidx.compose.material3.Button
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import dev.nocturn.app.data.model.Song
import dev.nocturn.app.data.model.ThemeMode
import dev.nocturn.app.ui.adaptive.LocalWindowWidthClass
import dev.nocturn.app.ui.components.BarBackButton
import dev.nocturn.app.ui.components.BarTitle
import dev.nocturn.app.ui.components.EmptyState
import dev.nocturn.app.ui.components.GlassScaffold
import dev.nocturn.app.ui.components.GlassTopBar
import dev.nocturn.app.ui.components.RecentCollectionCover
import dev.nocturn.app.ui.components.SongRow
import dev.nocturn.app.ui.components.bottomContentPadding
import dev.nocturn.app.ui.components.rememberHaptics
import dev.nocturn.app.ui.components.scrolledPast
import dev.nocturn.app.ui.preview.PreviewData
import dev.nocturn.app.ui.state.LibraryUiState
import dev.nocturn.app.ui.theme.NocturnTheme

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
    val isExpanded = LocalWindowWidthClass.current.isExpanded
    var interactive by remember { mutableStateOf(false) }
    LaunchedEffect(Unit) {
        interactive = true
    }
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
                    RecentHero(
                        count = state.recentSongs.size,
                        onPlayAll = { if (interactive) onPlayAll() },
                    )
                }
                if (isExpanded) {
                    items(
                        items = state.recentSongs.chunked(2),
                        key = { pair -> "recent-pair-${pair.first().id}" },
                    ) { pair ->
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(horizontal = 12.dp, vertical = 2.dp),
                            horizontalArrangement = Arrangement.spacedBy(8.dp),
                        ) {
                            val song0 = pair[0]
                            val index0 = state.recentSongs.indexOf(song0)
                            SongRow(
                                song = song0,
                                onClick = { if (interactive) onSongClick(song0, state.recentSongs) },
                                modifier = Modifier
                                    .weight(1f)
                                    .testTag("recent-song-row-${song0.id}"),
                                index = index0 + 1,
                                isPlaying = song0.id == state.nowPlayingSongId,
                                isLiked = state.likedSongIds.contains(song0.id),
                                onLikeClick = { onLikeClick(song0) },
                            )
                            if (pair.size > 1) {
                                val song1 = pair[1]
                                val index1 = state.recentSongs.indexOf(song1)
                                SongRow(
                                    song = song1,
                                    onClick = { if (interactive) onSongClick(song1, state.recentSongs) },
                                    modifier = Modifier
                                        .weight(1f)
                                        .testTag("recent-song-row-${song1.id}"),
                                    index = index1 + 1,
                                    isPlaying = song1.id == state.nowPlayingSongId,
                                    isLiked = state.likedSongIds.contains(song1.id),
                                    onLikeClick = { onLikeClick(song1) },
                                )
                            } else {
                                Spacer(Modifier.weight(1f))
                            }
                        }
                    }
                } else {
                    itemsIndexed(state.recentSongs, key = { _, song -> song.id }) { index, song ->
                        SongRow(
                            song = song,
                            onClick = { if (interactive) onSongClick(song, state.recentSongs) },
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
}

/** History heading, song count and play-all action. */
@Composable
private fun RecentHero(count: Int, onPlayAll: (() -> Unit)?) {
    val haptics = rememberHaptics()
    val isWide = LocalWindowWidthClass.current.isAtLeastMedium
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .padding(start = 20.dp, end = 20.dp, top = 12.dp, bottom = 16.dp),
    ) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            RecentCollectionCover(
                size = if (isWide) 120.dp else 104.dp,
                modifier = Modifier.shadow(
                    elevation = 16.dp,
                    shape = MaterialTheme.shapes.large,
                    ambientColor = Color(0xFF0099FF).copy(alpha = 0.5f),
                    spotColor = Color(0xFF0099FF).copy(alpha = 0.6f),
                ),
            )
            Spacer(Modifier.width(if (isWide) 24.dp else 18.dp))
            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = "最近播放",
                    style = if (isWide) MaterialTheme.typography.headlineMediumEmphasized else MaterialTheme.typography.headlineSmallEmphasized,
                    color = MaterialTheme.colorScheme.onSurface,
                )
                Spacer(Modifier.height(4.dp))
                Text(
                    text = if (count > 0) "$count 首歌曲" else "播放记录",
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
            if (isWide && onPlayAll != null) {
                Spacer(Modifier.width(20.dp))
                Button(
                    onClick = {
                        haptics.confirm()
                        onPlayAll()
                    },
                    modifier = Modifier
                        .height(48.dp)
                        .testTag("recent-play-all"),
                ) {
                    Icon(Icons.Rounded.PlayArrow, contentDescription = null, modifier = Modifier.size(20.dp))
                    Spacer(Modifier.width(8.dp))
                    Text("播放全部")
                }
            }
        }
        if (!isWide && onPlayAll != null) {
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

// ---------------------------------------------------------------------------------------------
// Previews
// ---------------------------------------------------------------------------------------------

@Preview(name = "最近播放 · 深色", showBackground = true)
@Composable
private fun RecentSongsPreviewDark() {
    NocturnTheme(themeMode = ThemeMode.Dark) {
        RecentSongsScreen(state = PreviewData.libraryLoggedIn)
    }
}

@Preview(name = "最近播放 · 空 · 浅色", showBackground = true)
@Composable
private fun RecentSongsEmptyPreviewLight() {
    NocturnTheme(themeMode = ThemeMode.Light) {
        RecentSongsScreen(state = PreviewData.libraryLoggedIn.copy(recentSongs = emptyList()))
    }
}
