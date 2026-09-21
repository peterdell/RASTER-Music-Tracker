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
- [x] Phase 2 continued: `CSong` investigated and deliberately deferred; `CSAPFile`
      tested instead, 78 tests total, committed (`3a6e1b9`).
- [x] Phase 2 continued: `Keyboard2NoteMapping` + `CASMFileBuilder` tests (complete,
      including `BuildSongData`), 93 tests total, committed (`1c3e20b`, `3d84de4`).
- [x] Phase 2 continued: fresh `Global.h`-free survey → `ChannelControl` +
      `RmtCommandLineInfo` tests, 107 tests total. Not yet committed.
- [ ] Ask user whether to commit this step.
- [ ] Phase 2 continued: more characterization tests before any cleanup. The easy,
      zero-`CSong`, zero-`Global.h` candidates are now largely exhausted (see the
      `LZSSFile`/`SongExport`/`Shell`/`WaveFile` notes above for why those specific
      ones are out). Next time, either:
      - Do another fresh `grep -L '"Global.h"' src/cpp/*.cpp` survey pass (some
        `Global.h`-having files may still have a clean, splittable seam like
        `Tuning`/`Tracks`/`Instruments` did — don't assume presence of `Global.h`
        alone rules a file out), or
      - Revisit whether it's time to tackle `CSong`'s constructor coupling
        deliberately (a real decoupling task, not a quick split) — this would also
        unblock `LZSSFile`, `SongExport`, and `SongContainer`, all currently blocked
        on it, plus `CSong`'s own `SongToAta`/`AtaToSong` (confirmed pure back when
        `CSong` was first investigated).
      - `ASMFile.cpp` is only 2 lines (essentially empty) — confirm there's nothing
        there before spending time on it.
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
