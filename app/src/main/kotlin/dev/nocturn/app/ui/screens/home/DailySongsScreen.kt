@file:OptIn(androidx.compose.material3.ExperimentalMaterial3ExpressiveApi::class)

package dev.nocturn.app.ui.screens.home

import androidx.compose.foundation.layout.Box
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
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import dev.nocturn.app.data.model.Song
import dev.nocturn.app.data.model.ThemeMode
import dev.nocturn.app.ui.components.BarBackButton
import dev.nocturn.app.ui.components.BarTitle
import dev.nocturn.app.ui.components.CoverArt
import dev.nocturn.app.ui.components.EmptyState
import dev.nocturn.app.ui.components.GlassScaffold
import dev.nocturn.app.ui.components.GlassTopBar
import dev.nocturn.app.ui.components.SongRow
import dev.nocturn.app.ui.components.bottomContentPadding
import dev.nocturn.app.ui.components.rememberHaptics
import dev.nocturn.app.ui.components.scrolledPast
import dev.nocturn.app.ui.preview.PreviewData
import dev.nocturn.app.ui.state.HomeUiState
import dev.nocturn.app.ui.theme.NocturnTheme
import java.time.LocalDate

/**
 * 每日推荐独立页：日历式大日期 + 封面 hero、播放全部，以及当天的完整曲目列表。首页只放一张
 * 入口卡片，完整列表在这里浏览。数据来自 [HomeUiState.dailySongs]。
 */
@Composable
fun DailySongsScreen(
    state: HomeUiState,
    modifier: Modifier = Modifier,
    onBack: () -> Unit = {},
    onPlayAll: () -> Unit = {},
    onSongClick: (Song, List<Song>) -> Unit = { _, _ -> },
    onLikeClick: (Song) -> Unit = {},
) {
    val listState = rememberLazyListState()
    val collapsed by listState.scrolledPast(220)
    val songs = state.dailySongs
    GlassScaffold(
        modifier = modifier,
        topBar = {
            GlassTopBar(scrolled = collapsed) {
                BarBackButton(onBack = onBack, testTag = "daily-back")
                BarTitle(text = "每日推荐", visible = collapsed)
            }
        },
    ) { topPadding ->
        if (songs.isEmpty()) {
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(top = topPadding),
                contentAlignment = Alignment.Center,
            ) {
                EmptyState(
                    title = "今日推荐还没准备好",
                    message = if (state.isLoggedIn) "稍后下拉首页刷新试试。" else "登录后即可获得每天为你挑选的歌曲。",
                    modifier = Modifier.testTag("daily-empty"),
                )
            }
            return@GlassScaffold
        }
        LazyColumn(
            state = listState,
            modifier = Modifier
                .fillMaxSize()
                .testTag("daily-list"),
            contentPadding = PaddingValues(top = topPadding, bottom = bottomContentPadding()),
        ) {
            item(key = "hero") {
                DailyHero(cover = songs.first(), count = songs.size, onPlayAll = onPlayAll)
            }
            itemsIndexed(songs, key = { _, song -> song.id }) { index, song ->
                SongRow(
                    song = song,
                    onClick = { onSongClick(song, songs) },
                    modifier = Modifier
                        .padding(horizontal = 8.dp)
                        .testTag("daily-song-$index"),
                    index = index + 1,
                    isPlaying = song.id == state.nowPlayingSongId,
                    isLiked = song.id in state.likedSongIds,
                    onLikeClick = { onLikeClick(song) },
                )
            }
        }
    }
}

/** Calendar-style hero: today's day number over the month, beside the first song's cover. */
@Composable
private fun DailyHero(cover: Song, count: Int, onPlayAll: () -> Unit) {
    val today = remember { LocalDate.now() }
    val haptics = rememberHaptics()
    val colors = MaterialTheme.colorScheme
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .padding(start = 20.dp, end = 20.dp, top = 12.dp, bottom = 16.dp),
    ) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Column(modifier = Modifier.weight(1f)) {
                Row(verticalAlignment = Alignment.Bottom) {
                    Text(
                        text = "%02d".format(today.dayOfMonth),
                        style = MaterialTheme.typography.displayLargeEmphasized,
                        color = colors.onSurface,
                    )
                    Spacer(Modifier.width(6.dp))
                    Text(
                        text = "/ %02d".format(today.monthValue),
                        style = MaterialTheme.typography.titleLargeEmphasized,
                        color = colors.onSurfaceVariant,
                        modifier = Modifier.padding(bottom = 10.dp),
                    )
                }
                Text(
                    text = "每日推荐",
                    style = MaterialTheme.typography.headlineSmallEmphasized,
                    color = colors.onSurface,
                )
                Spacer(Modifier.height(2.dp))
                Text(
                    text = "根据你的口味每天 6:00 更新 · $count 首",
                    style = MaterialTheme.typography.bodySmall,
                    color = colors.onSurfaceVariant,
                )
            }
            CoverArt(
                imageUrl = cover.cover,
                seed = cover.id.hashCode(),
                shape = MaterialTheme.shapes.large,
                contentDescription = null,
                modifier = Modifier
                    .size(112.dp)
                    .shadow(
                        elevation = 18.dp,
                        shape = MaterialTheme.shapes.large,
                        ambientColor = colors.primary.copy(alpha = 0.4f),
                        spotColor = colors.primary.copy(alpha = 0.5f),
                    ),
            )
        }
        Spacer(Modifier.height(18.dp))
        Button(
            onClick = {
                haptics.confirm()
                onPlayAll()
            },
            modifier = Modifier
                .fillMaxWidth()
                .height(48.dp)
                .testTag("daily-play-all"),
        ) {
            Icon(Icons.Rounded.PlayArrow, contentDescription = null, modifier = Modifier.size(20.dp))
            Spacer(Modifier.width(8.dp))
            Text("播放全部")
        }
    }
}

@Preview(name = "每日推荐 · 浅色", showBackground = true)
@Composable
private fun DailySongsPreview() {
    NocturnTheme(themeMode = ThemeMode.Light) {
        DailySongsScreen(state = PreviewData.homeState)
    }
}
