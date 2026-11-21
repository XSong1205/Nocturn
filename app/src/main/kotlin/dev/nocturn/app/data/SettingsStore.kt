package dev.nocturn.app.data

import android.content.Context
import dev.nocturn.app.data.model.ColorSchemeKey
import dev.nocturn.app.data.model.DynamicVariant
import dev.nocturn.app.data.model.ThemeMode
import dev.nocturn.app.data.model.isDynamic
import dev.nocturn.localapi.client.AudioQuality
import dev.nocturn.localapi.client.ImmerseType

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

    var colorSchemeKey: ColorSchemeKey
        get() = preferences.getString(KEY_COLOR_SCHEME, null)?.let {
            runCatching { ColorSchemeKey.valueOf(it) }.getOrNull()
        } ?: if (preferences.getBoolean(KEY_DYNAMIC_COLOR, true)) {
            ColorSchemeKey.M3eDynamic
        } else {
            ColorSchemeKey.NocturnClassic
        }
        set(value) {
            preferences.edit()
                .putString(KEY_COLOR_SCHEME, value.name)
                .putBoolean(KEY_DYNAMIC_COLOR, value.isDynamic)
                .apply()
        }

    /** Android 12+ dynamic color (Material You / Monet); on by default. */
    var dynamicColor: Boolean
        get() = colorSchemeKey.isDynamic
        set(value) {
            preferences.edit().putBoolean(KEY_DYNAMIC_COLOR, value).apply()
            if (!value && colorSchemeKey.isDynamic) {
                colorSchemeKey = ColorSchemeKey.NocturnClassic
            } else if (value && !colorSchemeKey.isDynamic) {
                colorSchemeKey = ColorSchemeKey.M3eDynamic
            }
        }

    var customSeedColor: Long
        get() = preferences.getLong(KEY_CUSTOM_SEED_COLOR, 0xFF6750A4L)
        set(value) {
            preferences.edit().putLong(KEY_CUSTOM_SEED_COLOR, value).apply()
        }

    var dynamicVariant: DynamicVariant
        get() = runCatching {
            DynamicVariant.valueOf(preferences.getString(KEY_DYNAMIC_VARIANT, null) ?: "")
        }.getOrDefault(DynamicVariant.Expressive)
        set(value) {
            preferences.edit().putString(KEY_DYNAMIC_VARIANT, value.name).apply()
        }

    var quality: AudioQuality
        get() = runCatching { AudioQuality.valueOf(preferences.getString(KEY_QUALITY, null) ?: "") }
            .getOrDefault(AudioQuality.Exhigh)
        set(value) {
            preferences.edit().putString(KEY_QUALITY, value.name).apply()
        }

    var immerseType: ImmerseType
        get() = runCatching { ImmerseType.valueOf(preferences.getString(KEY_IMMERSE_TYPE, null) ?: "") }
            .getOrDefault(ImmerseType.C51)
        set(value) {
            preferences.edit().putString(KEY_IMMERSE_TYPE, value.name).apply()
        }

    var unblockEnabled: Boolean
        get() = preferences.getBoolean(KEY_UNBLOCK_ENABLED, false)
        set(value) {
            preferences.edit().putBoolean(KEY_UNBLOCK_ENABLED, value).apply()
        }

    /** Global tactile feedback switch; on by default to match the platform. */
    var hapticsEnabled: Boolean
        get() = preferences.getBoolean(KEY_HAPTICS, true)
        set(value) {
            preferences.edit().putBoolean(KEY_HAPTICS, value).apply()
        }

    /** Status bar lyric provider integration (Lyricon); on by default. */
    var lyriconEnabled: Boolean
        get() = preferences.getBoolean(KEY_LYRICON_ENABLED, true)
        set(value) {
            preferences.edit().putBoolean(KEY_LYRICON_ENABLED, value).apply()
        }

    /** Auto-submit listen logs (scrobble) to update play counts/rankings; on by default. */
    var scrobbleEnabled: Boolean
        get() = preferences.getBoolean(KEY_SCROBBLE_ENABLED, true)
        set(value) {
            preferences.edit().putBoolean(KEY_SCROBBLE_ENABLED, value).apply()
        }

    /** Relay play state & session tracking for real-time play status; on by default. */
    var relayPlayStateEnabled: Boolean
        get() = preferences.getBoolean(KEY_RELAY_PLAY_STATE_ENABLED, true)
        set(value) {
            preferences.edit().putBoolean(KEY_RELAY_PLAY_STATE_ENABLED, value).apply()
        }

    /** Auto-claim free listen time at launch and when remaining time is low (<15min). */
    var autoClaimFreeListen: Boolean
        get() = preferences.getBoolean(KEY_AUTO_CLAIM_FREE_LISTEN, false)
        set(value) {
            preferences.edit().putBoolean(KEY_AUTO_CLAIM_FREE_LISTEN, value).apply()
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

    internal companion object {
        const val KEY_THEME = "theme-mode"
        const val KEY_DYNAMIC_COLOR = "dynamic-color"
        const val KEY_COLOR_SCHEME = "color-scheme-key"
        const val KEY_CUSTOM_SEED_COLOR = "custom-seed-color"
        const val KEY_DYNAMIC_VARIANT = "dynamic-variant"
        const val KEY_QUALITY = "audio-quality"
        const val KEY_IMMERSE_TYPE = "audio-immerse-type"
        const val KEY_UNBLOCK_ENABLED = "audio-unblock-enabled"
        const val KEY_HAPTICS = "haptics-enabled"
        const val KEY_LYRICON_ENABLED = "lyricon-enabled"
        const val KEY_SCROBBLE_ENABLED = "scrobble-enabled"
        const val KEY_RELAY_PLAY_STATE_ENABLED = "relay-play-state-enabled"
        const val KEY_AUTO_CLAIM_FREE_LISTEN = "auto-claim-free-listen"
        const val KEY_HISTORY = "search-history"
    }
}
