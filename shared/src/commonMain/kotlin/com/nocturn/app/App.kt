package com.nocturn.app

import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.graphics.Color
import top.yukonga.miuix.kmp.theme.ColorSchemeMode
import top.yukonga.miuix.kmp.theme.MiuixTheme
import top.yukonga.miuix.kmp.theme.ThemeController

@Composable
fun App() {
    val controller = remember {
        ThemeController(
            ColorSchemeMode.MonetSystem,
            keyColor = Color(0xFF3482FF),
        )
    }
    var showOnboarding by remember { mutableStateOf(true) }

    MiuixTheme(controller = controller) {
        if (showOnboarding) {
            OnboardingScreen(onStart = { showOnboarding = false })
        } else {
            LoginScreen()
        }
    }
}
