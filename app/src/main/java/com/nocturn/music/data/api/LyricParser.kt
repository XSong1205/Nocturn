package com.nocturn.music.data.api

import com.mocharealm.accompanist.lyrics.core.model.SyncedLyrics
import com.mocharealm.accompanist.lyrics.core.model.karaoke.KaraokeAlignment
import com.mocharealm.accompanist.lyrics.core.model.karaoke.KaraokeLine
import com.mocharealm.accompanist.lyrics.core.model.karaoke.KaraokeSyllable
import com.mocharealm.accompanist.lyrics.core.model.synced.SyncedLine
import com.nocturn.music.model.LyricLine
import com.nocturn.music.model.LyricWord
import com.nocturn.music.model.SongLyric
import kotlin.math.abs

object LyricParser {
    private val lrcTimeRegex = Regex("""\[(\d{1,2}):(\d{1,2})(?:\.(\d{1,3}))?]""")
    private val yrcLineRegex = Regex("""^\[(\d+),(\d+)\](.*)$""")
    private val yrcWordRegex = Regex("""\((\d+),(\d+),\d+\)([^\(]*)""")

    fun parse(
        rawLrc: String?,
        rawTranslationLrc: String? = null,
        rawRomaLrc: String? = null,
        rawYrc: String? = null
    ): SongLyric {
        // 优先解析 YRC 逐字歌词
        if (!rawYrc.isNullOrBlank()) {
            val yrcResult = parseYrc(rawYrc, rawTranslationLrc, rawRomaLrc)
            if (yrcResult.lines.isNotEmpty()) {
                return yrcResult
            }
        }

        // 回退解析普通 LRC
        return parseLrc(rawLrc, rawTranslationLrc, rawRomaLrc)
    }

    private fun parseYrc(
        rawYrc: String,
        rawTranslationLrc: String?,
        rawRomaLrc: String?
    ): SongLyric {
        val transMap = parseTimestampedText(rawTranslationLrc)
        val romaMap = parseTimestampedText(rawRomaLrc)
        val lines = mutableListOf<LyricLine>()

        rawYrc.lineSequence().forEach { line ->
            val trimmed = line.trim()
            if (trimmed.startsWith("{") || trimmed.isBlank()) {
                // 过滤元数据行如 {"t":-1000,"c":[...]}
                return@forEach
            }

            val lineMatch = yrcLineRegex.find(trimmed)
            if (lineMatch != null) {
                val (lineStartStr, lineDurStr, content) = lineMatch.destructured
                val lineStartMs = lineStartStr.toLongOrNull() ?: 0L
                val lineDurationMs = lineDurStr.toLongOrNull() ?: 0L

                val words = mutableListOf<LyricWord>()
                val wordMatches = yrcWordRegex.findAll(content)
                val fullTextBuilder = StringBuilder()

                for (wMatch in wordMatches) {
                    val (wStartStr, wDurStr, wText) = wMatch.destructured
                    val wStartMs = wStartStr.toLongOrNull() ?: 0L
                    val wDurMs = wDurStr.toLongOrNull() ?: 0L
                    words.add(LyricWord(wStartMs, wDurMs, wText))
                    fullTextBuilder.append(wText)
                }

                val fullText = fullTextBuilder.toString().trim()
                if (fullText.isNotBlank()) {
                    val trans = findClosestText(transMap, lineStartMs)
                    val roma = findClosestText(romaMap, lineStartMs)
                    lines.add(
                        LyricLine(
                            timeMs = lineStartMs,
                            durationMs = lineDurationMs,
                            text = fullText,
                            translation = trans,
                            romalrc = roma,
                            words = words
                        )
                    )
                }
            }
        }

        val sorted = lines.sortedBy { it.timeMs }
        val synced = buildSyncedLyrics(sorted)
        return SongLyric(lines = sorted, rawLrc = rawYrc, syncedLyrics = synced)
    }

    private fun parseLrc(
        rawLrc: String?,
        rawTranslationLrc: String?,
        rawRomaLrc: String?
    ): SongLyric {
        if (rawLrc.isNullOrBlank()) {
            return SongLyric(emptyList(), "")
        }

        val transMap = parseTimestampedText(rawTranslationLrc)
        val romaMap = parseTimestampedText(rawRomaLrc)
        val parsedLines = mutableListOf<LyricLine>()

        rawLrc.lineSequence().forEach { line ->
            val matches = lrcTimeRegex.findAll(line).toList()
            if (matches.isNotEmpty()) {
                val text = line.substring(matches.last().range.last + 1).trim()
                if (text.isNotBlank()) {
                    for (match in matches) {
                        val timeMs = parseLrcTime(match)
                        val trans = findClosestText(transMap, timeMs)
                        val roma = findClosestText(romaMap, timeMs)
                        parsedLines.add(
                            LyricLine(
                                timeMs = timeMs,
                                text = text,
                                translation = trans,
                                romalrc = roma
                            )
                        )
                    }
                }
            }
        }

        val sorted = parsedLines.sortedBy { it.timeMs }
        val synced = buildSyncedLyrics(sorted)
        return SongLyric(lines = sorted, rawLrc = rawLrc, syncedLyrics = synced)
    }

    private fun buildSyncedLyrics(lines: List<LyricLine>): SyncedLyrics {
        if (lines.isEmpty()) return SyncedLyrics(emptyList())

        val syncedLines = lines.mapIndexed { index, line ->
            val nextStart = lines.getOrNull(index + 1)?.timeMs ?: (line.timeMs + 5000L)
            val endMs = if (line.durationMs > 0) {
                line.timeMs + line.durationMs
            } else {
                nextStart.coerceAtLeast(line.timeMs + 1000L)
            }

            if (line.hasWordTiming) {
                val syllables = line.words.map { w ->
                    KaraokeSyllable(
                        content = w.text,
                        start = w.startMs.toInt(),
                        end = (w.startMs + w.durationMs).toInt()
                    )
                }
                val effectiveStart = syllables.firstOrNull()?.start ?: line.timeMs.toInt()
                val effectiveEnd = syllables.lastOrNull()?.end?.coerceAtLeast(endMs.toInt()) ?: endMs.toInt()
                KaraokeLine.MainKaraokeLine(
                    syllables = syllables,
                    translation = line.translation.takeIf { it.isNotBlank() },
                    alignment = KaraokeAlignment.Unspecified,
                    start = effectiveStart,
                    end = effectiveEnd,
                    phonetic = line.romalrc.takeIf { it.isNotBlank() }
                )
            } else {
                SyncedLine(
                    content = line.text,
                    translation = line.translation.takeIf { it.isNotBlank() },
                    start = line.timeMs.toInt(),
                    end = endMs.toInt()
                )
            }
        }
        return SyncedLyrics(lines = syncedLines)
    }

    private fun parseTimestampedText(raw: String?): Map<Long, String> {
        if (raw.isNullOrBlank()) return emptyMap()
        val result = mutableMapOf<Long, String>()
        raw.lineSequence().forEach { line ->
            val trimmed = line.trim()
            if (trimmed.isBlank() || trimmed.startsWith("{")) return@forEach

            val yrcMatch = yrcLineRegex.find(trimmed)
            if (yrcMatch != null) {
                val timeMs = yrcMatch.groupValues[1].toLongOrNull() ?: 0L
                val text = yrcMatch.groupValues[3].trim()
                if (text.isNotBlank()) {
                    result[timeMs] = text
                }
                return@forEach
            }

            val lrcMatch = lrcTimeRegex.find(trimmed)
            if (lrcMatch != null) {
                val timeMs = parseLrcTime(lrcMatch)
                val text = trimmed.substring(lrcMatch.range.last + 1).trim()
                if (text.isNotBlank()) {
                    result[timeMs] = text
                }
            }
        }
        return result
    }

    private fun findClosestText(map: Map<Long, String>, targetTimeMs: Long, toleranceMs: Long = 600L): String {
        if (map.isEmpty()) return ""
        map[targetTimeMs]?.let { return it }

        var closestText = ""
        var minDiff = Long.MAX_VALUE

        for ((time, text) in map) {
            val diff = abs(time - targetTimeMs)
            if (diff < minDiff && diff <= toleranceMs) {
                minDiff = diff
                closestText = text
            }
        }
        return closestText
    }

    private fun parseLrcTime(match: MatchResult): Long {
        val (minStr, secStr) = match.destructured
        val msStr = match.groups[3]?.value ?: "0"
        val minutes = minStr.toLongOrNull() ?: 0L
        val seconds = secStr.toLongOrNull() ?: 0L
        val ms = when (msStr.length) {
            1 -> msStr.toLong() * 100
            2 -> msStr.toLong() * 10
            3 -> msStr.toLong()
            else -> 0L
        }
        return (minutes * 60 + seconds) * 1000 + ms
    }
}
