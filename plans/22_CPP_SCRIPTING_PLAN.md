# C++ scripting and the cross-program export comparison (proposal)

Status: **C1 DONE 2026-09-27** (decisions 1, 2 and 6 as recommended);
**C2 DONE 2026-09-27** (decisions 3, 4 and 5 as recommended: `set output`
plus the `RMT_SCRIPT_OUTPUT` override, the JUnit `CrossProgramExportTest`,
WAV reported but not compared); **C3 DONE 2026-09-27** (`doc/rmt_changes.md`
entries for the C++ program, README, the plans closed). Origin: the user's
question after the Java scripting feature - would `/SCRIPT` support in the
Windows program simplify testing? Answer: yes, twice - it enables an
end-to-end comparison of both programs' export pipelines, which no test
covers today, and it replaces the hard-coded developer routines behind the
C++ `/SCRIPT`/`/TEST`. This resolves decision 6 of
`plans/21_JAVA_SCRIPTING_PLAN.md`.

C1 as built: `Script.h/.cpp` (the parser, std only, in both projects),
`ScriptRunner.h/.cpp` (the commands as in Java; the exports through the
dialog-independent entry points with the dialogs' defaults from the same
globals; `set ntsc` as `CRmtView::SetNTSC`, `set driver` as the options
dialog), script message mode in `Messages.cpp` (`SetScriptMessageMode`,
`ClearScriptProblems`, `GetScriptProblems`: boxes to the console, questions
answered No, errors and warnings collected), `AttachScriptConsole`
(`AttachConsole(ATTACH_PARENT_PROCESS)`, else `<script>.log`),
`CSAPFile::ParseSubsongs` and `CSongExporter::DefaultXexText/SetXexText`
factored out of the dialogs, `CSong::SetLoadedFile/SetLastExportIOType`.
`InitInstance`: the window is created but not shown for a script, the run's
exit code travels through `CRmtApp::ExitInstance()` (an `ExitProcess`
crashed the DLL teardown, `InitInstance() == FALSE` alone always exits with
0). `/TEST`, `RmtTest.*` and `SongExporterTest.*` removed. Tests:
`ScriptTests.cpp` (the parser, as the Java `ScriptParserTest`;
`ParseSubsongs`); 415 C++ tests. Live: the `doc/rmt_scripting.md` example
through `Rmt.exe /SCRIPT` - exit 0 with the four files; a rerun exit 1
(overwrite refused); a broken quote exit 2.

**Bug found by the first script run and fixed**: `CWaveFileExporter::ExportWAV`
crashed mid-file (0xC0000005, a different length each time) - the timer
thread kept rendering through the same POKEY (`TimerRoutine` ->
`RenderSound1_50`) while the export rendered through `RenderSoundV2`; the
`WRITE` state the function sets to prevent that never worked, because the
song's `m_pokeyStream` is already null after the dump. The source's own
"TODO: Fix the timing overlap causing conflicts" was this. The export now
stops the song timer for its duration and re-arms it at the end. The Java
port is not affected (the audio engine and the export share the session
lock).

C2 as built: `test-resources/scripts/delta.rmtscript` and
`stereo.rmtscript` (saves in the three formats, the eight exports with
defaults, a round with every option, `set ntsc`/`set driver` rounds; the
stereo song without `sap`/`xex`, which both programs refuse - "LZSS data is
too big to fit in memory"); `set output <folder>` and the
`RMT_SCRIPT_OUTPUT` environment variable in both programs, `RMT_SCRIPT_LOG`
in the C++ one (its console output into a file whatever console the caller
has); `build/compare_exports.ps1` (exit 1 on a difference, 2 when a program
is not built) called from `build_rmt-daily.bat` after the release build
when `target\rmt.jar` exists; `CrossProgramExportTest` (skips without
`Rmt.exe`). **The first run found four port bugs**, all fixed:

- Java: ASM exports (`asm`, `rmtplayer-asm`) were written with CRLF; C++
  opens every export in binary mode with `CASMFile::EOL = "\n"`, so LF.
  The TXT saves stay CRLF (C++ text mode) - `SongFiles.asmBytes` vs
  `textBytes`.
- Java: `.rmw` instrument records were 32 bytes of name; C++ writes
  `sizeof(ai->name)` = 33 (the terminating zero). Save and load fixed - a
  Java-written `.rmw` was not loadable by `Rmt.exe` and vice versa.
- Java: the 16 UI settings among the `.rmw` main parameters
  (`g_activepart` .. `g_keyboard_escresetatarisound`) were written as zeros
  and discarded on load. `Song.saveRMW(tracks4_8, uiParams)` /
  `LoadRmwResult.uiParams` now carry them, `SongFiles` supplies and applies
  them from the session (`g_keyboard_playautofollow` has no Java field -
  its C++ default 1 is written).
- C++: `export lzss` and `export sap` crashed (0xC0000409, fail-fast) on the
  stereo song - `ExportLZSS`, `ExportSAP_B_LZSS` kept 64 KB buffers on the
  stack for compressed data that can exceed them. Heap vectors of 1 MB now
  (`std::vector<byte>`), as the other exporters already used.

Also from the run: `RedirectScriptOutputToFile` uses `_dup2` for stderr
(two `freopen` of the same file gave two file offsets and lost lines).
After the fixes, every non-WAV file of both scripts is byte-identical.

## 1. Goal

1. `Rmt.exe /SCRIPT:<file>` runs the same script format as the Java port
   (`doc/rmt_scripting.md`): `open`, `save`, `export` with the same format
   names and options, `set overwrite|ntsc|driver`, `echo`, `quit`; same
   exit codes 0/1/2; messages to the console instead of boxes.
2. A **cross-program comparison**: one set of scripts over the reference
   songs, run through `Rmt.exe` and `rmt.jar`, every output file compared
   byte for byte. The whole pipeline - load, tuning tables, tracker driver,
   register dump, LZSS, exporters - is checked in one go, in both
   directions. It would have caught the B8 findings (the frequency-table
   difference of the ASAP-based dump, the stereo WAV bug) by itself.
3. `CSongExporterTest::Test` and `CRmtTest::RunFor` (`/TEST`) retired: a
   script does what they did, from a text file, without editing constants
   or desktop paths in the source.

## 2. What exists on the C++ side

- `CRmtCommandLineInfo` parses `/SCRIPT:<file>` (and `/TEST:<file>`).
- `CRmtApp::InitInstance` creates and shows the window, opens the file
  argument, then - for `/SCRIPT` - checks the file opens, runs the
  developer routine and returns `FALSE` (the app exits). Running a script
  at that point works today (`/TEST` proves it: `DumpSongToPokeyStream`
  pumps window messages through `RefreshScreen`).
- Every export has a dialog-free entry point, used by the developer
  routine and by the tests: `CRmtExporter::ExportAsStrippedRMTApply`,
  `CASMFileExporter::ExportAsAsmApply` / `ExportAsRelocatableAsmForRmtPlayerApply(TRelocatableAsmExportParams)`,
  `CSAPFileExporter::ExportSAP_R/ExportSAP_B_LZSS(songExport, CSAPFile, ou)`,
  `CSongExporter::ExportLZSS`, `ExportXEX_LZSS(songExport, CXEXFile, ou)`,
  `ExportWAV`. `CSongExport`/`CSongContainer` wrap the song and its
  recorded stream. `CSong::ExportV2` is the dialog-showing dispatcher the
  menu uses; the script runner calls the `Apply` variants with parameters
  from the options, as the Java `ScriptHost` answers the dialogs.
- `CSong::FileOpen(filename, FALSE)` returns `BOOL`; `SaveTxt`/`SaveRMW`
  take an `ostream`, `ExportV2(song, ou, RMT)` writes the module - the
  `save` command writes the stream itself (with `TestBeforeFileSave` for
  `.rmt`, as `FileSave` does).
- Messages: `SendMessageBox` already has a no-UI branch (`g_statusBar ==
  nullptr` -> `OutputDebugString`), used by the tests. Questions
  (`SendQuestionMessage`) show a `MessageBox` unconditionally.
- No console: `Rmt.exe` is a GUI subsystem program.

## 3. Design (C++)

### 3.1 Files

`src/cpp/Script.h/.cpp` (parser: `TScriptCommand {line, name, args,
options}`, the Java `ScriptParser` rules 1:1) and `ScriptRunner.h/.cpp`
(the commands; a `TScriptHost`-like set of parameter builders with the
dialogs' defaults from the same globals the dialogs read:
`g_rmtstripped_*`, `g_AsmFormat`, `g_PrefixForAllAsmLabels`, the
relocatable labels, `g_rmtmsxtext`/`g_msxcheck`/`g_msx_shuffle`/
`g_region_auto`/`g_msxcol`, `CSAPFile::Init(song)`,
`GetSubsongParts`). Both link into `RmtTests` (the parser and the option
parsing get GoogleTests; the runner's file commands are exercised by the
comparison, section 4).

### 3.2 Batch mode

- `g_scriptMode` (a `Messages.cpp` flag): `Send*Message` write to the
  console (`ERROR:`/`WARNING:` to stderr, `INFO:` to stdout) instead of
  `MessageBox`, and `SendQuestionMessage` answers No/Cancel with a note -
  the Java `ConsoleMessages`. Errors and warnings during a command fail
  it, as in Java.
- Console: `AttachConsole(ATTACH_PARENT_PROCESS)` at start of the script
  run and `freopen` of `CONOUT$`/`CONERR$`; when there is no parent
  console (started from Explorer), the output goes to
  `<script>.log` next to the script instead. Batch files must use
  `start /wait Rmt.exe /SCRIPT:...` (a GUI program returns to `cmd.exe`
  at once otherwise) - documented.
- Exit code: `ExitProcess(code)` after the run (MFC's `InitInstance` ->
  `FALSE` path always exits with 0; the developer routine already uses
  `exit(1)`).
- The window is created as today (the dump needs its message pump), but
  hidden: `ShowWindow(SW_HIDE)` before the script when `/SCRIPT` was
  given - no flash on the desktop.
- `/TEST`: removed from `CRmtCommandLineInfo` and `InitInstance`;
  `RmtTest.cpp`/`SongExporterTest.cpp` and their `.vcxproj` entries
  deleted (the ASAP verification `TestASAP` and the menu analyzer with
  them - both unreachable from the UI). `RmtCommandLineInfoTests.cpp`
  updated.

### 3.3 Commands

Same as Java, same names, same defaults (`doc/rmt_scripting.md` becomes
the document for both programs, with a "Windows" paragraph on
`start /wait` and the `.log` fallback). `set driver` maps to
`g_trackerDriverVersion` + `g_Atari.Init` + `LoadRMTRoutines` as the
options dialog; `set ntsc` to `SetNTSC`.

## 4. The cross-program comparison

- `test-resources/scripts/`: one script per reference song exporting
  every format twice - once with defaults, once with every option set
  (fixed `date`, `text`, labels, address, `set ntsc yes` for a second
  round) - so both the defaults and the option plumbing are compared.
  Output goes to a folder given by an environment variable or a `set
  output <folder>` command (**decision 3**), so the same script runs in
  both programs and in CI.
- `build/compare_exports.ps1`: runs each script through
  `out\Release\output\Rmt.exe` and `target\rmt.jar`, then compares the
  two output trees byte for byte and reports every difference (file,
  first differing offset). Exit code 1 on any difference. Called at the
  end of `build_rmt-daily.bat` (after both programs are built) and
  runnable by hand.
- Optionally (**decision 4**) a JUnit test `CrossProgramExportTest` that
  runs the same comparison when `Rmt.exe` exists at its build location and
  skips otherwise - the comparison then also runs with every `mvn test`
  on the developer machine.
- Known legitimate differences to settle before the first run: the SAP
  `date` and the XEX default text carry today's date (the scripts fix
  them); text exports use the platform line separator (`SongFiles.textBytes`
  = C++ text-mode `ofstream`, both CRLF on Windows - fine); the WAV export
  is 8-bit in C++ and 16-bit in Java (decision 3 of the audio plan) - the
  comparison decodes both to samples and compares the 8-bit-equivalent
  values, or the WAV is excluded and covered by the SAP-R comparison
  (**decision 5**).

## 5. Batches

- **C1**: parser + runner + batch mode + `/SCRIPT` wiring in
  `InitInstance`, `/TEST` and the developer routines removed; GoogleTests
  for the parser and the option parsing; the Release build; a manual run
  of the `doc/rmt_scripting.md` example through `Rmt.exe`.
- **C2**: the scripts, `compare_exports.ps1`, the daily-build hook, the
  first comparison run - and the findings it produces (each one either a
  port bug to fix in the right language, or a documented difference).
- **C3**: `doc/rmt_scripting.md` for both programs, README, NOTES/plans;
  the JUnit variant if decided.

## 6. Decisions requested

1. **Go ahead with the C++ `/SCRIPT` at all** (a new feature in the
   shipping C++ program, a few hundred lines plus batch-mode plumbing).
   *Recommended: yes* - the comparison is the strongest port test
   available and the developer routines it replaces are already dead
   weight.
2. **Remove `/TEST` and the developer routines** (`RmtTest.cpp`,
   `SongExporterTest.cpp`, WASAP launch, menu analyzer) rather than keep
   them beside the script. *Recommended: remove.*
3. **Output folder for the shared scripts**: a `set output <folder>`
   command (added to both programs) vs. an environment variable expanded
   in paths. *Recommended: `set output`* - explicit, and no environment
   dependency in the script.
4. **JUnit wrapper** for the comparison (skips without `Rmt.exe`).
   *Recommended: yes* - it keeps the check in the Java test loop.
5. **WAV** in the comparison: decode and compare at 8-bit precision, or
   exclude (SAP-R covers the register stream, and the two renderers -
   `sa_pokey.dll` vs ASAP - are not expected to be sample-identical
   anyway). *Recommended: exclude*, and compare the WAV header only.
6. **Hidden window** during a script run (3.2) vs. visible as `/TEST`
   shows it. *Recommended: hidden.*
