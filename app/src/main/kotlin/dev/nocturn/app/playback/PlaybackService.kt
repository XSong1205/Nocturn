package dev.nocturn.app.playback

import android.app.PendingIntent
import android.content.Intent
import androidx.media3.common.AudioAttributes
import androidx.media3.common.C
import androidx.media3.common.util.UnstableApi
import androidx.media3.exoplayer.ExoPlayer
import androidx.media3.session.DefaultMediaNotificationProvider
import androidx.media3.session.MediaSession
import androidx.media3.session.MediaSessionService
import dev.nocturn.app.MainActivity
import dev.nocturn.app.R

/**
 * Process-wide audio playback host.
 *
 * Owns the single [ExoPlayer] instance and publishes it through a [MediaSession], so playback
 * continues while the app is in the background, keeps following the system audio focus route, and
 * appears in the media notification. [MediaSessionPlaybackEngine] is the only client and reaches
 * this service through a Media3 `MediaController`.
 *
 * The default notification provider is still marked unstable in Media3 1.8.0, hence the explicit
 * opt-in.
 */
@androidx.annotation.OptIn(UnstableApi::class)
class PlaybackService : MediaSessionService() {
    private var player: ExoPlayer? = null
    private var mediaSession: MediaSession? = null

    override fun onCreate() {
        super.onCreate()
        // Must be configured before the session starts publishing playback state.
        setMediaNotificationProvider(
            DefaultMediaNotificationProvider(this).apply {
                setSmallIcon(R.drawable.ic_nocturn)
            },
        )

        val exoPlayer = ExoPlayer.Builder(this)
            .setAudioAttributes(
                AudioAttributes.Builder()
                    .setUsage(C.USAGE_MEDIA)
                    .setContentType(C.AUDIO_CONTENT_TYPE_MUSIC)
                    .build(),
                /* handleAudioFocus = */ true,
            )
            // Pause instead of blasting music from the phone speaker when headphones unplug.
            .setHandleAudioBecomingNoisy(true)
            // Keep the CPU alive for network streaming while the screen is off.
            .setWakeMode(C.WAKE_MODE_NETWORK)
            .build()
        player = exoPlayer

        val contentIntent = Intent(this, MainActivity::class.java).apply {
            flags = Intent.FLAG_ACTIVITY_SINGLE_TOP or Intent.FLAG_ACTIVITY_CLEAR_TOP
        }
        val sessionActivity = PendingIntent.getActivity(
            this,
            SESSION_ACTIVITY_REQUEST_CODE,
            contentIntent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE,
        )
        mediaSession = MediaSession.Builder(this, exoPlayer)
            .setSessionActivity(sessionActivity)
            .build()
    }

    override fun onGetSession(controllerInfo: MediaSession.ControllerInfo): MediaSession? = mediaSession

    override fun onTaskRemoved(rootIntent: Intent?) {
        val sessionPlayer = player
        val isIdle = sessionPlayer == null ||
            sessionPlayer.mediaItemCount == 0 ||
            !sessionPlayer.playWhenReady
        if (isIdle) {
            // Nothing is playing any more: let the system tear the service down with the task.
            stopSelf()
        }
        super.onTaskRemoved(rootIntent)
    }

    override fun onDestroy() {
        mediaSession?.let { session ->
            player?.release()
            player = null
            session.release()
        }
        mediaSession = null
        super.onDestroy()
    }

    private companion object {
        const val SESSION_ACTIVITY_REQUEST_CODE = 0
    }
}
