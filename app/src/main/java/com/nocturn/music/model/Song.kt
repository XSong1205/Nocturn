package com.nocturn.music.model

import androidx.compose.runtime.Immutable
import kotlinx.serialization.Serializable

@Immutable
@Serializable
data class Song(
    val id: Long,
    val title: String,
    val artist: String,
    val album: String = "",
    val coverUrl: String = "",
    val durationMs: Long = 0L,
    val isVip: Boolean = false,
    val streamUrl: String = ""
) {
    val durationFormatted: String
        get() {
            val totalSeconds = durationMs / 1000
            val minutes = totalSeconds / 60
            val seconds = totalSeconds % 60
            return "%02d:%02d".format(minutes, seconds)
        }

    fun effectivePlayUrl(customApiBaseUrl: String = ""): String {
        return if (streamUrl.isNotBlank()) {
            streamUrl
        } else {
            "https://music.163.com/song/media/outer/url?id=$id.mp3"
        }
    }
}
