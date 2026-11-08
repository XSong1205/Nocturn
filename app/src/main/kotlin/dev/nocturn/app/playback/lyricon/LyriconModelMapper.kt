package dev.nocturn.app.playback.lyricon

import dev.nocturn.app.data.model.LyricLine as NocturnLyricLine
import dev.nocturn.app.data.model.LyricWord as NocturnLyricWord
import dev.nocturn.app.data.model.Lyrics as NocturnLyrics
import dev.nocturn.app.data.model.Song as NocturnSong
import io.github.proify.lyricon.lyric.model.LyricWord as LyriconWord
import io.github.proify.lyricon.lyric.model.RichLyricLine as LyriconRichLine
import io.github.proify.lyricon.lyric.model.Song as LyriconSong

/**
 * Transforms Nocturn data models ([NocturnSong], [NocturnLyrics], [NocturnLyricLine], [NocturnLyricWord])
 * into Lyricon Provider SDK models ([LyriconSong], [LyriconRichLine], [LyriconWord]).
 *
 * Timestamps are uniformly handled in milliseconds.
 */
object LyriconModelMapper {

    /** Default duration in ms for an LRC line when next line and song duration are unavailable. */
    const val DEFAULT_LINE_DURATION_MS: Long = 5_000L

    /** Standard placeholder text for pure music / instrumental tracks. */
    const val PURE_MUSIC_TEXT: String = "纯音乐，请欣赏"

    /** Standard placeholder text when lyrics are not available. */
    const val NO_LYRICS_TEXT: String = "暂无歌词"

    /**
     * Converts a [NocturnSong] and optional [NocturnLyrics] into a [LyriconSong].
     */
    fun toLyriconSong(
        song: NocturnSong,
        lyrics: NocturnLyrics = NocturnLyrics.EMPTY,
    ): LyriconSong = toLyriconSong(
        id = song.id,
        title = song.name,
        artist = song.artistText,
        durationMs = song.durationMs,
        lyrics = lyrics,
    )

    /**
     * Converts raw song metadata parameters and optional [NocturnLyrics] into a [LyriconSong].
     */
    fun toLyriconSong(
        id: Long,
        title: String,
        artist: String?,
        durationMs: Long,
        lyrics: NocturnLyrics = NocturnLyrics.EMPTY,
    ): LyriconSong {
        val duration = durationMs.coerceAtLeast(0L)
        val lines = toLyriconLines(lyrics, duration)
        return LyriconSong(
            id = id.toString(),
            name = title.ifBlank { "未知曲目" },
            artist = artist?.ifBlank { null },
            duration = duration,
            lyrics = lines,
        )
    }

    /**
     * Transforms [NocturnLyrics] into a list of [LyriconRichLine].
     *
     * - Filters out blank lines and sorts by start time.
     * - Calculates line begin/end timestamps with boundary clamping and fallbacks.
     * - Maps syllable-level [NocturnLyricWord] into [LyriconWord] with parent line clamping.
     * - Preserves translations and romanizations.
     * - Returns an empty list if lyrics are empty.
     */
    fun toLyriconLines(
        lyrics: NocturnLyrics,
        songDurationMs: Long,
    ): List<LyriconRichLine> {
        if (lyrics.isEmpty) return emptyList()

        val validLines = lyrics.lines
            .filter { it.text.isNotBlank() }
            .sortedBy { it.startMs }

        if (validLines.isEmpty()) return emptyList()

        val safeSongDuration = songDurationMs.coerceAtLeast(0L)

        return validLines.mapIndexed { index, line ->
            toLyriconLine(
                line = line,
                nextStartMs = validLines.asSequence()
                    .drop(index + 1)
                    .map { it.startMs.coerceAtLeast(0L) }
                    .firstOrNull { it > line.startMs.coerceAtLeast(0L) },
                songDurationMs = safeSongDuration,
            )
        }
    }

    /**
     * Converts a single [NocturnLyricLine] into a [LyriconRichLine].
     */
    fun toLyriconLine(
        line: NocturnLyricLine,
        nextStartMs: Long?,
        songDurationMs: Long,
    ): LyriconRichLine {
        val lineBegin = line.startMs.coerceAtLeast(0L)

        val calculatedEnd = if (line.durationMs != null && line.durationMs > 0L) {
            val duration = line.durationMs
            val rawEnd = if (Long.MAX_VALUE - duration < lineBegin) Long.MAX_VALUE else lineBegin + duration
            if (nextStartMs != null && nextStartMs > lineBegin) {
                minOf(rawEnd, nextStartMs)
            } else if (nextStartMs == null && songDurationMs > lineBegin) {
                minOf(rawEnd, songDurationMs)
            } else {
                rawEnd
            }
        } else {
            // LRC mode: interpolate to next line start, or song duration, or fallback
            if (nextStartMs != null && nextStartMs > lineBegin) {
                nextStartMs
            } else if (songDurationMs > lineBegin) {
                songDurationMs
            } else {
                if (Long.MAX_VALUE - DEFAULT_LINE_DURATION_MS < lineBegin) {
                    Long.MAX_VALUE
                } else {
                    lineBegin + DEFAULT_LINE_DURATION_MS
                }
            }
        }

        val minimumEnd = if (lineBegin == Long.MAX_VALUE) Long.MAX_VALUE else lineBegin + 1L
        val lineEnd = maxOf(minimumEnd, calculatedEnd)

        val words = if (line.words.isNotEmpty()) {
            toLyriconWords(line.words, lineBegin, lineEnd)
        } else {
            null
        }

        val translationText = line.translation?.trim()?.takeIf { it.isNotEmpty() }
        val romaText = line.romanization?.trim()?.takeIf { it.isNotEmpty() }

        return LyriconRichLine(
            begin = lineBegin,
            end = lineEnd,
            text = line.text.trim(),
            translation = translationText,
            words = words,
            roma = romaText,
        )
    }

    /**
     * Converts a list of [NocturnLyricWord] into clamped [LyriconWord] elements.
     * All words are guaranteed to lie within `[lineBegin, lineEnd]`.
     */
    fun toLyriconWords(
        words: List<NocturnLyricWord>,
        lineBegin: Long,
        lineEnd: Long,
    ): List<LyriconWord> {
        val sortedWords = words.sortedBy { it.startMs }
        if (sortedWords.isEmpty()) return emptyList()

        return sortedWords.map { word ->
            val rawWordBegin = word.startMs.coerceAtLeast(0L)
            val duration = word.durationMs.coerceAtLeast(0L)
            val rawWordEnd = if (Long.MAX_VALUE - duration < rawWordBegin) {
                Long.MAX_VALUE
            } else {
                rawWordBegin + duration
            }

            val clampedBegin = rawWordBegin.coerceIn(lineBegin, lineEnd)
            val clampedEnd = rawWordEnd.coerceIn(clampedBegin, lineEnd)

            LyriconWord(
                begin = clampedBegin,
                end = clampedEnd,
                text = word.text,
            )
        }
    }
}
