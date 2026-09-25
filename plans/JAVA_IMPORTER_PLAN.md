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

1. **Batch A: TMC** (`TmcImporter.parseHeader`/`apply`, `ConvertTracks`
   helper). ~715 C++ lines; single guard-only failure mode; no `goto`s to
   restructure (`ImportTMCApply`'s per-track parsing loop is straight-line
   with early `break`s, which Java's `break`/`continue` already handle
   natively - no special design needed there).
2. **Batch B: MOD** (`ModImporter.parseHeader`/`apply`, `TMODInstrumentMark`/
   `AtariVolume` helpers). ~940 C++ lines; three-code header-validation
   guard; the `goto`-driven portamento state machine and dual loop-break
   labels described above need dedicated design attention, not a
   mechanical transcription.

Each batch mirrors its corresponding pair of existing C++ tests (3 tests
per batch, 6 total) into a new `ImporterTest.java` (or added to
`SongEditingTest.java`, matching this project's established
consolidate-everything-into-one-fixture convention - to be decided at
implementation time based on which reads more naturally, since neither
importer needs the rest of that fixture's setup).

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
