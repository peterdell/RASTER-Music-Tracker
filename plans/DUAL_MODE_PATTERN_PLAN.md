# Plan: the dual-mode pattern - formalize it, and audit what's left to apply it to

## Context

`plans/FILE_TIERING_STRATEGY.md`'s recommendation (against pulling the
Java-port composition split forward into C++ now) named "continue applying
the dual-mode pattern to more untested hazardous methods" as the one thing
worth doing now instead. This plan formalizes that pattern and - before
proposing any new batch of work - audits whether there's actually any
untriaged code left that fits it. **Finding: essentially none.** The
backlog this pattern applies to is already closed by prior work
(`plans/SONG_IO_SONG_REMAINING_PLAN.md`, `plans/IO_IMPORTER_PLAN.md`,
`plans/BROADER_SURVEY_PLAN.md`). This plan exists to (a) name the pattern
for future reference, since it'll matter again at Java-port time per
`plans/FILE_TIERING_STRATEGY.md`'s option 2, and (b) document the audit
that led to that finding, so a future session doesn't have to redo it from
scratch or wrongly assume there's low-hanging fruit here (as an earlier,
uncorrected draft of `plans/FILE_TIERING_STRATEGY.md`'s recommendation
briefly did - see its own correction note).

## The pattern, formalized

"Dual-mode" describes a method whose only hazard is a `MessageBox`/dialog
that unconditionally fires, even though the method also does real,
independently-useful computation the hazard has nothing to do with. The
fix is always: separate the computation from the hazard, so a test can
reach the former without triggering the latter. Three variants have been
used so far, depending on the shape of the original method:

1. **Optional output-parameter** - the method takes an extra pointer/
   reference parameter; when non-null, it populates it and returns without
   ever touching `g_hwnd`; when null (the original, still-default call
   shape), it preserves the exact original behavior (build display text,
   show the `MessageBox`). Used for `CSong::InstrInfo`/`CSong::TrackInfo`
   (`TInstrInfo*`/`TTrackInfo*`).
2. **Test-injectable answer** - for a confirmation prompt
   (`MB_YESNOCANCEL`/`MB_OKCANCEL`) whose *return value* changes control
   flow, rather than an unconditional info dialog. `SendQuestionMessage()`
   (`Messages.h`/`Messages.cpp`) returns whatever `SetTestQuestionAnswer()`
   set whenever `g_statusBar == nullptr` (always true in tests), so the
   method itself needs no change at all - the seam lives in the shared
   messaging helper, not per-method. Used for
   `CSong::SongMaketracksduplicate`/`CSong::Songswitch4_8`,
   `CSong::TracksOrderChange`'s inner confirm, `CSong::FileReload`'s
   confirm.
3. **Two-phase parse + apply** - for methods where a real options dialog's
   own display text depends on data only available *after* partially
   processing the input (so the dialog can't just be "gather parameters
   up front, then run unconditionally" like the other two variants). Split
   into `XxxParseHeader(input, THeaderOut&)` (the unconditional real work
   before the dialog) and `XxxApply(const THeaderOut&, ...flags, TResultOut&)`
   (the real work after it), leaving a thin wrapper that still shows both
   real dialogs, calling the two testable halves around them. Used for
   `CSong::ImportTMC`/`CSong::ImportMOD` (→
   `ImportTMCParseHeader`/`ImportTMCApply`,
   `ImportMODParseHeader`/`ImportMODApply` in `IO_ImporterCore.cpp`).

A fourth, related but distinct shape - **thin dialog wrapper + testable
Apply() core**, where the dialog only gathers a fixed-shape parameter
struct up front and the real mutation logic runs identically afterward
regardless of which values were chosen - was used for `CSong::InstrChange`,
`CSong::SongInsertCopyOrCloneOfSongLines`, and `CSong::TracksOrderChange`.
It isn't "dual-mode" in the strict sense (the dialog wrapper itself stays
real and untested; only its downstream `*Apply()` gets extracted and
tested), but it's the same underlying idea - separate the hazard from the
logic - and worth naming alongside the other three since a future
Java-port seam decision will likely treat all four as one family.

## Audit: is there anything left to apply this to?

Checked every remaining `Send<Type>Message()` call site in the codebase
(`grep -rn "Send(Error|Warning|Information|Info|Question)Message("`,
~90 sites) against the three existing triage docs. Every site falls into
one of these buckets - none is a new, untriaged dual-mode candidate:

- **Already dual-mode'd and tested**: every call inside `SongEditing.cpp`
  (`TrackInfo`, `InstrInfo`, `SongMaketracksduplicate`, `Songswitch4_8`,
  `MakeModule`, `LoadRMT`/`LoadRMW`/`LoadTxt`, etc.) and inside
  `IO_ImporterCore.cpp` (`ImportTMCApply`/`ImportMODApply`'s guard-only
  warnings).
- **Guard-only, no split needed** (avoidable by simply not feeding invalid
  input in a test - the established, separate treatment for this
  sub-case, not dual-mode): `TuningTables.cpp`'s `InitTuning` basetuning
  check, `Undo.cpp`'s five `"... BAD!"` internal-error assertions on
  invalid enum values.
- **Real dialog wrapper, core already extracted** (the fourth shape
  above, already DONE per `plans/SONG_IO_SONG_REMAINING_PLAN.md` Batch 5):
  `CSong::TracksOrderChange`'s `.DoModal()` call in `Song.cpp`.
- **Confirmed no extractable logic at all, stays deferred**:
  `IO_Song.cpp`'s 12 `FileXxx` methods (Batch 7 - each unconditionally
  constructs a real `CFileDialog`/`CFileNewDlg`; the dialog's result *is*
  the method, not a parameter to route around).
- **Real hardware/DLL coupling, not a messaging problem**: `C6502.cpp`,
  `Pokey.cpp`, `PokeyRenderer.cpp`, `RmtMidi.cpp`, `WaveFileExporter.cpp`
  (all Category A in `plans/BROADER_SURVEY_PLAN.md`), `Midi_Song.cpp`
  (real `midiInGetNumDevs()`/`midiInGetDevCaps()`), `SongContainer.cpp`'s
  `GetModifiablePokeyStream()` (gated on the deferred
  `CSong::DumpSongToPokeyStream()` real audio pipeline, per
  `plans/EXPORTV2_PLAN.md`).
- **Real UI, not a messaging problem**: `GUI_Song.cpp` (the keyboard-input
  dispatch layer - `InfoKey`/`InstrKey`/`ProveKey`/`TrackKey`/`SongKey` and
  cursor-goto helpers - confirmed Category A), `Commands.cpp`, `Shell.cpp`,
  `Rmt.cpp` (app entry point).
- **Test infrastructure, not production hazard**: `SongExporterTest.cpp`'s
  `SendInfoMessage`/`SendErrorMessage` calls are the test project's own
  `OpenOutputStream`/`CloseOutputStream` helpers, already exercised by
  tests directly. `RmtTest.cpp` is a separate command-line test/scripting
  driver, not part of the `CSong` hazard surface.

**Conclusion: the dual-mode backlog is closed.** There is currently no
method left where a `MessageBox`/dialog blocks reachable, independently-
useful logic the way `InstrInfo`/`TrackInfo`/`ImportTMC`/`ImportMOD` did.

## The one open item this audit surfaced (not dual-mode - a different question)

`plans/BROADER_SURVEY_PLAN.md`'s Category B, priority #2: `Undo.cpp`
(`CUndo`, 16 methods - `Init`, `Clear`, `DeleteEvent`, `GetUndoSteps`,
`Undo`, `GetRedoSteps`, `Redo`, `InsertEvent`, `DropLast`, `Separator`,
`ChangeTrack`, `ChangeSong`, `ChangeInstrument`, `ChangeInfo`,
`PosIsEqual`, `PerformEvent`). Its `g_hwnd` uses are all guard-only (no
dual-mode work needed there), but its methods operate on the real
`extern CSong g_Song` global rather than taking a song instance as a
parameter - the survey flagged this as needing a check of "how existing
tests' local `song` fixture relates to `g_Song`" before `CUndo` can be
characterized directly, since `g_Undo` is currently only exercised
incidentally (via `UndoStub.cpp`'s no-op stub), never tested on its own.
An older note in `plans/NOTES.md` (predating the `CSong` split work
entirely) called `CUndo` "squarely downstream of the `CSong` split work" -
that split is now done, so this note is stale and the question is worth
re-opening, not re-trusting as-is (same "verify before deferring"
discipline as everywhere else in this effort).

This is genuinely real, untested production functionality (undo/redo), so
it's worth pursuing - but it is not this plan's subject matter, and
starting it isn't a decision I should make unilaterally here.

## Decision needed

**Do you want `Undo.cpp`/`CUndo` opened as a new investigation/triage
batch now** (checking the `g_Song`-vs-local-fixture relationship first,
per the survey's own note), or should it stay parked until something else
prompts revisiting the broader survey?
