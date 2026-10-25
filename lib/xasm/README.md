# lib/xasm

Vendored binaries of xasm by Piotr Fusik, the 6502 cross-assembler that
Raster built RMT's original players with (asm/Legacy's
`rmt_ata_sap4_8_msx.bat` calls it). They exist for one purpose:
`build/check_xasm_compatibility.sh` (run by CI via
`.github/workflows/check-drivers.yml`, and runnable locally in Git
Bash) proves on every push that the xasm-compatible player sources stay
xasm-compatible **byte for byte** - asm/Unpatched entirely, and
asm/Patch-16's tracker driver per
`plans/54_XASM_TRACKER_COMPATIBILITY_PLAN.md`. Beyond keeping Raster's
original toolchain working, xasm compatibility is required for
including the player source in [ASAP](https://github.com/pfusik/asap/),
whose 6502 players are built with xasm.

The check compares bytes, not exit codes, because xasm treats a space
after a complete expression as the start of a comment: an edit like
`dta frqtabpure-frqtab ,$00` assembles WITHOUT an error message but
emits one byte instead of two. Exactly that slipped into
asm/Unpatched/rmt.a65 during the plan-51 editorial pass and was only
caught by a manual xasm run - this check makes the catch permanent.

MADS remains the pinned toolchain that proves the shipped drivers
(`lib/mads`, `build/check_drivers.sh`); xasm here guards a second,
independent property of the sources.

## Where the binaries come from

- Upstream: [pfusik/xasm](https://github.com/pfusik/xasm) (GPL).
- Both taken from release
  [`xasm-3.2.1`](https://github.com/pfusik/xasm/releases/tag/xasm-3.2.1)
  - **xasm 3.2.1** (the first line `xasm` prints, also echoed by the
  check script):
- `windows_x86_64/xasm.exe` - from `xasm-3.2.1-windows.zip`
  (SHA-256 `0a92cc0844222c9787e6387c8ddc2d25018654558c93fe2ab6c037d98b7b33b7`)
  - the local check on Windows (Git Bash).
- `linux_x86_64/xasm` - `usr/bin/xasm` extracted from
  `xasm_3.2.1-1_amd64.deb`
  (SHA-256 `39ca04aacd6e35eebd9109f46c6c7cdfce270df59cd3258a2835523d2b012ab4`)
  - the check on CI (ubuntu runner). Dynamically linked against glibc;
  its executable bit is kept in the git index
  (`git update-index --chmod=+x`).
- Verified: xasm 3.2.1 reproduces `rmt_driver_v6.obx` (from
  asm/Patch-16/rmtplayr.a65 with the plan-54 command-line selectors)
  and `rmt_driver_v1.obx` (from asm/Unpatched/rmt_ata.a65) byte for
  byte, and agrees with the vendored MADS on every byte of
  rmt_sap4/rmt_sap8/rmt_msx. xasm 3.0.2 (the version the plan-54
  compatibility work was done with) produces the same bytes.
- Upstream also ships a macOS .dmg; it is not vendored. If a macOS
  check is ever wanted, vendor it the same way and extend the `uname`
  switch in `build/check_xasm_compatibility.sh`.

## Why pinned binaries instead of downloading in CI

The same reasons as `lib/mads/README.md`: the check is a byte-identity
check, so a new xasm release could change the comparison without
anything in this repository having changed; pinned binaries make the
proof reproducible forever, make an assembler upgrade a deliberate act,
and keep CI hermetic.

## How to update them

1. Pick the upstream release to update to.
2. Download `xasm-<version>-windows.zip` (take `xasm.exe`) and
   `xasm_<version>-1_amd64.deb` (extract `usr/bin/xasm`; the deb's
   `data.tar.zst` needs a zstd-capable tar, e.g. Windows 11's bsdtar)
   from that release, and record their SHA-256 here.
3. Run `build/check_xasm_compatibility.sh` locally: every comparison
   must still be byte-identical. If the new xasm changes code
   generation, the check fails and the update must not land.
