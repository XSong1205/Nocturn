package com.nocturn.music.player

import android.content.Context
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
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.launch

/**
 * 词幕 (Lyricon) 歌词提供端桥接控制器
 * 负责向词幕中心服务推送播放状态、曲目元数据、YRC 逐字歌词与 LRC 行级歌词。
 */
object LyriconBridge {
    private const val TAG = "LyriconBridge"

    private var provider: LyriconProvider? = null
    private val scope = CoroutineScope(Dispatchers.Main + SupervisorJob())

    private var lyricsJob: Job? = null
    private var observeJob: Job? = null
    private var isRegistered = false
    private var currentSongId: Long? = null

    private var lastPositionSyncTime = 0L

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

            // 监听播放/暂停状态
            launch {
                NocturnPlayer.isPlaying.collectLatest { isPlaying ->
                    if (!SettingsRepository.lyriconEnabled.value || !isRegistered) return@collectLatest
                    onPlaybackStateChanged(isPlaying)
                }
            }

            // 节流监听播放进度 (500ms 刷新一次 IPC)
            launch {
                NocturnPlayer.currentPositionMs.collect { pos ->
                    if (!SettingsRepository.lyriconEnabled.value || !isRegistered) return@collect
                    onPositionUpdate(pos)
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
            p.player.setPlaybackState(NocturnPlayer.isPlaying.value)
            p.player.setPosition(NocturnPlayer.currentPositionMs.value)
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
                )

                launch(Dispatchers.Main) {
                    try {
                        p.player.setSong(fullSong)
                        p.player.setDisplayTranslation(true)
                        p.player.setDisplayRoma(true)
                        p.player.setPosition(NocturnPlayer.currentPositionMs.value)
                        p.player.setPlaybackState(NocturnPlayer.isPlaying.value)
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
            val endMs = if (line.durationMs > 0) {
                line.timeMs + line.durationMs
            } else {
                nextStart.coerceAtLeast(line.timeMs + 1000L)
            }

            val words = if (line.hasWordTiming) {
                line.words.map { w ->
                    LyriconWord(
                        text = w.text,
                        begin = w.startMs,
                        end = w.startMs + w.durationMs
                    )
                }
            } else {
                null
            }

            LyriconLine(
                begin = line.timeMs,
                end = endMs,
                text = line.text,
                secondary = line.romalrc.takeIf { it.isNotBlank() },
                translation = line.translation.takeIf { it.isNotBlank() },
                words = words
            )
        }
    }

    fun onPlaybackStateChanged(isPlaying: Boolean) {
        val p = provider ?: return
        if (!isRegistered) return
        try {
            p.player.setPlaybackState(isPlaying)
        } catch (e: Exception) {
            Log.w(TAG, "Failed to sync playback state to Lyricon", e)
        }
    }

    fun onSeek(positionMs: Long) {
        val p = provider ?: return
        if (!isRegistered) return
        try {
            p.player.seekTo(positionMs)
        } catch (e: Exception) {
            Log.w(TAG, "Failed to send seekTo to Lyricon", e)
        }
    }

    fun onPositionUpdate(positionMs: Long, force: Boolean = false) {
        val p = provider ?: return
        if (!isRegistered) return

        val now = SystemClock.elapsedRealtime()
        if (force || now - lastPositionSyncTime >= 500L) {
            lastPositionSyncTime = now
            try {
                p.player.setPosition(positionMs)
            } catch (e: Exception) {
                // ignore
            }
        }
    }

    private fun syncCurrentState() {
        val song = NocturnPlayer.currentSong.value
        currentSongId = null // force refresh
        updateSong(song)
        onPositionUpdate(NocturnPlayer.currentPositionMs.value, force = true)
        onPlaybackStateChanged(NocturnPlayer.isPlaying.value)
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
