@file:OptIn(androidx.compose.material3.ExperimentalMaterial3ExpressiveApi::class)

package dev.nocturn.app.ui.screens.player

import android.os.Build
import android.os.SystemClock
import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.scaleIn
import androidx.compose.animation.scaleOut
import androidx.compose.animation.togetherWith
import androidx.compose.animation.core.CubicBezierEasing
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.gestures.stopScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.GraphicEq
import androidx.compose.material3.ContainedLoadingIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.IntState
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.derivedStateOf
import androidx.compose.runtime.getValue
import androidx.compose.runtime.key
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.snapshotFlow
import androidx.compose.runtime.withFrameNanos
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.BlendMode
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.semantics.text
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.mocharealm.accompanist.lyrics.core.model.SyncedLyrics
import com.mocharealm.accompanist.lyrics.core.model.ISyncedLine
import com.mocharealm.accompanist.lyrics.ui.composable.list.LyricsLazyListState
import com.mocharealm.accompanist.lyrics.ui.composable.list.LyricsScrollChain
import com.mocharealm.accompanist.lyrics.ui.composable.list.rememberLyricsLazyListState
import com.mocharealm.accompanist.lyrics.ui.composable.lyrics.KaraokeLyricsView
import com.mocharealm.accompanist.lyrics.ui.composable.lyrics.LyricsAnchor
import com.mocharealm.accompanist.lyrics.ui.composable.lyrics.LyricsFade
import dev.nocturn.app.data.model.LyricLine
import dev.nocturn.app.data.model.Lyrics
import dev.nocturn.app.ui.components.rememberHaptics
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.launch

// A short, damped carry between rows, rather than moving the entire sheet as one rigid block.
private val LineScrollChain = LyricsScrollChain(
    stiffness = 180f,
    damping = 26f,
    coupling = 0.25f,
    distanceFalloff = 0.2f,
    minResponse = 0.6f,
)
private val LineScrollAnimation = tween<Float>(540, easing = CubicBezierEasing(0.2f, 0f, 0.2f, 1f))

internal enum class LyricsStage {
    Loading,
    Empty,
    Content,
}

/** YRC syllables and LRC lines share Accompanist's prepared renderer and gesture-aware follow. */
@Composable
internal fun LyricsView(
    lyrics: Lyrics,
    positionMs: Long,
    loading: Boolean,
    modifier: Modifier = Modifier,
    onLineClick: ((LyricLine) -> Unit)? = null,
    durationMs: Long = 0L,
    playing: Boolean = false,
    showTranslation: Boolean = true,
    showRomanization: Boolean = false,
    positionSampledAtNanos: Long = 0L,
    positionDiscontinuity: Long = 0L,
    playbackSpeed: Float = 1f,
    positionOverride: Boolean = false,
    songId: Long? = null,
) {
    val currentStage = when {
        lyrics.isEmpty && loading -> LyricsStage.Loading
        lyrics.isEmpty -> LyricsStage.Empty
        else -> LyricsStage.Content
    }
    val spatial = MaterialTheme.motionScheme.defaultSpatialSpec<Float>()
    val effects = MaterialTheme.motionScheme.defaultEffectsSpec<Float>()

    AnimatedContent(
        targetState = currentStage,
        transitionSpec = {
            (
                fadeIn(animationSpec = effects) +
                    scaleIn(initialScale = 0.96f, animationSpec = spatial)
            ) togetherWith (
                fadeOut(animationSpec = effects) +
                    scaleOut(targetScale = 1.02f, animationSpec = spatial)
            )
        },
        contentAlignment = Alignment.Center,
        label = "lyrics-view-stage",
        modifier = modifier.fillMaxSize(),
    ) { stage ->
        when (stage) {
            LyricsStage.Loading -> LyricsLoading(modifier = Modifier.fillMaxSize())
            LyricsStage.Empty -> LyricsPlaceholder(modifier = Modifier.fillMaxSize())
            LyricsStage.Content -> {
                // Hashing Lyrics directly as a composition key walks every word at each player sample.
                val contentKey = remember(lyrics, songId) { Any() }
                key(contentKey) {
                    val synced = remember(lyrics, durationMs) { lyrics.toAccompanistLyrics(durationMs) }
                    val listState = rememberLyricsLazyListState(
                        initialFirstVisibleItemIndex = lyrics.indexAt(positionMs).coerceAtLeast(0),
                    )
                    val position = rememberLyricsPosition(
                        positionMs, durationMs, playing, positionSampledAtNanos,
                        positionDiscontinuity, playbackSpeed, positionOverride,
                    )
                    LyricsRenderer(
                        lyrics, synced, listState, position, onLineClick,
                        showTranslation, showRomanization, Modifier.fillMaxSize(),
                    )
                }
            }
        }
    }
}

/** Stable inputs let controller samples skip the prepared renderer between actual line changes. */
@Composable
private fun LyricsRenderer(
    lyrics: Lyrics,
    synced: SyncedLyrics,
    listState: LyricsLazyListState,
    position: IntState,
    onLineClick: ((LyricLine) -> Unit)?,
    showTranslation: Boolean,
    showRomanization: Boolean,
    modifier: Modifier,
) {
    val haptics = rememberHaptics()
    val scope = rememberCoroutineScope()
    val currentLine by remember(lyrics, position) {
        derivedStateOf { lyrics.lines.getOrNull(lyrics.indexAt(position.intValue.toLong())) }
    }
    val timeProvider = remember(position) { { position.intValue } }
    val typography = MaterialTheme.typography
    val mainStyle = remember(typography) {
        typography.headlineSmall.copy(fontSize = 28.sp, lineHeight = 36.sp, fontWeight = FontWeight.Bold)
    }
    val onClick = remember(lyrics, synced, onLineClick, haptics) {
        { clicked: ISyncedLine ->
            if (onLineClick != null) {
                val index = synced.lines.indexOf(clicked)
                lyrics.lines.getOrNull(index)?.let { haptics.confirm(); onLineClick(it) }
            }
        }
    }
    Box(modifier.fillMaxSize()) {
        KaraokeLyricsView(
            listState = listState,
            lyrics = synced,
            currentPosition = timeProvider,
            onLineClicked = onClick,
            onLinePressed = {},
            modifier = Modifier.fillMaxSize().testTag("player-lyrics-list").semantics {
                currentLine?.let { line ->
                    text = AnnotatedString(listOfNotNull(
                        line.text,
                        line.translation?.takeIf { showTranslation },
                        line.romanization?.takeIf { showRomanization },
                    ).joinToString("\n"))
                }
            },
            normalLineTextStyle = mainStyle,
            translationTextStyle = typography.bodyLarge,
            phoneticTextStyle = typography.bodyMedium,
            textColor = MaterialTheme.colorScheme.onSurface,
            blendMode = BlendMode.SrcOver,
            // Blur cached row layers, never the whole scrolling viewport. Browsing removes the blur.
            useBlurEffect = Build.VERSION.SDK_INT >= Build.VERSION_CODES.S,
            blurDelta = 2f,
            showTranslation = showTranslation,
            showPhonetic = showRomanization,
            anchor = LyricsAnchor.Fraction(0.38f),
            topFade = LyricsFade.Fraction(0.12f),
            bottomFade = LyricsFade.Fraction(0.16f),
            itemSpacing = 20.dp,
            keepAliveZone = 24.dp,
            scrollAnimationSpec = LineScrollAnimation,
            scrollChain = LineScrollChain,
            autoScrollResumeDelayMillis = 3_500,
        )
        androidx.compose.animation.AnimatedVisibility(
            visible = listState.isManualScrolling,
            modifier = Modifier.align(Alignment.BottomCenter).padding(bottom = 8.dp),
        ) {
            Surface(shape = CircleShape, color = MaterialTheme.colorScheme.surfaceContainerHigh) {
                TextButton(
                    onClick = {
                        haptics.tap()
                        scope.launch {
                            listState.stopScroll()
                            listState.resumeAutoScroll()
                        }
                    },
                    modifier = Modifier.testTag("lyrics-resume-follow"),
                ) { Text("回到当前歌词") }
            }
        }
    }
}

/** Keep one observable clock for the scene's lifetime. Frame ticks never invalidate the screen. */
@Composable
internal fun rememberLyricsPosition(
    positionMs: Long,
    durationMs: Long,
    playing: Boolean,
    positionSampledAtNanos: Long = 0L,
    positionDiscontinuity: Long = 0L,
    playbackSpeed: Float = 1f,
    positionOverride: Boolean = false,
): IntState {
    val sample = rememberUpdatedState(LyricsPositionSample(
        positionMs, durationMs, playing, positionSampledAtNanos,
        positionDiscontinuity, playbackSpeed, positionOverride,
    ))
    val position = remember { mutableIntStateOf(interpolatedLyricsPosition(positionMs, 0, playing, durationMs)) }
    val clock = remember { LyricsPlaybackClock(position.intValue) }
    LaunchedEffect(position) {
        snapshotFlow { sample.value }.collectLatest { anchor ->
            val receivedAt = SystemClock.elapsedRealtimeNanos()
            val sampledAt = anchor.sampledAtNanos.takeIf { it > 0L } ?: receivedAt
            var now = receivedAt
            position.intValue = clock.positionAt(anchor, receivedAt, now)
            if (!anchor.playing) return@collectLatest
            val limit = anchor.durationMs.takeIf { it > 0L } ?: Int.MAX_VALUE.toLong()
            var initialFrameTimeNanos: Long? = null
            while ((now - sampledAt).coerceAtLeast(0L) < 750_000_000L && position.intValue < limit) {
                withFrameNanos { frameTimeNanos ->
                    // Choreographer frame timestamps use a different sleep basis. Use the exact
                    // same elapsed-realtime clock as the source, but allow frame-clock delta to
                    // advance simulated test environments deterministically.
                    val frameOrigin = initialFrameTimeNanos ?: frameTimeNanos.also { initialFrameTimeNanos = it }
                    val frameElapsedNanos = (frameTimeNanos - frameOrigin).coerceAtLeast(0L)
                    val realNow = SystemClock.elapsedRealtimeNanos()
                    now = maxOf(realNow, receivedAt + frameElapsedNanos)
                    position.intValue = clock.positionAt(anchor, receivedAt, now)
                }
            }
        }
    }
    return position
}

@Composable
private fun LyricsLoading(modifier: Modifier = Modifier) {
    Column(
        modifier = modifier.fillMaxSize(),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center,
    ) {
        ContainedLoadingIndicator(modifier = Modifier.size(52.dp))
        Spacer(Modifier.height(16.dp))
        Text(
            text = "正在加载歌词",
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
    }
}

@Composable
private fun LyricsPlaceholder(modifier: Modifier = Modifier) {
    Column(
        modifier = modifier.fillMaxSize(),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center,
    ) {
        Surface(
            shape = CircleShape,
            color = MaterialTheme.colorScheme.surfaceContainerHigh,
            modifier = Modifier.size(84.dp),
        ) {
            Box(contentAlignment = Alignment.Center) {
                Icon(
                    imageVector = Icons.Filled.GraphicEq,
                    contentDescription = null,
                    modifier = Modifier.size(36.dp),
                    tint = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
        }
        Spacer(Modifier.height(18.dp))
        Text(
            text = "暂无歌词",
            style = MaterialTheme.typography.titleMedium,
            color = MaterialTheme.colorScheme.onSurface,
        )
        Spacer(Modifier.height(6.dp))
        Text(
            text = "纯音乐，请安静欣赏",
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            textAlign = TextAlign.Center,
        )
        Spacer(Modifier.height(12.dp))
        Box(
            modifier = Modifier
                .size(width = 54.dp, height = 4.dp)
                .background(
                    color = MaterialTheme.colorScheme.outlineVariant,
                    shape = CircleShape,
                ),
        )
    }
}
