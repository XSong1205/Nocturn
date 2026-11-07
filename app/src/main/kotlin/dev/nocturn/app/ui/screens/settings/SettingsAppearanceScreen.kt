@file:OptIn(androidx.compose.material3.ExperimentalMaterial3ExpressiveApi::class)

package dev.nocturn.app.ui.screens.settings

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
import androidx.compose.material.icons.filled.AutoAwesome
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.SegmentedButton
import androidx.compose.material3.SegmentedButtonDefaults
import androidx.compose.material3.SingleChoiceSegmentedButtonRow
import androidx.compose.material3.Surface
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import dev.nocturn.app.data.model.ThemeMode
import dev.nocturn.app.data.model.displayName
import dev.nocturn.app.ui.components.BarBackButton
import dev.nocturn.app.ui.components.BarTitle
import dev.nocturn.app.ui.components.GlassScaffold
import dev.nocturn.app.ui.components.GlassTopBar
import dev.nocturn.app.ui.components.SectionHeader
import dev.nocturn.app.ui.components.bottomContentPadding
import dev.nocturn.app.ui.components.rememberHaptics
import dev.nocturn.app.ui.components.rememberUnfilteredHaptics
import dev.nocturn.app.ui.components.scrolledPast
import dev.nocturn.app.ui.preview.PreviewData
import dev.nocturn.app.ui.state.SettingsUiState
import dev.nocturn.app.ui.theme.NocturnTheme
import dev.nocturn.app.ui.theme.isDynamicColorSupported

/**
 * Secondary settings page: Appearance, theme mode and Monet dynamic color.
 */
@Composable
fun SettingsAppearanceScreen(
    state: SettingsUiState,
    modifier: Modifier = Modifier,
    onBack: () -> Unit = {},
    onThemeModeChange: (ThemeMode) -> Unit = {},
    onDynamicColorChange: (Boolean) -> Unit = {},
) {
    val listState = rememberLazyListState()
    val collapsed by listState.scrolledPast(140)
    val haptics = rememberHaptics()
    val switchHaptics = rememberUnfilteredHaptics()

    GlassScaffold(
        modifier = modifier,
        topBar = {
            GlassTopBar(scrolled = collapsed) {
                BarBackButton(onBack = onBack, testTag = "settings-appearance-back")
                BarTitle(text = "外观与显示", visible = collapsed)
            }
        },
    ) { topPadding ->
        LazyColumn(
            state = listState,
            modifier = Modifier
                .fillMaxSize()
                .testTag("settings-appearance-screen"),
            contentPadding = PaddingValues(top = topPadding + 4.dp, bottom = bottomContentPadding()),
        ) {
            item(key = "hero") {
                SettingsHero(
                    title = "外观与显示",
                    subtitle = "主题明暗风格与动态色彩提取偏好",
                )
            }

            item(key = "theme-mode") {
                SectionHeader(
                    title = "主题模式",
                    subtitle = "设置界面的明暗显示风格",
                )
                Spacer(Modifier.height(10.dp))
                val modes = ThemeMode.entries
                SingleChoiceSegmentedButtonRow(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 20.dp),
                ) {
                    modes.forEachIndexed { index, mode ->
                        SegmentedButton(
                            selected = state.themeMode == mode,
                            onClick = {
                                haptics.step()
                                onThemeModeChange(mode)
                            },
                            shape = SegmentedButtonDefaults.itemShape(
                                index = index,
                                count = modes.size,
                            ),
                            icon = {},
                            label = { Text(mode.displayName(), maxLines = 1) },
                            modifier = Modifier.testTag("settings-theme-${mode.name.lowercase()}"),
                        )
                    }
                }
            }

            item(key = "dynamic-color") {
                Spacer(Modifier.height(24.dp))
                SectionHeader(
                    title = "色彩方案",
                    subtitle = "Material You 壁纸取色引擎",
                )
                Spacer(Modifier.height(10.dp))
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
                    ) {
                        Column(modifier = Modifier.weight(1f)) {
                            Text(
                                text = "动态取色",
                                style = MaterialTheme.typography.titleMedium,
                                color = MaterialTheme.colorScheme.onSurface,
                            )
                            Spacer(Modifier.height(2.dp))
                            Text(
                                text = if (isDynamicColorSupported()) {
                                    "基于系统壁纸与主题智能提取全应用配色"
                                } else {
                                    "基于系统壁纸提取配色（需 Android 12+）"
                                },
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                            )
                        }
                        Spacer(Modifier.width(12.dp))
                        Switch(
                            checked = state.dynamicColor && isDynamicColorSupported(),
                            onCheckedChange = { enabled ->
                                switchHaptics.toggle(enabled)
                                onDynamicColorChange(enabled)
                            },
                            enabled = isDynamicColorSupported(),
                            modifier = Modifier.testTag("settings-dynamic-color-switch"),
                        )
                    }
                }
            }

            item(key = "expressive-info") {
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
                            Icons.Filled.AutoAwesome,
                            contentDescription = null,
                            tint = MaterialTheme.colorScheme.primary,
                            modifier = Modifier.size(20.dp),
                        )
                        Spacer(Modifier.width(12.dp))
                        Column(modifier = Modifier.weight(1f)) {
                            Text(
                                text = "Material 3 Expressive",
                                style = MaterialTheme.typography.titleSmall,
                                color = MaterialTheme.colorScheme.onSurface,
                            )
                            Spacer(Modifier.height(4.dp))
                            Text(
                                text = "界面遵循 Material 3 Expressive 设计哲学，融入通透的磨砂玻璃实时模糊、多形态非对称圆角与富有弹性的空间动效，带来纯粹克制的美学感受。",
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                            )
                        }
                    }
                }
            }
        }
    }
}

@Preview(name = "外观与显示 · 浅色", showBackground = true)
@Composable
private fun SettingsAppearanceScreenPreview() {
    NocturnTheme {
        SettingsAppearanceScreen(state = PreviewData.settingsState)
    }
}
