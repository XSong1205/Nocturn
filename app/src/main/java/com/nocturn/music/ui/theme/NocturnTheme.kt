package com.nocturn.music.ui.theme

import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import com.nocturn.music.data.repository.SettingsRepository
import top.yukonga.miuix.kmp.squircle.squircleClip
import top.yukonga.miuix.kmp.theme.ColorSchemeMode
import top.yukonga.miuix.kmp.theme.MiuixTheme
import top.yukonga.miuix.kmp.theme.ThemeController

val NocturnCornerRadius: Dp = 20.dp
val NocturnLargeRadius: Dp = 28.dp
val NocturnSmallRadius: Dp = 12.dp

val HyperBlue = Color(0xFF0F6BFF)
val HyperRed = Color(0xFFFA3B3B)
val HyperDarkBackground = Color(0xFF141416)
val HyperLightBackground = Color(0xFFF7F8FA)

@Composable
fun Modifier.squircleCard(radius: Dp = NocturnCornerRadius): Modifier =
    this.squircleClip(cornerRadius = radius)

@Composable
fun NocturnTheme(
    content: @Composable () -> Unit
) {
    val themeMode by SettingsRepository.themeMode.collectAsState()

    val colorSchemeMode = when (themeMode) {
        1 -> ColorSchemeMode.Light
        2 -> ColorSchemeMode.Dark
        else -> ColorSchemeMode.System
    }

    val controller = remember(colorSchemeMode) {
        ThemeController(colorSchemeMode)
    }

    MiuixTheme(controller = controller) {
        content()
    }
}
