# Plan 60: A container class for the RMT module (format version 1)

Status: M1 DONE 2026-10-28, M2 and M3 DONE 2026-10-29; M4 open. Decided 2026-10-28 (user), see the open decisions
below for the four answers and the addendum on nesting.

A class in the Java port that represents an RMT module as
`doc/rmt_format.md` describes it (the GitHub copy the user named,
`raster-atari-org/.../1.36/doc/rmt_format.md`, is the same document).
For a start, only the parts of format version "1" that RMT uses today:
the binary load header, the module header struct, the instruments
table, the instrument struct with its table of notes and envelope, the
track addresses tables, the track struct and the track sequence table.
The version "2" draft (metadata segment, `PLR`, `TUN`) is out of scope
here; the design only leaves room for it (M5).

## Why

Today the module exists only as bytes in a 64K scratch array, produced
and consumed by code that knows the layout implicitly:

- `SongIO.makeModule()` writes the layout from a `Song` (header, then
  the instrument pointer table, the track low/high pointer tables, the
  instrument data, the track data, the song lines).
- `SongIO.decodeModule()` parses it back into a `Song` - the first
  parser of the layout.
- `AsmFileExporter.buildRelocatableAsm()` parses the same bytes a
  second time, with its own pointer-table sanity checks, to emit
  relocatable assembler source.
- `SongIO.loadRMT()` / `RmtExporter.exportAsRMT()` handle the file
  level: the first binary block (the module) and the optional second
  block with the song and instrument names.
- `RmtExporter.exportAsStrippedRMTApply()` builds the module at another
  address by rebuilding it from the `Song`.
- The ports' `ModuleDescription` record (`SongFiles`) carries the
  result around as `mem` + two addresses + two flag arrays.

So the format's knowledge is spread over two parsers, one writer and a
raw-bytes record. A container makes the format one executable thing:
the spec in `doc/rmt_format.md`, the class, and the tests that prove
they agree on every module in the repository. It is also the natural
base for format version "2" (the metadata segment and the `TUN`/`PLR`
segments proposed in the same document), which cannot be bolted onto
`makeModule()`'s byte juggling.

## Design

Package `org.atari.raster.rmt.model`, Java only. C++ keeps its
`MakeModule`/`DecodeModule` (it is being phased out); the cross-program
export comparison keeps proving that both programs still write the
same bytes.

### `RmtModule` - the module, as content

Mirrors the spec's structs, one Java type per struct - nested in
`RmtModule` as `RmtModule.SongLine`, `RmtModule.Track`,
`RmtModule.Instrument` (with `Instrument.Envelope`) and the
`RmtModule.FormatException` (user, 2026-10-28, during M1: the parts have
no meaning outside the module, and the nesting mirrors the spec; static
nested classes, whose names shadow the editor's `Instrument`/`Track`
inside `RmtModule` - the container needs neither, and its own
`NOTE_TABLE_MAX`/`ENVELOPE_MAX` are asserted equal to the editor's):

- **Header** (the "Module Header Struct"): `tracks` (4 or 8, from
  `RMT4`/`RMT8`), `trackLength` (1-256, the byte's 0 meaning 256),
  `songSpeed`, `instrumentSpeed`, `formatVersion`. The four table
  addresses are NOT content: they follow from the layout and the load
  address, so serializing computes them and parsing validates them.
- **Instruments**: a sparse map, instrument number to
  `RmtModuleInstrument` - the "Instrument Struct": the 12 parameter
  bytes as named fields (`tlen`, `tgo`, `elen`, `ego`, the
  `tspd`/`tmode`/`ttype` byte, `audctl`, `vslide`, `vmin`, `delay`,
  `vibrato`, `fshift`, the unused byte), the table of notes
  (`byte[]`), the envelope (a list of 3-byte entries: volume,
  portamento/distortion/command/filter, XY). A missing number is the
  spec's null pointer.
- **Tracks**: a sparse map, track number to `RmtModuleTrack` - the
  "Track Struct" event stream as bytes, with a validating walker that
  recognizes the four cases (note, volume only, pause, speed/jump/end)
  so a track's length and its end marker are checked, not trusted. (A
  typed event list can come later; the walker is what the codec and
  the asm export need.)
- **Song**: the "Track Sequence Table" as a list of lines, each either
  the track numbers of its 4 or 8 channels (`255` = empty) or a "go to"
  (the target line; the pointer is derived on serialization, as
  `songToAta()` derives it today).

Operations:

- `parse(byte[] mem, int fromAddr, int endAddr)` - the exact checks of
  `decodeModule()` (the `RMT` signature, `4`/`8`, speed not 0,
  instrument speed 1-8, version at most 1) plus the pointer-table
  sanity checks `buildRelocatableAsm()` performs today, as one
  validation with named reasons.
- `write(byte[] mem, int loadAddress)` / `toBytes(int loadAddress)` -
  the layout exactly as `makeModule()` writes it. Byte-identity with
  `makeModule()` for the same content is the acceptance criterion.
- `relocate` is implicit: content is address-free, so writing at
  another load address is the stripped export's relocation.
- No `Song` knowledge: the conversions live in `RmtModuleCodec`
  (decision 4).

### `RmtModuleCodec` - between `Song` and `RmtModule`

`fromSong(Song, SongIOType)` and `toSong(RmtModule, Song)` (the channel
count is the song's since plan 61),
reusing the per-entity codecs that exist
(`Instruments.instrToAta`/`ataToInstr`/`ataV0ToInstr`,
`Tracks.trackToAta`/`ataToTrack`, `SongIO.songToAta`/`ataToSong`)
rather than re-implementing them. The save-all-versus-save-used rule of
`makeModule()` (`SongIOType.RMT` keeps every non-empty track and
instrument) is decided here, in `fromSong`. Keeping it out of
`RmtModule` keeps the container reusable outside RMT (ASAP, ASMA
tooling), where the version "2" format is headed.

### `RmtModuleFile` - the `.rmt` file

The binary load file: the first segment (6-byte header `$FFFF`, start,
end; the module) and the optional second segment with the song name and
the saved instruments' names, null-terminated, in index order. The
spec does not describe this second segment yet, although every `.rmt`
RMT saves has it and a "stripped" module is one without it - see M4.
`read(byte[])` / `write()`; a list of segments, so version "2"'s
further segments have a place later.

### What stays

`SongIO.makeModule()`/`decodeModule()` keep their signatures and become
wrappers over the container (M2), so the many callers and the
`ModuleDescription` record do not change in this plan.

## Steps

- [x] M1. DONE 2026-10-28. `RmtModule` (with its nested structs),
      `RmtModuleFile`, `RmtModuleCodec` and `RmtModuleTest` (11 tests):
      all six fixtures read and write back byte for byte; relocation to
      $6000 round-trips; `fromSong` + `write` equals `makeModule()` for
      both iotypes at two addresses; `toSong` yields `decodeModule()`'s
      song state; `fileFromSong` equals `exportAsRMT()`; typed struct,
      walker and rejection tests. Found on the way: four of the six
      fixtures are stripped (Delta, RebbStars, Why Do You Dance With Me,
      testsfx - only Captain Future Theme and Makary's carry names), and
      testsfx holds 10 instruments (its README said 9) plus eleven
      one-event audition tracks and a one-line looping song (its README
      said none - corrected to "nothing that plays"). Original text:
      `RmtModule`, `RmtModuleFile` and `RmtModuleCodec` with
      `parse`/`write`/`read` and `fromSong`/`toSong`, plus the tests
      that make the spec executable: every `.rmt` under
      `test-resources/songs` parses, and writing it back at its own
      load address reproduces the file's first block byte for byte;
      `fromSong` + `write` equals `makeModule()` for the test songs;
      `parse(makeModule output)` + `toSong` yields the song state
      `decodeModule()` yields. One commit. The program does not use the
      classes yet (decision 3).
- [x] M2. DONE 2026-10-29. `makeModule()` and `decodeModule()` keep
      their signatures and are wrappers over `RmtModuleCodec`/`RmtModule`
      (`loadRMT()` goes through `decodeModule()`); `exportAsRMT()` and
      `exportAsStrippedRMTApply()` write through `RmtModuleFile`
      (`exportAsRMT` lost its now-redundant `Instruments` and saved-flags
      parameters: the saved instruments are the ones the module holds).
      `SongIO`'s and `SongFiles`' `0x4000`/`65536` literals are
      `RmtModuleFile.DEFAULT_LOAD_ADDRESS`/`Memory.SIZE`. Two behavior
      changes, both by design: format version 0 modules are rejected like
      any other module the container does not represent (the user: the
      version 0 path does not make sense; `ataV0ToInstr` stays for
      version 0 `.rti` files), and a song with a speed outside the
      header's range no longer makes a module - `SongIOTest`'s fixture
      had song speed 0, which the old writer encoded and the old decoder
      rejected; it gets the speeds `clearSong()` sets. 749 tests and the
      cross-program export comparison green.
- [x] M3. DONE 2026-10-29. `AsmFileExporter.buildRelocatableAsm()`
      reads the module once with `RmtModule.parse` (its own pointer
      re-reading and sanity checks are gone) and takes every offset from
      the new `RmtModule.Layout` (`layout()`), which `write()` now lays
      the module out by, so the layout is computed in one place. The
      data dump still emits bytes, from `module.toBytes`. A module
      without instruments or tracks is refused, as before. Proof: a
      temporary dump of all 384 outputs (6 fixtures x RMT/stripped x
      xasm/atasm x the 8 relocation label combinations x full/size info)
      before and after - identical, the 32 refusals (testsfx stripped,
      which has neither) included - plus 749 tests green.
- [ ] M4. `doc/rmt_format.md`, version "1" section, gets what the
      container had to know and the document does not say: the optional
      second segment with the names (and that a module without it is
      what RMT calls "stripped"), format version 0 instruments (the
      `ataV0ToInstr` path the decoder still accepts), the "go to" line's
      four bytes with the `$FF` padding of the remaining channels, and
      the track end conventions the walker enforces.
- [ ] M5. Not in this plan, but kept possible by M1's segment list:
      version "2" (`RMX1` metadata, `TUN`, `PLR`).

Proof for every step: `mvn -o test` green, the cross-program export
test unchanged; no asm or C++ changes, so no byte-identity chain.

## Open decisions

All four decided by the user 2026-10-28, plus two addenda the same day:

5. **Constants for the format's attributes and values** (user's request,
   with the proposal's recommendations accepted): on `RmtModule` -
   `SIGNATURE`, `CHANNELS_MONO`/`CHANNELS_STEREO`, `HEADER_LENGTH`,
   `FORMAT_VERSION`, `MAX_TRACK_LENGTH`, `MAX_SONG_SPEED`,
   `MAX_INSTRUMENT_SPEED`, `MAX_INSTRUMENTS`, `MAX_TRACKS`,
   `MAX_SONG_LINES`; on `SongLine` - `GO_TO_MARKER`, `EMPTY`,
   `GO_TO_LENGTH`; on `Track` - the event codes (`MAX_NOTE`,
   `VOLUME_ONLY`, `PAUSE`, `CONTROL`, `CONTROL_SPEED`/`JUMP`/`END`, the
   two masks, `MAX_LENGTH`); on `Instrument` - `PARAMETERS_LENGTH`,
   `ENVELOPE_ENTRY_LENGTH`, `MAX_NOTE_TABLE_ENTRIES`,
   `MAX_ENVELOPE_ENTRIES`, `MAX_TABLE_SPEED`, `MAX_VIBRATO`,
   `MAX_VOLUME`; on `RmtModuleFile` - `DEFAULT_LOAD_ADDRESS`. The
   container defines its own limits (the format's), and a test asserts
   they equal the editor's constants; `DEFAULT_LOAD_ADDRESS` is the
   file's, not the content's; today's literals in `SongIO`, `Tracks`
   and `SongFiles` are replaced in M2.
6. **Naming style** (user): `MAX_` is a prefix, never a suffix; hex
   values are lower case (`$fe`, `0xfe`) in code, comments and messages.

The original four:

1. **Names: `RmtModule` and `RmtModuleFile`** (plain `Module` collides
   with `java.lang.Module`; `RmtModuleV1` was the alternative for a
   version "2" sibling).
2. **Depth of the model: typed fields** for the header, the instrument
   parameters, the envelope entries and the song lines; **the table of
   notes and the track event stream as validated bytes**. A fully typed
   track event model can follow when a caller needs it.
3. **Scope: M1 only first.** M2 and M3 wait for the review of M1.
4. **The `Song` conversions in a separate `RmtModuleCodec`**, keeping
   `RmtModule` free of `Song` knowledge.

## References

- `doc/rmt_format.md` (the specification; its version "2" draft and the
  open `TUN` proposal of 2026-10-28).
- `src/java/org/atari/raster/rmt/model/SongIO.java` (`makeModule`,
  `decodeModule`, `loadRMT`, `songToAta`/`ataToSong`),
  `RmtExporter.java`, `AsmFileExporter.java` (`buildRelocatableAsm`),
  `AtariIO.java` (`loadBinaryBlock`/`saveBinaryBlock`),
  `Instruments.java`/`Tracks.java` (the per-entity codecs),
  `ui/SongFiles.java` (`ModuleDescription`, `saveRmtBytes`).
- plans/45_JAVA_SONG_SPLIT_PLAN.md (how `SongIO` came to be),
  plans/36_MODULE_PROVENANCE_PLAN.md (why the format needs to carry
  more than it does - the motivation for version "2").
