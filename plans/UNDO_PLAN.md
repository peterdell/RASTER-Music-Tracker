# Plan: `Undo.cpp` (`CUndo`) investigation

## Context

Flagged by `plans/BROADER_SURVEY_PLAN.md` (Category B, priority #2) and
surfaced again while auditing dual-mode candidates
(`plans/DUAL_MODE_PATTERN_PLAN.md`): `CUndo` is real, significant,
currently-uncharacterized production functionality (undo/redo), stubbed
out as no-ops in the test binary (`test/UndoStub.cpp`) since it was judged
"squarely downstream of the `CSong` split work" - a note written before
that split existed. Since the `CSong` split is now done and most of its
globals are already real and test-safe, this plan re-investigates from
scratch, per this effort's "verify before trusting old triage" discipline.

`Undo.h`/`Undo.cpp` declare/implement `CUndo`'s 16 methods in full (no
tiering needed - the whole class is ~500 lines and, per the findings
below, none of it needs to stay behind in a separate hazardous file).

## Key findings from investigation

1. **`test/UndoStub.cpp` already proves 8 of 16 methods are real and
   safe**: `CUndo()`/`~CUndo()`, `Init`, `Clear`, `DeleteEvent`,
   `GetUndoSteps`, `GetRedoSteps`, `DropLast`, `Separator`, `PosIsEqual`
   are copied there **verbatim** from `Undo.cpp` (confirmed identical by
   diffing the two files) because they only touch `CUndo`'s own
   `m_uar`/`m_head`/`m_tail`/`m_undosteps`/`m_redosteps` state - no
   globals at all. They're just never given their own direct
   characterization tests today (only exercised incidentally through
   other classes' tests).
2. **The remaining 8 methods' real dependencies are now all already real
   and test-safe** (this is the change from the stale earlier note):
   `g_Song`, `g_Tracks`, `g_Instruments`, `g_TrackClipboard`,
   `g_activepart`, `g_changes` are all real globals, already reset before
   every test in `SongEditingTest::SetUp()`
   (`test/SongEditingTests.cpp:59-87`). Specifically:
   - `ChangeTrack` only reads `g_Tracks.GetTrack()` (already safe).
   - `ChangeSong` only reads `g_Song.SongGetTrack`/`SongGetGo`/`GetSong`/
     `GetSongGo` (already safe, already exercised by existing
     `SongEditing.cpp` tests).
   - `ChangeInstrument`/`PerformEvent`'s instrument cases call
     `g_Instruments.Update()`, which has had real (non-stub) behavior
     since `plans/SONG_IO_SONG_REMAINING_PLAN.md` Batch 3.
   - `ChangeInfo`/`PerformEvent`'s info case call `g_Song.GetSongInfoPars`/
     `SetSongInfoPars`, already implemented in `SongEditing.cpp` and
     exercised by dozens of existing tests.
   - `InsertEvent`/`PerformEvent` call `g_Song.GetUECursor`/`SetUECursor`/
     `UECursorIsEqual`, all three in `SongCore.cpp`/`SongEditing.cpp`
     already (safe).
   - `Undo()`/`Redo()` unconditionally call `g_Song.Stop()` - already
     established safe: `Stop()` is a no-op unless
     `GetPlayMode() != PLAY_STOP`, `m_play` defaults to `PLAY_STOP`
     (fixed default-member-initializer, Batch 4), and nothing in the test
     suite calls the real `Play()` first. Same documented-precondition
     pattern already used for `SongJump`/`SongUp`/`SongDown`/
     `TracksAllBuildLoops`.
3. **One real, not-yet-exercised hazard found**: `InsertEvent()` calls
   `g_Song.SetRMTTitle()`, but only the first time a change is made
   (`if (!g_changes) { g_changes = 1; g_Song.SetRMTTitle(); }`).
   `SetRMTTitle()` (`GUI_Song.cpp`, confirmed Category A "real UI" in
   `plans/BROADER_SURVEY_PLAN.md`) unconditionally calls
   `AfxGetApp()->GetMainWnd()` before its one internal null-check (the
   check guards `GetMainWnd()`'s *result*, not `AfxGetApp()` itself).
   `RmtTests.exe` never constructs a `CWinApp`-derived object, so
   `AfxGetApp()` returns MFC's default-null `afxCurrentWinApp` - calling
   `->GetMainWnd()` on that is a null-pointer dereference, a real crash
   risk. **Not verified empirically** (deliberately, given the crash risk
   and this effort's established caution around exactly this class of
   hazard - see `plans/SONG_IO_SONG_REMAINING_PLAN.md` Batch 6's
   timeout-guarded verification of `CSongTimer`). Avoided instead the same
   way `Stop()`'s `m_play` precondition is: tests always set
   `g_changes = 1` before calling any `Change*()` method, which skips this
   branch entirely without needing a link-only stub for `SetRMTTitle()`
   itself. This should be stated as a documented precondition on
   `ChangeTrack`/`ChangeSong`/`ChangeInstrument`/`ChangeInfo`'s test
   coverage, matching the project's existing precondition-documentation
   style.
4. **Genuine bug found: `new`/`delete[]` mismatch on `TUndoEvent::data`**.
   `DeleteEvent()` always does `delete[] ue->data;` regardless of
   `UndoType`, but `data` (a `void*`, so the type is erased) is allocated
   two different ways depending on the type:
   - Array `new` (correctly matches `delete[]`): `UETYPE_NOTEINSTRVOL`,
     `UETYPE_NOTEINSTRVOLSPEED`, `UETYPE_SPEED`, `UETYPE_LENGO`,
     `UETYPE_SONGTRACK`, `UETYPE_SONGGO` (all `new int[n]`).
   - **Scalar `new` freed with `delete[]` - undefined behavior**:
     `UETYPE_TRACKDATA` (`new TTrack`), `UETYPE_TRACKSALL`
     (`new TTracksAll`), `UETYPE_SONGDATA` (`new TSong`), `UETYPE_INSTRDATA`
     (`new TInstrument`), `UETYPE_INSTRSALL` (`new TInstrumentsAll`).
   Same bug class already found and fixed in `CTracks`/`CInstruments`
   (`plans/NOTES.md`: "`delete` instead of `delete[]`" for `CTracks`,
   "same `delete`/`delete[]` mismatch" for `CInstruments`) - all trivial
   structs with no destructors, so this has likely never visibly corrupted
   anything in practice, but it's still real UB, not just a theoretical
   concern. Per this effort's established policy ("real, safe fixes -
   uninitialized members, `delete`/`delete[]`, includes - get fixed
   immediately; hazards needing a real redesign get characterized and
   avoided instead"), **this should be fixed outright** as part of this
   batch, not just documented: give `TUndoEvent` a small `bool isArray`
   (or a matching `enum`) flag set at allocation time, and have
   `DeleteEvent()` branch on it - or simpler, since only `int*` arrays vs.
   single-struct pointers are ever stored, track that directly.

## Method inventory

| Method | Status today | After this batch |
|---|---|---|
| `CUndo()`/`~CUndo()` | real (copied verbatim into `UndoStub.cpp`) | real, tested directly |
| `Init`/`Clear`/`DeleteEvent`/`GetUndoSteps`/`GetRedoSteps`/`DropLast`/`Separator`/`PosIsEqual` | real (copied verbatim into `UndoStub.cpp`) | real, tested directly; `DeleteEvent` also gets the `new`/`delete[]` fix |
| `ChangeTrack` | no-op stub | real, tested (all 5 `UndoType` branches + the `BAD!` guard, avoided) |
| `ChangeSong` | no-op stub | real, tested (all 3 branches + guard) |
| `ChangeInstrument` | no-op stub | real, tested (both branches + guard) |
| `ChangeInfo` | no-op stub | real, tested (1 branch + guard) |
| `InsertEvent` | no-op stub | real, tested (via the `Change*` methods above; `g_changes = 1` precondition documented) |
| `PerformEvent` | no-op stub | real, tested (via `Undo`/`Redo`, exercising all `UndoType` branches) |
| `Undo`/`Redo` | no-op stub (`return FALSE`) | real, tested (empty-history guard, single-step, multi-step via `separator`, redo-after-undo) |

## Proposed batching

- **Batch 1** (trivial, no code change beyond a new test file): add
  `test/UndoTests.cpp` characterizing the 8 already-real bookkeeping
  methods directly (`Init`/`Clear`/`DeleteEvent`/`GetUndoSteps`/
  `GetRedoSteps`/`DropLast`/`Separator`/`PosIsEqual`) - they're already
  linked and correct, just never asserted on directly.
- **Batch 2**: fix the `new`/`delete[]` mismatch in `Undo.cpp`, remove
  `test/UndoStub.cpp`'s 8 no-op stub bodies (`InsertEvent`/`PerformEvent`/
  `Undo`/`Redo`/`ChangeTrack`/`ChangeSong`/`ChangeInstrument`/
  `ChangeInfo`) and link the real `Undo.cpp` directly instead (matching
  the "some files need no split at all" precedent already established for
  `IO_Instruments.cpp`/`AtariTrackerDriverCore.cpp` - `Undo.cpp` needs no
  splitting since every one of its methods turns out safe). Add
  characterization tests to `UndoTests.cpp` for `ChangeTrack`/
  `ChangeSong`/`ChangeInstrument`/`ChangeInfo`/`Undo`/`Redo` covering
  every `UndoType` branch plus the empty-history/guard-only paths, always
  with `g_changes = 1` pre-set per finding #3.
- Existing tests that currently rely on `g_Undo`'s no-op behavior (per
  `SongEditingTests.cpp`'s own header comment: "these tests below
  characterize the edit's own visible effect, not the undo recording")
  need re-checking once `g_Undo` goes real - none of `SongEditing.cpp`'s
  methods change behavior, but some assertions may currently assume no
  undo-event was recorded and would need updating to match reality (or
  may simply still pass, since most just check `m_song`/`g_Tracks` state,
  not `g_Undo`'s internal history).

## No open design decision found

Unlike `plans/DUAL_MODE_PATTERN_PLAN.md`'s audit, this investigation
didn't surface a genuine decision point - every finding above resolves
cleanly via patterns this effort has already established and approved
(documented preconditions, fixing real `delete`/`delete[]` bugs outright,
linking a file directly once its coupling turns out safe). Ready to
implement as the two batches above once approved.

## Implementation - DONE

Implemented as a single combined pass rather than two strictly separate
batches, since Batch 1 alone (the 8 already-real bookkeeping methods)
would have had almost nothing to assert on without also having real
`Change*()`/`Undo()`/`Redo()` behavior to populate history with - the two
batches turned out to have no independent value split apart.

- **`Undo.h`**: `TUndoEvent` gets a new `bool dataIsArray = false;` member
  (the fix for finding #4's `new`/`delete[]` mismatch).
- **`Undo.cpp`**: `DeleteEvent()` now branches on `dataIsArray` (`delete[]`
  vs `delete`); every array-`new` allocation site (`UETYPE_NOTEINSTRVOL`/
  `NOTEINSTRVOLSPEED`/`SPEED`/`LENGO`/`SONGTRACK`/`SONGGO`) sets
  `ue->dataIsArray = true;`; the scalar-`new` sites (`TRACKDATA`/
  `TRACKSALL`/`SONGDATA`/`INSTRDATA`/`INSTRSALL`/`INFODATA`) correctly
  leave it at the struct's `false` default.
- **`test/RmtTests.vcxproj`**: added `..\Undo.cpp` (the real file) to the
  test project.
- **`test/UndoStub.cpp`**: trimmed to just `CUndo g_Undo;` (the global
  storage Global.cpp normally provides) - all 16 methods are real now, no
  stub bodies needed.
- **`test/SongEditingStub.cpp`**: one new link-only no-op stub,
  `void CSong::SetRMTTitle() {}`, needed because `InsertEvent()`
  unconditionally *references* it even though `UndoTests.cpp` never
  reaches it at runtime (finding #3's `g_changes = 1` precondition, set in
  `UndoTest::SetUp()`) - the symbol still has to exist to link, since
  `GUI_Song.cpp` (its real home) isn't linked here. Exact same shape as
  the file's existing `SyncSkipLinesAfterNoteInsertComboBox()` stub.
- **`test/UndoTests.cpp`** (new): 30 tests - the 8 bookkeeping methods
  (including all 4 `PosIsEqual` group-size branches, using a cast
  out-of-range `UndoType` to reach the group this codebase's real enum
  values never populate), every `ChangeTrack`/`ChangeSong`/
  `ChangeInstrument`/`ChangeInfo` `UndoType` branch (swapped correctly by
  `Undo()`/`Redo()`, verified in both directions), each method's `BAD!`
  guard branch (characterized, not avoided - confirmed non-crashing),
  multi-step history (two independent `Undo()`s undo the right one each
  time), the `separator = 0` coalescing behavior (confirmed it keeps only
  the *first* snapshot when the same cursor/type/position repeats,
  matching the real "type several notes in a row" production case), and
  `Clear`/`Init`/`DropLast`.
- **`test/SongEditingTests.cpp`**: fixed a now-stale comment claiming
  `g_Undo`'s `ChangeTrack`/`ChangeSong` were still stubbed as no-ops.
- Full solution rebuild (`Rmt.exe` + `RmtTests.exe`, Release|x64) confirmed
  0 errors; 280 tests pass (up from 250, +30, 0 regressions).
