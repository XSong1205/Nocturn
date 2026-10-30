# Repository Guidelines

## Project Structure & Module Organization

Nocturn currently provides LocalAPI infrastructure; its Compose Multiplatform/miuix Android UI is planned.

- `local-api/`: KMP client, operations, models, and lyrics parsing in `src/commonMain`; Android factory in `src/androidMain`; contract tests in `src/commonTest`. The JVM `host` target runs tests only.
- `local-api-android/`: Android lifecycle code in `src/main/kotlin`, JNI/CMake in `src/main/cpp`, network configuration in `src/main/res`, and device tests in `src/androidTest`.
- `local-api-node/`: allowlisted routes and provider adapters in `src`, Node tests in `test`, and build/smoke tools in `scripts`.
- `scripts/prepare-node-runtime.mjs` downloads verified native dependencies. Generated assets belong under `local-api-android/build/generated`; API and runtime notes live in `docs/`.

## Build, Test, and Development Commands

Use JDK 21, Node.js 18+, SDK 36, NDK `28.2.13676358`, and CMake `3.22.1`. Configure `local.properties` or `ANDROID_HOME`.

From the repository root:

```powershell
.\gradlew.bat :local-api:assemble :local-api-android:assembleDebug
.\gradlew.bat :local-api:hostTest :local-api-android:lintDebug
.\gradlew.bat :local-api-android:connectedDebugAndroidTest
```

These build libraries, run shared tests/static analysis, and execute device tests respectively. Device tests require a compatible connected device.

Inside `local-api-node/`:

```powershell
npm.cmd ci
npm.cmd test
npm.cmd run smoke
```

Tests build the bundle first. Smoke checks use live services; set `NOCTURN_REFERENCE_API=https://ncmapi.rpixel.online` for comparison. `npm.cmd start` runs the built service with `NOCTURN_API_TOKEN` and an absolute `NOCTURN_DATA_DIR` configured.

## Coding Style & Naming Conventions

Use four-space indentation for Kotlin/C++, two spaces for JavaScript. Follow Kotlin official style: PascalCase types/files and camelCase functions. JavaScript uses single quotes, semicolons, `.mjs` modules, and explicit `.cjs` adapters. No formatter is configured; preserve surrounding style. Android Lint is the configured static checker.

## Testing Guidelines

Use `kotlin.test`, Ktor MockEngine, coroutine `runTest`, Node `node:test`, and AndroidJUnit4. Name Kotlin suites `*Test.kt`, Node suites `*.test.mjs`, and test cases after observable behavior. No coverage percentage is configured. Add meaningful regression tests for changed contracts, pagination, parsing, and credentials. Keep offline suites deterministic; live smoke tests must avoid SMS and account mutations.

## Commit & Pull Request Guidelines

Use concise imperative subjects, optionally scoped, such as `fix(api): preserve unlike semantics`. PRs should describe behavior changes, link relevant issues, list validation, and update affected documentation. Include screenshots for future UI changes.

## Security & Runtime Constraints

Preserve loopback-only binding, bearer authentication, parameter allowlists, and credential redaction. Keep Kotlin and Node route contracts aligned. Never commit credentials, generated bundles, native binaries, or caches. Node starts once per Android process; the pinned runtime supports 4 KB pages only. Do not claim device validation without executing it.
