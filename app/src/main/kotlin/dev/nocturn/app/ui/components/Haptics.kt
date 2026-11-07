package dev.nocturn.app.ui.components

import androidx.compose.runtime.Composable
import androidx.compose.runtime.Stable
import androidx.compose.runtime.remember
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.ui.hapticfeedback.HapticFeedback
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.platform.LocalHapticFeedback

/**
 * Global switch for the tactile layer. Every haptic in the app flows through [NocturnHaptics], so
 * the shell can silence all of them from one place (the "触感反馈" setting) without each screen
 * knowing about the preference.
 */
val LocalHapticsEnabled = staticCompositionLocalOf { true }

/**
 * Semantic haptic vocabulary for the whole app. Surfaces call these named intents instead of raw
 * [HapticFeedbackType] values so the tactile language stays consistent and can be re-tuned in one
 * place. All calls are no-ops when [enabled] is false, which keeps the call sites free of
 * conditionals.
 *
 * Mapping (Material 3 Expressive guidance):
 * - [tap] — lightweight confirmation for list rows, cards, chips and navigation destinations.
 * - [confirm] — committing an action that changes playback or durable state (play/pause, seek).
 * - [toggle] — a binary switch turned on or off (like, shuffle, settings).
 * - [step] — moving one step through a small option set (repeat mode, segmented choices).
 * - [threshold] — a drag crossed the acting threshold, before release decides anything.
 * - [release] — a committed drag gesture ended (dismiss, skip, collapse).
 */
@Stable
class NocturnHaptics(
    private val feedback: HapticFeedback,
    val enabled: Boolean,
) {
    fun tap() = emit(HapticFeedbackType.ContextClick)

    fun confirm() = emit(HapticFeedbackType.Confirm)

    fun toggle(on: Boolean) = emit(if (on) HapticFeedbackType.ToggleOn else HapticFeedbackType.ToggleOff)

    fun step() = emit(HapticFeedbackType.SegmentTick)

    fun threshold() = emit(HapticFeedbackType.GestureThresholdActivate)

    fun release() = emit(HapticFeedbackType.GestureEnd)

    private fun emit(type: HapticFeedbackType) {
        if (enabled) feedback.performHapticFeedback(type)
    }
}

/**
 * Resolves the app haptics for the current composition. Screens should call this instead of
 * [LocalHapticFeedback] so the global preference and the semantic vocabulary stay in force.
 */
@Composable
fun rememberHaptics(): NocturnHaptics {
    val feedback = LocalHapticFeedback.current
    val enabled = LocalHapticsEnabled.current
    return remember(feedback, enabled) { NocturnHaptics(feedback, enabled) }
}

/**
 * A controller that always emits, ignoring [LocalHapticsEnabled]. Only the setting that switches
 * the layer on should use it: the user needs to feel the pulse they just enabled, which the filtered
 * controller would suppress because the new value has not reached the composition yet.
 */
@Composable
fun rememberUnfilteredHaptics(): NocturnHaptics {
    val feedback = LocalHapticFeedback.current
    return remember(feedback) { NocturnHaptics(feedback, enabled = true) }
}
