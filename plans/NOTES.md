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
- [x] Wrote a dedicated triage plan for the above (`plans/01_SONG_IO_SONG_REMAINING_PLAN.md`,
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
        `plans/01_SONG_IO_SONG_REMAINING_PLAN.md`), `LoadRMW`/`LoadTxt` (blocked
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
      own `plans/02_EXPORTV2_PLAN.md`, mirroring this doc's own structure.
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
        `plans/02_EXPORTV2_PLAN.md` for the full per-method breakdown and
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
        stale `g_PrefixForAllAsmLabels` test stub (per `02_EXPORTV2_PLAN.md`'s
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
      `plans/05_SAP_LZSS_WAV_XEX_PLAN.md`; highlights below.
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
      - This completes `plans/05_SAP_LZSS_WAV_XEX_PLAN.md`'s actively-pursued
        scope. `ExportWAV` (real `CXPokey` audio synthesis) and
        `ExportLZSS`/`ExportCompactLZSS` (real multi-file disk writes,
        self-described as "hacked up"/"currently unused") remain
        deliberately deferred per that plan's findings.
- [x] Re-verified the `FileXxx` family (Batch 7,
      `plans/01_SONG_IO_SONG_REMAINING_PLAN.md`) - the last remaining
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
      `plans/01_SONG_IO_SONG_REMAINING_PLAN.md`'s Batch 7 section for the
      full per-method writeup).
- [x] Broader survey of every other production `.cpp` file not yet linked
      into `RmtTests.vcxproj` (~55 files) - see `plans/04_BROADER_SURVEY_PLAN.md`.
      Most are confirmed out of scope (real UI/dialogs/views, real hardware/
      DLL coupling - verified `RmtMidi.cpp` genuinely calls
      `midiInGetNumDevs()`/`midiInGetDevCaps()` - `Global.cpp`'s mega-wiring,
      already-solved-shape remnants, or effectively empty). Four new
      candidates found: `IO_Importer.cpp` (`ImportTMC`/`ImportMOD`, highest
      value), `Undo.cpp`, and two small/cheap wins (`Instruments.cpp`'s and
      `AtariTrackerDriver.cpp`'s remainders).
- [x] `IO_Importer.cpp` Batch A: `CSong::ImportTMC` unlocked - see
      `plans/03_IO_IMPORTER_PLAN.md`.
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
        `plans/01_SONG_IO_SONG_REMAINING_PLAN.md`; the comment was just never
        updated when that changed.
      - Full solution rebuild (Release|x64) confirmed 0 errors; 236 tests
        pass (up from 233, +3, 0 regressions).
- [x] `IO_Importer.cpp` Batch B: `CSong::ImportMOD` unlocked - completes the
      `IO_Importer.cpp` triage. See `plans/03_IO_IMPORTER_PLAN.md`.
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
      characterization batch. See `plans/08_MESSAGEBOX_REFACTOR_PLAN.md` for
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
      migration. See `plans/08_MESSAGEBOX_REFACTOR_PLAN.md`.
      - **Batch 3**: `IO_Song.cpp`'s 18 fire-and-forget sites. Pure
        consistency (that file stays deferred per Batch 7 of
        `plans/01_SONG_IO_SONG_REMAINING_PLAN.md`).
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
        `SongEditing.cpp`, revisiting `plans/01_SONG_IO_SONG_REMAINING_PLAN.md`'s
        "defer both entirely" decision.
      - **Batch 5**: the remaining hardware/DLL-coupled files (`C6502.cpp`
        x2, `Pokey.cpp` x3, `PokeyRenderer.cpp` x5, `RmtMidi.cpp` x1),
        confirmed permanently out of scope for testing but migrated anyway
        for full consistency, per the user's "do all of these" request.
      - **Zero `MessageBox(g_hwnd, ...)` call sites remain anywhere in the
        codebase** outside `Messages.cpp`'s own real implementation - the
        whole refactor described in `plans/08_MESSAGEBOX_REFACTOR_PLAN.md` is
        complete.
      - Full solution rebuild (Release|x64) confirmed 0 errors; 243 tests
        pass (unchanged - none of Batches 3-5's files are linked into the
        test project - 0 regressions).
- [x] `SongMaketracksduplicate`/`Songswitch4_8` unlocked - the follow-up the
      MessageBox refactor was flagged as enabling. Both were deferred
      indefinitely by `plans/01_SONG_IO_SONG_REMAINING_PLAN.md`'s "Decisions
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
- [x] New `plans/CPP_RULES.md` (user-requested, style rules going forward) with
      its first rule: always brace `if`/`else`/`for`/`while`/`do` bodies,
      even single-statement ones (the classic "goto fail"-style bug class).
      Also set up and applied real enforcement, not just documentation:
      - **`.clang-tidy`** at repo root, scoped to exactly two checks
        (`readability-braces-around-statements`,
        `readability-misleading-indentation`) - this project doesn't use
        clang-tidy for anything broader.
      - **`EnableClangTidyCodeAnalysis`/`ClangTidyChecks`** set in both
        `Rmt.vcxproj` and `RmtTests.vcxproj`, enabling Visual Studio's
        native Clang-Tidy integration (live editor squiggles + *Run Code
        Analysis*). Verified via MSBuild's own `.props`/`.targets` files
        (not assumed) that this is gated on the separate `RunCppAnalysis`
        property and does **not** run during a normal Build/Rebuild -
        confirmed empirically too (unchanged build time after enabling).
        `ClangTidyChecks` must mirror `.clang-tidy`'s check list by hand,
        since Visual Studio's integration always passes its own
        `-checks=` argument, overriding the file.
      - **One-time bulk `--fix` pass** across the whole codebase (~112 of
        120 `.cpp` files - 8 pure-MFC-UI files excluded, see below).
        Non-trivial to get right - two real problems found and fixed along
        the way:
        1. First attempt used no `--header-filter`, which - contrary to
           what the flag's absence suggests - let clang-tidy modify
           *included headers* too, not just the file passed on the command
           line. This reached into and modified vendored **GoogleTest
           headers** (`test/googletest/include/gtest/*.h`) before being
           caught. Fully reverted (`git checkout` - working tree was clean
           beforehand, confirmed via `git status` first, so the revert was
           exact) and redone with `--header-filter='^$'` (matches nothing),
           confirmed to leave every header untouched on the redo.
        2. `clang-tidy --fix` inserts braces in a minimal/inline style
           (`if (cond) { stmt;\n}`), not this codebase's Allman convention
           - applying it as-is would have left ~1000+ lines inconsistently
           formatted (218 violations in `SongEditing.cpp` alone). Reverted
           again and re-applied with `git-clang-format` layered on top
           (formats only the lines `clang-tidy` had just changed, per the
           git diff - never a whole-file reformat, which matters here
           since the codebase mixes tab-indented legacy files and
           space-indented newer ones; each file's dominant indent
           character was detected and matched). First `git-clang-format`
           style attempt also added unwanted spaces before *function-call*
           parens (`SpaceBeforeParens: Always` applies to calls too, not
           just control statements) - fixed to
           `SpaceBeforeParens: ControlStatements`.
      - **8 files excluded from the automated fix**: `MainFrm.cpp`,
        `OptionsDialog.cpp`, `Rmt.cpp`, `RmtView.cpp`, `TuningDialog.cpp`,
        `effectsdlg.cpp`, `exportdlgs.cpp`, `importdlgs.cpp`. All use an
        old-style MFC message-map macro (bare `ClassName::Method` instead
        of `&ClassName::Method`) that Clang's parser rejects outright as a
        hard error (confirmed `-fms-extensions` doesn't help) - unrelated
        to braces, but blocks clang-tidy from processing these files at
        all. Flagged in `plans/CPP_RULES.md` as a known gap needing manual
        attention later.
      - Verified zero remaining `readability-braces-around-statements`
        violations across every processed file (re-ran clang-tidy
        read-only after the fix). Full solution rebuild (Release|x64)
        confirmed 0 errors; 250 tests pass (unchanged - purely syntactic
        change, 0 regressions), confirming the bulk fix altered no
        behavior.
- [x] Corrected the brace-placement rule and its enforcement to full K&R
      style (user feedback: the first pass left function/method/class
      bodies in Allman while only flipping control statements - both
      should use the same brace style, matching Java's convention rather
      than mixing two styles in one codebase):
      - `plans/CPP_RULES.md` updated: the rule text no longer carves out
        function/class/struct/enum bodies as an exception, and the
        `BraceWrapping` enforcement details now list every `After*` key as
        `false` (function, class, struct, enum, namespace), not just
        `AfterControlStatement`.
      - Previewed on `TuningTables.cpp` first (as requested) via a plain
        whole-file `clang-format -i` (not `git-clang-format`, since nearly
        every brace in the file needed moving - a diff-scoped pass would
        have missed most of them) using the same anti-side-effect flags as
        the original bulk fix (`PointerAlignment: Left`,
        `DerivePointerAlignment: false`, `SortIncludes: false`,
        `AlignTrailingComments: false`, `IndentCaseLabels: false`,
        `SpaceBeforeParens: ControlStatements`, `ReflowComments: false`).
      - **New gap found**: clang-format refuses to move a brace past an
        existing end-of-line comment on the header line (e.g.
        `if (cond) //comment` stays with `{` on the next line) - it won't
        reorder the comment relative to the code that follows. Fixed by a
        small Python pass (not clang-format) that merges each such
        two-line pattern into `if (cond) { //comment`, restricted to lines
        whose code portion ends in `)` or `else` (to avoid touching
        commented-out dead code, bare scoping blocks, and array/aggregate
        initializers, which all also end in a lone `{` line but are out of
        this rule's scope and were intentionally left in their existing
        style - confirmed by inspecting every non-matching case by hand
        before running the merge).
      - Rolled out to the rest of the codebase after the one-file preview
        was confirmed clean and tests passed: same 111 files as the
        original bulk fix (120 total `.cpp` files under `src/cpp` and
        `src/cpp/test`, minus the 8 excluded pure-MFC files, minus
        `TuningTables.cpp` already done), split by each file's dominant
        tab/space convention (19 tab files, 92 space files) exactly as
        before. 130 trailing-comment cases needed the manual merge; 29
        remaining lone-`{` lines were confirmed out of scope (dead code
        inside `/* */` block comments, bare `{ }` scoping blocks used for
        local-variable lifetime inside a function/case body, and
        array/struct initializer braces) and left untouched.
      - Full solution rebuild (both `Rmt.exe` and `RmtTests.exe`,
        Release|x64) confirmed 0 errors; 250 tests pass, 0 regressions -
        confirming this second pass, like the first, was purely syntactic.
- [x] Extended the K&R brace-style rule to `.h` files (user question: the
      .cpp rollout had split files into tiers for tooling reasons - would
      headers get the same treatment? Answer: they'd been skipped
      entirely so far, not deliberately excluded - `.clang-tidy`'s
      `HeaderFilterRegex: '^$'` only meant "don't reach into headers
      transitively from a .cpp", never "don't check a header directly").
      84 headers surveyed under `src/cpp` (no vendored/third-party headers
      live there - `asap/` and `test/googletest/` are separate
      subdirectories, correctly never touched). None hit the 8-file
      MFC-macro parse hazard that blocked some `.cpp` files, since
      `ON_COMMAND` message maps live in `.cpp` files, not headers.
      - Getting `clang-tidy`/`clang-format` to parse a header standalone
        needed `/TP` (force C++ mode - `cl` infers C from the `.h`
        extension otherwise) and `/FIStdAfx.h` (force-include the
        project's precompiled header), since headers routinely assume
        MFC/CRT types are already visible via the project's PCH include
        order rather than including everything they use themselves. Two
        headers (`PokeyController.h`, `PokeyStream.h`) needed additional
        `/FIMemory.h`/`/FIostream` force-includes for the same reason.
        `Memory.h` and `StdAfx.h` themselves needed their own
        force-include list to exclude force-including themselves - doing
        so duplicates their content (a `#pragma once` guard has no effect
        on a file being force-included into itself as its own primary
        translation unit, unlike a normal `#include`).
      - `clang-tidy --fix` found exactly 6 real brace-around-statements
        violations (`Song.h` x4, `Tracks.h` x2 - one-line accessors like
        `BOOL OctaveUp() { if (...) { ...; return 1; } else return 0; };`
        missing braces on the `else`).
      - **Two new side effects found and fixed, both header-specific**
        (neither ever surfaced during the `.cpp` passes because `.cpp`
        files essentially never contain the patterns that trigger them -
        confirmed by grepping the already-committed `.cpp` history for
        both before accepting this explanation rather than assuming it):
        1. `AllowShortFunctionsOnASingleLine: None` (inherited from the
           `.cpp` style) force-expanded every already-compliant one-line
           inline accessor (`BOOL Undo() { return g_Undo.Undo(); };`) into
           3 lines, even though the brace was already on the same line -
           pure scope creep unrelated to brace placement, and it was most
           of the diff on the largest-changed headers (`Song.h`,
           `Tracks.h`). Fixed by using `AllowShortFunctionsOnASingleLine:
           InlineOnly` for headers specifically (keeps existing one-liners
           as one-liners; still splits ones that need real brace fixes,
           like the 6 above).
        2. LLVM's default half-indent for `public:`/`private:`/
           `protected:` access specifiers doesn't match this codebase's
           convention of keeping them at column 0 (same column as the
           class's own brace) - first pass reindented ~63 headers' access
           specifiers. Fixed with `AccessModifierOffset: -4`.
      - `resource.h` (Visual-Studio-auto-generated `#define` list, zero
        braces) was caught and excluded after its first pass produced an
        886-line diff that was pure `#define`-value column-realignment -
        nothing to do with braces at all.
      - Same 3 manual struct-header fixes as the `.cpp` pass needed
        (`InstrumentTypes.h`, `SongTypes.h`, `TracksTypes.h` - a
        trailing-comment-on-the-struct-line case clang-format won't merge
        on its own); one array-initializer brace in `Tuning.h` correctly
        left alone as out of scope, same reasoning as the `.cpp` pass.
      - Full solution rebuild (`Rmt.exe` + `RmtTests.exe`, Release|x64)
        confirmed 0 errors; 250 tests pass, 0 regressions.
- [x] Wrote `plans/07_FILE_TIERING_STRATEGY.md` (user question: after the `.h`
      brace-style rollout, would headers also get split into tiers like
      `SongCore.cpp`/`SongEditing.cpp`/etc.?). Explained that the `.cpp`
      tiers are a per-class implementation split by runtime hazard (does a
      method call `MessageBox`/timers/hardware?), not a header split -
      confirmed by checking the actual class declarations: all ten of
      `CSong`'s `.cpp` files (`Song.cpp`, `SongCore.cpp`,
      `SongEditing.cpp`, `Song_DumpSong.cpp`, `SongExportV2.cpp`,
      `IO_Song.cpp`, `IO_Importer.cpp`, `IO_ImporterCore.cpp`,
      `GUI_Song.cpp`, `Midi_Song.cpp` - ~9,950 lines, ~151 methods) share
      the single `Song.h`, and the same holds for `CTracks`/`Tracks.h` and
      `CInstruments`/`Instruments.h`. `SongUI.h`/`SongTimer.h`/
      `SongContainer.h`/`SongExporter.h` are not `CSong` tiers at all -
      they declare genuinely separate classes that merely share the
      "Song" name prefix. Answer: headers are never tiered, since a
      declaration carries no runtime hazard regardless of which tier its
      implementation lands in.
      - Follow-up question: how does this `.cpp`-tier mechanism translate
        when the Java port actually happens, given Java has no header/impl
        split and no partial classes? Recorded two options in the same
        plan doc as an open decision to revisit when the port begins,
        rather than deciding now: (1) mechanically recombine each class's
        tiers back into one large Java file per class, using the former
        tier boundaries only as section comments; (2) use the tier
        boundaries as seams for real composition instead - extract the
        hazardous dependencies (dialogs, timers, hardware) behind
        interfaces (`UserPrompt`, `AudioClock`, `SoundDevice`, ...) that
        the core class depends on via injection, turning
        `GUI_Song.cpp`/`Midi_Song.cpp` into their own classes that depend
        on `Song` rather than hazard-tiers of it. Noted that either way,
        this session's dual-mode refactors (`SendQuestionMessage()`'s
        test-injectable answer, `InstrInfo`/`TrackInfo`'s optional
        output-struct parameter) already preview option 2's seam design,
        so that triage work carries over regardless of which option is
        chosen later.
- [x] Wrote `plans/06_DUAL_MODE_PATTERN_PLAN.md`: formalized the dual-mode
      pattern's three established variants (optional output-parameter,
      test-injectable answer, two-phase parse+apply), then audited every
      remaining `Send<Type>Message()` call site in the codebase (~90)
      against the existing triage docs before proposing any new batch.
      **Corrected a mistake found during that audit**: an earlier draft of
      `plans/07_FILE_TIERING_STRATEGY.md`'s recommendation had implied
      `GUI_Song.cpp`/`Midi_Song.cpp` were untapped dual-mode candidates -
      cross-checking `plans/04_BROADER_SURVEY_PLAN.md` showed both were
      already investigated and confirmed genuinely hazardous (the real
      keyboard-input dispatch layer; real MIDI hardware enumeration), with
      no extractable logic. Fixed that section in place rather than
      leaving it to mislead a future session. Conclusion: **the dual-mode
      backlog is closed** - every remaining hazard is either already
      dual-mode'd, guard-only (no split needed), a real dialog wrapper
      with its core already extracted, or genuinely real UI/hardware
      coupling. The one loose thread the audit turned up -
      `Undo.cpp`/`CUndo`, not yet investigated per
      `plans/04_BROADER_SURVEY_PLAN.md` - isn't itself dual-mode-shaped (its
      `g_hwnd` uses are guard-only); flagged as a decision point rather
      than started unilaterally.
- [x] User chose to open `Undo.cpp`/`CUndo` as a new investigation. Wrote
      `plans/09_UNDO_PLAN.md` after reading `Undo.cpp`/`Undo.h`/
      `test/UndoStub.cpp` in full. Key findings:
      - 8 of `CUndo`'s 16 methods (`Init`/`Clear`/`DeleteEvent`/
        `GetUndoSteps`/`GetRedoSteps`/`DropLast`/`Separator`/`PosIsEqual`)
        are already real, copied verbatim into `UndoStub.cpp` (confirmed
        identical) since they touch no globals - just never given direct
        characterization tests.
      - The other 8's real dependencies (`g_Song`/`g_Tracks`/
        `g_Instruments`/`g_TrackClipboard`/`g_activepart`/`g_changes`) are
        now all real and already reset every test in
        `SongEditingTest::SetUp()` - the stale premise that `CUndo` was
        "downstream of the `CSong` split work" no longer holds now that
        split is done.
      - One real, not-yet-exercised hazard found: `InsertEvent()` calls
        `g_Song.SetRMTTitle()` (`GUI_Song.cpp`, confirmed real UI) only on
        the first change (`if (!g_changes)`), and that method
        unconditionally calls `AfxGetApp()->GetMainWnd()` before its one
        null-check (which only guards the *result*) - `RmtTests.exe` never
        constructs a `CWinApp`, so `AfxGetApp()` returns MFC's default-null
        pointer and this would likely crash. **Deliberately not verified
        empirically**, matching this effort's established caution around
        exactly this class of hazard (`plans/01_SONG_IO_SONG_REMAINING_PLAN.md`
        Batch 6's timeout-guarded `CSongTimer` verification) - avoided
        instead via a documented precondition (tests set `g_changes = 1`
        before calling any `Change*()` method), the same treatment already
        used for `Stop()`'s `m_play` precondition.
      - Found a genuine `new`/`delete[]` mismatch: `DeleteEvent()` always
        frees `TUndoEvent::data` with `delete[]`, but 5 of 11 `UndoType`
        cases allocate it with scalar `new` (`new TTrack`/`new TTracksAll`/
        `new TSong`/`new TInstrument`/`new TInstrumentsAll`) - the same bug
        class already found and fixed in `CTracks`/`CInstruments`. Per this
        effort's established policy, flagged to be fixed outright as part
        of the implementation batch, not just characterized.
      - Proposed two batches (characterize the 8 already-real bookkeeping
        methods directly; then fix the `delete[]` bug, link `Undo.cpp`
        for real in place of `UndoStub.cpp`'s remaining no-ops, and add
        tests for every `UndoType` branch) - not yet implemented, this
        was investigation/planning only, per this effort's "write the
        plan before touching code" discipline for non-mechanical work.
- [x] Implemented `plans/09_UNDO_PLAN.md` (user: "Implement"), as one combined
      pass rather than two batches (the split had no independent value -
      see the plan doc's own note). `Undo.h`'s `TUndoEvent` gets a new
      `bool dataIsArray = false;` member fixing the `new`/`delete[]`
      mismatch; `Undo.cpp`'s `DeleteEvent()` branches on it, and every
      array-`new` call site sets it `true` (the scalar-`new` sites
      correctly rely on the struct's `false` default). `Undo.cpp` is now
      linked directly into `RmtTests.vcxproj`; `test/UndoStub.cpp` is
      trimmed to just the `CUndo g_Undo;` global (all 16 methods are real
      now). One new link-only stub needed:
      `void CSong::SetRMTTitle() {}` in `SongEditingStub.cpp` -
      `InsertEvent()` references it unconditionally even though
      `UndoTests.cpp` never reaches it at runtime (tests pre-set
      `g_changes = 1`, matching plan finding #3's documented precondition
      to avoid `GUI_Song.cpp`'s real, crash-risking `AfxGetApp()` call).
      New `test/UndoTests.cpp`: 30 tests covering all 8 bookkeeping methods
      (including a cast out-of-range `UndoType` to reach `PosIsEqual`'s
      128-191 group, which no real enum value populates), every
      `Change*()` `UndoType` branch swapped correctly by `Undo()`/`Redo()`
      in both directions, each `BAD!` guard branch characterized as
      non-crashing, multi-step undo history, the `separator = 0`
      coalescing behavior (confirmed it keeps only the first snapshot,
      matching real "type several notes in a row" behavior), and
      `Clear`/`Init`/`DropLast`. Also fixed a now-stale comment in
      `SongEditingTests.cpp` claiming `g_Undo`'s `ChangeTrack`/
      `ChangeSong` were still stubbed. Full solution rebuild (`Rmt.exe` +
      `RmtTests.exe`, Release|x64) confirmed 0 errors; 280 tests pass (up
      from 250, +30, 0 regressions). Committed (`9515376`).
- [x] Implemented `plans/04_BROADER_SURVEY_PLAN.md`'s `Instruments.cpp`
      remainder candidate (user: "yes" to opening it, after asking "what
      pieces are next?" and being given the priority-ordered list of
      everything still open across every plan doc). Confirmed the survey's
      predictions by reading `Instruments.cpp` in full: `ClearInstrument`/
      `SetEnvelopeVolume`/`MemorizeOctaveAndVolume`/
      `RememberOctaveAndVolume` only need `g_AtariTrackerDriver`
      (real, `AtariTrackerDriverCore.cpp`), `g_tracks4_8`/
      `g_keyboard_RememberOctavesAndVolumes` (both already real globals),
      and `Update()` (real since Batch 3) - nothing hazardous left. Linked
      `Instruments.cpp` directly into `RmtTests.vcxproj`; trimmed
      `test/InstrumentsStub.cpp`'s 3 no-op stub bodies (kept only the
      still-needed `g_tracks4_8` storage). Added `InstrumentsCoreTest` (11
      tests) to `test/InstrumentsTests.cpp`, testing each method's real
      behavior directly on a local `CInstruments` instance (no need to
      touch the real `g_Instruments` global - `Update()`/
      `InstrumentTurnOff()` both operate via `this`/an unrelated global,
      not `g_Instruments` specifically).
      - **Found (initially did not fix) a real, pre-existing quirk while
        writing tests**: `CInstruments::CInstruments()` allocates `m_instr`
        with plain `new TInstrument[INSTRSNUM]` - no zero-initialization,
        so a freshly-constructed, never-`ClearInstrument()`-ed
        instrument's fields are indeterminate. First surfaced as 3
        flaky-looking test failures (stack/heap memory reuse between
        successive test fixtures made some runs "accidentally" see
        zeroed memory and pass, others see a previous test's leftover
        values). Confirmed this is the exact same shape as
        `CTracks::m_track`'s allocation (also plain `new[]`, also never
        fixed) - not a new bug class, and this project already
        established the precedent of leaving it alone since
        `InitTracks()`/`InitInstruments()` are always called before real
        use in both production and every existing test fixture. Initially
        kept consistent with that precedent: fixed the *tests* (explicitly
        set a known baseline before asserting on a field the test itself
        never previously touched) rather than the allocation - see the
        follow-up entry below where the user asked for the allocation
        itself to be fixed after all.
      - Full solution rebuild (`Rmt.exe` + `RmtTests.exe`, Release|x64)
        confirmed 0 errors; 291 tests pass (up from 280, +11, 0
        regressions). Committed (`5fed437`).
- [x] User explicitly asked to fix `CInstruments`'s constructor after all
      (overriding the "leave it consistent with the `CTracks` precedent"
      call made above), so `InstrumentsCore.cpp`'s
      `m_instr = new TInstrument[INSTRSNUM];` became
      `m_instr = new TInstrument[INSTRSNUM]();` (value-initialization -
      zeros every element, valid since `TInstrument` is a plain aggregate
      with no constructor of its own). Removed the 3 tests' now-redundant
      explicit "known baseline" lines added in the previous entry, since
      the constructor now establishes that baseline itself. Scoped to
      `CInstruments` only, as asked - `CTracks::m_track`'s identical-shape
      allocation was deliberately left untouched, not fixed opportunistically.
      Full solution rebuild (`Rmt.exe` + `RmtTests.exe`, Release|x64)
      confirmed 0 errors; 291 tests pass, 0 regressions (purely an
      initialization fix - no test needed to change its assertions, only
      the redundant setup lines were removable). Committed (`a180186`).
- [x] Implemented `plans/04_BROADER_SURVEY_PLAN.md`'s `AtariTrackerDriver.cpp`
      remainder candidate (user: "Continue", after the prior "what pieces
      are next?" answer named it as the next priority item). Confirmed by
      reading `AtariTrackerDriver.cpp` in full: `Init()`/`SetPokey()`/
      `Silence()` only call the already-stubbed no-op `m_atari->JSR()`;
      `Play()` additionally needs `IsSpecialProveMode()` (`Global.h`),
      confirmed trivial (`return g_prove == MIDI_CH15_MODE ||
      g_prove == POKEY_EXPLORER_MODE;`, `g_prove` already a real global)
      and copied verbatim into `SongEditingStub.cpp`, matching its
      existing `SetEditMode()` precedent; `LoadRMTRoutines()` needs
      `CRmtAtariBinaries::GetTrackerDriverBinary()`
      (`AtariBinaries.cpp`, already linked for `ExportSAP_B_LZSS`/
      `ExportXEX_LZSS`) and `CAtariIO::LoadDataAsBinaryFile()`
      (`AtariIO.cpp`, already linked, pure in-memory parsing like the
      already-safe `LoadBinaryBlock`) - both real dependencies already
      satisfied by prior work, and the matching
      `rmt/resources/drivers/rmt_driver_v6.obx` (the default `PATCH16`
      driver) is already checked into the repo. Linked
      `AtariTrackerDriver.cpp` directly (`RmtTests.vcxproj`); removed its
      now-redundant `Init()`/`Play()` no-op stubs from
      `test/PokeyStreamStub.cpp`. New `test/AtariTrackerDriverTests.cpp`:
      7 tests, using locally-constructed `CAtari`/`CAtariTrackerDriver`
      instances (both cheap, matching `AtariTests.cpp`'s own precedent)
      rather than the shared globals, for full isolation.
      - **Found and fixed a real bug while writing the "unknown driver
        version returns 0" guard test**: it kept returning the *default*
        driver's byte count regardless of which (nonexistent) version was
        requested. Traced to `LoadRMTRoutines()` passing the global
        `g_trackerDriverVersion` to `GetTrackerDriverBinary()` instead of
        its own `trackerDriverVersion` parameter - the parameter was
        entirely dead code. Confirmed harmless in production (`Rmt.cpp`/
        `RmtView.cpp`, the only two real call sites, always pass
        `g_trackerDriverVersion` as the argument anyway, so the bug never
        produced a wrong driver load in practice) before fixing it
        outright, matching this effort's policy for real, safe,
        zero-observable-behavior-change fixes. Removed the now-unused
        `g_trackerDriverVersion` test-project global this fix made
        unnecessary (added, then removed again in the same session, once
        the real fix meant it was never actually needed).
      - Full solution rebuild (`Rmt.exe` + `RmtTests.exe`, Release|x64)
        confirmed 0 errors; 298 tests pass (up from 291, +7, 0
        regressions). Committed (`fb40226`).
- [x] Implemented `plans/11_EXPORTWAV_PLAN.md` (user: "Continue with
      ExportWAV", the last remaining `ExportV2` Tier 2 family member).
      Re-investigated from scratch rather than trusting
      `plans/05_SAP_LZSS_WAV_XEX_PLAN.md`'s "genuinely hazardous, needs its
      own investigation" note: `ExportWAV` calls `CXPokey::RenderSoundV2()`,
      not the DirectSound-heavy `RenderSound1_50()` - `RenderSoundV2()`
      only drives `CPokey`, whose `GetSoundDriver()` defaults to `NONE`
      until `CPokey::InitSound()` explicitly `LoadLibrary()`s a POKEY DLL
      (never called in tests), so every switch on it safely no-ops, same
      shape as `CSongTimer`'s `m_timerRoutine` guard. `CWaveFile`'s
      `mmioOpen`/`mmioCreateChunk`/etc. are pure RIFF file I/O, not
      hardware - needs `winmm.lib` (a real production dependency), not
      DirectSound.
      - **Presented a proposed fix, then simplified it per user
        feedback**: `CXPokey`'s constructor left every member
        uninitialized (including the `WAVEFORMATEX` `GetSoundFormat()`
        returns, and the `bool stereo` `CopyAtariMemoryToPokey()`
        branches on). Initially proposed giving `m_SoundFormat`
        "realistic" 44100Hz/8-bit/stereo defaults matching `InitSound()`'s
        own hardcoded call; the user asked "wouldn't it suffice to
        initialize all members of `CXPokey` to the initial/zero values?" -
        simpler, matches the existing `CTracks::m_track`/
        `CInstruments::m_instr` precedent exactly, and a degenerate
        all-zero `WAVEFORMATEX` doesn't crash `mmioCreateChunk` (which
        doesn't validate format sanity). Implemented that way instead:
        default member initializers on every field in `PokeyRenderer.h`.
      - **Two more `*Core.cpp` splits needed** to link `ExportWAV`'s safe
        half without pulling in the real hazards, mirroring every prior
        split in this effort: `Pokey.cpp`/`PokeyCore.cpp` (constructor/
        destructor/bookkeeping methods + the 11 `APokeySound_*`/`Pokey_*`
        function-pointer globals move to Core; only `InitSound()`/
        `InitPokeyDll()` - the real `LoadLibrary()` calls - stay behind)
        and `PokeyRenderer.cpp`/`PokeyRendererCore.cpp` (constructor/
        destructor/`RenderSoundV2`/etc. move to Core, including
        `g_lpds`/`g_lpdsbPrimary` - changed from file-`static` to plain
        externs since both the Core destructor and the remainder's
        `InitSoundInternal()` need them; only the actual DirectSound-
        touching methods stay behind). Both new files added to
        `Rmt.vcxproj` too, so production keeps 100% of the original
        functionality across more files.
      - Checked GitHub issue #10 ("Export as WAV does not work with
        Altirra runtime libraries") before proceeding, since
        `SongExporterTest.cpp` (a hand-run developer utility, not part of
        the GoogleTest suite - hardcoded to the original author's own
        desktop paths) has `WAV = false;` with a matching TODO comment.
        Confirmed the known bug is specific to a real POKEY DLL being
        hijacked by the Altirra emulator's own audio hook - unrelated to
        and unaffected by this characterization, which never loads any
        POKEY DLL at all.
      - New test in `test/SongEditingTests.cpp`:
        `ExportWAVWritesAValidRiffWaveHeaderWhenNoPokeyDriverIsLoaded` -
        this suite's first real file *write* (as opposed to
        `ExportSAP_B_LZSS`/`ExportXEX_LZSS`'s real file *reads*), since
        `CWaveFile::OpenFile()`'s `mmioOpen()` is hardcoded to a real
        on-disk path with no `std::ostream`-widening escape hatch like
        the other exporters have. Written to the OS temp directory,
        verified (`RIFF`/`WAVE` header bytes), then deleted.
      - Verified incrementally with an explicit timeout given the
        audio/file-I/O-adjacent hazard class (same caution as Batch 6's
        `CSongTimer` verification): the new test alone first, then the
        full suite - no hangs, no crashes.
      - Full solution rebuild (`Rmt.exe` + `RmtTests.exe`, Release|x64)
        confirmed 0 errors; 299 tests pass (up from 298, +1, 0
        regressions). This closes out `plans/02_EXPORTV2_PLAN.md`'s Tier 2
        family entirely except the deliberately-deferred, low-value
        `ExportLZSS`/`ExportCompactLZSS`. Committed (`2beb129`).
- [x] Implemented `plans/10_EXPORTLZSS_PLAN.md` (user: "open
      ExportLZSS/ExportCompactLZSS", the last two deliberately-deferred
      items in the entire `ExportV2` family). Mechanical part: both
      methods were already dialog-free, just stuck in `SongExporter.cpp`
      alongside the real dialog-showing methods - moved both into the
      already-linked `SongExporterCore.cpp` (same split already used for
      `ExportXEX_LZSS`), needing only `#include <iomanip>` for `PADHEX`/
      `PADDEC`'s `std::setfill`/`std::setw`.
      - **Real finding that changed the test design mid-way**: tried
        progressively more elaborate test songs (more songlines, higher
        per-frame note/instrument/volume entropy, an explicit
        intro-then-loop structure to make `GetThirdCountPoint()`
        non-zero) trying to get `ExportLZSS`'s "> 16 compressed bytes"
        guards to trigger - none worked, confirmed via a temporary
        diagnostic print of `GetFirstCountPoint`/`GetSecondCountPoint`/
        `GetThirdCountPoint` plus the LZSS compressor's own debug output
        ("stream #0 is empty" on every attempt, regardless of song
        content). Root cause: no test song's note/instrument content can
        ever change the recorded `CPokeyStream` bytes, in this or any
        other test in this suite - the actual "note -> POKEY register
        write" translation happens inside the real RMT 6502 driver
        routines, executed via `C6502::JSR()`, a no-op stub throughout
        this entire project. Rewrote the test to characterize this
        deterministic outcome directly (the caller's own path ends up
        empty, `_INTRO.lzss`/`_LOOP.lzss` never get created) instead of
        continuing to chase a byte count no test song here can produce -
        removed the diagnostic print before finalizing.
      - Also found a trivial test-writing mistake (not a production bug):
        `ExportCompactLZSS`'s test initially searched its log output for
        `"Index: 00"`, but `PADHEX` (`General.h`) prepends `"0x"`, so the
        real text is `"Index: 0x00"`.
      - Confirmed `ExportCompactLZSS`'s second `while` loop
        (`// I don't know anymore, at this point...`) is genuinely dead
        code (empty body, no side effects) by reading it directly - left
        alone, out of scope.
      - Verified incrementally with an explicit timeout given the
        real-file-write hazard class (same posture as every prior test in
        this family): both new tests alone first, then the full suite -
        no hangs. Full solution rebuild (`Rmt.exe` + `RmtTests.exe`,
        Release|x64) confirmed 0 errors; 301 tests pass (up from 299, +2,
        0 regressions). This closes `plans/02_EXPORTV2_PLAN.md`'s Tier 2
        family entirely - nothing from that scope remains deferred.
        Committed (`9a0f494`).
- [x] With the characterization-testing phase essentially exhausted (per
      `plans/04_BROADER_SURVEY_PLAN.md`'s own "nothing else worth pursuing"
      conclusion), asked "what's next?" - the user pointed out that the
      Java port can't be planned without first understanding the current
      UI, since the ported app needs to match today's Windows UI, not just
      the model classes. Forked a read-only investigation
      (`plans/12_UI_SURVEY_PLAN.md`, ~311 lines): confirmed `CRmtDoc` is
      explicitly unused (its own comment says so) - the real architecture
      is global objects (`g_Song` etc.) plus one `CRmtView` controller/
      view class, not real MVC; rendering is a single off-screen GDI
      bitmap redrawn every 16ms and `StretchBlt`'d to screen, with all
      text/graphics as a hand-rolled bitmap font blitted from one sprite
      sheet (`IDB_GFX`) rather than real GDI text; the keyboard model is a
      clean `Part` x `EditMode` dispatch; ~25 real dialogs were inventoried
      and cross-referenced against what characterization testing already
      established about their `*Apply()`-core splits (confirming
      `plans/07_FILE_TIERING_STRATEGY.md`'s composition option has genuine
      seams to use). Also answered a follow-up factual question (Java's
      `javax.sound.midi` vs. RMT's raw `mmsystem.h` MIDI code - concluded
      Java's API is simpler for the I/O plumbing itself, but the real
      complexity is `Midi_Song.cpp`'s ~600 lines of dispatch logic, which
      the code's own comments call "very terrible... will eventually be
      replaced").
      - Asked the three open questions the survey couldn't resolve on its
        own initiative, with the user's answers:
        1. **Visual style**: keep the exact pixelated bitmap-font look
           (not modernize) - the `IDB_GFX` sheet will need extracting to a
           plain image file for the Java port.
        2. **Debug UI** (`Pokey` register-poking submenu,
           `PokeyView`/`AtariView` overlays): keep from the start, not
           dropped/deprioritized.
        3. **MIDI**: deferred entirely for the initial port, matching the
           recommendation - not part of the first Java port's scope.
      - Recorded all three answers in `plans/12_UI_SURVEY_PLAN.md`'s "Open
        questions" section. No Java port work has actually started yet -
        this was survey/decision-recording only.
- [x] Kicked off the actual Java port (user: "Continue"). Read
      `dis6502`/`jdis6502`'s own `CLAUDE.md`/`plans/PORTING_GUIDE.md`
      (the same author's prior, now-complete C++-to-Java port) for
      transferable conventions before proposing anything. Initially
      proposed a separate repo (matching `dis6502`'s own layout) - the
      user instead proposed doing it in-repo (`src/java` parallel to
      `src/cpp`, `lib/java` parallel to `lib/x64`), which turned out to
      fit this repo's own history better: `plans/OVERALL_PLAN.md`'s very
      first instruction (move C++ into `src/cpp`) already anticipated a
      sibling, whereas `dis6502`'s own C++ repo was never restructured
      that way. User also clarified `lib/java` is for vendored non-Maven
      jars (e.g. a future official ASAP Java library, mirroring
      `src/cpp/asap/`'s vendored C code), not Maven-managed dependencies.
      - Confirmed via direct investigation (not assumed): WUDSN Base
        jars already installed locally; Java 21 + Maven 3.9.9 available;
        this repo's own README links wudsn.com downloads, confirming the
        same author/umbrella as `dis6502`/WUDSN Base - grounding the
        `com.wudsn.tools`/`org.atari.raster.rmt` groupId/artifactId choice
        and the `model`/`ui` package split (read `dis6502`'s actual
        source layout to confirm, not just its `CLAUDE.md` prose).
      - User decisions: depend on WUDSN Base (yes); start with the
        smallest already-tested model classes, not `CSong` (matching
        `dis6502`'s own "model before UI, completely, with tests" rule).
      - Created `pom.xml` (repo root), `src/java/` + `src/java/test/`
        (nested, mirroring `src/cpp/test`'s own nesting inside `src/cpp`),
        `lib/java/README.md`. Hit and fixed a real Maven pitfall: nesting
        `test/` inside the main `sourceDirectory` means the main compile
        picks up test sources too, missing the test-scoped JUnit
        dependency - confusing "cannot find symbol: assertEquals" errors
        that look like a missing-dependency problem but aren't. Fixed
        with an explicit `maven-compiler-plugin`
        `<excludes>test/**</excludes>` on the main compile. Also needed
        one online `mvn test` (not `-o`) to fetch the
        `surefire-junit-platform` provider, uncached locally since
        `dis6502` only ever needed the JUnit-3 provider - offline builds
        work normally after that one-time fetch.
      - Ported the first class, `CFraction` → `Fraction` (chosen as the
        smallest, dependency-free class - a "prove the conventions" pick,
        not a critical-path one). Made it immutable rather than mirroring
        C++'s in-place mutation (idiomatic-substitution judgment call per
        `dis6502`'s own guidance), collapsing pre-/post-increment into one
        `increment()` method - noted in the class's own javadoc since the
        not-yet-ported `RmtView.cpp`/`TuningTypes.h` call sites will need
        to account for this later.
      - **Fixed a real, pre-existing `operator==` bug in both languages**,
        per the user's explicit choice (matching `dis6502`'s bug-handling
        policy for provable bugs during active porting): it checked the
        reduced difference's *denominator* for zero, but the constructor's
        own reduction always leaves a non-zero denominator, so it
        evaluated to false for every pair of operands including equal
        fractions - already flagged in `FractionTests.cpp`'s own
        characterization test (from an earlier session) as "a future
        cleanup/port should decide deliberately." Fixed `Fraction.cpp`
        (checks `numerator == 0` now) and the Java `equals()`; renamed and
        updated the C++ test (`EqualityOperatorComparesValue`). C++ side
        verified with a full `Rmt.exe`/`RmtTests.exe` rebuild (301 tests,
        0 regressions); Java side verified with `mvn -o test` (13 tests
        pass).
      - Wrote `plans/13_JAVA_PORT_PLAN.md` documenting every decision above;
        updated `CLAUDE.md` to describe both source trees/build systems;
        updated `.gitignore` for `target/`. Not yet committed (both the
        C++ fix and the Java port should be separate commits, per
        `dis6502`'s own established policy).
  - **2026-09-24**: Second Java-port batch, `CTuning`/`TTuningSettings`/
    `TTuningRatios` → `Tuning`/`TuningSettings`/`TuningRatios`.
    - **Genuine scope fork, asked explicitly**: `CTuning`'s own C++ source
      is already split across `Tuning.cpp` (pure pitch math, fully covered
      by `TuningTests.cpp`'s golden-master values) and `TuningTables.cpp`
      (`GenerateTable`/`InitTuning`, reading C++ global tuning state, with
      **zero** existing test coverage). User chose the pure-math-only
      scope, deferring `GenerateTable`/`InitTuning`/`GetTruePitch`/
      `CalculateDeltaAUDF`/`Timbre`/`TTuning` to a follow-up batch rather
      than porting untested logic (including a 29-row temperament-ratio
      table) with no golden master to verify against.
    - `Tuning` keeps only `getPitch`/`getAUDF`/`getPOKEYPitch`, using the
      C++ test-only constructor (`Tuning(int clockFrequency)`) as the sole
      constructor - no need for C++'s two-argument `InitTuning()` entry
      point without the table-generation half.
    - `TuningSettings` stayed a plain mutable field-holder (not immutable
      like `Fraction`), matching `TTuningSettings`'s own struct-plus-
      `Initialize()` shape - it's a settings struct meant to be mutated by
      not-yet-ported UI code, so immutability doesn't fit. C++ leaves
      `basetuning`/`basenote` genuinely uninitialized until `Initialize()`
      runs; Java's mandatory field zero-initialization means that hazard
      doesn't carry over.
    - `TuningRatios` directly reuses the already-ported `Fraction` for its
      13 interval fields - the reason it was worth porting in the same
      batch. Field names became idiomatic camelCase (`min2nd`, `perf5th`,
      etc.) instead of the C++ struct's `SCREAMING_SNAKE_CASE`.
    - Tests (`TuningTest`/`TuningSettingsTest`/`TuningRatiosTest`) mirror
      `TuningTests.cpp`/`TuningTypesTests.cpp` exactly, including the
      `minorSecondIsStoredReduced` characterization test. No C++ changes
      needed - no bugs found this batch. Verified with `mvn -o test`: 28
      tests pass (13 `Fraction` + 15 new). Details in
      `plans/13_JAVA_PORT_PLAN.md`. Committed (`8b01038`).
  - **2026-09-24**: Backfilled the C++ test coverage the scope decision
    above deferred - `CTuning::GetTruePitch`/`CalculateDeltaAUDF`/
    `GenerateTable`/`InitTuning`, none of which had ever been unit-tested.
    Turned out to be much less hazardous than the earlier scope question
    assumed: `g_tuning`/`g_tuningRatios` were already real, linked globals
    (added to `test/SongEditingStub.cpp` by the later `SongEditing.cpp`
    work, after `CTuning`'s original split), so `InitTuning()`'s
    `MessageBox`+`exit(1)` guard is trivially avoidable by calling
    `g_tuning.Initialize(false)` first - the same technique
    `SongEditingTests.cpp` already used, just not one I'd checked for
    before recommending the Java-side deferral.
    - Moved `GetTruePitch`/`CalculateDeltaAUDF`/`GenerateTable` from
      `private` to `public` in `Tuning.h` (matching the
      `CCompressLzss::Optimise_*` visibility-only precedent).
    - Deleted the now-obsolete `test/TuningInitStub.cpp` (empty-body link
      stub for `InitTuning()`); `test/RmtTests.vcxproj` now links the real
      `TuningTables.cpp`. Added `test/TuningTablesStub.cpp` for
      `g_notesperoctave` (defined for real in the still-unlinked
      `Global.cpp`, same treatment as `g_tuning`/`g_tuningRatios`).
    - Added 24 tests (301 -> 325, all passing): `GetTruePitch` (equal
      temperament + octave-doubling identity, a full 12-note preset row,
      and a ragged 6-note preset row exercising the notesnum-detection
      scan), `CalculateDeltaAUDF` (one test per distortion/timbre branch,
      including both "invalid timbre" fallbacks via synthesized
      out-of-enum `Timbre` values), `GenerateTable` (8-bit and
      16-bit-joined generation), and `InitTuning` (byte-level checks
      across all 13 real lookup-table offsets, plus confirming it
      populates the private `CUSTOM[]` array that `GetTruePitch`'s
      `TUNING_CUSTOM` branch reads). All values are golden-master captures
      (placeholder assertion, run, read the real value from the failure
      diagnostic) - this branching/modulo arithmetic isn't safe to
      hand-derive, confirmed by several of my own hand-guessed placeholder
      values coming back wrong on the first run. No new C++ bugs found.
    - This unblocks a future Java follow-up batch for these same four
      methods, which now has real golden-master values to port against.
    - Verified via a full Release|x64 solution rebuild: `Rmt.exe` and
      `RmtTests.exe` both build clean (0 errors) and all 325 tests pass.
      Details in `plans/13_JAVA_PORT_PLAN.md`. Committed (`73f199e`).
  - **2026-09-24**: Third Java-port batch - finished `Tuning` with
    `GenerateTable`/`InitTuning`/`GetTruePitch`/`CalculateDeltaAUDF`/
    `Timbre`, now unblocked by the C++ test backfill above. Every expected
    value was reused directly from the C++ golden-master captures and
    matched on the first `mvn -o test` run - no re-guessing needed, which
    is exactly why the C++ batch was done first.
    - `Timbre` ported as a Java enum with each constant carrying its C++
      byte value (needed since `CalculateDeltaAUDF`/`GenerateTable` extract
      a high nibble from it).
    - `generateTable()`/`initTuning()` take `TuningSettings`/
      `TuningRatios` explicitly instead of reading C++ globals, plus an
      explicit `offset` parameter on `generateTable()` in place of C++
      pointer arithmetic. `InitTuning()`'s `MessageBox`+`exit(1)` guard
      became an `IllegalStateException` (same pattern as `Fraction`'s
      division-by-zero guard).
    - Deduplicated one piece of logic the C++ source itself duplicates
      (`GetTruePitch`/`InitTuning` both scan a `temperament_preset` row
      for its first zero/padding entry) into one private
      `computeNotesPerOctave()` helper - doesn't change behavior.
    - Confirmed and omitted two things already dead in the C++ source:
      unused `dist_4_buzzy`/`dist_c_unstable` constants, and `GenerateTable`'s
      never-read `MOD7`/`MOD15`/`MOD73` locals.
    - One C++ test has no Java equivalent (the "invalid timbre for this
      distortion" fallback, only reachable via `static_cast` on a
      synthesized out-of-enum value) - Java's closed enum type has no way
      to construct that state, noted in a comment rather than
      characterized.
    - Verified with `mvn -o test`: 51 tests pass, all green first try.
      Details in `plans/13_JAVA_PORT_PLAN.md`. Committed (`0d3bbbb`).
  - **2026-09-24**: Added an Eclipse project (`.project`/`.classpath`/
    `.settings/`, m2e-managed) at the repository root, per the user's
    request, so the Java port can be compiled/edited in Eclipse without
    needing a separate Maven-import wizard step. Mirrors `pom.xml`'s
    custom `src/java`(excluding `test/`)/`src/java/test` source layout;
    dependencies resolve from the same `pom.xml` via m2e's Maven
    Dependencies classpath container. `CLAUDE.md` updated with an import
    note. Committed (`1ee3ae8`).
  - **2026-09-24**: Fourth Java-port batch - `Notes` (from `CNotes`,
    `Notes.h/.cpp`), the next smallest already-tested, dependency-free
    class. Ported as a non-instantiable all-static-methods class (`CNotes`
    has no instance state in C++ either).
    - **Genuine scope fork, asked explicitly**: `IsValidNote()`'s known
      off-by-one bug (accepts `note == 61` past its documented "0-60
      inclusive" range) turned out to be live production logic - called
      from `Tracks.cpp`/`InstrumentsCore.cpp`/`IO_Tracks.cpp`/
      `SongEditing.cpp` via `CTracks::IsValidNote`'s delegation - unlike
      `Fraction::operator==`'s dead code. User chose to preserve it
      faithfully rather than fix it as a side-effect of a small class's
      port; fixing it would need its own investigation of every call site
      first. Noted in `Notes`'s javadoc and its test's comment.
    - Verified with `mvn -o test`: 59 tests pass (+8), all green first
      try. No C++ changes. Details in `plans/13_JAVA_PORT_PLAN.md`. Committed
      (`67d8cb7`).
  - **2026-09-24**: Fifth Java-port batch - `ChannelControl` (from
    `CChannelControl`, `ChannelControl.h/.cpp`), per-channel on/off/toggle/
    solo state, no globals, no known bugs.
    - Simplified C++'s private `SetChannelOnOff(ch, onoff)` (a two-sentinel
      multiplexed helper: `ch == -1` for all channels, `onoff == -1` for
      toggle) and `SetChannelSolo()`'s `goto`-based shared tail into
      direct, single-purpose Java methods - Java has no `goto`, and the
      sentinel multiplexing added no value once each call site can just
      call the specific method it means. Traced both of
      `SetChannelSolo()`'s branches against the original before
      simplifying to confirm zero behavior change.
    - `std::vector<bool>` became a plain `boolean[]` (channel count is
      fixed after construction in both languages).
    - Verified with `mvn -o test`: 66 tests pass (+7), all green first
      try. No C++ changes. Details in `plans/13_JAVA_PORT_PLAN.md`. Committed
      (`28861b0`).
  - **2026-09-24**: Asked the user what's next once the small, unambiguous
    "model" candidates were exhausted (`CStringUtility` trivial-but-not-
    model, `RmtCommandLineInfo` MFC-infra, `Keyboard2NoteMapping`
    Windows-virtual-key-code-coupled). User chose to start `CTracks`, the
    next genuinely core domain class, a real step up in size (~1100 lines
    across `Tracks.h/.cpp`/`IO_Tracks.cpp`).
    - Sixth Java-port batch: `Track`/`Tracks`, scoped to exactly what
      `TracksTests.cpp` already characterizes (mirrors the `Tuning`
      precedent, not re-asked since the pattern is now established):
      `IsEmptyTrack`/`ClearTrack`/`InsertLine`/`DeleteLine`/
      `CalculateNotEmpty`/`CompareTracks`/`TrackOptimizeVol0`/
      `GetModifiedNote`/`GetModifiedInstr`/`GetModifiedVolumeP`/
      `TrackToAta`/`AtaToTrack` (the last two are pure despite living in
      `IO_Tracks.cpp`). Deferred, untested in C++ either:
      `TrackBuildLoop`/`TrackExpandLoop`/`ModifyTrack`/`GetTracksAll`/
      `SetTracksAll`. Also deferred (matching the C++ split): `TracksEdit.cpp`'s
      `g_Undo`-coupled editing methods and `IO_Tracks.cpp`'s untested
      stream I/O (`SaveTrack`/`LoadTrack`/`SaveAll`/`LoadAll`).
    - `TTrack` became a plain mutable `Track` class (public fields,
      matching `TuningSettings`'s treatment, not `Fraction`'s
      immutability).
    - Careful unsigned-byte handling in `trackToAta`/`ataToTrack`: every
      read of the `byte[]` buffer masks with `& 0xFF` before use (Java
      `byte` is signed; C++'s was `unsigned char`), via a small
      `unsignedByte()` helper. Writes need no equivalent care.
    - C++'s `WRITEATIDX`/`WRITEPAUSE` macros (which `return -1` directly
      from the enclosing function on overflow) became private
      `writeAt`/`writePause` methods taking a single-element `int[]` as a
      mutable cursor - Java has no macros/multi-return, so callers check
      a boolean and propagate `-1` explicitly.
    - **Found and characterized (not fixed) a latent hazard**:
      `AtaToTrack`'s decode loop has no branch for `data == 63` with
      `count == 0x40` - would infinite-loop without advancing `src` if
      ever hit. Confirmed `TrackToAta`'s own encoder never produces this
      byte pattern (only reachable via a malformed/corrupted byte
      stream), so preserved as-is with a comment rather than hardened
      against - "how to handle corrupted input" is a different kind of
      decision than a same-input-different-output bug.
    - Verified with `mvn -o test`: 81 tests pass (+15), all green first
      try. No C++ changes. Details in `plans/13_JAVA_PORT_PLAN.md`. Committed
      (`94ab12c`).
  - **2026-09-24**: User asked to flag the two issues found-but-not-fixed
    during the Java-porting effort (`Notes`'s `isValidNote` off-by-one,
    `Tracks`'s `ataToTrack` `count==0x40` hazard) directly in the C++
    source, not just in the Java port's javadoc and in these plan docs.
    Added a comment above `CNotes::IsValidNote`'s declaration (`Notes.h`)
    and inside `AtaToTrack`'s `data==63` branch (`IO_Tracks.cpp`), both
    cross-referencing the Java port. Comment-only; verified with a full
    Release|x64 rebuild (0 errors, 325 tests pass). Committed (`d60274b`).
  - **2026-09-24**: Backfilled C++ test coverage for the five `CTracks`
    methods deferred from the `Tracks` Java-port batch (`TrackBuildLoop`/
    `TrackExpandLoop`(x2)/`ModifyTrack`/`GetTracksAll`/`SetTracksAll`),
    following the same playbook as `CTuning`'s earlier backfill: golden-
    master capture for the intricate branching logic, hand-derivation for
    the simple deep-copy.
    - `TrackBuildLoop` (6 tests) and `TrackExpandLoop` (4 tests, both
      overloads) golden-mastered - the triple-nested repeating-suffix
      search is too easy to mis-trace by hand. One test specifically
      characterizes the "more than 1 non-empty line in the loop region"
      guard by constructing an all-empty matching suffix and confirming
      it's correctly rejected.
    - `GetTracksAll`/`SetTracksAll` (1 round-trip test): **found and fixed
      a test-authoring bug, not a `CTracks` bug** - the first version
      stack-allocated a local `TTracksAll saved;`, which is ~1MB (254
      `TTrack` entries at ~4KB each) and crashed with a stack overflow.
      Fixed by heap-allocating via `std::make_unique<TTracksAll>()`,
      matching how `CTracks` itself always heap-allocates `m_track`.
    - `ModifyTrack` (4 tests): covers the `to >= TRACKLEN` clamp and the
      "filters by the *active* instrument, not necessarily the exact
      line's own instrument" semantics.
    - Verified via a full Release|x64 solution rebuild: `Rmt.exe`/
      `RmtTests.exe` both build clean and all 340 tests pass (325 -> 340,
      +15, 0 regressions). No new C++ bugs found - unblocks a future Java
      follow-up batch for these five methods. Details in
      `plans/13_JAVA_PORT_PLAN.md`. Not yet committed.
  - **2026-09-24**: User asked whether the C++ build uses available cores
    (14 logical, this machine). Found `Rmt.vcxproj` already had
    `MultiProcessorCompilation=true` but `RmtTests.vcxproj` (the slower
    project to rebuild - ~90 files including a large slice of production
    code plus GoogleTest) had no such setting at all, compiling
    single-threaded regardless of core count. Added it, matching
    `Rmt.vcxproj`'s own setting - solution has no inter-project
    dependencies either, so `-m` also lets the two projects build
    concurrently.
    - **Measured result was a 2.5x regression**, not an improvement:
      `RmtTests.vcxproj`'s rebuild alone went from ~2m26s (before, no
      `/MP`) to ~6m18s (after, `/MP` + `-m`). Checked
      `Get-MpComputerStatus`: Windows Defender real-time protection is
      active on this machine, and exclusions couldn't be checked/added
      (needs admin rights this session doesn't have) - AV scanning
      contention on many parallel `.obj` file writes is the standard,
      well-documented cause of this exact symptom, though unconfirmed
      without either an exclusion or a controlled A/B test with real-time
      protection paused (both need admin).
    - User's decision: keep the `/MP` change (correct in principle,
      matches `Rmt.vcxproj`'s existing setting) and add a Defender
      exclusion for `out/` and `src/cpp/test/out/` themselves, then
      re-time.
    - **Resolved, confirmed by re-timing**: added
      `build/setup_defender_exclusions.ps1` (self-elevating via UAC,
      excludes both folders); user ran it. Re-ran the identical
      `RmtTests.vcxproj` rebuild: **~1m01s**, down from ~6m18s
      (`/MP` + no exclusion) and better than 2x faster than the original
      ~2m26s single-threaded baseline - confirms the antivirus-contention
      theory decisively. All 340 tests still pass. The AV-exclusion
      hypothesis from earlier in this entry is now a confirmed fact, not
      a guess.
  - **2026-09-24**: User asked which files `Rmt.vcxproj`'s `PostBuildEvent`
    xcopy step was copying, then whether it could be made to only copy
    changed files. Investigation found the real reason it always did a
    full copy: `build_rmt_pre.bat`'s `PreBuildEvent` ran
    `del /Q /S %1` (`%1` = `$(OutDir)`) before *every* build, wiping out
    the destination's file timestamps that `xcopy /d` would otherwise need
    to compare against - so adding `/d` alone would have done nothing.
    - Also found this touches `build_rmt-daily.bat` (the actual release-
      packaging script, which ships `rmt135-daily.zip` to wudsn.com): its
      `copy_output` step just copies whatever's currently in
      `out/<Config>/output/` into the release, with no staleness check of
      its own - so simply dropping the wipe (to let `xcopy /d` work) would
      let a deleted `rmt/` file linger in `output/` and ship in the next
      release, undetected.
    - **User's chosen fix**: keep `xcopy` (smaller diff than switching to
      `robocopy /MIR`, which was also offered), but relocate the wipe
      rather than dropping it - move it from `build_rmt_pre.bat` (runs on
      every dev-loop build) into `build_rmt-daily.bat`'s
      `:build_configuration` (runs only when actually cutting a release),
      and add `/d` to `Rmt.vcxproj`'s `xcopy` (both Debug/Release
      `PostBuildEvent`s) now that the destination's timestamps survive
      between dev builds. Also dropped the now-unused `$(OutDir)` argument
      from the `PreBuildEvent`'s `Command` and corrected both scripts'
      stale "clears the output folder" comments/messages.
    - Verified all three cases directly (not just trusted the logic): a
      missing `output/` still triggers a full 438-file copy; an unchanged
      `output/` copies 0 files; touching one `rmt/` file (`tuning.ini`)
      copies exactly that one file. Full solution build + `RmtTests.exe`
      re-run: 340 tests still pass, 0 regressions.
    - **Did not run `build_rmt-daily.bat` itself** to verify its half of
      the change - it uploads to the live wudsn.com production site via
      WinRAR/an upload script, which is not something to trigger from an
      automated session. The wipe placement there was verified by reading
      the script, not by executing it; worth a real run next time a daily
      build is actually cut.
  - **2026-09-24**: Seventh Java-port batch - finished `Tracks` with
    `TrackBuildLoop`/`TrackExpandLoop`(x2)/`ModifyTrack`/`GetTracksAll`/
    `SetTracksAll`, now unblocked by the C++ backfill above. Every expected
    value was reused directly from the C++ golden-master captures and
    matched on the first `mvn -o test` run - same payoff `Tuning`'s own
    follow-up got from doing the C++ side first.
    - `trackBuildLoop`/`trackExpandLoop` (both overloads) ported with no
      structural changes - nothing here needed an idiomatic-substitution
      cleanup the way `ChannelControl`'s goto/sentinel logic did.
    - New `TracksAll` class (was the C++ struct `TTracksAll`): a plain
      mutable `int maxTrackLength` + `Track[TRACKSNUM]`, matching
      `TuningSettings`/`Track`'s "plain struct" treatment. `getTracksAll`/
      `setTracksAll` deep-copy via a private `copyTrack` helper.
    - `modifyTrack` takes a `Track` directly (not a track number),
      matching C++'s `TTrack*` parameter.
    - Verified with `mvn -o test`: 96 tests pass (+15), all green first
      try. This completes `CTracks`'s port to the extent the C++ source
      itself allows - `TracksEdit.cpp`'s `g_Undo`-coupled methods and
      `IO_Tracks.cpp`'s untested stream I/O remain deferred. Details in
      `plans/13_JAVA_PORT_PLAN.md`. Committed (`c4a11d3`).
  - **2026-09-24**: Eighth Java-port batch - `CInstruments` (from
    `Instruments.h`, `InstrumentsCore.cpp`, `Instruments.cpp`,
    `InstrumentsAtaFormat.cpp`), scoped to exactly what
    `InstrumentsTests.cpp` already characterizes, same discipline as
    `Tuning`/`Tracks`: `ClearInstrument`/`InitInstruments`,
    `SetEnvelopeVolume`, `MemorizeOctaveAndVolume`/
    `RememberOctaveAndVolume`, `InstrToAta`/`AtaToInstr`/`AtaV0ToInstr`.
    Deferred: `CheckInstrumentParameters`/`RecalculateFlag`/
    `CalculateNotEmpty`/`GetNote` (untested), `GetFrequency`/`Update`/
    `SaveAll`/`LoadAll`/`SaveInstrument`/`LoadInstrument` (need the
    not-yet-ported `CAtari`), and all GUI methods. `GetInstrumentsAll()`
    skipped outright - it's a zero-copy reinterpret-cast view in C++
    (unlike `GetTracksAll`'s real deep copy), untested, with no clean Java
    equivalent to design without inventing new behavior.
    - `g_tracks4_8`/`g_keyboard_RememberOctavesAndVolumes` become explicit
      `stereo`/`rememberOctavesAndVolumes` parameters, same pattern as
      `Tuning`'s globals-to-parameters redesign.
    - `RememberOctaveAndVolume`'s C++ `int&`/`int&` output parameters
      became a small `Instruments.OctaveAndVolume` record return value.
    - No hardware/Atari-memory side effects ported (`InstrumentTurnOff()`/
      `Update()`) - no Java equivalent exists yet since no live-playback
      subsystem has been ported; not a behavior difference since the
      concept these calls act on doesn't exist here yet either.
    - New small supporting types: `EnvelopeParameter` (plain int constants,
      matching their use as raw array indices, not an enum) and
      `InstrumentSection` (a plain enum, dropping C++'s explicit `NONE=-1`
      backing value since nothing reads the underlying int anywhere).
      `shpar`/`shenv` (GUI display-metadata tables) weren't ported - only
      needed by the deferred TXT-format save/load.
    - Found one thing worth double-checking mid-port: I initially wrote
      one test (`ataV0ToInstrDecodesOldFormat`) passing `stereo=true` where
      the C++ original used `g_tracks4_8 = 4` (mono) - caught before
      running by re-checking the boolean's derivation
      (`stereo = g_tracks4_8 > 4`) against each test's own global setup,
      not by a test failure (the fix was made before the first build).
    - Verified with `mvn -o test`: 112 tests pass (+16), all green after
      that fix. Details in `plans/13_JAVA_PORT_PLAN.md`. Committed
      (`e6a324b`).
  - **2026-09-24**: Backfilled C++ test coverage for the five deferred
    `CInstruments` methods (`CheckInstrumentParameters`/`RecalculateFlag`/
    `CalculateNotEmpty`/`GetNote`/`GetFrequency`), same playbook as
    `CTuning`'s and `CTracks`'s earlier backfills. All 28 values were
    hand-derived directly (simple conditionals/loops, no golden-master
    capture needed) and passed on the first run.
    - **`GetFrequency` didn't actually need its deferral either** - same
      "re-verify assumptions instead of trusting the old scoping note"
      lesson as `CSong`'s constructor earlier in this project.
      `CAtari::GetByteAt`/`SetByteAt` are plain array accessors, and
      `g_Atari` is already a real, cheap, linked global (`AtariStub.cpp`)
      - no new test-only seam needed, just setting/clearing bytes in
      `g_Atari`'s memory around each test.
    - `CheckInstrumentParameters` (6 tests, one per clamped field),
      `RecalculateFlag` (7 tests, one per flag plus the
      filter-over-Bass16 priority rule and the inclusive envelope-row
      scan boundary), `CalculateNotEmpty` (5 tests, including the
      beyond-`PAR_ENV_LENGTH` boundary), `GetNote` (4 tests), and
      `GetFrequency` (6 tests, all three distortion-offset branches plus
      the note-table shift).
    - Verified via a full Release|x64 solution rebuild: `Rmt.exe`/
      `RmtTests.exe` both build clean and all 368 tests pass (340 -> 368,
      +28, 0 regressions). No new C++ bugs found. Unblocks a future Java
      follow-up batch for these five methods. Details in
      `plans/13_JAVA_PORT_PLAN.md`. Committed (`ac664a9`).
  - **2026-09-24**: Ninth Java-port batch - finished `Instruments` with
    `checkInstrumentParameters`/`recalculateFlag`/`calculateNotEmpty`/
    `getNote`/`getFrequency`, now unblocked by the C++ backfill above.
    Every expected value was reused directly from the C++
    characterization tests and matched on the first `mvn -o test` run.
    - `getFrequency` takes the emulated Atari memory as an explicit
      `byte[]` parameter instead of a `CAtari` instance - resolves the
      design question flagged in the prior "Next steps" entry, matching
      `Tuning.generateTable`'s own precedent. `RMT_FRQTABLES` (0xB000)
      duplicated locally as a private constant pending `CAtari`'s own
      port.
    - The other four methods ported with no structural changes.
    - Verified with `mvn -o test`: 140 tests pass (+28), all green first
      try. This completes `CInstruments`'s port to the extent the C++
      source itself allows - the I/O methods (need unported infrastructure)
      and all GUI methods remain deferred. Details in
      `plans/13_JAVA_PORT_PLAN.md`. Committed (`3820359`).
  - **2026-09-24**: Tenth Java-port batch - `Atari` (from `CAtari`,
    `Atari.h/.cpp`). Before porting, re-verified `Init(bool)` instead of
    trusting the old "hazardous, calls CTuning::InitTuning()" scoping
    note - same "re-verify assumptions" finding as `CSong`'s constructor
    and `CInstruments::GetFrequency` earlier in this project. It's safe as
    long as `g_tuning.basetuning` is set first (already characterized in
    `TuningTests.cpp`), so backfilled 2 C++ tests
    (`InitPal/NtscPopulatesOwnMemoryWithTuningTables`) reusing
    `TuningTests.cpp`'s own golden-master bytes directly, then ported.
    Interestingly, PAL and NTSC produced the *same* byte value at this
    particular low semitone - confirmed as a real, independently-verified
    result, not a copy-paste artifact. `Init()`/`DeInit()`/`JSR()` stay
    deferred - genuine 6502 DLL/hardware interop, not a scoping question.
    - `GetMemoryAt`/`GetConstMemoryAt` (C++ pointer-into-buffer accessors)
      became a single `getMemory()` - Java array indexing already gives
      write-through access to the same buffer.
    - `init()` takes `TuningSettings`/`TuningRatios` explicitly (matching
      `Tuning`'s pattern) and builds a short-lived `Tuning` instance
      internally - C++'s global `g_Tuning` has no Java equivalent yet and
      none was needed here. Writes into a scratch buffer sized to just the
      table region first, then copies it into this instance's own memory
      at `RMT_FRQTABLES` - the Java equivalent of C++'s already-offset
      `GetMemoryAt(RMT_FRQTABLES)` pointer.
    - Cleanup: `Instruments.java`'s private `RMT_FRQTABLES` duplicate (from
      the previous batch, pending this class) now references
      `Atari.RMT_FRQTABLES`.
    - Verified via a full Release|x64 solution rebuild (370 C++ tests) and
      `mvn -o test` (149 Java tests, +9): all green, 0 regressions.
      Details in `plans/13_JAVA_PORT_PLAN.md`. Committed (`626ca5c`, `031fbbe`).
  - **2026-09-24**: User asked to continue with `CUndo` next. Investigation
    found it fully tested (30 tests) and not hazardous, but deeply wired
    into `CSong` (cursor/song-data/song-info methods, `TInfo`/`TSong`,
    `Part`) and needing `CInstruments::GetInstrumentsAll()` (skipped
    earlier as an untested C++ zero-copy view with no clean Java
    equivalent). Asked the user how to proceed, since this was effectively
    "start porting `CSong`" - the class this whole project has
    deliberately deferred as its own "God Object." User chose to start a
    minimal `CSong` slice now, scoped to exactly what `CUndo` needs.
    - Eleventh Java-port batch: `Part`/`PlayMode`/`EditArea` (enums),
      `Bookmark`/`SongInfo`/`SongData` (were `TBookmark`/`TInfo`/`TSong`),
      `Song` (a deliberately minimal `CSong` slice), `UndoType` (plain
      `int` constants, not an enum - `posIsEqual`/etc. need to accept the
      same out-of-range synthetic values C++'s tests exercise via
      `static_cast`, which a closed enum can't represent), `UndoEvent`
      (drops the `dataIsArray` C++ memory-management bookkeeping entirely
      - Java's GC makes it moot), `Undo` (ported in full).
    - New `InstrumentsAll` (real deep-copy snapshot, analogous to
      `TracksAll`): an additive capability for `Undo`'s genuine
      snapshot/restore need, not a reintroduction of the skipped
      `GetInstrumentsAll()`. Added `copyFrom` deep-copy helpers to
      `Instrument`/`Track`/`TracksAll`/`InstrumentsAll` along the way.
    - C++'s circular `CSong`↔`CUndo` global coupling (`Song::Stop()` calls
      `g_Undo.Separator()`; `Undo::Undo()`/`Redo()` call `g_Song.Stop()`)
      became a one-directional field (`Undo` stores `Song`) plus one
      explicit parameter (`Song.stop(Undo)`), avoiding a circular
      constructor dependency.
    - Omitted, no Java equivalent yet (documented in both classes'
      javadoc): `BLOCKDESELECT` (needs `CTrackClipboard`), the timer-wait
      in `Stop()`, `g_changes`/`SetRMTTitle()` window-title bookkeeping (no
      UI exists), and `Instruments.Update()` calls in `PerformEvent`
      (matches `Instruments`'s own prior omission of the same call).
    - **Found and flagged a latent C++ bug in both languages, not fixed**:
      `ChangeSong`'s `UETYPE_SONGDATA` case never copies the current
      bookmark into its snapshot, so the first undo of a whole-song-data
      change restores an indeterminate bookmark. Not currently
      characterized by any test. Flagged with a comment in `Undo.cpp`;
      preserved with the same omission in `Undo.java` for parity (Java's
      zero-init changes the *symptom* to a zeroed `Bookmark` rather than
      garbage, but the same missing-copy oversight is there either way).
    - Verified with `mvn -o test`: 179 tests pass (+30), all green on the
      **first** build - despite being the most structurally complex port
      this project has done (5 new collaborating classes, 2 new snapshot
      types). Full Release|x64 C++ rebuild also verified (comment-only
      change). Details in `plans/13_JAVA_PORT_PLAN.md`. Committed (`ce9ab71`,
      `c96206f`).
  - **2026-09-24**: Twelfth Java-port batch - `SapFile` (from `CSAPFile`,
    `SAPFile.h/.cpp`), the already-tested subset (getters/setters,
    `clear`/`normalize`/`export`). `Init(const CSong&)` deferred - untested
    in C++, and needs `Song` methods the deliberately minimal `Song` slice
    doesn't have, plus a real system-clock read.
    - `Export(std::ostream&)` became `export()` returning a `String`
      directly, matching the existing C++ test's own in-memory-stream
      usage and avoiding Java's checked-`IOException` ceremony for what's
      just building text.
    - Ported the already-known `DEFSONG` bug (prints `songs` instead of
      `defaultSong` - characterized, not fixed, in an earlier session)
      faithfully at first; user then asked to fix it on both sides before
      committing. Fixed in `SAPFile.cpp`/`SAPFileTests.cpp` and
      `SapFile.java`/`SapFileTest.java` together - no other production
      code depended on the buggy value (repo-wide search). Re-verified:
      370 C++ tests (full Release|x64 rebuild) and 183 Java tests, both
      green.
    - C++'s `ThrowRuntimeException` (blocking `MessageBox` + `exit(2)`)
      became a thrown `IllegalStateException`, matching the established
      idiom.
    - Verified with `mvn -o test`: 183 tests pass (+4). No C++ changes.
      Details in `plans/13_JAVA_PORT_PLAN.md`. Committed (`cf32e86`,
      `8830804`).
  - **2026-09-24**: Thirteenth Java-port batch - `AsmFileBuilder` (from
    `CASMFileBuilder`, `ASMFileBuilder.h/.cpp`), ported in full - no
    globals, no not-yet-ported dependencies, no deferrals, no C++ changes
    needed. New `AssemblerFormat` enum (`ATASM`/`XASM`).
    - C++'s `CString&` output parameter became a returned `Result(String
      code, int size)` record instead of a threaded mutable buffer.
    - `buildTracksData`'s trailing validity check scans
      `trackPos[0..65535]` unconditionally, not just the processed range -
      a fragile contract preserved as-is (already characterized this way
      in `ASMFileBuilderTests.cpp`), not hardened.
    - Found a real Java `Formatter` incompatibility (not a C++ bug): C++'s
      `"% 04x"`/`"% x"` space-flag-on-hex format string is silently
      dropped by `CString::Format` but throws
      `FormatFlagsConversionMismatchException` in Java's `Formatter`.
      Reproduced the exact same visible output by hand via string
      concatenation instead.
    - Verified with `mvn -o test`: 191 tests pass (+8), all green on the
      first build. Details in `plans/13_JAVA_PORT_PLAN.md`. Committed
      (`434f8a3`).
  - **2026-09-24**: Fourteenth Java-port batch - `Keyboard2NoteMapping`
    (from the free functions `NoteKey`/`NumbKey`/`Numblock09Key`,
    `Keyboard2NoteMapping.h/.cpp`), ported in full, no C++ changes needed.
    New `KeyboardLayout` (plain `int` constants, not a Java enum, since a
    test needs a synthetic out-of-range layout value the same way
    `UndoType` does).
    - The C++ global `g_keyboard_layout` became an explicit
      `keyboardLayout` parameter to `noteKey`.
    - C++'s `unsigned char[256]` tables holding `0xFF` for "unmapped",
      read through a `char`-returning function (a signed narrowing
      conversion turning `0xFF` into `-1`), became `byte[]` tables with
      `-1` written directly in place of every `0xFF`.
    - Neither language bounds-checks `vk` against `0..255` - preserved as
      a characterized, not hardened, fragile contract.
    - Verified with `mvn -o test`: 198 tests pass (+7), all green on the
      first build. Details in `plans/13_JAVA_PORT_PLAN.md`. Committed
      (`b8ab6d9`).
  - **2026-09-25**: Fifteenth Java-port batch - `RmtCommandLineInfo` (from
    `CRmtCommandLineInfo`, `RmtCommandLineInfo.h/.cpp`), the `SCRIPT:`/
    `TEST:` switch-parsing logic, all `RmtCommandLineInfoTests.cpp`
    exercises. No C++ changes needed.
    - Omitted C++'s fallback to `CCommandLineInfo::ParseParam()` (MFC's own
      default handling) - no Java equivalent, and no test depends on it.
    - Simplified a harmless redundant double call to `GetSwitchName()` down
      to one call - not a behavior change, just removing duplicate work.
    - Drops the `CCommandLineInfo` MFC base class entirely; the Java class
      is a plain data holder.
    - Verified with `mvn -o test`: 205 tests pass (+7), all green on the
      first build. Details in `plans/13_JAVA_PORT_PLAN.md`. Committed
      (`474e1ae`).
  - **2026-09-25**: Sixteenth Java-port batch - `StringUtility` (from
    `CStringUtility`, `StringUtility.h/.cpp`), a single one-line static
    method, ported in full, no C++ changes needed.
    - Reproduced `CString::Right(n)`'s clamp-to-string-length behavior
      explicitly, since `String.substring` has no equivalent and would
      throw for a negative starting index when the suffix is longer than
      the string.
    - Verified with `mvn -o test`: 210 tests pass (+5), all green on the
      first build. Details in `plans/13_JAVA_PORT_PLAN.md`. Committed
      (`d9fb6cf`).
  - **2026-09-25**: Seventeenth Java-port batch - `CompressLzss` (from
    `CCompressLzss`/`CLzss`, `lzss_sap.h/.cpp`), an optimal LZSS compressor
    for SAP-R register-dump music files. Explicitly requested despite this
    project's usual "small class" scope - the largest, most intricate
    class ported so far (~600 lines of bit-packing). No C++ changes
    needed; `lzssp.h` (532 lines) turned out to be unrelated Atari
    player-address constants, not part of the compressor.
    - New `SapROptimization` enum (plain, no synthetic out-of-range value
      needed here).
    - Redesigned the public API around a `compress(byte[], SapROptimization)
      -> byte[]` returning exactly the compressed bytes, instead of C++'s
      caller-pre-sized-buffer-plus-separate-length-return shape.
    - Eliminated dead code: two switch statements in C++'s `LZSS_SAP`
      choose bit-width parameters from local variables hardcoded to one
      value with no way to reach any other branch - hardcoded that one
      outcome directly. Dropped the unused `registers` parameter and its
      untested 5-argument overload.
    - Omitted all diagnostic `fprintf`/stats-histogram code - it only ever
      wrote to a log stream, never affecting the returned buffer or size.
    - **Caught a real unsigned/signed pitfall before running any test**:
      `Optimise_AUDF`'s two-tone check does a raw magnitude comparison
      (`buf[1] < 0xF0`), unlike every other comparison in the file (which
      mask first, safe regardless of Java's signed-byte sign extension). A
      literal port would have silently inverted this comparison for any
      byte >= 0x80. Fixed with the established `unsignedByte()` helper,
      applied consistently to every register-buffer read.
    - The fixed 128KB scratch buffer became a small growable `BitBuffer`
      (needs positional bit-mutation, so a plain append-only structure
      wouldn't work); the pure-append output buffer became a
      `ByteArrayOutputStream`.
    - Verified with `mvn -o test`: 221 tests pass (+11), all green on the
      first build - the 3 golden-master compression tests matched exactly
      with no debugging needed. Details in `plans/13_JAVA_PORT_PLAN.md`.
      Committed (`201cd1a`).
  - **2026-09-25**: Eighteenth Java-port batch - grew `Song` (previously
    minimal, just what `Undo` needed) to cover all of `CSong`'s
    `SongCore.cpp` methods, the already-tested "safe cluster" split out of
    `Song.cpp`/`IO_Song.cpp`. First direct `SongTest`; no C++ changes
    needed.
    - New methods: `getName`, `getTracks`/`isStereo` (now taking an
      explicit `tracks4_8` parameter), `isNTSC`, `getInstrumentSpeed`,
      `playPressedTonesInit`, `getActiveInstr`, `getActiveColumn`, the
      *track* `getPlayLine`/`setPlayLine` and the *song*
      `songGetPlayLine`/`songSetPlayLine` (two separate new fields),
      `songTrackGoDec`/`songTrackGoInc`,
      `findNearTrackBySongLineAndColumn`, `songPlayNextLine`, `songToAta`,
      `ataToSong`. New `TrackFlag` constants class.
    - Dropped `PlayPressedTonesInit`'s always-true C++ `BOOL` return (made
      `void`), for symmetry with its sibling `setPlayPressedTonesSilence`
      (already `void` from an earlier batch); kept `songPlayNextLine`/
      `ataToSong` as `boolean`-returning since no existing sibling forces
      otherwise and their tests assert on the return directly.
    - Verified with `mvn -o test`: 236 tests pass (+15), all green on the
      first build. Details in `plans/13_JAVA_PORT_PLAN.md`. Committed
      (`b5a78f1`).
  - **2026-09-25**: Nineteenth Java-port batch - `Messages` (from
    Messages.h/.cpp), the subset `MessagesTests.cpp` exercises, ported in
    full, no C++ changes needed. New `MessageButtons`/`MessageAnswer`
    enums (Java `SCREAMING_SNAKE_CASE`, unlike the C++ originals' camelCase
    spellings).
    - Log-fallback-only by design: `g_statusBar` always stays null in
      every C++ test, so the real Win32 `MessageBox()` path is never
      reached and isn't ported - there's no Java UI/window to show a
      dialog in anyway.
    - Omitted `SendInfoMessage` - unconditionally calls
      `SetStatusBarText()` (no log-fallback branch at all) and isn't
      tested.
    - The module-level `g_testQuestionAnswer` global became an instance
      field (default `CANCEL`, the safe choice); `OutputDebugString`
      became `System.err`.
    - Verified with `mvn -o test`: 240 tests pass (+4), all green on the
      first build. Details in `plans/13_JAVA_PORT_PLAN.md`. Committed
      (`83bffaf`).
  - **2026-09-25**: Twentieth Java-port batch - `AtariTrackerDriver` (from
    `CAtariTrackerDriver`), the subset `AtariTrackerDriverTests.cpp`
    exercises. First Java port needing real file I/O against a bundled
    resource. No C++ changes needed.
    - New `TrackerDriverVersion` enum, `AtariIO` (just
      `loadDataAsBinaryFile`), `RmtAtariBinaries` (just
      `getTrackerDriverBinary`, reading `rmt/resources/drivers/*.obx`
      relative to the working directory instead of C++'s
      executable-relative `g_prgpath`).
    - Key finding: C++'s `C6502::JSR`/`CAtari::JSR` are already a
      link-only no-op stub even in the C++ test build - not merely
      "stubbed for tests" but genuinely inert. Since every JSR-calling
      method has no other observable effect, this port omits the calls
      entirely; `Play`/`SetPokey`/`Silence` become plain no-ops, and
      `Play`'s prove-mode branch (dead either way) isn't ported.
    - Added a `getRmtInstrument(int)` accessor (no C++ equivalent) so
      `init`'s reset behavior stays testable without porting its untested
      real-world writers (`SetTrackNoteInstrumentVolume`/
      `InstrumentTurnOff`).
    - Verified with `mvn -o test`: 243 tests pass (+3), all green on the
      first build, including the real resource-loading/binary-parsing
      test matching its exact expected byte. Details in
      `plans/13_JAVA_PORT_PLAN.md`. Committed (`a2ef5aa`).
  - **2026-09-25**: Twenty-first Java-port batch - `PokeyStream` (from
    `CPokeyStream`), the pure state-machine methods
    `PokeyStreamTests.cpp` exercises, plus the two early-return guard
    clauses of `Record`/`WriteToFile`. No C++ changes needed. New
    `StreamState` enum (plain, no synthetic value needed).
    - `StartRecording`/`FinishedRecording`, and `record`/`writeToFile`'s
      real bodies, are all deferred entirely - none are touched beyond
      the tested guards; the real recording pipeline is exercised for
      real in `SongEditingTests.cpp` instead, which isn't ported. No
      stream-buffer/driver fields are modeled at all in the Java class.
    - `CallFromPlay`'s raw `int playerState` parameter becomes a real
      `PlayMode` parameter - C++ only uses `int` there because
      `PokeyStream.h` doesn't include `PlayMode`'s header, not because
      any non-`PlayMode` value is needed.
    - Verified with `mvn -o test`: 252 tests pass (+9), all green on the
      first build, including the hand-traced multi-step loop-detection
      tests. Details in `plans/13_JAVA_PORT_PLAN.md`. Committed (`c5233c1`).
  - **2026-09-25**: Wrote `plans/15_JAVA_SONGEDITING_PLAN.md`, scoping the
    much larger `SongEditing.cpp` port (plus its neighboring exporters/
    importers) into sub-batches, per the user's request. No implementation
    yet at that point.
  - **2026-09-25**: Twenty-second Java-port batch - `Song` grows to cover
    `SongEditing.cpp` sub-batch 1 (cursor/navigation helpers):
    `GetSubsongParts`, `MarkTF_USED`/`MarkTF_NOEMPTY`, `ActiveInstrSet`/
    `Prev`/`Next`, `TrackLeft`/`TrackRight`, `RespectBoundaries`,
    `TrackGetLoopingNoteInstrVol`, `SongTrackSet`/`SetByNum`/`Dec`/`Inc`/
    `Empty`/`GoOnOff`. No C++ changes needed.
    - Found `GetUECursor`/`SetUECursor` were already fully ported (all 4
      `Part` cases) from the earlier CUndo/Song batch - removed from this
      sub-batch's scope.
    - `Song`'s constructor now also takes a `Tracks` collaborator
      (alongside `Instruments`) - updated all 4 existing call sites.
    - Pulled `getSmallestMaxtracklen` and `songGetActiveTrack` forward
      from later sub-batches/`Song.h`, since `respectBoundaries`/
      `songTrackSetByNum` needed them.
    - `g_keyboard_RememberOctavesAndVolumes` becomes an explicit parameter
      on the `activeInstr*` methods; unlike the C++ test binary (which
      link-time-stubs the octave/volume methods as no-ops for this one
      test file only), the Java port always runs their real bodies, since
      nothing here asserts on octave/volume anyway.
    - `TrackLeft`/`TrackRight`'s `goto`-based control flow became a
      `wrapColumn` boolean flag - same branches, no `goto` needed.
    - Verified with `mvn -o test`: 270 tests pass (+18), all green on the
      first build. Details in `plans/13_JAVA_PORT_PLAN.md`. Committed
      (`bcf89a5`).
  - **2026-09-25**: Twenty-third Java-port batch - `Song` grows to cover
    `SongEditing.cpp` sub-batch 6 (`InstrInfo`/`InstrChangeApply`/
    `TrackInfo`, the dual-mode info/dialog methods). No C++ changes
    needed.
    - Skipped the `MessageBox`-building branch entirely (no Java UI to
      show it in, and it's untested) rather than literally porting the
      dual-mode `if/else` shape - simpler than the C++ original.
    - New `Song.InstrInfo`/`Song.TrackInfo` mutable classes (matching
      `SongInfo`'s precedent, needed for the "leaves the struct untouched
      for invalid input" contract a record can't express) and
      `Song.InstrChangeParams` plus `instrChangeApply` - the big one
      (~250 lines of instrument-remap logic). C++'s `goto abortchanges`
      became a plain `break` guarded by an `if (onlysomething && !error)`
      on the following block.
    - Made `Tracks.MAXVOLUME` public (was private) - needed as a literal
      clamp value, not just for validity checking.
    - Verified with `mvn -o test`: 275 tests pass (+5), all green on the
      first build, including the intricate remap arithmetic. Details in
      `plans/13_JAVA_PORT_PLAN.md`. Committed (`5f27af5`).
  - **2026-09-25**: Twenty-fourth Java-port batch - `Song` grows to cover
    `SongEditing.cpp` sub-batches 2-5 (song-line editing, track-length
    cleanup, track/instrument copy-paste, bookmark/settings), all done
    together per the user's request. No C++ changes needed.
    - Scope correction: `TrackCopy`/`TrackPaste`/`TrackCut` only needed
      `CTrackClipboard`'s single-track `m_trackcopy` slot, not the whole
      class - modeled as a `trackCopyClipboard` field directly on `Song`.
    - `setTracks`/`setNTSC` drop C++'s conditional `ReInitSound()` call;
      `setTracks` becomes a trivial identity transform once that's
      removed.
    - `RenumberAllInstruments`'s `type=3` has no direct C++ test coverage
      - ported as a faithful mechanical translation anyway, not
      independently verified against a golden master. Its final
      `g_Instruments.Update(i)` loop is omitted, matching `Instruments`'s
      own prior omission.
    - Found a real test-fixture gap (not a production bug): Java's
      `Instruments.clearInstrument()` has real behavior (sets a default
      "Instrument XX" name) unlike the C++ test binary's no-op stub there,
      so the Java fixture needed to explicitly blank instrument names
      after `initInstruments()` for the name-comparison tests to start
      from the same clean slate the C++ fixture's manual memset provides.
    - Verified with `mvn -o test`: 307 tests pass (+32), all green on the
      first build once that fixture gap was found and fixed. Details in
      `plans/13_JAVA_PORT_PLAN.md`. Committed (`bd826a9`).
  - **2026-09-25**: Fixed the `LoadTxt` bug on the C++ side (user's
    explicit "fix on both" decision - see
    `plans/15_JAVA_SONGEDITING_PLAN.md`'s sub-batch 8 entry for the full
    mechanism). `LoadTxt()`'s `[MODULE]`/`[SONG]` segment loops now skip a
    lone `'\n'` byte (the blank "gap" line `SaveTxt()` writes before each
    segment) instead of handing it to `getline()` as content, which used
    to silently swallow the next segment's `'['` boundary marker. Updated
    `SongEditingTests.cpp`'s round-trip test to assert the song data now
    survives the round trip (previously it documented that it didn't).
    Verified via full Release|x64 rebuild: 370 tests pass (same count -
    no new test, an existing one now asserts the fixed behavior). Java's
    own `LoadTxt` port (sub-batch 8) hasn't started yet, so there's
    nothing to port the fix into yet - it'll use the corrected behavior
    directly when that sub-batch happens.
    - **Unrelated finding, not fixed here**: `ClearSong()` (and anything
      that calls it) crashes when run as the only test in an isolated
      `--gtest_filter` invocation - confirmed on an existing, untouched
      test too (`ClearSongSetsTheTrackCount`), so not something this fix
      introduced. `g_Atari.Init()`'s tuning-initialization path seems to
      depend on global state only valid once an earlier test has run
      first. Doesn't affect the full suite (370/370 pass). Flagged for
      awareness, not investigated further.
  - **2026-09-25**: Twenty-fifth Java-port batch - `Song` grows to cover
    `SongEditing.cpp` sub-batch 7 (`MakeModule`/`DecodeModule`, the RMT
    module byte-format encoder/decoder). No C++ changes needed.
    - New `SongIOType` (all 15 C++ values) and `RmtFormatVersion` (plain
      `int` constants) enums.
    - Java can't slice arrays without copying, unlike C++'s pointer
      offsets into a shared buffer - `makeModule` uses a scratch buffer
      plus `arraycopy`, `decodeModule` uses `Arrays.copyOfRange`.
    - `decodeModule` returns a `DecodeModuleResult(version, tracks4_8)`
      record instead of a plain `int` - C++'s own return is ambiguous (0
      means both "failed" and "decoded a real version-0 file") and
      `SetTracks`'s `g_tracks4_8` side effect needed a way to reach the
      caller.
    - Verified with `mvn -o test`: 308 tests pass (+1), all green on the
      first build. Details in `plans/13_JAVA_PORT_PLAN.md`. Committed
      (`23b5170`).
  - **2026-09-25**: Fixed `SaveRMW`/`LoadRMW`'s "main parameters"
    `sizeof(mainparams[0])` bug on the C++ side (user's explicit direction,
    after being shown the finding - see
    `plans/15_JAVA_SONGEDITING_PLAN.md`'s sub-batch 8 entry for the full
    mechanism). `mainparams` is `int* mainparams[31]`, so
    `sizeof(mainparams[0])` is `sizeof(int*)` - 4 bytes on the 32-bit
    builds this project originally shipped as (masking the bug), 8 bytes
    on this 64-bit build (silently over-reading/writing 4 bytes of
    adjacent memory per parameter). User identified the 32-bit history as
    the cause and asked for the size-independent, originally-correct
    behavior to be restored. Fixed with an explicit `sizeof(int)` in both
    `SaveRMW`/`LoadRMW`. Added a new test
    (`SaveRMWWritesEachMainParameterAsExactlyFourBytes`) that parses exact
    byte offsets to catch this - the existing round-trip test didn't, since
    the extra bytes happened to self-cancel on this build's specific
    memory layout. Verified via full Release|x64 rebuild: 371 tests pass
    (+1). Java's own `SaveRMW`/`LoadRMW` port (sub-batch 8) hasn't started
    yet, so there's nothing to port the fix into yet - it'll use the
    corrected 4-byte-per-parameter format directly.
  - **2026-09-25**: Twenty-sixth Java-port batch - `Song` grows to cover
    `SongEditing.cpp` sub-batches 8 and 10 (`SaveTxt`/`LoadTxt`/`SaveRMW`/
    `LoadRMW`/`LoadRMT`, plus `ClearSong`, pulled forward as a genuine
    blocking dependency rather than the originally-planned capstone).
    - `ClearSong` turned out fully portable once re-checked in full - only
      a handful of small new `Song` fields and one omitted MFC UI-sync
      call (no Java equivalent, unobserved by any test) were needed.
    - Deliberately deferred `IO_Instruments.cpp`/`IO_Tracks.cpp`'s TXT/RMW
      per-instrument/per-track serialization as its own separate
      undertaking - untested by any existing C++ or Java test.
    - `saveTxt`/`loadTxt` take/return `String`; `saveRMW`/`loadRMW`/
      `loadRMT` take/return `byte[]` - sidesteps Java's checked
      `IOException` entirely, no stream abstraction introduced.
    - `SaveRMW`/`LoadRMW`'s ~15 unmapped "main parameters" (UI/keyboard
      globals with no Java equivalent): per the user's decision, the file
      keeps all 31 four-byte slots in the same order as C++ for byte-
      compatibility, writing 0 for the unmapped ones.
    - New `RmtVersion.RMT_VERSION_STRING` and `AtariIO.loadBinaryBlock`.
    - Verified with `mvn -o test`: 316 tests pass (+8), all green on the
      first build. Details in `plans/13_JAVA_PORT_PLAN.md`. Committed as
      `39347e2`.
  - **2026-09-25**: Twenty-seventh Java-port batch - `Song` grows to cover
    `SongEditing.cpp` sub-batch 9 (navigation/playback: `SongJump`/`SongUp`/
    `SongDown`/`SongSubsongPrev`/`SongSubsongNext`, `TrackUp`/`TrackDown`,
    `SongPrepareNewLine`/`SongPutnewemptyunusedtrack`,
    `SongMaketracksduplicate`/`Songswitch4_8`, `PlayPressedTones`,
    `InstrPaste`, `Play`/`PlayBeat`/`PlayVBI`).
    - All always-true/never-observed `BOOL` returns dropped to `void`;
      the rest keep `boolean` since tests assert on it.
    - `songUp`/`songDown`/`songSubsongPrev`/`songSubsongNext` omit C++'s
      untested `if (m_play && m_followplay) { Stop(); ...; Play(); }` tail
      (`playMode` defaults to `PLAY_STOP` in every test).
    - `g_keyboard_updowncontinue` becomes an explicit parameter on
      `trackUp`/`trackDown`; new trivial delegator `trackGetLastLine()`.
    - `songMaketracksduplicate`/`songswitch4_8` take a `Messages`
      parameter, since `SendQuestionMessage`'s return value drives
      branching (unlike the guard-only `SendErrorMessage` calls dropped
      elsewhere). `songswitch4_8` needed a two-`tracks4_8`-parameter shape
      (current in, possibly-updated out) since C++'s one parameter does
      double duty that Java's stored-nowhere `tracks4_8` can't replicate
      with one.
    - `AtariTrackerDriver` grew `setTrackNoteInstrumentVolume`/
      `setTrackVolume`/`instrumentTurnOff`, pulled forward from "no
      dedicated test coverage - deferred" now that `PlayPressedTones`/
      `PlayBeat` exercise them; JSR calls stay omitted, but their other
      real effects (`g_rmtinstr` bookkeeping, one POKEY-register memory
      reset) are kept.
    - `instrPaste`'s and `play`'s/`playBeat`'s C++ `goto`s become boolean
      flags and a labeled `while(true)`/`continue` loop - Java has no
      `goto`.
    - `PLAY_BLOCK`'s real block-selection branch is unreachable here (no
      `CTrackClipboard` block-selection surface exists) - always falls
      back to `PLAY_TRACK`; `trackPlayBlockStart`/`trackPlayBlockEnd` kept
      as real fields for when that changes.
    - `playVBI`'s quantization branches are omitted (need `Tracks`'s
      setter family and `g_respectvolume`, none ported; unreachable in
      every existing test).
    - Verified with `mvn -o test`: 339 tests pass (+23), all green on the
      first build. No C++ changes in this batch. Details in
      `plans/13_JAVA_PORT_PLAN.md`. Committed as `2141688`.
  - **2026-09-25**: Started scoping `CTrackClipboard` as its own dedicated
    porting pass (per `plans/15_JAVA_SONGEDITING_PLAN.md`'s execution order)
    and found it has *no* existing C++ test coverage at all - unlike every
    other class ported this whole effort. Every method except `BlockEffect`
    (a real MFC dialog, confirmed unextractable) is otherwise hazard-free
    (`ClipboardCore.cpp`'s own header comment already says so), but porting
    it faithfully means first writing a new `ClipboardTests.cpp`
    characterization suite - a different, bigger kind of task than pure
    porting. Flagged to the user, who chose to do the exporters batch
    (which already has C++ tests) instead and leave `CTrackClipboard` for
    a later decision.
  - **2026-09-25**: Twenty-eighth Java-port batch - two new free-standing
    classes, `RmtExporter` (`exportAsRMT`/`exportAsStrippedRMTApply`) and
    `AsmFileExporter` (`exportAsAsmApply`/`buildRelocatableAsm`/
    `exportAsRelocatableAsmForRmtPlayerApply`/`composeRMTFEATstring`),
    mirroring C++'s own `CRmtExporter`/`CASMFileExporter` split rather than
    folding onto `Song`.
    - Found and fixed a genuine C++ off-by-one on both sides:
      `ExportAsStrippedRMTApply` wrote one extra, always-zero trailing byte
      per export (used `MakeModule`'s exclusive-upper-bound
      `firstByteAfterModule` directly as `SaveBinaryBlock`'s inclusive
      `toAddr`, inconsistent with `ExportAsRMT`'s own correct
      `firstByteAfterModule - 1` a few lines above). Fixed in
      `RmtExporterCore.cpp` plus a new byte-exact regression test
      (`ExportAsStrippedRMTApplyWritesExactlyTheModuleSizeWithNoExtraByte`).
    - New `AtariIO.saveBinaryBlock`; `Song.nameToString` widened to
      `public static` for reuse by the new exporter classes;
      `ComposeRMTFEATstring`'s dead `trackSavedFlags` parameter dropped;
      `BuildRelocatableAsm`'s pointer-offset buffer becomes a copied
      scratch array, matching this port's established treatment of that
      pattern.
    - Verified with `mvn -o test`: 345 tests pass (+6); full C++
      Release|x64 rebuild + `RmtTests.exe`: 372 tests pass (+1), 0
      regressions on either side. Details in `plans/13_JAVA_PORT_PLAN.md`.
      Committed as `f411e21` (the C++ fix) and `0e29082` (the Java port).
  - **2026-09-25**: Corrected an earlier assessment - `CTrackClipboard`
    isn't actually untested. `SongEditingTests.cpp` already exercises a
    real subset of it indirectly, via `CSong`'s own `BLOCKSETBEGIN`/
    `BLOCKSETEND`/`BLOCKDESELECT`/`ISBLOCKSELECTED`/`BlockPaste` delegate
    wrappers. Ported that subset as a new `TrackClipboard` class: the
    constructor, `isBlockSelected`/`isTrackSelected`, `clear`,
    `blockSetBegin`, `blockSetEnd`, `blockDeselect`, `blockInitBase`,
    `blockCopyToClipboard`, `blockPasteToTrack`, `getFromTo`, plus `Song`'s
    own wrapper methods (`songBlockSetBegin`/`songBlockSetEnd`/
    `blockDeselect`/`isBlockSelected`/`blockPaste`).
    - Still not ported (confirmed no test coverage anywhere):
      `BlockAllOnOff`/`BlockExchangeClipboard`/`BlockClear`/
      `BlockRestoreFromBackup`/`BlockNoteTransposition`/
      `BlockInstrumentChange`/`BlockVolumeChange` - need new C++
      characterization tests first, deferred.
    - A real ripple into sub-batch 9's `play()`: `Song.isBlockSelected`/
      `blockDeselect` were previously hardcoded stubs, which is why
      `PLAY_BLOCK` was characterized as unreachable - now real, so
      `isBlockSelected`/`blockDeselect`/`trackUp`/`trackDown`/`songUp`/
      `songDown`/`songInsertCopyOrCloneOfSongLinesApply`/`play` all take an
      explicit `TrackClipboard` parameter, and `PLAY_BLOCK` reads
      `clipboard.getFromTo()`/`getSelSongLine()` for real. `Undo` gained a
      `TrackClipboard` constructor field too.
    - Verified with `mvn -o test`: 347 tests pass (+2), no regressions
      across every existing call site the new parameter touched. Details
      in `plans/13_JAVA_PORT_PLAN.md`. Committed as `59c1c62`.
  - **2026-09-25**: Scoping-only pass (no implementation) for the Java port
    of `IO_ImporterCore.cpp`'s TMC/MOD import, per the user's "perform
    required scoping" request. Read `IO_Importer.cpp`/`IO_ImporterCore.cpp`
    in full (1938 lines combined). Found this surface is already fully
    C++-tested (a prior phase's `plans/03_IO_IMPORTER_PLAN.md` already split
    each format into a real-dialog wrapper plus a dialog-independent
    `ParseHeader`/`Apply` pair, both tested in `SongEditingTests.cpp`) and
    every Java dependency both `Apply()`s need already exists - no gaps.
    Wrote `plans/14_JAVA_IMPORTER_PLAN.md`: new free-standing
    `TmcImporter`/`ModImporter` classes proposed (matching
    `RmtExporter`/`AsmFileExporter`'s precedent); struct-to-record
    mappings for `TImportTMCHeader`/`Result`/`TImportMODHeader`/`Result`;
    noted that Java's byte-array-everywhere idiom actually *removes* one
    of C++'s wrinkles (MOD's "needs continued stream access" problem
    disappears once the whole file is a `byte[]`); flagged
    `ImportMODApply`'s `goto`-driven tone-portamento state machine as the
    one part needing real design attention at implementation time, unlike
    every `goto` restructured so far in this port. Suggested batching: TMC
    first (smaller, no `goto`s), MOD second. Not yet implemented.
  - **2026-09-25**: Thirtieth Java-port batch - `TmcImporter` (Batch A of
    `plans/14_JAVA_IMPORTER_PLAN.md`), porting `CSong::ImportTMCParseHeader`/
    `ImportTMCApply` and their `CConvertTracks` helper as a new
    free-standing class (matching `RmtExporter`/`AsmFileExporter`'s
    precedent).
    - Transcribed almost line-for-line from the already-C++-tested
      `ImportTMCApply` once every byte read got the established `ub()`
      treatment - all 3 mirrored tests passed on the first run.
    - `preladeni` (a transposition amount) needed Java's raw signed byte
      widening instead of the usual unsigned mask, matching C++'s
      `(char)` cast.
    - TMC envelope command 5's `rand()` call becomes
      `ThreadLocalRandom.current().nextInt(256)` - confirmed unreachable
      by the one existing test.
    - One added defensive bounds check (`line >= 1`) where C++'s
      equivalent would read an out-of-bounds array index on a
      zero-songline TMC file - not reachable by any current test, but
      Java throws where C++ silently corrupts memory.
    - Verified with `mvn -o test`: 350 tests pass (+3), no regressions. No
      C++ changes in this batch. Details in `plans/13_JAVA_PORT_PLAN.md`/
      `plans/14_JAVA_IMPORTER_PLAN.md`. Committed as `e93e323`.
  - **2026-09-26**: Thirty-first Java-port batch - `ModImporter` (Batch B
    of `plans/14_JAVA_IMPORTER_PLAN.md`), porting `CSong::ImportMODParseHeader`/
    `ImportMODApply` and their `TMODInstrumentMark`/`AtariVolume` helpers -
    completes both batches of the importer plan.
    - C++'s "continued stream access" wrinkle disappeared exactly as
      predicted during scoping - `ParseHeaderResult` carries the whole
      file, `apply()` indexes into it directly.
    - The `goto`-driven tone-portamento state machine restructured into a
      small `tonePortamento()` helper called from its two entry points
      plus a shared `if (noteWritten)` convergence block; the `Effect3:`
      shared-tail label needed only a single `if`, no helper; the two
      loop-breaking labels became one labelled `break songLoop;`. Not
      dynamically exercised by either language's test (both pass
      `portamento=false`) - verified correct by structural comparison,
      documented as a known test gap.
    - Found a second ambiguous dead statement (`lopend` computed, never
      assigned anywhere in C++) - flagged to the user, who chose to leave
      it characterized as-is on both sides.
    - Verified with `mvn -o test`: 353 tests pass (+3), no regressions. No
      C++ changes in this batch. Details in `plans/13_JAVA_PORT_PLAN.md`/
      `plans/14_JAVA_IMPORTER_PLAN.md`. Committed as `53900a2`.
  - **2026-09-26**: C++-only characterization batch (no Java changes),
    per the user's explicit request to characterize both remaining
    un-ported areas before continuing the Java port. Added one hand-traced
    test each for `CTrackClipboard`'s remaining 7 methods
    (`BlockAllOnOff`/`BlockExchangeClipboard`/`BlockClear`/
    `BlockRestoreFromBackup`/`BlockNoteTransposition`/`BlockInstrumentChange`/
    `BlockVolumeChange`) to `SongEditingTests.cpp` - all 7 passed on the
    first run. Full C++ Release|x64 rebuild + `RmtTests.exe`: 379 tests
    pass (+7), 0 regressions. `CTrackClipboard` is now fully C++-tested
    except `BlockEffect` (real MFC dialog, stays deferred). The Java port
    of these 7 methods is still not started. Details in
    `plans/13_JAVA_PORT_PLAN.md`/`plans/15_JAVA_SONGEDITING_PLAN.md`. Committed
    as `a9c31a9`.
  - **2026-09-26**: Second C++-only characterization batch, completing the
    user's "characterize both remaining areas" request:
    `IO_Instruments.cpp`/`IO_Tracks.cpp`'s TXT/RMW per-instrument/
    per-track serialization. Added
    `SaveTxtAndLoadTxtRoundTripNonEmptyInstrumentAndTrack`/
    `SaveRMWAndLoadRMWRoundTripNonEmptyInstrumentAndTrack` to
    `SongEditingTests.cpp`, the first tests to exercise `SaveAll`/`LoadAll`/
    `SaveInstrument`/`LoadInstrument`/`SaveTrack`/`LoadTrack` with real,
    non-default instrument/track data.
    - **Found and fixed a real bug** while writing the TXT test:
      `CInstruments::LoadInstrument()`'s TXT case had the identical
      "gap line before a segment bracket" defect as `CSong::LoadTxt()`'s
      own `[MODULE]`/`[SONG]` bug fixed earlier this session (GitHub issue
      #21) - a different function that fix never touched. It silently
      swallowed the following `[TRACK]` segment marker, so `LoadTrack()`
      was never called at all - confirmed by the new test failing (track
      left at its blank default) before the fix and passing after. Fixed
      the same way: skip a lone `'\n'` byte instead of handing it to
      `getline()`. `CTracks::LoadTrack()`'s own TXT case does not have
      this bug.
    - Diagnosed a crash-in-isolation red herring along the way: running
      the new TXT test alone via `--gtest_filter` hit the *already-known,
      already-documented* "crashes only in isolation" issue from earlier
      this session (`g_Atari.Init()`'s tuning-state dependency inside
      `ClearSong()`) - confirmed via targeted `fprintf`/`fflush`
      instrumentation (all removed afterward, verified via `grep -n DEBUG`
      returning nothing), then re-verified against the full suite where it
      doesn't occur. Not a new bug, not investigated further, matching
      this issue's established "known, unrelated, doesn't affect the real
      verification method" treatment.
    - Verified with a full C++ Release|x64 rebuild + `RmtTests.exe`: 381
      tests pass (+2), 0 regressions. The Java port of this serialization
      surface is still not started. Details in `plans/13_JAVA_PORT_PLAN.md`/
      `plans/15_JAVA_SONGEDITING_PLAN.md`. Committed as `654a140`.
  - **2026-09-26**: Thirty-second Java-port batch - `TrackClipboard` grows
    `blockAllOnOff`/`blockExchangeClipboard`/`blockClear`/
    `blockRestoreFromBackup`/`blockNoteTransposition`/
    `blockInstrumentChange`/`blockVolumeChange`, transcribed directly from
    the C++ characterization tests added earlier this session. All take
    `Tracks` explicitly; the guard-only `SetStatusBarText` call in the last
    three is dropped, matching `blockDeselect`'s own established omission.
    Verified with `mvn -o test`: 360 tests pass (+7), all passing on the
    first run, no regressions. `TrackClipboard` is now feature-complete
    except `BlockEffect` (not ported on the C++ side either). Details in
    `plans/13_JAVA_PORT_PLAN.md`/`plans/15_JAVA_SONGEDITING_PLAN.md`. Committed
    as `c5f9f58`.
  - **2026-09-26**: Thirty-third Java-port batch - `IO_Instruments.cpp`/
    `IO_Tracks.cpp`'s TXT/RMW per-instrument/per-track serialization,
    transcribed directly from the C++ characterization tests added in the
    previous batch. Added `saveAllTxt`/`loadInstrumentTxt`/`saveAllRmw`/
    `loadAllRmw` to `Instruments`, the equivalent four methods to `Tracks`,
    and wired all of them into `Song`'s `saveTxt`/`loadTxt`/`saveRMW`/
    `loadRMW` (previously these skipped the `[INSTRUMENT]`/`[TRACK]`
    segments entirely).
    - Carried the C++ `'\n'`-gap-line fix forward into `loadInstrumentTxt`;
      `loadTrackTxt` needed no such fix, matching `CTracks::LoadTrack()`'s
      already-correct C++ shape.
    - New `instrumentSectionToRmw`/`instrumentSectionFromRmw` private
      mapping helpers handle `InstrumentSection`'s enum-ordinal-vs-C++-
      backing-value mismatch (`NONE=-1, NAME=0, PARAMETERS=1, ENVELOPE=2,
      NOTETABLE=3`), needed only for RMW's byte-exact encoding.
    - Confirmed via C++ memory-layout analysis that RMW's envelope bytes
      need an outer-loop-over-column, inner-loop-over-row write/read order,
      independent of the C++ fill loop's own iteration order.
    - `Song`'s `charH4`/`charL4`/`hexstr`/`trimstr`/`readLine`/
      `nextSegment`/`Line` widened from `private` to package-private so
      `Instruments`/`Tracks` could reuse them, following the existing
      `nameToString` precedent - the trivial byte-level `writeIntLE`/
      `readIntLE`/`unsignedByte` helpers stay duplicated per class instead,
      matching this port's established split.
    - One test-only bug caught before commit: the new
      `saveTxtAndLoadTxtRoundTripNonEmptyInstrumentAndTrack` test initially
      asserted the loaded instrument name via the test file's
      `nameToString()` helper (which expects a NULL terminator); TXT
      loading correctly space-pads the name field with no NULL byte
      (matching C++'s `memset`+`strncpy`), so the assertion was fixed to
      use `new String(...).stripTrailing()`, the pattern already
      established by earlier tests for this exact space-padded-not-null-
      terminated scenario. No production code was at fault.
    - Verified with `mvn -o clean test`: 362 tests pass (+2), no
      regressions. Details in `plans/13_JAVA_PORT_PLAN.md`/
      `plans/15_JAVA_SONGEDITING_PLAN.md`. Committed as `6bc1ba9`.
  - **2026-09-26**: Wrote `plans/16_JAVA_PORT_NEXT_STEPS_PLAN.md`, a roadmap
    for the rest of the port, after the user asked for one. Corrected an
    overly-narrow claim in `plans/13_JAVA_PORT_PLAN.md`'s own "Next steps"
    section (it said "no further areas identified", but that was scoped
    only to `plans/15_JAVA_SONGEDITING_PLAN.md`'s own tracked list). A fresh
    audit - checking every plan doc's actual "DONE" status plus a direct
    `src/cpp`-vs-`src/java` file comparison - found two real remaining
    categories: **Phase A**, small model-layer gaps (`TracksEdit.cpp`'s
    7 never-characterized methods, unblocked now that `Undo` is real;
    `PokeyStream`'s real recording path and its five dependent SAP-R/LZSS/
    WAV/XEX export methods, all already C++-characterized but not yet
    ported), and **Phase B**, the entire Java UI layer (`com.wudsn.tools.
    rmt.ui` doesn't exist yet at all) - `plans/12_UI_SURVEY_PLAN.md` already
    resolved keeping the bitmap-font look and the debug overlays and
    deferring MIDI, but the rendering/repaint model and the Java UI toolkit
    choice are still open. Committed as `17a46f5`.
  - **2026-09-26**: Thirty-fourth Java-port batch (Phase A item 1 of the
    new roadmap) - `TracksEdit.cpp`'s 7 methods
    (`DelNoteInstrVolSpeed`/`SetNoteInstrVol`/`SetInstr`/`SetVol`/
    `SetSpeed`/`SetEnd`/`SetGo`), the last un-triaged file already linked
    into `RmtTests.vcxproj`. Deferred twice earlier this port (Sixth/
    Seventh ported batches) for needing `g_Undo`, back when `Undo` was
    still a no-op test stub - now unblocked.
    - C++-characterized first: 30 new tests in `SongEditingTests.cpp`
      (whose `g_Tracks`/`g_Undo` fixture is already real and wired
      together, unlike `TracksTests.cpp`'s local, `g_Undo`-independent
      fixture), hand-traced and all passing on the first run, including
      full branch coverage of `SetNoteInstrVol`'s `g_respectvolume` logic
      and `SetEnd`'s "toggle back to max length when the given line
      already equals the current length" behavior. `g_respectvolume`
      (already stubbed `FALSE`) is now also reset in
      `SongEditingTest::SetUp()` for determinism. Full C++ Release|x64
      rebuild + `RmtTests.exe`: 411 tests pass (+30), 0 regressions.
    - Ported to `Tracks.java` next: `g_Undo` becomes an explicit `Undo`
      parameter and `g_respectvolume` an explicit `respectVolume` boolean
      parameter on `setNoteInstrVol`, matching this port's established
      "C++ global -> explicit parameter" idiom. Tests (`SongEditingTest`,
      extended) mirror the 30 new C++ tests exactly. Verified with
      `mvn -o clean test`: 392 tests pass (+30), all green on the first
      run, no regressions. Details in `plans/13_JAVA_PORT_PLAN.md`/
      `plans/16_JAVA_PORT_NEXT_STEPS_PLAN.md`. Committed as `caf07aa`.
  - **2026-09-26**: Vendored `asap.jar`/`asap-8.0.0-java-src.zip` into
    `lib/java/` (copied from `C:\jac\system\WWW\Sites\asma.atari.org\java\lib`,
    per the user's explicit direction: Phase A items 2-4's real CPU/POKEY
    emulation "shall later be done using the 'asap' library"). Analyzed
    the vendored Java source (package `net.sf.asap`) to find the
    integration path: `Cpu6502.java`/`Pokey`/`PokeyChannel`/
    `PokeyPair.java` are a complete, portable, pure-software 6502+POKEY
    emulator with no native/DLL dependency (unlike C++'s own `C6502`,
    which wraps `sa_c6502.dll` - the exact reason
    `AtariTrackerDriver.play()`/`setPokey()`/`silence()` are permanently
    no-ops in the Java port today). ASAP's public API alone
    (`load`/`playSong`/`generate`) already suffices, unmodified, to
    replace `ExportWAV` entirely (export module bytes via the
    already-ported `RmtExporter`, let ASAP render real PCM). For the
    SAP-R-style raw register dump the other four exports need, confirmed
    the needed shadow values (`PokeyChannel.audf`/`.audc`, `Pokey.audctl`)
    are package-private (not `private`) and reachable from `ASAP.java`
    itself, which is the only class keeping its own `cpu`/`pokeys` fields
    `private` - planned fix is one small added method on the vendored
    `ASAP.java`, then vendoring a custom-compiled `asap.jar` from that
    patched source, mirroring `src/cpp/asap/asap-patch.h`/`.cpp`'s
    existing extension-point pattern for this same upstream library. Full
    write-up in `plans/16_JAVA_PORT_NEXT_STEPS_PLAN.md`'s Phase A item 2.
    Analysis only - no code changes yet, since this item hasn't started.
    Committed as `2971772`.
  - **2026-09-26**: Phase A item 5 (closing sanity sweep) - done, clean, no
    code changes. Extracted every method defined across all 44 non-stub
    `.cpp` files linked into `RmtTests.vcxproj` (387 names), checked each
    for a direct-call match in `src/cpp/test/*.cpp`, then hand-traced every
    one of the 133 "misses" instead of trusting the raw heuristic. All
    resolved to either indirect coverage (a differently-named public
    wrapper, e.g. `ClipboardCore.cpp`'s methods only reached via `Song`'s
    `BLOCKSETBEGIN`-style wrappers, or `lzss_sap.cpp`'s whole internal
    LZSS engine only reached via `LZSS_SAP()`; or an unqualified same-class
    internal call invisible to the heuristic, e.g. `PokeyCore.cpp`/
    `WaveFile.cpp`'s methods, confirmed genuinely exercised for real via
    `ExportWAV`'s test, which writes and validates an actual temp `.wav`
    file) or deliberate, already-documented exclusion (`PokeyController.cpp`,
    the Pokey-explorer debug submenu's real MFC command handlers - per
    `plans/12_UI_SURVEY_PLAN.md`, "developer/diagnostic UI, not core
    end-user functionality"). The only genuine gap found was
    `PokeyStream.cpp`'s `StartRecording`/`FinishedRecording` - already
    tracked as Phase A item 2, not new. Full write-up in
    `plans/16_JAVA_PORT_NEXT_STEPS_PLAN.md`'s item 5. This closes out
    Phase A's model-layer scope except for the ASAP-dependent items.
    Committed as `40c7007`.
  - **2026-09-26**: Thirty-fifth Java-port batch, per the user's explicit
    "do the asap part" request - Phase A items 2-3: real CPU/POKEY
    emulation via ASAP, `PokeyStream`'s real body, and
    `Song.dumpSongToPokeyStream`.
    - Vendored ASAP's official Java source as actual project source
      (`src/java/net/sf/asap/`, copied from the already-vendored
      `asap-8.0.0-java-src.zip`, plus its `.obx` resources from
      `asap.jar`) rather than rebuilding a custom jar - `pom.xml`'s
      existing `src/java` resource rule already handles non-`.java`
      resources, so this needed no build changes at all.
    - Patched two small, clearly-marked methods onto the vendored
      `ASAP.java` (mirroring `asap-patch.h`/`.cpp`'s own extension-point
      pattern for this same library): `stepFrame()` (exposes the private
      `doFrame()`) and `getPokeyRegisterShadow(chip, offset)` (the raw
      last-poked register byte - real POKEY registers are write-only, so
      the public API can't read this). Also widened `Pokey.skctl` from
      `private` to package-private (matching its sibling fields) since
      the Two-Tone-mode check needs it.
    - New `AsapEmulator` class wraps `net.sf.asap.ASAP` for this need,
      deliberately kept separate from `AtariTrackerDriver` (which models
      live keyboard preview with no whole module involved, not "the
      real CPU" in general - two different operations that only share
      one CPU in C++ because C++ has a single universal one either way).
    - `PokeyStream.startRecording`/`record`/`finishedRecording` now have
      real bodies; `writeToFile` redesigned as `getFrameBytes` (returns
      `byte[]`, matching this port's stream-avoidance idiom).
    - `Song.dumpSongToPokeyStream` ported, replacing every
      `g_AtariTrackerDriver->Play()` call with `AsapEmulator#stepFrame()`
      since Java has no loaded-driver memory for a real JSR - instead
      exports the current song to a real RMT module byte array each time
      and hands it to a fresh `AsapEmulator`, which independently decodes
      and plays it back (a full re-derivation of "what's sounding now",
      not a replay of the model's own note-selection logic). Needed
      threading a new `PokeyStream`-taking overload through
      `songPlayNextLine`/`playVBI`/`play`/`playBeat` (pre-existing
      overloads delegate `null`, unchanged for everyone else) so
      `CallFromPlay`/`TrackSongLine`'s loop-detection hooks fire.
    - **Debugging story, three real findings along the way**: (1) a test
      track that set note+instrument but no volume silently encoded as a
      blank/paused row instead of a real note event (needs all three
      `>=0`) - not a bug, just an under-specified first test. (2) ASAP's
      own RMT parser correctly rejects a module as "no songs found" when
      every instrument used has a silent (all-zero) envelope, since the
      song then has no real audible duration - again correct behavior,
      fixed by giving the test instrument a real envelope volume,
      matching the established `saveTxtAndLoadTxtRoundTripNonEmptyInstrumentAndTrack`
      test's own pattern for "non-empty instrument." (3) A genuine bug in
      the new Java code itself: `dumpSongToPokeyStream` initially called
      `pokeyStream.finishedRecording()`/`channelControl.setAllChannelsOn()`
      at the end, based on a wrong assumption - re-reading
      `Song_DumpSong.cpp` showed `DumpSongToPokeyStream()` never calls
      `FinishedRecording()` in the real C++ call chain either
      (`CSongContainer`'s destructor, the only plausible caller, is
      empty) - fixed by removing both calls to match C++ exactly.
    - Test (`SongEditingTest`, extended):
      `dumpSongToPokeyStreamRecordsPokeyRegisterDataUntilTheLoopPoint` -
      builds a real looping song and confirms actual non-zero POKEY
      register bytes were captured, a stronger check than
      `SongEditingTests.cpp`'s own C++ test can make (its own
      `AtariTrackerDriver::Play()` is also a no-op there, via the C++
      test binary's no-op JSR stub - see `AtariStub.cpp`). Verified with
      `mvn -o clean test`: 393 tests pass (+1), no regressions.
    - Full write-up in `plans/13_JAVA_PORT_PLAN.md`'s thirty-fifth ported
      batch and `plans/16_JAVA_PORT_NEXT_STEPS_PLAN.md`'s Phase A items 2-3.
      Committed as `9d96136`.
  - **2026-09-26**: Thirty-sixth Java-port batch, continuing "do the asap
    part" - three of the five dependent export methods: new
    `SapFileExporter.exportSapR`, `SongExporter.exportLzss`, and
    `WaveFileExporter.exportWav`.
    - `exportSapR`/`exportLzss` are direct ports (SAP-R header +
      `PokeyStream#getFrameBytes`; full/intro/loop sections through the
      already-ported `CompressLzss`). `ExportCompactLZSS` deliberately not
      ported - its own C++ source calls itself "TODO: What is this?
      Currently unused?" and its body has dead logic and writes a
      diagnostic dump, not a real export.
    - `exportWav` is a deliberate idiomatic substitution: C++'s `ExportWAV`
      replays the recorded `PokeyStream` into a *separate* software POKEY
      audio synthesizer (`CXPokey`/`PokeyRenderer.h/.cpp`/`PokeyCore.cpp`)
      that was never ported to Java. Rather than porting a second,
      redundant synthesizer from scratch, this exports the song to a real
      RMT module and lets `AsapEmulator`'s underlying ASAP render the WAV
      directly (`load`/`playSong`/`generate`/`getWavHeader`) - same
      observable result, no duplicated engineering effort.
    - Found and worked around a real, previously-unexercised edge case in
      the already-shipped `CompressLzss`: a zero-length section (a short
      loop's `thirdCountPoint` can legitimately be 0) throws
      `ArrayIndexOutOfBoundsException` there. Already a documented,
      deliberately-kept "fragile contract" (C++ has silent UB for
      malformed lengths instead, per `CompressLzss`'s own class javadoc
      from an earlier session) - guarded at the new caller
      (`SongExporter#compressSection`) rather than reopening that
      decision.
    - Tests (`SongEditingTest`, extended) confirm real, non-zero captured
      data for SAP-R and a valid RIFF/WAVE file for WAV - both stronger
      checks than the C++ characterization tests can make, for the same
      reason as `dumpSongToPokeyStream`'s own test (the C++ test binary's
      CPU is also a no-op stub). The LZSS test mirrors
      `SongEditingTests.cpp`'s own honest "never crosses the compression
      threshold in this test environment" finding, for a different
      underlying reason (real audio, but a minimal 2-line loop still
      lacks enough content to compress past 16 bytes).
    - Verified with `mvn -o clean test`: 396 tests pass (+3), no
      regressions. `ExportSAP_B_LZSS`/`ExportXEX_LZSS` remain unported -
      both need a new `VUPlayer` class and real on-disk resource-file
      loading, a distinctly bigger chunk of work, flagged for a decision
      on whether to continue now. Full write-up in
      `plans/13_JAVA_PORT_PLAN.md`'s thirty-sixth ported batch and
      `plans/16_JAVA_PORT_NEXT_STEPS_PLAN.md`'s Phase A item 4. Committed as
      `f209496`.
  - **2026-09-26**: Thirty-seventh Java-port batch, continuing "do the asap
    part" - the fourth of the five export methods: `ExportSAP_B_LZSS`.
    - New `AtariIO.loadBinaryFile(byte[] data, byte[] memory)` reuses the
      already-tested `loadBinaryBlock` in a loop, mirroring C++'s own
      `LoadBinaryFile` minus the file-open step itself (the caller reads
      the file via `Files.readAllBytes`, matching this class's byte-array
      idiom).
    - New `VUPlayer` class ports only the dozen memory-address constants
      `PatchMemoryForSAP_B` actually needs (not all of `lzssp.h`'s several
      hundred - the rest are for VUPlayer's own UI/keyboard-handling code,
      unrelated to this export path) plus `patchMemoryForSapB` itself -
      including a pre-existing C++ oddity preserved as-is:
      `memory[LZSS_POINTER]` gets written eight times in a row to the
      *same* address (only the last write survives), already self-flagged
      by the original author's own two `// TODO: Why same address?`
      comments - a known, already-acknowledged oddity in low-priority
      ("hacked up") code, not a provable bug to unilaterally fix mid-port.
    - `SapFileExporter.exportSapBLzss` reads the real, checked-in
      `rmt/resources/players/vu_player_v2.obx` from disk - the first real
      on-disk file dependency in this Java test suite, working because
      Maven always runs with the repository root as the working
      directory (same convention the C++ test suite's own
      `AtariBinariesStub.cpp` uses, just resolved differently).
    - Test (`SongEditingTest`, extended):
      `exportSapBLzssLoadsTheRealResourceAndWritesPatchedMemory` - passed
      on the first real run. Verified with `mvn -o clean test`: 397 tests
      pass (+1), no regressions.
    - `ExportXEX_LZSS` is the only export method still unported - a
      distinctly bigger, more novel undertaking (needs
      `BruteforceOptimalLZSS`, a new `CXEXFile`-equivalent class,
      `CSong::GetSubsongParts`, a *different* embedded-resource loading
      path for the VU player binary, and per-subsong
      `dumpSongToPokeyStream` looping) - flagged for a decision on whether
      to continue. Full write-up in `plans/13_JAVA_PORT_PLAN.md`'s
      thirty-seventh ported batch and
      `plans/16_JAVA_PORT_NEXT_STEPS_PLAN.md`'s Phase A item 4. Committed as
      `fd7c063`.
  - **2026-09-26**: Thirty-eighth Java-port batch, completing "do the asap
    part" - the fifth and last dependent export method: `ExportXEX_LZSS`,
    finishing Phase A item 4 entirely.
    - New `XexFile` class (a plain mutable settings struct matching
      `CXEXFile`, minus its two dead fields - `songname`/`currentTime` are
      set by `InitFromSong` but never read anywhere in the export path).
    - New `SongExporter.exportXexLzss`: parses the already-ported
      `Song#getSubsongParts`'s hex-token-string result into subtune
      songline numbers, calls `dumpSongToPokeyStream` once per subsong
      (`PLAY_FROM` mode this time, not `PLAY_SONG` - the only export
      method that plays from a specific point rather than the whole song
      from the start), bruteforces the best `SapROptimization` per section
      (new `bruteforceOptimalLzss` - tries all 8 variants, keeps the
      shortest), and reconstructs the VUPlayer XEX binary byte-for-byte,
      including a new `strToAtariVideo` (ASCII to Atari screen-code
      conversion) and the NTSC/PAL region patch.
    - Confirmed C++'s two different loading mechanisms for
      `vu_player_v2.obx` (`CRmtAtariBinaries::GetVUPlayerBinary`'s
      embedded-resource load here vs. `ExportSAP_B_LZSS`'s real on-disk
      `std::ifstream` load) read the exact same real file with no
      observable difference - reused `SapFileExporter`'s existing
      `VU_PLAYER_PATH`/loading logic rather than porting a second,
      redundant resource-loading mechanism for the same file.
    - Test (`SongEditingTest`, extended):
      `exportXexLzssLoadsTheRealResourceAndWritesReconstructedBinary` -
      passed on the first real run despite being the largest, most
      involved method ported this session. Verified with
      `mvn -o clean test`: 398 tests pass (+1), no regressions.
    - **Phase A of `plans/16_JAVA_PORT_NEXT_STEPS_PLAN.md` is now fully
      DONE**: all five items closed (`TracksEdit.cpp`, real CPU/POKEY
      emulation via ASAP, all five export methods, and the closing sanity
      sweep). The Java port's model layer has no further known-unported,
      real, meaningful C++ behavior left - the only remaining work is
      Phase B, the entire not-yet-started Java UI layer. Full write-up in
      `plans/13_JAVA_PORT_PLAN.md`'s thirty-eighth ported batch and
      `plans/16_JAVA_PORT_NEXT_STEPS_PLAN.md`'s Phase A item 4/closing
      summary. Committed as `08d9465`.
  - **2026-09-26**: Wrote `plans/17_JAVA_UI_PORT_HANDOVER.md` at the user's
    request - a short entry point for a fresh session picking up Phase B
    (the Java UI port), written for the switch to the Fable model: current
    state, the hard "must match today's Windows UI" requirement, the
    architecture decisions already resolved, what was still open, a
    recommended first slice, and the working conventions from the model
    port. Committed as `70681d1`.
  - **2026-09-26** (Fable 5.1 from here on): Phase B kicked off. Read the
    handover and `plans/12_UI_SURVEY_PLAN.md` in full, then the C++ paint/
    input/config code they describe, the resource files, and the `dis6502`
    precedent. Wrote `plans/18_JAVA_UI_PORT_PLAN.md` - the dedicated Phase B
    plan (batches B0-B9) - and put the open decisions to the user.
    - Key findings that shaped it: `dis6502` is pure Swing on
      `com.wudsn.tools.base.gui` (already a dependency of this project),
      and its `GraphicPanel` uses RMT's exact rendering model (fixed
      off-screen `BufferedImage`, nearest-neighbor scaled blit); the glyph
      sheet already exists as a plain `src/cpp/res/gfx-8x16.bmp`
      (1024x240, decoded: 128 glyphs x 15 color bands, mini font in the
      two skipped bands 7-8, icons at y=122), so the "export IDB_GFX" item
      is moot; options persist to a plain `rmt.ini`, not the registry;
      and the Java model has *no* real-time audio path at all - the
      biggest question the handover did not list.
    - Decisions taken by the user, all per recommendation: Swing + WUDSN
      Base gui; timer-driven full redraw (~16 ms `javax.swing.Timer`);
      keep the `rmt.ini` format (Preferences only for window geometry);
      UI first, real-time audio deferred to B8. The user will provide
      reference `Rmt.exe` screenshots for pixel-level golden-image tests,
      and runs Windows at **150% display scaling** - the exact setting of
      dis6502's known HiDPI bug, so B1's on-screen check at 150% is a hard
      gate.
    - Design deviations recorded in the plan: `GUI_Song.cpp`'s key
      handlers and `GUI_Instruments.cpp`'s editor drawing move from the
      model classes (`CSong`/`CInstruments` in C++) into `ui` classes that
      take the model, keeping `Song`/`Instruments` UI-free. Committed as
      `f193d1b`.
  - **2026-09-26**: Phase B, batch B0 (foundation, no window yet) - the
    first Java UI code. New `org.atari.raster.rmt.ui` package with
    `CanvasXY`/`Canvas` (the bitmap-font renderer, ported from
    `CanvasXY.cpp`/`Canvas.cpp` with identical blit arithmetic against
    the checked-in `gfx-8x16.bmp`), `TextColor`/`TextMiniColor`/`RgbColor`/
    `RmtScreenLayout`, `UiState` (C++'s transient `Global.h` UI globals as
    one explicitly-passed holder), and `CursorLoader` (a small runtime
    parser for the five original `.cur` files, chosen over a lossy PNG
    conversion). `EditMode` added to `model` next to its `General.h`
    siblings. Verified the glyph-sheet geometry from the file itself
    (1024x240 8-bit, background = `RgbColor.BACKGROUND`) before relying
    on it.
    - Tests are analytic rather than golden images: every drawn cell is
      compared pixel-for-pixel against the exact sheet cell C++'s
      `BitBlt` would have copied, across all color bands, both mini-font
      bands, the icon strip, each method's own space-skipping rule
      (`TextXY`/`TextXYCol`/`TextMiniXY`/`TextXYFull` skip, `TextXYSelN`/
      `TextDownXY` draw), and prove-mode/hover recoloring. 27 new tests,
      all passing on the first run; 425 total, no regressions.
    - Two B0 items moved on purpose: the `RmtApplication` stub to B1 and
      `RmtOptions` to B6 - neither had anything real to do yet, and this
      port avoids placeholder code.
    - The user's first `Rmt.exe` reference screenshots (dropped into
      `plans/B1/`) moved to `test-resources/ui-reference/` (repo-root
      `test-resources/`, as dis6502 has; plans/ stays for plan documents)
      and renamed to a scenario/view scheme; its `README.md` records the
      capture conditions (150% Windows scaling - so RMT-200% captures are
      the lossless ones) and proposes names for the missing captures.
      B0 committed as `68f58b3`, the placeholder PNGs as `17d8804`.
  - **2026-09-26**: Phase B, batch B1 (the vertical slice) - the first
    Java window. `RmtSession` (the model composition root, wired as
    `CRmtApp::InitInstance()` does, plus `UiState`/`RmtOptions` and the
    one mutable `tracks4_8`), `RmtOptions` (the persisted `Global.h`
    options the drawing reads, defaults = `ResetRMTConfig()`; `rmt.ini`
    itself stays B6), `SongUI` (`DrawInfo`/`DrawSong`/`DrawTracks`/
    `DrawPlayTimeCounter` ported in full, `DrawVolumeAnalyzer`/
    `DrawInstrument`/the FPS read-out left as B2 no-ops), `TracksControl`,
    `SongInput` (navigation arms of `OnKeyDown`/`TrackKey`/`SongKey`
    only, every data-changing arm marked `// B3`), `TrackerPanel`
    (off-screen canvas, `Resize()` math, 16/16/15 ms `javax.swing.Timer`,
    dirty flag, nearest-neighbor blit), `RmtMainWindow` (WUDSN Base
    `MainWindow` + `StatusBar`, min size, `SetRMTTitle`), `RmtApplication`
    (`main`, `.rmt` from the command line, `-Drmt.scaling` stopgap).
    Model additions: `Song` read accessors, `Song.loadRMT` now returns
    `LoadRmtResult(success, tracks4_8)` like `loadRMW`, `setLoadedFile`
    (`FileOpen()`'s tail), `TrackClipboard` selection getters.
    - **Verified against the user's `Rmt.exe` screenshots, pixel for
      pixel.** `SongUITest` locates the client area in the whole-desktop
      captures and compares the Java frame (rendered headless at the
      captures' own `GW=1278 GH=0654`) in the info area, SONG block and
      tracks screen, excluding only B2's analyzer strip and FPS text.
      Both `song1-mono/tracks-scale200` and `song0-empty/tracks-scale200`
      match exactly after one fix (below). Plus Java-only golden PNGs of
      the whole frame under `src/java/test/.../ui/golden/`.
    - **Screenshot geometry corrected**: the captures show `Rmt.exe`
      renders 1:1 in device pixels even at 150% Windows scaling (`GW=1278`
      for a 2556-pixel client area at RMT 200%), i.e. it *is* DPI-aware
      in effect. So a `-scale200` capture is an exact 2x2-block image and
      a plain one is 1:1 - both lossless; the earlier "3 device pixels,
      100% is lossy" reasoning in the README was wrong and is replaced.
    - **Real bug found by the comparison and fixed model-wide**: every
      Java port of a C++ `CInstruments::Update()` call site had dropped
      the call as "writes to Atari RAM", but `Update()` also runs
      `RecalculateFlag()`, so the info area's instrument hints
      (`AUTOFILTER(1+3)` etc.) never appeared after loading. Added
      `Instruments.update()` (the flag half; the memory write stays B8)
      and call it where C++ does: `decodeModule`,
      `renumberAllInstruments`, `instrPaste`, `setEnvelopeVolume`,
      `loadInstrumentTxt`/`loadInstrumentRmw`, both `Undo` instrument
      cases, `ModImporter`, `TmcImporter`.
    - **HiDPI gate passed on the user's 150% display**: `TrackerPanel`
      reads the device scale from the paint transform, sizes the canvas
      from the panel's *device* size and blits through an integer
      translation only (never through the 1.5x), exactly as `Rmt.exe`
      does. Checked live with `Delta.rmt` at RMT 100% (crisp 1:1) and 200%
      (4x-magnified crop shows uniform 2x2 blocks, no irregular rows).
    - 446 tests (+21: `SongUITest` 4, `SongInputTest` 10,
      `RmtSessionTest` 4, `TrackerPanelTest` 3), no regressions.
      `loadRMT`'s two existing tests adapted to the new result record.
      Committed as `e240703` together with the reference screenshots.
  - **2026-09-26**: Phase B, batch B2 (complete drawing). `SongUI.
    drawVolumeAnalyzer` (analyzer boxes/bars, channel-join hooks, the
    mini register read-outs, and the tuning/POKEY panel), `PokeyView`
    (full `CPokeyView::Draw` except the Pokey Explorer rows, which need
    the unported `CPokeyController` - B3), `AtariView` (kept exactly as
    unreachable as C++'s compile-time-`FALSE` `DEBUG_MEMORY`),
    `InstrumentsUI` (the drawing half of `GUI_Instruments.cpp`: the whole
    instrument editor incl. the edit-help line; `CursorGoto`/`GetGUIArea`
    are B4), the FPS counter (`TrackerPanel.getFPS()` ported as is,
    `UiState.lastFps`), `RmtSession` now builds `Atari`/`Tuning*`/the
    driver routines as `InitInstance()` does. `Instruments.SHPAR`'s
    display columns (name, x, y) live in `InstrumentsUI.SHPAR`/`SHENV`;
    the value/TXT columns stay in the model.
    - **Every `Rmt.exe` capture that can be reproduced now matches the
      whole client area** (only the FPS text excluded): both tracks
      screens, both instrument screens (`song1-mono/instruments` only in
      its play-independent regions - it was captured mid-song), and the
      GOTO capture, which was taken *during playback with follow-play*:
      its row offsets identified the exact smooth-scroll state (track
      rows +8 = `speeda 10` of speed $0A, song rows -7 = play line $3C,
      which is also the cursor line), so it verifies GOTO rendering in
      both blocks *and* smooth scrolling. `Song.setSpeeda` added for that.
    - Two real differences found by the full-frame comparison and fixed
      in `CanvasXY`/`Canvas`: **GDI's `LineTo` excludes its end pixel**
      (Java's `drawLine` includes it - every track/envelope line was one
      pixel too long) and **`printf` must use the C locale**
      (`Locale.ROOT`, else "440,84HZ" on a German machine). Plus C's
      `%0hX` has no Java spelling (`%X`).
    - A C++ dead branch documented rather than ported: `DrawVolumeAnalyzer`'s
      instrument-mode variant is nested inside `if (g_active_ti ==
      PART_TRACKS)`, so it never runs - the instrument captures confirm
      nothing is drawn there.
    - The POKEY register shadow: `RmtSession` sets `SKCTL = 3` at
      `$D20F`/`$D21F`, what the driver's init routine leaves there in
      C++ (no 6502 runs here until B8); everything else is 0, as in the
      captures.
    - 451 tests (+5: `SongUITest` 4, `CanvasXYTest` 1), no regressions.
      Committed as `1ae8501`.
  - **2026-09-26**: Phase B, batch B3 (full keyboard input). `SongInput`
    now ports `CRmtView::OnKeyDown()`/`OnKeyUp()` and all five
    `GUI_Song.cpp` handlers (`InfoKey`/`InstrKey`/`ProveKey`/`TrackKey`/
    `SongKey`) plus `CursorToSpeedColumn`/`IsNotAMovementVKey` one to one;
    `TextFieldEditor` ports `EditText()` (GuiHelpers.cpp);
    `UiState.switchEditMode` ports `SwitchEditMode()` (Global.cpp).
    `VirtualKey` holds the Windows VK codes: the C++ switches and the
    `Keyboard2NoteMapping` tables are all in Windows codes, and Swing's
    differ for Enter/Insert/Delete/Alt and the punctuation keys, so
    `TrackerPanel` translates once and everything behind it stays
    literal (tests feed VK codes too). Caps Lock is read per key event
    into `UiState.capsLock`. `InstrumentsUI.SHPAR`/`SHENV` gained the
    key-navigation and +/- step columns of C++'s tables.
    - Model hooks added for the handlers: `Song`'s `Song.h` inline
      delegators (`trackSet*/trackGet*/trackDel*`, `octaveUp/Down`,
      `volumeUp/Down`, `setPlayPressedTonesV`) with Undo/respect-volume as
      parameters, setters for the info-area values, `setQuantization`,
      `setTrackActiveCur`, `getSongNameChars`; `RmtOptions` gained the
      four `g_keyboard_*` options (defaults per `ResetRMTConfig()`).
    - Deliberately not ported: the media keys (B8), the `FlaToCha`
      Shift+numpad scan-code workaround (Swing reports numpad keys by
      code), `ProveKey`'s Pokey Explorer branch (`CPokeyController`
      unported), and the two dialogs behind Ctrl+O/Ctrl+F (B7). Two C++
      quirks kept as is and commented: `InfoKey`'s TAB post-increment
      (`(EditArea)(value++)` assigns the unchanged value, so TAB only
      wraps from the last field) and `TrackDown()`'s BOOL result being
      re-derived for Enter's "force a line move" hack.
    - One expectation of mine corrected by the port itself: two-digit
      instrument parameters shift hex digits in (`"01"` + `3` = `"13"`),
      they don't replace the value.
    - 72 ui tests (`SongInputTest` 20, `TextFieldEditorTest` 6,
      `VirtualKeyTest` 3 new); 470 total. Committed as `b3ee4fa`.
  - **2026-09-26**: Phase B, batch B4 (mouse). `MouseInput` ports
    `CRmtView::MouseAction()` (every hit rectangle, in C++'s
    `CRect(l,t,r,b)`/`PtInRect` semantics) with its button/move/wheel
    handlers and the `CSong::*CursorGoto` helpers (`TrackCursorGoto`,
    `SongCursorGoto` incl. the play+follow restart, the three
    `InfoCursorGoto*` field selectors and the three popup openers);
    `InstrumentsUI` gained `getGUIArea`/`cursorGoto` (the hit-testing half
    of GUI_Instruments.cpp, zones 0-8); `PopupSelectors` ports the three
    click-positioned popups (`COctaveSelectDlg`/`CVolumeSelectDlg`/
    `CInstrumentSelectDlg`) as small modal `JDialog`s; `RmtCursor` +
    `UiState.cursor` carry the `SetCursor` choice, `TrackerPanel` maps
    them to the five original `.cur` files via `CursorLoader`.
    `UiState.mouseButtonsHeld` is `g_mousebutt` (dragging over the
    envelope keeps drawing). `RmtSession.setNTSC` ports
    `CRmtView::SetNTSC`.
    - Windows and popups stay behind `MouseInput.Callbacks`, implemented
      by the panel, so the hit-testing is tested headless
      (`MouseInputTest`, 11 tests, with a recording stub). Two info-field
      commands are callbacks left for B7 (`changeMaxTrackLength` needs
      its dialog; `switchMonoStereo` needs the real question box behind
      `Songswitch4_8`); NTSC toggling works.
    - Mouse coordinates: Swing point -> device pixels (the paint's
      transform scale) -> `INVERSE_SCALE` logical, the exact reverse for
      positioning a popup at the click (C++ offsets the popup by
      `(-73, -7)`/`(-146, -7)` in *logical* units used as screen pixels -
      an RMT quirk kept, but applied to real screen coordinates). Wheel:
      Swing rotation x -120 = Windows `zDelta`.
    - 481 tests (+11), no regressions. Committed as `2ed49dd`.
  - **2026-09-26**: Phase B, batch B5 (menus, toolbars, accelerators,
    status line). `Actions` (+ `Actions.properties`, WUDSN `NLS`
    convention as in dis6502) holds every menu/command of Rmt.rc's
    `IDR_MAIN_WINDOW` MENU/TOOLBAR/ACCELERATORS with the STRINGTABLE
    prompts as tool tips; `RmtCommandId` is the command-ID enum (with
    "checkable" and "accelerator is only a hint" flags); `RmtMainMenu`
    builds the 12 menus item for item (incl. the Pokey tree, whose 8
    register submenus share 4 commands); `RmtToolBars` the two toolbars
    from the original 32x30 strips (RGB 192,192,192 transparent) plus
    the skip-lines combo; `RmtCommands` ports the ~200 `CRmtView::On*`
    handlers and their `OnUpdate*` enable/check/text logic;
    `SwingMessages` implements the new `Messages.Handler` (the model's
    `Send*Message` now has the `MessageBox` branch C++ has when a window
    exists). `RmtMainWindow` wires it all; states refresh on every
    display tick (MFC's idle `ON_UPDATE_COMMAND_UI`).
    - **Accelerators dispatch before the tracker's key handlers**, as
      MFC's `TranslateAccelerator` does - which is why numpad +/- adjust
      the step size rather than the volume in RMT 1.35 (they are in the
      accelerator table without a modifier), reproduced as is. Menu
      labels that show a key the tracker handles itself (Song's
      Ctrl+U/I/P/O/N/D, Block's Ctrl+B/C/V/M/X/E/F/A/Del, Shift+Ctrl+S)
      are displayed but never dispatched (`acceleratorIsHint`); C++'s
      stale label keys ("Edit Tracks\tF1" while F2 is the accelerator)
      give way to the real table.
    - Not wired yet, each reporting itself in the status line: the
      file/dialog commands (B7), Options (B6), About/local help (B7/B9);
      MIDI and the Pokey Explorer register commands are disabled
      (unported). Playback commands set the play state exactly as C++
      does; the timer routine that advances/sounds it is B8.
    - 497 tests (+16: `RmtCommandsTest` 13, `RmtMainMenuTest` 3), no
      regressions. Committed as `58aff2b`.
  - **2026-09-27**: The last five dialog captures arrived
    (`insert-copy-clone`, `tracks-order`, `instrument-change`,
    `renumber-instruments`, `renumber-tracks`); `test-resources/ui-reference/`
    is complete, its README's "Missing" section is gone.
  - **2026-09-27**: Phase B, batch B6 (options & persistence). `RmtConfig`
    ports `ReadRMTConfig`/`WriteRMTConfig`/`ResetRMTConfig` and
    `ReadTuningConfig`/`WriteTuningConfig` byte for byte (header, section
    comments, `NAME = value`, `NAME = num / den`; the parser's
    `tmp[-1] = 0`/`tmp + 2` splitting and C's `atoi`/`atof` reproduced,
    `RmtConfigTest` pins the default files' exact text). `RmtOptions` now
    holds every `rmt.ini` value (`reset()` = `ResetRMTConfig`'s
    assignments). The folder is C++'s program folder: the jar's folder, or
    the working directory when run from `target/classes` (`-Drmt.config.dir`
    overrides; `/rmt.ini`, `/tuning.ini` are git-ignored). `RmtMainWindow`
    reads both files in its constructor (`OnInitialUpdate`'s order:
    config, tuning, `ChangeViewElements(0)`), the View toggles write
    `rmt.ini` at once (`ChangeViewElements(1)`), exit writes both files
    and the window geometry (`RmtWindowPreferences` = `CMainFrame`'s
    registry `Frame` values on `java.util.prefs`, restored only when
    complete and clamped onto the screen as `PreCreateWindow` does); the
    close box now goes through `FILE_EXIT` like `CMainFrame::OnClose`.
    Dialogs on WUDSN `ModalDialog`: `OptionsDialog` (four group boxes as
    `IDD_OPTIONS`, `DDV` ranges 100-300/2-256/0-15 with MFC's prompts,
    Touch-response enabling the offset field, driver/keyboard combos with
    C++'s entries, "None" + Java Sound's MIDI inputs), `OptionsPathsDialog`
    (three folders + Browse, OK clears the last-used paths),
    `TuningDialog` (base tuning 6.875-7040, the 12 base notes and 30
    temperaments decoded from Rmt.rc's `DLGINIT`, 13 ratio pairs;
    Test/Reset/OK/Cancel semantics as `TuningDlg`, incl. Reset leaving the
    fields alone). `OptionsValues` is `COptionsDialog`'s `m_*` set as a
    plain holder so `RmtCommands.applyOptions` (`OnToolsOptions`'s
    epilogue: rescale on a scaling change, `SetNTSC`, driver reload) is
    headless-testable; `Host.editOptions`/`rescale` are the two new window
    services. New NLS repositories `DataTypes`/`Texts` (dis6502
    convention) hold the field labels and titles.
    - **Bug fixed in both languages**: `ReadTuningConfig()` never read
      `MAJ_7TH` although `WriteTuningConfig()` wrote it, so a custom major
      seventh was lost on every restart. One line added in `RmtView.cpp`
      (Release|x64 rebuild, `RmtTests.exe` 411 pass) and read in Java.
    - Documented deviations: a ratio is stored reduced by `Fraction`
      (`20 / 19` where C++ 1.35 writes `40 / 38` - same value, C++ reads
      either); a zero denominator is refused in the dialog (C++ accepted
      it and divided by zero later); a `TRACKERDRIVERVERSION` outside the
      enum is ignored (C++ casts it). `ReInitSound` on a sound-buffer
      change is B8's, `g_Midi.MidiInit()` has no counterpart.
    - Also fixed from B5: `TrackerPanel` consumed every Alt combination,
      so the menu mnemonics (Alt+T for Tools, ...) never fired and Alt+F4
      did not close the window (a consumed key event suppresses AWT's
      system close; Alt+F4 is not in Rmt.rc's accelerator table, Windows
      handles it). Alt combinations are now left unconsumed, as Windows
      gives them to the menu (`WM_SYSKEYDOWN`, C++'s own comment: "it
      seems to behave like the F10 key"). Live-checked both.
    - Live-checked: the two start-up boxes for missing files (C++ texts),
      both files written, Options/Paths/Tuning laid out as the reference
      captures, Interface Size 200 rescaling the canvas at once, the value
      persisted on exit.
    - 509 tests (+12: `RmtConfigTest` 6, `OptionsDialogsTest` 4,
      `RmtCommandsTest` 2), no regressions. Committed as `b00e44d`.
  - **2026-09-27**: Phase B, batch B7a (file commands - the first of the
    B7 sub-batches; B7 as planned is too large for one commit and splits
    into B7a file commands, B7b import/export, B7c editing dialogs, B7d
    block effects). `SongFiles` ports `IO_Song.cpp`'s
    `FileOpen/FileReload/FileSave/FileSaveAs/FileNew/FileInstrumentSave/
    FileInstrumentLoad/FileTrackSave/FileTrackLoad` and `GUI_Song.cpp`'s
    `WarnUnsavedChanges` flow for flow (the same prompts, texts, order of
    `Stop()`/warn/dialog/clear/load, `g_lastLoadPath_*` updates, extension
    ensuring, "delete the file and warn" on a failed RMT save) behind a
    `SongFiles.Host` (the `CFileDialog`s, `CFileNewDlg`, `CTracksLoadDlg`,
    `SetRMTTitle`) so `SongFilesTest` drives every flow headless with a
    scripted stub; `RmtMainWindow` implements the host on `JFileChooser`
    (one selectable filter per `FILE_LOADSAVE` entry, the filter index
    deciding the format as `nFilterIndex` does, `OFN_OVERWRITEPROMPT` as a
    Yes/No box) and the new `FileNewDialog`/`TracksLoadDialog` (WUDSN
    `ModalDialog`) and `AboutDialog` (a plain `JDialog` like dis6502's; the
    two emulation boxes credit ASAP 8.0.0, per the plan's B7 note).
    `RmtCommands` gains `OnFileSave`'s "Prompt a save dialog box each time
    Ctrl+S is pressed" question; exit now runs `WarnUnsavedChanges` first
    (`OnFileExit`), so the B9 item is done early.
    - Model additions, all with C++ counterparts: `Undo.setChangeListener`
      (`InsertEvent`'s `g_changes = 1` - `RmtSession` points it at
      `uiState.changes`; the title's " *" now appears on the first edit),
      `Instruments.saveInstrumentRti/loadInstrumentRti` (the RTI file
      format, versions 0/1), `Tracks.saveTrackTxt` made public and
      `loadTrackTxt(track, ...)` with an explicit target, plus
      `countTrackSectionsTxt`/`loadTracksTxt` (`FileTrackLoad`'s two
      passes), `Song.fileNewApply` and `Song.testBeforeFileSave` (the
      pre-save validation with its error/warning/"unexpected end of song"
      boxes through `Messages`). `RmtSession.openRmtFile` is gone -
      `SongFiles.fileOpen(path, false)` is what `InitInstance` calls.
    - Deviations: TXT files are written with the platform line separator
      and read byte-transparently (ISO-8859-1), as C++'s text-mode
      streams; `testBeforeFileSave` guards the `m_songgo[last + 1] = 0`
      write C++ makes past the array when the last song line is in use;
      a truncated RTI file is rejected where C++'s stream reads would fail
      silently. Shift+Ctrl+S is a label hint in both (not in the
      accelerator table), so it does nothing in either program.
    - Live-checked: New Module, Load song file (filter preselected from
      the current format), About, the " *" title after an edit and the
      "Save current changes?" prompt on Alt+F4 (No exits).
    - 522 tests (+13: `SongFilesTest` 13), no regressions. Committed as
      `59f1e02`.
  - **2026-09-27**: Phase B, batch B7b (import/export). `SongFiles` gained
    `fileImport` (`FileImport()` + `ImportMOD()`/`ImportTMC()`'s dialog
    wrappers around the ported `ModImporter`/`TmcImporter`, incl. the
    abort-in-the-final-box path that restores an empty song of the
    original size) and `fileExportAs` + `exportV2` (`FileExportAs()` +
    `ExportV2()`: the module built first, then per format the dialog and
    the ported exporter - stripped RMT, simple ASM, relocatable ASM for
    RmtPlayer, SAP-R, LZSS (full file + `_INTRO`/`_LOOP` siblings, each
    only above 16 bytes), SAP type B, XEX, WAV; the POKEY stream generated
    once per export as `CSongContainer` does). C++ creates the output
    file before the format dialog and deletes it with "Incomplete export
    file ... was deleted." on any failure or cancel - reproduced. Eight
    dialogs on WUDSN `ModalDialog`: `ImportModDialog` (radio texts per
    channel count, the envelope box following the volume box),
    `ImportTmcDialog`, `ImportFinishedDialog` (one class for both C++
    ones; OK gated by the "I understand" box, remembered per session),
    `ExportStrippedRmtDialog` (hex address clamped to fit, live RMT FEAT
    block via `composeRMTFEATstring`, clipboard button, SFX-dependent
    warning), `ExportAsmDialog` (three radio groups, 32-char prefix),
    `ExportRelocatableAsmDialog` (label fields enabled by their boxes, live
    size summary via `buildRelocatableAsm(..., wantSizeInfoOnly)` - the
    same `$0038/$0160/$02fb/$001a` as the Rmt.exe capture),
    `ExportSapDialog`, `ExportXexDialog` (5x40 text with the 40-column
    preview and the SHIFT-key toggle, color scrollbar 1..127 with the PAL
    hue names). `ExportSettings` on the session holds the C++ globals
    these remember (`g_rmtstripped_*`, `g_AsmFormat`,
    `g_PrefixForAllAsmLabels`, the relocatable labels, `g_rmtmsxtext` +
    rasterbar options, the two "understood" flags, the last import
    filter) with `ClearSong()`'s subset reset where C++ resets it and the
    stripped address taken from a loaded module (`LoadRmtResult` now
    carries it). "Imported ..." stays the window title until the next
    `SetRMTTitle` reason, as in C++.
    - Model additions: `SapFile.init(song, tracks4_8, date)`
      (`CSAPFile::Init` incl. FASTPLAY), `XexFile.setDisplayedText`
      (`ShowXEXExportDialog`'s screen-block fill + screen codes),
      `Song.get/setLastExportIOType`, `LoadRmtResult.moduleAddress`.
    - Deviations: the SAP dialog's date comes from `java.time` (same
      `dd/MM/yyyy`); a driver-file or memory-overflow failure of the
      SAP-B/XEX exporters (`IllegalStateException` in the model) shows one
      "Export aborted" box with the exception's text instead of C++'s two
      differently titled boxes; status-bar texts ("Generating stream data
      ...", "Compressing data ...") are not shown; the WAV export renders
      the loop-point frame count through ASAP (`WaveFileExporter`) rather
      than the C++ POKEY renderer loop.
    - Live-checked: Export song as... with the stripped RMT dialog (same
      `$4000 - $44AE, length $04AF (1199 bytes)` as the reference), the
      cancel path's "Export aborted" box, Import Song File's filters; the
      other seven dialogs opened on Delta.rmt through a scratch preview
      class and compared with the reference captures.
    - 532 tests (+10: `ImportExportTest`), no regressions. Committed as
      `c7ac02a`.
  - **2026-09-27**: Phase B, batch B7c (editing dialogs). `SongDialogs`
    ports the dialog-opening editing commands behind a `Host` (headless
    `SongDialogsTest` with a stub): `SongInsertCopyOrCloneOfSongLines`
    (+ the view's active-line restore; also Ctrl+O through
    `SongInput.setInsertCopyOrCloneAction`), `InstrInfo`'s message branch
    (the note/track listings recomputed in the UI - the model's
    `InstrInfo` keeps only the ranges), `InstrChange` (+ the "Instrument
    changes" summary box), `TracksOrderChange` (range validation, the
    "columns will be cleared" question, the remembered range via new
    `Song` accessors), the mono/stereo switch (the model's own question,
    then `g_Atari.Init` = `initTuning()` and the window's minimum size -
    also from the info-area click, as is "Change maximal length of
    tracks"), `ChangeMaxtracklen`, `RenumberAllTracks`/`Instruments` with
    their undo bookkeeping. Dialogs on WUDSN `ModalDialog`:
    `InsertCopyOrCloneDialog` (`ValuesTest()`'s clamping and the "n lines
    will be inserted" info), `InstrumentChangeDialog` (the 12 coupled
    combos with `SelChangeComboX()`'s rules, Default ranges / All
    instruments, the mutually exclusive scope boxes, the channel
    selection sub-dialog), `ChannelsSelectionDialog`, `TracksOrderDialog`
    (the From/To button rows with the assignment lines painted between
    them, the six presets, the right-side controls disabled for mono),
    `ChangeMaxTrackLengthDialog`, `RenumberDialogs` (one class for both
    radio dialogs).
    - Deviation: the two guard failures of
      `songInsertCopyOrCloneOfSongLinesApply` (song-range overrun, no
      empty track to clone into) show one combined "Warning" box - the
      model's boolean can't tell them apart (C++ names each).
    - Live-checked: all seven dialogs opened on Delta.rmt through a
      scratch preview class and compared with the reference captures
      (`insert-copy-clone`, `tracks-order`, `instrument-change`,
      `renumber-instruments`, `renumber-tracks`,
      `change-max-track-length`).
    - 539 tests (+7: `SongDialogsTest`), no regressions. Committed as
      `2462f39`.
  - **2026-09-27**: Phase B, batch B7d (block effects) - the last B7
    sub-batch. C++ keeps the six effects' arithmetic inside
    `CEffectsDlg::PerformEffect()` (which is why the plan expected "dialog
    + its logic together"); the port separates them: model
    `BlockEffects.perform()` (fade in/out, modify, echo, expand/shrink,
    volume humanize, volume set/remove, plus `parseChPar` =
    `ZpracujChPar`; `rand() % 1000` becomes an injected `Random`) with
    `BlockEffectsTest` deriving each effect by hand from the C++
    arithmetic, and UI `BlockEffectDialog` (`IDD_EFFECTS`: the effect
    combo with the three prompts/fields, Try/Restore/Play-Stop/Default,
    OK applies and remembers the effect, Cancel restores; the dialog works
    on the live track from a copy of the original, as C++'s
    `m_trackptr`/`m_trackorig`). `SongDialogs.blockEffect()` is
    `CTrackClipboard::BlockEffect()` (block clamped to the track length,
    the "all data" / "instrument xx only" info), `blockEffectFromKey()` the
    Ctrl+F path with its undo step dropped on cancel - reached from
    `SongInput` (Ctrl+F) and `BLOCK_APPLY_EFFECTS` (`OnBlockEffect`, which
    sends Ctrl+F in C++). `BlockEffects.Settings` on the session holds
    `g_effai`/`eff_ed`. Play/Stop sets the block play mode; audible from
    B8 on.
    - Live-checked: the dialog opened on Delta.rmt through a scratch
      preview class and compared with `block-effects.png`.
    - 547 tests (+8: `BlockEffectsTest` 7, `SongDialogsTest` 1), no
      regressions. With this, every command of `IDR_MAIN_WINDOW` is wired
      except the printing commands (MFC's own), "Open ASAP file" (B8's
      player), local help (B9) and the MIDI/Pokey-explorer ones (not
      ported).
  - **2026-09-27**: Phase B, batch B8a (emulation core) - the first audio
    sub-batch of `plans/19_JAVA_AUDIO_PLAN.md`. The 6502 and the POKEY pair
    behind live playback are ASAP's, in an "RMT mode" appended to the
    vendored `ASAP.java` (decision 1): `rmtInitialize` (hardware pages
    $D000-$D7FF as plain RAM, so the driver's POKEY stores land in the
    register shadow as in C++; the ASAP player schedule disabled),
    `rmtJsr` (`C6502::JSR`: registers in, a halt opcode at $FFF0 as the
    return address, registers out, bit 24 if the frame's cycle limit hit),
    `rmtMemory`, `rmtPokeRegister`, `rmtRender` (16-bit LE, decision 3).
    Model: `AtariCpu` wraps it; `Atari(AtariCpu)` shares the CPU's 64K
    (the memory-only `Atari()` stays for the model tests, where `jsr`
    returns its inputs, as the C++ test build's JSR stub); the
    `AtariTrackerDriver` methods are the real JSR sequences of
    `AtariTrackerDriver.cpp`/`Core.cpp` (`init` returns the routine's A,
    `play(boolean specialProveMode)`, `RMT_ATA_INSTROFF` before the AUDC
    reset); `Instruments.update` also writes `instrToAta` to
    `$4000 + instr * 256` after `attachAtari(Atari, stereo supplier)`.
    `RmtSession` builds `new Atari(new AtariCpu(ntsc, true))`, attaches
    the instruments and drops its manual SKCTL pokes (the real `RMT_INIT`
    writes them).
    - Findings: `RMT_INIT` returns A=1 from every driver binary (decision
      4, asserted); ASAP's `PokeyPair.initialize` leaves the channels
      muted with `MUTE_SONG_INIT` until a SAP INIT has run, so
      `rmtInitialize` calls `endSongInit` (the first render was silent).
    - **Cross-check** (`LivePlaybackTest`, Delta.rmt): the SAP-R dump
      through the tracker driver equals ASAP's independent emulation of
      the exported module frame for frame over all 3840 frames with the
      UNPATCHED driver. With the default PATCH16 driver all AUDC/AUDCTL
      bytes agree but AUDF bytes differ by 1-2: the patched drivers carry
      their own frequency tables at $B000 (C++ loads the binary *after*
      `g_Atari.Init()` at startup, so its tables win until the next
      `g_Atari.Init()` - every song load), ASAP's player has the classic
      ones. So the port's SAP-R dump (exported module through ASAP's
      player, the 2026-09-26 substitute for the then-impossible JSR) was
      not observably identical to C++'s live-driver dump. Reverted to the
      C++ way: `dumpSongToPokeyStream` calls `atariTrackerDriver.play()`,
      `PokeyStream.record()` reads $D200/$D210 through the driver
      (`startRecording(Song, tracks4_8, AtariTrackerDriver)`), and
      `AsapEmulator` is deleted (ASAP's module player stays for the WAV
      export - itself now a known non-identical substitution, B8c - and
      as the test reference). `lib/java/README.md` updated.
    - Java-only bug fixed on the way: the dump set the *play* lines
      before `play()`, which PLAY_FROM overwrites from the *active* lines
      (C++ sets those), so every XEX subsong was dumped from the cursor
      line. Test `dumpSongToPokeyStreamPlayFromStartsAtTheGivenSongline`.
    - The `SongEditingTest` dump tests now run the real driver on a
      CPU-backed Atari (`useRealAtari()`), like the port's other tests
      that go beyond the C++ test build's stubs.
    - 554 tests (+7: `AtariCpuTest` 3, `LivePlaybackTest` 3,
      `SongEditingTest` 1), no regressions. No C++ change.
  - **2026-09-27**: Phase B, batch B8b (audio engine) - the sound is on.
    `AudioEngine` is `CSongTimer` + `CSong::TimerRoutine()` +
    `CXPokey::RenderSound1_50()`: a daemon thread that per video frame
    runs `playVBI`, `playPressedTones`, then per instrument-speed
    sub-frame `driver.play(specialProveMode)`, `CopyAtariMemoryToPokey`
    (the $D200/$D210 shadow into the POKEY pair, a muted channel's
    AUDF/AUDC as 0) and the sub-frame's share of the frame's cycles
    rendered; `playTime++` while playing. Pacing is the sound card's: one
    frame of cycles per iteration and a blocking `SourceDataLine.write`
    into a 3-frame buffer (C++'s `m_Latency = 3`), 16-bit/44.1 kHz/2
    channels (a mono song on both), so PAL/NTSC timing needs no
    "17-17-16 groove"; without a device the thread sleeps a frame and the
    model still advances (how the tests drive it, through
    `renderFrame()`).
    - Threading: `RmtSession.lock` (`ReentrantLock`) with
      `locked`/`unlocked` helpers. The engine holds it for the frame's
      model step; the EDT holds it in `TrackerPanel`'s key/mouse/wheel
      listeners, the display timer's idle action and the paint's
      `drawAll`, in `RmtMainWindow.executeCommand`, and in the dialog
      buttons that touch the model (Effects Try/Restore/Play, Tuning
      apply/reset). Every modal dialog, file chooser and message box is
      shown with the lock released (`session.unlocked(...)` in the host
      methods and in `SwingMessages`, which also moves a message raised on
      the engine's thread onto the EDT) so the sound keeps running while
      they are open - C++ audio runs through dialogs, the Effects dialog's
      Play depends on it. `Stop()`'s `WaitForTimerRoutineProcessed`
      busy-wait is not ported: the engine applies the silence on its next
      frame (a wait under the lock would deadlock).
    - `ReInitSound()`: `RmtSession.reInitSound()` = POKEY pair
      re-initialized for the song's video standard/channel count, tuning
      tables regenerated, driver reset. Called from the new
      `RmtSession.setTracks4_8()` (C++'s `SetTracks`; the nine
      `session.tracks4_8 = ...` writers in `SongFiles`/`SongDialogs` now
      go through it, and the field starts at 8 as C++'s hardcoded
      `g_tracks4_8 = 8` does), from `setNTSC` on a change, from the import
      and from the options dialog's sound-buffer change; the engine also
      re-checks at the top of every frame as a safety net.
    - `g_playtime = 0` in `Play()` and `ClearSong()` (both had been noted
      as omitted) through `Song.setPlayTimeResetListener`, the
      `Undo.setChangeListener` precedent; the counter runs in the TIME
      display now.
    - Live check: Delta.rmt through the real line - a probe measured a
      steady 50 frames/s after the first second (PAL nominal 49.86), and
      two screenshots 5 s apart show TIME 0:02.82 -> 0:08.00, the
      follow-play cursor moving and the POKEY register view changing.
    - 559 tests (+5: `AudioEngineTest`), no regressions. No C++ change.
  - **2026-09-27**: Phase B, batch B8c - the rest of the audio batch; B8
    is complete.
    - Esc's "reset the Atari sound routines" option needed no code: Esc is
      the Stop accelerator and `SONG_STOP` already runs `driver.init()`
      when `keyboardEscResetAtariSound` is set (B6).
    - Media keys: `VK_MEDIA_PLAY_PAUSE` (play from start / stop),
      `VK_MEDIA_NEXT_TRACK`/`VK_MEDIA_PREV_TRACK` (`PLAY_SEEK_NEXT`/`_PREV`)
      in `SongInput.keyDown` as in `CRmtView::OnKeyDown`. AWT has no key
      codes for them (Windows delivers them as `VK_UNDEFINED`), so
      `VirtualKey.fromKeyEvent` cannot map them; the handler is there and
      tested through `keyDown(vk)` (`SongInputTest`).
    - "Open ASAP file": `ID_TOOLS_OPEN_ASAP_FILE` has no handler in the C++
      sources, so MFC shows the item disabled; `isEnabled` now says false
      and the handler does nothing (was a "not available (B7)" notice).
    - WAV export back to C++'s design: `WaveFileExporter.exportWav(
      PokeyStream, ntsc, stereo, instrumentSpeed)` replays the recorded
      stream frame by frame up to the loop point through `AtariCpu`'s
      POKEY pair (the frame's registers poked per instrument-speed
      sub-frame, the sub-frame's share of the frame's cycles rendered - what
      `ExportWAV` + `RenderSoundV2` do through the driver's variables and
      `SetPokey`), 16-bit/44.1 kHz/2 channels with a hand-written RIFF
      header. The 2026-09-26 "idiomatic substitution" (the exported module
      through ASAP's player) is gone: B8a showed it was not observably
      identical (frequency tables). `SongFiles`' WAV case adds C++'s
      `driver.Init()` / all channels on before and all channels off after
      ("TODO: Set channels on again?" - kept as is). `AtariCpu.toTwoChannels`
      is the shared mono-to-2-channel expansion (engine + export).
    - **C++ bug, fixed in both**: for a stereo song `ExportWAV` copied the
      stream frame's bytes 0-8 into the first POKEY's track variables, but
      `CPokeyStream::Record()` stores the *second* POKEY's 9 bytes first
      ("1st POKEY is 2nd in the stream"), and the second POKEY's variables
      (`trackn_audf/audc+4..7`, `v_audctl2`, confirmed against
      `asm/Patch-16/rmtplayr.a65`'s SetPokey) were never written - a
      stereo WAV carried only the right-hand POKEY, played on the left.
      `WaveFileExporter.cpp` gets a `frameSize == 18` branch; Java maps
      both POKEYs. Test `exportWavOfAStereoSongPutsTheSecondPokeyOnTheRightChannel`
      (a note on track 4 only: left channel silent, right sounding). C++
      `ExportWAV` is DirectSound-bound and has no test; verified by the
      Release build and the unchanged 411-test suite.
    - 561 tests (+2), no regressions. C++: 1 file.
  - **2026-09-27**: Phase B, batch B9a (runnable jar and program folder) -
    the first of the three B9 sub-batches of `plans/20_JAVA_B9_PLAN.md`.
    - `pom.xml`: `maven-shade-plugin` as dis6502 -> `target/rmt.jar`
      (the two WUDSN jars merged, `Main-Class`, `Build-Date` in UTC and
      `Implementation-Version` in the manifest; `dependency-reduced-pom.xml`
      gitignored). `launch/Rmt.launch` for Eclipse.
    - `ProgramFolder` (model): C++'s `g_prgpath` + `GetResourceFilePath()`,
      the one process-wide setting the port keeps (process configuration,
      not model state). `getResourceRoot()` is the first of the program
      folder, its `rmt/`, the working directory and its `rmt/` that holds
      `resources/`: an installed copy (the jar inside the `rmt/` layout the
      C++ build also ships) resolves exactly as `Rmt.exe`, and a checkout
      works from `target/rmt.jar`, the classes folder or `mvn test`
      without configuration. `RmtAtariBinaries` and `SapFileExporter`'s
      player path (now `vuPlayerPath()`) resolve through it instead of the
      former working-directory paths. `RmtApplication` sets it from
      `getProgramFolder()` (`-Drmt.config.dir`, the jar's folder, the
      working directory), which also stays the `rmt.ini`/`tuning.ini`
      folder.
    - `RmtCommandLine`: MFC's shape (a `/` or `-` parameter is a switch,
      the first other one the file to open, the rest ignored) over the
      ported `RmtCommandLineInfo`. Per the user's decision ("proper
      scripts instead of /TEST"), `/SCRIPT:<file>` and `/TEST:<file>` are
      rejected with the C++ "Invalid Command Line Parameter" box and exit
      code 1 - `/SCRIPT` is reserved for the scripting feature (plan
      section 5), the C++ developer routines are not ported.
    - Local help (`HELP`): `docs/rmt_en.html` of the resource root opened
      in the browser (`CRmtApp::OnHelp` = `CShell::OpenLocalFile`); a
      missing file gives a "Help" error box with the path. `rmt/docs/*.*`
      stays gitignored; the release step (B9b) fills it from `doc/`.
    - `VERSION_AND_BUILD`: "RASTER Music Tracker 1.35 (Java <Build-Date>)"
      from the jar manifest, "(Java)" from a classes folder - C++'s
      `GetVersionAndBuild()` with `__DATE__ __TIME__`.
    - Tests: `ProgramFolderTest` (checkout, installed layout, the
      working-directory fallback), `RmtCommandLineTest`, the missing-help
      case in `RmtCommandsTest`. 569 tests (+8), no regressions.
    - Live: a staged `rmt/` layout with the jar, run from a foreign working
      directory - Delta.rmt plays (POKEY registers live, so the driver
      came from the staged `resources/`), About shows the build date,
      `rmt.ini`/`tuning.ini` are written beside the jar on exit; the jar
      run from the checkout root shows C++'s first-start "Could not find
      rmt.ini" box (a fresh `target/` folder) and then plays;
      `java -jar target/rmt.jar /SCRIPT:x` shows the rejection box and
      exits with 1.
  - **2026-09-27**: Phase B, batch B9b (release workflow).
    - `.github/workflows/release.yml`, dis6502's workflow adapted: checks
      out this repository and `wudsn/wudsn-base`, installs WUDSN Base into
      the runner's Maven repository, builds the shaded jar with the tests
      (the one display-bound test skips itself headless), stages the
      distribution layout, builds a jpackage app image per OS (Windows,
      Linux, macOS; `--add-modules ALL-MODULE-PATH`, the C++ `.ico` on
      Windows, `application.png` on Linux), uploads the archives and, on a
      `v*` tag or a manual republish, attaches them to a GitHub Release.
      Version = the tag without `v` (`v1.35.0` -> `1.35.0`, the C++ 1.35
      line; the version string is a file-format marker, so it stays).
    - `build/stage_java_release.sh`: the one place that knows the layout -
      `target/rmt.jar` + `rmt/{resources,instruments,songs,exports,rmt.ini,
      tuning.ini}` + `docs/` from `doc/` (as the C++ post-build copy and the
      daily build do) into jpackage's `--input`, so the image's `app/`
      folder is the program folder `ProgramFolder` resolves.
    - Local dry run on Windows (CI itself cannot run here): the staging
      script + `jpackage --type app-image` -> a 157 MB `rmt/` image with
      `rmt.exe`, `runtime/` and `app/`; `rmt.exe` started from a foreign
      folder plays Delta.rmt (POKEY registers live), About shows "1.35 (Java
      2026-09-27 09:52 UTC)", `app/rmt.ini`/`tuning.ini` are written on
      exit.
    - No Java or C++ source change; no new tests.
  - **2026-09-27**: Phase B, batch B9c (polish) - B9 and Phase B complete.
    - HiDPI toolbar: `RmtToolBars.iconScale()` rounds the default screen's
      device scale (1 below 150%, 2 from 150%, 3 from 250%), `scaled()`
      enlarges the 32x30 button images nearest-neighbour by it, and
      `PixelIcon` draws the enlarged image 1:1 in device pixels (Swing is
      told the logical size, the paint removes the scaling transform - the
      canvas's own technique). A first version let Swing scale the 2x image
      again, which made 3x at 150%. A deliberate improvement over the
      DPI-unaware MFC toolbar (decision 3 of `20_JAVA_B9_PLAN.md`); the canvas
      itself was HiDPI-correct since B1.
    - `notAvailable`'s status text drops "yet" - only printing (MFC's own)
      still uses it. The status line stays a `JLabel` (decision 4).
    - README.md gains a "Java port" section (scope, build/run/test, the
      program folder, Eclipse, releases, plans). `.gitattributes` keeps
      `*.sh`/`*.yml` LF - the repository runs with `core.autocrlf=true`, and
      a CRLF shebang would break `build/stage_java_release.sh` on the
      Linux/macOS runners.
    - `RmtToolBarsTest` (strip loading with the transparent button face,
      the scaling, the scale factor, the device-pixel paint). 573 tests
      (+4), no regressions.
    - Live: the jar at the display's 150% shows the toolbar at 2x next to
      the unchanged canvas (`b9c-toolbar.png`).
    - Phase B is closed in `18_JAVA_UI_PORT_PLAN.md` and `13_JAVA_PORT_PLAN.md`;
      the next feature is scripting (`20_JAVA_B9_PLAN.md` section 5).
  - **2026-09-27**: Scripting, batch S1 (`plans/21_JAVA_SCRIPTING_PLAN.md`) -
    the user's "proper scripts instead of /TEST".
    - `org.atari.raster.rmt.script`: `ScriptParser` (one command per line,
      `#` comments, quoted tokens with `\"`/`\`, `name=value` options,
      case-insensitive names; `ScriptCommand`, `ScriptException` with the
      line number) and `ScriptRunner` (`open <file>`, `save <file>` by
      extension, `export <format> <file> [options]` for the Export
      dialog's eight formats - `stripped-rmt`, `asm`, `sapr`, `lzss`,
      `sap`, `xex`, `rmtplayer-asm`, `wav` - with each dialog's fields as
      options and the dialog's defaults when omitted, `set overwrite
      yes|no`, `echo`, `quit`). The runner drives the unchanged
      `SongFiles` flows through a `SongFiles.Host` that answers the file
      chooser and the export dialogs from the command, and a
      `Messages.Handler` that prints the message boxes to the console
      (errors and warnings fail the command, questions are declined).
      Exit codes 0 (all done), 1 (a command failed - the script stops, the
      message names the line), 2 (unreadable or unparsable script). Paths
      are relative to the script's folder. An existing output file is an
      error unless `set overwrite yes`.
    - `/SCRIPT:<file>` (`RmtCommandLine`, `RmtApplication.runScript`) runs
      before any Swing object exists, `java.awt.headless` set, with the
      program folder's `rmt.ini`/`tuning.ini` read as the window reads
      them (the tuning shapes the exports). `/TEST` stays rejected, its
      message now pointing at `/SCRIPT`.
    - Tests: `ScriptRunnerTest` (ui test package, for `StubSongFilesHost`
      - which gained `answerDialogDefaults` for the SAP/XEX dialogs):
      every format's script export is byte-identical to the dialog path
      with untouched dialogs; options reach the exporters and are
      validated with line numbers; save by extension (a re-save of
      Delta.rmt is idempotent, not identical to the 2003 original);
      overwrite refused/allowed; failures and exit codes; `quit`.
      `ScriptParserTest` the syntax. 584 tests (+11).
    - Two fixes on the way: (1) a missing `vu_player_v2.obx` escaped
      `SapFileExporter.exportSapBLzss`/`SongExporter.exportXexLzss` as an
      `UncheckedIOException` - uncaught in the UI and leaving the empty
      output file behind - instead of the `IllegalStateException` the
      callers catch (C++'s "Fatal error with RMT LZSS system routines"
      box, which then deletes the file); (2) a configuration-only
      `-Drmt.config.dir` lost the installed resources -
      `ProgramFolder.setInstallFolder` (the jar's folder) is searched
      after the program folder and before the working directory.
    - Live: the plan's sample script headless from the checkout root
      (exit 0, SAP/XEX/stripped RMT/WAV written) and from a foreign
      folder with the staged `rmt/` layout and a configuration-only
      folder; a rerun refuses to overwrite (exit 1); a broken quote gives
      exit 2 with the line; `/TEST:x` still shows the rejection box.
  - **2026-09-27**: Scripting, batch S2 - the settings and the menu command.
    - `set ntsc yes|no` and `set driver <version>` in `ScriptRunner`: the
      two Options-dialog settings that shape exports, applied through the
      same session methods the dialog uses (`setNTSC` with its
      `ReInitSound`, `setTrackerDriverVersion`). Driver names are the enum
      names, case-insensitive with `-` or `_`; `none` is not offered.
    - **Tools > Run Script...** (`TOOLS_RUN_SCRIPT`; a command of the Java
      port only, no C++ counterpart - decision 5 of the scripting plan):
      `RmtCommands.Host.chooseScriptFile()` (a file chooser for
      `.rmtscript`/`.txt`), then the runner on the live session in its new
      interactive message policy: `ScriptRunner(session, out, err,
      messageBoxes)` wraps the window's `Messages.Handler` (new
      `Messages.getHandler()`) so the model's boxes stay boxes while errors
      and warnings still fail the command, and restores the handler
      afterwards. The commands' output is shown once at the end - an
      information box on success, an error box with the failed line
      otherwise; `updateMinimumSize`/`skipLinesChanged` follow a possible
      song change.
    - Tests: `ScriptRunnerTest` (`set ntsc` makes the session and its
      POKEY pair NTSC and the WAV shorter, `set driver` sets the option,
      bad values fail with the choices listed; the interactive policy
      delivers the box to the window and restores the handler);
      `RmtCommandsTest` (the command opens a song from a script, reports
      once, reports a failure, does nothing when cancelled). 587 tests
      (+3).
    - Live: Delta.rmt in the window, Tools > Run Script, a two-export
      script typed into the chooser - the SAP-R and the stripped RMT were
      written next to the script, the "Script 'ui.rmtscript' finished."
      box listed them (`s2-result.png`).
  - **2026-09-27**: Scripting, batch S3 (documentation) - the scripting
    feature is complete (`plans/21_JAVA_SCRIPTING_PLAN.md`).
    - `doc/rmt_scripting.md`: how to run a script (command line without a
      display, Tools > Run Script), the exit codes, the syntax, the
      commands, the eight export formats with their options and defaults,
      an example, what is not a command. Linked from README's Java
      section; `build/stage_java_release.sh` copies it into the
      distribution's `docs/`.
    - Decision 6 (the C++ program) left open as recommended.
  - **2026-09-27**: C++ scripting, batch C1 (`plans/22_CPP_SCRIPTING_PLAN.md`)
    - the user's question "would /SCRIPT support on Windows help to
    simplify testing?" answered with the same script format in `Rmt.exe`,
    so the two programs' exports can be compared end to end (C2).
    - `Script.h/.cpp`: the parser (std only, linked into both projects), the
      Java `ScriptParser`'s rules 1:1. `ScriptRunner.h/.cpp`: `open`, `save`,
      `export` for the eight formats, `set overwrite|ntsc|driver`, `echo`,
      `quit` - the exports through the dialog-independent entry points
      (`ExportAsStrippedRMTApply`, `ExportAsAsmApply`,
      `ExportAsRelocatableAsmForRmtPlayerApply`, `CSAPFileExporter::ExportSAP_R/
      ExportSAP_B_LZSS`, `ExportLZSS`, `ExportXEX_LZSS(xexFile)`, `ExportWAV`)
      with the parameters the dialogs would have collected and their
      defaults from the same globals, the globals updated as the dialogs
      update them; `set ntsc` as `CRmtView::SetNTSC`, `set driver` as the
      options dialog's branch. Factored out of the dialogs for that:
      `CSAPFile::ParseSubsongs`, `CSongExporter::DefaultXexText/SetXexText`;
      added `CSong::SetLoadedFile/SetLastExportIOType` (the Java port had
      them since B7).
    - Script message mode (`Messages.cpp`): the boxes to the console -
      errors/warnings to stderr and collected (they fail the command),
      information to stdout, questions answered No/Cancel with a note.
      `AttachScriptConsole`: the parent console when there is one, else
      `<script>.log`. `Rmt.exe` is a GUI program, so batch files use
      `start /wait`.
    - `CRmtApp::InitInstance`: the window is created but not shown for a
      script (the register dump pumps its messages), the run's exit code
      goes through a new `CRmtApp::ExitInstance()` override (`InitInstance()
      == FALSE` alone always exits 0; an `ExitProcess` crashed in the DLL
      teardown). `/TEST` removed from `CRmtCommandLineInfo`; `RmtTest.*` and
      `SongExporterTest.*` (the developer routines, the WASAP launcher, the
      menu analyzer) deleted with their project entries.
    - **Bug found by the first script run**: the WAV export crashed
      mid-file (0xC0000005; 336 KB, 2.5 MB, 4.5 MB on three runs of a
      6.8 MB file). `ExportWAV` renders through `RenderSoundV2` on the
      main thread while the timer thread keeps rendering through
      `RenderSound1_50` on the same POKEY; the `WRITE` state meant to stop
      the timer routine never did, because `CSong::m_pokeyStream` is null
      again once `DumpSongToPokeyStream()` returned - the source's own
      "TODO: Fix the timing overlap causing conflicts / JAC! Does this
      problem really still exist?". Fixed: `ExportWAV` stops the song timer
      (`CSong::StopTimer`) for its duration and re-arms it (`ChangeTimer`)
      at the end; link-only stubs for the two in `SongEditingStub.cpp`.
      Three WAV runs now give the identical 6,773,804 bytes. The Java port
      is not affected (its engine and the export share the session lock).
    - Tests: `ScriptTests.cpp` (the parser cases of the Java
      `ScriptParserTest`, `ParseSubsongs`); the `/TEST` case dropped from
      `RmtCommandLineInfoTests.cpp`. 415 C++ tests (+4). Release build clean.
    - Live: the `doc/rmt_scripting.md` example through `Rmt.exe /SCRIPT`
      from Git Bash and through PowerShell's `Start-Process -Wait` - exit 0,
      SAP/XEX/stripped RMT/WAV written; a rerun refuses to overwrite (exit
      1); a broken quote gives exit 2.
    - `doc/rmt_scripting.md` now covers both programs (the Windows
      paragraph: `start /wait`, the `.log` fallback); README updated.
  - **2026-09-27**: C++ scripting, batch C2 (`plans/22_CPP_SCRIPTING_PLAN.md`)
    - the cross-program export comparison, and the port bugs its first run
    found.
    - `test-resources/scripts/delta.rmtscript`, `stereo.rmtscript`: the
      saves in the three formats, the eight exports with defaults, a round
      with every option, `set ntsc`/`set driver` rounds. The stereo script
      has no `sap`/`xex`: both programs refuse the song ("LZSS data is too
      big to fit in memory").
    - `set output <folder>` in both programs (decision 3), the
      `RMT_SCRIPT_OUTPUT` environment variable overriding it (one script,
      two output trees), `RMT_SCRIPT_LOG` in `Rmt.exe` (its console output
      into a file whatever console the caller has; stdout and stderr share
      one offset via `_dup2` - two `freopen` lost lines).
    - `build/compare_exports.ps1`: both programs over every script with the
      C++ program folder's `rmt.ini`/`tuning.ini`, the output trees byte for
      byte, WAV reported only (decision 5); exit 1 on a difference, 2 when a
      program is not built. `build_rmt-daily.bat` calls it after the release
      build when `target\rmt.jar` exists and stops before the upload on a
      difference. `CrossProgramExportTest` (decision 4): the same comparison
      with every `mvn test`, skipped without `Rmt.exe`.
    - **Four port bugs found by the first run, all fixed** (see the plan's
      C2 paragraph): Java ASM exports CRLF instead of C++'s LF (binary-mode
      export, `CASMFile::EOL`; `SongFiles.asmBytes`); Java `.rmw`
      instrument names 32 bytes instead of C++'s 33 (`sizeof(ai->name)`,
      save and load - the files were not interchangeable); Java `.rmw` main
      parameters 8..23 (the 16 UI-setting globals) written as zeros and
      discarded on load - `Song.saveRMW(tracks4_8, uiParams)`,
      `LoadRmwResult.uiParams`, supplied/applied by `SongFiles` from the
      session in the `DEFINE_MAINPARAMS` order (`g_keyboard_playautofollow`
      has no Java field, its C++ default 1 is written); C++ `ExportLZSS`
      and `ExportSAP_B_LZSS` fail-fast crashes (0xC0000409) on the stereo
      song from 64 KB stack buffers, now 1 MB heap vectors as the sibling
      exporters. After the fixes every non-WAV file of both scripts is
      byte-identical (RMT/TXT/RMW saves, stripped RMT, ASM, SAP-R, LZSS with
      its INTRO/LOOP siblings, SAP, XEX, RMT player ASM, in both languages'
      option and NTSC/driver rounds).
    - Tests: `SongEditingTest.saveRMWAndLoadRMWRoundTripTheCallersUiSettings`,
      `ScriptRunnerTest.setOutputRedirectsSavesAndExportsAndTheOverrideWins`,
      `CrossProgramExportTest`. 590 Java tests (+3, one conditional); 415
      C++ tests. Release build clean.
  - **2026-09-27**: C++ scripting, batch C3 (documentation) - the C++
    scripting plan is complete (`plans/22_CPP_SCRIPTING_PLAN.md`).
    - `doc/rmt_changes.md`: the `/SCRIPT` feature and the two export
      crashes fixed on the way (WAV timer race, stereo LZSS/SAP stack
      buffers) as RMT 1.35 entries, linking `doc/rmt_scripting.md` on GitHub
      (the Windows distribution's `docs/` stays HTML only - the user's
      decision; a copy of the `.md` there was reverted); the removal of
      `/TEST` and the cross-program comparison under "Technical".
    - README: the comparison sentence in the Java section.
      `plans/21_JAVA_SCRIPTING_PLAN.md`: decision 6 closed as (b), C1-C3 done.
      `plans/13_JAVA_PORT_PLAN.md`: the "Next" item (scripting) marked DONE, no
      open port batch remains.
  - **2026-09-27**: Documentation generation, batch D1
    (`plans/23_DOC_GENERATION_PLAN.md`; the user's decisions: Markdown stays
    the source on GitHub, the distributions ship HTML, the menus are to be
    extracted by the build).
    - `org.atari.raster.rmt.doc.DocGenerator` (in the jar): every `doc/*.md`
      to `<name>.html` through CommonMark 0.29.0 with the GFM tables
      extension (Maven dependencies, shaded), one page template with a small
      stylesheet, the title from the first heading, links to `.md` pages
      rewritten to `.html` (links with a scheme untouched), byte order marks
      stripped, `<!-- include: file.md -->` inlined (a missing file fails
      the run), `img/`, `*.gif`/`*.png` and `.html` files without a `.md`
      source copied (the manuals), `.txt` and `.md` not shipped.
      `DocGeneratorTest`: the real `doc/` folder (every page generated,
      tables/fences/link rewriting, the `<file>` placeholder as text), the
      include marker and its failure, Markdown winning over a page of the
      same name, the title fallback.
    - `build/stage_java_release.sh` and `build/build_rmt_pre.bat` run the
      generator into `docs/` (the pre-build falls back to copying the HTML
      files when `target\rmt.jar` is not built, so the C++ dev loop needs no
      Java build; `build_rmt-daily.bat` requires the jar). `.gitignore`
      covers `rmt/docs/img/`. `doc/rmt_changes.md`: the `<file>` placeholder
      in code spans (CommonMark took it for a tag), a relative link to the
      scripting page, an entry for the HTML documentation in the download.
      README: the documentation paragraph.
    - Verified: `mvn -o clean package` 594 tests (+4); the staging and the
      pre-build both produce the 12 files; the scripting page rendered in a
      browser (tables, code, title).
  - **2026-09-27**: Documentation generation, batch D2 - `dump actions` in
    both programs and the command table in the cross-program comparison
    (`plans/23_DOC_GENERATION_PLAN.md`, findings listed there).
    - C++: `CCommands` refactored (`Analyze()` collects from the compiled
      resources: main menu, main and block toolbars, both accelerator
      tables; `WriteActionInfos(file)` writes the Markdown table, LF, and
      returns the ERROR count); rows in the order met (menus, toolbar-only
      buttons, key-only commands); an own English key formatter (MFC's was
      locale-dependent); the accelerator-table bug, the popup-ID row, the
      `&&` handling fixed. `CScriptRunner::Dump` (`dump actions <file>`,
      fails on ERROR markers). `Rmt.rc`: the stale labels and prompts the
      first dump flagged, the Help menu on the program's own IDs, the About
      toolbar button on `ID_HELP_ABOUT_APP` (`ID_HELP_ABOUT` had no
      handler), five prompts in the "status\nLabel (Key)" convention.
      Tools > Run Script... (`ID_TOOLS_RUN_SCRIPT`, `CRmtView::
      OnToolsRunScript`, `CScriptRunner::RunInteractive` with the output
      captured for one result box, `SetScriptMessageMode(enabled,
      interactive)`: boxes shown, problems still collected) - the Java
      port's S2 command, now in both. The Debug-only start-up `Analyze()`
      call removed.
    - Java: `ActionInfos` (the same table from `Actions`/`RmtCommandId`/
      `RmtMainMenu`/`RmtToolBars`, the keys the items display, the same key
      names), `ScriptRunner` `dump`; the Pokey Explorer's keys and the block
      toolbar's keys displayed as hints as the C++ menus display them, the
      toolbar-only labels as the C++ tooltips, `SONG_TOGGLE_NTSC` (Ctrl+F12,
      accelerator-only) executing `OnSongToggleNTSC`'s port.
    - `test-resources/scripts/actions.rmtscript`; `build_rmt-daily.bat`
      regenerates `doc/rmt_action_infos.md` after the Release build and
      stops on ERROR markers; the checked-in table is the build's (176
      rows, both programs byte-identical). `doc/rmt_scripting.md` (`dump`,
      Run Script in both), `doc/rmt_changes.md` entries.
    - Verified: `mvn -o clean package` 595 tests (+1); RmtTests 415;
      `compare_exports.ps1` 3 scripts identical; Tools > Run Script in
      `Rmt.exe`: the command opens the "Run script file" dialog (checked
      through a posted WM_COMMAND); the run through to the result box
      confirmed by the user by hand ("Script works OK").
  - **2026-09-27**: Documentation generation, batch D3 - the manual to
    Markdown (`plans/23_DOC_GENERATION_PLAN.md` section 2.4).
    - `doc/rmt_en.md`: `rmt_en.html` converted by a one-time script (the
      HTML was regular: headings, key tables with modifier/key/description
      cells, field tables, two `<pre>`, one list); the key cells became
      `CONTROL+F8` style code spans, the tables Key/Action, Field/Meaning
      and Value/Meaning. New chapter "Menus, Toolbars and Keys" with
      `<!-- include: rmt_action_infos.md -->`, so the manual's command
      reference is the build's. Corrected on the way, because the generated
      table proves them: the three F-key rows (F2/F3/Shift+F4, the manual
      said F1/F2/F3). Everything else 1:1 - the revision of the outdated
      chapters is D4, the user's (the empty descriptions of three key rows
      and the Num +/- question below are for it).
    - `doc/rmt_en.html` deleted: `DocGenerator` generates it from the
      Markdown (title from the heading, the included table). README, the
      Java `ONLINE_HELP_URL` and the C++ online help open the Markdown page
      on GitHub instead of the html-preview of the deleted file; the local
      help of both programs still opens `docs/rmt_en.html` (generated).
    - Open question for D4: the manual says numblock +/- change the volume
      and Ctrl+numblock +/- the step size, while `Rmt.rc`'s accelerator
      table binds plain `VK_ADD`/`VK_SUBTRACT` to the step size (and the
      view has no `PreTranslateMessage`, so the accelerator wins); the menu
      labels now follow the table. Checked by the user in `Rmt.exe`: the
      keypad's + and - without a modifier change the step size, as the
      table says; the manual's two rows corrected (the "volume for newly
      entered notes" rows had it the other way round). The volume keys are
      the keypad's + and - with Ctrl or Shift (the accelerator entries have
      no modifier flags, so a modified key reaches the tracker's handlers,
      which all map VK_ADD/VK_SUBTRACT to VolumeUp/VolumeDown) - confirmed
      by the user; the manual now says so, and names the mouse wheel over
      the VOLUME/OCTAVE fields.
    - Verified: `mvn -o clean package` 595 tests (`DocGeneratorTest` checks
      the generated manual's heading and included table); the staging
      produces 12 files with the generated manual (383 table rows); the C++
      Release build clean; the manual rendered in a browser.
  - **2026-09-27**: Documentation, D4 (first part) - the manual's hotkey
    tables verified against the code (`CRmtView::OnKeyDown`, the
    accelerator table, `InfoKey`/`InstrKey`/`ProveKey`/`TrackKey`/`SongKey`
    in `GUI_Song.cpp`). Corrected in `doc/rmt_en.md`:
    - "SHIFT+ESC reinitialises the sound output": there is no Shift+Esc;
      Esc stops, and the option "ESC resets Atari sound" (default on)
      reinitialises the routines - one row now says so.
    - "PAGE UP/DOWN: next/previous song line": true in SONG, INFO and
      INSTRUMENT EDIT; in TRACK EDIT they move by the highlight step within
      the track and CONTROL+PAGE UP/DOWN change the song line - the row
      says so, the empty TRACK EDIT row for CONTROL+PAGE UP/DOWN filled.
    - The two other empty TRACK EDIT rows filled: SHIFT+CONTROL+ENTER sets
      the track's end line (as CONTROL+END); INSERT/DELETE insert/delete a
      line (DELETE deletes the selection block when one exists).
    - CONTROL+ENTER: the "Swap ENTER and CONTROL+ENTER" option noted.
    - SONG EDIT: "CONTROL+PAGE UP/DOWN to the previous/next subsong" is
      SHIFT+PAGE UP/DOWN in `SongKey` (Control is not handled there; Shift
      selects the subsong in every mode).
    - Everything else in the hotkey tables matches the code.
    - Not in the code: a handler for the menu hints Ctrl+Shift+S (Save As)
      and Alt+Enter (Properties) - neither an accelerator nor a key handler
      case (`VK_S`, Alt+Return); the Java port follows the same table. To
      be tried by hand; if dead, the hints in `Rmt.rc` should go or real
      accelerators be added.
    - Decisions of the user after the check: Ctrl+Shift+S (Save As) and
      Alt+Enter (Properties) become real accelerators in `Rmt.rc` and in
      `RmtCommandId` (both were menu hints without a binding); the note keys
      ("tonekeys") are generated - `dump notekeys <file>`
      (`NoteKeysTable()` in `Keyboard2NoteMapping.cpp`, `NoteKeys` in the
      Java model, the same text: "### QWERTY"/"### AZERTY", a Note/Keys
      table each, the OEM keys by their US or French legends), written by
      `actions.rmtscript` as `doc/rmt_note_keys.md`, compared across the
      programs and included in the manual's new "Note Keys" chapter. The
      French legends of the OEM keys ($ = , ; : ù ) * ^ ² ! <) are from the
      standard French layout and not verified on a French keyboard.
    - Verified: `mvn -o clean package` 595 tests (`dump notekeys` in
      `ScriptRunnerTest`, the included tables in `DocGeneratorTest`, the
      two new accelerators in `RmtMainMenuTest`); RmtTests 416 (+1, the
      note key table); `compare_exports.ps1` 3 scripts, the command table
      and the note key table byte-identical in both programs; the staged
      manual carries both layouts.
  - **2026-09-27**: Java package renamed `com.wudsn.tools.rmt` ->
    `org.atari.raster.rmt` (the user: RMT has its own organization,
    separate from wudsn.com). `git mv` of `src/java/com/wudsn/tools/rmt`
    and `src/java/test/com/wudsn/tools/rmt` to `.../org/atari/raster/rmt`;
    every reference replaced (205 files: `package`/`import` lines,
    qualified names, javadoc, `pom.xml` - Maven group `org.atari.raster`,
    artifact `org.atari.raster.rmt`, the shade `mainClass` -, `.project`,
    `launch/Rmt.launch`, `release.yml` - icon path and `--main-class` -,
    `stage_java_release.sh`, `build_rmt_pre.bat`, `lib/java/README.md`,
    the C++ comments naming Java classes, the plans). The WUDSN Base
    dependencies (`com.wudsn.tools.base`, `.base.atari`) and the vendored
    `net.sf.asap` are unchanged. `RmtWindowPreferences` uses
    `Preferences.userNodeForPackage`, so the stored window position moves
    to a new node (one-time loss of the remembered position). CLAUDE.md
    notes the package.
  - **2026-09-27**: `doc/keymappings.txt` removed (the user's decision
    after a check): Vin Samuel's working notes of the 1.31 hotkey remap,
    from which the manual's tables were once written (with the same errors
    the code check corrected today); not shipped, referenced only as a
    Visual Studio solution item (`Rmt.vcxproj` and its filters, entries
    dropped). Its one row the manual lacked - HOME in the instrument
    parameters moves to ENVELOPE LENGTH - added to the manual first.
  - **2026-09-27**: the note key document as a keyboard (the user's wish:
    "a more structured output that has the form of a keyboard"). Per
    layout, before the table by note: a fenced block with the four key
    rows of the keyboard - the legends as the keyboard prints them, the
    note each key plays underneath, the rows staggered. The row order is
    data in both programs (`KeyboardRows()` in `Keyboard2NoteMapping.cpp`,
    `QWERTY_ROWS`/`AZERTY_ROWS` in `NoteKeys`); the AZERTY number row shows
    its French legends (& é " ' ( - è _ ç à). Both programs' documents
    byte-identical; RmtTests 416, Java 595; the manual's "Note Keys"
    chapter text adjusted.
  - **2026-09-28**: the plan files numbered (the user's wish: to see at a
    glance what is old and done and what is new). The 23 batch plans got a
    two-digit prefix in creation order (`01_SONG_IO_SONG_REMAINING_PLAN.md`
    .. `23_DOC_GENERATION_PLAN.md`; `git mv`, every reference in the
    repository updated - plans, CLAUDE.md, READMEs, source comments, the
    Visual Studio and Eclipse project files); `CPP_RULES.md`, `OVERALL_PLAN.md`
    and `NOTES.md` stay unnumbered. New: `plans/README.md`, the index with
    each plan's purpose and status; CLAUDE.md states the rule (next free
    number, a row in the index). `23_DOC_GENERATION_PLAN.md` marked D4 DONE.
    `RULES.md` renamed `CPP_RULES.md` (the user's wish; references updated).
  - **2026-09-28**: Exports without screen updates, batch E1
    (`plans/24_EXPORT_SCREEN_UPDATES_PLAN.md`, section 6 has the numbers).
    - The user's observation confirmed by a screenshot: the register dump
      redrew the whole screen up to 60 times a second with the song in
      playback state. Now: no redraw during the dump, a status text every
      250 ms (A); one `CExportSection` guard around every export - window
      disabled, wait cursor, song timer stopped, restored on leaving (B),
      replacing `DisableEventSection` and the WAV exporter's own timer
      handling; the dump restores the cursor lines and the play time in
      both programs (C).
    - Measured (stereo song): `sapr` 920 -> 520 ms, `lzss` 2370 -> 1980 ms,
      `wav` 1560 -> 1170 ms. The gain is the stopped timer thread (it
      rendered sound at 50 Hz beside the export); the redraws themselves
      were cheap at this window size.
    - Found on the way: the script run's window was only hidden until the
      program had once been closed interactively - MFC shows the frame
      with the placement's saved `showCmd` during `ProcessShellCommand`.
      That showed the window in script runs and wrote a layout-dependent
      `g_cursoractview` into `.rmw` saves (the comparison caught it).
      Fixed with `m_nCmdShow = SW_HIDE` before the shell command.
      `RMT_SCRIPT_SHOW_WINDOW=1` shows it on purpose (documented). Both
      script runners print the export time.
    - Verified: RmtTests 416, Java 595, `compare_exports.ps1` identical,
      a hidden script run has no window, a shown one stands still during
      the export.
  - **2026-09-28**: Java toolbar buttons back to the C++ size (the user:
    "The toolbar buttons in java are too large"). B9c had enlarged the
    32x30 button images 2x/3x on HiDPI displays as an improvement over
    `Rmt.exe`; the C++ size is the reference: `PixelIcon` now draws the
    images 1:1 in device pixels at every Windows scaling (still without
    Swing's blurry fractional scaling), `iconScale()`/`scaled()` removed,
    the combo box height follows. Checked on the 150 % display: the Java
    toolbar matches `Rmt.exe`'s. 594 Java tests (-1, the removed helpers' test).
  - **2026-09-28**: the Windows standard keys (the user's decision, the
    planned 1.35 item): F1 opens the help in both programs (a label hint
    without a binding in `Rmt.rc`, hint-only in the Java port), New is
    Ctrl+N (was Ctrl+W), Open Ctrl+O (was Ctrl+L), Print Ctrl+P. The three
    editor functions on those keys moved to free letters: "Insert new
    empty unused track" Ctrl+T, "Insert copy or clone of song lines"
    Ctrl+K, "Insert new line with unused empty tracks" Ctrl+J - in
    `Rmt.rc` (accelerators, labels, prompts), `TrackKey`/`SongKey`
    (`GUI_Song.cpp`), `Actions.java`, `SongInput.java` (`VK_J`/`VK_K`/`VK_T`
    added to `VirtualKey`), the manual, the change history (the planned
    item removed, done). Printing is not ported in Java: Ctrl+P there
    reports "not available", as the menu entry does. The user asked
    whether Print works in Java at all - it does not (deliberately
    unported, with MIDI and the Pokey Explorer).
    `doc/rmt_action_infos.md` regenerated; both programs' tables identical.
    The user-facing entries since the port started moved to their own
    section of `doc/rmt_changes.md` ("Changes in RMT 1.35 since the start
    of the Java port"), the file's mixed line endings normalised.
