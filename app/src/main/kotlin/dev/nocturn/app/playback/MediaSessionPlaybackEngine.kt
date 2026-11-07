package dev.nocturn.app.playback

import android.content.ComponentName
import android.content.Context
import android.os.Handler
import android.os.Looper
import android.os.SystemClock
import androidx.media3.common.PlaybackException
import androidx.media3.common.Player
import androidx.media3.session.MediaController
import androidx.media3.session.SessionToken
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import kotlinx.coroutines.suspendCancellableCoroutine
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext
import kotlin.coroutines.resume
import kotlin.coroutines.resumeWithException

/**
 * [PlaybackEngine] backed by a Media3 [MediaController] bound to [PlaybackService].
 *
 * The controller follows the application main looper, so public methods must be called from the
 * main thread. Every player callback is mirrored into [snapshot] as a plain immutable state, which
 * keeps the UI layer free of Media3 types.
 */
class MediaSessionPlaybackEngine(context: Context) : PlaybackEngine {
    private val appContext = context.applicationContext
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Main.immediate)
    private val mainHandler = Handler(Looper.getMainLooper())
    private val mutableSnapshot = MutableStateFlow(PlaybackSnapshot())

    override val snapshot: StateFlow<PlaybackSnapshot> = mutableSnapshot.asStateFlow()

    private val connectionMutex = Mutex()

    @Volatile
    private var controller: MediaController? = null

    @Volatile
    private var released = false

    private var progressJob: Job? = null
    private var positionDiscontinuity = 0L

    private val playerListener = object : Player.Listener {
        override fun onPositionDiscontinuity(
            oldPosition: Player.PositionInfo,
            newPosition: Player.PositionInfo,
            reason: Int,
        ) {
            positionDiscontinuity++
        }

        override fun onEvents(player: Player, events: Player.Events) {
            publishSnapshot(player)
        }

        override fun onPlayerError(error: PlaybackException) {
            mutableSnapshot.update {
                it.copy(
                    playing = false,
                    buffering = false,
                    error = PlaybackSnapshotMapping.playbackErrorMessage(error.errorCode),
                )
            }
        }
    }

    override suspend fun connect() {
        connectionMutex.withLock {
            if (released || controller != null) return
            val connected = withContext(Dispatchers.Main.immediate) { awaitController() }
            if (released) {
                connected.release()
                return
            }
            controller = connected
            connected.addListener(playerListener)
            publishSnapshot(connected)
            startProgressUpdates()
        }
    }

    private suspend fun awaitController(): MediaController {
        val sessionToken = SessionToken(
            appContext,
            ComponentName(appContext, PlaybackService::class.java),
        )
        val future = MediaController.Builder(appContext, sessionToken).buildAsync()
        return suspendCancellableCoroutine { continuation ->
            future.addListener(
                {
                    when {
                        !continuation.isActive -> MediaController.releaseFuture(future)
                        else -> try {
                            continuation.resume(future.get())
                        } catch (cancelled: CancellationException) {
                            continuation.cancel(cancelled)
                        } catch (error: Throwable) {
                            continuation.resumeWithException(error)
                        }
                    }
                },
                appContext.mainExecutor,
            )
            continuation.invokeOnCancellation { MediaController.releaseFuture(future) }
        }
    }

    private fun startProgressUpdates() {
        progressJob?.cancel()
        progressJob = scope.launch {
            while (isActive) {
                val current = controller
                if (current == null) break
                val positionMs = current.currentPosition
                val sampledAtNanos = SystemClock.elapsedRealtimeNanos()
                mutableSnapshot.update { previous ->
                    PlaybackSnapshotMapping.withProgress(
                        previous = previous,
                        positionMs = positionMs,
                        durationMs = current.duration,
                        positionSampledAtNanos = if (current.isPlaying || positionMs != previous.positionMs) {
                            sampledAtNanos
                        } else previous.positionSampledAtNanos,
                    )
                }
                delay(PROGRESS_UPDATE_INTERVAL_MS)
            }
        }
    }

    private fun publishSnapshot(player: Player) {
        val positionMs = player.currentPosition
        val sampledAtNanos = SystemClock.elapsedRealtimeNanos()
        val mediaIds = ArrayList<String>(player.mediaItemCount)
        for (index in 0 until player.mediaItemCount) {
            mediaIds += player.getMediaItemAt(index).mediaId
        }
        mutableSnapshot.update { previous ->
            PlaybackSnapshotMapping.fromPlayer(
                currentIndex = player.currentMediaItemIndex,
                mediaIds = mediaIds,
                isPlaying = player.isPlaying,
                playbackState = player.playbackState,
                positionMs = positionMs,
                durationMs = player.duration,
                playerRepeatMode = player.repeatMode,
                shuffleEnabled = player.shuffleModeEnabled,
                previous = previous,
                positionSampledAtNanos = sampledAtNanos,
                positionDiscontinuity = positionDiscontinuity,
                playbackSpeed = player.playbackParameters.speed,
            )
        }
    }

    override fun setQueue(songs: List<PlayableSong>, startIndex: Int) {
        val current = controller
        if (current == null) {
            mutableSnapshot.update { it.copy(error = PlaybackSnapshotMapping.NOT_CONNECTED_MESSAGE) }
            return
        }
        if (songs.isEmpty()) {
            current.clearMediaItems()
            mutableSnapshot.value = PlaybackSnapshot(
                repeatMode = mutableSnapshot.value.repeatMode,
                shuffle = mutableSnapshot.value.shuffle,
                error = PlaybackSnapshotMapping.EMPTY_QUEUE_MESSAGE,
            )
            return
        }
        val resolved = MediaItemMapping.resolveQueue(songs, startIndex)
        if (resolved.items.isEmpty()) {
            current.clearMediaItems()
            mutableSnapshot.value = PlaybackSnapshot(
                repeatMode = mutableSnapshot.value.repeatMode,
                shuffle = mutableSnapshot.value.shuffle,
                error = PlaybackSnapshotMapping.INVALID_SOURCE_MESSAGE,
            )
            return
        }
        mutableSnapshot.update {
            it.copy(
                error = if (resolved.skipped > 0) {
                    PlaybackSnapshotMapping.SKIPPED_SOURCES_MESSAGE
                } else {
                    null
                },
            )
        }
        current.setMediaItems(resolved.items, resolved.startIndex, 0L)
        current.prepare()
        publishSnapshot(current)
    }

    override fun play() {
        val current = controller ?: return
        if (current.playerError != null) {
            // A failed item leaves the player in the error state; prepare() retries it.
            mutableSnapshot.update { it.copy(error = null) }
            current.prepare()
        }
        current.play()
    }

    override fun pause() {
        controller?.pause()
    }

    override fun seekTo(positionMs: Long) {
        val current = controller ?: return
        val duration = current.duration
        val target = if (duration > 0L) {
            positionMs.coerceIn(0L, duration)
        } else {
            positionMs.coerceAtLeast(0L)
        }
        current.seekTo(target)
    }

    override fun next() {
        controller?.seekToNext()
    }

    override fun previous() {
        controller?.seekToPrevious()
    }

    override fun setRepeatMode(mode: RepeatMode) {
        val current = controller ?: return
        current.repeatMode = PlaybackSnapshotMapping.repeatModeToPlayer(mode)
        publishSnapshot(current)
    }

    override fun setShuffle(enabled: Boolean) {
        val current = controller ?: return
        current.shuffleModeEnabled = enabled
        publishSnapshot(current)
    }

    override fun clearError() {
        mutableSnapshot.update { it.copy(error = null) }
    }

    override fun release() {
        if (released) return
        released = true
        progressJob?.cancel()
        progressJob = null
        if (Looper.myLooper() == Looper.getMainLooper()) {
            releaseController()
        } else {
            mainHandler.post { releaseController() }
        }
        scope.cancel()
    }

    private fun releaseController() {
        val current = controller
        controller = null
        current?.removeListener(playerListener)
        current?.release()
    }

    private companion object {
        const val PROGRESS_UPDATE_INTERVAL_MS = 500L
    }
}
