@file:OptIn(androidx.compose.material3.ExperimentalMaterial3ExpressiveApi::class)

package dev.nocturn.app.ui

import androidx.compose.material3.MotionScheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.State
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.snapshotFlow
import androidx.compose.ui.Modifier
import androidx.compose.ui.layout.onSizeChanged
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.LocalLayoutDirection
import androidx.compose.ui.unit.LayoutDirection
import androidx.navigation3.runtime.NavEntry
import androidx.navigation3.runtime.rememberDecoratedNavEntries
import androidx.navigation3.runtime.rememberSaveableStateHolderNavEntryDecorator
import androidx.navigation3.scene.SinglePaneSceneStrategy
import androidx.navigation3.scene.rememberNavigationEventState
import androidx.navigation3.scene.rememberSceneState
import androidx.navigation3.ui.NavDisplay
import androidx.navigationevent.NavigationEventTransitionState
import androidx.navigationevent.compose.NavigationBackHandler
import dev.nocturn.app.ui.components.NocturnBackdrop
import dev.nocturn.app.ui.state.AppTab
import dev.nocturn.app.ui.state.NavState
import dev.nocturn.app.ui.state.Screen
import dev.nocturn.app.ui.state.tabRoot

private val EdgeAnchoredMotion = MotionScheme.standard()

/** Stack position distinguishes repeated visits to the same album, artist or playlist. */
private data class ScreenDestination(val tab: AppTab, val index: Int, val screen: Screen) {
    /** Saveable and unique per stack slot, so each visit keeps its own saved state. */
    val contentKey: String get() = "${tab.name}/$index/$screen"
}

/**
 * Routes the selected tab and its pushed pages through Navigation3's [NavDisplay].
 *
 * Scenes, the seekable predictive back transition and its commit/cancel settle are all the
 * library's. [PageMotion] only picks the native enter/exit transforms per transition, and
 * [PageSurface] adds the rounded corners and the scrim that native transforms cannot draw.
 */
@Composable
internal fun ScreenNavigationHost(
    navigation: NavState,
    backEnabled: Boolean,
    onBack: () -> Unit,
    modifier: Modifier = Modifier,
    content: @Composable (Screen) -> Unit,
) {
    val backStack = remember(navigation) {
        (listOf(tabRoot(navigation.tab)) + navigation.stack).mapIndexed { index, screen ->
            ScreenDestination(navigation.tab, index, screen)
        }
    }
    val motion = remember { PageMotion() }
    motion.rtl = LocalLayoutDirection.current == LayoutDirection.Rtl
    motion.density = LocalDensity.current.density
    // Pages are anchored to the screen edge, so their slide must not overshoot and open a gap:
    // Material's effects springs are critically damped, unlike its spatial ones.
    motion.offsetSpec = EdgeAnchoredMotion.defaultEffectsSpec()
    motion.floatSpec = EdgeAnchoredMotion.defaultEffectsSpec()
    // Entries are remembered per back stack; they read the latest content through this state.
    val latestContent = rememberUpdatedState(content)
    val entries = rememberDecoratedNavEntries(
        backStack = backStack,
        entryDecorators = listOf(rememberSaveableStateHolderNavEntryDecorator()),
        entryProvider = { destination ->
            NavEntry(
                key = destination,
                contentKey = destination.contentKey,
                metadata = mapOf(
                    PageMetadata.TAB to destination.tab,
                    PageMetadata.DEPTH to destination.index,
                ),
            ) {
                PageSurface(motion, destination.contentKey) {
                    RoutedPage(destination.screen, latestContent)
                }
            }
        },
    )
    val sceneStrategies = remember { listOf(SinglePaneSceneStrategy<ScreenDestination>()) }
    val sceneState = rememberSceneState(
        entries = entries,
        sceneStrategies = sceneStrategies,
        onBack = onBack,
    )
    val navigationEventState = rememberNavigationEventState(sceneState)
    val latestOnBack by rememberUpdatedState(onBack)
    val scope = rememberCoroutineScope()
    motion.scope = scope
    // Completion goes through PageMotion so a released predictive gesture can play its commit
    // animation before the stack is popped; a button or key back pops at once. The player above
    // the pages owns back while it is open.
    NavigationBackHandler(
        state = navigationEventState,
        isBackEnabled = backEnabled && sceneState.currentScene.previousEntries.isNotEmpty(),
        onBackCancelled = { motion.onCancelled() },
        onBackCompleted = { motion.onCompleted { latestOnBack() } },
    )
    // Gesture events feed the draw-phase state here, off composition.
    LaunchedEffect(navigationEventState, motion) {
        snapshotFlow { navigationEventState.transitionState }.collect { gesture ->
            if (gesture is NavigationEventTransitionState.InProgress) {
                motion.onGesture(gesture.latestEvent)
            } else {
                motion.onIdle()
            }
        }
    }
    NavDisplay(
        sceneState = sceneState,
        navigationEventState = navigationEventState,
        modifier = modifier.onSizeChanged {
            motion.widthPx = it.width
            motion.heightPx = it.height
        },
        transitionSpec = { motion.transform(initialState, targetState, swipeEdge = null) },
        popTransitionSpec = { motion.transform(initialState, targetState, swipeEdge = null) },
        predictivePopTransitionSpec = { edge -> motion.transform(initialState, targetState, edge) },
    )
}

/**
 * The routed screen itself. Its parameters only change when the screen or the content lambda
 * does, so transition and gesture frames skip it.
 */
@Composable
private fun RoutedPage(screen: Screen, content: State<@Composable (Screen) -> Unit>) {
    NocturnBackdrop { content.value(screen) }
}
