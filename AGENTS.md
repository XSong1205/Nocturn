# Repository Guidelines & Specification

Nocturn is a private, offline-first NetEase Cloud Music ecosystem providing a local Node.js service embedded into an Android runtime, a Kotlin Multiplatform (KMP) client library, and an expressive Android client built with Jetpack Compose, Material Design 3 Expressive, and Navigation 3.

---

## 1. Project Structure & Module Organization

- `app/`: Android reference client (`dev.nocturn.app`).
  - `src/main/kotlin/dev/nocturn/app/data`: `MusicRepository` implementation, NetEase JSON normalization, and encrypted session store (`SessionStore`).
  - `src/main/kotlin/dev/nocturn/app/playback`: Media3 `MediaSessionService`, ExoPlayer engine, and Lyricon floating lyrics manager.
  - `src/main/kotlin/dev/nocturn/app/ui`: Jetpack Compose Material 3 Expressive UI layer.
    - `theme/`: Semantic colors (`NocturnDarkColors`, `NocturnLightColors`), Chinese-optimized typography (`NocturnTypography`, `NocturnText`), shape scale (`NocturnShapes`), and `NocturnTheme`.
    - `adaptive/`: Window width classes (`Compact`, `Medium`, `Expanded`), `LocalWindowWidthClass`, and responsive layout helpers.
    - `desktop/`: Expanded/large window layouts (`DesktopScaffold`, `DesktopSidebar`, `DesktopPlayerBar`, `DesktopLyricsPanel`).
    - `components/`: Shared design components (`GlassBars`, `Blur`, `CoverArt`, `SongRow`, `PlaylistCard`, `HeroCard`, `Backdrop`).
    - `screens/`: Feature screens (Home, Library, Player, Playlist, Search, Settings).
    - `state/`: Unidirectional UI state contracts and store holders.
  - `src/test`: Robolectric + Compose UI tests and host-rendered deterministic screenshots in `app/build/screenshots/`.
- `local-api/`: Kotlin Multiplatform (KMP) client.
  - `src/commonMain`: HTTP client, endpoints, operations, data models, and lyrics parsers.
  - `src/androidMain`: Android platform factory.
  - `src/commonTest`: Multiplatform contract tests and parsing tests.
  - `host`: JVM target for running test suites.
- `local-api-android/`: Android lifecycle orchestration for the embedded Node service.
  - `src/main/kotlin`: Android service lifecycle, local token management, and health checks.
  - `src/main/cpp`: JNI bridge and CMake build definitions for the embedded Node runtime.
  - `src/main/res`: Network security configuration enforcing loopback-only traffic.
  - `src/androidTest`: On-device instrumented tests.
- `local-api-node/`: Node.js Mobile service wrapper.
  - `src/`: Allowlisted endpoints, provider adapters (`provider.cjs`, `playlist-tracks.cjs`), and server entrypoints.
  - `test/`: Node test suites (`node:test`).
  - `scripts/`: Bundle and live smoke check tools.
- `scripts/prepare-node-runtime.mjs`: Tooling to download, verify, and unpack the pinned Node.js Mobile runtime for Android.
- `docs/`: In-depth engineering specifications:
  - `API_REFERENCE.md`: Implementation comparison and LocalAPI contracts.
  - `DOCS_REFRESH_2026-11-01.md`: Audit of upstream route additions.
  - `LYRICS_UI.md`: Fluid lyrics rendering, timing clock synchronization, and performance benchmarks.
  - `NAVIGATION.md`: Jetpack Navigation 3 recipes, predictive back gestures, and scene lifecycle.
  - `NODE_RUNTIME.md`: Android Node.js Mobile runtime integration and memory constraints.

---

## 2. Build, Test, and Development Commands

### Toolchain Prerequisites
- **JDK**: JDK 21
- **Node.js**: Node 18+ (LTS)
- **Android SDK**: Compile SDK `37`, Target SDK `36`, Min SDK `28`
- **NDK**: `28.2.13676358`
- **CMake**: `3.22.1`
- **Gradle**: Pin Gradle `9.8.2` with AGP `9.5.0` and Kotlin `2.4.21` (always bump together due to minimum AGP version constraints).

### Root Gradle Commands (PowerShell)
```powershell
# Build debug APK, run host unit tests, and execute Android lint
.\gradlew.bat :app:assembleDebug :app:testDebugUnitTest :app:lintDebug

# Build KMP and Android local-api libraries
.\gradlew.bat :local-api:assemble :local-api-android:assembleDebug

# Run KMP host tests and Android static analysis
.\gradlew.bat :local-api:hostTest :local-api-android:lintDebug

# Run on-device instrumentation tests (requires connected device)
.\gradlew.bat :local-api-android:connectedDebugAndroidTest
```

### Local API Node Commands
From `local-api-node/`:
```powershell
npm.cmd ci
npm.cmd test
npm.cmd run smoke
```
- Tests compile the bundle before testing.
- `npm.cmd start` requires `NOCTURN_API_TOKEN` and an absolute `NOCTURN_DATA_DIR`.
- Live smoke checks require `NOCTURN_REFERENCE_API=https://ncmapi.rpixel.online`.

---

## 3. Coding Style & Naming Conventions

- **Kotlin / C++**: 4-space indentation. PascalCase for class/interface names and file names; camelCase for functions and variables.
- **JavaScript**: 2-space indentation. Single quotes, semicolons, `.mjs` for ESM modules, and explicit `.cjs` for CommonJS adapters.
- **Compose**: PascalCase for `@Composable` functions returning `Unit` or emitting UI; camelCase for composables returning values.
- **Static Analysis**: Android Lint is the authoritative static checker. Zero warnings/errors tolerated in release paths.

---

## 4. Material Design 3 (M3) & Expressive UI/UX Architecture

Nocturn adheres strictly to Google Material Design 3 (M3) and Material 3 Expressive design systems. The interface is engineered to **behave like Material 3**, not merely apply arbitrary rounded corners or pastel colors.

### 4.1 System Hierarchy & Design Philosophy
Treat M3 as a cohesive pipeline:
**User Goal → Information Architecture → Hierarchy → Adaptive Layout → Semantic Tokens → Components → States & Feedback → Motion → Accessibility → Expressive Restraint**

- Custom components must inherit token, state, motion, and accessibility behavior from the platform theme.
- Never "Materialize" a screen by merely adding large corner radii, gradients, or Material Symbols.
- Distinguish primary actions from secondary and destructive actions.

### 4.2 Semantic Theming & Design Tokens (`dev.nocturn.app.ui.theme`)

1. **Color Roles (`Color.kt`)**:
   - **Dark Palette (`NocturnDarkColors`)**: Night background (`#100D14`), Coral primary (`#FFB4A8`), Lime secondary accent (`#CDF56E`), Sky Cyan tertiary accent (`#7BD8FF`), container hierarchy (`surfaceContainerLowest` through `surfaceContainerHighest`).
   - **Light Palette (`NocturnLightColors`)**: CoralDeep primary (`#B02D1C`), warm paper surfaces (`#FFF8F4`).
   - **Dynamic Color (Monet)**: Automatically supported on Android 12+ (API 31+) via `dynamicDarkColorScheme()` / `dynamicLightColorScheme()` when enabled in Settings.
   - **Rule**: Hardcoded hex color literals in UI screens and composables are strictly prohibited. Always consume semantic tokens from `MaterialTheme.colorScheme` (e.g., `surface`, `onSurface`, `surfaceContainer`, `outlineVariant`, `errorContainer`).

2. **Typography Scale (`Type.kt`)**:
   - **Chinese / CJK Optimization**:
     - Uses `CenteredLineHeight` (`LineHeightStyle.Alignment.Center`, `Trim.None`) to ensure vertical optical centering inside chips, buttons, and row items.
     - Drops Latin-specific positive tracking on body and label styles (as CJK characters have inherent side bearings).
     - Tightens letter spacing and increases font weight on display and headline styles.
     - Configures explicit `*Emphasized` variants (`ExtraBold` / `Bold`) for expressive moments.
   - **Ad-hoc Text Styles (`NocturnText`)**:
     - `eyebrow`: All-caps bold Latin badges (e.g., "VIP", "Hi-Res").
     - `chip`: Semi-bold CJK pill labels (e.g., "编辑精选", "12 首歌曲").
     - `numeric`: Monospaced numbers with tabular figures enabled (`fontFeatureSettings = "tnum"`).

3. **Shape Scale (`NocturnShapes`)**:
   - Expressive rounded corner scale: `extraSmall` (10dp), `small` (14dp), `medium` (20dp), `large` (28dp), `largeIncreased` (32dp), `extraLarge` (36dp), `extraLargeIncreased` (44dp), `extraExtraLarge` (52dp).

4. **Expressive Theme Setup (`Theme.kt`)**:
   - Configured via `MaterialExpressiveTheme` with `MotionScheme.expressive()`.
   - Injects `LocalIsDarkTheme` and ensures proper `LocalContentColor` fallbacks over transparent layered backdrops.

### 4.3 Adaptive Layout & Form Factors (`dev.nocturn.app.ui.adaptive`)

Layouts must design for the **available window width** and **platform form factor**:

| Window Width Class | Breakpoint | Shell Presentation | Layout Architecture |
|---|---|---|---|
| **Compact** | `< 600dp` | Bottom `ShortNavigationBar` | Portrait phone layout. Floating/docked `MiniPlayer`, full-screen swipeable `PlayerScreen` sheet overlay. |
| **Medium** | `600dp .. 839dp` | Left `ClientNavigationRail` | Foldables unfolded, small tablets, landscape phones. Bottom centered floating pill `MiniPlayer` (bounded to `760dp` max width). Content area adapts fluidly. |
| **Expanded** | `≥ 840dp` | Mobile: Left `ClientNavigationRail`<br>Windows Desktop: Left `DesktopSidebar` | Tablets & large screens use `ClientNavigationRail` with multi-column/two-pane content layouts (`DesktopScaffold` forced disabled on mobile). Windows forces `DesktopScaffold`: Left expandable sidebar, dynamic center view, right collapsible `DesktopLyricsPanel` inspector, full-width docked `DesktopPlayerBar`. |

- **Adaptive Rules**:
  - Windows platform forces desktop UI (`DesktopScaffold`).
  - Android and other mobile platforms force disable desktop UI (`DesktopScaffold`), preventing high-resolution tablets from being misjudged as desktop workstations.
  - Expanded space on tablets must improve productivity and context (e.g., two-pane lyrics & cover in `PlayerScreen`, multi-column song grids in playlists and albums), utilizing touch-friendly mobile/tablet navigation.
  - Safe insets (`WindowInsets.navigationBars`, `statusBars`) must be respected across all form factors.

### 4.4 Component Selection & Semantics

- **Buttons**:
  - Primary CTA: `Button` (Filled) using `primary` / `onPrimary`. Maximum one primary button per screen region.
  - Secondary / Supporting: `FilledTonalButton` or `OutlinedButton`.
  - Low emphasis / In-row: `TextButton` or `IconButton`.
- **Navigation Controls**:
  - Compact: `ShortNavigationBar` + `ShortNavigationBarItem` (M3 Expressive).
  - Medium: `NavigationRail` + `NavigationRailItem`.
  - Expanded: `DesktopSidebar` with expandable menu items.
  - **Prohibited**: Do NOT use `FilterChip` or generic buttons as primary navigation. Tabs (`PrimaryTabRow` / `SecondaryTabRow`) are strictly for switching parallel sub-views within a screen, not top-level navigation.
- **Lists vs Cards**:
  - Ordinary items (e.g. tracks in playlist or search results) must use flat `SongRow` items with dividers/padding.
  - Do NOT wrap every list item inside an elevated `Card`. Cards (`PlaylistCard`, `HeroCard`) are reserved for structured grouping, albums, playlists, and featured daily banners.
- **Sheets & Feedback**:
  - `PlayerSheetDrag`: Modal draggable sheet for full-screen music player.
  - `PlayerQueueSheet`: Modal bottom sheet for queue management.
  - `SnackbarHost`: Non-intrusive transient feedback with recovery actions (e.g., dismiss or retry).

### 4.5 Motion, Fluid Transitions & Real-Time Lyrics Engine

- **Navigation Transitions (`PageTransitions.kt`)**:
  - Jetpack Navigation 3 standard: push and pop use opposite full-width horizontal slides (mirrored in RTL).
  - Predictive back gesture: identical pop transform sought continuously through edge swipe, commit, or cancel.
  - Top-level tab switching: smooth crossfade animation.
  - Reduced-motion: respect system animation scales; zero-duration instant swaps when animations are disabled.
- **Accompanist Fluid Lyrics Engine (`docs/LYRICS_UI.md`)**:
  - Fluid scrolling using 580ms Apple Music fluid curve: `CubicBezierEasing(0.25f, 0.1f, 0.25f, 1f)` paired with high-damping spring chains (`stiffness = 200f`, `damping = 28f`, `coupling = 0.15f`).
  - Monotonic clock: Media3 playback sampling (`elapsedRealtimeNanos`) feeds a single Compose highlight clock.
  - Negative offset prevention: backward seeking, scrubbing, or repeat immediately bypasses the per-line spring chain to eliminate negative-offset row collisions.
- **Glassmorphism & Backdrop Blur (`GlassBars.kt`, `Blur.kt`)**:
  - Android 12+ (API 31+): hardware-accelerated `layerBackdrop` and `RenderEffect` blur (`blurAvailable`).
  - Android 10/11 fallback: elegant translucent tinted surfaces without blur.

### 4.6 Accessibility & Inclusive Design (WCAG 2.1 AA)

- **Touch Targets**: Minimum `48dp × 48dp` for all touchable controls. Visual icons (e.g. 24dp) must be padded to meet the 48dp boundary.
- **Contrast Ratios**: Minimum `4.5:1` for body/label text; minimum `3:1` for large headlines and essential graphical boundaries.
- **Screen Reader Semantics**:
  - Every `IconButton` and image action must supply an explicit `contentDescription` (localized or meaningful string).
  - Purely decorative images must pass `contentDescription = null`.
- **Non-Color State Communication**: Active/selected states must never rely on color alone; use icon changes, badges, container shapes, or bold typography.
- **Dynamic Font Scaling**: Text must use `sp` units and accommodate large scale factors (up to 200%) without clipping or truncation.
- **Tactile Feedback**: Haptics are centralized via `LocalHapticsEnabled` and `rememberHaptics()`, respecting user preference.

### 4.7 M3 Expressive Levers & Restraint

- M3 Expressive is applied with intentionality to emotional anchors: the Now Playing player, vinyl/cover rotation, daily recommendation hero cards, and lyrics flow.
- Dense data surfaces, search inputs, settings lists, and form sheets remain calm, clear, and high-contrast.
- **Restraint Rule**: *If everything is expressive, nothing is emphasized.*

### 4.8 Self-Audit Checklist for UI & Code Reviews

Before submitting or approving UI changes, score each category `0` (fail), `1` (needs work), or `2` (ready):
1. **Task Clarity**: Primary goal and action are immediately obvious.
2. **Information Hierarchy**: Semantic grouping with coherent emphasis.
3. **Component Semantics**: Controls match their actual M3 behavior (no fake buttons, no chips as nav).
4. **Token Discipline**: Only semantic tokens used (`colorScheme`, `typography`, `shapes`), zero hardcoded hex colors.
5. **Adaptive Behavior**: Responsive across Compact (<600dp), Medium (600-839dp), and Expanded (≥840dp).
6. **States & Feedback**: Default, pressed, focused, disabled, loading, empty, and error recovery states defined.
7. **Accessibility**: 48dp hit targets, 4.5:1 contrast, `contentDescription`, dynamic text scaling, reduced motion.
8. **Expressive Restraint**: Expressive flourishes do not distract from readability or usability.

*Any score of `0` in Component Semantics, States & Feedback, or Accessibility blocks PR merge.*

---

## 5. API Documentation & Contract Updates

Use the deployed Docs (`https://ncmapi.rpixel.online/docs/`) and `home.md`, together with installed provider source. Read `docs/API_REFERENCE.md` and `docs/DOCS_REFRESH_2026-11-01.md` before extending LocalAPI.
- Preserve previous Docs snapshots before refreshing; record fetch time, content hash, and service version under ignored `build/reference/`.
- Verify npm modules independently: matching `/inner/version` does not guarantee matching modules. Pin exact reviewed commits or releases.
- Add routes simultaneously across Node's `src/endpoints.mjs`, `src/provider.cjs`, Kotlin's `LocalApiEndpoint`, operations, contract tests, and documentation.
- Preserve parameter allowlists, bounded batches, and string transport for 64-bit IDs.
- Music playlist edits MUST use `playlist-tracks.cjs`. Preserve its single submission, flat business result, and code 512 without retry.

---

## 6. Testing Guidelines

- **Frameworks**: `kotlin.test`, Ktor `MockEngine`, coroutine `runTest`, Node `node:test`, AndroidJUnit4, and Robolectric + Compose UI tests.
- **Naming**: Kotlin suites `*Test.kt`, Node suites `*.test.mjs`, test methods named after observable behavior.
- **UI Screen Testing**: Compose tests render every screen with deterministic `PreviewData`. Host screenshots are saved to `app/build/screenshots/` (host renders, not device screenshots).
- **Determinism**: Offline suites must remain 100% deterministic. Smoke checks add timestamps to prevent upstream cache poisoning. Direct module calls bypass upstream caching.

---

## 7. Commit & Pull Request Guidelines

- Use concise imperative subjects, optionally scoped: e.g., `feat(ui): add desktop lyrics panel`, `fix(api): preserve unlike semantics`.
- PR descriptions must explain behavioral changes, link issues, detail validation steps, and update affected documentation.

---

## 8. Security & Runtime Constraints

- Enforce loopback-only binding (`127.0.0.1`), bearer token authentication, parameter allowlists, and credential redaction.
- Align Kotlin and Node route contracts strictly.
- Never commit user credentials, generated bundles, native binaries, or cache artifacts.
- Embedded Node runtime starts once per Android process and supports 4 KB memory pages only.
- Do not claim on-device hardware validation without actual execution.
