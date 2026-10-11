# Plan 37: Tuning compatibility with RMT 1.34 - existing tunes must sound correct in 1.36

Status: IN PROGRESS 2026-10-11. T1 is DONE - the cause is neither of
the two proposed candidates but a third: a 1.36 initialization-order
regression (see the T1 result below).

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
  Atari.init(), called from every RmtSession lifecycle point. The
  classic v1-family drivers keep their baked-in tables (they do not
  read $B000), which is why F1 anchored Delta byte-exactly - old
  tunes are NOT affected by this plan.
- Defaults: TuningSettings.initialize() = VinsCool's calibrated
  values (PAL 440.83751645933 Hz / NTSC 444.895778867913 Hz,
  basenote 3, temperament 0), identical in rmt/tuning.ini.

So the F2 dump already played GENERATED tables at default settings -
and still differs from the party stream by +-1. Sameness of system
does not give sameness of output. Exactly two candidate causes
remain:

- (a) The composer's tuning settings differed from the defaults
  (tuning.ini is global and invisible in the .rmt).
- (b) Generation drift: our fork imported VinsCool's work in two
  steps (303b03d "1.31 to 1.34", then c4563c0 "latest branch") - our
  generator may be NEWER than the 1.34.00 release build composers
  actually used, with changed rounding or table math. The mixed +-1
  directions in the measured remap look more like a rounding/
  algorithm difference than like a shifted base frequency (which
  would push all values the same way).

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
  rmt.jar. Decide
  separately whether the obx files' static $B000 segment should
  also be rebuilt to the generated defaults (it keeps standalone
  XEX exports and the Simple RMT Player consistent), or stay
  untouched as shipped binaries - check-drivers.yml pins their
  bytes. Until that decision and the fixture regeneration land,
  the committed Captain_Future_Theme.sapr still holds the OLD
  static-table dump.
- **T3. Default compatibility. DONE 2026-10-11 at source level
  (see T1):** 1.34.00's defaults equal ours for PAL and NTSC. The
  runtime proof comes free with T4.
- **T4. Verify against the composer's artifact.** Re-run the plan 36
  F2 comparison. Acceptance: the +-1 remap residue is ZERO - the
  streams are byte-identical modulo the documented LZSS
  normalization and the stale-state intro, nothing else.
  Known knock-on effects to handle in the same step: DONE
  2026-10-11 - the committed Captain_Future_Theme.sapr reference is
  regenerated with the fixed export (header and 7524-frame length
  unchanged, 3727 AUDF bytes moved by exactly one step, body
  byte-identical to the dump verified against the party stream);
  plan 33's Simple RMT Player comparison then needs the player
  bundle's tables aligned too, since the standalone player XEX
  carries the static obx tables. Delta and the other v1-family
  tunes are unaffected (no $B000 read).
- **T5. Record tuning provenance per tune.** Add a "Tuning:" line to
  the fixture metadata sheets (test-resources/songs READMEs), stating
  the settings the anchoring confirmed (expected: "RMT 1.34
  defaults" once T4 passes). This is the manual stand-in until a
  format carries tuning (the RMX TUN segment - out of scope, plan
  36).

## Opinion

T1 first, because the fix differs fundamentally: if (a), nothing in
1.36 is wrong - the deliverable becomes recovering each tune's
settings from its anchor XEX and documenting them (T5), and T2 is
still worth doing as a regression fence. If (b), T2 is the fix
itself and T4 is its proof. The Captain Future anchor makes the
whole plan testable end to end, which is the luxury plan 36 was run
to obtain: acceptance is a byte count, not a listening session.

## Out of scope

- Per-song tuning stored in the file format (RMX TUN) - future
  format plan.
- The classic v1-family drivers (F1/F3 tunes): their tables are
  baked into the driver binaries and already anchor byte-exactly;
  nothing changes for them.
