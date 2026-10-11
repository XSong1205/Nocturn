package com.nocturn.music.ui.components

import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.gestures.detectDragGestures
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import kotlin.math.sin

/**
 * Material 3 Expressive (M3E) 标志性波浪形音频进度条 (Wavy Slider)
 *
 * 在播放状态下呈现生命感律动波浪 (Wave)，当拖拽或暂停时平滑变形为拉直的张力线条。
 * 区别于死板的传统直线 Slider，带给音乐沉浸式的情感表达。
 */
@Composable
fun ExpressiveWavySlider(
    value: Float,
    onValueChange: (Float) -> Unit,
    modifier: Modifier = Modifier,
    onValueChangeFinished: (() -> Unit)? = null,
    isPlaying: Boolean = true,
    activeColor: Color = Color.White,
    inactiveColor: Color = Color.White.copy(alpha = 0.22f),
    thumbColor: Color = Color.White,
    trackHeight: Dp = 6.dp,
    waveAmplitudeDp: Dp = 3.5.dp,
    waveLengthDp: Dp = 22.dp
) {
    var isDragging by remember { mutableStateOf(false) }
    var dragFraction by remember { mutableFloatStateOf(value) }

    LaunchedEffect(value) {
        if (!isDragging) {
            dragFraction = value.coerceIn(0f, 1f)
        }
    }

    // 播放中波动相位无限循环
    val infiniteTransition = rememberInfiniteTransition(label = "wavePhaseTransition")
    val wavePhase by infiniteTransition.animateFloat(
        initialValue = 0f,
        targetValue = 2f * Math.PI.toFloat(),
        animationSpec = infiniteRepeatable(
            animation = tween(1800, easing = LinearEasing),
            repeatMode = RepeatMode.Restart
        ),
        label = "wavePhase"
    )

    // 拖拽时波浪压平 (Morph to flat track)
    val animatedAmplitudeFraction by animateFloatAsState(
        targetValue = if (isPlaying && !isDragging) 1f else 0.15f,
        animationSpec = androidx.compose.animation.core.spring(
            dampingRatio = 0.72f,
            stiffness = 300f
        ),
        label = "amplitudeFraction"
    )

    val thumbRadius by animateFloatAsState(
        targetValue = if (isDragging) 10f else 6.5f,
        animationSpec = androidx.compose.animation.core.spring(
            dampingRatio = 0.65f,
            stiffness = 400f
        ),
        label = "thumbRadius"
    )

    Box(
        modifier = modifier
            .fillMaxWidth()
            .height(36.dp)
            .pointerInput(Unit) {
                detectTapGestures(
                    onPress = { offset ->
                        val newFraction = (offset.x / size.width.toFloat()).coerceIn(0f, 1f)
                        dragFraction = newFraction
                        onValueChange(newFraction)
                        onValueChangeFinished?.invoke()
                    }
                )
            }
            .pointerInput(Unit) {
                detectDragGestures(
                    onDragStart = { offset ->
                        isDragging = true
                        val newFraction = (offset.x / size.width.toFloat()).coerceIn(0f, 1f)
                        dragFraction = newFraction
                        onValueChange(newFraction)
                    },
                    onDragEnd = {
                        isDragging = false
                        onValueChangeFinished?.invoke()
                    },
                    onDragCancel = {
                        isDragging = false
                    },
                    onDrag = { change, _ ->
                        change.consume()
                        val newFraction = (change.position.x / size.width.toFloat()).coerceIn(0f, 1f)
                        dragFraction = newFraction
                        onValueChange(newFraction)
                    }
                )
            }
    ) {
        Canvas(modifier = Modifier.fillMaxWidth().height(36.dp)) {
            val width = size.width
            val centerY = size.height / 2f
            val currentProgressX = width * dragFraction.coerceIn(0f, 1f)

            val trackStrokePx = trackHeight.toPx()
            val waveAmplitudePx = waveAmplitudeDp.toPx() * animatedAmplitudeFraction
            val waveLengthPx = waveLengthDp.toPx()

            // 1. 绘制已播放部分的波浪路径 (Active Wavy Track)
            val activePath = Path()
            activePath.moveTo(0f, centerY)

            val stepPx = 3f
            var x = 0f
            while (x <= currentProgressX) {
                val y = centerY + sin((x / waveLengthPx) * 2f * Math.PI.toFloat() + wavePhase) * waveAmplitudePx
                activePath.lineTo(x, y)
                x += stepPx
            }
            // 确保绘制到精确 progress 点
            val endY = centerY + sin((currentProgressX / waveLengthPx) * 2f * Math.PI.toFloat() + wavePhase) * waveAmplitudePx
            activePath.lineTo(currentProgressX, endY)

            drawPath(
                path = activePath,
                color = activeColor,
                style = Stroke(
                    width = trackStrokePx,
                    cap = StrokeCap.Round
                )
            )

            // 2. 绘制未播放部分的底轨 (Inactive Track 平直沉静轨道)
            if (currentProgressX < width) {
                drawLine(
                    color = inactiveColor,
                    start = Offset(currentProgressX + trackStrokePx / 2f, centerY),
                    end = Offset(width, centerY),
                    strokeWidth = trackStrokePx * 0.85f,
                    cap = StrokeCap.Round
                )
            }

            // 3. 绘制动态交互 Thumb (M3E 物理形变胶囊/圆形指示标)
            val thumbRadiusPx = thumbRadius.dp.toPx()
            drawCircle(
                color = thumbColor,
                radius = thumbRadiusPx,
                center = Offset(currentProgressX, endY)
            )
        }
    }
}
