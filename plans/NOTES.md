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

## Phase 2 started (2026-09-21): GoogleTest infrastructure + first characterization tests

Decisions confirmed with user for this step:
- GoogleTest source is **vendored** (not a NuGet package), consistent with how
  `src/cpp/asap/` already vendors ASAP as source. Pinned to **v1.15.2**, unmodified,
  under `src/cpp/test/googletest/` (`include/`, `src/`, `LICENSE`, `README.md`). Only
  `gtest` was vendored, not `gmock` (not needed yet; add the same way later if mocking
  becomes necessary once UI/model are decoupled).
- The test project and test sources live at **`src/cpp/test/`**, colocated with the
  C++ code it exercises.

What was built:
- `src/cpp/test/RmtTests.vcxproj`: a new console-subsystem project added to `Rmt.sln`
  (GUID `9C6C52DE-D787-4D5B-8738-837AA9DD020C`), `Debug|x64`/`Release|x64` only (matches
  the rest of the solution). `UseOfMfc=Dynamic` and `CharacterSet=MultiByte` mirror
  `Rmt.vcxproj` because several production headers (`StringUtility.h`, `Notes.cpp`, via
  `StdAfx.h`) pull in MFC (`CString` etc.) — the test binary needs to link the same way
  to compile them unmodified. `OutDir`/`IntDir` point at `out\$(Configuration)\test\` /
  `test-intermediate\`, separate from `out\$(Configuration)\output\` (the shipped Rmt
  build), so `build/build_rmt-daily.bat`'s xcopy of the output folder never picks up
  `RmtTests.exe`.
- Test project compiles GoogleTest (`googletest/src/gtest-all.cc` + `gtest_main.cc`)
  together with the **actual production `.cpp` files** it's testing, referenced
  directly via relative `Include="..\Fraction.cpp"` paths — no code was copied or
  duplicated. `AdditionalIncludeDirectories` = `..;googletest\include;googletest`.
- First characterization tests (28, all passing) for the three least-coupled, most
  self-contained modules found so far:
  - `FractionTests.cpp` — `CFraction` (arithmetic, construction/reduction, sign
    handling, conversions).
  - `StringUtilityTests.cpp` — `CStringUtility::EndsWithNoCase`.
  - `NotesTests.cpp` — `CNotes` (`IsValidNote`, `GetNote`, `GetNoteAndScale`).
- Verified via full `MSBuild Rmt.sln -t:Rebuild` for both configurations: both `Rmt.exe`
  and `RmtTests.exe` build with 0 errors, and running `RmtTests.exe` shows
  `28 tests from 3 test suites ran ... PASSED`.

### Known-behavior oddities found while characterizing (not fixed — flagged for a
### deliberate decision during cleanup, not silently patched)

- **`CFraction::operator==` is effectively always `false`.** It computes the reduced
  difference of the two fractions and then checks `ff.denominator == 0`, but
  `CFraction`'s own constructor/`simplify()`/`gcd()` always leaves a non-zero
  denominator (even when the numerator reduces to 0), so the check can never succeed.
  It looks like it should be checking `ff.numerator == 0` instead. A repo-wide search
  found **no current caller** of this operator (`RmtView.cpp`'s `ReadFraction`/
  `WriteFraction` only touch `.numerator`/`.denominator` fields directly), so this is
  dead-but-broken code today, not a live bug — but the Java port must decide
  deliberately whether to preserve or fix this behavior once/if it's used.
  Characterized as-is in `FractionTests.cpp::EqualityOperatorIsCurrentlyAlwaysFalse`.
  (Also note: writing `a == b` directly between two `CFraction`s no longer compiles
  under C++20 — the implicit `operator double()` plus the compiler's synthesized
  reversed `b == a` candidate make it ambiguous with the built-in `double==double`.
  The test calls `a.operator==(b)` explicitly to route around this; any other code
  written against this operator will need the same workaround, or the class needs a
  `const`-correct/`<=>`-friendly rewrite.)
- **`CNotes::IsValidNote` is off by one from its own documented range.** `NOTESNUM`
  is commented "Notes 0-60 inclusive" (61 values), but the check is `note <= NOTESNUM`
  (61) instead of `< NOTESNUM`, so note 61 is also accepted as "valid". Nothing
  currently crashes because `GetNote()`'s backing array has a few extra padding
  entries, but it's a latent bug worth a conscious fix-or-preserve decision later.
  Characterized as-is in
  `NotesTests.cpp::IsValidNoteAcceptsOneOffTheEndOfItsDocumentedRange`.

## Status

- [x] Read `plans/OVERALL_PLAN.md`, `README.md`, and linked docs present in the repo.
- [x] Surveyed current `src/` layout and build files.
- [x] Decisions confirmed with user (move scope, test framework, vendoring approach,
      test project location).
- [x] Phase 1 (move `src/*` to `src/cpp/`) done, build-verified, and committed
      (`ea3354b`).
- [x] Phase 2 started: GoogleTest vendored + wired up, 28 characterization tests
      passing for `CFraction`, `CStringUtility`, `CNotes`. Not yet committed.
- [ ] Ask user whether to commit this step.
- [ ] Phase 2 continued: more characterization tests before any cleanup, roughly in
      order of increasing coupling:
      - `TuningTypes`/`Tuning` (uses `CFraction`; check `Tuning.cpp` for MFC/global
        coupling before starting).
      - `lzss_sap.cpp`/`CCompressLzss` (pure data transform, good candidate, but
        larger/more intricate — needs known-good input/output fixtures, e.g. round
        trip a byte buffer, rather than guessed expected values).
      - `AssemblerTypes`, `Song` fixed-format struct parsing (`Track`, `Instruments`)
        where feasible without a live Atari/POKEY emulation.
      - Everything touching `g_Song`/other globals, MFC dialogs, and the timer-driven
        sound generation is expected to need actual decoupling work (the "cleanup"
        half of Phase 2) before it's testable at all — do not attempt to test that
        code as-is; redesign first, matching the plan's own warning about UI/model/
        timer mixing.
