package com.nocturn.music.ui.theme

import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import com.nocturn.music.data.repository.SettingsRepository
import top.yukonga.miuix.kmp.squircle.squircleClip
import top.yukonga.miuix.kmp.theme.MiuixTheme
import top.yukonga.miuix.kmp.theme.darkColorScheme
import top.yukonga.miuix.kmp.theme.lightColorScheme

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
    val isSystemDark = isSystemInDarkTheme()

    val isDark = when (themeMode) {
        1 -> false // Light
        2 -> true  // Dark
        else -> isSystemDark
    }

    val colorScheme = if (isDark) darkColorScheme() else lightColorScheme()

    MiuixTheme(colors = colorScheme) {
        content()
    }
}
