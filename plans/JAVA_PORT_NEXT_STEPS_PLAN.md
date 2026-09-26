# Plan: next steps for the Java port

## Context

`plans/JAVA_PORT_PLAN.md`'s "Next steps" section (written immediately after
the thirty-third ported batch) claimed "no further Java-porting areas are
currently identified" - that claim was scoped only to
`plans/JAVA_SONGEDITING_PLAN.md`'s own tracked list and was too narrow. A
fresh audit of every plan doc plus a direct `src/cpp`-vs-`src/java` file
comparison (done to answer "what's next" properly) found two real
categories of remaining work, addressed as two phases below. This
supersedes that earlier claim.

## Phase A: close out the remaining model-layer gaps

Small, well-scoped, low-risk - continues this session's established
per-item workflow (C++ characterize first if untested, then port to Java,
verify with `mvn -o clean test` and/or the full C++ suite, update
`plans/NOTES.md`, ask before committing).

1. **`TracksEdit.cpp`'s 7 methods - DONE (2026-09-26).** `CTracks::
   DelNoteInstrVolSpeed`/`SetNoteInstrVol`/`SetInstr`/`SetVol`/`SetSpeed`/
   `SetEnd`/`SetGo` (`src/cpp/TracksEdit.cpp`, 158 lines) touched only
   `g_Undo`/`g_respectvolume` - no dialogs, no hardware - but had never had
   a dedicated test despite the file already being linked into
   `RmtTests.vcxproj`. Deferred twice in `plans/JAVA_PORT_PLAN.md` (Sixth
   and Seventh ported batches) back when `CUndo` was still a no-op test
   stub; unblocked once `Undo` was fully characterized and ported
   (`plans/UNDO_PLAN.md`). C++-characterized (30 new tests in
   `SongEditingTests.cpp`, all passing on the first run) then ported to
   `Tracks.java` (`delNoteInstrVolSpeed`/`setNoteInstrVol`/`setInstr`/
   `setVol`/`setSpeed`/`setEnd`/`setGo`, each taking an explicit `Undo`
   parameter, `setNoteInstrVol` also taking an explicit `respectVolume`
   boolean - matching this port's "C++ global -> explicit parameter"
   idiom). Full write-up in `plans/JAVA_PORT_PLAN.md`'s next ported batch
   entry.
2. **`PokeyStream`'s real recording path + a real CPU/POKEY emulator -
   DONE (2026-09-26), via ASAP.** `plans/JAVA_PORT_PLAN.md`'s twentieth/
   twenty-first ported batches deliberately ported only the pure
   state-machine subset of `AtariTrackerDriver`/`PokeyStream`. The real
   blocker wasn't a buffer-size decision - it was that `PokeyStream.Record()`
   needs real POKEY register *values*, which only exist because C++'s
   `Play()`/`SetPokey()`/`Silence()` run the RMT player routine through a
   real 6502 CPU (`C6502.h`/`.cpp`, wrapping an external native DLL,
   `sa_c6502.dll`) that has no Java equivalent.

   **Resolved via ASAP** (Another Slight Atari Player,
   `asap.sourceforge.net`), per the user's own design decision. Its
   official Java build/source (`lib/java/asap.jar`/
   `asap-8.0.0-java-src.zip`, see `lib/java/README.md`) is now vendored
   **as source** directly into `src/java/net/sf/asap/` (compiled as
   ordinary project sources via `pom.xml`'s existing `src/java` resource
   rule, which also picks up the vendored `.obx` player-routine resources
   automatically) - not as a rebuilt jar, since Maven already compiles
   `src/java` and this keeps the vendored+patched diff auditable via
   plain `git diff`. Two small, clearly-marked RMT additions on top of the
   otherwise-unmodified generated source (mirroring
   `src/cpp/asap/asap-patch.h`/`.cpp`'s same extension-point pattern for
   this same upstream library):
   - `ASAP.stepFrame()` - exposes the otherwise-private `doFrame()` for
     exact single-frame stepping (the public `generate()` API's own frame
     stepping is an internal buffering detail, not reliably 1:1 with
     video frames).
   - `ASAP.getPokeyRegisterShadow(chip, offset)` - returns the raw last-
     poked byte of a POKEY register (`PokeyChannel.audf`/`.audc`,
     `Pokey.audctl`/`.skctl`), which the public API has no way to read
     (real POKEY audio registers are hardware write-only). `Pokey.skctl`
     needed widening from `private` to package-private for this (matching
     its sibling fields' existing visibility).

   New `AsapEmulator` (`com.wudsn.tools.rmt.model`) wraps `net.sf.asap.ASAP`
   for RMT's specific need: `startRecording(byte[] moduleBytes)` loads and
   plays the module from the start, `stepFrame()`/`getRegisterShadow(chip,
   offset)` drive per-frame capture. Deliberately a separate class from
   `AtariTrackerDriver` (still permanently no-op, for live keyboard
   preview - a different operation with no whole module involved), not a
   replacement for it - see `AsapEmulator`'s own class javadoc.

   `PokeyStream.startRecording`/`record`/`finishedRecording` now have real
   bodies (transcribed directly from `PokeyStream.cpp`, with
   `driver.getRegisterShadow(chip, i)` replacing `GetByteAt(0xd200+i)`);
   `writeToFile(OutputStream, ...)` redesigned as `getFrameBytes(int
   frames, int offset)` returning `byte[]`, matching this port's
   established byte-array-over-stream idiom. `ASAP.load()`/`playSong()`/
   `generate()` alone (no patch needed) already suffice for
   `ExportWAV`-style real PCM audio generation, once that item is ported.
3. **`CSong::DumpSongToPokeyStream` - DONE (2026-09-26).** Ported to
   `Song.dumpSongToPokeyStream`, running the same `PlayVBI()`-driven loop
   as C++ but replacing every `g_AtariTrackerDriver->Play()` call with
   `AsapEmulator#stepFrame()`: since the Java port has no loaded-driver
   memory for a real JSR to execute, it instead exports the *current* song
   to a real RMT module byte array (via the already-ported `makeModule`/
   `RmtExporter.exportAsRMT` - a new step C++ doesn't need) and hands that
   to a fresh `AsapEmulator`, which independently decodes and plays it
   back, producing the same observable POKEY register writes entirely
   within ASAP's own emulated CPU.

   **`PokeyStream` threaded through `songPlayNextLine`/`playVBI`/`play`/
   `playBeat`**: each gained a `PokeyStream`-taking overload (pre-existing
   overloads delegate with `null`, unchanged for every other caller) so
   `dumpSongToPokeyStream` can consult `CallFromPlay`/`TrackSongLine`'s
   loop-detection hooks, exactly matching C++'s `m_pokeyStream`-consulting
   call sites. `CallFromPlayBeat`'s hook (inside `PlayBeat()`) is the one
   exception, still omitted: it only fires in `PLAY_BLOCK` mode, which
   `dumpSongToPokeyStream` never uses (always `PLAY_SONG`, the whole song).

   **A real bug found and fixed while debugging the first working test**:
   `dumpSongToPokeyStream` initially called `pokeyStream.finishedRecording()`/
   `channelControl.setAllChannelsOn()` at the end, matching an incorrect
   assumption about C++'s behavior - re-reading `Song_DumpSong.cpp` closely
   showed `DumpSongToPokeyStream()` never calls `FinishedRecording()` at
   all (that's a separate cleanup step, apparently never actually invoked
   anywhere in the production C++ call chain either - `CSongContainer`'s
   destructor is empty). Fixed by removing both calls, matching C++ exactly;
   documented the omission in the method's own closing comment.

   Test (`SongEditingTest`, extended):
   `dumpSongToPokeyStreamRecordsPokeyRegisterDataUntilTheLoopPoint` -
   builds a real 2-line looping song, runs it through the whole pipeline,
   and confirms not just plumbing (frame count, exact byte-length math)
   but **real, non-zero POKEY register content** - a stronger check than
   `SongEditingTests.cpp`'s own C++ test can make (that test's
   `AtariTrackerDriver::Play()` is *also* a no-op there, via the C++ test
   binary's own no-op JSR stub, so it only verifies plumbing). Needed a
   real, non-silent instrument envelope (a blank one makes ASAP's own RMT
   parser correctly reject the module as "no songs found" - matching real
   playback semantics, not a bug). Verified with `mvn -o clean test`: 393
   tests pass (+1), no regressions.
4. **Three of the five dependent export methods - DONE (2026-09-26).**
   `CSongExporter::ExportSAP_R`/`ExportLZSS` and `CWaveFileExporter::ExportWAV`
   are ported. `SongContainer`/`SongExport` (the lazy-caching wrapper pair)
   were deliberately **not** ported - pure caching optimizations, not
   needed for correctness; each export calls
   `Song#dumpSongToPokeyStream` directly instead, achieving the same
   observable result without the cross-export caching.
   - **New `SapFileExporter.exportSapR`**: writes `SapFile.export()`'s
     header (type `"R"`) followed by `PokeyStream#getFrameBytes`'s raw
     register bytes up to the loop point - a direct, one-to-one port.
   - **New `SongExporter.exportLzss`**: compresses the full/intro/loop
     sections via the already-ported `CompressLzss`. `ExportCompactLZSS`
     was deliberately **not** ported - its own C++ source self-describes
     as "TODO: What is this? Currently unused?" and its body has genuinely
     dead logic and writes a diagnostic text dump, not a real export
     (matches `plans/EXPORTLZSS_PLAN.md`'s own "low priority, hacked up"
     characterization). Surfaced and worked around a real,
     previously-unexercised `CompressLzss` edge case: a zero-length
     section (e.g. a short loop's `thirdCountPoint`) throws
     `ArrayIndexOutOfBoundsException` there - already a known,
     deliberately-preserved "fragile contract" per `CompressLzss`'s own
     class javadoc (C++ has silent UB for malformed lengths instead), so
     guarded at the new caller instead of reopening that decision.
   - **New `WaveFileExporter.exportWav` - a deliberate idiomatic
     substitution, not a line-for-line port**: C++'s `ExportWAV` replays
     the already-recorded `PokeyStream` bytes into a *separate* software
     POKEY audio-synthesis engine (`CXPokey`/`PokeyRenderer.h/.cpp`/
     `PokeyCore.cpp` - confirmed C++-tested via `ExportWAV`'s own test
     during the Phase A item 5 sanity sweep, but never ported to Java).
     Porting that second, redundant synthesizer from scratch would
     duplicate real engineering effort for no behavioral gain, since
     `AsapEmulator`'s underlying ASAP already contains one: this port
     instead exports the song to a real RMT module (same step as
     `dumpSongToPokeyStream`) and lets `ASAP.load`/`playSong`/`generate`/
     `getWavHeader` independently render it. The observable result (a
     valid WAV file that sounds like the song) is the same; only which
     software POKEY emulator computes the samples differs.
   - Tests (`SongEditingTest`, extended): `exportSapRWritesTheHeaderAndRealPokeyStreamData`
     (confirms real, non-zero POKEY bytes past the header - stronger than
     the C++ test can check, same reason as `dumpSongToPokeyStream`'s own
     test), `exportWavRendersAValidRiffWaveFile`,
     `exportLzssStaysBelowTheCompressedSizeThresholdForAMinimalSong`
     (mirrors `SongEditingTests.cpp`'s own honest "never crosses the
     threshold in this test environment" finding, for a different reason -
     real ASAP audio, but a minimal 2-line loop still doesn't have enough
     distinct content to compress past 16 bytes). Verified with
     `mvn -o clean test`: 396 tests pass (+3), no regressions.
   - **`ExportSAP_B_LZSS` - DONE (2026-09-26) too**, alongside the
     `AtariIO.loadBinaryFile`/`VUPlayer` machinery it needed. New
     `AtariIO.loadBinaryFile(byte[] data, byte[] memory)` (reuses the
     already-tested `loadBinaryBlock` in a loop, matching C++'s own
     `LoadBinaryFile` structure exactly, minus the file-open step itself -
     the caller reads the file via `Files.readAllBytes`, matching this
     class's byte-array idiom). New `VUPlayer` class ports only the dozen
     memory-address constants `PatchMemoryForSAP_B` actually needs (not
     all of `lzssp.h`'s several hundred), plus `patchMemoryForSapB` itself -
     including a pre-existing C++ oddity (`memory[LZSS_POINTER]` written
     eight times to the *same* address, only the last write survives)
     preserved as-is, already self-flagged by the original author's own
     `// TODO: Why same address?` comments, not something to unilaterally
     fix during a port. `SapFileExporter.exportSapBLzss` reads the real,
     checked-in `rmt/resources/players/vu_player_v2.obx` from disk
     (relative to Maven's working directory, which is always the repo
     root) - the first real on-disk file dependency in this Java test
     suite. Test (`SongEditingTest`, extended):
     `exportSapBLzssLoadsTheRealResourceAndWritesPatchedMemory` - passed on
     the first real run. Verified with `mvn -o clean test`: 397 tests pass
     (+1), no regressions.
   - **`ExportXEX_LZSS` - DONE (2026-09-26) too, completing this item.**
     New `XexFile` class (a plain mutable settings struct, matching
     `CXEXFile` minus its two dead fields - `songname`/`currentTime` are
     set by `InitFromSong` but never read anywhere in the export path
     itself). New `SongExporter.exportXexLzss`: parses
     `Song#getSubsongParts`'s existing hex-token-string result into
     subtune songline numbers, calls `dumpSongToPokeyStream` once per
     subsong (`PLAY_FROM` mode, each starting at its own songline - unlike
     every other export, which uses `PLAY_SONG` once for the whole song),
     bruteforces the best `SapROptimization` per section (new
     `bruteforceOptimalLzss`, trying all 8 variants and keeping the
     shortest - C++'s own recompute-the-winner-a-second-time step is
     unneeded here since `CompressLzss#compress` already returns a fresh
     array per call), and reconstructs the VUPlayer XEX binary
     byte-for-byte, including the Atari-screen-code text conversion (new
     `strToAtariVideo`) and the NTSC/PAL region patch. Confirmed
     `CRmtAtariBinaries::GetVUPlayerBinary`'s embedded-resource load and
     `exportSapBLzss`'s real on-disk `std::ifstream` load read the exact
     same real file in C++ (`resources/players/vu_player_v2.obx`) with no
     observable difference - so this reuses `SapFileExporter`'s existing
     `VU_PLAYER_PATH`/loading logic rather than porting a second,
     redundant resource-loading mechanism. Test (`SongEditingTest`,
     extended): `exportXexLzssLoadsTheRealResourceAndWritesReconstructedBinary` -
     passed on the first real run despite the method's size. Verified with
     `mvn -o clean test`: 398 tests pass (+1), no regressions.

   **Phase A item 4 (the five dependent export methods) is now fully
   complete**: all five real, meaningful export methods are ported
   (`ExportCompactLZSS` correctly excluded as self-described dead code).
5. **Closing sanity sweep - DONE (2026-09-26), clean.** Ran the
   `BROADER_SURVEY_PLAN.md`-style pass described above: extracted every
   `ClassName::MethodName` defined across all 44 non-stub `.cpp` files
   linked into `RmtTests.vcxproj` (387 unique names), then checked each for
   a direct-call-by-name match anywhere in `src/cpp/test/*.cpp`. 133 came
   back with no direct match - each was then traced by hand (its own
   header for `private`/`static`, and a repo-wide grep for its real
   callers) rather than taken at face value, since the raw heuristic has
   two known blind spots that make most "misses" false positives:
   - **Differently-named public wrappers**: e.g. `ClipboardCore.cpp`'s
     `BlockSetBegin`/`BlockSetEnd`/etc. are only ever reached through
     `Song`'s differently-cased `BLOCKSETBEGIN`/`BLOCKSETEND` wrappers
     (already tested); `lzss_sap.cpp`'s `Compress`/`Optimize` and all of
     its internal `add_bit`/`lzop_*`/`match` free functions are only
     reached through `CCompressLzss::LZSS_SAP()` (the one call
     `LzssTests.cpp` actually makes); `RmtCommandLineInfo.cpp`'s
     `GetSwitchName`/`GetSwitchValue` are private, called only from the
     tested `ParseParam()`.
   - **Unqualified same-class internal calls**, invisible to a
     cross-file/`this->`-style grep: e.g. `AtariIO.cpp`'s `LoadWord` is
     called bare (`LoadWord(in, fromAddr)`, no `this->`) from the already-
     tested `LoadBinaryBlock`; every method in `PokeyCore.cpp` (7/7
     flagged) is called from `PokeyRendererCore.cpp`'s `RenderSoundV2()` -
     confirmed genuinely exercised for real via `ExportWAV`'s test, which
     writes and validates an actual temp `.wav` file (`WaveFile.cpp`'s
     3/3 flagged methods are the same story - real file I/O, exercised for
     real, just not by literal name in the test file itself).
   - **`PokeyController.cpp` (55/55 flagged, the only whole-file 100% miss
     besides `WaveFile.cpp`) is genuine, deliberate Category A**: its
     constructor runs as part of every `CSong` construction (so it's
     "linked and instantiated," unlike `TracksEdit.cpp` before this
     session), but its 40+ `OnIncrease*`/`OnDecrease*`/`OnToggle*` methods
     are the Pokey-explorer debug submenu's real MFC command handlers -
     confirmed only ever called from `PokeyView.cpp`/menu routing, matching
     `plans/UI_SURVEY_PLAN.md`'s own explicit note that this submenu is
     "developer/diagnostic UI, not core end-user functionality." Not a
     testing gap; a UI file that happens to get linked for its harmless
     constructor.
   - The **one exception, already tracked, not new**: `PokeyStream.cpp`'s
     `StartRecording`/`FinishedRecording` are genuinely untested - this is
     item 2 above, not a new finding.

   **Conclusion: no new gaps.** `TracksEdit.cpp` was the one genuinely
   overlooked file; every other flagged name resolves to indirect coverage
   or deliberate, already-documented exclusion. The C++
   characterization-testing phase's model-layer scope is complete except
   for the ASAP-dependent items above.

**Phase A is now fully DONE (2026-09-26)**: all five items closed -
`TracksEdit.cpp`'s 7 methods, real CPU/POKEY emulation via ASAP
(`PokeyStream`/`dumpSongToPokeyStream`), all five dependent export methods
(`ExportSAP_R`/`ExportLZSS`/`ExportWAV`/`ExportSAP_B_LZSS`/`ExportXEX_LZSS`),
and the closing sanity sweep. The Java port's model layer has no further
known-unported, real, meaningful C++ behavior left - see
`plans/JAVA_PORT_PLAN.md`'s "Next steps" section for what's left overall
(Phase B, the entire UI layer).

**Confirmed permanently out of scope on the C++ side, not revisited by
this phase**: `BlockEffect` (no extractable logic), the `FileXxx` dialog
family (real `CFileDialog`, no separable core), `TimerRoutine`/
`ChangeTimer`/`StopTimer`/`ReInitSound` (real audio-hardware/timer-thread
coupling). None of these have a Java equivalent path available short of
building real UI/hardware abstractions, which is Phase B's problem, not
Phase A's.

## Phase B: the Java UI port

Not started at all - `com.wudsn.tools.rmt.ui` doesn't exist yet as a
package. By far the largest remaining phase: `plans/UI_SURVEY_PLAN.md`'s
own inventory covers dozens of dialogs/views/commands versus the ~45
already-ported model classes.

**Already resolved** (`plans/UI_SURVEY_PLAN.md`'s "Open questions"
section):
- Keep the exact pixelated bitmap-font look, not a modernized text
  render - needs the `IDB_GFX` glyph sheet exported from the Win32
  resource to a plain image file for the Java port to blit from.
- Keep the `Pokey`/`AtariView` debug overlays and the `Pokey` register-
  poking debug submenu from the start, not deprioritized.
- Defer MIDI input entirely (`Midi_Song.cpp`'s ~600 lines of dispatch
  logic, including an admittedly-unfinished "channel 15 control surface"
  hack) - not part of the initial Java UI scope.

**Still open, needed before writing UI code**:
- **Rendering/repaint model.** Today's app always redraws everything
  every frame on a timer (`CRmtScreenLayout` fixed-pixel grid). A Java
  port can keep that model (simplest, most faithful, matches every pixel
  of behavior) or move to an event-driven repaint-on-change model (more
  idiomatic for a GUI toolkit, but a real, deliberate behavior change that
  would also need the timer-driven `RefreshScreen()` polling loop
  rethought). Undecided.
- **Java UI toolkit choice.** Not decided anywhere in the plan docs -
  Swing, JavaFX, and a lower-level 2D canvas library are all viable for
  the fixed-size sprite-sheet blitting this app needs; `dis6502`'s own
  prior Java port (this project's precedent for build/package layout) may
  have already made this choice for a similar rendering need and is worth
  checking first.
- **Two UX patterns with no default toolkit equivalent**: the click-
  positioned popup dialogs (`COctaveSelectDlg`/`CVolumeSelectDlg`/
  `CInstrumentSelectDlg`, positioned at the mouse click rather than
  centered) and the 5 custom per-context cursors. Both need a deliberate
  design, not something that falls out of a standard dialog/cursor API.
- **A handful of dialogs UI_SURVEY_PLAN.md flagged but didn't trace to
  their consuming model method**: `CRenumberTracksDlg`/
  `CRenumberInstrumentsDlg`, `CChangeMaxtracklenDlg`,
  `CChannelsSelectionDlg`, `CExportStrippedRMTDialog`, `CExportAsmDlg`,
  `CExportRelocatableAsmForRmtPlayer`, `CTracksLoadDlg` - worth a
  follow-up trace pass once those specific flows become relevant to
  porting order, not before.

**Note on `plans/FILE_TIERING_STRATEGY.md`'s open "recombination vs
composition" question for `CSong`**: already effectively resolved in
practice, not by an explicit decision - `Song.java` mechanically
recombines every safe C++ tier (`SongCore.cpp`/`SongEditing.cpp`/
`Song_DumpSong.cpp`/`SongExportV2.cpp`/`IO_Song.cpp`) into one ~3,700-line
class (option 1, "mechanical recombination"), and that's worked fine so
far. Worth noting explicitly so a future session doesn't reopen this
question from scratch, but Phase B still needs its own answer for the
genuinely separate UI-facing classes (`CSongUI`, `CSongTimer`, the dialog
layer) - those were never tiers of `CSong` to begin with and map onto
their own new Java classes regardless.

**Recommended approach**: don't attempt the whole UI in one pass. Start
with the smallest possible vertical slice - a main window, read-only
tracker-grid rendering, and keyboard navigation (no editing, no dialogs,
no export flows, no debug overlays) - as a working proof of concept that
answers the toolkit and repaint-model questions above with real code
before committing to a design for anything bigger. This mirrors how the
model port itself started with `Fraction` (the smallest, dependency-free
class) rather than `Song`. Once that slice works, break the rest of
Phase B into its own dedicated plan (`plans/JAVA_UI_PORT_PLAN.md`),
following the same per-batch cadence used throughout the model port -
not attempted in the same sitting as this roadmap.

## Suggested order

1. Phase A, items 1-5, in the order listed - each is independently small
   and low-risk, and item 5's sanity sweep is only meaningful once 1-4 are
   actually done.
2. Once Phase A closes, resolve Phase B's open questions (a design
   conversation, not code) and write `plans/JAVA_UI_PORT_PLAN.md`.
3. Begin Phase B's vertical-slice proof of concept.

Each item is still intended to be confirmed with the user individually
before implementation, matching this project's established cadence.
