package com.nocturn.music.ui.screens

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
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.nocturn.music.data.repository.MusicRepository
import com.nocturn.music.model.BannerItem
import com.nocturn.music.model.Playlist
import com.nocturn.music.model.Song
import com.nocturn.music.player.NocturnPlayer
import com.nocturn.music.ui.components.AsyncImage
import com.nocturn.music.ui.components.BannerCarousel
import com.nocturn.music.ui.components.PlaylistCard
import com.nocturn.music.ui.components.SongListItem
import com.nocturn.music.ui.theme.HyperBlue
import com.nocturn.music.ui.theme.squircleCard
import top.yukonga.miuix.kmp.basic.Card
import top.yukonga.miuix.kmp.basic.SmallTitle
import top.yukonga.miuix.kmp.basic.Text
import top.yukonga.miuix.kmp.theme.MiuixTheme
import com.nocturn.music.ui.navigation.SecondaryRoute
import kotlinx.coroutines.async
import kotlinx.coroutines.coroutineScope

@JvmName("HomeScreenWithPlaylistClick")
@Composable
fun HomeScreen(
    onOpenRoute: (SecondaryRoute) -> Unit,
    onNavigateToSearch: () -> Unit,
    modifier: Modifier = Modifier
) {
    var banners by remember { mutableStateOf<List<BannerItem>>(emptyList()) }
    var recommendedPlaylists by remember { mutableStateOf<List<Playlist>>(emptyList()) }
    var toplists by remember { mutableStateOf<List<Playlist>>(emptyList()) }
    var hotSongs by remember { mutableStateOf<List<Song>>(emptyList()) }
    var isLoading by remember { mutableStateOf(true) }

    val currentPlayingSong by NocturnPlayer.currentSong.collectAsState()
    val isPlaying by NocturnPlayer.isPlaying.collectAsState()

    LaunchedEffect(Unit) {
        isLoading = true
        coroutineScope {
            val bannersDeferred = async { MusicRepository.getBanners() }
            val playlistsDeferred = async { MusicRepository.getRecommendedPlaylists() }
            val toplistsDeferred = async { MusicRepository.getToplists() }

            banners = bannersDeferred.await()
            recommendedPlaylists = playlistsDeferred.await()
            val allToplists = toplistsDeferred.await()
            toplists = allToplists.take(6)

            if (allToplists.isNotEmpty()) {
                val firstChart = MusicRepository.getPlaylistDetail(allToplists.first().id)
                hotSongs = firstChart?.tracks?.take(10) ?: emptyList()
            }
        }
        isLoading = false
    }

    val bottomBarPadding = com.nocturn.music.ui.navigation.LocalBottomBarPadding.current

    LazyColumn(
        modifier = modifier.fillMaxSize(),
        contentPadding = PaddingValues(bottom = maxOf(bottomBarPadding + 16.dp, 100.dp))
    ) {
        // 1. Featured Banners
        item {
            Spacer(modifier = Modifier.height(10.dp))
            BannerCarousel(
                banners = banners,
                onBannerClick = { banner ->
                    if (banner.targetId > 0) {
                        onOpenRoute(SecondaryRoute.Playlist(banner.targetId))
                    }
                }
            )
            Spacer(modifier = Modifier.height(18.dp))
        }

        // 2. Quick Navigation Chips
        item {
            QuickActionsBar(
                onDailyRecommend = {
                    onOpenRoute(SecondaryRoute.DailyRecommend)
                },
                onTopCharts = {
                    onOpenRoute(SecondaryRoute.TopChartsSquare)
                },
                onSearch = onNavigateToSearch
            )
            Spacer(modifier = Modifier.height(24.dp))
        }

        // 3. Recommended Playlists
        item {
            SectionHeader(
                title = "推荐歌单",
                actionText = "更多",
                onAction = onNavigateToSearch
            )
            Spacer(modifier = Modifier.height(12.dp))

            LazyRow(
                contentPadding = PaddingValues(horizontal = 16.dp),
                horizontalArrangement = Arrangement.spacedBy(14.dp)
            ) {
                items(recommendedPlaylists) { playlist ->
                    PlaylistCard(
                        playlist = playlist,
                        onClick = { onOpenRoute(SecondaryRoute.Playlist(playlist.id, playlist.name, playlist.coverUrl)) }
                    )
                }
            }
            Spacer(modifier = Modifier.height(26.dp))
        }

        // 4. Official Top Charts (排行榜)
        item {
            SectionHeader(
                title = "云音乐官方榜",
                actionText = "全榜",
                onAction = { onOpenRoute(SecondaryRoute.TopChartsSquare) }
            )
            Spacer(modifier = Modifier.height(12.dp))

            LazyRow(
                contentPadding = PaddingValues(horizontal = 16.dp),
                horizontalArrangement = Arrangement.spacedBy(14.dp)
            ) {
                items(toplists) { chart ->
                    TopChartCard(
                        chart = chart,
                        onClick = { onOpenRoute(SecondaryRoute.Playlist(chart.id, chart.name, chart.coverUrl)) }
                    )
                }
            }
            Spacer(modifier = Modifier.height(26.dp))
        }

        // 5. Hot Songs (热歌速递)
        item {
            SectionHeader(
                title = "热门新歌",
                actionText = "播放全部",
                onAction = {
                    if (hotSongs.isNotEmpty()) {
                        NocturnPlayer.playQueue(hotSongs, 0)
                    }
                }
            )
            Spacer(modifier = Modifier.height(8.dp))
        }

        items(hotSongs) { song ->
            SongListItem(
                song = song,
                onClick = {
                    NocturnPlayer.playSong(song)
                },
                isPlaying = currentPlayingSong?.id == song.id && isPlaying,
                showCover = true
            )
        }
    }
}

@Composable
private fun SectionHeader(
    title: String,
    actionText: String? = null,
    onAction: (() -> Unit)? = null,
    modifier: Modifier = Modifier
) {
    Row(
        modifier = modifier
            .fillMaxWidth()
            .padding(horizontal = 16.dp),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically
    ) {
        SmallTitle(
            text = title,
            modifier = Modifier.padding(vertical = 4.dp)
        )

        if (actionText != null && onAction != null) {
            Box(
                modifier = Modifier
                    .clip(RoundedCornerShape(8.dp))
                    .clickable(onClick = onAction)
                    .padding(horizontal = 8.dp, vertical = 4.dp)
            ) {
                Text(
                    text = actionText,
                    color = MiuixTheme.colorScheme.primary,
                    fontSize = 13.sp,
                    fontWeight = FontWeight.Medium
                )
            }
        }
    }
}

@Composable
private fun QuickActionsBar(
    onDailyRecommend: () -> Unit,
    onTopCharts: () -> Unit,
    onSearch: () -> Unit,
    modifier: Modifier = Modifier
) {
    Row(
        modifier = modifier
            .fillMaxWidth()
            .padding(horizontal = 16.dp),
        horizontalArrangement = Arrangement.SpaceAround
    ) {
        QuickActionButton(iconText = "★", label = "每日推荐", color = Color(0xFFFF5252), onClick = onDailyRecommend)
        QuickActionButton(iconText = "📊", label = "排行榜", color = Color(0xFFFF9800), onClick = onTopCharts)
        QuickActionButton(iconText = "♫", label = "歌单广场", color = MiuixTheme.colorScheme.primary, onClick = onSearch)
        QuickActionButton(iconText = "🔍", label = "全网搜索", color = Color(0xFF4CAF50), onClick = onSearch)
    }
}

@Composable
private fun QuickActionButton(
    iconText: String,
    label: String,
    color: Color,
    onClick: () -> Unit
) {
    Column(
        horizontalAlignment = Alignment.CenterHorizontally,
        modifier = Modifier
            .clip(RoundedCornerShape(12.dp))
            .clickable(onClick = onClick)
            .padding(8.dp)
    ) {
        Box(
            modifier = Modifier
                .size(46.dp)
                .clip(CircleShape)
                .background(color.copy(alpha = 0.12f)),
            contentAlignment = Alignment.Center
        ) {
            Text(
                text = iconText,
                color = color,
                fontSize = 20.sp,
                fontWeight = FontWeight.Bold
            )
        }
        Spacer(modifier = Modifier.height(6.dp))
        Text(
            text = label,
            color = MiuixTheme.colorScheme.onSurface,
            fontSize = 12.sp,
            fontWeight = FontWeight.Medium
        )
    }
}

@Composable
private fun TopChartCard(
    chart: Playlist,
    onClick: () -> Unit,
    modifier: Modifier = Modifier
) {
    Card(
        modifier = modifier
            .width(160.dp)
            .clickable(onClick = onClick)
    ) {
        Column(modifier = Modifier.padding(10.dp)) {
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .height(110.dp)
                    .squircleCard(14.dp)
            ) {
                AsyncImage(
                    url = chart.coverUrl,
                    contentDescription = chart.name,
                    modifier = Modifier.fillMaxSize()
                )
            }
            Spacer(modifier = Modifier.height(8.dp))
            Text(
                text = chart.name,
                color = MiuixTheme.colorScheme.onSurface,
                fontSize = 14.sp,
                fontWeight = FontWeight.Bold,
                maxLines = 1
            )
            if (chart.description.isNotBlank()) {
                Text(
                    text = chart.description,
                    color = MiuixTheme.colorScheme.onSurfaceVariantSummary,
                    fontSize = 11.sp,
                    maxLines = 1,
                    modifier = Modifier.padding(top = 2.dp)
                )
            }
        }
    }
}
