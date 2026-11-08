package dev.nocturn.app.ui

import androidx.compose.runtime.Composable
import androidx.compose.runtime.State
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clipToBounds
import androidx.compose.ui.platform.LocalLayoutDirection
import androidx.compose.ui.unit.LayoutDirection
import androidx.navigation3.runtime.NavEntry
import androidx.navigation3.runtime.rememberDecoratedNavEntries
import androidx.navigation3.runtime.rememberSaveableStateHolderNavEntryDecorator
import androidx.navigation3.scene.SinglePaneSceneStrategy
import androidx.navigation3.scene.rememberNavigationEventState
import androidx.navigation3.scene.rememberSceneState
import androidx.navigation3.ui.NavDisplay
import androidx.navigationevent.compose.NavigationBackHandler
import dev.nocturn.app.ui.components.NocturnBackdrop
import dev.nocturn.app.ui.state.AppTab
import dev.nocturn.app.ui.state.NavState
import dev.nocturn.app.ui.state.Screen
import dev.nocturn.app.ui.state.tabRoot

private const val TAB_METADATA = "dev.nocturn.page.tab"

/** Stack position distinguishes repeated visits to the same album, artist or playlist. */
private data class ScreenDestination(val tab: AppTab, val index: Int, val screen: Screen) {
    val contentKey: String get() = "${tab.name}/$index/$screen"
}

/**
 * Navigation follows the Navigation 3 animations recipe: native enter/exit transforms for push,
 * pop and predictive pop. There is no separate gesture animation or delayed stack mutation.
 */
@Composable
internal fun ScreenNavigationHost(
    navigation: NavState,
    backEnabled: Boolean,
    onBack: () -> Unit,
    modifier: Modifier = Modifier,
    content: @Composable (Screen) -> Unit,
) {
    val backStack = remember(navigation.tab, navigation.stack) {
        (listOf(tabRoot(navigation.tab)) + navigation.stack).mapIndexed { index, screen ->
            ScreenDestination(navigation.tab, index, screen)
        }
    }
    val direction = if (LocalLayoutDirection.current == LayoutDirection.Rtl) -1 else 1
    val latestContent = rememberUpdatedState(content)
    val entries = rememberDecoratedNavEntries(
        backStack = backStack,
        entryDecorators = listOf(rememberSaveableStateHolderNavEntryDecorator()),
        entryProvider = { destination ->
            NavEntry(
                key = destination,
                contentKey = destination.contentKey,
                metadata = mapOf(TAB_METADATA to destination.tab),
            ) {
                RoutedPage(destination.screen, latestContent)
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
    // Use the explicit state overload only to disable page back while the player owns it.
    // NavDisplay handles seeking, cancellation, completion and entry lifecycle itself.
    NavigationBackHandler(
        state = navigationEventState,
        isBackEnabled = backEnabled && sceneState.currentScene.previousEntries.isNotEmpty(),
        onBackCompleted = { if (backEnabled && backStack.size > 1) onBack() },
    )
    NavDisplay(
        sceneState = sceneState,
        navigationEventState = navigationEventState,
        modifier = modifier,
        transitionSpec = {
            if (initialState.metadata[TAB_METADATA] != targetState.metadata[TAB_METADATA]) {
                PageTransitions.switchTab()
            } else {
                PageTransitions.push(direction)
            }
        },
        popTransitionSpec = {
            if (initialState.metadata[TAB_METADATA] != targetState.metadata[TAB_METADATA]) {
                PageTransitions.switchTab()
            } else {
                PageTransitions.pop(direction)
            }
        },
        // Keep the same transform after release so commit/cancel cannot reverse direction.
        predictivePopTransitionSpec = { PageTransitions.pop(direction) },
    )
}

/** Keep page content independent of the host's transition and gesture recompositions. */
@Composable
private fun RoutedPage(screen: Screen, content: State<@Composable (Screen) -> Unit>) {
    // Ambient circles extend beyond the page; keep them inside its sliding bounds.
    NocturnBackdrop(Modifier.clipToBounds()) { content.value(screen) }
}
