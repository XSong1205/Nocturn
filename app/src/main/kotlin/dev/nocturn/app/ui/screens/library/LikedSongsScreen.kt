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
import dev.nocturn.app.ui.components.BarBackButton
import dev.nocturn.app.ui.components.BarTitle
import dev.nocturn.app.ui.components.EmptyState
import dev.nocturn.app.ui.components.GlassScaffold
import dev.nocturn.app.ui.components.GlassTopBar
import dev.nocturn.app.ui.components.LikedCollectionCover
import dev.nocturn.app.ui.components.ScreenError
import dev.nocturn.app.ui.components.ScreenLoading
import dev.nocturn.app.ui.components.SongRow
import dev.nocturn.app.ui.components.bottomContentPadding
import dev.nocturn.app.ui.components.rememberHaptics
import dev.nocturn.app.ui.components.scrolledPast
import dev.nocturn.app.ui.preview.PreviewData
import dev.nocturn.app.ui.state.LikedSongsUiState
import dev.nocturn.app.ui.theme.NocturnTheme

/**
 * Full-screen list of the account's liked songs.
 *
 * The list scrolls under a frosted bar; the hero (collection cover, title, count, play all)
 * scrolls away and its title then appears in the bar. Rows are the shared [SongRow] so playing
 * state and like toggles look identical everywhere. Loading, error and empty states are handled
 * explicitly because this route is often opened right after login.
 */
@Composable
fun LikedSongsScreen(
    state: LikedSongsUiState,
    modifier: Modifier = Modifier,
    onBack: () -> Unit = {},
    onPlayAll: () -> Unit = {},
    onSongClick: (Song, List<Song>) -> Unit = { _, _ -> },
    onLikeClick: (Song) -> Unit = {},
) {
    val listState = rememberLazyListState()
    val collapsed by listState.scrolledPast(200)
    var interactive by remember { mutableStateOf(false) }
    LaunchedEffect(Unit) {
        interactive = true
    }
    GlassScaffold(
        modifier = modifier,
        topBar = {
            GlassTopBar(scrolled = collapsed) {
                BarBackButton(onBack = onBack, testTag = "liked-back")
                BarTitle(text = "我喜欢的音乐", visible = collapsed)
            }
        },
    ) { topPadding ->
        when {
            state.loading -> ScreenLoading(
                label = "正在加载喜欢的音乐",
                modifier = Modifier
                    .padding(top = topPadding)
                    .testTag("liked-loading"),
            )

            state.error != null -> ScreenError(
                message = state.error,
                modifier = Modifier
                    .padding(top = topPadding)
                    .testTag("liked-error"),
            )

            state.songs.isEmpty() -> Column(Modifier.padding(top = topPadding)) {
                LikedHero(count = 0, onPlayAll = null)
                EmptyState(
                    title = "还没有喜欢的歌曲",
                    message = "在播放页点击爱心，把喜欢的歌收进这里",
                    modifier = Modifier.testTag("liked-empty"),
                )
            }

            else -> LazyColumn(
                state = listState,
                modifier = Modifier
                    .fillMaxSize()
                    .testTag("liked-songs-list"),
                contentPadding = PaddingValues(top = topPadding, bottom = bottomContentPadding()),
            ) {
                item(key = "hero") {
                    LikedHero(
                        count = state.songs.size,
                        onPlayAll = { if (interactive) onPlayAll() },
                    )
                }
                itemsIndexed(state.songs, key = { _, song -> song.id }) { index, song ->
                    SongRow(
                        song = song,
                        onClick = { if (interactive) onSongClick(song, state.songs) },
                        modifier = Modifier
                            .padding(horizontal = 8.dp)
                            .testTag("liked-song-row-${song.id}"),
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

/** Collection hero: the gradient cover tile beside title and count, then a full-width play all. */
@Composable
private fun LikedHero(count: Int, onPlayAll: (() -> Unit)?) {
    val haptics = rememberHaptics()
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .padding(start = 20.dp, end = 20.dp, top = 12.dp, bottom = 16.dp),
    ) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            LikedCollectionCover(
                size = 104.dp,
                modifier = Modifier.shadow(
                    elevation = 16.dp,
                    shape = MaterialTheme.shapes.large,
                    ambientColor = Color(0xFFE5326F).copy(alpha = 0.5f),
                    spotColor = Color(0xFFE5326F).copy(alpha = 0.6f),
                ),
            )
            Spacer(Modifier.width(18.dp))
            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = "我喜欢的音乐",
                    style = MaterialTheme.typography.headlineSmallEmphasized,
                    color = MaterialTheme.colorScheme.onSurface,
                )
                Spacer(Modifier.height(4.dp))
                Text(
                    text = if (count > 0) "$count 首歌曲" else "收藏歌曲",
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
                    .testTag("liked-play-all"),
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

@Preview(name = "我喜欢的音乐 · 深色", showBackground = true)
@Composable
private fun LikedSongsPreviewDark() {
    NocturnTheme(themeMode = ThemeMode.Dark) {
        LikedSongsScreen(state = PreviewData.likedSongsState)
    }
}

@Preview(name = "我喜欢的音乐 · 空 · 浅色", showBackground = true)
@Composable
private fun LikedSongsEmptyPreviewLight() {
    NocturnTheme(themeMode = ThemeMode.Light) {
        LikedSongsScreen(state = LikedSongsUiState(loading = false))
    }
}
