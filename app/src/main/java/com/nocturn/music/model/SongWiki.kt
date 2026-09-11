package com.nocturn.music.model

import kotlinx.serialization.Serializable

/**
 * 音乐百科实体模型 (Song Wiki / Encyclopedia)
 */
@Serializable
data class SongWiki(
    val songId: Long = 0L,
    val title: String = "",
    val artist: String = "",
    val album: String = "",
    val styles: List<String> = emptyList(),
    val description: String = "",
    val credits: List<Pair<String, String>> = emptyList(),
    val language: String = "",
    val publishTime: String = "",
    val bpm: Int = 0,
    val key: String = ""
)
