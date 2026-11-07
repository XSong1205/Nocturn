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
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.only
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.safeDrawing
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.windowInsetsPadding
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.QueueMusic
import androidx.compose.material.icons.filled.CloudOff
import androidx.compose.material.icons.filled.ExpandMore
import androidx.compose.material.icons.filled.Favorite
import androidx.compose.material.icons.filled.FavoriteBorder
import androidx.compose.material.icons.filled.Lyrics
import androidx.compose.material.icons.filled.MusicNote
import androidx.compose.material.icons.filled.Pause
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material.icons.filled.Repeat
import androidx.compose.material.icons.filled.RepeatOne
import androidx.compose.material.icons.filled.Shuffle
import androidx.compose.material.icons.filled.SkipNext
import androidx.compose.material.icons.filled.SkipPrevious
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
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Shape
import androidx.compose.ui.graphics.TransformOrigin
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.input.pointer.util.VelocityTracker
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.LocalLayoutDirection
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.LayoutDirection
import androidx.compose.ui.unit.dp
import dev.nocturn.app.data.model.Song
import dev.nocturn.app.data.model.LyricLine
import dev.nocturn.app.data.model.ThemeMode
import dev.nocturn.app.playback.RepeatMode
import dev.nocturn.app.ui.components.CoverArt
import dev.nocturn.app.ui.components.GlassMaterials
import dev.nocturn.app.ui.components.LikeButton
import dev.nocturn.app.ui.components.LocalShellBackdrop
import dev.nocturn.app.ui.components.NocturnBackdrop
import dev.nocturn.app.ui.components.PlayPauseButton
import dev.nocturn.app.ui.components.glass
import dev.nocturn.app.ui.components.pressScale
import dev.nocturn.app.ui.components.rememberHaptics
import dev.nocturn.app.ui.preview.PreviewData
import dev.nocturn.app.ui.state.MiniPlayerUiState
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
        color = MaterialTheme.colorScheme.surface,
    ) {
        Box(modifier = Modifier.fillMaxSize()) {
            Column(
                modifier = Modifier
                    .fillMaxSize()
                    .windowInsetsPadding(WindowInsets.safeDrawing.only(WindowInsetsSides.Vertical + WindowInsetsSides.Horizontal))
                    .padding(horizontal = 20.dp),
                horizontalAlignment = Alignment.CenterHorizontally,
            ) {
                PlayerHeader(
                    song = song,
                    onCollapse = onCollapse,
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
                } else {
                    AnimatedContent(
                        targetState = showLyrics,
                        modifier = Modifier
                            .weight(1f)
                            .fillMaxWidth()
                            // The lyric list scrolls vertically itself, so only the cover drags.
                            .then(if (showLyrics) Modifier else dismissDrag),
                        transitionSpec = {
                            (
                                fadeIn(animationSpec = effects) +
                                    scaleIn(initialScale = 0.94f, animationSpec = spatial)
                                ) togetherWith (
                                fadeOut(animationSpec = effects) +
                                    scaleOut(targetScale = 1.03f, animationSpec = spatial)
                                )
                        },
                        contentAlignment = Alignment.Center,
                        label = "player-stage",
                    ) { lyricsMode ->
                        if (lyricsMode) {
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
                                label = "lyrics-crossfade",
                            ) { currentSong ->
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
                                    modifier = Modifier.fillMaxSize(),
                                    onLineClick = onLyricLineClick,
                                    songId = currentSong.id,
                                )
                            }
                        } else {
                            PlayerCoverStage(
                                song = song,
                                playing = state.playing,
                                buffering = state.buffering,
                                onToggleStage = { showLyrics = true },
                                modifier = Modifier.fillMaxSize(),
                            )
                        }
                    }
                    if (showLyrics && (hasTranslation || hasRomanization)) {
                        PlayerLyricsOptions(
                            hasTranslation = hasTranslation,
                            hasRomanization = hasRomanization,
                            showTranslation = showTranslation,
                            showRomanization = showRomanization,
                            onShowTranslationChange = { showTranslation = it },
                            onShowRomanizationChange = { showRomanization = it },
                        )
                    }
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
                        onToggleLike = onToggleLike,
                        onToggleShuffle = onToggleShuffle,
                        onCycleRepeat = onCycleRepeat,
                        onOpenQueue = { queueVisible = true },
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
    modifier: Modifier = Modifier,
) {
    val haptics = rememberHaptics()
    Column(
        modifier = modifier.fillMaxWidth(),
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        Box(
            modifier = Modifier
                .padding(top = 10.dp, bottom = 2.dp)
                .size(width = 36.dp, height = 4.dp)
                .clip(CircleShape)
                .background(MaterialTheme.colorScheme.outlineVariant),
        )
        Row(
            modifier = Modifier.fillMaxWidth(),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            IconButton(
                onClick = {
                    haptics.tap()
                    onCollapse()
                },
                modifier = Modifier.testTag("player-collapse"),
            ) {
                Icon(
                    imageVector = Icons.Filled.ExpandMore,
                    contentDescription = "收起播放器",
                    modifier = Modifier.size(28.dp),
                )
            }
            val titleEffects = MaterialTheme.motionScheme.defaultEffectsSpec<Float>()
            val titleSpatial = MaterialTheme.motionScheme.defaultSpatialSpec<IntOffset>()
            val titleExit = MaterialTheme.motionScheme.fastEffectsSpec<Float>()
            AnimatedContent(
                targetState = song,
                modifier = Modifier.weight(1f),
                contentKey = { it?.id },
                transitionSpec = {
                    (fadeIn(titleEffects) + slideInVertically(titleSpatial) { it / 2 }) togetherWith
                        fadeOut(titleExit)
                },
                contentAlignment = Alignment.Center,
                label = "player-title",
            ) { shown ->
                Column(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalAlignment = Alignment.CenterHorizontally,
                ) {
                    Text(
                        text = shown?.name ?: "未在播放",
                        style = MaterialTheme.typography.titleMediumEmphasized,
                        color = MaterialTheme.colorScheme.onSurface,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                    )
                    Spacer(Modifier.height(2.dp))
                    Text(
                        text = shown?.artistText ?: "挑一首歌开始今晚的循环",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                    )
                }
            }
            Spacer(Modifier.size(48.dp))
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
                imageVector = Icons.Filled.CloudOff,
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
private fun PlayerCoverStage(
    song: Song,
    playing: Boolean,
    buffering: Boolean,
    onToggleStage: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val shape = MaterialTheme.shapes.extraLarge
    val coverScale by animateFloatAsState(
        targetValue = if (playing) 1f else 0.92f,
        animationSpec = MaterialTheme.motionScheme.slowSpatialSpec(),
        label = "cover-scale",
    )
    BoxWithConstraints(
        modifier = modifier,
        contentAlignment = Alignment.Center,
    ) {
        val side = minOf(maxWidth, maxHeight, 360.dp).coerceAtLeast(120.dp)
        val coverSpatial = MaterialTheme.motionScheme.defaultSpatialSpec<Float>()
        AnimatedContent(
            targetState = song,
            contentKey = { it.id },
            transitionSpec = {
                (
                    fadeIn(tween(260, delayMillis = 60)) +
                        scaleIn(coverSpatial, initialScale = 0.86f)
                    ) togetherWith (
                    fadeOut(tween(160)) +
                        scaleOut(tween(220), targetScale = 1.06f)
                    )
            },
            contentAlignment = Alignment.Center,
            label = "player-cover",
        ) { shownSong ->
            PlayerCover(
                song = shownSong,
                side = side,
                scale = coverScale,
                buffering = buffering,
                shape = shape,
                onClick = onToggleStage,
            )
        }
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
    // The wave only moves while music plays; it irons flat on pause and while the thumb is held so
    // the seek target is easy to read.
    val haptics = rememberHaptics()
    val amplitude by animateFloatAsState(
        targetValue = if (playing && !seeking) 0.85f else 0f,
        animationSpec = MaterialTheme.motionScheme.slowEffectsSpec(),
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
                wavelength = 26.dp,
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
                imageVector = Icons.Filled.SkipPrevious,
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
                imageVector = Icons.Filled.SkipNext,
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
    onToggleLike: () -> Unit,
    onToggleShuffle: () -> Unit,
    onCycleRepeat: () -> Unit,
    onOpenQueue: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val colors = MaterialTheme.colorScheme
    val haptics = rememberHaptics()
    val shuffleTint by animateColorAsState(
        targetValue = if (shuffle) colors.primary else colors.onSurfaceVariant,
        animationSpec = MaterialTheme.motionScheme.fastEffectsSpec(),
        label = "shuffle-tint",
    )
    val repeatTint by animateColorAsState(
        targetValue = if (repeatMode == RepeatMode.Off) colors.onSurfaceVariant else colors.primary,
        animationSpec = MaterialTheme.motionScheme.fastEffectsSpec(),
        label = "repeat-tint",
    )
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
                    imageVector = Icons.Filled.Lyrics,
                    contentDescription = if (showLyrics) "收起歌词" else "查看歌词",
                )
            }
            LikeButton(
                liked = liked,
                onToggle = onToggleLike,
                modifier = Modifier.testTag("player-like"),
            )
            IconButton(
                onClick = {
                    haptics.toggle(on = !shuffle)
                    onToggleShuffle()
                },
                modifier = Modifier.testTag("player-shuffle"),
            ) {
                Icon(
                    imageVector = Icons.Filled.Shuffle,
                    contentDescription = if (shuffle) "关闭随机播放" else "开启随机播放",
                    tint = shuffleTint,
                )
            }
            IconButton(
                onClick = {
                    haptics.step()
                    onCycleRepeat()
                },
                modifier = Modifier.testTag("player-repeat"),
            ) {
                AnimatedContent(
                    targetState = repeatMode == RepeatMode.One,
                    transitionSpec = {
                        (scaleIn(initialScale = 0.6f) + fadeIn()) togetherWith (scaleOut(targetScale = 0.6f) + fadeOut())
                    },
                    label = "repeat-icon",
                ) { one ->
                    Icon(
                        imageVector = if (one) Icons.Filled.RepeatOne else Icons.Filled.Repeat,
                        contentDescription = repeatMode.description(),
                        tint = repeatTint,
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
                    imageVector = Icons.AutoMirrored.Filled.QueueMusic,
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
                    imageVector = Icons.Filled.MusicNote,
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
                                imageVector = if (playing) Icons.Filled.Pause else Icons.Filled.PlayArrow,
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
