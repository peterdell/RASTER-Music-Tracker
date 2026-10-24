# The Patch-16 player source and its label map

VinsCool's Patch16 player ("RMT Atari routine version Patch16-3.2"),
the source of the tracker driver: `build.cmd` assembles `rmtplayr.a65`
to `out/tracker.obx`, byte-identical to
`rmt/resources/drivers/rmt_driver_v6.obx`, plus the Simple RMT Player
as XEX and SAP (`build/check_drivers.sh` proves the driver bytes on
every push).

Patch-16 grew out of Raster's unpatched player (`asm/Unpatched/rmt.a65`
is the living, commented source of that one; plan 51 merges Raster's
commentary into this file). Vin renamed a number of labels along the
way, which made reading the two sources side by side difficult. The
tables below map them; detected mechanically (code lines aligned with
all identifiers blanked, substitutions voted on), each pair verified by
hand in both sources (2026-10-24).

Plan 53 realigned the labels the same day: `rmtplayr.a65` (and the
other Patch-16 files referencing these labels) use Raster's original
names again, and Vin's name is kept as a `;V: <name>` comment at the
defining location - the counterpart of the `;R:` convention that marks
Raster's carried-over comments. The renames emit no bytes: all three
targets, the generated `tracker_obx.h` addresses and the whole
feature-flag matrix are unchanged. Vin's labels that have no
counterpart in Raster's source (`dodex`, `v_skctl`, the `do_*`
routines, ...) keep their names.

## Realigned labels (left: the name in use; right: Vin's name, kept as `;V:`)

Variables and pointers:

| Name (both sources) | Vin's former name | Role |
| --- | --- | --- |
| `p_instrstable` | `ptrInstrumentTbl` | zero-page ptr to the instrument table ptrs |
| `p_trackslbstable` | `ptrTracksTblLo` | zero-page ptr to the track ptrs, low bytes |
| `p_trackshbstable` | `ptrTracksTblHi` | zero-page ptr to the track ptrs, high bytes |
| `p_song` | `ptrSongLines` | zero-page ptr to the song data |
| `trackn_db` | `trackn_TblLo` | per-track data address, low byte |
| `trackn_hb` | `trackn_TblHi` | per-track data address, high byte |
| `v_speed` | `smc_speed` | self-modified operand: speed |
| `v_bspeed` | `smc_bspeed` | self-modified operand: buffered speed |
| `v_abeat` | `smc_abeat` | self-modified operand: current beat (row) |
| `v_maxtracklen` | `smc_maxtracklen` | self-modified operand: maximum track length |
| `v_instrspeed` | `smc_instrspeed` | self-modified operand: instrument speed |
| `v_ainstrspeed` | `smc_silence_instrspeed` | self-modified operand: actual instrument speed countdown |
| `tabbeganddistor` | `TABLES_MSB` | per-distortion offsets into the frequency tables |

Code labels:

| Name (both sources) | Vin's former name | Role |
| --- | --- | --- |
| `ri0` | `ri_clear_loop` | rmt_init: clear the track variables (both IFT variants) |
| `ri1` | `ri_copy_loop` | rmt_init: copy the four module header pointers |
| `si1` | `silence_loop` | rmt_silence: zero the POKEY registers |
| `nn0` | `gsl_continueProcessing` | GetSongLine: re-entry after a goto (ldx #0 first) |
| `nn1` | `gsl_nextSongLine` | GetSongLine: per-track loop head |
| `nn1a2` | `gsl_initTrack` | GetSongLine: set trackn_pause / init instrument (empty-track entry) |
| `nn2` | `gsl_GotoOrEmpty` | GetSongLine: track number >= $fe |
| `nn3` | `gsl_thisIsAGoto` | GetSongLine: track number = $fe (goto) |
| `oo1` | `gtl_loopTracks` | GetTrackLine: per-track loop head |
| `oo1a` | `gtl_ProcessVolumeData` | GetTrackLine: set pause and volume |
| `oo1x` | `gtl_checkEndOfLoop` | GetTrackLine: end-of-loop check |
| `oo2` | `gtl_Is62or63` | GetTrackLine: note > 61 (pause / speed-go-end) |
| `pp1b` | `pp1_b` | instrument processing: take the loop start index |

FEAT flags (`rmt_feat.a65`):

| Name (both sources) | Vin's former name | Role |
| --- | --- | --- |
| `FEAT_COMMAND7VOLUMEONLY` | `FEAT_VOLUME_ONLY` | command 7 >= $80: volume-only mode |

## Dropped labels (no counterpart in Patch-16)

- `nn1a`, `nn1b`, `nn2a`, `oo0`, `oo0a`, `oo1b` - redundant local
  labels on code that survives unlabeled.
- `xtracks01`, `xtracks02`, `xtracks03sub1`, `xtracks05sub1` - Raster's
  markers on the TRACKS-dependent instructions; the instructions
  survive unlabeled.
- `GetSongLineTrackLineInitOfNewSetInstrumentsOnlyRmtp3` - alias of
  `GetSongLine`; callers use `GetSongLine` directly. (The third part of
  the chain gained a real label instead: `InitOfNewSetInstrumentsOnly`
  existed only in comments in Raster's source and is a real label in
  Patch-16.)
- `trackn_distor` - defined but never used in Raster's source (dead);
  removed.
- `FEAT_COMMAND7SETNOTE` - command 7 < $80 ("set note") was removed in
  Vin's command-7 rework; `FEAT_COMMAND7` and `FEAT_TWO_TONE` guard the
  replacement (two-tone filter control).
- `FEAT_BASS16G1L/G3L/G1R/G3R` and `FEAT_VOLUMEONLYG0L/G2L/G3L/G0R/G2R/G3R`
  - the per-generator sub-flags are gone; `FEAT_BASS16` and
  `FEAT_VOLUME_ONLY` alone control the features.

## Restructured, not renamed

- Instrument envelope fetch: Raster's `pp1` loop reads the three
  envelope bytes into `reg1`/`reg2`/`reg3`; Vin split a pre-pass out of
  it (`dodex`/`dodex_a`/`dexnext`) that stores them into new per-track
  arrays `trackn_volumeenvelope`/`trackn_command`/`trackn_effectparameter`
  instead. The main pass (`pp0`/`pp1`/`pp2`/`ppnext`) survives with its
  old names.
- The SetPokey / filter / 16-bit bass / AUDCTL assembly area (Raster's
  `qq*`/`qs*` labels) is fully reworked in Patch-16; some old names
  reappear there on different code, so no label-to-label mapping holds.
  Read the two implementations side by side instead.
