# Lyricon Provider

Nocturn uses the published `io.github.proify.lyricon:provider:0.1.70` SDK. The application owns one
Provider and observes the application-scoped Media3 playback snapshot. The settings switch enables
or unregisters that Provider. The playback service stops publication and clears the replay cache
when destroyed; starting the service starts observation again.

Reference: [Provider documentation](https://tomakino.github.io/lyricon/zh-cn/developer/provider/),
especially [connection lifecycle](https://tomakino.github.io/lyricon/zh-cn/developer/provider/connection)
and [player control](https://tomakino.github.io/lyricon/zh-cn/developer/provider/player-control).

## Synchronization

- A new track immediately publishes metadata with empty lyrics, clearing the preceding track.
  The asynchronous lookup then publishes timed lines, words, translation and romanization, followed
  by the latest playback position and state. Cancelled lookups cannot overwrite a newer track.
- `autoSync` restores the SDK's cached state after connecting. It does **not** advance playback
  time. `register()` acknowledges a registration request, rather than a completed connection.
- Playback uses Android `PlaybackState` with Media3's elapsed-realtime sample timestamp and actual
  speed. Lyricon's center extrapolates between Nocturn's 500 ms samples. Shared-memory positions
  stay current, but Nocturn does not switch the SDK back to Boolean/manual playback mode on normal
  ticks. Manual-only 500 ms positions produce periodic stalls in word highlighting.
- Normal polling jitter up to 750 ms preserves the remote clock anchor. Play/pause, buffering,
  speed changes, explicit seeks and larger drift update it. Explicit seeks may move backwards even
  within that tolerance. Buffering maps to paused because the SDK's center ignores `STATE_BUFFERING`.
- Connection/reconnection republishes the current cached content and current position/state without
  another lyrics request. A rejected registration request retries after five seconds; the SDK owns
  connection timeout and reconnection attempts.
- Missing lyrics and instrumental tracks use `sendText()`. Romanization uses `roma`, not `secondary`,
  which represents secondary lyric content. Translation is enabled; the Lyricon display's own options
  still determine which content is visible.

## Validation

```powershell
.\gradlew.bat :app:assembleDebug :app:testDebugUnitTest :app:lintDebug
```

`LyriconManagerTest` covers regular progress, a stable remote clock despite sample jitter, playback
speed, buffering, backward seek, delayed lyrics, metadata updates, stale responses, reconnect,
registration rejection, settings changes and destruction. Mapper tests cover timed lines and words,
translation/romanization, empty input and timestamp boundaries. These are host tests, not evidence
that a device's LSPosed configuration is correct.

Debug APKs include a silent, offline probe with fixed bilingual word-timed lyrics:

```powershell
adb shell am start -n dev.nocturn.app/.LyriconDiagnosticsActivity
adb shell run-as dev.nocturn.app cat files/lyricon-diagnostics.txt
```

The probe exercises progression, a four-second pause, backward seek, resumption, delayed track
replacement and stop. `CONNECTED` in its report comes from the real SDK connection callback. Check
the status bar itself for displayed words and translation. Completing or closing the probe restores the application's
normal Provider. It sends no audio and makes no lyric network requests. It respects the existing
Lyricon settings switch and does not change LSPosed configuration.

For device diagnostics, filter logcat on `NocturnLyricon`: creation errors, successful connection,
reconnection, disconnect and timeout are logged without credentials or lyric response bodies.
The production Provider uses the default SystemUI center. LocalCentralService requires a deliberately
configured test build as described in the upstream documentation; it is not enabled in release APKs.

2026-11-08: debug build, all 331 host tests and Android Lint passed (0 errors, 24 warnings).
The debug APK was installed on the connected device and the probe received the real central-service
connection callback. The device was locked during capture, so this does not confirm visual smoothness.
