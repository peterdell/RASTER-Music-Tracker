# Notes for OVERALL_PLAN.md

Running notes for the multi-session C++ -> Java port project. Update this file as work
progresses so a new session can pick up context quickly.

## Background read (2026-09-21)

Read `README.md` and the docs it links to that exist in this repo:
`doc/rmt_changes.md`, `doc/rmt_versions.md`, `doc/rmt_tracker.md`, `doc/rmt_format.md`.
(`doc/rmt_en.html` / `doc/rmt_en_128.html` are the full end-user manuals; not read in
full yet, only referenced.)

Key facts:
- RMT is a Windows MFC app for composing Atari XL/XE music, originally by Radek Sterba
  (2002-2009), continued by Vin Samuel (2021-2024), now maintained by Peter Dell (JAC!).
- Current dev version is 1.35 (daily), stable is 1.34.00. Module file format v1 is
  documented in `doc/rmt_format.md`; a draft v2 format is also described there.
- The tracker embeds two emulated pieces of hardware: a MOS 6502 CPU emulator and one/two
  POKEY sound chips, currently provided via external DLLs (`sa_c6502.dll`,
  `sa_pokey.dll`/`apokeysnd.dll`). `doc/rmt_tracker.md` documents a future plan to replace
  these DLLs with the C version of ASAP (already vendored partially under `src/asap`).
- Known architectural issues called out by the maintainer in `doc/rmt_tracker.md`:
  - Code heavily uses macros and global variables; "not testable".
  - DLL loading/initialization is broken/uses workarounds for frequency and PAL/NTSC
    detection.
  - Duplicated code paths for the same logical operation (e.g. PAL/NTSC toggle).

## Current repository layout

- `Rmt.sln` (root) + `src/Rmt.vcxproj` — single VS project, MFC app, VS2026/toolset v145,
  64-bit only as of the 1.35 changes.
- `build/build_rmt.bat`, `build/build_rmt-daily.bat`, `build/build_rmt_pre.bat` — build
  scripts (batch files), plus `build/build_rmt-daily-excluded-extensions.txt`.
- `src/` — 173 files directly in the folder (all C++ MFC sources: doc/view classes,
  dialogs, IO/exporters, song/instrument model, etc.), plus two subfolders:
  - `src/asap/` — vendored ASAP C sources (`asap.c`, `astil.c`, `wasap.c`, ...).
  - `src/res/` — Windows resources (icons, cursors, bitmaps, `Rmt.rc2`).
- `asm/` — 6502 assembler player routines (referenced by the plan, not yet inspected in
  detail).
- No automated test framework is present. `src/RmtTest.cpp` / `src/SongExporterTest.h`
  (`CRmtTest`, `CSongExporterTest`) are ad-hoc manual/exploratory test helpers invoked
  from the app itself (e.g. via command line), not a real unit test suite. This confirms
  the plan's premise: **there are no tests to characterize current behavior yet.**
- Sound generation is driven by Windows timers and is mixed into the UI code (per the
  plan's own note); this needs to be untangled before/while adding tests.

## Plan phases (from `plans/OVERALL_PLAN.md`)

1. Create `src/cpp/`, move all current `src/*` files into it, adapt all references
   (`.sln`, `.vcxproj`, `.vcxproj.filters`, resource compiler paths, `#include` paths,
   build `.bat` scripts). Build must keep working at every step.
2. Clean up the existing C++ source and add characterization tests for current behavior
   (needed before the Java port can be validated for correctness).
3. Port to Java (modeled after the prior `dis6502` -> Java port), eventually living
   alongside the C++ code, likely under a sibling `src/java/` (not yet decided/asked).

## Decisions made

- **2026-09-21**: Everything in `src/` (including `src/asap/` and `src/res/`, and the
  project files `Rmt.vcxproj`/`.filters`/`.user`/`.rc`/`.args.json`/`resource.h(m)`)
  moved under `src/cpp/` together, using `git mv` to preserve history. This means no
  relative paths *between* the moved files needed to change (their relative structure
  is identical, just one level deeper) — only paths that reach back out to sibling
  repo-root folders (`../build`, `../doc`, `../lib`, `../README.md`) needed an extra
  `..\` prepended.
- **2026-09-21**: Characterization tests (plan step 2, not started yet) will use
  **GoogleTest**, integrated via MSBuild/VS Test Explorer.

## Phase 1 completed (2026-09-21): move `src/*` -> `src/cpp/*`

What was changed:
- `git mv` of all 173 tracked files (plus 4 untracked/gitignored stray files:
  `PokeyRederer.cpp.bak`, `PokeyRederer.h.bak`, `Rmt.aps`, `Rmt.vcxproj.bak`) from
  `src/` into `src/cpp/`, preserving the `asap/` and `res/` subfolder structure.
- `Rmt.sln` (repo root): project reference updated from `src\Rmt.vcxproj` to
  `src\cpp\Rmt.vcxproj`.
- `src/cpp/Rmt.vcxproj` and `src/cpp/Rmt.vcxproj.filters`: every `Include="..\build\...`,
  `..\lib\...`, `..\doc\...`, `..\README.md` path got an extra `..\` (now
  `..\..\build\...` etc.), since the project file is now one directory level deeper.
  Paths using `$(SolutionDir)` (OutDir/IntDir, the pre/post-build event commands) did
  **not** need changes since they're already root-anchored.
- `build/build_rmt_pre.bat`: this script's working directory is the *project* directory
  (an MSBuild `PreBuildEvent` default), which changed from `src/` to `src/cpp/`, so its
  two `xcopy ..\doc\... ..\rmt\docs` lines got an extra `..\` each. Comment updated too.
- No other files referenced `src\Rmt...`, `src\asap`, or `src\res` (checked via
  repo-wide grep), and the root `.gitignore` has no `src/`-specific entries.

Verification performed: full `MSBuild Rmt.sln -t:Rebuild` for **both** `Debug|x64` and
`Release|x64` succeeded with 0 errors (only pre-existing warnings in vendored
`src/cpp/asap/asap.c`, unrelated to the move), and `out/Debug/output/Rmt.exe` /
`out/Release/output/Rmt.exe` were produced. The move is complete and the build is
confirmed green. Changes are staged/modified in the working tree but **not committed**
yet (per the "only commit when the user explicitly asks" rule).

## Status

- [x] Read `plans/OVERALL_PLAN.md`, `README.md`, and linked docs present in the repo.
- [x] Surveyed current `src/` layout and build files.
- [x] Decisions confirmed with user (move scope, test framework).
- [x] Phase 1 (move `src/*` to `src/cpp/`) done and build-verified (Debug + Release).
- [ ] Ask user whether to commit the Phase 1 change.
- [ ] Phase 2: clean up the existing C++ source and add GoogleTest-based
      characterization tests before touching behavior, since none exist today.
