package com.nocturn.music.model

import androidx.compose.runtime.Immutable
import kotlinx.serialization.Serializable

@Immutable
@Serializable
data class Playlist(
    val id: Long,
    val name: String,
    val coverUrl: String = "",
    val playCount: Long = 0L,
    val trackCount: Int = 0,
    val description: String = "",
    val creatorName: String = "",
    val creatorAvatarUrl: String = "",
    val tags: List<String> = emptyList(),
    val tracks: List<Song> = emptyList()
) {
    val playCountFormatted: String
        get() = when {
            playCount >= 100_000_000 -> "%.1f亿".format(playCount / 100_000_000f)
            playCount >= 10_000 -> "%.1f万".format(playCount / 10_000f)
            else -> playCount.toString()
        }
}
