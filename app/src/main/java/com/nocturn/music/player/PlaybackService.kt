package com.nocturn.music.player

import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.app.Service
import android.content.Context
import android.content.Intent
import android.content.pm.ServiceInfo
import android.graphics.Bitmap
import android.os.Build
import android.os.IBinder
import android.os.SystemClock
import android.support.v4.media.MediaMetadataCompat
import android.support.v4.media.session.MediaSessionCompat
import android.support.v4.media.session.PlaybackStateCompat
import androidx.core.app.NotificationCompat
import androidx.core.app.ServiceCompat
import androidx.media.app.NotificationCompat.MediaStyle
import androidx.media.session.MediaButtonReceiver
import com.nocturn.music.MainActivity
import com.nocturn.music.R
import com.nocturn.music.model.Song
import com.nocturn.music.ui.components.ImageLoader
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.launch

class PlaybackService : Service() {

    companion object {
        const val CHANNEL_ID = "nocturn_playback_channel"
        const val NOTIFICATION_ID = 1001

        const val ACTION_PLAY_PAUSE = "com.nocturn.music.ACTION_PLAY_PAUSE"
        const val ACTION_NEXT = "com.nocturn.music.ACTION_NEXT"
        const val ACTION_PREV = "com.nocturn.music.ACTION_PREV"
        const val ACTION_STOP = "com.nocturn.music.ACTION_STOP"
        const val ACTION_SYNC_STATE = "com.nocturn.music.ACTION_SYNC_STATE"

        private var instance: PlaybackService? = null

        fun start(context: Context, song: Song? = null, isPlaying: Boolean = true) {
            val intent = Intent(context, PlaybackService::class.java)
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                context.startForegroundService(intent)
            } else {
                context.startService(intent)
            }
        }

        fun stop(context: Context) {
            val intent = Intent(context, PlaybackService::class.java).apply {
                action = ACTION_STOP
            }
            context.startService(intent)
        }

        fun syncSeekPosition(positionMs: Long) {
            instance?.updateSeekPosition(positionMs)
        }
    }

    private lateinit var mediaSession: MediaSessionCompat
    private val serviceScope = CoroutineScope(Dispatchers.Main + SupervisorJob())
    private var coverJob: Job? = null
    private var currentLoadedCover: Bitmap? = null
    private var currentCoverUrl: String? = null
    private var isForeground = false

    override fun onCreate() {
        super.onCreate()
        instance = this
        createNotificationChannel()
        initMediaSession()
        observePlayer()
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        MediaButtonReceiver.handleIntent(mediaSession, intent)

        when (intent?.action) {
            ACTION_PLAY_PAUSE -> NocturnPlayer.togglePlayPause()
            ACTION_NEXT -> NocturnPlayer.playNext()
            ACTION_PREV -> NocturnPlayer.playPrevious()
            ACTION_STOP -> {
                NocturnPlayer.stop()
                stopForegroundInternal()
                stopSelf()
                return START_NOT_STICKY
            }
            ACTION_SYNC_STATE -> {
                updatePlaybackState(
                    isPlaying = NocturnPlayer.isPlaying.value,
                    positionMs = NocturnPlayer.currentPositionMs.value,
                    durationMs = NocturnPlayer.durationMs.value,
                    isBuffering = NocturnPlayer.isBuffering.value
                )
            }
            else -> {
                val song = NocturnPlayer.currentSong.value
                val isPlaying = NocturnPlayer.isPlaying.value
                updateNotificationAndSession(
                    song = song,
                    isPlaying = isPlaying,
                    isBuffering = NocturnPlayer.isBuffering.value,
                    durationMs = NocturnPlayer.durationMs.value
                )
            }
        }
        return START_STICKY
    }

    override fun onBind(intent: Intent?): IBinder? = null

    private fun initMediaSession() {
        val mediaButtonReceiverPendingIntent = MediaButtonReceiver.buildMediaButtonPendingIntent(
            this,
            PlaybackStateCompat.ACTION_PLAY_PAUSE
        )

        mediaSession = MediaSessionCompat(this, "NocturnMediaSession", null, mediaButtonReceiverPendingIntent).apply {
            setCallback(object : MediaSessionCompat.Callback() {
                override fun onPlay() {
                    NocturnPlayer.togglePlayPause()
                }

                override fun onPause() {
                    NocturnPlayer.togglePlayPause()
                }

                override fun onSkipToNext() {
                    NocturnPlayer.playNext()
                }

                override fun onSkipToPrevious() {
                    NocturnPlayer.playPrevious()
                }

                override fun onSeekTo(pos: Long) {
                    NocturnPlayer.seekTo(pos)
                    updatePlaybackState(
                        isPlaying = NocturnPlayer.isPlaying.value,
                        positionMs = pos,
                        durationMs = NocturnPlayer.durationMs.value,
                        isBuffering = NocturnPlayer.isBuffering.value
                    )
                }

                override fun onStop() {
                    NocturnPlayer.stop()
                    stopForegroundInternal()
                    stopSelf()
                }
            })

            isActive = true
        }
    }

    private data class PlayerStateSnapshot(
        val song: Song?,
        val isPlaying: Boolean,
        val isBuffering: Boolean,
        val durationMs: Long
    )

    private fun observePlayer() {
        serviceScope.launch {
            combine(
                NocturnPlayer.currentSong,
                NocturnPlayer.isPlaying,
                NocturnPlayer.isBuffering,
                NocturnPlayer.durationMs
            ) { song, isPlaying, isBuffering, durationMs ->
                PlayerStateSnapshot(song, isPlaying, isBuffering, durationMs)
            }.distinctUntilChanged()
            .collect { snapshot ->
                updateNotificationAndSession(
                    song = snapshot.song,
                    isPlaying = snapshot.isPlaying,
                    isBuffering = snapshot.isBuffering,
                    durationMs = snapshot.durationMs
                )
            }
        }
    }

    private fun updateNotificationAndSession(
        song: Song?,
        isPlaying: Boolean,
        isBuffering: Boolean,
        durationMs: Long
    ) {
        if (song == null && !isPlaying) {
            stopForegroundInternal()
            stopSelf()
            return
        }

        updatePlaybackState(
            isPlaying = isPlaying,
            positionMs = NocturnPlayer.currentPositionMs.value,
            durationMs = durationMs,
            isBuffering = isBuffering
        )

        val coverUrl = song?.coverUrl.orEmpty()
        if (coverUrl != currentCoverUrl) {
            currentCoverUrl = coverUrl
            currentLoadedCover = ImageLoader.getCached(coverUrl)

            if (currentLoadedCover == null && coverUrl.isNotBlank()) {
                coverJob?.cancel()
                coverJob = serviceScope.launch {
                    val bitmap = ImageLoader.loadBitmap(coverUrl)
                    if (bitmap != null && currentCoverUrl == coverUrl) {
                        currentLoadedCover = bitmap
                        updateMetadata(song, durationMs, bitmap)
                        renderNotification(song, isPlaying, bitmap)
                    }
                }
            }
        }

        updateMetadata(song, durationMs, currentLoadedCover)
        renderNotification(song, isPlaying, currentLoadedCover)
    }

    fun updateSeekPosition(positionMs: Long) {
        updatePlaybackState(
            isPlaying = NocturnPlayer.isPlaying.value,
            positionMs = positionMs,
            durationMs = NocturnPlayer.durationMs.value,
            isBuffering = NocturnPlayer.isBuffering.value
        )
    }

    private fun updatePlaybackState(
        isPlaying: Boolean,
        positionMs: Long,
        durationMs: Long,
        isBuffering: Boolean = false
    ) {
        val state = when {
            isBuffering -> PlaybackStateCompat.STATE_BUFFERING
            isPlaying -> PlaybackStateCompat.STATE_PLAYING
            else -> PlaybackStateCompat.STATE_PAUSED
        }

        val actions = PlaybackStateCompat.ACTION_PLAY or
            PlaybackStateCompat.ACTION_PAUSE or
            PlaybackStateCompat.ACTION_PLAY_PAUSE or
            PlaybackStateCompat.ACTION_SKIP_TO_NEXT or
            PlaybackStateCompat.ACTION_SKIP_TO_PREVIOUS or
            PlaybackStateCompat.ACTION_SEEK_TO or
            PlaybackStateCompat.ACTION_STOP

        val playbackState = PlaybackStateCompat.Builder()
            .setActions(actions)
            .setState(
                state,
                positionMs.coerceAtLeast(0L),
                if (isPlaying && !isBuffering) 1.0f else 0.0f,
                SystemClock.elapsedRealtime()
            )
            .build()

        mediaSession.setPlaybackState(playbackState)
    }

    private fun updateMetadata(song: Song?, durationMs: Long, coverBitmap: Bitmap?) {
        if (song == null) {
            mediaSession.setMetadata(null)
            return
        }

        val effectiveDuration = if (song.durationMs > 0) song.durationMs else durationMs

        val metadataBuilder = MediaMetadataCompat.Builder()
            .putString(MediaMetadataCompat.METADATA_KEY_TITLE, song.title)
            .putString(MediaMetadataCompat.METADATA_KEY_ARTIST, song.artist)
            .putString(MediaMetadataCompat.METADATA_KEY_ALBUM, song.album.ifBlank { "Nocturn" })
            .putLong(MediaMetadataCompat.METADATA_KEY_DURATION, effectiveDuration)

        if (coverBitmap != null) {
            metadataBuilder.putBitmap(MediaMetadataCompat.METADATA_KEY_ALBUM_ART, coverBitmap)
            metadataBuilder.putBitmap(MediaMetadataCompat.METADATA_KEY_ART, coverBitmap)
            metadataBuilder.putBitmap(MediaMetadataCompat.METADATA_KEY_DISPLAY_ICON, coverBitmap)
        }

        mediaSession.setMetadata(metadataBuilder.build())
    }

    private fun renderNotification(song: Song?, isPlaying: Boolean, coverBitmap: Bitmap?) {
        val notification = buildNotification(song, isPlaying, coverBitmap)
        val notificationManager = getSystemService(NotificationManager::class.java)

        if (isPlaying) {
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
                startForeground(
                    NOTIFICATION_ID,
                    notification,
                    ServiceInfo.FOREGROUND_SERVICE_TYPE_MEDIA_PLAYBACK
                )
            } else {
                startForeground(NOTIFICATION_ID, notification)
            }
            isForeground = true
        } else {
            if (isForeground) {
                ServiceCompat.stopForeground(this, ServiceCompat.STOP_FOREGROUND_DETACH)
                isForeground = false
            }
            notificationManager?.notify(NOTIFICATION_ID, notification)
        }
    }

    private fun buildNotification(song: Song?, isPlaying: Boolean, coverBitmap: Bitmap?): Notification {
        val title = song?.title?.ifBlank { "Nocturn" } ?: "Nocturn"
        val artist = song?.artist?.ifBlank { "音乐播放中" } ?: "音乐播放中"
        val album = song?.album?.takeIf { it.isNotBlank() }

        val openAppIntent = Intent(this, MainActivity::class.java).apply {
            flags = Intent.FLAG_ACTIVITY_SINGLE_TOP or Intent.FLAG_ACTIVITY_CLEAR_TOP
        }
        val openAppPendingIntent = PendingIntent.getActivity(
            this, 0, openAppIntent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )

        val prevPendingIntent = PendingIntent.getService(
            this, 1,
            Intent(this, PlaybackService::class.java).apply { action = ACTION_PREV },
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )

        val playPausePendingIntent = PendingIntent.getService(
            this, 2,
            Intent(this, PlaybackService::class.java).apply { action = ACTION_PLAY_PAUSE },
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )

        val nextPendingIntent = PendingIntent.getService(
            this, 3,
            Intent(this, PlaybackService::class.java).apply { action = ACTION_NEXT },
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )

        val stopPendingIntent = PendingIntent.getService(
            this, 4,
            Intent(this, PlaybackService::class.java).apply { action = ACTION_STOP },
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )

        val playPauseIcon = if (isPlaying) android.R.drawable.ic_media_pause else android.R.drawable.ic_media_play

        val mediaStyle = MediaStyle()
            .setMediaSession(mediaSession.sessionToken)
            .setShowActionsInCompactView(0, 1, 2)
            .setShowCancelButton(true)
            .setCancelButtonIntent(stopPendingIntent)

        val builder = NotificationCompat.Builder(this, CHANNEL_ID)
            .setStyle(mediaStyle)
            .setSmallIcon(R.drawable.ic_launcher_foreground)
            .setContentTitle(title)
            .setContentText(artist)
            .apply {
                if (album != null) {
                    setSubText(album)
                }
                if (coverBitmap != null) {
                    setLargeIcon(coverBitmap)
                }
            }
            .setContentIntent(openAppPendingIntent)
            .setDeleteIntent(stopPendingIntent)
            .setVisibility(NotificationCompat.VISIBILITY_PUBLIC)
            .setOnlyAlertOnce(true)
            .setOngoing(isPlaying)
            .addAction(android.R.drawable.ic_media_previous, "上一首", prevPendingIntent)
            .addAction(playPauseIcon, if (isPlaying) "暂停" else "播放", playPausePendingIntent)
            .addAction(android.R.drawable.ic_media_next, "下一首", nextPendingIntent)

        return builder.build()
    }

    private fun createNotificationChannel() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val channel = NotificationChannel(
                CHANNEL_ID,
                "Nocturn 音乐播放",
                NotificationManager.IMPORTANCE_LOW
            ).apply {
                description = "展示当前正在播放的音乐及控制面板"
                setShowBadge(false)
                lockscreenVisibility = Notification.VISIBILITY_PUBLIC
                setSound(null, null)
                enableVibration(false)
            }
            val manager = getSystemService(NotificationManager::class.java)
            manager?.createNotificationChannel(channel)
        }
    }

    private fun stopForegroundInternal() {
        if (isForeground) {
            ServiceCompat.stopForeground(this, ServiceCompat.STOP_FOREGROUND_REMOVE)
            isForeground = false
        } else {
            val notificationManager = getSystemService(NotificationManager::class.java)
            notificationManager?.cancel(NOTIFICATION_ID)
        }
    }

    override fun onTaskRemoved(rootIntent: Intent?) {
        super.onTaskRemoved(rootIntent)
        if (!NocturnPlayer.isPlaying.value) {
            stopForegroundInternal()
            stopSelf()
        }
    }

    override fun onDestroy() {
        instance = null
        serviceScope.cancel()
        if (::mediaSession.isInitialized) {
            mediaSession.isActive = false
            mediaSession.release()
        }
        stopForegroundInternal()
        super.onDestroy()
    }
}
