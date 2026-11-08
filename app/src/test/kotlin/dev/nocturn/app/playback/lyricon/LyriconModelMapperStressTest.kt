package dev.nocturn.app.playback.lyricon

import dev.nocturn.app.data.model.ArtistRef
import dev.nocturn.app.data.model.LyricLine as NocturnLyricLine
import dev.nocturn.app.data.model.LyricWord as NocturnLyricWord
import dev.nocturn.app.data.model.Lyrics as NocturnLyrics
import dev.nocturn.app.data.model.Song as NocturnSong
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import kotlin.random.Random

/**
 * Adversarial stress tests and boundary edge cases for [LyriconModelMapper].
 */
class LyriconModelMapperStressTest {

    // =========================================================================
    // Category 1: Extreme Values & Long Overflow / Underflow
    // =========================================================================

    @Test
    fun handlesLineStartAtLongMaxValueWithoutOverflowCrash() {
        val lyrics = NocturnLyrics(
            lines = listOf(
                NocturnLyricLine(startMs = Long.MAX_VALUE, durationMs = null, text = "极值测试"),
            ),
        )

        val lines = LyriconModelMapper.toLyriconLines(lyrics, songDurationMs = 100_000L)

        assertEquals(1, lines.size)
        assertEquals(Long.MAX_VALUE, lines[0].begin)
        assertTrue("line.end must be >= line.begin", lines[0].end >= lines[0].begin)
        assertEquals(Long.MAX_VALUE, lines[0].end)
    }

    @Test
    fun handlesLineStartNearLongMaxValueWithPositiveDurationWithoutOverflow() {
        val lyrics = NocturnLyrics(
            lines = listOf(
                NocturnLyricLine(startMs = Long.MAX_VALUE - 100L, durationMs = 500L, text = "接近极大值"),
            ),
        )

        val lines = LyriconModelMapper.toLyriconLines(lyrics, songDurationMs = Long.MAX_VALUE)

        assertEquals(1, lines.size)
        assertEquals(Long.MAX_VALUE - 100L, lines[0].begin)
        assertEquals(Long.MAX_VALUE, lines[0].end)
        assertTrue(lines[0].end > lines[0].begin)
    }

    @Test
    fun handlesLineStartAndDurationAtLongMinValue() {
        val lyrics = NocturnLyrics(
            lines = listOf(
                NocturnLyricLine(startMs = Long.MIN_VALUE, durationMs = Long.MIN_VALUE, text = "极小负值"),
            ),
        )

        val lines = LyriconModelMapper.toLyriconLines(lyrics, songDurationMs = Long.MIN_VALUE)

        assertEquals(1, lines.size)
        assertEquals(0L, lines[0].begin)
        assertTrue("End must be positive fallback", lines[0].end >= lines[0].begin + 1L)
        assertEquals(LyriconModelMapper.DEFAULT_LINE_DURATION_MS, lines[0].end)
    }

    @Test
    fun handlesWordsAtLongMaxValueWithoutExceptionInCoerceIn() {
        val lyrics = NocturnLyrics(
            lines = listOf(
                NocturnLyricLine(
                    startMs = Long.MAX_VALUE,
                    durationMs = 1000L,
                    text = "极值词",
                    words = listOf(
                        NocturnLyricWord(startMs = Long.MAX_VALUE, durationMs = Long.MAX_VALUE, text = "极"),
                        NocturnLyricWord(startMs = Long.MAX_VALUE - 50L, durationMs = 100L, text = "值"),
                    ),
                ),
            ),
        )

        val lines = LyriconModelMapper.toLyriconLines(lyrics, songDurationMs = Long.MAX_VALUE)

        assertEquals(1, lines.size)
        val words = lines[0].words
        assertNotNull(words)
        assertEquals(2, words!!.size)
        for (w in words) {
            assertTrue("Word begin must be within line bounds", w.begin >= lines[0].begin)
            assertTrue("Word end must be within line bounds", w.end <= lines[0].end)
            assertTrue("Word end must be >= begin", w.end >= w.begin)
        }
    }

    @Test
    fun handlesExtremeSongIds() {
        val testIds = listOf(Long.MAX_VALUE, Long.MIN_VALUE, 0L, -1L, -999999L)
        for (id in testIds) {
            val song = LyriconModelMapper.toLyriconSong(
                id = id,
                title = "测试ID",
                artist = "测试",
                durationMs = 60_000L,
            )
            assertEquals(id.toString(), song.id)
        }
    }

    @Test
    fun handlesSongDurationAtLongMaxValue() {
        val lyrics = NocturnLyrics(
            lines = listOf(
                NocturnLyricLine(startMs = 10_000L, durationMs = null, text = "超长歌曲最后一句"),
            ),
        )

        val lines = LyriconModelMapper.toLyriconLines(lyrics, songDurationMs = Long.MAX_VALUE)

        assertEquals(1, lines.size)
        assertEquals(10_000L, lines[0].begin)
        assertEquals(Long.MAX_VALUE, lines[0].end)
    }

    @Test
    fun handlesNegativeSongDuration() {
        val lyrics = NocturnLyrics(
            lines = listOf(
                NocturnLyricLine(startMs = 10_000L, durationMs = null, text = "负歌曲时长"),
            ),
        )

        val lines = LyriconModelMapper.toLyriconLines(lyrics, songDurationMs = -50_000L)

        assertEquals(1, lines.size)
        assertEquals(10_000L, lines[0].begin)
        assertEquals(10_000L + LyriconModelMapper.DEFAULT_LINE_DURATION_MS, lines[0].end)
    }

    // =========================================================================
    // Category 2: Boundary, Empty, Blank, and Pure Music Scenarios
    // =========================================================================

    @Test
    fun filtersOutOnlyBlankAndWhitespaceLines() {
        val lyrics = NocturnLyrics(
            lines = listOf(
                NocturnLyricLine(startMs = 1000L, text = ""),
                NocturnLyricLine(startMs = 2000L, text = "   "),
                NocturnLyricLine(startMs = 3000L, text = "\t\n\r"),
                NocturnLyricLine(startMs = 4000L, text = " 　 "), // Ideographic space
            ),
        )

        val lines = LyriconModelMapper.toLyriconLines(lyrics, songDurationMs = 60_000L)
        assertTrue(lines.isEmpty())
    }

    @Test
    fun correctlyInterpolatesNextStartMsWhenInterspersedWithBlankLines() {
        val lyrics = NocturnLyrics(
            lines = listOf(
                NocturnLyricLine(startMs = 10_000L, durationMs = null, text = "第一有效句"),
                NocturnLyricLine(startMs = 15_000L, durationMs = null, text = "   "), // Blank
                NocturnLyricLine(startMs = 20_000L, durationMs = null, text = "\t"),   // Blank
                NocturnLyricLine(startMs = 25_000L, durationMs = null, text = "第二有效句"),
            ),
        )

        val lines = LyriconModelMapper.toLyriconLines(lyrics, songDurationMs = 60_000L)

        assertEquals(2, lines.size)
        assertEquals("第一有效句", lines[0].text)
        assertEquals(10_000L, lines[0].begin)
        assertEquals(25_000L, lines[0].end) // Must bridge past the blank lines to 25_000L
        assertEquals("第二有效句", lines[1].text)
        assertEquals(25_000L, lines[1].begin)
        assertEquals(60_000L, lines[1].end)
    }

    @Test
    fun preservesSpecialUnicodeAndEmojisInLyricText() {
        val specialText = "🔥 夜曲 🎶 𝄞 \uD83C\uDFB5 [Live] 特殊字符"
        val lyrics = NocturnLyrics(
            lines = listOf(
                NocturnLyricLine(startMs = 1_000L, durationMs = 2_000L, text = "  $specialText  "),
            ),
        )

        val lines = LyriconModelMapper.toLyriconLines(lyrics, songDurationMs = 30_000L)

        assertEquals(1, lines.size)
        assertEquals(specialText, lines[0].text)
    }

    @Test
    fun handlesSongWithBlankTitleAndWhitespaceOnlyArtist() {
        val song = LyriconModelMapper.toLyriconSong(
            id = 123L,
            title = "   ",
            artist = "  \t  ",
            durationMs = 10_000L,
        )

        assertEquals("未知曲目", song.name)
        assertNull(song.artist)
    }

    // =========================================================================
    // Category 3: Chronological Chaos & Duplicate Timestamps
    // =========================================================================

    @Test
    fun handlesCompletelyReversedLinesProperly() {
        val lyrics = NocturnLyrics(
            lines = listOf(
                NocturnLyricLine(startMs = 50_000L, durationMs = null, text = "第5句"),
                NocturnLyricLine(startMs = 40_000L, durationMs = null, text = "第4句"),
                NocturnLyricLine(startMs = 30_000L, durationMs = null, text = "第3句"),
                NocturnLyricLine(startMs = 20_000L, durationMs = null, text = "第2句"),
                NocturnLyricLine(startMs = 10_000L, durationMs = null, text = "第1句"),
            ),
        )

        val lines = LyriconModelMapper.toLyriconLines(lyrics, songDurationMs = 60_000L)

        assertEquals(5, lines.size)
        val texts = lines.map { it.text }
        assertEquals(listOf("第1句", "第2句", "第3句", "第4句", "第5句"), texts)
        for (i in 0 until lines.size - 1) {
            assertTrue("Lines must be sorted by begin", lines[i].begin <= lines[i + 1].begin)
            assertEquals("End of line $i must equal begin of line ${i + 1}", lines[i + 1].begin, lines[i].end)
        }
    }

    @Test
    fun handlesAllLinesWithIdenticalStartMs() {
        val lyrics = NocturnLyrics(
            lines = listOf(
                NocturnLyricLine(startMs = 15_000L, durationMs = null, text = "声部A"),
                NocturnLyricLine(startMs = 15_000L, durationMs = null, text = "声部B"),
                NocturnLyricLine(startMs = 15_000L, durationMs = null, text = "声部C"),
            ),
        )

        val lines = LyriconModelMapper.toLyriconLines(lyrics, songDurationMs = 30_000L)

        assertEquals(3, lines.size)
        for (line in lines) {
            assertEquals(15_000L, line.begin)
            assertEquals(30_000L, line.end) // All fall back to songDurationMs since no subsequent line has startMs > 15_000L
            assertTrue(line.end >= line.begin)
        }
    }

    @Test
    fun handlesNegativeStartMsCoalescingToZeroWithMultipleLines() {
        val lyrics = NocturnLyrics(
            lines = listOf(
                NocturnLyricLine(startMs = -5000L, durationMs = null, text = "负5秒"),
                NocturnLyricLine(startMs = -2000L, durationMs = null, text = "负2秒"),
                NocturnLyricLine(startMs = 3000L, durationMs = null, text = "正3秒"),
            ),
        )

        val lines = LyriconModelMapper.toLyriconLines(lyrics, songDurationMs = 30_000L)

        assertEquals(3, lines.size)
        assertEquals(0L, lines[0].begin)
        assertEquals(3000L, lines[0].end)
        assertEquals(0L, lines[1].begin)
        assertEquals(3000L, lines[1].end)
        assertEquals(3000L, lines[2].begin)
        assertEquals(30_000L, lines[2].end)
    }

    // =========================================================================
    // Category 4: Words / Syllables (YRC) Edge Cases
    // =========================================================================

    @Test
    fun sortsUnorderedWordsWithinALine() {
        val lyrics = NocturnLyrics(
            lines = listOf(
                NocturnLyricLine(
                    startMs = 10_000L,
                    durationMs = 5_000L,
                    text = "乱序字",
                    words = listOf(
                        NocturnLyricWord(startMs = 13_000L, durationMs = 1_000L, text = "字"),
                        NocturnLyricWord(startMs = 10_000L, durationMs = 1_500L, text = "乱"),
                        NocturnLyricWord(startMs = 11_500L, durationMs = 1_500L, text = "序"),
                    ),
                ),
            ),
        )

        val lines = LyriconModelMapper.toLyriconLines(lyrics, songDurationMs = 30_000L)
        val words = lines[0].words!!

        assertEquals(3, words.size)
        assertEquals("乱", words[0].text)
        assertEquals(10_000L, words[0].begin)
        assertEquals("序", words[1].text)
        assertEquals(11_500L, words[1].begin)
        assertEquals("字", words[2].text)
        assertEquals(13_000L, words[2].begin)
    }

    @Test
    fun clampsWordCompletelyBeforeLineBegin() {
        val lyrics = NocturnLyrics(
            lines = listOf(
                NocturnLyricLine(
                    startMs = 10_000L,
                    durationMs = 5_000L,
                    text = "行内容",
                    words = listOf(
                        NocturnLyricWord(startMs = 2_000L, durationMs = 3_000L, text = "远早于行开始的词"),
                    ),
                ),
            ),
        )

        val lines = LyriconModelMapper.toLyriconLines(lyrics, songDurationMs = 30_000L)
        val words = lines[0].words!!

        assertEquals(1, words.size)
        assertEquals(10_000L, words[0].begin)
        assertEquals(10_000L, words[0].end)
        assertTrue(words[0].end >= words[0].begin)
    }

    @Test
    fun handlesWordWithNegativeDuration() {
        val lyrics = NocturnLyrics(
            lines = listOf(
                NocturnLyricLine(
                    startMs = 10_000L,
                    durationMs = 5_000L,
                    text = "负时长词",
                    words = listOf(
                        NocturnLyricWord(startMs = 11_000L, durationMs = -500L, text = "词"),
                    ),
                ),
            ),
        )

        val lines = LyriconModelMapper.toLyriconLines(lyrics, songDurationMs = 30_000L)
        val words = lines[0].words!!

        assertEquals(1, words.size)
        assertEquals(11_000L, words[0].begin)
        assertEquals(11_000L, words[0].end) // Negative duration coerced to 0L, so begin == end
    }

    @Test
    fun handlesOverlappingWordsWithinLine() {
        val lyrics = NocturnLyrics(
            lines = listOf(
                NocturnLyricLine(
                    startMs = 10_000L,
                    durationMs = 10_000L,
                    text = "重叠词组",
                    words = listOf(
                        NocturnLyricWord(startMs = 10_000L, durationMs = 4_000L, text = "重"),
                        NocturnLyricWord(startMs = 12_000L, durationMs = 4_000L, text = "叠"), // Overlaps with "重"
                    ),
                ),
            ),
        )

        val lines = LyriconModelMapper.toLyriconLines(lyrics, songDurationMs = 30_000L)
        val words = lines[0].words!!

        assertEquals(2, words.size)
        assertEquals(10_000L, words[0].begin)
        assertEquals(14_000L, words[0].end)
        assertEquals(12_000L, words[1].begin)
        assertEquals(16_000L, words[1].end)
    }

    // =========================================================================
    // Category 5: Invariant Fuzzing & Scale Stress Testing
    // =========================================================================

    @Test
    fun maintainsInvariantsUnderFuzzedInputs() {
        val random = Random(42)
        val fuzzLines = (1..500).map { i ->
            val start = random.nextLong(-10_000L, 500_000L)
            val duration = if (random.nextBoolean()) random.nextLong(-5_000L, 20_000L) else null
            val wordCount = random.nextInt(0, 8)
            val words = (1..wordCount).map { j ->
                NocturnLyricWord(
                    startMs = start + random.nextLong(-5_000L, 25_000L),
                    durationMs = random.nextLong(-2_000L, 10_000L),
                    text = "w$j",
                )
            }
            NocturnLyricLine(
                startMs = start,
                durationMs = duration,
                text = "Line $i",
                words = words,
                translation = if (random.nextBoolean()) "Trans $i" else "   ",
                romanization = if (random.nextBoolean()) "Roma $i" else null,
            )
        }

        val lyrics = NocturnLyrics(lines = fuzzLines)
        val result = LyriconModelMapper.toLyriconLines(lyrics, songDurationMs = random.nextLong(-1_000L, 600_000L))

        assertEquals(500, result.size)

        for (i in result.indices) {
            val line = result[i]
            assertTrue("Line $i begin (${line.begin}) must be >= 0", line.begin >= 0L)
            assertTrue("Line $i end (${line.end}) must be >= begin (${line.begin})", line.end >= line.begin)

            if (i < result.size - 1) {
                assertTrue("Lines must be ordered: line $i begin <= line ${i + 1} begin", line.begin <= result[i + 1].begin)
            }

            line.words?.let { words ->
                for (j in words.indices) {
                    val word = words[j]
                    assertTrue("Word $j begin must be >= line begin", word.begin >= line.begin)
                    assertTrue("Word $j end must be <= line end", word.end <= line.end)
                    assertTrue("Word $j end must be >= word begin", word.end >= word.begin)
                    if (j < words.size - 1) {
                        assertTrue("Words must be sorted by begin", word.begin <= words[j + 1].begin)
                    }
                }
            }
        }
    }

    @Test
    fun handlesLargeLyricsScaleGracefully() {
        val largeCount = 3_000
        val lines = (1..largeCount).map { i ->
            NocturnLyricLine(
                startMs = i * 1_000L,
                durationMs = 800L,
                text = "Transcript line $i",
                words = listOf(
                    NocturnLyricWord(startMs = i * 1_000L, durationMs = 400L, text = "Transcript"),
                    NocturnLyricWord(startMs = i * 1_000L + 400L, durationMs = 400L, text = "line"),
                ),
            )
        }

        val startTime = System.currentTimeMillis()
        val result = LyriconModelMapper.toLyriconLines(NocturnLyrics(lines), songDurationMs = largeCount * 1_000L + 1_000L)
        val elapsed = System.currentTimeMillis() - startTime

        assertEquals(largeCount, result.size)
        assertTrue("3,000 lines mapping must complete within 2 seconds (took ${elapsed}ms)", elapsed < 2_000L)
    }
}
