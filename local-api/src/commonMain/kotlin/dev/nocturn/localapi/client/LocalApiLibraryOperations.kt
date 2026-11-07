package dev.nocturn.localapi.client

import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.put

enum class PlaylistOrder(val value: String) { Hot("hot"), New("new") }

enum class ArtistSongOrder(val value: String) { Hot("hot"), Time("time") }

enum class PlaylistType(val value: String) { Normal("NORMAL"), Video("VIDEO"), Shared("SHARED") }

enum class PlaylistTrackOperation(val value: String) { Add("add"), Delete("del") }

suspend fun LocalApiClient.playlistCategories(): JsonObject = request(LocalApiEndpoint.PlaylistCategories)

suspend fun LocalApiClient.hotPlaylistCategories(): JsonObject = request(LocalApiEndpoint.HotPlaylistCategories)

suspend fun LocalApiClient.highQualityPlaylistTags(): JsonObject = request(LocalApiEndpoint.HighQualityPlaylistTags)

suspend fun LocalApiClient.topPlaylists(
    category: String = "全部",
    order: PlaylistOrder = PlaylistOrder.Hot,
    page: ApiPage = ApiPage(),
    cookie: String? = null,
): JsonObject {
    requireLibraryText(category, 40, "playlist category")
    return request(LocalApiEndpoint.TopPlaylists, buildJsonObject {
        put("cat", category)
        put("order", order.value)
        put("limit", page.limit)
        put("offset", page.offset)
        cookie?.let { put("cookie", it) }
    })
}

/** [before] is the last playlist's updateTime cursor, not an item offset. */
suspend fun LocalApiClient.highQualityPlaylists(
    category: String = "全部",
    limit: Int = 50,
    before: Long? = null,
    cookie: String? = null,
): JsonObject {
    requireLibraryText(category, 40, "playlist category")
    require(limit in 1..200) { "Page limit must be between 1 and 200." }
    require(before == null || before in 0..9_007_199_254_740_991L) { "A valid timestamp cursor is required." }
    return request(LocalApiEndpoint.HighQualityPlaylists, buildJsonObject {
        put("cat", category)
        put("limit", limit)
        before?.let { put("before", it.toString()) }
        cookie?.let { put("cookie", it) }
    })
}

/** Creates a playlist only when explicitly called; credentials are never persisted here. */
suspend fun LocalApiClient.createPlaylist(
    name: String,
    privatePlaylist: Boolean = false,
    type: PlaylistType = PlaylistType.Normal,
    cookie: String,
): JsonObject {
    requireLibraryText(name, 40, "playlist name")
    requireLibraryCookie(cookie)
    return request(LocalApiEndpoint.PlaylistCreate, buildJsonObject {
        put("name", name)
        put("privacy", if (privatePlaylist) 10 else 0)
        put("type", type.value)
        put("cookie", cookie)
    })
}

suspend fun LocalApiClient.deletePlaylists(ids: List<Long>, cookie: String): JsonObject {
    requireLibraryIds(ids)
    requireLibraryCookie(cookie)
    return request(LocalApiEndpoint.PlaylistDelete, buildJsonObject {
        put("id", ids.joinToString(","))
        put("cookie", cookie)
    })
}

/** A single explicit add/delete action; callers decide whether a failed mutation may be retried. */
suspend fun LocalApiClient.updatePlaylistTracks(
    id: Long,
    songIds: List<Long>,
    operation: PlaylistTrackOperation,
    cookie: String,
): JsonObject {
    require(id > 0) { "A positive playlist ID is required." }
    requireLibraryIds(songIds)
    requireLibraryCookie(cookie)
    return request(LocalApiEndpoint.PlaylistTracks, buildJsonObject {
        put("pid", id.toString())
        put("tracks", songIds.joinToString(","))
        put("op", operation.value)
        put("cookie", cookie)
    })
}

suspend fun LocalApiClient.renamePlaylist(id: Long, name: String, cookie: String): JsonObject {
    require(id > 0) { "A positive playlist ID is required." }
    requireLibraryText(name, 40, "playlist name")
    requireLibraryCookie(cookie)
    return request(LocalApiEndpoint.PlaylistNameUpdate, buildJsonObject {
        put("id", id.toString())
        put("name", name)
        put("cookie", cookie)
    })
}

/** An empty [description] clears the existing description. Text is preserved verbatim. */
suspend fun LocalApiClient.updatePlaylistDescription(id: Long, description: String, cookie: String): JsonObject {
    require(id > 0) { "A positive playlist ID is required." }
    require(description.length <= 1000) { "Playlist descriptions must contain at most 1000 characters." }
    requireLibraryCookie(cookie)
    return request(LocalApiEndpoint.PlaylistDescriptionUpdate, buildJsonObject {
        put("id", id.toString())
        put("desc", description)
        put("cookie", cookie)
    })
}

/** An empty [tags] list clears the existing tags. Semicolons delimit upstream tag values. */
suspend fun LocalApiClient.updatePlaylistTags(id: Long, tags: List<String>, cookie: String): JsonObject {
    require(id > 0) { "A positive playlist ID is required." }
    require(tags.size <= 3) { "Supply at most three playlist tags." }
    tags.forEach {
        requireLibraryText(it, 40, "playlist tag")
        require(';' !in it) { "Playlist tags cannot contain semicolons." }
    }
    requireLibraryCookie(cookie)
    return request(LocalApiEndpoint.PlaylistTagsUpdate, buildJsonObject {
        put("id", id.toString())
        put("tags", tags.joinToString(";"))
        put("cookie", cookie)
    })
}

suspend fun LocalApiClient.subscribePlaylist(id: Long, subscribed: Boolean = true, cookie: String): JsonObject =
    librarySubscription(LocalApiEndpoint.PlaylistSubscribe, id, subscribed, cookie)

suspend fun LocalApiClient.subscribeAlbum(id: Long, subscribed: Boolean = true, cookie: String): JsonObject =
    librarySubscription(LocalApiEndpoint.AlbumSubscribe, id, subscribed, cookie)

suspend fun LocalApiClient.subscribeArtist(id: Long, subscribed: Boolean = true, cookie: String): JsonObject =
    librarySubscription(LocalApiEndpoint.ArtistSubscribe, id, subscribed, cookie)

suspend fun LocalApiClient.subscribedAlbums(cookie: String, page: ApiPage = ApiPage(25)): JsonObject =
    libraryCollection(LocalApiEndpoint.SubscribedAlbums, cookie, page)

suspend fun LocalApiClient.subscribedArtists(cookie: String, page: ApiPage = ApiPage(25)): JsonObject =
    libraryCollection(LocalApiEndpoint.SubscribedArtists, cookie, page)

suspend fun LocalApiClient.artistSongs(
    id: Long,
    order: ArtistSongOrder = ArtistSongOrder.Hot,
    page: ApiPage = ApiPage(),
    cookie: String? = null,
): JsonObject {
    require(id > 0) { "A positive artist ID is required." }
    return request(LocalApiEndpoint.ArtistSongs, buildJsonObject {
        put("id", id.toString())
        put("order", order.value)
        put("limit", page.limit)
        put("offset", page.offset)
        cookie?.let { put("cookie", it) }
    })
}

private suspend fun LocalApiClient.librarySubscription(
    endpoint: LocalApiEndpoint,
    id: Long,
    subscribed: Boolean,
    cookie: String,
): JsonObject {
    require(id > 0) { "A positive ID is required." }
    requireLibraryCookie(cookie)
    return request(endpoint, buildJsonObject {
        put("id", id.toString())
        put("t", if (subscribed) 1 else 2)
        put("cookie", cookie)
    })
}

private suspend fun LocalApiClient.libraryCollection(
    endpoint: LocalApiEndpoint,
    cookie: String,
    page: ApiPage,
): JsonObject {
    requireLibraryCookie(cookie)
    return request(endpoint, buildJsonObject {
        put("limit", page.limit)
        put("offset", page.offset)
        put("cookie", cookie)
    })
}

private fun requireLibraryText(value: String, maxLength: Int, label: String) {
    require(value.isNotBlank() && value.length <= maxLength) { "A $label of 1 to $maxLength characters is required." }
}

private fun requireLibraryIds(ids: List<Long>) {
    require(ids.isNotEmpty() && ids.size <= 200 && ids.all { it > 0 }) {
        "Supply between 1 and 200 positive IDs."
    }
}

private fun requireLibraryCookie(cookie: String) {
    require(cookie.isNotBlank()) { "An account cookie is required." }
}
