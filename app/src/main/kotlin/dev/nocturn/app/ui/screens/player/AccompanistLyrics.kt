package dev.nocturn.app.ui.screens.player

import com.mocharealm.accompanist.lyrics.core.model.SyncedLyrics
import com.mocharealm.accompanist.lyrics.core.model.karaoke.KaraokeAlignment
import com.mocharealm.accompanist.lyrics.core.model.karaoke.KaraokeLine
import com.mocharealm.accompanist.lyrics.core.model.karaoke.KaraokeSyllable
import com.mocharealm.accompanist.lyrics.core.model.synced.SyncedLine
import dev.nocturn.app.data.model.Lyrics

/** Keep ordinary LRC line timing; only real YRC words receive a karaoke sweep. */
internal fun Lyrics.toAccompanistLyrics(durationMs: Long): SyncedLyrics {
    val sorted = lines.sortedBy { it.startMs }
    return SyncedLyrics(sorted.mapIndexed { index, line ->
        val start = line.startMs.coerceIn(0L, Int.MAX_VALUE - 1L)
        val nextStart = sorted.getOrNull(index + 1)?.startMs
        val length = line.durationMs?.takeIf { it > 0L }
            ?: nextStart?.let { (it - start).coerceAtLeast(1L) }
            ?: (durationMs - start).takeIf { it > 0L }
            ?: 5_000L
        val end = start + length.coerceIn(1L, Int.MAX_VALUE - start)
        // The upstream interval is inclusive. Adjacent LRC rows must not both focus at a boundary.
        val inclusiveEnd = (end - 1).coerceAtLeast(start).toInt()
        val words = line.words.sortedBy { it.startMs }.map { word ->
            val wordStart = word.startMs.coerceIn(start, inclusiveEnd.toLong()).toInt()
            val wordEnd = (wordStart.toLong() + word.durationMs.coerceIn(1L, Int.MAX_VALUE - wordStart.toLong()))
                .coerceAtMost(end).toInt()
            KaraokeSyllable(word.text, wordStart, wordEnd)
        }
        if (words.isNotEmpty() && words.joinToString("") { it.content }.trim() == line.text) {
            KaraokeLine.MainKaraokeLine(
                syllables = words,
                translation = line.translation,
                alignment = KaraokeAlignment.Start,
                start = start.toInt(),
                end = inclusiveEnd,
                phonetic = line.romanization,
            )
        } else {
            SyncedLine(
                content = line.text,
                translation = line.translation,
                start = start.toInt(),
                end = inclusiveEnd,
                phonetic = line.romanization,
            )
        }
    })
}

/** Bound interpolation to one missed controller sample so a stalled source cannot run away. */
internal fun interpolatedLyricsPosition(
    positionMs: Long,
    elapsedMs: Long,
    playing: Boolean,
    durationMs: Long,
    playbackSpeed: Float = 1f,
): Int {
    val limit = durationMs.takeIf { it > 0L }?.coerceAtMost(Int.MAX_VALUE.toLong())
        ?: Int.MAX_VALUE.toLong()
    val base = positionMs.coerceIn(0L, limit)
    val speed = playbackSpeed.takeIf { it.isFinite() && it > 0f } ?: 1f
    val advance = if (playing) elapsedMs.coerceIn(0L, 750L).toDouble() * speed else 0.0
    return (base + advance.coerceAtMost((limit - base).toDouble()).toLong()).toInt()
}
