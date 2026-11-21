@file:OptIn(
    androidx.compose.material3.ExperimentalMaterial3Api::class,
    androidx.compose.material3.ExperimentalMaterial3ExpressiveApi::class,
)

package dev.nocturn.app.ui

import androidx.activity.compose.BackHandler
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.spring
import androidx.compose.animation.expandVertically
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.shrinkVertically
import androidx.compose.animation.slideInVertically
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.asPaddingValues
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBars
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.windowInsetsBottomHeight
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.layout.windowInsetsPadding
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Headphones
import androidx.compose.material.icons.rounded.Home
import androidx.compose.material.icons.rounded.LibraryMusic
import androidx.compose.material.icons.rounded.Search
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.NavigationRail
import androidx.compose.material3.NavigationRailItem
import androidx.compose.material3.Scaffold
import androidx.compose.material3.ShortNavigationBar
import androidx.compose.material3.ShortNavigationBarItem
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.derivedStateOf
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.RectangleShape
import androidx.compose.ui.layout.boundsInRoot
import androidx.compose.ui.layout.onGloballyPositioned
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.compose.dropUnlessResumed
import kotlinx.coroutines.flow.filter
import kotlinx.coroutines.flow.map
import dev.nocturn.app.NocturnViewModel
import dev.nocturn.app.ui.adaptive.LocalAppPlatform
import dev.nocturn.app.ui.adaptive.LocalDesktopUiEnabled
import dev.nocturn.app.ui.adaptive.LocalWindowWidthClass
import dev.nocturn.app.ui.adaptive.WindowWidthClass
import dev.nocturn.app.ui.adaptive.shouldUseDesktopUi
import dev.nocturn.app.ui.adaptive.windowWidthClassOf
import dev.nocturn.app.ui.desktop.DesktopScaffold
import dev.nocturn.app.ui.components.FreeListenDialog
import dev.nocturn.app.ui.components.GlassMaterials
import dev.nocturn.app.ui.components.LocalBottomChromePadding
import dev.nocturn.app.ui.components.LocalHapticsEnabled
import dev.nocturn.app.ui.components.LocalShellBackdrop
import dev.nocturn.app.ui.components.blurAvailable
import dev.nocturn.app.ui.components.glass
import dev.nocturn.app.ui.components.rememberHaptics
import dev.nocturn.app.ui.screens.home.DailySongsScreen
import dev.nocturn.app.ui.screens.home.HomeScreen
import dev.nocturn.app.ui.screens.library.LibraryScreen
import dev.nocturn.app.ui.screens.library.RecentSongsScreen
import dev.nocturn.app.ui.screens.library.LikedSongsScreen
import dev.nocturn.app.ui.screens.library.LoginSheet
import dev.nocturn.app.ui.screens.player.MiniPlayer
import dev.nocturn.app.ui.screens.player.PlayerScreen
import dev.nocturn.app.ui.screens.player.PlayerSheetDrag
import dev.nocturn.app.ui.screens.player.PlayerQueueSheet
import dev.nocturn.app.ui.screens.playlist.AlbumScreen
import dev.nocturn.app.ui.screens.playlist.ArtistScreen
import dev.nocturn.app.ui.screens.playlist.PlaylistScreen
import dev.nocturn.app.ui.screens.search.SearchScreen
import dev.nocturn.app.ui.screens.settings.SettingsAboutScreen
import dev.nocturn.app.ui.screens.settings.SettingsAppearanceScreen
import dev.nocturn.app.ui.screens.settings.SettingsGeneralScreen
import dev.nocturn.app.ui.screens.settings.SettingsPlaybackScreen
import dev.nocturn.app.ui.screens.settings.SettingsScreen
import dev.nocturn.app.ui.screens.settings.SettingsStorageScreen
import dev.nocturn.app.ui.state.AlbumUiState
import dev.nocturn.app.ui.state.AppTab
import dev.nocturn.app.ui.state.ArtistUiState
import dev.nocturn.app.ui.state.MiniPlayerUiState
import dev.nocturn.app.ui.state.PlaylistUiState
import dev.nocturn.app.ui.state.Screen
import dev.nocturn.app.ui.theme.NocturnTheme
import top.yukonga.miuix.kmp.blur.layerBackdrop
import top.yukonga.miuix.kmp.blur.rememberLayerBackdrop

/**
 * Root of the Material 3 Expressive client. The shell owns navigation chrome (bottom navigation,
 * mini player, now-playing overlay, login sheet, snackbars); each screen owns its scroll surface
 * and top app bar.
 */
@Composable
fun NocturnApp(model: NocturnViewModel) {
    // The shell reads only what it lays out. Playback ticks (player, mini player) and per-screen
    // state are collected inside the surfaces that draw them, so a position update or a loaded
    // page never recomposes the navigation hosts or the other routed pages.
    val navigation by model.navigation.collectAsStateWithLifecycle()
    val library by model.library.collectAsStateWithLifecycle()
    // Derived reads: the shell recomposes only when the derived value flips, not on every emission.
    val playerState = model.player.collectAsStateWithLifecycle()
    val playerVisible by remember { derivedStateOf { playerState.value.visible } }
    val searchState = model.search.collectAsStateWithLifecycle()
    val searchBackActive by remember {
        derivedStateOf { searchState.value.let { it.expanded || it.submittedKeyword != null } }
    }
    val snackbarHostState = remember { SnackbarHostState() }
    var currentBottomPadding by remember { mutableStateOf(0.dp) }
    val playerSheet = rememberPlayerSheetState()
    val routedContent: @Composable (Screen) -> Unit = remember(model) {
        { screen -> ScreenContent(screen = screen, model = model) }
    }

    LaunchedEffect(model) {
        model.messages.collect { message ->
            snackbarHostState.currentSnackbarData?.dismiss()
            snackbarHostState.showSnackbar(message)
        }
    }

    // Search keeps its own two-level back (close the expanded bar, then clear the results) so a
    // back gesture on the results page returns to the search landing instead of leaving the app.
    // Higher-priority handlers (navigation stack, player) are composed after this one.
    BackHandler(
        enabled = navigation.isTabRoot && navigation.tab == AppTab.Search && !playerVisible &&
            searchBackActive,
    ) {
        model.onSearchBack()
    }

    NocturnTheme(
        themeMode = library.themeMode,
        colorSchemeKey = library.colorSchemeKey,
        customSeedColor = library.customSeedColor,
        dynamicVariant = library.dynamicVariant,
    ) {
        // Routed pages are recorded once into a layer; the floating chrome blurs that capture.
        val pageBackdrop = if (blurAvailable) rememberLayerBackdrop() else null
        // The global tactile switch: every `rememberHaptics()` below this point reads it, so the
        // preference silences the whole app's haptics without each surface checking it.
        CompositionLocalProvider(
            LocalHapticsEnabled provides library.hapticsEnabled,
            LocalShellBackdrop provides pageBackdrop,
        ) {
            // The window surface is visible underneath the library's predictive back transition.
            BoxWithConstraints(
                Modifier
                    .fillMaxSize()
                    .background(MaterialTheme.colorScheme.surfaceContainerLowest),
            ) {
                val widthClass = windowWidthClassOf(maxWidth)
                val isWide = widthClass.isAtLeastMedium
                val platform = LocalAppPlatform.current
                val desktopUi = LocalDesktopUiEnabled.current ?: shouldUseDesktopUi(platform, widthClass)
                CompositionLocalProvider(LocalWindowWidthClass provides widthClass) {
                    PlayerNavigationHost(
                        visible = playerVisible,
                        onCollapse = model::collapsePlayer,
                        onExpand = model::openPlayer,
                        state = playerSheet,
                        shell = {
                            if (desktopUi) {
                                var desktopLyricsOpen by rememberSaveable { mutableStateOf(false) }
                                var desktopQueueOpen by rememberSaveable { mutableStateOf(false) }
                                var desktopVolume by rememberSaveable { mutableFloatStateOf(1f) }

                                DesktopScaffold(
                                    selectedTab = navigation.tab,
                                    onSelectTab = model::selectTab,
                                    onOpenLikedSongs = { model.openLikedSongs() },
                                    onOpenRecentSongs = { model.openRecentSongs() },
                                    onOpenSettings = { model.openSettings() },
                                    onOpenLogin = { model.startLogin() },
                                    userProfile = library.profile,
                                    playerState = playerState.value,
                                    onTogglePlay = model::togglePlayback,
                                    onPrevious = model::previous,
                                    onNext = model::next,
                                    onSeek = model::seek,
                                    onToggleLike = model::toggleLikeCurrent,
                                    onToggleShuffle = model::toggleShuffle,
                                    onCycleRepeat = model::cycleRepeatMode,
                                    onOpenPlayer = model::openPlayer,
                                    lyricsPanelOpen = desktopLyricsOpen,
                                    onToggleLyricsPanel = { desktopLyricsOpen = !desktopLyricsOpen },
                                    queuePanelOpen = desktopQueueOpen,
                                    onToggleQueuePanel = { desktopQueueOpen = !desktopQueueOpen },
                                    volume = desktopVolume,
                                    onVolumeChange = { desktopVolume = it },
                                    isCurrentScreenSettings = navigation.current is Screen.Settings ||
                                        navigation.current is Screen.SettingsPlayback ||
                                        navigation.current is Screen.SettingsAppearance ||
                                        navigation.current is Screen.SettingsGeneral ||
                                        navigation.current is Screen.SettingsStorage ||
                                        navigation.current is Screen.SettingsAbout,
                                ) {
                                    CompositionLocalProvider(
                                        LocalBottomChromePadding provides 0.dp,
                                    ) {
                                        ScreenNavigationHost(
                                            navigation = navigation,
                                            backEnabled = !playerVisible,
                                            onBack = { model.back() },
                                            modifier = if (pageBackdrop != null) Modifier.layerBackdrop(pageBackdrop) else Modifier,
                                            content = routedContent,
                                        )
                                    }
                                    if (desktopQueueOpen) {
                                        PlayerQueueSheet(
                                            state = playerState.value,
                                            onDismiss = { desktopQueueOpen = false },
                                            onSongClick = model::playQueueIndex,
                                        )
                                    }
                                }
                            } else if (isWide) {
                                Row(Modifier.fillMaxSize()) {
                                    ClientNavigationRail(
                                        selectedTab = navigation.tab,
                                        onSelect = model::selectTab,
                                    )
                                    Box(
                                        Modifier
                                            .weight(1f)
                                            .fillMaxHeight(),
                                    ) {
                                        val miniPlayer by model.miniPlayer.collectAsStateWithLifecycle()
                                        val hasSong = miniPlayer.song != null
                                        val bottomPadding = if (hasSong) 92.dp else (WindowInsets.navigationBars.asPaddingValues().calculateBottomPadding() + 16.dp)
                                        currentBottomPadding = bottomPadding

                                        CompositionLocalProvider(
                                            LocalBottomChromePadding provides bottomPadding,
                                        ) {
                                            ScreenNavigationHost(
                                                navigation = navigation,
                                                backEnabled = !playerVisible,
                                                onBack = { model.back() },
                                                modifier = if (pageBackdrop != null) Modifier.layerBackdrop(pageBackdrop) else Modifier,
                                                content = routedContent,
                                            )
                                        }

                                        if (hasSong) {
                                            Box(
                                                modifier = Modifier
                                                    .fillMaxWidth()
                                                    .align(Alignment.BottomCenter)
                                                    .padding(horizontal = 24.dp, vertical = 12.dp)
                                                    .windowInsetsPadding(WindowInsets.navigationBars),
                                                contentAlignment = Alignment.Center,
                                            ) {
                                                MiniPlayer(
                                                    state = miniPlayer,
                                                    modifier = Modifier
                                                        .widthIn(max = 760.dp)
                                                        .fillMaxWidth()
                                                        .then(
                                                            Modifier.onGloballyPositioned {
                                                                playerSheet.miniBounds = it.boundsInRoot()
                                                            },
                                                        ),
                                                    onExpand = model::openPlayer,
                                                    onTogglePlay = model::togglePlayback,
                                                    onNext = model::next,
                                                    onPrevious = model::previous,
                                                    sheetDrag = playerSheet,
                                                )
                                            }
                                        }
                                    }
                                }
                            } else {
                                Scaffold(
                                    containerColor = Color.Transparent,
                                    contentWindowInsets = WindowInsets(0, 0, 0, 0),

                                    bottomBar = {
                                        val miniPlayer by model.miniPlayer.collectAsStateWithLifecycle()
                                        BottomChrome(
                                            miniPlayer = miniPlayer,
                                            showNavigationBar = navigation.isTabRoot,
                                            selectedTab = navigation.tab,
                                            onSelectTab = model::selectTab,
                                            onExpandPlayer = model::openPlayer,
                                            onTogglePlay = model::togglePlayback,
                                            onNext = model::next,
                                            onPrevious = model::previous,
                                            sheetDrag = playerSheet,
                                            miniPlayerModifier = Modifier.onGloballyPositioned {
                                                playerSheet.miniBounds = it.boundsInRoot()
                                            },
                                        )
                                    },
                                ) { innerPadding ->
                                    // Screens draw edge-to-edge behind the floating chrome; scroll containers read
                                    // the chrome height from LocalBottomChromePadding for their content padding.
                                    currentBottomPadding = innerPadding.calculateBottomPadding()
                                    CompositionLocalProvider(
                                        LocalBottomChromePadding provides innerPadding.calculateBottomPadding(),
                                    ) {
                                        ScreenNavigationHost(
                                            navigation = navigation,
                                            backEnabled = !playerVisible,
                                            onBack = { model.back() },
                                            modifier = if (pageBackdrop != null) Modifier.layerBackdrop(pageBackdrop) else Modifier,
                                            content = routedContent,
                                        )
                                    }
                                }
                            }
                        },
                    player = {
                        val player by model.player.collectAsStateWithLifecycle()
                        PlayerScreen(
                            state = player,
                            onCollapse = model::collapsePlayer,
                            onTogglePlay = model::togglePlayback,
                            onNext = model::next,
                            onPrevious = model::previous,
                            onSeek = model::seek,
                            onToggleLike = model::toggleLikeCurrent,
                            onToggleShuffle = model::toggleShuffle,
                            onCycleRepeat = model::cycleRepeatMode,
                            onCyclePlaybackMode = model::cyclePlaybackMode,
                            onQueueSongClick = model::playQueueIndex,
                            sheetDrag = playerSheet,
                        )
                    },
                )
                LoginSheet(
                    state = library.login,
                    onClose = model::closeLogin,
                    onRefresh = model::refreshLoginQr,
                    onModeChange = model::selectLoginMode,
                    onSendCode = model::sendLoginCode,
                    onSubmitPhone = model::submitPhoneLogin,
                )
                SnackbarHost(
                    hostState = snackbarHostState,
                    modifier = Modifier
                        .align(Alignment.BottomCenter)
                        .padding(
                            bottom = if (playerVisible) {
                                WindowInsets.navigationBars.asPaddingValues().calculateBottomPadding() + 84.dp
                            } else {
                                currentBottomPadding
                            },
                        ),
                )
                val showFreeListenDialog by model.showFreeListenDialog.collectAsStateWithLifecycle()
                val freeListenRights by model.freeListenRights.collectAsStateWithLifecycle()
                val isFreeListenLoading by model.isFreeListenLoading.collectAsStateWithLifecycle()
                val autoClaimFreeListen by model.autoClaimFreeListen.collectAsStateWithLifecycle()
                FreeListenDialog(
                    visible = showFreeListenDialog,
                    rights = freeListenRights,
                    isLoading = isFreeListenLoading,
                    autoClaim = autoClaimFreeListen,
                    onAutoClaimChange = model::setAutoClaimFreeListen,
                    onClaim = model::claimFreeListen,
                    onDismiss = model::closeFreeListenDialog,
                )
            }
        }
    }
}
}

// ---------------------------------------------------------------------------------------------
// Bottom chrome
// ---------------------------------------------------------------------------------------------

/**
 * Floating mini player capsule above the navigation bar. The mini player uses its own miuix
 * glass capsule to float over the underlying page content without any backing blur strip.
 * The navigation bar below retains its frosted glass chrome. When the navigation bar hides
 * (detail routes) a spacer of the system navigation inset grows in its place so the mini player
 * stays clear of the gesture handle.
 */
@Composable
internal fun BottomChrome(
    miniPlayer: MiniPlayerUiState,
    showNavigationBar: Boolean,
    selectedTab: AppTab,
    onSelectTab: (AppTab) -> Unit,
    onExpandPlayer: () -> Unit,
    onTogglePlay: () -> Unit,
    onNext: () -> Unit,
    onPrevious: () -> Unit,
    sheetDrag: PlayerSheetDrag? = null,
    miniPlayerModifier: Modifier = Modifier,
) {
    val sizeSpec = spring<androidx.compose.ui.unit.IntSize>(
        dampingRatio = Spring.DampingRatioNoBouncy,
        stiffness = Spring.StiffnessMediumLow,
    )
    val hasSong = miniPlayer.song != null
    Column(
        Modifier.fillMaxWidth(),
    ) {
        AnimatedVisibility(
            visible = hasSong,
            enter = expandVertically(sizeSpec, expandFrom = Alignment.Top) +
                slideInVertically(spring(stiffness = Spring.StiffnessMediumLow)) { it / 2 } +
                fadeIn(),
            exit = shrinkVertically(sizeSpec, shrinkTowards = Alignment.Top) + fadeOut(),
        ) {
            MiniPlayer(
                state = miniPlayer,
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 12.dp, vertical = 6.dp)
                    .then(miniPlayerModifier),
                onExpand = onExpandPlayer,
                onTogglePlay = onTogglePlay,
                onNext = onNext,
                onPrevious = onPrevious,
                sheetDrag = sheetDrag,
            )
        }
        AnimatedVisibility(
            visible = showNavigationBar,
            enter = expandVertically(sizeSpec, expandFrom = Alignment.Top) + fadeIn(),
            exit = shrinkVertically(sizeSpec, shrinkTowards = Alignment.Top) + fadeOut(),
        ) {
            ClientNavigationBar(selectedTab = selectedTab, onSelect = onSelectTab)
        }
        // Mirrors the navigation bar with the same spring so the mini player glides down to the
        // gesture area instead of jumping when the bar collapses.
        AnimatedVisibility(
            visible = !showNavigationBar,
            enter = expandVertically(sizeSpec, expandFrom = Alignment.Top),
            exit = shrinkVertically(sizeSpec, shrinkTowards = Alignment.Top),
        ) {
            Spacer(
                Modifier
                    .fillMaxWidth()
                    .windowInsetsBottomHeight(WindowInsets.navigationBars),
            )
        }
    }
}

@Composable
private fun ScreenContent(
    screen: Screen,
    model: NocturnViewModel,
) {
    when (screen) {
        Screen.Home -> HomeRoute(model)
        Screen.Search -> SearchRoute(model)
        Screen.Library -> LibraryRoute(model)
        is Screen.Playlist -> PlaylistRoute(screen.id, model)
        is Screen.Album -> AlbumRoute(screen.id, model)
        is Screen.Artist -> ArtistRoute(screen.id, model)
        Screen.RecentSongs -> RecentSongsRoute(model)
        Screen.LikedSongs -> LikedSongsRoute(model)
        Screen.DailySongs -> DailySongsRoute(model)
        Screen.Settings -> SettingsRoute(model)
        Screen.SettingsPlayback -> SettingsPlaybackRoute(model)
        Screen.SettingsAppearance -> SettingsAppearanceRoute(model)
        Screen.SettingsGeneral -> SettingsGeneralRoute(model)
        Screen.SettingsStorage -> SettingsStorageRoute(model)
        Screen.SettingsAbout -> SettingsAboutRoute(model)
    }
}

@Composable
private fun HomeRoute(model: NocturnViewModel) {
    val home by model.home.collectAsStateWithLifecycle()
    val reselectFlow = remember(model) {
        model.tabReselectEvents.filter { it == AppTab.Home }.map { }
    }
    HomeScreen(
        state = home,
        onRefresh = model::refreshHome,
        onPlaylistClick = rememberResumedNavigation(model::openPlaylist),
        onChartClick = rememberResumedNavigation { toplist -> model.openPlaylist(toplist.id) },
        onSongClick = { song, songs ->
            model.playSongs(songs, songs.indexOf(song).coerceAtLeast(0))
        },
        onOpenDaily = dropUnlessResumed { model.openDailySongs() },
        onPlayAllDaily = { model.playAll(home.dailySongs) },
        onPlayPlaylist = model::playPlaylist,
        onRoamingClick = model::openRoaming,
        onSearchClick = dropUnlessResumed {
            model.selectTab(AppTab.Search)
            model.expandSearch()
        },
        onAccountClick = dropUnlessResumed { model.selectTab(AppTab.Library) },
        onFreeListenClick = model::openFreeListenDialog,
        reselectFlow = reselectFlow,
    )
}

@Composable
private fun SearchRoute(model: NocturnViewModel) {
    val search by model.search.collectAsStateWithLifecycle()
    SearchScreen(
        state = search,
        onQueryChange = model::updateSearchQuery,
        onSearch = { keyword -> model.submitSearch(keyword) },
        onSuggestionClick = { keyword -> model.submitSearch(keyword) },
        onHotClick = { keyword -> model.submitSearch(keyword) },
        onClearHistory = model::clearSearchHistory,
        onTabChange = model::selectSearchTab,
        onLoadMore = model::loadMoreSearch,
        onSongClick = { song, songs ->
            model.playSongs(songs, songs.indexOf(song).coerceAtLeast(0))
        },
        onLikeClick = model::toggleLike,
        onPlaylistClick = rememberResumedNavigation(model::openPlaylist),
        onAlbumClick = rememberResumedNavigation(model::openAlbum),
        onArtistClick = rememberResumedNavigation(model::openArtist),
        onBack = { model.onSearchBack() },
        onRetry = model::retrySearch,
        onExpand = model::expandSearch,
        onCollapse = model::collapseSearch,
    )
}

@Composable
private fun LibraryRoute(model: NocturnViewModel) {
    val library by model.library.collectAsStateWithLifecycle()
    val reselectFlow = remember(model) {
        model.tabReselectEvents.filter { it == AppTab.Library }.map { }
    }
    LibraryScreen(
        state = library,
        onLoginClick = model::startLogin,
        onLogout = model::logout,
        onLikedSongsClick = dropUnlessResumed { model.openLikedSongs() },
        onPlaylistClick = rememberResumedNavigation(model::openPlaylist),
        onHeartbeatClick = model::startHeartbeatMode,
        onRecentSongsClick = dropUnlessResumed { model.openRecentSongs() },
        onSettingsClick = dropUnlessResumed { model.openSettings() },
        onRetry = model::refreshLibrary,
        reselectFlow = reselectFlow,
    )
}

@Composable
private fun SettingsRoute(model: NocturnViewModel) {
    val settingsState by model.settingsUi.collectAsStateWithLifecycle()
    SettingsScreen(
        state = settingsState,
        onBack = { model.back() },
        onNavigatePlayback = dropUnlessResumed { model.openSettingsPlayback() },
        onNavigateAppearance = dropUnlessResumed { model.openSettingsAppearance() },
        onNavigateGeneral = dropUnlessResumed { model.openSettingsGeneral() },
        onNavigateStorage = dropUnlessResumed { model.openSettingsStorage() },
        onNavigateAbout = dropUnlessResumed { model.openSettingsAbout() },
    )
}

@Composable
private fun SettingsPlaybackRoute(model: NocturnViewModel) {
    val settingsState by model.settingsUi.collectAsStateWithLifecycle()
    SettingsPlaybackScreen(
        state = settingsState,
        onBack = { model.back() },
        onQualityChange = model::setQuality,
        onImmerseTypeChange = model::setImmerseType,
        onUnblockEnabledChange = model::setUnblockEnabled,
        onScrobbleEnabledChange = model::setScrobbleEnabled,
        onRelayPlayStateEnabledChange = model::setRelayPlayStateEnabled,
    )
}

@Composable
private fun SettingsAppearanceRoute(model: NocturnViewModel) {
    val settingsState by model.settingsUi.collectAsStateWithLifecycle()
    SettingsAppearanceScreen(
        state = settingsState,
        onBack = { model.back() },
        onThemeModeChange = model::setThemeMode,
        onColorSchemeChange = model::setColorScheme,
        onDynamicVariantChange = model::setDynamicVariant,
        onResetColorScheme = model::resetColorScheme,
        onDynamicColorChange = model::setDynamicColor,
    )
}

@Composable
private fun SettingsGeneralRoute(model: NocturnViewModel) {
    val settingsState by model.settingsUi.collectAsStateWithLifecycle()
    SettingsGeneralScreen(
        state = settingsState,
        onBack = { model.back() },
        onHapticsChange = model::setHapticsEnabled,
        onLyriconChange = model::setLyriconEnabled,
    )
}

@Composable
private fun SettingsStorageRoute(model: NocturnViewModel) {
    val settingsState by model.settingsUi.collectAsStateWithLifecycle()
    SettingsStorageScreen(
        state = settingsState,
        onBack = { model.back() },
        onClearSearchHistory = model::clearSearchHistory,
        onClearCache = model::clearCache,
    )
}

@Composable
private fun SettingsAboutRoute(model: NocturnViewModel) {
    val settingsState by model.settingsUi.collectAsStateWithLifecycle()
    SettingsAboutScreen(
        state = settingsState,
        onBack = { model.back() },
    )
}

// Each stack entry renders the state stored under its own id, so a page revealed by back (or
// previewed under a predictive back gesture) shows itself, not the last page loaded. Each route
// observes only its own entry of the map, so loading another page does not recompose it.

@Composable
private fun PlaylistRoute(id: Long, model: NocturnViewModel) {
    val all = model.playlists.collectAsStateWithLifecycle()
    val entry by remember(id) { derivedStateOf { all.value[id] } }
    PlaylistScreen(
        state = rememberLastNonNull(entry) ?: PlaylistUiState(playlistId = id, loading = true),
        onBack = { model.back() },
        onPlayAll = model::playAll,
        onSongClick = { song, songs ->
            model.playSongs(songs, songs.indexOf(song).coerceAtLeast(0))
        },
        onLikeClick = model::toggleLike,
        onLoadMore = { model.loadMorePlaylist(id) },
        onRetry = { model.loadPlaylist(id) },
        onSubscribeToggle = { model.togglePlaylistSubscription(id) },
        onSearchClick = dropUnlessResumed {
            model.selectTab(AppTab.Search)
            model.expandSearch()
        },
        onHeartbeatMode = {
            val detail = entry?.detail
            if (detail != null) {
                val summary = dev.nocturn.app.data.model.PlaylistSummary(
                    id = detail.id,
                    name = detail.name,
                    coverUrl = detail.coverUrl,
                    trackCount = detail.trackCount,
                    playCount = detail.playCount,
                    specialType = if (detail.name.contains("喜欢")) 5 else 0,
                )
                model.startHeartbeatMode(summary)
            }
        },
    )
}

@Composable
private fun AlbumRoute(id: Long, model: NocturnViewModel) {
    val all = model.albums.collectAsStateWithLifecycle()
    val entry by remember(id) { derivedStateOf { all.value[id] } }
    AlbumScreen(
        state = rememberLastNonNull(entry) ?: AlbumUiState(albumId = id, loading = true),
        onBack = { model.back() },
        onPlayAll = model::playAll,
        onSongClick = { song, songs ->
            model.playSongs(songs, songs.indexOf(song).coerceAtLeast(0))
        },
        onLikeClick = model::toggleLike,
        onArtistClick = rememberResumedNavigation(model::openArtist),
    )
}

@Composable
private fun ArtistRoute(id: Long, model: NocturnViewModel) {
    val all = model.artists.collectAsStateWithLifecycle()
    val entry by remember(id) { derivedStateOf { all.value[id] } }
    ArtistScreen(
        state = rememberLastNonNull(entry) ?: ArtistUiState(artistId = id, loading = true),
        onBack = { model.back() },
        onSongClick = { song, songs ->
            model.playSongs(songs, songs.indexOf(song).coerceAtLeast(0))
        },
        onLikeClick = model::toggleLike,
        onAlbumClick = rememberResumedNavigation(model::openAlbum),
        onPlayAll = model::playAll,
    )
}

@Composable
private fun RecentSongsRoute(model: NocturnViewModel) {
    val library by model.library.collectAsStateWithLifecycle()
    RecentSongsScreen(
        state = library,
        onBack = { model.back() },
        onPlayAll = { model.playAll(library.recentSongs) },
        onSongClick = { song, songs ->
            model.playSongs(songs, songs.indexOf(song).coerceAtLeast(0))
        },
        onLikeClick = model::toggleLike,
    )
}

@Composable
private fun LikedSongsRoute(model: NocturnViewModel) {
    val likedSongs by model.likedSongs.collectAsStateWithLifecycle()
    LikedSongsScreen(
        state = likedSongs,
        onBack = { model.back() },
        onPlayAll = { model.playAll(likedSongs.songs) },
        onSongClick = { song, songs ->
            model.playSongs(songs, songs.indexOf(song).coerceAtLeast(0))
        },
        onLikeClick = model::toggleLike,
    )
}

@Composable
private fun DailySongsRoute(model: NocturnViewModel) {
    val home by model.home.collectAsStateWithLifecycle()
    DailySongsScreen(
        state = home,
        onBack = { model.back() },
        onPlayAll = { model.playAll(home.dailySongs) },
        onSongClick = { song, songs ->
            model.playSongs(songs, songs.indexOf(song).coerceAtLeast(0))
        },
        onLikeClick = model::toggleLike,
    )
}

/**
 * Returns [value], or the last non-null value seen at this call site once it turns null. A detail
 * page whose state was pruned while it is still animating out keeps showing its content.
 */
@Composable
private fun <T : Any> rememberLastNonNull(value: T?): T? {
    val holder = remember { LastValue<T>() }
    if (value != null) holder.value = value
    return holder.value
}

private class LastValue<T : Any> {
    var value: T? = null
}

@Composable
private fun ClientNavigationBar(
    selectedTab: AppTab,
    onSelect: (AppTab) -> Unit,
    modifier: Modifier = Modifier,
) {
    val haptics = rememberHaptics()
    val backdrop = LocalShellBackdrop.current
    val material = GlassMaterials.chrome()
    ShortNavigationBar(
        modifier = modifier.glass(backdrop, RectangleShape, material),
        containerColor = Color.Transparent,
    ) {
        ShortNavigationBarItem(
            selected = selectedTab == AppTab.Home,
            onClick = {
                haptics.step()
                onSelect(AppTab.Home)
            },
            icon = { Icon(Icons.Rounded.Home, contentDescription = null) },
            label = { Text("首页") },
        )
        ShortNavigationBarItem(
            selected = selectedTab == AppTab.Search,
            onClick = {
                haptics.step()
                onSelect(AppTab.Search)
            },
            icon = { Icon(Icons.Rounded.Search, contentDescription = null) },
            label = { Text("搜索") },
        )
        ShortNavigationBarItem(
            selected = selectedTab == AppTab.Library,
            onClick = {
                haptics.step()
                onSelect(AppTab.Library)
            },
            icon = { Icon(Icons.Rounded.LibraryMusic, contentDescription = null) },
            label = { Text("我的") },
        )
    }
}

@Composable
private fun ClientNavigationRail(
    selectedTab: AppTab,
    onSelect: (AppTab) -> Unit,
    modifier: Modifier = Modifier,
) {
    val haptics = rememberHaptics()
    NavigationRail(
        modifier = modifier
            .fillMaxHeight()
            .testTag("client-navigation-rail"),
        containerColor = MaterialTheme.colorScheme.surfaceContainerLow,
        contentColor = MaterialTheme.colorScheme.onSurface,
        header = {
            Box(
                modifier = Modifier
                    .padding(top = 16.dp, bottom = 12.dp)
                    .size(44.dp)
                    .clip(MaterialTheme.shapes.medium)
                    .background(MaterialTheme.colorScheme.primaryContainer),
                contentAlignment = Alignment.Center,
            ) {
                Icon(
                    imageVector = Icons.Rounded.Headphones,
                    contentDescription = "Nocturn",
                    tint = MaterialTheme.colorScheme.onPrimaryContainer,
                    modifier = Modifier.size(24.dp),
                )
            }
        },
    ) {
        Spacer(Modifier.height(12.dp))
        NavigationRailItem(
            selected = selectedTab == AppTab.Home,
            onClick = {
                haptics.step()
                onSelect(AppTab.Home)
            },
            icon = { Icon(Icons.Rounded.Home, contentDescription = null) },
            label = { Text("首页") },
            modifier = Modifier.testTag("nav-rail-home"),
        )
        Spacer(Modifier.height(6.dp))
        NavigationRailItem(
            selected = selectedTab == AppTab.Search,
            onClick = {
                haptics.step()
                onSelect(AppTab.Search)
            },
            icon = { Icon(Icons.Rounded.Search, contentDescription = null) },
            label = { Text("搜索") },
            modifier = Modifier.testTag("nav-rail-search"),
        )
        Spacer(Modifier.height(6.dp))
        NavigationRailItem(
            selected = selectedTab == AppTab.Library,
            onClick = {
                haptics.step()
                onSelect(AppTab.Library)
            },
            icon = { Icon(Icons.Rounded.LibraryMusic, contentDescription = null) },
            label = { Text("我的") },
            modifier = Modifier.testTag("nav-rail-library"),
        )
    }
}
