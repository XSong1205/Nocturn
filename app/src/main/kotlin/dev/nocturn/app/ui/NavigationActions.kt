package dev.nocturn.app.ui

import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.compose.LocalLifecycleOwner

/** Argument-taking counterpart to dropUnlessResumed for destination callbacks. */
@Composable
internal fun <T> rememberResumedNavigation(onNavigate: (T) -> Unit): (T) -> Unit {
    val lifecycle = LocalLifecycleOwner.current.lifecycle
    val latestAction = rememberUpdatedState(onNavigate)
    return remember(lifecycle) {
        { value ->
            if (lifecycle.currentState.isAtLeast(Lifecycle.State.RESUMED)) {
                latestAction.value(value)
            }
        }
    }
}
