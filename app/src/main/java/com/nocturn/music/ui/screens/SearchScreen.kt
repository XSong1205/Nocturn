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
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.nocturn.music.data.repository.MusicRepository
import com.nocturn.music.data.repository.SettingsRepository
import com.nocturn.music.model.Album
import com.nocturn.music.model.Artist
import com.nocturn.music.model.Playlist
import com.nocturn.music.model.Song
import com.nocturn.music.player.NocturnPlayer
import com.nocturn.music.ui.components.AsyncImage
import com.nocturn.music.ui.components.PlaylistCard
import com.nocturn.music.ui.components.SongListItem
import com.nocturn.music.ui.theme.HyperBlue
import com.nocturn.music.ui.theme.squircleCard
import kotlinx.coroutines.launch
import top.yukonga.miuix.kmp.basic.Button
import top.yukonga.miuix.kmp.basic.Card
import top.yukonga.miuix.kmp.basic.Text
import top.yukonga.miuix.kmp.basic.TextField
import top.yukonga.miuix.kmp.theme.MiuixTheme

import com.nocturn.music.ui.navigation.SecondaryRoute

private val hotKeywords = listOf(
    "周杰伦", "陈奕迅", "林俊杰", "邓紫棋", "告五人",
    "经典粤语", "纯音乐", "欧美流行", "动漫原声", "民谣"
)

@OptIn(ExperimentalLayoutApi::class)
@JvmName("SearchScreenWithPlaylistClick")
@Composable
fun SearchScreen(
    onOpenRoute: (SecondaryRoute) -> Unit,
    modifier: Modifier = Modifier
) {
    var query by remember { mutableStateOf("") }
    var selectedTab by remember { mutableIntStateOf(0) }
    var isSearching by remember { mutableStateOf(false) }

    var songResults by remember { mutableStateOf<List<Song>>(emptyList()) }
    var playlistResults by remember { mutableStateOf<List<Playlist>>(emptyList()) }
    var artistResults by remember { mutableStateOf<List<Artist>>(emptyList()) }
    var albumResults by remember { mutableStateOf<List<Album>>(emptyList()) }

    val searchHistory by SettingsRepository.searchHistory.collectAsState()
    val currentPlayingSong by NocturnPlayer.currentSong.collectAsState()
    val isPlaying by NocturnPlayer.isPlaying.collectAsState()
    val scope = rememberCoroutineScope()

    var hotKeywordsList by remember { mutableStateOf(hotKeywords) }
    androidx.compose.runtime.LaunchedEffect(Unit) {
        val remote = MusicRepository.getHotSearchKeywords()
        if (remote.isNotEmpty()) {
            hotKeywordsList = remote
        }
    }

    fun performSearch(text: String) {
        val keyword = text.trim()
        if (keyword.isBlank()) return
        query = keyword
        SettingsRepository.addSearchHistory(keyword)
        isSearching = true

        scope.launch {
            when (selectedTab) {
                0 -> songResults = MusicRepository.searchSongs(keyword)
                1 -> playlistResults = MusicRepository.searchPlaylists(keyword)
                2 -> artistResults = MusicRepository.searchArtists(keyword)
                3 -> albumResults = MusicRepository.searchAlbums(keyword)
            }
            isSearching = false
        }
    }

    Column(
        modifier = modifier
            .fillMaxSize()
            .padding(top = 8.dp)
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 16.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            TextField(
                value = query,
                onValueChange = { query = it },
                label = "搜索歌曲、歌单、歌手、专辑",
                modifier = Modifier.weight(1f)
            )

            Spacer(modifier = Modifier.width(8.dp))

            Button(
                onClick = { performSearch(query) },
                modifier = Modifier.height(48.dp)
            ) {
                Text(text = "搜索")
            }
        }

        Spacer(modifier = Modifier.height(12.dp))

        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 16.dp),
            horizontalArrangement = Arrangement.SpaceAround
        ) {
            val tabs = listOf("单曲", "歌单", "歌手", "专辑")
            tabs.forEachIndexed { index, tabTitle ->
                val isSelected = selectedTab == index
                Box(
                    modifier = Modifier
                        .clip(RoundedCornerShape(12.dp))
                        .background(if (isSelected) HyperBlue.copy(alpha = 0.12f) else Color.Transparent)
                        .clickable {
                            selectedTab = index
                            if (query.isNotBlank()) performSearch(query)
                        }
                        .padding(horizontal = 16.dp, vertical = 8.dp)
                ) {
                    Text(
                        text = tabTitle,
                        color = if (isSelected) HyperBlue else MiuixTheme.colorScheme.onSurfaceSecondary,
                        fontSize = 14.sp,
                        fontWeight = if (isSelected) FontWeight.Bold else FontWeight.Normal
                    )
                }
            }
        }

        Spacer(modifier = Modifier.height(8.dp))

        if (query.isBlank() && songResults.isEmpty() && playlistResults.isEmpty()) {
            LazyColumn(
                modifier = Modifier.fillMaxSize(),
                contentPadding = PaddingValues(16.dp, bottom = 90.dp)
            ) {
                if (searchHistory.isNotEmpty()) {
                    item {
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Text(
                                text = "历史搜索",
                                color = MiuixTheme.colorScheme.onSurface,
                                fontSize = 15.sp,
                                fontWeight = FontWeight.Bold
                            )
                            Text(
                                text = "清除",
                                color = MiuixTheme.colorScheme.onSurfaceSecondary.copy(alpha = 0.6f),
                                fontSize = 12.sp,
                                modifier = Modifier
                                    .clip(RoundedCornerShape(8.dp))
                                    .clickable { SettingsRepository.clearSearchHistory() }
                                    .padding(4.dp)
                            )
                        }
                        Spacer(modifier = Modifier.height(8.dp))
                        FlowRow(
                            horizontalArrangement = Arrangement.spacedBy(8.dp),
                            verticalArrangement = Arrangement.spacedBy(8.dp)
                        ) {
                            searchHistory.forEach { historyTag ->
                                Box(
                                    modifier = Modifier
                                        .clip(RoundedCornerShape(14.dp))
                                        .background(MiuixTheme.colorScheme.surfaceContainerHighest)
                                        .clickable { performSearch(historyTag) }
                                        .padding(horizontal = 12.dp, vertical = 6.dp)
                                ) {
                                    Text(
                                        text = historyTag,
                                        color = MiuixTheme.colorScheme.onSurface,
                                        fontSize = 13.sp
                                    )
                                }
                            }
                        }
                        Spacer(modifier = Modifier.height(24.dp))
                    }
                }

                item {
                    Text(
                        text = "热搜推荐",
                        color = MiuixTheme.colorScheme.onSurface,
                        fontSize = 15.sp,
                        fontWeight = FontWeight.Bold
                    )
                    Spacer(modifier = Modifier.height(10.dp))
                    FlowRow(
                        horizontalArrangement = Arrangement.spacedBy(8.dp),
                        verticalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        hotKeywordsList.forEachIndexed { i, keyword ->
                            val isTop = i < 3
                            Box(
                                modifier = Modifier
                                    .clip(RoundedCornerShape(14.dp))
                                    .background(
                                        if (isTop) HyperBlue.copy(alpha = 0.1f)
                                        else MiuixTheme.colorScheme.surfaceContainerHighest
                                    )
                                    .clickable { performSearch(keyword) }
                                    .padding(horizontal = 12.dp, vertical = 6.dp)
                            ) {
                                Text(
                                    text = "${i + 1}. $keyword",
                                    color = if (isTop) HyperBlue else MiuixTheme.colorScheme.onSurface,
                                    fontSize = 13.sp,
                                    fontWeight = if (isTop) FontWeight.SemiBold else FontWeight.Normal
                                )
                            }
                        }
                    }
                }
            }
        } else {
            when (selectedTab) {
                0 -> {
                    LazyColumn(
                        modifier = Modifier.fillMaxSize(),
                        contentPadding = PaddingValues(bottom = 90.dp)
                    ) {
                        item {
                            if (songResults.isNotEmpty()) {
                                Row(
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .clickable { NocturnPlayer.playQueue(songResults, 0) }
                                        .padding(horizontal = 16.dp, vertical = 8.dp),
                                    verticalAlignment = Alignment.CenterVertically
                                ) {
                                    Text(
                                        text = "▶ 播放全部 (${songResults.size}首)",
                                        color = HyperBlue,
                                        fontSize = 14.sp,
                                        fontWeight = FontWeight.SemiBold
                                    )
                                }
                            }
                        }
                        items(songResults) { song ->
                            SongListItem(
                                song = song,
                                onClick = { NocturnPlayer.playSong(song) },
                                isPlaying = currentPlayingSong?.id == song.id && isPlaying,
                                showCover = true
                            )
                        }
                    }
                }
                1 -> {
                    LazyVerticalGrid(
                        columns = GridCells.Adaptive(minSize = 140.dp),
                        contentPadding = PaddingValues(16.dp, bottom = 90.dp),
                        horizontalArrangement = Arrangement.spacedBy(12.dp),
                        verticalArrangement = Arrangement.spacedBy(16.dp)
                    ) {
                        items(items = playlistResults) { pl ->
                            PlaylistCard(
                                playlist = pl,
                                onClick = { onOpenRoute(SecondaryRoute.Playlist(pl.id, pl.name, pl.coverUrl)) }
                            )
                        }
                    }
                }
                2 -> {
                    LazyColumn(
                        modifier = Modifier.fillMaxSize(),
                        contentPadding = PaddingValues(bottom = 90.dp)
                    ) {
                        items(artistResults) { artist ->
                            Row(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .clickable { onOpenRoute(SecondaryRoute.Artist(artist.id, artist.name, artist.avatarUrl)) }
                                    .padding(horizontal = 16.dp, vertical = 10.dp),
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Box(
                                    modifier = Modifier
                                        .size(52.dp)
                                        .clip(CircleShape)
                                ) {
                                    AsyncImage(
                                        url = artist.avatarUrl,
                                        contentDescription = artist.name,
                                        modifier = Modifier.fillMaxSize()
                                    )
                                }
                                Spacer(modifier = Modifier.width(14.dp))
                                Column {
                                    Text(
                                        text = artist.name,
                                        color = MiuixTheme.colorScheme.onSurface,
                                        fontSize = 15.sp,
                                        fontWeight = FontWeight.SemiBold
                                    )
                                    if (artist.musicSize > 0) {
                                        Text(
                                            text = "单曲: ${artist.musicSize} · 专辑: ${artist.albumSize}",
                                            color = MiuixTheme.colorScheme.onSurfaceSecondary.copy(alpha = 0.7f),
                                            fontSize = 12.sp,
                                            modifier = Modifier.padding(top = 2.dp)
                                        )
                                    }
                                }
                            }
                        }
                    }
                }
                3 -> {
                    LazyColumn(
                        modifier = Modifier.fillMaxSize(),
                        contentPadding = PaddingValues(bottom = 90.dp)
                    ) {
                        items(albumResults) { album ->
                            Row(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .clickable { onOpenRoute(SecondaryRoute.Album(album.id, album.name, album.coverUrl)) }
                                    .padding(horizontal = 16.dp, vertical = 8.dp),
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Box(
                                    modifier = Modifier
                                        .size(52.dp)
                                        .squircleCard(10.dp)
                                ) {
                                    AsyncImage(
                                        url = album.coverUrl,
                                        contentDescription = album.name,
                                        modifier = Modifier.fillMaxSize()
                                    )
                                }
                                Spacer(modifier = Modifier.width(14.dp))
                                Column {
                                    Text(
                                        text = album.name,
                                        color = MiuixTheme.colorScheme.onSurface,
                                        fontSize = 15.sp,
                                        fontWeight = FontWeight.SemiBold
                                    )
                                    Text(
                                        text = "${album.artistName} · 共${album.size}首",
                                        color = MiuixTheme.colorScheme.onSurfaceSecondary.copy(alpha = 0.7f),
                                        fontSize = 12.sp,
                                        modifier = Modifier.padding(top = 2.dp)
                                    )
                                }
                            }
                        }
                    }
                }
            }
        }
    }
}
