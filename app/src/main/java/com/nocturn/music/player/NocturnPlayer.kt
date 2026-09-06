package com.nocturn.music.player

import android.media.AudioAttributes
import android.media.MediaPlayer
import com.nocturn.music.NocturnApp
import com.nocturn.music.data.repository.SettingsRepository
import com.nocturn.music.model.PlayMode
import com.nocturn.music.model.Song
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import java.util.Random

object NocturnPlayer {
    private var mediaPlayer: MediaPlayer? = null
    private val scope = CoroutineScope(Dispatchers.Main + Job())
    private var progressJob: Job? = null
    private val random = Random()

    private val _currentSong = MutableStateFlow<Song?>(null)
    val currentSong = _currentSong.asStateFlow()

    private val _isPlaying = MutableStateFlow(false)
    val isPlaying = _isPlaying.asStateFlow()

    private val _currentPositionMs = MutableStateFlow(0L)
    val currentPositionMs = _currentPositionMs.asStateFlow()

    private val _durationMs = MutableStateFlow(0L)
    val durationMs = _durationMs.asStateFlow()

    private val _playMode = MutableStateFlow(PlayMode.LIST_LOOP)
    val playMode = _playMode.asStateFlow()

    private val _playQueue = MutableStateFlow<List<Song>>(emptyList())
    val playQueue = _playQueue.asStateFlow()

    private val _queueIndex = MutableStateFlow(-1)
    val queueIndex = _queueIndex.asStateFlow()

    private val _isBuffering = MutableStateFlow(false)
    val isBuffering = _isBuffering.asStateFlow()

    private val _errorMessage = MutableStateFlow<String?>(null)
    val errorMessage = _errorMessage.asStateFlow()

    private fun initMediaPlayer() {
        if (mediaPlayer == null) {
            mediaPlayer = MediaPlayer().apply {
                setAudioAttributes(
                    AudioAttributes.Builder()
                        .setContentType(AudioAttributes.CONTENT_TYPE_MUSIC)
                        .setUsage(AudioAttributes.USAGE_MEDIA)
                        .build()
                )
                setOnPreparedListener { mp ->
                    _isBuffering.value = false
                    _durationMs.value = mp.duration.toLong()
                    mp.start()
                    _isPlaying.value = true
                    startProgressTracker()
                    PlaybackService.start(NocturnApp.appContext, _currentSong.value, true)
                }
                setOnCompletionListener {
                    handleTrackCompletion()
                }
                setOnErrorListener { _, what, extra ->
                    _isBuffering.value = false
                    _isPlaying.value = false
                    _errorMessage.value = "播放失败 (错误码 $what:$extra)，可能需要 VIP 或网络异常"
                    stopProgressTracker()
                    true
                }
                setOnBufferingUpdateListener { _, _ -> }
            }
        }
    }

    fun playSong(song: Song) {
        val currentList = _playQueue.value.toMutableList()
        val existingIndex = currentList.indexOfFirst { it.id == song.id }
        if (existingIndex >= 0) {
            _queueIndex.value = existingIndex
        } else {
            currentList.add(song)
            _playQueue.value = currentList
            _queueIndex.value = currentList.size - 1
        }
        playCurrentSong()
    }

    fun playQueue(songs: List<Song>, startIndex: Int = 0) {
        if (songs.isEmpty()) return
        _playQueue.value = songs
        _queueIndex.value = startIndex.coerceIn(0, songs.size - 1)
        playCurrentSong()
    }

    fun playQueueShuffled(songs: List<Song>) {
        if (songs.isEmpty()) return
        val shuffled = songs.shuffled()
        _playQueue.value = shuffled
        _queueIndex.value = 0
        _playMode.value = PlayMode.RANDOM
        playCurrentSong()
    }

    private var playJob: Job? = null

    private fun playCurrentSong() {
        val index = _queueIndex.value
        val queue = _playQueue.value
        if (index < 0 || index >= queue.size) return

        val song = queue[index]
        _currentSong.value = song
        _errorMessage.value = null
        _isBuffering.value = true
        _currentPositionMs.value = 0L

        playJob?.cancel()
        playJob = scope.launch {
            try {
                initMediaPlayer()
                val player = mediaPlayer ?: return@launch
                player.reset()

                val quality = SettingsRepository.audioQuality.value
                val playUrl = if (song.streamUrl.isNotBlank()) {
                    song.streamUrl
                } else {
                    com.nocturn.music.data.repository.MusicRepository.getSongPlayUrl(song.id, quality.level)
                }

                player.setDataSource(playUrl)
                player.prepareAsync()
            } catch (e: Exception) {
                e.printStackTrace()
                _isBuffering.value = false
                _errorMessage.value = "无法加载音频流: ${e.message}"
            }
        }
    }

    fun togglePlayPause() {
        val player = mediaPlayer ?: return
        if (player.isPlaying) {
            player.pause()
            _isPlaying.value = false
            stopProgressTracker()
            PlaybackService.start(NocturnApp.appContext, _currentSong.value, false)
        } else {
            player.start()
            _isPlaying.value = true
            startProgressTracker()
            PlaybackService.start(NocturnApp.appContext, _currentSong.value, true)
        }
    }

    fun playNext() {
        val queue = _playQueue.value
        if (queue.isEmpty()) return

        when (_playMode.value) {
            PlayMode.RANDOM -> {
                if (queue.size > 1) {
                    var nextIndex: Int
                    do {
                        nextIndex = random.nextInt(queue.size)
                    } while (nextIndex == _queueIndex.value)
                    _queueIndex.value = nextIndex
                }
            }
            PlayMode.SINGLE_LOOP -> {
                _queueIndex.value = (_queueIndex.value + 1) % queue.size
            }
            PlayMode.LIST_LOOP -> {
                _queueIndex.value = (_queueIndex.value + 1) % queue.size
            }
        }
        playCurrentSong()
    }

    fun playPrevious() {
        val queue = _playQueue.value
        if (queue.isEmpty()) return

        if (_currentPositionMs.value > 3000L) {
            seekTo(0L)
            return
        }

        when (_playMode.value) {
            PlayMode.RANDOM -> {
                if (queue.size > 1) {
                    _queueIndex.value = random.nextInt(queue.size)
                }
            }
            else -> {
                _queueIndex.value = if (_queueIndex.value - 1 < 0) queue.size - 1 else _queueIndex.value - 1
            }
        }
        playCurrentSong()
    }

    fun seekTo(positionMs: Long) {
        val player = mediaPlayer ?: return
        val target = positionMs.coerceIn(0L, _durationMs.value)
        player.seekTo(target.toInt())
        _currentPositionMs.value = target
    }

    fun togglePlayMode(): PlayMode {
        val next = _playMode.value.next()
        _playMode.value = next
        return next
    }

    fun setPlayMode(mode: PlayMode) {
        _playMode.value = mode
    }

    fun addToQueue(song: Song) {
        val current = _playQueue.value.toMutableList()
        if (current.none { it.id == song.id }) {
            current.add(song)
            _playQueue.value = current
        }
    }

    fun playNextInQueue(song: Song) {
        val current = _playQueue.value.toMutableList()
        current.removeAll { it.id == song.id }
        val insertIndex = (_queueIndex.value + 1).coerceAtMost(current.size)
        current.add(insertIndex, song)
        _playQueue.value = current
    }

    fun removeFromQueue(index: Int) {
        val current = _playQueue.value.toMutableList()
        if (index in current.indices) {
            val removedIndex = index
            val curIndex = _queueIndex.value
            current.removeAt(removedIndex)
            _playQueue.value = current

            if (current.isEmpty()) {
                stop()
            } else if (removedIndex == curIndex) {
                _queueIndex.value = removedIndex.coerceAtMost(current.size - 1)
                playCurrentSong()
            } else if (removedIndex < curIndex) {
                _queueIndex.value = curIndex - 1
            }
        }
    }

    fun clearQueue() {
        stop()
        _playQueue.value = emptyList()
        _queueIndex.value = -1
        _currentSong.value = null
    }

    fun stop() {
        stopProgressTracker()
        try {
            mediaPlayer?.stop()
            mediaPlayer?.reset()
        } catch (e: Exception) {
            e.printStackTrace()
        }
        _isPlaying.value = false
        _currentPositionMs.value = 0L
        _durationMs.value = 0L
        PlaybackService.stop(NocturnApp.appContext)
    }

    private fun handleTrackCompletion() {
        when (_playMode.value) {
            PlayMode.SINGLE_LOOP -> {
                seekTo(0L)
                mediaPlayer?.start()
                _isPlaying.value = true
                startProgressTracker()
            }
            PlayMode.RANDOM -> {
                playNext()
            }
            PlayMode.LIST_LOOP -> {
                playNext()
            }
        }
    }

    private fun startProgressTracker() {
        stopProgressTracker()
        progressJob = scope.launch {
            while (isActive) {
                try {
                    mediaPlayer?.let { player ->
                        if (player.isPlaying) {
                            _currentPositionMs.value = player.currentPosition.toLong()
                        }
                    }
                } catch (e: Exception) {
                    // Ignored
                }
                delay(30)
            }
        }
    }

    private fun stopProgressTracker() {
        progressJob?.cancel()
        progressJob = null
    }
}
