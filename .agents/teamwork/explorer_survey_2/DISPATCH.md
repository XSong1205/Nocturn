# Dispatch for Explorer 2 (Playback Lifecycle & Architecture)

**Task**: Investigate Nocturn's playback lifecycle, MediaSession / Media3 integration, state management, and song change flow.
**Original Request**: `d:\Nocturn\.agents\teamwork\ORIGINAL_REQUEST.md` (MUST read first)
**Working Directory**: `d:\Nocturn\.agents\teamwork\explorer_survey_2`

**Objective**:
1. Explore `app/src/main/kotlin/dev/nocturn/app/playback/` and `dev/nocturn/app/NocturnViewModel.kt`.
2. Map out how playback state (playing/paused/stopped), current song metadata (id, title, artist, duration), current progress / position polling, and seekTo events are dispatched or observed.
3. Identify where and how a new service, helper, or listener for external providers (like Lyricon) should be hooked in (e.g., within MediaSessionService, PlaybackEngine, or NocturnViewModel/MusicRepository).
4. Identify how app lifecycle or playback service lifecycle affects lifecycle of Lyricon Provider.
5. Report findings and architecture recommendation in `d:\Nocturn\.agents\teamwork\explorer_survey_2/handoff.md`.
