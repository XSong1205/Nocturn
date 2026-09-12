package com.nocturn.music.ui.navigation

import androidx.activity.compose.BackHandler
import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.spring
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.slideInHorizontally
import androidx.compose.animation.slideInVertically
import androidx.compose.animation.slideOutHorizontally
import androidx.compose.animation.slideOutVertically
import androidx.compose.animation.togetherWith
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.compositionLocalOf
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.RectangleShape
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.unit.dp
import com.nocturn.music.data.repository.SettingsRepository
import com.nocturn.music.ui.components.MiniPlayerBar
import com.nocturn.music.ui.screens.AboutScreen
import com.nocturn.music.ui.screens.HomeScreen
import com.nocturn.music.ui.screens.MyScreen
import com.nocturn.music.ui.screens.PlayerScreen
import com.nocturn.music.ui.screens.PlaylistDetailScreen
import com.nocturn.music.ui.screens.QueueSheet
import com.nocturn.music.ui.screens.SearchScreen
import com.nocturn.music.ui.screens.SettingsScreen
import com.nocturn.music.ui.screens.TopChartsSquareScreen
import com.nocturn.music.ui.screens.oobe.OobeScreen
import kotlinx.serialization.Serializable
import top.yukonga.miuix.kmp.basic.NavigationBar
import top.yukonga.miuix.kmp.basic.NavigationBarItem
import top.yukonga.miuix.kmp.basic.Scaffold
import top.yukonga.miuix.kmp.basic.TopAppBar
import top.yukonga.miuix.kmp.blur.BlendColorEntry
import top.yukonga.miuix.kmp.blur.BlurDefaults
import top.yukonga.miuix.kmp.blur.ProgressiveBlur
import top.yukonga.miuix.kmp.blur.isRuntimeShaderSupported
import top.yukonga.miuix.kmp.blur.layerBackdrop
import top.yukonga.miuix.kmp.blur.progressiveTextureBlur
import top.yukonga.miuix.kmp.blur.rememberLayerBackdrop
import top.yukonga.miuix.kmp.blur.textureBlur
import top.yukonga.miuix.kmp.icon.MiuixIcons
import top.yukonga.miuix.kmp.icon.extended.Home
import top.yukonga.miuix.kmp.icon.extended.Music
import top.yukonga.miuix.kmp.icon.extended.Search
import top.yukonga.miuix.kmp.icon.extended.Settings
import top.yukonga.miuix.kmp.nav.core.NavDisplay
import top.yukonga.miuix.kmp.nav.core.NavKey
import top.yukonga.miuix.kmp.nav.core.rememberNavBackStack
import top.yukonga.miuix.kmp.nav.transition.NavSwipeDirection
import top.yukonga.miuix.kmp.nav.transition.NavTransitions
import top.yukonga.miuix.kmp.theme.MiuixTheme

val LocalBottomBarPadding = compositionLocalOf { 0.dp }
val LocalTopBarPadding = compositionLocalOf { 0.dp }

enum class NavigationTab(val label: String) {
    Discover("发现"),
    Search("搜索"),
    My("我的"),
    Settings("设置")
}

val NavigationTab.icon: ImageVector
    get() = when (this) {
        NavigationTab.Discover -> MiuixIcons.Home
        NavigationTab.Search -> MiuixIcons.Search
        NavigationTab.My -> MiuixIcons.Music
        NavigationTab.Settings -> MiuixIcons.Settings
    }

@Serializable
sealed interface ScreenRoute : NavKey {
    @Serializable
    data object Main : ScreenRoute

    @Serializable
    data class Playlist(
        val id: Long,
        val initialName: String? = null,
        val initialCoverUrl: String? = null
    ) : ScreenRoute

    @Serializable
    data class Album(
        val id: Long,
        val initialName: String? = null,
        val initialCoverUrl: String? = null
    ) : ScreenRoute

    @Serializable
    data class Artist(
        val id: Long,
        val name: String,
        val avatarUrl: String? = null
    ) : ScreenRoute

    @Serializable
    data object Favorites : ScreenRoute

    @Serializable
    data object DailyRecommend : ScreenRoute

    @Serializable
    data object TopChartsSquare : ScreenRoute

    @Serializable
    data object About : ScreenRoute
}

fun SecondaryRoute.toScreenRoute(): ScreenRoute = when (this) {
    is SecondaryRoute.Playlist -> ScreenRoute.Playlist(id, initialName, initialCoverUrl)
    is SecondaryRoute.Album -> ScreenRoute.Album(id, initialName, initialCoverUrl)
    is SecondaryRoute.Artist -> ScreenRoute.Artist(id, name, avatarUrl)
    is SecondaryRoute.Favorites -> ScreenRoute.Favorites
    is SecondaryRoute.DailyRecommend -> ScreenRoute.DailyRecommend
    is SecondaryRoute.TopChartsSquare -> ScreenRoute.TopChartsSquare
    is SecondaryRoute.About -> ScreenRoute.About
}

fun ScreenRoute.toSecondaryRoute(): SecondaryRoute = when (this) {
    is ScreenRoute.Playlist -> SecondaryRoute.Playlist(id, initialName, initialCoverUrl)
    is ScreenRoute.Album -> SecondaryRoute.Album(id, initialName, initialCoverUrl)
    is ScreenRoute.Artist -> SecondaryRoute.Artist(id, name, avatarUrl)
    is ScreenRoute.Favorites -> SecondaryRoute.Favorites
    is ScreenRoute.DailyRecommend -> SecondaryRoute.DailyRecommend
    is ScreenRoute.TopChartsSquare -> SecondaryRoute.TopChartsSquare
    is ScreenRoute.About -> SecondaryRoute.About
    ScreenRoute.Main -> SecondaryRoute.About
}

@Composable
fun AppNavigation() {
    val isOobeCompleted by SettingsRepository.isOobeCompleted.collectAsState()
    val isBlurConfigured by SettingsRepository.isBlurEnabled.collectAsState()
    val blurActive = isRuntimeShaderSupported() && isBlurConfigured

    var currentTab by remember { mutableStateOf(NavigationTab.Discover) }
    var isPlayerExpanded by remember { mutableStateOf(false) }
    var isQueueOpen by remember { mutableStateOf(false) }

    // 使用 miuix-nav 官方导航栈管理器管理全应用路由
    val backStack = rememberNavBackStack<ScreenRoute>(ScreenRoute.Main)

    val surfaceColor = MiuixTheme.colorScheme.surface
    val backdrop = rememberLayerBackdrop {
        drawRect(surfaceColor)
        drawContent()
    }

    // 统一处理系统与手势返回逻辑
    BackHandler(enabled = isPlayerExpanded || isQueueOpen || backStack.size > 1) {
        when {
            isQueueOpen -> isQueueOpen = false
            isPlayerExpanded -> isPlayerExpanded = false
            backStack.size > 1 -> backStack.removeLastOrNull()
        }
    }

    Box(modifier = Modifier.fillMaxSize()) {
        // 使用 miuix-nav 官方 NavDisplay 实现 HyperOS 连续景深平滑转场与边缘滑动返回
        NavDisplay(
            backStack = backStack,
            transition = NavTransitions.MiuixDefault,
            onBack = { backStack.removeLastOrNull() }
        ) {
            // 一级主界面：包含顶部 Tab 与底部 NavigationBar、MiniPlayerBar
            entry<ScreenRoute.Main>(swipeDismiss = NavSwipeDirection.None) {
                Scaffold(
                    topBar = {
                        val topBarColor = if (blurActive) Color.Transparent else surfaceColor
                        Box(
                            modifier = Modifier
                                .fillMaxWidth()
                                .then(
                                    if (blurActive) {
                                        Modifier.progressiveTextureBlur(
                                            backdrop = backdrop,
                                            shape = RectangleShape,
                                            blurRadius = 24f,
                                            gradient = ProgressiveBlur.Top,
                                            colors = BlurDefaults.blurColors(
                                                blendColors = listOf(
                                                    BlendColorEntry(color = surfaceColor.copy(alpha = 0.85f))
                                                )
                                            )
                                        )
                                    } else {
                                        Modifier.background(surfaceColor)
                                    }
                                )
                        ) {
                            TopAppBar(
                                color = topBarColor,
                                title = when (currentTab) {
                                    NavigationTab.Discover -> "Nocturn 发现"
                                    NavigationTab.Search -> "搜索音乐"
                                    NavigationTab.My -> "我的音乐"
                                    NavigationTab.Settings -> "偏好设置"
                                }
                            )
                        }
                    },
                    bottomBar = {
                        Column(modifier = Modifier.fillMaxWidth()) {
                            MiniPlayerBar(
                                onBarClick = { isPlayerExpanded = true },
                                onQueueClick = { isQueueOpen = true },
                                backdrop = backdrop
                            )

                            Box(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .background(surfaceColor)
                            ) {
                                NavigationBar(
                                    color = surfaceColor
                                ) {
                                    NavigationTab.values().forEach { tab ->
                                        NavigationBarItem(
                                            selected = currentTab == tab,
                                            onClick = {
                                                currentTab = tab
                                            },
                                            icon = tab.icon,
                                            label = tab.label
                                        )
                                    }
                                }
                            }
                        }
                    },
                    modifier = Modifier.fillMaxSize()
                ) { innerPadding ->
                    val bottomBarPadding = innerPadding.calculateBottomPadding()
                    val topBarPadding = innerPadding.calculateTopPadding()

                    CompositionLocalProvider(
                        LocalBottomBarPadding provides bottomBarPadding,
                        LocalTopBarPadding provides (if (blurActive) topBarPadding else 0.dp)
                    ) {
                        Box(
                            modifier = Modifier
                                .fillMaxSize()
                                .layerBackdrop(backdrop)
                        ) {
                            Box(
                                modifier = Modifier
                                    .fillMaxSize()
                                    .padding(
                                        top = if (blurActive) 0.dp else topBarPadding,
                                        bottom = if (blurActive) 0.dp else bottomBarPadding
                                    )
                            ) {
                                AnimatedContent(
                                    targetState = currentTab,
                                    transitionSpec = {
                                        if (targetState.ordinal > initialState.ordinal) {
                                            (slideInHorizontally(tween(300, easing = FastOutSlowInEasing)) { it / 3 } + fadeIn(tween(240)))
                                                .togetherWith(slideOutHorizontally(tween(300, easing = FastOutSlowInEasing)) { -it / 3 } + fadeOut(tween(180)))
                                        } else {
                                            (slideInHorizontally(tween(300, easing = FastOutSlowInEasing)) { -it / 3 } + fadeIn(tween(240)))
                                                .togetherWith(slideOutHorizontally(tween(300, easing = FastOutSlowInEasing)) { it / 3 } + fadeOut(tween(180)))
                                        }
                                    },
                                    label = "tab-transition"
                                ) { tab ->
                                    when (tab) {
                                        NavigationTab.Discover -> HomeScreen(
                                            onOpenRoute = { backStack.add(it.toScreenRoute()) },
                                            onNavigateToSearch = { currentTab = NavigationTab.Search }
                                        )
                                        NavigationTab.Search -> SearchScreen(
                                            onOpenRoute = { backStack.add(it.toScreenRoute()) }
                                        )
                                        NavigationTab.My -> MyScreen(
                                            onNavigateToSettings = { currentTab = NavigationTab.Settings },
                                            onOpenRoute = { backStack.add(it.toScreenRoute()) }
                                        )
                                        NavigationTab.Settings -> SettingsScreen(
                                            onOpenRoute = { backStack.add(it.toScreenRoute()) }
                                        )
                                    }
                                }
                            }
                        }
                    }
                }
            }

            // 二级页面注册：开启 swipeDismiss = NavSwipeDirection.LeftToRight 支持跟手滑动返回
            entry<ScreenRoute.Playlist>(swipeDismiss = NavSwipeDirection.LeftToRight) { route ->
                PlaylistDetailScreen(
                    route = route.toSecondaryRoute(),
                    onBack = { backStack.removeLastOrNull() },
                    onNavigateToRoute = { backStack.add(it.toScreenRoute()) }
                )
            }

            entry<ScreenRoute.Album>(swipeDismiss = NavSwipeDirection.LeftToRight) { route ->
                PlaylistDetailScreen(
                    route = route.toSecondaryRoute(),
                    onBack = { backStack.removeLastOrNull() },
                    onNavigateToRoute = { backStack.add(it.toScreenRoute()) }
                )
            }

            entry<ScreenRoute.Artist>(swipeDismiss = NavSwipeDirection.LeftToRight) { route ->
                PlaylistDetailScreen(
                    route = route.toSecondaryRoute(),
                    onBack = { backStack.removeLastOrNull() },
                    onNavigateToRoute = { backStack.add(it.toScreenRoute()) }
                )
            }

            entry<ScreenRoute.Favorites>(swipeDismiss = NavSwipeDirection.LeftToRight) { route ->
                PlaylistDetailScreen(
                    route = route.toSecondaryRoute(),
                    onBack = { backStack.removeLastOrNull() },
                    onNavigateToRoute = { backStack.add(it.toScreenRoute()) }
                )
            }

            entry<ScreenRoute.DailyRecommend>(swipeDismiss = NavSwipeDirection.LeftToRight) { route ->
                PlaylistDetailScreen(
                    route = route.toSecondaryRoute(),
                    onBack = { backStack.removeLastOrNull() },
                    onNavigateToRoute = { backStack.add(it.toScreenRoute()) }
                )
            }

            entry<ScreenRoute.TopChartsSquare>(swipeDismiss = NavSwipeDirection.LeftToRight) {
                TopChartsSquareScreen(
                    onBack = { backStack.removeLastOrNull() },
                    onChartClick = { id, name, cover ->
                        backStack.add(ScreenRoute.Playlist(id, name, cover))
                    }
                )
            }

            entry<ScreenRoute.About>(swipeDismiss = NavSwipeDirection.LeftToRight) {
                AboutScreen(
                    onBack = { backStack.removeLastOrNull() }
                )
            }
        }

        // 全屏播放器：采用 HyperOS 弹性阻尼升起与收起动效
        AnimatedVisibility(
            visible = isPlayerExpanded,
            enter = slideInVertically(
                animationSpec = spring(
                    dampingRatio = 0.82f,
                    stiffness = Spring.StiffnessMediumLow
                )
            ) { it } + fadeIn(tween(240)),
            exit = slideOutVertically(
                animationSpec = spring(
                    dampingRatio = 0.90f,
                    stiffness = Spring.StiffnessMediumLow
                )
            ) { it } + fadeOut(tween(200))
        ) {
            PlayerScreen(
                onDismiss = { isPlayerExpanded = false },
                onOpenQueue = { isQueueOpen = true },
                onNavigateToRoute = { route ->
                    isPlayerExpanded = false
                    backStack.add(route.toScreenRoute())
                }
            )
        }

        // 播放队列弹窗
        QueueSheet(
            show = isQueueOpen,
            onDismissRequest = { isQueueOpen = false }
        )

        // 开机初始化向导 (OOBE)
        AnimatedVisibility(
            visible = !isOobeCompleted,
            enter = fadeIn(tween(260)),
            exit = fadeOut(tween(280)) + slideOutVertically(tween(280, easing = FastOutSlowInEasing)) { -it / 5 }
        ) {
            OobeScreen(
                onComplete = {
                    SettingsRepository.setOobeCompleted(true)
                }
            )
        }
    }
}
