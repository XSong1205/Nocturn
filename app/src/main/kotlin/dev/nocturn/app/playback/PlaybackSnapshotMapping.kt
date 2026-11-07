package dev.nocturn.app.playback

import androidx.media3.common.C
import androidx.media3.common.PlaybackException
import androidx.media3.common.Player

/**
 * Pure mapping from Media3 player state to [PlaybackSnapshot] plus the user-facing error copy.
 *
 * Kept free of player instances so the mapping can be unit tested on the JVM. All user-visible
 * strings are Chinese because the whole product is Chinese.
 */
internal object PlaybackSnapshotMapping {
    const val NOT_CONNECTED_MESSAGE = "播放服务未连接，请稍后重试。"
    const val EMPTY_QUEUE_MESSAGE = "播放队列为空，先选一首歌吧。"
    const val INVALID_SOURCE_MESSAGE = "暂时没有可用音源，无法播放歌曲。"
    const val SKIPPED_SOURCES_MESSAGE = "部分歌曲没有可用音源，已自动跳过。"

    fun repeatModeToPlayer(mode: RepeatMode): Int = when (mode) {
        RepeatMode.Off -> Player.REPEAT_MODE_OFF
        RepeatMode.All -> Player.REPEAT_MODE_ALL
        RepeatMode.One -> Player.REPEAT_MODE_ONE
    }

    fun repeatModeFromPlayer(playerRepeatMode: Int): RepeatMode = when (playerRepeatMode) {
        Player.REPEAT_MODE_ALL -> RepeatMode.All
        Player.REPEAT_MODE_ONE -> RepeatMode.One
        else -> RepeatMode.Off
    }

    fun isBuffering(playbackState: Int): Boolean = playbackState == Player.STATE_BUFFERING

    /** Media3 reports unknown durations as [C.TIME_UNSET]; snapshots only carry non-negative millis. */
    fun normalizeDurationMs(rawDurationMs: Long): Long =
        if (rawDurationMs > 0L) rawDurationMs else 0L

    fun normalizePositionMs(rawPositionMs: Long): Long = rawPositionMs.coerceAtLeast(0L)

    /**
     * Mirrors a player state into a snapshot. The queue is derived from [mediaIds] so the snapshot
     * never holds a player reference. A previous error is kept while playback is idle so the UI can
     * surface it, and dropped as soon as playback is healthy again.
     */
    fun fromPlayer(
        currentIndex: Int,
        mediaIds: List<String>,
        isPlaying: Boolean,
        playbackState: Int,
        positionMs: Long,
        durationMs: Long,
        playerRepeatMode: Int,
        shuffleEnabled: Boolean,
        previous: PlaybackSnapshot = PlaybackSnapshot(),
        positionSampledAtNanos: Long = 0L,
        positionDiscontinuity: Long = previous.positionDiscontinuity,
        playbackSpeed: Float = 1f,
    ): PlaybackSnapshot {
        val queueIds = mediaIds.mapNotNull { mediaId -> MediaItemMapping.songIdOf(mediaId) }
        val currentSongId = mediaIds.getOrNull(currentIndex)?.let { mediaId ->
            MediaItemMapping.songIdOf(mediaId)
        }
        return PlaybackSnapshot(
            currentSongId = currentSongId,
            playing = isPlaying,
            buffering = isBuffering(playbackState),
            positionMs = normalizePositionMs(positionMs),
            durationMs = normalizeDurationMs(durationMs),
            repeatMode = repeatModeFromPlayer(playerRepeatMode),
            shuffle = shuffleEnabled,
            queueIds = queueIds,
            currentIndex = if (currentIndex in mediaIds.indices) currentIndex else -1,
            error = if (isPlaying) null else previous.error,
            positionSampledAtNanos = positionSampledAtNanos,
            positionDiscontinuity = positionDiscontinuity,
            playbackSpeed = playbackSpeed,
        )
    }

    /** Applies a periodic position poll without touching the identity/error parts of the snapshot. */
    fun withProgress(
        previous: PlaybackSnapshot,
        positionMs: Long,
        durationMs: Long,
        positionSampledAtNanos: Long = previous.positionSampledAtNanos,
    ): PlaybackSnapshot = previous.copy(
        positionMs = normalizePositionMs(positionMs),
        durationMs = normalizeDurationMs(durationMs),
        positionSampledAtNanos = positionSampledAtNanos,
    )

    /** Chinese user-facing copy for [PlaybackException.errorCode] values. */
    fun playbackErrorMessage(errorCode: Int): String = when (errorCode) {
        PlaybackException.ERROR_CODE_IO_NETWORK_CONNECTION_FAILED,
        PlaybackException.ERROR_CODE_IO_NETWORK_CONNECTION_TIMEOUT,
        PlaybackException.ERROR_CODE_TIMEOUT,
        -> "网络连接不稳定，暂时无法播放，请检查网络后重试。"

        PlaybackException.ERROR_CODE_IO_BAD_HTTP_STATUS,
        PlaybackException.ERROR_CODE_IO_INVALID_HTTP_CONTENT_TYPE,
        PlaybackException.ERROR_CODE_IO_FILE_NOT_FOUND,
        PlaybackException.ERROR_CODE_IO_NO_PERMISSION,
        PlaybackException.ERROR_CODE_IO_CLEARTEXT_NOT_PERMITTED,
        PlaybackException.ERROR_CODE_PREMIUM_ACCOUNT_REQUIRED,
        PlaybackException.ERROR_CODE_NOT_AVAILABLE_IN_REGION,
        PlaybackException.ERROR_CODE_AUTHENTICATION_EXPIRED,
        -> "暂时拿不到这首歌的音源，可能已下架或需要会员登录，请换一首。"

        PlaybackException.ERROR_CODE_PARSING_CONTAINER_MALFORMED,
        PlaybackException.ERROR_CODE_PARSING_CONTAINER_UNSUPPORTED,
        PlaybackException.ERROR_CODE_PARSING_MANIFEST_UNSUPPORTED,
        PlaybackException.ERROR_CODE_DECODER_INIT_FAILED,
        PlaybackException.ERROR_CODE_DECODING_FAILED,
        PlaybackException.ERROR_CODE_DECODING_FORMAT_EXCEEDS_CAPABILITIES,
        PlaybackException.ERROR_CODE_DECODING_FORMAT_UNSUPPORTED,
        -> "这首歌的音频格式暂不支持，请换一首。"

        PlaybackException.ERROR_CODE_AUDIO_TRACK_INIT_FAILED,
        PlaybackException.ERROR_CODE_AUDIO_TRACK_WRITE_FAILED,
        -> "音频输出初始化失败，请检查设备音频后重试。"

        else -> "播放失败，请稍后重试或换一首歌。"
    }
}
