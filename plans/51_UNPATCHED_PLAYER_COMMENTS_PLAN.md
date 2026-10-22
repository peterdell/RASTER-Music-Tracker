# Plan 51: Raster's player commentary, translated and merged into Patch-16

Status: open.

asm/Legacy/rmt.a65 is the sole surviving source of Raster's own
commentary on the RMT player (plan 50's archaeology, recorded in
asm/Legacy/README.md): ~800 private working comments, mostly Czech,
that rmt_bez_poznamek.php stripped before anything shipped. Everything
downstream - the 1.28 distribution, Patch-16, ASAP's rmt.asx - grew out
of the stripped file, so Raster's explanations and VinsCool's 2021-2022
annotations have never met. This plan brings them together: a clean
English-commented copy of the unpatched player, then Raster's comments
merged into the living Patch-16 source.

Principles:

- asm/Legacy stays byte-pristine - it is the historical artifact; every
  derived work lands elsewhere.
- Comments only, never code: each step carries a mechanical proof that
  the assembled bytes did not change.
- Raster's `;*` (public) vs `;` (private) distinction is semantic
  (rmt_bez_poznamek.php's keep-marker) and is preserved.

## U1 - asm/Unpatched: the copy

Copy every .a65 directly in asm/Legacy, byte-identical: `rmt.a65` (the
commented player core, 1.20090108) and all four of Raster's target
harnesses that include it - `rmt_ata.a65` (the tracker-driver build
with the $3D00 entry points), `rmt_sap4.a65`/`rmt_sap8.a65` (the SAP
builds, mono/stereo) and `rmt_msx.a65` (the standalone
player-with-screen) - plus `rmt_feat.a65` (verified byte-identical to
the distribution's copy already). Only the RMF experiment stays behind.
With the full set, every original object file is buildable from
readable sources, the way Raster's own rmt_ata_sap4_8_msx.bat built
them. The folder name matches TrackerDriverVersion.UNPATCHED - this
player IS the unpatched driver. Add a README.md naming the source
revision, the targets and the build, pointing back to asm/Legacy and to
this plan.

Verifications while at it:

- Apply rmt_bez_poznamek.php's algorithm (keep lines containing `;*`,
  truncate at plain `;`, drop emptied lines) to the copied rmt.a65 and
  diff against legacy/rmt_128/asm_src/rmtplayr.a65 - proving the
  revision claim ("the same 1.20090108 the distribution was stripped
  from") instead of asserting it.
- Assemble all four targets with xasm
  (C:/jac/system/Atari800/Tools/ASM/XASM/xasm.exe, the Legacy bat's own
  toolchain). Compare rmt_ata.obx against
  rmt/resources/drivers/rmt_driver_v1.obx (UNPATCHED = driver number
  1). Identical: the repository gains the proven source of the
  unpatched driver binary, which it never had - record that in both
  READMEs. Different: record the delta and what explains it; the copy
  is still only a comment carrier. sap4/sap8/msx have no committed
  binary to compare against (ASAP's rmt4/rmt8.obx are the older 2005
  revision) - for them, assembling cleanly is the check, and the .obx
  outputs are not committed.

## U2 - the translation

Translate the plain-`;` Czech comments of all copied asm/Unpatched
sources to English, in place (the bulk is rmt.a65; the four harnesses
carry a few dozen more). `;*` comments are already English and
stay untouched. Rules:

- Only text after `;` changes; code columns stay byte-identical.
- The `;*` vs `;` prefix of every line is preserved.
- A comment too terse or idiomatic to translate with confidence is
  marked (e.g. `;?`) and listed for the user's review rather than
  guessed silently.

Proof, both mechanical: (a) strip-diff - every file with all comments
removed is byte-identical before and after; (b) xasm-assemble all four
targets before and after, byte-identical .obx each.

## U3 - the merge into Patch-16

Merge the translated Raster comments into asm/Patch-16/rmtplayr.a65,
next to VinsCool's existing annotations. The hard step - Patch-16 is
2306 lines against rmt.a65's 1356, restructured and extended:

- Anchor by normalized code-line matching (the plan-50 diff technique:
  comments and whitespace stripped); only unambiguous anchors get a
  comment merged automatically.
- Provenance stays visible: Raster's merged comments are marked as his
  (suffix/prefix convention to be fixed in execution, e.g. `;R:`), so
  the two voices never blur.
- Anchors where Vin CHANGED the adjacent code are the trap - a correct
  comment on the old code misleads on the new. These are collected and
  reviewed one by one, not merged blindly; where the code diverged too
  far, the comment stays out and the gap is listed.
- Proof: the assembled Patch-16 outputs (the driver obx / rmtplayer
  build in asm/Patch-16) are byte-identical before and after the merge;
  the existing driver byte-checks stay green.

One commit per step. U2 and U3 both produce a review list for the user
(uncertain translations; diverged anchors) - the step is done when the
list is resolved, not when the script ran.
