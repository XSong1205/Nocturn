@file:OptIn(androidx.compose.material3.ExperimentalMaterial3ExpressiveApi::class)

package dev.nocturn.app.ui.screens.settings

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.selection.selectable
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Add
import androidx.compose.material.icons.rounded.AutoAwesome
import androidx.compose.material.icons.rounded.Check
import androidx.compose.material.icons.rounded.Colorize
import androidx.compose.material.icons.rounded.Palette
import androidx.compose.material.icons.rounded.RestartAlt
import androidx.compose.material.icons.rounded.Wallpaper
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.FilledTonalButton
import androidx.compose.material3.FilledTonalIconButton
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.RadioButton
import androidx.compose.material3.SegmentedButton
import androidx.compose.material3.SegmentedButtonDefaults
import androidx.compose.material3.SingleChoiceSegmentedButtonRow
import androidx.compose.material3.Surface
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.toArgb
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import dev.nocturn.app.data.model.ColorSchemeKey
import dev.nocturn.app.data.model.DynamicVariant
import dev.nocturn.app.data.model.ThemeMode
import dev.nocturn.app.data.model.description
import dev.nocturn.app.data.model.displayName
import dev.nocturn.app.data.model.isDynamic
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
import dev.nocturn.app.ui.theme.LocalIsDarkTheme
import dev.nocturn.app.ui.theme.NocturnTheme
import dev.nocturn.app.ui.theme.isDynamicColorSupported
import dev.nocturn.app.ui.theme.resolvePalettePreview

/**
 * Secondary settings page: Personalization & Appearance, theme mode, Material 3 Expressive
 * color schemes and Monet dynamic color customization.
 */
@OptIn(ExperimentalLayoutApi::class)
@Composable
fun SettingsAppearanceScreen(
    state: SettingsUiState,
    modifier: Modifier = Modifier,
    onBack: () -> Unit = {},
    onThemeModeChange: (ThemeMode) -> Unit = {},
    onColorSchemeChange: (ColorSchemeKey, Long?) -> Unit = { _, _ -> },
    onDynamicVariantChange: (DynamicVariant) -> Unit = {},
    onResetColorScheme: () -> Unit = {},
    onDynamicColorChange: (Boolean) -> Unit = {},
) {
    val listState = rememberLazyListState()
    val collapsed by listState.scrolledPast(140)
    val haptics = rememberHaptics()
    val switchHaptics = rememberUnfilteredHaptics()
    val dark = LocalIsDarkTheme.current
    val context = LocalContext.current

    var showVariantDialog by remember { mutableStateOf(false) }
    var showCustomColorDialog by remember { mutableStateOf(false) }

    GlassScaffold(
        modifier = modifier,
        topBar = {
            GlassTopBar(scrolled = collapsed) {
                BarBackButton(onBack = onBack, testTag = "settings-appearance-back")
                BarTitle(text = "个性化", visible = collapsed)
            }
        },
    ) { topPadding ->
        Box(
            modifier = Modifier.fillMaxSize(),
            contentAlignment = Alignment.TopCenter,
        ) {
            LazyColumn(
                state = listState,
                modifier = Modifier
                    .fillMaxSize()
                    .widthIn(max = 840.dp)
                    .testTag("settings-appearance-screen"),
                contentPadding = PaddingValues(top = topPadding + 4.dp, bottom = bottomContentPadding()),
            ) {
                item(key = "hero") {
                    SettingsHero(
                        title = "个性化",
                        subtitle = "主题模式与 Material 3 调色板偏好",
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

                // ---------------------------------------------------------------------------------
                // Theme Colors (主题色彩) Card
                // ---------------------------------------------------------------------------------
                item(key = "theme-colors") {
                    Spacer(Modifier.height(24.dp))
                    Surface(
                        shape = MaterialTheme.shapes.extraLarge,
                        color = MaterialTheme.colorScheme.surfaceContainerLow,
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(horizontal = 20.dp),
                    ) {
                        Column(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(20.dp),
                        ) {
                            // Header row: [Palette Icon + "主题色彩"] ... ["内容主题" Chip] [Reset Button]
                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                verticalAlignment = Alignment.CenterVertically,
                            ) {
                                Icon(
                                    imageVector = Icons.Rounded.Palette,
                                    contentDescription = null,
                                    tint = MaterialTheme.colorScheme.onSurface,
                                    modifier = Modifier.size(24.dp),
                                )
                                Spacer(Modifier.width(10.dp))
                                Text(
                                    text = "主题色彩",
                                    style = MaterialTheme.typography.titleMedium,
                                    fontWeight = FontWeight.Bold,
                                    color = MaterialTheme.colorScheme.onSurface,
                                )

                                Spacer(Modifier.weight(1f))

                                // Content Theme / Dynamic Variant Chip
                                FilledTonalButton(
                                    onClick = {
                                        haptics.step()
                                        showVariantDialog = true
                                    },
                                    shape = CircleShape,
                                    contentPadding = PaddingValues(horizontal = 14.dp, vertical = 6.dp),
                                    modifier = Modifier
                                        .height(36.dp)
                                        .testTag("settings-dynamic-variant-chip"),
                                ) {
                                    Text(
                                        text = state.dynamicVariant.displayName(),
                                        style = MaterialTheme.typography.labelMedium,
                                        fontWeight = FontWeight.Medium,
                                    )
                                }

                                Spacer(Modifier.width(8.dp))

                                // Reset Button
                                FilledTonalIconButton(
                                    onClick = {
                                        haptics.toggle(true)
                                        onResetColorScheme()
                                    },
                                    shape = CircleShape,
                                    modifier = Modifier
                                        .size(36.dp)
                                        .testTag("settings-reset-colors"),
                                ) {
                                    Icon(
                                        imageVector = Icons.Rounded.RestartAlt,
                                        contentDescription = "恢复默认配色",
                                        modifier = Modifier.size(20.dp),
                                    )
                                }
                            }

                            Spacer(Modifier.height(20.dp))

                            // Grid of Color Palette Swatches
                            val paletteKeys = listOf(
                                ColorSchemeKey.M3eDynamic,
                                ColorSchemeKey.M3Dynamic,
                                ColorSchemeKey.M3eOfficial,
                                ColorSchemeKey.M3Baseline,
                                ColorSchemeKey.Amber,
                                ColorSchemeKey.Ocean,
                                ColorSchemeKey.Mint,
                                ColorSchemeKey.Rose,
                                ColorSchemeKey.NocturnClassic,
                            )

                            FlowRow(
                                horizontalArrangement = Arrangement.spacedBy(16.dp),
                                verticalArrangement = Arrangement.spacedBy(16.dp),
                                modifier = Modifier.fillMaxWidth(),
                            ) {
                                paletteKeys.forEach { key ->
                                    val isSelected = state.colorSchemeKey == key
                                    val previewColors = resolvePalettePreview(
                                        key = key,
                                        customSeed = state.customSeedColor,
                                        dark = dark,
                                        context = context,
                                        dynamicVariant = state.dynamicVariant,
                                    )

                                    ColorPaletteSwatch(
                                        preview = previewColors,
                                        isSelected = isSelected,
                                        badge = when (key) {
                                            ColorSchemeKey.M3eDynamic -> ColorSwatchBadge.M3E
                                            ColorSchemeKey.M3Dynamic -> ColorSwatchBadge.M3
                                            else -> ColorSwatchBadge.None
                                        },
                                        contentDescription = "${key.displayName()}${if (isSelected) "，已选择" else ""}",
                                        onClick = {
                                            haptics.step()
                                            onColorSchemeChange(key, null)
                                        },
                                    )
                                }

                                // Custom Color "+" Swatch Button
                                val isCustomSelected = state.colorSchemeKey == ColorSchemeKey.Custom
                                if (isCustomSelected) {
                                    val customPreview = resolvePalettePreview(
                                        key = ColorSchemeKey.Custom,
                                        customSeed = state.customSeedColor,
                                        dark = dark,
                                        context = context,
                                        dynamicVariant = state.dynamicVariant,
                                    )
                                    ColorPaletteSwatch(
                                        preview = customPreview,
                                        isSelected = true,
                                        badge = ColorSwatchBadge.None,
                                        contentDescription = "自定义色彩，已选择",
                                        onClick = {
                                            haptics.step()
                                            showCustomColorDialog = true
                                        },
                                    )
                                } else {
                                    CustomColorAddButton(
                                        onClick = {
                                            haptics.step()
                                            showCustomColorDialog = true
                                        },
                                    )
                                }
                            }

                            Spacer(Modifier.height(18.dp))

                            // Quick Dynamic Color Toggle (Ensuring testTag compatibility)
                            Row(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .clip(MaterialTheme.shapes.medium)
                                    .clickable {
                                        val next = !state.dynamicColor
                                        switchHaptics.toggle(next)
                                        onDynamicColorChange(next)
                                    }
                                    .padding(vertical = 8.dp, horizontal = 4.dp),
                                verticalAlignment = Alignment.CenterVertically,
                            ) {
                                Column(modifier = Modifier.weight(1f)) {
                                    Text(
                                        text = "动态取色优先",
                                        style = MaterialTheme.typography.titleSmall,
                                        color = MaterialTheme.colorScheme.onSurface,
                                    )
                                    Spacer(Modifier.height(2.dp))
                                    Text(
                                        text = if (isDynamicColorSupported()) {
                                            "当前已启用 Material You 壁纸动态色调提取"
                                        } else {
                                            "当前系统暂不支持壁纸提取（需 Android 12+）"
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
                }

                // ---------------------------------------------------------------------------------
                // Material 3 Expressive Info Card
                // ---------------------------------------------------------------------------------
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
                                Icons.Rounded.AutoAwesome,
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
                                    text = "采用 Android 官方最新版 Material 3 Expressive 配色哲学，高饱和度与富有张力的对比层次，结合无渐变纯净底色与通透磨砂玻璃，提供自然、生动的视觉与触控体验。",
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

    // ---------------------------------------------------------------------------------------------
    // Dynamic Scheme Variant Dialog
    // ---------------------------------------------------------------------------------------------
    if (showVariantDialog) {
        AlertDialog(
            onDismissRequest = { showVariantDialog = false },
            title = {
                Text(text = "选择取色风格", style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold)
            },
            text = {
                Column(modifier = Modifier.verticalScroll(rememberScrollState())) {
                    DynamicVariant.entries.forEach { variant ->
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .clip(MaterialTheme.shapes.small)
                                .selectable(
                                    selected = state.dynamicVariant == variant,
                                    role = Role.RadioButton,
                                    onClick = {
                                        haptics.step()
                                        onDynamicVariantChange(variant)
                                        showVariantDialog = false
                                    },
                                )
                                .padding(vertical = 10.dp, horizontal = 4.dp)
                                .testTag("settings-variant-${variant.name.lowercase()}"),
                            verticalAlignment = Alignment.CenterVertically,
                        ) {
                            RadioButton(
                                selected = state.dynamicVariant == variant,
                                onClick = null,
                            )
                            Spacer(Modifier.width(10.dp))
                            Column {
                                Text(
                                    text = variant.displayName(),
                                    style = MaterialTheme.typography.bodyMedium,
                                    fontWeight = FontWeight.SemiBold,
                                    color = MaterialTheme.colorScheme.onSurface,
                                )
                                Text(
                                    text = variant.description(),
                                    style = MaterialTheme.typography.bodySmall,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                                )
                            }
                        }
                    }
                }
            },
            confirmButton = {
                TextButton(onClick = { showVariantDialog = false }) {
                    Text("完成")
                }
            },
        )
    }

    // ---------------------------------------------------------------------------------------------
    // Custom Seed Color Dialog
    // ---------------------------------------------------------------------------------------------
    if (showCustomColorDialog) {
        CustomSeedColorDialog(
            initialColor = Color(state.customSeedColor),
            onDismiss = { showCustomColorDialog = false },
            onConfirm = { chosenColor ->
                onColorSchemeChange(ColorSchemeKey.Custom, chosenColor.toArgb().toLong() and 0xFFFFFFFFL)
                showCustomColorDialog = false
            },
        )
    }
}

// -------------------------------------------------------------------------------------------------
// Circular Color Palette Swatch Component
// -------------------------------------------------------------------------------------------------

enum class ColorSwatchBadge { None, M3E, M3, Eyedropper, Wallpaper }

@Composable
fun ColorPaletteSwatch(
    preview: dev.nocturn.app.ui.theme.PalettePreviewColors,
    isSelected: Boolean,
    badge: ColorSwatchBadge,
    contentDescription: String,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val haptics = rememberHaptics()
    val outerSize = 58.dp
    val innerSize = 48.dp

    Box(
        modifier = modifier
            .size(outerSize)
            .semantics { this.contentDescription = contentDescription }
            .then(
                if (isSelected) {
                    Modifier
                        .border(
                            width = 2.5.dp,
                            color = MaterialTheme.colorScheme.primary,
                            shape = CircleShape,
                        )
                        .padding(4.dp)
                } else {
                    Modifier.padding(4.dp)
                },
            )
            .clip(CircleShape)
            .clickable(role = Role.Button) {
                haptics.step()
                onClick()
            },
        contentAlignment = Alignment.Center,
    ) {
        // Draw the 3-quadrant pie
        Canvas(modifier = Modifier.size(innerSize)) {
            // Left half: 180 deg (start from 90 deg down to 270 deg up)
            drawArc(
                color = preview.primary,
                startAngle = 90f,
                sweepAngle = 180f,
                useCenter = true,
            )
            // Top-right quarter: 90 deg (start from 270 deg up to 360/0 deg right)
            drawArc(
                color = preview.secondary,
                startAngle = 270f,
                sweepAngle = 90f,
                useCenter = true,
            )
            // Bottom-right quarter: 90 deg (start from 0 deg right to 90 deg down)
            drawArc(
                color = preview.tertiary,
                startAngle = 0f,
                sweepAngle = 90f,
                useCenter = true,
            )
        }

        // Center Checkmark if selected
        if (isSelected) {
            Box(
                modifier = Modifier
                    .size(24.dp)
                    .clip(CircleShape)
                    .background(MaterialTheme.colorScheme.surface.copy(alpha = 0.88f)),
                contentAlignment = Alignment.Center,
            ) {
                Icon(
                    imageVector = Icons.Rounded.Check,
                    contentDescription = null,
                    tint = MaterialTheme.colorScheme.onSurface,
                    modifier = Modifier.size(16.dp),
                )
            }
        }

        // Badge at bottom-end if dynamic: displays M3E or M3 clearly
        if (badge != ColorSwatchBadge.None) {
            val badgeText = when (badge) {
                ColorSwatchBadge.M3E, ColorSwatchBadge.Eyedropper -> "M3E"
                ColorSwatchBadge.M3, ColorSwatchBadge.Wallpaper -> "M3"
                ColorSwatchBadge.None -> ""
            }
            Box(
                modifier = Modifier
                    .align(Alignment.BottomEnd)
                    .clip(RoundedCornerShape(6.dp))
                    .background(MaterialTheme.colorScheme.surfaceContainerHighest)
                    .border(1.2.dp, MaterialTheme.colorScheme.surface, RoundedCornerShape(6.dp))
                    .padding(horizontal = 3.5.dp, vertical = 1.dp),
                contentAlignment = Alignment.Center,
            ) {
                Text(
                    text = badgeText,
                    style = MaterialTheme.typography.labelSmall.copy(
                        fontSize = 8.sp,
                        fontWeight = FontWeight.Black,
                        letterSpacing = 0.sp,
                    ),
                    color = MaterialTheme.colorScheme.primary,
                )
            }
        }
    }
}

@Composable
fun CustomColorAddButton(
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val haptics = rememberHaptics()
    Box(
        modifier = modifier
            .size(58.dp)
            .padding(4.dp)
            .clip(CircleShape)
            .background(MaterialTheme.colorScheme.surfaceContainerHigh)
            .clickable(role = Role.Button) {
                haptics.step()
                onClick()
            }
            .semantics { contentDescription = "自定义种子色彩" },
        contentAlignment = Alignment.Center,
    ) {
        Icon(
            imageVector = Icons.Rounded.Add,
            contentDescription = null,
            tint = MaterialTheme.colorScheme.onSurfaceVariant,
            modifier = Modifier.size(24.dp),
        )
    }
}

// -------------------------------------------------------------------------------------------------
// Custom Seed Color Dialog
// -------------------------------------------------------------------------------------------------

@Composable
fun CustomSeedColorDialog(
    initialColor: Color,
    onDismiss: () -> Unit,
    onConfirm: (Color) -> Unit,
) {
    CustomSeedColorContent(
        initialColor = initialColor,
        onDismiss = onDismiss,
        onConfirm = onConfirm,
        asDialog = true,
    )
}

@Composable
fun CustomSeedColorContent(
    initialColor: Color,
    onDismiss: () -> Unit,
    onConfirm: (Color) -> Unit,
    asDialog: Boolean = false,
) {
    val presetSeeds = listOf(
        Color(0xFFE53935), // Red
        Color(0xFFD81B60), // Pink
        Color(0xFF8E24AA), // Purple
        Color(0xFF5E35B1), // Deep Purple
        Color(0xFF3949AB), // Indigo
        Color(0xFF1E88E5), // Blue
        Color(0xFF00ACC1), // Cyan
        Color(0xFF00897B), // Teal
        Color(0xFF43A047), // Green
        Color(0xFFFDD835), // Yellow
        Color(0xFFFB8C00), // Orange
        Color(0xFF6D4C41), // Brown
    )

    var selectedColor by remember { mutableStateOf(initialColor) }
    var hexText by remember {
        val argb = initialColor.toArgb()
        mutableStateOf(String.format("%06X", 0xFFFFFF and argb))
    }

    val bodyContent = @Composable {
        Column(modifier = Modifier.fillMaxWidth()) {
            Text(
                text = "选择预设种子色彩或输入 HEX 颜色代码：",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
            Spacer(Modifier.height(16.dp))

            // Swatches row
            FlowRow(
                horizontalArrangement = Arrangement.spacedBy(10.dp),
                verticalArrangement = Arrangement.spacedBy(10.dp),
                modifier = Modifier.fillMaxWidth(),
            ) {
                presetSeeds.forEach { seed ->
                    val isChosen = selectedColor == seed
                    Box(
                        modifier = Modifier
                            .size(38.dp)
                            .clip(CircleShape)
                            .background(seed)
                            .clickable {
                                selectedColor = seed
                                val argb = seed.toArgb()
                                hexText = String.format("%06X", 0xFFFFFF and argb)
                            }
                            .then(
                                if (isChosen) {
                                    Modifier.border(2.dp, MaterialTheme.colorScheme.onSurface, CircleShape)
                                } else Modifier,
                            ),
                        contentAlignment = Alignment.Center,
                    ) {
                        if (isChosen) {
                            Icon(
                                imageVector = Icons.Rounded.Check,
                                contentDescription = null,
                                tint = Color.White,
                                modifier = Modifier.size(18.dp),
                            )
                        }
                    }
                }
            }

            Spacer(Modifier.height(16.dp))

            // Hex input field with preview box
            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Box(
                    modifier = Modifier
                        .size(44.dp)
                        .clip(RoundedCornerShape(8.dp))
                        .background(selectedColor)
                        .border(1.dp, MaterialTheme.colorScheme.outlineVariant, RoundedCornerShape(8.dp)),
                )
                Spacer(Modifier.width(12.dp))
                OutlinedTextField(
                    value = hexText,
                    onValueChange = { input ->
                        val sanitized = input.removePrefix("#").filter { it.isLetterOrDigit() }.take(6)
                        hexText = sanitized
                        if (sanitized.length == 6) {
                            runCatching {
                                val parsed = (sanitized.toLong(16) or 0xFF000000L).toInt()
                                selectedColor = Color(parsed)
                            }
                        }
                    },
                    label = { Text("HEX 代码") },
                    prefix = { Text("#") },
                    singleLine = true,
                    keyboardOptions = KeyboardOptions(imeAction = ImeAction.Done),
                    keyboardActions = KeyboardActions(onDone = { onConfirm(selectedColor) }),
                    modifier = Modifier.weight(1f),
                )
            }
        }
    }

    if (asDialog) {
        AlertDialog(
            onDismissRequest = onDismiss,
            title = {
                Text(text = "自定义色彩", style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold)
            },
            text = bodyContent,
            confirmButton = {
                Button(onClick = { onConfirm(selectedColor) }) {
                    Text("应用")
                }
            },
            dismissButton = {
                TextButton(onClick = onDismiss) {
                    Text("取消")
                }
            },
        )
    } else {
        Column(modifier = Modifier.padding(16.dp)) {
            Text(text = "自定义色彩", style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold)
            Spacer(Modifier.height(12.dp))
            bodyContent()
            Spacer(Modifier.height(16.dp))
            Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.End) {
                TextButton(onClick = onDismiss) {
                    Text("取消")
                }
                Spacer(Modifier.width(8.dp))
                Button(onClick = { onConfirm(selectedColor) }) {
                    Text("应用")
                }
            }
        }
    }
}

@Preview(name = "个性化 · 浅色", showBackground = true)
@Composable
private fun SettingsAppearanceScreenPreview() {
    NocturnTheme {
        SettingsAppearanceScreen(state = PreviewData.settingsState)
    }
}
