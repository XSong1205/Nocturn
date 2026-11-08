# Dispatch for M1 Spec Miner (M1-1)

**Milestone**: M1 - SDK Integration, Manifest Metadata & LyriconModelMapper
**Original Request**: `d:\Nocturn\.agents\teamwork\ORIGINAL_REQUEST.md` (MUST read first)
**Project Plan**: `d:\Nocturn\.agents\teamwork\orchestrator\PROJECT.md`
**Working Directory**: `d:\Nocturn\.agents\teamwork\spec_miner_m1_1`

**Objective**:
1. Investigate `app/build.gradle.kts`: verify where and how `implementation("io.github.proify.lyricon:provider:0.1.70")` should be placed.
2. Investigate `app/src/main/AndroidManifest.xml`: find the exact location inside `<application>` for `lyricon_module`, `lyricon_module_author`, `lyricon_module_description`, and `lyricon_module_tags`.
3. Investigate `app/src/main/res/values/arrays.xml`: determine if `arrays.xml` exists or needs creation, with array `@array/lyricon_module_tags` containing `$syllable` and `$translation`.
4. Check if any ProGuard / R8 rules (`app/proguard-rules.pro`) or packaging options are needed for Lyricon SDK.
5. Provide precise code snippets and recommendations for Worker. Do NOT modify source files directly.
6. Report in `d:\Nocturn\.agents\teamwork\spec_miner_m1_1\handoff.md`.


## 2026-11-07T13:30:25Z
You are teamwork_spec_miner for Milestone 1 (SDK Integration, Manifest Metadata & LyriconModelMapper).
Your working directory is: d:\Nocturn\.agents\teamwork\spec_miner_m1_1
You MUST read d:\Nocturn\.agents\teamwork\ORIGINAL_REQUEST.md before starting work.
Also read d:\Nocturn\.agents\teamwork\orchestrator\PROJECT.md and d:\Nocturn\.agents\teamwork\spec_miner_m1_1\DISPATCH.md.

Task:
1. Inspect app/build.gradle.kts to locate the precise insertion point and dependency format for io.github.proify.lyricon:provider:0.1.70.
2. Inspect app/src/main/AndroidManifest.xml to locate the exact position under <application> for the 4 meta-data entries (lyricon_module, lyricon_module_author, lyricon_module_description, lyricon_module_tags).
3. Inspect app/src/main/res/values/ (check if arrays.xml exists or if it should be created) for the string-array lyricon_module_tags with $syllable and $translation.
4. Check ProGuard / R8 rules in app/proguard-rules.pro to see if any keep rules are required.
5. Provide ready-to-use, precise implementation recommendations for the Worker. Do NOT edit or write source code files.
6. Write handoff report in d:\Nocturn\.agents\teamwork\spec_miner_m1_1\handoff.md and send completion message.
