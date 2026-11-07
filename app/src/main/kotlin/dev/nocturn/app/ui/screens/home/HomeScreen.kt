@file:OptIn(
    androidx.compose.material3.ExperimentalMaterial3Api::class,
    androidx.compose.material3.ExperimentalMaterial3ExpressiveApi::class,
)

package dev.nocturn.app.ui.screens.home

import androidx.compose.animation.core.animateDpAsState
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.snapping.SnapPosition
import androidx.compose.foundation.gestures.snapping.rememberSnapFlingBehavior
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.RowScope
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.CloudOff
import androidx.compose.material.icons.rounded.Person
import androidx.compose.material.icons.rounded.PlayArrow
import androidx.compose.material.icons.rounded.Search
import androidx.compose.material3.FilledTonalButton
import androidx.compose.material3.FilledTonalIconButton
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialShapes
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.carousel.HorizontalCenteredHeroCarousel
import androidx.compose.material3.carousel.HorizontalMultiBrowseCarousel
import androidx.compose.material3.carousel.rememberCarouselState
import androidx.compose.material3.pulltorefresh.PullToRefreshBox
import androidx.compose.material3.pulltorefresh.PullToRefreshDefaults
import androidx.compose.material3.pulltorefresh.rememberPullToRefreshState
import androidx.compose.material3.toShape
import androidx.compose.runtime.Composable
import androidx.compose.runtime.derivedStateOf
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import dev.nocturn.app.data.model.PlaylistSummary
import dev.nocturn.app.data.model.Song
import dev.nocturn.app.data.model.ThemeMode
import dev.nocturn.app.data.model.Toplist
import dev.nocturn.app.ui.components.CoverArt
import dev.nocturn.app.ui.components.EmptyState
import dev.nocturn.app.ui.components.GlassScaffold
import dev.nocturn.app.ui.components.GlassTopBar
import dev.nocturn.app.ui.components.HeroCard
import dev.nocturn.app.ui.components.LocalBottomChromePadding
import dev.nocturn.app.ui.components.MetaChip
import dev.nocturn.app.ui.components.NocturnBackdrop
import dev.nocturn.app.ui.components.ScreenError
import dev.nocturn.app.ui.components.ScreenLoading
import dev.nocturn.app.ui.components.SectionHeader
import dev.nocturn.app.ui.components.SongGridPager
import dev.nocturn.app.ui.components.SongRow
import dev.nocturn.app.ui.components.bottomContentPadding
import dev.nocturn.app.ui.components.centreOffsetOf
import dev.nocturn.app.ui.components.rememberHaptics
import dev.nocturn.app.ui.preview.PreviewData
import dev.nocturn.app.ui.state.HomeUiState
import dev.nocturn.app.ui.theme.NocturnTheme
import dev.nocturn.app.ui.util.formatCount
import dev.nocturn.app.ui.util.greetingForHour
import dev.nocturn.app.ui.util.todayLabel
import kotlinx.coroutines.launch

/**
 * 首页发现页。
 *
 * 结构：毛玻璃顶栏（问候 + 日期 + 搜索/账号）→ 竖版 Hero 卡片行（每日推荐 + 各排行榜）→
 * “根据你喜爱的歌曲推荐”分页歌曲网格（无数据时以榜单热歌“热歌速递”兜底）→ 推荐歌单轮播。
 * 每日推荐只占一张卡片，点开进入独立页面。外层是 [PullToRefreshBox]，所有数据来自
 * [HomeUiState]，页面本身不发网络请求。
 */
@Composable
fun HomeScreen(
    state: HomeUiState,
    modifier: Modifier = Modifier,
    onRefresh: () -> Unit = {},
    onPlaylistClick: (Long) -> Unit = {},
    onChartClick: (Toplist) -> Unit = {},
    onSongClick: (Song, List<Song>) -> Unit = { _, _ -> },
    onOpenDaily: () -> Unit = {},
    onPlayAllDaily: () -> Unit = {},
    onSearchClick: () -> Unit = {},
    onAccountClick: () -> Unit = {},
) {
    if (state.loading && !state.hasContent) {
        CenteredStatus(modifier) { ScreenLoading(label = "正在为你准备今晚的歌单") }
        return
    }
    val error = state.error
    if (error != null && !state.hasContent) {
        CenteredStatus(modifier) { ScreenError(message = error, onRetry = onRefresh) }
        return
    }
    if (!state.hasContent) {
        CenteredStatus(modifier) {
            EmptyState(
                title = "今晚暂时没有内容",
                message = "检查一下网络，或者下拉刷新再看看。",
                actionLabel = "刷新",
                onAction = onRefresh,
            )
        }
        return
    }

    HomeFeed(
        state = state,
        modifier = modifier,
        onRefresh = onRefresh,
        onPlaylistClick = onPlaylistClick,
        onChartClick = onChartClick,
        onSongClick = onSongClick,
        onOpenDaily = onOpenDaily,
        onPlayAllDaily = onPlayAllDaily,
        onSearchClick = onSearchClick,
        onAccountClick = onAccountClick,
    )
}

@Composable
private fun CenteredStatus(modifier: Modifier, content: @Composable () -> Unit) {
    Box(
        modifier = modifier
            .fillMaxSize()
            .padding(bottom = LocalBottomChromePadding.current),
        contentAlignment = Alignment.Center,
    ) {
        content()
    }
}

@Composable
private fun HomeFeed(
    state: HomeUiState,
    modifier: Modifier,
    onRefresh: () -> Unit,
    onPlaylistClick: (Long) -> Unit,
    onChartClick: (Toplist) -> Unit,
    onSongClick: (Song, List<Song>) -> Unit,
    onOpenDaily: () -> Unit,
    onPlayAllDaily: () -> Unit,
    onSearchClick: () -> Unit,
    onAccountClick: () -> Unit,
) {
    val listState = rememberLazyListState()
    val today = remember { todayLabel() }
    val greeting = state.greeting.ifBlank { greetingForHour() }
    val refreshState = rememberPullToRefreshState()
    val scrolled by remember { derivedStateOf { listState.canScrollBackward } }
    // "根据你喜爱的歌曲推荐" fills the paged grid; without it, de-duplicated chart tracks stand in
    // under the honest "热歌速递" title. Daily songs live behind their own hero card.
    val personalizedGrid = state.personalizedSongs.isNotEmpty()
    val gridSongs = remember(state.personalizedSongs, state.charts) {
        state.personalizedSongs.ifEmpty { state.charts.flatMap { it.tracks } }
            .distinctBy { it.id }
            .take(12)
    }

    GlassScaffold(
        modifier = modifier,
        topBar = {
            GlassTopBar(scrolled = scrolled) {
                HomeBarContent(
                    greeting = greeting,
                    subtitle = today,
                    onSearchClick = onSearchClick,
                    onAccountClick = onAccountClick,
                )
            }
        },
    ) { topPadding ->
        PullToRefreshBox(
            isRefreshing = state.refreshing,
            onRefresh = onRefresh,
            modifier = Modifier.fillMaxSize(),
            state = refreshState,
            indicator = {
                PullToRefreshDefaults.LoadingIndicator(
                    state = refreshState,
                    isRefreshing = state.refreshing,
                    modifier = Modifier
                        .align(Alignment.TopCenter)
                        .padding(top = topPadding),
                )
            },
        ) {
            LazyColumn(
                state = listState,
                modifier = Modifier
                    .fillMaxSize()
                    .testTag("home-feed"),
                contentPadding = PaddingValues(top = topPadding + 8.dp, bottom = bottomContentPadding()),
            ) {
                item(key = "hero") {
                    HomeHeroRow(
                        state = state,
                        onOpenDaily = onOpenDaily,
                        onPlayAllDaily = onPlayAllDaily,
                        onChartClick = onChartClick,
                        onAccountClick = onAccountClick,
                    )
                }

                if (gridSongs.isNotEmpty()) {
                    item(key = "song-grid-header") {
                        // `/personalized/newsong` only becomes "based on songs you like" once the
                        // account cookie personalises it; anonymous listeners get a generic new
                        // song shelf, so the header must not promise personalisation.
                        SectionHeader(
                            title = when {
                                !personalizedGrid -> "热歌速递"
                                state.isLoggedIn -> "根据你喜爱的歌曲推荐"
                                else -> "新歌推荐"
                            },
                            subtitle = when {
                                !personalizedGrid -> "各大榜单此刻最热"
                                state.isLoggedIn -> "为你挑选的新歌"
                                else -> "登录后按你的口味挑选"
                            },
                            modifier = Modifier.padding(top = 28.dp, bottom = 8.dp),
                        )
                    }
                    item(key = "song-grid") {
                        SongGridPager(
                            songs = gridSongs,
                            onSongClick = { song -> onSongClick(song, gridSongs) },
                            nowPlayingSongId = state.nowPlayingSongId,
                            modifier = Modifier.testTag("home-song-grid"),
                        )
                    }
                }

                if (state.recommendedPlaylists.isNotEmpty()) {
                    item(key = "playlists-header") {
                        SectionHeader(
                            title = "为你推荐",
                            subtitle = "按你的口味挑选的歌单",
                            modifier = Modifier.padding(top = 28.dp, bottom = 14.dp),
                        )
                    }
                    item(key = "playlists") {
                        PlaylistCarousel(
                            playlists = state.recommendedPlaylists,
                            onPlaylistClick = onPlaylistClick,
                        )
                    }
                }

                val inlineError = state.error
                if (inlineError != null) {
                    item(key = "inline-error") {
                        InlineError(message = inlineError, onRetry = onRefresh)
                    }
                }

                item(key = "footer") { Spacer(Modifier.height(8.dp)) }
            }
        }
    }
}

/** Greeting, date and the two quick actions, pinned in the frosted home bar. */
@Composable
private fun RowScope.HomeBarContent(
    greeting: String,
    subtitle: String,
    onSearchClick: () -> Unit,
    onAccountClick: () -> Unit,
) {
    val haptics = rememberHaptics()
    Column(
        modifier = Modifier
            .weight(1f)
            .padding(start = 16.dp),
    ) {
        Text(
            text = greeting,
            style = MaterialTheme.typography.headlineSmallEmphasized,
            color = MaterialTheme.colorScheme.onSurface,
            modifier = Modifier.testTag("home-greeting"),
        )
        Text(
            text = subtitle,
            style = MaterialTheme.typography.labelMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
    }
    IconButton(
        onClick = {
            haptics.tap()
            onSearchClick()
        },
        modifier = Modifier.testTag("home-search"),
    ) {
        Icon(Icons.Rounded.Search, contentDescription = "搜索")
    }
    IconButton(
        onClick = {
            haptics.tap()
            onAccountClick()
        },
        modifier = Modifier.testTag("home-account"),
    ) {
        Icon(Icons.Rounded.Person, contentDescription = "我的账号")
    }
}

// ---------------------------------------------------------------------------------------------
// Hero row: daily recommendation + charts as portrait cards
// ---------------------------------------------------------------------------------------------

/**
 * The top of the feed: a snapping row of portrait cards. Daily recommendations are one card (tap
 * opens the full list, the play glyph plays it); signed out, that slot invites login. Every chart
 * follows as its own card. Artwork drifts with a light parallax while the row scrolls.
 */
@Composable
private fun HomeHeroRow(
    state: HomeUiState,
    onOpenDaily: () -> Unit,
    onPlayAllDaily: () -> Unit,
    onChartClick: (Toplist) -> Unit,
    onAccountClick: () -> Unit,
) {
    val rowState = rememberLazyListState()
    val cardModifier = Modifier.size(width = 172.dp, height = 232.dp)
    LazyRow(
        state = rowState,
        modifier = Modifier
            .fillMaxWidth()
            .testTag("home-hero"),
        contentPadding = PaddingValues(horizontal = 16.dp),
        horizontalArrangement = Arrangement.spacedBy(12.dp),
        flingBehavior = rememberSnapFlingBehavior(rowState, SnapPosition.Start),
    ) {
        val daily = state.dailySongs
        when {
            daily.isNotEmpty() -> item(key = "daily") {
                HeroCard(
                    title = "每日推荐",
                    subtitle = "今日为你挑选 ${daily.size} 首",
                    imageUrl = daily.first().cover,
                    seed = daily.first().id.hashCode(),
                    onClick = onOpenDaily,
                    onPlay = onPlayAllDaily,
                    parallax = { rowState.layoutInfo.centreOffsetOf("daily") },
                    modifier = cardModifier.testTag("home-hero-daily"),
                )
            }

            !state.isLoggedIn -> item(key = "login") {
                HeroCard(
                    title = "每日推荐",
                    subtitle = "登录网易云音乐",
                    imageUrl = null,
                    seed = 20_241,
                    onClick = onAccountClick,
                    parallax = { rowState.layoutInfo.centreOffsetOf("login") },
                    modifier = cardModifier.testTag("home-login-prompt"),
                )
            }
        }
        items(items = state.charts, key = { "chart-${it.id}" }) { chart ->
            HeroCard(
                title = chart.name,
                subtitle = chart.updateFrequency?.takeIf { it.isNotBlank() }
                    ?: chart.description?.takeIf { it.isNotBlank() }
                    ?: "${chart.trackCount} 首歌曲",
                imageUrl = chart.coverUrl,
                seed = chart.id.hashCode(),
                onClick = { onChartClick(chart) },
                parallax = { rowState.layoutInfo.centreOffsetOf("chart-${chart.id}") },
                modifier = cardModifier.testTag("home-hero-chart-${chart.id}"),
            )
        }
    }
}

// Recommended playlists carousel
// ---------------------------------------------------------------------------------------------

@Composable
private fun PlaylistCarousel(
    playlists: List<PlaylistSummary>,
    onPlaylistClick: (Long) -> Unit,
) {
    val carouselState = rememberCarouselState(itemCount = { playlists.size })
    HorizontalMultiBrowseCarousel(
        state = carouselState,
        preferredItemWidth = 176.dp,
        modifier = Modifier
            .fillMaxWidth()
            .height(238.dp)
            .testTag("home-playlists"),
        itemSpacing = 12.dp,
        minSmallItemWidth = 64.dp,
        maxSmallItemWidth = 88.dp,
        contentPadding = PaddingValues(horizontal = 20.dp),
    ) { index ->
        val playlist = playlists[index]
        PlaylistCarouselCard(
            playlist = playlist,
            isFocal = carouselState.currentItem == index,
            onClick = { onPlaylistClick(playlist.id) },
            modifier = Modifier.maskClip(MaterialTheme.shapes.large),
        )
    }
}

@Composable
private fun PlaylistCarouselCard(
    playlist: PlaylistSummary,
    isFocal: Boolean,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val haptics = rememberHaptics()
    BoxWithConstraints(
        modifier = modifier
            .fillMaxSize()
            .clip(MaterialTheme.shapes.large)
            .clickable {
                haptics.tap()
                onClick()
            },
    ) {
        CoverArt(
            imageUrl = playlist.coverUrl,
            modifier = Modifier.fillMaxSize(),
            shape = MaterialTheme.shapes.large,
            seed = playlist.id.hashCode(),
            contentDescription = playlist.name,
        )
        Box(
            modifier = Modifier
                .fillMaxSize()
                .background(
                    Brush.verticalGradient(
                        0.55f to Color.Transparent,
                        1f to Color.Black.copy(alpha = 0.72f),
                    ),
                ),
        )
        if (isFocal && maxWidth > 132.dp) {
            Column(
                modifier = Modifier
                    .align(Alignment.BottomStart)
                    .padding(12.dp),
            ) {
                Text(
                    text = playlist.name,
                    style = MaterialTheme.typography.titleSmallEmphasized,
                    color = Color.White,
                    maxLines = 2,
                    overflow = TextOverflow.Ellipsis,
                )
                Spacer(Modifier.height(4.dp))
                MetaChip(
                    text = "${formatCount(playlist.playCount)} 次播放",
                    containerColor = Color.White.copy(alpha = 0.2f),
                    contentColor = Color.White,
                )
            }
        }
    }
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
            textAlign = TextAlign.Start,
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

@Preview(name = "首页 · 浅色", showBackground = true)
@Composable
private fun HomeScreenLightPreview() {
    NocturnTheme(themeMode = ThemeMode.Light) {
        NocturnBackdrop {
            HomeScreen(state = PreviewData.homeState)
        }
    }
}

@Preview(name = "首页 · 深色", showBackground = true)
@Composable
private fun HomeScreenDarkPreview() {
    NocturnTheme(themeMode = ThemeMode.Dark) {
        NocturnBackdrop {
            HomeScreen(state = PreviewData.homeState)
        }
    }
}

@Preview(name = "首页 · 未登录", showBackground = true)
@Composable
private fun HomeScreenAnonymousPreview() {
    NocturnTheme(themeMode = ThemeMode.Light) {
        NocturnBackdrop {
            HomeScreen(state = PreviewData.homeStateAnonymous)
        }
    }
}