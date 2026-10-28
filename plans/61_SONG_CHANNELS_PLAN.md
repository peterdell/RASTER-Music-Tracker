# Plan 61: The channel count is a property of the song

Status: DONE 2026-10-29 (Java only). Proposed 2026-10-28, naming decided
by the user the same day (see Design and Naming). M1 (the model, commit
22ed858), M2 (the session, c1acc37), the counts as `RmtModule.Channels`
with a `Song.Channels` proxy for locality (c10f053, the user's addition),
M3 (the texts). 749 tests green after each step; `tracks4_8` survives in
the sources only inside `{@code g_tracks4_8}` origin notes. Done before
plan 60 M2, as recommended.

The Java port keeps the mono/stereo indicator (`tracks4_8`, 4 or 8)
outside the model, as a public field of the UI's `RmtSession`, and
threads it as an explicit parameter through every model method that
needs it. This plan moves it into `Song`, where the rest of the song's
data lives, and removes the parameter plumbing.

## Why

`tracks4_8` is a leftover of the port's treatment of the C++ global
`g_tracks4_8`, which C++ itself marks as misplaced
(`src/cpp/Global.h`: `extern int g_tracks4_8; // TODO Move out`).
Plan 13's rule for globals - "a global the model reads becomes an
explicit parameter; the model stores nothing" (plan 13, lines 929,
1098, 1225) - produced today's shape:

- `Song.getTracks(int tracks4_8)` returns its argument,
  `Song.isStereo(int tracks4_8)` compares it, and `Song.setTracks(int)`
  is an identity function whose javadoc says "the caller is
  responsible for storing it".
- Because the model stores nothing, the value lives in the UI:
  `RmtSession.tracks4_8` ("the one mutable model-wide value: the model
  never stores it ... so the UI keeps it here"), written through
  `RmtSession.setTracks4_8`, which reproduces C++'s one side effect,
  `CSong::SetTracks()` -> `ReInitSound()`. The C++ "hardcoded to 8 to
  prevent ReInitSound() before RMT finished initializing" workaround
  came along.

But every fact about the value is a fact about the song's data: the
RMT module header stores it (the channel digit, `RmtModule.channels()`,
`CHANNELS_MONO`/`CHANNELS_STEREO`), it is read on load and written on
save, the mono/stereo switch (`songswitch4_8`) edits the song by
clearing the R1-R4 columns, and the editor's `song[i][j]` array is
sized for 8 channels either way. Only "re-initialize the POKEY pair
when it changes" is a session concern, and that is a reaction to a
model change, not the value's home.

What the current shape costs (counted 2026-10-28):

- 478 occurrences of `tracks4_8` in 52 Java files; 112 in `Song`
  (40 method signatures), 64 in `SongIO`, 28 in `SongInput`, 27 in
  `SongFiles`, 26 in `RmtCommands`, 17 each in `SongUI` and
  `MouseInput`, 14 each in `AsmFileExporter`, `RmtModuleCodec` and
  `RmtModuleTest`.
- "Value in, new value out" shapes that exist only to carry the number
  back to the session: `clearSong`, `fileNewApply` and `setTracks`
  return it; `songswitch4_8(current, new, undo, messages)` takes two
  and returns one; the records `SongIO.LoadTxtResult(tracks4_8)`,
  `LoadRmwResult(success, tracks4_8, uiParams)`,
  `LoadRmtResult(success, tracks4_8, moduleAddress)`,
  `DecodeModuleResult(version, tracks4_8)`, `ModImporter.ApplyResult`
  and `TmcImporter.ApplyResult` carry it; `RmtModuleCodec.toSong`
  returns an `int` for the same reason; `Instruments.attachAtari` takes
  a `() -> tracks4_8 == 8` supplier instead of asking the song.
- The field is public and mutable, so `AudioEngine.renderFrame` carries
  a "safety net" (`RmtSession` javadoc: "a safety net for a direct
  tracks4_8 write") that compares the CPU's stereo flag with the value
  at the top of every frame; three UI tests write the field directly.
- Every model test threads a local `tracks4_8` through its calls.

The C++ program needs nothing: it is scheduled for removal
(memory: no C++-only work), so no mirror change is made there, and the
C++ tests' `g_tracks4_8 = 4` stubs stay as they are.

## Design

### The model

`Song` stores the channel count:

```java
/**
 * The physical POKEY channels the song is written for, across one or two
 * POKEYs: the song columns L1-L4 and R1-R4 are CH1-CH4 of the left and,
 * in stereo, the right POKEY. {@link RmtModule#CHANNELS_MONO} (4) or
 * {@link RmtModule#CHANNELS_STEREO} (8), the module header's channel digit.
 */
private int channels = RmtModule.CHANNELS_STEREO;

public int getChannels()        // replaces getTracks(int tracks4_8)
public boolean isMono()         // channels == CHANNELS_MONO
public boolean isStereo()       // channels == CHANNELS_STEREO; replaces isStereo(int tracks4_8)
public void setChannels(int)    // replaces setTracks(int): stores, no return
```

Decided 2026-10-28 (user):

- A count, not a `boolean stereo`: the format stores a count, every
  consumer loops or divides by it, and a quad-POKEY extension (16
  channels) is neither mono nor stereo. The header's single digit caps
  a version 1 module at 9, which is the version 2 draft's problem, not
  the model's.
- `isMono()` and `isStereo()` compare for equality with the two counts,
  not `> 4`: with more layouts there is no "the other one". A quad
  driver would add `isQuad()` beside them.
- `isStereo()` is for the few places that mean "there is a right
  POKEY": the volume-nibble packing in `Instruments`, the POKEY pair
  initialization (`AtariCpu`), the WAV export. Everything that is
  really about the number of columns uses `getChannels()`. M1 audits
  the 55 current `isStereo(`/`getTracks(` call sites in 21 files with
  that rule and converts each to the count where it is one in disguise.
- `setChannels` validates: the valid counts are a fact of the tracker
  driver, not of the song - every current driver supports exactly 4 or
  8 - so it rejects anything else, in one place a quad driver would
  widen.

The default is 8, as `g_tracks4_8` starts at 8 in C++ and `clearSong(8)`
is the first thing `RmtSession` does; tests that need mono say so.

Every model method loses its `int tracks4_8` parameter and reads
`channels` instead: `Song` (40 signatures, among them `getTracks`,
`isStereo`, `clearSong`, `fileNewApply`, `songswitch4_8`, `playVBI`,
`markTfUsed`, `getSubsongParts`, `dumpSongToPokeyStream`, `trackLeft`/
`trackRight`, the block operations), `SongIO` (`makeModule`,
`decodeModule`, `loadRMT`, `loadRMW`, `loadTxt`, `saveTxt`, `saveRMW`,
`checkBeforeFileSave`), `RmtModuleCodec` (`fromSong`, `fileFromSong`,
`toSong`), `RmtExporter`, `AsmFileExporter`, `SapFile`,
`SapFileExporter`, `XexFile`, `SongExporter`, `PokeyStream`, `VUPlayer`,
`TrackClipboard`, `ModImporter`, `TmcImporter`.

The result records drop the field: `LoadTxtResult` disappears
(`loadTxt` returns nothing - its only content was the channel count),
`LoadRmwResult(success, uiParams)`, `LoadRmtResult(success,
moduleAddress)`, `DecodeModuleResult(version)` becomes a plain
`int version` (the record was introduced because C++'s `int` return
conflated "failed" with "version 0"; that part is kept by returning
`-1` for failure, or the record stays with one field - decided during
M1 by which reads better at the call sites), `ModImporter.ApplyResult`
and `TmcImporter.ApplyResult` without `tracks4_8`.
`songswitch4_8(newChannels, undo, messages)` returns `boolean` (the
user confirmed and the switch happened). `RmtModuleCodec.toSong`
returns nothing; `fromSong(song, iotype)` and
`fileFromSong(song, loadAddress)` read the song.

`Instruments` keeps its `boolean stereo` parameters
(`setEnvelopeVolume`, `instrToAta`, `ataToInstr`, `ataV0ToInstr`,
`update`): `Instruments` does not know the `Song` (the `Song` owns the
`Instruments`), and a boolean input is an honest parameter, not a
global in disguise. `attachAtari`'s supplier becomes `song::isStereo`,
passed by `RmtSession` once the song exists - or `attachAtari(atari,
song)`; decided in M1.

### The session

`RmtSession.tracks4_8` and `setTracks4_8` go. The reaction C++ has in
`SetTracks()` becomes one method,

```java
/** {@code ReInitSound()} when the POKEY pair is out of step with the song's video standard or channel count. */
public void syncSound()
```

which is what `AudioEngine.renderFrame`'s "safety net" already does;
the engine calls it at the top of the frame as before, and
`SongFiles`/`SongDialogs` call it synchronously after every load, new,
import and mono/stereo switch, where C++ re-initialized inside
`SetTracks()` - so playback and the exports that render through the
driver (`dumpSongToPokeyStream`, WAV, SAP-R) see a POKEY pair that
matches the song without waiting for a frame. `setNTSC` goes through
the same method. The "starts at 8" workaround is no longer needed:
nothing re-initializes until the session is built.

UI code that read `session.tracks4_8` reads `session.song.getChannels()`
(or `isStereo()`); `TracksOrderDialog`, `ChannelsSelectionDialog`,
`ExportStrippedRmtDialog`, `ExportRelocatableAsmDialog`,
`InstrumentChangeDialog`, `BlockEffectDialog` take the song or the
count as they do now - whichever each already has.

### Naming

`channels` is the glossary's term (`doc/rmt_tracker.md`: "Track - A
logical voice ... Channel - A physical output to create sound. Tracks
can be assigned to channels for replay"), matches `RmtModule.channels()`
and `CHANNELS_MONO`/`CHANNELS_STEREO`, and is what `doc/rmt_format.md`
("RMT4" for mono, "RMT8" for stereo) and the manual ("channel 1 to 8",
"active channel") count. By that glossary `tracks4_8` was the wrong
term: a song has up to 254 tracks whatever its channel count.
`tracks4_8` was C++'s name for the global and disappears with it (the
javadoc cross-references `{@code g_tracks4_8}` stay where they explain
a ported method's origin). `getTracks` (the ported `CSong::GetTracks()`
name) is not kept beside `getChannels`: "tracks" means the `Tracks`
entity everywhere else in the port.

On the chip the channels are CH1-CH4 (`AUDF1`-`AUDF4`); "channel 8"
exists on no chip, it is RMT's flat numbering of the right POKEY's CH4
(the manual's `CONTROL+1-8`). M3 adds one sentence to the glossary's
"Channel" entry saying so: RMT numbers the channels L1-L4 and R1-R4,
flat 1-8 in the key table, each being CH1-CH4 of one POKEY.

## Milestones

- M1 - the model: `Song.channels` with its accessors; the parameter
  removed from every model method, record and codec; the model tests
  (`SongEditingTest`, `SongIOTest`, `SongTest`, `InstrumentsTest`,
  `RmtModuleTest`, `ModImporterTest`, `TmcImporterTest`, `SapFileTest`)
  without their local `tracks4_8`. The session still compiles by
  storing the song's value until M2. `mvn -o test` green.
- M2 - the session: `RmtSession.tracks4_8`/`setTracks4_8` removed,
  `syncSound()` in place, every `ui` reader on the song; the ui tests
  (`AudioEngineTest`, `LivePlaybackTest`, `ImportExportTest`,
  `SongFilesTest`, `SongDialogsTest`, `RmtSessionTest`,
  `MouseInputTest`, `RmtMainMenuTest`, and the rest) adjusted - the
  three direct `session.tracks4_8 = ...` writes become
  `session.song.setChannels(...)`, and `AudioEngineTest`'s "writer that
  doesn't call reInitSound" case keeps proving the top-of-frame sync.
  `mvn -o test` green.
- M3 - the texts: `RmtSession`'s and `Song`'s class javadoc paragraphs
  about the explicit-parameter treatment rewritten; `RmtModule`'s
  "RMT's `tracks4_8 == 4`" comments on `CHANNELS_MONO`/`STEREO` point
  at `Song.getChannels()`; the glossary sentence on channel numbering
  in `doc/rmt_tracker.md` (see Naming); `plans/13_JAVA_PORT_PLAN.md` is
  history and stays as written, with one note at its `g_tracks4_8`
  rule (line 929) pointing here; `plans/NOTES.md` if it mentions the
  treatment.

## Proof

- `mvn -o test` after each milestone (749 tests on 2026-10-28, with no
  test deleted - only its plumbing).
- `grep -rn tracks4_8 src/java` finds only the `{@code g_tracks4_8}`
  origin references in javadoc after M3.
- The byte-for-byte fixture tests of `RmtModuleTest` and the
  `SongIOTest`/`ImportExportTest` round trips are the regression net
  for the codec and exporter signatures: an export that silently used
  the wrong channel count fails them.
- No asm, C++ or driver change: the plan-51 proof chain is not needed.

## Open decisions

- `DecodeModuleResult`: one-field record or `int` with `-1` for
  failure (M1, by the call sites).
- `attachAtari`: supplier `song::isStereo` or the `Song` itself (M1).
- Whether `syncSound()` is also the right place to keep the
  video-standard half of the check that `setNTSC` triggers today
  (recommended: yes, one method for both).
