# Plan 37: Tuning compatibility with RMT 1.34 - existing tunes must sound correct in 1.36

Status: IN PROGRESS 2026-10-12. T1, T2, T3 and T4 are DONE - the
cause was neither of the two proposed candidates but a third: a 1.36
initialization-order regression (see the T1 result below). T5 is
open. Reviewing T2 raised two further steps: the generated page has a
128-byte gap that T2's reordering turned into a regression in the
Java port (T6), and the driver's static page is to be aligned with
the generated defaults after all - the decision T2 left open is now
settled the other way (T7).

## Goal

An RMT 1.34-era tune opened or exported in RMT 1.36 must produce the
register stream the composer heard - tuning included. Plan 36 proved
the gap empirically: our rendering of Captain Future Theme differs
from the composer's own RMT 1.34 export by a consistent +-1 AUDF
remap (35 of 36 (register, value) pairs map one-to-one), i.e. by the
frequency tables alone, after every other difference was accounted
for.

## What is already in place (verified 2026-10-11)

1.36 already CONTAINS the complete 1.34 tuning system, in both
programs:

- Table generation: `CTuning` (src/cpp/Tuning.cpp, TuningTables.cpp)
  and its golden-master-tested Java port
  (org.atari.raster.rmt.model.Tuning, 29 temperament presets, custom
  ratios).
- Persistence: `tuning.ini` (TUNING/BASENOTE/TEMPERAMENT + 13 ratios),
  read by CRmtView::ReadTuningConfig() and RmtConfig.java; a
  TuningDialog exists in both UIs. The settings are GLOBAL
  configuration - the .rmt carries nothing (plan 36's provenance gap).
- Application: Atari init writes the generated tables over the
  1.34-generation driver's table page ($B000, RMT_FRQTABLES =
  RMTPLAYR_PAGE_DISTORTION_2) - C++ CAtari::Init(), Java
  Atari.init(), called from every RmtSession lifecycle point. This
  bullet describes the state as FOUND; T2 moved the regeneration to
  the driver load, which is the fix. The
  classic v1-family drivers keep their baked-in tables (they do not
  read $B000), which is why F1 anchored Delta byte-exactly - old
  tunes are NOT affected by this plan.
- Defaults: TuningSettings.initialize() = VinsCool's calibrated
  values (PAL 440.83751645933 Hz / NTSC 444.895778867913 Hz,
  basenote 3, temperament 0), identical in rmt/tuning.ini.

So the F2 dump already played GENERATED tables at default settings -
and still differs from the party stream by +-1. Sameness of system
does not give sameness of output. Exactly two candidate causes
remained at proposal time - T1 below refuted both:

- (a) The composer's tuning settings differed from the defaults
  (tuning.ini is global and invisible in the .rmt).
- (b) Generation drift: our fork imported VinsCool's work in two
  steps (303b03d "1.31 to 1.34", then c4563c0 "latest branch") - our
  generator may be NEWER than the 1.34.00 release build composers
  actually used, with changed rounding or table math. The mixed +-1
  directions in the measured remap look more like a rounding/
  algorithm difference than like a shifted base frequency (which
  would push all values the same way).

## The static page against the generated one (measured 2026-10-12)

Over the whole 1536-byte region - rmt_driver_v6.obx's $B000-$B5FF
segment against our generated PAL defaults - 366 bytes differ, in
three groups that need different treatment:

| Group                              | Bytes | Static            | Generated   | What it is |
| ---------------------------------- | ----- | ----------------- | ----------- | ---------- |
| $B100 distortion 4                 |   173 | all zero          | real values | rmtdata.a65 ships an all-zero placeholder; only the generator fills it |
| $B500-$B57F sawtooth               |   128 | hand-written      | all zero    | hand-made by Vin Samuel (VinsCool); the generator never writes this range (T6) |
| five distortion pages, both 15 kHz |    65 | 1.34-era snapshot | current     | the genuine tuning delta (T7) |

The 65 genuine differences by table: bell 64 kHz 4, bell 1.79 MHz 9,
pure 1.79 MHz 13, buzzy 64 kHz 1, buzzy 1.79 MHz 16, gritty 64 kHz 7,
gritty 1.79 MHz 5, pure 15 kHz 5, buzzy 15 kHz 5. Most are one AUDF
step, but buzzy 1.79 MHz differs by 3 to 8 and seven gritty 64 kHz
entries by 16 to 53, so "the 1.34 tuning" is not a uniform +-1
description of this page. T1's "9 bell-1.79 and 5 pure-15 kHz" is the
subset Captain Future Theme actually plays; this independent full-page
inventory reproduces both counts exactly.

## Proposed steps

- **T1. Diagnose the remap - settings or algorithm? DONE
  2026-10-11: it is NEITHER - it is an initialization-order
  regression in 1.36.** The chain of evidence:
  1. The 1.34.00 release source (tag v1.34.00, commit 3838559,
     2024-07-03 - Buddy's export is 2024-08-13) was fetched and its
     generator replicated numerically. At identical default
     settings, its tables are BYTE-IDENTICAL to our generator's for
     every shared table (bell 2, pure A, buzzy/gritty C, all
     clocks; the layouts differ per driver generation, the content
     does not). Candidate (b), generation drift, is refuted - and
     1.34.00's defaults equal ours exactly (global.h:
     440.83751645933 PAL / 444.895778867913 NTSC, basenote 3,
     temperament 0), settling T3 at source level.
  2. Candidate (a), composer settings, is refuted on first
     principles: the measured remap lives ONLY in the bell-1.79 and
     15 kHz tables (classified per differing byte by unnormalized
     AUDC distortion and AUDCTL clock mode), while the dominant
     pure-A 64 kHz/1.79 tables match perfectly - but ANY settings
     change moves every table. A basetuning scan (400-480 Hz)
     reproduces no part of the remap either.
  3. The real cause: the 1.34.00 driver binary (tracker.obx) ships
     a STATIC $B000-$B510 table segment, and our rmt_driver_v6.obx
     ships the same static values ($B000-$B5FF). These static
     tables differ from the generated ones in exactly 9 bell-1.79
     and 5 pure-A-15kHz entries - exactly the measured remap (the
     extra 15 kHz pairs are vibrato offsets riding on note 0). Our
     chain PLAYED THE STATIC VALUES; Buddy's 1.34.00 played the
     GENERATED ones.
  4. Why: in 1.34.00, init_tuning() is called inside
     Atari_InitRMTRoutine() (Atari6502.cpp:93) - i.e. AFTER
     Atari_LoadRMTRoutines() on every driver reset, so the
     generated tables always overwrite the obx's static page. In
     1.36 the regeneration moved into CAtari::Init()/Atari.init(),
     which runs BEFORE LoadRMTRoutines() at every call site
     (Rmt.cpp:124-128, RmtView.cpp:871-872, ScriptRunner.cpp:
     683-684; Java RmtSession constructor and
     setTrackerDriverVersion) - the obx load clobbers the generated
     page with the static one. Both programs share the regression
     identically (which is why compare_exports stays byte-identical
     between them). Worse, paths that re-init WITHOUT a driver
     reload (reInitSound, stereo/NTSC switches) leave the GENERATED
     tables active - so which tables play today depends on the last
     UI action.
- **T2. Fix the initialization order in BOTH programs. DONE
  2026-10-11.** The regeneration now happens where the clobbering
  happens: C++ inside CAtariTrackerDriver::LoadRMTRoutines() (after
  a successful load - covers Rmt.cpp, RmtView.cpp and
  ScriptRunner.cpp without touching their call order), Java in
  RmtSession.loadTrackerDriver()'s success path via the new
  Atari.initTuning() (split out of Atari.init(); the model-level
  AtariTrackerDriver deliberately has no tuning settings). Each
  program gains a regression test asserting the generated PAL
  bell-1.79 first byte ($D6) wins over the obx's static copy ($D7)
  after a load: AtariTrackerDriverTests.cpp (whose fixture now
  initializes the tuning globals, as AtariTests.cpp does, because
  InitTuning exits on an uninitialized basetuning) and
  RmtSessionTest.java (construction and the driver-switch path).
  679 Java and 431 C++ tests green. Verification against the anchor
  (T4's criterion, run ahead): the fixed rmt.jar's export now
  matches the recorded party stream in 6948 of 7524 frames after
  the documented AUDC_AUDF normalization - the +-1 remap is GONE;
  what remains is exactly the stale-state intro AUDCTL (573 bytes)
  and the 6 transition bytes at frame 576, both artifacts of the
  composer's own dump. The C++ cross-check was briefly blocked by
  an NTFS-corrupted generated doc file (rmt/docs/rmt_scripting.html,
  error 1392 even on delete); the user repaired it the same day,
  Rmt.exe rebuilt cleanly, 431 C++ tests green on the fresh build,
  and build/compare_exports.ps1 reports every exported file of all
  four scripts byte-identical between the fixed Rmt.exe and
  rmt.jar. Two consequences of this fix became steps of their own:
  the generated page does not cover the whole table region, which
  the new order turned into a Java-only regression (T6), and the
  driver's own static page is to be aligned rather than left as
  shipped (T7, which replaces the decision left open here).
- **T3. Default compatibility. DONE 2026-10-11 at source level
  (see T1):** 1.34.00's defaults equal ours for PAL and NTSC. The
  runtime proof comes free with T4.
- **T4. Verify against the composer's artifact. DONE 2026-10-11**
  (the run itself is reported under T2, which needed it as proof).
  Re-run the plan 36
  F2 comparison. Acceptance: the +-1 remap residue is ZERO - the
  streams are byte-identical modulo the documented LZSS
  normalization and the stale-state intro, nothing else. Met.
  Known knock-on effects to handle in the same step: DONE
  2026-10-11 - the committed Captain_Future_Theme.sapr reference is
  regenerated with the fixed export (header and 7524-frame length
  unchanged, 3727 AUDF bytes moved by exactly one step, body
  byte-identical to the dump verified against the party stream);
  plan 33's Simple RMT Player comparison still shows exactly those
  one-step differences, because the standalone player XEX carries
  the static obx tables - T7 removes that difference at its source.
  Delta and the other v1-family tunes are unaffected (no $B000
  read).
- **T5. Record tuning provenance per tune.** Add a "Tuning:" line to
  the fixture metadata sheets (test-resources/songs READMEs), stating
  the settings the anchoring confirmed (expected: "RMT 1.34
  defaults" once T4 passes). This is the manual stand-in until a
  format carries tuning (the RMX TUN segment - out of scope, plan
  36).
- **T6. Complete the generated table page.** The generator writes
  $B000-$B4FF (all five distortion pages, distortion 4 included) and
  $B580/$B5C0 (the pure and buzzy 15 kHz tables). It never writes
  $B500-$B57F, the two hand-made sawtooth tables
  (frqtabsawtooth_ch1/_ch3 in asm/Patch-16/rmtdata.a65). C++ writes
  in place - GenerateTable() into GetMemoryAt(RMT_FRQTABLES) - so
  those 128 bytes keep whatever the driver load put there, while
  Java fills a zeroed byte[0x600] and copies all of it over $B000,
  which CLEARS them. That was harmless while Atari.init() ran before
  the driver load; T2 reversed the order, so the Java port now
  zeroes the sawtooth tables after every load. Measured through the
  real classes: after loadRMTRoutines(PATCH16), $B500 reads
  E4 DE D7 D1 CB C5 BF BA (= rmtdata.a65's frqtabsawtooth_ch1);
  after initTuning() it reads all zeros. A module using the sawtooth
  distortion (rmtplayr.a65's do_sawtooth, CMD6 pointer reversal
  included) therefore plays AUDF 0 in the Java port and correctly in
  C++ - compare_exports does not catch it because Captain Future
  Theme never touches that page. Fix: bake both tables into both
  generators as constants, naming rmtdata.a65 as the source of
  truth, so the generator owns all 1536 bytes and the page becomes a
  pure function of the tuning settings - independent of load order,
  of the selected driver and of the obx's content. They cannot be
  computed: the values are hand-picked and non-monotonic (two
  channels beating against each other), not a scale. Add a test
  asserting the generated $B500-$B57F equals the obx's bytes at that
  offset; with check-drivers.yml already pinning the obx to the asm,
  that closes the chain rmtdata.a65 -> obx -> generator constants.
  Writing in place instead would also work today, but keeps
  correctness resting on an invariant spread over three places - the
  one T2 broke.
- **T7. Align the driver's static page with the generated defaults.**
  A composer hears RMT's generated tables, but a coder who takes the
  .rmt and assembles it with the Patch-16 player gets rmtdata.a65's
  static page: it is included unconditionally (rmtplayr.a65:2294)
  for both the tracker driver and the Simple RMT Player, and
  FEAT_TABLE_MANUAL is 0, so there is no alternative provision. The
  handoff therefore changes what the composer approved, invisibly to
  them. It is not only a tuning shift: the static distortion-4 page
  is all zeros, so a module using distortion 4 plays AUDF 0 for
  every note once assembled, while RMT fills that page. The
  counter-argument that the page can hold only one configuration
  stands, but argues for making the default case exact rather than
  leaving every case wrong. Strict order, T6 FIRST: dumping today's
  generated buffer into rmtdata.a65 would write 128 zero bytes over
  the hand-made sawtooth tables in the source of truth - the T6 bug
  committed to the asm. Steps: (1) T6; (2) write the generated PAL
  defaults into rmtdata.a65, filling the distortion-4 page and
  aligning the 65 tuning entries, the sawtooth bytes unchanged by
  construction; (3) reassemble rmt_driver_v6.obx with the vendored
  pinned MADS, so build/check_drivers.sh proves asm and obx still
  agree; (4) a test asserting the obx's $B000 page equals the
  generated PAL defaults byte for byte. Consequences: T2's
  regression tests use the static-versus-generated discrepancy as
  their tell ($D7 against $D6 at $B040, in
  AtariTrackerDriverTests.cpp and RmtSessionTest.java), and that
  discrepancy is what this removes - they must be re-anchored on
  changing a tuning setting and asserting the page follows.
  rmtdata.a65 gains a comment naming exactly what the page was
  generated from (base tuning, basenote, temperament, region), with
  PAL defaults as the choice, since PAL is RMT's default and the
  Simple RMT Player has its own region switch. Plan 33's Simple RMT
  Player comparison and the Captain Future fixture README then lose
  their "known +-1" caveat, and doc/rmt_tracker_drivers.md's
  static-versus-generated rule needs rewording.

## Opinion

At proposal time, T1 came first, because the fix differed
fundamentally: if (a), nothing in
1.36 is wrong - the deliverable becomes recovering each tune's
settings from its anchor XEX and documenting them (T5), and T2 is
still worth doing as a regression fence. If (b), T2 is the fix
itself and T4 is its proof. The Captain Future anchor makes the
whole plan testable end to end, which is the luxury plan 36 was run
to obtain: acceptance is a byte count, not a listening session.

On T7, the obx decision T2 left open: leaving the page untouched was
recommended first, on the grounds that it never reaches RMT's own
playback and can only ever hold one configuration. That
recommendation is withdrawn. It weighed RMT's playback, which is
exactly the case where the static page is already irrelevant, and the
case that decides this is the composer-to-coder handoff, where the
static page is the only table source there is. The all-zero
distortion-4 page makes it a defect rather than a matter of taste.
The general answer - carrying the composer's tuning with the module
instead of relying on one shipped default page - is listed under
separate findings below.

## Separate findings (not this plan's scope)

These surfaced while reviewing the table page and are deliberately
not folded in here:

- Tuning settings have no effect for any driver other than Patch 16.
  Only rmt_driver_v6.obx has a $B000-$B5FF segment; v1-v5 and v7
  keep their RMT 1.28-era tables inside the player body
  ($31C0-$32FF, from `PLAYER equ $3400` in asm/Legacy/rmt.a65), and
  neither program ever writes there. UNPATCHED_WITH_TUNING (v2) is a
  byte-identical copy of v1 - the tuning-capable legacy driver was
  planned and never built. Worth documenting rather than fixing.
- CInstruments::GetFrequency and Instruments.getFrequency still read
  RMT_FRQTABLES + 64/128/192, which is the 1.28 single-page layout
  (base $31C0: +64 bass1 for distortion $0C, +128 bass2 for $0E,
  +192 pure for the rest, +0 unused because basslo is not
  single-byte). The constant was repointed to $B000 when Patch 16
  moved the tables; the offsets were not, so all three now land
  inside the bell page and the function returns bell values for
  every distortion. Identical in both programs, so no cross-program
  divergence; it reaches users through `export asm notes=freq`
  (ASMFileExporterCore.cpp:70 and :204). Its own defect, and its own
  plan, because the right table now depends on the channel's clock
  and 16-bit flags as well as the distortion - which is why the code
  carries the comment "Tables are not set to a constant location!".
- Let the composer's tuning travel with the module: an option on the
  rmtplayer-asm export that emits the current tuning tables as a dta
  block the coder assembles over rmtdata.a65's page. That fixes the
  handoff for every configuration rather than only the default one,
  and fits the 1.37 changelog item about an intermediate .ini step
  preserving each module's Tuning.ini settings.

## Out of scope

- Per-song tuning stored in the file format (RMX TUN) - future
  format plan.
- The classic v1-family drivers (F1/F3 tunes): their tables are
  baked into the driver binaries and already anchor byte-exactly;
  nothing changes for them.
