# Plan 37: Tuning compatibility with RMT 1.34 - existing tunes must sound correct in 1.36

Status: PROPOSED 2026-10-11.

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

- **T1. Diagnose the remap - settings or algorithm?** Obtain the RMT
  1.34.00 release source (VinsCool's repository, release tag) and
  compare its table generation against ours at identical settings,
  table by table (timbre x clock x AUDCTL). Independently, scan the
  basetuning/temperament space with OUR generator and test whether
  any setting reproduces the measured 35-pair remap from F2. The two
  probes together separate (a) from (b).
- **T2. Byte-compatibility at equal settings.** Whatever T1 finds,
  pin the contract: for identical settings, 1.36 generates tables
  byte-identical to the 1.34.00 release build. Capture the 1.34.00
  tables as golden-master test fixtures; fix any drift in BOTH
  programs (standing rule), with the characterization tests keeping
  C++ and Java locked together.
- **T3. Default compatibility.** Prove our defaults equal the
  1.34.00 release defaults for PAL and NTSC - a default-tuned 1.34
  tune must sound right in a fresh 1.36 install with no user action.
- **T4. Verify against the composer's artifact.** Re-run the plan 36
  F2 comparison. Acceptance: the +-1 remap residue is ZERO - the
  streams are byte-identical modulo the documented LZSS
  normalization and the stale-state intro, nothing else.
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
