# Plan: `Song.cpp` / `IO_Song.cpp` remaining methods

## Context

`Song.cpp`/`IO_Song.cpp` has already had two "safe cluster" batches extracted
(see `plans/NOTES.md`):

- `SongCore.cpp`: constructor/destructor + ~20 zero-Global.h-dependency
  methods, plus `SongToAta`/`AtaToSong`.
- `SongEditing.cpp`: 46 methods that only transitively touch
  `g_tracks4_8`/`g_Undo`/`g_Tracks`/`g_Instruments`/`g_TrackClipboard` (all
  confirmed cheap to construct).

Everything left is coupled to at least one of: `g_hwnd` (MessageBox/dialogs),
`g_AtariTrackerDriver`/`g_Atari`/`g_Pokey` (hardware playback simulation),
`g_SongTimer` (a real OS multimedia timer thread), or a long tail of
UI/keyboard/path globals (`g_ChannelControl`, `g_defaultSongsPath`,
`g_keyboard_*`, `g_display*`, etc.). Unlike the first two batches, this is not
a single mechanical "find the zero-coupling methods" pass - the remaining
methods need per-method judgment calls about what's safe to test as-is, what
needs a production redesign, and what should simply stay deferred. Hence this
plan, written up before touching any code.

## Key findings from triage

1. **Every remaining `g_hwnd` usage in `Song.cpp`/`IO_Song.cpp` is a
   `MessageBox` call** (verified via `grep -n "g_hwnd"` across both files) -
   there is no direct GDI/CDC drawing or window manipulation left in these
   two files. But not all `MessageBox` calls are equally risky:
   - **Guard-only** (fires only on invalid/malformed input, e.g.
     `MakeModule`'s "Internal format problem", `SongPrepareNewLine`'s "no
     empty track" errors, `LoadRMW`/`LoadRMT`'s version-mismatch errors):
     safe to test around by simply not feeding invalid input - same pattern
     already used for `CUndo::ChangeTrack`'s error branch.
   - **Always-fires on the success path** (e.g. `TrackInfo` ends with an
     unconditional `MessageBox` summarizing the result, with no way to get
     the computed string without it): genuinely blocks a test run; needs a
     deliberate decision (see Open Questions).
   - **Confirmation prompts affecting control flow** (`MB_YESNOCANCEL`/
     `MB_OKCANCEL`, e.g. `SongMaketracksduplicate`'s "are you sure",
     `Songswitch4_8`'s confirm, `FileReload`'s confirm): same problem as
     above, worse because the return value changes what the method does.
2. **`InstrInfo` is a hidden near-free win**: it takes an output parameter
   `TInstrInfo* iinfo` and only shows its `MessageBox` when `iinfo == NULL`
   (interactive/legacy call path). Called with a real, non-null `iinfo`, it
   never touches `g_hwnd` at all - already effectively "safe cluster"
   material, just missed in the earlier grep-based classification because it
   references `g_hwnd` *unconditionally in the source*, not conditionally in
   a way a naive scan would catch.
3. **`InstrChange` instantiates a real `CInstrumentChangeDlg`** (an MFC
   dialog, likely calling `.DoModal()` further down) - this is a genuine UI
   flow, not just a message box, matching the `CEffectsDlg`/`BlockEffect()`
   precedent from the last batch. Not a candidate for testing without a
   deliberate redesign.
4. **`g_SongTimer` (`CSongTimer`) is a real hazard, confirmed by reading its
   implementation**: `SetTimer()` calls the Windows multimedia API
   `timeSetEvent(...)`, which spawns an actual periodic callback on a
   separate thread that calls `CSong::TimerRoutine()` - i.e. calling `Play()`
   for real would start a live background thread mutating song/Pokey state
   during a test run. `CSongTimer` itself has the same
   uninitialized-member pattern fixed in `CTracks`/`CInstruments`/`CAtari`/
   `CSong` (no default initializers on `m_song`/`m_timerRoutine`/
   `busyInCallback`/`m_timerRoutineProcessed`), so a locally-constructed
   instance is unsafe today regardless of the timer issue.
5. **Correction to an earlier version of this plan**: `TracksAllBuildLoops`/
   `TracksAllExpandLoops` were flagged as an easy `g_Tracks`-only addition
   based on a *direct*-reference-only grep - but both unconditionally call
   `Stop()` first, which was missed by only checking direct globals (the
   same class of oversight flagged earlier in `plans/NOTES.md`: verify the
   call graph, not just direct references). `Stop()` itself, however, is a
   no-op unless `GetPlayMode() != PLAY_STOP` - and since `m_play` defaults
   to `PLAY_STOP` and no test calls `Play()` first, both methods are safe to
   test in practice, just under a documented precondition ("never call
   `Play()` on this instance first"), the same caveat pattern already used
   for `SongJump`/`SongUp`/`SongDown` in Batch 4 - not the unconditionally-
   free win originally claimed.

## Method inventory (grouped by proposed handling)

### Batch 1 - DONE (commit `91accd6`) - two more safe-cluster methods, with a documented precondition (~10 min)
- `TracksAllBuildLoops`, `TracksAllExpandLoops` (only `g_Tracks`, plus a
  call to `Stop()` that's a no-op as long as `Play()` was never called on
  the instance first - see correction #5 above) - add to `SongEditing.cpp`
  with a comment documenting the precondition.

### Batch 2 - DONE (not yet committed) - pure format encode/decode via memory buffers

**Two corrections found while implementing, before touching any code:**

1. **`MakeTuningBlock`/`DecodeTuningBlock` are dead code** - the earlier
   direct-globals grep matched text that is entirely inside a
   `/* TODO: Unused ... */` block comment spanning from just before
   `MakeTuningBlock`'s signature to just after `DecodeTuningBlock`'s closing
   brace (confirmed by locating the literal `/*`/`*/` markers in `Song.cpp`).
   Both methods' declarations in `Song.h` are separately commented out too.
   There is nothing here to test or move - removed from this batch entirely
   (not "deferred", they don't exist as compiled code).
2. **`DecodeModule` calls `SetTracks()`**, which was not caught by the
   direct-globals check because it's a call to another `CSong` method, not
   a direct global reference (the same class of oversight as Batch 1's
   `Stop()` correction). `SetTracks()` conditionally calls `ReInitSound()`
   (real `g_AtariTrackerDriver`/`g_Pokey` hazard) only if
   `tracksNum != g_tracks4_8`. Rather than requiring tests to always pass a
   matching track count, `SetTracks()` moves into the safe cluster too (it
   only needs `g_tracks4_8` directly) with `ReInitSound()` given a link-only
   no-op stub in the test project - the same treatment `Stop()` got in
   Batch 1, and it makes `SetTracks()` genuinely testable (including the
   `g_tracks4_8` mutation) rather than just avoidable.

Final batch 2 method list - no `g_hwnd` except one guard-only case, only
already-safe globals (`g_Instruments`, `g_Tracks`, `g_tracks4_8`,
`g_tuning`/`g_tuningRatios` - the latter two are `TTuningSettings`/
`TTuningRatios` structs from `TuningTypes.cpp`, already compiled into the
test project and confirmed globals-free themselves):
- `ResetTuningVariables` (`g_tuning`, `g_tuningRatios`)
- `SetTracks` (`g_tracks4_8`, plus a stubbed `ReInitSound()` call)
- `MakeModule` (`g_Instruments`, `g_Tracks`, `g_tracks4_8`, plus one
  guard-only `g_hwnd` MessageBox on malformed input - avoidable)
- `DecodeModule` (`g_Instruments`, `g_Tracks`, calls `SetTracks()` above)
- `InstrInfo` (call only with a non-null `iinfo`, per finding #2 above -
  never touches `g_hwnd` on that path)

This mirrors the `SongToAta`/`AtaToSong` work already done and is the most
natural next batch: same file-splitting pattern (new sibling file, minimal
includes), same "avoid the guard branch" precedent already established.

### Batch 3 - DONE (not yet committed) - format encode/decode via file streams

**Corrections found while scoping the implementation, before touching any code:**

1. **`LoadRMW`/`LoadTxt` both unconditionally call `ClearSong(8)` first**,
   missed by the direct-globals check (a method call, not a direct
   reference - the same recurring class of oversight from Batches 1-2).
   Reading `ClearSong`'s body confirms it's genuinely the large, separate
   decision the plan already flagged: besides already-safe calls (`Stop()`,
   `SetTracks()`, `PlayPressedTonesInit()`, `ClearBookmark()`,
   `g_TrackClipboard.Clear()`, `g_Tracks.InitTracks()`,
   `g_Instruments.InitInstruments()`, `g_Undo.Init()`), it also calls
   `SetEditMode()` (unexamined), `CMainFrame* mf = (CMainFrame*)AfxGetMainWnd();`
   (a real MFC application-framework call needing a running `CWinApp` -
   genuinely different in kind from a stubbable global), `g_Atari.Init(...)`,
   and `g_AtariTrackerDriver->Init()` where `g_AtariTrackerDriver` is a
   pointer never even instantiated in the test project. **`LoadRMW`/`LoadTxt`
   are dropped from this batch** and stay blocked on `ClearSong`'s own
   dedicated decision (see below).
2. **`ExportV2` is a large dispatcher**, not a simple encode: beyond calling
   the already-safe `MakeModule()`, it switches over `iotype` and delegates
   to `CRmtExporter`, `CASMFileExporter`, and several `CSongExporter`
   methods (SAP-R, LZSS, SAP+LZSS, XEX+LZSS, WAV) via `CSongContainer`/
   `CSongExport` wrapper objects - none of which have been scoped for
   coupling. **Dropped from this batch**, needs its own separate triage
   (matching `ClearSong`'s treatment) rather than folding into "format
   encode/decode via streams".
3. **`LoadRMT` doesn't call `ClearSong`** and turns out fully testable: it
   uses `CAtariIO::LoadBinaryBlock()` (in `AtariIO.cpp`, confirmed **zero**
   global references, safe to link directly with no split needed) plus the
   already-safe `DecodeModule()`. Its 3 `MessageBox` calls are: two
   guard-only errors (avoidable with valid test data) and one "Info" dialog
   that only fires for a *stripped* RMT file (missing the second, optional
   name-data block) - avoidable simply by testing with a complete two-block
   RMT file, without needing `TrackInfo`'s refactor treatment.
4. **`IO_Instruments.cpp` needs no split at all** (matching the
   already-established "some files need no split - check coupling first"
   pattern): its 5 methods (`SaveAll`/`LoadAll`/`SaveInstrument`/
   `LoadInstrument`/`Update`) have exactly one real global reference
   (`g_Atari`, already safe) - the `#include "Global.h"`/`"resource.h"` were
   dead includes, confirmed removable via a clean production rebuild. This
   also means `CInstruments::Update()` can lose its no-op stub from Batch 2
   and get real behavior in tests.

Final batch 3 scope - `SaveRMW`, `SaveTxt`, `LoadRMT`, plus linking the now
`Global.h`-free `IO_Instruments.cpp` directly (unblocking `SaveRMW`'s/
`LoadRMW`'s calls to `g_Instruments.SaveAll`/`LoadAll`, and giving
`Update()` real behavior instead of a stub).

**`IDS_RMT_VERSION` decision** (already resolved - see Decisions above):
implement the compile-time constant now, replacing all 6 `LoadString` call
sites (`SaveRMW`/`LoadRMW` in `IO_Song.cpp`, plus `Rmt.cpp` and `RmtView.cpp`
×2), unblocking `SaveRMW`'s test.

**`LoadRMW`/`LoadTxt` - DONE**, once `ClearSong` was resolved (see its own
entry below) they turned out to need almost no further work: `LoadRMW`'s
last remaining hazard was the same `LoadString(IDS_RMT_VERSION)` call
already fixed for `SaveRMW`/`SaveTxt` elsewhere, and `LoadTxt` had no hazard
of its own at all beyond `ClearSong()`. Both moved to `SongEditing.cpp`,
widened to `std::istream&` (matching `LoadRMT`'s precedent), with the
now-dead `DEFINE_MAINPARAMS`/`RMWMAINPARAMSCOUNT` macro duplicate removed
from `IO_Song.cpp`. **Found a genuine pre-existing bug while writing
`LoadTxt`'s round-trip test**: `SaveTxt()` writes a blank "gap" line before
each `[SEGMENT]` marker, but `LoadTxt()`'s segment-boundary scan (byte-by-
byte, checking for `'['`) reads that gap's `'\n'` first and never recognizes
the following `'['` as a boundary - so `[SONG]` (and likely
`[INSTRUMENT]`/`[TRACK]`) is silently skipped on load. Net effect: loading a
`.txt` file RMT itself just saved does not restore the song data. Per this
effort's "characterize current behavior first" scope, this was tested as-is
(documenting both what parses correctly - the `[MODULE]` header - and what
doesn't) rather than fixed; fixing it is a real production behavior change
that needs its own explicit decision. Tracked upstream as
[raster-atari-org/RASTER-Music-Tracker#21](https://github.com/raster-atari-org/RASTER-Music-Tracker/issues/21).
See `plans/NOTES.md` for the full byte-level trace.

### Batch 4 - DONE (not yet committed) - heavier editing methods

**Corrections found while scoping the implementation:**

1. **`SongInsertCopyOrCloneOfSongLines` and `TracksOrderChange` instantiate
   real MFC dialogs** (`CInsertCopyOrCloneOfSongLinesDlg`/
   `CSongTracksOrderDlg`, both calling `.DoModal()`) - not just a guard-only
   `MessageBox` as originally assumed. Same hazard class as `InstrChange`'s
   `CInstrumentChangeDlg` - both dropped from this batch, joining
   `InstrChange`/`BlockEffect` in the "real dialog" category needing its own
   deliberate decision (see below), not characterizable as-is.
2. **`PlayPressedTones`/`InstrPaste` turned out safe**, not hazardous:
   reading `CAtariTrackerDriver`'s actual methods found `CAtari::JSR()`
   just delegates to `C6502::JSR()` - already a link-only no-op stub in the
   test project. `CAtariTrackerDriver`'s constructor is just a pointer
   store. `AtariTrackerDriver.cpp` was split the same way as other coupled
   files: the safe methods (`SetTrackNoteInstrumentVolume`/
   `SetTrackVolume`/`InstrumentTurnOff`/`GetAtari`/constructor/`GetByteAt`)
   moved to a new `AtariTrackerDriverCore.cpp`; `LoadRMTRoutines`/`Init`/
   `Play`/`SetPokey`/`Silence` stay behind (real driver-binary loading,
   `Global.h`'s `IsSpecialProveMode()`).
3. `TracksAllExpandLoops` was already done in Batch 1 (a stale leftover in
   this list from before that correction).

Final batch 4 scope, all confirmed safe: `SongJump`, `SongUp`, `SongDown`,
`SongSubsongPrev`, `SongSubsongNext` (conditionally call `Stop()`/`Play()`
only when `m_play && m_followplay`, never taken since nothing calls the real
`Play()` first), `TrackUp`, `TrackDown`, `SetUECursor` (just
`g_active_ti`/`g_activepart`, both already real globals from Batch 3's
`DEFINE_MAINPARAMS` work), `SongPrepareNewLine`, `SongPutnewemptyunusedtrack`
(error-only `g_hwnd`), `PlayPressedTones`, `InstrPaste` (see correction #2).

`SongMaketracksduplicate` and `Songswitch4_8` confirmed to only have
confirmation-prompt `MessageBox`es (no hidden dialogs) - stayed deferred at
the time per the "confirm prompts stay deferred" decision below, until
`plans/MESSAGEBOX_REFACTOR_PLAN.md`'s `SendQuestionMessage()` (with its
test-injectable answer) made the confirm prompt itself safe to trigger in
tests. **Both moved to `SongEditing.cpp` and tested - DONE** (see
`plans/NOTES.md`); the "defer both entirely" decision (#3 below) no longer
applies to these two specifically.

### Batch 5 - methods with an unconditional "success" dialog or confirm prompt (needs a decision)
- `InstrChange`, `SongInsertCopyOrCloneOfSongLines`, `TracksOrderChange` -
  **DONE**: re-analyzed in detail (per the same "verify before deferring"
  lesson `ClearSong` taught) rather than accepting the original "likely just
  defer all four" call. All three turned out to have the same shape as
  `InstrInfo`/`TrackInfo`: the dialog only supplies a fixed set of input
  parameters up front, and the actual mutation logic runs entirely
  independently of the dialog object. Each was split into a thin dialog
  wrapper (stays in `Song.cpp`) plus a testable `*Apply()` core (moved to
  `SongEditing.cpp`) - see `plans/NOTES.md` for the full per-method
  breakdown. `BlockEffect` is the one exception: confirmed to have no
  extractable logic at all (see its own entry below) - the original "defer"
  call stands for it specifically.
- `TrackInfo` - **DONE**: refactored to the same dual-mode (output-parameter
  vs. `MessageBox`) design `InstrInfo` already has, moved into
  `SongEditing.cpp`, 2 new tests, 214 tests passing (see
  `plans/NOTES.md` for the full writeup)
- `SongMaketracksduplicate`, `Songswitch4_8` - **DONE** (confirmation
  prompts only, no hidden dialogs - confirmed while scoping Batch 4;
  unlocked and moved to `SongEditing.cpp` once
  `plans/MESSAGEBOX_REFACTOR_PLAN.md`'s test-injectable
  `SendQuestionMessage()` made the confirm prompt itself safe to trigger)
- `FileReload` and the rest of the `FileXxx` family (see Batch 7)

### Batch 6 - DONE (not yet committed) - live playback / timer

**This batch's own "recommend defer entirely" turned out too conservative -
corrected by re-reading `SongTimer.cpp` line by line instead of trusting the
earlier hazard note.** `WaitForTimerRoutineProcessed()`/`StopTimer()`/
`KillTimer()` are all guarded by `if (m_timerRoutine)`, and the *only*
method that ever sets `m_timerRoutine` away from its default `0` is
`SetTimer()` (the one that calls the real, hazardous `timeSetEvent`). As
long as nothing calls `SetTimer()`/`CSong::ChangeTimer()`, every other
`CSongTimer` method is a provably safe no-op. `g_Atari.Init()` (called by
`Play()`'s `PLAY_SONG` case) delegates to the already-stubbed no-op
`C6502::Init()`, same pattern as `g_AtariTrackerDriver` in Batch 4.

Moved (confirmed safe): `Play`, `Stop`, `PlayBeat`, `PlayVBI`. Added the
planned `CSongTimer` default-member-initializer fix (safe, zero production-
behavior-change) - it's also *why* a real `CSongTimer g_SongTimer;` global
is safe to add to the test project (matches what the global already got for
free from static zero-init). Linking the whole `SongTimer.cpp` doesn't work
for the test binary (needs `winmm.lib` and the deliberately-unlinked
`CSong::TimerRoutine()`) - added a link-only stub for just
`WaitForTimerRoutineProcessed()` instead, preserving the real
`if (m_timerRoutine)` guard rather than stubbing it as an unconditional
no-op. Also discovered `TracksEdit.cpp` (already split from `Tracks.cpp` in
an earlier session) wasn't yet linked into the test project - needed by
`PlayVBI`'s quantization branch, and already safe (`g_Undo`/
`g_respectvolume` only).

**Still deferred, confirmed genuinely hazardous**: `TimerRoutine()` (besides
calling `ChangeTimer()`, also calls `g_Pokey.RenderSound1_50()` -
`CXPokey`/`PokeyRenderer.h` holds a real `LPDIRECTSOUNDBUFFER`, a
categorically different, not-yet-investigated audio-hardware coupling),
`ChangeTimer()`/`StopTimer()` (thin `g_SongTimer.SetTimer()`/`.StopTimer()`
wrappers - no independent value once `TimerRoutine()` stays deferred),
`ReInitSound()` (already stubbed, real `g_Pokey`/hardware init). `SetTracks`
was already moved in Batch 2.

Given the real hang risk if any of this reasoning were wrong, every step was
verified incrementally with an explicit timeout: the existing suite first
(no new tests), then `Stop()` alone, then `Play`/`PlayBeat`/`PlayVBI`
together, then the full suite - no hangs at any point.

### Batch 7 - file-dialog orchestration - CONFIRMED deferred indefinitely (re-verified)
`FileReload`, `FileOpen`, `FileSave`, `FileSaveAs`, `FileNew`, `FileImport`,
`FileExportAs`, `FileInstrumentSave`, `FileInstrumentLoad`, `FileTrackSave`,
`FileTrackLoad`. These are real `CFileDialog`/confirm-prompt/path-state
orchestration methods - the actual value (format encode/decode) is already
covered by Batches 2-3 testing the underlying `MakeModule`/`SaveRMW`/
`LoadRMW`/etc. directly via buffers/streams. Recommend never testing these
wrapper methods directly; if UI-level coverage is ever wanted, it needs a
real UI-testing approach, out of scope for this characterization effort.

**Re-verified by re-reading all 11 methods in full** (per the "verify before
trusting old triage" lesson - this was originally a bulk assessment, not a
per-method read like the other batches got). Confirmed there is no
`InstrChange`/`TracksOrderChange`-style "thin dialog wrapper + testable
Apply() core" split available here: those methods' dialogs only gather a
fixed-shape parameter struct, with the mutation logic running identically
regardless of *which* values were chosen. Here, the dialog's result (the
chosen file path, or - for `FileOpen`/`FileReload` - whether to proceed at
all) is not a parameter to route around, it's the entire reason the method
exists; the "load/save with a known path" logic it dispatches to
(`LoadRMT`/`LoadTxt`/`LoadRMW`/`SaveTxt`/`SaveRMW`/`ExportV2`/
`g_Instruments.SaveInstrument`/`LoadInstrument`/`g_Tracks.SaveTrack`) is
already exactly what Batches 2-3 and the `ExportV2` triage test directly.
Every one of the 11 methods unconditionally constructs a real `CFileDialog`
(`FileNew` uses `CFileNewDlg`) and unconditionally calls `.DoModal()` (except
`FileOpen`/`FileReload`, which skip it only when a filename is already
known - but even then still construct the dialog object). Same hazard class
already established as blocking `InstrChange`/`SongInsertCopyOrCloneOfSongLines`/
`TracksOrderChange` before their split was found; no such split exists here.
Original call stands, now on a verified rather than assumed basis.

**This closes out the deferred-hazard backlog for `Song.cpp`/`IO_Song.cpp`/
`ExportV2`**: every remaining category (`BlockEffect`; the confirmation-only
`SongMaketracksduplicate`/`Songswitch4_8`; `TimerRoutine`/`ChangeTimer`/
`StopTimer`/`ReInitSound`; `ExportWAV`/`ExportLZSS`/`ExportCompactLZSS`; and
now this `FileXxx` family) has been individually investigated and has a
confirmed, non-speculative reason to stay deferred. There is no more
"re-analyze and possibly unlock" work left in this plan.

### `ClearSong` - DONE
Originally flagged as its own large, deferred category (~18 globals plus a
real `AfxGetMainWnd()`/`CMainFrame` call and `g_AtariTrackerDriver->Init()`
on a pointer assumed never instantiated in the test project). Re-triaged
after Batches 4/6 had already made most of that tail real/safe:
`g_AtariTrackerDriver` is now a real instantiated object (`AtariTrackerDriverCore.cpp`),
`g_Atari.Init()`/`g_AtariTrackerDriver->Init()` both delegate to
already-stubbed-safe no-ops, and `g_Tracks.InitTracks()`/
`g_Instruments.InitInstruments()`/`g_Undo.Init()`/`g_TrackClipboard.Clear()`
are all real, cheap, own-state resets. The **only** genuine remaining
hazard was the `AfxGetMainWnd()`/`CMainFrame` UI-sync call - extracted into
its own `CSong::SyncSkipLinesAfterNoteInsertComboBox()` (stays in
`Song.cpp`, link-only no-op stub in tests), with the rest of `ClearSong()`
moved to `SongEditing.cpp` unchanged. ~7 trivial new global stubs
(`g_rmtroutine`, `g_rmtstripped_sfx`/`_gvf`, `g_rmtmsxtext`,
`g_PrefixForAllAsmLabels`, `g_changes`, `g_SkipLinesAfterNoteInsert`) and a
one-line `SetEditMode()` stub were added to the test project. 2 new tests,
216 tests passing (see `plans/NOTES.md` for the full writeup). `LoadRMW`/
`LoadTxt` (Batch 3), unblocked by this, are now DONE too - see Batch 3's
entry above.

### `ExportV2` - triage DONE, see `plans/EXPORTV2_PLAN.md`
A dispatcher, not a simple encode: beyond the already-safe `MakeModule()`,
it switches over `iotype` and delegates to `CRmtExporter`,
`CASMFileExporter`, and several `CSongExporter` methods (SAP-R, LZSS,
SAP+LZSS, XEX+LZSS, WAV) via `CSongContainer`/`CSongExport` wrapper objects.
Full triage written up in its own plan doc: the RMT/ASM export family
(`CRmtExporter`, `CASMFileExporter`) turned out to be the same
already-solved "real dialog cluster" shape (or, for `BuildRelocatableAsm`,
already a pure function needing no split at all); the SAP/LZSS/WAV/XEX
family is gated behind the real Atari audio-rendering pipeline
(`CSong::DumpSongToPokeyStream()`) flagged but not investigated back in
Batch 6, and stays deferred pending its own dedicated investigation.

## Suggested execution order

1. Batch 1 (trivial, folds into existing `SongEditing.cpp`)
2. Batch 2 (format encode/decode via buffers - same shape as prior work)
3. Batch 3 (format encode/decode via streams - same shape, minus the
   `LoadRMT` info-dialog question)
4. Batch 4 (heavier editing methods, one sub-group at a time given the
   number of individual checks needed)
5. `TrackInfo` refactor - **DONE**
6. Batch 6 and 7: defer unless priorities change; `CSongTimer`'s
   uninitialized-member fix could be done opportunistically regardless

## Decisions (resolved)

1. **`SaveRMW`/`SaveTxt`'s `LoadString` blocker**: `IDS_RMT_VERSION` becomes
   a compile-time constant instead of a runtime resource lookup (e.g.
   `constexpr const char* RMT_VERSION_STRING = "RASTER Music Tracker 1.35";`
   in a small new header, with a comment noting it mirrors `Rmt.rc`'s
   `IDS_RMT_VERSION` string resource and must be kept in sync with it). This
   replaces **all 6** `LoadString(IDS_RMT_VERSION)` call sites (`IO_Song.cpp`
   ×2 - `SaveRMW`/`LoadRMW`, `Rmt.cpp` ×1, `RmtView.cpp` ×2), not just the
   two needed for testing, to avoid two sources of truth for the version
   string. This is a small production change (not just test scaffolding)
   and should be its own clearly-labeled step within whichever batch touches
   `SaveRMW`/`LoadRMW`.
2. **`TrackInfo` refactor**: do it. Split the pure string-building logic out
   of `TrackInfo` into a testable form (output parameter or return value,
   mirroring `InstrInfo`'s existing `iinfo`-parameter design), leaving
   `MessageBox` as a thin wrapper around it.
3. **Confirmation-prompt methods** (`SongMaketracksduplicate`,
   `Songswitch4_8`): deferred at the time - not worth extracting the
   post-confirmation logic while the confirm prompt itself was a real,
   unavoidable `MessageBox`. **Superseded**: once
   `plans/MESSAGEBOX_REFACTOR_PLAN.md` gave `SendQuestionMessage()` a
   test-injectable answer, the confirm prompt itself became safe to
   trigger, so no extraction was even needed - both moved to
   `SongEditing.cpp` verbatim and tested on every branch (confirm and
   cancel). See `plans/NOTES.md`.
4. **Batch pacing**: continue one batch at a time, each scoped and approved
   before implementation, verified via full rebuild, then a commit decision
   - the same cadence used for every batch so far.

## Suggested execution order

1. Batch 1 (trivial, folds into existing `SongEditing.cpp`)
2. Batch 2 (format encode/decode via buffers - same shape as prior work)
3. Batch 3 (format encode/decode via streams; includes the `IDS_RMT_VERSION`
   production change above for `SaveRMW`/`LoadRMW`; `LoadRMT`'s unconditional
   "Info" summary dialog stays deferred like `TrackInfo` was before its
   refactor - not revisited here since it wasn't part of the resolved
   decisions above)
4. Batch 4 (heavier editing methods, one sub-group at a time given the
   number of individual checks needed)
5. `TrackInfo`'s refactor + tests - **DONE**
6. Batch 6 and 7: defer unless priorities change; `CSongTimer`'s
   uninitialized-member fix could be done opportunistically regardless
