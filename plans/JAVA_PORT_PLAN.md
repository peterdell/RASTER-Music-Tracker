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

## Next steps

Continue porting small, already-tested, UI-free model classes one at a
time (matching this batch's scope and verification rigor), building up
`com.wudsn.tools.rmt.model` before attempting `CSong` or anything in
`com.wudsn.tools.rmt.ui`. No specific next class has been chosen yet.
