# Raster's internal player sources

This folder is Radek Sterba's (Raster's) own development tree of the RMT
player routine (2002-2009; the header says "RMT Atari routine version
1.20090108") - not what coders received. The distribution shipped the
same routine as `rmtplayr.a65`, produced from this tree by stripping
the comments: `rmt_bez_poznamek.php` is Czech for "rmt without
comments", and the RMT 1.28 distribution with the resulting
`rmtplayr.a65` + `rmt_feat.a65` is preserved in `legacy/rmt_128/asm_src`
at the repository root. The two comment prefixes are the script's
input: a line containing `;*` is kept unchanged (the public comments -
the English warnings and the feature descriptions), a plain `;` comment
is stripped (Raster's private working notes, mostly Czech) - `rmt.a65`
has 40 `;*` and ~800 plain comments, the shipped `rmtplayr.a65` exactly
those 40. The continuation of the distributed player
lives in `asm/Patch-16`, still under the name `rmtplayr.a65`.

The player core here is `rmt.a65`, with Raster's Czech comments. The
other files are per-target harnesses that include it (`icl "rmt.a65"`):

| File | Target |
| --- | --- |
| `rmt_ata.a65` | The tracker-driver build RMT itself runs on its emulated Atari: the `$3D00` entry points, `STEREOMODE equ 1` |
| `rmt_sap4.a65` | The SAP build, 4 tracks mono (`STEREOMODE equ 0`), with the SAP init routine appended after the player |
| `rmt_sap8.a65` | The SAP build, 8 tracks stereo |
| `rmt_msx.a65` | The standalone player-with-screen build (video memory, VCOUNT timing) |
| `rmt_feat.a65` | The feature flags that strip unused player features at assembly time |
| `rmt_ata_sap4_8_msx.bat` | Assembles the four targets above |

`rmf/` is Raster's "FASTER MODIFICATION" experiment: the RMF module
format with `rmtfast.a65` as the player core, `rmf_play.a65` as its
harness, its own `rmt_feat*` variants, a `test.rmf` sample and
`statistika.txt` with his size/speed measurements against the regular
player. Raster started RMF but never completed it; the tracker's
remnants of the format were removed (doc/rmt_changes.md, and
`SongTypes.h` still reserves the obsolete type number 8).
