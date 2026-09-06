package com.nocturn.music.ui.screens

import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.togetherWith
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
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
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Slider
import androidx.compose.material3.SliderDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import kotlinx.coroutines.launch
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.rotate
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.nocturn.music.data.repository.MusicRepository
import com.nocturn.music.data.repository.SettingsRepository
import com.nocturn.music.model.PlayMode
import com.nocturn.music.model.SongLyric
import com.nocturn.music.player.NocturnPlayer
import com.nocturn.music.ui.components.AsyncImage
import com.nocturn.music.ui.theme.HyperBlue
import com.nocturn.music.ui.theme.HyperRed
import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.spring
import androidx.compose.animation.scaleIn
import androidx.compose.animation.scaleOut
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.ui.draw.blur
import androidx.compose.ui.draw.scale
import androidx.compose.ui.graphics.BlendMode
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontFamily
import com.mocharealm.accompanist.lyrics.core.model.SyncedLyrics
import com.mocharealm.accompanist.lyrics.ui.composable.lyrics.KaraokeLyricsView
import top.yukonga.miuix.kmp.squircle.squircleClip

import top.yukonga.miuix.kmp.basic.Text
import top.yukonga.miuix.kmp.theme.MiuixTheme

enum class CenterDisplayMode {
    COVER,
    LYRICS,
    VINYL
}

@Composable
fun PlayerScreen(
    onDismiss: () -> Unit,
    onOpenQueue: () -> Unit,
    modifier: Modifier = Modifier
) {
    val currentSong by NocturnPlayer.currentSong.collectAsState()
    val isPlaying by NocturnPlayer.isPlaying.collectAsState()
    val currentPositionMs by NocturnPlayer.currentPositionMs.collectAsState()
    val durationMs by NocturnPlayer.durationMs.collectAsState()
    val playMode by NocturnPlayer.playMode.collectAsState()
    val isBuffering by NocturnPlayer.isBuffering.collectAsState()
    val favoriteSongs by SettingsRepository.favoriteSongs.collectAsState()
    val audioQuality by SettingsRepository.audioQuality.collectAsState()

    var displayMode by remember { mutableStateOf(CenterDisplayMode.COVER) }
    var lyrics by remember { mutableStateOf(SongLyric()) }
    var isDraggingSlider by remember { mutableStateOf(false) }
    var sliderValue by remember { mutableFloatStateOf(0f) }

    val song = currentSong ?: return
    val isFavorite = favoriteSongs.any { it.id == song.id }
    val scope = rememberCoroutineScope()

    LaunchedEffect(song.id) {
        lyrics = MusicRepository.getLyric(song.id)
    }

    // 黑胶旋转动画
    val infiniteTransition = rememberInfiniteTransition(label = "vinyl")
    val rotationAngle by infiniteTransition.animateFloat(
        initialValue = 0f,
        targetValue = 360f,
        animationSpec = infiniteRepeatable(
            animation = tween(18000, easing = LinearEasing),
            repeatMode = RepeatMode.Restart
        ),
        label = "rotation"
    )

    // 唱臂角度
    val tonearmAngle by animateFloatAsState(
        targetValue = if (isPlaying) 0f else -32f,
        animationSpec = tween(400, easing = FastOutSlowInEasing),
        label = "tonearm"
    )

    // Apple Music 标志性封面随播放状态缩放动效 (播放时放大且浮起，暂停时微缩)
    val coverScale by animateFloatAsState(
        targetValue = if (isPlaying) 1.0f else 0.88f,
        animationSpec = spring(
            dampingRatio = Spring.DampingRatioMediumBouncy,
            stiffness = Spring.StiffnessLow
        ),
        label = "coverScale"
    )

    val coverShadowElevation by animateFloatAsState(
        targetValue = if (isPlaying) 28f else 10f,
        animationSpec = tween(350),
        label = "coverShadow"
    )

    Box(
        modifier = modifier
            .fillMaxSize()
            .background(Color(0xFF0C0C0E))
    ) {
        // =========================================================================
        // 1. Apple Music 动态流光漫反射背景 (Ambient Artwork Glow Layer)
        // =========================================================================
        Box(modifier = Modifier.fillMaxSize()) {
            AsyncImage(
                url = song.coverUrl,
                contentDescription = null,
                contentScale = ContentScale.Crop,
                modifier = Modifier
                    .fillMaxSize()
                    .scale(1.35f)
                    .blur(90.dp)
            )

            // 多重渐变暗色蒙版：确保顶部状态与底部控制具备澎湃OS通透质感与纯粹对比度
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .background(
                        Brush.verticalGradient(
                            colors = listOf(
                                Color(0xFF08080A).copy(alpha = 0.55f),
                                Color(0xFF0C0C0E).copy(alpha = 0.35f),
                                Color(0xFF09090B).copy(alpha = 0.70f),
                                Color(0xFF070708).copy(alpha = 0.95f)
                            )
                        )
                    )
            )

            // 微弱暗角晕影 (Vignette)
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .background(
                        Brush.radialGradient(
                            colors = listOf(
                                Color.Transparent,
                                Color.Black.copy(alpha = 0.65f)
                            )
                        )
                    )
            )
        }

        // =========================================================================
        // 2. 主体内容层
        // =========================================================================
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(horizontal = 22.dp, vertical = 12.dp),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            // ---------------------------------------------------------------------
            // 顶部导航与歌曲信息栏 (小米澎湃胶囊设计)
            // ---------------------------------------------------------------------
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(top = 18.dp, bottom = 8.dp),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.SpaceBetween
            ) {
                // 下拉关闭按钮 (HyperOS 超级椭圆毛玻璃小药丸)
                Box(
                    modifier = Modifier
                        .size(40.dp)
                        .squircleClip(14.dp)
                        .background(Color.White.copy(alpha = 0.12f))
                        .clickable(
                            interactionSource = remember { MutableInteractionSource() },
                            indication = null,
                            onClick = onDismiss
                        ),
                    contentAlignment = Alignment.Center
                ) {
                    Text(
                        text = "⌄",
                        color = Color.White,
                        fontSize = 22.sp,
                        fontWeight = FontWeight.Bold,
                        modifier = Modifier.padding(bottom = 4.dp)
                    )
                }

                // 顶部曲目摘要
                Column(
                    horizontalAlignment = Alignment.CenterHorizontally,
                    modifier = Modifier
                        .weight(1f)
                        .padding(horizontal = 14.dp)
                ) {
                    Text(
                        text = "正在播放",
                        color = Color.White.copy(alpha = 0.5f),
                        fontSize = 11.sp,
                        fontWeight = FontWeight.Medium
                    )
                    Text(
                        text = song.title,
                        color = Color.White,
                        fontSize = 15.sp,
                        fontWeight = FontWeight.SemiBold,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                        modifier = Modifier.padding(top = 1.dp)
                    )
                }

                // 音质胶囊角标 (HyperOS 拟态磨砂微标)
                Box(
                    modifier = Modifier
                        .squircleClip(12.dp)
                        .background(Color.White.copy(alpha = 0.14f))
                        .border(1.dp, Color.White.copy(alpha = 0.18f), RoundedCornerShape(12.dp))
                        .padding(horizontal = 10.dp, vertical = 5.dp)
                ) {
                    Text(
                        text = when (audioQuality.level) {
                            "hires" -> "Hi-Res"
                            "lossless" -> "无损"
                            else -> "SQ"
                        },
                        color = Color.White,
                        fontSize = 10.sp,
                        fontWeight = FontWeight.ExtraBold,
                        letterSpacing = 0.5.sp
                    )
                }
            }

            Spacer(modifier = Modifier.height(10.dp))

            // ---------------------------------------------------------------------
            // 中央展示区：Apple Music 封面卡片 / Accompanist Lyrics UI / 黑胶转盘
            // ---------------------------------------------------------------------
            Box(
                modifier = Modifier
                    .weight(1f)
                    .fillMaxWidth(),
                contentAlignment = Alignment.Center
            ) {
                AnimatedContent(
                    targetState = displayMode,
                    transitionSpec = {
                        (fadeIn(tween(350)) + scaleIn(tween(350), initialScale = 0.95f))
                            .togetherWith(fadeOut(tween(250)) + scaleOut(tween(250), targetScale = 0.95f))
                    },
                    label = "center-display-switch"
                ) { mode ->
                    when (mode) {
                        CenterDisplayMode.COVER -> {
                            AppleMusicCoverView(
                                coverUrl = song.coverUrl,
                                title = song.title,
                                scale = coverScale,
                                shadowElevation = coverShadowElevation,
                                onCoverClick = { displayMode = CenterDisplayMode.LYRICS }
                            )
                        }
                        CenterDisplayMode.LYRICS -> {
                            AccompanistLyricsContainer(
                                lyrics = lyrics,
                                currentPositionMs = currentPositionMs,
                                onSeek = { NocturnPlayer.seekTo(it) }
                            )
                        }
                        CenterDisplayMode.VINYL -> {
                            VinylView(
                                coverUrl = song.coverUrl,
                                rotation = if (isPlaying) rotationAngle else 0f,
                                tonearmAngle = tonearmAngle,
                                onToggleCover = { displayMode = CenterDisplayMode.COVER }
                            )
                        }
                    }
                }
            }

            Spacer(modifier = Modifier.height(12.dp))

            // ---------------------------------------------------------------------
            // 歌曲标题、歌手与收藏 (Apple Music 风格大标题 + 小米澎湃红心交互)
            // ---------------------------------------------------------------------
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 4.dp, vertical = 2.dp),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.SpaceBetween
            ) {
                Column(modifier = Modifier.weight(1f)) {
                    Text(
                        text = song.title,
                        color = Color.White,
                        fontSize = 21.sp,
                        fontWeight = FontWeight.Bold,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis
                    )
                    Text(
                        text = song.artist,
                        color = Color.White.copy(alpha = 0.65f),
                        fontSize = 15.sp,
                        fontWeight = FontWeight.Medium,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                        modifier = Modifier.padding(top = 3.dp)
                    )
                }

                // 收藏红心按钮 (小米澎湃圆角药丸触控)
                Box(
                    modifier = Modifier
                        .size(44.dp)
                        .squircleClip(16.dp)
                        .background(Color.White.copy(alpha = if (isFavorite) 0.18f else 0.08f))
                        .clickable {
                            val newFav = !isFavorite
                            SettingsRepository.toggleFavorite(song)
                            if (SettingsRepository.userProfile.value.isLogin) {
                                scope.launch {
                                    MusicRepository.likeSong(song.id, newFav)
                                }
                            }
                        },
                    contentAlignment = Alignment.Center
                ) {
                    Text(
                        text = if (isFavorite) "♥" else "♡",
                        color = if (isFavorite) HyperRed else Color.White.copy(alpha = 0.9f),
                        fontSize = 22.sp,
                        fontWeight = FontWeight.Bold
                    )
                }
            }

            Spacer(modifier = Modifier.height(14.dp))

            // ---------------------------------------------------------------------
            // Apple Music 风格扁平高灵敏度进度条 (剩余时间显示)
            // ---------------------------------------------------------------------
            val progress = if (durationMs > 0) {
                (currentPositionMs.toFloat() / durationMs.toFloat()).coerceIn(0f, 1f)
            } else 0f

            val currentDisplayValue = if (isDraggingSlider) sliderValue else progress

            Column(modifier = Modifier.fillMaxWidth()) {
                Slider(
                    value = currentDisplayValue,
                    onValueChange = {
                        isDraggingSlider = true
                        sliderValue = it
                    },
                    onValueChangeFinished = {
                        isDraggingSlider = false
                        val targetMs = (sliderValue * durationMs).toLong()
                        NocturnPlayer.seekTo(targetMs)
                    },
                    colors = SliderDefaults.colors(
                        thumbColor = Color.White,
                        activeTrackColor = Color.White.copy(alpha = 0.85f),
                        inactiveTrackColor = Color.White.copy(alpha = 0.18f)
                    ),
                    modifier = Modifier.fillMaxWidth()
                )

                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 4.dp),
                    horizontalArrangement = Arrangement.SpaceBetween
                ) {
                    val currentSec = currentPositionMs / 1000
                    val totalSec = durationMs / 1000
                    val remainingSec = (durationMs - currentPositionMs).coerceAtLeast(0L) / 1000

                    Text(
                        text = "%02d:%02d".format(currentSec / 60, currentSec % 60),
                        color = Color.White.copy(alpha = 0.55f),
                        fontSize = 12.sp,
                        fontFamily = FontFamily.Monospace,
                        fontWeight = FontWeight.Medium
                    )
                    Text(
                        text = if (totalSec > 0) "-%02d:%02d".format(remainingSec / 60, remainingSec % 60) else "--:--",
                        color = Color.White.copy(alpha = 0.55f),
                        fontSize = 12.sp,
                        fontFamily = FontFamily.Monospace,
                        fontWeight = FontWeight.Medium
                    )
                }
            }

            Spacer(modifier = Modifier.height(14.dp))

            // ---------------------------------------------------------------------
            // 核心控制区 (融合 Apple Music 极简大键位与小米澎湃超级椭圆触控感)
            // ---------------------------------------------------------------------
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 4.dp),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.SpaceBetween
            ) {
                // 播放模式
                Box(
                    modifier = Modifier
                        .size(44.dp)
                        .squircleClip(14.dp)
                        .background(Color.White.copy(alpha = 0.08f))
                        .clickable { NocturnPlayer.togglePlayMode() },
                    contentAlignment = Alignment.Center
                ) {
                    val modeIcon = when (playMode) {
                        PlayMode.LIST_LOOP -> "🔁"
                        PlayMode.SINGLE_LOOP -> "🔂"
                        PlayMode.RANDOM -> "🔀"
                    }
                    Text(text = modeIcon, fontSize = 20.sp)
                }

                // 上一曲
                Box(
                    modifier = Modifier
                        .size(52.dp)
                        .squircleClip(18.dp)
                        .background(Color.White.copy(alpha = 0.1f))
                        .clickable { NocturnPlayer.playPrevious() },
                    contentAlignment = Alignment.Center
                ) {
                    Text(text = "⏮", color = Color.White, fontSize = 24.sp)
                }

                // 主播放/暂停键 (HyperOS 澎湃大圆角大按键，带柔光投射)
                Box(
                    modifier = Modifier
                        .size(72.dp)
                        .squircleClip(24.dp)
                        .background(Color.White)
                        .shadow(16.dp, CircleShape, ambientColor = Color.White.copy(alpha = 0.35f))
                        .clickable { NocturnPlayer.togglePlayPause() },
                    contentAlignment = Alignment.Center
                ) {
                    if (isBuffering) {
                        Text(text = "...", color = Color.Black, fontSize = 22.sp, fontWeight = FontWeight.ExtraBold)
                    } else {
                        Text(
                            text = if (isPlaying) "❚❚" else "▶",
                            color = Color.Black,
                            fontSize = 26.sp,
                            fontWeight = FontWeight.Bold,
                            modifier = Modifier.padding(start = if (isPlaying) 0.dp else 4.dp)
                        )
                    }
                }

                // 下一曲
                Box(
                    modifier = Modifier
                        .size(52.dp)
                        .squircleClip(18.dp)
                        .background(Color.White.copy(alpha = 0.1f))
                        .clickable { NocturnPlayer.playNext() },
                    contentAlignment = Alignment.Center
                ) {
                    Text(text = "⏭", color = Color.White, fontSize = 24.sp)
                }

                // 黑胶 / 封面模式切换小胶囊
                Box(
                    modifier = Modifier
                        .size(44.dp)
                        .squircleClip(14.dp)
                        .background(
                            if (displayMode == CenterDisplayMode.VINYL) HyperBlue.copy(alpha = 0.35f)
                            else Color.White.copy(alpha = 0.08f)
                        )
                        .clickable {
                            displayMode = if (displayMode == CenterDisplayMode.VINYL) CenterDisplayMode.COVER else CenterDisplayMode.VINYL
                        },
                    contentAlignment = Alignment.Center
                ) {
                    Text(text = "💿", fontSize = 18.sp)
                }
            }

            Spacer(modifier = Modifier.height(18.dp))

            // ---------------------------------------------------------------------
            // 底部辅助栏 (歌词开关与播放队列按键)
            // ---------------------------------------------------------------------
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(start = 8.dp, end = 8.dp, bottom = 8.dp),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                // 歌词按钮 (Apple Music 标志性对话框歌词图标)
                Box(
                    modifier = Modifier
                        .squircleClip(16.dp)
                        .background(
                            if (displayMode == CenterDisplayMode.LYRICS) Color.White.copy(alpha = 0.28f)
                            else Color.White.copy(alpha = 0.10f)
                        )
                        .clickable {
                            displayMode = if (displayMode == CenterDisplayMode.LYRICS) CenterDisplayMode.COVER else CenterDisplayMode.LYRICS
                        }
                        .padding(horizontal = 16.dp, vertical = 8.dp),
                    contentAlignment = Alignment.Center
                ) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Text(
                            text = "💬",
                            fontSize = 14.sp
                        )
                        Spacer(modifier = Modifier.width(6.dp))
                        Text(
                            text = if (displayMode == CenterDisplayMode.LYRICS) "返回封面" else "歌词",
                            color = Color.White,
                            fontSize = 13.sp,
                            fontWeight = FontWeight.SemiBold
                        )
                    }
                }

                // 播放列表按钮 (小米澎湃通透卡片)
                Box(
                    modifier = Modifier
                        .squircleClip(16.dp)
                        .background(Color.White.copy(alpha = 0.10f))
                        .clickable(onClick = onOpenQueue)
                        .padding(horizontal = 16.dp, vertical = 8.dp),
                    contentAlignment = Alignment.Center
                ) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Text(text = "☰", color = Color.White, fontSize = 14.sp, fontWeight = FontWeight.Bold)
                        Spacer(modifier = Modifier.width(6.dp))
                        Text(text = "播放列表", color = Color.White.copy(alpha = 0.9f), fontSize = 13.sp, fontWeight = FontWeight.SemiBold)
                    }
                }
            }
        }
    }
}

/**
 * Apple Music 风格大尺寸专辑封面展示卡片
 * 融入小米澎湃超级椭圆 (Squircle R28) 与环境光漫射投影
 */
@Composable
private fun AppleMusicCoverView(
    coverUrl: String,
    title: String,
    scale: Float,
    shadowElevation: Float,
    onCoverClick: () -> Unit,
    modifier: Modifier = Modifier
) {
    Box(
        modifier = modifier
            .fillMaxSize()
            .clickable(
                interactionSource = remember { MutableInteractionSource() },
                indication = null,
                onClick = onCoverClick
            ),
        contentAlignment = Alignment.Center
    ) {
        Box(
            modifier = Modifier
                .fillMaxWidth(0.85f)
                .aspectRatio(1f)
                .scale(scale)
                .shadow(
                    elevation = shadowElevation.dp,
                    shape = RoundedCornerShape(28.dp),
                    ambientColor = Color.Black.copy(alpha = 0.45f),
                    spotColor = Color.Black.copy(alpha = 0.6f)
                )
                .squircleClip(28.dp)
                .background(Color(0xFF1B1B1E))
        ) {
            AsyncImage(
                url = coverUrl,
                contentDescription = title,
                contentScale = ContentScale.Crop,
                modifier = Modifier.fillMaxSize()
            )
        }
    }
}

/**
 * 深度集成 Accompanist Lyrics UI 的逐字卡拉OK歌词容器
 */
@Composable
private fun AccompanistLyricsContainer(
    lyrics: SongLyric,
    currentPositionMs: Long,
    onSeek: (Long) -> Unit,
    modifier: Modifier = Modifier
) {
    val synced = lyrics.syncedLyrics
    val lines = synced?.lines ?: emptyList()

    if (lines.isEmpty()) {
        Box(
            modifier = modifier.fillMaxSize(),
            contentAlignment = Alignment.Center
        ) {
            Column(
                horizontalAlignment = Alignment.CenterHorizontally,
                verticalArrangement = Arrangement.Center
            ) {
                Text(
                    text = "♪",
                    color = Color.White.copy(alpha = 0.4f),
                    fontSize = 36.sp,
                    fontWeight = FontWeight.Light
                )
                Spacer(modifier = Modifier.height(12.dp))
                Text(
                    text = "纯音乐，请欣赏\n或暂无滚动歌词",
                    color = Color.White.copy(alpha = 0.65f),
                    fontSize = 16.sp,
                    lineHeight = 24.sp,
                    textAlign = TextAlign.Center,
                    fontWeight = FontWeight.Medium
                )
            }
        }
        return
    }

    val listState = rememberLazyListState()

    Box(
        modifier = modifier
            .fillMaxSize()
            .padding(horizontal = 4.dp)
    ) {
        KaraokeLyricsView(
            listState = listState,
            lyrics = synced ?: SyncedLyrics(emptyList()),
            currentPosition = { currentPositionMs.toInt() },
            onLineClicked = { line -> onSeek(line.start.toLong()) },
            onLinePressed = { /* 可提供单行复制或分享 */ },
            normalLineTextStyle = TextStyle(
                fontSize = 24.sp,
                fontWeight = FontWeight.Bold,
                lineHeight = 34.sp
            ),
            accompanimentLineTextStyle = TextStyle(
                fontSize = 17.sp,
                fontWeight = FontWeight.SemiBold,
                lineHeight = 25.sp
            ),
            textColor = Color.White,
            blendMode = BlendMode.Plus,
            useBlurEffect = true,
            blurDelta = 3.2f,
            showTranslation = true,
            showPhonetic = true,
            offset = 48.dp,
            modifier = Modifier.fillMaxSize()
        )
    }
}

/**
 * 经典黑胶唱盘视图 (保留传统发烧友复古美学)
 */
@Composable
private fun VinylView(
    coverUrl: String,
    rotation: Float,
    tonearmAngle: Float,
    onToggleCover: () -> Unit,
    modifier: Modifier = Modifier
) {
    Box(
        modifier = modifier
            .fillMaxWidth()
            .padding(16.dp)
            .clickable(
                interactionSource = remember { MutableInteractionSource() },
                indication = null,
                onClick = onToggleCover
            ),
        contentAlignment = Alignment.Center
    ) {
        Box(
            modifier = Modifier
                .size(280.dp)
                .clip(CircleShape)
                .background(Color(0xFF111113))
                .rotate(rotation),
            contentAlignment = Alignment.Center
        ) {
            Box(
                modifier = Modifier
                    .size(260.dp)
                    .clip(CircleShape)
                    .background(Color(0xFF1A1A1C)),
                contentAlignment = Alignment.Center
            ) {
                Box(
                    modifier = Modifier
                        .size(240.dp)
                        .clip(CircleShape)
                        .background(Color(0xFF131315)),
                    contentAlignment = Alignment.Center
                ) {
                    Box(
                        modifier = Modifier
                            .size(170.dp)
                            .clip(CircleShape)
                    ) {
                        AsyncImage(
                            url = coverUrl,
                            contentDescription = null,
                            modifier = Modifier.fillMaxSize()
                        )
                    }

                    Box(
                        modifier = Modifier
                            .size(28.dp)
                            .clip(CircleShape)
                            .background(Color(0xFF0A0A0A)),
                        contentAlignment = Alignment.Center
                    ) {
                        Box(
                            modifier = Modifier
                                .size(10.dp)
                                .clip(CircleShape)
                                .background(Color.White.copy(alpha = 0.8f))
                        )
                    }
                }
            }
        }

        Box(
            modifier = Modifier
                .align(Alignment.TopCenter)
                .padding(top = 10.dp)
                .rotate(tonearmAngle)
        ) {
            Box(
                modifier = Modifier
                    .width(4.dp)
                    .height(60.dp)
                    .background(Color.White.copy(alpha = 0.4f))
            )
        }
    }
}
