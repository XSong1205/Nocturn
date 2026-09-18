package com.nocturn.music.player

import android.content.Context
import android.media.session.PlaybackState
import android.os.SystemClock
import android.util.Log
import com.nocturn.music.data.repository.MusicRepository
import com.nocturn.music.data.repository.SettingsRepository
import com.nocturn.music.model.Song as NocturnSong
import com.nocturn.music.model.SongLyric
import io.github.proify.lyricon.lyric.model.LyricWord as LyriconWord
import io.github.proify.lyricon.lyric.model.RichLyricLine as LyriconLine
import io.github.proify.lyricon.lyric.model.Song as LyriconSong
import io.github.proify.lyricon.provider.LyriconFactory
import io.github.proify.lyricon.provider.LyriconProvider
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.launch
import kotlin.math.abs

/**
 * 词幕 (Lyricon) 歌词提供端桥接控制器
 * 负责向词幕中心服务推送播放状态、曲目元数据、YRC 逐字歌词与 LRC 行级歌词。
 *
 * 核心设计（消除状态栏歌词卡顿）：
 * 1. 采用 Android 系统标准 PlaybackState（带 position, playbackSpeed=1.0f 与 elapsedRealtime 时间戳）向词幕同步；
 * 2. 词幕中心服务将自动启用高刷线性时钟插值（60Hz/120Hz），避免调用 setPosition 引起 500ms 阶梯状跳变卡顿与 IPC 抖动；
 * 3. 仅在曲目变更、播放/暂停切换、拖动进度 (seek) 或极端时钟漂移 (>800ms) 时触发状态校准。
 */
object LyriconBridge {
    private const val TAG = "LyriconBridge"

    private var provider: LyriconProvider? = null
    private val scope = CoroutineScope(Dispatchers.Main + SupervisorJob())

    private var lyricsJob: Job? = null
    private var observeJob: Job? = null
    private var isRegistered = false
    private var currentSongId: Long? = null

    // 记录最近一次同步的系统基准时钟，用于非侵入式漂移校准
    private var lastBaseElapsedRealtime = 0L
    private var lastBasePositionMs = 0L
    private var lastBaseSpeed = 0.0f

    fun init(context: Context) {
        if (provider != null) return

        try {
            val appCtx = context.applicationContext
            val p = LyriconFactory.createProvider(appCtx)
            p.autoSync = true

            p.service.addConnectionListener(object : io.github.proify.lyricon.provider.ConnectionListener {
                override fun onConnected(provider: LyriconProvider) {
                    Log.d(TAG, "Connected to Lyricon central service")
                    syncCurrentState()
                }
                override fun onReconnected(provider: LyriconProvider) {
                    Log.d(TAG, "Reconnected to Lyricon central service")
                    syncCurrentState()
                }
                override fun onDisconnected(provider: LyriconProvider) {
                    Log.d(TAG, "Disconnected from Lyricon central service")
                }
                override fun onConnectTimeout(provider: LyriconProvider) {
                    Log.w(TAG, "Connection to Lyricon central service timed out")
                }
            })

            provider = p

            // 监听设置开关
            scope.launch {
                SettingsRepository.lyriconEnabled.collectLatest { enabled ->
                    if (enabled) {
                        register()
                    } else {
                        unregister()
                    }
                }
            }

            startObservingPlayer()
        } catch (e: Exception) {
            Log.e(TAG, "Failed to initialize LyriconProvider", e)
        }
    }

    private fun register() {
        val p = provider ?: return
        if (isRegistered) return
        try {
            val success = p.register()
            isRegistered = true
            Log.d(TAG, "Lyricon provider registered: $success")
            syncCurrentState()
        } catch (e: Exception) {
            Log.e(TAG, "Failed to register Lyricon provider", e)
        }
    }

    private fun unregister() {
        val p = provider ?: return
        if (!isRegistered) return
        try {
            p.unregister()
            isRegistered = false
            Log.d(TAG, "Lyricon provider unregistered")
        } catch (e: Exception) {
            Log.e(TAG, "Failed to unregister Lyricon provider", e)
        }
    }

    private fun startObservingPlayer() {
        observeJob?.cancel()
        observeJob = scope.launch {
            // 监听当前歌曲变化
            launch {
                NocturnPlayer.currentSong.collectLatest { song ->
                    if (!SettingsRepository.lyriconEnabled.value || !isRegistered) return@collectLatest
                    updateSong(song)
                }
            }

            // 监听播放/暂停状态变化
            launch {
                NocturnPlayer.isPlaying.collectLatest { isPlaying ->
                    if (!SettingsRepository.lyriconEnabled.value || !isRegistered) return@collectLatest
                    syncPlaybackState(isPlaying = isPlaying)
                }
            }

            // 监听缓冲状态变化
            launch {
                NocturnPlayer.isBuffering.collectLatest { isBuffering ->
                    if (!SettingsRepository.lyriconEnabled.value || !isRegistered) return@collectLatest
                    syncPlaybackState(isBuffering = isBuffering)
                }
            }

            // 时钟漂移监测：绝不在正常播放期间轮询 setPosition 发送 IPC！
            // 仅当实际播放进度与线性插值推算进度差异超过 800ms（如系统调度挂起、音频解码卡顿）时，进行一次静默时钟重标定
            launch {
                NocturnPlayer.currentPositionMs.collect { pos ->
                    if (!SettingsRepository.lyriconEnabled.value || !isRegistered) return@collect
                    if (!NocturnPlayer.isPlaying.value) return@collect

                    val now = SystemClock.elapsedRealtime()
                    if (lastBaseElapsedRealtime > 0L && lastBaseSpeed > 0f) {
                        val estimated = lastBasePositionMs + (now - lastBaseElapsedRealtime)
                        if (abs(pos - estimated) > 800L) {
                            syncPlaybackState(
                                isPlaying = true,
                                positionMs = pos,
                                isBuffering = NocturnPlayer.isBuffering.value
                            )
                        }
                    }
                }
            }
        }
    }

    private fun updateSong(song: NocturnSong?) {
        val p = provider ?: return

        if (song == null) {
            currentSongId = null
            lyricsJob?.cancel()
            try {
                p.player.setSong(null)
            } catch (e: Exception) {
                Log.w(TAG, "Failed to clear song in Lyricon", e)
            }
            return
        }

        if (currentSongId == song.id) {
            return
        }
        currentSongId = song.id

        // 1. 发送歌曲基础元数据占位
        val placeholder = LyriconSong(
            id = song.id.toString(),
            name = song.title,
            artist = song.artist,
            duration = song.durationMs
        )
        try {
            p.player.setSong(placeholder)
            syncPlaybackState(
                isPlaying = NocturnPlayer.isPlaying.value,
                positionMs = NocturnPlayer.currentPositionMs.value,
                isBuffering = NocturnPlayer.isBuffering.value
            )
        } catch (e: Exception) {
            Log.w(TAG, "Failed to set placeholder song in Lyricon", e)
        }

        // 2. 异步获取解析后的逐字/行级歌词
        lyricsJob?.cancel()
        lyricsJob = scope.launch(Dispatchers.IO) {
            try {
                val songLyric = MusicRepository.getLyric(song.id)
                if (currentSongId != song.id) return@launch

                val richLines = convertToLyriconLines(songLyric)
                val fullSong = LyriconSong(
                    id = song.id.toString(),
                    name = song.title,
                    artist = song.artist,
                    duration = if (song.durationMs > 0) song.durationMs else NocturnPlayer.durationMs.value,
                    lyrics = richLines
                ).normalize()

                launch(Dispatchers.Main) {
                    try {
                        p.player.setSong(fullSong)
                        p.player.setDisplayTranslation(true)
                        p.player.setDisplayRoma(true)
                        syncPlaybackState(
                            isPlaying = NocturnPlayer.isPlaying.value,
                            positionMs = NocturnPlayer.currentPositionMs.value,
                            isBuffering = NocturnPlayer.isBuffering.value
                        )
                    } catch (e: Exception) {
                        Log.w(TAG, "Failed to set full song with lyrics in Lyricon", e)
                    }
                }
            } catch (e: Exception) {
                Log.w(TAG, "Failed to load lyrics for Lyricon", e)
            }
        }
    }

    private fun convertToLyriconLines(songLyric: SongLyric): List<LyriconLine> {
        val lines = songLyric.lines
        if (lines.isEmpty()) return emptyList()

        return lines.mapIndexed { index, line ->
            val nextStart = lines.getOrNull(index + 1)?.timeMs ?: (line.timeMs + 5000L)
            val baseEndMs = if (line.durationMs > 0) {
                line.timeMs + line.durationMs
            } else {
                nextStart.coerceAtLeast(line.timeMs + 1000L)
            }

            val words = if (line.hasWordTiming && line.words.isNotEmpty()) {
                line.words.mapNotNull { w ->
                    if (w.text.isEmpty()) return@mapNotNull null
                    val wBegin = w.startMs.coerceAtLeast(line.timeMs)
                    val wEnd = (w.startMs + w.durationMs).coerceAtLeast(wBegin)
                    val wDuration = (wEnd - wBegin).coerceAtLeast(0L)
                    LyriconWord(
                        begin = wBegin,
                        end = wEnd,
                        duration = wDuration,
                        text = w.text
                    )
                }.takeIf { it.isNotEmpty() }
            } else {
                null
            }

            val finalEndMs = if (!words.isNullOrEmpty()) {
                maxOf(baseEndMs, words.last().end)
            } else {
                baseEndMs
            }

            LyriconLine(
                begin = line.timeMs,
                end = finalEndMs,
                duration = (finalEndMs - line.timeMs).coerceAtLeast(0L),
                text = line.text,
                secondary = line.romalrc.takeIf { it.isNotBlank() },
                roma = line.romalrc.takeIf { it.isNotBlank() },
                translation = line.translation.takeIf { it.isNotBlank() },
                words = words
            )
        }
    }

    /**
     * 直接透传 Android 系统 MediaSession 构造的 PlaybackState
     */
    fun updatePlaybackState(playbackState: PlaybackState) {
        val p = provider ?: return
        if (!isRegistered) return
        try {
            p.player.setPlaybackState(playbackState)
            lastBaseElapsedRealtime = playbackState.lastPositionUpdateTime
            lastBasePositionMs = playbackState.position
            lastBaseSpeed = playbackState.playbackSpeed
        } catch (e: Exception) {
            Log.w(TAG, "Failed to update PlaybackState in Lyricon", e)
        }
    }

    /**
     * 根据当前播放状态构建标准 PlaybackState 并发送给词幕中心
     */
    fun syncPlaybackState(
        isPlaying: Boolean = NocturnPlayer.isPlaying.value,
        positionMs: Long = NocturnPlayer.currentPositionMs.value,
        isBuffering: Boolean = NocturnPlayer.isBuffering.value
    ) {
        val p = provider ?: return
        if (!isRegistered) return

        val state = when {
            isBuffering -> PlaybackState.STATE_BUFFERING
            isPlaying -> PlaybackState.STATE_PLAYING
            else -> PlaybackState.STATE_PAUSED
        }
        val speed = if (isPlaying && !isBuffering) 1.0f else 0.0f
        val now = SystemClock.elapsedRealtime()

        val playbackState = PlaybackState.Builder()
            .setState(state, positionMs.coerceAtLeast(0L), speed, now)
            .setActions(
                PlaybackState.ACTION_PLAY or
                    PlaybackState.ACTION_PAUSE or
                    PlaybackState.ACTION_PLAY_PAUSE or
                    PlaybackState.ACTION_SKIP_TO_NEXT or
                    PlaybackState.ACTION_SKIP_TO_PREVIOUS or
                    PlaybackState.ACTION_SEEK_TO or
                    PlaybackState.ACTION_STOP
            )
            .build()

        updatePlaybackState(playbackState)
    }

    /**
     * 进度拖动 (Seek) 处理
     * 保持连续时钟自动插值，消除跳帧
     */
    fun onSeek(positionMs: Long) {
        val p = provider ?: return
        if (!isRegistered) return
        try {
            p.player.seekTo(positionMs)
        } catch (e: Exception) {
            Log.w(TAG, "Failed to send seekTo to Lyricon", e)
        }
        // 关键：在 seekTo 后立即同步 PlaybackState，让词幕基于全新起始时间重新连续插值
        syncPlaybackState(
            isPlaying = NocturnPlayer.isPlaying.value,
            positionMs = positionMs,
            isBuffering = NocturnPlayer.isBuffering.value
        )
    }

    private fun syncCurrentState() {
        val song = NocturnPlayer.currentSong.value
        currentSongId = null // force refresh
        updateSong(song)
        syncPlaybackState()
    }

    fun release() {
        observeJob?.cancel()
        lyricsJob?.cancel()
        try {
            provider?.unregister()
            provider?.destroy()
        } catch (e: Exception) {
            Log.w(TAG, "Error releasing LyriconProvider", e)
        }
        provider = null
        isRegistered = false
        currentSongId = null
    }
}
