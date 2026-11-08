@file:OptIn(androidx.compose.material3.ExperimentalMaterial3ExpressiveApi::class)

package dev.nocturn.app.ui.screens.settings

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.KeyboardArrowRight
import androidx.compose.material.icons.filled.Headphones
import androidx.compose.material.icons.filled.Info
import androidx.compose.material.icons.filled.Palette
import androidx.compose.material.icons.filled.Storage
import androidx.compose.material.icons.filled.TouchApp
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import dev.nocturn.app.data.model.displayName
import dev.nocturn.app.ui.components.BarBackButton
import dev.nocturn.app.ui.components.BarTitle
import dev.nocturn.app.ui.components.GlassScaffold
import dev.nocturn.app.ui.components.GlassTopBar
import dev.nocturn.app.ui.components.bottomContentPadding
import dev.nocturn.app.ui.components.pressScale
import dev.nocturn.app.ui.components.rememberHaptics
import dev.nocturn.app.ui.components.scrolledPast
import dev.nocturn.app.ui.preview.PreviewData
import dev.nocturn.app.ui.state.SettingsUiState
import dev.nocturn.app.ui.theme.NocturnTheme
import dev.nocturn.app.ui.util.formatFileSize

/**
 * Root settings destination presenting secondary sub-menus:
 * playback/quality, appearance, general/interaction, storage/cache and about.
 */
@Composable
fun SettingsScreen(
    state: SettingsUiState,
    modifier: Modifier = Modifier,
    onBack: () -> Unit = {},
    onNavigatePlayback: () -> Unit = {},
    onNavigateAppearance: () -> Unit = {},
    onNavigateGeneral: () -> Unit = {},
    onNavigateStorage: () -> Unit = {},
    onNavigateAbout: () -> Unit = {},
) {
    val listState = rememberLazyListState()
    val collapsed by listState.scrolledPast(140)

    GlassScaffold(
        modifier = modifier,
        topBar = {
            GlassTopBar(scrolled = collapsed) {
                BarBackButton(onBack = onBack, testTag = "settings-back")
                BarTitle(text = "设置", visible = collapsed)
            }
        },
    ) { topPadding ->
        LazyColumn(
            state = listState,
            modifier = Modifier
                .fillMaxSize()
                .testTag("settings-screen"),
            contentPadding = PaddingValues(top = topPadding + 4.dp, bottom = bottomContentPadding()),
        ) {
            item(key = "hero") {
                SettingsHero(
                    title = "设置",
                    subtitle = "个性化偏好、播放选项与系统管理",
                )
            }

            item(key = "menu-playback") {
                SettingsMenuItemCard(
                    icon = Icons.Filled.Headphones,
                    iconContainerColor = MaterialTheme.colorScheme.primaryContainer,
                    iconContentColor = MaterialTheme.colorScheme.onPrimaryContainer,
                    title = "播放与音质",
                    subtitle = "默认音质（${state.quality.displayName()}）与解码偏好",
                    testTag = "settings-menu-playback",
                    onClick = onNavigatePlayback,
                )
            }

            item(key = "menu-appearance") {
                SettingsMenuItemCard(
                    icon = Icons.Filled.Palette,
                    iconContainerColor = MaterialTheme.colorScheme.secondaryContainer,
                    iconContentColor = MaterialTheme.colorScheme.onSecondaryContainer,
                    title = "外观与显示",
                    subtitle = "主题（${state.themeMode.displayName()}）、动态取色",
                    testTag = "settings-menu-appearance",
                    onClick = onNavigateAppearance,
                )
            }

            item(key = "menu-general") {
                SettingsMenuItemCard(
                    icon = Icons.Filled.TouchApp,
                    iconContainerColor = MaterialTheme.colorScheme.tertiaryContainer,
                    iconContentColor = MaterialTheme.colorScheme.onTertiaryContainer,
                    title = "通用与交互",
                    subtitle = "触感反馈（${if (state.hapticsEnabled) "已开启" else "已关闭"}）、词幕歌词（${if (state.lyriconEnabled) "已开启" else "已关闭"}）、手势与转场",
                    testTag = "settings-menu-general",
                    onClick = onNavigateGeneral,
                )
            }

            item(key = "menu-storage") {
                SettingsMenuItemCard(
                    icon = Icons.Filled.Storage,
                    iconContainerColor = MaterialTheme.colorScheme.surfaceContainerHigh,
                    iconContentColor = MaterialTheme.colorScheme.onSurfaceVariant,
                    title = "存储与缓存",
                    subtitle = "搜索历史（${state.searchHistoryCount} 条）、缓存（${formatFileSize(state.cacheSizeBytes)}）",
                    testTag = "settings-menu-storage",
                    onClick = onNavigateStorage,
                )
            }

            item(key = "menu-about") {
                SettingsMenuItemCard(
                    icon = Icons.Filled.Info,
                    iconContainerColor = MaterialTheme.colorScheme.surfaceVariant,
                    iconContentColor = MaterialTheme.colorScheme.onSurfaceVariant,
                    title = "关于",
                    subtitle = "Nocturn ${state.version} · 开源与技术架构",
                    testTag = "settings-menu-about",
                    onClick = onNavigateAbout,
                )
            }
        }
    }
}

@Composable
internal fun SettingsHero(
    title: String,
    subtitle: String,
    modifier: Modifier = Modifier,
) {
    Column(
        modifier = modifier
            .fillMaxWidth()
            .padding(horizontal = 20.dp, vertical = 12.dp),
    ) {
        Text(
            text = title,
            style = MaterialTheme.typography.headlineMediumEmphasized,
            color = MaterialTheme.colorScheme.onSurface,
        )
        Spacer(Modifier.height(4.dp))
        Text(
            text = subtitle,
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
    }
}

@Composable
internal fun SettingsMenuItemCard(
    icon: ImageVector,
    iconContainerColor: Color,
    iconContentColor: Color,
    title: String,
    subtitle: String,
    testTag: String,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val haptics = rememberHaptics()
    Surface(
        onClick = {
            haptics.tap()
            onClick()
        },
        modifier = modifier
            .fillMaxWidth()
            .padding(horizontal = 20.dp, vertical = 6.dp)
            .pressScale(0.97f)
            .testTag(testTag),
        shape = MaterialTheme.shapes.large,
        color = MaterialTheme.colorScheme.surfaceContainerLow,
        contentColor = MaterialTheme.colorScheme.onSurface,
    ) {
        Row(
            modifier = Modifier.padding(horizontal = 16.dp, vertical = 14.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Surface(
                shape = CircleShape,
                color = iconContainerColor,
                contentColor = iconContentColor,
                modifier = Modifier.size(44.dp),
            ) {
                Box(contentAlignment = Alignment.Center) {
                    Icon(
                        imageVector = icon,
                        contentDescription = null,
                        modifier = Modifier.size(22.dp),
                    )
                }
            }
            Spacer(Modifier.width(14.dp))
            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = title,
                    style = MaterialTheme.typography.titleMedium,
                    color = MaterialTheme.colorScheme.onSurface,
                )
                Spacer(Modifier.height(2.dp))
                Text(
                    text = subtitle,
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
            Icon(
                Icons.AutoMirrored.Filled.KeyboardArrowRight,
                contentDescription = null,
                tint = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
    }
}

@Preview(name = "设置 · 浅色", showBackground = true)
@Composable
private fun SettingsScreenPreviewLight() {
    NocturnTheme {
        SettingsScreen(state = PreviewData.settingsState)
    }
}
