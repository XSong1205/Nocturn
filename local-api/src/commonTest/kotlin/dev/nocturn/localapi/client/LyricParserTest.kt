package dev.nocturn.localapi.client

import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonObject
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertFalse
import kotlin.test.assertNull
import kotlin.test.assertTrue

class LyricParserTest {
    @Test
    fun lrcSupportsRepeatedTimestampsFractionPrecisionAndGlobalOffset() {
        val lines = LyricParser.parseLrc("[ar:Artist]\n[offset:-100]\n[00:01.2][00:03.45]Repeated\n[00:05.678]End")
        assertEquals(listOf(1100L, 3350L, 5578L), lines.map { it.startTimeMs })
        assertEquals(listOf("Repeated", "Repeated", "End"), lines.map { it.text })
        assertEquals(2250, lines[0].durationMs)
        assertNull(lines.last().durationMs)
    }

    @Test
    fun yrcUsesAbsoluteWordTimesAndSkipsJsonCredits() {
        val lines = LyricParser.parseYrc("""{"t":0,"c":[{"tx":"作曲: Artist"}]}""" + "\n[16210,3460](16210,670,0)还(16880,410,0)没...")
        assertEquals(1, lines.size)
        assertEquals("还没...", lines.single().text)
        assertEquals(3460, lines.single().durationMs)
        assertEquals(listOf(16210L, 16880L), lines.single().words.map { it.startTimeMs })
        assertEquals(410, lines.single().words[1].durationMs)
    }

    @Test
    fun responsePrefersWordLyricsAndAlignsTranslationAndRomanization() {
        val body = json("""{"code":200,"yrc":{"lyric":"[1000,1000](1000,500,0)你好"},"lrc":{"lyric":"[00:01.00]Fallback"},"ytlrc":{"lyric":"[00:01.10]Hello"},"yromalrc":{"lyric":"[00:01.00]Ni hao"}}""")
        val lyrics = LyricParser.parse(body)
        assertTrue(lyrics.wordSynced)
        assertEquals("你好", lyrics.lines.single().text)
        assertEquals("Hello", lyrics.lines.single().translation)
        assertEquals("Ni hao", lyrics.lines.single().romanization)
    }

    @Test
    fun malformedOrMissingYrcFallsBackToOrdinaryLyrics() {
        val body = json("""{"code":200,"yrc":{"lyric":"metadata only"},"lrc":{"lyric":"[00:01.00]A line"},"tlyric":{"lyric":"[00:20.00]Too far away"}}""")
        val lyrics = LyricParser.parse(body)
        assertFalse(lyrics.wordSynced)
        assertEquals("A line", lyrics.lines.single().text)
        assertNull(lyrics.lines.single().translation)
    }

    @Test
    fun instrumentalAndUncollectedAreDifferentStates() {
        assertTrue(LyricParser.parse(json("""{"code":200,"nolyric":true}""")).instrumental)
        val missing = LyricParser.parse(json("""{"code":200,"uncollected":true}"""))
        assertFalse(missing.instrumental)
        assertTrue(missing.lines.isEmpty())
    }

    @Test
    fun malformedAndOverflowingRowsCannotBreakValidLyrics() {
        val yrc = "[9223372036854775807,2](1,1,0)Invalid\n[1000,1000](1000,200,0)Valid"
        assertEquals("Valid", LyricParser.parseYrc(yrc).single().text)
        assertTrue(LyricParser.parseYrc("[1,2](0,1,0)Invalid").isEmpty())
        assertTrue(LyricParser.parseLrc("[00:99.00]Invalid").isEmpty())
    }

    @Test
    fun businessDenialIsNotMisreportedAsNoLyrics() {
        assertEquals(301, assertFailsWith<MusicServiceException> { LyricParser.parse(json("""{"code":301}""")) }.businessCode)
    }

    private fun json(source: String) = Json.parseToJsonElement(source) as JsonObject
}
