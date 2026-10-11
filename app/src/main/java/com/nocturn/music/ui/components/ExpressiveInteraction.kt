package com.nocturn.music.ui.components

import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.spring
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.awaitFirstDown
import androidx.compose.foundation.gestures.waitForUpOrCancellation
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Box
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.scale
import androidx.compose.ui.input.pointer.pointerInput
import kotlinx.coroutines.launch

/**
 * M3 Expressive 触觉与微形变点击容器 (Expressive Squish / Press scale)
 *
 * 在按下时产生微幅物理收缩与挤压 (Scale down to ~0.92f)，释放时以 Bouncy 弹簧回弹。
 */
@Composable
fun Modifier.expressiveClickable(
    onClick: () -> Unit,
    pressedScale: Float = 0.92f,
    enabled: Boolean = true
): Modifier {
    val scale = remember { Animatable(1f) }
    val scope = rememberCoroutineScope()

    return this
        .scale(scale.value)
        .pointerInput(enabled) {
            if (!enabled) return@pointerInput
            while (true) {
                awaitPointerEventScope {
                    awaitFirstDown(requireUnconsumed = false)
                    scope.launch {
                        scale.animateTo(
                            targetValue = pressedScale,
                            animationSpec = spring(
                                dampingRatio = 0.7f,
                                stiffness = Spring.StiffnessMedium
                            )
                        )
                    }
                    val upOrCancel = waitForUpOrCancellation()
                    scope.launch {
                        scale.animateTo(
                            targetValue = 1f,
                            animationSpec = spring(
                                dampingRatio = Spring.DampingRatioMediumBouncy,
                                stiffness = Spring.StiffnessLow
                            )
                        )
                    }
                    if (upOrCancel != null) {
                        onClick()
                    }
                }
            }
        }
}
