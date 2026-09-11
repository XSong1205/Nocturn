package com.nocturn.music.ui.screens

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
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
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.derivedStateOf
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.nocturn.music.data.repository.MusicRepository
import com.nocturn.music.data.repository.SettingsRepository
import com.nocturn.music.model.Playlist
import com.nocturn.music.model.Song
import androidx.compose.ui.graphics.vector.ImageVector
import com.nocturn.music.player.NocturnPlayer
import com.nocturn.music.ui.components.AsyncImage
import com.nocturn.music.ui.components.SongListItem
import com.nocturn.music.ui.navigation.SecondaryRoute
import com.nocturn.music.ui.theme.AppIcons
import com.nocturn.music.ui.theme.HyperBlue
import com.nocturn.music.ui.theme.HyperRed
import com.nocturn.music.ui.theme.squircleCard
import kotlinx.coroutines.launch
import top.yukonga.miuix.kmp.basic.Button
import top.yukonga.miuix.kmp.basic.Card
import top.yukonga.miuix.kmp.basic.Icon
import top.yukonga.miuix.kmp.basic.IconButton
import top.yukonga.miuix.kmp.basic.Scaffold
import top.yukonga.miuix.kmp.basic.SmallTopAppBar
import top.yukonga.miuix.kmp.basic.Text
import top.yukonga.miuix.kmp.basic.TextField
import top.yukonga.miuix.kmp.icon.MiuixIcons
import top.yukonga.miuix.kmp.icon.extended.Back
import top.yukonga.miuix.kmp.icon.extended.Refresh
import top.yukonga.miuix.kmp.icon.extended.Search
import top.yukonga.miuix.kmp.theme.MiuixTheme

@Composable
fun PlaylistDetailScreen(
    playlistId: Long,
    onBack: () -> Unit,
    onNavigateToRoute: (SecondaryRoute) -> Unit = {},
    modifier: Modifier = Modifier
) {
    PlaylistDetailScreen(
        route = SecondaryRoute.Playlist(playlistId),
        onBack = onBack,
        onNavigateToRoute = onNavigateToRoute,
        modifier = modifier
    )
}

@OptIn(ExperimentalLayoutApi::class)
@Composable
fun PlaylistDetailScreen(
    route: SecondaryRoute,
    onBack: () -> Unit,
    onNavigateToRoute: (SecondaryRoute) -> Unit = {},
    modifier: Modifier = Modifier
) {
    var remotePlaylist by remember { mutableStateOf<Playlist?>(null) }
    var isLoading by remember { mutableStateOf(true) }
    var loadError by remember { mutableStateOf<String?>(null) }

    var searchQuery by remember { mutableStateOf("") }
    var isSearchActive by remember { mutableStateOf(false) }
    var isDescriptionExpanded by remember { mutableStateOf(false) }
    var selectedSongForMenu by remember { mutableStateOf<Song?>(null) }

    val favoriteSongs by SettingsRepository.favoriteSongs.collectAsState()
    val userProfile by SettingsRepository.userProfile.collectAsState()
    val currentPlayingSong by NocturnPlayer.currentSong.collectAsState()
    val isPlaying by NocturnPlayer.isPlaying.collectAsState()
    val scope = rememberCoroutineScope()

    val isFavoriteMode = route is SecondaryRoute.Favorites

    val playlist: Playlist? = if (isFavoriteMode) {
        MusicRepository.getFavoriteSongsPlaylist(favoriteSongs, userProfile.nickname.ifBlank { "我" })
    } else {
        remotePlaylist
    }

    fun loadData(force: Boolean = false) {
        if (isFavoriteMode) {
            isLoading = false
            loadError = null
            return
        }
        scope.launch {
            isLoading = true
            loadError = null
            try {
                val fetched = when (route) {
                    is SecondaryRoute.Playlist -> MusicRepository.getPlaylistDetail(route.id, force)
                    is SecondaryRoute.Album -> {
                        if (route.id > 0) {
                            MusicRepository.getAlbumDetail(route.id, force)
                        } else {
                            val albums = MusicRepository.searchAlbums(route.initialName ?: "")
                            if (albums.isNotEmpty()) {
                                MusicRepository.getAlbumDetail(albums.first().id, force)
                            } else null
                        }
                    }
                    is SecondaryRoute.Artist -> {
                        if (route.id > 0) {
                            MusicRepository.getArtistDetail(route.id, force)
                        } else {
                            val artists = MusicRepository.searchArtists(route.name)
                            if (artists.isNotEmpty()) {
                                MusicRepository.getArtistDetail(artists.first().id, force)
                            } else null
                        }
                    }
                    is SecondaryRoute.DailyRecommend -> MusicRepository.getDailyRecommendPlaylist()
                    is SecondaryRoute.Favorites -> null
                    is SecondaryRoute.TopChartsSquare -> null
                    is SecondaryRoute.About -> null
                }
                if (fetched != null) {
                    remotePlaylist = fetched
                } else {
                    loadError = "未能加载到歌曲列表，请重试"
                }
            } catch (e: Exception) {
                e.printStackTrace()
                loadError = e.message ?: "网络加载失败"
            } finally {
                isLoading = false
            }
        }
    }

    LaunchedEffect(route) {
        loadData(force = false)
    }

    val displayTracks = remember(playlist?.tracks, searchQuery) {
        val all = playlist?.tracks ?: emptyList()
        if (searchQuery.isBlank()) {
            all
        } else {
            val q = searchQuery.trim().lowercase()
            all.filter {
                it.title.lowercase().contains(q) ||
                it.artist.lowercase().contains(q) ||
                it.album.lowercase().contains(q)
            }
        }
    }

    val screenTitle = when (route) {
        is SecondaryRoute.Playlist -> playlist?.name ?: route.initialName ?: "歌单详情"
        is SecondaryRoute.Album -> playlist?.name ?: route.initialName ?: "专辑详情"
        is SecondaryRoute.Artist -> playlist?.name ?: "${route.name} 的热门歌曲"
        is SecondaryRoute.Favorites -> "我喜欢的音乐"
        is SecondaryRoute.DailyRecommend -> playlist?.name ?: "每日推荐"
        is SecondaryRoute.TopChartsSquare -> "官方排行榜"
        is SecondaryRoute.About -> "关于应用"
    }

    val lazyListState = rememberLazyListState()

    val scrollProgress by remember {
        derivedStateOf {
            if (playlist == null) {
                1f
            } else if (lazyListState.firstVisibleItemIndex > 0) {
                1f
            } else {
                (lazyListState.firstVisibleItemScrollOffset / 200f).coerceIn(0f, 1f)
            }
        }
    }

    Scaffold(
        topBar = {
            SmallTopAppBar(
                title = screenTitle,
                color = if (scrollProgress > 0.05f) MiuixTheme.colorScheme.surface else MiuixTheme.colorScheme.surface.copy(alpha = 0.95f),
                navigationIcon = {
                    IconButton(
                        onClick = onBack,
                        modifier = Modifier.size(40.dp)
                    ) {
                        Icon(
                            imageVector = MiuixIcons.Back,
                            contentDescription = "返回",
                            tint = MiuixTheme.colorScheme.onSurface
                        )
                    }
                },
                actions = {
                    IconButton(
                        onClick = {
                            isSearchActive = !isSearchActive
                            if (!isSearchActive) searchQuery = ""
                        },
                        modifier = Modifier.size(40.dp)
                    ) {
                        Icon(
                            imageVector = MiuixIcons.Search,
                            contentDescription = "歌单内搜索",
                            tint = if (isSearchActive) HyperBlue else MiuixTheme.colorScheme.onSurface
                        )
                    }

                    if (!isFavoriteMode) {
                        IconButton(
                            onClick = { loadData(force = true) },
                            modifier = Modifier.size(40.dp)
                        ) {
                            Icon(
                                imageVector = MiuixIcons.Refresh,
                                contentDescription = "刷新",
                                tint = MiuixTheme.colorScheme.onSurface
                            )
                        }
                    }
                }
            )
        },
        modifier = modifier.fillMaxSize()
    ) { padding ->
        Box(
            modifier = Modifier
                .fillMaxSize()
                .padding(padding)
        ) {
            val pl = playlist
            if (pl == null && isLoading) {
                Box(
                    modifier = Modifier.fillMaxSize(),
                    contentAlignment = Alignment.Center
                ) {
                    Column(horizontalAlignment = Alignment.CenterHorizontally) {
                        Text(
                            text = "正在获取曲目信息...",
                            color = MiuixTheme.colorScheme.onSurfaceSecondary,
                            fontSize = 15.sp
                        )
                    }
                }
            } else if (pl == null || loadError != null) {
                Box(
                    modifier = Modifier.fillMaxSize(),
                    contentAlignment = Alignment.Center
                ) {
                    Column(
                        horizontalAlignment = Alignment.CenterHorizontally,
                        modifier = Modifier.padding(24.dp)
                    ) {
                        Icon(
                            imageVector = AppIcons.Close,
                            contentDescription = null,
                            tint = MiuixTheme.colorScheme.onSurfaceSecondary,
                            modifier = Modifier.size(36.dp)
                        )
                        Spacer(modifier = Modifier.height(12.dp))
                        Text(
                            text = loadError ?: "歌单加载失败",
                            color = MiuixTheme.colorScheme.onSurface,
                            fontSize = 15.sp,
                            fontWeight = FontWeight.Medium
                        )
                        Spacer(modifier = Modifier.height(16.dp))
                        Button(
                            onClick = { loadData(force = true) },
                            modifier = Modifier.height(44.dp)
                        ) {
                            Text(text = "重新加载")
                        }
                    }
                }
            } else {
                val bottomBarPadding = com.nocturn.music.ui.navigation.LocalBottomBarPadding.current
                LazyColumn(
                    state = lazyListState,
                    modifier = Modifier.fillMaxSize(),
                    contentPadding = PaddingValues(bottom = maxOf(bottomBarPadding + 16.dp, 24.dp))
                ) {
                    // 1. 顶部 Header 区域 (封面、信息、简介)
                    item {
                        Column(
                            modifier = Modifier
                                .fillMaxWidth()
                                .background(
                                    Brush.verticalGradient(
                                        colors = listOf(
                                            MiuixTheme.colorScheme.surfaceContainerHighest.copy(alpha = 0.5f),
                                            Color.Transparent
                                        )
                                    )
                                )
                                .padding(16.dp)
                        ) {
                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                verticalAlignment = Alignment.Top
                            ) {
                                Box(
                                    modifier = Modifier
                                        .size(122.dp)
                                        .squircleCard(18.dp)
                                        .background(MiuixTheme.colorScheme.surfaceContainer)
                                ) {
                                    if (isFavoriteMode) {
                                        Box(
                                            modifier = Modifier
                                                .fillMaxSize()
                                                .background(
                                                    Brush.linearGradient(
                                                        listOf(Color(0xFFE53935), Color(0xFFFF7043))
                                                    )
                                                ),
                                            contentAlignment = Alignment.Center
                                        ) {
                                            Icon(
                                                imageVector = AppIcons.FavoritesFill,
                                                contentDescription = null,
                                                tint = Color.White,
                                                modifier = Modifier.size(52.dp)
                                            )
                                        }
                                    } else {
                                        AsyncImage(
                                            url = pl.coverUrl,
                                            contentDescription = pl.name,
                                            modifier = Modifier.fillMaxSize()
                                        )
                                    }
                                }

                                Spacer(modifier = Modifier.width(16.dp))

                                Column(modifier = Modifier.weight(1f)) {
                                    Text(
                                        text = pl.name,
                                        color = MiuixTheme.colorScheme.onSurface,
                                        fontSize = 18.sp,
                                        fontWeight = FontWeight.Bold,
                                        maxLines = 2,
                                        overflow = TextOverflow.Ellipsis
                                    )

                                    if (pl.creatorName.isNotBlank()) {
                                        Spacer(modifier = Modifier.height(8.dp))
                                        Row(
                                            verticalAlignment = Alignment.CenterVertically,
                                            modifier = Modifier.clickable {
                                                if (route !is SecondaryRoute.Artist) {
                                                    onNavigateToRoute(SecondaryRoute.Artist(0L, pl.creatorName))
                                                }
                                            }
                                        ) {
                                            if (pl.creatorAvatarUrl.isNotBlank()) {
                                                Box(
                                                    modifier = Modifier
                                                        .size(22.dp)
                                                        .clip(CircleShape)
                                                ) {
                                                    AsyncImage(
                                                        url = pl.creatorAvatarUrl,
                                                        contentDescription = pl.creatorName,
                                                        modifier = Modifier.fillMaxSize()
                                                    )
                                                }
                                                Spacer(modifier = Modifier.width(6.dp))
                                            }
                                            Text(
                                                text = pl.creatorName,
                                                color = MiuixTheme.colorScheme.onSurfaceSecondary,
                                                fontSize = 13.sp,
                                                maxLines = 1,
                                                overflow = TextOverflow.Ellipsis
                                            )
                                        }
                                    }

                                    Spacer(modifier = Modifier.height(6.dp))
                                    Row(verticalAlignment = Alignment.CenterVertically) {
                                        if (pl.playCount > 0) {
                                            Text(
                                                text = "${pl.playCountFormatted} 播放 · ",
                                                color = MiuixTheme.colorScheme.onSurfaceSecondary.copy(alpha = 0.7f),
                                                fontSize = 12.sp
                                            )
                                        }
                                        Text(
                                            text = "共 ${pl.tracks.size} 首歌曲",
                                            color = MiuixTheme.colorScheme.onSurfaceSecondary.copy(alpha = 0.7f),
                                            fontSize = 12.sp
                                        )
                                    }

                                    if (pl.tags.isNotEmpty()) {
                                        Spacer(modifier = Modifier.height(6.dp))
                                        FlowRow(
                                            horizontalArrangement = Arrangement.spacedBy(6.dp),
                                            verticalArrangement = Arrangement.spacedBy(4.dp)
                                        ) {
                                            pl.tags.take(4).forEach { tag ->
                                                Box(
                                                    modifier = Modifier
                                                        .clip(RoundedCornerShape(6.dp))
                                                        .background(MiuixTheme.colorScheme.surfaceContainerHighest)
                                                        .padding(horizontal = 6.dp, vertical = 2.dp)
                                                ) {
                                                    Text(
                                                        text = tag,
                                                        color = MiuixTheme.colorScheme.onSurfaceSecondary,
                                                        fontSize = 10.sp
                                                    )
                                                }
                                            }
                                        }
                                    }
                                }
                            }

                            if (pl.description.isNotBlank()) {
                                Spacer(modifier = Modifier.height(10.dp))
                                Column(
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .clip(RoundedCornerShape(8.dp))
                                        .clickable { isDescriptionExpanded = !isDescriptionExpanded }
                                        .padding(vertical = 4.dp)
                                ) {
                                    Text(
                                        text = pl.description,
                                        color = MiuixTheme.colorScheme.onSurfaceSecondary.copy(alpha = 0.75f),
                                        fontSize = 12.sp,
                                        lineHeight = 16.sp,
                                        maxLines = if (isDescriptionExpanded) Int.MAX_VALUE else 2,
                                        overflow = TextOverflow.Ellipsis
                                    )
                                    if (pl.description.length > 50) {
                                        Text(
                                            text = if (isDescriptionExpanded) "收起" else "展开详情",
                                            color = HyperBlue,
                                            fontSize = 11.sp,
                                            modifier = Modifier.padding(top = 2.dp)
                                        )
                                    }
                                }
                            }
                        }
                    }

                    // 2. 歌单内搜索条 (展开时可见)
                    if (isSearchActive) {
                        item {
                            Row(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .padding(horizontal = 16.dp, vertical = 6.dp),
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                TextField(
                                    value = searchQuery,
                                    onValueChange = { searchQuery = it },
                                    label = "在歌单内搜索歌曲、歌手或专辑",
                                    modifier = Modifier.weight(1f)
                                )
                                if (searchQuery.isNotBlank()) {
                                    Spacer(modifier = Modifier.width(8.dp))
                                    Box(
                                        modifier = Modifier
                                            .clip(CircleShape)
                                            .clickable { searchQuery = "" }
                                            .padding(6.dp)
                                    ) {
                                        Icon(
                                            imageVector = AppIcons.Close,
                                            contentDescription = "清除",
                                            tint = MiuixTheme.colorScheme.onSurfaceSecondary,
                                            modifier = Modifier.size(16.dp)
                                        )
                                    }
                                }
                            }
                        }
                    }

                    // 3. 操作栏 (播放全部、随机播放、筛选统计)
                    item {
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(horizontal = 16.dp, vertical = 10.dp),
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            // 播放全部按钮
                            Row(
                                modifier = Modifier
                                    .clip(RoundedCornerShape(20.dp))
                                    .background(HyperBlue)
                                    .clickable {
                                        if (displayTracks.isNotEmpty()) {
                                            NocturnPlayer.playQueue(displayTracks, 0)
                                        }
                                    }
                                    .padding(horizontal = 16.dp, vertical = 8.dp),
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Icon(
                                    imageVector = AppIcons.Play,
                                    contentDescription = null,
                                    tint = Color.White,
                                    modifier = Modifier.size(14.dp)
                                )
                                Spacer(modifier = Modifier.width(6.dp))
                                Text(
                                    text = "播放全部",
                                    color = Color.White,
                                    fontSize = 14.sp,
                                    fontWeight = FontWeight.Bold
                                )
                                Text(
                                    text = " (${displayTracks.size})",
                                    color = Color.White.copy(alpha = 0.85f),
                                    fontSize = 12.sp
                                )
                            }

                            // 随机播放按钮
                            Row(
                                modifier = Modifier
                                    .clip(RoundedCornerShape(20.dp))
                                    .background(MiuixTheme.colorScheme.surfaceContainerHighest)
                                    .clickable {
                                        if (displayTracks.isNotEmpty()) {
                                            NocturnPlayer.playQueueShuffled(displayTracks)
                                        }
                                    }
                                    .padding(horizontal = 14.dp, vertical = 8.dp),
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Icon(
                                    imageVector = AppIcons.Shuffle,
                                    contentDescription = null,
                                    tint = MiuixTheme.colorScheme.onSurface,
                                    modifier = Modifier.size(14.dp)
                                )
                                Spacer(modifier = Modifier.width(6.dp))
                                Text(
                                    text = "随机播放",
                                    color = MiuixTheme.colorScheme.onSurface,
                                    fontSize = 13.sp,
                                    fontWeight = FontWeight.Medium
                                )
                            }
                        }
                    }

                    // 4. 曲目列表
                    if (displayTracks.isEmpty()) {
                        item {
                            Box(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .padding(vertical = 40.dp),
                                contentAlignment = Alignment.Center
                            ) {
                                Text(
                                    text = if (searchQuery.isNotBlank()) "未搜索到匹配「$searchQuery」的歌曲" else "歌单暂无歌曲",
                                    color = MiuixTheme.colorScheme.onSurfaceSecondary.copy(alpha = 0.6f),
                                    fontSize = 14.sp
                                )
                            }
                        }
                    } else {
                        itemsIndexed(displayTracks) { index, song ->
                            SongListItem(
                                song = song,
                                onClick = {
                                    NocturnPlayer.playQueue(displayTracks, index)
                                },
                                isPlaying = currentPlayingSong?.id == song.id && isPlaying,
                                index = index + 1,
                                showCover = isFavoriteMode,
                                onMoreClick = {
                                    selectedSongForMenu = song
                                }
                            )
                        }
                    }
                }
            }

            // 5. 单曲操作底部浮层 (Song Action Menu)
            val menuSong = selectedSongForMenu
            if (menuSong != null) {
                Box(
                    modifier = Modifier
                        .fillMaxSize()
                        .background(Color.Black.copy(alpha = 0.5f))
                        .clickable { selectedSongForMenu = null },
                    contentAlignment = Alignment.BottomCenter
                ) {
                    Card(
                        modifier = Modifier
                            .fillMaxWidth()
                            .squircleCard(24.dp)
                            .clickable(enabled = false) {}
                            .padding(16.dp)
                    ) {
                        Column(modifier = Modifier.padding(16.dp)) {
                            // 头部单曲信息
                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                if (menuSong.coverUrl.isNotBlank()) {
                                    Box(
                                        modifier = Modifier
                                            .size(46.dp)
                                            .squircleCard(10.dp)
                                    ) {
                                        AsyncImage(
                                            url = menuSong.coverUrl,
                                            contentDescription = menuSong.title,
                                            modifier = Modifier.fillMaxSize()
                                        )
                                    }
                                    Spacer(modifier = Modifier.width(12.dp))
                                }

                                Column(modifier = Modifier.weight(1f)) {
                                    Text(
                                        text = menuSong.title,
                                        color = MiuixTheme.colorScheme.onSurface,
                                        fontSize = 16.sp,
                                        fontWeight = FontWeight.Bold,
                                        maxLines = 1,
                                        overflow = TextOverflow.Ellipsis
                                    )
                                    Text(
                                        text = "${menuSong.artist} · ${menuSong.album}",
                                        color = MiuixTheme.colorScheme.onSurfaceSecondary,
                                        fontSize = 12.sp,
                                        maxLines = 1,
                                        overflow = TextOverflow.Ellipsis
                                    )
                                }
                            }

                            Spacer(modifier = Modifier.height(16.dp))

                            val isFav = SettingsRepository.isFavorite(menuSong.id)

                            SongMenuActionItem(
                                icon = AppIcons.Play,
                                title = "立即播放",
                                onClick = {
                                    NocturnPlayer.playSong(menuSong)
                                    selectedSongForMenu = null
                                }
                            )

                            SongMenuActionItem(
                                icon = AppIcons.SkipNext,
                                title = "下一首播放",
                                onClick = {
                                    NocturnPlayer.playNextInQueue(menuSong)
                                    selectedSongForMenu = null
                                }
                            )

                            SongMenuActionItem(
                                icon = AppIcons.Playlist,
                                title = "添加到播放列表",
                                onClick = {
                                    NocturnPlayer.addToQueue(menuSong)
                                    selectedSongForMenu = null
                                }
                            )

                            SongMenuActionItem(
                                icon = if (isFav) AppIcons.FavoritesFill else AppIcons.Favorites,
                                iconTint = if (isFav) HyperRed else MiuixTheme.colorScheme.onSurface,
                                title = if (isFav) "从「我喜欢的音乐」中移除" else "收藏到「我喜欢的音乐」",
                                onClick = {
                                    SettingsRepository.toggleFavorite(menuSong)
                                    selectedSongForMenu = null
                                }
                            )

                            if (menuSong.artist.isNotBlank() && menuSong.artist != "未知歌手") {
                                SongMenuActionItem(
                                    icon = AppIcons.Contacts,
                                    title = "查看歌手: ${menuSong.artist}",
                                    onClick = {
                                        val firstArtist = menuSong.artist.split("/", "&", ",").first().trim()
                                        selectedSongForMenu = null
                                        onNavigateToRoute(SecondaryRoute.Artist(0L, firstArtist))
                                    }
                                )
                            }

                            if (menuSong.album.isNotBlank()) {
                                SongMenuActionItem(
                                    icon = AppIcons.Album,
                                    title = "查看专辑: ${menuSong.album}",
                                    onClick = {
                                        selectedSongForMenu = null
                                        onNavigateToRoute(SecondaryRoute.Album(0L, menuSong.album))
                                    }
                                )
                            }
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun SongMenuActionItem(
    icon: ImageVector,
    title: String,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    iconTint: Color = MiuixTheme.colorScheme.onSurface
) {
    Row(
        modifier = modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(12.dp))
            .clickable(onClick = onClick)
            .padding(horizontal = 12.dp, vertical = 12.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Icon(
            imageVector = icon,
            contentDescription = null,
            tint = iconTint,
            modifier = Modifier.size(20.dp)
        )
        Spacer(modifier = Modifier.width(14.dp))
        Text(
            text = title,
            color = MiuixTheme.colorScheme.onSurface,
            fontSize = 14.sp,
            fontWeight = FontWeight.Medium
        )
    }
}
