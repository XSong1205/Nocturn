package dev.nocturn.app.ui.state

/** Bottom navigation destinations. */
enum class AppTab { Home, Search, Library }

/**
 * A picked destination. Pushed routes are stored on a back stack above the selected tab;
 * the tab screens themselves never enter the stack so the bottom bar stays stable.
 */
sealed interface Screen {
    data object Home : Screen
    data object Search : Screen
    data object Library : Screen
    data class Playlist(val id: Long) : Screen
    data class Album(val id: Long) : Screen
    data class Artist(val id: Long) : Screen
    data object RecentSongs : Screen
    data object LikedSongs : Screen
    data object DailySongs : Screen
    data object Settings : Screen
    data object SettingsPlayback : Screen
    data object SettingsAppearance : Screen
    data object SettingsGeneral : Screen
    data object SettingsStorage : Screen
    data object SettingsAbout : Screen
}

data class NavState(
    val tab: AppTab = AppTab.Home,
    val stacks: Map<AppTab, List<Screen>> = emptyMap(),
) {
    /** The back stack for the currently selected tab. */
    val stack: List<Screen>
        get() = stacks[tab] ?: emptyList()

    /** All screens across all tab stacks. */
    val allScreens: List<Screen>
        get() = stacks.values.flatten()

    /** Backward-compatible constructor for a single stack. */
    constructor(tab: AppTab = AppTab.Home, stack: List<Screen>) : this(
        tab = tab,
        stacks = if (stack.isEmpty()) emptyMap() else mapOf(tab to stack),
    )

    /** Backward-compatible copy overload for mutating the current tab's stack. */
    fun copy(stack: List<Screen>): NavState =
        copy(stacks = if (stack.isEmpty()) stacks - tab else stacks + (tab to stack))

    /** Backward-compatible copy overload specifying both tab and stack. */
    fun copy(tab: AppTab, stack: List<Screen>): NavState {
        val updatedStacks = if (stack.isEmpty()) {
            stacks - tab
        } else {
            stacks + (tab to stack)
        }
        return copy(tab = tab, stacks = updatedStacks)
    }

    val current: Screen
        get() = stack.lastOrNull() ?: when (tab) {
            AppTab.Home -> Screen.Home
            AppTab.Search -> Screen.Search
            AppTab.Library -> Screen.Library
        }

    val isTabRoot: Boolean get() = stack.isEmpty()

    /** The destination a back gesture reveals, or null at a tab root. */
    val previous: Screen?
        get() = when {
            stack.isEmpty() -> null
            stack.size == 1 -> tabRoot(tab)
            else -> stack[stack.size - 2]
        }
}

fun tabRoot(tab: AppTab): Screen = when (tab) {
    AppTab.Home -> Screen.Home
    AppTab.Search -> Screen.Search
    AppTab.Library -> Screen.Library
}

val Screen.isTabRoot: Boolean
    get() = this == Screen.Home || this == Screen.Search || this == Screen.Library

/** Which navigation destinations should be visible under the current route. */
data class AppChromeState(
    val playerExpanded: Boolean = false,
    val showNavigationBar: Boolean = true,
)
