# Plan: Java port of `IO_ImporterCore.cpp` (TMC/MOD import)

## Context

Flagged in `plans/JAVA_SONGEDITING_PLAN.md` as its own dedicated scoping
pass ("`IO_ImporterCore.cpp` alone is over 1000 lines... needs its own
dedicated scoping plan if/when tackled"). This is that pass - read both
`IO_Importer.cpp` (193 lines) and `IO_ImporterCore.cpp` (1745 lines) in
full before writing this plan, per this effort's established discipline.

**Good news found while scoping**: unlike `CTrackClipboard`'s remaining
7 methods, this surface is *already fully C++-tested* - `plans/IO_IMPORTER_PLAN.md`
(a prior phase of this same overall effort) already did the C++-side
triage: split each `Import*()` into a thin, still-real, still-dialog-showing
wrapper (`ImportTMC()`/`ImportMOD()`, staying in `IO_Importer.cpp`, **not**
part of this plan - confirmed real `CImportTmcDlg`/`CImportModDlg`/
`CImportTmcFinishedDlg`/`CImportModFinishedDlg` MFC dialogs, matching this
whole effort's established "real dialog stays deferred" pattern) plus a
dialog-independent `ParseHeader()`/`Apply()` pair per format
(`IO_ImporterCore.cpp`), each with real C++ tests already in
`SongEditingTests.cpp`:

- `ImportTMCParseHeaderFailsOnATruncatedFile`
- `ImportTMCParseHeaderSetsTheSongName`
- `ImportTMCApplyConvertsANoteIntoTheDestinationTrack`
- `ImportMODParseHeaderFailsOnATruncatedHeader`
- `ImportMODParseHeaderFailsOnUnrecognizedIdentification`
- `ImportMODApplyConvertsANoteIntoTheDestinationTrack`

So this is a clean "port already-tested C++" candidate, same shape as the
exporters batch - just a much bigger one.

## Java dependency check - all already ported

Every non-import-specific method/constant both `Apply()`s touch already
exists in this Java port, confirmed by direct lookup (no gaps found):
`Tracks.getTrack`/`clearTrack`/`isEmptyTrack`/`compareTracks`/
`trackOptimizeVol0`/`setMaxTrackLength`, `Instruments.getInstrument`/
`getName`, `Notes.getNote`/`NOTESNUM`, `Instrument.INSTRUMENT_NAME_MAX_LEN`/
`NOTE_TABLE_MAX_LEN`/`PAR_*` parameter constants, `EnvelopeParameter.*`,
`Song.clearSong`/`setTracks`/`tracksAllBuildLoops`/
`songClearUnusedTracksAndParts`/`getSongInfoPars`/`setSongInfoPars`,
`AtariIO.loadBinaryBlock`, `TrackFlag`/`SongIOType`. No new Java
infrastructure is needed beyond the importer classes themselves.

## Design decisions for the Java port

**New free-standing classes, not `Song` methods** - `TmcImporter`/
`ModImporter`, mirroring `RmtExporter`/`AsmFileExporter`'s precedent
(static methods taking `Song`/`Tracks`/`Instruments` explicitly) rather
than further bloating `Song.java`. C++'s own split (`CSong::ImportTMCParseHeader`/
`ImportTMCApply` are `CSong` *methods*, not a separate class like
`CRmtExporter`) doesn't need to be mirrored exactly here - `Song`'s "God
Object" size is already a named concern in this port's own class javadoc,
and nothing in either method needs `Song`'s private state beyond what's
already reachable through public accessors/records.

**`std::istream&` becomes `byte[]` - and MOD's "continued stream access"
wrinkle disappears entirely.** C++'s `ImportMODApply()` needed the raw
stream (not just the parsed header) because `TImportMODHeader.mem` only
holds the header+pattern data - the sample audio data lives further into
the file and gets `seekg`'d to directly inside `Apply()`. In Java, once
the *whole file* is a `byte[]` (matching `loadRMT`/every other binary
format's established idiom), there is no separate "stream" to seek in -
`ModImporter.apply()` just takes the same `byte[]` `parseHeader()` did and
indexes into it at the computed sample offset. This is a genuine
simplification versus the C++ split, not something to preserve.

**Struct-to-record mappings** (matching `AtariIO.BinaryBlockResult`/
`Song.DecodeModuleResult`'s established "output-parameters become a
record" idiom):
- `TImportTMCHeader(boolean ok, byte[] mem, int bfrom)`
- `TImportTMCResult(int numOfTracks, int nonEmptyInstruments, int songLines, int optiTracks, int optiBeats, int clearedTracks, int truncatedTracks, int truncatedBeats)`
- `TImportMODHeader(int errorCode, byte[] head, byte[] mem, int modLength, int chnls, int modSamples, int song, int patStart, int patternSize, int songLen, int restartPos, int maxPat)` -
  `head`/`mem` are both plain `byte[]` (C++'s `std::vector<BYTE> mem` needs
  no special treatment in Java - it's already resizable-at-construction
  only, i.e. read-only after `parseHeader()` returns, same as a fixed
  array)
- `TImportMODResult(int destNum, int nonEmptySamples, int songLines, int optiTracks, int optiBeats, int clearedTracks, int truncatedTracks, int truncatedBeats)`

**TMC-only helpers** (`CConvertTracks`, `TSourceTrack`, `TDestinationMark`,
`TInstrumentMark`) become a package-private helper class local to
`TmcImporter` (e.g. `TmcImporter.ConvertTracks`, with its three small
struct-like fields as private arrays/nested records) - confirmed unused by
MOD import, matching `IO_IMPORTER_PLAN.md`'s own C++-side finding.

**MOD-only helpers** (`TMODInstrumentMark`, `AtariVolume()`) become a
private nested/static helper inside `ModImporter` for the same reason.

**The real complexity: `ImportMODApply()`'s `goto`-driven control flow.**
Unlike every `goto` restructured so far in this port (`playBeat`'s single
retry loop, `instrPaste`'s shared-branch flags), `ImportMODApply()`'s inner
per-line/per-channel loop has *three* forward-jumping labels sharing
overlapping logic (`TonePortamento`/`Effect3`/`NoteByPortamento` - a
tone-portamento state machine with two different entry points converging
on shared "apply this note" code) plus two loop-breaking labels at the
very end (`OutOfTracks`/`OutOfSongLines`, effectively "break both nested
`for` loops and skip to post-processing"). This needs real, careful
design work when this batch is actually implemented - likely a small
private helper method for the portamento state machine (returning a
`note`/`vol` pair, called from both entry points) plus a labelled outer
loop (matching `playBeat`'s already-established `while(true)`-labelled
pattern) for the two loop-breaking labels. Flag this explicitly as the
part of Batch B that deserves the most attention, not a quick mechanical
transcription like the rest.

**`new BYTE[samplen]`/`delete[]` (a scratch buffer for the sample's
waveform, used only to derive an approximate volume envelope) becomes a
plain `byte[]`** - no manual memory management needed in Java, and no
change in size/lifetime semantics either.

**Guard-only `SendWarningMessage`/`SendErrorMessage` calls** (TMC: one,
corrupted-file; MOD: three header-validation via `errorCode` plus six more
inside `Apply()` itself - too-many-tracks, too-many-songlines, a sample
seek/read failure ×2, a final module-length mismatch) all drop the message
call itself, matching `instrChangeApply`'s established reasoning - the
return value/`errorCode` alone conveys success/failure, and no test
reaches the `Messages`-dependent branches anyway.

## Suggested batching

Same order as the C++-side plan, for the same reason (TMC is smaller and
structurally simpler - no continued-stream wrinkle, no `goto` state
machine):

1. **Batch A: TMC - DONE** (`TmcImporter.parseHeader`/`apply`, `ConvertTracks`
   helper). ~715 C++ lines; single guard-only failure mode; no `goto`s to
   restructure (`ImportTMCApply`'s per-track parsing loop is straight-line
   with early `break`s, which Java's `break`/`continue` already handle
   natively - no special design needed there). See its own write-up below.
2. **Batch B: MOD - DONE** (`ModImporter.parseHeader`/`apply`, `InstrumentMark`/
   `atariVolume` helpers). ~940 C++ lines; three-code header-validation
   guard; the `goto`-driven portamento state machine and dual loop-break
   labels described above needed dedicated design attention, not a
   mechanical transcription. See its own write-up below.

Each batch mirrors its corresponding pair of existing C++ tests (3 tests
per batch, 6 total) into a new `ImporterTest.java` (or added to
`SongEditingTest.java`, matching this project's established
consolidate-everything-into-one-fixture convention - to be decided at
implementation time based on which reads more naturally, since neither
importer needs the rest of that fixture's setup).

## Batch A - DONE: TMC import

Implemented as planned above: `TmcImporter.parseHeader`/`apply`, with
`ConvertTracks`/`SourceTrack`/`DestinationMark`/`InstrumentMark` as private
nested classes. Went into its own new `TmcImporterTest.java` (read more
naturally than folding into `SongEditingTest`'s much bigger fixture, since
neither test needs anything from it beyond `Tracks`/`Instruments`/`Song`/
`Undo`).

- **`ImportTMCParseHeader`/`ImportTMCApply` transcribed almost line-for-line**
  once every `mem[...]` byte read got the established `ub()`
  (unsigned-byte) treatment - confirmed correct by all 3 tests passing on
  the first run, including the full `ApplyConvertsANoteIntoTheDestinationTrack`
  conversion test (hand-derived byte layout copied directly from the C++
  test).
- **One genuinely signed byte read**: `preladeni` (the per-column
  transposition amount) comes from C++'s `(char)mem[...]` cast, not the
  usual unsigned read - Java's raw `byte[]` indexing already sign-extends
  by default, so `int preladeni = mem[i];` (no masking) reproduces this
  exactly, unlike every other byte read in this method.
- **The TMC envelope command 5 (`rand()`) call** uses
  `ThreadLocalRandom.current().nextInt(256)` instead - confirmed
  unreachable in the one existing test (its buffer defines no
  instruments), and a byte-exact match to C++'s `rand()` sequence isn't
  meaningful to preserve (different PRNG/seeding) - see `TmcImporter`'s
  own class javadoc.
- **`g_Instruments.Update(i)`** ("write to Atari RAM") omitted throughout,
  matching `Instruments`'s own established omission of the same call.
- A handful of `(BYTE)(...)` truncating casts in genuinely untested
  branches (the volume-fadeout/vibrato-table math, reached only when a
  saved instrument exists - not the case in the one existing test) are
  translated as `(int) (...) & 0xff`, a best-effort match given the
  original C++ itself relies on implementation-defined/UB double-to-BYTE
  conversion behavior in those same untested paths.
- One added safety guard (`line >= 1` before writing `songGo[line - 1]`):
  C++'s equivalent (`if (m_songgo[line - 1] < 0)`) would read
  `m_songgo[-1]` (out-of-bounds) if a TMC file decoded to zero songlines -
  not reachable by any current test, but Java would throw
  `ArrayIndexOutOfBoundsException` where C++ silently corrupts adjacent
  memory, so this one spot got a defensive bounds check rather than a
  faithful reproduction of the C++ UB.
- Verified with `mvn -o test`: 350 tests pass (+3, all in the new
  `TmcImporterTest`), 0 regressions. No C++ changes in this batch.

## Batch B - DONE: MOD import

Implemented as planned: `ModImporter.parseHeader`/`apply`, with
`InstrumentMark` (matching C++'s `TMODInstrumentMark`) as a private nested
class and `atariVolume` as a private static helper. Went into its own new
`ModImporterTest.java`, matching Batch A's file organization.

- **C++'s "continued stream access" wrinkle really did disappear**, exactly
  as predicted during scoping: `ParseHeaderResult` just carries the whole
  file (`byte[] data`), and `apply()` indexes directly into it at the
  computed sample offset (`smpfrom`) with a plain bounds check, replacing
  C++'s `seekg`/read-and-check-`gcount()` dance entirely.
- **The `goto`-driven tone-portamento state machine restructured as
  planned**: the `TonePortamento:` label's body became the private
  `tonePortamento()` helper (called once directly for an empty cell, once
  from the tone-portamento-effect branch when the pitch class hasn't
  changed yet - C++ itself never re-enters this logic more than once per
  line/channel, so extracting it to a plain method call is safe); the
  `NoteByPortamento:` label became a shared `if (noteWritten) {...}` block
  after both call sites converge. The `Effect3:` label (shared
  portamento-speed math for effects 1/2/3/5) needed no helper at all - just
  one `if` covering all four effect codes, since it's a linear "compute
  inputs once, then run the shared tail" shape, not a multi-entry state
  machine. **This exact state machine isn't dynamically exercised by
  either language's test** (both pass `portamento=false`) - verified
  correct by careful structural comparison against the C++ source instead,
  documented explicitly in `ModImporter`'s class javadoc as a known test
  gap.
- The `OutOfTracks:`/`OutOfSongLines:` labels (adjacent, both meaning "stop
  this pass's song/pattern processing entirely") became a single labelled
  `break songLoop;`, matching `playBeat`'s established loop-labelling
  idiom from sub-batch 9.
- **A discovered, likely-pre-existing dead statement in the C++ source**:
  the sample-envelope loop branch computes `lopend` (where a sample's loop
  should end, in envelope columns) but the C++ body's next statement is a
  bare `rmti->parameters[PAR_ENV_LENGTH];` - reads and discards, never
  assigning `lopend` anywhere. Looks like a forgotten assignment, but the
  *intended* target wasn't obvious enough to guess safely (unlike the
  earlier `sizeof`/off-by-one fixes this session, which had one unambiguous
  correct value) - flagged to the user, who chose to leave it characterized
  as-is on both sides rather than guess. Not reachable by the existing
  test (its sample has no loop).
- Verified with `mvn -o test`: 353 tests pass (+3, all in the new
  `ModImporterTest`), 0 regressions. No C++ changes in this batch.

This completes both batches of `plans/JAVA_IMPORTER_PLAN.md`.

## Explicitly out of scope for this plan

- **`ImportTMC()`/`ImportMOD()`** (`IO_Importer.cpp`) - real MFC dialog
  wrappers (`CImportTmcDlg`, `CImportModDlg`, `CImportTmcFinishedDlg`,
  `CImportModFinishedDlg`), confirmed on the C++ side to have no
  extractable logic beyond what `ParseHeader`/`Apply` already isolate -
  stay deferred indefinitely, matching every other real-dialog wrapper in
  this port.

## Next step

Neither batch has been implemented yet - this document is the scoping
pass only, matching how `plans/JAVA_SONGEDITING_PLAN.md` was itself
scoped before any of its sub-batches were built. Confirm with the user
before starting Batch A's implementation, per this project's established
per-batch confirmation cadence.
