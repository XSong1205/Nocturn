package com.nocturn.music.ui.navigation

sealed interface SecondaryRoute {
    data class Playlist(
        val id: Long,
        val initialName: String? = null,
        val initialCoverUrl: String? = null
    ) : SecondaryRoute

    data class Album(
        val id: Long,
        val initialName: String? = null,
        val initialCoverUrl: String? = null
    ) : SecondaryRoute

    data class Artist(
        val id: Long,
        val name: String,
        val avatarUrl: String? = null
    ) : SecondaryRoute

    data object Favorites : SecondaryRoute

    data object DailyRecommend : SecondaryRoute

    data object TopChartsSquare : SecondaryRoute

    data object About : SecondaryRoute
}
