# Java port: scripting (proposal)

Status: **S1 DONE 2026-09-27** (decisions 1-4 and 7 as recommended; S2's
menu item and the C++ question, decisions 5 and 6, still open). Origin:
the user's decision during B9 - "have proper scripts instead of /TEST" -
see `plans/JAVA_B9_PLAN.md` section 5. `/TEST:<file>` stays rejected by
the Java port with the C++ "Invalid Command Line Parameter" box; the C++
program still runs its developer routines behind both switches.

S1 as built: package `com.wudsn.tools.rmt.script` - `ScriptParser`
(`ScriptCommand` records, `ScriptException` with the line), `ScriptRunner`
(`open`, `save`, `export` for the eight formats with the options of 3.4,
`set overwrite`, `echo`, `quit`; a `SongFiles.Host` answering the file
chooser and the export dialogs from the command's options with the dialogs'
defaults, a `Messages.Handler` printing the boxes to the console - errors
and warnings fail the command; exit codes 0/1/2; paths relative to the
script). `RmtApplication.main` runs `/SCRIPT:<file>` headless before any
Swing, with `rmt.ini`/`tuning.ini` read as the window reads them.
`ScriptRunnerTest` proves every format's script export byte-identical to
the dialog path (the stub host answering the dialogs' defaults), the option
checks, save by extension, the overwrite policy, the failure exit codes and
line numbers; `ScriptParserTest` the syntax. Found on the way: a missing
`vu_player_v2.obx` escaped the SAP/XEX exporters as an
`UncheckedIOException` (an uncaught exception in the UI, an empty output
file left behind) - now the `IllegalStateException` the callers catch, i.e.
C++'s "Fatal error with RMT LZSS system routines" box; and a
configuration-only `-Drmt.config.dir` lost the installed resources -
`ProgramFolder.setInstallFolder` (the jar's folder) is searched after the
program folder. Live: the sample script of 3.2 run headless from the
checkout root and, with the staged `rmt/` layout, from a foreign folder
(exit 0, the four files written); a rerun refused to overwrite (exit 1); a
syntax error gave exit 2 with the line.

## 1. Goal

A script file that drives RMT without its window: open a song,
set what the export dialogs would ask, export in any format, save, quit -
so batch files, build scripts and CI can produce SAP/XEX/WAV/ASM files
from `.rmt` sources reproducibly. Errors stop the script with a non-zero
exit code and a message naming the script line.

## 2. What exists to build on

- Every export already runs through `SongFiles.exportV2(iotype, file)`,
  which asks its dialog through the `SongFiles.Host` interface
  (`showExportStrippedRmt`, `showExportAsm`, `showExportRelocatableAsm`,
  `showExportSap`, `showExportXex`, `chooseSaveFile`, ...) and otherwise
  needs no UI. The tests already drive it with `StubSongFilesHost`. A
  script runner is therefore a second `Host` that answers those calls from
  the script's options - no exporter changes.
- Open (`fileOpen`) and save (`fileSave`/`fileSaveAs`) are `SongFiles`
  methods with the same host pattern (so are import, instrument and track
  files, should they ever be wanted - see section 6).
- `Messages` (the model's message boxes) has a pluggable `Handler`; the
  runner installs a console handler (errors to stderr, the rest to stdout,
  questions answered by a script policy).
- `RmtSession` is headless-capable (the whole test suite runs on it);
  `AudioEngine` is not started. `ProgramFolder` resolves the driver
  binaries as in the UI.
- `RmtCommandLineInfo` already parses `/SCRIPT:<file>`.
- WUDSN Base's `com.wudsn.tools.base.console` parses a *command line* into
  one command with parameters (The!Cart Studio's console mode); it is not
  a script-file interpreter, so it is not the base here, but its
  conventions (exit codes, message queue rendering) are worth matching.

## 3. Proposed design

### 3.1 Invocation

- `java -jar rmt.jar /SCRIPT:<file>` (also `-script:<file>`; the C++
  spelling, so the same batch file can address either program if the C++
  gets the feature). No window is created; the process ends when the
  script ends. Exit code 0 = every command succeeded, 1 = a command
  failed (the message names the script line), 2 = the script could not be
  read or parsed.
- Later, optional: **Tools > Run script...** in the UI (a file chooser,
  the same runner on the live session, messages as boxes) - useful for
  repeated export sets while composing.

### 3.2 Syntax

One command per line; `#` starts a comment; blank lines ignored; tokens
separated by whitespace, `"..."` for values with blanks; command names
and option names case-insensitive; options as `name=value` after the
positional arguments; relative paths relative to the script file's folder
(so a script travels with its songs). Example:

```
# Build the release assets of Delta
open Delta.rmt
export sap Delta.sap author="Raster" name="Delta" subsongs="0"
export xex Delta.xex text="DELTA BY RASTER" rasterbar=no
export stripped-rmt Delta_stripped.rmt address=$5000 sfx=no
export wav Delta.wav
quit
```

Considered and rejected: JSON/properties (harder to write by hand in a
batch context, no gain for a flat command list), a full expression
language (no use case).

### 3.3 Commands (first version)

| Command | Meaning (the UI equivalent) |
|---|---|
| `open <file>` | `FileOpen` - `.rmt`/`.txt`/`.rmw` by extension |
| `save <file>` | `FileSaveAs` - `.rmt`/`.txt`/`.rmw` by extension |
| `export <format> <file> [options]` | `ExportV2`; formats and options in 3.4 |
| `set <name> <value>` | session settings a dialog would set: `ntsc yes/no`, `driver unpatched/patch16/...` (the options dialog), `overwrite yes/no` (the question policy, default `no`: an existing output file is an error) |
| `echo <text>` | a line to stdout |
| `quit` | ends the script (implicit at the end) |

### 3.4 Export formats and their options

The format names follow the Export dialog's filter list; every option has
the dialog's default (from `ExportSettings` / the song), so a bare
`export sap out.sap` produces exactly what the dialog's OK would with
untouched fields.

| Format | Options (dialog field) |
|---|---|
| `stripped-rmt` | `address` (hex `$4000` or decimal), `sfx`, `gvf` (global volume fade), `nos` (no starting songline), `asmformat=xasm/mads` |
| `asm` | `type` (the export type radio), `notes=index/freq`, `durations` (radio), `prefix` |
| `rmtplayer-asm` | `startlabel`, `relocate=instruments,tracks,songlines`, `instruments-label`, `tracks-label`, `songlines-label`, `asmformat`, `sfx`, `gvf`, `nos` |
| `sapr` | `author`, `name`, `date`, `subsongs` |
| `lzss` | none (writes the `_INTRO`/`_LOOP` siblings as the dialog path does) |
| `sap` | `author`, `name`, `date`, `subsongs` (SAP type B + LZSS driver) |
| `xex` | `text` (the on-screen text; the song's default when omitted), `rasterbar`, `shuffle`, `region=auto/pal/ntsc`, `color` |
| `wav` | none |

`makeModule`'s and `testBeforeFileSave`'s failures (the checks the UI
runs before exporting) are script errors with the model's message text.

### 3.5 Architecture

Package `com.wudsn.tools.rmt.script` (UI-free, so it can also serve a
future C++-style console build):

- `ScriptParser`: lines -> `ScriptCommand(lineNumber, name, arguments,
  options)`; syntax errors carry the line number.
- `ScriptRunner`: executes commands against an `RmtSession` through
  `SongFiles` with a `ScriptHost implements SongFiles.Host` (returns the
  `*Choice` records built from the command's options; `chooseSaveFile`
  is never reached because the script names the file; `songChanged` etc.
  are no-ops) and a console `Messages.Handler`. Returns the exit code.
- `RmtApplication.main`: `/SCRIPT:<file>` -> `ScriptRunner.run(file)` and
  `System.exit(code)` before any Swing object is created
  (`java.awt.headless=true` set first, so it works without a display).
- Tests: scripts run on the reference songs into a temp folder; each
  export compared byte for byte with the same export through the dialog
  path (`ImportExportTest`'s fixtures) - the guard that the script and
  the UI produce identical files; syntax errors and failures produce the
  expected exit codes and line numbers.

### 3.6 Documentation

`doc/rmt_scripting.md` (commands, options, defaults, exit codes, an
example) linked from README's Java section and from the local help's
index; a short "Scripting" paragraph in `doc/rmt_tracker_usage.md`.

## 4. Batches

- **S1**: parser, runner, `open`/`save`/`export` (all eight formats) /
  `echo`/`quit`, `/SCRIPT` wiring, console messages, exit codes, tests.
- **S2**: `set`; the Tools > Run script... menu command (optional,
  decision 5).
- **S3**: `doc/rmt_scripting.md`, README, NOTES/plan; the C++ decision
  (decision 6) if taken.

**User decision 2026-09-27: import (`mod`/`tmc`) and instrument
(`save`/`load`) commands are not required** - removed from the command set
and from S2 (section 6 keeps them as a possible later addition, since the
host pattern would carry them unchanged). `new` dropped with them: a
script starts from a song file.

## 5. Decisions requested

1. **Syntax**: line-based commands with `name=value` options (3.2).
   *Recommended.*
2. **Invocation**: `/SCRIPT:<file>` (C++ spelling) headless, exit codes
   0/1/2. *Recommended.* (`/TEST` stays rejected; nothing replaces it by
   name.)
3. **Relative paths** resolve against the script's folder, not the
   working directory. *Recommended* - a script and its songs move
   together.
4. **Overwrite policy**: an existing output file is an error unless
   `set overwrite yes` (the dialog asks; a script cannot). *Recommended.*
5. **UI entry point** (Tools > Run script...): include in S2, or command
   line only? *Recommended: include* - small, and it makes the feature
   discoverable.
6. **C++ program**: (a) leave it as is (developer switches), (b) give it
   the same script format later, built on `CSongExport`/the exporters as
   `CSongExporterTest` already does (feasible, a few hundred lines, but
   the C++ dialogs' option handling is more entangled than the Java
   host pattern). *Recommended: (a) for now, revisit once the Java
   scripting has settled and the option set is proven.*
7. **Format names**: the filter-list names in 3.4 (`stripped-rmt`, `sap`
   for the LZSS SAP, `sapr` for the raw stream, `rmtplayer-asm`).
   *Recommended*, unless you prefer the C++ enum names
   (`RMTSTRIPPED`, `LZSS_SAP`, ...).

## 6. Not in scope (by the user's decision, 2026-09-27)

`import mod`/`import tmc`, `instrument save/load`, `new`. Each would be a
`SongFiles` call through the same script host if a need arises; the
parser and runner need no change for them.
