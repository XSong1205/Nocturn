package dev.nocturn.app.ui.state

import dev.nocturn.app.data.model.ThemeMode
import dev.nocturn.localapi.client.AudioQuality

/**
 * State representing settings across both the root settings menu and all secondary sub-menus.
 */
data class SettingsUiState(
    val quality: AudioQuality = AudioQuality.Exhigh,
    val themeMode: ThemeMode = ThemeMode.System,
    val dynamicColor: Boolean = true,
    val hapticsEnabled: Boolean = true,
    val lyriconEnabled: Boolean = true,
    val searchHistoryCount: Int = 0,
    val cacheSizeBytes: Long = 0L,
    val isClearing: Boolean = false,
    val version: String = "0.1.28",
)
