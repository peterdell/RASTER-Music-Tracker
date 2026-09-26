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
2. **`PokeyStream`'s real recording path + `AtariTrackerDriver`'s
   remaining register-poking methods - real CPU/POKEY emulation needed,
   now solved via ASAP (2026-09-26 analysis).** `plans/JAVA_PORT_PLAN.md`'s
   twentieth/twenty-first ported batches deliberately ported only the pure
   state-machine subset of each class. The real blocker isn't a buffer-size
   decision (a manually-doubled `byte[]` or a `ByteArrayOutputStream`
   handles that trivially) - it's that `PokeyStream.Record()` needs real
   POKEY register *values*, which only exist because C++'s `Play()`/
   `SetPokey()`/`Silence()` run the RMT player routine through a real 6502
   CPU (`C6502.h`/`.cpp`, wrapping an external native DLL,
   `sa_c6502.dll`). The Java port's `AtariTrackerDriver.play()`/
   `setPokey()`/`silence()` are permanently no-ops for exactly this reason
   (no Java equivalent of that DLL) - so even a fully-ported `Record()`
   would just capture zeroed/garbage frames today.

   **Resolved by the user's own design decision**: use ASAP (Another
   Slight Atari Player, `asap.sourceforge.net`) as the real CPU/POKEY
   backing instead. `lib/java/asap.jar` and its full generated source
   (`lib/java/asap-8.0.0-java-src.zip`) are now vendored (see
   `lib/java/README.md`) - the same multi-language-codegen library this
   repo already vendors a C build of (`src/cpp/asap/`), used today only by
   `RmtTest.cpp`'s `/TEST` utility. Analysis of the vendored Java source
   (package `net.sf.asap`):
   - `Cpu6502.java` is a **complete, portable, pure-software 6502 CPU
     emulator** with its own 64KB `memory[]` - unlike C++'s `C6502`, it has
     no native/DLL dependency at all. `Pokey`/`PokeyChannel`/
     `PokeyPair.java` are a matching dual-POKEY chip emulator. This is
     exactly the missing piece.
   - The **public API** (`ASAP.load`/`playSong`/`generate`, etc.) is
     built around "load a module, produce PCM audio" - sufficient, as-is
     and unmodified, to replace `CWaveFileExporter::ExportWAV` entirely:
     export the song to RMT module bytes (already implemented via
     `RmtExporter`), hand them to `ASAP.load()`/`playSong()`/`generate()`,
     and let ASAP's own real emulation render the WAV samples. No CPU/POKEY
     porting needed for this export specifically.
   - The public API has **no way to read a raw POKEY register's last-
     written value** (needed for `Record()`'s SAP-R-style per-frame dump,
     i.e. `ExportSAP_R`/`ExportSAP_B_LZSS`/`ExportLZSS`/
     `ExportCompactLZSS`/`ExportXEX_LZSS`). Confirmed this value exists
     internally: `PokeyChannel.audf`/`.audc` and `Pokey.audctl` are
     package-private fields (not `private`) storing the exact raw byte
     last poked (verified in `PokeyChannel.setAudc()` - the stored
     `this.audc = data` is unmasked), and `PokeyPair.basePokey`/
     `.extraPokey` are package-private too. Only `ASAP.java` itself keeps
     its own `cpu`/`pokeys` fields `private`, blocking a same-package
     "sibling" accessor class from reaching them from outside `ASAP`.
   - **Planned fix**: add one new method directly to the vendored
     `ASAP.java` source (small, localized, mirrors the existing
     `src/cpp/asap/asap-patch.h`/`.cpp` extension-point pattern already
     used for this same upstream library on the C++ side), e.g.
     `public int getPokeyRegisterShadow(int chip, int offset)` (`chip`
     0/1 selects `pokeys.basePokey`/`.extraPokey`; `offset` 0-8 selects
     AUDF1/AUDC1/.../AUDF4/AUDC4/AUDCTL, matching `CPokeyStream::Record()`'s
     own `0xd200+i`/`0xd210+i` addressing exactly) - then compile and
     vendor a custom-patched `asap.jar` build from that modified source in
     place of the unmodified one. Not yet applied - this item hasn't
     started.
   - **Frame-stepping**: `ASAP.java` has no public "run one frame" method
     (`doFrame`/`do6502Frame`/`call6502Player` are all `private`, called
     only from `generate()`). Driving `Record()`-equivalent per-frame
     capture will mean calling `generate()` for exactly one video frame's
     worth of samples at a time (sample count = sample rate / NTSC-or-PAL
     frame rate) and reading the new register-shadow accessor after each
     call - the exact sample-per-frame arithmetic is an implementation
     detail for when this item is actually picked up, not resolved here.
3. **`CSong::DumpSongToPokeyStream` - Java port.** Already C++-
   characterized and confirmed provably-bounded/safe
   (`plans/SAP_LZSS_WAV_XEX_PLAN.md`, "DONE, safe" section) but never
   ported to `Song.java`. Blocked only on item 2 above.
4. **The five dependent export methods.** `CSongExporter::ExportSAP_R`/
   `ExportSAP_B_LZSS`/`ExportLZSS`/`ExportCompactLZSS`/`ExportXEX_LZSS`
   and `CWaveFileExporter::ExportWAV` are all already C++-characterized
   ("Implementation - DONE" in `plans/SAP_LZSS_WAV_XEX_PLAN.md`/
   `plans/EXPORTLZSS_PLAN.md`/`plans/EXPORTWAV_PLAN.md`) but not yet
   ported - each needs items 2-3 above first. `SapFile`/`CompressLzss`
   (the lower-level format helpers these methods call) are already ported
   (`plans/JAVA_PORT_PLAN.md`'s twelfth/seventeenth batches), so this is
   mostly wiring once the `PokeyStream` blocker clears.
5. **Closing sanity sweep.** Once items 1-4 land, re-run a
   `BROADER_SURVEY_PLAN.md`-style pass, but checking "linked into
   `RmtTests.vcxproj` yet still missing method-level tests" rather than
   "not linked at all" - the `TracksEdit.cpp` gap above shows the original
   survey's "is the file linked" question wasn't sufficient by itself to
   catch everything. If this sweep comes back clean, the C++
   characterization-testing phase and the model-layer Java port are both
   genuinely complete.

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
