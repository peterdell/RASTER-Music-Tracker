# Plan 52: assemble the players with every feature flag toggled once

Status: DONE 2026-10-24. `build/check_feat_matrix.sh` (check/update
modes) and the manifest `build/feat_matrix.sha256` exist and run in CI
as a second step of `.github/workflows/check-drivers.yml`. 288 manifest
lines: all 37 of Raster's Unpatched toggles assemble in all four stereo
modes; 6 Patch-16 toggles are genuinely broken (10 ASSEMBLY-ERROR rows,
labels defined inside the flag's IFT referenced outside it:
FEAT_TWO_TONE, FEAT_FULL_16BIT, FEAT_BASS16, FEAT_FULL_SAWTOOTH,
FEAT_AUDCTLMANUALSET for the tracker, FEAT_INSTRSPEED for the XEX
player) and documented as such. Execution deltas against the plan
below: Unpatched's SAP harnesses guard their hardcoded init address
with `ert start!=RMTPLAYEREND`, which correctly fires on any
size-changing flip, and rmt_ata is stereo-only - so the matrix
assembles generated wrappers (`STEREOMODE equ N` + `icl "rmt.a65"`,
temp files only) for all four stereo modes per variant and the SAP
harnesses only at DEFAULT. Sensor proven: a `nop` inserted into the
dark FEAT_SFX branch changed exactly the five FEAT_SFX rows and failed
the check, while every pre-existing check stayed green.

## Problem

The byte-identity proofs that guard the ASM sources (plan 51's merge
work, `build/check_drivers.sh` on every push) assemble only the default
`rmt_feat.a65` configuration. Five flags default to 0 (`FEAT_SFX`,
`FEAT_GLOBALVOLUMEFADE`, `FEAT_NOSTARTINGSONGLINE`, `FEAT_INSTRSPEED`,
`FEAT_CONSTANTSPEED`), so the code under their `IFT` blocks is never
assembled at all, and the `ELS`/`==0` branches of the default-1 flags
are equally unassembled. A comment merge (or any edit) could break
those branches without any check noticing.

## Approach: one-flag-at-a-time build matrix with a hash manifest

Probed 2026-10-24: single-flag flips assemble cleanly in both folders
(Unpatched `FEAT_SFX=1` 2506 bytes, `FEAT_PORTAMENTO=0` 2356 bytes with
xasm; Patch-16 `FEAT_SFX=1` 4482 bytes with the vendored MADS).

- `build/check_feat_matrix.sh`, beside `check_drivers.sh`, using the
  vendored MADS for everything (cross-platform, CI-ready;
  `check_drivers.sh` already proves MADS reproduces the xasm-era source
  byte for byte).
- For each folder (`asm/Unpatched`, `asm/Patch-16`): parse
  `rmt_feat.a65` for the `FEAT_* equ 0|1` lines, and for each flag copy
  the folder's sources to a temp directory with just that flag
  inverted. No repository file changes - that is why it works without
  touching xasm compatibility. (`.ifndef` guards plus `-d:` overrides,
  the `build.cmd` pattern, were considered and rejected: MADS-only
  syntax.) Patch-16's `FEAT_IS_TRACKER`/`FEAT_IS_SIMPLEP` are excluded
  from flipping - they select the target (the `ERT` guards reject other
  combinations) and are exercised by the targets themselves.
- Per variant, assemble every target that includes `rmt_feat.a65`
  (Unpatched: `rmt_ata`, `rmt_sap4`, `rmt_sap8`; Patch-16:
  `rmtplayr.a65` plus the Simple RMT Player as XEX and SAP with the
  `-d:` selections from `build.cmd`), require exit 0 and record the
  SHA-256 of each output. `rmt_msx.a65` does not include the player and
  stays out.
- The hashes land in a checked-in manifest, `build/feat_matrix.sha256`,
  one line per folder/flag/target. The script's check mode (the
  default) regenerates the matrix into a temp directory and diffs
  against the manifest; `update` mode rewrites it. A variant that does
  not assemble is recorded as `ASSEMBLY-ERROR` - visible and diffable,
  so a toggle that is legitimately broken is documented rather than
  silently skipped, and a newly breaking one is a failure.
- The default configuration is included as variant `DEFAULT`, asserted
  equal to the shipped drivers (`rmt_driver_v1.obx` for Unpatched's
  `rmt_ata`, `rmt_driver_v6.obx` for Patch-16's tracker) - tying the
  matrix to the binaries the tracker ships.
- CI: a second step in `.github/workflows/check-drivers.yml` (the
  matrix takes seconds).

The "equal outcome" guarantee this buys: a comment-only edit must leave
every manifest line unchanged, which extends byte-identity over both
sides of every single-flag `IFT`. An intentional code change
regenerates the manifest and the diff shows exactly which
configurations are affected.

## Known limits

- One-at-a-time covers every branch controlled by a single flag, and
  `&&`/`||` combinations where the other operands hold their defaults.
  A branch that needs two or more simultaneously non-default values
  stays dark; named combinations can be added to the matrix if one
  turns up.
- The matrix proves assembly outcomes, not runtime behaviour of the
  non-default configurations.
