# Plan 55: a permanent xasm-compatibility guard in CI

Status: DONE 2026-10-25.

## Goal

Plan 54 made the xasm compatibility of the player sources true;
nothing made it stay true. The plan-51 editorial passes keep touching
asm/Unpatched and asm/Patch-16, and xasm swallows some MADS-only
constructs silently - a space before a `dta` comma assembles WITHOUT an
error message and emits one byte instead of two (exactly that slipped
into asm/Unpatched/rmt.a65 and was only caught by a manual xasm run).
The property matters beyond keeping Raster's original toolchain
working: xasm compatibility is required for including the player
source in [ASAP](https://github.com/pfusik/asap/), whose 6502 players
are built with xasm (the plan-31 ASAP work targets exactly that
repository).
Make the property a CI invariant, the way the repository already
guards byte-identity (check_drivers.sh) and the feature-flag branches
(check_feat_matrix.sh): a vendored, pinned assembler plus a
byte-comparison script on every push.

## What was built

- `lib/xasm/`: vendored xasm 3.2.1 (GPL, upstream pfusik/xasm, release
  `xasm-3.2.1`), `windows_x86_64/xasm.exe` from the release zip and
  `linux_x86_64/xasm` extracted from the release .deb, archive SHA-256s
  recorded in `lib/xasm/README.md` alongside the pin rationale and the
  update procedure (mirroring `lib/mads`). The Linux binary's
  executable bit is kept in the git index. Verified before vendoring:
  3.2.1 produces the same bytes as the 3.0.2 the plan-54 work used.
- `build/check_xasm_compatibility.sh`: assembles with the vendored
  xasm and byte-compares -
  - asm/Patch-16/rmtplayr.a65 (with the plan-54 command-line selectors,
    portable `-d LABEL=VALUE` spelling) against the shipped
    rmt_driver_v6.obx,
  - asm/Unpatched/rmt_ata.a65 against the shipped rmt_driver_v1.obx,
  - asm/Unpatched's rmt_sap4/rmt_sap8/rmt_msx, which have no shipped
    binaries, against the vendored MADS's assembly of the same sources
    (cross-assembler equality, no new committed blobs).
  The Simple RMT Player targets (rmtplayer.a65, XEX/SAP) are
  deliberately out of scope - they stay MADS-only (plan 54).
- Third step in `.github/workflows/check-drivers.yml`.
- Pointers in asm/Patch-16/README.md and asm/Unpatched/README.md.

## Proof

The check runs green locally (all five comparisons byte-identical,
xasm 3.2.1). Sensor proven: replanting the silent `dta
frqtabpure-frqtab ,$00` space in rmt.a65 fails exactly the three
rmt.a65-based comparisons with exit 1; restoring it turns the check
green again.
