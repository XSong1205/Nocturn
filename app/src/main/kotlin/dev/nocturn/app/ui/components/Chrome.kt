package dev.nocturn.app.ui.components

import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.spring
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.asPaddingValues
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.statusBars
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.compositionLocalOf
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.input.pointer.PointerEvent
import androidx.compose.ui.input.pointer.PointerEventPass
import androidx.compose.ui.input.pointer.PointerEventType
import androidx.compose.ui.input.pointer.changedToUpIgnoreConsumed
import androidx.compose.ui.layout.Measurable
import androidx.compose.ui.layout.MeasureResult
import androidx.compose.ui.layout.MeasureScope
import androidx.compose.ui.node.LayoutModifierNode
import androidx.compose.ui.node.ModifierNodeElement
import androidx.compose.ui.node.PointerInputModifierNode
import androidx.compose.ui.platform.InspectorInfo
import androidx.compose.ui.unit.Constraints
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.IntSize
import androidx.compose.ui.unit.dp
import kotlinx.coroutines.launch

/**
 * Height of the shell's floating bottom chrome (mini player, navigation bar and the system
 * navigation inset). Screens draw edge-to-edge behind it and add this value to the bottom content
 * padding of their scroll containers so the last item can still scroll clear of the chrome.
 * Standalone renders (tests, previews) see `0.dp`.
 */
val LocalBottomChromePadding = compositionLocalOf { 0.dp }

/** Bottom content padding for a scroll container: the shell chrome plus [extra] breathing room. */
@Composable
fun bottomContentPadding(extra: Dp = 24.dp): Dp = LocalBottomChromePadding.current + extra

/**
 * Soft fade under the status bar that appears once [visible] (usually "the list has scrolled").
 * Keeps scrolled content from colliding with the status icons without painting an opaque bar over
 * the ambient backdrop while the page rests at the top.
 */
@Composable
fun StatusBarScrim(
    visible: Boolean,
    modifier: Modifier = Modifier,
    color: Color = MaterialTheme.colorScheme.background,
) {
    // Inside the shell the progressive status bar blur does this job.
    if (LocalShellBackdrop.current != null) return
    val alpha by animateFloatAsState(
        targetValue = if (visible) 1f else 0f,
        animationSpec = MaterialTheme.motionScheme.fastEffectsSpec(),
        label = "status-scrim",
    )
    val inset = WindowInsets.statusBars.asPaddingValues().calculateTopPadding()
    Box(
        modifier = modifier
            .fillMaxWidth()
            .height(inset + 16.dp)
            .graphicsLayer { this.alpha = alpha }
            .background(
                Brush.verticalGradient(
                    0f to color.copy(alpha = 0.94f),
                    0.62f to color.copy(alpha = 0.78f),
                    1f to Color.Transparent,
                ),
            ),
    )
}

/**
 * Expressive press feedback: the element sinks to [pressedScale] while a pointer is down and
 * springs back on release or cancellation. It only observes pointer events (never consumes them),
 * so it composes with `clickable`, `Surface(onClick)` and scrolling parents. A press that turns
 * into a scroll releases as soon as the parent consumes the movement.
 *
 * This is purely visual: the tactile pulse comes from the press handler through
 * [NocturnHaptics], which fires on click instead of touch-down so scrolling never buzzes.
 */
fun Modifier.pressScale(pressedScale: Float = 0.96f): Modifier =
    this then PressScaleElement(pressedScale)

private data class PressScaleElement(val pressedScale: Float) : ModifierNodeElement<PressScaleNode>() {
    override fun create() = PressScaleNode(pressedScale)

    override fun update(node: PressScaleNode) {
        node.pressedScale = pressedScale
    }

    override fun InspectorInfo.inspectableProperties() {
        name = "pressScale"
        properties["pressedScale"] = pressedScale
    }
}

private class PressScaleNode(var pressedScale: Float) :
    Modifier.Node(), PointerInputModifierNode, LayoutModifierNode {

    private val scale = Animatable(1f)
    private var pressed = false

    private fun setPressed(value: Boolean) {
        if (pressed == value || !isAttached) return
        pressed = value
        coroutineScope.launch {
            scale.animateTo(
                targetValue = if (value) pressedScale else 1f,
                animationSpec = if (value) {
                    spring(dampingRatio = Spring.DampingRatioNoBouncy, stiffness = Spring.StiffnessMediumLow)
                } else {
                    spring(dampingRatio = 0.55f, stiffness = Spring.StiffnessMediumLow)
                },
            )
        }
    }

    override fun onPointerEvent(pointerEvent: PointerEvent, pass: PointerEventPass, bounds: IntSize) {
        if (pass != PointerEventPass.Final) return
        val changes = pointerEvent.changes
        when {
            pointerEvent.type == PointerEventType.Press -> setPressed(true)
            changes.all { it.changedToUpIgnoreConsumed() || !it.pressed } -> setPressed(false)
            // A scrolling ancestor (or a drag gesture) took over the pointer.
            changes.any { it.isConsumed && it.pressed } -> setPressed(false)
        }
    }

    override fun onCancelPointerInput() = setPressed(false)

    override fun onDetach() {
        pressed = false
    }

    override fun MeasureScope.measure(measurable: Measurable, constraints: Constraints): MeasureResult {
        val placeable = measurable.measure(constraints)
        return layout(placeable.width, placeable.height) {
            placeable.placeWithLayer(0, 0) {
                val value = scale.value
                scaleX = value
                scaleY = value
            }
        }
    }
}
