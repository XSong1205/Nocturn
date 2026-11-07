package dev.nocturn.app

import android.os.Bundle
import android.os.SystemClock
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import dev.nocturn.app.data.model.ThemeMode
import dev.nocturn.app.ui.preview.PreviewData
import dev.nocturn.app.ui.screens.player.PlayerScreen
import dev.nocturn.app.ui.theme.NocturnTheme
import kotlinx.coroutines.delay

/** Repeats the same wrapped YRC/translation transitions without network, accounts or audio. */
class LyricsPerformanceActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        val animate = !intent.getBooleanExtra("paused", false)
        val dark = !intent.getBooleanExtra("light", false)
        setContent {
            var state by remember { mutableStateOf(PreviewData.bilingualPlayerState.copy(playing = animate)) }
            var seekRevision by remember { mutableIntStateOf(0) }
            LaunchedEffect(state.playing, seekRevision) {
                if (!state.playing) return@LaunchedEffect
                val startedAt = SystemClock.uptimeMillis()
                val startPosition = state.positionMs
                while (true) {
                    val position = 100_000L + (startPosition - 100_000L + SystemClock.uptimeMillis() - startedAt) % 44_000L
                    state = state.copy(positionMs = position)
                    delay(500)
                }
            }
            NocturnTheme(themeMode = if (dark) ThemeMode.Dark else ThemeMode.Light) {
                PlayerScreen(
                    state = state,
                    initialLyricsVisible = true,
                    onCollapse = { finish() },
                    onTogglePlay = { state = state.copy(playing = !state.playing) },
                    onSeek = {
                        state = state.copy(positionMs = (it * state.durationMs).toLong().coerceIn(100_000L, 143_999L))
                        seekRevision++
                    },
                )
            }
        }
    }
}
