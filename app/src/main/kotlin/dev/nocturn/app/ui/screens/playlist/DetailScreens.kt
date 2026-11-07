@file:OptIn(
    androidx.compose.material3.ExperimentalMaterial3Api::class,
    androidx.compose.material3.ExperimentalMaterial3ExpressiveApi::class,
)

package dev.nocturn.app.ui.screens.playlist

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
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
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Person
import androidx.compose.material.icons.rounded.PlayArrow
import androidx.compose.material3.AssistChip
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonGroup
import androidx.compose.material3.ButtonGroupDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.LargeFlexibleTopAppBar
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.MediumFlexibleTopAppBar
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import dev.nocturn.app.data.model.AlbumDetail
import dev.nocturn.app.data.model.AlbumSummary
import dev.nocturn.app.data.model.ArtistDetail
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
import dev.nocturn.app.ui.components.pressScale
import dev.nocturn.app.ui.components.rememberHaptics
import dev.nocturn.app.ui.components.scrolledPast
import dev.nocturn.app.ui.preview.PreviewData
import dev.nocturn.app.ui.state.AlbumUiState
import dev.nocturn.app.ui.state.ArtistUiState
import dev.nocturn.app.ui.theme.NocturnTheme
import dev.nocturn.app.ui.util.formatCount
import dev.nocturn.app.ui.util.formatDate

/**
 * 专辑详情：MediumFlexibleTopAppBar（随滚动折叠）+ 大封面 hero + 元信息 + 歌手入口 +
 * ButtonGroup 动作区 + 曲目列表。
 */
@Composable
fun AlbumScreen(
    state: AlbumUiState,
    modifier: Modifier = Modifier,
    onBack: () -> Unit = {},
    onPlayAll: (List<Song>) -> Unit = {},
    onSongClick: (Song, List<Song>) -> Unit = { _, _ -> },
    onLikeClick: (Song) -> Unit = {},
    onArtistClick: (Long) -> Unit = {},
) {
    val detail = state.detail
    if (detail == null) {
        DetailStatus(
            modifier = modifier,
            loading = state.loading,
            error = state.error,
            backTag = "album-back",
            onBack = onBack,
            onRetry = null,
            loadingLabel = "正在打开专辑",
            emptyTitle = "专辑不见了",
            emptyMessage = "它可能已被删除，或者被设为私密。",
        )
        return
    }

    val listState = rememberLazyListState()
    val collapsed by listState.scrolledPast(280)
    GlassScaffold(
        modifier = modifier,
        topBar = {
            GlassTopBar(scrolled = collapsed) {
                BarBackButton(onBack = onBack, testTag = "album-back")
                BarTitle(text = detail.name, visible = collapsed)
            }
        },
    ) { topPadding ->
        LazyColumn(
            state = listState,
            modifier = Modifier
                .fillMaxSize()
                .testTag("album-list"),
            contentPadding = PaddingValues(top = topPadding + 8.dp, bottom = bottomContentPadding(32.dp)),
        ) {
            item(key = "hero") {
                AlbumHero(detail = detail, onArtistClick = onArtistClick)
            }
            item(key = "actions") {
                AlbumActions(detail = detail, onPlayAll = onPlayAll, onArtistClick = onArtistClick)
            }
            item(key = "tracks-header") {
                SectionHeader(
                    title = "曲目",
                    subtitle = "${detail.songs.size} 首",
                    modifier = Modifier.padding(top = 24.dp, bottom = 8.dp),
                )
            }
            if (detail.songs.isEmpty()) {
                item(key = "empty-tracks") {
                    EmptyState(
                        title = "专辑里还没有曲目",
                        message = "换一张专辑试试吧。",
                        modifier = Modifier.padding(top = 24.dp),
                    )
                }
            }
            itemsIndexed(
                items = detail.songs,
                key = { _, song -> song.id },
            ) { index, song ->
                AlbumSongRow(
                    song = song,
                    index = index,
                    songs = detail.songs,
                    state = state,
                    onSongClick = onSongClick,
                    onLikeClick = onLikeClick,
                )
            }
        }
    }
}

@Composable
private fun AlbumSongRow(
    song: Song,
    index: Int,
    songs: List<Song>,
    state: AlbumUiState,
    onSongClick: (Song, List<Song>) -> Unit,
    onLikeClick: (Song) -> Unit,
) {
    SongRow(
        song = song,
        onClick = { onSongClick(song, songs) },
        modifier = Modifier
            .padding(horizontal = 8.dp)
            .testTag("album-song-$index"),
        index = index + 1,
        isPlaying = state.nowPlayingSongId == song.id,
        isLiked = song.id in state.likedSongIds,
        onLikeClick = { onLikeClick(song) },
    )
}

@Composable
private fun AlbumHero(
    detail: AlbumDetail,
    onArtistClick: (Long) -> Unit,
    modifier: Modifier = Modifier,
) {
    val colors = MaterialTheme.colorScheme
    Column(
        modifier = modifier
            .fillMaxWidth()
            .padding(horizontal = 20.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        CoverArt(
            imageUrl = detail.coverUrl,
            modifier = Modifier
                .size(196.dp)
                .shadow(
                    elevation = 22.dp,
                    shape = MaterialTheme.shapes.extraLarge,
                    ambientColor = colors.primary.copy(alpha = 0.38f),
                    spotColor = colors.primary.copy(alpha = 0.44f),
                ),
            shape = MaterialTheme.shapes.extraLarge,
            seed = detail.id.hashCode(),
            contentDescription = detail.name,
        )
        Spacer(Modifier.height(20.dp))
        Text(
            text = detail.name,
            style = MaterialTheme.typography.headlineSmallEmphasized,
            color = colors.onSurface,
            textAlign = TextAlign.Center,
            maxLines = 2,
            overflow = TextOverflow.Ellipsis,
        )
        if (detail.artistText.isNotBlank()) {
            Spacer(Modifier.height(4.dp))
            Text(
                text = detail.artistText,
                style = MaterialTheme.typography.titleSmall,
                color = colors.onSurfaceVariant,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
        }
        Spacer(Modifier.height(14.dp))
        Row(
            horizontalArrangement = Arrangement.spacedBy(8.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            formatDate(detail.publishTime)?.let { date ->
                MetaChip(
                    text = "${date} 发行",
                    containerColor = colors.secondaryContainer,
                    contentColor = colors.onSecondaryContainer,
                )
            }
            val company = detail.company
            if (!company.isNullOrBlank()) {
                MetaChip(text = company)
            }
        }
        if (detail.artists.isNotEmpty()) {
            Spacer(Modifier.height(14.dp))
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                detail.artists.take(3).forEach { artist ->
                    AssistChip(
                        onClick = { onArtistClick(artist.id) },
                        label = { Text(artist.name) },
                        leadingIcon = {
                            Icon(
                                Icons.Rounded.Person,
                                contentDescription = null,
                                modifier = Modifier.size(18.dp),
                            )
                        },
                    )
                }
            }
        }
        val description = detail.description
        if (!description.isNullOrBlank()) {
            Spacer(Modifier.height(16.dp))
            Surface(
                modifier = Modifier.fillMaxWidth(),
                shape = MaterialTheme.shapes.large,
                color = colors.surfaceContainerLow,
            ) {
                Text(
                    text = description,
                    style = MaterialTheme.typography.bodySmall,
                    color = colors.onSurfaceVariant,
                    maxLines = 5,
                    overflow = TextOverflow.Ellipsis,
                    modifier = Modifier.padding(16.dp),
                )
            }
        }
        Spacer(Modifier.height(20.dp))
    }
}

@Composable
private fun AlbumActions(
    detail: AlbumDetail,
    onPlayAll: (List<Song>) -> Unit,
    onArtistClick: (Long) -> Unit,
) {
    val haptics = rememberHaptics()
    ButtonGroup(
        overflowIndicator = { menuState -> ButtonGroupDefaults.OverflowIndicator(menuState) },
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 20.dp)
            .testTag("album-actions"),
    ) {
        clickableItem(
            onClick = {
                haptics.confirm()
                onPlayAll(detail.songs)
            },
            label = "播放全部",
            icon = { Icon(Icons.Rounded.PlayArrow, contentDescription = null) },
            weight = 1.5f,
            enabled = detail.songs.isNotEmpty(),
        )
        clickableItem(
            onClick = {
                haptics.tap()
                detail.artists.firstOrNull()?.let { onArtistClick(it.id) }
            },
            label = "歌手主页",
            icon = { Icon(Icons.Rounded.Person, contentDescription = null) },
            weight = 1f,
            enabled = detail.artists.isNotEmpty(),
        )
    }
}

/**
 * 歌手详情：LargeFlexibleTopAppBar（随滚动折叠）+ 圆形头像 hero + 数据指标 +
 * 热门歌曲列表 + 专辑横向列表。
 */
@Composable
fun ArtistScreen(
    state: ArtistUiState,
    modifier: Modifier = Modifier,
    onBack: () -> Unit = {},
    onSongClick: (Song, List<Song>) -> Unit = { _, _ -> },
    onLikeClick: (Song) -> Unit = {},
    onAlbumClick: (Long) -> Unit = {},
    onPlayAll: (List<Song>) -> Unit = {},
) {
    val detail = state.detail
    if (detail == null) {
        DetailStatus(
            modifier = modifier,
            loading = state.loading,
            error = state.error,
            backTag = "artist-back",
            onBack = onBack,
            onRetry = null,
            loadingLabel = "正在打开歌手页",
            emptyTitle = "找不到这位歌手",
            emptyMessage = "TA 可能已经离开了。",
        )
        return
    }

    val haptics = rememberHaptics()
    val listState = rememberLazyListState()
    val collapsed by listState.scrolledPast(300)
    GlassScaffold(
        modifier = modifier,
        topBar = {
            GlassTopBar(scrolled = collapsed) {
                BarBackButton(onBack = onBack, testTag = "artist-back")
                BarTitle(text = detail.name, visible = collapsed)
            }
        },
    ) { topPadding ->
        LazyColumn(
            state = listState,
            modifier = Modifier
                .fillMaxSize()
                .testTag("artist-list"),
            contentPadding = PaddingValues(top = topPadding + 8.dp, bottom = bottomContentPadding(32.dp)),
        ) {
            item(key = "hero") { ArtistHero(detail = detail) }
            item(key = "actions") {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 20.dp, vertical = 20.dp),
                ) {
                    Button(
                        onClick = {
                            haptics.confirm()
                            onPlayAll(detail.hotSongs)
                        },
                        modifier = Modifier
                            .fillMaxWidth()
                            .testTag("artist-play-all"),
                        enabled = detail.hotSongs.isNotEmpty(),
                    ) {
                        Icon(
                            Icons.Rounded.PlayArrow,
                            contentDescription = null,
                            modifier = Modifier.size(18.dp),
                        )
                        Spacer(Modifier.width(8.dp))
                        Text("播放热门歌曲")
                    }
                }
            }
            item(key = "hot-header") {
                SectionHeader(
                    title = "热门歌曲",
                    subtitle = "${detail.hotSongs.size} 首",
                    modifier = Modifier.padding(top = 4.dp, bottom = 8.dp),
                )
            }
            if (detail.hotSongs.isEmpty()) {
                item(key = "empty-hot") {
                    EmptyState(
                        title = "暂无热门歌曲",
                        message = "稍后再来看看吧。",
                        modifier = Modifier.padding(top = 16.dp),
                    )
                }
            }
            itemsIndexed(
                items = detail.hotSongs,
                key = { _, song -> song.id },
            ) { index, song ->
                SongRow(
                    song = song,
                    onClick = { onSongClick(song, detail.hotSongs) },
                    modifier = Modifier
                        .padding(horizontal = 8.dp)
                        .testTag("artist-song-$index"),
                    index = index + 1,
                    isPlaying = state.nowPlayingSongId == song.id,
                    isLiked = song.id in state.likedSongIds,
                    onLikeClick = { onLikeClick(song) },
                )
            }
            if (detail.albums.isNotEmpty()) {
                item(key = "albums-header") {
                    SectionHeader(
                        title = "专辑",
                        subtitle = "${detail.albumCount} 张",
                        modifier = Modifier.padding(top = 24.dp, bottom = 12.dp),
                    )
                }
                item(key = "albums") {
                    LazyRow(
                        modifier = Modifier
                            .fillMaxWidth()
                            .testTag("artist-albums"),
                        contentPadding = PaddingValues(horizontal = 20.dp),
                        horizontalArrangement = Arrangement.spacedBy(14.dp),
                    ) {
                        items(items = detail.albums, key = { album -> album.id }) { album ->
                            AlbumTile(
                                album = album,
                                onClick = { onAlbumClick(album.id) },
                            )
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun ArtistHero(
    detail: ArtistDetail,
    modifier: Modifier = Modifier,
) {
    val colors = MaterialTheme.colorScheme
    Column(
        modifier = modifier.fillMaxWidth(),
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        Box(
            modifier = Modifier.size(216.dp),
            contentAlignment = Alignment.Center,
        ) {
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .clip(CircleShape)
                    .background(
                        Brush.sweepGradient(
                            listOf(colors.primary, colors.tertiary, colors.secondary, colors.primary),
                        ),
                    ),
            )
            CoverArt(
                imageUrl = detail.avatarUrl ?: detail.coverUrl,
                modifier = Modifier.size(200.dp),
                shape = CircleShape,
                seed = detail.id.hashCode(),
                contentDescription = detail.name,
            )
        }
        Spacer(Modifier.height(18.dp))
        Text(
            text = detail.name,
            style = MaterialTheme.typography.headlineMediumEmphasized,
            color = colors.onSurface,
            textAlign = TextAlign.Center,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
            modifier = Modifier.padding(horizontal = 24.dp),
        )
        Spacer(Modifier.height(12.dp))
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            MetaChip(text = "${detail.albumCount} 张专辑")
            MetaChip(text = "${detail.songCount} 首歌曲")
            MetaChip(
                text = "${formatCount(detail.fansCount)} 粉丝",
                containerColor = colors.secondaryContainer,
                contentColor = colors.onSecondaryContainer,
            )
        }
        val brief = detail.briefDescription
        if (!brief.isNullOrBlank()) {
            Spacer(Modifier.height(14.dp))
            Text(
                text = brief,
                style = MaterialTheme.typography.bodyMedium,
                color = colors.onSurfaceVariant,
                textAlign = TextAlign.Center,
                maxLines = 3,
                overflow = TextOverflow.Ellipsis,
                modifier = Modifier.padding(horizontal = 28.dp),
            )
        }
        Spacer(Modifier.height(18.dp))
    }
}

@Composable
private fun AlbumTile(
    album: AlbumSummary,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val haptics = rememberHaptics()
    Column(
        modifier = modifier
            .width(136.dp)
            .pressScale()
            .clip(MaterialTheme.shapes.large)
            .clickable {
                haptics.tap()
                onClick()
            }
            .testTag("artist-album-${album.id}")
            .padding(bottom = 6.dp),
    ) {
        CoverArt(
            imageUrl = album.coverUrl,
            modifier = Modifier.size(136.dp),
            shape = MaterialTheme.shapes.large,
            seed = album.id.hashCode(),
            contentDescription = album.name,
        )
        Spacer(Modifier.height(10.dp))
        Text(
            text = album.name,
            style = MaterialTheme.typography.titleSmall,
            color = MaterialTheme.colorScheme.onSurface,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
            modifier = Modifier.padding(horizontal = 4.dp),
        )
        if (album.artistText.isNotBlank()) {
            Text(
                text = album.artistText,
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
                modifier = Modifier.padding(horizontal = 4.dp),
            )
        }
    }
}

// ---------------------------------------------------------------------------------------------
// Shared status fallback
// ---------------------------------------------------------------------------------------------

@Composable
private fun DetailStatus(
    modifier: Modifier,
    loading: Boolean,
    error: String?,
    backTag: String,
    onBack: () -> Unit,
    onRetry: (() -> Unit)?,
    loadingLabel: String,
    emptyTitle: String,
    emptyMessage: String,
) {
    GlassScaffold(
        modifier = modifier,
        topBar = {
            GlassTopBar(scrolled = false) {
                BarBackButton(onBack = onBack, testTag = backTag)
            }
        },
    ) { topPadding ->
        Box(
            modifier = Modifier
                .fillMaxSize()
                .padding(top = topPadding, bottom = LocalBottomChromePadding.current),
            contentAlignment = Alignment.Center,
        ) {
            when {
                loading -> ScreenLoading(label = loadingLabel)
                error != null -> ScreenError(message = error, onRetry = onRetry)
                else -> EmptyState(title = emptyTitle, message = emptyMessage)
            }
        }
    }
}

// ---------------------------------------------------------------------------------------------
// Previews
// ---------------------------------------------------------------------------------------------

@Preview(name = "专辑详情 · 浅色", showBackground = true)
@Composable
private fun AlbumScreenLightPreview() {
    NocturnTheme(themeMode = ThemeMode.Light) {
        NocturnBackdrop {
            AlbumScreen(state = PreviewData.albumState)
        }
    }
}

@Preview(name = "专辑详情 · 深色", showBackground = true)
@Composable
private fun AlbumScreenDarkPreview() {
    NocturnTheme(themeMode = ThemeMode.Dark) {
        NocturnBackdrop {
            AlbumScreen(state = PreviewData.albumState)
        }
    }
}

@Preview(name = "歌手详情 · 浅色", showBackground = true)
@Composable
private fun ArtistScreenLightPreview() {
    NocturnTheme(themeMode = ThemeMode.Light) {
        NocturnBackdrop {
            ArtistScreen(state = PreviewData.artistState)
        }
    }
}

@Preview(name = "歌手详情 · 深色", showBackground = true)
@Composable
private fun ArtistScreenDarkPreview() {
    NocturnTheme(themeMode = ThemeMode.Dark) {
        NocturnBackdrop {
            ArtistScreen(state = PreviewData.artistState)
        }
    }
}
