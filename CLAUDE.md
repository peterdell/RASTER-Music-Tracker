# CLAUDE.md

Guidance for Claude Code when working in this repository.

## Project

RASTER Music Tracker (RMT) — a Windows tool for composing Atari XL/XE music.
This repository holds both the original C++ implementation and its in-progress
Java port, side by side:

- **C++** (the original, currently-shipping app): source in `src/cpp/`
  (tests in `src/cpp/test/`), build scripts in `build/`, assembler player
  routines in `asm/`, native runtime DLLs in `lib/x64/`. Build via
  `Rmt.sln`/`RmtTests.vcxproj` (MSBuild, both projects use `/MP`). See
  `plans/RULES.md` for C++ coding-style rules. If Windows Defender's
  real-time protection is on and unexcluded, `/MP`'s parallel `.obj` writes
  can make builds *slower*, not faster (measured 2m26s -> 6m18s on one
  machine) - run `build/setup_defender_exclusions.ps1` (needs admin, self-
  elevates via UAC) to exclude `out/`/`src/cpp/test/out/` if rebuilds feel
  unusually slow.
- **Java** (the in-progress port, see `plans/JAVA_PORT_PLAN.md`): source in
  `src/java/` (tests in `src/java/test/`, nested inside it - the main
  compile explicitly excludes `test/**`, see `pom.xml`), vendored
  non-Maven third-party jars in `lib/java/`. Build via `pom.xml` (Maven):
  `mvn -o test` from the repository root. Depends on `com.wudsn.tools.base`/
  `.base.atari` (installed locally, not built from this repo) and JUnit 5.
  An Eclipse project (`.project`/`.classpath` at the repository root)
  is also checked in, for compiling/editing in Eclipse via m2e (Maven
  Integration for Eclipse) - "File > Import > Existing Projects into
  Workspace", select the repository root. m2e resolves the same
  dependencies from `pom.xml`.

Documentation in `doc/`.

## Plan files

All plan files (e.g. files created via plan mode, task breakdowns, design plans) must be
placed in the `plans/` subfolder at the repository root. Create `plans/` if it does not
already exist.
