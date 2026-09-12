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
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
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
import androidx.compose.runtime.derivedStateOf
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import kotlinx.coroutines.delay
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
import com.nocturn.music.ui.theme.AppIcons
import top.yukonga.miuix.kmp.basic.Icon
import top.yukonga.miuix.kmp.basic.Text
import top.yukonga.miuix.kmp.theme.MiuixTheme
import androidx.compose.foundation.gestures.detectVerticalDragGestures
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.runtime.mutableLongStateOf
import com.nocturn.music.ui.navigation.SecondaryRoute

enum class CenterDisplayMode {
    COVER,
    LYRICS
}

@Composable
fun PlayerScreen(
    onDismiss: () -> Unit,
    onOpenQueue: () -> Unit,
    onNavigateToRoute: (SecondaryRoute) -> Unit = {},
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

    val isBlurEnabled by SettingsRepository.isBlurEnabled.collectAsState()
    val lyricOffsetMs by SettingsRepository.lyricOffsetMs.collectAsState()
    val isYrcHighlightEnabled by SettingsRepository.isYrcHighlightEnabled.collectAsState()

    val effectivePositionMs = (currentPositionMs + lyricOffsetMs).coerceAtLeast(0L)

    var displayMode by remember { mutableStateOf(CenterDisplayMode.COVER) }
    var lyrics by remember { mutableStateOf(SongLyric()) }
    var isDraggingSlider by remember { mutableStateOf(false) }
    var sliderValue by remember { mutableFloatStateOf(0f) }
    var showMoreSheet by remember { mutableStateOf(false) }

    // 下拉拖拽关闭手势状态 (配合 HyperOS 物理阻尼与弹性回弹)
    var dragOffsetY by remember { mutableFloatStateOf(0f) }
    val animatedDragOffsetY by animateFloatAsState(
        targetValue = dragOffsetY,
        animationSpec = spring(
            dampingRatio = 0.82f,
            stiffness = Spring.StiffnessMediumLow
        ),
        label = "playerDragOffset"
    )

    val song = currentSong ?: return
    val isFavorite = favoriteSongs.any { it.id == song.id }
    val scope = rememberCoroutineScope()

    LaunchedEffect(song.id) {
        lyrics = MusicRepository.getLyric(song.id)
    }

    // Apple Music 标志性超椭圆封面随播放状态缩放动效 (播放时放大且浮起，暂停时微缩)
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
            .graphicsLayer {
                translationY = animatedDragOffsetY
                val progress = (animatedDragOffsetY / 1200f).coerceIn(0f, 0.25f)
                scaleX = 1f - progress * 0.4f
                scaleY = 1f - progress * 0.4f
            }
            .background(Color(0xFF0C0C0E))
    ) {
        // =========================================================================
        // 1. Apple Music 动态流光漫反射背景 (Ambient Artwork Glow Layer)
        // =========================================================================
        Box(modifier = Modifier.fillMaxSize()) {
            if (isBlurEnabled) {
                AsyncImage(
                    url = song.coverUrl,
                    contentDescription = null,
                    contentScale = ContentScale.Crop,
                    modifier = Modifier
                        .fillMaxSize()
                        .scale(1.35f)
                        .blur(90.dp)
                )
            }

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
                .statusBarsPadding()
                .navigationBarsPadding()
                .padding(horizontal = 22.dp)
                .padding(bottom = 6.dp),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            // ---------------------------------------------------------------------
            // 类 Apple Music 顶部胶囊指示小横条 (支持向下滑动退出)
            // ---------------------------------------------------------------------
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(top = 8.dp, bottom = 8.dp)
                    .pointerInput(Unit) {
                        detectVerticalDragGestures(
                            onVerticalDrag = { _, dragAmount ->
                                if (dragAmount > 0 || dragOffsetY > 0) {
                                dragOffsetY = (dragOffsetY + dragAmount).coerceAtLeast(0f)
                            }
                            },
                            onDragEnd = {
                                if (dragOffsetY > 260f) {
                                    onDismiss()
                                }
                                dragOffsetY = 0f
                            },
                            onDragCancel = {
                                dragOffsetY = 0f
                            }
                        )
                    },
                contentAlignment = Alignment.Center
            ) {
                Box(
                    modifier = Modifier
                        .size(width = 38.dp, height = 4.5.dp)
                        .background(Color.White.copy(alpha = 0.35f), CircleShape)
                )
            }

            // ---------------------------------------------------------------------
            // 顶部导航与歌曲信息栏 (优化顶栏：去掉“正在播放”，优化图标，添加更多操作)
            // ---------------------------------------------------------------------
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(top = 4.dp, bottom = 8.dp)
                    .pointerInput(Unit) {
                        detectVerticalDragGestures(
                            onVerticalDrag = { _, dragAmount ->
                                if (dragAmount > 0 || dragOffsetY > 0) {
                                    dragOffsetY = (dragOffsetY + dragAmount).coerceAtLeast(0f)
                                }
                            },
                            onDragEnd = {
                                if (dragOffsetY > 260f) {
                                    onDismiss()
                                }
                                dragOffsetY = 0f
                            },
                            onDragCancel = {
                                dragOffsetY = 0f
                            }
                        )
                    },
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
                    Icon(
                        imageVector = AppIcons.ArrowDown,
                        contentDescription = "收起",
                        tint = Color.White,
                        modifier = Modifier.size(20.dp)
                    )
                }

                // 顶部曲目摘要 (已根据需求去除“正在播放”，直接展示精致歌名与歌手)
                Column(
                    horizontalAlignment = Alignment.CenterHorizontally,
                    modifier = Modifier
                        .weight(1f)
                        .padding(horizontal = 12.dp)
                ) {
                    Text(
                        text = song.title,
                        color = Color.White,
                        fontSize = 15.sp,
                        fontWeight = FontWeight.SemiBold,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis
                    )
                    Text(
                        text = song.artist,
                        color = Color.White.copy(alpha = 0.65f),
                        fontSize = 12.sp,
                        fontWeight = FontWeight.Normal,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                        modifier = Modifier.padding(top = 1.dp)
                    )
                }

                // 右侧功能区：音质微标 + 更多操作按钮 (三个点图标)
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    // 音质胶囊角标 (HyperOS 拟态磨砂微标)
                    Box(
                        modifier = Modifier
                            .squircleClip(12.dp)
                            .background(Color.White.copy(alpha = 0.14f))
                            .border(1.dp, Color.White.copy(alpha = 0.18f), RoundedCornerShape(12.dp))
                            .clickable { showMoreSheet = true }
                            .padding(horizontal = 9.dp, vertical = 5.dp)
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

                    // 更多选项按钮 (MIUIX 更多操作抽屉入口)
                    Box(
                        modifier = Modifier
                            .size(40.dp)
                            .squircleClip(14.dp)
                            .background(Color.White.copy(alpha = 0.12f))
                            .clickable(
                                interactionSource = remember { MutableInteractionSource() },
                                indication = null,
                                onClick = { showMoreSheet = true }
                            ),
                        contentAlignment = Alignment.Center
                    ) {
                        Icon(
                            imageVector = AppIcons.More,
                            contentDescription = "更多选项",
                            tint = Color.White,
                            modifier = Modifier.size(20.dp)
                        )
                    }
                }
            }

            Spacer(modifier = Modifier.height(6.dp))

            // ---------------------------------------------------------------------
            // 中央展示区：Apple Music 封面卡片 / Accompanist Lyrics UI / 黑胶转盘
            // (非歌词浏览模式下，空白处支持随心向下滑动退出播放页)
            // ---------------------------------------------------------------------
            Box(
                modifier = Modifier
                    .weight(1f)
                    .fillMaxWidth()
                    .then(
                        if (displayMode != CenterDisplayMode.LYRICS) {
                            Modifier.pointerInput(Unit) {
                                detectVerticalDragGestures(
                                    onVerticalDrag = { _, dragAmount ->
                                        if (dragAmount > 0 || dragOffsetY > 0) {
                                            dragOffsetY = (dragOffsetY + dragAmount).coerceAtLeast(0f)
                                        }
                                    },
                                    onDragEnd = {
                                        if (dragOffsetY > 260f) {
                                            onDismiss()
                                        }
                                        dragOffsetY = 0f
                                    },
                                    onDragCancel = {
                                        dragOffsetY = 0f
                                    }
                                )
                            }
                        } else Modifier
                    ),
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
                                currentPositionMs = effectivePositionMs,
                                onSeek = { NocturnPlayer.seekTo(it) },
                                isBlurEnabled = isBlurEnabled
                            )
                        }
                    }
                }
            }

            Spacer(modifier = Modifier.height(10.dp))

            // ---------------------------------------------------------------------
            // 底部控制操作区容器 (空白处支持向下滑动退出播放页)
            // ---------------------------------------------------------------------
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .pointerInput(Unit) {
                        detectVerticalDragGestures(
                            onVerticalDrag = { _, dragAmount ->
                                if (dragAmount > 0 || dragOffsetY > 0) {
                                    dragOffsetY = (dragOffsetY + dragAmount).coerceAtLeast(0f)
                                }
                            },
                            onDragEnd = {
                                if (dragOffsetY > 260f) {
                                    onDismiss()
                                }
                                dragOffsetY = 0f
                            },
                            onDragCancel = {
                                dragOffsetY = 0f
                            }
                        )
                    }
            ) {
                // -----------------------------------------------------------------
                // 歌曲标题、歌手与收藏 (Apple Music 风格大标题 + 小米澎湃红心交互)
                // -----------------------------------------------------------------
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
                        Icon(
                            imageVector = if (isFavorite) AppIcons.FavoritesFill else AppIcons.Favorites,
                            contentDescription = if (isFavorite) "取消喜欢" else "喜欢",
                            tint = if (isFavorite) HyperRed else Color.White.copy(alpha = 0.9f),
                            modifier = Modifier.size(24.dp)
                        )
                    }
                }

                Spacer(modifier = Modifier.height(12.dp))

                // -----------------------------------------------------------------
                // Apple Music 风格扁平高灵敏度进度条 (剩余时间显示)
                // -----------------------------------------------------------------
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

                // -----------------------------------------------------------------
                // 核心控制区 (融合 Apple Music 极简大键位与小米澎湃超级椭圆触控感)
                // -----------------------------------------------------------------
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
                        val (modeIcon, modeDesc) = when (playMode) {
                            PlayMode.LIST_LOOP -> AppIcons.Repeat to "列表循环"
                            PlayMode.SINGLE_LOOP -> AppIcons.RepeatOne to "单曲循环"
                            PlayMode.RANDOM -> AppIcons.Shuffle to "随机播放"
                        }
                        Icon(
                            imageVector = modeIcon,
                            contentDescription = modeDesc,
                            tint = Color.White.copy(alpha = 0.9f),
                            modifier = Modifier.size(20.dp)
                        )
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
                        Icon(
                            imageVector = AppIcons.SkipPrevious,
                            contentDescription = "上一曲",
                            tint = Color.White,
                            modifier = Modifier.size(24.dp)
                        )
                    }

                    // 主播放/暂停键 (HyperOS 澎湃大圆角大按键，带柔光投射与物理弹性动画)
                    val playBtnScale by animateFloatAsState(
                        targetValue = if (isPlaying) 1.0f else 0.94f,
                        animationSpec = spring(
                            dampingRatio = Spring.DampingRatioMediumBouncy,
                            stiffness = Spring.StiffnessLow
                        ),
                        label = "playerPlayBtnScale"
                    )
                    Box(
                        modifier = Modifier
                            .size(72.dp)
                            .scale(playBtnScale)
                            .squircleClip(24.dp)
                            .background(Color.White)
                            .shadow(16.dp, CircleShape, ambientColor = Color.White.copy(alpha = 0.35f))
                            .clickable { NocturnPlayer.togglePlayPause() },
                        contentAlignment = Alignment.Center
                    ) {
                        if (isBuffering) {
                            Text(text = "...", color = Color.Black, fontSize = 22.sp, fontWeight = FontWeight.ExtraBold)
                        } else {
                            AnimatedContent(
                                targetState = isPlaying,
                                transitionSpec = {
                                    (scaleIn(spring(dampingRatio = Spring.DampingRatioMediumBouncy)) + fadeIn(tween(180)))
                                        .togetherWith(scaleOut(spring(dampingRatio = Spring.DampingRatioMediumBouncy)) + fadeOut(tween(140)))
                                },
                                label = "playPauseIconAnim"
                            ) { playing ->
                                Icon(
                                    imageVector = if (playing) AppIcons.Pause else AppIcons.Play,
                                    contentDescription = if (playing) "暂停" else "播放",
                                    tint = Color.Black,
                                    modifier = Modifier.size(30.dp)
                                )
                            }
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
                        Icon(
                            imageVector = AppIcons.SkipNext,
                            contentDescription = "下一曲",
                            tint = Color.White,
                            modifier = Modifier.size(24.dp)
                        )
                    }

                    // 播放列表按钮 (与左侧播放模式按键对称呼应)
                    Box(
                        modifier = Modifier
                            .size(44.dp)
                            .squircleClip(14.dp)
                            .background(Color.White.copy(alpha = 0.08f))
                            .clickable(onClick = onOpenQueue),
                        contentAlignment = Alignment.Center
                    ) {
                        Icon(
                            imageVector = AppIcons.Playlist,
                            contentDescription = "播放列表",
                            tint = Color.White.copy(alpha = 0.9f),
                            modifier = Modifier.size(20.dp)
                        )
                    }
                }

                Spacer(modifier = Modifier.height(18.dp))

                // -----------------------------------------------------------------
                // 底部辅助栏 (Apple Music 风格居中歌词/超椭圆封面切换胶囊)
                // -----------------------------------------------------------------
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(bottom = 6.dp),
                    contentAlignment = Alignment.Center
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
                            .padding(horizontal = 20.dp, vertical = 8.dp),
                        contentAlignment = Alignment.Center
                    ) {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Icon(
                                imageVector = AppIcons.Messages,
                                contentDescription = null,
                                tint = Color.White,
                                modifier = Modifier.size(16.dp)
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
                }
            }
        }

        // =========================================================================
        // 3. MIUIX“更多”操作底部抽屉 (加入歌单、歌曲百科、歌手专辑、音质设置等)
        // =========================================================================
        PlayerMoreActionSheet(
            show = showMoreSheet,
            song = song,
            onDismissRequest = { showMoreSheet = false },
            onNavigateToRoute = onNavigateToRoute
        )
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
    modifier: Modifier = Modifier,
    isBlurEnabled: Boolean = true
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
                Icon(
                    imageVector = AppIcons.Music,
                    contentDescription = null,
                    tint = Color.White.copy(alpha = 0.4f),
                    modifier = Modifier.size(36.dp)
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

    val coroutineScope = rememberCoroutineScope()
    val listState = rememberLazyListState()

    val currentPositionState = rememberUpdatedState(currentPositionMs)

    // 用户手动浏览状态与锁定位置：
    // 当用户手动滑动列表时，激活浏览状态并锁定当前提供给 KaraokeLyricsView 的时间，
    // 彻底切断内部 snapshotFlow 频繁触发 scrollBy 导致与用户拖动手势冲突、强行拉回的恶性循环！
    var isUserBrowsing by remember { mutableStateOf(false) }
    var lockedPositionMs by remember { mutableLongStateOf(0L) }

    LaunchedEffect(listState.isScrollInProgress) {
        if (listState.isScrollInProgress) {
            if (!isUserBrowsing) {
                lockedPositionMs = currentPositionState.value
                isUserBrowsing = true
            }
        }
    }

    // 计算当前处于激活播放状态的歌词索引
    val activeLineIndex by remember(lines) {
        derivedStateOf {
            val time = currentPositionState.value.toInt()
            val activeIndex = lines.indexOfFirst { line ->
                time >= line.start && time < line.end
            }
            if (activeIndex != -1) {
                activeIndex
            } else {
                val nextIdx = lines.indexOfFirst { it.start > time }
                if (nextIdx != -1) (nextIdx - 1).coerceAtLeast(0) else lines.lastIndex
            }
        }
    }

    // 闲置 12 秒后平滑恢复焦点行自动追踪
    LaunchedEffect(isUserBrowsing, listState.isScrollInProgress) {
        if (isUserBrowsing && !listState.isScrollInProgress) {
            delay(12000)
            isUserBrowsing = false
            if (activeLineIndex in lines.indices) {
                val scrollTarget = (activeLineIndex - 1).coerceAtLeast(0)
                listState.animateScrollToItem(scrollTarget)
            }
        }
    }

    val currentPositionProvider = remember {
        {
            if (isUserBrowsing) {
                lockedPositionMs.toInt()
            } else {
                currentPositionState.value.toInt()
            }
        }
    }

    // 初次进入或切换歌曲时，直达焦点行
    LaunchedEffect(lyrics) {
        isUserBrowsing = false
        val initialIdx = activeLineIndex
        if (initialIdx in lines.indices) {
            val scrollTarget = (initialIdx - 1).coerceAtLeast(0)
            listState.scrollToItem(scrollTarget)
        }
    }

    Box(
        modifier = modifier
            .fillMaxSize()
            .padding(horizontal = 4.dp)
    ) {
        KaraokeLyricsView(
            listState = listState,
            lyrics = synced ?: SyncedLyrics(emptyList()),
            currentPosition = currentPositionProvider,
            onLineClicked = { line ->
                onSeek(line.start.toLong())
                isUserBrowsing = false
                val clickedIdx = lines.indexOf(line)
                if (clickedIdx != -1) {
                    coroutineScope.launch {
                        val scrollTarget = (clickedIdx - 1).coerceAtLeast(0)
                        listState.animateScrollToItem(scrollTarget)
                    }
                }
            },
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
            useBlurEffect = isBlurEnabled,
            blurDelta = 3.2f,
            showTranslation = true,
            showPhonetic = true,
            offset = 48.dp,
            modifier = Modifier.fillMaxSize()
        )

        // Apple Music 风格“回到正在播放”悬浮胶囊按钮
        androidx.compose.animation.AnimatedVisibility(
            visible = isUserBrowsing,
            enter = androidx.compose.animation.fadeIn(tween(250)) + androidx.compose.animation.slideInVertically(tween(250)) { it / 2 },
            exit = androidx.compose.animation.fadeOut(tween(200)) + androidx.compose.animation.slideOutVertically(tween(200)) { it / 2 },
            modifier = Modifier
                .align(Alignment.BottomCenter)
                .padding(bottom = 12.dp)
        ) {
            Box(
                modifier = Modifier
                    .squircleClip(20.dp)
                    .background(Color.White.copy(alpha = 0.22f))
                    .border(1.dp, Color.White.copy(alpha = 0.3f), RoundedCornerShape(20.dp))
                    .clickable {
                        isUserBrowsing = false
                        coroutineScope.launch {
                            if (activeLineIndex in lines.indices) {
                                val scrollTarget = (activeLineIndex - 1).coerceAtLeast(0)
                                listState.animateScrollToItem(scrollTarget)
                            }
                        }
                    }
                    .padding(horizontal = 16.dp, vertical = 8.dp),
                contentAlignment = Alignment.Center
            ) {
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(6.dp)
                ) {
                    Icon(
                        imageVector = AppIcons.Refresh,
                        contentDescription = null,
                        tint = Color.White,
                        modifier = Modifier.size(15.dp)
                    )
                    Text(
                        text = "回到正在播放",
                        color = Color.White,
                        fontSize = 13.sp,
                        fontWeight = FontWeight.SemiBold
                    )
                }
            }
        }
    }
}

