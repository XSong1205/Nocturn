# Dispatch for Spec Miner (Survey 1)

**Task**: Extract complete Lyricon Provider SDK specifications and API contracts.
**Target Docs**: https://tomakino.github.io/lyricon/zh-cn/developer/ and related Lyricon docs/SDK details.
**Original Request**: `d:\Nocturn\.agents\teamwork\ORIGINAL_REQUEST.md` (MUST read first)
**Working Directory**: `d:\Nocturn\.agents\teamwork\spec_miner_survey_1`

**Objective**:
1. Research Lyricon developer docs (use `read_url_content` or `search_web` if needed for https://tomakino.github.io/lyricon/zh-cn/developer/).
2. Identify exact Maven coordinates, dependencies, repo requirements (e.g. Maven Central / Jitpack / etc.).
3. Identify Manifest metadata keys (`lyricon_module`, `lyricon_module_author`, `lyricon_module_description`, `lyricon_module_tags`, etc.).
4. Identify SDK classes and APIs: `LyriconFactory`, Provider creation, lifecycle methods (register, unregister, release, etc.), playback synchronization APIs (song ID, title, artist, duration, play/pause, position, seekTo), lyric data models (`RichLyricLine`, `LyricWord`, translation, `setDisplayTranslation`, etc.).
5. Produce a comprehensive report in `d:\Nocturn\.agents\teamwork\spec_miner_survey_1/handoff.md`.

## 2026-11-07T13:10:14Z
From: 7c985a65-7d83-475c-933c-cc1d23728416
Task:
Extract complete Lyricon Provider SDK specifications and API contracts.
Target Documentation: https://tomakino.github.io/lyricon/zh-cn/developer/ and related Lyricon docs/SDK sources.
Examine:
1. Maven dependency details: group, artifact, version (e.g. io.github.proify.lyricon:provider:0.1.70), repositories needed (e.g., mavenCentral()).
2. AndroidManifest metadata: all required and optional meta-data tags under <application> (e.g., lyricon_module, lyricon_module_author, lyricon_module_description, lyricon_module_tags with $syllable and $translation).
3. SDK APIs: LyriconFactory, Provider creation, lifecycle (register, unregister, release, destroy, etc.).
4. Playback synchronization APIs: how to push current song info (ID, title, artist, duration), play/pause state, playback position / progress, seekTo.
5. Lyric data structures: RichLyricLine, LyricWord, start/end times, syllables, translations, setDisplayTranslation, placeholder/pure music handling.
