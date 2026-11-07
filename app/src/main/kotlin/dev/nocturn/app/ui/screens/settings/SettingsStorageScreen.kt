@file:OptIn(androidx.compose.material3.ExperimentalMaterial3ExpressiveApi::class)

package dev.nocturn.app.ui.screens.settings

import androidx.compose.foundation.layout.Arrangement
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
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.CleaningServices
import androidx.compose.material.icons.filled.DeleteOutline
import androidx.compose.material.icons.filled.History
import androidx.compose.material.icons.filled.Security
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import dev.nocturn.app.ui.components.BarBackButton
import dev.nocturn.app.ui.components.BarTitle
import dev.nocturn.app.ui.components.BlurBehindWindow
import dev.nocturn.app.ui.components.GlassScaffold
import dev.nocturn.app.ui.components.GlassTopBar
import dev.nocturn.app.ui.components.SectionHeader
import dev.nocturn.app.ui.components.bottomContentPadding
import dev.nocturn.app.ui.components.rememberHaptics
import dev.nocturn.app.ui.components.scrolledPast
import dev.nocturn.app.ui.preview.PreviewData
import dev.nocturn.app.ui.state.SettingsUiState
import dev.nocturn.app.ui.theme.NocturnTheme
import dev.nocturn.app.ui.util.formatFileSize

/**
 * Secondary settings page: Storage and cache management (search history and disk cache).
 */
@Composable
fun SettingsStorageScreen(
    state: SettingsUiState,
    modifier: Modifier = Modifier,
    onBack: () -> Unit = {},
    onClearSearchHistory: () -> Unit = {},
    onClearCache: () -> Unit = {},
) {
    val listState = rememberLazyListState()
    val collapsed by listState.scrolledPast(140)
    val haptics = rememberHaptics()
    var showClearHistoryDialog by rememberSaveable { mutableStateOf(false) }

    GlassScaffold(
        modifier = modifier,
        topBar = {
            GlassTopBar(scrolled = collapsed) {
                BarBackButton(onBack = onBack, testTag = "settings-storage-back")
                BarTitle(text = "存储与缓存", visible = collapsed)
            }
        },
    ) { topPadding ->
        LazyColumn(
            state = listState,
            modifier = Modifier
                .fillMaxSize()
                .testTag("settings-storage-screen"),
            contentPadding = PaddingValues(top = topPadding + 4.dp, bottom = bottomContentPadding()),
        ) {
            item(key = "hero") {
                SettingsHero(
                    title = "存储与缓存",
                    subtitle = "本地临时文件、封面缓存与搜索记录",
                )
            }

            item(key = "search-history-header") {
                SectionHeader(
                    title = "搜索历史",
                    subtitle = "搜索栏中记录的常用搜索词",
                )
                Spacer(Modifier.height(10.dp))
            }

            item(key = "search-history-card") {
                Surface(
                    shape = MaterialTheme.shapes.large,
                    color = MaterialTheme.colorScheme.surfaceContainerLow,
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 20.dp),
                ) {
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(16.dp),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.SpaceBetween,
                    ) {
                        Surface(
                            shape = MaterialTheme.shapes.medium,
                            color = MaterialTheme.colorScheme.secondaryContainer,
                            contentColor = MaterialTheme.colorScheme.onSecondaryContainer,
                            modifier = Modifier.size(40.dp),
                        ) {
                            Row(
                                verticalAlignment = Alignment.CenterVertically,
                                horizontalArrangement = Arrangement.Center,
                            ) {
                                Icon(
                                    Icons.Filled.History,
                                    contentDescription = null,
                                    modifier = Modifier.size(20.dp),
                                )
                            }
                        }
                        Spacer(Modifier.width(14.dp))
                        Column(modifier = Modifier.weight(1f)) {
                            Text(
                                text = "本地搜索记录",
                                style = MaterialTheme.typography.titleMedium,
                                color = MaterialTheme.colorScheme.onSurface,
                            )
                            Spacer(Modifier.height(2.dp))
                            Text(
                                text = if (state.searchHistoryCount > 0) {
                                    "已保存 ${state.searchHistoryCount} 条搜索关键词"
                                } else {
                                    "暂无搜索记录"
                                },
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                            )
                        }
                        Spacer(Modifier.width(8.dp))
                        OutlinedButton(
                            onClick = {
                                haptics.tap()
                                showClearHistoryDialog = true
                            },
                            enabled = state.searchHistoryCount > 0,
                            modifier = Modifier.testTag("settings-clear-history-button"),
                        ) {
                            Text("清除")
                        }
                    }
                }
            }

            item(key = "cache-header") {
                Spacer(Modifier.height(24.dp))
                SectionHeader(
                    title = "应用缓存",
                    subtitle = "在线加载的专辑封面、歌单图与网络缓存",
                )
                Spacer(Modifier.height(10.dp))
            }

            item(key = "cache-card") {
                Surface(
                    shape = MaterialTheme.shapes.large,
                    color = MaterialTheme.colorScheme.surfaceContainerLow,
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 20.dp),
                ) {
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(16.dp),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.SpaceBetween,
                    ) {
                        Surface(
                            shape = MaterialTheme.shapes.medium,
                            color = MaterialTheme.colorScheme.primaryContainer,
                            contentColor = MaterialTheme.colorScheme.onPrimaryContainer,
                            modifier = Modifier.size(40.dp),
                        ) {
                            Row(
                                verticalAlignment = Alignment.CenterVertically,
                                horizontalArrangement = Arrangement.Center,
                            ) {
                                Icon(
                                    Icons.Filled.CleaningServices,
                                    contentDescription = null,
                                    modifier = Modifier.size(20.dp),
                                )
                            }
                        }
                        Spacer(Modifier.width(14.dp))
                        Column(modifier = Modifier.weight(1f)) {
                            Text(
                                text = "临时封面与网络缓存",
                                style = MaterialTheme.typography.titleMedium,
                                color = MaterialTheme.colorScheme.onSurface,
                            )
                            Spacer(Modifier.height(2.dp))
                            Text(
                                text = "当前占用空间：${formatFileSize(state.cacheSizeBytes)}",
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                            )
                        }
                        Spacer(Modifier.width(8.dp))
                        Button(
                            onClick = {
                                haptics.confirm()
                                onClearCache()
                            },
                            modifier = Modifier.testTag("settings-clear-cache-button"),
                        ) {
                            Text("清理")
                        }
                    }
                }
            }

            item(key = "security-info") {
                Spacer(Modifier.height(20.dp))
                Surface(
                    shape = MaterialTheme.shapes.large,
                    color = MaterialTheme.colorScheme.surfaceContainerHigh,
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 20.dp),
                ) {
                    Row(
                        modifier = Modifier.padding(16.dp),
                        verticalAlignment = Alignment.Top,
                    ) {
                        Icon(
                            Icons.Filled.Security,
                            contentDescription = null,
                            tint = MaterialTheme.colorScheme.primary,
                            modifier = Modifier.size(20.dp),
                        )
                        Spacer(Modifier.width(12.dp))
                        Column(modifier = Modifier.weight(1f)) {
                            Text(
                                text = "数据隔离与安全保障",
                                style = MaterialTheme.typography.titleSmall,
                                color = MaterialTheme.colorScheme.onSurface,
                            )
                            Spacer(Modifier.height(4.dp))
                            Text(
                                text = "登录 Cookie 与密钥独立存储于系统加密安全区（Android Keystore），清理缓存仅清空临时图像与网络响应，不会导致退出登录或丢失歌单偏好。",
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                            )
                        }
                    }
                }
            }
        }
    }

    if (showClearHistoryDialog) {
        AlertDialog(
            onDismissRequest = { showClearHistoryDialog = false },
            icon = { Icon(Icons.Filled.DeleteOutline, contentDescription = null) },
            title = { Text("清除搜索历史？") },
            text = {
                BlurBehindWindow()
                Text("此操作将清空本地记录的所有搜索词历史，该操作无法撤销。")
            },
            confirmButton = {
                TextButton(
                    onClick = {
                        haptics.confirm()
                        showClearHistoryDialog = false
                        onClearSearchHistory()
                    },
                    modifier = Modifier.testTag("settings-clear-history-confirm"),
                ) {
                    Text("清除")
                }
            },
            dismissButton = {
                TextButton(onClick = {
                    haptics.tap()
                    showClearHistoryDialog = false
                }) { Text("取消") }
            },
        )
    }
}

@Preview(name = "存储与缓存 · 浅色", showBackground = true)
@Composable
private fun SettingsStorageScreenPreview() {
    NocturnTheme {
        SettingsStorageScreen(state = PreviewData.settingsState)
    }
}
