# Repository Guidelines

## Project Structure & Module Organization

Nocturn provides LocalAPI infrastructure plus a reference client: an embedded Node service, an Android runtime, a Kotlin Multiplatform client, and a Jetpack Compose Material 3 Expressive NetEase Cloud Music client.

- `app/`: the Android client (`dev.nocturn.app`). `src/main/kotlin/dev/nocturn/app/data` holds the `MusicRepository` implementation, NetEase JSON normalisation and the encrypted session store; `playback/` the Media3 `MediaSessionService` and controller engine; `ui/` the expressive theme, shared components and screens; `NocturnViewModel` owns navigation, screen state and playback orchestration. Robolectric/Compose tests and host-rendered screenshots live in `src/test`.
- `local-api/`: KMP client, operations, models, and lyrics parsing in `src/commonMain`; Android factory in `src/androidMain`; contract tests in `src/commonTest`. The JVM `host` target runs tests only.
- `local-api-android/`: Android lifecycle code in `src/main/kotlin`, JNI/CMake in `src/main/cpp`, network configuration in `src/main/res`, and device tests in `src/androidTest`.
- `local-api-node/`: allowlisted routes and provider adapters in `src`, Node tests in `test`, and build/smoke tools in `scripts`.
- `scripts/prepare-node-runtime.mjs` downloads and verifies the pinned Node.js Mobile runtime. Generated assets belong under `local-api-android/build/generated`; API and runtime notes live in `docs/`.

## Build, Test, and Development Commands

Use JDK 21, Node.js 18+, Android SDK with platform 37 (every module compiles against API 37; `targetSdk` stays 36), NDK `28.2.13676358`, and CMake `3.22.1`. Configure `local.properties` or `ANDROID_HOME`. The wrapper pins Gradle 9.8.2 together with AGP 9.5.0 and Kotlin 2.4.21 — bump all three together, since AGP declares a minimum Gradle version.

From the repository root:

```powershell
.\gradlew.bat :app:assembleDebug :app:testDebugUnitTest :app:lintDebug
.\gradlew.bat :local-api:assemble :local-api-android:assembleDebug
.\gradlew.bat :local-api:hostTest :local-api-android:lintDebug
.\gradlew.bat :local-api-android:connectedDebugAndroidTest
```

The first line builds, tests and lints the client. The remaining lines build libraries, run shared tests/static analysis, and execute device tests respectively. Device tests require a compatible connected device. Host screenshot renders are written by the test suite to `app/build/screenshots/`.

Inside `local-api-node/`:

```powershell
npm.cmd ci
npm.cmd test
npm.cmd run smoke
```

Tests build the bundle first. Smoke checks use live services; set `NOCTURN_REFERENCE_API=https://ncmapi.rpixel.online` for comparison. `npm.cmd start` runs the built service with `NOCTURN_API_TOKEN` and an absolute `NOCTURN_DATA_DIR` configured.

## Coding Style & Naming Conventions

Use four-space indentation for Kotlin/C++, two spaces for JavaScript. Follow Kotlin official style: PascalCase types/files and camelCase functions. JavaScript uses single quotes, semicolons, `.mjs` modules, and explicit `.cjs` adapters. No formatter is configured; preserve surrounding style. Android Lint is the configured static checker.

## API Documentation & Contract Updates

Use [the deployed Docs](https://ncmapi.rpixel.online/docs/) and its `home.md`, together with the installed provider source. Read [the implementation comparison](docs/API_REFERENCE.md) and [the 2026-11-01 refresh audit](docs/DOCS_REFRESH_2026-11-01.md) before extending LocalAPI. The audit records 41 new declarations plus one example-only path; these are documentation additions, not implemented LocalAPI routes.

- Preserve the previous Docs snapshot before refreshing; record the fetch time, content hash and service version. Snapshots belong under ignored `build/reference/`; keep findings in `docs/`. The deployed Last-Modified header is unreliable.
- A matching `/inner/version` and npm version does not prove matching modules. The deployed service and pinned dependency both reported `4.41.1`, but the npm package lacks the new search/banner modules. Verify each module; pin a release or exact reviewed commit when updating dependencies, rather than fetching mutable `main` during builds.
- Add routes together in Node's `src/endpoints.mjs` and `src/provider.cjs`, Kotlin's `LocalApiEndpoint`, operations, contract tests and documentation. Preserve parameter allowlists, bounded batches and string transport for large IDs. New xeapi routes must join the provider's lazy key initialization without adding network-dependent startup.
- The 2026-11-01 audit identifies pending `vivid`, sky-only `immerseType`, and cellphone-login `sca` support. Check current code and update the comparison when resolving these gaps.
- Keep endpoint-specific contracts: new search uses `keyword`; song search returns `resources/hasMore`, lyric search uses `more`, and mobile suggestions need not start with a song card. The documented `/recommend/category/config` conflicts with the installed `/recommend/category/set`, which reads `category`, not the example's `categoryId`.
- Music playlist edits use the independent `playlist-tracks.cjs` adapter. Preserve its single submission, flat business result and code 512 without retry. The upstream `playlist/track/add` and `playlist/track/delete` modules are for video playlists. Use metadata modules that pass text as object values; preserve empty descriptions/tags for clearing.

## Testing Guidelines

Use `kotlin.test`, Ktor MockEngine, coroutine `runTest`, Node `node:test`, AndroidJUnit4, and Robolectric + Compose UI test. Name Kotlin suites `*Test.kt`, Node suites `*.test.mjs`, and test cases after observable behavior. No coverage percentage is configured. Add meaningful regression tests for changed contracts, pagination, parsing, and credentials. The client's UI tests render every screen with deterministic preview data; screenshots land in `app/build/screenshots/` and are host renders, not device screenshots. Keep offline suites deterministic. Live smoke uses public read-only routes without account Cookies; login, SMS and account mutations use injected tests. Remote HTTP checks add a timestamp to avoid upstream caching; embedded direct-module calls bypass that cache.

## Commit & Pull Request Guidelines

Use concise imperative subjects, optionally scoped, such as `fix(api): preserve unlike semantics`. PRs should describe behavior changes, link relevant issues, list validation, and update affected documentation.

## Security & Runtime Constraints

Preserve loopback-only binding, bearer authentication, parameter allowlists, and credential redaction. Keep Kotlin and Node route contracts aligned. Never commit credentials, generated bundles, native binaries, or caches. Node starts once per Android process; the pinned runtime supports 4 KB pages only. Do not claim device validation without executing it.
