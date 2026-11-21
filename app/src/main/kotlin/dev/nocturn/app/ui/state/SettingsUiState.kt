package dev.nocturn.app.ui.state

import dev.nocturn.app.data.model.ColorSchemeKey
import dev.nocturn.app.data.model.DynamicVariant
import dev.nocturn.app.data.model.ThemeMode
import dev.nocturn.localapi.client.AudioQuality
import dev.nocturn.localapi.client.ImmerseType

/**
 * State representing settings across both the root settings menu and all secondary sub-menus.
 */
data class SettingsUiState(
    val quality: AudioQuality = AudioQuality.Exhigh,
    val immerseType: ImmerseType = ImmerseType.C51,
    val unblockEnabled: Boolean = false,
    val themeMode: ThemeMode = ThemeMode.System,
    val colorSchemeKey: ColorSchemeKey = ColorSchemeKey.M3eDynamic,
    val customSeedColor: Long = 0xFF6750A4L,
    val dynamicVariant: DynamicVariant = DynamicVariant.Expressive,
    val dynamicColor: Boolean = true,
    val hapticsEnabled: Boolean = true,
    val lyriconEnabled: Boolean = true,
    val scrobbleEnabled: Boolean = true,
    val relayPlayStateEnabled: Boolean = true,
    val searchHistoryCount: Int = 0,
    val cacheSizeBytes: Long = 0L,
    val isClearing: Boolean = false,
    val version: String = "0.1.28",
)
