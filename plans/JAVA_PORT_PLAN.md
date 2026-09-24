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

## Third ported batch (2026-09-24): finished `Tuning` - `GenerateTable`/
## `InitTuning`/`GetTruePitch`/`CalculateDeltaAUDF`/`Timbre`

With the C++ side's own test coverage backfilled (see above), this batch
had real golden-master values to port against instead of needing to guess
or hand-derive - and every value matched on the first `mvn -o test` run,
confirming the transcription was faithful.

- **`Timbre`** (`com.wudsn.tools.rmt.model.Timbre`): a Java enum whose
  constants each carry their C++ byte value (`public final int value`) via
  a constructor - needed because `CalculateDeltaAUDF`/`GenerateTable` both
  extract the high nibble from a `Timbre` value (`timbre.value & 0xF0`) to
  determine which distortion group it belongs to, a pattern Java enums
  don't support natively without an explicit backing field.
- **`generateTable()`/`initTuning()` take `TuningSettings`/`TuningRatios`
  explicitly** instead of reading C++'s `g_tuning`/`g_tuningRatios`
  globals - the redesign flagged as needed back when this batch was
  deferred, now implemented. `generateTable()` also gained an explicit
  `offset` parameter (Java has no pointer arithmetic to express
  `table_memory + 0x100` the way C++ does) and a caller-supplied `byte[]`
  in place of a raw pointer.
- **`initTuning()`'s `MessageBox`+`exit(1)` guard becomes an
  `IllegalStateException`** - the same idiomatic substitution already
  established for `Fraction`'s division-by-zero guard, not a new pattern.
- **Deduplicated one piece of logic C++ itself duplicates**: `GetTruePitch`
  and `InitTuning` both scan a `temperament_preset` row for its first
  zero/padding entry to find how many notes per octave that preset
  defines - identical loops in the C++ source. Unified into one private
  `computeNotesPerOctave()` helper in the Java port, since nothing depends
  on keeping the two copies separate and this doesn't change behavior.
- **Two private C++ constants silently omitted**: `dist_4_buzzy`/
  `dist_c_unstable` are declared in `Tuning.h` but never actually
  referenced by `InitTuning()` there either - confirmed dead code in the
  C++ source itself before leaving them out of the Java port.
- **`TuningTable`** (private nested Java `record`, was the C++ struct
  `TTuning`): kept private since, like its C++ counterpart, it's only ever
  used internally to hold `CTuning`'s own 5 (of 7 declared, 2 dead) `dist_*`
  constants - never part of any public API surface in either language.
- One minor, deliberate divergence from C++'s exact structure: `GenerateTable`
  computes `MOD7`/`MOD15`/`MOD73` locals that are never actually read by any
  of its branches (confirmed by re-reading `TuningTables.cpp`'s switch
  statement before omitting them) - dropped as dead computation in the
  Java port, noted inline.
- Tests (`TuningTest`, using `@Nested` classes for the `GenerateTable`/
  `InitTuning` fixtures, mirroring `TuningGenerateTableTest`/
  `TuningInitTuningTest` in the C++ file) reuse the exact same
  golden-master values already captured on the C++ side - deliberately,
  since the whole point of doing the C++ batch first was to get real
  expected values instead of re-guessing them for Java. One C++ test
  (the "invalid timbre for this distortion" fallback, only reachable via
  `static_cast<Timbre>(...)` on an out-of-enum byte value) has no Java
  equivalent - Java's `Timbre` is a closed, type-safe enum with no way to
  synthesize a non-existent constant, and this fallback is unreachable via
  any real `Timbre` value in either language - noted in a comment rather
  than characterized.
- Verified with `mvn -o test`: 51 tests pass (13 `Fraction` + 3
  `TuningRatios` + 2 `TuningSettings` + 21 `Tuning` (flat) + 3
  `GenerateTableTest` + 9 `InitTuningTest`), all green on the first run.

## Fourth ported batch (2026-09-24): `Notes` (`com.wudsn.tools.rmt.model.Notes`)

Ported from `CNotes` (`src/cpp/Notes.h/.cpp`) - chosen as the next
smallest, dependency-free, already-tested class (`NotesTests.cpp`, 8
tests). `CNotes` has no instance state at all (every method is `static` in
C++), so the Java port is a non-instantiable class (private constructor)
with all-static methods, rather than an object.

- **Genuine scope fork, asked explicitly**: `IsValidNote()` has a known,
  already-characterized off-by-one bug (accepts `note == 61` past its
  documented "0-60 inclusive" range). Unlike `Fraction::operator==`, this
  is **not** dead code - it's called from `Tracks.cpp`/`InstrumentsCore.cpp`/
  `IO_Tracks.cpp`/`SongEditing.cpp` (via `CTracks::IsValidNote`'s
  delegation to it), so fixing it would be a real production behavior
  change needing its own investigation of every call site. User chose to
  preserve it faithfully in the Java port rather than fix it as a
  side-effect of this small class's port - noted in `Notes`'s own javadoc
  and its test's comment, matching the C++ characterization test's own
  wording.
- Tests (`NotesTest`) mirror `NotesTests.cpp` exactly, including the
  off-by-one characterization test. Verified with `mvn -o test`: 59 tests
  pass (13 `Fraction` + 8 `Notes` + 3 `TuningRatios` + 2 `TuningSettings` +
  33 `Tuning`/nested). No C++ changes - no new bugs found, and the known
  one was deliberately preserved, not fixed.

## Fifth ported batch (2026-09-24): `ChannelControl` (`com.wudsn.tools.rmt.model.ChannelControl`)

Ported from `CChannelControl` (`src/cpp/ChannelControl.h/.cpp`) - per-channel
on/off/toggle/solo state, no globals, no known bugs, fully tested
(`ChannelControlTests.cpp`, 7 tests).

- **Idiomatic simplification, no behavior change**: C++'s private
  `SetChannelOnOff(ch, onoff)` multiplexes "one channel or all channels"
  (`ch == -1`) and "set or toggle" (`onoff == -1`) behind two sentinel
  parameters, and `SetChannelSolo()` uses a `goto` to share its "turn
  everything off, then turn on just the target" tail between two branches.
  Ported as direct, single-purpose methods instead (`setAllChannelsOn`/
  `Off`, `toggleAllChannelsOnOff`, an inlined solo check) - Java has no
  `goto`, and tracing `SetChannelSolo()`'s two branches confirmed the
  simplification preserves the exact same behavior (the "turn everything
  on" case triggers only when the target is on *and* no other channel is
  on; every other case turns everything off then turns on just the
  target).
- `std::vector<bool>` becomes a plain `boolean[]` (fixed size after
  construction in both languages - nothing ever adds/removes channels).
- Tests (`ChannelControlTest`) mirror `ChannelControlTests.cpp` exactly.
  Verified with `mvn -o test`: 66 tests pass (+7). No C++ changes, no bugs
  found.

## Sixth ported batch (2026-09-24): `Track`/`Tracks` (`com.wudsn.tools.rmt.model`)

Ported from `CTracks`/`TTrack` (`src/cpp/Tracks.h/.cpp`, `TrackTypes.h`,
`src/cpp/IO_Tracks.cpp`) - the first meaningfully larger class since
`Fraction`, and the first genuinely-sized scope decision since `Tuning`.

- **Scope, decided by matching existing test coverage** (same default
  established for `Tuning`, not re-asked since the pattern is now well
  established): ported everything `TracksTests.cpp` already characterizes
  - `IsEmptyTrack`/`ClearTrack`/`InsertLine`/`DeleteLine`/
  `CalculateNotEmpty`/`CompareTracks`/`TrackOptimizeVol0`/
  `GetModifiedNote`/`GetModifiedInstr`/`GetModifiedVolumeP`/`TrackToAta`/
  `AtaToTrack` (the last two are pure - no globals - despite living in
  `IO_Tracks.cpp`) - plus the trivial inline validity checks/getters they
  depend on. **Deferred, untested in C++ either**: `TrackBuildLoop`/
  `TrackExpandLoop`/`ModifyTrack`/`GetTracksAll`/`SetTracksAll` (declared
  in `Tracks.h` but with no existing characterization tests - same
  reasoning as `GenerateTable`/`InitTuning`'s original deferral). Also
  deferred, matching the C++ source's own split: `TracksEdit.cpp`'s
  `g_Undo`-coupled editing methods, and `IO_Tracks.cpp`'s untested
  `SaveTrack`/`LoadTrack`/`SaveAll`/`LoadAll` stream I/O (two on-disk
  formats, `CString`-heavy).
- **`Track`** (was the C++ struct `TTrack`): a plain mutable class with
  public fields, matching `TuningSettings`'s "settings struct" treatment
  rather than `Fraction`'s immutability - callers mutate its fields
  directly by index throughout, in both languages.
- **Unsigned-byte care in `trackToAta`/`ataToTrack`**: C++'s
  `unsigned char*` becomes a Java `byte[]`, so every read masks with
  `& 0xFF` (via a small `unsignedByte()` helper) before using the value in
  arithmetic or comparisons - Java `byte` is signed, and an unmasked read
  of a byte `>= 0x80` would sign-extend to a negative `int` and corrupt
  the decode. Writes need no equivalent care: a narrowing `(byte) value`
  cast on an already-correctly-computed 0-255 `int` produces the exact
  same bit pattern either way.
- **C++'s `WRITEATIDX`/`WRITEPAUSE` macros** (which `return -1` directly
  from `TrackToAta` on buffer overflow) become a private `writeAt`/
  `writePause` pair taking a single-element `int[]` as a mutable output
  parameter for the write cursor, since Java has no macros and no
  multiple-return - callers check the boolean result and propagate `-1`
  explicitly. Faithful, if more verbose than the C++ macro.
- **A latent hazard characterized, not fixed**: `AtaToTrack`'s decode loop
  has no branch for `data == 63` with `count == 0x40` - if ever
  encountered, the loop wouldn't advance `src` (infinite loop). Confirmed
  this combination is never produced by `TrackToAta`'s own encoder (only
  reachable from a malformed/corrupted byte stream), so it's preserved
  as-is with a comment rather than hardened against - a "how to handle
  corrupted input" question is a different kind of decision than a
  same-input-different-output bug, and out of scope for this port.
- Tests (`TracksTest`, with `@Nested` classes mirroring `TracksTests.cpp`'s
  own `TracksModifiedValueTest`/`TrackAtaFormatTest` fixtures) mirror the
  C++ file exactly. Verified with `mvn -o test`: 81 tests pass (+15), all
  green on the first run. No C++ changes, no new bugs found.

## C++ follow-up (2026-09-24): backfilled the deferred `CTracks` methods'
## test coverage

Added 15 characterization tests to `TracksTests.cpp` for
`TrackBuildLoop`/`TrackExpandLoop` (both overloads)/`ModifyTrack`/
`GetTracksAll`/`SetTracksAll` - the five methods deferred from the `Tracks`
Java-port batch above for having no existing test coverage, following the
exact playbook already established for `CTuning`'s `GenerateTable`/
`InitTuning` (write tests with placeholder assertions where the logic is
too intricate to hand-derive safely, run once, read the real values from
the failure diagnostics, fill them in).

- **`TrackBuildLoop`**: searches for the shortest repeating suffix (>= 2
  lines, matching an earlier segment, with more than 1 non-empty line
  inside the matched region) and, if found, truncates the track into a
  loop. Golden-master captured (the triple-nested search is too easy to
  mis-trace by hand) - 6 tests, including one that specifically
  characterizes the "more than 1 non-empty line" guard by constructing a
  suffix that matches exactly but is entirely empty, confirming it's
  correctly rejected.
- **`TrackExpandLoop`** (both the track-number and `TTrack*` overloads):
  the inverse - cyclically expands a looped track back to full length,
  including reading back its own just-written expansion mid-loop. 4 tests.
- **`GetTracksAll`/`SetTracksAll`**: a plain deep-copy round trip (no
  branching, hand-derived directly, no golden-master capture needed) - 1
  test.
  - **Found and fixed a test-authoring bug while writing it, not a
    `CTracks` bug**: the first version stack-allocated a local
    `TTracksAll` (`TTracksAll saved;`), which is ~1MB (254 `TTrack`
    entries at ~4KB each) and crashed with a stack overflow. Fixed by
    heap-allocating it (`std::make_unique<TTracksAll>()`), matching how
    `CTracks` itself always heap-allocates `m_track`.
- **`ModifyTrack`**: applies a transposition/instrument-shift/volume-
  percentage change across a line range, optionally filtered by the
  *active* instrument (the most recently seen `instr[]` value at or after
  `from`, not necessarily set on the exact line being modified). 4 tests,
  including the `to >= TRACKLEN` clamping guard and the active-instrument
  filter's exact semantics.
- Verified via a full Release|x64 solution rebuild: `Rmt.exe`/`RmtTests.exe`
  both build clean and all 340 tests pass (325 -> 340, +15, 0 regressions).
  No new C++ bugs found. This unblocks a future Java follow-up batch for
  these same five methods, which now has real golden-master values to
  port against - same benefit the earlier `CTuning` backfill gave the
  `Tuning` batch's own follow-up.

## Seventh ported batch (2026-09-24): finished `Tracks` - `TrackBuildLoop`/
## `TrackExpandLoop`/`ModifyTrack`/`GetTracksAll`/`SetTracksAll`

The Java follow-up unblocked by the C++ backfill above. Every expected
value was reused directly from the C++ golden-master captures (not
re-derived or re-guessed) and matched on the first `mvn -o test` run -
same payoff as `Tuning`'s own C++-first/Java-second sequencing.

- `trackBuildLoop`/`trackExpandLoop` (both overloads, the second taking a
  `Track` directly matching C++'s `TrackExpandLoop(TTrack*)`) ported with
  no structural changes - the triple-nested search and the cyclic-copy
  loop translate directly, no idiomatic-substitution opportunities worth
  taking here (unlike `ChannelControl`'s goto/sentinel cleanup).
- **`TracksAll`** (was the C++ struct `TTracksAll`): a new small class -
  `int maxTrackLength` plus a `Track[TRACKSNUM]` array, matching
  `TuningSettings`/`Track`'s "plain mutable struct" treatment.
  `getTracksAll`/`setTracksAll` do a straightforward deep copy via a
  private `copyTrack` helper (`System.arraycopy` per field array).
- `modifyTrack` takes a `Track` directly (not a track number), matching
  C++'s `TTrack*` parameter - callers already have the `Track` reference
  in hand via `getTrack()`.
- Tests (added to `TracksTest` as more `@Nested` classes, mirroring
  `TracksTests.cpp`'s own `TrackBuildLoopTest`/`TrackExpandLoopTest`/
  `ModifyTrackTest` fixtures) reuse the exact golden-master values already
  captured on the C++ side. Verified with `mvn -o test`: 96 tests pass
  (+15), all green on the first run.
- This completes `CTracks`'s port to the extent the C++ source itself
  allows - the two remaining pieces (`TracksEdit.cpp`'s `g_Undo`-coupled
  editing methods, `IO_Tracks.cpp`'s untested stream I/O) stay deferred
  for the reasons already on record above.

## Eighth ported batch (2026-09-24): `Instrument`/`Instruments`/
## `EnvelopeParameter`/`InstrumentSection`

Ported from `CInstruments` (`src/cpp/Instruments.h`, `InstrumentsCore.cpp`,
`Instruments.cpp`, `InstrumentsAtaFormat.cpp`) - the already-tested subset
only, matching the scoping discipline established for `Tuning`/`Tracks`.

- **In scope** (everything `InstrumentsTests.cpp` already characterizes):
  the constructor, `ClearInstrument`/`InitInstruments`, `SetEnvelopeVolume`,
  `MemorizeOctaveAndVolume`/`RememberOctaveAndVolume`, `InstrToAta`/
  `AtaToInstr`/`AtaV0ToInstr`, plus the trivial inline validity/getter
  methods they depend on.
- **Deferred, untested in C++**: `CheckInstrumentParameters`/
  `RecalculateFlag`/`CalculateNotEmpty`/`GetNote` (simple, but no existing
  test coverage - same reasoning as every other untested-method deferral
  this session) and `GetFrequency` (also needs the not-yet-ported
  `CAtari`'s memory buffer). **Deferred, needs unported I/O
  infrastructure**: `Update`/`SaveAll`/`LoadAll`/`SaveInstrument`/
  `LoadInstrument` (untested stream I/O; `Update()` needs `CAtari`).
  **Deferred, GUI**: `SetCanvas`/`DrawInstrument`/`DrawName`/
  `DrawParameter`/`DrawEnv`/`DrawNoteTableValue`/`GetGUIArea`/
  `CursorGoto` - not part of the model layer. **Skipped outright**:
  `GetInstrumentsAll()` - in C++ this is a zero-copy reinterpret-cast view
  (unlike `GetTracksAll`/`SetTracksAll`'s real deep copy), untested, and
  Java has no equivalent aliasing mechanism to design around without
  inventing new, uncharacterized behavior.
- **Explicit parameters instead of C++ globals**, matching `Tuning`'s
  pattern: `g_tracks4_8` (mono/stereo envelope-volume packing) becomes an
  explicit `stereo` parameter on `setEnvelopeVolume`/`instrToAta`/
  `ataToInstr`/`ataV0ToInstr`; `g_keyboard_RememberOctavesAndVolumes`
  becomes an explicit parameter on `memorizeOctaveAndVolume`/
  `rememberOctaveAndVolume`.
- **No hardware/Atari-memory side effects**: C++'s `ClearInstrument()`
  calls `g_AtariTrackerDriver->InstrumentTurnOff()` and both it and
  `SetEnvelopeVolume()` call `Update()` (writes into "the emulated Atari
  memory"). Neither has a Java equivalent yet since no live-playback
  subsystem has been ported - not a behavior difference to characterize,
  since the concept these calls act on doesn't exist here yet either.
- **`RememberOctaveAndVolume`'s C++ `int& oct, int& vol` output
  parameters** become a small `Instruments.OctaveAndVolume` record return
  value; when disabled, it returns the caller's given `octave`/`volume`
  unchanged (matching C++ leaving the caller's variables untouched).
- `TInstrument`'s `name` field (a fixed-size, cursor-edited `char[]`)
  became a plain `char[32]` on the new `Instrument` class - kept as a
  character array rather than converted to `String`, since the
  not-yet-ported UI edits it character-by-character via a cursor position.
- `EnvelopeParameter` (row indices into the envelope) ported as plain
  `public static final int` constants, not an enum, matching how they're
  actually used as raw array indices throughout. `InstrumentSection`
  ported as a plain Java enum (C++'s explicit `NONE = -1` backing value
  isn't preserved - nothing reads the underlying numeric value anywhere).
  `shpar`/`shenv` (GUI display-metadata tables) were not ported - they're
  only needed by the deferred TXT-format `SaveInstrument`/`LoadInstrument`,
  not by anything in this batch's scope.
- Tests (`InstrumentsTest`, with `@Nested` classes mirroring
  `InstrumentAtaFormatTest`/`InstrumentsCoreTest`) mirror
  `InstrumentsTests.cpp` exactly, translating the global-based test setup
  (`g_tracks4_8 = 4`/`8`, `g_keyboard_RememberOctavesAndVolumes`) into
  explicit boolean arguments. Verified with `mvn -o test`: 112 tests pass
  (+16). No C++ changes, no new bugs found.

## C++ follow-up (2026-09-24): backfilled the deferred `CInstruments`
## methods' test coverage

Added 28 characterization tests to `InstrumentsTests.cpp` for
`CheckInstrumentParameters`/`RecalculateFlag`/`CalculateNotEmpty`/
`GetNote`/`GetFrequency` - the five methods deferred from the
`Instruments` Java-port batch above for having no existing test coverage,
following the same playbook as `CTuning`'s and `CTracks`'s earlier
backfills. All values were hand-derived directly (none of this logic
needed golden-master capture - simple conditionals/loops, not intricate
branching arithmetic like `TrackBuildLoop`'s), and all passed on the first
run.

- **`GetFrequency` turned out not to need the deferral either** - the
  same "re-verify assumptions instead of trusting old scoping notes"
  lesson as `CSong`'s constructor earlier in this project.
  `CAtari::GetByteAt`/`SetByteAt` are plain array accessors with no
  hazard of their own, and `g_Atari` is already a real, cheap, linked
  global (`AtariStub.cpp`) - so testing it needed no new test-only seam,
  just setting bytes into `g_Atari`'s memory before calling it (and
  clearing them again in `TearDown()`, since it's a shared global other
  tests could run after). 6 tests, covering all three distortion-based
  offset branches (0x0C/0x06/0x0E/default) plus the note-table shift.
- **`CheckInstrumentParameters`**: 6 tests, one per clamped field
  (`PAR_ENV_GOTO`/`PAR_TBL_GOTO`/`editEnvelopeX`/
  `editNoteTableCursorPos`) plus the out-of-range-index guard and an
  unchanged-when-within-bounds sanity check.
- **`RecalculateFlag`**: 7 tests, one per flag
  (`IF_FILTER`/`IF_BASS16`/`IF_PORTAMENTO`/`IF_AUDCTL`), the
  "autofilter takes priority over Bass16" rule, and the inclusive
  `0..PAR_ENV_LENGTH` envelope-row scan boundary.
- **`CalculateNotEmpty`**: 5 tests, including one confirming envelope
  rows beyond `PAR_ENV_LENGTH` are correctly ignored.
- **`GetNote`**: 4 tests, covering the note-table-zero shift and the
  invalid-shifted-note rejection (reusing `CNotes::IsValidNote`,
  including its own known off-by-one).
- Verified via a full Release|x64 solution rebuild: `Rmt.exe`/
  `RmtTests.exe` both build clean and all 368 tests pass (340 -> 368,
  +28, 0 regressions). No new C++ bugs found. This unblocks a future Java
  follow-up batch for these five methods, which now has real
  characterization values to port against.

## Ninth ported batch (2026-09-24): finished `Instruments` -
## `checkInstrumentParameters`/`recalculateFlag`/`calculateNotEmpty`/
## `getNote`/`getFrequency`

The Java follow-up unblocked by the C++ backfill above - same payoff as
`Tuning`'s and `Tracks`'s own C++-first/Java-second sequencing: every
expected value was reused directly from the C++ characterization tests and
matched on the first `mvn -o test` run.

- `checkInstrumentParameters`/`recalculateFlag`/`calculateNotEmpty`/
  `getNote` ported with no structural changes - straightforward
  conditionals/loops, no idiomatic-substitution opportunities.
- **`getFrequency` takes the emulated Atari memory as an explicit
  `byte[]` parameter** instead of a `CAtari` instance, resolving the
  design question flagged in the prior "Next steps" - matches
  `Tuning.generateTable`'s own precedent (explicit buffer parameter
  instead of a not-yet-ported hardware/global dependency). The
  `RMT_FRQTABLES`/`RMTPLAYR_PAGE_DISTORTION_2` address constant (0xB000,
  from `Atari.h`/`tracker_obx.h`) is duplicated locally as a private
  constant, matching how other cross-cutting C++ constants have been
  handled pending their own classes' ports.
- Tests (added to `InstrumentsTest` as more `@Nested` classes, mirroring
  `InstrumentsTests.cpp`'s own new fixtures) reuse the exact hand-derived
  values already captured on the C++ side. Verified with `mvn -o test`:
  140 tests pass (+28), all green on the first run.
- This completes `CInstruments`'s port to the extent the C++ source
  itself allows - `Update`/`SaveAll`/`LoadAll`/`SaveInstrument`/
  `LoadInstrument` (need I/O infrastructure not yet ported) and all GUI
  methods stay deferred for the reasons already on record above.

## Tenth ported batch (2026-09-24): `Atari` (`com.wudsn.tools.rmt.model.Atari`)

Ported from `CAtari` (`src/cpp/Atari.h/.cpp`) - the already-tested subset,
plus `Init(bool)`, which turned out not to need its original deferral
either.

- **`Init(bool)` re-verified, not just trusted from the old scoping
  note**: it calls `g_Tuning.InitTuning()`, which is safe as long as
  `g_tuning.basetuning` is set first - already characterized in
  `TuningTests.cpp`. Same "re-verify instead of trusting an old scoping
  note" finding as `CSong`'s constructor and `CInstruments::GetFrequency`
  earlier in this project. Backfilled 2 C++ tests
  (`AtariTest.InitPal/NtscPopulatesOwnMemoryWithTuningTables`) reusing
  `TuningTests.cpp`'s own golden-master byte values directly, since the
  point is to characterize `Init(bool)`'s own wiring (right clock, right
  instance, right memory offset), not `InitTuning()`'s arithmetic (already
  covered). Interestingly, the PAL and NTSC runs produced the *same* byte
  values at this particular low semitone - a real, verified result (both
  configurations genuinely exercised and checked independently), not a
  copy-paste artifact.
- **Deferred, unchanged**: `Init()`/`DeInit()`/`JSR()` - genuine 6502
  DLL/hardware interop, not a scoping question like the others.
- **`GetMemoryAt`/`GetConstMemoryAt` (C++ pointer-into-buffer accessors)
  become a single `getMemory()`** returning the backing array directly -
  Java array indexing already gives write-through access to the same
  buffer, so there's no need for an offset-pointer equivalent or a
  separate const/non-const pair.
- **`init()` takes `TuningSettings`/`TuningRatios` as explicit
  parameters**, matching `Tuning`'s own pattern, and constructs a
  short-lived `Tuning` instance internally to compute the tables - C++'s
  global `g_Tuning` has no Java equivalent yet, and none is needed here
  since nothing outside this call uses the instance afterward.
  `Tuning.initTuning()` writes at offsets relative to the start of
  whatever buffer it's given, so a small scratch buffer sized to just the
  table region is filled first, then copied into this instance's own
  memory at `RMT_FRQTABLES` - the Java equivalent of C++ passing
  `GetMemoryAt(RMT_FRQTABLES)` (a pointer already offset into the full 64K
  buffer).
- **Cleanup**: `Instruments.java`'s private duplicate of `RMT_FRQTABLES`
  (added in the previous batch, pending `CAtari`'s own port) now
  references `Atari.RMT_FRQTABLES` instead, since that class exists now.
- Verified via a full Release|x64 solution rebuild (370 C++ tests) and
  `mvn -o test` (149 Java tests, +9): all green, 0 regressions.

## Eleventh ported batch (2026-09-24): `Undo` - and the first slice of `CSong`

The user asked to continue with `CUndo` next. Investigation found it fully
tested in C++ (34 tests, later found to actually be 30) and not
hazardous - but it's deeply wired into `CSong` (`GetUECursor`/
`SetUECursor`/`UECursorIsEqual`/`BLOCKDESELECT`/`GetSong`/`GetSongGo`/
`GetBookmark`/`GetSongInfoPars`/`SetSongInfoPars`/`SongGetTrack`/
`SongGetGo`/`SetRMTTitle`/`Stop`, plus the `TInfo`/`TSong` types and the
`Part` enum) and needs `CInstruments::GetInstrumentsAll()`, which had been
deliberately skipped when `Instruments` was first ported (a zero-copy C++
view, untested, no clean Java equivalent). Asked the user how to proceed
given this was effectively "start porting `CSong`," the class every plan
doc in this project has deliberately deferred - they chose to start a
minimal slice now, scoped to exactly what `Undo` touches.

- **New classes**: `Part`/`PlayMode`/`EditArea` (enums), `Bookmark` (was
  `TBookmark`), `SongInfo` (was `TInfo`), `SongData` (was `TSong`, the
  whole-song undo snapshot), `Song` (a deliberately minimal `CSong` slice -
  see its own javadoc for exactly what's included/omitted), `UndoType`
  (plain `int` constants, not an enum - `posIsEqual`/`changeTrack`/etc.
  need to accept the same out-of-range synthetic values C++'s own tests
  exercise via `static_cast`, which a closed Java enum can't represent),
  `UndoEvent` (was `TUndoEvent` - drops the `dataIsArray` bookkeeping
  entirely, since Java's GC makes the `delete`/`delete[]` distinction
  moot), `Undo` (was `CUndo`, ported in full).
- **New `InstrumentsAll`** (real deep-copy snapshot, analogous to
  `TracksAll`): C++'s `GetInstrumentsAll()` is a zero-copy view (already
  skipped), but `Undo` genuinely needs snapshot/restore semantics for
  `UETYPE_INSTRSALL` - this is an additive capability built for that real
  need, not a reintroduction of the skipped method. Added
  `Instrument.copyFrom`/`Track.copyFrom`/`TracksAll.copyFrom`/
  `InstrumentsAll.copyFrom` deep-copy helpers along the way (refactoring
  `Tracks`'s existing private `copyTrack` to use the new
  `Track.copyFrom`).
- **Circular C++ global coupling resolved as a one-directional
  reference + one parameter**: C++'s `CSong::Stop()` calls the global
  `g_Undo.Separator()`, while `CUndo::Undo()`/`Redo()` call the global
  `g_Song.Stop()` - two free-standing globals referencing each other.
  `Song.stop(Undo)` takes `Undo` as an explicit parameter instead of a
  stored field, avoiding a circular constructor dependency; `Undo`'s
  constructor stores `Tracks`/`Instruments`/`Song` (it genuinely
  coordinates all three for its whole lifetime, unlike `Song`'s one-off
  need for `Undo`).
- **Omitted, no Java equivalent yet** (all documented in `Undo`/`Song`'s
  javadoc): `BLOCKDESELECT` (needs `CTrackClipboard`), `Stop()`'s
  timer-wait call and `InsertEvent()`'s `g_changes`/`SetRMTTitle()`
  window-title bookkeeping (no UI/window exists), and
  `Instruments.Update()` calls in `PerformEvent` for
  `UETYPE_INSTRDATA`/`UETYPE_INSTRSALL` ("must save to Atari" - matches
  `Instruments`'s own prior omission of the same call).
- **Found and flagged a latent C++ bug, not fixed, in both languages**:
  `ChangeSong`'s `UETYPE_SONGDATA` case never copies the current bookmark
  into its snapshot (only `song`/`songgo`), so the first undo of a
  whole-song-data change restores whatever indeterminate bookmark `new
  TSong` happened to leave there. Not currently characterized by any test
  (the existing test doesn't check the bookmark after undo). Flagged with
  a comment in `Undo.cpp` and preserved with the same omission (for
  parity) in `Undo.java`'s `changeSong`, even though Java's mandatory
  zero-initialization means the *symptom* there is a zeroed `Bookmark`,
  not garbage - the underlying missing-copy oversight is the same in both
  languages.
- Tests (`UndoTest`) mirror `UndoTests.cpp` in full (30 tests). Verified
  with `mvn -o test`: 179 tests pass (+30), all green on the **first**
  build - despite this being the most structurally complex port this
  project has done (5 new collaborating classes plus 2 new snapshot
  types). Full Release|x64 C++ solution rebuild also verified (comment-only
  change).

## Twelfth ported batch (2026-09-24): `SapFile` (`com.wudsn.tools.rmt.model.SapFile`)

Ported from `CSAPFile` (`src/cpp/SAPFile.h/.cpp`) - the already-tested
subset: field getters/setters, `clear`/`normalize`, and `export`.
`Init(const CSong&)` is deferred - untested in C++ (only stubbed there for
linking) and needs `Song` methods this project's deliberately minimal
`Song` slice doesn't have (`GetName`/`IsStereo`/`IsNTSC`/
`GetInstrumentSpeed`), plus a real system-clock read for the export date.

- C++'s `Export(std::ostream&)` becomes `export()` returning a `String`
  directly - simpler than threading a `Writer`/`Appendable` (and its
  checked `IOException`) through for what's fundamentally just building
  text; also matches the existing C++ test's own use of an in-memory
  `std::ostringstream` sink.
- **Known bug fixed in both languages, per the user's explicit decision**:
  the `DEFSONG` line printed `songs` instead of `defaultSong` - already
  characterized (not fixed) in an earlier session's `SAPFileTests.cpp`.
  Ported faithfully at first (matching the earlier "characterize, don't
  fix" call), then the user asked for it to be fixed on both sides before
  committing this batch. Fixed in `SAPFile.cpp`/`SAPFileTests.cpp` and
  `SapFile.java`/`SapFileTest.java` in the same commit; no other
  production code depended on the buggy value (verified by a repo-wide
  search). C++ verified with a full Release|x64 solution rebuild (370
  tests); Java with `mvn -o test` (183 tests).
- C++'s `ThrowRuntimeException` (blocking `MessageBox` + `exit(2)`, not a
  catchable exception - the reason the empty/invalid-type paths are
  untested in C++ too) becomes a thrown `IllegalStateException`, matching
  the established idiom (e.g. `Tuning.initTuning`'s `basetuning == 0`
  guard).
- Tests (`SapFileTest`) mirror `SAPFileTests.cpp` exactly. Verified with
  `mvn -o test`: 183 tests pass (+4). No C++ changes.

## Thirteenth ported batch (2026-09-24): `AsmFileBuilder` (`com.wudsn.tools.rmt.model.AsmFileBuilder`)

Ported from `CASMFileBuilder` (`src/cpp/ASMFileBuilder.h/.cpp`) - fully,
matching `ASMFileBuilderTests.cpp`'s own complete coverage. No globals, no
dependency on any not-yet-ported class, and no deferrals: this is the first
batch this session where the entire C++ class was already both tested and
free of any hazard, and no C++ source changes were needed either.

- **New `AssemblerFormat`** (was the C++ enum of the same name):
  `ATASM`/`XASM`.
- C++'s `CString& strCode` output parameter (overwritten as the method's
  first statement, then appended to) becomes a returned `Result(String
  code, int size)` record pairing the built text with the method's own
  `int` return value - simpler than threading a mutable buffer through as
  a parameter, and Java strings are immutable anyway.
- **A fragile contract preserved as-is, not hardened**: `buildTracksData`'s
  trailing validity check scans `trackPos[0..65535]` unconditionally, not
  just the `[from, to)` range actually processed - the caller's array must
  always have at least 65536 entries regardless of how small a range is
  being built. Already characterized this way in `ASMFileBuilderTests.cpp`;
  the Java test reproduces it with the same oversized array rather than
  changing the contract.
- **Found a real Java `Formatter` incompatibility**, not a C++ bug: C++'s
  error-comment format string in `buildSongData` uses a `"% 04x"`/`"% x"`
  space flag, which C++'s `CString::Format` silently drops for a hex
  conversion (producing one literal space from the string, not the flag).
  Java's `Formatter` throws `FormatFlagsConversionMismatchException` for a
  space flag combined with an `x` conversion - there's no direct
  equivalent. Reproduced the exact same visible output by hand (manual
  string concatenation without the flag) instead.
- Tests (`AsmFileBuilderTest`) mirror `ASMFileBuilderTests.cpp`'s 8 tests
  exactly. Verified with `mvn -o test`: 191 tests pass (+8), all green on
  the first build. No C++ changes.

## Fourteenth ported batch (2026-09-24): `Keyboard2NoteMapping` (`com.wudsn.tools.rmt.model.Keyboard2NoteMapping`)

Ported from the free functions `NoteKey`/`NumbKey`/`Numblock09Key`
(`src/cpp/Keyboard2NoteMapping.h/.cpp`) - fully, matching
`Keyboard2NoteMappingTests.cpp`'s own complete coverage. No C++ changes
needed.

- **New `KeyboardLayout`**: plain `int` constants (`QWERTY`/`AZERTY`), not a
  Java `enum` - `NoteKeyReturnsMinusOneForUnknownLayout` exercises a
  synthetic out-of-range value (`static_cast<KeyboardLayout>(2)` in C++),
  the same reason `UndoType` isn't a Java enum either.
- C++'s global `g_keyboard_layout` becomes an explicit `keyboardLayout`
  parameter to `noteKey`, matching the established idiom for globals a
  ported method actually needs.
- C++'s lookup tables are `unsigned char[256]` holding `0xFF` for
  "unmapped", read back through a `char`-returning function - a signed
  narrowing conversion on this project's platform, turning `0xFF` into
  `-1`. The Java tables are `byte[]` with `-1` written directly in place of
  every `0xFF` (the same bit pattern a Java `byte` holds either way), so no
  runtime conversion is needed.
- **A fragile contract preserved as-is**: neither the C++ nor the Java
  version bounds-checks `vk` against the tables' `0..255` range - already
  characterized this way in `Keyboard2NoteMappingTests.cpp`, which only
  ever exercises in-range values.
- Tests (`Keyboard2NoteMappingTest`) mirror `Keyboard2NoteMappingTests.cpp`'s
  7 tests exactly. Verified with `mvn -o test`: 198 tests pass (+7), all
  green on the first build. No C++ changes.

## Fifteenth ported batch (2026-09-25): `RmtCommandLineInfo` (`com.wudsn.tools.rmt.model.RmtCommandLineInfo`)

Ported from `CRmtCommandLineInfo` (`src/cpp/RmtCommandLineInfo.h/.cpp`) -
the `SCRIPT:`/`TEST:` switch parsing, which is all
`RmtCommandLineInfoTests.cpp` exercises. No C++ changes needed.

- **Omitted, no Java equivalent, no test depends on it**: C++'s fallback to
  `CCommandLineInfo::ParseParam()` (MFC's own default handling of plain
  filenames and unrecognized flags) for the `bFlag=false` case and the
  "neither switch matched" case - has no observable effect in any existing
  test, so it's simply left out rather than reintroducing an MFC
  dependency for untested behavior.
- **Minor simplification, not a behavior change**: C++ calls
  `GetSwitchName(switchString)` twice in a row (once for the `SCRIPT`
  check, then redundantly again, identically, for the `TEST` check) -
  `parseParam` computes it once, since the two calls are provably
  equivalent and neither call has any side effect.
- Drops the `CCommandLineInfo` base class entirely - the ported class is a
  plain data holder with a `parseParam` method, not an MFC command-line
  parser subclass.
- Tests (`RmtCommandLineInfoTest`) mirror `RmtCommandLineInfoTests.cpp`'s 7
  tests exactly. Verified with `mvn -o test`: 205 tests pass (+7), all
  green on the first build. No C++ changes.

## Sixteenth ported batch (2026-09-25): `StringUtility` (`com.wudsn.tools.rmt.model.StringUtility`)

Ported from `CStringUtility` (`src/cpp/StringUtility.h/.cpp`) - fully; the
smallest class ported so far, a single one-line static method. No C++
changes needed.

- C++'s `CString::Right(n)` clamps `n` to the string's own length rather
  than throwing or reading out of bounds when the suffix is longer than the
  string - `endsWithNoCase` reproduces that clamp explicitly (`string`
  itself, unsliced, when `suffix` is longer), since Java's
  `String.substring` has no equivalent clamping behavior and would throw
  `StringIndexOutOfBoundsException` for a negative starting index otherwise.
- Tests (`StringUtilityTest`) mirror `StringUtilityTests.cpp`'s 5 tests
  exactly. Verified with `mvn -o test`: 210 tests pass (+5), all green on
  the first build. No C++ changes.

## Seventeenth ported batch (2026-09-25): `CompressLzss` (`com.wudsn.tools.rmt.model.CompressLzss`)

Ported from `CCompressLzss`/`CLzss` (`src/cpp/lzss_sap.h/.cpp`) - an
optimal LZSS compressor for SAP-R register-dump music files (by DMSC,
C++-ported for RMT by VinsCool). Explicitly requested despite this
project's usual "small class" scope: a genuine ~600-line bit-packing
algorithm, the largest and most algorithmically intricate class ported so
far. No C++ changes needed; `lzssp.h` (532 lines of unrelated Atari player
memory-address constants, not used by the compressor at all) turned out to
be a red herring when first scoping this batch.

- Covers exactly what `LzssTests.cpp` exercises: the three `Optimise_*`
  passes (`optimiseAudc`/`optimiseAudctl`/`optimiseAudf`) and the
  compressor's one real call shape - C++'s `LZSS_SAP(src, srclen, dst,
  optimisation)` four-argument overload, always run with `opt='6'`/
  `format_version=0`.
- **New `SapROptimization`** (was `SAPROptimization`): a plain Java enum -
  unlike some other ported enums, no test needs a synthetic out-of-range
  value here.
- **Public API redesigned around Java arrays**: C++'s `LZSS_SAP` writes
  into a caller-supplied, generously oversized `dst` buffer and returns
  the compressed byte count separately - the existing C++ test itself
  immediately resizes its output vector to that count. `compress(byte[]
  src, SapROptimization)` does that trimming internally and returns the
  exact compressed bytes directly.
- **Dead code eliminated, not behavior changed**: C++'s `LZSS_SAP` chooses
  its bit-width/format parameters via two switch statements over local
  variables hardcoded to a single value (`opt='6'`, `format_version=0`)
  with no public API to reach any other branch - hardcoded the one
  reachable outcome directly instead of reproducing unreachable branches.
  Likewise dropped the `registers` parameter (declared, never read in
  `Optimize()`'s body) and the untested 5-argument `LZSS_SAP` overload.
- **Diagnostics omitted, no effect on tested output**: every
  `fprintf(log, ...)` call in `Compress()` - channel-skip notices,
  empty/constant-stream warnings, and the `show_stats`/`stat_len`/
  `stat_off` verbose dump - writes only to a log stream (hardcoded to
  `stderr`), never to the returned buffer or size, so none of it is
  reproduced; the `stat_len`/`stat_off` histograms that exist solely to
  feed that dump are dropped along with it.
- **A real unsigned-vs-signed pitfall caught before running any test**:
  `Optimise_AUDF`'s "two-tone" check compares a raw register byte's
  *magnitude* (`buf[1] < 0xF0`), unlike every other comparison in this
  file (which mask with `&` first, safe under Java's signed-byte
  sign-extension regardless). A literal port would silently invert the
  comparison for any byte `>= 0x80`. Fixed by reusing this project's
  established `unsignedByte(byte[], int)` helper (as in `Instruments`) for
  every register-buffer read in all three `Optimise_*` methods, not just
  this one, for consistency.
- **A fragile contract preserved as-is, manifesting differently**: neither
  version requires `src`'s length to be an exact multiple of the 9-byte
  SAP-R frame size - already an unhandled case in C++ (silently reads past
  `src`'s end for a partial trailing frame, undefined behavior). Java
  throws `ArrayIndexOutOfBoundsException` instead. Not exercised by any
  test or real call site.
- `struct bf`'s fixed `uint8_t buf[128*1024]` scratch array became a
  small growable `BitBuffer` (doubling capacity as needed) instead of a
  literal 128KB allocation, since positional bit/half-byte mutation (`buf[
  bpos] |= ...`) needs random-access writes that a plain append-only
  structure like `ByteArrayOutputStream` can't provide; `struct bf`'s
  separate `out`/`total` output fields, which are purely append-only,
  became a `ByteArrayOutputStream` directly.
- Tests (`CompressLzssTest`) mirror `LzssTests.cpp`'s 11 tests exactly,
  including the 3 golden-master compression tests. Verified with
  `mvn -o test`: 221 tests pass (+11), all green on the first build - the
  golden-master byte arrays matched exactly with no debugging needed. No
  C++ changes.

## Eighteenth ported batch (2026-09-25): `Song` grows to cover `SongCore.cpp`

Grew `Song` (previously a deliberately minimal slice, just what `Undo`
needed) to cover all of `CSong`'s `SongCore.cpp` methods - the
already-tested, globals-free "safe cluster" split out from
`Song.cpp`/`IO_Song.cpp` for the same reason `Tuning.cpp`/
`TuningTables.cpp` and `Instruments.cpp`/`InstrumentsCore.cpp` were split.
No C++ changes needed. This is the first direct `SongTest`; previously
`Song` was only exercised indirectly through `UndoTest`.

- **New methods**: `getName`, `getTracks(int tracks4_8)`,
  `isStereo(int tracks4_8)`, `isNTSC`, `getInstrumentSpeed`,
  `playPressedTonesInit`, `getActiveInstr`, `getActiveColumn`,
  `getPlayLine`/`setPlayLine` (the *track* play line - new
  `trackPlayLine` field, distinct from the already-existing
  `getActiveLine`/`setActiveLine`), `songGetPlayLine`/`songSetPlayLine`
  (the *song* play line - new `songPlayLine` field), `songTrackGoDec`/
  `songTrackGoInc`, `findNearTrackBySongLineAndColumn`,
  `songPlayNextLine`, `songToAta`, `ataToSong`. New `TrackFlag` (plain
  `byte` constants `TF_NOEMPTY`/`TF_USED`, for the track-usage byte arrays
  these last two methods take/build).
- **`g_tracks4_8` becomes an explicit parameter** on every method that
  reads it in C++ (`getTracks`, `isStereo`, `songToAta`, `ataToSong`),
  matching the established idiom.
- **One always-true C++ `BOOL` return dropped, for consistency with an
  existing sibling**: `PlayPressedTonesInit()`'s C++ return is
  unconditionally `TRUE`, exactly like `SetPlayPressedTonesSilence()`
  (already ported as `void` in an earlier batch, before any direct
  `SongTest` existed to assert on it) - `playPressedTonesInit()` follows
  suit as `void` too, for symmetry with its already-committed sibling.
  `songPlayNextLine`/`ataToSong` keep their `boolean` returns (matching
  their tests' direct `assertTrue(...)` usage) despite also never
  returning `false` in the current C++ source - no existing Java sibling
  forces a different call here, so their real declared return type is
  preserved instead of guessed at.
- `AtaToSong`'s `unsigned char b >= 0` check is tautological in C++ (an
  unsigned type can't be negative) - dropped in `ataToSong`, which reads
  `b` as an already-correctly-unsigned `int` via the established
  `unsignedByte(byte[], int)` helper.
- Tests (`SongTest`, plus a new `UecursorIsEqualTest` nested class)
  mirror `SongTests.cpp`'s `SongCoreTest` fixture's 14 tests exactly (15
  Java `@Test` methods once the nested class is counted). Verified with
  `mvn -o test`: 236 tests pass (+15), all green on the first build. No
  C++ changes.

## Nineteenth ported batch (2026-09-25): `Messages` (`com.wudsn.tools.rmt.model.Messages`)

Ported from Messages.h/.cpp - the subset `MessagesTests.cpp` exercises. No
C++ changes needed.

- **New `MessageButtons`/`MessageAnswer`** (were the C++ enums of the same
  names): plain Java enums, using Java's conventional
  `SCREAMING_SNAKE_CASE` naming rather than the C++ originals' camelCase
  member spellings (`YesNo`→`YES_NO`, etc.) - unlike every other enum
  ported so far, whose C++ spellings already used that convention.
- **Log-fallback-only, by design**: C++'s `g_statusBar` always stays
  `nullptr` in every test (no real status bar/UI is ever constructed
  there), so every `Send<Type>Message()` always takes the log-fallback
  path in `MessagesTests.cpp`, never the real Win32 `MessageBox()` one.
  This port only implements that log-fallback path - there's no Java
  UI/window to show a dialog in, and no test reaches the other branch.
  `sendQuestionMessage`'s `MessageButtons` parameter is kept purely for
  signature parity, since it fed only the now-omitted `MessageBox()`
  branch.
- **`SendInfoMessage` omitted**: unlike the other four functions, it
  unconditionally calls `SetStatusBarText()` (no log-fallback branch at
  all - it's not exercised by any test either).
- C++'s `g_testQuestionAnswer` (a module-level global test hook) becomes
  an instance field, defaulting to `MessageAnswer.CANCEL` for the same
  "safe/non-destructive choice" reason as the original; `OutputDebugString`
  becomes `System.err` output.
- Tests (`MessagesTest`) mirror `MessagesTests.cpp`'s 4 tests exactly.
  Verified with `mvn -o test`: 240 tests pass (+4), all green on the first
  build. No C++ changes.

## Twentieth ported batch (2026-09-25): `AtariTrackerDriver` (`com.wudsn.tools.rmt.model.AtariTrackerDriver`)

Ported from `CAtariTrackerDriver` (`src/cpp/AtariTrackerDriver.h`,
`AtariTrackerDriver.cpp`, `AtariTrackerDriverCore.cpp`) - only the subset
`AtariTrackerDriverTests.cpp` exercises: the constructor, `GetByteAt`,
`LoadRMTRoutines`, `Init`, `Play`, `SetPokey`, `Silence`. `GetAtari`/
`SetTrackNoteInstrumentVolume`/`SetTrackVolume`/`InstrumentTurnOff` (all in
`AtariTrackerDriverCore.cpp`) have no dedicated test coverage - deferred.
The first Java port needing real file I/O against a bundled resource. No
C++ changes needed.

- **New `TrackerDriverVersion`** (was the C++ enum of the same name): a
  plain Java enum - its declared order already matches C++'s explicit
  backing values (0-7), so `ordinal()` doubles as the value needed for
  driver filenames without a separate backing field.
- **New `AtariIO`**: only `loadDataAsBinaryFile` (the Atari "binary load"
  block-format parser) - the only `CAtariIO` method exercised by any test
  (indirectly, through `loadRMTRoutines`). `LoadWord`/`LoadBinaryBlock`/
  `LoadBinaryFile` all operate on a `std::istream` rather than an
  in-memory buffer and have no dedicated coverage - deferred. C++'s output
  parameters (`minadr`/`maxadr`) become fields on a returned `Result`
  record.
- **New `RmtAtariBinaries`**: only `getTrackerDriverBinary` (`GetVUPlayerBinary`
  isn't tested - deferred). Resolves `rmt/resources/drivers/rmt_driver_v<N>.obx`
  relative to the current working directory rather than C++'s
  `g_prgpath` (the running executable's own directory) - matches this
  whole Java project's own documented convention of running tests as
  `mvn -o test` from the repository root, where that folder already
  exists unmodified. Doesn't reproduce C++'s per-version in-memory cache -
  no test depends on single-load behavior, and re-reading a small file
  per call is simple and safe.
- **C++'s `C6502::JSR`/`CAtari::JSR` are entirely unimplemented, not
  merely stubbed**: even in the C++ test build, `C6502::JSR` is already a
  link-only no-op that leaves every register/cycle argument unchanged (see
  `AtariStub.cpp`). Since `Init`/`Play`/`SetPokey`/`Silence` have no other
  observable effect once that (already-inert) call is removed, this port
  omits the call entirely rather than modeling a no-op 6502 calling
  convention with nothing left to do - `Play`/`SetPokey`/`Silence` are
  plain no-op methods. This also makes `Play`'s `IsSpecialProveMode()`
  branch dead (both branches would call only no-op JSRs either way), so
  it isn't ported either - matching `AtariTrackerDriverTests.cpp`'s own
  characterization of `Play` as "doesn't crash in either mode."
- Added a `getRmtInstrument(int)` accessor (not present in C++, which only
  exposes this via the global `g_rmtinstr` array directly) so `init`'s
  reset behavior stays testable without porting the untested
  `SetTrackNoteInstrumentVolume`/`InstrumentTurnOff` methods that are
  `g_rmtinstr`'s only other real-world writers.
- Tests (`AtariTrackerDriverTest`) cover the 3 tests that remain
  meaningful once `Play`/`SetPokey`/`Silence` are no-ops (their C++
  "doesn't crash" tests are trivially satisfied and not reproduced as
  dedicated tests); `initResetsEveryChannelsInstrumentAndReturnsZero`
  adapted to not depend on the unported pre-set-to-5 step (Java's `int[]`
  default of 0 already demonstrates a real reset to -1). Verified with
  `mvn -o test`: 243 tests pass (+3), all green on the first build,
  including the real resource-file-loading/binary-parsing test matching
  its exact expected byte. No C++ changes.

## Next steps

Continue porting small, already-tested, UI-free model classes one at a
time, building up `com.wudsn.tools.rmt.model`. The `Song` slice ported
earlier is deliberately minimal (just what `Undo` needed) - a real `CSong`
port (playback, file I/O, the full editing surface) remains a much larger,
separate undertaking, matching this project's long-standing "God Object"
deferral. No specific next class has been chosen yet.
