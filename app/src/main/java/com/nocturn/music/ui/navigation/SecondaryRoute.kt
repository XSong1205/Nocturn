package com.nocturn.music.ui.navigation

import kotlinx.serialization.Serializable
import top.yukonga.miuix.kmp.nav.core.NavKey

@Serializable
sealed interface SecondaryRoute : NavKey {
    @Serializable
    data class Playlist(
        val id: Long,
        val initialName: String? = null,
        val initialCoverUrl: String? = null
    ) : SecondaryRoute

    @Serializable
    data class Album(
        val id: Long,
        val initialName: String? = null,
        val initialCoverUrl: String? = null
    ) : SecondaryRoute

    @Serializable
    data class Artist(
        val id: Long,
        val name: String,
        val avatarUrl: String? = null
    ) : SecondaryRoute

    @Serializable
    data object Favorites : SecondaryRoute

    @Serializable
    data object DailyRecommend : SecondaryRoute

    @Serializable
    data object TopChartsSquare : SecondaryRoute

    @Serializable
    data object About : SecondaryRoute
}
