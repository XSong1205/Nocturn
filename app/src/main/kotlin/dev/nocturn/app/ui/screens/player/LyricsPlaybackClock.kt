package dev.nocturn.app.ui.screens.player

internal data class LyricsPositionSample(
    val positionMs: Long,
    val durationMs: Long,
    val playing: Boolean,
    val sampledAtNanos: Long,
    val discontinuity: Long,
    val playbackSpeed: Float,
    val positionOverride: Boolean,
)

/** Ordinary controller corrections can hold a syllable briefly, but must never rewind it. */
internal class LyricsPlaybackClock(initialPositionMs: Int) {
    private var renderedPositionMs = initialPositionMs
    private var discontinuity: Long? = null
    private var previousSamplePositionMs: Long? = null
    private var wasOverriding = false

    fun positionAt(sample: LyricsPositionSample, receivedAtNanos: Long, nowNanos: Long): Int {
        val sampledAt = sample.sampledAtNanos.takeIf { it > 0L } ?: receivedAtNanos
        val elapsedMs = (nowNanos - sampledAt).coerceAtLeast(0L) / 1_000_000L
        val candidate = interpolatedLyricsPosition(
            sample.positionMs, elapsedMs, sample.playing, sample.durationMs, sample.playbackSpeed,
        )
        // Preview/demo callers have no Media3 revision. A backwards source anchor is their seek.
        // Production samples always use the explicit discontinuity, even for a one-millisecond seek.
        val localSeek = sample.sampledAtNanos == 0L &&
            previousSamplePositionMs?.let { sample.positionMs < it } == true
        val reset = discontinuity != sample.discontinuity || sample.positionOverride || wasOverriding || localSeek
        val limit = sample.durationMs.takeIf { it > 0L }?.coerceAtMost(Int.MAX_VALUE.toLong())
            ?: Int.MAX_VALUE.toLong()
        renderedPositionMs = (if (reset) candidate else maxOf(renderedPositionMs, candidate))
            .coerceAtMost(limit.toInt())
        discontinuity = sample.discontinuity
        previousSamplePositionMs = sample.positionMs
        wasOverriding = sample.positionOverride
        return renderedPositionMs
    }
}
