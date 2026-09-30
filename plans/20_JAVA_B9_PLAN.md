# Java port, Phase B batch B9: packaging & polish

Status: **B9 DONE 2026-09-27** (B9a, B9b, B9c). Phase B of the Java port
is complete; the next feature is scripting (section 5). Decisions 1-4 accepted by
the user on 2026-09-27 (decision 5 replaced by "proper scripts", section 5).
Companion to `plans/18_JAVA_UI_PORT_PLAN.md` (B9 is its last batch; B1-B8 are
committed).

B9a as built: `maven-shade-plugin` -> `target/rmt.jar` (manifest
`Main-Class`, `Build-Date` in UTC, `Implementation-Version`);
`launch/Rmt.launch`; `ProgramFolder` (model, the one process-wide setting:
`g_prgpath`) with `getResourceRoot()` = the first of program folder, its
`rmt/`, the working directory, its `rmt/` that has `resources/` - so an
installed copy resolves like `Rmt.exe`, and `java -jar target/rmt.jar` or
the classes folder from a checkout work too; `RmtAtariBinaries`/
`SapFileExporter` resolve through it; `RmtCommandLine` (file, switches;
`/SCRIPT`/`/TEST` rejected with the C++ "Invalid Command Line Parameter"
box and exit code 1, unknown switches ignored as MFC does); local help =
`<resource root>/docs/rmt_en.html` through the browser, a missing file
reported; `VERSION_AND_BUILD` from the manifest. Tests: `ProgramFolderTest`,
`RmtCommandLineTest`, the help test in `RmtCommandsTest` (569 total). Live:
the jar in a staged `rmt/` layout run from a foreign working directory
(plays, About shows the build date, `rmt.ini`/`tuning.ini` written beside
the jar on exit), the jar from the checkout root (the first-start "Could
not find rmt.ini" box, as C++, then plays), `/SCRIPT:x` rejected.

## 1. What exists

- **Build**: `pom.xml` compiles `src/java` (tests nested in
  `src/java/test`), `finalName` `rmt`, no packaging beyond the plain jar.
  Running the app today needs a hand-written classpath with the two
  WUDSN jars from the local Maven repository (see NOTES.md).
- **Program folder** (`RmtApplication.getProgramFolder()`, C++'s
  `g_prgpath`): `-Drmt.config.dir`, else the jar's folder, else the working
  directory. Used for `rmt.ini`/`tuning.ini` only. The Atari binaries
  (`rmt/resources/drivers/*.obx`, `rmt/resources/players/vu_player_v2.obx`)
  are read **relative to the working directory** (`RmtAtariBinaries`,
  `SapFileExporter.VU_PLAYER_PATH`) - a documented test-time shortcut that
  breaks as soon as the jar runs from anywhere but the repository root.
- **C++ distribution layout** (`Rmt.vcxproj` PostBuildEvent, the daily
  build): the checked-in `rmt/` folder (`docs/`, `exports/`,
  `instruments/`, `resources/`, `songs/`, `rmt.ini`, `tuning.ini`) is
  copied next to `Rmt.exe`; `g_prgpath` is that folder, so `docs/rmt_en.html`
  (local help, `CShell::OpenLocalFile`) and `resources/...` resolve from it.
  `rmt/docs/*.*` is gitignored and filled from `doc/` by the build.
- **Command line** (`CRmtApp::InitInstance`): MFC's `CCommandLineInfo`
  gives a file to open; `/TEST:<file>` runs `CRmtTest::RunFor` (a
  developer utility: open + export everything, or an LZSS round-trip for a
  `.sapr`) and exits; `/SCRIPT:<file>` only checks the file can be opened
  (error box otherwise) and then runs `CSongExporterTest::Test` - a
  developer routine with **hard-coded desktop paths**
  (`C:\Users\JAC\Desktop\ASMA-Input\Test\out`) - and exits. The parser
  (`RmtCommandLineInfo`) is ported and tested; `RmtApplication.main` only
  handles `args[0]` as the file.
- **Help**: `HELP` reports "Local help (B9)"; `CONTEXT_HELP` opens the
  online page (as C++). About is done.
- **Exit**: `RmtMainWindow.exit()` already runs `warnUnsavedChanges()`,
  saves `rmt.ini`/`tuning.ini`/geometry and disposes - the plan's
  "unsaved-changes prompts on close" item is done (B7a).
- **Minimum size**: `updateMinimumSize()` = `CMainFrame::OnGetMinMaxInfo`
  (800x600 mono, 1120x600 stereo) - done (B1/B7c).
- **HiDPI**: the canvas is painted in device pixels and blitted 1:1 (the
  B1 gate at the user's 150%); the toolbar strips are 1:1 BMP slices
  (small on HiDPI, as `Rmt.exe`'s own toolbar is - MFC's is not DPI-aware
  either), dialogs are Swing's native L&F. The screenshots of B5-B8 at
  150% show no artifacts.
- **dis6502's packaging** (the model named by the plan): `maven-shade-plugin`
  builds a single runnable jar with the WUDSN jars merged and the main
  class in the manifest; an Eclipse `launch/*.launch`; a GitHub Actions
  `release.yml` that builds the shaded jar and a portable **jpackage
  app-image** (bundled JVM) for Windows/Linux/macOS on a `v*` tag and
  publishes them as release assets. No `.cmd` launcher.

## 2. Sub-batches

### B9a - runnable jar and program folder (the substance)

1. `pom.xml`: `maven-shade-plugin` (as dis6502, main class
   `org.atari.raster.rmt.ui.RmtApplication`) -> `target/rmt.jar`, runnable
   with `java -jar`. `launch/Rmt.launch` for Eclipse.
2. **Program folder = the `rmt/` layout.** `RmtAtariBinaries` and
   `SapFileExporter` resolve `resources/...` relative to a program folder
   passed in / configured once at start-up (a `RmtResources` holder with
   `Path programFolder`, set by `RmtApplication` from
   `getProgramFolder()`; the tests set it to `rmt/` explicitly - today's
   working-directory assumption becomes explicit instead of implicit). The
   development run keeps working from the repository root when
   `-Drmt.config.dir=rmt` (or the default working directory + `rmt/`
   fallback: if `<programFolder>/resources` does not exist but
   `<programFolder>/rmt/resources` does, use the latter - the exact case
   of `java -jar target/rmt.jar` from a checkout).
3. `docs/`: local help opens `<programFolder>/docs/rmt_en.html` through
   `Desktop.browse` (C++'s `CShell::OpenLocalFile`); with the file absent
   the C++ shows whatever the shell says - the port shows the "Help"
   message box with the missing path. `rmt/docs/*.*` stays gitignored; the
   release step copies `doc/rmt_en.html`, `rmt_en_128.html`, `rmt.gif`,
   `img/` into it (as the C++ daily build does).
4. Command line, `CRmtApp::InitInstance`'s order: the file argument opens
   the song. **User decision (2026-09-27): "have proper scripts instead of
   /TEST"** - the C++ `/TEST` switch and the developer routines behind
   `/TEST`/`/SCRIPT` (`CSongExporterTest::Test` with its hard-coded desktop
   output folder, `CRmtTest::RunFor`'s LZSS probe) are **not ported**.
   `/SCRIPT:<file>` is reserved for a real scripting feature, designed and
   built as its own batch after B9 (section 5); in B9a an unknown or
   reserved switch is reported with the C++ "Invalid Command Line
   Parameter" error box and the program exits, so nothing pretends to work.
   The ported `RmtCommandLineInfo` parser stays (it is characterized by
   tests) and will serve the scripting batch.
5. Version: `VERSION_AND_BUILD` gets the build date from the jar manifest
   (`Implementation-Version`/`Built-Date` written by the shade transformer)
   as C++'s `__DATE__ __TIME__`; "1.35 (Java)" stays.
6. Tests: the resource resolution (explicit folder, the `rmt/` fallback),
   the command-line dispatch (a small `RmtCommandLine` result record:
   file/test/script, error text), the help path. Live check: `mvn -o
   package`, `java -jar target/rmt.jar test-resources/.../Delta.rmt` from
   the repository root and from another working directory with
   `-Drmt.config.dir`, F1 opens the local help, F5 plays.

### B9b - release workflow. DONE 2026-09-27.

`.github/workflows/release.yml` as dis6502's (WUDSN Base built first, the
shaded jar with the tests, jpackage app-image per OS with
`--add-modules ALL-MODULE-PATH`, artifacts, a GitHub Release on a `v*`
tag or a manual republish), plus the RMT specifics:
`build/stage_java_release.sh` stages `target/rmt.jar` with the `rmt/`
layout and `docs/` filled from `doc/` into jpackage's `--input` folder, so
the image's `app/` holds the jar next to `resources/`, `docs/`, `rmt.ini`
(the program folder). Windows icon `src/cpp/res/application.ico`, Linux
`application.png`, none on macOS (no `.icns` yet). Assets
`rmt-java-windows-x64.zip`, `rmt-java-linux-x64.tar.gz`,
`rmt-java-macos.tar.gz` (six assets named `rmt-java-<platform>-<arch>`
since 2026-10-01, see the workflow); version = the tag without `v`, default `1.35.0`
(decision 2). Local dry run on Windows: the staged layout + `jpackage`
(157 MB image) - `rmt.exe` run from a foreign folder plays Delta.rmt, About
shows the build date, `app/rmt.ini` is written on exit. CI itself not run
here.

### B9c - polish. DONE 2026-09-27.

- HiDPI: `RmtToolBars.iconScale()` = the device scale rounded (1 below
  150%, 2 from 150%, 3 from 250%), the 32x30 button images enlarged
  nearest-neighbour (`scaled()`) and drawn 1:1 in device pixels by
  `PixelIcon` (Swing's own fractional scaling removed in the paint, as the
  canvas does), the combo box height with them - decision 3, a deliberate
  improvement over the DPI-unaware MFC toolbar.
- The status line stays a `JLabel` (decision 4); `notAvailable` (printing
  only, MFC's own) drops the "yet".
- README.md "Java port" section (what it is, build/run/test, program
  folder, Eclipse, releases, plans); `.gitattributes` keeps `*.sh`/`*.yml`
  LF (the staging script runs on Linux/macOS runners; the repository uses
  `core.autocrlf=true`).
- `RmtToolBarsTest` (strip loading, the scaling, the scale factor).
- Phase B closed in `18_JAVA_UI_PORT_PLAN.md` and `13_JAVA_PORT_PLAN.md`.

## 3. Decisions requested

1. **Resource resolution**: an explicit program folder set at start-up
   (with the `rmt/` sub-folder fallback for a checkout) rather than
   classpath resources for the `.obx` files. *Recommended: program folder*
   - it is C++'s model, keeps `rmt/` the single distribution layout for
   both programs, and lets users drop in a different driver binary as they
   can with `Rmt.exe`.
2. **Release tag/version line**: `v1.35.<n>` (the Java port ships as the
   same 1.35 line the C++ is at, `RMT_VERSION_STRING` unchanged, so
   `.rmw`/`rmt.ini` version checks keep matching) vs a new major as
   dis6502 did (4.0 after 3.6.1). *Recommended: `v1.35.<n>` for now* - the
   version string is also a file-format compatibility marker in
   `Song.java` (`RMT_VERSION_STRING` checks), so changing it is a model
   change, not a packaging one.
3. **Toolbar HiDPI scaling**: scale the 16-px strips by the integer part of
   the device scale (1x below 150%, 2x from 150% to 249%, ...). *Recommended:
   yes* - a pure display improvement, no behavior change; documented as a
   deliberate deviation.
4. **Status bar**: keep the `JLabel` (C++'s extra panes are empty).
   *Recommended: keep.*
5. ~~`/SCRIPT` and `/TEST`~~ **Resolved by the user (2026-09-27): proper
   scripts instead of `/TEST`** - see B9a.4 and section 5.

## 4. Out of scope (unchanged from the UI plan)

Printing (MFC's own), MIDI, the Pokey Explorer, the HTML help viewer
(the browser is the viewer, as C++'s `CShell::OpenLocalFile`).

## 5. After B9: scripting (new feature, needs its own design proposal)

The user wants a real scripting feature in place of the C++ developer
switches. To be proposed as its own plan before any code: a script file
(passed as `/SCRIPT:<file>`, or run from a menu command) with commands such
as `open <song>`, `export <format> <file>` (every format of the Export
dialog: RMT/stripped RMT, ASM, relocatable ASM, SAP B/R, LZSS, XEX, WAV, TXT,
RTI), `set` for the export options the dialogs ask for (address, subsongs,
SAP header fields, XEX text), and `quit`; headless execution (no window,
messages to stdout/stderr, non-zero exit code on the first failure) so it
works in batch files and CI; errors with file and line number. Open
questions for the proposal: the syntax (one command per line vs. a
properties/JSON form), whether the C++ program gets the same feature, and
which of the dialogs' remembered defaults apply when a script omits an
option.
