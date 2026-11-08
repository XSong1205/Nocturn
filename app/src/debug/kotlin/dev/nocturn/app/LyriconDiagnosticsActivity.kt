package dev.nocturn.app

import android.app.Activity
import android.os.Bundle
import android.widget.TextView
import dev.nocturn.app.data.MusicRepository
import dev.nocturn.app.data.model.LyricLine
import dev.nocturn.app.data.model.LyricWord
import dev.nocturn.app.data.model.Lyrics
import dev.nocturn.app.playback.PlayableSong
import dev.nocturn.app.playback.PlaybackEngine
import dev.nocturn.app.playback.PlaybackSnapshot
import dev.nocturn.app.playback.RepeatMode
import dev.nocturn.app.playback.lyricon.DefaultLyriconProviderBridge
import dev.nocturn.app.playback.lyricon.LyriconManager
import dev.nocturn.app.playback.lyricon.LyriconProviderBridge
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.launch
import java.io.File

/** Silent, offline end-to-end Provider probe. Only included in debug APKs. */
class LyriconDiagnosticsActivity : Activity() {
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Main.immediate)
    private var manager: LyriconManager? = null

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        val container = (application as NocturnApplication).container
        val status = TextView(this).apply {
            textSize = 20f
            setPadding(40, 160, 40, 40)
        }
        setContentView(status)
        val report = File(filesDir, "lyricon-diagnostics.txt").apply { writeText("") }
        fun record(message: String) {
            report.appendText("$message\n")
            status.text = report.readText()
        }
        if (!container.settings.lyriconEnabled) {
            record("Enable Lyricon in Nocturn settings before running this probe.")
            return
        }
        // Avoid two providers with the same identity competing for the central connection.
        container.lyriconManager.stop()
        val snapshot = MutableStateFlow(PlaybackSnapshot())
        val engine = object : PlaybackEngine {
            override val snapshot = snapshot
            override suspend fun connect() = Unit
            override fun setQueue(songs: List<PlayableSong>, startIndex: Int) = Unit
            override fun play() = Unit
            override fun pause() = Unit
            override fun seekTo(positionMs: Long) = Unit
            override fun next() = Unit
            override fun previous() = Unit
            override fun setRepeatMode(mode: RepeatMode) = Unit
            override fun setShuffle(enabled: Boolean) = Unit
            override fun clearError() = Unit
            override fun release() = Unit
        }
        val lyrics = Lyrics(
            List(6) { index ->
                val begin = index * 3_000L
                LyricLine(
                    startMs = begin,
                    durationMs = 3_000L,
                    text = "Lyricon line ${index + 1}",
                    translation = "词幕同步测试 ${index + 1}",
                    words = listOf(
                        LyricWord(begin, 1_000L, "Lyricon "),
                        LyricWord(begin + 1_000L, 1_000L, "line "),
                        LyricWord(begin + 2_000L, 1_000L, "${index + 1}"),
                    ),
                )
            },
        )
        val repository = object : MusicRepository by container.repository {
            override suspend fun lyrics(songId: Long): Lyrics {
                delay(1_000L)
                return lyrics
            }
        }
        manager = LyriconManager(this, engine, repository, container.settings, scope) { context ->
            val delegate = DefaultLyriconProviderBridge(context)
            object : LyriconProviderBridge by delegate {
                override fun setOnConnectedListener(listener: (() -> Unit)?) {
                    delegate.setOnConnectedListener(listener?.let {
                        {
                            record("CONNECTED: real Lyricon central service")
                            it()
                        }
                    })
                }

                override fun register(): Boolean = delegate.register().also {
                    record("REGISTER requested=$it")
                }
            }
        }.also { it.start() }
        scope.launch {
            fun sample(position: Long, playing: Boolean = true, discontinuity: Long = 0L, id: Long = 1L) {
                snapshot.value = PlaybackSnapshot(
                    currentSongId = id, title = "Nocturn Lyricon probe $id", artist = "Nocturn",
                    durationMs = 18_000L, playing = playing, positionMs = position,
                    positionDiscontinuity = discontinuity,
                )
            }
            record("PLAY: expect lines 1, 2, 3, 4 every 3 seconds")
            for (position in 0L..11_500L step 500L) {
                sample(position)
                delay(500L)
            }
            record("PAUSE: expect line 4 to remain still")
            sample(11_500L, playing = false)
            delay(4_000L)
            record("SEEK: back to line 2, then resume")
            for (position in 3_000L..7_500L step 500L) {
                sample(position, discontinuity = 1L)
                delay(500L)
            }
            record("SWITCH: old lyrics must clear during the 1-second lookup")
            for (position in 0L..6_000L step 500L) {
                sample(position, discontinuity = 2L, id = 2L)
                delay(500L)
            }
            record("STOP: display must clear; probe complete")
            manager?.stop()
            finish()
        }
    }

    override fun onDestroy() {
        manager?.destroy()
        scope.cancel()
        if (manager != null) (application as NocturnApplication).container.lyriconManager.start()
        super.onDestroy()
    }
}
