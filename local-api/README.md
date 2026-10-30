# Nocturn LocalAPI Kotlin client

`local-api` is a Kotlin Multiplatform module. Android is the product target; the `host`
JVM target runs the shared HTTP contract tests without a device.

Android usage from a coroutine:

```kotlin
import dev.nocturn.localapi.client.*

val api = createLocalApi(applicationContext)
val results = api.searchSongs("夜曲")
val details = api.songDetail(listOf(123456L))
val url = api.songUrl(123456L, AudioQuality.Standard, cookie = accountCookie)
val lyrics = api.lyrics(123456L)
val urls = api.songUrls(listOf(123456L, 347230L), AudioQuality.Dolby, cookie = accountCookie)
val songPage = api.searchSongPage("夜曲", ApiPage(30, 0))
val playlistPage = api.playlistSongPage(24381616L, ApiPage(50, 0), cookie = accountCookie)
val syncedLyrics = api.songLyrics(1824020871L)
// Close the client when its owning application/service scope ends.
api.close()
```

`createLocalApi` joins the process-wide Android runtime startup and waits for readiness.
Closing a client releases its HTTP engine; it does not stop the embedded Node process.
Node must run for the lifetime of the Android process.

The low-level `request(LocalApiEndpoint, JsonObject)` method supports the server's curated
46-route registry. It returns the provider JSON object unchanged, including QR polling
states and music-service business errors. HTTP transport errors throw `LocalApiException`
with a stable code and optional status. Server message text and response bodies are omitted
from exception messages to avoid exposing credentials. Callers remain responsible for
interpreting the provider's business `code`, unavailable playback URLs, and account state.

The client only accepts an explicit HTTP loopback address and port, disables redirects,
and sends the runtime credential in the Authorization header. `/health` is unauthenticated
and never transmits that credential. Credentials and account Cookies must not be logged.
Cookies are passed explicitly per request; this layer does not persist them.

`LocalApiClient` takes ownership of its supplied `HttpClient`; `close()` also closes the
source client and its shared engine. Do not supply an application-wide engine used elsewhere.

Run the shared contract tests with `./gradlew :local-api:hostTest`.

## Typed operations

- `search` supports all documented search types, including combined search 1018, and uses cloudsearch by default. `searchSuggestions` defaults to mobile results. Existing `searchSongs` retains its legacy search behavior.
- `songUrls` accepts 1–200 positive IDs. IDs are sent as strings so Kotlin Long values cannot lose precision in JavaScript. `AudioQuality` includes all nine documented values; the actual URL and quality remain subject to upstream permissions and device support.
- `playlistSongs` always sends an explicit bounded `ApiPage`. `playlistSongPage` reads the complete trackIds and fetches a selected batch of details, preserves order, and reports `nextOffset`. It does not use the possibly truncated tracks preview.
- `Song` normalizes legacy artists/album/duration and newer ar/al/dt shapes. `searchSongPage` and `playlistSongPage` throw `MusicServiceException` for upstream business failures.
- `songLyrics` parses YRC word timing and falls back to LRC; it aligns translations/romanization, skips JSON credits, and distinguishes instrumental tracks from uncollected lyrics. `LyricParser.parseLrc` and `parseYrc` are also available for local text.
- `pollQrLogin` maps 800/801/802/803 into explicit states, only returns a cookie on successful authorization and redacts it from toString. `restoreSession` validates a saved cookie without re-authenticating. `refreshLogin` must not be used to refresh QR-login cookies.
- Guest registration, SMS send/verify/login, user account/subcounts, created/collected playlists, recent songs, daily refresh, availability and quality details are available through explicit operations. Nothing sends SMS, rotates sessions, or changes likes automatically.

`QrLoginResult.cookie` and raw login JSON are sensitive. Persist them only in the future secure account store. Password login and download management remain outside this iteration.
