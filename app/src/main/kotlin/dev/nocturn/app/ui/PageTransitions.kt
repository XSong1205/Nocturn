package dev.nocturn.app.ui

import androidx.compose.animation.ContentTransform
import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.slideInHorizontally
import androidx.compose.animation.slideOutHorizontally
import androidx.compose.animation.togetherWith

/** Native, seekable NavDisplay transforms. NavDisplay owns their progress and lifetime. */
internal object PageTransitions {
    private const val SLIDE_MILLIS = 300
    private const val FADE_MILLIS = 150

    fun push(direction: Int): ContentTransform =
        slideInHorizontally(
            animationSpec = tween(SLIDE_MILLIS, easing = FastOutSlowInEasing),
            initialOffsetX = { direction * it },
        ) togetherWith slideOutHorizontally(
            animationSpec = tween(SLIDE_MILLIS, easing = FastOutSlowInEasing),
            targetOffsetX = { -direction * it },
        )

    fun pop(direction: Int): ContentTransform = push(-direction)

    fun switchTab(): ContentTransform =
        fadeIn(tween(FADE_MILLIS)) togetherWith fadeOut(tween(FADE_MILLIS))
}
