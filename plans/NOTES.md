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

## Phase 2 continued (2026-09-21): CTuning tests + a real "cleanup" example

Attempted to add characterization tests for `CTuning` (`GetPOKEYPitch`, `GetPitch`,
`GetAUDF` — POKEY pitch/frequency math) and hit exactly the coupling problem the plan
warned about, which led to a small, deliberate cleanup step rather than skipping the
module. Notes on what happened, since the same pattern (a "pure-looking" method dragged
into `Global.h`'s huge dependency graph by its enclosing translation unit) will very
likely recur in other files:

- `CTuning::GetPitch`/`GetAUDF`/`GetPOKEYPitch` are pure functions of their parameters
  and the private `m_clockFrequency` member — no global reads. But `m_clockFrequency`
  was previously only ever set via `InitTuning(clockFrequency, table_memory)`, which
  calls the no-arg `InitTuning()`, which reads `g_tuning`/`g_tuningRatios`/
  `g_notesperoctave` and can **terminate the process** via `MessageBox(...); exit(1);`
  if `g_tuning.basetuning` is still `0.0` (its default before setup) — a real hazard
  for an automated test binary. **Fix:** added a small, purely-additive test-only
  constructor, `explicit CTuning(ClockFrequency)`, that sets `m_clockFrequency`
  directly (`Tuning.h`). The implicit default constructor is preserved
  (`CTuning() = default;`) since the global `g_Tuning` (`Global.cpp`) depends on
  default-constructibility. No existing behavior changed.
- Bigger problem: `Tuning.cpp` had `#include "Global.h"` at the top, and `Global.h`
  transitively pulls in `Atari.h`, `AtariTrackerDriver.h`, `ChannelControl.h`,
  `SongTypes.h`, `SongUI.h`, etc. — a huge slice of the application. Since MSVC links
  a translation unit's object file as a whole, even though only `InitTuning()`/
  `GenerateTable()` (2 of ~7 methods) actually touch globals, compiling `Tuning.cpp`
  at all required resolving symbols for basically the whole app's global state, not
  just 4 variables. **Fix (a real, minimal "cleanup" example for this phase):** split
  `Tuning.cpp` into two files along the existing seam between pure math and
  global-state orchestration:
  - `Tuning.cpp` keeps `GetPOKEYPitch`, `GetPitch`, `GetAUDF`, `CalculateDeltaAUDF`,
    `GetTruePitch`, and the two-arg `InitTuning(clockFrequency, table_memory)` (just
    sets members and delegates) — **no longer includes `Global.h` at all.**
  - New `src/cpp/TuningTables.cpp` holds `GenerateTable()` and the no-arg
    `InitTuning()` — the only two methods that actually read `g_tuning`/
    `g_tuningRatios`/`g_notesperoctave`/`g_hwnd`. Added to `Rmt.vcxproj` (production
    build) right after `Tuning.cpp`.
  - This is a pure mechanical move — method bodies are byte-for-byte identical, just
    relocated — verified behavior-preserving by a full solution rebuild (see below).
  - In `RmtTests.vcxproj`, a tiny link-only stub (`src/cpp/test/TuningInitStub.cpp`,
    `void CTuning::InitTuning() {}`) satisfies the linker for the two-arg
    `InitTuning()`'s reference to the no-arg one, without pulling in
    `TuningTables.cpp`/`Global.h`. Tests never call `InitTuning()` at all — they use
    the new direct-`m_clockFrequency` constructor.
- **Takeaway for future modules:** when a file mixes a few globally-coupled methods
  with otherwise-pure ones, check whether splitting along that seam (own file per
  concern) is enough to make the pure part testable — much cheaper than either (a)
  linking near-the-whole-app into the test binary, or (b) skipping the module
  entirely. Not every file will have such a clean seam, though; when the coupling is
  fundamental (not just "wrong file"), that's genuinely deferred to a larger
  redesign, not a quick split.

Added tests (15 more, 43 total, all passing in both Debug and Release):
- `TuningTypesTests.cpp` — `TTuningSettings::Initialize` (PAL/NTSC base tuning) and
  `TTuningRatios::Initialize` (interval ratios), fully pure, hand-verified expected
  values (simple integer GCD reduction, e.g. the `MIN_2ND` literal `40/38` is actually
  stored as `20/19` after `CFraction` normalizes it — characterized explicitly).
- `TuningTests.cpp` — `CTuning::GetPitch`/`GetAUDF`/`GetPOKEYPitch` using the PAL POKEY
  clock (1773447 Hz, matching `CAtari::FREQ_17_PAL`). Expected values were captured by
  running the actual implementation once with placeholder assertions and reading the
  real output from the test-failure diagnostics (a "golden master" approach) rather
  than hand-derived, since the branching pitch-modulo logic is too intricate to safely
  hand-verify — this is the right technique for characterization tests in general and
  is now the precedent for any future test with non-trivial arithmetic/branching.

Verified via full `MSBuild Rmt.sln -t:Rebuild` for both configurations again: `Rmt.exe`
and `RmtTests.exe` both build clean (0 errors, only the pre-existing `asap.c`
warnings), and `RmtTests.exe` reports `43 tests from 6 test suites ... PASSED` in both
Debug and Release.

## Build verification workflow (2026-09-21, standing preference)

The user pointed out that Debug and Release aren't just "same binary plus debug
symbols" (Debug also disables optimization, enables `/RTC1` runtime checks, and links
the debug CRT), but since Claude doesn't attach a debugger and only reads compiler/
linker text output, it gets no benefit from Debug's symbols. **Going forward, verify
changes by building/running only the Release|x64 configuration**, not both. Only also
build/run Debug when a change is plausibly optimization- or undefined-behavior-
sensitive, or before a larger checkpoint. (This is also saved as a standalone
cross-session memory: `build-verification-config.md`.) Everything in this file from
here on that says "verified" means Release-only unless stated otherwise.

## Phase 2 continued (2026-09-21): `lzss_sap.cpp`/`CCompressLzss`

Added tests for the SAP-R LZSS compressor (11 more, 54 total, all passing):

- `lzss_sap.cpp` needed no cleanup split — it only includes `lzss_sap.h` (which
  includes `StdAfx.h` for basic types, not `Global.h`), so it was already free of the
  `Global.h` coupling problem hit with `Tuning.cpp`.
- `CCompressLzss::Optimise_AUDC`/`Optimise_AUDCTL`/`Optimise_AUDF` were `private`.
  Since each is a small, pure, single-9-byte-frame transform with no dependency on
  the rest of the class's state, and this codebase has no LZSS *decoder* to invert
  `LZSS_SAP()`'s output and recover their effect indirectly, the only way to test them
  meaningfully was to make them directly callable. **Fix (same pattern as `CTuning`'s
  test-only constructor): moved these three method declarations from `private` to
  `public` in `lzss_sap.h`.** Purely a visibility change — no behavior change, and
  `LZSS_SAP()` still calls them exactly as before internally. Added 8 hand-verified
  tests (`OptimiseAudcTest`, `OptimiseAudcTlTest`, `OptimiseAudfTest`) — these were
  straightforward to hand-derive since the bit logic is simple masking, unlike
  `CTuning`'s branching modulo arithmetic.
- Added 3 end-to-end `LzssTest` cases for `LZSS_SAP()` itself (the actual LZSS
  matching/bit-packing), using the golden-master capture technique again: placeholder
  expected byte vectors, run once, read the real output from the `EXPECT_EQ` failure
  diagnostics (`--gtest_filter` to isolate one test, `2>/dev/null` to suppress
  `LZSS_SAP()`'s own internal `fprintf(stderr, ...)` debug/stats dump which otherwise
  drowns out the gtest failure text), then filled in the real values. Note: `LZSS_SAP()`
  always runs with `show_stats=2` hardcoded internally (full verbose stats to
  `stderr`), so running these tests is noisy on stderr — that's real, unmodified
  behavior, not a test artifact worth suppressing at the source.
- Destination buffers for compression must be sized generously (used `src.size() * 3
  + 64` in the tests): a literal byte costs 9 encoded bits (1 flag + 8 data), i.e.
  worse than 8 bits raw, so worst case compressed output can exceed input size for
  small/incompressible inputs.

Verified via a full Release|x64 solution rebuild (see workflow note above): `Rmt.exe`
and `RmtTests.exe` both build clean (0 errors) and all 54 tests pass.

## Status

- [x] Read `plans/OVERALL_PLAN.md`, `README.md`, and linked docs present in the repo.
- [x] Surveyed current `src/` layout and build files.
- [x] Decisions confirmed with user (move scope, test framework, vendoring approach,
      test project location).
- [x] Phase 1 (move `src/*` to `src/cpp/`) done, build-verified, and committed
      (`ea3354b`).
- [x] Phase 2 started: GoogleTest vendored + wired up, committed (`52b9073`).
- [x] Phase 2 continued: split `Tuning.cpp`/`TuningTables.cpp`, 43 tests, committed
      (`6cdabe7`).
- [x] Phase 2 continued: `lzss_sap.cpp`/`CCompressLzss` tests, 54 tests total (11 new).
      Not yet committed.
- [ ] Ask user whether to commit this step.
- [ ] Phase 2 continued: more characterization tests before any cleanup, roughly in
      order of increasing coupling:
      - `AssemblerTypes`, `Song` fixed-format struct parsing (`Track`, `Instruments`)
        where feasible without a live Atari/POKEY emulation.
      - Before starting a new module, always check whether it `#include`s `Global.h`
        (or another huge header) and, if so, whether the globally-coupled methods can
        be split out the same way as `Tuning.cpp`/`TuningTables.cpp` — check this
        early, since it changes the scope of the work.
      - When a method needed for testing is `private` but pure (no dependency on
        other private state) and there's no other way to exercise it (no decoder/
        inverse operation, no way to observe its effect indirectly), the established
        pattern here is: make it `public` with a one-line comment explaining why. Pure
        visibility changes, no behavior change.
      - Everything touching `g_Song`/other globals, MFC dialogs, and the timer-driven
        sound generation is expected to need actual decoupling work (the "cleanup"
        half of Phase 2) before it's testable at all — do not attempt to test that
        code as-is; redesign first, matching the plan's own warning about UI/model/
        timer mixing.
