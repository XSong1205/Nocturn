@file:OptIn(
    androidx.compose.material3.ExperimentalMaterial3Api::class,
    androidx.compose.material3.ExperimentalMaterial3ExpressiveApi::class,
)

package dev.nocturn.app.ui.screens.player

import android.content.res.Configuration
import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.SizeTransform
import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.animate
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.spring
import androidx.compose.animation.core.tween
import androidx.compose.animation.expandVertically
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.scaleIn
import androidx.compose.animation.scaleOut
import androidx.compose.animation.shrinkVertically
import androidx.compose.animation.slideInHorizontally
import androidx.compose.animation.slideInVertically
import androidx.compose.animation.slideOutHorizontally
import androidx.compose.animation.slideOutVertically
import androidx.compose.animation.togetherWith
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.Orientation
import androidx.compose.foundation.gestures.detectDragGestures
import androidx.compose.foundation.gestures.draggable
import androidx.compose.foundation.gestures.rememberDraggableState
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.WindowInsetsSides
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.only
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.safeDrawing
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.windowInsetsPadding
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.QueueMusic
import androidx.compose.material.icons.rounded.CloudOff
import androidx.compose.material.icons.rounded.ExpandMore
import androidx.compose.material.icons.rounded.Favorite
import androidx.compose.material.icons.rounded.FavoriteBorder
import androidx.compose.material.icons.rounded.Lyrics
import androidx.compose.material.icons.rounded.MusicNote
import androidx.compose.material.icons.rounded.Pause
import androidx.compose.material.icons.rounded.PlayArrow
import androidx.compose.material.icons.rounded.Repeat
import androidx.compose.material.icons.rounded.RepeatOne
import androidx.compose.material.icons.rounded.Shuffle
import androidx.compose.material.icons.rounded.SkipNext
import androidx.compose.material.icons.rounded.SkipPrevious
import androidx.compose.material3.HorizontalFloatingToolbar
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.IconToggleButton
import androidx.compose.material3.LinearWavyProgressIndicator
import androidx.compose.material3.LoadingIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Slider
import androidx.compose.material3.SliderDefaults
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Shape
import androidx.compose.ui.graphics.TransformOrigin
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.input.pointer.util.VelocityTracker
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.LocalLayoutDirection
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.LayoutDirection
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.lerp
import androidx.compose.ui.util.lerp
import dev.nocturn.app.data.model.Song
import dev.nocturn.app.data.model.LyricLine
import dev.nocturn.app.data.model.ThemeMode
import dev.nocturn.app.playback.RepeatMode
import dev.nocturn.app.ui.adaptive.LocalWindowWidthClass
import dev.nocturn.app.ui.adaptive.WindowWidthClass
import dev.nocturn.app.ui.components.CoverArt
import dev.nocturn.app.ui.components.GlassMaterials
import dev.nocturn.app.ui.components.LikeButton
import dev.nocturn.app.ui.components.LocalShellBackdrop
import dev.nocturn.app.ui.components.NocturnBackdrop
import dev.nocturn.app.ui.components.PlayPauseButton
import dev.nocturn.app.ui.components.PlayerMonetBackdrop
import dev.nocturn.app.ui.components.glass
import dev.nocturn.app.ui.components.pressScale
import dev.nocturn.app.ui.components.rememberHaptics
import dev.nocturn.app.ui.preview.PreviewData
import dev.nocturn.app.ui.state.MiniPlayerUiState
import dev.nocturn.app.ui.state.PlaybackMode
import dev.nocturn.app.ui.state.PlayerUiState
import dev.nocturn.app.ui.theme.NocturnText
import dev.nocturn.app.ui.theme.NocturnTheme
import dev.nocturn.app.ui.util.formatDuration
import kotlin.math.abs
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch

/**
 * Full-screen "now playing" surface in the Material 3 Expressive language.
 *
 * The player is a single immersive stage: an oversized cover that flips to a centred,
 * position-synced lyric sheet, a wavy seek bar that doubles as a slider, an
 * emphasised transport row and a floating toolbar for the secondary actions. [PlayerScreen] is
 * purely presentational — every interaction is reported through one of the callbacks so the shell
 * keeps owning playback.
 *
 * Signature is part of the shell contract: keep [state] and the callbacks (new parameters must be
 * optional).
 */
@Composable
fun PlayerScreen(
    state: PlayerUiState,
    modifier: Modifier = Modifier,
    onCollapse: () -> Unit = {},
    onTogglePlay: () -> Unit = {},
    onNext: () -> Unit = {},
    onPrevious: () -> Unit = {},
    onSeek: (Float) -> Unit = {},
    onToggleLike: () -> Unit = {},
    onToggleShuffle: () -> Unit = {},
    onCycleRepeat: () -> Unit = {},
    onCyclePlaybackMode: () -> Unit = onCycleRepeat,
    onQueueSongClick: (Int) -> Unit = {},
    initialLyricsVisible: Boolean = false,
    sheetDrag: PlayerSheetDrag? = null,
) {
    var showLyrics by rememberSaveable { mutableStateOf(initialLyricsVisible) }
    var queueVisible by rememberSaveable { mutableStateOf(false) }
    var showTranslation by rememberSaveable { mutableStateOf(true) }
    var showRomanization by rememberSaveable { mutableStateOf(false) }
    val hasTranslation = remember(state.lyrics) { state.lyrics.lines.any { !it.translation.isNullOrBlank() } }
    val hasRomanization = remember(state.lyrics) { state.lyrics.lines.any { !it.romanization.isNullOrBlank() } }
    val onLyricLineClick: ((LyricLine) -> Unit)? = remember(state.durationMs, onSeek) {
        val duration = state.durationMs
        if (duration > 0L) {
            { line -> onSeek((line.startMs.toFloat() / duration).coerceIn(0f, 1f)) }
        } else null
    }
    // While the user drags the seek bar the UI previews the target position instead of the
    // playback position; the real seek is committed on release.
    var seekPreview by remember { mutableStateOf<Float?>(null) }

    val song = state.song
    val displayedProgress = (seekPreview ?: state.progress).coerceIn(0f, 1f)
    val displayedPositionMs = if (seekPreview != null && state.durationMs > 0) {
        (seekPreview!! * state.durationMs.toFloat()).toLong()
    } else {
        state.positionMs
    }
    val spatial = MaterialTheme.motionScheme.defaultSpatialSpec<Float>()
    val spatialOffset = MaterialTheme.motionScheme.defaultSpatialSpec<IntOffset>()
    val effects = MaterialTheme.motionScheme.defaultEffectsSpec<Float>()

    // Swipe-down-to-dismiss on the header and cover. Inside the shell the drag drives the sheet
    // morph back into the mini player ([sheetDrag]); standalone, the surface follows the finger,
    // shrinks and rounds, and either collapses or springs back on release.
    val haptics = rememberHaptics()
    val dismissThreshold = with(LocalDensity.current) { 140.dp.toPx() }
    var dragOffset by remember { mutableFloatStateOf(0f) }
    var pastThreshold by remember { mutableStateOf(false) }
    val latestSheetDrag by rememberUpdatedState(sheetDrag)
    val dragState = rememberDraggableState { delta ->
        val sheet = latestSheetDrag
        if (sheet != null) {
            sheet.onDrag(delta)
            return@rememberDraggableState
        }
        dragOffset = (dragOffset + delta).coerceAtLeast(0f)
        val past = dragOffset > dismissThreshold
        if (past && !pastThreshold) haptics.threshold()
        pastThreshold = past
    }
    val dismissDrag = Modifier.draggable(
        state = dragState,
        orientation = Orientation.Vertical,
        onDragStarted = { latestSheetDrag?.onDragStart() },
        onDragStopped = { velocity ->
            val sheet = latestSheetDrag
            if (sheet != null) {
                sheet.onDragEnd(velocity)
            } else if (dragOffset > dismissThreshold || velocity > 2_000f) {
                haptics.release()
                onCollapse()
            } else {
                animate(
                    initialValue = dragOffset,
                    targetValue = 0f,
                    animationSpec = spring(dampingRatio = 0.72f, stiffness = Spring.StiffnessMediumLow),
                ) { value, _ -> dragOffset = value }
            }
            pastThreshold = false
        },
    )

    Surface(
        modifier = modifier
            .fillMaxSize()
            .graphicsLayer {
                if (dragOffset <= 0f) return@graphicsLayer
                val fraction = (dragOffset / (size.height * 0.5f)).coerceIn(0f, 1f)
                translationY = dragOffset
                val scale = 1f - 0.06f * fraction
                scaleX = scale
                scaleY = scale
                transformOrigin = TransformOrigin(0.5f, 0f)
                shape = RoundedCornerShape((32f * fraction).dp)
                clip = true
            },
        color = Color.Transparent,
    ) {
        Box(modifier = Modifier.fillMaxSize()) {
            PlayerMonetBackdrop(song = song)
            Column(
                modifier = Modifier
                    .fillMaxSize()
                    .windowInsetsPadding(WindowInsets.safeDrawing.only(WindowInsetsSides.Vertical + WindowInsetsSides.Horizontal))
                    .padding(horizontal = 20.dp),
                horizontalAlignment = Alignment.CenterHorizontally,
            ) {
                val isExpanded = LocalWindowWidthClass.current.isExpanded
                PlayerHeader(
                    song = song,
                    onCollapse = onCollapse,
                    showLyrics = false,
                    modifier = dismissDrag,
                )
                val error = state.error
                // Keep the last message so the banner does not go blank while it collapses.
                val shownError = remember { mutableStateOf("") }
                if (error != null) shownError.value = error
                AnimatedVisibility(
                    visible = error != null,
                    enter = expandVertically() + fadeIn(),
                    exit = shrinkVertically() + fadeOut(),
                ) {
                    PlayerErrorBanner(message = shownError.value)
                }
                if (song == null) {
                    PlayerEmptyState(modifier = Modifier.weight(1f))
                } else if (isExpanded) {
                    Row(
                        modifier = Modifier
                            .weight(1f)
                            .fillMaxWidth()
                            .padding(vertical = 12.dp),
                        horizontalArrangement = Arrangement.spacedBy(32.dp),
                    ) {
                        Column(
                            modifier = Modifier
                                .weight(1f)
                                .fillMaxHeight(),
                            horizontalAlignment = Alignment.CenterHorizontally,
                            verticalArrangement = Arrangement.SpaceBetween,
                        ) {
                            PlayerCoverStage(
                                song = song,
                                playing = state.playing,
                                buffering = state.buffering,
                                liked = state.liked,
                                onToggleLike = onToggleLike,
                                onToggleStage = {},
                                modifier = Modifier
                                    .weight(1f)
                                    .fillMaxWidth(),
                            )
                            Spacer(Modifier.height(16.dp))
                            PlayerProgressSection(
                                progress = displayedProgress,
                                positionMs = displayedPositionMs,
                                durationMs = state.durationMs,
                                playing = state.playing,
                                seeking = seekPreview != null,
                                onSeekPreview = { seekPreview = it },
                                onSeekCommit = {
                                    seekPreview?.let(onSeek)
                                    seekPreview = null
                                },
                            )
                            Spacer(Modifier.height(8.dp))
                            PlayerTransport(
                                playing = state.playing,
                                onPrevious = onPrevious,
                                onTogglePlay = onTogglePlay,
                                onNext = onNext,
                            )
                            Spacer(Modifier.height(8.dp))
                            PlayerActionsToolbar(
                                showLyrics = true,
                                onShowLyricsChange = {},
                                liked = state.liked,
                                shuffle = state.shuffle,
                                repeatMode = state.repeatMode,
                                playbackMode = state.playbackMode,
                                onToggleLike = onToggleLike,
                                onToggleShuffle = onToggleShuffle,
                                onCycleRepeat = onCycleRepeat,
                                onCyclePlaybackMode = onCyclePlaybackMode,
                                onOpenQueue = { queueVisible = true },
                                showLikeInToolbar = false,
                            )
                        }
                        Column(
                            modifier = Modifier
                                .weight(1.1f)
                                .fillMaxHeight(),
                            horizontalAlignment = Alignment.CenterHorizontally,
                        ) {
                            if (hasTranslation || hasRomanization) {
                                PlayerLyricsOptions(
                                    hasTranslation = hasTranslation,
                                    hasRomanization = hasRomanization,
                                    showTranslation = showTranslation,
                                    showRomanization = showRomanization,
                                    onShowTranslationChange = { showTranslation = it },
                                    onShowRomanizationChange = { showRomanization = it },
                                )
                                Spacer(Modifier.height(8.dp))
                            }
                            LyricsView(
                                lyrics = state.lyrics,
                                positionMs = displayedPositionMs,
                                loading = state.lyricsLoading,
                                durationMs = state.durationMs,
                                playing = state.playing && !state.buffering && seekPreview == null,
                                positionSampledAtNanos = state.positionSampledAtNanos,
                                positionDiscontinuity = state.positionDiscontinuity,
                                playbackSpeed = state.playbackSpeed,
                                positionOverride = seekPreview != null,
                                showTranslation = showTranslation,
                                showRomanization = showRomanization,
                                modifier = Modifier
                                    .weight(1f)
                                    .fillMaxWidth(),
                                onLineClick = onLyricLineClick,
                                songId = song.id,
                            )
                        }
                    }
                } else {
                    PlayerSmoothStage(
                        song = song,
                        lyrics = state.lyrics,
                        showLyrics = showLyrics,
                        lyricsLoading = state.lyricsLoading,
                        playing = state.playing,
                        buffering = state.buffering,
                        liked = state.liked,
                        positionMs = displayedPositionMs,
                        durationMs = state.durationMs,
                        positionSampledAtNanos = state.positionSampledAtNanos,
                        positionDiscontinuity = state.positionDiscontinuity,
                        playbackSpeed = state.playbackSpeed,
                        seekPreviewActive = seekPreview != null,
                        showTranslation = showTranslation,
                        showRomanization = showRomanization,
                        hasTranslation = hasTranslation,
                        hasRomanization = hasRomanization,
                        onShowTranslationChange = { showTranslation = it },
                        onShowRomanizationChange = { showRomanization = it },
                        onToggleLike = onToggleLike,
                        onToggleLyrics = { showLyrics = it },
                        onLineClick = onLyricLineClick,
                        modifier = Modifier
                            .weight(1f)
                            .fillMaxWidth(),
                        dismissDragModifier = dismissDrag,
                    )
                    PlayerProgressSection(
                        progress = displayedProgress,
                        positionMs = displayedPositionMs,
                        durationMs = state.durationMs,
                        playing = state.playing,
                        seeking = seekPreview != null,
                        onSeekPreview = { seekPreview = it },
                        onSeekCommit = {
                            seekPreview?.let(onSeek)
                            seekPreview = null
                        },
                    )
                    PlayerTransport(
                        playing = state.playing,
                        onPrevious = onPrevious,
                        onTogglePlay = onTogglePlay,
                        onNext = onNext,
                    )
                    PlayerActionsToolbar(
                        showLyrics = showLyrics,
                        onShowLyricsChange = { showLyrics = it },
                        liked = state.liked,
                        shuffle = state.shuffle,
                        repeatMode = state.repeatMode,
                        playbackMode = state.playbackMode,
                        onToggleLike = onToggleLike,
                        onToggleShuffle = onToggleShuffle,
                        onCycleRepeat = onCycleRepeat,
                        onCyclePlaybackMode = onCyclePlaybackMode,
                        onOpenQueue = { queueVisible = true },
                        showLikeInToolbar = false,
                    )
                }
            }
        }
    }

    if (queueVisible && song != null) {
        PlayerQueueSheet(
            state = state,
            onDismiss = { queueVisible = false },
            onSongClick = { index ->
                queueVisible = false
                onQueueSongClick(index)
            },
        )
    }
}

@Composable
private fun PlayerHeader(
    song: Song?,
    onCollapse: () -> Unit,
    showLyrics: Boolean = false,
    modifier: Modifier = Modifier,
) {
    val haptics = rememberHaptics()
    Column(
        modifier = modifier
            .fillMaxWidth()
            .padding(horizontal = 4.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        // 小横条：支持点击收起，同时所在整个 Header 均附加了 dismissDrag 手势以支持向下滑动收起
        Box(
            modifier = Modifier
                .padding(top = 18.dp, bottom = if (showLyrics) 8.dp else 16.dp)
                .size(width = 40.dp, height = 4.5.dp)
                .clip(CircleShape)
                .background(MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.35f))
                .clickable(
                    role = Role.Button,
                    onClickLabel = "收起播放器",
                    onClick = {
                        haptics.step()
                        onCollapse()
                    },
                )
                .testTag("player-collapse"),
        )
        AnimatedVisibility(
            visible = showLyrics && song != null,
            enter = fadeIn(tween(240)) + expandVertically(),
            exit = fadeOut(tween(180)) + shrinkVertically(),
        ) {
            Column(
                horizontalAlignment = Alignment.CenterHorizontally,
                modifier = Modifier
                    .fillMaxWidth()
                    .height(48.dp)
                    .padding(horizontal = 24.dp),
                verticalArrangement = Arrangement.Center,
            ) {
                if (song != null) {
                    Text(
                        text = song.name,
                        style = MaterialTheme.typography.titleMedium,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                    )
                    Text(
                        text = song.artistText,
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                    )
                }
            }
        }
    }
}

@Composable
private fun PlayerErrorBanner(
    message: String,
    modifier: Modifier = Modifier,
) {
    Surface(
        modifier = modifier
            .fillMaxWidth()
            .padding(top = 6.dp),
        shape = MaterialTheme.shapes.large,
        color = MaterialTheme.colorScheme.errorContainer,
        contentColor = MaterialTheme.colorScheme.onErrorContainer,
    ) {
        Row(
            modifier = Modifier.padding(horizontal = 14.dp, vertical = 10.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Icon(
                imageVector = Icons.Rounded.CloudOff,
                contentDescription = null,
                modifier = Modifier.size(18.dp),
            )
            Spacer(Modifier.width(10.dp))
            Text(
                text = message,
                style = MaterialTheme.typography.bodySmall,
                modifier = Modifier.weight(1f),
            )
        }
    }
}

@Composable
private fun PlayerSmoothStage(
    song: Song,
    lyrics: dev.nocturn.app.data.model.Lyrics,
    showLyrics: Boolean,
    lyricsLoading: Boolean,
    playing: Boolean,
    buffering: Boolean,
    liked: Boolean,
    positionMs: Long,
    durationMs: Long,
    positionSampledAtNanos: Long,
    positionDiscontinuity: Long,
    playbackSpeed: Float,
    seekPreviewActive: Boolean,
    showTranslation: Boolean,
    showRomanization: Boolean,
    hasTranslation: Boolean,
    hasRomanization: Boolean,
    onShowTranslationChange: (Boolean) -> Unit,
    onShowRomanizationChange: (Boolean) -> Unit,
    onToggleLike: () -> Unit,
    onToggleLyrics: (Boolean) -> Unit,
    onLineClick: ((LyricLine) -> Unit)?,
    modifier: Modifier = Modifier,
    dismissDragModifier: Modifier = Modifier,
) {
    val haptics = rememberHaptics()
    val spatialOffset = MaterialTheme.motionScheme.defaultSpatialSpec<IntOffset>()
    val spatial = MaterialTheme.motionScheme.defaultSpatialSpec<Float>()
    val shadowColor = MaterialTheme.colorScheme.primary.copy(alpha = 0.28f)

    // 零重组高性能动画驱动器：Progress 仅在 Draw / GraphicsLayer 阶段读取，彻底避免每帧重组
    val progressAnimatable = remember { Animatable(if (showLyrics) 1f else 0f) }
    LaunchedEffect(showLyrics) {
        val target = if (showLyrics) 1f else 0f
        if (progressAnimatable.targetValue != target) {
            progressAnimatable.animateTo(
                targetValue = target,
                animationSpec = spring(
                    dampingRatio = 0.80f,
                    stiffness = 380f,
                    visibilityThreshold = 0.001f,
                ),
            )
        }
    }

    val coverPlayScale by animateFloatAsState(
        targetValue = if (playing) 1f else 0.94f,
        animationSpec = MaterialTheme.motionScheme.slowSpatialSpec(),
        label = "cover-play-scale",
    )

    BoxWithConstraints(
        modifier = modifier
            .fillMaxWidth()
            .then(if (!showLyrics) dismissDragModifier else Modifier),
    ) {
        val density = LocalDensity.current
        val availableWidth = maxWidth
        val availableHeight = maxHeight

        // 歌词模式下的顶部 Mini Header 规格
        val smallCoverSide = 48.dp
        val smallCoverX = 4.dp
        val smallCoverY = 2.dp
        val smallCorner = 10.dp
        val miniHeaderHeight = smallCoverSide + 8.dp

        // 封面模式下的居中大封面与详情规格
        val largeDetailsHeight = 64.dp
        val maxCoverHeight = (availableHeight - largeDetailsHeight - 24.dp).coerceAtLeast(120.dp)
        val largeCoverSide = minOf(availableWidth - 8.dp, maxCoverHeight, 360.dp).coerceAtLeast(140.dp)
        val largeCoverX = ((availableWidth - largeCoverSide) / 2).coerceAtLeast(0.dp)
        val largeCoverY = (((maxCoverHeight - largeCoverSide) / 2)).coerceAtLeast(4.dp)
        val largeCorner = 24.dp

        val minScale = (smallCoverSide / largeCoverSide).coerceIn(0.01f, 1f)

        val largeDetailsX = 4.dp
        val largeDetailsY = largeCoverY + largeCoverSide + 14.dp
        val largeDetailsWidth = (availableWidth - 8.dp).coerceAtLeast(0.dp)

        val smallDetailsX = smallCoverX + smallCoverSide + 12.dp
        val smallDetailsY = smallCoverY
        val smallDetailsWidth = (availableWidth - smallDetailsX - 4.dp).coerceAtLeast(0.dp)

        // 预计算像素值以供 graphicsLayer 零重组插值使用
        val smallCoverXPx = with(density) { smallCoverX.toPx() }
        val smallCoverYPx = with(density) { smallCoverY.toPx() }
        val largeCoverXPx = with(density) { largeCoverX.toPx() }
        val largeCoverYPx = with(density) { largeCoverY.toPx() }
        val largeCornerPx = with(density) { largeCorner.toPx() }
        val smallCornerCompensatedPx = with(density) { (smallCorner / minScale).toPx() }
        val largeElevationPx = with(density) { 16.dp.toPx() }
        val smallElevationPx = with(density) { 4.dp.toPx() }
        val largeDetailsTransYPx = with(density) { 24.dp.toPx() }
        val miniDetailsTransYPx = with(density) { 16.dp.toPx() }
        val lyricsTransYPx = with(density) { 28.dp.toPx() }

        // 1. 歌词层（占据除 Mini Header 以外的中间区域）
        val lyricsTopPadding = miniHeaderHeight + 4.dp
        if (showLyrics || progressAnimatable.value > 0.001f) {
            Column(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(top = lyricsTopPadding)
                    .graphicsLayer {
                        val p = progressAnimatable.value
                        alpha = ((p - 0.15f) / 0.85f).coerceIn(0f, 1f)
                        translationY = (1f - p) * lyricsTransYPx
                    },
                horizontalAlignment = Alignment.CenterHorizontally,
            ) {
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .weight(1f),
                ) {
                    AnimatedContent(
                        targetState = song,
                        contentKey = { it.id },
                        transitionSpec = {
                            (
                                fadeIn(tween(260, delayMillis = 60)) +
                                    slideInVertically(spatialOffset) { it / 8 } +
                                    scaleIn(spatial, initialScale = 0.94f)
                            ) togetherWith (
                                fadeOut(tween(160)) +
                                    slideOutVertically(spatialOffset) { -it / 10 } +
                                    scaleOut(spatial, targetScale = 1.02f)
                            )
                        },
                        contentAlignment = Alignment.Center,
                        label = "smooth-lyrics-crossfade",
                    ) { currentSong ->
                        LyricsView(
                            lyrics = lyrics,
                            positionMs = positionMs,
                            loading = lyricsLoading,
                            durationMs = durationMs,
                            playing = playing && !buffering && !seekPreviewActive,
                            positionSampledAtNanos = positionSampledAtNanos,
                            positionDiscontinuity = positionDiscontinuity,
                            playbackSpeed = playbackSpeed,
                            positionOverride = seekPreviewActive,
                            showTranslation = showTranslation,
                            showRomanization = showRomanization,
                            modifier = Modifier.fillMaxSize(),
                            onLineClick = onLineClick,
                            songId = currentSong.id,
                        )
                    }
                }

                if (showLyrics && (hasTranslation || hasRomanization)) {
                    PlayerLyricsOptions(
                        hasTranslation = hasTranslation,
                        hasRomanization = hasRomanization,
                        showTranslation = showTranslation,
                        showRomanization = showRomanization,
                        onShowTranslationChange = onShowTranslationChange,
                        onShowRomanizationChange = onShowRomanizationChange,
                    )
                }
            }
        }

        // 2. 封面模式下的大歌曲详情（固定布局尺寸，零重组 GPU 平移与渐隐）
        if (!showLyrics || progressAnimatable.value < 0.99f) {
            Row(
                modifier = Modifier
                    .offset(x = largeDetailsX, y = largeDetailsY)
                    .width(largeDetailsWidth)
                    .graphicsLayer {
                        val p = progressAnimatable.value
                        alpha = (1f - p * 2.2f).coerceIn(0f, 1f)
                        translationY = -p * largeDetailsTransYPx
                    },
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Column(
                    modifier = Modifier.weight(1f),
                    verticalArrangement = Arrangement.spacedBy(4.dp),
                ) {
                    Text(
                        text = song.name,
                        style = MaterialTheme.typography.headlineSmall,
                        color = MaterialTheme.colorScheme.onSurface,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                    )
                    Text(
                        text = song.artistText,
                        style = MaterialTheme.typography.titleMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                    )
                }
                if (!showLyrics) {
                    LikeButton(
                        liked = liked,
                        onToggle = onToggleLike,
                        modifier = Modifier.testTag("player-like"),
                    )
                }
            }
        }

        // 3. 歌词模式下的顶部 Mini 详情（固定布局尺寸，零重组 GPU 平移与淡入）
        if (showLyrics || progressAnimatable.value > 0.01f) {
            Row(
                modifier = Modifier
                    .offset(x = smallDetailsX, y = smallDetailsY)
                    .width(smallDetailsWidth)
                    .height(smallCoverSide)
                    .graphicsLayer {
                        val p = progressAnimatable.value
                        alpha = ((p - 0.35f) / 0.65f).coerceIn(0f, 1f)
                        translationY = (1f - p) * miniDetailsTransYPx
                    }
                    .then(if (showLyrics) dismissDragModifier else Modifier),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Column(
                    modifier = Modifier.weight(1f),
                    verticalArrangement = Arrangement.Center,
                ) {
                    Text(
                        text = song.name,
                        style = MaterialTheme.typography.titleMedium,
                        color = MaterialTheme.colorScheme.onSurface,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                    )
                    Text(
                        text = song.artistText,
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                    )
                }
                if (showLyrics) {
                    LikeButton(
                        liked = liked,
                        onToggle = onToggleLike,
                        modifier = Modifier.testTag("player-like"),
                    )
                }
            }
        }

        // 4. 连续变形运动的封面 (The Morphing Cover)
        // 布局尺寸固定为 largeCoverSide，完全由 graphicsLayer 在 GPU 层执行无级插值缩放与位移
        val seed = song.id.hashCode()

        Box(
            modifier = Modifier
                .size(largeCoverSide)
                .graphicsLayer {
                    val p = progressAnimatable.value
                    val currentPlayScale = androidx.compose.ui.util.lerp(coverPlayScale, 1f, p)
                    val s = androidx.compose.ui.util.lerp(1f, minScale, p) * currentPlayScale

                    scaleX = s
                    scaleY = s
                    transformOrigin = TransformOrigin(0f, 0f)

                    translationX = androidx.compose.ui.util.lerp(largeCoverXPx, smallCoverXPx, p)
                    translationY = androidx.compose.ui.util.lerp(largeCoverYPx, smallCoverYPx, p)

                    val corner = androidx.compose.ui.util.lerp(largeCornerPx, smallCornerCompensatedPx, p)
                    shape = RoundedCornerShape(corner)
                    clip = true

                    shadowElevation = androidx.compose.ui.util.lerp(largeElevationPx, smallElevationPx, p)
                    spotShadowColor = shadowColor
                }
                .clickable {
                    haptics.tap()
                    onToggleLyrics(!showLyrics)
                },
        ) {
            CoverArt(
                imageUrl = song.cover,
                modifier = Modifier.fillMaxSize(),
                shape = RoundedCornerShape(largeCorner),
                seed = seed,
                contentDescription = if (showLyrics) "${song.name} 封面，点击查看封面" else "${song.name} 封面，点击查看歌词",
            )
            if (buffering) {
                Box(
                    modifier = Modifier
                        .fillMaxSize()
                        .background(Color.Black.copy(alpha = 0.32f)),
                    contentAlignment = Alignment.Center,
                ) {
                    LoadingIndicator(
                        modifier = Modifier.size(56.dp),
                        color = Color.White,
                    )
                }
            }
        }
    }
}

@Composable
private fun PlayerCoverStage(
    song: Song,
    playing: Boolean,
    buffering: Boolean,
    liked: Boolean,
    onToggleLike: () -> Unit,
    onToggleStage: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val shape = RoundedCornerShape(24.dp)
    val coverScale by animateFloatAsState(
        targetValue = if (playing) 1f else 0.94f,
        animationSpec = MaterialTheme.motionScheme.slowSpatialSpec(),
        label = "cover-scale",
    )
    Column(
        modifier = modifier.fillMaxWidth(),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.SpaceBetween,
    ) {
        Box(
            modifier = Modifier
                .weight(1f)
                .fillMaxWidth(),
            contentAlignment = Alignment.Center,
        ) {
            BoxWithConstraints(
                modifier = Modifier.fillMaxSize(),
                contentAlignment = Alignment.Center,
            ) {
                val side = minOf(maxWidth, maxHeight, 360.dp).coerceAtLeast(120.dp)
                PlayerCover(
                    song = song,
                    side = side,
                    scale = coverScale,
                    buffering = buffering,
                    shape = shape,
                    onClick = onToggleStage,
                )
            }
        }
        Spacer(Modifier.height(18.dp))
        PlayerSongDetails(
            song = song,
            liked = liked,
            onToggleLike = onToggleLike,
            modifier = Modifier.padding(bottom = 6.dp),
        )
    }
}

@Composable
private fun PlayerSongDetails(
    song: Song,
    liked: Boolean,
    onToggleLike: () -> Unit,
    modifier: Modifier = Modifier,
) {
    Row(
        modifier = modifier
            .fillMaxWidth()
            .padding(horizontal = 4.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Column(
            modifier = Modifier.weight(1f),
            verticalArrangement = Arrangement.spacedBy(4.dp),
        ) {
            Text(
                text = song.name,
                style = MaterialTheme.typography.headlineSmall,
                color = MaterialTheme.colorScheme.onSurface,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
            Text(
                text = song.artistText,
                style = MaterialTheme.typography.titleMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
        }
        LikeButton(
            liked = liked,
            onToggle = onToggleLike,
            modifier = Modifier.testTag("player-like"),
        )
    }
}

@Composable
private fun PlayerCover(
    song: Song,
    side: Dp,
    scale: Float,
    buffering: Boolean,
    shape: Shape,
    onClick: () -> Unit,
) {
    val seed = song.id.hashCode()
    val haptics = rememberHaptics()
    Box(
        modifier = Modifier
            .size(side)
            .graphicsLayer {
                scaleX = scale
                scaleY = scale
            }
            .shadow(
                elevation = 16.dp,
                shape = shape,
                spotColor = MaterialTheme.colorScheme.primary.copy(alpha = 0.25f),
            )
            .clip(shape)
            .clickable {
                haptics.tap()
                onClick()
            },
    ) {
        CoverArt(
            imageUrl = song.cover,
            modifier = Modifier.fillMaxSize(),
            shape = shape,
            seed = seed,
            contentDescription = "${song.name} 封面，点击查看歌词",
        )
        if (buffering) {
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .background(Color.Black.copy(alpha = 0.32f)),
                contentAlignment = Alignment.Center,
            ) {
                LoadingIndicator(
                    modifier = Modifier.size(56.dp),
                    color = Color.White,
                )
            }
        }
    }
}

@Composable
private fun PlayerLyricsOptions(
    hasTranslation: Boolean,
    hasRomanization: Boolean,
    showTranslation: Boolean,
    showRomanization: Boolean,
    onShowTranslationChange: (Boolean) -> Unit,
    onShowRomanizationChange: (Boolean) -> Unit,
    modifier: Modifier = Modifier,
) {
    val haptics = rememberHaptics()
    Row(
        modifier = modifier
            .fillMaxWidth()
            .height(40.dp),
        horizontalArrangement = Arrangement.End,
        verticalAlignment = Alignment.CenterVertically,
    ) {
        if (hasRomanization) {
            IconToggleButton(
                checked = showRomanization,
                onCheckedChange = { haptics.toggle(it); onShowRomanizationChange(it) },
                modifier = Modifier.testTag("lyrics-romanization").semantics {
                    contentDescription = if (showRomanization) "隐藏音译" else "显示音译"
                },
            ) { Text("音", style = MaterialTheme.typography.titleMedium) }
        }
        if (hasTranslation) {
            IconToggleButton(
                checked = showTranslation,
                onCheckedChange = { haptics.toggle(it); onShowTranslationChange(it) },
                modifier = Modifier.testTag("lyrics-translation").semantics {
                    contentDescription = if (showTranslation) "隐藏译文" else "显示译文"
                },
            ) { Text("译", style = MaterialTheme.typography.titleMedium) }
        }
    }
}

@Composable
private fun PlayerProgressSection(
    progress: Float,
    positionMs: Long,
    durationMs: Long,
    playing: Boolean,
    seeking: Boolean,
    onSeekPreview: (Float) -> Unit,
    onSeekCommit: () -> Unit,
    modifier: Modifier = Modifier,
) {
    // In Material Design 3 Expressive, the wavy indicator indicates active playback with a gentle ripple.
    // When paused or seeking, the wave flattens smoothly to a resting line with zero waveSpeed.
    val haptics = rememberHaptics()
    val isWavy = playing && !seeking
    val amplitude by animateFloatAsState(
        targetValue = if (isWavy) 0.50f else 0f,
        animationSpec = if (isWavy) {
            MaterialTheme.motionScheme.slowEffectsSpec()
        } else {
            MaterialTheme.motionScheme.fastEffectsSpec()
        },
        label = "seek-amplitude",
    )
    val positionColor by animateColorAsState(
        targetValue = if (seeking) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurfaceVariant,
        animationSpec = MaterialTheme.motionScheme.fastEffectsSpec(),
        label = "seek-position-color",
    )
    Column(modifier = modifier.fillMaxWidth()) {
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .height(44.dp),
            contentAlignment = Alignment.Center,
        ) {
            LinearWavyProgressIndicator(
                progress = { progress },
                modifier = Modifier.fillMaxWidth(),
                color = MaterialTheme.colorScheme.primary,
                trackColor = MaterialTheme.colorScheme.primary.copy(alpha = 0.20f),
                amplitude = { amplitude },
                wavelength = 36.dp,
                waveSpeed = if (isWavy) 10.dp else 0.dp,
            )
            Slider(
                value = progress,
                onValueChange = onSeekPreview,
                onValueChangeFinished = {
                    haptics.confirm()
                    onSeekCommit()
                },
                modifier = Modifier
                    .fillMaxWidth()
                    .testTag("player-seek"),
                colors = SliderDefaults.colors(
                    thumbColor = MaterialTheme.colorScheme.primary,
                    activeTrackColor = Color.Transparent,
                    inactiveTrackColor = Color.Transparent,
                    activeTickColor = Color.Transparent,
                    inactiveTickColor = Color.Transparent,
                ),
            )
        }
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 2.dp),
            horizontalArrangement = Arrangement.SpaceBetween,
        ) {
            Text(
                text = formatDuration(positionMs),
                style = NocturnText.numeric,
                color = positionColor,
            )
            Text(
                text = formatDuration(durationMs),
                style = NocturnText.numeric,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
    }
}

@Composable
private fun PlayerTransport(
    playing: Boolean,
    onPrevious: () -> Unit,
    onTogglePlay: () -> Unit,
    onNext: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val haptics = rememberHaptics()
    Row(
        modifier = modifier
            .fillMaxWidth()
            .padding(top = 12.dp, bottom = 16.dp),
        horizontalArrangement = Arrangement.SpaceEvenly,
        verticalAlignment = Alignment.CenterVertically,
    ) {
        IconButton(
            onClick = {
                haptics.step()
                onPrevious()
            },
            modifier = Modifier
                .size(64.dp)
                .pressScale(0.88f)
                .testTag("player-previous"),
        ) {
            Icon(
                imageVector = Icons.Rounded.SkipPrevious,
                contentDescription = "上一首",
                modifier = Modifier.size(34.dp),
            )
        }
        PlayPauseButton(
            playing = playing,
            onClick = onTogglePlay,
            size = 64.dp,
            modifier = Modifier.testTag("player-toggle-play"),
        )
        IconButton(
            onClick = {
                haptics.step()
                onNext()
            },
            modifier = Modifier
                .size(64.dp)
                .pressScale(0.88f)
                .testTag("player-next"),
        ) {
            Icon(
                imageVector = Icons.Rounded.SkipNext,
                contentDescription = "下一首",
                modifier = Modifier.size(34.dp),
            )
        }
    }
}

@Composable
private fun PlayerActionsToolbar(
    showLyrics: Boolean,
    onShowLyricsChange: (Boolean) -> Unit,
    liked: Boolean,
    shuffle: Boolean,
    repeatMode: RepeatMode,
    playbackMode: PlaybackMode = PlaybackMode.Sequence,
    onToggleLike: () -> Unit,
    onToggleShuffle: () -> Unit,
    onCycleRepeat: () -> Unit,
    onCyclePlaybackMode: () -> Unit = onCycleRepeat,
    onOpenQueue: () -> Unit,
    modifier: Modifier = Modifier,
    showLikeInToolbar: Boolean = false,
) {
    val colors = MaterialTheme.colorScheme
    val haptics = rememberHaptics()
    Box(
        modifier = modifier
            .fillMaxWidth()
            .padding(top = 8.dp, bottom = 12.dp)
            .testTag("player-dock"),
        contentAlignment = Alignment.Center,
    ) {
        HorizontalFloatingToolbar(expanded = true) {
            IconToggleButton(
                checked = showLyrics,
                onCheckedChange = { haptics.toggle(it); onShowLyricsChange(it) },
                modifier = Modifier.testTag("player-toggle-lyrics"),
            ) {
                Icon(
                    imageVector = Icons.Rounded.Lyrics,
                    contentDescription = if (showLyrics) "收起歌词" else "查看歌词",
                )
            }
            if (showLikeInToolbar) {
                LikeButton(
                    liked = liked,
                    onToggle = onToggleLike,
                    modifier = Modifier.testTag("player-like"),
                )
            }
            IconButton(
                onClick = {
                    haptics.step()
                    onCyclePlaybackMode()
                },
                modifier = Modifier.testTag("player-mode"),
            ) {
                AnimatedContent(
                    targetState = playbackMode,
                    transitionSpec = {
                        (scaleIn(initialScale = 0.6f) + fadeIn()) togetherWith (scaleOut(targetScale = 0.6f) + fadeOut())
                    },
                    label = "playback-mode-icon",
                ) { mode ->
                    val (icon, desc, active) = when (mode) {
                        PlaybackMode.Sequence -> Triple(Icons.Rounded.Repeat, "循环播放：列表循环", false)
                        PlaybackMode.Heartbeat -> Triple(Icons.Rounded.Favorite, "播放模式：心动模式", true)
                        PlaybackMode.Shuffle -> Triple(Icons.Rounded.Shuffle, "播放模式：随机播放", true)
                        PlaybackMode.SingleCycle -> Triple(Icons.Rounded.RepeatOne, "循环播放：单曲循环", true)
                    }
                    Icon(
                        imageVector = icon,
                        contentDescription = desc,
                        tint = if (active) colors.primary else colors.onSurfaceVariant,
                    )
                }
            }
            IconButton(
                onClick = {
                    haptics.tap()
                    onOpenQueue()
                },
                modifier = Modifier.testTag("player-queue"),
            ) {
                Icon(
                    imageVector = Icons.AutoMirrored.Rounded.QueueMusic,
                    contentDescription = "播放队列",
                    tint = colors.onSurfaceVariant,
                )
            }
        }
    }
}

@Composable
private fun PlayerEmptyState(modifier: Modifier = Modifier) {
    Column(
        modifier = modifier.fillMaxWidth(),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center,
    ) {
        Surface(
            shape = CircleShape,
            color = MaterialTheme.colorScheme.surfaceContainerHigh,
            modifier = Modifier.size(92.dp),
        ) {
            Box(contentAlignment = Alignment.Center) {
                Icon(
                    imageVector = Icons.Rounded.MusicNote,
                    contentDescription = null,
                    modifier = Modifier.size(40.dp),
                    tint = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
        }
        Spacer(Modifier.height(18.dp))
        Text(
            text = "还没有正在播放的歌",
            style = MaterialTheme.typography.titleMedium,
            color = MaterialTheme.colorScheme.onSurface,
            textAlign = TextAlign.Center,
        )
        Spacer(Modifier.height(6.dp))
        Text(
            text = "去首页挑一首，今晚从这里开始",
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            textAlign = TextAlign.Center,
        )
    }
}

/**
 * Compact player docked above the navigation bar. The shell supplies the [modifier] (width,
 * horizontal padding and the backing scrim) so the capsule lines up exactly with the app chrome;
 * the component itself never consumes navigation-bar insets.
 *
 * Gestures: tap or swipe up opens the full player; swipe towards the start edge skips to the next
 * song and towards the end edge goes back one. The song block follows the finger and the next track
 * slides in from the side it was flung to.
 */
@Composable
fun MiniPlayer(
    state: MiniPlayerUiState,
    modifier: Modifier = Modifier,
    onExpand: () -> Unit = {},
    onTogglePlay: () -> Unit = {},
    onNext: () -> Unit = {},
    onPrevious: () -> Unit = {},
    sheetDrag: PlayerSheetDrag? = null,
) {
    val song = state.song ?: return
    val haptics = rememberHaptics()
    val density = LocalDensity.current
    val rtl = LocalLayoutDirection.current == LayoutDirection.Rtl
    val skipThreshold = with(density) { 64.dp.toPx() }
    val expandThreshold = with(density) { 36.dp.toPx() }
    val swipeX = remember { Animatable(0f) }
    val exitOffset = remember { Animatable(0f) }
    var exitSongId by remember { mutableStateOf<Long?>(null) }
    var settleJob by remember { mutableStateOf<Job?>(null) }
    // +1 after "next", -1 after "previous", 0 when the song changed from elsewhere.
    var skipDirection by remember { mutableIntStateOf(0) }
    val scope = rememberCoroutineScope()
    val currentOnExpand by rememberUpdatedState(onExpand)
    val currentOnNext by rememberUpdatedState(onNext)
    val currentOnPrevious by rememberUpdatedState(onPrevious)
    val currentSheetDrag by rememberUpdatedState(sheetDrag)

    LaunchedEffect(song.id) {
        if (exitSongId != null) {
            delay(220)
            exitSongId = null
            exitOffset.snapTo(0f)
            skipDirection = 0
        } else {
            skipDirection = 0
        }
    }

    // The axis is decided once, when the drag passes touch slop: horizontal skips tracks, vertical
    // pulls the now-playing sheet up with the finger (or, standalone, opens it past a threshold).
    val swipeModifier = Modifier.pointerInput(rtl) {
        var dragX = 0f
        var dragY = 0f
        var armed = false
        // 0 = undecided, 1 = horizontal skip, 2 = vertical sheet drag.
        var axis = 0
        val velocity = VelocityTracker()
        detectDragGestures(
            onDragStart = {
                dragX = 0f
                dragY = 0f
                armed = false
                axis = 0
                velocity.resetTracking()
                settleJob?.cancel()
                exitSongId = null
                scope.launch {
                    exitOffset.snapTo(0f)
                    swipeX.snapTo(0f)
                }
            },
            onDragCancel = {
                if (axis == 2) currentSheetDrag?.onDragEnd(0f)
                exitSongId = null
                settleJob?.cancel()
                settleJob = scope.launch {
                    swipeX.animateTo(
                        0f,
                        spring(dampingRatio = Spring.DampingRatioNoBouncy, stiffness = Spring.StiffnessMediumLow),
                    )
                }
            },
            onDragEnd = {
                when {
                    axis == 1 && abs(dragX) > skipThreshold -> {
                        // Swiping towards the start edge advances, mirrored in RTL.
                        val forward = if (rtl) dragX > 0 else dragX < 0
                        skipDirection = if (forward) 1 else -1
                        exitSongId = song.id
                        val currentSwipe = swipeX.value
                        haptics.release()
                        if (forward) currentOnNext() else currentOnPrevious()
                        settleJob?.cancel()
                        settleJob = scope.launch {
                            exitOffset.snapTo(currentSwipe)
                            swipeX.snapTo(0f)
                            delay(350)
                            if (exitSongId == song.id) {
                                exitOffset.animateTo(
                                    0f,
                                    spring(dampingRatio = Spring.DampingRatioNoBouncy, stiffness = Spring.StiffnessMediumLow),
                                )
                                exitSongId = null
                            }
                        }
                    }
                    axis == 2 -> {
                        val sheet = currentSheetDrag
                        if (sheet != null) {
                            sheet.onDragEnd(velocity.calculateVelocity().y)
                        } else if (-dragY > expandThreshold) {
                            currentOnExpand()
                        }
                    }
                    else -> {
                        exitSongId = null
                        settleJob?.cancel()
                        settleJob = scope.launch {
                            swipeX.animateTo(
                                0f,
                                spring(dampingRatio = Spring.DampingRatioNoBouncy, stiffness = Spring.StiffnessMediumLow),
                            )
                        }
                    }
                }
            },
        ) { change, amount ->
            change.consume()
            velocity.addPosition(change.uptimeMillis, change.position)
            dragX += amount.x
            dragY += amount.y
            if (axis == 0) {
                axis = if (abs(dragX) > abs(dragY)) 1 else 2
                if (axis == 2) currentSheetDrag?.onDragStart()
            }
            if (axis == 1) {
                scope.launch { swipeX.snapTo(dragX * 0.6f) }
                val past = abs(dragX) > skipThreshold
                if (past && !armed) haptics.threshold()
                armed = past
            } else {
                currentSheetDrag?.onDrag(amount.y)
            }
        }
    }

    // Over the shell the capsule is frosted glass sampling the page behind it; a shadow under a
    // translucent surface would read as a dark halo, so only the opaque fallback casts one.
    val backdrop = LocalShellBackdrop.current
    val capsule = GlassMaterials.capsule()
    val capsuleShape = RoundedCornerShape(28.dp)
    Box(modifier = modifier) {
        Surface(
            onClick = {
                haptics.tap()
                onExpand()
            },
            modifier = Modifier
                .fillMaxWidth()
                .pressScale(0.98f)
                .then(swipeModifier)
                .then(if (backdrop != null) Modifier.glass(backdrop, capsuleShape, capsule) else Modifier)
                .testTag("mini-player"),
            shape = capsuleShape,
            color = if (backdrop != null) Color.Transparent else MaterialTheme.colorScheme.surfaceContainerHigh,
            contentColor = MaterialTheme.colorScheme.onSurface,
            shadowElevation = if (backdrop != null) 0.dp else 4.dp,
        ) {
            Column(modifier = Modifier.fillMaxWidth()) {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(start = 10.dp, top = 8.dp, end = 4.dp, bottom = 6.dp),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    AnimatedContent(
                        targetState = song,
                        contentKey = { it.id },
                        modifier = Modifier.weight(1f),
                        transitionSpec = {
                            val sign = (if (rtl) -1 else 1) * skipDirection
                            if (sign == 0) {
                                (fadeIn(tween(220)) + slideInVertically(tween(260)) { it / 3 }) togetherWith
                                    fadeOut(tween(120))
                            } else {
                                (slideInHorizontally(spring(dampingRatio = Spring.DampingRatioNoBouncy, stiffness = Spring.StiffnessMediumLow)) { sign * it / 2 } +
                                    fadeIn(tween(200))) togetherWith
                                    (slideOutHorizontally(tween(180)) { -sign * it / 3 } + fadeOut(tween(140)))
                            }.using(SizeTransform(clip = false))
                        },
                        label = "mini-player-song",
                    ) { shown ->
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            modifier = Modifier.graphicsLayer {
                                val offset = if (shown.id == exitSongId) exitOffset.value else swipeX.value
                                translationX = offset
                                alpha = 1f - (abs(offset) / (size.width * 0.6f)).coerceIn(0f, 0.6f)
                            },
                        ) {
                            CoverArt(
                                imageUrl = shown.cover,
                                modifier = Modifier.size(44.dp),
                                shape = MaterialTheme.shapes.small,
                                seed = shown.id.hashCode(),
                                contentDescription = null,
                            )
                            Spacer(Modifier.width(12.dp))
                            Column(modifier = Modifier.weight(1f)) {
                                Text(
                                    text = shown.name,
                                    style = MaterialTheme.typography.titleSmall,
                                    color = MaterialTheme.colorScheme.onSurface,
                                    maxLines = 1,
                                    overflow = TextOverflow.Ellipsis,
                                )
                                Text(
                                    text = shown.artistText,
                                    style = MaterialTheme.typography.bodySmall,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                                    maxLines = 1,
                                    overflow = TextOverflow.Ellipsis,
                                )
                            }
                        }
                    }
                    IconButton(
                        onClick = {
                            haptics.confirm()
                            onTogglePlay()
                        },
                        modifier = Modifier.testTag("mini-player-toggle-play"),
                    ) {
                        AnimatedContent(
                            targetState = state.playing,
                            transitionSpec = {
                                (scaleIn(initialScale = 0.6f) + fadeIn()) togetherWith
                                    (scaleOut(targetScale = 0.6f) + fadeOut())
                            },
                            label = "mini-player-play",
                        ) { playing ->
                            Icon(
                                imageVector = if (playing) Icons.Rounded.Pause else Icons.Rounded.PlayArrow,
                                contentDescription = if (playing) "暂停" else "播放",
                                tint = MaterialTheme.colorScheme.primary,
                            )
                        }
                    }
                }
                LinearWavyProgressIndicator(
                    progress = { state.progress.coerceIn(0f, 1f) },
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 14.dp)
                        .padding(bottom = 8.dp),
                    color = MaterialTheme.colorScheme.primary,
                    trackColor = MaterialTheme.colorScheme.primary.copy(alpha = 0.22f),
                    amplitude = { 0f },
                )
            }
        }
    }
}

private fun RepeatMode.description(): String = when (this) {
    RepeatMode.Off -> "循环播放：关闭"
    RepeatMode.All -> "循环播放：列表循环"
    RepeatMode.One -> "循环播放：单曲循环"
}

// ---------------------------------------------------------------------------------------------
// Previews
// ---------------------------------------------------------------------------------------------

@Preview(name = "播放器 · 浅色", widthDp = 412, heightDp = 915, showBackground = true)
@Preview(
    name = "播放器 · 深色",
    widthDp = 412,
    heightDp = 915,
    showBackground = true,
    uiMode = Configuration.UI_MODE_NIGHT_YES,
)
@Composable
private fun PlayerScreenPreview() {
    NocturnTheme(themeMode = ThemeMode.System) {
        PlayerScreen(state = PreviewData.playerState)
    }
}

@Preview(name = "迷你播放器 · 浅色", widthDp = 412, heightDp = 120, showBackground = true)
@Preview(
    name = "迷你播放器 · 深色",
    widthDp = 412,
    heightDp = 120,
    showBackground = true,
    uiMode = Configuration.UI_MODE_NIGHT_YES,
)
@Composable
private fun MiniPlayerPreview() {
    NocturnTheme(themeMode = ThemeMode.System) {
        NocturnBackdrop {
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(horizontal = 16.dp, vertical = 24.dp),
                contentAlignment = Alignment.Center,
            ) {
                MiniPlayer(state = PreviewData.miniPlayerState)
            }
        }
    }
}

@Preview(
    name = "播放器 · 歌词模式 · 深色",
    widthDp = 412,
    heightDp = 915,
    showBackground = true,
    uiMode = Configuration.UI_MODE_NIGHT_YES,
)
@Composable
private fun PlayerScreenLyricsPreview() {
    NocturnTheme(themeMode = ThemeMode.System) {
        PlayerScreen(
            state = PreviewData.bilingualPlayerState.copy(positionMs = 115_000),
            initialLyricsVisible = true,
        )
    }
}

@Preview(
    name = "播放器 · 大屏双栏 · 深色",
    widthDp = 1024,
    heightDp = 768,
    showBackground = true,
    uiMode = Configuration.UI_MODE_NIGHT_YES,
)
@Preview(
    name = "播放器 · 大屏双栏 · 浅色",
    widthDp = 1024,
    heightDp = 768,
    showBackground = true,
)
@Composable
private fun PlayerScreenTabletPreview() {
    NocturnTheme(themeMode = ThemeMode.System) {
        CompositionLocalProvider(LocalWindowWidthClass provides WindowWidthClass.Expanded) {
            PlayerScreen(
                state = PreviewData.bilingualPlayerState.copy(positionMs = 115_000),
            )
        }
    }
}
