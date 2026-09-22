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

## Phase 2 continued (2026-09-21): `CTracks` (`Tracks.cpp`/`IO_Tracks.cpp`)

`AssemblerTypes.h/cpp` turned out to be a single trivial enum with nothing to test —
skipped. Moved on to `CTracks`, which is the "fixed-format struct parsing" candidate
the plan called out (`TrackToAta`/`AtaToTrack` encode/decode the compact on-Atari track
byte format). Added 26 tests (69 total, all passing):

Found and fixed two real, independent bugs while reading `Tracks.cpp` (both
behavior-preserving for the only production caller, the global `g_Tracks`, but real
hazards for constructing any other `CTracks` instance, e.g. in a test):
- **Uninitialized-pointer read**: `CTracks`'s constructor did
  `if (m_track) delete[] m_track;` before `m_track` was ever assigned, and `m_track`
  had no default member initializer. For the single global `g_Tracks`, C++ static
  zero-initialization happened to make this safe (globals start zeroed before their
  constructor runs), but constructing a second, non-global `CTracks` (as a test would)
  reads indeterminate stack/heap memory there — if that garbage happens to look
  non-null, `delete[]` gets called on a bogus pointer. **Fixed** with a default member
  initializer, `TTrack* m_track = nullptr;` (`Tracks.h`).
- **`delete` instead of `delete[]`**: `m_track` is allocated with
  `new TTrack[TRACKSNUM]` but freed with plain `delete` in both the constructor's
  pre-check and the destructor — undefined behavior for an array allocation. `TTrack`
  has no destructor of its own so this likely never visibly corrupted anything in
  practice, but it's still UB and worth fixing outright, not just characterizing.
  **Fixed**: both occurrences changed to `delete[]` (`Tracks.cpp`).
- Also fixed an **include typo** in `Tracks.h`: it self-included `"Tracks.h"` (a
  harmless no-op given `#pragma once`) where it clearly meant `"TracksTypes.h"` (the
  header that actually defines `TTracksAll`/`TRACKSNUM`, which `Tracks.h` uses). It
  only ever compiled because something else in the real app happened to include
  `TracksTypes.h` first; a standalone include of `Tracks.h` (as the test project needs)
  would not have compiled otherwise. **Fixed**: corrected the include.

Coupling split (same pattern as `Tuning.cpp`/`TuningTables.cpp`): `Tracks.cpp` included
`Global.h` only for 7 of its ~25 methods — the ones that record undo history (`g_Undo`)
and consult `g_respectvolume`: `DelNoteInstrVolSpeed`, `SetNoteInstrVol`, `SetInstr`,
`SetVol`, `SetSpeed`, `SetEnd`, `SetGo`. Moved exactly those into a new
`src/cpp/TracksEdit.cpp` (added to `Rmt.vcxproj`); `Tracks.cpp` no longer includes
`Global.h` at all. `IO_Tracks.cpp` (the `TrackToAta`/`AtaToTrack`/`Save*`/`Load*`
methods) and `IOHelpers.cpp` (its `Hexstr`/`NextSegment`/etc. free-function
dependencies) were **already** `Global.h`-free — no split needed there, just added
directly to `RmtTests.vcxproj`.

Tests added (`TracksTests.cpp`):
- `TracksTest` fixture (constructs a real `CTracks`, calls `InitTracks()`): empty-track
  detection, `ClearTrack`, `InsertLine`/`DeleteLine` (line-shifting), `CalculateNotEmpty`,
  `CompareTracks`, and `TrackOptimizeVol0` — the last one is a genuinely intricate
  redundant-zero-volume cleanup pass, hand-traced carefully against the source before
  writing the test's expected values (see the test's own comment for the exact
  redundancy rule it exercises).
- `TracksModifiedValueTest`: `GetModifiedNote`/`GetModifiedInstr`/`GetModifiedVolumeP` —
  pure transform functions (transpose/wrap/scale-and-clamp), all hand-verified.
- `TrackAtaFormatTest`: two full round-trip tests (`TrackToAta` encode → exact expected
  bytes → `AtaToTrack` decode → original field values restored) for a single-note line
  and a leading-pause-then-note case. Byte values were hand-derived bit-by-bit from the
  format comments already present in `IO_Tracks.cpp`, not captured — this format's
  bit-packing is simple enough (unlike LZSS's) to safely hand-verify, and all 15 of
  this batch's hand-derived tests passed on the very first run, which cross-checks the
  hand-derivation was correct.

Verified via a full Release|x64 solution rebuild: `Rmt.exe` and `RmtTests.exe` both
build clean and all 69 tests pass.

## Phase 2 continued (2026-09-21): `CInstruments` (`Instruments.cpp`/`IO_Instruments.cpp`)

Unlike `CTracks`, `CInstruments`'s coupling was split across **two** files (both
`Instruments.cpp` and `IO_Instruments.cpp` included `Global.h`), and touched more
globals: `g_AtariTrackerDriver`, `g_Atari` (a full `CAtari` instance — much heavier
than `Tracks`'s trivial `g_Undo`/`g_respectvolume`), `g_tracks4_8`, and
`g_keyboard_RememberOctavesAndVolumes`. Added 5 tests (74 total, all passing):

- Found the **same `delete`/`delete[]` mismatch** as `CTracks` in `CInstruments`'s
  destructor (`m_instr` allocated with `new TInstrument[INSTRSNUM]`, freed with plain
  `delete`) — fixed to `delete[]`. (The constructor here was already safe: it
  unconditionally assigns `m_instr` with no prior read, unlike `CTracks`'s.)
- Split **both** files along the `g_Atari`/`g_AtariTrackerDriver` coupling seam:
  - New `InstrumentsCore.cpp`: constructor/destructor, `SetCanvas`, `InitInstruments`,
    `CheckInstrumentParameters`, `RecalculateFlag`, `CalculateNotEmpty`, `GetNote` —
    none of these touch any global. `Instruments.cpp` keeps `ClearInstrument`,
    `SetEnvelopeVolume`, `GetFrequency`, `MemorizeOctaveAndVolume`,
    `RememberOctaveAndVolume` (all read a global) plus the large `shpar`/`shenv`
    static data tables.
  - New `InstrumentsAtaFormat.cpp`: `InstrToAta`, `AtaToInstr`, `AtaV0ToInstr` — the
    "fixed-format struct parsing" functions (compact on-Atari instrument byte format),
    which only need the trivial `g_tracks4_8` int, not `g_Atari`. `IO_Instruments.cpp`
    keeps `SaveInstrument`/`LoadInstrument`/`SaveAll`/`LoadAll`/`Update` (the last of
    which is the only one needing the heavy `g_Atari`).
  - Both new files added to `Rmt.vcxproj`; production `Rmt.exe` rebuild verified clean.
- For the test project, `InitInstruments()` (kept in `InstrumentsCore.cpp`) still
  calls `ClearInstrument()` (which stayed behind, needing `g_Atari`), so linking
  needed one more small stub: `src/cpp/test/InstrumentsStub.cpp` provides both the
  storage for `g_tracks4_8` (a real, read/write global the tests flip between mono
  (4) and stereo (8) to test both `InstrToAta`/`AtaToInstr` packing paths) and an
  empty-body link-only stub for `ClearInstrument()`, since tests never call
  `InitInstruments()` (they set up `TInstrument` fields directly via `GetInstrument()`
  instead).
- All 5 `InstrToAta`/`AtaToInstr`/`AtaV0ToInstr` tests use **hand-derived** expected
  bytes (worked through the bit-packing arithmetic manually, including the format's
  intentional mono-vs-stereo envelope-volume lossiness — mono packs a single nibble
  so `VOLUMER` collapses to `VOLUMEL` on round-trip, stereo preserves both). All 5
  passed on the first run, cross-checking the by-hand derivation.

Verified via a full Release|x64 solution rebuild: `Rmt.exe` and `RmtTests.exe` both
build clean and all 74 tests pass.

## Phase 2 continued (2026-09-21): `CSong` confirmed as the "God Object" — deferred; `CSAPFile` tested instead

Investigated `CSong` (`Song.h`/`Song.cpp`, 3224 lines) as the next "fixed-format struct
parsing" candidate (`SongToAta`/`AtaToSong`, in `IO_Song.cpp`). **Confirmed this is
exactly the class the plan's own risk note anticipated ("the current code fully mixes
UI and model") and is not a quick-split candidate like `Tuning`/`Tracks`/`Instruments`
were:**

- `SongToAta`/`AtaToSong` themselves are actually pure (only touch `m_song`/`m_songgo`,
  plain `int` arrays, plus the trivial `g_tracks4_8` global) — the problem is
  constructing a `CSong` at all. Its constructor
  (`CSong::CSong()`, `Song.cpp`) unconditionally does
  `m_PokeyController = new CPokeyController(&g_Atari);` — meaning even a bare,
  default-constructed `CSong` immediately pulls in `CPokeyController` and the full,
  heavy `CAtari` global. Unlike `CTracks`/`CInstruments`, whose constructors were
  clean, there's no cheap seam here (adding a test-only constructor that skips real
  initialization felt too invasive/behavior-risky to do opportunistically, unlike the
  earlier additive-only seams).
- **Decision: skip `CSong` for now.** Don't force a large redesign into an
  otherwise-incremental testing session. Revisit once/if a deliberate decoupling pass
  on `CSong`'s construction is undertaken (a real "cleanup" task, not a quick split).

Pivoted to `CSAPFile` (`SAPFile.h`/`.cpp`, SAP file header export) instead — a small,
already well-encapsulated class whose only external coupling is `Init(const CSong&)`,
which tests don't need to call. Added 4 tests (78 total, all passing):

- `Export()` took `std::ofstream&`; **widened the parameter type to `std::ostream&`**
  (`SAPFile.h`/`.cpp`) — every existing call site already passes an actual
  `std::ofstream`, which satisfies `std::ostream&` too (public inheritance), so this
  is behavior-preserving and lets tests capture output with a `std::ostringstream`
  instead of touching a real file. Same "widen a parameter to the interface actually
  needed" pattern as any other minimal testability seam here.
- `SAPFile.cpp`'s only coupling is that `SAPFile.h` `#include`s `Song.h` (needed for
  `Init()`'s `const CSong&` parameter), so the translation unit needs 4 `CSong` method
  symbols resolved at link time (`GetName`, `IsStereo`, `IsNTSC`,
  `GetInstrumentSpeed`) even though tests never call `Init()`. Rather than link real
  `Song.cpp` (which would drag in the `CSong` constructor problem above), added
  trivial link-only stub bodies for exactly those 4 methods
  (`src/cpp/test/SAPFileStub.cpp`) — same pattern as the `ClearInstrument()`/
  `InitTuning()` stubs.
- **Found two more real hazards while writing tests, both characterized (not fixed)
  since they'd need a deliberate redesign, unlike the earlier one-line-fix bugs:**
  - `Export()`'s `DEFSONG` line prints `m_songs` instead of `m_defsong` — a genuine
    copy-paste bug (`ou << "DEFSONG " << m_songs << EOL;`). Characterized as-is in
    `SAPFileTest.ExportTypeBWithInitAndPlayer`'s comment and expected output, not
    fixed, since fixing it changes real export output and deserves a deliberate call.
  - `ThrowRuntimeException(...)` (used for `Export()`'s empty/invalid-TYPE error
    paths) is **not a C++ exception** — `CRuntimeException`'s constructor shows a
    blocking `MessageBox` and then calls `exit(2)`, unconditionally terminating the
    whole process. This is the same class of hazard as `CTuning::InitTuning()`'s
    `MessageBox`+`exit(1)` guard. Tests here never exercise those two branches (always
    set a valid `"B"` or `"R"` type) to avoid hanging/killing the test binary. Whoever
    eventually wants to test or use those paths should treat "rename it away from
    `Exception`, or make it throw a real, catchable exception" as a prerequisite, not
    something to route around per-callsite.

Verified via a full Release|x64 solution rebuild: `Rmt.exe` and `RmtTests.exe` both
build clean and all 78 tests pass.

## Phase 2 continued (2026-09-21): `Keyboard2NoteMapping` and `CASMFileBuilder`

Two more small, self-contained modules, both with zero real coupling issues. Added 12
tests (90 total, all passing):

- `Keyboard2NoteMapping.cpp` (`NoteKey`/`NumbKey`/`Numblock09Key`, 3 free functions
  doing lookups into fixed 256-entry virtual-key-code tables): only needs
  `g_keyboard_layout` (a `KeyboardLayout` enum global), stubbed the same way as
  `g_tracks4_8` earlier. 7 tests, hand-verified by carefully re-indexing the QWERTY/
  AZERTY/numblock tables against real Windows virtual-key constants (`VK_A=0x41`,
  `VK_Z=0x5A`, `VK_0=0x30`, `VK_NUMPAD0=0x60`, etc.) — all passed first try.
- `ASMFileBuilder.cpp` (`CASMFileBuilder::BuildInstrumentData`/`BuildTracksData`/
  `BuildSongData`, static methods generating ASM export text from byte buffers): zero
  globals, zero `Global.h`, no split needed at all. Only gotcha: the test file itself
  needs `#include "StdAfx.h"` before `ASMFileBuilder.h`, since that header uses
  `CString` without including anything that declares it — production `.cpp` files get
  away with this only because `StdAfx.h` is always included first via convention. 5
  tests for `BuildInstrumentData`/`BuildTracksData` (didn't get to `BuildSongData` yet
  — its jump/goto encoding state machine is the most intricate of the three).
  - **Found a fragile-but-not-obviously-wrong contract**: `BuildTracksData`'s trailing
    validity check scans `track_pos[0..65535]` unconditionally (not just the
    `[from, to)` range the function actually processes), so any caller must pass an
    array of at least 65536 `int`s or this reads out of bounds. Characterized (test
    allocates a real 65536-entry `std::vector<int>`), not changed, since this might be
    an intentional convention matching the emulated Atari 64K address space used
    elsewhere in the codebase — flagging it here in case that assumption turns out to
    be wrong when `BuildSongData` or real callers are examined more closely.

Verified via a full Release|x64 solution rebuild: `Rmt.exe` and `RmtTests.exe` both
build clean and all 90 tests pass.

## Phase 2 continued (2026-09-21): finished `CASMFileBuilder::BuildSongData`

Added the 3 remaining `BuildSongData` tests (93 total, all passing). This function
encodes song lines (`numTracks` bytes each) plus an optional 4-byte "goto" sequence:
`0xFE`, an unused filler byte, then a little-endian target address (relative to
`start`) that gets converted back into a `?line_NN` label reference — the 4-byte
shape (marker + filler + 2 address bytes) mirrors `CSong::SongToAta()`/`AtaToSong()`'s
own goto encoding from the earlier `IO_Song.cpp` investigation (`dest[apos]=254`,
`dest[apos+1]=go` (unused by this ASM builder), `dest[apos+2..3]=`the 16-bit address).
All 3 tests were hand-derived by tracing the `jmp` state machine (0 = normal byte,
-1/-2 = waiting for the two address bytes, >0 = address accumulated, being resolved)
byte-by-byte, including the error-comment path's unusual format string
`"$ % 04x[% x:% x]"` — the space immediately after each `%` is consumed as the (no-op,
for `%x`) space flag rather than printed literally, verified correct by all 3 tests
passing on the first run.

Verified via a full Release|x64 solution rebuild: `Rmt.exe` and `RmtTests.exe` both
build clean and all 93 tests pass.

## Phase 2 continued (2026-09-21): fresh `Global.h`-free survey — `ChannelControl` and `RmtCommandLineInfo`

Did a fresh repo-wide survey (`grep -L '"Global.h"' src/cpp/*.cpp`, then filtered out
already-covered/tiny/dialog files) rather than continuing to guess at candidates one at
a time. Found several more `CSong`-blocked files worth noting so they aren't
re-investigated later:

- `LZSSFile.cpp` (`CLZSSFile::GetFrameSize`) and `SongExport.cpp`
  (`CSongExport`) both take/hold a `CSong&`/`CSongContainer&` — blocked by the same
  deferred `CSong` construction problem as before; a stubbed method isn't enough here
  since the test would need an actual instance to pass by reference, not just a
  resolved symbol.
- `Shell.cpp` (`CShell::OpenFile`/`OpenLocalFile`) wraps real Windows Shell API calls
  (`ShellExecute`) — not a unit-test candidate as-is (would actually try to launch
  files/programs); would need dependency injection to test meaningfully, out of scope
  for a characterization pass.
- `WaveFile.cpp` similarly wraps real `mmio*` Windows Multimedia I/O calls, writing an
  actual file - same category as `Shell.cpp`, skipped for the same reason.

Added 14 tests (107 total, all passing) for two clean, self-contained, zero-coupling
classes found in the same survey:

- `ChannelControl.cpp` (`CChannelControl`): per-channel on/off/toggle/solo state,
  backed by a `std::vector<bool>`, no globals, no `Song`/`Atari` dependency at all.
  Notable behavior characterized: `SetChannelSolo()` toggles — soloing an
  already-solo'd channel (the only one on) turns *everything* back on rather than
  leaving it soloed, which reads as intentional ("press solo again to un-solo") once
  traced through, not a bug.
- `RmtCommandLineInfo.cpp` (`CRmtCommandLineInfo`, an MFC `CCommandLineInfo`
  subclass parsing `/SCRIPT:`/`/TEST:` command-line switches): also zero globals.
  Compiles/links fine as an MFC subclass in the test project (no special stubbing
  needed beyond what's already linked for `CString` etc.).

Verified via a full Release|x64 solution rebuild: `Rmt.exe` and `RmtTests.exe` both
build clean and all 107 tests pass.

## Phase 2 continued (2026-09-21): survey of `Global.h`-*having* files; `CPokeyStream`

Followed up the "`Global.h`-free" survey with the complementary one: `grep -l
'"Global.h"' src/cpp/*.cpp`, filtered to skip already-covered files, GUI/dialog files
(`GUI_*`, `MainFrm`, `OptionsDialog`, `PokeyView`, `Rmt.cpp`, `RmtView`, `SongUI`,
`TracksControl`, `TuningDialog`, `effectsdlg`), the already-produced coupled-half
split-off files (`TracksEdit.cpp`, `TuningTables.cpp`), and `Song.cpp`/`IO_Song.cpp`
(still deliberately deferred with `CSong`). Findings from the ones actually opened:

- **`AtariBinaries.cpp`** (0 direct `g_*` refs, but calls `GetResourceFilePath()` from
  `Global.h`, which reads real files from an on-disk `resources/` folder via
  `CFile`/`CByteArray`) — a real-file-I/O candidate like `Shell.cpp`/`WaveFile.cpp`,
  not pure buffer logic. Skipped for the same reason.
- **`SAPFileExporter.cpp`** (0 direct `g_*` refs) — takes `CSongExport&`, blocked by
  the same deferred `CSong` chain as `LZSSFile.cpp`/`SongExport.cpp`, plus also loads
  a real binary resource file (`vu_player_v2.obx`) via `GetResourceFilePath()`. Two
  independent reasons to skip.
- **`Messages.cpp`** (3 `g_*` refs, tiny) — `SendInfoMessage`/`SendErrorMessage` just
  route text to either `OutputDebugString` (nothing to assert on from a test — no
  observable return value or side effect worth capturing) or a blocking `MessageBox`
  (gated behind a file-scope `g_statusBar` pointer that a standalone test TU can't
  reach since it's not declared `extern` in the header, so it always stays `nullptr`
  in isolation — safe, but also nothing meaningful to test). Skipped: not because it's
  hazardous, but because there's no assertable pure behavior once isolated.

Added 9 tests (116 total, all passing) for **`CPokeyStream`** (`PokeyStream.cpp`,
records POKEY register frames during quick-play for later SAP-R/LZSS export). Like
`Tuning`/`Tracks`, this file mixes a few genuinely coupled methods
(`StartRecording()` needs `const CSong&` for `CLZSSFile::GetFrameSize()`; `Record()`
and `FinishedRecording()` dereference a `CAtariTrackerDriver*` set only by
`StartRecording()`) with several pure state-machine methods that don't touch either.
Rather than a file-level split (the coupled methods are woven through the same file,
not cleanly separable into their own translation unit here), used **link-only
stubs** for exactly the symbols needed (`CAtariTrackerDriver::GetByteAt`/`Init`
returning constants, `CLZSSFile::GetFrameSize` returning a constant, and a real,
already-tested `g_ChannelControl` instance) so the whole file compiles and links,
while tests only ever call the methods that don't dereference the (deliberately
left null) tracker-driver pointer — same reasoning as never calling
`ThrowRuntimeException`'s paths or `CTuning::InitTuning()` without setup.

Two behaviors worth remembering, both confirmed by tracing the state machine by hand
before writing assertions:
- `IsRecording()` means "not stopped" (`m_recordState != STOP`), which is **true**
  for `RECORD`, `WRITE`, and `START` alike — not specifically "actively recording".
  First guess at this was wrong and the test failure caught it immediately.
- `TrackSongLine()` self-re-arms back to `RECORD` after detecting the first loop
  (via an internal `SwitchIntoRecording()` call), but its simpler sibling
  `CallFromPlayBeat()` does **not** — after one loop it's left in `WRITE`, and since
  both methods gate all their logic behind `m_recordState == RECORD`, a second loop
  via `CallFromPlayBeat()` alone is impossible without the caller manually calling
  `SetState(RECORD)` again in between. Also: `Clear()` resets counters/buffer but
  never touches `m_recordState`, so a stream that was mid-recording stays
  "recording" (per `IsRecording()`) after `Clear()`.

Verified via a full Release|x64 solution rebuild: `Rmt.exe` and `RmtTests.exe` both
build clean and all 116 tests pass.

## IMPORTANT CORRECTION (2026-09-21): `CSong`'s constructor is actually cheap — earlier
## deferral reasoning was based on an assumption I never verified

When `CSong` was first investigated, I said its constructor was blocked because it
calls `m_PokeyController = new CPokeyController(&g_Atari);`, and concluded from that
alone (without checking either class) that this "pulls in the full `CAtari`
subsystem." **That conclusion was wrong, and I should have verified it at the time
instead of inferring it from the call shape.** Having now actually opened both
files while investigating `Atari.cpp`:

- `CAtari`'s constructor is `CAtari() { ClearMemory(); }` — a single `memset` over a
  64KB buffer. Nothing else. `g_Atari` (the global instance) is completely cheap to
  construct.
- `CPokeyController`'s constructor is `CPokeyController(CAtari* atari) :
  m_atari(atari), m_channel_index(0), m_divisor(1.0) {}` — an initializer list and an
  empty body. Also completely cheap.

So `CSong::CSong()`'s only two statements beyond trivial member resets
(`memset(m_songname, ...)`, three `-1` assignments) are cheap, **provided a `g_Atari`
global (or stand-in) already exists for linking** — which is itself now known to be
cheap and safe to stub (see `AtariStub.cpp`/`AtariTests.cpp` below).

**This does not mean `CSong` is now fully testable** — the *constructor* being cheap
is necessary but nowhere near sufficient:
- `Song.cpp` is still 3224 lines in one translation unit, and compiling any of it
  (even just to reach the constructor) requires the linker to resolve every global
  every *other* method in the file touches (`g_AtariTrackerDriver`, `g_Instruments`,
  `g_TrackClipboard`, `g_Pokey`, `g_tracks4_8`, `MainFrm.h`/`EffectsDlg.h`-linked UI
  code, and more) — a much larger stubbing effort than any file done so far, not
  fundamentally different in kind from the `Tuning`/`Tracks`/`Instruments` splits,
  but bigger in scope. A clean split (constructor/destructor + whatever else turns
  out pure into a `SongCore.cpp`, mirroring the established pattern) is very likely
  possible but hasn't been attempted yet.
- Individual methods have their own independent hazards even once construction is
  solved. E.g. `CSong::Stop()` (called unconditionally by `CUndo::Undo()`/`Redo()`)
  guards its body with `if (GetPlayMode() != PLAY_STOP)`, but `m_play` (the backing
  field) has **no default member initializer** — the same uninitialized-member
  pattern fixed for `CTracks`/`CInstruments`/`CAtari` elsewhere, except here a
  garbage-triggered `true` branch calls `g_SongTimer.WaitForTimerRoutineProcessed()`,
  which sounds like it could **block waiting for a timer thread that isn't running in
  a test binary** — a hang risk, not just a crash risk. This alone should be fixed
  (add `= PLAY_STOP` or equivalent) before anyone relies on constructing a bare
  `CSong` for tests.

**Net effect**: `CSong`'s constructor is no longer a valid reason to defer it, but the
translation-unit size and per-method hazards like `Stop()`'s are real ones. Treat a
proper `CSong`/`Song.cpp` split as a legitimately-sized, deliberate task (like the
plan's own "cleanup" phase), not as blocked-forever. Downgrade "CSong deferred" from
"can't construct it" to "haven't yet done the (larger, but same-pattern) split work."

## Phase 2 continued (2026-09-21): `CanvasXY`, `C6502`, `Undo`, `Song_DumpSong`, `Atari`

User asked to continue with these five specifically:

- **`CanvasXY.cpp`** (`CCanvasXY`) — a GDI drawing wrapper (`CDC*`-based text/line/
  rect drawing). Pure UI rendering, nothing to assert on without an in-memory device
  context and pixel inspection; out of scope for characterization tests, same
  category as `RmtView`/dialogs. Skipped.
- **`Song_DumpSong.cpp`** — turned out to be a single function,
  `CSong::DumpSongToPokeyStream`, i.e. just another `CSong` method (not a separate
  class/file's worth of testable surface). Deeply entangled in the live playback
  loop (`Play()`, `PlayVBI()`, message pumping, `g_AtariTrackerDriver`). Confirms it's
  squarely inside the `CSong` split work above, not separately actionable. Skipped
  for now.
- **`C6502.cpp`** — confirmed a genuine, by-design hazard: `C6502::Init()` calls
  `LoadLibrary("sa_c6502.dll")` and shows a **real blocking `MessageBox`** if the DLL
  is missing. Not characterization-testable as-is (real DLL dependency, real UI
  popup); this is *why* `CAtari::Init()`/`DeInit()`/`JSR()` needed link-only stubs
  for `AtariTests.cpp` rather than linking the real `C6502.cpp`. No tests written for
  `C6502.cpp` itself.
- **`Undo.cpp`** (`CUndo`) — investigated in depth. Its constructor/destructor are
  cheap (just null out / free a 302-entry array), but essentially every operational
  method (`ChangeTrack`, `ChangeSong`, `ChangeInstrument`, `ChangeInfo`,
  `PerformEvent`, `Undo`, `Redo`) reads and writes real `g_Song`/`g_Tracks`/
  `g_Instruments` state, and `Undo()`/`Redo()` both call `CSong::Stop()` unconditionally
  — which hits the hang risk described above. **Deferred**, not because `CSong` can't
  be constructed (see correction above) but because testing `CUndo` meaningfully
  needs a real, properly-initialized `g_Song`/`g_Tracks`/`g_Instruments` trio and
  `Stop()`'s `m_play` hazard fixed first — squarely downstream of the `CSong` split
  work, not a quick win by itself.
- **`Atari.cpp`** (`CAtari`) — the actual win this batch. Added 7 tests (123 total,
  all passing) for the pure memory-buffer methods (`GetByteAt`/`SetByteAt`/
  `GetMemoryAt`/`GetConstMemoryAt`/`ClearMemory`, both `GetClockFrequency`/
  `GetFrameCycleCount` overloads). Found and fixed the same uninitialized-member
  pattern as `CTracks`/`CInstruments`: `m_ntsc` had no default initializer, so
  `IsNTSC()` (and everything derived from it) was indeterminate on any non-global
  instance. Fixed with `BOOL m_ntsc = FALSE;` (`Atari.h`) — safe for the existing
  global `g_Atari` (already got `FALSE` for free from static zero-init) and now safe
  for test-constructed instances too. `Init()`/`DeInit()`/`JSR()` (real C6502/DLL
  calls) and `Init(bool ntsc)` (calls `CTuning::InitTuning()`, hazardous per
  `TuningTests.cpp`) are all avoided; link-only stubs (`AtariStub.cpp`) provide
  `C6502::Init/DeInit/JSR` (never called) and a real, cheap `g_Tuning` instance
  (needed only because `Atari.cpp`'s translation unit references it, not because
  tests use it).

Verified via a full Release|x64 solution rebuild: `Rmt.exe` and `RmtTests.exe` both
build clean and all 123 tests pass.

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
- [x] Phase 2 continued: `lzss_sap.cpp`/`CCompressLzss` tests, 54 tests total,
      committed (`9475297`).
- [x] Phase 2 continued: `CTracks`/`IO_Tracks.cpp` tests + 3 real bug fixes, 69 tests
      total, committed (`38a4d6f`).
- [x] Phase 2 continued: `CInstruments`/`IO_Instruments.cpp` tests + 1 more
      `delete`/`delete[]` fix, split across 2 new files, 74 tests total, committed
      (`e85f0f7`).
- [x] Phase 2 continued: `CSong` investigated, `CSAPFile` tested instead, 78 tests
      total, committed (`3a6e1b9`). **(Note: the "CSong deferred" reasoning here was
      later found incomplete — see the correction above.)**
- [x] Phase 2 continued: `Keyboard2NoteMapping` + `CASMFileBuilder` tests (complete,
      including `BuildSongData`), 93 tests total, committed (`1c3e20b`, `3d84de4`).
- [x] Phase 2 continued: fresh `Global.h`-free survey → `ChannelControl` +
      `RmtCommandLineInfo` tests, 107 tests total, committed (`f28854c`).
- [x] Phase 2 continued: `Global.h`-*having* survey → `CPokeyStream` tests, 116 tests
      total, committed (`c0a0b0e`).
- [x] Phase 2 continued: `CanvasXY`/`C6502`/`Undo`/`Song_DumpSong` investigated
      (skipped/deferred, see above) + `CAtari` tests + 1 more uninitialized-member
      fix, 123 tests total. Not yet committed.
- [ ] Ask user whether to commit this step.
- [ ] Phase 2 continued: more characterization tests before any cleanup.
      - **Reconsider a deliberate `Song.cpp`/`IO_Song.cpp` split** as the next
        significant piece of work (not a quick win, but no longer blocked on "can't
        construct `CSong`" — see the correction above). Before starting: (a) fix
        `m_play`'s missing default initializer the same way `m_ntsc` was just fixed,
        (b) identify which of `Song.cpp`'s ~3224 lines' worth of methods are pure vs.
        which globals each coupled method needs, similar to the `Tuning`/`Tracks`/
        `Instruments` triage, before deciding how many files the split needs to land
        in. This would unblock `LZSSFile`, `SongExport`, `SongContainer`,
        `SAPFileExporter`, `PokeyStream::StartRecording()`, `CUndo`, and `CSong`'s own
        `SongToAta`/`AtaToSong`, all currently blocked on it either directly or via a
        stubbed-around dependency.
      - Remaining unopened files from the `Global.h`-having survey:
        `AtariTrackerDriver.cpp` (worth checking now that `CAtari` turned out cheap —
        may have a similarly-mistaken "looks heavy" assumption worth re-verifying).
      - `ASMFile.cpp` is only 2 lines (essentially empty) — confirm there's nothing
        there before spending time on it.
      - General lesson from this batch: **when a class is deferred because it "needs"
        another class, actually open and read that other class's constructor before
        concluding it's heavy** — the assumption, not just the conclusion, needs
        verification. This cost nothing to fix here since no code was based on the
        wrong assumption, but it's worth being more careful about going forward.
      - **`CSong` stays deferred** until there's appetite for a real constructor
        decoupling pass (or a deliberate decision to add a riskier test-only seam
        there). Don't retry it opportunistically.
      - `RuntimeException`-style hazards may recur elsewhere — run
        `grep -rn "ThrowRuntimeException"` across the codebase to find every call site
        before assuming a given file's error paths are safe to test.
      - Before starting a new module, always check whether it `#include`s `Global.h`
        (or another huge header) and, if so, whether the globally-coupled methods can
        be split out the same way as `Tuning`/`Tracks`/`Instruments` — check this
        early, since it changes the scope of the work. It's fine for the split to
        land across more than one file if the coupling itself is spread across more
        than one file (as with `Instruments.cpp` + `IO_Instruments.cpp`). But if the
        coupling is baked into the **constructor** itself (as with `CSong`), that's a
        signal to defer rather than force a split, unlike coupling confined to a few
        methods. Some files (`ASMFileBuilder.cpp`, `Keyboard2NoteMapping.cpp`) need no
        split at all — check coupling before assuming a split is needed.
      - Also worth a quick read-through for the same class of bugs found so far
        (uninitialized members with no default initializer, `delete` vs `delete[]`
        mismatches on array allocations, wrong/self-referential includes, functions
        with production side effects that make them unsafe to call in tests like
        `MessageBox`+`exit()` or the `ThrowRuntimeException` macro, or fragile
        "processes a fixed-size range regardless of what was actually passed in"
        contracts like `BuildTracksData`'s) — cheap to spot while reading for coupling
        anyway. Real, safe fixes (uninitialized members, `delete`/`delete[]`,
        includes) get fixed immediately; hazards needing a real redesign or a
        deliberate decision get characterized and avoided in tests, not silently
        patched.
      - When a method needed for testing is `private` but pure (no dependency on
        other private state) and there's no other way to exercise it (no decoder/
        inverse operation, no way to observe its effect indirectly), the established
        pattern here is: make it `public` with a one-line comment explaining why. Pure
        visibility changes, no behavior change. Similarly, a parameter type narrower
        than necessary (like `SAPFile::Export`'s old `std::ofstream&`) can usually be
        safely widened to the actual interface used (`std::ostream&`) if every real
        call site already satisfies the wider type.
      - When a test needs a real, cross-checkable "seam" value that a coupled method
        would otherwise supply from a global (like `CInstruments` tests flipping
        `g_tracks4_8` between mono/stereo, or `Keyboard2NoteMapping` tests flipping
        `g_keyboard_layout`), prefer a small stub `.cpp` that defines just that global
        (or an empty-body override for a call-graph-only dependency like
        `ClearInstrument()`/`CSong::GetName()`) over pulling in the whole subsystem
        that would normally set it.
      - When writing a standalone test `.cpp` for a header that (like
        `ASMFileBuilder.h`) relies on `StdAfx.h` having already been included by
        convention rather than including its own dependencies, add
        `#include "StdAfx.h"` in the test file before including that header.
      - Everything touching `g_Song`/other globals, MFC dialogs, and the timer-driven
        sound generation is expected to need actual decoupling work (the "cleanup"
        half of Phase 2) before it's testable at all — do not attempt to test that
        code as-is; redesign first, matching the plan's own warning about UI/model/
        timer mixing.
- [x] Phase 2 continued: `Song.cpp`/`IO_Song.cpp` scoped and split (first slice), 138
      tests total. Not yet committed.
      - Scoping pass: grepped every method in `Song.cpp` (103 methods) and
        `IO_Song.cpp` (20 methods) for global references, sorting into four tiers —
        Tier 1 (zero references, pure), Tier 2 (editing operations touching a few
        globals), Tier 3 (heavy format/export logic), Tier 4 (live playback methods
        deep in the timer loop). Only Tier 1 was implemented this batch; Tiers 2–4
        remain for a future batch.
      - Added default member initializers to ~25 `CSong` private members in
        `Song.h` that previously had none — the same uninitialized-member class of
        bug found in `CTracks`/`CInstruments`/`CAtari` earlier, but far more
        extensive here given `CSong`'s size. Safe for the existing global
        (`g_Song` already gets the same values for free via static
        zero-initialization) and necessary for any locally-constructed `CSong`
        (used by the new tests). Notably includes `m_play` (fixes the `Stop()` hang
        risk flagged earlier — its default is now `PLAY_STOP`, matching what
        `g_Song` already had) and several array-index members that were an
        out-of-bounds-read risk uninitialized.
      - Created `src/cpp/SongCore.cpp` with the constructor/destructor plus ~20
        zero-global-reference methods moved out of `Song.cpp` (`GetName`,
        `GetTracks`, `IsStereo`, `IsNTSC`, `GetInstrumentSpeed`,
        `PlayPressedTonesInit`, `SetPlayPressedTonesSilence`,
        `GetActiveInstr/Column/Line`, `GetPlayLine`, `SetActiveLine`, `SetPlayLine`,
        `UECursorIsEqual`, `SongGetGo` (both overloads), `SongTrackGoDec/Inc`,
        `FindNearTrackBySongLineAndColumn`, `SongPlayNextLine`) plus
        `SongToAta`/`AtaToSong` moved out of `IO_Song.cpp` (same zero-global-
        reference criterion). Pure mechanical moves, no behavior change — verified
        by a full production `Rmt.exe` rebuild (0 errors) right after the move,
        before any test code was added.
      - `PokeyController.cpp` investigated as part of wiring `CSong`'s constructor
        into the test binary (it does `new CPokeyController(&g_Atari)`): 0 global
        references, trivial constructor, linked directly into `RmtTests.vcxproj`
        with no stub needed.
      - Added a real, default-constructed `CAtari g_Atari;` to
        `test/AtariStub.cpp` (cheap, per the earlier `CAtari`-constructor
        correction) since `SongCore.cpp`'s `CSong` constructor needs its address.
      - Removed 4 now-redundant link-only `CSong` method stubs from
        `test/SAPFileStub.cpp` (`GetName`/`IsStereo`/`IsNTSC`/`GetInstrumentSpeed`)
        — they existed only because `SAPFile.cpp` needed those symbols to link
        before `SongCore.cpp` provided the real definitions; keeping both caused
        `LNK2005` multiply-defined-symbol errors once `SongCore.cpp` was linked
        into the same test binary.
      - Wrote `test/SongTests.cpp` (15 tests) covering every moved method,
        including a hand-derived `SongToAta`/`AtaToSong` round trip for both track
        data and goto-line encoding (`AtaToSong`'s `len` parameter represents how
        much of the module's song section is being decoded, not `SongToAta`'s own
        smaller "bytes actually used" return value — the round-trip test decodes a
        wider byte range than `SongToAta` reported using, to keep the goto target
        in bounds). All values verified correct on first run — no hand-derivation
        errors found this time.
      - Full solution rebuild (Release|x64) confirmed 0 errors for both `Rmt.exe`
        and `RmtTests.exe`; 138 tests pass (up from 123, +15, 0 regressions).
      - **`Song.cpp`/`IO_Song.cpp` Tiers 2–4 remain for a future batch** — editing
        operations, format/export logic, and live playback methods respectively,
        all still coupled to `g_Song`/other globals and not yet split out.
- [x] Phase 2 continued: `Song.cpp`/`IO_Song.cpp`'s "editing operations" batch
      (formerly Tier 2) done, plus a `Clipboard.cpp` split, 184 tests total. Not
      yet committed.
      - Fresh scoping pass found that **every remaining method in `Song.cpp`/
        `IO_Song.cpp` is coupled**, even ones with zero *direct* global
        references (e.g. `SongJump`, `TrackCut`) - they call other coupled
        methods internally. A naive "zero direct refs" grep is not enough;
        transitive call-graph analysis is needed (built a small Python script
        for this, not checked in - see scratchpad).
      - However, **`CUndo`'s constructor is also cheap** (just nulls an array)
        - another instance of the "verify before deferring" lesson from
        `CAtari`/`CPokeyController`. Combined with already-cheap `CTracks`/
        `CInstruments`/`CTrackClipboard`, this unlocked a "safe cluster": 46
        methods that only transitively touch `g_tracks4_8`/`g_Undo`/`g_Tracks`/
        `g_Instruments`/`g_TrackClipboard`, all confirmed cheap.
      - Extracted those 46 into a new `SongEditing.cpp` (mechanical move,
        pure/behavior-preserving, verified via a production `Rmt.exe` rebuild
        with 0 errors before any test code was added). Two candidates
        (`SaveRMW`/`SaveTxt`) were deliberately excluded despite passing the
        automated global-reference check: they call `CString::LoadString()`,
        an MFC resource-string load unreliable in a console test binary -
        left for the IO_Song.cpp format/export batch instead.
      - **`Clipboard.cpp` turned out to be almost entirely extractable too**:
        18 of its 19 methods (everything except `BlockEffect()`, which
        instantiates a real `CEffectsDlg` MFC dialog) only touch `g_Tracks`/
        `g_Song` and the trivial `ClearStatusBar()`/`SetStatusBarText()`
        helpers - split into a new `ClipboardCore.cpp`, leaving `Clipboard.cpp`
        with just `BlockEffect()`. Also verified via a clean production
        rebuild before any tests were added.
      - **Real, pre-existing design smell found (not fixed, characterized
        instead)**: `CTrackClipboard`'s `BlockSetBegin()`/`BlockPasteToTrack()`
        internally read the *global* `g_Song`, not necessarily the `CSong`
        instance a caller's `BLOCKSETBEGIN()`/`BlockPaste()` was invoked on.
        This means block-selection behavior is coupled to the global song
        regardless of which `CSong` object hosts the call. Tests exercising
        these specific methods use `g_Song` as the instance under test to
        match real usage, rather than trying to fix or route around this.
      - **Real gap found in the existing test harness (fixed)**: two more
        "looks safe by direct-global-check" surprises, both from calling a
        method whose *own* body needs a global outside the checked set:
        `CInstruments::MemorizeOctaveAndVolume()`/`RememberOctaveAndVolume()`
        (called by `ActiveInstrSet`) need `g_keyboard_RememberOctavesAndVolumes`
        and live in the still-coupled `Instruments.cpp` (not linked into the
        test project at all - would have been a linker error, not a silent
        bug); `CInstruments::Update()` (called by `RenumberAllInstruments`)
        lives in the still-`Global.h`-coupled `IO_Instruments.cpp`. Both
        stubbed as no-ops in `InstrumentsStub.cpp`, alongside the pre-existing
        `ClearInstrument()` stub. Lesson: a method call on an already-"safe"
        global object doesn't guarantee the *called* method itself is
        safe/linkable - always check the callee's own body and translation
        unit too, not just which object it's called on.
      - **Real test-isolation bug found and fixed (in the new test file, not
        production code)**: since `CInstruments::ClearInstrument()` is a
        no-op stub, `CInstruments::InitInstruments()` no longer actually
        resets instrument data between tests in this binary, letting one
        test's `TInstrument` field mutations leak into the next and produce
        wrong `RenumberAllInstruments` results. Fixed by having the test
        fixture directly `memset()` every instrument to zero in `SetUp()`
        rather than relying on `InitInstruments()`.
      - Added `test/UndoStub.cpp` (real bodies for `CUndo`'s pure bookkeeping
        methods - constructor/destructor/`Init`/`Clear`/`DeleteEvent`/
        `GetUndoSteps`/`GetRedoSteps`/`DropLast`/`Separator`/`PosIsEqual` -
        plus no-op stubs for `ChangeTrack`/`ChangeSong`/`ChangeInstrument`/
        `ChangeInfo`/`Undo`/`Redo`/`PerformEvent`/`InsertEvent`, which record/
        replay real edits against `g_Tracks`/`g_Instruments`/`g_Song`/
        `g_hwnd` - not needed since the editing methods under test only care
        about the edit's own visible effect, not the undo recording).
      - Added `test/SongEditingStub.cpp` (real `g_Tracks`/`g_Instruments`/
        `g_TrackClipboard`/`g_Song` globals, plus no-op `ClearStatusBar()`/
        `SetStatusBarText()` stubs).
      - Wrote `test/SongEditingTests.cpp` (46 tests, one per moved method,
        two derivation errors caught and fixed on first run: `SongTrackDec`'s
        wraparound only triggers below -1, not at 0; `BlockPaste()` reads
        from `CTrackClipboard::m_track` - populated by `BlockCopyToClipboard()`
        - not `m_trackcopy`, which is `TrackCopy()`'s separate, unrelated
        clipboard field).
      - Full solution rebuild (Release|x64) confirmed 0 errors for both
        `Rmt.exe` and `RmtTests.exe`; 184 tests pass (up from 138, +46, 0
        regressions).
      - **Remaining in `Song.cpp`/`IO_Song.cpp`**: format/export logic
        (`MakeModule`/`DecodeModule`/`MakeTuningBlock`/`DecodeTuningBlock`/
        `SaveRMW`/`SaveTxt`/`LoadRMW`/`LoadTxt`/`LoadRMT`/the `FileXxx`
        methods/etc.) and live playback methods (`Play`/`Stop`/`PlayBeat`/
        `PlayVBI`/`TimerRoutine`/etc.), plus `InstrChange`/`InstrInfo`/
        `TrackInfo`/`TracksOrderChange`/`Songswitch4_8` (heavier editing
        methods needing `g_hwnd`/`g_AtariTrackerDriver`/other hazards) - none
        of these were in the "safe cluster" and still need their own,
        separate triage/decoupling work.
- [x] Wrote a dedicated triage plan for the above (`plans/SONG_IO_SONG_REMAINING_PLAN.md`,
      committed `7276946`/`f7d72c6`) before implementing further, given the
      remaining work needs per-method judgment calls (not a single mechanical
      pass). Key findings recorded there: every remaining `g_hwnd` use in
      these two files is a `MessageBox` call (no real GDI/dialogs), but they
      split into guard-only (avoidable), always-fires-on-success (blocking),
      and confirmation-prompt (blocking + branches) categories; `InstrInfo`
      already has an untapped pure-mode output parameter; `InstrChange`
      instantiates a real MFC dialog; `CSongTimer::SetTimer()` confirmed to
      start a real OS multimedia timer thread. Also recorded 4 resolved
      decisions: `IDS_RMT_VERSION` becomes a compile-time constant (all 6
      call sites, not just the 2 needed for testing) instead of working
      around `LoadString`; `TrackInfo` gets a small refactor splitting its
      pure string-building from the `MessageBox` display, mirroring
      `InstrInfo`; the two confirmation-prompt methods stay deferred; batch
      pacing continues one at a time with explicit approval.
- [x] Phase 2 continued: plan's "Batch 1" (`TracksAllBuildLoops`/
      `TracksAllExpandLoops`), 186 tests total. Not yet committed.
      - **Correction to the plan**: these two were originally flagged as an
        unconditionally-free `g_Tracks`-only addition based on a *direct*-
        reference-only grep - missed that both unconditionally call `Stop()`
        first (the same class of oversight flagged earlier: verify the call
        graph, not just direct references). `Stop()` is a no-op unless
        `GetPlayMode() != PLAY_STOP`, and since `m_play` defaults to
        `PLAY_STOP` and no test calls `Play()` first, both are safe to test
        - just under a documented precondition, not unconditionally free.
      - Moved both into `SongEditing.cpp`. `Stop()` itself stays in
        `Song.cpp` (still coupled to the real `g_SongTimer` hazard) and is
        given a link-only stub (`test/SongEditingStub.cpp`) - behaviorally
        identical to the real `Stop()` for the `PLAY_STOP` state these tests
        are always in.
      - **Real test-isolation bug found and fixed (in the test file, not
        production code)**: `CTracks::InitTracks()` doesn't reset
        `m_maxTrackLength` (only clears track data), so the earlier
        `ChangeMaxtracklenShortensLongerTracksAndUpdatesTheGlobalLength` test
        leaked its `SetMaxTrackLength(32)` change into every later test in
        the suite - caught because the 2 new tests failed when the *full*
        suite ran, despite passing in isolation. Fixed by having the fixture
        call `g_Tracks.SetMaxTrackLength(64)` before `InitTracks()` in
        `SetUp()`. Reinforces the standing practice of always running the
        full suite, not just newly-added/filtered tests, before considering
        a batch verified.
      - 2 new hand-derived tests (a period-1 and a period-2 loop pattern),
        both correct on first run. Full solution rebuild (Release|x64)
        confirmed 0 errors; 186 tests pass (up from 184, +2, 0 regressions).
- [x] Phase 2 continued: plan's "Batch 2" (`ResetTuningVariables`, `SetTracks`,
      `SetNTSC`, `MakeModule`, `DecodeModule`, `InstrInfo`), 191 tests total.
      Not yet committed.
      - **Two more corrections found while implementing, before touching any
        code**: (1) `MakeTuningBlock`/`DecodeTuningBlock` are dead code -
        entirely inside a `/* TODO: Unused ... */` block comment spanning
        from just before `MakeTuningBlock`'s signature to just after
        `DecodeTuningBlock`'s closing brace, with both declarations also
        commented out in `Song.h`. The earlier plan's direct-globals grep
        matched text *inside the comment* as if it were real code - removed
        from the batch entirely (not "deferred", they don't compile). (2)
        `DecodeModule` calls `SetTracks()`, missed by the direct-globals
        check (a call to another `CSong` method, not a direct global
        reference - the same class of oversight as Batch 1's `Stop()`
        correction). `SetTracks()` conditionally calls `ReInitSound()` (real
        `g_AtariTrackerDriver`/`g_Pokey` hazard) only when the track count
        actually changes - given the same link-only no-op stub treatment as
        `Stop()`, which makes `SetTracks()` itself genuinely safe-cluster
        material (only needs `g_tracks4_8` directly).
      - **`SetNTSC()` folded in too**: identical shape to `SetTracks()`
        (conditionally calls the now-stubbed `ReInitSound()`), and directly
        useful for testing `ResetTuningVariables()`'s NTSC/PAL branching.
      - Added real `TTuningSettings g_tuning;`/`TTuningRatios g_tuningRatios;`
        globals (both already globals-free per `TuningTypesTests.cpp`) and a
        real `HWND g_hwnd = NULL;` (only ever passed to a `MessageBox()` call
        on guard branches these tests never reach) to
        `test/SongEditingStub.cpp`.
      - **Real test-input bug found and fixed (in the test file, not
        production code)**: the first `MakeModule`/`DecodeModule` round-trip
        attempt failed with `DecodeModule` returning 0 - `m_mainSpeed`/
        `m_instrumentSpeed` default to 0 on a fresh `CSong`, and
        `DecodeModule` rejects a decoded speed byte of 0 as invalid ("there
        can be no zero speed"). Fixed by setting both to valid values via
        `SetSongInfoPars()` before encoding, the same pattern already used
        in `SongCoreTest.GetInstrumentSpeedReflectsSongInfoPars`.
      - `MakeModule`/`DecodeModule` tested via a round trip (mirroring
        `SongToAta`/`AtaToSong`'s approach) rather than hand-deriving the RMT
        header's byte layout - lets the real encode/decode logic prove
        itself internally consistent. All other new tests hand-derived and
        correct on first run.
      - Full solution rebuild (Release|x64) confirmed 0 errors; 191 tests
        pass (up from 186, +5, 0 regressions) - full suite run (not just
        filtered) confirmed no cross-test isolation issues this time.
- [x] Phase 2 continued: plan's "Batch 3" (`SaveRMW`, `SaveTxt`, `LoadRMT`),
      194 tests total. Not yet committed.
      - **Three more corrections found while scoping the implementation**:
        (1) `LoadRMW`/`LoadTxt` both unconditionally call `ClearSong(8)` -
        reading `ClearSong`'s body confirmed it's genuinely the large,
        separate decision already flagged in the plan (real
        `AfxGetMainWnd()` MFC-framework call, `g_AtariTrackerDriver->Init()`
        on a pointer never instantiated in the test project, on top of the
        ~18 globals already known about) - both dropped from this batch,
        stay blocked on `ClearSong`'s own future decision. (2) `ExportV2` is
        a large dispatcher (`CRmtExporter`/`CASMFileExporter`/several
        `CSongExporter` format methods), not a simple encode - dropped,
        needs its own separate triage. (3) `LoadRMT` doesn't call
        `ClearSong` and turned out fully testable via a hand-built two-block
        binary file (mirroring `MakeModule`/`DecodeModule`'s round-trip
        approach) - its 3 `MessageBox` calls are 2 guard-only errors plus
        one "stripped RMT" info dialog, all avoidable with valid, complete
        test input.
      - **Implemented the `IDS_RMT_VERSION` → compile-time-constant decision**
        from the previous session: added `RmtVersion.h`
        (`RMT_VERSION_STRING`, with a comment noting it mirrors `Rmt.rc`'s
        string resource) and replaced all 6 `LoadString(IDS_RMT_VERSION)`
        call sites (`Rmt.cpp`, `RmtView.cpp` ×2, plus `SaveRMW`/`LoadRMW` in
        the moved code), not just the ones needed for testing.
      - **`IO_Instruments.cpp` needed no split at all** (matching the
        "some files need no split - check coupling first" pattern): its 5
        methods have exactly one real global reference (`g_Atari`, already
        safe) - the `#include "Global.h"`/`"resource.h"` were dead includes,
        confirmed removable via a production rebuild. `CInstruments::Update()`
        lost its Batch-2 no-op stub and now has real behavior in tests.
      - **Found two pure compile-time data tables misplaced in the coupled
        `Instruments.cpp`**: `shpar[]`/`shenv[]` (parameter/envelope
        descriptor tables) only reference `InstrumentGUIPosition`'s
        `constexpr` layout constants - moved to the already-linked
        `InstrumentsAtaFormat.cpp`, unblocking `IO_Instruments.cpp`'s link.
      - **Widened `std::ofstream&`/`std::ifstream&` to `std::ostream&`/
        `std::istream&`** across a small cascade of methods actually called
        by `SaveRMW`/`SaveTxt`/`LoadRMT` - `CSong` itself, `CInstruments::
        SaveAll/LoadAll/SaveInstrument/LoadInstrument`, `CTracks::
        SaveAll/LoadAll/SaveTrack/LoadTrack`, `IOHelpers.cpp`'s
        `NextSegment`, and `CAtariIO::LoadBinaryBlock/LoadWord` - same
        precedent as `SAPFile::Export`'s earlier widening: every real call
        site already passes a genuine file stream, and the wider type lets
        tests use in-memory streams instead of real temp files.
      - Added a `WriteBinaryBlock()` test helper replicating
        `CAtariIO::LoadBinaryBlock()`'s expected byte format, used to build
        a valid two-block RMT file in memory for `LoadRMT`'s test. One
        hand-derivation error caught and fixed on first run: the raw
        instrument-name field isn't trimmed the way `CSong::GetName()` is,
        so the loaded name comes back space-padded to
        `INSTRUMENT_NAME_MAX_LEN`.
      - Full solution rebuild (Release|x64) confirmed 0 errors; 194 tests
        pass (up from 191, +3, 0 regressions).
      - **Remaining in `Song.cpp`/`IO_Song.cpp`**: `ClearSong` and `ExportV2`
        (each its own dedicated future decision, see
        `plans/SONG_IO_SONG_REMAINING_PLAN.md`), `LoadRMW`/`LoadTxt` (blocked
        on `ClearSong`), the `FileXxx` family (recommended to stay deferred
        indefinitely - real dialog orchestration, no independent test value
        beyond what's already covered), and the heavier editing/playback
        methods from the original Batch 4/6 lists.
- [x] Phase 2 continued: plan's "Batch 4" (`SongJump`, `SongUp`, `SongDown`,
      `SongSubsongPrev`, `SongSubsongNext`, `TrackUp`, `TrackDown`,
      `SetUECursor`, `SongPrepareNewLine`, `SongPutnewemptyunusedtrack`,
      `PlayPressedTones`, `InstrPaste`), 208 tests total. Not yet committed.
      - **Two more real-dialog corrections found while scoping, same class as
        `InstrChange`**: `SongInsertCopyOrCloneOfSongLines` instantiates a
        real `CInsertCopyOrCloneOfSongLinesDlg` and calls `.DoModal()` right
        at the top - not just a guard-only `MessageBox` as the plan assumed.
        `TracksOrderChange` does the same with `CSongTracksOrderDlg`. Both
        dropped from this batch, joining `InstrChange`/`BlockEffect` as
        "real dialog" hazards needing a deliberate decision, not
        characterizable as-is. `Songswitch4_8`/`SongMaketracksduplicate`
        confirmed to only have confirmation-prompt `MessageBox`es (matching
        the earlier "defer both" decision) - no hidden dialogs there.
      - **`PlayPressedTones`/`InstrPaste` initially looked hazard-only**
        (both call `g_AtariTrackerDriver` unconditionally, not as a guard),
        but reading `CAtariTrackerDriver`'s actual methods found
        `CAtari::JSR()` just delegates to `C6502::JSR()` - already a
        link-only no-op stub in the test project (see `AtariStub.cpp`,
        established back when `C6502::Init()`'s real DLL-loading hazard was
        first characterized). `CAtariTrackerDriver`'s constructor is also
        just a pointer store. This unlocked both methods - another instance
        of "verify before deferring."
      - **Split `AtariTrackerDriver.cpp`** the same way as `Song.cpp`/
        `Instruments.cpp` etc.: `SetTrackNoteInstrumentVolume`/
        `SetTrackVolume`/`InstrumentTurnOff`/`GetAtari`/the constructor/
        `GetByteAt` (only need `g_rmtinstr` and the now-safe `CAtari::JSR()`)
        moved to a new `AtariTrackerDriverCore.cpp`; `LoadRMTRoutines`/
        `Init`/`Play`/`SetPokey`/`Silence` stay behind (real driver-binary
        loading, `Global.h`'s `IsSpecialProveMode()`). Removed a now-
        redundant `GetByteAt()` link-only stub from `test/PokeyStreamStub.cpp`
        (predates this session's `CAtariTrackerDriver` investigation) in
        favor of the real implementation.
      - Added a link-only stub for `CSong::Play()` (needed because
        `SongUp`/`SongDown`/`SongSubsongPrev`/`SongSubsongNext` reference it
        inside their never-taken `if (m_play && m_followplay)` branches -
        same treatment as `Stop()`/`ReInitSound()`).
      - 14 new hand-derived tests, all correct on first run. Full solution
        rebuild (Release|x64) confirmed 0 errors; 208 tests pass (up from
        194, +14, 0 regressions).
      - **Remaining "own category" real-dialog hazards**: `InstrChange`,
        `SongInsertCopyOrCloneOfSongLines`, `TracksOrderChange`,
        `BlockEffect` (all instantiate real MFC dialogs) - need a deliberate
        decision (matching `TrackInfo`'s already-decided small-refactor
        treatment, or staying deferred) before any further characterization.
- [x] Phase 2 continued: plan's "Batch 6" (`Play`, `Stop`, `PlayBeat`,
      `PlayVBI`), 212 tests total. Not yet committed. **This batch carried
      real hang risk** (a genuine OS timer thread) if the safety reasoning
      below turned out wrong, so every step was verified with an explicit
      timeout before proceeding to the next.
      - **The plan's own "recommend defer this entire batch" turned out too
        conservative** - re-verifying `CSongTimer`'s actual implementation
        (not just trusting the earlier hazard note) found
        `WaitForTimerRoutineProcessed()`/`StopTimer()`/`KillTimer()` are all
        guarded by `if (m_timerRoutine)`, and the *only* method that ever
        sets `m_timerRoutine` away from its default `0` is `SetTimer()`
        (which calls the real, hazardous `timeSetEvent`). As long as nothing
        ever calls `SetTimer()`/`CSong::ChangeTimer()`, every other
        `CSongTimer` method is a provably safe no-op - confirmed by reading
        `SongTimer.cpp` line by line, not assumed.
      - Similarly, `g_Atari.Init()` (called by `Play()`'s `PLAY_SONG` case)
        delegates to the already-stubbed no-op `C6502::Init()` - the same
        delegation pattern found for `g_AtariTrackerDriver` in Batch 4.
      - `TimerRoutine()` stays genuinely deferred: besides calling the
        hazardous `ChangeTimer()`, it also calls `g_Pokey.RenderSound1_50()`,
        and `CXPokey` (`PokeyRenderer.h`) holds a real
        `LPDIRECTSOUNDBUFFER` - a real audio-hardware coupling, a
        categorically different (and not yet investigated) hazard.
        `ReInitSound()`/`ChangeTimer()` stay deferred/stubbed for the same
        `g_SongTimer.SetTimer()`/`g_Pokey ` reasons.
      - Added the same missing-default-member-initializer fix to
        `CSongTimer` (`m_song`/`m_timerRoutine`/`busyInCallback`/
        `m_timerRoutineProcessed`) as done earlier for `CTracks`/
        `CInstruments`/`CAtari`/`CSong` - safe, zero production-behavior
        change, and it's *why* a real `CSongTimer g_SongTimer;` global is
        safe to add to the test project at all (matches what the existing
        global already got for free from static zero-initialization).
      - Discovered `TracksEdit.cpp` (already split from `Tracks.cpp` in a
        prior session, per its own header comment) wasn't linked into the
        test project yet - needed by `PlayVBI`'s quantization branch
        (`CTracks::SetNoteInstrVol`/`SetVol`). Its only dependencies
        (`g_Undo`, `g_respectvolume`) were already safe/real.
      - Linking the *whole* `SongTimer.cpp` doesn't work for the test binary
        (needs `winmm.lib` for `timeSetEvent`/`timeKillEvent`, and
        `CSong::TimerRoutine()`, deliberately unlinked) - instead added a
        link-only stub for just `WaitForTimerRoutineProcessed()` that
        preserves the real guard (`if (m_timerRoutine)`) without the
        unreachable busy-wait body, rather than stubbing it as an
        unconditional no-op.
      - Verified safety incrementally rather than all at once: built and ran
        the existing suite (no new tests yet) with a timeout after wiring
        `g_SongTimer` in; added and ran just the `Stop()` test alone with a
        timeout; then added `Play`/`PlayBeat`/`PlayVBI` and re-verified with
        a timeout before running the full suite. No hangs at any step; all
        new tests hand-derived correctly on first run.
      - Full solution rebuild (Release|x64) confirmed 0 errors; 212 tests
        pass (up from 208, +4, 0 regressions).
- [x] Phase 2 continued: `CSong::TrackInfo` refactored to the same dual-mode
      (output-parameter vs. `MessageBox`) design `InstrInfo` already has,
      per the "Decisions (resolved)" call made earlier for this method.
      Went through system-enforced Plan Mode given the header/struct-
      placement design involved.
      - Added `struct TTrackInfo { int count; int lines; int
        usedincolumn[SONGTRACKS]; };` to `SongTypes.h`, right after
        `TBookmark`. It doesn't live in `TrackTypes.h` (which would be the
        more obvious home) because `TrackTypes.h` has no includes and
        doesn't define `SONGTRACKS`, while `SongTypes.h` already includes
        `TrackTypes.h` and already owns `SONGTRACKS`/`SONGLEN` - putting the
        struct in `TrackTypes.h` would need it to include `SongTypes.h`
        back, a circular include. No new `#include` was needed anywhere
        else: `Song.h` already includes `SongTypes.h`, and both
        `SongEditing.cpp` and `test/SongEditingTests.cpp` already include
        `Song.h`.
      - `Song.h`'s declaration became `void TrackInfo(int track, TTrackInfo*
        tinfo = NULL);`, matching `InstrInfo`'s style exactly. The one real
        call site (`RmtView.cpp`) needed no change thanks to the default
        argument.
      - Moved `TrackInfo`'s body from `Song.cpp` to `SongEditing.cpp`
        (replaced with the same one-line "implemented in SongEditing.cpp"
        comment already used for `InstrInfo`/`Play`), split into the same
        `if (tinfo) {...} else {...}` shape `InstrInfo` uses - the
        stats-gathering loop itself is untouched, byte for byte, from the
        original. Placed right after `InstrInfo` in `SongEditing.cpp` to
        keep the two "Info" methods adjacent.
      - 2 new hand-derived tests (one exercising the populated-struct path,
        one a guard test for out-of-range tracks - included even though
        `InstrInfo` itself has no analogous guard test, since it's cheap and
        mirrors this file's existing guard-test style), both correct on
        first run. Full solution rebuild (Release|x64) confirmed 0 errors;
        214 tests pass (up from 212, +2, 0 regressions).
- [x] `ClearSong` - previously its own deferred category (~18 globals plus
      a real `AfxGetMainWnd()`/`CMainFrame` call and `g_AtariTrackerDriver`
      assumed uninstantiated). Re-analyzed in detail before touching any
      code, since later batches (4/6) had already resolved most of what
      made it look hazardous:
      - `g_AtariTrackerDriver` is now a real instantiated object
        (`test/SongEditingStub.cpp`, added for Batch 4/6), and both
        `g_Atari.Init()` and `g_AtariTrackerDriver->Init()` delegate to
        already-established-safe no-op stubs - the "pointer never
        instantiated" note that originally blocked this method was stale.
      - `g_Tracks.InitTracks()`, `g_Instruments.InitInstruments()`,
        `g_Undo.Init()`, `g_TrackClipboard.Clear()` are all real in the
        test project already and only touch their own class's state
        (confirmed by reading each body, not assumed) - cheap and safe.
      - The **only** genuine remaining hazard, once everything else was
        re-verified: `CMainFrame* mf = (CMainFrame*)AfxGetMainWnd(); if
        (mf) mf->m_comboSkipLinesAfterNoteInsert.SetCurSel(...)` - a real
        MFC application-framework call, categorically different from a
        stubbable global.
      - Extracted just those two lines into a new
        `CSong::SyncSkipLinesAfterNoteInsertComboBox()`, which stays behind
        in `Song.cpp` (link-only no-op stub in tests, same treatment as
        `ReInitSound()`). Moved the rest of `ClearSong()`'s body, now
        provably safe, into `SongEditing.cpp` verbatim - zero production
        behavior change, since `ClearSong()`'s own signature and single
        call-site pattern (`IO_Song.cpp`) are untouched.
      - Added ~7 trivial new global stubs to `test/SongEditingStub.cpp`
        (`g_rmtroutine`, `g_rmtstripped_sfx`/`_gvf`, `g_rmtmsxtext`,
        `g_PrefixForAllAsmLabels`, `g_changes`, `g_SkipLinesAfterNoteInsert`)
        - all plain `BOOL`/`int`/`CString` flags with no coupling of their
        own, same treatment as `g_playtime`/`g_activepart` in Batch 6 - plus
        a one-line real `SetEditMode()` implementation (copied verbatim
        from `Global.cpp`, which itself stays unlinked).
      - Removed two now-dead `extern` declarations (`g_TrackClipboard`,
        `g_PrefixForAllAsmLabels`) from the top of `Song.cpp`, left over
        from before `ClearSong()` was its only remaining user there -
        directly reduces `Song.cpp`'s coupling, which was the point of this
        analysis.
      - Verified incrementally given the number of real subsystems this
        method exercises: production-only build, then test-project build,
        then the 2 new tests filtered alone with a timeout, then the full
        suite with a timeout, before the final full clean rebuild - no
        hangs or surprises at any step.
      - 2 new hand-derived tests (song grid/song-go/position/song-info/
        bookmark reset, and a separate test for the track-count plumbing),
        both correct on first run. Full solution rebuild (Release|x64)
        confirmed 0 errors; 216 tests pass (up from 214, +2, 0 regressions).
      - `LoadRMW`/`LoadTxt` (Batch 3) call `ClearSong()` and are now
        unblocked on that front, but haven't themselves been re-triaged -
        left for a future batch.
- [x] `LoadRMW`/`LoadTxt` (Batch 3, dropped at the time for `ClearSong`'s
      sake) - now unblocked. Turned out to need almost no further work once
      re-checked:
      - `LoadRMW`'s only remaining hazard was `CString::LoadString(
        IDS_RMT_VERSION)` - the exact same resource-string hazard already
        fixed for `SaveRMW`/`SaveTxt`/`Rmt.cpp`/`RmtView.cpp`, just never
        applied to this 6th call site since `LoadRMW` was dropped from that
        batch before the fix went in. Replaced with `RMT_VERSION_STRING`.
      - `LoadTxt` had no hazard of its own at all beyond `ClearSong()` -
        `NextSegment`/`Trimstr`/`Hexstr` (`IOHelpers.cpp`) are pure
        functions already fully linked, and `g_Instruments.LoadInstrument`/
        `g_Tracks.LoadTrack` are already real (`IO_Instruments.cpp`/
        `IO_Tracks.cpp`, linked since Batch 3).
      - Both moved to `SongEditing.cpp`, right after `SaveRMW`/`SaveTxt`
        respectively (matching each format's Save/Load pairing). Widened
        from `std::ifstream&` to `std::istream&`, matching `LoadRMT`'s
        already-established precedent - the one real call site
        (`FileOpen()` in `IO_Song.cpp`) already passes a genuine file
        stream. Removed the now-dead `DEFINE_MAINPARAMS`/
        `RMWMAINPARAMSCOUNT` macro duplicate from `IO_Song.cpp` (the only
        remaining user there was `LoadRMW`, and `SongEditing.cpp` already
        had its own copy from `SaveRMW`'s earlier move).
      - **Found a genuine pre-existing bug while writing `LoadTxt`'s
        round-trip test** (confirmed by dumping `SaveTxt()`'s exact byte
        output and tracing `LoadTxt()`'s parser against it by hand, not
        assumed): `SaveTxt()` writes a blank "gap" line between the
        `[MODULE]` header block and `[SONG]` (e.g. `...\nVERSION: 01\n\n
        [SONG]\n05 -- -- --\n`). `LoadTxt()`'s inner `[MODULE]`-segment loop
        detects the next segment by calling `in.read(&b, 1)` one byte at a
        time and checking `if (b == '[') break;` - but that gap's `'\n'` is
        read as `b` first, not `'['`, so the `'['` that actually starts
        `[SONG]` gets silently absorbed mid-buffer by the following
        `getline()` call instead of being recognized as a segment boundary.
        Net effect: loading a `.txt` file that RMT itself just saved does
        not restore any song data (and likely not instrument/track data
        either, since `CInstruments::SaveAll()`/`CTracks::SaveAll()`'s TXT
        format follows the same "blank line, then `[SEGMENT]`" shape -
        not independently confirmed here since this test's song had no
        non-empty instruments/tracks to trigger those segments at all).
      - Given this project's "characterize current behavior first" scope,
        asked the user how to handle the finding rather than deciding
        alone; the user chose to characterize it as-is. The test documents
        both halves: the `[MODULE]` header (`RMT:`/`NAME:`/`MAINSPEED:`/
        `INSTRSPEED:`) parses correctly, but the song grid stays at
        `ClearSong()`'s `-1` default instead of the saved data. Fixing the
        bug itself was explicitly left as a separate, not-yet-made decision.
        Tracked upstream as
        [raster-atari-org/RASTER-Music-Tracker#21](https://github.com/raster-atari-org/RASTER-Music-Tracker/issues/21).
      - 2 new hand-derived tests (`LoadRMW`'s full round trip via
        `SaveRMW`, since it has no comparable bug; `LoadTxt`'s
        header-parses/song-doesn't split). Full solution rebuild
        (Release|x64) confirmed 0 errors; 218 tests pass (up from 216, +2,
        0 regressions).
- [x] The "real dialog cluster" (`InstrChange`, `SongInsertCopyOrCloneOfSongLines`,
      `TracksOrderChange`, `BlockEffect`) - re-analyzed in detail before
      accepting the plan's original "likely just defer all four" call, per
      the same lesson `ClearSong` taught: 3 of the 4 turned out to have the
      exact same shape as `InstrInfo`/`TrackInfo` - the dialog only supplies
      a fixed set of input parameters up front, and all the real mutation
      logic runs entirely independently of the dialog object afterward.
      - **`BlockEffect`** (`CTrackClipboard`, `Clipboard.cpp`) is the one
        confirmed exception: its entire body is 3 lines of setup before
        `dlg.DoModal()` - all real work happens *live inside*
        `CEffectsDlg`'s own UI handlers while the dialog is open (it's
        passed a pointer straight into the live track data:
        `dlg.m_trackptr = td;`). There's no separable business logic to
        extract; the original "defer" call stands for this one specifically.
      - **`InstrChange`**: added `struct TInstrChangeParams` (`SongTypes.h`,
        16 fields, one per dialog control, named after the local variables
        the method's body has always used internally) and
        `CSong::InstrChangeApply(const TInstrChangeParams&, CString*
        resultMsg = NULL)` (`SongEditing.cpp`, right after `InstrInfo`) -
        dual-mode like `InstrInfo`/`TrackInfo`. `InstrChange()` itself
        shrinks to: validate the instrument, show the dialog, and (if
        confirmed) copy its 16 fields into a `TInstrChangeParams` and call
        `InstrChangeApply()`. Zero other change to the ~200-line business
        logic itself.
      - **`SongInsertCopyOrCloneOfSongLines`**: added
        `CSong::SongInsertCopyOrCloneOfSongLinesApply(int& line, int
        linefrom, int lineto, BOOL clone, int tuning, int volumep)`
        (`SongEditing.cpp`) - just 5 plain parameters, no new struct needed.
        Its two `MessageBox` calls (song/track-range overrun) are guard-only
        errors, avoidable with valid test data - same treatment as
        `LoadRMT`'s guard-only branches, no dual-mode escape hatch needed.
      - **`TracksOrderChange`**: has a *mid-function* `MB_YESNOCANCEL`
        confirmation prompt gating further execution (same category as the
        already-deferred `SongMaketracksduplicate`/`Songswitch4_8`) - the
        one of the three that needed real design care. Added
        `CSong::TracksOrderChangeApply(int fromline, int toline, const int
        tracksorder[SONGTRACKS])`. Crucially, `m_TracksOrderChange_songlinefrom`/
        `songlineto` get updated in the *wrapper*, not in `Apply()` - the
        original code updates them right after range validation but
        *before* the confirm prompt, so they persist even if the user
        cancels at the confirm step. Moving that assignment into `Apply()`
        (only reached after confirmation) would have silently changed that
        behavior; keeping it in the wrapper preserves it exactly while still
        making the reorder logic itself a pure, directly-testable function
        of its explicit inputs.
      - 5 new hand-derived tests, all correct on first run (remap +
        `onlytrack` restriction for `InstrChangeApply`; copy + clone paths
        for `SongInsertCopyOrCloneOfSongLinesApply`, including tracing
        `FindNearTrackBySongLineAndColumn`'s search order and confirming
        `g_Tracks.ModifyTrack(..., tuning=0, ..., volumep=100)` is a true
        no-op by hand; column reorder/clear for `TracksOrderChangeApply`).
        Full solution rebuild (Release|x64) confirmed 0 errors; 223 tests
        pass (up from 218, +5, 0 regressions).
- [x] `ExportV2` triage (analysis only, no code changes) - written up in its
      own `plans/EXPORTV2_PLAN.md`, mirroring this doc's own structure.
      Read every method `ExportV2` can reach (`CRmtExporter`,
      `CASMFileExporter`, `CSongExporter`, `CSongContainer`/`CSongExport`)
      rather than assuming from the dispatcher's shape alone. Found two
      starkly different tiers:
      - `ExportV2` itself is a safe dispatcher even though it
        unconditionally constructs `CSongContainer`/`CSongExporter`/
        `CSongExport` regardless of `iotype` - both constructors just store
        pointers/validate play mode; the real Atari rendering pipeline only
        runs lazily, inside `CSongContainer::GetPokeyStream()`, the first
        time something actually calls it.
      - **RMT/ASM export family** (`CRmtExporter::ExportAsRMT`/
        `ExportAsStrippedRMT`, `CASMFileExporter::ExportAsAsm`/
        `ExportAsRelocatableAsmForRmtPlayer`/`BuildRelocatableAsm`): same
        shape as the already-solved real-dialog cluster, or in
        `BuildRelocatableAsm`'s case (~545 lines, the bulk of
        `ASMFileExporter.cpp`) already a pure function taking explicit
        parameters - confirmed by scanning its entire body for globals/
        dialogs and finding none beyond already-safe `g_Instruments`/
        `g_Tracks`. `ExportAsRMT` is fully clean today, blocked only on
        widening `CAtariIO::SaveBinaryBlock` to `std::ostream&` (same
        precedent as `LoadBinaryBlock`).
      - **SAP/LZSS/WAV/XEX export family** (`CSongExporter`'s methods):
        gated behind `CSong::DumpSongToPokeyStream()`, the real Atari
        audio-rendering pipeline Batch 6 flagged but explicitly did not
        investigate when scoping `TimerRoutine`. Recommended to stay
        deferred pending its own dedicated investigation - not ruled
        out, just correctly identified as out of scope for this pass.
      - Found one incidental stale-stub risk for whichever future batch
        links `ASMFileExporter.cpp`: `g_PrefixForAllAsmLabels` is currently
        defined both there (real, unlinked) and in
        `test/SongEditingStub.cpp` (a stub added during the `ClearSong`
        batch) - the stub must be removed first to avoid an LNK2005 clash.
      - No code changes, no build/test impact this pass - see
        `plans/EXPORTV2_PLAN.md` for the full per-method breakdown and
        suggested execution order (Batches A-D, plus what stays deferred).
- [x] `ExportV2` Batch A: `CRmtExporter::ExportAsRMT` + `CAtariIO::SaveBinaryBlock`.
      - Widened `SaveBinaryBlock()` to `std::ostream&` (from `std::ofstream&`),
        matching `LoadBinaryBlock`'s existing precedent - its handful of
        real call sites all pass a genuine file stream already.
      - Split `RmtExporter.cpp` into a new `RmtExporterCore.cpp` holding
        just `ExportAsRMT()` (widened to `std::ostream&` too, for the same
        reason), leaving `ExportAsStrippedRMT()` (the real-dialog one,
        Batch B) behind with a pointer comment. Removed the now-dead
        `g_Instruments`/`Instruments.h` extern/include from
        `RmtExporter.cpp` itself, since only `ExportAsRMT()` had used them.
      - **A real hazard, seen firsthand, not just inferred from a comment**:
        the first version of the new round-trip test forgot to set
        `mainspeed`/`instrspeed` to non-zero values before calling
        `MakeModule()` (the same "`DecodeModule()` rejects a zero speed
        byte" gotcha documented back in Batch 2) - and `LoadRMT()`'s
        guard-only error `MessageBox` for that failure is a real, blocking
        WinAPI-style call. The test actually popped a real modal dialog and
        hung for ~8 seconds before something dismissed it, instead of
        failing fast. Fixed by setting `mainspeed`/`instrspeed` before
        encoding, same as the existing `LoadRMT`/`MakeModule` tests already
        do - but this is now first-hand confirmation (not just a comment)
        that these guard-only `MessageBox` branches are a genuine, not
        theoretical, test-safety hazard: getting the test data wrong
        doesn't just fail an assertion, it can hang the whole run.
      - 1 new hand-derived test (`ExportAsRMT` round-tripped through the
        already-tested `LoadRMT`, same philosophy as `LoadRMT`'s own test).
        Full solution rebuild (Release|x64) confirmed 0 errors; 224 tests
        pass (up from 223, +1, 0 regressions).
- [x] `ExportV2` Batch B: `CRmtExporter::ExportAsStrippedRMT`.
      - Same dialog-gather-then-work shape as `InstrChange`/
        `TracksOrderChange`: `CExportStrippedRMTDialog` supplies 7 fields,
        all read into locals/globals right after `DoModal()`, then the rest
        of the function (regenerate the module at the confirmed address/
        SFX-ness, save it as a binary block) runs independently of the
        dialog object. Extracted into
        `CRmtExporter::ExportAsStrippedRMTApply(CSong&, std::ostream&, int
        targetAddrOfModule, BOOL sfxSupport)` in `RmtExporterCore.cpp`,
        widened to `std::ostream&` like `ExportAsRMT()` for the same
        testability reason.
      - Dropped one genuinely dead local (`int targetAddrOfModule =
        dlg.m_exportAddr;` in the original wrapper) while moving the code -
        confirmed by reading the rest of the function that it was never
        read again (the code uses `g_rmtstripped_adr_module` directly
        instead), so this is a zero-behavior-change cleanup, not a
        judgment call.
      - The wrapper's preliminary "build an SFX-variant module just to show
        its size in the dialog" step (`exportTempDescription` before
        `DoModal()`) stays in the wrapper - it's dialog-*adjacent* (only
        used to populate dialog display fields), not dialog-independent
        business logic, so there's nothing to gain by extracting it too.
      - **Tests deliberately decode via `CAtariIO::LoadBinaryBlock()`/
        `CSong::DecodeModule()` directly, not via `LoadRMT()`**:
        `ExportAsStrippedRMTApply()` only ever writes a single block (no
        names block), and `LoadRMT()` shows a real, blocking "Info"
        `MessageBox` when it doesn't find a second block - avoided given
        the firsthand confirmation from Batch A that these guard/info
        `MessageBox` branches can actually hang a test run, not just fail
        an assertion.
      - 2 new hand-derived tests (one per `sfxSupport` value, both
        confirming the block decodes successfully at the given target
        address). Full solution rebuild (Release|x64) confirmed 0 errors;
        226 tests pass (up from 224, +2, 0 regressions).
- [x] `ExportV2` Batch C: `CASMFileExporter`.
      - **`BuildRelocatableAsm()`** (~340 lines, confirmed pure while
        scoping `ExportV2`'s original triage) needed one more piece to
        actually link: it calls `ComposeRMTFEATstring()`, a separate
        `CASMFileExporter` method still living in the dialog-only half of
        the file. Re-read it in full and confirmed it's pure too - only
        touches `song.m_songgo`/`song.m_song`/`song.GetTracks()`/
        `song.GetInstrumentSpeed()`/`song.m_mainSpeed` (`CASMFileExporter`
        is a `friend` of `CSong`, so it can read these directly - see
        `Song.h`) and already-safe `g_Tracks`/`g_Instruments`. Moved both,
        plus their small `rword()` helper, into a new
        `ASMFileExporterCore.cpp`.
      - **`ExportAsAsm()`**: same dialog-gather-then-work shape as the
        earlier batches - `CExportAsmDlg` supplies 4 distinct fields
        (`m_prefixForAllAsmLabels`, `m_exportType`, `m_notesIndexOrFreq`,
        `m_durationsType`) referenced 16 times through an otherwise
        dialog-independent ~250-line body. `m_prefixForAllAsmLabels` didn't
        need to become a 4th parameter, though: the wrapper already writes
        its confirmed value back into `g_PrefixForAllAsmLabels` *before*
        calling the extracted method, so `ExportAsAsmApply()` just reads
        that global directly, like `InstrChangeApply()` reading
        `m_TracksOrderChange_songlinefrom` did for `TracksOrderChangeApply()`.
      - **`ExportAsRelocatableAsmForRmtPlayer()`**: its dialog supplies 11
        fields, but almost the entire post-dialog body was already a thin,
        dialog-independent pass-through to `BuildRelocatableAsm()` (which
        itself takes plain parameters, not `dlg` fields). Bundled those 11
        fields into a new `TRelocatableAsmExportParams` struct
        (`ASMFileExporter.h`, same pattern as `TInstrChangeParams` in
        `SongTypes.h`) and extracted `ExportAsRelocatableAsmForRmtPlayerApply()`
        - the `exportDescWithSFX`/`exportDescStripped` selection and the
        final stream write are the only things it does beyond delegating.
      - **Found and fixed a linker trap of my own making**: removing the
        stale `g_PrefixForAllAsmLabels` test stub (per `EXPORTV2_PLAN.md`'s
        Tier-1 findings) and just linking `ASMFileExporterCore.cpp` wasn't
        enough - `ASMFileExporterCore.cpp`'s `ExportAsAsmApply()` needed the
        global too, but its *definition* still lived in the dialog-only
        `ASMFileExporter.cpp`, which the test project doesn't link. Fixed by
        moving the definition itself into `ASMFileExporterCore.cpp` (the
        linked half) and leaving an `extern` declaration behind in
        `ASMFileExporter.cpp` - caught immediately by the first test build
        (LNK2001), not by production (which still links both files).
      - **Found and fixed a second orphan-safe-method case**:
        `CInstruments::GetFrequency()` (`Instruments.cpp`, not linked in
        tests) is called by `ExportAsAsmApply()`'s frequency-lookup branch.
        Read its body and confirmed its only dependency is
        `g_Atari.GetByteAt()` - a plain array read on `CAtari`'s own memory
        buffer, already established safe back in Batch 6 - so moved it into
        `InstrumentsCore.cpp` next to the already-there `GetNote()`, adding
        `extern CAtari g_Atari;` there (already a real, linked global via
        `test/AtariStub.cpp`).
      - 3 new hand-derived tests (`ExportAsAsmApply`'s tracks-only output;
        `BuildRelocatableAsm` producing valid assembler for a real encoded
        module; `ExportAsRelocatableAsmForRmtPlayerApply` writing to its
        stream), all correct on first run. Full solution rebuild
        (Release|x64) confirmed 0 errors; 229 tests pass (up from 226, +3,
        0 regressions).
- [x] `ExportV2` Batch D - attempted, found not practically testable as
      scoped. Moved `CSong::ExportV2()` out of `IO_Song.cpp` into its own
      new `SongExportV2.cpp` - a real structural improvement regardless of
      the outcome below, since it isolates it from the rest of that file
      (the `FileXxx` family, Batch 7, recommended deferred indefinitely).
      - **Discovered by actually attempting the link, not by reasoning
        about it in advance**: `ExportV2()`'s `switch (iotype)` statement
        references all 9 branches syntactically in the compiled function
        body, so the linker needs every one of those symbols to resolve
        regardless of which `iotype` a given test would actually pass in.
        Trying to link just the file produced 13 LNK2019/LNK2001 errors:
        `CSongContainer`/`CSongExport`/`CSongExporter`'s constructors, all
        5 of `CSongExporter`'s Tier-2 export methods (SAP-R/LZSS/SAP+LZSS/
        XEX+LZSS/WAV - the ones gated behind the real Atari audio-rendering
        pipeline, still deferred per this doc's Tier-2 findings), the two
        real-dialog wrappers `CRmtExporter::ExportAsStrippedRMT()`/
        `CASMFileExporter::ExportAsRelocatableAsmForRmtPlayer()` (not their
        already-tested `*Apply()` siblings, which the switch doesn't call),
        and a real `g_Pokey` global.
      - Notably, `CSongContainer`'s constructor isn't even a "link-only,
        never-really-called" case the way most stubs in this effort are -
        it's constructed unconditionally before the switch, for every
        `iotype` including `RMT`. Confirmed its own body is simple (stores
        a pointer, validates the song is stopped), but the same
        translation unit also holds `GetPokeyStream()`/
        `GetModifiablePokeyStream()`, which call into the real rendering
        pipeline - linking the constructor for real would mean either
        linking those too (expanding into Tier 2) or splitting
        `SongContainer.cpp` yet again, several layers deeper than this
        batch's actual goal.
      - Asked the user how to close this out given the disproportionate
        new surface (7+ symbols, several of them exactly the
        deferred/dialog territory this whole effort keeps out of the test
        binary) versus the value (confirming a thin dispatch layer whose
        every real branch is already directly tested via Batches A-C).
        Chose to skip direct testing: keep the `SongExportV2.cpp` move for
        production clarity, but don't add it to the test project.
      - No new tests, no test-project changes. Production-only change
        (the file move). Full solution rebuild (Release|x64) confirmed 0
        errors; 229 tests still pass (unchanged from Batch C, 0
        regressions).
- [x] SAP/LZSS/WAV/XEX family (`ExportV2`'s Tier 2) - full triage and first
      unlock. Full per-method breakdown in the new
      `plans/SAP_LZSS_WAV_XEX_PLAN.md`; highlights below.
      - **`CSong::DumpSongToPokeyStream()` (`Song_DumpSong.cpp`) - the real
        prerequisite blocking this entire family - turned out safe.** It
        runs a real `while (m_play != PLAY_STOP) { PlayVBI(); ... }`
        playback loop, and nothing before this had ever proven it
        terminates. Traced by hand: `SongPlayNextLine()` (`SongCore.cpp`)
        sets `m_play = PLAY_STOP` once `CPokeyStream::TrackSongLine()`
        detects a revisited songline, and for `PLAY_SONG`/`PLAY_FROM` (the
        only modes this method is ever called with in production)
        `m_songplayline` always advances or wraps at 255 - so a revisit,
        and therefore a stop, is guaranteed within a small, bounded number
        of songline advances regardless of song content. Independently
        corroborated by `PokeyStreamTests.cpp`'s own
        `TrackSongLineDetectsLoopOnSecondFullPassAndResolvesOnThird` test.
      - `CPokeyStream` itself (the whole recording state machine) was
        *already* fully linked and tested - its own header comment said
        the "data path" needed a real `CAtariTrackerDriver`/`CAtari`,
        blocked by "CSong's g_Atari-coupled constructor". That note was
        stale: `g_AtariTrackerDriver`/`g_Atari` have been real, linked
        globals since Batch 4/6, and nothing in `CSong`'s constructor
        actually touches `g_Atari` at all. Comment corrected.
      - **A genuinely severe hazard, found and defended against**:
        `CSongContainer`'s constructor calls `ThrowRuntimeException()` if
        the song isn't `PLAY_STOP` - and unlike every other guard-only
        `MessageBox` this effort has been careful around, that one calls
        `exit(2)` right after, terminating the *entire test process*, not
        just failing one test. `song.Stop()` is now called defensively
        before every `CSongContainer` construction in tests.
      - Added link-only stubs for `g_AtariTrackerDriver->Play()` (only
        called if `g_rmtroutine`, which no test sets), `RefreshScreen()`
        (mirrors its own real, guaranteed-taken guard clause - it always
        returns 0 immediately since `g_hwnd` is always NULL here) and
        `DisableEventSection` (its real ctor/dtor are a purely cosmetic,
        non-blocking cursor/window-enable toggle) in a new
        `test/Song_DumpSongStub.cpp`, plus a real one-line
        `SendInfoMessage()` (delegates to the already-stubbed
        `SetStatusBarText()`).
      - Verified incrementally with an explicit timeout at every step,
        matching Batch 6's protocol for genuine hang risk: build, then the
        one new test alone with a short timeout, then the full suite. The
        first attempt "failed" in 0ms with wrong assertion values, not a
        hang - the `m_instrumentSpeed` defaults to 0" gotcha (documented
        back in Batch 2) hit for a third time, this time gating the
        recording loop's inner `for` loop entirely so no frames were ever
        recorded. Fixed by setting `instrspeed` non-zero, same as every
        other time this has come up.
      - **Process hygiene note**: kicked off a full clean rebuild in the
        background and then kept editing source files before it finished -
        the build read inconsistent file states mid-compile and failed
        with a spurious linker error unrelated to any real code problem.
        Re-ran cleanly once all edits were done; not a real regression.
        Lesson: don't edit files a running build might still be compiling.
      - **`CSongExporter::ExportSAP_R` unlocked and tested**: same "dialog
        gathers params, real work happens independently" shape as the
        RMT/ASM exporters - `CSAPFileExportDialog::Show()` populates a
        `CSAPFile`, then delegates to the already dialog-free
        `CSAPFileExporter::ExportSAP_R(songExport, sapFile, ou)`. No
        `*Apply()` split needed, just linking it directly. Widened it and
        `CPokeyStream::WriteToFile()` to `std::ostream&`. Split
        `SAPFileExporter.cpp` into a new `SAPFileExporterCore.cpp` (just
        `ExportSAP_R`) after confirming - by actually attempting to link
        the whole file first - that `/Gy`'s function-level linking doesn't
        eliminate the unused `ExportSAP_B_LZSS`'s dependencies in this
        project (not built with `/OPT:REF`).
      - The other four methods each stay deferred for their own distinct,
        now-documented reasons rather than one blanket "Tier 2" excuse:
        `ExportSAP_B_LZSS`/`ExportXEX_LZSS` need a real on-disk resource
        file (`resources/players/vu_player_v2.obx`) - a new category of
        test dependency this suite has never needed before;
        `ExportWAV` is confirmed genuinely hazardous (real POKEY audio
        *synthesis* via `CXPokey::RenderSoundV2()`, not just register
        bookkeeping - the same category Batch 6 flagged for
        `TimerRoutine`); `ExportLZSS`/`ExportCompactLZSS` write multiple
        real files to disk by design and are self-described in their own
        comments as "hacked up"/"currently unused?".
      - 2 new hand-derived tests (`DumpSongToPokeyStream` via
        `CSongContainer::GetPokeyStream()`; `ExportSAP_R`'s header + stream
        data). Full solution rebuild (Release|x64) confirmed 0 errors; 231
        tests pass (up from 229, +2, 0 regressions).
- [x] `CSAPFileExporter::ExportSAP_B_LZSS` unlocked - this suite's first
      real-on-disk-file-backed test.
      - Asked the user how to handle the one remaining blocker
        (`resources/players/vu_player_v2.obx`, loaded via
        `GetResourceFilePath()`/`g_prgpath`, a global normally set once by
        the real app's startup code); chose to set `g_prgpath` in test
        setup rather than stay deferred. `g_prgpath` is now set once, at
        static-init time (`test/AtariBinariesStub.cpp`), computed from
        `__FILE__` to point at this repo's own checked-in `rmt/` folder -
        stable as long as building and running stay on the same machine,
        which this workflow always does.
      - `ExportSAP_B_LZSS`'s remaining dependencies were all already safe
        or trivial to link for real: `VUPlayer::PatchMemoryForSAP_B()`
        (pure memory patching, confirmed reading the whole 53-line file),
        `SendErrorMessage()` (real body copied verbatim - `g_statusBar`
        defaults `nullptr` and no test sets it, so its `MessageBox()`
        branch is preserved as real code but unreachable, same treatment
        as `CSongTimer::WaitForTimerRoutineProcessed()`'s guard),
        `CCompressLzss::LZSS_SAP()` (`lzss_sap.cpp`, already linked and
        tested), `AtariBinaries.cpp` (needed linking - pure MFC `CFile`
        I/O plus the externally-supplied `GetResourceFilePath()`).
      - Once both `CSAPFileExporter` methods were safe, the
        `SAPFileExporter.cpp`/`SAPFileExporterCore.cpp` split from the
        previous batch was no longer needed - merged `ExportSAP_B_LZSS`
        into `SAPFileExporterCore.cpp` and deleted the now-empty
        `SAPFileExporter.cpp` (removed from `Rmt.vcxproj` too). Widened
        `ExportSAP_B_LZSS` to `std::ostream&` to match.
      - The real resource file's compression pass
        (`CCompressLzss::LZSS_SAP()`) prints a lot of diagnostic output to
        stdout (compression ratios, a per-value dump table) - pre-existing
        production behavior, not introduced by this test; noisy but
        harmless.
      - 1 new hand-derived test, correct on first run (16ms, confirming
        the real file loaded and the whole pipeline ran). Full solution
        rebuild (Release|x64) confirmed 0 errors; 232 tests pass (up from
        231, +1, 0 regressions).
- [x] `CSongExporter::ExportXEX_LZSS` unlocked - completes the user's
      explicit "ExportSAP_B_LZSS/ExportXEX_LZSS" directive.
      - Unlike `ExportSAP_R`/`ExportSAP_B_LZSS` (which delegate to the
        already dialog-free `CSAPFileExporter` class), the real work here
        - the `CXEXFile`-taking overload of `ExportXEX_LZSS` - lives
        directly in `CSongExporter`, in the same `SongExporter.cpp`
        translation unit as the dialog-showing 1-arg overload,
        `ShowXEXExportDialog()`, `ExportWAV()`, and
        `ExportLZSS()`/`ExportCompactLZSS()`. Split those four (plus the
        two private static helpers the real overload calls,
        `StrToAtariVideo()` and `BruteforceOptimalLZSS()`, and the empty
        `CSongExporter()` ctor and `CXEXFile::InitFromSong()` - both
        needed directly by the test) into a new `SongExporterCore.cpp`,
        leaving the dialog/disk-write/audio-rendering methods behind in
        `SongExporter.cpp`.
      - Widened `ou` from `std::ofstream&` to `std::ostream&`, same
        precedent as elsewhere; the 1-arg overload's `std::ofstream&`
        argument still binds fine.
      - Needs the same real on-disk `resources/players/vu_player_v2.obx`
        resource `ExportSAP_B_LZSS` needed, but reached via a different,
        previously-unexercised code path:
        `CRmtAtariBinaries::GetVUPlayerBinary()` →
        `LoadResourceByteArray()` → `LoadByteArray()` (MFC `CFile`-based),
        vs. `ExportSAP_B_LZSS`'s `std::ifstream`-based
        `CAtariIO::LoadBinaryFile()`. Confirmed this is the same hazard
        category already accepted (real disk read via `g_prgpath`), not a
        new one - `GetResourceFilePath()`/`g_prgpath` are shared by both
        paths (`test/AtariBinariesStub.cpp`).
      - Calls `CSong::DumpSongToPokeyStream()` directly with `PLAY_FROM`
        (not via `CSongContainer` - it builds its own `CPokeyStream` per
        subtune), reusing the loop-termination proof from the batch above.
      - Newly linking `LZSSFile.cpp` (for the real
        `CLZSSFile::GetFrameSize()`) surfaced a stale hardcoded stub for
        the same method in `test/PokeyStreamStub.cpp` (`return 9;`,
        `LNK2005` duplicate symbol) - removed the stub now that the real,
        trivial body (`song.IsStereo() ? 18 : 9`) is linked for real.
      - 1 new hand-derived test, correct on first run (6ms - the real
        resource file loaded and the whole LZSS/`DumpSongToPokeyStream`
        pipeline ran for real). Full solution rebuild (Release|x64)
        confirmed 0 errors; 233 tests pass (up from 232, +1, 0
        regressions).
      - This completes `plans/SAP_LZSS_WAV_XEX_PLAN.md`'s actively-pursued
        scope. `ExportWAV` (real `CXPokey` audio synthesis) and
        `ExportLZSS`/`ExportCompactLZSS` (real multi-file disk writes,
        self-described as "hacked up"/"currently unused") remain
        deliberately deferred per that plan's findings.
- [x] Re-verified the `FileXxx` family (Batch 7,
      `plans/SONG_IO_SONG_REMAINING_PLAN.md`) - the last remaining
      "deferred hazard category" from that plan not yet given a per-method
      read (it was only ever assessed in bulk). Read all 11 methods
      (`FileReload`/`FileOpen`/`FileSave`/`FileSaveAs`/`FileNew`/
      `FileImport`/`FileExportAs`/`FileInstrumentSave`/`FileInstrumentLoad`/
      `FileTrackSave`/`FileTrackLoad`) in full in `IO_Song.cpp`. Unlike
      `InstrChange`/`SongInsertCopyOrCloneOfSongLines`/`TracksOrderChange`
      (whose dialogs just gather a fixed-shape parameter struct, with
      identical mutation logic regardless of which values were picked),
      here the dialog's result - the chosen file path, or whether to
      proceed at all - IS the method's entire reason for existing, so
      there's no dialog-free "Apply()" core to extract; the logic each one
      dispatches to once a path is known (`LoadRMT`/`LoadTxt`/`LoadRMW`/
      `SaveTxt`/`SaveRMW`/`ExportV2`/instrument and track save/load) is
      already exactly what earlier batches test directly. All 11
      unconditionally construct a real `CFileDialog`/`CFileNewDlg` and
      (except `FileOpen`/`FileReload`, which can skip `.DoModal()` when a
      filename is already known, but still construct the dialog object)
      unconditionally call `.DoModal()`. No code changed - this confirms
      Batch 7's original bulk recommendation on a verified rather than
      assumed basis, and closes out the deferred-hazard backlog for
      `Song.cpp`/`IO_Song.cpp`/`ExportV2`: every remaining category now has
      an individually-investigated, confirmed reason to stay deferred (see
      `plans/SONG_IO_SONG_REMAINING_PLAN.md`'s Batch 7 section for the
      full per-method writeup).
- [x] Broader survey of every other production `.cpp` file not yet linked
      into `RmtTests.vcxproj` (~55 files) - see `plans/BROADER_SURVEY_PLAN.md`.
      Most are confirmed out of scope (real UI/dialogs/views, real hardware/
      DLL coupling - verified `RmtMidi.cpp` genuinely calls
      `midiInGetNumDevs()`/`midiInGetDevCaps()` - `Global.cpp`'s mega-wiring,
      already-solved-shape remnants, or effectively empty). Four new
      candidates found: `IO_Importer.cpp` (`ImportTMC`/`ImportMOD`, highest
      value), `Undo.cpp`, and two small/cheap wins (`Instruments.cpp`'s and
      `AtariTrackerDriver.cpp`'s remainders).
- [x] `IO_Importer.cpp` Batch A: `CSong::ImportTMC` unlocked - see
      `plans/IO_IMPORTER_PLAN.md`.
      - `ImportTMC`/`ImportMOD` break every prior `*Apply()` split's
        assumption: their options dialog's own display text needs data only
        available after partially decoding the file (the parsed song
        name), not just pre-existing state. Presented this as an explicit
        design question; user chose the "two-phase `Apply()`" design (most
        faithful to today's exact call sequence: `ParseHeader()` for the
        unconditional pre-dialog work, `Apply()` for the rest, taking the
        dialog's flags as parameters).
      - Added `TImportTMCHeader`/`TImportTMCResult` to `SongTypes.h`
        (`TImportTMCHeader.mem[65536]` mirrors the existing
        `TExportDescription.mem[65536]` precedent for embedding a
        full-RAM buffer directly in a struct).
      - `ImportTMCApply()`'s ~500-line body is a verbatim transcription of
        the original conversion logic (only `mem`/`bfrom` become
        `header.mem`/`header.bfrom`, `x_usetable` etc. become parameters) -
        including its inert commented-out dead-code block, kept
        byte-for-byte rather than cleaned up, to keep this a pure
        mechanical move.
      - `CConvertTracks` (+3 supporting structs) confirmed TMC-only (grepped
        `ImportMOD`'s body - no reference) and moved alongside.
      - No new links needed in the test project - every global/method
        `IO_ImporterCore.cpp` touches was already linked from prior batches.
      - 3 new hand-derived tests, including a full conversion test built
        from a hand-crafted minimal TMC byte buffer (byte-layout fully
        derived and commented in the test itself). One assertion was wrong
        on the first attempt (expected a note's volume to survive at 15,
        got 0) - traced to real, faithful behavior: volume normalizes
        against the *instrument's* tracked max envelope volume, which
        defaults to 0 for an instrument that was never defined (the test's
        deliberately minimal/degenerate input) - not a test bug; fixed the
        expectation and documented why.
      - Fixed a stale comment in `SongEditingTests.cpp` claiming
        `CInstruments::Update()` was still stubbed as a no-op - it's had
        real behavior since Batch 3 of
        `plans/SONG_IO_SONG_REMAINING_PLAN.md`; the comment was just never
        updated when that changed.
      - Full solution rebuild (Release|x64) confirmed 0 errors; 236 tests
        pass (up from 233, +3, 0 regressions).
- [x] `IO_Importer.cpp` Batch B: `CSong::ImportMOD` unlocked - completes the
      `IO_Importer.cpp` triage. See `plans/IO_IMPORTER_PLAN.md`.
      - Same two-phase `Apply()` split as `ImportTMC`, plus two new
        wrinkles found while implementing (not assumed up front):
        1. `ImportModApply()` needs *continued* stream access (sample audio
           data lives beyond what `ParseHeader()` loads), so it takes
           `std::istream&` directly alongside the parsed header.
           `TImportMODHeader.mem` uses a `std::vector<BYTE>` (runtime-sized,
           unlike TMC's fixed 64KB buffer) rather than replicating the
           original's raw `new[]`/`delete[]` - same content/lifetime,
           safer ownership.
        2. Unlike `ImportTMC`'s one guard-only failure mode, `ImportMOD`'s
           header validation has three differently-worded original guard
           messages - collapsing to a single bool would lose which to show,
           so `TImportMODHeader` carries a plain `errorCode` (1/2/3)
           instead, and the wrapper switches on it to reconstruct the exact
           original text. The six *other* guard-only `MessageBox` calls
           living inside the real conversion logic (track/songline
           overflow, sample seek/read failure, final length mismatch) are
           kept exactly as-is - same "avoidable with valid test data"
           treatment as everywhere else, not a new decision.
      - `TMODInstrumentMark`/`AtariVolume()` (MOD-only, confirmed unused by
        `ImportTMC`) moved alongside. `x_fourier` (dialog checkbox 8) is
        captured by the original but only ever read inside a genuinely
        commented-out Fourier-transform block - same dead-code category as
        `MakeTuningBlock`/`DecodeTuningBlock` - so it's not threaded
        through to `Apply()`.
      - 3 new hand-derived tests: two small `ParseHeader` guard tests (a
        truncated header; an out-of-range channel count via a deliberately
        crafted "2CHN" identification - discovered while writing the test
        that an *all-zero* identification does NOT trigger this guard,
        since bytes outside the printable range fall through to a legacy
        "15-sample module" branch that hardcodes a valid `chnls`), and one
        full conversion test built from a hand-crafted minimal "M.K."
        31-sample module buffer (byte layout derived and commented in the
        test itself). The large conversion test passed on the first run,
        confirming the derivation was correct on paper before ever
        compiling it.
      - Full solution rebuild (Release|x64) confirmed 0 errors; 239 tests
        pass (up from 236, +3, 0 regressions). This completes the
        `IO_Importer.cpp` triage (both `ImportTMC` and `ImportMOD`).
- [x] `MessageBox(g_hwnd, ...)` → `Send<Type>Message(...)` refactor,
      Batch 1 (the prerequisite + new functions) - user-requested, not a
      characterization batch. See `plans/MESSAGEBOX_REFACTOR_PLAN.md` for
      the full survey (71 call sites across 24 files) and the three
      decisions the user made: merge `MB_ICONSTOP`/`MB_ICONERROR` into one
      `SendErrorMessage` and `MB_ICONWARNING`/`MB_ICONEXCLAMATION` into one
      `SendWarningMessage` (genuinely the same Win32 icon value in both
      cases, confirmed, not just visually similar); extend the existing
      `Messages.h`/`Messages.cpp` rather than a new file pair; give the new
      `SendQuestionMessage` confirmation-prompt function a test-injectable
      answer (`SetTestQuestionAnswer()`) rather than a fixed default, so
      confirm-gated code can eventually be characterized on every branch
      (not just automatically safe to avoid, like every other guard-only
      `MessageBox` in this effort so far).
      - `Messages.cpp` only needed one line changed
        (`#include "Global.h"` → a direct `extern HWND g_hwnd;`, matching
        the "declare individual externs" pattern) to become linkable for
        real - it was already effectively `Global.h`-free otherwise. This
        let it link into `RmtTests.vcxproj` directly for the first time,
        removing two verbatim-copied duplicates that existed solely
        because `Messages.cpp` wasn't linkable before now:
        `test/AtariBinariesStub.cpp`'s copy of `SendErrorMessage()`/
        `g_statusBar`, and `test/Song_DumpSongStub.cpp`'s copy of
        `SendInfoMessage()`.
      - Added `SendWarningMessage`/`SendInformationMessage` (mirroring
        `SendErrorMessage`'s existing 1-arg/2-arg shape) and
        `SendQuestionMessage`/`SetTestQuestionAnswer`/the
        `MessageButtons`/`MessageAnswer` enums. All three fire-and-forget
        functions share one new internal `SendMessageBox()` helper
        (icon + log-prefix parameters) instead of tripling the
        `if (g_statusBar == nullptr) {...} else {...}` logic.
        `SendInformationMessage` is deliberately named differently from
        the pre-existing `SendInfoMessage` (status-bar text, not a real
        modal notice) - a near-miss name, flagged in the plan doc as worth
        double-checking during the future call-site migration batches.
      - 4 new tests in a new `test/MessagesTests.cpp`: smoke tests for the
        three fire-and-forget functions (little else is observable - they
        log to `OutputDebugString`), and a real round-trip test proving
        `SendQuestionMessage` returns whatever `SetTestQuestionAnswer()`
        set, across all four answers and all three button sets.
      - Full solution rebuild (Release|x64) confirmed 0 errors; 243 tests
        pass (up from 239, +4, 0 regressions).
- [x] `MessageBox(g_hwnd, ...)` refactor, Batch 2: migrated all
      fire-and-forget call sites in the already-linked/near-linked files -
      `SongEditing.cpp` (14, not 10 as first estimated), `IO_ImporterCore.cpp`
      (6), `IO_Importer.cpp` (6, including `ImportMOD`'s `errorCode`-driven
      `switch`), `Undo.cpp` (5), `TuningTables.cpp` (1) - 32 sites total.
      `SongEditing.cpp`/`IO_ImporterCore.cpp` each lost their now-dead
      `extern HWND g_hwnd;` (no longer referenced anywhere in either file).
      `IO_Importer.cpp`/`Undo.cpp`/`TuningTables.cpp` keep their existing
      `#include "Global.h"` since they still need other globals from it -
      fully removing `Global.h` there was explicitly left out of scope
      (a bigger, separate decision from the one requested). Pure mechanical
      swap, no new hazard categories, so no new tests. Full solution
      rebuild (Release|x64) confirmed 0 errors; 243 tests pass (unchanged,
      0 regressions).
- [x] `MessageBox(g_hwnd, ...)` refactor, Batches 3-5 - completes the
      migration. See `plans/MESSAGEBOX_REFACTOR_PLAN.md`.
      - **Batch 3**: `IO_Song.cpp`'s 18 fire-and-forget sites. Pure
        consistency (that file stays deferred per Batch 7 of
        `plans/SONG_IO_SONG_REMAINING_PLAN.md`).
      - **Batch 4**: the 6 confirmation prompts (`GUI_Song.cpp` x1,
        `IO_Song.cpp` x2, `Song.cpp` x3) via `SendQuestionMessage`, bundled
        with `Song.cpp`'s 2 stray fire-and-forget sites found while
        touching the file anyway. `IO_Song.cpp`'s `TestBeforeFileSave()`
        had to drop a shared `int r` from a combined declaration (used only
        for its `MessageBox()` result) in favor of a locally-scoped
        `MessageAnswer answer`. This is where `SongMaketracksduplicate`/
        `Songswitch4_8` *could* finally get real tests via the
        test-injectable answer hook - not done, since `Song.cpp` itself
        still isn't linked into `RmtTests.vcxproj` for other, unrelated
        reasons; actually testing those two needs its own future move into
        `SongEditing.cpp`, revisiting `plans/SONG_IO_SONG_REMAINING_PLAN.md`'s
        "defer both entirely" decision.
      - **Batch 5**: the remaining hardware/DLL-coupled files (`C6502.cpp`
        x2, `Pokey.cpp` x3, `PokeyRenderer.cpp` x5, `RmtMidi.cpp` x1),
        confirmed permanently out of scope for testing but migrated anyway
        for full consistency, per the user's "do all of these" request.
      - **Zero `MessageBox(g_hwnd, ...)` call sites remain anywhere in the
        codebase** outside `Messages.cpp`'s own real implementation - the
        whole refactor described in `plans/MESSAGEBOX_REFACTOR_PLAN.md` is
        complete.
      - Full solution rebuild (Release|x64) confirmed 0 errors; 243 tests
        pass (unchanged - none of Batches 3-5's files are linked into the
        test project - 0 regressions).
- [x] `SongMaketracksduplicate`/`Songswitch4_8` unlocked - the follow-up the
      MessageBox refactor was flagged as enabling. Both were deferred
      indefinitely by `plans/SONG_IO_SONG_REMAINING_PLAN.md`'s "Decisions
      (resolved) #3" solely because of their confirmation prompt, at the
      time a real, unavoidable-in-tests `MessageBox`. Now that it routes
      through `SendQuestionMessage()` (test-injectable answer), the prompt
      itself is safe to trigger, so no `*Apply()`-style extraction was even
      needed - re-verified every other dependency first (per this effort's
      "verify before trusting old triage" habit): `MarkTF_USED`/
      `MarkTF_NOEMPTY`/`FindNearTrackBySongLineAndColumn`/
      `TrackCopyFromTo` are all already in `SongEditing.cpp` (linked/safe);
      `g_Undo.ChangeSong`/`ChangeTrack` are already-established no-op
      stubs, `g_Undo.DropLast`/`Clear` are real (`UndoStub.cpp`);
      `Songswitch4_8`'s closing `g_Atari.Init(IsNTSC())` is the exact same
      call `ClearSong` already makes (already proven safe - `CAtari::Init(bool)`
      only sets `m_ntsc` then calls `CTuning::InitTuning(...)`, which
      resolves to the already-stubbed no-op 0-arg overload in tests).
      Moved both method bodies verbatim into `SongEditing.cpp` (zero code
      changes beyond relocation); `Song.cpp` left with a one-line
      "implemented in SongEditing.cpp" comment for each, matching every
      other split in this effort.
      - 7 new hand-derived tests, all passing on the first run: guard tests
        (goto line, no track selected), a full duplicate-on-confirm test
        (with real note/instr data proving the copy), a leave-unchanged-on-
        cancel test, and three `Songswitch4_8` tests (cancel leaves state
        untouched, confirmed 8→4 clears the R1-R4 columns, confirmed 4→8
        switches). No test needed to reset `SetTestQuestionAnswer()`'s
        default between tests - every test either never triggers a confirm
        prompt or explicitly sets the answer first, so there's no
        cross-test ordering dependency.
      - Full solution rebuild (Release|x64) confirmed 0 errors; 250 tests
        pass (up from 243, +7, 0 regressions).
