package dev.nocturn.app.ui.state

import dev.nocturn.app.data.model.Lyrics
import dev.nocturn.app.data.model.Song
import dev.nocturn.app.playback.RepeatMode

data class MiniPlayerUiState(
    val song: Song? = null,
    val playing: Boolean = false,
    val buffering: Boolean = false,
    val progress: Float = 0f,
)

data class PlayerUiState(
    val visible: Boolean = false,
    val song: Song? = null,
    val playing: Boolean = false,
    val buffering: Boolean = false,
    val positionMs: Long = 0,
    val durationMs: Long = 0,
    val liked: Boolean = false,
    val shuffle: Boolean = false,
    val repeatMode: RepeatMode = RepeatMode.Off,
    val lyrics: Lyrics = Lyrics.EMPTY,
    val lyricsLoading: Boolean = false,
    val queue: List<Song> = emptyList(),
    val currentIndex: Int = -1,
    val qualityLabel: String = "极高",
    val isLoggedIn: Boolean = false,
    val error: String? = null,
    val positionSampledAtNanos: Long = 0L,
    val positionDiscontinuity: Long = 0L,
    val playbackSpeed: Float = 1f,
) {
    val progress: Float
        get() = if (durationMs > 0) {
            (positionMs.toFloat() / durationMs.toFloat()).coerceIn(0f, 1f)
        } else {
            0f
        }
}
