# The unpatched RMT player, from Raster's commented sources

The classic ("unpatched", TrackerDriverVersion.UNPATCHED) RMT player,
copied byte-identically from Raster's internal development tree in
`asm/Legacy` (plans/51, U1). `rmt.a65` is the player core, revision
1.20090108 with his full commentary - the ~800 working comments that
`rmt_bez_poznamek.php` stripped from everything that ever shipped (see
`asm/Legacy/README.md`). The next plan steps translate the Czech
comments to English here (U2) and merge them into `asm/Patch-16` (U3);
`asm/Legacy` itself stays byte-pristine as the historical artifact.

Two proofs anchor the copy (2026-10-22):

- Stripping `rmt.a65` with the php script's exact algorithm (right-trim
  every line, keep lines containing `;*`, truncate at plain `;`, drop
  emptied lines) reproduces the RMT 1.28 distribution's `rmtplayr.a65`
  (`legacy/rmt_128/asm_src`) byte for byte - this really is the
  revision the distribution was stripped from.
- Assembling `rmt_ata.a65` reproduces
  `rmt/resources/drivers/rmt_driver_v1.obx` byte for byte (2488 bytes):
  these files are the PROVEN SOURCE of the unpatched driver binary the
  tracker ships.

The targets, as in Raster's `rmt_ata_sap4_8_msx.bat`, each assembled
with xasm (`C:\jac\system\Atari800\Tools\ASM\XASM\xasm.exe <target>.a65`
in this folder; the .obx outputs are not committed):

| File | Target |
| --- | --- |
| `rmt_ata.a65` | The tracker driver: `icl "rmt.a65"` at `$3400` plus the `$3D00` entry points; assembles to `rmt_driver_v1.obx` exactly |
| `rmt_sap4.a65` | The SAP player, 4 tracks mono (`STEREOMODE equ 0`), SAP init routine appended |
| `rmt_sap8.a65` | The SAP player, 8 tracks stereo |
| `rmt_msx.a65` | The standalone player-with-screen shell; calls a separately assembled player at `$3400` (does not include `rmt.a65`) |
| `rmt_feat.a65` | The feature flags, all-universal configuration (byte-identical to the distribution's copy) |

sap4/sap8/msx have no committed binaries to compare against (ASAP's
`rmt4.obx`/`rmt8.obx` are built from the older 1.20051226 revision);
for them, assembling cleanly is the check.
