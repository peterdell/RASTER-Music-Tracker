# lib/mads

Vendored binaries of MADS (Mad-Assembler) by Tomasz Biela, the 6502
assembler that builds the RMT tracker drivers from their sources in
`asm/`. They exist for one purpose: `build/check_drivers.sh` (run by CI
via `.github/workflows/check-drivers.yml`, and runnable locally in Git
Bash) proves on every push that the shipped driver binaries in
`rmt/resources/drivers/` are **byte for byte** what their checked-in
sources assemble to - verifiable builds instead of trusted blobs.

MADS alone suffices for both driver generations: it assembles the
XASM-era Legacy source (`asm/Legacy/rmt_ata.a65` -> `rmt_driver_v1.obx`)
identically to XASM, as well as the Patch16 source
(`asm/Patch-16/rmtplayr.a65` -> `rmt_driver_v6.obx`). Decided in
`plans/32_RITMO_FORK_ANALYSIS_PLAN.md` (section 7), where the RITMO
fork's ca65 conversion was considered and not adopted.

## Where the binaries come from

- Upstream: [tebe6502/Mad-Assembler](https://github.com/tebe6502/Mad-Assembler)
  (Free Pascal sources and prebuilt binaries in its `bin/` folder).
- Both taken from upstream commit
  `7ced682fc5a5b8470ba67d182398ce02fdf303a7`. One commit does NOT mean
  one version: upstream rebuilds the `bin/` platforms at different
  times, so the version is a property of each binary (the first line
  `mads` prints, also echoed by the check script).
- `windows_x86_64/mads.exe` - **mads 2.1.9 (2026/10/05)** - the local
  check on Windows (Git Bash).
- `linux_x86_64/mads` - **mads 2.1.7 (2025/02/02)** - the check on CI
  (ubuntu runner). Statically linked; its executable bit is kept in the
  git index (`git update-index --chmod=+x`).
- Both versions are verified: each reproduces both drivers byte for
  byte (2.1.9 locally, 2.1.7 locally and on CI).
- Upstream also ships `bin/macos_aarch64` and `bin/macos_x86_64`; they
  are not vendored. If a macOS check is ever wanted, vendor them the
  same way and extend the `uname` switch in `build/check_drivers.sh`.

## Why pinned binaries instead of downloading in CI

The driver check is a **byte-identity** check, so it is only as stable
as the assembler's code generation: a new MADS release could change the
output and break the check without anything in this repository having
changed. Pinned binaries make the proof reproducible forever and make an
assembler upgrade a deliberate act. They also keep CI hermetic - no
network fetch, no dependence on upstream's repository layout or
availability.

## How to update them

1. Pick the upstream commit to update to.
2. Download both binaries **from that same commit** (and note the
   version each one reports when run - they can differ, see above):
   `https://raw.githubusercontent.com/tebe6502/Mad-Assembler/<commit>/bin/windows_x86_64/mads.exe`
   `https://raw.githubusercontent.com/tebe6502/Mad-Assembler/<commit>/bin/linux_x86_64/mads`
3. Run `build/check_drivers.sh` locally: both drivers must still come
   out byte-identical. If the new MADS changes code generation, the
   check fails and the update must not land (or the question of
   regenerating the shipped drivers must be settled first - which would
   change `rmt/resources/drivers/`, a release-relevant decision, not an
   update side effect).
4. Update the versions and the commit in this README.
5. Re-add the executable bit on the Linux binary if git lost it:
   `git update-index --chmod=+x lib/mads/linux_x86_64/mads`.
6. Push; the CI run re-proves the same with the Linux binary.
