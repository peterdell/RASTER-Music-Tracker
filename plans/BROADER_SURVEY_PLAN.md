# Plan: Broader survey of remaining untested files

## Context

`plans/SONG_IO_SONG_REMAINING_PLAN.md` and `plans/SAP_LZSS_WAV_XEX_PLAN.md`
closed out every deferred-hazard category *within* `Song.cpp`/`IO_Song.cpp`/
`ExportV2`. This is a wider survey of every other production `.cpp` file in
`src/cpp/` not yet linked into `RmtTests.vcxproj`, to find what - if
anything - is left worth the same characterization-testing treatment before
considering this phase of the project done.

Method: for each unlinked file, grepped its `g_*` global references and
skimmed its method signatures/bodies (not a full line-by-line read for every
file - deep reads only for the promising candidates), then categorized it.

## Category A - confirmed out of scope (real UI, hardware, or mega-wiring)

- **Pure UI/dialogs/views**: `AboutDialog.cpp`, `AtariView.cpp`, `Canvas.cpp`,
  `CanvasXY.cpp`, `Commands.cpp` (real `LoadAccelerators`/toolbar creation),
  `effectsdlg.cpp`, `exportdlgs.cpp`, `filenewdlg.cpp`, `GUI_Instruments.cpp`,
  `GUI_Song.cpp`, `importdlgs.cpp`, `MainFrm.cpp`, `OptionsDialog.cpp`,
  `PokeyView.cpp`, `RmtDoc.cpp`, `RmtView.cpp`, `SAPFileExportDialog.cpp`,
  `Shell.cpp`, `SongUI.cpp` (mouse/keyboard/drawing event handlers -
  `g_mem_dc`/`g_mouse`/`g_view`/etc.), `TracksControl.cpp`,
  `TuningDialog.cpp`, `TypedComboBox.cpp`.
- **Real hardware/DLL coupling**: `C6502.cpp` (`g_c6502_dll` - external CPU
  emulator DLL), `Pokey.cpp` (POKEY sound-chip DLL wrapper),
  `PokeyRenderer.cpp` (`g_lpds`/`g_lpdsbPrimary` - real DirectSound buffers,
  already the confirmed hazard behind `TimerRoutine`/`ExportWAV`),
  `Midi_Song.cpp`/`RmtMidi.cpp` (confirmed via read: real
  `midiInGetNumDevs()`/`midiInGetDevCaps()` MIDI hardware enumeration).
- **`Global.cpp`**: the app's entire global-state wiring (every long-tail
  global referenced anywhere touches this file) - deliberately never linked
  wholesale anywhere in this effort; individual externs are declared instead
  wherever one specific global is needed. Out of scope by design.
- **`WaveFile.cpp`**: zero `g_*` globals, but real Windows Multimedia I/O
  (`mmioOpen`/`mmioCreateChunk`/etc.) writing real `.wav` files to disk -
  and it's useless without the (deferred) `CXPokey` audio data `ExportWAV`
  would feed it. Stays bundled with that existing deferral.
- **Already-solved-shape remnants of prior triage, no new work**:
  `RmtExporter.cpp`, `ASMFileExporter.cpp`, `SongExporter.cpp`,
  `SongExportV2.cpp`, `IO_Song_ExportAsm.cpp` (dialog wrappers/deferred
  export methods left behind by this session's and earlier sessions'
  `*Core.cpp` splits), `Clipboard.cpp` (`BlockEffect()` only - confirmed no
  extractable logic, see `plans/NOTES.md`), `Messages.cpp`/`GuiHelpers.cpp`
  (their real one-liner bodies are already copied verbatim into test stubs
  where needed - `SendErrorMessage`/`SetStatusBarText`/
  `DisableEventSection` - linking the actual files would additionally need
  a real `CStatusBar`/`g_hwnd`, for no new coverage).
- Trivial/empty files with nothing to test: `ASMFile.cpp` (2 lines),
  `AssemblerTypes.cpp` (1 line), `RuntimeException.cpp` (2 lines, the
  `exit(2)` macro body itself - already characterized indirectly via the
  `CSongContainer` hazard note), `SongIO.cpp` (1 line), `import.cpp` (0
  lines).

## Category B - new candidates worth their own triage batch

Found by checking actual coupling, not assumed - same "verify before
deferring" discipline as the rest of this effort.

1. **`IO_Importer.cpp` (1868 lines) - the single biggest opportunity left.**
   `CSong::ImportTMC(std::ifstream&)`/`CSong::ImportMOD(std::ifstream&)` are
   pure format-decode logic, same shape as the already-completed
   `LoadRMT`/`LoadRMW`/`LoadTxt` work. Only touches already-safe globals
   (`g_Instruments`, `g_Tracks`, `g_tracks4_8`) plus `g_hwnd` - every one of
   its ~13 `MessageBox` calls is guard-only (corrupted/unsupported file,
   out-of-tracks/out-of-songlines truncation warnings), avoidable with valid
   test data, same pattern already established. `CConvertTracks` (a small
   helper class also in this file) has no globals of its own. This is real,
   substantial, currently-untested production functionality (importing
   Protracker MOD and TMC files) - the strongest candidate by far.
2. **`Undo.cpp` (522 lines).** `CUndo::Undo()`/`Redo()`/`ChangeTrack()`/
   `ChangeSong()`/`ChangeInstrument()`/`ChangeInfo()`/`PerformEvent()`/etc.
   operate directly on the real `extern CSong g_Song` global (not a locally
   constructed instance - needs checking how existing tests' local `song`
   fixture relates to `g_Song`, since `CUndo`'s methods assume it's *the*
   song). Every `g_hwnd` use is a guard-only "BAD!" internal-error assertion
   on an invalid enum value, unreachable with valid input. `g_Undo` is
   already exercised incidentally throughout the existing suite (via
   `UndoStub.cpp`'s no-op stub) but its own methods have never been
   characterized directly - real undo/redo behavior is significant
   production functionality.
3. **`Instruments.cpp` remainder - DONE.** `ClearInstrument`,
   `SetEnvelopeVolume`, `MemorizeOctaveAndVolume`, `RememberOctaveAndVolume`
   all confirmed to call only already-safe globals/methods, exactly as
   predicted here - linked directly (`RmtTests.vcxproj`), the 3 no-op
   stubs in `InstrumentsStub.cpp` removed, 11 new tests added
   (`InstrumentsCoreTest` in `InstrumentsTests.cpp`). One real, unrelated
   finding along the way, since fixed at the user's explicit request:
   `CInstruments`'s constructor allocated `m_instr` with plain `new[]` (no
   zero-initialization) - same shape as `CTracks::m_track`. Initially left
   alone to match the existing (never-fixed) `CTracks` precedent, with
   tests explicitly setting a known baseline instead; the user then asked
   for the constructor itself to be fixed, so it now uses value-initializing
   `new TInstrument[INSTRSNUM]()`, and the tests' now-redundant explicit
   baselines were removed again. `CTracks::m_track` itself is unchanged -
   this fix was scoped to `CInstruments` only, not requested more broadly.
   291 tests passing (up from 280), 0 regressions.
4. **`AtariTrackerDriver.cpp` remainder - DONE.** `Init()`, `SetPokey()`,
   `Silence()` confirmed to delegate only to the already-stubbed no-op
   `m_atari->JSR()`; `IsSpecialProveMode()` (needed by `Play()`) confirmed
   trivial (only reads `g_prove`) and copied verbatim into
   `SongEditingStub.cpp`, next to its existing `SetEditMode()`.
   `LoadRMTRoutines()` confirmed to use the same
   `CRmtAtariBinaries::Get*Binary()`/`CAtariIO::LoadDataAsBinaryFile()`
   resource-loading path already unlocked for `ExportSAP_B_LZSS`/
   `ExportXEX_LZSS` (loads `resources/drivers/rmt_driver_v6.obx`, the
   default `PATCH16` driver, already checked into `rmt/`). Linked
   `AtariTrackerDriver.cpp` directly (`RmtTests.vcxproj`); removed its
   `Init()`/`Play()` no-op stubs from `test/PokeyStreamStub.cpp`. 7 new
   tests (`AtariTrackerDriverTests.cpp`).
   - **Found and fixed a real bug while writing the "unknown driver
     version" guard test**: `LoadRMTRoutines()` ignored its own
     `trackerDriverVersion` parameter entirely, always passing the global
     `g_trackerDriverVersion` to `GetTrackerDriverBinary()` instead.
     Harmless in production today - both real call sites (`Rmt.cpp`,
     `RmtView.cpp`) always pass `g_trackerDriverVersion` as the argument
     anyway, so the bug never changed observable behavior - but fixed
     outright since the fix changes nothing for any real caller and
     removes a dead-parameter footgun.
   - Full solution rebuild (`Rmt.exe` + `RmtTests.exe`, Release|x64)
     confirmed 0 errors; 298 tests pass (up from 291, +7, 0 regressions).

## Suggested priority

1. `IO_Importer.cpp` (`ImportTMC`/`ImportMOD`) - highest value, most
   real production functionality, same well-established pattern as
   `LoadRMT`/`LoadRMW`/`LoadTxt`.
2. `Undo.cpp` - second-highest value (real undo/redo is significant
   behavior), but needs the `g_Song`-vs-local-fixture relationship checked
   first since it's a bigger unknown than `IO_Importer.cpp`.
3. `Instruments.cpp` remainder - cheap, low-risk, do opportunistically.
4. `AtariTrackerDriver.cpp` remainder - cheap once `IsSpecialProveMode()` is
   checked; pairs naturally with #3 since both touch `CInstruments`/
   `CAtariTrackerDriver`.

Nothing else surveyed looks worth pursuing - the remaining ~50 unlinked
files are real UI, real hardware/DLL coupling, already-solved-shape
remnants, or effectively empty.
