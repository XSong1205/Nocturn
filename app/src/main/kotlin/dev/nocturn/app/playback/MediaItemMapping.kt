package dev.nocturn.app.playback

import android.net.Uri
import androidx.media3.common.MediaItem
import androidx.media3.common.MediaMetadata

/**
 * Pure translation between the playback domain model and Media3 media items.
 *
 * Nothing in this file touches a player or a controller, so URL sanitising, media id parsing and
 * queue index bookkeeping can be unit tested without starting [PlaybackService].
 */
internal object MediaItemMapping {
    /** The playable subset of a requested queue plus the index playback should start at. */
    data class ResolvedQueue(
        val items: List<MediaItem>,
        val startIndex: Int,
        val skipped: Int,
    )

    /** Media3 media id for a domain song; [songIdOf] reverses it. */
    fun mediaIdFor(songId: Long): String = songId.toString()

    fun songIdOf(mediaId: String?): Long? =
        mediaId?.trim()?.toLongOrNull()?.takeIf { it > 0L }

    /**
     * Sanitises a stream or artwork URL. Only HTTP(S) is usable; cleartext URLs are upgraded to
     * HTTPS so the app never relaxes its cleartext policy for media. Anything else is rejected.
     */
    fun normalizeUrl(rawUrl: String?): String? {
        val trimmed = rawUrl?.trim().orEmpty()
        if (trimmed.isEmpty()) return null
        val uri = runCatching { Uri.parse(trimmed) }.getOrNull() ?: return null
        if (uri.host.isNullOrBlank()) return null
        return when (uri.scheme?.lowercase()) {
            "https" -> uri.toString()
            "http" -> uri.buildUpon().scheme("https").build().toString()
            else -> null
        }
    }

    /** Builds a playable item, or returns null when the entry has no usable stream URL. */
    fun buildMediaItem(song: PlayableSong): MediaItem? {
        val streamUrl = normalizeUrl(song.url) ?: return null
        val metadata = MediaMetadata.Builder()
            .setTitle(song.title)
            .setArtist(song.artist)
            .setIsBrowsable(false)
            .setIsPlayable(true)
            .apply {
                song.album?.takeIf { it.isNotBlank() }?.let { album -> setAlbumTitle(album) }
                normalizeUrl(song.coverUrl)?.let { cover -> setArtworkUri(Uri.parse(cover)) }
                if (song.durationMs > 0L) setDurationMs(song.durationMs)
            }
            .build()
        return MediaItem.Builder()
            .setMediaId(mediaIdFor(song.id))
            .setUri(streamUrl)
            .setMediaMetadata(metadata)
            .build()
    }

    /**
     * Builds the playable subset of [songs] and remaps [startIndex] onto it.
     *
     * Entries without a usable stream URL are dropped. If the requested start entry is dropped,
     * the nearest playable entry after it is used, falling back to the first playable entry.
     */
    fun resolveQueue(songs: List<PlayableSong>, startIndex: Int): ResolvedQueue {
        if (songs.isEmpty()) return ResolvedQueue(emptyList(), -1, 0)
        val requested = startIndex.coerceIn(0, songs.lastIndex)
        val kept = ArrayList<Pair<Int, MediaItem>>(songs.size)
        songs.forEachIndexed { index, song ->
            buildMediaItem(song)?.let { item -> kept += index to item }
        }
        if (kept.isEmpty()) return ResolvedQueue(emptyList(), -1, songs.size)
        val exact = kept.indexOfFirst { it.first == requested }.takeIf { it >= 0 }
        val after = kept.indexOfFirst { it.first > requested }.takeIf { it >= 0 }
        return ResolvedQueue(
            items = kept.map { it.second },
            startIndex = exact ?: after ?: 0,
            skipped = songs.size - kept.size,
        )
    }
}
