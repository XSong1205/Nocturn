# Handoff Report: Playback Engine & Snapshot Enrichment (M3.1)

## 1. Observation

### 1.1 Existing Model and Mapping State
1. **`PlaybackContract.kt` (lines 31–48)**:
   ```kotlin
   data class PlaybackSnapshot(
       val currentSongId: Long? = null,
       val playing: Boolean = false,
       val buffering: Boolean = false,
       val positionMs: Long = 0,
       val durationMs: Long = 0,
       val repeatMode: RepeatMode = RepeatMode.Off,
       val shuffle: Boolean = false,
       val queueIds: List<Long> = emptyList(),
       val currentIndex: Int = -1,
       val error: String? = null,
       /** Same elapsed-realtime clock used by the lyric renderer; zero means no source timestamp. */
       val positionSampledAtNanos: Long = 0L,
       /** Changes only for Media3 position discontinuities (seek, repeat, item transition). */
       val positionDiscontinuity: Long = 0L,
       val playbackSpeed: Float = 1f,
   )
   ```
   `PlaybackSnapshot` tracks playback state and `currentSongId`, but omits `title` and `artist`. Downstream consumers such as `LyriconManager` (Milestone 3) require `title` and `artist` when passing data to `LyriconModelMapper.toLyriconSong(id, title, artist, durationMs, lyrics)`.

2. **`MediaItemMapping.kt` (lines 44–62)**:
   ```kotlin
   fun buildMediaItem(song: PlayableSong): MediaItem? {
       val streamUrl = normalizeUrl(song.url) ?: return null
       val metadata = MediaMetadata.Builder()
           .setTitle(song.title)
           .setArtist(song.artist)
           .setIsBrowsable(false)
           .setIsPlayable(true)
           .apply {
               song.album?.takeIf { it.isNotBlank() }?.let { album -> setAlbumTitle(album) }
               normalizeUrl(song.coverUrl)?.let { cover -> setArtworkUri(Uri.parse(cover)) }
               if (song.durationMs > 0L) setDurationMs(song.durationMs)
           }
           .build()
       return MediaItem.Builder()
           .setMediaId(mediaIdFor(song.id))
           .setUri(streamUrl)
           .setMediaMetadata(metadata)
           .build()
   }
   ```
   `song.title` and `song.artist` are already explicitly attached to `MediaMetadata` for every item submitted to the Media3 player.

3. **`PlaybackSnapshotMapping.kt` (lines 44–77, 80–89)**:
   ```kotlin
   fun fromPlayer(
       currentIndex: Int,
       mediaIds: List<String>,
       isPlaying: Boolean,
       playbackState: Int,
       positionMs: Long,
       durationMs: Long,
       playerRepeatMode: Int,
       shuffleEnabled: Boolean,
       previous: PlaybackSnapshot = PlaybackSnapshot(),
       positionSampledAtNanos: Long = 0L,
       positionDiscontinuity: Long = previous.positionDiscontinuity,
       playbackSpeed: Float = 1f,
   ): PlaybackSnapshot
   ```
   `fromPlayer` does not currently take `title` or `artist`.
   `withProgress` copies `previous` with new `positionMs`, `durationMs`, and `positionSampledAtNanos`:
   ```kotlin
   fun withProgress(
       previous: PlaybackSnapshot,
       positionMs: Long,
       durationMs: Long,
       positionSampledAtNanos: Long = previous.positionSampledAtNanos,
   ): PlaybackSnapshot = previous.copy(
       positionMs = normalizePositionMs(positionMs),
       durationMs = normalizeDurationMs(durationMs),
       positionSampledAtNanos = positionSampledAtNanos,
   )
   ```

4. **`MediaSessionPlaybackEngine.kt` (lines 58–80, 123–169)**:
   - `playerListener`:
     ```kotlin
     override fun onPositionDiscontinuity(
         oldPosition: Player.PositionInfo,
         newPosition: Player.PositionInfo,
         reason: Int,
     ) {
         positionDiscontinuity++
     }

     override fun onEvents(player: Player, events: Player.Events) {
         publishSnapshot(player)
     }
     ```
     `positionDiscontinuity` is incremented on every position discontinuity. Media3 invokes `onPositionDiscontinuity` before `onEvents`, which then triggers `publishSnapshot(player)`.
   - `startProgressUpdates()`:
     Runs periodically every 500ms (`PROGRESS_UPDATE_INTERVAL_MS = 500L`). Calls `PlaybackSnapshotMapping.withProgress(previous, positionMs, durationMs, ...)`. It does not modify `positionDiscontinuity` or track identity metadata.
   - `publishSnapshot(player: Player)`:
     Reads `player.currentPosition`, `player.duration`, and `mediaIds`, but does not inspect `player.currentMediaItem?.mediaMetadata`.

5. **Call sites of `PlaybackSnapshot` across the codebase**:
   - `NocturnViewModel.kt` (lines 1184–1215): Consumes `snapshot.currentSongId`, `snapshot.durationMs`, `snapshot.playing`, `snapshot.positionMs`, etc., via named property access.
   - `PlaybackSnapshotMappingTest.kt`: Uses named parameters in all invocations (e.g., `PlaybackSnapshot(error = "网络连接不稳定")` and `PlaybackSnapshot(currentSongId = 8L, ...)`).
   - `SettingsViewModelTest.kt`: Instantiates `MutableStateFlow(PlaybackSnapshot())` with no arguments.

---

## 2. Logic Chain

1. **Safety of Default Parameters (Referencing Observation 1.1, 1.5)**:
   By declaring `val title: String? = null` and `val artist: String? = null` with default `null` in `PlaybackSnapshot`, all existing instantiations and call sites continue to compile without modification. Positional deconstruction is not used anywhere in the codebase.

2. **Availability of Metadata at Runtime (Referencing Observation 1.2, 1.4)**:
   Because `MediaItemMapping.buildMediaItem` sets `title` and `artist` on `MediaMetadata`, `player.currentMediaItem?.mediaMetadata` (or fallback `player.mediaMetadata`) provides immediate access to `title: CharSequence?` and `artist: CharSequence?`.
   Calling `.toString()?.takeIf { it.isNotBlank() }` safely parses them into `String?`.

3. **Preserving Pure JVM Testability (Referencing Observation 1.3)**:
   `PlaybackSnapshotMapping.kt` avoids direct dependencies on `Player` instances so unit tests can run purely on the JVM. Adding optional `title: String? = null` and `artist: String? = null` to `fromPlayer` preserves this design pattern.

4. **Defensive Metadata Retention (Referencing Observation 1.3, 1.4)**:
   During transient state transitions (e.g. buffering or rapid player events), if `currentSongId` remains unchanged but `title`/`artist` is temporarily null, using:
   ```kotlin
   val effectiveTitle = title ?: if (currentSongId != null && currentSongId == previous.currentSongId) previous.title else null
   val effectiveArtist = artist ?: if (currentSongId != null && currentSongId == previous.currentSongId) previous.artist else null
   ```
   guarantees metadata stability without carrying stale metadata over to newly selected songs.

5. **Zero-Overhead Periodic Progress Polling (Referencing Observation 1.3, 1.4)**:
   Because `withProgress` invokes `previous.copy(...)`, the 500ms progress loop (`startProgressUpdates`) automatically preserves `title`, `artist`, `currentSongId`, and `positionDiscontinuity` without extra allocations or logic.

6. **Position Discontinuity Integrity for Lyricon Sync (Referencing Observation 1.4)**:
   `MediaSessionPlaybackEngine` increments `positionDiscontinuity++` on `onPositionDiscontinuity` (seeks, playlist loops, item transitions), while `startProgressUpdates` keeps it unchanged.
   This provides an explicit signal for `LyriconManager`:
   - When `snapshot.positionDiscontinuity` changes $\rightarrow$ call `remotePlayer.seekTo(snapshot.positionMs)`.
   - When only `snapshot.positionMs` changes $\rightarrow$ call `remotePlayer.setPosition(snapshot.positionMs)`.

---

## 3. Caveats

1. **Nullability on Idle / Empty Queue**:
   When the player has no items loaded (`mediaItemCount == 0`), `player.currentMediaItem` is null, so `snapshot.title` and `snapshot.artist` are null. Consumers (`LyriconManager`) must handle `null` safely (e.g., using `LyriconModelMapper.toLyriconSong` which defaults empty/null titles to `"未知曲目"`).
2. **Whitespace-Only Metadata**:
   Whitespace-only titles or artists are converted to `null` via `.takeIf { it.isNotBlank() }` so that placeholder defaults kick in rather than rendering blank space in the status bar lyric.
3. **Execution Environment Note**:
   On Windows development environments, running Gradle unit tests concurrently while multiple daemon sessions are active can cause transient `.bin` lock collisions under `app/build/kotlin/compileDebugUnitTestKotlin/classpath-snapshot/`. A clean run or single daemon invocation resolves this.

---

## 4. Conclusion & Concrete Code Recommendations

### 4.1 Target 1: `app/src/main/kotlin/dev/nocturn/app/playback/PlaybackContract.kt`
Insert `val title: String? = null` and `val artist: String? = null` into `PlaybackSnapshot`:

```kotlin
// In PlaybackContract.kt around line 32:
data class PlaybackSnapshot(
    val currentSongId: Long? = null,
    val title: String? = null,
    val artist: String? = null,
    val playing: Boolean = false,
    val buffering: Boolean = false,
    val positionMs: Long = 0,
    val durationMs: Long = 0,
    val repeatMode: RepeatMode = RepeatMode.Off,
    val shuffle: Boolean = false,
    val queueIds: List<Long> = emptyList(),
    val currentIndex: Int = -1,
    val error: String? = null,
    /** Same elapsed-realtime clock used by the lyric renderer; zero means no source timestamp. */
    val positionSampledAtNanos: Long = 0L,
    /** Changes only for Media3 position discontinuities (seek, repeat, item transition). */
    val positionDiscontinuity: Long = 0L,
    val playbackSpeed: Float = 1f,
)
```

### 4.2 Target 2: `app/src/main/kotlin/dev/nocturn/app/playback/PlaybackSnapshotMapping.kt`
Update `fromPlayer`:
```kotlin
// In PlaybackSnapshotMapping.kt around line 44:
    fun fromPlayer(
        currentIndex: Int,
        mediaIds: List<String>,
        isPlaying: Boolean,
        playbackState: Int,
        positionMs: Long,
        durationMs: Long,
        playerRepeatMode: Int,
        shuffleEnabled: Boolean,
        previous: PlaybackSnapshot = PlaybackSnapshot(),
        positionSampledAtNanos: Long = 0L,
        positionDiscontinuity: Long = previous.positionDiscontinuity,
        playbackSpeed: Float = 1f,
        title: String? = null,
        artist: String? = null,
    ): PlaybackSnapshot {
        val queueIds = mediaIds.mapNotNull { mediaId -> MediaItemMapping.songIdOf(mediaId) }
        val currentSongId = mediaIds.getOrNull(currentIndex)?.let { mediaId ->
            MediaItemMapping.songIdOf(mediaId)
        }
        val effectiveTitle = title ?: if (currentSongId != null && currentSongId == previous.currentSongId) previous.title else null
        val effectiveArtist = artist ?: if (currentSongId != null && currentSongId == previous.currentSongId) previous.artist else null
        return PlaybackSnapshot(
            currentSongId = currentSongId,
            title = effectiveTitle,
            artist = effectiveArtist,
            playing = isPlaying,
            buffering = isBuffering(playbackState),
            positionMs = normalizePositionMs(positionMs),
            durationMs = normalizeDurationMs(durationMs),
            repeatMode = repeatModeFromPlayer(playerRepeatMode),
            shuffle = shuffleEnabled,
            queueIds = queueIds,
            currentIndex = if (currentIndex in mediaIds.indices) currentIndex else -1,
            error = if (isPlaying) null else previous.error,
            positionSampledAtNanos = positionSampledAtNanos,
            positionDiscontinuity = positionDiscontinuity,
            playbackSpeed = playbackSpeed,
        )
    }
```
*(Note: `withProgress` requires no changes because `previous.copy(...)` automatically retains `title` and `artist`.)*

### 4.3 Target 3: `app/src/main/kotlin/dev/nocturn/app/playback/MediaSessionPlaybackEngine.kt`
Update `publishSnapshot`:
```kotlin
// In MediaSessionPlaybackEngine.kt around line 146:
    private fun publishSnapshot(player: Player) {
        val positionMs = player.currentPosition
        val sampledAtNanos = SystemClock.elapsedRealtimeNanos()
        val mediaIds = ArrayList<String>(player.mediaItemCount)
        for (index in 0 until player.mediaItemCount) {
            mediaIds += player.getMediaItemAt(index).mediaId
        }
        val currentMediaItem = player.currentMediaItem
        val metadata = currentMediaItem?.mediaMetadata ?: player.mediaMetadata
        val title = metadata.title?.toString()?.takeIf { it.isNotBlank() }
        val artist = metadata.artist?.toString()?.takeIf { it.isNotBlank() }
        mutableSnapshot.update { previous ->
            PlaybackSnapshotMapping.fromPlayer(
                currentIndex = player.currentMediaItemIndex,
                mediaIds = mediaIds,
                isPlaying = player.isPlaying,
                playbackState = player.playbackState,
                positionMs = positionMs,
                durationMs = player.duration,
                playerRepeatMode = player.repeatMode,
                shuffleEnabled = player.shuffleModeEnabled,
                previous = previous,
                positionSampledAtNanos = sampledAtNanos,
                positionDiscontinuity = positionDiscontinuity,
                playbackSpeed = player.playbackParameters.speed,
                title = title,
                artist = artist,
            )
        }
    }
```

### 4.4 Target 4: `app/src/test/kotlin/dev/nocturn/app/playback/PlaybackSnapshotMappingTest.kt`
Add unit tests verifying:
1. `title` and `artist` mapping in `fromPlayer`:
   ```kotlin
   @Test
   fun mirrorsTitleAndArtistIntoSnapshot() {
       val snapshot = PlaybackSnapshotMapping.fromPlayer(
           currentIndex = 0,
           mediaIds = listOf("42"),
           isPlaying = true,
           playbackState = Player.STATE_READY,
           positionMs = 10_000L,
           durationMs = 180_000L,
           playerRepeatMode = Player.REPEAT_MODE_OFF,
           shuffleEnabled = false,
           title = "夜曲",
           artist = "周杰伦",
       )
       assertEquals("夜曲", snapshot.title)
       assertEquals("周杰伦", snapshot.artist)
   }
   ```
2. Defensive retention for same song when metadata is temporarily null:
   ```kotlin
   @Test
   fun fallbackPreservesTitleAndArtistForSameSongWhenNull() {
       val previous = PlaybackSnapshot(
           currentSongId = 42L,
           title = "夜曲",
           artist = "周杰伦",
           playing = true,
       )
       val updated = PlaybackSnapshotMapping.fromPlayer(
           currentIndex = 0,
           mediaIds = listOf("42"),
           isPlaying = true,
           playbackState = Player.STATE_READY,
           positionMs = 12_000L,
           durationMs = 180_000L,
           playerRepeatMode = Player.REPEAT_MODE_OFF,
           shuffleEnabled = false,
           previous = previous,
           title = null,
           artist = null,
       )
       assertEquals("夜曲", updated.title)
       assertEquals("周杰伦", updated.artist)
   }
   ```
3. Verification that `withProgress` preserves `title` and `artist`:
   In `progressUpdatesOnlyTouchPositionAndDuration()`, add `title = "枫"` and `artist = "周杰伦"` to `previous`, then assert:
   ```kotlin
   assertEquals("枫", progressed.title)
   assertEquals("周杰伦", progressed.artist)
   ```

---

## 5. Verification Method

1. **Independent JVM Unit Testing**:
   Execute the dedicated mapping test suite:
   ```powershell
   .\gradlew.bat :app:testDebugUnitTest --tests "dev.nocturn.app.playback.PlaybackSnapshotMappingTest"
   ```
2. **Whole Client Compilation & Lint**:
   ```powershell
   .\gradlew.bat :app:compileDebugKotlin :app:lintDebug
   ```
3. **Invalidation Conditions**:
   - `snapshot.title` or `snapshot.artist` returns null when `currentMediaItem.mediaMetadata` has valid values.
   - Any compiler error in `NocturnViewModel` or other components consuming `PlaybackSnapshot`.
   - `positionDiscontinuity` failing to increment on seek or resetting unexpectedly during progress updates.
