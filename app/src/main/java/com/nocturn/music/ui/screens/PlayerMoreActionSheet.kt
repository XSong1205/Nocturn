package com.nocturn.music.ui.screens

import android.content.ClipData
import android.content.ClipboardManager
import android.content.Context
import android.widget.Toast
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.slideInVertically
import androidx.compose.animation.slideOutVertically
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
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.nocturn.music.data.repository.MusicRepository
import com.nocturn.music.data.repository.SettingsRepository
import com.nocturn.music.model.AudioQuality
import com.nocturn.music.model.Playlist
import com.nocturn.music.model.Song
import com.nocturn.music.model.SongWiki
import com.nocturn.music.ui.components.AsyncImage
import com.nocturn.music.ui.navigation.SecondaryRoute
import com.nocturn.music.ui.theme.AppIcons
import com.nocturn.music.ui.theme.HyperBlue
import com.nocturn.music.ui.theme.squircleCard
import kotlinx.coroutines.launch
import top.yukonga.miuix.kmp.basic.Button
import top.yukonga.miuix.kmp.basic.Card
import top.yukonga.miuix.kmp.basic.Icon
import top.yukonga.miuix.kmp.basic.IconButton
import top.yukonga.miuix.kmp.basic.InfiniteProgressIndicator
import top.yukonga.miuix.kmp.basic.SmallTitle
import top.yukonga.miuix.kmp.basic.Text
import top.yukonga.miuix.kmp.icon.MiuixIcons
import top.yukonga.miuix.kmp.icon.extended.Back
import top.yukonga.miuix.kmp.squircle.squircleClip
import top.yukonga.miuix.kmp.theme.MiuixTheme
import top.yukonga.miuix.kmp.window.WindowBottomSheet

private enum class SheetViewMode {
    MAIN_MENU,
    ADD_TO_PLAYLIST,
    SONG_WIKI,
    AUDIO_QUALITY
}

/**
 * 播放界面“更多”操作面板 (基于 MIUIX HyperOS 规范构建)
 */
@OptIn(ExperimentalLayoutApi::class)
@Composable
fun PlayerMoreActionSheet(
    show: Boolean,
    song: Song,
    onDismissRequest: () -> Unit,
    onNavigateToRoute: (SecondaryRoute) -> Unit
) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    val userProfile by SettingsRepository.userProfile.collectAsState()
    val currentQuality by SettingsRepository.audioQuality.collectAsState()

    var viewMode by remember { mutableStateOf(SheetViewMode.MAIN_MENU) }
    var userPlaylists by remember { mutableStateOf<List<Playlist>>(emptyList()) }
    var isLoadingPlaylists by remember { mutableStateOf(false) }

    var songWiki by remember { mutableStateOf<SongWiki?>(null) }
    var isLoadingWiki by remember { mutableStateOf(false) }

    LaunchedEffect(show) {
        if (show) {
            viewMode = SheetViewMode.MAIN_MENU
        }
    }

    WindowBottomSheet(
        show = show,
        onDismissRequest = onDismissRequest
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 20.dp, vertical = 12.dp)
        ) {
            when (viewMode) {
                SheetViewMode.MAIN_MENU -> {
                    MainMenuContent(
                        song = song,
                        currentQuality = currentQuality,
                        onOpenAddToPlaylist = {
                            viewMode = SheetViewMode.ADD_TO_PLAYLIST
                            if (userProfile.isLogin && userProfile.userId > 0L) {
                                isLoadingPlaylists = true
                                scope.launch {
                                    userPlaylists = MusicRepository.getUserPlaylists(userProfile.userId)
                                    isLoadingPlaylists = false
                                }
                            }
                        },
                        onOpenWiki = {
                            viewMode = SheetViewMode.SONG_WIKI
                            isLoadingWiki = true
                            scope.launch {
                                songWiki = MusicRepository.getSongWiki(song)
                                isLoadingWiki = false
                            }
                        },
                        onOpenQuality = {
                            viewMode = SheetViewMode.AUDIO_QUALITY
                        },
                        onNavigateToArtist = {
                            onDismissRequest()
                            onNavigateToRoute(SecondaryRoute.Artist(0L, song.artist))
                        },
                        onNavigateToAlbum = {
                            if (song.album.isNotBlank()) {
                                onDismissRequest()
                                onNavigateToRoute(SecondaryRoute.Album(0L, song.album, song.coverUrl))
                            } else {
                                Toast.makeText(context, "暂无专辑信息", Toast.LENGTH_SHORT).show()
                            }
                        },
                        onCopyInfo = {
                            val clip = ClipData.newPlainText("song_info", "${song.title} - ${song.artist}\nhttps://music.163.com/#/song?id=${song.id}")
                            (context.getSystemService(Context.CLIPBOARD_SERVICE) as ClipboardManager).setPrimaryClip(clip)
                            Toast.makeText(context, "歌曲信息与链接已复制到剪贴板", Toast.LENGTH_SHORT).show()
                            onDismissRequest()
                        }
                    )
                }

                SheetViewMode.ADD_TO_PLAYLIST -> {
                    AddToPlaylistContent(
                        song = song,
                        isLogin = userProfile.isLogin,
                        playlists = userPlaylists,
                        isLoading = isLoadingPlaylists,
                        onBack = { viewMode = SheetViewMode.MAIN_MENU },
                        onSelectPlaylist = { pl ->
                            scope.launch {
                                val success = MusicRepository.addToPlaylist(pl.id, song.id)
                                if (success) {
                                    Toast.makeText(context, "已成功加入歌单「${pl.name}」", Toast.LENGTH_SHORT).show()
                                    onDismissRequest()
                                } else {
                                    Toast.makeText(context, "添加失败或歌曲已在歌单中", Toast.LENGTH_SHORT).show()
                                }
                            }
                        }
                    )
                }

                SheetViewMode.SONG_WIKI -> {
                    SongWikiContent(
                        song = song,
                        wiki = songWiki,
                        isLoading = isLoadingWiki,
                        onBack = { viewMode = SheetViewMode.MAIN_MENU }
                    )
                }

                SheetViewMode.AUDIO_QUALITY -> {
                    AudioQualityContent(
                        currentQuality = currentQuality,
                        onBack = { viewMode = SheetViewMode.MAIN_MENU },
                        onSelectQuality = { q ->
                            SettingsRepository.setAudioQuality(q)
                            Toast.makeText(context, "已切换音质为 ${q.label}，下次切歌或重播生效", Toast.LENGTH_SHORT).show()
                            viewMode = SheetViewMode.MAIN_MENU
                        }
                    )
                }
            }
        }
    }
}

@Composable
private fun MainMenuContent(
    song: Song,
    currentQuality: AudioQuality,
    onOpenAddToPlaylist: () -> Unit,
    onOpenWiki: () -> Unit,
    onOpenQuality: () -> Unit,
    onNavigateToArtist: () -> Unit,
    onNavigateToAlbum: () -> Unit,
    onCopyInfo: () -> Unit
) {
    Column(modifier = Modifier.fillMaxWidth()) {
        // 歌曲概要卡片
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(bottom = 16.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Box(
                modifier = Modifier
                    .size(54.dp)
                    .squircleCard(14.dp)
                    .background(MiuixTheme.colorScheme.surfaceContainer)
            ) {
                AsyncImage(
                    url = song.coverUrl,
                    contentDescription = null,
                    modifier = Modifier.fillMaxSize()
                )
            }
            Spacer(modifier = Modifier.width(14.dp))
            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = song.title,
                    color = MiuixTheme.colorScheme.onSurface,
                    fontSize = 17.sp,
                    fontWeight = FontWeight.Bold,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
                Spacer(modifier = Modifier.height(2.dp))
                Text(
                    text = "${song.artist} · ${song.album.ifBlank { "单曲" }}",
                    color = MiuixTheme.colorScheme.onSurfaceSecondary.copy(alpha = 0.8f),
                    fontSize = 13.sp,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
            }
        }

        // 操作列表 (HyperOS 卡片组设计)
        Card(
            modifier = Modifier.fillMaxWidth()
        ) {
            Column(modifier = Modifier.padding(vertical = 4.dp)) {
                ActionMenuItem(
                    icon = AppIcons.PlaylistAdd,
                    title = "收藏到歌单",
                    subtitle = "保存至我的自定义云端歌单",
                    onClick = onOpenAddToPlaylist
                )
                ActionMenuItem(
                    icon = AppIcons.Album,
                    title = "音乐百科",
                    subtitle = "探索流派、创作背景与人员职员表",
                    onClick = onOpenWiki
                )
                ActionMenuItem(
                    icon = AppIcons.Artist,
                    title = "查看歌手: ${song.artist}",
                    subtitle = "浏览歌手所有热门曲目",
                    onClick = onNavigateToArtist
                )
                if (song.album.isNotBlank()) {
                    ActionMenuItem(
                        icon = AppIcons.Album,
                        title = "查看专辑: ${song.album}",
                        subtitle = "完整收录唱片详情",
                        onClick = onNavigateToAlbum
                    )
                }
                ActionMenuItem(
                    icon = AppIcons.Settings,
                    title = "播放音质档位",
                    subtitle = "当前: ${currentQuality.label}",
                    onClick = onOpenQuality
                )
                ActionMenuItem(
                    icon = AppIcons.Share,
                    title = "复制歌曲信息与链接",
                    subtitle = "快速分享至其他应用",
                    onClick = onCopyInfo
                )
            }
        }
        Spacer(modifier = Modifier.height(10.dp))
    }
}

@Composable
private fun ActionMenuItem(
    icon: ImageVector,
    title: String,
    subtitle: String? = null,
    onClick: () -> Unit
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clickable(onClick = onClick)
            .padding(horizontal = 16.dp, vertical = 12.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Box(
            modifier = Modifier
                .size(38.dp)
                .squircleClip(12.dp)
                .background(MiuixTheme.colorScheme.surfaceContainerHigh),
            contentAlignment = Alignment.Center
        ) {
            Icon(
                imageVector = icon,
                contentDescription = null,
                tint = MiuixTheme.colorScheme.onSurface,
                modifier = Modifier.size(20.dp)
            )
        }
        Spacer(modifier = Modifier.width(14.dp))
        Column(modifier = Modifier.weight(1f)) {
            Text(
                text = title,
                color = MiuixTheme.colorScheme.onSurface,
                fontSize = 15.sp,
                fontWeight = FontWeight.Medium,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis
            )
            if (subtitle != null) {
                Text(
                    text = subtitle,
                    color = MiuixTheme.colorScheme.onSurfaceSecondary.copy(alpha = 0.7f),
                    fontSize = 12.sp,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
            }
        }
    }
}

@Composable
private fun AddToPlaylistContent(
    song: Song,
    isLogin: Boolean,
    playlists: List<Playlist>,
    isLoading: Boolean,
    onBack: () -> Unit,
    onSelectPlaylist: (Playlist) -> Unit
) {
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .heightIn(max = 500.dp)
    ) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            verticalAlignment = Alignment.CenterVertically
        ) {
            IconButton(
                onClick = onBack,
                modifier = Modifier.size(36.dp)
            ) {
                Icon(
                    imageVector = MiuixIcons.Back,
                    contentDescription = "返回",
                    tint = MiuixTheme.colorScheme.onSurface
                )
            }
            Spacer(modifier = Modifier.width(8.dp))
            Text(
                text = "收藏到歌单",
                color = MiuixTheme.colorScheme.onSurface,
                fontSize = 18.sp,
                fontWeight = FontWeight.Bold
            )
        }

        Spacer(modifier = Modifier.height(14.dp))

        if (!isLogin) {
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(vertical = 36.dp),
                contentAlignment = Alignment.Center
            ) {
                Text(
                    text = "请先在「我的」页面登录网易云音乐账号\n登录后即可同步并管理云端歌单",
                    color = MiuixTheme.colorScheme.onSurfaceSecondary,
                    fontSize = 14.sp,
                    lineHeight = 22.sp,
                    textAlign = androidx.compose.ui.text.style.TextAlign.Center
                )
            }
        } else if (isLoading) {
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .height(200.dp),
                contentAlignment = Alignment.Center
            ) {
                InfiniteProgressIndicator(color = MiuixTheme.colorScheme.primary)
            }
        } else if (playlists.isEmpty()) {
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(vertical = 36.dp),
                contentAlignment = Alignment.Center
            ) {
                Text(
                    text = "暂未拉取到自建歌单",
                    color = MiuixTheme.colorScheme.onSurfaceSecondary,
                    fontSize = 14.sp
                )
            }
        } else {
            LazyColumn(
                modifier = Modifier.fillMaxWidth(),
                contentPadding = PaddingValues(bottom = 16.dp)
            ) {
                items(playlists) { pl ->
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .clickable { onSelectPlaylist(pl) }
                            .padding(vertical = 10.dp, horizontal = 4.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Box(
                            modifier = Modifier
                                .size(48.dp)
                                .squircleCard(12.dp)
                                .background(MiuixTheme.colorScheme.surfaceContainer)
                        ) {
                            AsyncImage(
                                url = pl.coverUrl,
                                contentDescription = pl.name,
                                modifier = Modifier.fillMaxSize()
                            )
                        }
                        Spacer(modifier = Modifier.width(14.dp))
                        Column(modifier = Modifier.weight(1f)) {
                            Text(
                                text = pl.name,
                                color = MiuixTheme.colorScheme.onSurface,
                                fontSize = 15.sp,
                                fontWeight = FontWeight.SemiBold,
                                maxLines = 1,
                                overflow = TextOverflow.Ellipsis
                            )
                            Text(
                                text = "${pl.trackCount} 首",
                                color = MiuixTheme.colorScheme.onSurfaceSecondary,
                                fontSize = 12.sp
                            )
                        }
                    }
                }
            }
        }
    }
}

@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun SongWikiContent(
    song: Song,
    wiki: SongWiki?,
    isLoading: Boolean,
    onBack: () -> Unit
) {
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .heightIn(max = 520.dp)
    ) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            verticalAlignment = Alignment.CenterVertically
        ) {
            IconButton(
                onClick = onBack,
                modifier = Modifier.size(36.dp)
            ) {
                Icon(
                    imageVector = MiuixIcons.Back,
                    contentDescription = "返回",
                    tint = MiuixTheme.colorScheme.onSurface
                )
            }
            Spacer(modifier = Modifier.width(8.dp))
            Text(
                text = "音乐百科",
                color = MiuixTheme.colorScheme.onSurface,
                fontSize = 18.sp,
                fontWeight = FontWeight.Bold
            )
        }

        Spacer(modifier = Modifier.height(14.dp))

        if (isLoading) {
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .height(220.dp),
                contentAlignment = Alignment.Center
            ) {
                InfiniteProgressIndicator(color = MiuixTheme.colorScheme.primary)
            }
        } else {
            val validWiki = wiki ?: SongWiki(
                songId = song.id,
                title = song.title,
                artist = song.artist,
                album = song.album,
                description = "暂无更详细的官方百科词条"
            )

            LazyColumn(
                modifier = Modifier.fillMaxWidth(),
                contentPadding = PaddingValues(bottom = 16.dp)
            ) {
                // 音乐风格标签
                if (validWiki.styles.isNotEmpty()) {
                    item {
                        SmallTitle(text = "音乐风格")
                        FlowRow(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(vertical = 8.dp),
                            horizontalArrangement = Arrangement.spacedBy(8.dp),
                            verticalArrangement = Arrangement.spacedBy(8.dp)
                        ) {
                            validWiki.styles.forEach { style ->
                                Box(
                                    modifier = Modifier
                                        .squircleClip(12.dp)
                                        .background(MiuixTheme.colorScheme.primary.copy(alpha = 0.12f))
                                        .padding(horizontal = 12.dp, vertical = 6.dp)
                                ) {
                                    Text(
                                        text = style,
                                        color = MiuixTheme.colorScheme.primary,
                                        fontSize = 12.sp,
                                        fontWeight = FontWeight.SemiBold
                                    )
                                }
                            }
                        }
                        Spacer(modifier = Modifier.height(10.dp))
                    }
                }

                // 歌曲背景故事
                if (validWiki.description.isNotBlank()) {
                    item {
                        SmallTitle(text = "歌曲简介")
                        Card(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(vertical = 6.dp)
                        ) {
                            Text(
                                text = validWiki.description,
                                color = MiuixTheme.colorScheme.onSurface,
                                fontSize = 14.sp,
                                lineHeight = 22.sp,
                                modifier = Modifier.padding(14.dp)
                            )
                        }
                        Spacer(modifier = Modifier.height(10.dp))
                    }
                }

                // 创作人员职员名单 (Credits)
                if (validWiki.credits.isNotEmpty()) {
                    item {
                        SmallTitle(text = "创作与制作团队")
                        Card(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(vertical = 6.dp)
                        ) {
                            Column(modifier = Modifier.padding(14.dp)) {
                                validWiki.credits.forEachIndexed { idx, pair ->
                                    Row(
                                        modifier = Modifier
                                            .fillMaxWidth()
                                            .padding(vertical = 4.dp),
                                        horizontalArrangement = Arrangement.SpaceBetween
                                    ) {
                                        Text(
                                            text = pair.first,
                                            color = MiuixTheme.colorScheme.onSurfaceSecondary,
                                            fontSize = 13.sp,
                                            fontWeight = FontWeight.Medium
                                        )
                                        Text(
                                            text = pair.second,
                                            color = MiuixTheme.colorScheme.onSurface,
                                            fontSize = 13.sp,
                                            fontWeight = FontWeight.SemiBold
                                        )
                                    }
                                    if (idx < validWiki.credits.lastIndex) {
                                        Spacer(modifier = Modifier.height(4.dp))
                                    }
                                }
                            }
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun AudioQualityContent(
    currentQuality: AudioQuality,
    onBack: () -> Unit,
    onSelectQuality: (AudioQuality) -> Unit
) {
    Column(modifier = Modifier.fillMaxWidth()) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            verticalAlignment = Alignment.CenterVertically
        ) {
            IconButton(
                onClick = onBack,
                modifier = Modifier.size(36.dp)
            ) {
                Icon(
                    imageVector = MiuixIcons.Back,
                    contentDescription = "返回",
                    tint = MiuixTheme.colorScheme.onSurface
                )
            }
            Spacer(modifier = Modifier.width(8.dp))
            Text(
                text = "切换播放音质",
                color = MiuixTheme.colorScheme.onSurface,
                fontSize = 18.sp,
                fontWeight = FontWeight.Bold
            )
        }

        Spacer(modifier = Modifier.height(14.dp))

        Card(
            modifier = Modifier.fillMaxWidth()
        ) {
            Column(modifier = Modifier.padding(vertical = 4.dp)) {
                AudioQuality.values().forEach { q ->
                    val isSelected = q == currentQuality
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .clickable { onSelectQuality(q) }
                            .padding(horizontal = 16.dp, vertical = 14.dp),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.SpaceBetween
                    ) {
                        Column {
                            Text(
                                text = q.label,
                                color = if (isSelected) MiuixTheme.colorScheme.primary else MiuixTheme.colorScheme.onSurface,
                                fontSize = 15.sp,
                                fontWeight = if (isSelected) FontWeight.Bold else FontWeight.Medium
                            )
                            Text(
                                text = when (q.level) {
                                    "standard" -> "标准音质 128Kbps"
                                    "higher" -> "较高音质 192Kbps"
                                    "exhigh" -> "极高 HQ 320Kbps"
                                    "lossless" -> "无损 SQ FLAC 16bit/44.1kHz"
                                    "hires" -> "Hi-Res 金标发烧级 24bit/96kHz"
                                    else -> q.level
                                },
                                color = MiuixTheme.colorScheme.onSurfaceSecondary.copy(alpha = 0.7f),
                                fontSize = 12.sp
                            )
                        }

                        if (isSelected) {
                            Box(
                                modifier = Modifier
                                    .size(24.dp)
                                    .clip(CircleShape)
                                    .background(MiuixTheme.colorScheme.primary),
                                contentAlignment = Alignment.Center
                            ) {
                                Icon(
                                    imageVector = AppIcons.Check,
                                    contentDescription = null,
                                    tint = Color.White,
                                    modifier = Modifier.size(16.dp)
                                )
                            }
                        }
                    }
                }
            }
        }
        Spacer(modifier = Modifier.height(10.dp))
    }
}
