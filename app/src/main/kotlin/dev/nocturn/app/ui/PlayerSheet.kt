@file:OptIn(androidx.compose.material3.ExperimentalMaterial3ExpressiveApi::class)

package dev.nocturn.app.ui

import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.CubicBezierEasing
import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.FiniteAnimationSpec
import androidx.compose.animation.core.LinearOutSlowInEasing
import androidx.compose.animation.core.SpringSpec
import androidx.compose.animation.core.spring
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.Stable
import androidx.compose.runtime.derivedStateOf
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.setValue
import androidx.compose.runtime.snapshotFlow
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.geometry.RoundRect
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Outline
import androidx.compose.ui.graphics.Shape
import androidx.compose.ui.graphics.TransformOrigin
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.layout.onSizeChanged
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.unit.Density
import androidx.compose.ui.unit.LayoutDirection
import androidx.compose.ui.unit.dp
import androidx.navigationevent.NavigationEventInfo
import androidx.navigationevent.NavigationEventTransitionState
import androidx.navigationevent.compose.NavigationBackHandler
import androidx.navigationevent.compose.rememberNavigationEventState
import dev.nocturn.app.ui.screens.player.PlayerSheetDrag
import dev.nocturn.app.ui.theme.LocalIsDarkTheme
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Job
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.flow.filterIsInstance
import kotlinx.coroutines.launch

/** Matches the mini player capsule so the morph lands on its exact outline. */
private val MiniCornerRadius = 28.dp

/** How far a full predictive back gesture pulls the open player toward the mini player. */
private const val BACK_PREVIEW_DEPTH = 0.22f
private val BackPreviewEasing = CubicBezierEasing(0f, 0f, 0f, 1f)

/** Releases faster than this (progress per second) settle in their own direction. */
private const val FLING_PROGRESS_VELOCITY = 1.2f
private const val SCRIM_LIGHT = 0.32f
private const val SCRIM_DARK = 0.5f

/** Used until the host supplies the theme's spatial spring. */
private fun fallbackSpring(): FiniteAnimationSpec<Float> =
    spring(dampingRatio = 1f, stiffness = 420f, visibilityThreshold = 0.0005f)

/** Keep Material's timing, but settle at screen edges without overshoot or a visible final snap. */
private fun FiniteAnimationSpec<Float>.forContainer(): FiniteAnimationSpec<Float> =
    if (this is SpringSpec<Float>) {
        spring(dampingRatio = dampingRatio.coerceAtLeast(1f), stiffness = stiffness, visibilityThreshold = 0.0005f)
    } else {
        this
    }

private data object PlayerSheetInfo : NavigationEventInfo()

/**
 * The now-playing surface as one continuous morph between the mini player (progress 0) and the
 * full-screen player (progress 1). Taps, drags on the mini player, drags on the player header and
 * predictive back all move the same progress, so every path shows the same geometry.
 *
 * Progress is read in the draw phase only; composition sees it once, through [composed], when the
 * player has to be attached or can be dropped.
 */
@Stable
internal class PlayerSheetState(private val scope: CoroutineScope) : PlayerSheetDrag {
    private val animatable = Animatable(0f)
    private val scrimAnimatable = Animatable(0f)
    private var dragProgress by mutableFloatStateOf(0f)
    private var dragStartProgress = 0f
    private var dragStartScrim = 0f
    private var dragging by mutableStateOf(false)
    private var settleJob: Job? = null
    private var settleTarget = 0f

    /** Root bounds of the mini player capsule, written from its layout callback. */
    var miniBounds: Rect? = null

    internal var containerSize: Size = Size.Zero
    internal var expanded: Boolean by mutableStateOf(false)
    internal var onExpand: () -> Unit = {}
    internal var onCollapse: () -> Unit = {}

    /** Large entrances get more time than exits; opacity uses Material's effects spring. */
    internal var expandSpec: FiniteAnimationSpec<Float> = fallbackSpring()
    internal var collapseSpec: FiniteAnimationSpec<Float> = fallbackSpring()
    internal var effectsSpec: FiniteAnimationSpec<Float> = fallbackSpring()

    val progress: Float get() = (if (dragging) dragProgress else animatable.value).coerceIn(0f, 1f)
    val scrimProgress: Float
        get() = if (dragging) {
            // Picking up a moving sheet must also pick up its independently animated scrim.
            // Interpolate from that point to either endpoint without an opacity jump.
            if (dragProgress <= dragStartProgress) {
                dragStartScrim * dragProgress / dragStartProgress.coerceAtLeast(0.0001f)
            } else {
                1f - (1f - dragStartScrim) * (1f - dragProgress) / (1f - dragStartProgress).coerceAtLeast(0.0001f)
            }
        } else {
            scrimAnimatable.value.coerceIn(0f, 1f)
        }

    /** True while any part of the player must be composed. */
    val composed: Boolean by derivedStateOf {
        dragging || expanded || animatable.value > 0f || scrimAnimatable.value > 0f
    }

    /** The full-screen rect morphs from here. */
    internal fun miniRect(density: Density): Rect = miniBounds?.takeIf { !it.isEmpty } ?: run {
        val height = with(density) { 72.dp.toPx() }
        val inset = with(density) { 12.dp.toPx() }
        Rect(inset, containerSize.height - height - inset, containerSize.width - inset, containerSize.height - inset)
    }

    private val travel: Float
        get() = (miniBounds?.top ?: (containerSize.height * 0.85f)).coerceAtLeast(1f)

    override fun onDragStart() {
        val current = progress
        val currentScrim = scrimProgress
        settleJob?.cancel()
        dragProgress = current
        dragStartProgress = current
        dragStartScrim = currentScrim
        dragging = true
    }

    override fun onDrag(deltaY: Float) {
        dragProgress = (dragProgress - deltaY / travel).coerceIn(0f, 1f)
    }

    override fun onDragEnd(velocityY: Float) {
        if (!dragging) return
        val velocity = -velocityY / travel
        val open = when {
            velocity > FLING_PROGRESS_VELOCITY -> true
            velocity < -FLING_PROGRESS_VELOCITY -> false
            else -> dragProgress > 0.5f
        }
        // The drag hands over to the spring only once the spring holds the dragged value, so
        // no frame falls back to the stale animated value in between.
        settle(if (open) 1f else 0f, from = dragProgress, velocity = velocity, releaseDrag = true)
        if (open != expanded) {
            if (open) onExpand() else onCollapse()
        }
    }

    /** Back preview: pulls the open player down by a decelerated share of the gesture. */
    internal fun preview(backProgress: Float) {
        settleJob?.cancel()
        settleTarget = Float.NaN
        val depth = BACK_PREVIEW_DEPTH * BackPreviewEasing.transform(backProgress.coerceIn(0f, 1f))
        scope.launch {
            animatable.snapTo(1f - depth)
            scrimAnimatable.snapTo(1f - depth)
        }
    }

    /** Animates toward [target] unless a settle toward it is already running. */
    internal fun settle(
        target: Float,
        from: Float = progress,
        velocity: Float = animatable.velocity,
        releaseDrag: Boolean = false,
    ) {
        if (!releaseDrag && settleTarget == target && settleJob?.isActive == true) return
        val scrimFrom = scrimProgress
        settleJob?.cancel()
        settleTarget = target
        settleJob = scope.launch {
            // Only a gesture needs snapping. Retargeting an animation keeps its current position
            // and velocity, so a quick reversal does not stop dead for a frame.
            if (releaseDrag) {
                animatable.snapTo(from)
                scrimAnimatable.snapTo(scrimFrom)
                dragging = false
            }
            coroutineScope {
                launch { scrimAnimatable.animateTo(target, effectsSpec) }
                animatable.animateTo(
                    target,
                    if (target == 1f) expandSpec else collapseSpec,
                    initialVelocity = velocity,
                )
            }
        }
    }

    /** Sync with the view model when nothing is holding the sheet. */
    internal fun follow(expanded: Boolean) {
        this.expanded = expanded
        if (dragging) return
        val target = if (expanded) 1f else 0f
        if (animatable.value != target || settleTarget != target) settle(target)
    }
}

@Composable
internal fun rememberPlayerSheetState(): PlayerSheetState {
    val scope = rememberCoroutineScope()
    return remember(scope) { PlayerSheetState(scope) }
}

/**
 * Hosts the shell and, above it, the now-playing sheet. The shell stays composed and drawn under
 * the sheet at every progress, so the mini player and chrome are revealed exactly as they are.
 */
@Composable
internal fun PlayerNavigationHost(
    visible: Boolean,
    onCollapse: () -> Unit,
    shell: @Composable () -> Unit,
    player: @Composable () -> Unit,
    onExpand: () -> Unit = {},
    state: PlayerSheetState = rememberPlayerSheetState(),
) {
    state.onExpand = onExpand
    state.expandSpec = MaterialTheme.motionScheme.slowSpatialSpec<Float>().forContainer()
    state.collapseSpec = MaterialTheme.motionScheme.defaultSpatialSpec<Float>().forContainer()
    state.effectsSpec = MaterialTheme.motionScheme.defaultEffectsSpec()
    state.onCollapse = onCollapse
    LaunchedEffect(state, visible) { state.follow(visible) }

    val density = LocalDensity.current
    val scrim = if (LocalIsDarkTheme.current) SCRIM_DARK else SCRIM_LIGHT
    Box(
        Modifier
            .fillMaxSize()
            .onSizeChanged { state.containerSize = Size(it.width.toFloat(), it.height.toFloat()) },
    ) {
        shell()
        if (visible || state.composed) {
            Box(
                Modifier
                    .fillMaxSize()
                    .drawBehind {
                        val alpha = scrim * state.scrimProgress
                        if (alpha > 0f) drawRect(Color.Black, alpha = alpha)
                    },
            )
            Box(
                Modifier
                    .fillMaxSize()
                    .graphicsLayer {
                        val p = state.progress.coerceIn(0f, 1f)
                        val mini = state.miniRect(density)
                        val left = lerp(mini.left, 0f, p)
                        val top = lerp(mini.top, 0f, p)
                        val right = lerp(mini.right, size.width, p)
                        val bottom = lerp(mini.bottom, size.height, p)
                        val width = (right - left).coerceAtLeast(1f)
                        // Uniform scale keeps the player's own layout; the clip below does the
                        // non-uniform part, so its corners are never stretched.
                        val scale = width / size.width
                        transformOrigin = TransformOrigin(0f, 0f)
                        scaleX = scale
                        scaleY = scale
                        translationX = left
                        translationY = top
                        // Ease the corners into the full-screen outline without a sharp change
                        // of speed at the start/end of the shape transition.
                        val miniRadius = MiniCornerRadius.toPx()
                        val rounding = FastOutSlowInEasing.transform(((1f - p) / 0.45f).coerceIn(0f, 1f))
                        shape = MorphShape(
                            height = (bottom - top) / scale,
                            radius = miniRadius * rounding / scale,
                        )
                        clip = p < 1f
                        // Cross-fade with the mini player underneath over the first stretch.
                        alpha = LinearOutSlowInEasing.transform((p / 0.22f).coerceIn(0f, 1f))
                    },
            ) {
                player()
            }
        }
    }

    PlayerBackHandler(state = state, enabled = visible)
}

@Composable
private fun PlayerBackHandler(state: PlayerSheetState, enabled: Boolean) {
    val navigationState = rememberNavigationEventState(currentInfo = PlayerSheetInfo)
    val latestState by rememberUpdatedState(state)
    NavigationBackHandler(
        state = navigationState,
        isBackEnabled = enabled,
        onBackCancelled = { latestState.settle(1f) },
        onBackCompleted = { latestState.onCollapse() },
    )
    LaunchedEffect(navigationState) {
        snapshotFlow { navigationState.transitionState }
            .filterIsInstance<NavigationEventTransitionState.InProgress>()
            .collect { gesture -> latestState.preview(gesture.latestEvent.progress) }
    }
}

/** A rounded rect of the given height at the top of the layer, in unscaled layer pixels. */
private data class MorphShape(val height: Float, val radius: Float) : Shape {
    override fun createOutline(size: Size, layoutDirection: LayoutDirection, density: Density): Outline =
        Outline.Rounded(
            RoundRect(
                rect = Rect(0f, 0f, size.width, height.coerceIn(0f, size.height)),
                cornerRadius = CornerRadius(radius),
            ),
        )
}

private fun lerp(start: Float, stop: Float, fraction: Float) = start + (stop - start) * fraction
