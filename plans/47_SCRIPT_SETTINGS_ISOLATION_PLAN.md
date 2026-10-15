# Plan 47: A script run never touches the settings, and the harnesses stop sharing them

Status: DONE 2026-10-15, H1-H6. The H6 comparison first failed with two
differences in `delta` alone (`d2.asm` frequency values, `d4.sapr` AUDF
bytes), which turned into the plan's best finding: the Java port's
`-Drmt.config.dir` is a *program folder* override, not a configuration
one - pointed at the bare settings copy, the port's content-root search
walked up from the install folder, adopted the repository's checked-in
`rmt/` folder, and read ITS `rmt.ini` - which carried
`TRACKERDRIVERVERSION = 1` (changed to 6, the program's own default,
with the user's confirmation). The two programs then compared a Patch 16
run against a v1-family run, which is also the likely cold case behind
the original pollution incident: nothing need ever have written the exe
folder's ini - any staging step that copies the checked-in `rmt/`
layout plants driver 1 beside the program. The fix: `RMT_CONFIG_DIR` is
honored by BOTH programs as a configuration-only override, and the
harness keeps `-Drmt.config.dir` on the program folder.

The hardening decided after plan 46: an
aborted run had left `TRACKERDRIVERVERSION = 1` in the program folder's
`rmt.ini`, and every later script run inherited it - the failure that
masqueraded as plan 45's regression.

## The corrected diagnosis

The first reading - "a script's `set driver` is persisted when the
program exits" - is wrong for today's code, and the plan exists partly
because proving that took an experiment: running `set driver unpatched`
through `Rmt.exe /SCRIPT:` and through the jar leaves `rmt.ini` byte-for
byte unchanged. A C++ script run returns `FALSE` from `InitInstance`,
which shuts MFC down without `OnFileExit` (the only exit-path writer),
and the Java headless path never constructs the window that writes. The
polluting write presumably came from an older build generation; it
cannot be reproduced with the current one.

What remains true and worth fixing:

- **Nothing guarantees the no-write behavior.** It is an accident of
  which paths a script happens to reach. One real leak exists today: a
  script run in a folder with no `rmt.ini` CREATES one
  (`ReadRMTConfig`'s missing-file path calls `ResetRMTConfig`, which
  writes the defaults) - and any future code path that saves settings
  would silently reintroduce the pollution.
- **The harnesses share one mutable `rmt.ini`.** `compare_exports.ps1`
  and `CrossProgramExportTest` run both programs against the real file
  beside `Rmt.exe`, so whatever writes it - an interactive session, a
  stray tool, an older build - changes the next test run's inputs.

## Steps

- **H1. Guarantee it in C++.** A `/SCRIPT:` run never writes `rmt.ini`
  or `tuning.ini`: `WriteRMTConfig()`/`WriteTuningConfig()` return
  early in script mode (a flag set from
  `cmdInfo.IsScriptFileSpecified()`), which also closes the
  missing-file leak - a batch job is not a session and leaves no trace.
- **H2. Pin it in Java.** The port's headless script path already
  cannot write (it never constructs the window); a test holds both
  programs to the rule - run `set driver`/`set ntsc` and assert the
  configuration files' bytes unchanged.
- **H3. A config-folder override for the C++ program.**
  `RMT_CONFIG_DIR`, next to the existing `RMT_SCRIPT_OUTPUT`/
  `RMT_SCRIPT_LOG`: when set, `rmt.ini` and `tuning.ini` are read (and,
  outside script mode, written) there instead of the program folder -
  the twin of the Java port's `-Drmt.config.dir`.
- **H4. The harnesses run on a copy.** `compare_exports.ps1` and
  `CrossProgramExportTest` copy the two `.ini` files into a per-run
  scratch folder and point both programs at it (`RMT_CONFIG_DIR` for
  `Rmt.exe`, the config folder for the in-process port). The program
  folder's files become read-only inputs that no run can corrupt.
- **H5. Documents.** `doc/rmt_scripting.md` states the guarantee and
  the variable; the change log records both.
- **H6. Verification.** Both suites green; the pollution experiment
  (`set driver unpatched`, exit, read `rmt.ini`) byte-identical in both
  programs; `compare_exports.ps1` green on the copied configuration.

## Out of scope

- The settings architecture itself (what belongs in `rmt.ini` at all) -
  plans 41/44 own that story.
- The daily build's `dump actions` run: it changes no settings, and its
  output is the point of the run.
- `ReadRMTConfig`'s missing-file `MessageBox`, which would block a
  hidden script run on a folder without an `rmt.ini`. Pre-existing,
  same class as the missing-driver box that `Rmt.cpp` already gates
  on script mode; the harnesses always provide the file.
