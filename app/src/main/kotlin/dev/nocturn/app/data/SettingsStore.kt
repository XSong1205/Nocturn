package dev.nocturn.app.data

import android.content.Context
import dev.nocturn.app.data.model.ThemeMode
import dev.nocturn.localapi.client.AudioQuality

/**
 * Small, unencrypted preferences: appearance, preferred audio quality and local search
 * history. Account credentials never live here (see [SessionStore]).
 */
class SettingsStore(context: Context) {
    private val preferences = context.applicationContext
        .getSharedPreferences("nocturn-settings", Context.MODE_PRIVATE)

    var themeMode: ThemeMode
        get() = runCatching { ThemeMode.valueOf(preferences.getString(KEY_THEME, null) ?: "") }
            .getOrDefault(ThemeMode.System)
        set(value) {
            preferences.edit().putString(KEY_THEME, value.name).apply()
        }

    /** Android 12+ dynamic color (Material You / Monet); on by default. */
    var dynamicColor: Boolean
        get() = preferences.getBoolean(KEY_DYNAMIC_COLOR, true)
        set(value) {
            preferences.edit().putBoolean(KEY_DYNAMIC_COLOR, value).apply()
        }

    var quality: AudioQuality
        get() = runCatching { AudioQuality.valueOf(preferences.getString(KEY_QUALITY, null) ?: "") }
            .getOrDefault(AudioQuality.Exhigh)
        set(value) {
            preferences.edit().putString(KEY_QUALITY, value.name).apply()
        }

    /** Global tactile feedback switch; on by default to match the platform. */
    var hapticsEnabled: Boolean
        get() = preferences.getBoolean(KEY_HAPTICS, true)
        set(value) {
            preferences.edit().putBoolean(KEY_HAPTICS, value).apply()
        }

    fun searchHistory(limit: Int = 10): List<String> {
        val raw = preferences.getString(KEY_HISTORY, null) ?: return emptyList()
        return raw.split('\n').map { it.trim() }.filter { it.isNotEmpty() }.take(limit)
    }

    fun recordSearch(keyword: String) {
        val value = keyword.trim().take(80)
        if (value.isEmpty()) return
        val next = (listOf(value) + searchHistory().filterNot { it.equals(value, ignoreCase = true) })
            .take(10)
        preferences.edit().putString(KEY_HISTORY, next.joinToString("\n")).apply()
    }

    fun clearSearchHistory() {
        preferences.edit().remove(KEY_HISTORY).apply()
    }

    private companion object {
        const val KEY_THEME = "theme-mode"
        const val KEY_DYNAMIC_COLOR = "dynamic-color"
        const val KEY_QUALITY = "audio-quality"
        const val KEY_HAPTICS = "haptics-enabled"
        const val KEY_HISTORY = "search-history"
    }
}
