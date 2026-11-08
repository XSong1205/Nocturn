package dev.nocturn.app.playback.lyricon

import dev.nocturn.app.data.model.ArtistRef
import dev.nocturn.app.data.model.LyricLine as NocturnLyricLine
import dev.nocturn.app.data.model.LyricWord as NocturnLyricWord
import dev.nocturn.app.data.model.Lyrics as NocturnLyrics
import dev.nocturn.app.data.model.Song as NocturnSong
import io.github.proify.lyricon.lyric.model.LyricWord as LyriconWord
import io.github.proify.lyricon.lyric.model.RichLyricLine as LyriconRichLine
import io.github.proify.lyricon.lyric.model.Song as LyriconSong
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class LyriconModelMapperTest {

    @Test
    fun mapsSongModelWithArtistsToLyriconSong() {
        val song = NocturnSong(
            id = 12345L,
            name = "七里香",
            artists = listOf(ArtistRef(1L, "周杰伦"), ArtistRef(2L, "温岚")),
            album = null,
            durationMs = 300_000L,
        )

        val lyriconSong = LyriconModelMapper.toLyriconSong(song)

        assertEquals("12345", lyriconSong.id)
        assertEquals("七里香", lyriconSong.name)
        assertEquals("周杰伦 / 温岚", lyriconSong.artist)
        assertEquals(300_000L, lyriconSong.duration)
        assertNotNull(lyriconSong.lyrics)
        assertTrue(lyriconSong.lyrics!!.isEmpty())
    }

    @Test
    fun mapsSongWithNoArtistsToUnknownArtist() {
        val song = NocturnSong(
            id = 42L,
            name = "无名曲",
            artists = emptyList(),
            album = null,
            durationMs = 180_000L,
        )

        val lyriconSong = LyriconModelMapper.toLyriconSong(song)

        assertEquals("未知歌手", lyriconSong.artist)
    }

    @Test
    fun mapsOverloadedSongParametersWithExplicitMetadata() {
        val lyriconSong = LyriconModelMapper.toLyriconSong(
            id = 9999L,
            title = "夜曲",
            artist = "周杰伦",
            durationMs = 240_000L,
        )

        assertEquals("9999", lyriconSong.id)
        assertEquals("夜曲", lyriconSong.name)
        assertEquals("周杰伦", lyriconSong.artist)
        assertEquals(240_000L, lyriconSong.duration)
        assertNotNull(lyriconSong.lyrics)
        assertTrue(lyriconSong.lyrics!!.isEmpty())
    }

    @Test
    fun mapsOverloadedSongWithNullArtist() {
        val lyriconSong = LyriconModelMapper.toLyriconSong(
            id = 888L,
            title = "未知音轨",
            artist = null,
            durationMs = 120_000L,
        )

        assertEquals("888", lyriconSong.id)
        assertEquals("未知音轨", lyriconSong.name)
        assertNull(lyriconSong.artist)
        assertEquals(120_000L, lyriconSong.duration)
    }

    @Test
    fun mapsStandardLineByLineLyricsWithAccurateTimestamps() {
        val lyrics = NocturnLyrics(
            lines = listOf(
                NocturnLyricLine(
                    startMs = 12_000L,
                    durationMs = 4_000L,
                    text = "雨下整夜 我的爱溢出就像雨水",
                ),
                NocturnLyricLine(
                    startMs = 17_000L,
                    durationMs = 3_000L,
                    text = "院子落叶 跟我的思念厚厚一叠",
                ),
            ),
        )

        val lines = LyriconModelMapper.toLyriconLines(lyrics, songDurationMs = 200_000L)

        assertEquals(2, lines.size)
        assertEquals(12_000L, lines[0].begin)
        assertEquals(16_000L, lines[0].end)
        assertEquals("雨下整夜 我的爱溢出就像雨水", lines[0].text)
        assertNull(lines[0].words)

        assertEquals(17_000L, lines[1].begin)
        assertEquals(20_000L, lines[1].end)
        assertEquals("院子落叶 跟我的思念厚厚一叠", lines[1].text)
        assertNull(lines[1].words)
    }

    @Test
    fun sortsLyricsWhenTimestampsAreOutOfOrder() {
        val lyrics = NocturnLyrics(
            lines = listOf(
                NocturnLyricLine(startMs = 30_000L, durationMs = 2_000L, text = "第三句"),
                NocturnLyricLine(startMs = 10_000L, durationMs = 5_000L, text = "第一句"),
                NocturnLyricLine(startMs = 20_000L, durationMs = 4_000L, text = "第二句"),
            ),
        )

        val lines = LyriconModelMapper.toLyriconLines(lyrics, songDurationMs = 60_000L)

        assertEquals(listOf("第一句", "第二句", "第三句"), lines.map { it.text })
        assertEquals(listOf(10_000L, 20_000L, 30_000L), lines.map { it.begin })
        assertEquals(listOf(15_000L, 24_000L, 32_000L), lines.map { it.end })
    }

    @Test
    fun coercesNegativeStartTimestampsToZero() {
        val lyrics = NocturnLyrics(
            lines = listOf(
                NocturnLyricLine(startMs = -2_000L, durationMs = 5_000L, text = "前奏"),
            ),
        )

        val lines = LyriconModelMapper.toLyriconLines(lyrics, songDurationMs = 60_000L)

        assertEquals(1, lines.size)
        assertEquals(0L, lines[0].begin)
        assertEquals(5_000L, lines[0].end)
    }

    @Test
    fun extrapolatesLineEndToNextLineWhenDurationIsNull() {
        val lyrics = NocturnLyrics(
            lines = listOf(
                NocturnLyricLine(startMs = 10_000L, durationMs = null, text = "第一行"),
                NocturnLyricLine(startMs = 15_500L, durationMs = null, text = "第二行"),
                NocturnLyricLine(startMs = 22_000L, durationMs = null, text = "最后一行"),
            ),
        )

        val lines = LyriconModelMapper.toLyriconLines(lyrics, songDurationMs = 30_000L)

        assertEquals(3, lines.size)
        assertEquals(10_000L, lines[0].begin)
        assertEquals(15_500L, lines[0].end)
        assertEquals(15_500L, lines[1].begin)
        assertEquals(22_000L, lines[1].end)
        assertEquals(22_000L, lines[2].begin)
        assertEquals(30_000L, lines[2].end)
    }

    @Test
    fun extrapolatesLineEndToNextLineWhenDurationIsZeroOrNegative() {
        val lyrics = NocturnLyrics(
            lines = listOf(
                NocturnLyricLine(startMs = 5_000L, durationMs = 0L, text = "零时长行"),
                NocturnLyricLine(startMs = 10_000L, durationMs = -100L, text = "负时长行"),
                NocturnLyricLine(startMs = 18_000L, durationMs = 2_000L, text = "正常行"),
            ),
        )

        val lines = LyriconModelMapper.toLyriconLines(lyrics, songDurationMs = 60_000L)

        assertEquals(10_000L, lines[0].end)
        assertEquals(18_000L, lines[1].end)
        assertEquals(20_000L, lines[2].end)
    }

    @Test
    fun extrapolatesLastLineEndToSongDurationWhenDurationIsNull() {
        val lyrics = NocturnLyrics(
            lines = listOf(
                NocturnLyricLine(startMs = 190_000L, durationMs = null, text = "尾声"),
            ),
        )

        val lines = LyriconModelMapper.toLyriconLines(lyrics, songDurationMs = 200_000L)

        assertEquals(190_000L, lines[0].begin)
        assertEquals(200_000L, lines[0].end)
    }

    @Test
    fun fallsBackToDefaultDurationWhenLastLineExceedsOrMatchesSongDuration() {
        val lyrics = NocturnLyrics(
            lines = listOf(
                NocturnLyricLine(startMs = 50_000L, durationMs = null, text = "超时行"),
            ),
        )

        val lines = LyriconModelMapper.toLyriconLines(lyrics, songDurationMs = 50_000L)

        assertEquals(50_000L, lines[0].begin)
        assertEquals(55_000L, lines[0].end)
    }

    @Test
    fun fallsBackToDefaultDurationWhenSongDurationIsZero() {
        val lyrics = NocturnLyrics(
            lines = listOf(
                NocturnLyricLine(startMs = 10_000L, durationMs = null, text = "无歌曲时长行"),
            ),
        )

        val lines = LyriconModelMapper.toLyriconLines(lyrics, songDurationMs = 0L)

        assertEquals(10_000L, lines[0].begin)
        assertEquals(15_000L, lines[0].end)
    }

    @Test
    fun mapsYrcWordsWithAccurateBeginAndEndTimestamps() {
        val lyrics = NocturnLyrics(
            lines = listOf(
                NocturnLyricLine(
                    startMs = 16_210L,
                    durationMs = 3_460L,
                    text = "还没有",
                    words = listOf(
                        NocturnLyricWord(startMs = 16_210L, durationMs = 670L, text = "还"),
                        NocturnLyricWord(startMs = 16_880L, durationMs = 410L, text = "没"),
                        NocturnLyricWord(startMs = 17_290L, durationMs = 320L, text = "有"),
                    ),
                ),
            ),
        )

        val lines = LyriconModelMapper.toLyriconLines(lyrics, songDurationMs = 60_000L)

        assertEquals(1, lines.size)
        val words = lines[0].words
        assertNotNull(words)
        assertEquals(3, words!!.size)

        assertEquals("还", words[0].text)
        assertEquals(16_210L, words[0].begin)
        assertEquals(16_880L, words[0].end)

        assertEquals("没", words[1].text)
        assertEquals(16_880L, words[1].begin)
        assertEquals(17_290L, words[1].end)

        assertEquals("有", words[2].text)
        assertEquals(17_290L, words[2].begin)
        assertEquals(17_610L, words[2].end)
    }

    @Test
    fun leavesWordsNullWhenLineHasNoSyllableWords() {
        val lyrics = NocturnLyrics(
            lines = listOf(
                NocturnLyricLine(startMs = 1_000L, durationMs = 3_000L, text = "普通LRC行", words = emptyList()),
            ),
        )

        val lines = LyriconModelMapper.toLyriconLines(lyrics, songDurationMs = 30_000L)

        assertNull(lines[0].words)
    }

    @Test
    fun clampsWordEndWhenWordDurationExceedsLineEnd() {
        val lyrics = NocturnLyrics(
            lines = listOf(
                NocturnLyricLine(
                    startMs = 10_000L,
                    durationMs = 3_000L, // lineEnd = 13_000L
                    text = "第一超长字",
                    words = listOf(
                        NocturnLyricWord(startMs = 10_000L, durationMs = 1_000L, text = "第一"),
                        NocturnLyricWord(startMs = 11_000L, durationMs = 4_000L, text = "超长字"), // 11_000 + 4_000 = 15_000 > 13_000
                    ),
                ),
            ),
        )

        val lines = LyriconModelMapper.toLyriconLines(lyrics, songDurationMs = 30_000L)
        val words = lines[0].words!!

        assertEquals(2, words.size)
        assertEquals(10_000L, words[0].begin)
        assertEquals(11_000L, words[0].end)

        assertEquals(11_000L, words[1].begin)
        assertEquals(13_000L, words[1].end) // Clamped to lineEnd
    }

    @Test
    fun clampsWordBeginWhenWordStartsBeforeLineBegin() {
        val lyrics = NocturnLyrics(
            lines = listOf(
                NocturnLyricLine(
                    startMs = 10_000L,
                    durationMs = 5_000L, // lineEnd = 15_000L
                    text = "抢跑词",
                    words = listOf(
                        NocturnLyricWord(startMs = 8_000L, durationMs = 3_000L, text = "抢跑词"), // raw: 8_000 to 11_000
                    ),
                ),
            ),
        )

        val lines = LyriconModelMapper.toLyriconLines(lyrics, songDurationMs = 30_000L)
        val words = lines[0].words!!

        assertEquals(10_000L, words[0].begin) // Clamped to lineBegin
        assertEquals(11_000L, words[0].end)
    }

    @Test
    fun clampsWordCompletelyPastLineEnd() {
        val lyrics = NocturnLyrics(
            lines = listOf(
                NocturnLyricLine(
                    startMs = 10_000L,
                    durationMs = 2_000L, // lineEnd = 12_000L
                    text = "正常",
                    words = listOf(
                        NocturnLyricWord(startMs = 13_000L, durationMs = 1_000L, text = "滞后"),
                    ),
                ),
            ),
        )

        val lines = LyriconModelMapper.toLyriconLines(lyrics, songDurationMs = 30_000L)
        val words = lines[0].words!!

        assertEquals(12_000L, words[0].begin)
        assertEquals(12_000L, words[0].end)
    }

    @Test
    fun handlesZeroDurationWordsWithinLineBounds() {
        val lyrics = NocturnLyrics(
            lines = listOf(
                NocturnLyricLine(
                    startMs = 10_000L,
                    durationMs = 2_000L,
                    text = "瞬",
                    words = listOf(
                        NocturnLyricWord(startMs = 10_500L, durationMs = 0L, text = "瞬"),
                    ),
                ),
            ),
        )

        val lines = LyriconModelMapper.toLyriconLines(lyrics, songDurationMs = 30_000L)
        val words = lines[0].words!!

        assertEquals(10_500L, words[0].begin)
        assertEquals(10_500L, words[0].end)
    }

    @Test
    fun preservesTranslationWhenProvided() {
        val lyrics = NocturnLyrics(
            lines = listOf(
                NocturnLyricLine(
                    startMs = 16_210L,
                    durationMs = 3_460L,
                    text = "还没有",
                    translation = "Not yet",
                ),
            ),
        )

        val lines = LyriconModelMapper.toLyriconLines(lyrics, songDurationMs = 60_000L)

        assertEquals("Not yet", lines[0].translation)
    }

    @Test
    fun normalizesNullTranslationToNull() {
        val lyrics = NocturnLyrics(
            lines = listOf(
                NocturnLyricLine(startMs = 1_000L, durationMs = 2_000L, text = "歌词", translation = null),
            ),
        )

        val lines = LyriconModelMapper.toLyriconLines(lyrics, songDurationMs = 30_000L)

        assertNull(lines[0].translation)
    }

    @Test
    fun normalizesEmptyOrBlankTranslationToNull() {
        val lyrics = NocturnLyrics(
            lines = listOf(
                NocturnLyricLine(startMs = 1_000L, durationMs = 2_000L, text = "第一句", translation = ""),
                NocturnLyricLine(startMs = 3_000L, durationMs = 2_000L, text = "第二句", translation = "   "),
                NocturnLyricLine(startMs = 5_000L, durationMs = 2_000L, text = "第三句", translation = "\t\n"),
            ),
        )

        val lines = LyriconModelMapper.toLyriconLines(lyrics, songDurationMs = 30_000L)

        assertNull(lines[0].translation)
        assertNull(lines[1].translation)
        assertNull(lines[2].translation)
    }

    @Test
    fun mapsRomanizationToRomaWithoutDuplicatingSecondaryLyrics() {
        val lyrics = NocturnLyrics(
            lines = listOf(
                NocturnLyricLine(
                    startMs = 16_210L,
                    durationMs = 3_460L,
                    text = "还没有",
                    romanization = "hai mei you",
                ),
            ),
        )

        val lines = LyriconModelMapper.toLyriconLines(lyrics, songDurationMs = 60_000L)

        assertEquals("hai mei you", lines[0].roma)
        assertNull(lines[0].secondary)
    }

    @Test
    fun handlesEmptyLyricsReturnsEmptyList() {
        val emptyLyrics = NocturnLyrics.EMPTY

        val lines = LyriconModelMapper.toLyriconLines(emptyLyrics, songDurationMs = 180_000L)
        val song = LyriconModelMapper.toLyriconSong(
            id = 1L,
            title = "纯音乐",
            artist = "未知歌手",
            durationMs = 180_000L,
            lyrics = emptyLyrics,
        )

        assertTrue(lines.isEmpty())
        assertNotNull(song.lyrics)
        assertTrue(song.lyrics!!.isEmpty())
    }

    @Test
    fun handlesPureMusicPlaceholderSong() {
        val song = LyriconModelMapper.toLyriconSong(
            id = 0L,
            title = "纯音乐，请欣赏",
            artist = null,
            durationMs = 0L,
            lyrics = NocturnLyrics.EMPTY,
        )

        assertEquals("0", song.id)
        assertEquals("纯音乐，请欣赏", song.name)
        assertNull(song.artist)
        assertEquals(0L, song.duration)
        assertNotNull(song.lyrics)
        assertTrue(song.lyrics!!.isEmpty())
    }

    @Test
    fun handlesMultipleAdjacentLinesWithIdenticalTimestamps() {
        val lyrics = NocturnLyrics(
            lines = listOf(
                NocturnLyricLine(startMs = 10_000L, durationMs = null, text = "和声"),
                NocturnLyricLine(startMs = 10_000L, durationMs = null, text = "主唱"),
                NocturnLyricLine(startMs = 15_000L, durationMs = null, text = "下一句"),
            ),
        )

        val lines = LyriconModelMapper.toLyriconLines(lyrics, songDurationMs = 30_000L)

        assertEquals(3, lines.size)
        assertEquals(10_000L, lines[0].begin)
        assertTrue(lines[0].end >= lines[0].begin)
        assertEquals(10_000L, lines[1].begin)
        assertEquals(15_000L, lines[1].end)
        assertEquals(15_000L, lines[2].begin)
        assertEquals(30_000L, lines[2].end)
    }
}
