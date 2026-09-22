# Plan: SAP/LZSS/WAV/XEX export family (`ExportV2`'s Tier 2)

## Context

`plans/EXPORTV2_PLAN.md` identified this family (`CSongExporter::ExportSAP_R`/
`ExportLZSS`/`ExportSAP_B_LZSS`/`ExportXEX_LZSS`/`ExportWAV`) as Tier 2 -
gated behind `CSong::DumpSongToPokeyStream()`, a real Atari-hardware-adjacent
playback loop that Batch 6 flagged but explicitly never investigated. This
plan covers investigating and (where safe) unlocking that dependency, then
triaging each of the five export methods individually.

## `DumpSongToPokeyStream` / `CSongContainer` - DONE, safe

`CSong::DumpSongToPokeyStream()` (`Song_DumpSong.cpp`) runs a real
`while (m_play != PLAY_STOP) { PlayVBI(); ...; pokeyStream.Record(); ... }`
loop - the exact shape of thing this whole effort has been careful never to
run inside a test, since nothing before had proven it terminates. Traced by
hand and confirmed safe:

- **The loop is provably bounded.** `SongPlayNextLine()` (`SongCore.cpp`)
  sets `m_play = PLAY_STOP` as soon as `CPokeyStream::TrackSongLine()`
  detects a revisited songline. For `PLAY_SONG`/`PLAY_FROM` - the only two
  modes `DumpSongToPokeyStream()` is ever called with in production
  (`SongContainer.cpp`, `SongExporter.cpp`) - `m_songplayline` always
  advances (wrapping at 255), so a revisit is guaranteed within a small,
  bounded number of songline advances regardless of song content (there are
  only 256 possible songline values). Independently corroborated by
  `PokeyStreamTests.cpp`'s own
  `TrackSongLineDetectsLoopOnSecondFullPassAndResolvesOnThird` test, which
  traces the same state machine in isolation and was already passing before
  this batch.
- **`CPokeyStream` (the whole recording state machine) was already fully
  linked and tested** (`PokeyStreamTests.cpp`) - its own header comment
  said the "data path" (`StartRecording()`/`Record()`) was blocked by
  needing a real `CAtariTrackerDriver`/`CAtari`, but that blocker was
  stale: `g_AtariTrackerDriver`/`g_Atari` have been real, linked globals
  since Batch 4/6. Comment updated.
- Remaining dependencies, all confirmed safe or given trivial link-only
  stubs (never actually invoked at runtime given the test data used):
  `g_ChannelControl.SetAllChannelsOff()` (already real/tested),
  `g_AtariTrackerDriver->Play()` (stubbed - only called if `g_rmtroutine`,
  which defaults `FALSE` and no test sets it), `RefreshScreen()` (stubbed
  to match its own guaranteed-taken guard clause: it always returns 0
  immediately when `g_hwnd` is NULL, which it always is here),
  `DisableEventSection` (stubbed - its real ctor/dtor only do a cosmetic,
  non-blocking cursor/window-enable toggle), `SendInfoMessage()` (real
  one-liner, delegates to the already-stubbed `SetStatusBarText()`).
- **Severe hazard found and defended against**: `CSongContainer`'s
  constructor calls `ThrowRuntimeException()` if the song isn't
  `PLAY_STOP` - and that macro shows a real, blocking `MessageBox` and
  then calls `exit(2)`, terminating the entire test process (not just
  failing one test). `song.Stop()` is called defensively before
  constructing any `CSongContainer` in tests, on top of the fixture's
  already-stopped default.
- Confirmed via the established "verify incrementally with an explicit
  timeout" protocol (matching Batch 6): built, ran the single new test
  alone with a short timeout, then the full suite, before anything else.
  No hangs; the first attempt's failure was a wrong assertion (the classic
  "`m_instrumentSpeed` defaults to 0" gotcha, hit for the third time this
  effort - it gates the recording loop's inner `for` entirely), not a
  hang or crash.

`Song_DumpSong.cpp`, `SongContainer.cpp`, `SongExport.cpp` now linked into
the test project. 1 new test.

## Per-method triage of the five export methods

- **`CSongExporter::ExportSAP_R` - DONE.** Same "dialog gathers params,
  real work happens independently" shape as the RMT/ASM exporters:
  `CSAPFileExportDialog::Show()` populates a `CSAPFile`, then delegates to
  `CSAPFileExporter::ExportSAP_R(songExport, sapFile, ou)` - a *different*,
  already dialog-free method. No `*Apply()` split was even needed; just
  linked `CSAPFileExporter::ExportSAP_R` directly (moved to a new
  `SAPFileExporterCore.cpp`, split from `SAPFileExporter.cpp`'s
  `ExportSAP_B_LZSS`, see below) and widened it (and
  `CPokeyStream::WriteToFile()`) to `std::ostream&`. 1 new test.
- **`CSAPFileExporter::ExportSAP_B_LZSS` - stays deferred, different
  reason than expected.** Not a dialog problem (it's called the same way
  as `ExportSAP_R`, from a thin dialog wrapper) - it's a **real file-system
  dependency**: it calls `GetResourceFilePath()`/`CAtariIO::LoadBinaryFile()`
  to load `resources/players/vu_player_v2.obx` from disk at runtime. Every
  other test in this suite is fully self-contained (in-memory streams,
  no filesystem reads) - introducing a real, path-relative file
  dependency would be a new, different category of test fragility
  (whether that resource is copied next to the test binary has never been
  verified). Confirmed by actually attempting to link `SAPFileExporter.cpp`
  wholesale into the test project first (hoping `/Gy`'s function-level
  linking would let the linker discard the unused `ExportSAP_B_LZSS` and
  its dependencies) - it doesn't get discarded (this project isn't built
  with `/OPT:REF`), so `SendErrorMessage()`/`VUPlayer::PatchMemoryForSAP_B()`/
  `GetResourceFilePath()` all needed to resolve. Split into
  `SAPFileExporterCore.cpp` (just `ExportSAP_R`) instead, matching every
  other batch's "split along the coupling seam" pattern once the wholesale
  link attempt failed.
- **`CSongExporter::ExportWAV` - stays deferred, confirmed genuinely
  hazardous.** Delegates to `CWaveFileExporter::ExportWAV()`
  (`WaveFileExporter.cpp`), which calls `pokey.GetSoundFormat()` and
  `pokey.RenderSoundV2(...)` - real POKEY *audio synthesis*
  (`CXPokey`/`PokeyRenderer.h`), not just register-value bookkeeping like
  `CPokeyStream`. This is exactly the "real audio-hardware coupling"
  category Batch 6 flagged for `TimerRoutine` (`CXPokey` holds a real
  `LPDIRECTSOUNDBUFFER`) - a categorically deeper hazard than anything
  else in this family, and it also opens/writes a real `.wav` file to
  disk via `CWaveFile::OpenFile()`. Correctly identified as needing its
  own investigation; not attempted here.
- **`CSongExporter::ExportLZSS`/`ExportCompactLZSS` - stays deferred,
  different reason again.** Neither shows a dialog at all. Both write
  **multiple real files directly to disk**, with filenames derived from
  `songExport.GetFilePath()` (e.g. `fn + "_INTRO.lzss"`) - ignoring the
  `ou` parameter for most of their output. The code's own comments call
  this "a hacked up method that was added only out of necessity... I
  refuse to touch RMT2LZSS ever again", and `ExportCompactLZSS` is marked
  "Currently unused?" - low-value, real-file-write targets, likely not
  worth the same testing investment as the rest of this family.
- **`CSongExporter::ExportXEX_LZSS`** (the 2-arg overload doing the real
  work) - **not fully triaged**; partially read. It calls
  `DumpSongToPokeyStream()` directly (now safe, confirmed reachable with
  `PLAY_FROM`) but also `CRmtAtariBinaries::GetVUPlayerBinary()` →
  `LoadResourceByteArray()`, the same real on-disk resource-file
  dependency as `ExportSAP_B_LZSS` above. Recommend deferring for the same
  reason, pending a decision on whether real-resource-file tests are
  wanted at all in this suite.

## Suggested next steps

1. **Decide whether real-resource-file dependencies are acceptable in this
   test suite at all.** `ExportSAP_B_LZSS` and `ExportXEX_LZSS` both need
   `resources/players/vu_player_v2.obx` (or the tracker-driver binaries,
   already used elsewhere) to be resolvable from wherever the test binary
   runs - a first for this effort, which has otherwise been 100%
   self-contained. If acceptable, both become approachable with the same
   dialog-splitting treatment already used throughout.
2. **`ExportWAV`**: stays deferred pending its own dedicated investigation
   into `CXPokey`'s real audio-rendering hazard - same posture as
   `TimerRoutine`/`ChangeTimer`/`ReInitSound`.
3. **`ExportLZSS`/`ExportCompactLZSS`**: low priority given their real-file-
   write design and the "hacked up"/"currently unused?" self-assessment in
   their own comments; likely not worth pursuing without a specific reason.
