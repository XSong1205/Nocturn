# BRIEFING — 2026-11-07T13:38:30Z

## Mission
Probe authoritative specifications and codebase layout for Milestone 1 (SDK integration, AndroidManifest metadata, res/values arrays.xml, ProGuard, and LyriconModelMapper contract), providing complete and exact recommendations for the Worker.

## 🔒 My Identity
- Archetype: teamwork_spec_miner
- Roles: Specification Miner
- Working directory: d:\Nocturn\.agents\teamwork\spec_miner_m1_1
- Original parent: 7c985a65-7d83-475c-933c-cc1d23728416
- Milestone: M1 (SDK Integration, Manifest Metadata & LyriconModelMapper)

## 🔒 Key Constraints
- Read-only on source code — do NOT edit or implement source files
- Prioritize authoritative specifications (Lyricon docs, Nocturn codebase, Gradle build files)
- Keep all metadata inside .agents/teamwork/spec_miner_m1_1
- Deliver complete, ready-to-use implementation snippets and verification method for Worker

## Current Parent
- Conversation ID: 7c985a65-7d83-475c-933c-cc1d23728416
- Updated: not yet

## Task Summary
- **What to build**: Specification discovery and code snippet guidance for Milestone 1
- **Success criteria**: Exact dependency insertion in `app/build.gradle.kts`, exact manifest meta-data in `AndroidManifest.xml`, exact `arrays.xml` contents, ProGuard analysis, and precise Lyricon model types and mapping requirements for `LyriconModelMapper`
- **Interface contracts**: `d:\Nocturn\.agents\teamwork\orchestrator\PROJECT.md`
- **Code layout**: `d:\Nocturn\.agents\teamwork\orchestrator\PROJECT.md § Code Layout`

## Key Decisions Made
- `app/build.gradle.kts`: dependency `implementation("io.github.proify.lyricon:provider:0.1.70")` should be placed after line 147 (`lyrics-core`) or 152 (`miuix-blur`).
- `AndroidManifest.xml`: 4 `<meta-data>` tags (`lyricon_module`, `lyricon_module_author`, `lyricon_module_description`, `lyricon_module_tags`) should be placed under `<application>` immediately preceding the `<activity>` tag (line 23).
- `app/src/main/res/values/`: `arrays.xml` does not exist and must be created with `lyricon_module_tags` containing `$syllable` and `$translation`.
- `app/proguard-rules.pro`: AAR `proguard.txt` is empty. Explicit `-keep class io.github.proify.lyricon.** { *; }` and `-dontwarn io.github.proify.lyricon.**` must be added to protect AIDL IPC and Kotlinx Serialization.
- `LyriconModelMapper`: Full bidirectional mapping specs formulated between Nocturn domain models and Lyricon SDK data models.

## Artifact Index
- `DISPATCH.md` — Dispatch instructions
- `BRIEFING.md` — Persistent agent memory
- `progress.md` — Execution liveness heartbeat
- `handoff.md` — Handoff report with findings and recommendations for Worker
