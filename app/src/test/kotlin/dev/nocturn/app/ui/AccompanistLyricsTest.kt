package dev.nocturn.app.ui

import com.mocharealm.accompanist.lyrics.core.model.karaoke.KaraokeLine
import com.mocharealm.accompanist.lyrics.core.model.synced.SyncedLine
import dev.nocturn.app.data.model.LyricLine
import dev.nocturn.app.data.model.LyricWord
import dev.nocturn.app.data.model.Lyrics
import dev.nocturn.app.ui.screens.player.interpolatedLyricsPosition
import dev.nocturn.app.ui.screens.player.toAccompanistLyrics
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class AccompanistLyricsTest {
    @Test
    fun yrcKeepsAbsoluteSyllableTimesAndCaptions() {
        val source = Lyrics(listOf(LyricLine(
            startMs = 16_210,
            text = "还没有",
            translation = "Not yet",
            durationMs = 3_460,
            words = listOf(LyricWord(16_210, 670, "还"), LyricWord(16_880, 410, "没"), LyricWord(17_290, 320, "有")),
            romanization = "hai mei you",
        )))
        val line = source.toAccompanistLyrics(60_000).lines.single() as KaraokeLine.MainKaraokeLine
        assertEquals(listOf(16_210, 16_880, 17_290), line.syllables.map { it.start })
        assertEquals(listOf(16_880, 17_290, 17_610), line.syllables.map { it.end })
        assertEquals(19_669, line.end)
        assertEquals("Not yet", line.translation)
        assertEquals("hai mei you", line.phonetic)
    }

    @Test
    fun lrcUsesNextTimestampAndTrackEndWithoutInventingWords() {
        val mapped = Lyrics(listOf(LyricLine(1_000, "第一句"), LyricLine(3_000, "最后一句")))
            .toAccompanistLyrics(10_000)
        assertTrue(mapped.lines.all { it is SyncedLine })
        assertEquals(listOf(2_999, 9_999), mapped.lines.map { it.end })
        assertEquals(listOf(1), mapped.getCurrentAllHighlightLineIndicesByTime(3_000))
        assertTrue(mapped.getCurrentAllHighlightLineIndicesByTime(999).isEmpty())
    }

    @Test
    fun yrcInterludeRemainsUnfocusedUntilTheNextVocal() {
        val mapped = Lyrics(listOf(
            LyricLine(1_000, "第一句", durationMs = 500, words = listOf(LyricWord(1_000, 500, "第一句"))),
            LyricLine(5_000, "第二句"),
        )).toAccompanistLyrics(10_000)
        assertTrue(mapped.getCurrentAllHighlightLineIndicesByTime(3_000).isEmpty())
    }

    @Test
    fun partialWordDataFallsBackToTheCompleteLine() {
        val mapped = Lyrics(listOf(LyricLine(
            1_000, "完整歌词", words = listOf(LyricWord(1_000, 500, "完整")),
        ))).toAccompanistLyrics(10_000)
        assertEquals("完整歌词", (mapped.lines.single() as SyncedLine).content)
    }

    @Test
    fun extremeTimestampsAndDurationsDoNotOverflow() {
        val mapped = Lyrics(listOf(LyricLine(
            Long.MAX_VALUE, "结尾", durationMs = Long.MAX_VALUE,
            words = listOf(LyricWord(Long.MAX_VALUE, Long.MAX_VALUE, "结尾")),
        ))).toAccompanistLyrics(Long.MAX_VALUE)
        val line = mapped.lines.single() as KaraokeLine.MainKaraokeLine
        assertTrue(line.start >= 0 && line.end >= line.start)
        assertTrue(line.syllables.single().end >= line.syllables.single().start)
    }

    @Test
    fun clockFreezesForPauseAndBufferingAndBoundsMissingSamples() {
        assertEquals(1_250, interpolatedLyricsPosition(1_000, 250, true, 10_000))
        assertEquals(1_000, interpolatedLyricsPosition(1_000, 250, false, 10_000))
        assertEquals(1_750, interpolatedLyricsPosition(1_000, 10_000, true, 10_000))
        assertEquals(10_000, interpolatedLyricsPosition(9_900, 250, true, 10_000))
        assertEquals(0, interpolatedLyricsPosition(-10, 0, false, 0))
        assertEquals(Int.MAX_VALUE, interpolatedLyricsPosition(Long.MAX_VALUE, 500, true, Long.MAX_VALUE))
        // A new controller anchor takes effect immediately, including a backward seek.
        assertEquals(500, interpolatedLyricsPosition(500, 0, true, 10_000))
    }
}
