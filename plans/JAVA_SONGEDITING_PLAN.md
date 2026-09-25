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

### 7. Module format - buffers - DONE (commit pending)
`MakeModule`, `DecodeModule`. New `SongIOType` (plain enum, all 15 C++
values ported for the sub-batch-8 methods that'll need `RMW`/`TXT`) and
`RmtFormatVersion` (plain `int` constants - `DecodeModule` compares a raw
byte value against it with `>`, not just equality).

- `MakeModule`'s one guard-only failure path (a track too event-dense to
  encode) returns `-1` without reproducing C++'s `SendErrorMessage` call -
  matches `instrChangeApply`'s established reasoning (`Song` holds no
  `Messages` reference, no test reaches this path).
- C++ writes `InstrToAta`/`TrackToAta`/`songToAta`'s output directly into
  `mem` at a pointer offset; since Java arrays can't be sliced without
  copying, `makeModule` writes each into a reusable scratch buffer first,
  then `arraycopy`s the actual encoded length into `mem` at the right
  offset. `decodeModule` does the mirror-image `Arrays.copyOfRange` on the
  way in.
- `DecodeModule` returns a `DecodeModuleResult(version, tracks4_8)` record
  instead of just an `int` - C++'s own `int` return is 0 for failure,
  which is ambiguous with a genuinely-decoded version-0 file (a real,
  pre-existing C++ design wart, not introduced here), and `SetTracks`'s
  side effect on `g_tracks4_8` needed *some* way to reach the caller given
  this port's "no stored global" treatment of `tracks4_8` everywhere else.
- Omits C++'s `g_Instruments.Update(instrumentNr)` call per decoded
  instrument ("writes to Atari ram") - matches `Instruments`'s own prior
  omission of the same call.

### 8. Module format - streams - DONE (commit pending)
`SaveTxt`/`LoadTxt`, `SaveRMW`/`LoadRMW`, `LoadRMT`.

**`SaveRMW`/`LoadRMW`'s "main parameters" `sizeof` bug - FIXED on the C++
side**: the 31-parameter binary block was written/read via
`int* mainparams[31]` and `sizeof(mainparams[0])` - which is
`sizeof(int*)`, not `sizeof(int)`. On the 32-bit builds this project
originally shipped as, those two happened to be the same size (4 bytes),
masking the bug entirely; on this 64-bit build `sizeof(int*)` is 8, so
every parameter was silently written/read as 8 bytes instead of 4 (4 bytes
of adjacent memory past each variable). The user identified the 32-bit
history as the likely cause and asked for the C++ side to be restored to
that originally-correct, size-independent behavior. Fixed by using
`sizeof(int)` explicitly in both `SaveRMW`/`LoadRMW`. The existing
round-trip test didn't catch this - on this build's specific memory
layout, the extra 4 bytes each slot wrote turned out to capture the *next*
parameter's own value, which then got redundantly (but correctly)
restored when that next slot was read back - a self-canceling coincidence,
not something to rely on. Added
`SaveRMWWritesEachMainParameterAsExactlyFourBytes`, which parses the
output's exact byte offsets to catch a regression back to the 8-byte
layout directly. Verified via full Release|x64 rebuild: 371 tests pass.
Changes the on-disk byte layout of newly-saved `.rmw` files (existing
files saved by any 64-bit build had the buggy 8-byte layout already,
so this doesn't newly break anything that was working - it's a fix, not a
behavior change to a format that was reliably interoperable before). The
Java port of `SaveRMW`/`LoadRMW` (this sub-batch, not yet started) will
use the corrected 4-byte-per-parameter format directly.

**`LoadTxt` bug - FIXED on the C++ side** (see
[raster-atari-org/RASTER-Music-Tracker#21](https://github.com/raster-atari-org/RASTER-Music-Tracker/issues/21)):
`LoadTxt()`'s `[MODULE]`/`[SONG]` segment loops used to detect the next
segment by reading one byte at a time and checking for `'['` - but the
blank "gap" line `SaveTxt()` writes before each segment meant that byte
was `'\n'` first, which got handed to `getline()` as if it were real
content, silently swallowing the whole following segment-header line
(`[SONG]`, etc.) instead of recognizing its `'['` as a boundary. Fixed by
skipping a lone `'\n'` byte in both loops instead of treating it as
content (verified via a full Release|x64 rebuild + full suite - 370 tests
pass, including the updated round-trip test, which now asserts the song
data actually survives the round trip instead of documenting that it
doesn't). The Java port of `LoadTxt` (this sub-batch, not yet started)
will port the corrected behavior directly - there is no longer a "port the
bug or fix it" decision to make.

**Unrelated finding, not part of this fix**: while isolating the above,
found that `ClearSong()` (and anything that calls it, including
`LoadTxt`) crashes when run as the *only* test in a filtered
`--gtest_filter` invocation (confirmed on an existing, untouched test too,
`ClearSongSetsTheTrackCount` - not something this fix introduced).
`g_Atari.Init()`'s tuning-initialization path appears to depend on some
global state that's only valid once an earlier test has run first. Does
not affect the full suite (370/370 pass either way) - flagged for
awareness, not investigated further or fixed here.

**Genuine blocking dependency found while implementing: `ClearSong` (sub-
batch 10) had to be pulled forward.** `LoadTxt`/`LoadRMW` both
unconditionally call `ClearSong(8)` at their very start. Re-checking its
full body (rather than trusting the "capstone, do last" scoping) found
every real dependency already existed except a handful of small `Song`
fields it also touches (`m_followplay`/`m_speeda`/`m_filename`/
`m_ioType`/`m_lastExportIOType`/`m_TracksOrderChange_songline{from,to}` -
none previously needed) and its one genuine hazard
(`SyncSkipLinesAfterNoteInsertComboBox()`, a real `AfxGetMainWnd()`/
`CMainFrame` UI-sync call), which - like `g_Atari.Init()`/
`g_AtariTrackerDriver->Init()`, `g_TrackClipboard.Clear()`, and a dozen
trivial globals it also touches - has no Java equivalent and isn't
observed by any test, so it's simply omitted (matching the same pattern
used throughout this port). Added the new fields plus
`getFollowPlayMode`/`setFollowPlayMode`/`getFilename`/`getIOType`
accessors and `clearSong(int, Undo)`, returning the new `tracks4_8` value
(matching `setTracks`'s established "no stored global" reasoning). Tests
(`clearSongResetsSongDataAndPositionBackToDefaults`,
`clearSongSetsTheTrackCount`) mirror `SongEditingTests.cpp`'s `ClearSong`
section exactly.

**Deliberately deferred, its own separate undertaking: `IO_Instruments.cpp`/
`IO_Tracks.cpp`'s TXT/RMW per-instrument/per-track serialization.**
`SaveTxt`/`LoadTxt` call `g_Instruments.SaveAll`/`LoadAll`/
`g_Tracks.SaveAll`/`LoadAll` for the `[INSTRUMENT]`/`[TRACK]` sections;
`SaveRMW`/`LoadRMW` call the RMW-format equivalents (which, unlike TXT,
serialize *every* instrument/track unconditionally, not just non-empty
ones - an even larger surface). None of this is exercised by any C++ or
Java test (every existing test's song has no non-empty instruments/tracks,
so TXT's `SaveAll` writes nothing for them anyway), so these calls are
omitted entirely from the Java port rather than attempting a large,
untested new serialization layer. `LoadTxt`'s `[INSTRUMENT]`/`[TRACK]`
segment branches skip to the next segment instead of decoding, matching
the encoding side's omission.

**Design choices carried through all five methods**:
- **Streams become `String`/`byte[]`**: `SaveTxt`/`LoadTxt` take/return a
  `String` (matching `SapFile.export()`'s established idiom - the C++
  test itself already builds the whole string upfront via a
  `std::istringstream`); `SaveRMW`/`LoadRMW`/`LoadRMT` take/return a
  `byte[]`, since the format is binary. This sidesteps Java's checked
  `IOException` entirely - no stream abstraction is introduced anywhere in
  this port.
- **`SaveRMW`/`LoadRMW`'s ~15 unmapped "main parameters"**: per the user's
  explicit decision, the file keeps all 31 four-byte slots in the same
  order as C++ (byte-compatible with real C++-saved `.rmw` files for the
  16 fields this port *does* model), writing `0` for the UI/keyboard-
  setting globals ({@code g_prove}, {@code g_keyboard_layout}, etc.) that
  don't exist anywhere in this Java port, rather than shrinking the block.
- **New `RmtVersion.RMT_VERSION_STRING`** (was the C++ compile-time
  constant of the same name).
- **New `AtariIO.loadBinaryBlock`** (was `CAtariIO::LoadBinaryBlock`,
  needed by `LoadRMT`): byte-array-based like `loadDataAsBinaryFile`,
  additionally reporting how many *input* bytes were consumed (header
  plus data) - a stream-based caller gets this for free from the stream's
  own advanced position, but `LoadRMT` needs it explicitly to find where
  its second block starts in a byte array.
- `LoadRMW`'s version-mismatch guard and `LoadRMT`'s two guard-only
  failure paths return `false`/`LoadRmwResult(false, ...)` without
  reproducing their `SendErrorMessage` calls, matching
  `instrChangeApply`'s established reasoning.

Tests (`SongEditingTest`, extended) mirror `SongEditingTests.cpp`'s
corresponding sections exactly (6 tests, plus the new C++-mirroring
`saveRMWWritesEachMainParameterAsExactlyFourBytes`).

### 9. Navigation/playback - DONE
`SongJump`/`SongUp`/`SongDown`/`SongSubsongPrev`/`SongSubsongNext`,
`TrackUp`/`TrackDown`, `SongPrepareNewLine`/`SongPutnewemptyunusedtrack`,
`SongMaketracksduplicate`/`Songswitch4_8`, `PlayPressedTones`, `InstrPaste`,
`Play`/`PlayBeat`/`PlayVBI` (`Stop` was already done).

**All always-true/never-observed `BOOL` returns dropped to `void`**:
`songJump`/`songUp`/`songDown`/`songSubsongPrev`/`songSubsongNext`/
`trackUp`/`trackDown` - no caller or test in this batch ever reads their
return value, matching the established pattern. `songPrepareNewLine`/
`songPutnewemptyunusedtrack`/`songMaketracksduplicate`/`playPressedTones`/
`play`/`playBeat`/`playVBI` keep `boolean`, since tests do assert on it.

**`songUp`/`songDown`/`songSubsongPrev`/`songSubsongNext` omit C++'s
`if (m_play && m_followplay) { Stop(); ...; Play(); }` tail** - untested in
every case (`playMode` defaults to `PLAY_STOP` and no sub-batch-9 test
changes that first), matching the omission already used for
`TracksAllBuildLoops`/`TracksAllExpandLoops`'s untested branches.

**`g_keyboard_updowncontinue` becomes an explicit parameter** on
`trackUp`/`trackDown` (matches this class's established idiom for globals
a method needs). The C++ test binary's stub defaults it to `FALSE`
(`SongEditingStub.cpp`), unlike the real app's `TRUE` default set in
`RmtView.cpp` - not modeled, since no settings dialog exists yet. New
trivial delegator `trackGetLastLine()` (from `Song.h`'s inline family) was
added for `trackDown`'s guard - the only member of that family this batch
actually needs (`TrackGetNote`/`Instr`/`Vol`/`Speed`/`SetXxx` etc. stay
unported, see the `PlayVBI` note below).

**`songMaketracksduplicate`/`songswitch4_8` take a `Messages` parameter**:
unlike the guard-only `SendErrorMessage`/`SendInformationMessage` calls
dropped throughout this port, `SendQuestionMessage`'s *return value*
drives branching here, so it can't just be omitted. Fully testable on
every branch since `MessageBox` calls route through `Messages` (see
`plans/MESSAGEBOX_REFACTOR_PLAN.md`) - these two were previously deferred
solely because of that prompt. `songswitch4_8(int currentTracks4_8, int
newTracks4_8, Undo, Messages)` needed a genuinely new shape versus C++'s
single-parameter `Songswitch4_8(int tracks4_8)`: C++'s parameter serves
double duty (the requested target *and*, via the untouched global, the
value that survives a cancel), which Java's stored-nowhere `tracks4_8`
(see `setTracks`'s established reasoning) can't do with one parameter -
so it takes the current value explicitly and returns it unchanged on
cancel, or `setTracks`'s result on confirm.

**`AtariTrackerDriver` grew three real methods** pulled forward from their
prior "no dedicated test coverage - deferred" status now that
`SongEditingTests.cpp`'s `PlayPressedTones`/`PlayBeat` exercise them:
`setTrackNoteInstrumentVolume`/`setTrackVolume`/`instrumentTurnOff`. Their
C++ bodies are JSR calls (still omitted, per that class's established
no-op reasoning) plus real, observable side effects that aren't
JSR-dependent - `g_rmtinstr` bookkeeping and, for `instrumentTurnOff`, one
POKEY-register memory reset via `Atari.setByteAt` - which this port keeps.

**`instrPaste`'s C++ `goto InstrPaste_Envelopes`** (shared by `special`
values 1/2/3/4/6/8/9) becomes a small `switch` that only sets four boolean
flags, followed by one shared copy loop - Java has no `goto`. All of
`Instrument`'s envelope/note-table/parameter fields needed here already
existed from the earlier `InstrCopy`/`InstrCut`/`InstrDelete` batch.

**`play`'s C++ `goto Play3`** (shared by `PLAY_TRACK` and a block-play
request that falls back to it) becomes a local `playTrack` boolean flag
for the same reason. **`PLAY_BLOCK`'s real block-selection branch is
unreachable in this port**: it only runs when `isBlockSelected()` is true,
which - since no `CTrackClipboard` block-selection surface is ported (see
below) - is never the case, so requesting `PLAY_BLOCK` always falls back
to `PLAY_TRACK` behavior, exactly as C++ does whenever nothing is actually
selected. The new fields `trackPlayBlockStart`/`trackPlayBlockEnd`
(mirroring `m_trackplayblockstart`/`m_trackplayblockend`) are kept as real
fields anyway, purely so `playBeat`/`playVBI`'s own `PLAY_BLOCK` checks
stay faithful and need no further changes once block selection is ported.

**`playBeat`'s C++ `goto TrackLine`** (a full retry of the per-track scan
after advancing to the next songline) becomes a labeled
`while(true)`/`continue` loop for the same "no `goto`" reason.

**`playVBI` omits its quantization branches** (triggered when
`speeda == speed && followplay` and `quantizationNote` is a real note or
`-2`): they need `Tracks`'s `SetInstr`/`SetVol`/`SetSpeed`/`SetNoteInstrVol`
family and the `g_respectvolume` global, none of which are ported.
`quantizationNote` defaults to `-1` and no ported caller ever sets it to a
note or `-2`, so both branches are unreachable in every existing test -
the reset to `-1` at the end is kept since it's a real, cheap,
always-correct effect either way. This is the reason the `TrackGetVol`/
`TrackSetNoteInstrVol`/`TrackSetNoteActualInstrVol`/`TrackSetVol`/
`SetPlayPressedTonesTNIV` family from `Song.h` mostly stays unported -
`SetPlayPressedTonesTNIV` itself *is* ported (used directly by
`PlayPressedTones` and its own test), but the four `Track*`
get/set-via-active-cursor delegators aren't.

Tests (`SongEditingTest`, extended) mirror `SongEditingTests.cpp`'s
corresponding section exactly (23 tests; 64 -> 87, 0 regressions).

### 10. `ClearSong` - DONE (commit pending; done as part of sub-batch 8, not last)
Turned out not to be a capstone after all - `LoadTxt`/`LoadRMW` (sub-batch
8) both call it unconditionally, making it a genuine blocking dependency
rather than something to save for last. See sub-batch 8's entry above for
the full writeup (new fields, omitted globals/UI call, and why it turned
out fully portable once actually re-checked).

## `CTrackClipboard` (new `TrackClipboard` class) - partially DONE

**Corrected finding**: this class is *not* actually untested, as first
assessed - `SongEditingTests.cpp` already exercises a real subset of it
indirectly, via `CSong`'s own `BLOCKSETBEGIN`/`BLOCKSETEND`/`BLOCKDESELECT`/
`ISBLOCKSELECTED`/`BlockPaste` delegate wrappers (which operate on the
global `g_TrackClipboard`/`g_Song`). Ported that subset: the constructor,
`isBlockSelected`/`isTrackSelected` (the latter only ever exercised as an
internal guard, never directly asserted on), `clear`, `blockSetBegin`,
`blockSetEnd`, `blockDeselect`, `blockInitBase` (internal, called by
`blockSetBegin`), `blockCopyToClipboard`, `blockPasteToTrack`, `getFromTo`,
plus `Song`'s own wrappers (`songBlockSetBegin`/`songBlockSetEnd`/
`blockDeselect`/`isBlockSelected`/`blockPaste`).

**Still NOT ported - confirmed no test coverage anywhere (direct or
indirect)**: `BlockAllOnOff`, `BlockExchangeClipboard`, `BlockClear`,
`BlockRestoreFromBackup`, `BlockNoteTransposition`, `BlockInstrumentChange`,
`BlockVolumeChange`. Porting these needs new C++ characterization tests
first - deferred pending a future decision. `BlockEffect` stays deferred
indefinitely (confirmed on the C++ side to have no extractable logic).

**The `g_Song`-reads-a-global wrinkle, resolved**: `blockSetBegin`/
`blockPasteToTrack` take an explicit `Song` parameter (plus `Tracks`/
`tracks4_8`), matching this project's established "explicit parameter
instead of global" idiom - not a new wrinkle, just C++'s own pre-existing
coupling (`CTrackClipboard` reading `g_Song` rather than any particular
`CSong` instance) made visible in the port.

**A real ripple into sub-batch 9's `play()`**: `Song.isBlockSelected`/
`blockDeselect` were previously hardcoded stubs (always-false/no-op,
documented as "no `CTrackClipboard` ported yet"), which is why `play()`'s
`PLAY_BLOCK` branch was characterized as unreachable. Now that real
block-selection state exists, `isBlockSelected`/`blockDeselect`/
`trackUp`/`trackDown`/`songUp`/`songDown`/
`songInsertCopyOrCloneOfSongLinesApply`/`play` all take an explicit
`TrackClipboard` parameter, and `play()`'s `PLAY_BLOCK` branch reads
`clipboard.getFromTo()`/`getSelSongLine()` for real - see `plans/JAVA_PORT_PLAN.md`
for the full ripple's write-up. `Undo` also gained a `TrackClipboard`
constructor field (matching its own established "store collaborators"
pattern, unlike `Song`'s per-call explicit-parameter style), since
`CUndo::PerformEvent()` calls `g_Song.BLOCKDESELECT()`.

Tests (`SongEditingTest`, extended) mirror `SongEditingTests.cpp`'s two
existing indirect tests exactly (2 tests). Verified with `mvn -o test`: 347
tests pass (+2), no regressions (including all of sub-batch 9's existing
tests, now threading a real `TrackClipboard` through unchanged call sites).

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

## Exporters that don't need the PokeyStream path - DONE

`CRmtExporter::ExportAsRMT`/`ExportAsStrippedRMTApply` and
`CASMFileExporter::ExportAsAsmApply`/`BuildRelocatableAsm`/
`ExportAsRelocatableAsmForRmtPlayerApply`/`ComposeRMTFEATstring` are all
ported, as two new free-standing classes (`RmtExporter`/`AsmFileExporter`,
matching C++'s own split - static methods taking `Song`/`Instruments`/
`Tracks` explicitly rather than being folded onto `Song`).

**A genuine C++ off-by-one found and fixed on both sides**:
`ExportAsStrippedRMTApply` passed `firstByteAfterModule` (`MakeModule`'s
documented *exclusive* upper bound) directly as `SaveBinaryBlock`'s
*inclusive* `toAddr`, writing one extra, always-zero trailing byte per
export - inconsistent with `ExportAsRMT`'s own correct
`firstByteAfterModule - 1` usage a few lines above it in the same file.
Fixed in C++ (`RmtExporterCore.cpp`) with a new regression test
(`ExportAsStrippedRMTApplyWritesExactlyTheModuleSizeWithNoExtraByte`,
comparing the exported block's exact byte length against an independently-
computed `MakeModule` call), and ported with the corrected convention from
the start in Java's `RmtExporter.exportAsStrippedRMTApply`.

**New `AtariIO.saveBinaryBlock`** (`CAtariIO::SaveBinaryBlock`'s
counterpart to the already-ported `loadBinaryBlock`) - returns the encoded
`byte[]` rather than writing to a stream, matching this port's established
byte-array-over-stream idiom.

**`Song.nameToString` widened from `private` to `public static`**: both
new exporter classes need the same "raw `char[]` name buffer to a
null-terminated `String`" conversion C++ gets implicitly from
`CString name = someCharPtr;` - reusing the existing helper (already used
internally by `Song`) avoided duplicating it.

**`ComposeRMTFEATstring` drops C++'s `trackSavedFlags` parameter**:
confirmed by inspection to be genuinely dead in the C++ body (only
`instrumentSavedFlags` is ever read) - carries no behavior to preserve.

**Pointer-offset-into-shared-buffer pattern** in `BuildRelocatableAsm`
(C++'s `unsigned char* buf = &exportDesc->mem[exportDesc->targetAddrOfModule];`)
becomes a copied scratch buffer (`Arrays.copyOfRange`), matching this
port's established treatment of the same pattern elsewhere (e.g.
`Song#decodeModule`) - every offset C++ computes relative to `buf` stays
numerically identical against the copy, letting the rest of the ~300-line
method (including its calls into the already-ported `AsmFileBuilder`) read
almost line-for-line off the C++ source.

Tests (`SongEditingTest`, extended) mirror `SongEditingTests.cpp`'s
corresponding sections exactly (6 tests). Verified with `mvn -o test`: 345
tests pass (+6), and a full C++ Release|x64 rebuild + `RmtTests.exe`: 372
tests pass (+1, the new regression test), 0 regressions on either side.

## Explicitly out of scope for this plan

- **TMC/MOD importers** (`CSong::ImportTMC`/`ImportTMCParseHeader`/
  `ImportTMCApply`, `ImportMOD`/`ImportMODParseHeader`/`ImportMODApply`,
  `IO_Importer.cpp`/`IO_ImporterCore.cpp`) - its own large undertaking on
  the scale of `SongEditing.cpp` itself, not a sub-batch of it. Now scoped
  in its own dedicated plan, `plans/JAVA_IMPORTER_PLAN.md` - not yet
  implemented.
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
4. Sub-batch 7 (module format buffers) - DONE.
5. Sub-batch 8 (module format streams) - DONE, including pulling sub-batch
   10 (`ClearSong`) forward as a genuine blocking dependency rather than a
   capstone. Both C++ bugs it surfaced (`LoadTxt`'s segment-boundary bug,
   `SaveRMW`/`LoadRMW`'s `sizeof` bug) are fixed.
6. Sub-batch 9 (navigation/playback) - DONE.
7. `CTrackClipboard` (new `TrackClipboard` class) - **partially DONE**,
   after the exporters below (see that entry's own write-up for why).
   Correction to the earlier "no test coverage" assessment: a real subset
   (`BlockSetBegin`/`BlockSetEnd`/`BlockDeselect`/`IsBlockSelected`/
   `BlockCopyToClipboard`/`BlockPasteToTrack`/`GetFromTo`/`Clear`/
   `BlockInitBase`, plus `Song`'s wrappers) was already tested indirectly
   via `SongEditingTests.cpp` and is now ported. Still needs new C++ tests
   before porting: `BlockAllOnOff`/`BlockExchangeClipboard`/`BlockClear`/
   `BlockRestoreFromBackup`/`BlockNoteTransposition`/
   `BlockInstrumentChange`/`BlockVolumeChange`.
8. Exporters (`CRmtExporter::ExportAsRMT`/`ExportAsStrippedRMTApply`,
   `CASMFileExporter::ExportAsAsmApply`/`BuildRelocatableAsm`/
   `ExportAsRelocatableAsmForRmtPlayerApply`/`ComposeRMTFEATstring`) - DONE,
   done ahead of item 7 above for that reason. The SAP-R/LZSS/WAV/XEX
   family stays deferred until `PokeyStream`'s real recording path is
   unblocked.
9. TMC/MOD importers - separate, dedicated plan, not part of this one -
   now scoped in `plans/JAVA_IMPORTER_PLAN.md` (not yet implemented).
10. `IO_Instruments.cpp`/`IO_Tracks.cpp`'s TXT/RMW per-instrument/per-track
    serialization - deferred out of sub-batch 8 (see its entry above),
    needed for a fully faithful `SaveTxt`/`LoadTxt`/`SaveRMW`/`LoadRMW`
    round trip of real song data, not just the module-level fields.

Each numbered sub-batch above is intended to be confirmed with the user
individually before implementation, per this project's established cadence
- this document is the scoping pass, not a commitment to implement
everything in it.
