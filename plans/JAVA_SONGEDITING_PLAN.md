# Plan: Java port of `SongEditing.cpp` (and its neighboring exporters/importers)

## Context

`Song.java` currently covers only `SongCore.cpp` (the constructor-adjacent,
globals-free slice ported in the "Eighteenth ported batch" - see
`plans/JAVA_PORT_PLAN.md`). `SongEditing.cpp` is the much larger sibling
C++ file: ~80 already-tested `CSong` methods that only transitively touch
`g_Tracks`/`g_Instruments`/`g_Undo`/`g_TrackClipboard`/`g_tracks4_8` (all
already real, safe globals in the C++ test project - see
`plans/SONG_IO_SONG_REMAINING_PLAN.md` for the full triage history that
produced it). `SongEditingTests.cpp` (2181 lines) also covers several
*other* C++ classes that aren't `CSong` methods at all: the exporter family
(`CRmtExporter`, `CASMFileExporter`, `CSAPFileExporter`, `CSongExporter`,
`CWaveFileExporter`) and the TMC/MOD importers (`IO_ImporterCore.cpp`).

This plan scopes all of it into Java-port sub-batches, following this
project's established cadence: one sub-batch at a time, each confirmed with
the user before implementation, verified via `mvn -o test`, documented in
`plans/JAVA_PORT_PLAN.md`/`plans/NOTES.md`, and committed only once
approved. Like the original C++ triage, expect corrections once a sub-batch
is actually implemented - method call graphs (not just direct field/global
reads) have repeatedly turned out to hide extra dependencies in this
project (`Stop()`, `SetTracks()`, `ClearSong()` all had this happen during
the C++ side of this same triage).

## Already ported

- `Song.java` (`SongCore.cpp`): name/tracks/NTSC/instrument-speed getters,
  play-line bookkeeping, `UECursorIsEqual`, `SongGetGo`/`SongTrackGoDec`/
  `Inc`, `FindNearTrackBySongLineAndColumn`, `SongPlayNextLine`,
  `SongToAta`/`AtaToSong`.
- `Song.java` also already has two `SongEditing.cpp` methods, ported early
  because `Undo` needed them directly: `blockDeselect()` (a documented
  no-op - see below) and `stop(Undo)`.
- `Tracks.java` already has the per-track primitives (`trackBuildLoop`/
  `trackExpandLoop`) that `TracksAllBuildLoops`/`TracksAllExpandLoops`
  below just loop over.

## Sub-batches - `SongEditing.cpp` proper (`CSong` methods)

### 1. Cursor/navigation helpers - DONE (commit pending)
`GetSubsongParts`, `MarkTF_USED`/`MarkTF_NOEMPTY`, `ActiveInstrSet`/`Prev`/
`Next`, `TrackLeft`/`TrackRight`, `RespectBoundaries`,
`TrackGetLoopingNoteInstrVol`, `SongTrackSet`/`SetByNum`/`Dec`/`Inc`/
`Empty`/`GoOnOff`.

**Correction found while implementing**: `GetUECursor`/`SetUECursor` turned
out to already be *fully* ported (all 4 `Part` cases each) as part of the
earlier CUndo/Song batch - `Undo` needed all four cases itself, so there
was no partial subset left to port here. Removed from this sub-batch's
scope; `TrackLeft`/`TrackRight`'s tests just observe `getUECursor`'s
already-ported `PART_TRACKS` case rather than needing new cursor code.

**Two dependencies pulled forward from later sub-batches**, once actually
scoped: `getSmallestMaxtracklen` (needed by `respectBoundaries`, originally
sub-batch 3's `GetEffectiveMaxtracklen`/`ChangeMaxtracklen` group - only
this one method was pulled forward, not its siblings) and
`songGetActiveTrack` (a small `Song.h` inline getter, needed by
`SongTrackSetByNum`, not previously ported since `SongCore.cpp` didn't
need it).

`Song`'s constructor now also takes a `Tracks` collaborator (alongside the
existing `Instruments`), needed by `markTfNoEmpty`/
`trackGetLoopingNoteInstrVol`/`getSmallestMaxtracklen`. No new
dependencies beyond `Tracks`/`Instruments`/`Song`'s own state - lowest-risk
batch, as expected.

### 2. Song-line editing - DONE (commit pending)
`SongInsertLine`, `SongDeleteLine`, `SongInsertCopyOrCloneOfSongLinesApply`,
`SongCopyLine`/`SongPasteLine`/`SongClearLine`. Confirmed self-contained:
`SongCopyLine`/`SongPasteLine` use `Song`'s own `m_songlineclipboard`/
`m_songgoclipboard` fields (plain per-instance state, not
`CTrackClipboard`), plus `Undo.changeSong` (already ported) for the
paste/clear undo events. Also added `clearBookmark`/`isBookmark` (small
`Song.h` inline methods, needed by `songInsertLine`/`songDeleteLine`'s
bookmark-adjustment logic, not previously ported).

### 3. Track-length analysis/cleanup - DONE (commit pending)
`GetEffectiveMaxtracklen`, `GetSmallestMaxtracklen` (already done),
`ChangeMaxtracklen`, `SongClearUnusedTracksAndParts`,
`SongClearDuplicatedTracks`, `SongClearUnusedTracks`,
`TracksAllBuildLoops`/`TracksAllExpandLoops` (thin loops over `Tracks`'
already-ported primitives, plus a `Stop()` call that's a no-op as long as
nothing has called `Play()` first - same documented precondition the C++
side used), `RenumberAllTracks`, `ClearAllInstrumentsUnusedInAnyTrack`,
`RenumberAllInstruments`, `TracksOrderChangeApply`.

**`RenumberAllInstruments`'s `type=3` (order by instrument name) has no
direct C++ test coverage** (`SongEditingTests.cpp` only exercises
`type=1`/`type=2`) - ported anyway as a faithful, mechanical translation
(the method's `type` selector makes all three branches equally reachable
production code, not a speculative addition), but not independently
verified against a golden master the way the other two types were.
Also omits C++'s final `g_Instruments.Update(i)` loop ("writes to Atari")
- matches `Instruments`'s own prior omission of the same call; no test
observes Atari memory here and `Song` holds no `Atari` reference.

### 4. Track/instrument copy-paste - DONE (commit pending)
**Correction to this sub-batch's original scope**: `TrackCopy`/`TrackPaste`
turned out *not* to need the full `CTrackClipboard` class after all - they
only ever touch its single-`TTrack` `m_trackcopy` slot (confirmed via a
`SongEditingTests.cpp` comment explicitly distinguishing it from
`BlockPaste`'s separate block-selection clipboard). Modeled directly as a
`trackCopyClipboard` field on `Song` instead of waiting on a full
`CTrackClipboard` port - a deliberately minimal slice, not a reintroduction
of the deferred class. Final scope: `InstrCopy`/`InstrCut`/`InstrDelete`
(use `Song`'s own `m_instrclipboard` field), `TrackDelete`,
`TrackCopyFromTo`, `TrackSwapFromTo`, and now also `TrackCopy`/`TrackPaste`/
`TrackCut` per the correction above. `BLOCKSETBEGIN`/`BLOCKSETEND`/
`ISBLOCKSELECTED`/`BlockPaste` remain excluded - they still need real
block-selection state, not just a single-track slot - see the "Needs
`CTrackClipboard`" section below (updated to reflect this narrower
remaining scope).

### 5. Bookmark/settings - DONE (commit pending)
`SetBookmark`, `SetTracks`/`SetNTSC`, `ResetTuningVariables`. C++'s
conditional `ReInitSound()` call in `SetTracks`/`SetNTSC` (real
`g_AtariTrackerDriver`/`g_Pokey` hardware reinit, no-op-stubbed in every
C++ test) is dropped entirely - `setTracks` becomes a trivial identity
transform once that's removed (its entire non-trivial behavior *was* the
dropped call), returning the new value for the caller to store, matching
this port's "no stored global" treatment of `tracks4_8` everywhere else.

### 6. The three "info/dialog" methods - DONE (commit pending)
`InstrInfo`, `InstrChangeApply`, `TrackInfo` - already refactored on the
C++ side into the dual-mode (output-parameter vs. `MessageBox`) shape (see
`plans/DUAL_MODE_PATTERN_PLAN.md`).

**Simplification found while implementing**: since the Java port has no
UI/window to show a `MessageBox` in, and only the non-null-output-parameter
branch is tested, the `MessageBox`-building branch (and the local arrays
that exist solely to feed it, e.g. `InstrInfo`'s `withnote[]`/`intrack[]`)
isn't ported at all - not even as a dead/unreachable code path. This is
simpler than a literal "dual-mode" port (which would still carry an
`if (info != null) {...} else {...}` shape); there's no `else` branch to
carry, since there's nothing to show it. `InstrChangeApply`'s
`CString* resultMsg` output parameter (non-null in every test) becomes a
plain returned `String` for the same reason. `InstrInfo`/`TrackInfo`'s
output structs became mutable classes (not immutable records), matching
`SongInfo`'s existing precedent, since `TrackInfo`'s own test relies on
the "leaves the struct untouched for invalid input" contract - a record
can't represent "untouched," only "returns some value."

### 7. Module format - buffers
`MakeModule`, `DecodeModule`. Same shape as the already-ported `SapFile`/
`AsmFileBuilder` buffer-building work. `MakeModule` has one guard-only
`MessageBox` on malformed input (avoidable, matching the C++ test's own
approach - don't feed it malformed input, or convert to an
`IllegalStateException` per the established idiom if a test needs to
exercise that path).

### 8. Module format - streams
`SaveTxt`/`LoadTxt`, `SaveRMW`/`LoadRMW`, `LoadRMT`. `LoadRMT` needs
`AtariIO.LoadBinaryBlock` (a *stream*-based method - not yet ported;
`AtariIO.java` currently only has `loadDataAsBinaryFile`, the buffer-based
one `AtariTrackerDriver` needed). **Known pre-existing bug, characterized
not fixed on the C++ side**: `LoadTxt()` silently fails to recognize a
`[SEGMENT]` marker immediately following `SaveTxt()`'s blank "gap" line,
so round-tripping a `.txt`-saved RMT file through `LoadTxt()` doesn't
actually restore song data (tracked upstream as
[raster-atari-org/RASTER-Music-Tracker#21](https://github.com/raster-atari-org/RASTER-Music-Tracker/issues/21)).
Per this project's standing preference (see the
`fix-provable-bugs-in-both-languages-during-porting` memory), this is
**not** obviously "small and provably safe to fix" - it's a real behavior
change to production file-loading logic - so default to porting it
faithfully (bug and all) and flag it for an explicit decision, the same
way the DEFSONG bug was flagged before being fixed on both sides.

### 9. Navigation/playback
`SongJump`/`SongUp`/`SongDown`/`SongSubsongPrev`/`SongSubsongNext`,
`TrackUp`/`TrackDown`, `SongPrepareNewLine`/`SongPutnewemptyunusedtrack`,
`SongMaketracksduplicate`/`Songswitch4_8` (need `Messages.sendQuestionMessage`
- already ported), `PlayPressedTones`, `InstrPaste`, `Play`/`PlayBeat`/
`PlayVBI` (need `AtariTrackerDriver`/`Atari` - already ported, JSR is
already a no-op per `AtariTrackerDriver`'s own javadoc). `Stop` is already
done.

### 10. `ClearSong` - capstone
Touches most of the above (`Stop`, `SetTracks`, `PlayPressedTonesInit`,
`ClearBookmark`, `Tracks.initTracks`, `Instruments.initInstruments`,
`Undo.init`, plus `g_TrackClipboard.Clear()`) - do this last, once its
dependencies exist. One piece (`SyncSkipLinesAfterNoteInsertComboBox`, a
real MFC-main-window sync call) stays deferred, matching the C++ side's own
extraction of it into its own no-op-stubbed method.

## Needs `CTrackClipboard` ported first (its own sub-effort)

`BLOCKSETBEGIN`/`BLOCKSETEND`/`ISBLOCKSELECTED`/`BlockPaste` (need real
block-selection state plus `BlockPasteToTrack`) - narrower than originally
scoped, now that `TrackCopy`/`TrackPaste`/`TrackCut` turned out to need only
a trivial single-track slot (ported in sub-batch 4, modeled directly on
`Song` rather than needing this class at all). `BLOCKDESELECT` is already
a documented Java no-op for the same reason.

`Clipboard.h`/`.cpp` (130 lines total) is a real, separate `CTrackClipboard`
class - constructor, `Clear`, `IsBlockSelected`/`IsTrackSelected`,
`BlockSetBegin`/`End`/`Deselect`, `BlockCopyToClipboard`/
`BlockExchangeClipboard`/`BlockPasteToTrack`/`BlockClear`/
`BlockRestoreFromBackup`, `BlockNoteTransposition`/`BlockInstrumentChange`/
`BlockVolumeChange`, `BlockEffect` (**confirmed on the C++ side to have no
extractable logic - stays deferred even once the rest of the class is
ported**), `BlockAllOnOff`, `BlockInitBase`. **A real wrinkle already
flagged in `SongEditing.cpp`'s own header comment**: `BlockSetBegin`/
`BlockPasteToTrack` internally read the *global* `g_Song`, not necessarily
the `CSong` instance they're logically operating on - a pre-existing C++
coupling quirk, not introduced by this port. In Java this likely means
`TrackClipboard` needs an explicit `Song` parameter on those specific
methods (matching the established "explicit parameter instead of global"
idiom) rather than assuming a single owning `Song`. Needs its own
dedicated scoping pass before porting - don't fold it into a `SongEditing`
batch casually.

## Needs a deferred `PokeyStream`/`AtariTrackerDriver` surface

`CSong::DumpSongToPokeyStream`/`CSongContainer::GetPokeyStream`,
`CSAPFileExporter::ExportSAP_R`/`ExportSAP_B_LZSS`,
`CSongExporter::ExportXEX_LZSS`/`ExportLZSS`/`ExportCompactLZSS`,
`CWaveFileExporter::ExportWAV`. All of these ultimately drive
`PokeyStream.StartRecording`/`Record`'s real data-writing body - explicitly
deferred in the "Twenty-first ported batch" (see `plans/JAVA_PORT_PLAN.md`)
because that path needs a real `AtariTrackerDriver` collaborator and a
growable buffer, neither modeled yet. Revisit once `PokeyStream`'s real
recording path is unblocked - likely its own dedicated sub-effort, not a
quick add-on.

## Exporters that don't need the PokeyStream path

`CRmtExporter::ExportAsRMT`/`ExportAsStrippedRMTApply` (69 lines total -
likely just re-serializes via `SaveRMW`-like logic once that's ported),
`CASMFileExporter::ExportAsAsmApply`/`BuildRelocatableAsm`/
`ExportAsRelocatableAsmForRmtPlayerApply` (112 lines - probably builds on
top of the already-ported `AsmFileBuilder`). Worth scoping in detail once
`SaveRMW`/`MakeModule` (sub-batches 7-8 above) and enough of the cursor/
editing surface exist to support them.

## Explicitly out of scope for this plan

- **TMC/MOD importers** (`CSong::ImportTMC`/`ImportTMCParseHeader`/
  `ImportTMCApply`, `ImportMOD`/`ImportMODParseHeader`/`ImportMODApply`,
  `IO_Importer.cpp`/`IO_ImporterCore.cpp`) - `IO_ImporterCore.cpp` alone is
  over 1000 lines just for these two format importers. This is its own
  large undertaking on the scale of `SongEditing.cpp` itself, not a
  sub-batch of it - needs its own dedicated scoping plan if/when tackled.
- **`BlockEffect`** - confirmed on the C++ side to have no extractable
  logic at all (unlike `InstrChangeApply`/`TrackInfo`'s dialog-wrapper
  split) - stays deferred indefinitely, matching the C++ decision.
- **The `FileXxx` family** (`FileReload`/`FileOpen`/`FileSave`/etc.) -
  confirmed real `CFileDialog` orchestration with no testable core to
  extract - deferred indefinitely on the C++ side, and there is nothing
  format-level left for Java to gain by porting these wrapper methods
  (the actual format encode/decode they dispatch to is covered by the
  batches above).
- **`TimerRoutine`/`ChangeTimer`/`StopTimer`/`ReInitSound`** - genuine
  real-audio-hardware/timer-thread hazard on the C++ side, no Java
  equivalent exists or is planned.

## Suggested execution order

1. Sub-batch 1 (cursor/navigation helpers) - DONE.
2. Sub-batch 6 (`InstrInfo`/`InstrChangeApply`/`TrackInfo`) - DONE.
3. Sub-batches 2-5 (song-line editing, track-length cleanup, clipboard-free
   copy-paste, bookmark/settings) - DONE.
4. Sub-batches 7-8 (module format buffers/streams) - same shape as prior
   `SapFile`/`AsmFileBuilder` work; surface the `LoadTxt` bug decision
   explicitly before implementing sub-batch 8.
5. Sub-batch 9 (navigation/playback).
6. Sub-batch 10 (`ClearSong`) once its dependencies land.
7. `CTrackClipboard` as its own dedicated scoping pass (now narrowed to
   just `BLOCKSETBEGIN`/`BLOCKSETEND`/`ISBLOCKSELECTED`/`BlockPaste`, since
   sub-batch 4 already resolved `TrackCopy`/`TrackPaste`), once its
   `g_Song`-reads-a-global wrinkle is worked out.
8. Exporters (`CRmtExporter`/`CASMFileExporter` first, since they don't
   need `PokeyStream`; the SAP-R/LZSS/WAV/XEX family only once
   `PokeyStream`'s real recording path is unblocked).
9. TMC/MOD importers - separate, dedicated plan, not part of this one.

Each numbered sub-batch above is intended to be confirmed with the user
individually before implementation, per this project's established cadence
- this document is the scoping pass, not a commitment to implement
everything in it.
