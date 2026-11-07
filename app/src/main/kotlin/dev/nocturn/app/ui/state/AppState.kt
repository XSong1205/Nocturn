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
    val stack: List<Screen> = emptyList(),
) {
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
