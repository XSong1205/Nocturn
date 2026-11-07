@file:OptIn(androidx.compose.material3.ExperimentalMaterial3ExpressiveApi::class)

package dev.nocturn.app.ui

import androidx.compose.animation.ContentTransform
import androidx.compose.animation.EnterExitState
import androidx.compose.animation.EnterTransition
import androidx.compose.animation.ExitTransition
import androidx.compose.animation.core.CubicBezierEasing
import androidx.compose.animation.core.FiniteAnimationSpec
import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.animate
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.snap
import androidx.compose.animation.core.spring
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.scaleOut
import androidx.compose.animation.slideInHorizontally
import androidx.compose.animation.slideOutHorizontally
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.drawWithContent
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.dp
import androidx.navigation3.scene.Scene
import androidx.navigation3.ui.LocalNavAnimatedContentScope
import androidx.navigationevent.NavigationEvent
import dev.nocturn.app.ui.theme.LocalIsDarkTheme
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch

/** [androidx.navigation3.runtime.NavEntry.metadata] keys that classify a page transition. */
internal object PageMetadata {
    const val TAB = "dev.nocturn.page.tab"
    const val DEPTH = "dev.nocturn.page.depth"
}

/**
 * Push and button pop take their spring from the theme's motion scheme (see [PageMotion.offsetSpec]
 * and [PageMotion.floatSpec]), the same spatial spec the rest of the app's surfaces use. The slide,
 * corners and scrim share it so they stay in step.
 */

/**
 * Predictive back, following Android's "full-screen surface" guideline.
 *
 * Preview (finger down): the page scales to 90% on STANDARD_DECELERATE (0, 0, 0, 1) of the gesture
 * progress, shifts away from the swipe edge and toward the finger's height with an 8dp margin to
 * the screen edge, and keeps its rounded corners. It stays fully opaque; the page below is dimmed.
 *
 * Commit (finger up past the threshold): the page shrinks a little further and fades out with the
 * SystemUI exit curve (.1, .1, 0, 1) while the page below brightens, then the stack is popped.
 *
 * Cancel: the preview springs back to its original size, position and corners.
 *
 * Both phases are driven by [PageMotion] and read only in the draw phase. [NavDisplay]'s own
 * seekable transition is kept neutral and only holds both pages composed; its length is just how
 * long the page below stays composed after a release, which must outlast the commit animation.
 */
private const val GESTURE_LIFETIME_MILLIS = 3000
private const val COMMIT_MILLIS = 300
private const val COMMIT_EXTRA_SHRINK = 0.06f
private const val PREVIEW_SCALE = 0.9f
private const val PREVIEW_EDGE_MARGIN_DP = 8f
private val GestureDecelerate = CubicBezierEasing(0f, 0f, 0f, 1f)
private val CommitExit = CubicBezierEasing(0.1f, 0.1f, 0f, 1f)

private const val TAB_FADE_IN_MILLIS = 220
private const val TAB_FADE_OUT_MILLIS = 90

/** Foreground pages are fully rounded once this much of their cover has run. */
private const val CORNER_RAMP = 0.12f
private val PageCornerRadius = 28.dp
private const val SCRIM_LIGHT = 0.32f
private const val SCRIM_DARK = 0.5f

internal enum class PageMotionMode { Push, Pop, Gesture, Fade }

internal enum class PageLayer { None, Foreground, Background }

internal data class PageRole(val layer: PageLayer, val mode: PageMotionMode)

private val SettledRole = PageRole(PageLayer.None, PageMotionMode.Fade)

/**
 * Chooses the native [ContentTransform] for every NavDisplay transition, remembers which page is
 * the foreground of it, and owns the predictive back state that [PageSurface] draws from.
 *
 * The float states are written per gesture event and read only inside draw blocks, so a gesture
 * frame never recomposes a page.
 */
internal class PageMotion {
    var rtl: Boolean = false
    var density: Float = 1f
    var widthPx: Int = 0
    var heightPx: Int = 0
    var scope: CoroutineScope? = null

    /** Spatial springs from the theme's motion scheme, set by the host before the first use. */
    var offsetSpec: FiniteAnimationSpec<IntOffset> = tween(420)
    var floatSpec: FiniteAnimationSpec<Float> = tween(420)

    /** Raw back-gesture progress, 0..1. Held at its release value while the commit plays. */
    val preview = mutableFloatStateOf(0f)

    /** Post-release progress, 0..1: 0 while the finger is down, 1 once the page has faded out. */
    val commit = mutableFloatStateOf(0f)

    /** Vertical shift in px, from where the finger is relative to where the gesture began. */
    val touchShift = mutableFloatStateOf(0f)

    /** +1 for a swipe from the left edge, -1 from the right edge, 0 otherwise. */
    val edgeSign = mutableIntStateOf(0)

    /** Incremented once per commit; the foreground page runs the animation. */
    val commitRequest = mutableIntStateOf(0)

    var committing: Boolean = false
        private set

    private var mode = PageMotionMode.Fade
    private var foreground: Any? = null
    private var background: Any? = null
    private var committedKey: Any? = null
    private var gestureLive = false
    private var startY = Float.NaN
    private var pendingBack: (() -> Unit)? = null
    private var activeRequest = 0
    private var cancelJob: Job? = null

    // ---- Gesture input --------------------------------------------------------------------

    fun onGesture(event: NavigationEvent) {
        if (committing) return
        if (!gestureLive) {
            gestureLive = true
            cancelJob?.cancel()
            cancelJob = null
            commit.floatValue = 0f
            committedKey = null
            startY = event.touchY
            touchShift.floatValue = 0f
        }
        preview.floatValue = event.progress.coerceIn(0f, 1f)
        edgeSign.intValue = when (event.swipeEdge) {
            NavigationEvent.EDGE_LEFT -> 1
            NavigationEvent.EDGE_RIGHT -> -1
            else -> 0
        }
        // Spec: at most height/20 - 8dp, so the page never comes closer than 8dp to the screen edge.
        val maxShift = (heightPx / 20f - PREVIEW_EDGE_MARGIN_DP * density).coerceAtLeast(0f)
        val range = heightPx * 0.5f
        if (range > 0f) {
            val normalized = ((event.touchY - startY) / range).coerceIn(-1f, 1f)
            val eased = GestureDecelerate.transform(kotlin.math.abs(normalized))
            touchShift.floatValue = (if (normalized < 0f) -eased else eased) * maxShift
        }
    }

    fun onIdle() {
        if (!committing) gestureLive = false
    }

    /** The gesture was released before the commit point: spring back to the untouched page. */
    fun onCancelled() {
        if (committing) return
        gestureLive = false
        startY = Float.NaN
        cancelJob?.cancel()
        cancelJob = scope?.launch {
            animate(
                initialValue = preview.floatValue,
                targetValue = 0f,
                animationSpec = spring(dampingRatio = 0.9f, stiffness = Spring.StiffnessMedium),
            ) { value, _ -> preview.floatValue = value }
        }
    }

    /**
     * The gesture was committed. With a live preview the page plays its commit animation and only
     * then [back] pops the stack; without one (button, key) the pop is immediate.
     */
    fun onCompleted(back: () -> Unit) {
        if (committing) return
        if (!gestureLive || preview.floatValue <= 0f) {
            back()
            return
        }
        committing = true
        committedKey = foreground
        pendingBack = back
        val request = ++activeRequest
        commitRequest.intValue = request
        // The foreground page runs the animation; this only guarantees the pop if it cannot.
        scope?.launch {
            delay(COMMIT_MILLIS + 300L)
            finishCommit(request)
        }
    }

    /** Plays the post-release shrink and fade. Called by the foreground page. */
    suspend fun runCommit(request: Int) {
        try {
            animate(
                initialValue = 0f,
                targetValue = 1f,
                animationSpec = tween(COMMIT_MILLIS, easing = LinearEasing),
            ) { value, _ -> commit.floatValue = value }
        } finally {
            finishCommit(request)
        }
    }

    private fun finishCommit(request: Int) {
        if (!committing || request != activeRequest) return
        committing = false
        gestureLive = false
        startY = Float.NaN
        val back = pendingBack
        pendingBack = null
        back?.invoke()
    }

    // ---- Transitions ----------------------------------------------------------------------

    fun <T : Any> transform(from: Scene<T>, to: Scene<T>, swipeEdge: Int?): ContentTransform {
        // NavDisplay re-evaluates the spec when the finger lifts. While the commit plays the stack
        // has not changed yet, so keep the neutral gesture spec: it holds the page below composed
        // and fully drawn until the pop.
        if (committing) return gesture()
        val fromMeta = from.metadata
        val toMeta = to.metadata
        val fromDepth = fromMeta[PageMetadata.DEPTH] as? Int ?: 0
        val toDepth = toMeta[PageMetadata.DEPTH] as? Int ?: 0
        val fromKey = from.entries.lastOrNull()?.contentKey
        val toKey = to.entries.lastOrNull()?.contentKey
        if (toDepth > fromDepth) committedKey = null
        val nextMode = when {
            fromMeta[PageMetadata.TAB] != toMeta[PageMetadata.TAB] -> PageMotionMode.Fade
            swipeEdge != null -> PageMotionMode.Gesture
            // The page already faded out under the finger's release: nothing is left to animate.
            committedKey != null && fromKey == committedKey -> PageMotionMode.Fade
            toDepth > fromDepth -> PageMotionMode.Push
            toDepth < fromDepth -> PageMotionMode.Pop
            else -> PageMotionMode.Fade
        }
        mode = nextMode
        val pushed = nextMode == PageMotionMode.Push
        foreground = if (pushed) toKey else fromKey
        background = if (pushed) fromKey else toKey
        return when (nextMode) {
            PageMotionMode.Push -> push()
            PageMotionMode.Pop -> pop()
            PageMotionMode.Gesture -> gesture()
            PageMotionMode.Fade ->
                if (committedKey != null && fromKey == committedKey) instant() else fade()
        }
    }

    fun roleOf(contentKey: Any): PageRole = when (contentKey) {
        foreground -> if (mode == PageMotionMode.Fade) SettledRole else PageRole(PageLayer.Foreground, mode)
        background -> if (mode == PageMotionMode.Fade) SettledRole else PageRole(PageLayer.Background, mode)
        else -> SettledRole
    }

    private val direction: Int get() = if (rtl) -1 else 1

    /** The new page slides over the old one, which parallaxes a quarter width and dims. */
    private fun push() = ContentTransform(
        targetContentEnter = slideInHorizontally(offsetSpec) { direction * it },
        initialContentExit = slideOutHorizontally(offsetSpec) { -direction * it / 4 },
    )

    /** Button, key and three-button back: the mirror image of [push]. */
    private fun pop() = ContentTransform(
        targetContentEnter = slideInHorizontally(offsetSpec) { -direction * it / 4 },
        initialContentExit = slideOutHorizontally(offsetSpec) { direction * it },
    )

    /**
     * Geometry-free: it only keeps both pages composed while the gesture seeks, and its length is
     * how long the page below outlives a release. Everything visible is drawn by [PageSurface].
     */
    private fun gesture() = ContentTransform(
        targetContentEnter = EnterTransition.None,
        // The 0.1% is deliberate: an animation from 1 to 1 is optimised away and would leave the
        // transition with no duration, so the page below would be dropped the moment it settles.
        initialContentExit = scaleOut(
            animationSpec = tween(GESTURE_LIFETIME_MILLIS, easing = LinearEasing),
            targetScale = 0.999f,
        ),
    )

    /** Switching tabs replaces the whole stack, so there is no spatial relationship to show. */
    private fun fade() = ContentTransform(
        targetContentEnter = fadeIn(tween(TAB_FADE_IN_MILLIS, delayMillis = TAB_FADE_OUT_MILLIS)),
        initialContentExit = fadeOut(tween(TAB_FADE_OUT_MILLIS)),
    )

    /** After the commit the old page is already invisible and the new one is in place. */
    private fun instant() = ContentTransform(
        targetContentEnter = EnterTransition.None,
        initialContentExit = ExitTransition.None,
    )
}

/** Cover for push and button pop: the value mirrors the page's own enter or exit transition. */
private fun PageRole.coverSpec(spatial: FiniteAnimationSpec<Float>): FiniteAnimationSpec<Float> = when (mode) {
    PageMotionMode.Push, PageMotionMode.Pop -> spatial
    PageMotionMode.Gesture, PageMotionMode.Fade -> snap()
}

/**
 * Wraps one NavDisplay entry. It rounds the page in front and dims the page behind it, and for a
 * predictive back it draws the preview and commit geometry. Everything animated is read in the
 * draw phase, and the role is recomputed only when this entry's own enter/exit segment changes,
 * so neither a gesture frame nor a transition frame recomposes [content].
 */
@Composable
internal fun PageSurface(
    motion: PageMotion,
    contentKey: Any,
    content: @Composable () -> Unit,
) {
    val transition = LocalNavAnimatedContentScope.current.transition
    val current = transition.currentState
    val target = transition.targetState
    val role = remember(current, target) {
        if (current == target) SettledRole else motion.roleOf(contentKey)
    }
    val cover by transition.animateFloat(
        transitionSpec = { role.coverSpec(motion.floatSpec) },
        label = "page-cover",
    ) { state -> if (state == EnterExitState.Visible) 0f else 1f }

    // Once this page has played its commit it stays hidden, whatever the transition does next.
    var committed by remember { mutableStateOf(false) }
    val latestRole by rememberUpdatedState(role)
    val request = motion.commitRequest.intValue
    LaunchedEffect(request) {
        val r = latestRole
        if (request > 0 && r.layer == PageLayer.Foreground && r.mode == PageMotionMode.Gesture) {
            committed = true
            motion.runCommit(request)
        }
    }

    val scrim = if (LocalIsDarkTheme.current) SCRIM_DARK else SCRIM_LIGHT
    val gestureForeground = role.layer == PageLayer.Foreground && role.mode == PageMotionMode.Gesture
    val gestureBackground = role.layer == PageLayer.Background && role.mode == PageMotionMode.Gesture
    Box(
        Modifier
            .fillMaxSize()
            .graphicsLayer {
                if (gestureForeground || committed) {
                    val eased = GestureDecelerate.transform(motion.preview.floatValue)
                    val exit = CommitExit.transform(motion.commit.floatValue)
                    val scale = (1f - (1f - PREVIEW_SCALE) * eased) * (1f - COMMIT_EXTRA_SHRINK * exit)
                    scaleX = scale
                    scaleY = scale
                    val shiftX = (motion.widthPx / 20f - PREVIEW_EDGE_MARGIN_DP * motion.density)
                        .coerceAtLeast(0f)
                    translationX = motion.edgeSign.intValue * shiftX * eased
                    translationY = motion.touchShift.floatValue * eased
                    alpha = 1f - exit
                    shape = RoundedCornerShape(PageCornerRadius * (eased / CORNER_RAMP).coerceIn(0f, 1f))
                    clip = eased > 0f
                } else if (role.layer == PageLayer.Foreground) {
                    val rounding = (cover / CORNER_RAMP).coerceIn(0f, 1f)
                    if (rounding > 0f) {
                        shape = RoundedCornerShape(PageCornerRadius * rounding)
                        clip = true
                    }
                }
            }
            .drawWithContent {
                drawContent()
                val dim = when {
                    gestureBackground -> {
                        val eased = GestureDecelerate.transform(motion.preview.floatValue)
                        scrim * eased * (1f - CommitExit.transform(motion.commit.floatValue))
                    }
                    role.layer == PageLayer.Background -> scrim * cover.coerceIn(0f, 1f)
                    else -> 0f
                }
                if (dim > 0f) drawRect(Color.Black, alpha = dim)
            },
    ) {
        content()
    }
}
