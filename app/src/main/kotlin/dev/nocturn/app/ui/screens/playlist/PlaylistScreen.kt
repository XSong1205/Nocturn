@file:OptIn(
    androidx.compose.material3.ExperimentalMaterial3Api::class,
    androidx.compose.material3.ExperimentalMaterial3ExpressiveApi::class,
)

package dev.nocturn.app.ui.screens.playlist

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
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
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.CloudOff
import androidx.compose.material.icons.rounded.Favorite
import androidx.compose.material.icons.rounded.FavoriteBorder
import androidx.compose.material.icons.rounded.PlayArrow
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.LinearWavyProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.SplitButtonDefaults
import androidx.compose.material3.SplitButtonLayout
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.ToggleButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.derivedStateOf
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import dev.nocturn.app.data.model.PlaylistDetail
import dev.nocturn.app.data.model.Song
import dev.nocturn.app.data.model.ThemeMode
import dev.nocturn.app.ui.components.BarBackButton
import dev.nocturn.app.ui.components.BarTitle
import dev.nocturn.app.ui.components.CoverArt
import dev.nocturn.app.ui.components.EmptyState
import dev.nocturn.app.ui.components.GlassScaffold
import dev.nocturn.app.ui.components.GlassTopBar
import dev.nocturn.app.ui.components.LocalBottomChromePadding
import dev.nocturn.app.ui.components.MetaChip
import dev.nocturn.app.ui.components.NocturnBackdrop
import dev.nocturn.app.ui.components.ScreenError
import dev.nocturn.app.ui.components.ScreenLoading
import dev.nocturn.app.ui.components.SectionHeader
import dev.nocturn.app.ui.components.SongRow
import dev.nocturn.app.ui.components.bottomContentPadding
import dev.nocturn.app.ui.components.rememberHaptics
import dev.nocturn.app.ui.components.scrolledPast
import dev.nocturn.app.ui.preview.PreviewData
import dev.nocturn.app.ui.state.PlaylistUiState
import dev.nocturn.app.ui.theme.NocturnTheme
import dev.nocturn.app.ui.util.formatCount

/**
 * 歌单 / 排行榜详情页。
 *
 * 沉浸式头部（大封面 + 渐变环境光 + 名称 / 创建者 / 播放量 / 标签 / 可展开简介）+ 悬浮返回栏，
 * 动作区使用 Expressive SplitButton（播放全部 + 收藏切换），曲目列表触底自动分页。
 */
@Composable
fun PlaylistScreen(
    state: PlaylistUiState,
    modifier: Modifier = Modifier,
    onBack: () -> Unit = {},
    onPlayAll: (List<Song>) -> Unit = {},
    onSongClick: (Song, List<Song>) -> Unit = { _, _ -> },
    onLikeClick: (Song) -> Unit = {},
    onLoadMore: () -> Unit = {},
    onRetry: () -> Unit = {},
    onSubscribeToggle: () -> Unit = {},
) {
    val detail = state.detail
    if (detail == null) {
        GlassScaffold(
            modifier = modifier,
            topBar = {
                GlassTopBar(scrolled = false) {
                    BarBackButton(onBack = onBack, testTag = "playlist-back")
                }
            },
        ) { topPadding ->
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(top = topPadding, bottom = LocalBottomChromePadding.current),
                contentAlignment = Alignment.Center,
            ) {
                val error = state.error
                when {
                    state.loading -> ScreenLoading(label = "正在打开歌单")
                    error != null -> ScreenError(message = error, onRetry = onRetry)
                    else -> EmptyState(
                        title = "歌单不见了",
                        message = "它可能已被删除，或者被设为私密。",
                    )
                }
            }
        }
        return
    }

    PlaylistContent(
        state = state,
        detail = detail,
        modifier = modifier,
        onBack = onBack,
        onPlayAll = onPlayAll,
        onSongClick = onSongClick,
        onLikeClick = onLikeClick,
        onLoadMore = onLoadMore,
        onRetry = onRetry,
        onSubscribeToggle = onSubscribeToggle,
    )
}

@Composable
private fun PlaylistContent(
    state: PlaylistUiState,
    detail: PlaylistDetail,
    modifier: Modifier,
    onBack: () -> Unit,
    onPlayAll: (List<Song>) -> Unit,
    onSongClick: (Song, List<Song>) -> Unit,
    onLikeClick: (Song) -> Unit,
    onLoadMore: () -> Unit,
    onRetry: () -> Unit,
    onSubscribeToggle: () -> Unit,
) {
    val listState = rememberLazyListState()
    var descriptionExpanded by rememberSaveable { mutableStateOf(false) }

    val shouldLoadMore by remember(listState) {
        derivedStateOf {
            val info = listState.layoutInfo
            val lastVisible = info.visibleItemsInfo.lastOrNull()?.index ?: -1
            info.totalItemsCount > 0 && lastVisible >= info.totalItemsCount - 3
        }
    }
    LaunchedEffect(shouldLoadMore, state.hasMore, state.loadingMore, state.songs.size) {
        if (shouldLoadMore && state.hasMore && !state.loadingMore) onLoadMore()
    }
    // Once the hero has scrolled away the playlist name appears in the frosted bar.
    val heroScrolledAway by listState.scrolledPast(420)

    GlassScaffold(
        modifier = modifier,
        topBar = {
            GlassTopBar(scrolled = heroScrolledAway) {
                BarBackButton(onBack = onBack, testTag = "playlist-back")
                BarTitle(text = detail.name, visible = heroScrolledAway)
            }
        },
    ) { topPadding ->
        // Immersive: the hero's ambient gradient runs under the frosted bar, its content starts
        // below it.
        LazyColumn(
            state = listState,
            modifier = Modifier
                .fillMaxSize()
                .testTag("playlist-list"),
            contentPadding = PaddingValues(bottom = bottomContentPadding(32.dp)),
        ) {
            item(key = "hero") {
                PlaylistHero(
                    topInset = topPadding,
                    detail = detail,
                    hasMore = state.hasMore,
                    expanded = descriptionExpanded,
                    onToggleDescription = { descriptionExpanded = !descriptionExpanded },
                )
            }
            item(key = "actions") {
                PlaylistActions(
                    state = state,
                    detail = detail,
                    onPlayAll = onPlayAll,
                    onSubscribeToggle = onSubscribeToggle,
                )
            }
            item(key = "tracks-header") {
                SectionHeader(
                    title = "歌曲列表",
                    subtitle = buildString {
                        append(detail.trackCount.coerceAtLeast(state.songs.size))
                        append(" 首")
                        if (state.hasMore) append(" · 上滑加载更多")
                    },
                    modifier = Modifier.padding(top = 26.dp, bottom = 8.dp),
                )
            }
            if (state.songs.isEmpty() && !state.loadingMore) {
                item(key = "empty-tracks") {
                    EmptyState(
                        title = "还没有歌曲",
                        message = "这个歌单暂时是空的。",
                        modifier = Modifier.padding(top = 24.dp),
                    )
                }
            }
            itemsIndexed(
                items = state.songs,
                key = { _, song -> song.id },
            ) { index, song ->
                SongRow(
                    song = song,
                    onClick = { onSongClick(song, state.songs) },
                    modifier = Modifier
                        .padding(horizontal = 8.dp)
                        .testTag("playlist-song-$index"),
                    index = index + 1,
                    isPlaying = state.nowPlayingSongId == song.id,
                    isLiked = song.id in state.likedSongIds,
                    onLikeClick = { onLikeClick(song) },
                )
            }
            if (state.loadingMore) {
                item(key = "loading-more") { LoadingMoreRow() }
            } else if (!state.hasMore && state.songs.isNotEmpty()) {
                item(key = "list-end") { ListEnd() }
            }
            val inlineError = state.error
            if (inlineError != null) {
                item(key = "inline-error") {
                    InlineError(message = inlineError, onRetry = onRetry)
                }
            }
        }
    }
}

@Composable
private fun PlaylistHero(
    topInset: Dp,
    detail: PlaylistDetail,
    hasMore: Boolean,
    expanded: Boolean,
    onToggleDescription: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val colors = MaterialTheme.colorScheme
    Box(
        modifier = modifier
            .fillMaxWidth()
            .testTag("playlist-header"),
    ) {
        Box(
            modifier = Modifier
                .matchParentSize()
                .background(
                    Brush.verticalGradient(
                        0f to colors.primaryContainer.copy(alpha = 0.5f),
                        0.5f to colors.surfaceContainerLow.copy(alpha = 0.55f),
                        1f to Color.Transparent,
                    ),
                ),
        )
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(start = 20.dp, end = 20.dp, top = topInset + 12.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
        ) {
            CoverArt(
                imageUrl = detail.coverUrl,
                modifier = Modifier
                    .size(184.dp)
                    .shadow(
                        elevation = 22.dp,
                        shape = MaterialTheme.shapes.extraLarge,
                        ambientColor = colors.primary.copy(alpha = 0.42f),
                        spotColor = colors.primary.copy(alpha = 0.48f),
                    ),
                shape = MaterialTheme.shapes.extraLarge,
                seed = detail.id.hashCode(),
                contentDescription = detail.name,
            )
            Spacer(Modifier.height(22.dp))
            Text(
                text = detail.name,
                style = MaterialTheme.typography.headlineMediumEmphasized,
                color = colors.onSurface,
                textAlign = TextAlign.Center,
                maxLines = 3,
                overflow = TextOverflow.Ellipsis,
            )
            val creator = detail.creatorName
            if (!creator.isNullOrBlank()) {
                Spacer(Modifier.height(12.dp))
                Row(verticalAlignment = Alignment.CenterVertically) {
                    CoverArt(
                        imageUrl = detail.creatorAvatarUrl,
                        modifier = Modifier
                            .size(26.dp)
                            .clip(CircleShape),
                        shape = CircleShape,
                        seed = creator.hashCode(),
                        contentDescription = null,
                    )
                    Spacer(Modifier.width(8.dp))
                    Text(
                        text = creator,
                        style = MaterialTheme.typography.titleSmall,
                        color = colors.onSurfaceVariant,
                    )
                }
            }
            Spacer(Modifier.height(14.dp))
            Row(
                horizontalArrangement = Arrangement.spacedBy(8.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                MetaChip(text = "${formatCount(detail.playCount)} 次播放")
                MetaChip(text = "${detail.trackCount} 首歌曲")
                val frequency = detail.updateFrequency
                if (!frequency.isNullOrBlank()) {
                    MetaChip(
                        text = frequency,
                        containerColor = colors.secondaryContainer,
                        contentColor = colors.onSecondaryContainer,
                    )
                }
            }
            if (detail.tags.isNotEmpty()) {
                Spacer(Modifier.height(10.dp))
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    detail.tags.take(3).forEach { tag ->
                        MetaChip(
                            text = "# $tag",
                            containerColor = colors.tertiaryContainer,
                            contentColor = colors.onTertiaryContainer,
                        )
                    }
                }
            }
            val description = detail.description
            if (!description.isNullOrBlank()) {
                Spacer(Modifier.height(18.dp))
                Surface(
                    modifier = Modifier.fillMaxWidth(),
                    shape = MaterialTheme.shapes.large,
                    color = colors.surfaceContainerLow,
                ) {
                    Column(modifier = Modifier.padding(16.dp)) {
                        Text(
                            text = description,
                            style = MaterialTheme.typography.bodySmall,
                            color = colors.onSurfaceVariant,
                            maxLines = if (expanded) 40 else 3,
                            overflow = TextOverflow.Ellipsis,
                        )
                        if (description.length > 60) {
                            ToggleButton(
                                checked = expanded,
                                onCheckedChange = { onToggleDescription() },
                                modifier = Modifier
                                    .padding(top = 6.dp)
                                    .testTag("playlist-description-toggle"),
                            ) {
                                Text(if (expanded) "收起" else "展开完整简介")
                            }
                        }
                    }
                }
            }
            if (hasMore) {
                Spacer(Modifier.height(4.dp))
            }
        }
    }
}

@Composable
private fun PlaylistActions(
    state: PlaylistUiState,
    detail: PlaylistDetail,
    onPlayAll: (List<Song>) -> Unit,
    onSubscribeToggle: () -> Unit,
) {
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 20.dp),
    ) {
        val haptics = rememberHaptics()
        SplitButtonLayout(
            leadingButton = {
                SplitButtonDefaults.LeadingButton(
                    onClick = {
                        haptics.confirm()
                        onPlayAll(state.songs)
                    },
                    modifier = Modifier.testTag("playlist-play-all"),
                    enabled = state.songs.isNotEmpty(),
                ) {
                    Icon(
                        Icons.Rounded.PlayArrow,
                        contentDescription = null,
                        modifier = Modifier.size(20.dp),
                    )
                    Spacer(Modifier.width(8.dp))
                    Text("播放全部")
                }
            },
            trailingButton = {
                SplitButtonDefaults.TrailingButton(
                    onClick = {
                        haptics.toggle(on = !detail.subscribed)
                        onSubscribeToggle()
                    },
                    modifier = Modifier.testTag("playlist-subscribe"),
                    enabled = !state.subscribing,
                    colors = ButtonDefaults.filledTonalButtonColors(),
                ) {
                    Icon(
                        imageVector = if (detail.subscribed) {
                            Icons.Rounded.Favorite
                        } else {
                            Icons.Rounded.FavoriteBorder
                        },
                        contentDescription = if (detail.subscribed) "已收藏歌单" else "收藏歌单",
                        modifier = Modifier.size(20.dp),
                    )
                }
            },
        )
        val subscribeError = state.subscribeError
        if (subscribeError != null) {
            Spacer(Modifier.height(8.dp))
            Text(
                text = subscribeError,
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.error,
            )
        }
    }
}

@Composable
private fun LoadingMoreRow() {
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 40.dp, vertical = 18.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        LinearWavyProgressIndicator(modifier = Modifier.fillMaxWidth())
        Spacer(Modifier.height(10.dp))
        Text(
            text = "正在加载更多",
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
    }
}

@Composable
private fun ListEnd() {
    Text(
        text = "— 已经到底啦 —",
        style = MaterialTheme.typography.labelMedium,
        color = MaterialTheme.colorScheme.onSurfaceVariant,
        textAlign = TextAlign.Center,
        modifier = Modifier
            .fillMaxWidth()
            .padding(vertical = 22.dp),
    )
}

@Composable
private fun InlineError(message: String, onRetry: () -> Unit) {
    val haptics = rememberHaptics()
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 20.dp, vertical = 16.dp)
            .clip(MaterialTheme.shapes.large)
            .background(MaterialTheme.colorScheme.errorContainer)
            .padding(horizontal = 14.dp, vertical = 10.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Icon(
            Icons.Rounded.CloudOff,
            contentDescription = null,
            tint = MaterialTheme.colorScheme.onErrorContainer,
            modifier = Modifier.size(18.dp),
        )
        Spacer(Modifier.width(8.dp))
        Text(
            text = message,
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onErrorContainer,
            maxLines = 2,
            overflow = TextOverflow.Ellipsis,
            modifier = Modifier.weight(1f),
        )
        TextButton(onClick = {
            haptics.tap()
            onRetry()
        }) { Text("重试") }
    }
}

// ---------------------------------------------------------------------------------------------
// Previews
// ---------------------------------------------------------------------------------------------

@Preview(name = "歌单详情 · 浅色", showBackground = true)
@Composable
private fun PlaylistScreenLightPreview() {
    NocturnTheme(themeMode = ThemeMode.Light) {
        NocturnBackdrop {
            PlaylistScreen(state = PreviewData.playlistState)
        }
    }
}

@Preview(name = "歌单详情 · 深色", showBackground = true)
@Composable
private fun PlaylistScreenDarkPreview() {
    NocturnTheme(themeMode = ThemeMode.Dark) {
        NocturnBackdrop {
            PlaylistScreen(state = PreviewData.playlistState)
        }
    }
}
