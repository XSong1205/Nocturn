package com.nocturn.music.data.repository

import android.content.Context
import com.nocturn.music.NocturnApp
import com.nocturn.music.model.ApiMode
import com.nocturn.music.model.AudioQuality
import com.nocturn.music.model.Song
import com.nocturn.music.model.UserProfile
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json
import org.json.JSONArray

object SettingsRepository {
    private val prefs by lazy {
        NocturnApp.appContext.getSharedPreferences("nocturn_prefs", Context.MODE_PRIVATE)
    }

    private val json = Json { ignoreUnknownKeys = true }

    private val _themeMode = MutableStateFlow(prefs.getInt("theme_mode", 0))
    val themeMode = _themeMode.asStateFlow()

    private val _apiMode = MutableStateFlow(
        try {
            ApiMode.valueOf(prefs.getString("api_mode", ApiMode.EMBEDDED.name) ?: ApiMode.EMBEDDED.name)
        } catch (e: Exception) {
            ApiMode.EMBEDDED
        }
    )
    val apiMode = _apiMode.asStateFlow()

    private val _customApiUrl = MutableStateFlow(
        prefs.getString("custom_api_url", "https://ncmapi.rpixel.online") ?: "https://ncmapi.rpixel.online"
    )
    val customApiUrl = _customApiUrl.asStateFlow()

    private val _audioQuality = MutableStateFlow(
        AudioQuality.valueOf(prefs.getString("audio_quality", AudioQuality.HIGH.name) ?: AudioQuality.HIGH.name)
    )
    val audioQuality = _audioQuality.asStateFlow()

    private val _userProfile = MutableStateFlow(loadUserProfile())
    val userProfile = _userProfile.asStateFlow()

    private val _favoriteSongs = MutableStateFlow(loadFavoriteSongs())
    val favoriteSongs = _favoriteSongs.asStateFlow()

    private val _searchHistory = MutableStateFlow(loadSearchHistory())
    val searchHistory = _searchHistory.asStateFlow()

    private val _isOobeCompleted = MutableStateFlow(prefs.getBoolean("oobe_completed", false))
    val isOobeCompleted = _isOobeCompleted.asStateFlow()

    fun setOobeCompleted(completed: Boolean) {
        _isOobeCompleted.value = completed
        prefs.edit().putBoolean("oobe_completed", completed).apply()
    }

    private val _lyriconEnabled = MutableStateFlow(prefs.getBoolean("lyricon_enabled", true))
    val lyriconEnabled: kotlinx.coroutines.flow.StateFlow<Boolean> = _lyriconEnabled.asStateFlow()

    fun setLyriconEnabled(enabled: Boolean) {
        _lyriconEnabled.value = enabled
        prefs.edit().putBoolean("lyricon_enabled", enabled).apply()
    }

    fun setThemeMode(mode: Int) {
        _themeMode.value = mode
        prefs.edit().putInt("theme_mode", mode).apply()
    }

    fun setApiMode(mode: ApiMode) {
        _apiMode.value = mode
        prefs.edit().putString("api_mode", mode.name).apply()
    }

    fun setCustomApiUrl(url: String) {
        val trimmed = url.trim()
        _customApiUrl.value = trimmed
        prefs.edit().putString("custom_api_url", trimmed).apply()
    }

    fun setAudioQuality(quality: AudioQuality) {
        _audioQuality.value = quality
        prefs.edit().putString("audio_quality", quality.name).apply()
    }

    fun saveUserProfile(profile: UserProfile) {
        _userProfile.value = profile
        prefs.edit().putString("user_profile", json.encodeToString(profile)).apply()
    }

    fun clearUserProfile() {
        val empty = UserProfile()
        _userProfile.value = empty
        prefs.edit().remove("user_profile").apply()
    }

    fun isFavorite(songId: Long): Boolean {
        return _favoriteSongs.value.any { it.id == songId }
    }

    fun setFavoriteSongs(songs: List<Song>) {
        _favoriteSongs.value = songs
        saveFavoritesToDisk(songs)
    }

    fun toggleFavorite(song: Song) {
        val current = _favoriteSongs.value.toMutableList()
        val index = current.indexOfFirst { it.id == song.id }
        if (index >= 0) {
            current.removeAt(index)
        } else {
            current.add(0, song)
        }
        _favoriteSongs.value = current
        saveFavoritesToDisk(current)
    }

    fun addSearchHistory(keyword: String) {
        val trimmed = keyword.trim()
        if (trimmed.isBlank()) return
        val current = _searchHistory.value.toMutableList()
        current.remove(trimmed)
        current.add(0, trimmed)
        if (current.size > 20) {
            current.removeAt(current.size - 1)
        }
        _searchHistory.value = current
        saveHistoryToDisk(current)
    }

    fun clearSearchHistory() {
        _searchHistory.value = emptyList()
        prefs.edit().remove("search_history").apply()
    }

    private fun loadUserProfile(): UserProfile {
        val raw = prefs.getString("user_profile", null) ?: return UserProfile()
        return try {
            json.decodeFromString(raw)
        } catch (e: Exception) {
            UserProfile()
        }
    }

    private fun loadFavoriteSongs(): List<Song> {
        val raw = prefs.getString("favorite_songs", null) ?: return emptyList()
        return try {
            json.decodeFromString(raw)
        } catch (e: Exception) {
            emptyList()
        }
    }

    private fun saveFavoritesToDisk(list: List<Song>) {
        try {
            prefs.edit().putString("favorite_songs", json.encodeToString(list)).apply()
        } catch (e: Exception) {
            e.printStackTrace()
        }
    }

    private fun loadSearchHistory(): List<String> {
        val raw = prefs.getString("search_history", null) ?: return emptyList()
        return try {
            val arr = JSONArray(raw)
            val list = mutableListOf<String>()
            for (i in 0 until arr.length()) {
                list.add(arr.getString(i))
            }
            list
        } catch (e: Exception) {
            emptyList()
        }
    }

    private fun saveHistoryToDisk(list: List<String>) {
        val arr = JSONArray()
        list.forEach { arr.put(it) }
        prefs.edit().putString("search_history", arr.toString()).apply()
    }
}
