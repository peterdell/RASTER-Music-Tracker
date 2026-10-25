# Plan 54: xasm compatibility for the Patch-16 tracker driver source

Status: DONE 2026-10-25.

## Goal

Assemble `asm/Patch-16/rmtplayr.a65` (the tracker driver) with xasm
3.0.2, byte-identical to `rmt/resources/drivers/rmt_driver_v6.obx`.
Scope is the tracker target only, per the user - the Simple RMT Player
targets (`rmtplayer.a65`, XEX/SAP) stay MADS-only.

## Why the `.ifndef` blocks had to move

Groundwork first (committed separately): the pass that fixed the seven
unprefixed comment lines and rewrote the three bare accumulator shifts
as `lsr @` removed every xasm syntax error from `rmtplayr.a65` itself.
After that, the only remaining blocker was `rmt_feat.a65`'s two
`.ifndef` default blocks for the target selectors
`FEAT_IS_TRACKER`/`FEAT_IS_SIMPLEP` - MADS-only syntax expressing "an
overridable default". xasm has no defined-test, and its `/d:` switch
collides with an in-file `equ` (duplicate label), so no in-file
rewrite exists.

Design chosen: no defaults at all. The selectors are defined on the
command line by every build - MADS `-d:`, xasm `/d:` - and
`rmt_feat.a65` keeps only the `ERT` consistency guards. This leaves
`rmtplayr.a65`'s content untouched (the plan-51 WinMerge pairing with
`asm/Unpatched/rmt.a65` is unaffected), keeps the `RMTPLAYR_*` prefix
of the generated header (MADS derives it from the main source file
name), and keeps `FEAT_IS_TRACKER`/`FEAT_IS_SIMPLEP` out of
`tracker_obx.h` (`-d:` definitions are not emitted by `-hc:`, the plan
42 observation). The alternative - Raster-style wrapper files, proven
byte-identical in a scratch probe too - was rejected because a wrapper
as the main file would rename all 350 header defines to its own
prefix.

A bare `mads rmtplayr.a65` without `-d:` now fails with "Undeclared
label FEAT_IS_TRACKER" instead of silently building the tracker
default; rmt_feat.a65's header comment names the required definitions.

## Changes

- `asm/Patch-16/rmt_feat.a65`: the two `.ifndef` blocks replaced by a
  comment naming the command-line definitions for both targets.
- `asm/Patch-16/build.cmd`: the tracker line passes
  `-d:FEAT_IS_TRACKER=1 -d:FEAT_IS_SIMPLEP=0`.
- `build/check_drivers.sh`: `check` takes an optional fifth argument
  (extra MADS options, word-split); the Patch-16 call passes the
  selectors.
- `build/check_feat_matrix.sh`: the tracker `record` passes the
  selectors; comment updated.
- `asm/Patch-16/rmtvis.a65`: stale comment ("in rmt_feat.a65 ... equ")
  updated to the command-line wording.
- `asm/Patch-16/README.md`: documents the xasm invocation
  (`xasm rmtplayr.a65 /d:FEAT_IS_TRACKER=1 /d:FEAT_IS_SIMPLEP=0
  /o:tracker.obx`).
- `src/cpp/tracker_obx.h`: regenerated. The diff is two new defines,
  `RMTPLAYR_NN2A`/`RMTPLAYR_OO1B` - not from this plan but from the
  plan-51 editorial pass that re-added Raster's local labels
  `nn2a`/`oo1b`; the committed header had gone stale against it
  (check_drivers.sh was red on dev until this regeneration).

## Proof

All from the repository sources after the change: MADS tracker.obx ==
rmt_driver_v6.obx, rmtplayer.xex/.sap byte-identical to the session
baselines; xasm 3.0.2 tracker build byte-identical to
rmt_driver_v6.obx; `build/check_drivers.sh` green (352 defines);
`build/check_feat_matrix.sh` green (288 lines, the 10 known
ASSEMBLY-ERROR rows unchanged); all Java tests green.
