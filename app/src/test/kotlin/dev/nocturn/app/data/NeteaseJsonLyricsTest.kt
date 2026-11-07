package dev.nocturn.app.data

import dev.nocturn.localapi.client.MusicServiceException
import dev.nocturn.app.data.model.LyricWord
import org.junit.Assert.assertEquals
import org.junit.Assert.assertThrows
import org.junit.Assert.assertTrue
import org.junit.Test

class NeteaseJsonLyricsTest {
    @Test
    fun yrcWordLyricsArePreferredAndAlignedWithTranslation() {
        val lyrics = NeteaseJson.parseLyrics(
            json(
                """
                {
                  "code": 200,
                  "yrc": {"lyric": "[16210,3460](16210,670,0)还(16880,410,0)没(17290,320,0)有\n[20000,1000](20000,500,0)结(20500,500,0)束"},
                  "ytlrc": {"lyric": "[16210,3460](16210,670,0)Not yet\n[20000,1000](20000,500,0)Finished"},
                  "yromalrc": {"lyric": "[00:16.21]hai mei you\n[00:20.00]jie shu"},
                  "lrc": {"lyric": "[00:16.21]还没有\n[00:20.00]结束"}
                }
                """.trimIndent(),
            ),
        )
        assertEquals(2, lyrics.lines.size)
        assertEquals(16210L, lyrics.lines[0].startMs)
        assertEquals("还没有", lyrics.lines[0].text)
        assertEquals("Not yet", lyrics.lines[0].translation)
        assertEquals(3460L, lyrics.lines[0].durationMs)
        assertEquals(
            listOf(LyricWord(16210, 670, "还"), LyricWord(16880, 410, "没"), LyricWord(17290, 320, "有")),
            lyrics.lines[0].words,
        )
        assertEquals("hai mei you", lyrics.lines[0].romanization)
        assertEquals("结束", lyrics.lines[1].text)
        assertEquals("Finished", lyrics.lines[1].translation)
    }

    @Test
    fun lrcFallbackSupportsMultipleTimestampsAndGlobalOffset() {
        val lyrics = NeteaseJson.parseLyrics(
            json(
                """
                {
                  "code": 200,
                  "lrc": {"lyric": "[offset:500]\n[00:10.00][00:20.00]副歌\n[00:30.5]结束"},
                  "tlyric": {"lyric": "[00:10.00]Chorus\n[00:20.00]Chorus\n[00:30.50]The end"}
                }
                """.trimIndent(),
            ),
        )
        assertEquals(3, lyrics.lines.size)
        assertEquals(10500L, lyrics.lines[0].startMs)
        assertEquals("副歌", lyrics.lines[0].text)
        assertEquals("Chorus", lyrics.lines[0].translation)
        assertEquals(20500L, lyrics.lines[1].startMs)
        assertEquals(31000L, lyrics.lines[2].startMs)
        assertEquals("The end", lyrics.lines[2].translation)
    }

    @Test
    fun instrumentalAndMissingLyricsBecomeEmptyLyrics() {
        assertTrue(
            NeteaseJson.parseLyrics(
                json("""{"code":200,"nolyric":true,"lrc":{"lyric":""}}"""),
            ).isEmpty,
        )
        assertTrue(
            NeteaseJson.parseLyrics(
                json("""{"code":200,"lrc":{"lyric":"[00:00.00]纯音乐，请欣赏"}}"""),
            ).isEmpty,
        )
        assertTrue(NeteaseJson.parseLyrics(json("""{"code":200}""")).isEmpty)
        assertTrue(NeteaseJson.parseLyrics(json("""{"code":200,"lrc":{"lyric":"not lrc at all"}}""")).isEmpty)
    }

    @Test
    fun jsonCreditMetadataIsNotRenderedAsLyrics() {
        val lyrics = NeteaseJson.parseLyrics(
            json(
                """
                {
                  "code": 200,
                  "lrc": {"lyric": "{\"t\":0,\"c\":[{\"tx\":\"作词\"}]}\n[00:01.00]真正的歌词"}
                }
                """.trimIndent(),
            ),
        )
        assertEquals(1, lyrics.lines.size)
        assertEquals("真正的歌词", lyrics.lines[0].text)
    }

    @Test
    fun lyricBusinessFailureIsTypedAndNotAnEmptyResult() {
        val failure = assertThrows(MusicServiceException::class.java) {
            NeteaseJson.parseLyrics(json("""{"code":301}"""))
        }
        assertEquals(301, failure.businessCode)
    }
}
