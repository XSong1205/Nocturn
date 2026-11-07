package dev.nocturn.app.playback

import androidx.media3.common.C
import androidx.media3.common.PlaybackException
import androidx.media3.common.Player
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * Pure JVM coverage for the player-state to [PlaybackSnapshot] mapping. These tests never start a
 * [PlaybackService] or create a MediaController.
 */
class PlaybackSnapshotMappingTest {
    @Test
    fun mirrorsPlayerStateIntoSnapshot() {
        val snapshot = PlaybackSnapshotMapping.fromPlayer(
            currentIndex = 1,
            mediaIds = listOf("7", "8", "9"),
            isPlaying = true,
            playbackState = Player.STATE_READY,
            positionMs = 61_000L,
            durationMs = 245_000L,
            playerRepeatMode = Player.REPEAT_MODE_ALL,
            shuffleEnabled = true,
        )

        assertEquals(8L, snapshot.currentSongId)
        assertEquals(listOf(7L, 8L, 9L), snapshot.queueIds)
        assertEquals(1, snapshot.currentIndex)
        assertTrue(snapshot.playing)
        assertFalse(snapshot.buffering)
        assertEquals(61_000L, snapshot.positionMs)
        assertEquals(245_000L, snapshot.durationMs)
        assertEquals(RepeatMode.All, snapshot.repeatMode)
        assertTrue(snapshot.shuffle)
        assertNull(snapshot.error)
    }

    @Test
    fun bufferingStateAndUnsetValuesAreNormalised() {
        val snapshot = PlaybackSnapshotMapping.fromPlayer(
            currentIndex = -1,
            mediaIds = emptyList(),
            isPlaying = false,
            playbackState = Player.STATE_BUFFERING,
            positionMs = -5L,
            durationMs = C.TIME_UNSET,
            playerRepeatMode = Player.REPEAT_MODE_OFF,
            shuffleEnabled = false,
        )

        assertNull(snapshot.currentSongId)
        assertEquals(emptyList<Long>(), snapshot.queueIds)
        assertEquals(-1, snapshot.currentIndex)
        assertFalse(snapshot.playing)
        assertTrue(snapshot.buffering)
        assertEquals(0L, snapshot.positionMs)
        assertEquals(0L, snapshot.durationMs)
        assertEquals(RepeatMode.Off, snapshot.repeatMode)
    }

    @Test
    fun keepsErrorWhileIdleAndClearsItWhenPlaybackRecovers() {
        val failed = PlaybackSnapshot(error = "网络连接不稳定")

        val idle = PlaybackSnapshotMapping.fromPlayer(
            currentIndex = 0,
            mediaIds = listOf("7"),
            isPlaying = false,
            playbackState = Player.STATE_IDLE,
            positionMs = 0L,
            durationMs = C.TIME_UNSET,
            playerRepeatMode = Player.REPEAT_MODE_OFF,
            shuffleEnabled = false,
            previous = failed,
        )
        assertEquals("网络连接不稳定", idle.error)

        val playing = PlaybackSnapshotMapping.fromPlayer(
            currentIndex = 0,
            mediaIds = listOf("7"),
            isPlaying = true,
            playbackState = Player.STATE_READY,
            positionMs = 1_000L,
            durationMs = 245_000L,
            playerRepeatMode = Player.REPEAT_MODE_OFF,
            shuffleEnabled = false,
            previous = failed,
        )
        assertNull(playing.error)
    }

    @Test
    fun progressUpdatesOnlyTouchPositionAndDuration() {
        val previous = PlaybackSnapshot(
            currentSongId = 8L,
            playing = true,
            positionMs = 1_000L,
            durationMs = 245_000L,
            repeatMode = RepeatMode.One,
            shuffle = true,
            queueIds = listOf(7L, 8L, 9L),
            currentIndex = 1,
            error = "播放失败",
        )

        val progressed = PlaybackSnapshotMapping.withProgress(
            previous = previous,
            positionMs = 30_000L,
            durationMs = -1L,
        )

        assertEquals(30_000L, progressed.positionMs)
        assertEquals(0L, progressed.durationMs)
        assertEquals(previous.currentSongId, progressed.currentSongId)
        assertEquals(previous.queueIds, progressed.queueIds)
        assertEquals(previous.currentIndex, progressed.currentIndex)
        assertEquals(RepeatMode.One, progressed.repeatMode)
        assertTrue(progressed.shuffle)
        assertEquals("播放失败", progressed.error)
    }

    @Test
    fun repeatModesRoundTripThroughPlayerConstants() {
        assertEquals(Player.REPEAT_MODE_OFF, PlaybackSnapshotMapping.repeatModeToPlayer(RepeatMode.Off))
        assertEquals(Player.REPEAT_MODE_ALL, PlaybackSnapshotMapping.repeatModeToPlayer(RepeatMode.All))
        assertEquals(Player.REPEAT_MODE_ONE, PlaybackSnapshotMapping.repeatModeToPlayer(RepeatMode.One))

        assertEquals(RepeatMode.Off, PlaybackSnapshotMapping.repeatModeFromPlayer(Player.REPEAT_MODE_OFF))
        assertEquals(RepeatMode.All, PlaybackSnapshotMapping.repeatModeFromPlayer(Player.REPEAT_MODE_ALL))
        assertEquals(RepeatMode.One, PlaybackSnapshotMapping.repeatModeFromPlayer(Player.REPEAT_MODE_ONE))
        assertEquals(RepeatMode.Off, PlaybackSnapshotMapping.repeatModeFromPlayer(Player.REPEAT_MODE_ALL + 99))
    }

    @Test
    fun errorMessagesAreChineseAndSpecificToTheFailureCategory() {
        val network = PlaybackSnapshotMapping.playbackErrorMessage(
            PlaybackException.ERROR_CODE_IO_NETWORK_CONNECTION_FAILED,
        )
        val unavailableSource = PlaybackSnapshotMapping.playbackErrorMessage(
            PlaybackException.ERROR_CODE_IO_BAD_HTTP_STATUS,
        )
        val unsupportedFormat = PlaybackSnapshotMapping.playbackErrorMessage(
            PlaybackException.ERROR_CODE_DECODING_FORMAT_UNSUPPORTED,
        )
        val audioOutput = PlaybackSnapshotMapping.playbackErrorMessage(
            PlaybackException.ERROR_CODE_AUDIO_TRACK_INIT_FAILED,
        )
        val generic = PlaybackSnapshotMapping.playbackErrorMessage(
            PlaybackException.ERROR_CODE_UNSPECIFIED,
        )

        listOf(network, unavailableSource, unsupportedFormat, audioOutput, generic).forEach { message ->
            assertTrue("error message must not be blank", message.isNotBlank())
        }
        assertTrue(network.contains("网络"))
        assertTrue(unavailableSource.contains("音源"))
        assertTrue(unsupportedFormat.contains("音频格式"))
        assertTrue(audioOutput.contains("音频输出"))
        assertTrue(generic.contains("播放失败"))
        assertEquals(
            5,
            setOf(network, unavailableSource, unsupportedFormat, audioOutput, generic).size,
        )
    }
}
