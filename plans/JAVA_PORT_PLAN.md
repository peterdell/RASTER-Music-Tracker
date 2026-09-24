# Plan: kicking off the C++-to-Java port

## Context

`plans/OVERALL_PLAN.md`'s ultimate goal, now that the characterization-
testing phase is essentially exhausted (`plans/BROADER_SURVEY_PLAN.md`)
and `plans/UI_SURVEY_PLAN.md` has inventoried the current UI. This plan
records the setup decisions made to actually start the port, following the
same author's own prior, now-complete C++-to-Java port
(`dis6502`/`jdis6502`, `C:\jac\system\Java\Programming\Repositories\dis6502`)
as precedent wherever it applies.

## Decisions made

1. **In-repo, not a separate repository** (the user's own proposal,
   evaluated and adopted over blindly copying `dis6502`'s separate-repo
   layout): `src/java` sits parallel to `src/cpp`; `lib/java` sits
   parallel to `lib/x64`. This actually fits this repo's own history
   better than `dis6502`'s precedent - `plans/OVERALL_PLAN.md`'s very
   first instruction was to move C++ into a `src/cpp` subfolder, clearly
   anticipating a future sibling, whereas `dis6502`'s own C++ repo was
   never restructured that way (its `src/` has no `cpp` subfolder),
   which is why that port ended up in a separate repository instead.
   Benefit: `rmt/` (driver/player `.obx` binaries, sample songs), `doc/`,
   and most of `plans/` (already largely Java-port-focused) are shared
   with zero duplication, and cross-language changes can be one commit
   series instead of two repos to keep in sync.
2. **`lib/java` is for vendored, pre-built third-party jars** (e.g. a
   future official ASAP Java library, the equivalent of the vendored,
   auto-generated `src/cpp/asap/` C code) - **not** Maven-managed
   dependencies. Those go in `pom.xml`'s `<dependencies>` as normal (see
   `lib/java/README.md`).
3. **Depend on WUDSN Base** (`com.wudsn.tools.base`/`.base.atari`),
   matching `dis6502`. Confirmed already installed locally
   (`~/.m2/repository/com/wudsn/tools/...`, version `1.0.0-SNAPSHOT`) -
   no rebuild needed to start. Its Atari module has some directly
   relevant utilities (e.g. `SAPFile.isHeader()` - SAP-format detection,
   narrower than RMT's own `CSAPFileExporter` but written by the same
   author for a related tool) worth reusing where they genuinely overlap;
   RMT's own model/export logic still gets ported from the C++ source
   either way.
4. **Start with the smallest, already-fully-tested model classes**, not
   `CSong` - matching `dis6502`'s own hard rule ("port the model/logic
   layer completely, with unit tests, before starting on `ui/`") and
   deferring `plans/FILE_TIERING_STRATEGY.md`'s bigger composition-vs-
   mechanical question until `CSong` itself is reached.
5. **Maven groupId/artifactId**: `com.wudsn.tools`/`com.wudsn.tools.rmt`,
   matching `dis6502`'s own `com.wudsn.tools`/`com.wudsn.tools.dis6502` -
   this repository (`github.com/peterdell/RASTER-Music-Tracker`,
   upstream `raster-atari-org/RASTER-Music-Tracker`) is downloadable from
   wudsn.com per its own README, the same umbrella as `dis6502`/WUDSN
   Base (same author).
6. **Package split**: `com.wudsn.tools.rmt.model` / `.ui`, mirroring
   `dis6502`'s exact `model`/`ui` split (confirmed by reading its actual
   `src/com/wudsn/tools/dis6502/` layout, not assumed from its `CLAUDE.md`
   prose alone).
7. **Java 21, JUnit 5 (Jupiter)** - matching `dis6502`'s Java version;
   JUnit 5 chosen fresh rather than replicating `dis6502`'s own JUnit 3
   bridge (`TestRunner`/`TestRunnerTest`), which was that project's own
   historical baggage, not a convention to carry forward.

## Project layout

```
pom.xml                          # Maven module root, at the repo root
src/java/                        # sourceDirectory - production code
  com/wudsn/tools/rmt/model/     # ported model classes (no UI coupling)
  com/wudsn/tools/rmt/ui/        # not started yet
  test/                          # testSourceDirectory - nested *inside* src/java
    com/wudsn/tools/rmt/model/
lib/java/                        # vendored non-Maven jars (empty for now)
  README.md
```

**Real pitfall hit and fixed while setting this up**: `src/java/test`
being nested *inside* `src/java` (to mirror `src/cpp/test`'s own nesting
inside `src/cpp`) means Maven's default main-source scan picks up the
test sources too, compiling them without the test-scoped JUnit dependency
on the classpath - a confusing "cannot find symbol: assertEquals" error
that has nothing to do with the dependency itself being missing. Fixed
with an explicit `maven-compiler-plugin` `<excludes>test/**</excludes>`
on the main `compile` execution.

**Build/test commands** (from the repository root):
- `mvn -o test` - compile + run every test. Works fully offline once the
  one-time setup below has run.
- `mvn -o compile` / `mvn -o test-compile` - compile only.
- **One-time, needs network access**: the local Maven cache didn't have
  the `surefire-junit-platform` provider (needed to run JUnit 5 tests via
  Surefire) cached - only `surefire-junit3` was present (`dis6502`'s own
  legacy JUnit 3 bridge never needed it). Running `mvn test` once *without*
  `-o` downloaded it (and `junit-platform-launcher`); after that, `-o`
  (offline) works normally. If a future machine hits the same "provider
  not found" error, this is why - just run once without `-o`.

## First ported class: `Fraction` (`com.wudsn.tools.rmt.model.Fraction`)

Ported from `CFraction` (`src/cpp/Fraction.h`/`.cpp`) as the first,
smallest, dependency-free class - a deliberate "establish conventions"
choice, not because it's on any critical path.

- **Design choice**: made immutable (every arithmetic method returns a
  new `Fraction` rather than mutating `this`), folding C++'s
  `simplify()`/`fix_sign()`/`reduction()` into the constructor directly.
  Matches `dis6502`'s own guidance ("prefer idiomatic substitutions when
  they produce identical behavior... judge by does it produce the same
  behavior for the user, not does the code structure match"). C++'s
  distinct pre-/post-increment operators collapse into one `increment()`
  method as a result - noted in `Fraction`'s own javadoc, since the (not
  yet ported) call sites in `RmtView.cpp`/`TuningTypes.h` will need to
  account for this when their turn comes.
- **Real bug found and fixed in both languages** (user's explicit
  decision, matching `dis6502`'s established policy for provable bugs
  during active porting): `operator==`/`equals()` checked the reduced
  difference's *denominator* for zero, but the constructor's own
  reduction always leaves a non-zero denominator - so it evaluated to
  false for every pair of operands, including equal fractions. Already
  flagged in `FractionTests.cpp`'s own characterization test as needing a
  deliberate decision "before anyone starts using it" - no production
  code relied on it (verified by repo-wide search) before fixing. Fixed
  in `Fraction.cpp` (checks `numerator == 0` now) and its Java port
  (`equals()`), with `FractionTests.cpp`'s test renamed from
  `EqualityOperatorIsCurrentlyAlwaysFalse` to
  `EqualityOperatorComparesValue` and its assertions updated to match.
  C++ side verified with a full `Rmt.exe`/`RmtTests.exe` rebuild (301
  tests pass, 0 regressions); Java side verified with `mvn -o test` (13
  tests pass).

## Second ported batch: `Tuning`/`TuningSettings`/`TuningRatios`

Ported from `CTuning`/`TTuningSettings`/`TTuningRatios`
(`src/cpp/Tuning.h/.cpp`, `src/cpp/TuningTypes.h/.cpp`).

- **Scope decision (asked explicitly - genuine fork)**: `CTuning`'s own C++
  source is already split across `Tuning.cpp` (pure pitch math, fully
  covered by `TuningTests.cpp`'s golden-master values) and
  `TuningTables.cpp` (`GenerateTable`/`InitTuning`, which read C++ global
  tuning state and have **zero** existing test coverage - never
  characterized, since that split was made specifically so tests could
  link the pure math without pulling in `Global.h`). The user chose to
  port only the pure-math half now (matching `Tuning.cpp`'s own scope) and
  defer `GenerateTable`/`InitTuning`/`GetTruePitch`/`CalculateDeltaAUDF`/
  `Timbre`/`TTuning` to a follow-up batch, rather than porting untested
  logic with no golden master to verify against.
- **`Tuning`** (`com.wudsn.tools.rmt.model.Tuning`): `getPitch`/`getAUDF`/
  `getPOKEYPitch` only, using the C++ test-only constructor
  (`Tuning(int clockFrequency)`) as the *only* constructor, since without
  the table-generation half there's no need for C++'s two-argument
  `InitTuning(clockFrequency, table_memory)` entry point. `Pitch`/`AUDF`
  (C++ `typedef double`/`typedef int`) become plain `double`/`int`, same
  as `Fraction`'s treatment of its own fields.
- **`TuningSettings`**: a plain mutable field-holder (`basetuning`,
  `basenote`, `temperament`) plus `initialize(boolean ntsc)`, matching
  `TTuningSettings`'s own struct-plus-`Initialize()` shape (unlike
  `Fraction`, immutability doesn't fit here - this is a settings struct
  meant to be mutated by not-yet-ported UI code). C++ leaves
  `basetuning`/`basenote` genuinely uninitialized until `Initialize()`
  runs; Java's mandatory field zero-initialization means that hazard
  simply doesn't exist on this side.
- **`TuningRatios`**: 13 `Fraction` fields plus `initialize()`, directly
  reusing the already-ported `Fraction` - the reason this class was worth
  including in the same batch. Field names are idiomatic Java camelCase
  (`min2nd`, `perf5th`, etc.) rather than the C++ struct's
  `SCREAMING_SNAKE_CASE`, mapped 1:1 to the same intervals in the same
  order.
- **Tests**: `TuningTest`/`TuningSettingsTest`/`TuningRatiosTest` mirror
  `TuningTests.cpp`/`TuningTypesTests.cpp` exactly, including the
  `minorSecondIsStoredReduced` characterization test (documents that the
  40/38 literal is stored reduced to 20/19). No C++ changes were needed
  for this batch - no bugs found. Verified with `mvn -o test`: 28 tests
  pass (13 `Fraction` + 15 new).

## C++ follow-up (2026-09-24): filled in the deferred `GenerateTable`/
## `InitTuning`/`GetTruePitch`/`CalculateDeltaAUDF` test coverage

The scope decision above assumed porting those four methods to Java would
need brand-new test coverage "written from scratch, not just mirrored" -
that's still true for the *Java* side, but the *C++* side turned out to
already have everything needed to characterize them safely, which hadn't
been checked at the time:

- `g_tuning`/`g_tuningRatios` are already real, linked globals in
  `test/SongEditingStub.cpp` (added for the `CSong`/`SongEditing.cpp` work,
  after the original `CTuning` split). `InitTuning()`'s `MessageBox`+
  `exit(1)` guard only fires while `g_tuning.basetuning == 0`, so calling
  `g_tuning.Initialize(false)` first (already itself tested, in
  `TuningTypesTests.cpp`) avoids it entirely - the exact same technique
  `SongEditingTests.cpp` already uses. This was missed when the Java-port
  scope question was asked; the C++ side was never actually this
  hazardous once the rest of the test project had grown around it.
- `GetTruePitch`/`CalculateDeltaAUDF`/`GenerateTable` moved from `private`
  to `public` in `Tuning.h` (one-line comment, matching the established
  `CCompressLzss::Optimise_*` visibility-only precedent) - `GetTruePitch`/
  `CalculateDeltaAUDF` are pure regardless; `GenerateTable` still reads
  `g_tuning` directly (unchanged), but that's fine once it's initialized.
- `test/TuningInitStub.cpp` (the old link-only empty-body `InitTuning()`)
  is now obsolete and was deleted; `test/RmtTests.vcxproj` links the real
  `TuningTables.cpp` directly instead. New `test/TuningTablesStub.cpp`
  supplies `g_notesperoctave` (defined for real in `Global.cpp`, which
  still isn't linked here - same treatment as `g_tuning`/`g_tuningRatios`
  in `SongEditingStub.cpp`).
- Added 24 tests to `TuningTests.cpp` (301 -> 325, all passing, verified
  with a full Release|x64 solution rebuild): `GetTruePitch` (equal
  temperament including the octave-doubling identity, a full 12-note
  preset row, and a *ragged* 6-note preset row - characterizes the
  notesnum-detection scan, not just the common case), `CalculateDeltaAUDF`
  (one test per distortion/timbre branch, including both "invalid
  timbre for this distortion" fallbacks via a synthesized out-of-enum
  `Timbre` value), `GenerateTable` (8-bit and 16-bit-joined table
  generation), and `InitTuning` (byte-level checks across all 13 real
  lookup-table offsets it writes, plus confirming it populates the
  private `CUSTOM[]` array `GetTruePitch`'s `TUNING_CUSTOM` branch reads).
  All expected values are golden-master captures (placeholder assertion,
  run, read the real value from the failure diagnostic), not hand-derived
  - this arithmetic (modulo-driven branching, ragged-array scans) is not
  safe to hand-verify. No further C++ bugs found this batch.
- **This unblocks a future Java follow-up batch** for these same four
  methods with actual golden-master values to port against, resolving the
  original reason they were deferred from the Java `Tuning`/
  `TuningSettings`/`TuningRatios` batch above.

## Next steps

A Java follow-up batch for `GenerateTable`/`InitTuning`/`GetTruePitch`/
`CalculateDeltaAUDF`/`Timbre`/`TTuning` is now unblocked (see above) - would
still need redesigning to take explicit parameters instead of reading C++
globals, since no Java global-state architecture exists yet, but now has
real golden-master values from the C++ side to verify against.

Beyond that, continue porting small, already-tested, UI-free model classes
one at a time (matching this batch's scope and verification rigor),
building up `com.wudsn.tools.rmt.model` before attempting `CSong` or
anything in `com.wudsn.tools.rmt.ui`.
