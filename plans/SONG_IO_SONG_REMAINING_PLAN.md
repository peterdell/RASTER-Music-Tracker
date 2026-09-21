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

### Batch 3 - format encode/decode via file streams (promising, same shape as Batch 2)
- `LoadTxt` (`g_Instruments`, `g_Tracks`, `g_tracks4_8` - no `g_hwnd` at all)
- `LoadRMW` (`g_hwnd` only on a version-mismatch guard - avoidable)
- `LoadRMT` (`g_hwnd` used both for a guard-only error AND an unconditional
  "Info" summary dialog at the very end - **has the same "always fires on
  success" problem as `TrackInfo`**, needs the same decision)
- `ExportV2` (static method; `g_Atari`, `g_Pokey` - needs checking exactly
  what it does with them, since this generates actual Pokey audio dump data,
  likely more involved than a straight encode)
- `SaveRMW` / `SaveTxt` (already known-safe except for `CString::LoadString`
  - see below)

**Known blocker, needs a decision**: `SaveRMW`/`SaveTxt` (deferred from the
last batch) call `CString::LoadString(IDS_RMT_VERSION)`, which needs the
app's compiled `.rc` resources - not linked into the test binary. Options:
(a) leave deferred permanently, (b) find/link just the string table
resource into the test binary, (c) accept a hardcoded version string via a
stub. Not blocking Batch 3's other methods.

### Batch 4 - heavier editing methods with guard-only `g_hwnd` (needs care, case by case)
These use `g_hwnd` only for error/confirmation dialogs that a test can avoid
by using valid preconditions - but each needs its own quick read to confirm
the `MessageBox` really is skippable and to check for other coupling
(`g_AtariTrackerDriver`, `g_TrackClipboard`, etc. - already safe/cheap):
- `SongJump`, `SongUp`, `SongDown`, `SongSubsongPrev`, `SongSubsongNext` -
  conditionally call `Stop()`/`Play()` only when `m_play && m_followplay`;
  testable in the "not playing" branch (same technique already used for
  `SongPlayNextLine`).
- `TrackUp`, `TrackDown`
- `SongInsertCopyOrCloneOfSongLines`, `SongPrepareNewLine`,
  `SongPutnewemptyunusedtrack` (error-only `g_hwnd`)
- `SongMaketracksduplicate` (has a **confirmation prompt**, not just an
  error - needs the same decision as Batch 5's confirm-prompt methods, or
  could be tested only via the "not asked" code paths if any exist)
- `TracksOrderChange`, `Songswitch4_8` (also has a confirmation prompt),
  `TracksAllExpandLoops`
- `SetUECursor` (small; `g_active_ti`/`g_activepart` - check these are
  simple globals, likely trivial to stub)
- `PlayPressedTones`, `InstrPaste` (touch `g_AtariTrackerDriver` directly -
  need to check if that's a guard-only or load-bearing dependency; may
  belong in Batch 6 instead)

### Batch 5 - methods with an unconditional "success" dialog or confirm prompt (needs a decision)
- `InstrChange` (real `CInstrumentChangeDlg` - likely just defer)
- `TrackInfo` (unconditional info `MessageBox`, no output-parameter escape
  hatch like `InstrInfo` has)
- `SongMaketracksduplicate`, `Songswitch4_8` (confirmation prompts - see
  Batch 4)
- `FileReload` and the rest of the `FileXxx` family (see Batch 7)

**Open question for the user**: for `TrackInfo` specifically, is it worth a
small production refactor (split the string-building into a pure helper,
leave a thin `MessageBox`-only wrapper) to make it testable, the same way
`InstrInfo` already happens to be split? This is a real (small) production
code change, not just a mechanical move, so it needs an explicit go-ahead
before doing it.

### Batch 6 - live playback / timer (high hazard, likely defer or needs redesign)
- `Play`, `Stop`, `PlayBeat`, `PlayVBI`, `TimerRoutine`, `ReInitSound`,
  `StopTimer`, `ChangeTimer`, `SetTracks` (calls `ReInitSound`)
- Confirmed hazard: `CSongTimer::SetTimer()` starts a real OS multimedia
  timer thread (`timeSetEvent`) that calls back into `CSong::TimerRoutine()`
  asynchronously - must never be triggered in a test process.
- `CSongTimer` also has the same missing-default-initializer pattern as
  `CTracks`/`CInstruments`/`CAtari`/`CSong` before those were fixed - worth
  fixing on its own merits (safe, zero production-behavior-change) even if
  this whole area stays otherwise deferred.
- **Recommendation: defer this entire batch** unless there's real appetite
  for redesigning `CSongTimer` behind a mockable interface - matches the
  plan's own standing note that timer-driven code needs a "cleanup" pass
  before it's testable at all, not incremental extraction.

### Batch 7 - file-dialog orchestration (recommend deferring indefinitely)
`FileReload`, `FileOpen`, `FileSave`, `FileSaveAs`, `FileNew`, `FileImport`,
`FileExportAs`, `FileInstrumentSave`, `FileInstrumentLoad`, `FileTrackSave`,
`FileTrackLoad`. These are real `CFileDialog`/confirm-prompt/path-state
orchestration methods - the actual value (format encode/decode) is already
covered by Batches 2-3 testing the underlying `MakeModule`/`SaveRMW`/
`LoadRMW`/etc. directly via buffers/streams. Recommend never testing these
wrapper methods directly; if UI-level coverage is ever wanted, it needs a
real UI-testing approach, out of scope for this characterization effort.

### `ClearSong` (own category - large but maybe worth it later)
Touches ~18 globals (`g_Atari`, `g_AtariTrackerDriver`, `g_Instruments`,
`g_TrackClipboard`, `g_Tracks`, `g_Undo`, `g_tracks4_8`, plus several
UI/state flags like `g_activepart`, `g_changes`, `g_rmtroutine`, etc.). It's
called from many of the `FileXxx`/`LoadXxx` methods, so testing it would
require stubbing a long tail of globals. Given the size, propose leaving it
for its own dedicated future decision rather than folding into any batch
above.

## Suggested execution order

1. Batch 1 (trivial, folds into existing `SongEditing.cpp`)
2. Batch 2 (format encode/decode via buffers - same shape as prior work)
3. Batch 3 (format encode/decode via streams - same shape, minus the
   `LoadRMT` info-dialog question)
4. Batch 4 (heavier editing methods, one sub-group at a time given the
   number of individual checks needed)
5. Decide Batch 5's open question (`TrackInfo` refactor - yes/no) before
   attempting it
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
   `Songswitch4_8`): defer both entirely. Not worth extracting the
   post-confirmation logic at this time.
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
5. `TrackInfo`'s refactor + tests (now unblocked per the decision above)
6. Batch 6 and 7: defer unless priorities change; `CSongTimer`'s
   uninitialized-member fix could be done opportunistically regardless
