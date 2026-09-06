package com.nocturn.music.model

import androidx.compose.runtime.Immutable
import kotlinx.serialization.Serializable

@Immutable
@Serializable
data class Artist(
    val id: Long,
    val name: String,
    val avatarUrl: String = "",
    val musicSize: Int = 0,
    val albumSize: Int = 0
)

@Immutable
@Serializable
data class Album(
    val id: Long,
    val name: String,
    val coverUrl: String = "",
    val artistName: String = "",
    val publishTimeMs: Long = 0L,
    val size: Int = 0
)

@Immutable
@Serializable
data class BannerItem(
    val picUrl: String,
    val titleColor: String = "red",
    val typeTitle: String = "热点",
    val targetId: Long = 0L,
    val url: String = ""
)
