# Plan: `IO_Importer.cpp` (`ImportTMC`/`ImportMOD`)

## Context

Flagged by `plans/BROADER_SURVEY_PLAN.md` as the single biggest remaining
testing opportunity: `CSong::ImportTMC(std::ifstream&)` and
`CSong::ImportMOD(std::ifstream&)` are real, substantial, currently-untested
production functionality (importing Protracker MOD and TMC module files),
in a 1868-line file. Read both methods in full before writing this plan, per
this effort's established "analyze before implementing" discipline.

## Structure (both methods are the same shape)

Both methods follow an identical three-phase shape:

1. **Unconditional real work** (`ClearSong(8)`, `SetMaxTrackLength(64)`,
   parse the file header via `CAtariIO::LoadBinaryBlock`/raw stream reads,
   validate it - guard-only `MessageBox` on corruption/bad format/OOM -
   and derive display text, e.g. the song name or channel count). This
   happens *before* any dialog and already mutates real song state
   (`ClearSong`) even if the file turns out to be corrupt - a pre-existing
   quirk, not something to fix.
2. **One real options dialog** (`CImportTmcDlg` for TMC; `CImportModDlg`
   for MOD), shown with text built from phase 1's parsed data. Its result
   supplies a handful of `BOOL` flags (TMC: `x_usetable`/`x_optimizeloops`/
   `x_truncateunusedparts`; MOD: those three plus `x_shiftdownoctave`/
   `x_portamento`/`x_fullvolumerange`/`x_volumeincrease`/
   `x_decreaseinstrument`, plus a track-layout choice/`rmttype`, plus
   `x_fourier` - unused, its only reference is inside a genuinely
   commented-out `/* ... */` dead-code block, same "not real code" category
   as the earlier `MakeTuningBlock`/`DecodeTuningBlock` finding). Cancelling
   here returns early (0), leaving phase 1's `ClearSong` un-reverted.
3. **The real conversion** (the bulk of each function: per-track/
   per-instrument/per-songline decode into `g_Tracks`/`g_Instruments`/
   `m_song`/etc., using the flags from phase 2), followed by one more real
   **confirmation dialog** (`CImportTmcFinishedDlg`/`CImportModFinishedDlg`)
   showing stats (tracks/instruments/songlines converted, plus optional
   `TracksAllBuildLoops`/`SongClearUnusedTracksAndParts` optimization
   stats). Cancelling this one *does* revert, via
   `ClearSong(originalg_tracks4_8)`.

Coupling confirmed via full read: only already-safe globals throughout
(`g_Instruments`, `g_Tracks`, `g_tracks4_8`) plus guard-only `g_hwnd`
`MessageBox`es (corrupted/unsupported/OOM errors, and the two "aborted"
notices after a confirm-dialog cancel) - no different in kind from
`LoadRMT`/`MakeModule`'s already-accepted guard-only pattern. All calls
into already-safe cluster methods (`SetTracks`, `TracksAllBuildLoops`,
`SongClearUnusedTracksAndParts`, `g_Instruments.Update()`). `CConvertTracks`
(TMC-only helper class) has no global coupling of its own. No hardware/
timer/audio coupling anywhere in either method.

## The open design question

Every prior `*Apply()` split in this effort (`InstrChange`,
`SongInsertCopyOrCloneOfSongLines`, `TracksOrderChange`, `ExportAsAsm`,
`ExportAsRelocatableAsmForRmtPlayer`) had a dialog that only needed
*already-existing* state to build its own text (current instrument values,
current track layout, export options) - so the split was clean: dialog
gathers a fixed-shape parameter struct up front, real work runs identically
afterward regardless of which values were chosen.

`ImportTMC`/`ImportMOD` break that assumption: **the options dialog's own
display text depends on data only available after partially decoding the
file** (the parsed song name / channel count from phase 1). The dialog
isn't just "gather parameters, then run `Apply()`" - phase 1's real
decoding has to happen *before* the dialog can even be shown correctly.

Three ways to handle this, in order of how much they preserve today's exact
structure vs. how clean the resulting test surface is:

1. **Two-phase `Apply()`**: split into `ImportTMCParseHeader(std::istream&,
   TImportTMCHeader& out)` (phase 1 only - `ClearSong`, load+validate,
   derive song name/channel info) and
   `ImportTMCApply(const TImportTMCHeader& header, BOOL usetable, BOOL
   optimizeloops, BOOL truncateunusedparts, TImportTMCResult& result)`
   (phases 2's real-work-minus-dialog and phase 3's real-work-minus-dialog).
   The thin wrapper calls both, in between showing its two real dialogs.
   Most faithful to today's exact call sequence (each real step runs
   exactly once, in the same order); most new surface (two new methods per
   import format instead of one), and `TImportTMCHeader` needs to carry
   whatever raw decoded state (`mem[]`/`bfrom` for TMC; `mem`/`chnls`/
   `modsamples`/etc. for MOD) phase 3 needs, which is naturally sized (up to
   64KB for TMC's `mem[65536]`) and thus better heap-allocated inside the
   header struct than stack-copied by value.
2. **Single `Apply()`, header parsed twice**: `ImportTMCApply(std::istream&
   in, BOOL usetable, BOOL optimizeloops, BOOL truncateunusedparts,
   TImportTMCResult& result)` does *everything* (phases 1-3 minus both
   dialogs) as one self-contained, easily-testable function. The thin
   wrapper *duplicates* phase 1's ~20 lines (parse+validate+song name) just
   to build its first dialog's text, then calls `Apply()` afterward, which
   redoes that exact same parse (deterministic, harmless to repeat -
   `ClearSong` is idempotent in effect, and the stream just needs
   `seekg`ing back to the start first). Simplest, single well-tested entry
   point; the cost is ~20 duplicated lines per format between the wrapper
   and `Apply()`, and phase 1's `MessageBox`/return-0 guard exists in both
   places (once for real, once inside the now-untested wrapper).
3. **Accept a small, documented behavior change**: move the dialog's text
   to something not requiring phase-1 data (e.g. a generic title, with the
   song name/channel info added to the *result* dialog instead, or simply
   dropped). Cleanest code, but changes real user-visible dialog text -
   out of step with every other decision in this effort, which has
   consistently avoided production behavior changes outside of the
   dedicated, separately-approved ones (`IDS_RMT_VERSION`, `TrackInfo`).
   Not recommended.

## Suggested batching

Given the size, treat as two batches once the design question above is
resolved:
- **Batch A**: `ImportTMC` + `CConvertTracks` (smaller, ~715 lines of the
  file, one dialog's worth of flags). **DONE** - see below.
- **Batch B**: `ImportMOD` (~940 lines, one dialog with two flag sets
  depending on channel count, plus the unused-but-present `x_fourier`/
  dead Fourier-transform code). Not yet started.

Both move into a new `IO_ImporterCore.cpp` (mirroring the established
`*Core.cpp` split pattern), leaving the still-real, still-dialog-showing
thin wrappers behind in `IO_Importer.cpp`. `std::ifstream&` widens to
`std::istream&` for both, matching every prior stream-parameter precedent
this effort has established.

## Batch A - DONE: `ImportTMC`

Implemented the "two-phase `Apply()`" design (user's explicit choice - most
faithful to today's exact call sequence). `TImportTMCHeader`/
`TImportTMCResult` added to `SongTypes.h` (`TImportTMCHeader.mem[65536]`
mirrors the existing `TExportDescription.mem[65536]` precedent for embedding
a full-RAM buffer directly in a struct).

- `CSong::ImportTMCParseHeader(std::istream&, TImportTMCHeader&)`: the
  unconditional phase-1 work (`ClearSong(8)`, `SetMaxTrackLength(64)`,
  `CAtariIO::LoadBinaryBlock`, song name parse). Returns `false` (matching
  the original guard) on a corrupted/unsupported file.
- `CSong::ImportTMCApply(const TImportTMCHeader&, BOOL usetable, BOOL
  optimizeloops, BOOL truncateunusedparts, TImportTMCResult&)`: the entire
  real conversion (tracks, instruments, song), transcribed verbatim from the
  original function body (including its inert commented-out dead-code
  block, kept byte-for-byte rather than cleaned up, to keep this a pure
  mechanical move) - only `mem`/`bfrom` become `header.mem`/`header.bfrom`,
  and the three `x_*` locals become parameters. Ends by populating `result`
  (including running `TracksAllBuildLoops`/`SongClearUnusedTracksAndParts`
  when requested, same as the original).
- `CConvertTracks` (+ its 3 supporting structs, TMC-only, confirmed unused
  by `ImportMOD`) moved into `IO_ImporterCore.cpp` alongside them.
- `ImportTMC()` itself, now a thin wrapper in `IO_Importer.cpp`, calls both
  in sequence between showing its two real dialogs, exactly preserving the
  original call order and the "cancel the front dialog leaves `ClearSong`
  un-reverted" quirk.
- No new links beyond `IO_ImporterCore.cpp` itself were required - every
  global/method it touches (`g_Instruments`, `g_Tracks`, `CAtariIO`,
  `CSong`'s own `ClearSong`/`SetTracks`/`TracksAllBuildLoops`/
  `SongClearUnusedTracksAndParts`) was already linked from prior batches.
- 3 new hand-derived tests (`ImportTMCParseHeader`'s truncated-file guard
  and song-name parse; a full `ImportTMCApply` conversion built from a
  hand-crafted minimal TMC byte buffer - see the test file for the full
  byte-layout derivation). One test's first assertion attempt was wrong
  (expected the note's volume to survive at 15, got 0) - traced to a real,
  faithful behavior: `MakeOrFindTrackShiftLR()`'s volume normalizes against
  the *instrument's* tracked max envelope volume, which defaults to 0 for
  an instrument that was never defined (as in this minimal test's degenerate
  input) - not a test bug, fixed the expectation and documented why.
  Also fixed a stale comment in `SongEditingTests.cpp` claiming
  `CInstruments::Update()` was still stubbed as a no-op - it's had real
  behavior since Batch 3 of `plans/SONG_IO_SONG_REMAINING_PLAN.md`,
  the comment was just never updated.
- Full solution rebuild (Release|x64) confirmed 0 errors; 236 tests pass
  (up from 233, +3, 0 regressions).
