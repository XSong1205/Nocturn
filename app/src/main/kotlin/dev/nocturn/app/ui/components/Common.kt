@file:OptIn(
    androidx.compose.material3.ExperimentalMaterial3Api::class,
    androidx.compose.material3.ExperimentalMaterial3ExpressiveApi::class,
)

package dev.nocturn.app.ui.components

import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.spring
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.scaleIn
import androidx.compose.animation.scaleOut
import androidx.compose.animation.togetherWith
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.CloudOff
import androidx.compose.material.icons.filled.Favorite
import androidx.compose.material.icons.filled.FavoriteBorder
import androidx.compose.material.icons.filled.MusicNote
import androidx.compose.material.icons.filled.Pause
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material3.Button
import androidx.compose.material3.ContainedLoadingIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import dev.nocturn.app.ui.theme.NocturnText
import kotlin.math.PI
import kotlin.math.sin

/** Section heading used by every scroll surface: bold title, optional subtitle and action. */
@Composable
fun SectionHeader(
    title: String,
    modifier: Modifier = Modifier,
    subtitle: String? = null,
    actionLabel: String? = null,
    onAction: (() -> Unit)? = null,
) {
    Row(
        modifier = modifier
            .fillMaxWidth()
            .padding(horizontal = 20.dp),
        verticalAlignment = Alignment.Bottom,
        horizontalArrangement = Arrangement.SpaceBetween,
    ) {
        Column(modifier = Modifier.weight(1f)) {
            Text(
                text = title,
                style = MaterialTheme.typography.titleLarge,
                color = MaterialTheme.colorScheme.onSurface,
            )
            if (subtitle != null) {
                Spacer(Modifier.height(2.dp))
                Text(
                    text = subtitle,
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
        }
        if (actionLabel != null && onAction != null) {
            val haptics = rememberHaptics()
            TextButton(onClick = {
                haptics.tap()
                onAction()
            }) {
                Text(actionLabel, style = MaterialTheme.typography.labelLarge)
            }
        }
    }
}

@Composable
fun ScreenLoading(
    modifier: Modifier = Modifier,
    label: String = "正在加载",
) {
    Column(
        modifier = modifier
            .fillMaxWidth()
            .padding(vertical = 48.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        ContainedLoadingIndicator(modifier = Modifier.size(56.dp))
        Spacer(Modifier.height(16.dp))
        Text(
            text = label,
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
    }
}

@Composable
fun ScreenError(
    message: String,
    modifier: Modifier = Modifier,
    onRetry: (() -> Unit)? = null,
) {
    Column(
        modifier = modifier
            .fillMaxWidth()
            .padding(horizontal = 32.dp, vertical = 48.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        Surface(
            shape = MaterialTheme.shapes.large,
            color = MaterialTheme.colorScheme.errorContainer,
            contentColor = MaterialTheme.colorScheme.onErrorContainer,
            modifier = Modifier.size(72.dp),
        ) {
            Box(contentAlignment = Alignment.Center) {
                Icon(Icons.Filled.CloudOff, contentDescription = null, modifier = Modifier.size(32.dp))
            }
        }
        Spacer(Modifier.height(20.dp))
        Text(
            text = "连接不上音乐服务",
            style = MaterialTheme.typography.titleMedium,
            color = MaterialTheme.colorScheme.onSurface,
        )
        Spacer(Modifier.height(6.dp))
        Text(
            text = message,
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            textAlign = TextAlign.Center,
        )
        if (onRetry != null) {
            val haptics = rememberHaptics()
            Spacer(Modifier.height(20.dp))
            Button(onClick = {
                haptics.tap()
                onRetry()
            }) {
                Icon(Icons.Filled.Refresh, contentDescription = null, modifier = Modifier.size(18.dp))
                Spacer(Modifier.width(8.dp))
                Text("重试")
            }
        }
    }
}

@Composable
fun EmptyState(
    title: String,
    message: String,
    modifier: Modifier = Modifier,
    actionLabel: String? = null,
    onAction: (() -> Unit)? = null,
) {
    Column(
        modifier = modifier
            .fillMaxWidth()
            .padding(horizontal = 32.dp, vertical = 48.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        Surface(
            shape = CircleShape,
            color = MaterialTheme.colorScheme.secondaryContainer,
            contentColor = MaterialTheme.colorScheme.onSecondaryContainer,
            modifier = Modifier.size(72.dp),
        ) {
            Box(contentAlignment = Alignment.Center) {
                Icon(Icons.Filled.MusicNote, contentDescription = null, modifier = Modifier.size(32.dp))
            }
        }
        Spacer(Modifier.height(20.dp))
        Text(
            text = title,
            style = MaterialTheme.typography.titleMedium,
            color = MaterialTheme.colorScheme.onSurface,
        )
        Spacer(Modifier.height(6.dp))
        Text(
            text = message,
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            textAlign = TextAlign.Center,
        )
        if (actionLabel != null && onAction != null) {
            val haptics = rememberHaptics()
            Spacer(Modifier.height(20.dp))
            TextButton(onClick = {
                haptics.tap()
                onAction()
            }) { Text(actionLabel) }
        }
    }
}

/**
 * Heart button. Liking pops the heart past its resting size and settles back with a spring, the
 * tint crossfades, and a toggle haptic confirms the change.
 */
@Composable
fun LikeButton(
    liked: Boolean,
    onToggle: () -> Unit,
    modifier: Modifier = Modifier,
    enabled: Boolean = true,
) {
    val haptics = rememberHaptics()
    val pop = remember { Animatable(1f) }
    var previous by remember { mutableStateOf(liked) }
    LaunchedEffect(liked) {
        if (liked != previous) {
            previous = liked
            if (liked) {
                pop.animateTo(1.32f, spring(dampingRatio = 1f, stiffness = Spring.StiffnessHigh))
                pop.animateTo(1f, spring(dampingRatio = 0.42f, stiffness = Spring.StiffnessMedium))
            } else {
                pop.animateTo(0.86f, spring(dampingRatio = 1f, stiffness = Spring.StiffnessHigh))
                pop.animateTo(1f, spring(dampingRatio = 0.6f, stiffness = Spring.StiffnessMedium))
            }
        }
    }
    val tint by animateColorAsState(
        targetValue = if (liked) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurfaceVariant,
        animationSpec = MaterialTheme.motionScheme.fastEffectsSpec(),
        label = "like-tint",
    )
    IconButton(
        onClick = {
            haptics.toggle(on = !liked)
            onToggle()
        },
        enabled = enabled,
        modifier = modifier,
    ) {
        Icon(
            imageVector = if (liked) Icons.Filled.Favorite else Icons.Filled.FavoriteBorder,
            contentDescription = if (liked) "取消喜欢" else "喜欢",
            tint = tint,
            modifier = Modifier.graphicsLayer {
                scaleX = pop.value
                scaleY = pop.value
            },
        )
    }
}

/**
 * Primary transport button. The container morphs between a circle (paused, "press to play") and a
 * softly rounded container (playing), the glyph crossfades with a spring scale, and presses sink
 * slightly. Corner radii scale with the button size on every screen density.
 */
@Composable
fun PlayPauseButton(
    playing: Boolean,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    size: Dp = 64.dp,
) {
    val spatial = MaterialTheme.motionScheme.fastSpatialSpec<Float>()
    val effects = MaterialTheme.motionScheme.fastEffectsSpec<Float>()
    val haptics = rememberHaptics()
    val cornerFraction by animateFloatAsState(
        targetValue = if (playing) 0.4f else 0.5f,
        animationSpec = MaterialTheme.motionScheme.defaultSpatialSpec(),
        label = "play-pause-corner",
    )
    Surface(
        onClick = {
            haptics.confirm()
            onClick()
        },
        modifier = modifier
            .pressScale(0.92f)
            .size(size),
        shape = RoundedCornerShape(size * cornerFraction.coerceIn(0f, 0.5f)),
        color = MaterialTheme.colorScheme.primary,
        contentColor = MaterialTheme.colorScheme.onPrimary,
    ) {
        Box(contentAlignment = Alignment.Center) {
            AnimatedContent(
                targetState = playing,
                transitionSpec = {
                    (scaleIn(initialScale = 0.6f, animationSpec = spatial) +
                        fadeIn(animationSpec = effects)) togetherWith
                        (scaleOut(targetScale = 0.6f, animationSpec = spatial) +
                            fadeOut(animationSpec = effects))
                },
                label = "play-pause",
            ) { isPlaying ->
                Icon(
                    imageVector = if (isPlaying) Icons.Filled.Pause else Icons.Filled.PlayArrow,
                    contentDescription = if (isPlaying) "暂停" else "播放",
                    modifier = Modifier.size(size * 0.46f),
                )
            }
        }
    }
}

/** Tiny three-bar equaliser that marks the currently playing row. */
@Composable
fun PlaybackBars(
    playing: Boolean,
    modifier: Modifier = Modifier,
    color: Color = MaterialTheme.colorScheme.primary,
) {
    val transition = rememberInfiniteTransition(label = "playback-bars")
    val phase by transition.animateFloat(
        initialValue = 0f,
        targetValue = 1f,
        animationSpec = infiniteRepeatable(tween(900), repeatMode = RepeatMode.Restart),
        label = "playback-bars-phase",
    )
    Canvas(modifier) {
        val bars = 3
        val gap = size.width / (bars * 2 - 1)
        repeat(bars) { index ->
            val localPhase = (phase + index * 0.3f) % 1f
            val wave = ((sin(localPhase * PI * 2) + 1.0) / 2.0).toFloat()
            val heightFactor = if (playing) 0.35f + wave * 0.65f else 0.3f
            drawRoundRect(
                color = color,
                topLeft = Offset(x = index * gap * 2, y = size.height * (1f - heightFactor)),
                size = Size(gap, size.height * heightFactor),
                cornerRadius = CornerRadius(gap / 2),
            )
        }
    }
}

/** Small pill label, e.g. VIP, quality or update frequency. */
@Composable
fun MetaChip(
    text: String,
    modifier: Modifier = Modifier,
    containerColor: Color = MaterialTheme.colorScheme.surfaceContainerHigh,
    contentColor: Color = MaterialTheme.colorScheme.onSurfaceVariant,
) {
    Surface(
        modifier = modifier,
        shape = CircleShape,
        color = containerColor,
        contentColor = contentColor,
    ) {
        Text(
            text = text,
            style = NocturnText.chip,
            maxLines = 1,
            modifier = Modifier.padding(horizontal = 10.dp, vertical = 4.dp),
        )
    }
}

/** Applies a bottom-aligned fade so scrolling content melts into the mini player. */
@Composable
fun BottomScrim(modifier: Modifier = Modifier, color: Color = MaterialTheme.colorScheme.background) {
    Box(
        modifier
            .fillMaxWidth()
            .height(120.dp)
            .background(Brush.verticalGradient(listOf(Color.Transparent, color.copy(alpha = 0.92f)))),
    )
}
