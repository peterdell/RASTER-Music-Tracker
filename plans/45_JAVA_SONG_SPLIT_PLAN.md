# Plan 45: Split the Java Song class - the state stays, the file I/O moves out

Status: PROPOSED 2026-10-14. Requested as an opinion and a proposal on
splitting `Song.java`, whose suggested shape was a `SongLogic` class for
the load/save logic, "maybe as a friend of the Song class".

## The problem

`Song.java` is 4356 lines with 211 public members and 11 nested result
types - the largest file of the port by a factor of four. Its own
javadoc quotes the C++ characterization notes calling `CSong` "the God
Object", and the port inherited the shape.

The C++ program does not feel the size the same way, because `CSong`
is spread over translation units - in three waves. The fork's 2022
cleanup by CycoZA cut the concern tiers (`IO_Song.cpp`, `GUI_Song.cpp`,
`Song_DumpSong.cpp`); the user separated state from logic before the
port began (`SongExporter`/`SongExporterCore.cpp`,
`SongContainer`/`SongExport.cpp`, `SongUI.cpp`); and the
characterization effort added the testability tiers (`SongCore.cpp`,
`SongEditing.cpp` - cut by dependency cheapness, not by concern), which
is what isolated the dependencies:

| C++ file           | Lines | Concern                                      |
| ------------------ | ----- | -------------------------------------------- |
| `Song.cpp`         |   413 | the dependency-heavy rest (timers, dialogs)  |
| `SongCore.cpp`     |   303 | globals-free accessors and properties        |
| `SongEditing.cpp`  |  3292 | editing, module codec, playback (the "safe cluster") |
| `SongUI.cpp`       |  1085 | drawing the song's screen areas              |
| `GUI_Song.cpp`     |  2221 | keyboard/mouse input handling                |
| `IO_Song.cpp`      |   924 | load and save of the module file formats     |
| `Song_DumpSong.cpp`|    97 | the POKEY register dump                      |

C++ can spread one class over files; Java has no partial classes, so
the port piled every ported `CSong` method into one file. The split
that worked in C++ therefore has to become a *class* split in Java -
which is the stronger cut anyway, because a class boundary separates
dependencies for the compiler, not only for the reader.

## Opinion

1. **The split is worth doing, and the investment is on the Java
   side** - where it belongs while C++ is being phased out. C++ gets
   only a zero-cost mirror: a file move that keeps the two programs'
   file maps in sync (S5), decided by the user because the price is
   low.
2. **"Separate state from logic" fits this cluster precisely**, because
   the load/save methods are stateless transformations between bytes
   and the song's state. The port already has the pattern established:
   `SongExporter`, `RmtExporter` and `AsmFileExporter` are final
   classes with static methods that take the model as a parameter, and
   the I/O region's own private helpers are static already
   (`writeIntLE`/`readIntLE`/`indexOf`). The new class is the same
   species.
3. **But not under the name `SongLogic`.** Everything in the class
   except its fields is logic, so a class named after logic has no
   boundary: it would fill up the way `Song` did and become the next
   God Object with a newer name. The tier should be named for its
   concern - **`SongIO`** - which happens to be C++'s own name for the
   same tier (`IO_Song.cpp`), so the port's traceability convention
   (every javadoc names its C++ origin) extends from methods to the
   class itself.
4. **The pattern has a limit worth respecting**: the *editing*
   operations (insert/delete song lines, renumbering, boundary
   handling, the block operations) should stay on `Song`. They maintain
   the invariants of the state they sit next to; moving them out too
   would leave an anemic data holder whose every invariant is enforced
   from a distance. The carve that pays is by concern - codec and file
   I/O out, stateful editing stays - not "state here, all logic there".
5. **"Friend" translates to package-private.** Java's spelling of a
   C++ friend is package-private access inside
   `org.atari.raster.rmt.model`. Most of what the I/O methods touch is
   already public (`getSong()`, `getSongGo()`, `clearSong`, the speed
   setters); what is not gets a package-private accessor with a
   one-line comment naming `SongIO` as its reader - not a nested class
   (the file would stay huge), not public fields.

## Proposed steps

- **S1. Create `SongIO`** (`model` package, `public final class` with a
  private constructor, static methods taking `Song song` plus the
  collaborators each method already names, like `SongExporter`). Move
  the file-format block, today's lines 2448-3460 (~1000 lines):
  `testBeforeFileSave`, `saveTxt`/`loadTxt`, `saveRMW`/`loadRMW`,
  `loadRMT`, their result records (`LoadTxtResult`, `LoadRmwResult`,
  `LoadRmtResult`) and the static byte helpers, where only `SongIO`
  uses them. `charH4`/`charL4` stay on `Song` - the UI's drawing code
  calls them. `testBeforeFileSave` is the save-gate *check* - C++'s
  "Test" is Raster's name for it - and is renamed
  **`checkBeforeFileSave`** as it moves, in both programs (the user's
  decision of 2026-10-14): C++'s `CSong::TestBeforeFileSave()` becomes
  `CheckBeforeFileSave()` too, a five-site rename (`Song.h:284`, the
  body at `IO_Song.cpp:818`, its two callers there and
  `ScriptRunner.cpp:502`), with the javadoc naming the old C++ name
  once for traceability. It is also the one mover without a test in
  either program - C++'s body sits in `IO_Song.cpp` outside the test
  link, and Java exercises only its passing path through `save`. It
  brings a test with it, through `Messages`' existing test hook, for
  the error, warning and append-final-goto branches.
- **S2. Move the module codec with it**: `makeModule`/`decodeModule`
  (with `DecodeModuleResult`) and `songToAta`/`ataToSong`. They are the
  RMT format itself, called by `loadRMT`/`RmtExporter` - the javadoc
  notes that their C++ home is `SongEditing.cpp`, where the
  characterization batches put them for its dependency cut, so this is
  the one deliberate divergence from the C++ file assignment, by
  concern rather than by testability history - CycoZA's 2022 layout
  would have had them nearer `IO_Song.cpp`.
- **S3. Grant the access.** Whatever `SongIO` needs beyond the public
  API becomes a package-private accessor on `Song` (measured in the
  region: the song-line array and names, the two speeds, the active
  instrument, the loaded-file name and type - a handful). Each carries
  the comment that it is package-private for `SongIO`, the Java
  spelling of the C++ `friend` the user suggested.
- **S4. Update the call sites, no delegation shims.** `Song` keeps no
  forwarding methods - the callers move: `RmtExporter` (model),
  `SongFiles` (ui), and the tests (`SongTest`, `SongEditingTest`,
  `LivePlaybackTest`). The I/O tests themselves migrate to a
  `SongIOTest`, keeping their bodies.
- **S5. Mirror the cut in C++, keeping the two file maps in sync**
  (the user's decision of 2026-10-14: if the price is low, stay in
  sync - and in C++ the price is only a file move, since the methods
  remain `CSong` members wherever their bodies live). A new
  `SongIO.cpp`, named after the Java class, added to `Rmt.vcxproj`
  *and* to `RmtTests.vcxproj`'s link list, receives the same set:
  `SaveTxt`/`LoadTxt`/`SaveRMW`/`LoadRMW`/`LoadRMT`/`MakeModule`/
  `DecodeModule` from `SongEditing.cpp` and `SongToAta`/`AtaToSong`
  from `SongCore.cpp`, each leaving the usual
  "implemented in SongIO.cpp" pointer comment behind.
  Two things deliberately stay put: `IO_Song.cpp` keeps its
  MFC file-dialog wrappers and `CheckBeforeFileSave` (S1's rename of
  `TestBeforeFileSave` - its body lives there, outside the test link,
  so moving it adds nothing), and `GetSubsongParts` stays in
  `SongEditing.cpp` as its Java twin stays on `Song`. Apart from that
  rename, no signature changes, so no caller changes.
- **S6. Verify nothing behaves differently.** All 706 Java tests and
  all 451 C++ tests green, `CrossProgramExportTest` and
  `compare_exports.ps1` byte-identical on every export, the C++ diff
  pure motion plus the two project files. `Song.java` lands around
  3200 lines, and its javadoc's tier map is updated to say where the
  I/O went.

## What came out

- `Song.java` went from 4356 to 3340 lines. `SongIO.java` holds the
  codec and the file formats in 991 lines, `IOHelpers.java` the shared
  text and hex helpers in 71 - a find of the execution: `hexstr`,
  `trimstr`, `readLine`, `nextSegment`, `charH4`/`charL4` were never
  `Song`'s in C++, they are `IOHelpers.cpp`'s, used by the
  `Instruments`/`Tracks` TXT formats and the UI's hex drawing too, so
  they got the Java `IOHelpers` their originals always had instead of
  moving into `SongIO`.
- The `friend`: 20 declarations in `Song` are package-private now (the
  song-line arrays, the name, the speeds, the cursor/play state the RMW
  format round-trips, and `unsignedByte`, which turned out to serve only
  the formats and moved on into `SongIO`). Everything else `SongIO`
  reads goes through the existing public API.
- `SongIOTest` carries the 13 moved I/O tests (bodies unchanged) and
  five new ones for `checkBeforeFileSave`: the empty song, the two
  errors, the appended final "go to line 0" after its information box,
  and the warning question answered YES and NO - the branches no test
  in either program had ever run.
- **The C++ name was already reserved**, the way `IDD_FILE_PROPRTIES`
  was for plan 44: `SongIO.h`/`SongIO.cpp` existed, the header holding
  the file open/save/import/export dialog filter classes and an empty
  `class SongIO`, the source being a one-line include of it - so
  `Rmt.vcxproj` listed the file all along, and only the test project
  needed the new entry. The moved bodies landed in that reserved file.
- C++: `SongIO.cpp` now holds the nine moved bodies plus the
  `DEFINE_MAINPARAMS` macro pair, which had to travel with `SaveRMW`/
  `LoadRMW`; pointer comments in `SongCore.cpp`, `SongEditing.cpp`,
  `Song.cpp` and `IO_Song.cpp` all name the new home. The rename landed
  at all five `TestBeforeFileSave` sites.

## Found while verifying, pre-existing and still open

The cross-program comparison failed with the freshly built `Rmt.exe` and
passed with the stale one - which looked like this plan's regression and
is not: the same two commands fail at HEAD. `export wav` followed by
`export asm notes=freq` refuses the frequency read whenever the loaded
driver is of the v1 family, because the WAV exporter writes the POKEY
register shadows to Patch 16's fixed addresses
(`WaveFileExporter.cpp:70-82`), and `RMTPLAYR_TRACKN_AUDC` is $3180-$3187
- right across the v1 family's dispatch table at $3182. Plan 40's
plausibility check then correctly refuses the clobbered table. It only
surfaced now because an earlier aborted script run left
`TRACKERDRIVERVERSION = 1` in the test `rmt.ini` (`delta.rmtscript`
restores `patch16` on its last line, which an abort never reaches), and
every later `delta` run then started on the v1 driver. The setting is
restored; the WAV-export clobbering of a v1 driver's table is a real,
state-dependent bug of its own, fixed by
[plan 46](46_WAV_EXPORT_DRIVER_CORRUPTION_PLAN.md) - the Java port
never had it.

## Outlook, deliberately not this plan

If the cut proves itself, the same knife fits two more concerns later:
the playback/timing cluster (`play`, `playVBI`, `songPlayNextLine`,
`dumpSongToPokeyStream` - C++'s `Song_DumpSong.cpp` tier) and the
info/statistics cluster (`instrInfo`, `trackInfo`, `getSubsongParts`).
Whether they are worth their own classes is best judged once `Song` is
~3200 lines and the remaining mass is visible - splitting everything at
once would churn every caller in one commit for a shape nobody has seen
yet.

## Out of scope

- Any C++ change beyond S5's file move. S5 must not target the
  existing `IO_Song.cpp`: the test binary deliberately does not link it
  (MFC dialogs, the `Global.h` graph), which is why the
  characterization parked the module codec in `SongEditing.cpp` to
  begin with - a move there would unlink the 46-batch tests covering
  these methods.
- Any behavior change, however small - this is a move, and the byte
  comparisons must prove it.
- Splitting the editing operations out of `Song` (see opinion 4).
