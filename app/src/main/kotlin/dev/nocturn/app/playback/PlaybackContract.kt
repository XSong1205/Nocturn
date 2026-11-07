package dev.nocturn.app.playback

import dev.nocturn.app.data.model.Song
import kotlinx.coroutines.flow.StateFlow

enum class RepeatMode { Off, All, One }

/** A resolved, playable queue entry. The URL is an HTTP(S) stream produced by `song/url/v1`. */
data class PlayableSong(
    val id: Long,
    val title: String,
    val artist: String,
    val album: String?,
    val coverUrl: String?,
    val durationMs: Long,
    val url: String,
) {
    companion object {
        fun from(song: Song, url: String) = PlayableSong(
            id = song.id,
            title = song.name,
            artist = song.artistText,
            album = song.album?.name,
            coverUrl = song.cover,
            durationMs = song.durationMs,
            url = url,
        )
    }
}

/** The complete observable state of the media session, mirrored from Media3 callbacks. */
data class PlaybackSnapshot(
    val currentSongId: Long? = null,
    val playing: Boolean = false,
    val buffering: Boolean = false,
    val positionMs: Long = 0,
    val durationMs: Long = 0,
    val repeatMode: RepeatMode = RepeatMode.Off,
    val shuffle: Boolean = false,
    val queueIds: List<Long> = emptyList(),
    val currentIndex: Int = -1,
    val error: String? = null,
    /** Same elapsed-realtime clock used by the lyric renderer; zero means no source timestamp. */
    val positionSampledAtNanos: Long = 0L,
    /** Changes only for Media3 position discontinuities (seek, repeat, item transition). */
    val positionDiscontinuity: Long = 0L,
    val playbackSpeed: Float = 1f,
)

/**
 * Process-wide playback. The production implementation is a [androidx.media3.session.MediaController]
 * bound to the app's [PlaybackService] so playback continues with the app in the background and
 * appears in the system media notification.
 */
interface PlaybackEngine {
    val snapshot: StateFlow<PlaybackSnapshot>

    /** Connects to (or starts) the media session. Idempotent. */
    suspend fun connect()

    /** Replaces the queue and starts the item at [startIndex]. */
    fun setQueue(songs: List<PlayableSong>, startIndex: Int)

    fun play()

    fun pause()

    fun seekTo(positionMs: Long)

    fun next()

    fun previous()

    fun setRepeatMode(mode: RepeatMode)

    fun setShuffle(enabled: Boolean)

    fun clearError()

    fun release()
}
