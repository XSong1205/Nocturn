package com.nocturn.music.ui.navigation

import androidx.activity.compose.BackHandler
import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.scaleIn
import androidx.compose.animation.scaleOut
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
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.vector.ImageVector
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
import top.yukonga.miuix.kmp.basic.NavigationBar
import top.yukonga.miuix.kmp.basic.NavigationBarItem
import top.yukonga.miuix.kmp.basic.Scaffold
import top.yukonga.miuix.kmp.basic.TopAppBar
import top.yukonga.miuix.kmp.icon.MiuixIcons
import top.yukonga.miuix.kmp.icon.extended.Home
import top.yukonga.miuix.kmp.icon.extended.Music
import top.yukonga.miuix.kmp.icon.extended.Search
import top.yukonga.miuix.kmp.icon.extended.Settings

import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.compositionLocalOf
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.RectangleShape
import androidx.compose.ui.unit.dp
import top.yukonga.miuix.kmp.blur.BlendColorEntry
import top.yukonga.miuix.kmp.blur.BlurDefaults
import top.yukonga.miuix.kmp.blur.isRuntimeShaderSupported
import top.yukonga.miuix.kmp.blur.layerBackdrop
import top.yukonga.miuix.kmp.blur.rememberLayerBackdrop
import top.yukonga.miuix.kmp.blur.textureBlur
import top.yukonga.miuix.kmp.theme.MiuixTheme

val LocalBottomBarPadding = compositionLocalOf { 0.dp }

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

@Composable
fun AppNavigation() {
    val isOobeCompleted by SettingsRepository.isOobeCompleted.collectAsState()
    var currentTab by remember { mutableStateOf(NavigationTab.Discover) }
    val secondaryStack = remember { mutableStateListOf<SecondaryRoute>() }
    var isPlayerExpanded by remember { mutableStateOf(false) }
    var isQueueOpen by remember { mutableStateOf(false) }

    val currentSecondary = secondaryStack.lastOrNull()

    val surfaceColor = MiuixTheme.colorScheme.surface
    val backdrop = rememberLayerBackdrop {
        drawRect(surfaceColor)
        drawContent()
    }
    val blurActive = isRuntimeShaderSupported()

    BackHandler(enabled = isPlayerExpanded || isQueueOpen || secondaryStack.isNotEmpty()) {
        when {
            isQueueOpen -> isQueueOpen = false
            isPlayerExpanded -> isPlayerExpanded = false
            secondaryStack.isNotEmpty() -> secondaryStack.removeLast()
        }
    }

    Box(modifier = Modifier.fillMaxSize()) {
        Scaffold(
            topBar = {
                if (currentSecondary == null) {
                    TopAppBar(
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

                    AnimatedVisibility(
                        visible = currentSecondary == null,
                        enter = slideInVertically(tween(250, easing = FastOutSlowInEasing)) { it } + fadeIn(tween(180)),
                        exit = slideOutVertically(tween(250, easing = FastOutSlowInEasing)) { it } + fadeOut(tween(150))
                    ) {
                        val navBarColor = if (blurActive) Color.Transparent else surfaceColor
                        Box(
                            modifier = Modifier
                                .then(
                                    if (blurActive) {
                                        Modifier.textureBlur(
                                            backdrop = backdrop,
                                            shape = RectangleShape,
                                            blurRadius = 25f,
                                            colors = BlurDefaults.blurColors(
                                                blendColors = listOf(
                                                    BlendColorEntry(color = surfaceColor.copy(alpha = 0.8f))
                                                )
                                            )
                                        )
                                    } else {
                                        Modifier
                                    }
                                )
                                .background(navBarColor)
                        ) {
                            NavigationBar(
                                color = navBarColor
                            ) {
                                NavigationTab.values().forEach { tab ->
                                    NavigationBarItem(
                                        selected = currentTab == tab && currentSecondary == null,
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
                }
            },
            modifier = Modifier.fillMaxSize()
        ) { innerPadding ->
            val bottomBarPadding = innerPadding.calculateBottomPadding()

            CompositionLocalProvider(LocalBottomBarPadding provides bottomBarPadding) {
                Box(
                    modifier = Modifier
                        .fillMaxSize()
                        .layerBackdrop(backdrop)
                ) {
                    // 主 Tab 内容区 (需要 innerPadding.calculateTopPadding() 避让顶部 TopAppBar；底部当 blurActive 时延伸到底栏下方实现毛玻璃透视)
                    Box(
                        modifier = Modifier
                            .fillMaxSize()
                            .padding(
                                top = innerPadding.calculateTopPadding(),
                                bottom = if (blurActive) 0.dp else bottomBarPadding
                            )
                    ) {
                        AnimatedContent(
                            targetState = currentTab,
                            transitionSpec = {
                                if (targetState.ordinal > initialState.ordinal) {
                                    (slideInHorizontally(tween(320, easing = FastOutSlowInEasing)) { it / 3 } + fadeIn(tween(250)))
                                        .togetherWith(slideOutHorizontally(tween(320, easing = FastOutSlowInEasing)) { -it / 3 } + fadeOut(tween(200)))
                                } else {
                                    (slideInHorizontally(tween(320, easing = FastOutSlowInEasing)) { -it / 3 } + fadeIn(tween(250)))
                                        .togetherWith(slideOutHorizontally(tween(320, easing = FastOutSlowInEasing)) { it / 3 } + fadeOut(tween(200)))
                                }
                            },
                            label = "tab-transition"
                        ) { tab ->
                            when (tab) {
                                NavigationTab.Discover -> HomeScreen(
                                    onOpenRoute = { secondaryStack.add(it) },
                                    onNavigateToSearch = { currentTab = NavigationTab.Search }
                                )
                                NavigationTab.Search -> SearchScreen(
                                    onOpenRoute = { secondaryStack.add(it) }
                                )
                                NavigationTab.My -> MyScreen(
                                    onNavigateToSettings = { currentTab = NavigationTab.Settings },
                                    onOpenRoute = { secondaryStack.add(it) }
                                )
                                NavigationTab.Settings -> SettingsScreen(
                                    onOpenRoute = { secondaryStack.add(it) }
                                )
                            }
                        }
                    }

                    // 二级界面展示层 (包含歌单、专辑、歌手、我喜欢的音乐、排行榜广场、关于等)
                    // 二级页面均带有自适配状态栏的 SmallTopAppBar，顶层不可使用 innerPadding.calculateTopPadding()，否则会导致标题栏双重下移；
                    // 仅需保留底部 padding 为 MiniPlayerBar 留出空间 (开启毛玻璃时同样延展到底部)。
                    // 采用澎湃OS (HyperOS)“一镜到底”视觉动效规范：平滑缩放连贯展开与沉浸渐变
                    AnimatedVisibility(
                        visible = currentSecondary != null,
                        enter = scaleIn(initialScale = 0.90f, animationSpec = tween(340, easing = FastOutSlowInEasing)) + fadeIn(tween(260)),
                        exit = scaleOut(targetScale = 0.92f, animationSpec = tween(280, easing = FastOutSlowInEasing)) + fadeOut(tween(220))
                    ) {
                        Box(
                            modifier = Modifier
                                .fillMaxSize()
                                .padding(bottom = if (blurActive) 0.dp else bottomBarPadding)
                        ) {
                        AnimatedContent(
                            targetState = currentSecondary,
                            transitionSpec = {
                                (scaleIn(initialScale = 0.92f, animationSpec = tween(320, easing = FastOutSlowInEasing)) + fadeIn(tween(260)))
                                    .togetherWith(scaleOut(targetScale = 0.95f, animationSpec = tween(260, easing = FastOutSlowInEasing)) + fadeOut(tween(200)))
                            },
                            label = "secondary-stack-transition"
                        ) { route ->
                            if (route != null) {
                                when (route) {
                                    is SecondaryRoute.TopChartsSquare -> {
                                        TopChartsSquareScreen(
                                            onBack = { secondaryStack.removeLastOrNull() },
                                            onChartClick = { id, name, cover ->
                                                secondaryStack.add(SecondaryRoute.Playlist(id, name, cover))
                                            }
                                        )
                                    }
                                    is SecondaryRoute.About -> {
                                        AboutScreen(
                                            onBack = { secondaryStack.removeLastOrNull() }
                                        )
                                    }
                                    else -> {
                                        PlaylistDetailScreen(
                                            route = route,
                                            onBack = { secondaryStack.removeLastOrNull() },
                                            onNavigateToRoute = { secondaryStack.add(it) }
                                        )
                                    }
                                }
                            }
                        }
                    }
                }
            }
        }
    }

    AnimatedVisibility(
            visible = isPlayerExpanded,
            enter = slideInVertically(tween(360, easing = FastOutSlowInEasing)) { it } + fadeIn(tween(250)),
            exit = slideOutVertically(tween(360, easing = FastOutSlowInEasing)) { it } + fadeOut(tween(200))
        ) {
            PlayerScreen(
                onDismiss = { isPlayerExpanded = false },
                onOpenQueue = { isQueueOpen = true },
                onNavigateToRoute = { route ->
                    isPlayerExpanded = false
                    secondaryStack.add(route)
                }
            )
        }

        QueueSheet(
            show = isQueueOpen,
            onDismissRequest = { isQueueOpen = false }
        )

        AnimatedVisibility(
            visible = !isOobeCompleted,
            enter = fadeIn(tween(260)),
            exit = fadeOut(tween(300)) + scaleOut(targetScale = 1.05f, animationSpec = tween(300, easing = FastOutSlowInEasing))
        ) {
            OobeScreen(
                onComplete = {
                    SettingsRepository.setOobeCompleted(true)
                }
            )
        }
    }
}
