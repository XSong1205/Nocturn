package dev.nocturn.localapi.client

import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.booleanOrNull
import kotlin.math.abs

data class LyricWord(val startTimeMs: Long, val durationMs: Long, val text: String)
data class LyricLine(
    val startTimeMs: Long,
    val durationMs: Long?,
    val text: String,
    val words: List<LyricWord> = emptyList(),
    val translation: String? = null,
    val romanization: String? = null,
)
data class SongLyrics(val lines: List<LyricLine>, val wordSynced: Boolean, val instrumental: Boolean)

/** Parses ordinary LRC and NetEase absolute-millisecond YRC without treating JSON credits as lyrics. */
object LyricParser {
    private val lrcTime = Regex("\\[(\\d{1,6}):([0-5]\\d)(?:[.:](\\d{1,3}))?]")
    private val offsetTag = Regex("\\[offset:([+-]?\\d+)]", RegexOption.IGNORE_CASE)
    private val yrcLine = Regex("^\\[(\\d+),(\\d+)](.*)$")
    private val yrcWord = Regex("\\((\\d+),(\\d+),\\d+\\)")

    fun parse(response: JsonObject): SongLyrics {
        response.requireSuccess()
        val yrc = parseYrc(text(response, "yrc"))
        val wordSynced = yrc.isNotEmpty()
        val original = yrc.ifEmpty { parseLrc(text(response, "lrc")) }
        val translated = timeline(text(response, if (wordSynced) "ytlrc" else "tlyric"))
            .ifEmpty { timeline(text(response, "tlyric")) }
        val romanized = timeline(text(response, if (wordSynced) "yromalrc" else "romalrc"))
            .ifEmpty { timeline(text(response, "romalrc")) }
        val lines = original.map { line ->
            line.copy(translation = nearest(translated, line.startTimeMs), romanization = nearest(romanized, line.startTimeMs))
        }
        val instrumental = (response["nolyric"] as? JsonPrimitive)?.booleanOrNull == true ||
            lines.any { it.text.trim() == "纯音乐，请欣赏" || it.text.trim() == "纯音乐，请欣赏。" }
        return SongLyrics(lines, wordSynced, instrumental)
    }

    fun parseLrc(source: String): List<LyricLine> {
        require(source.length <= 2_000_000) { "Lyrics exceed the supported size." }
        val offset = offsetTag.find(source)?.groupValues?.get(1)?.toLongOrNull()?.coerceIn(-86_400_000, 86_400_000) ?: 0
        val lines = source.lineSequence().flatMap { row ->
            if (row.trimStart().startsWith('{')) return@flatMap emptySequence()
            val times = lrcTime.findAll(row).toList()
            if (times.isEmpty()) return@flatMap emptySequence()
            val content = row.substring(times.last().range.last + 1).trim()
            times.asSequence().map { match ->
                val millis = match.groupValues[3].padEnd(3, '0').toLong()
                val time = match.groupValues[1].toLong() * 60_000 + match.groupValues[2].toLong() * 1000 + millis
                LyricLine((time + offset).coerceAtLeast(0), null, content)
            }
        }.sortedBy { it.startTimeMs }.toList()
        return lines.mapIndexed { index, line ->
            line.copy(durationMs = lines.getOrNull(index + 1)?.let { (it.startTimeMs - line.startTimeMs).coerceAtLeast(0) })
        }
    }

    fun parseYrc(source: String): List<LyricLine> {
        require(source.length <= 2_000_000) { "Lyrics exceed the supported size." }
        return source.lineSequence().mapNotNull { row ->
            val line = yrcLine.matchEntire(row.trim()) ?: return@mapNotNull null
            val start = line.groupValues[1].toLongOrNull() ?: return@mapNotNull null
            val duration = line.groupValues[2].toLongOrNull() ?: return@mapNotNull null
            if (duration > Long.MAX_VALUE - start) return@mapNotNull null
            val content = line.groupValues[3]
            val markers = yrcWord.findAll(content).toList()
            val words = markers.mapIndexedNotNull { index, marker ->
                val wordStart = marker.groupValues[1].toLongOrNull() ?: return@mapIndexedNotNull null
                val wordDuration = marker.groupValues[2].toLongOrNull() ?: return@mapIndexedNotNull null
                if (wordStart < start || wordStart > start + duration || wordDuration > Long.MAX_VALUE - wordStart) {
                    return@mapIndexedNotNull null
                }
                val end = markers.getOrNull(index + 1)?.range?.first ?: content.length
                LyricWord(wordStart, wordDuration, content.substring(marker.range.last + 1, end))
            }
            if (words.isEmpty()) null else LyricLine(start, duration, words.joinToString("") { it.text }, words)
        }.sortedBy { it.startTimeMs }.toList()
    }

    private fun text(body: JsonObject, field: String): String = (body[field] as? JsonObject)?.text("lyric").orEmpty()
    private fun timeline(source: String): List<LyricLine> = parseYrc(source).ifEmpty { parseLrc(source) }
    private fun nearest(lines: List<LyricLine>, time: Long): String? {
        // Timelines are sorted. Binary search keeps alignment O(n log n) for long songs.
        var low = 0
        var high = lines.size
        while (low < high) {
            val middle = (low + high) ushr 1
            if (lines[middle].startTimeMs < time) low = middle + 1 else high = middle
        }
        return listOfNotNull(lines.getOrNull(low - 1), lines.getOrNull(low))
            .minByOrNull { abs(it.startTimeMs - time) }
            ?.takeIf { abs(it.startTimeMs - time) <= 500 }?.text?.takeIf { it.isNotBlank() }
    }
}

suspend fun LocalApiClient.songLyrics(id: Long, cookie: String? = null): SongLyrics =
    LyricParser.parse(lyricsNew(id, cookie))
