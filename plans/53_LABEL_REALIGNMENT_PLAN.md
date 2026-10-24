# Plan 53: realign Patch-16's labels to Raster's original names

Status: DONE 2026-10-24. 131 occurrences realigned across rmtplayr.a65
(107), rmt_feat.a65 (1), rmtplayer.a65 (14), rmtvis.a65 (7) and
backup_code.a65 (2), with 29 `;V:` markers at the defining locations
(ri0 and si1 are each defined twice, under their IFT variants). Proofs
all green: the three Patch-16 targets byte-identical (tracker.obx ==
rmt_driver_v6.obx), check_drivers.sh with the regenerated
tracker_obx.h (rename-only diff, every address unchanged), the feature
matrix manifest differing in exactly the three FEAT_VOLUME_ONLY ->
FEAT_COMMAND7VOLUMEONLY name cells with identical hashes, all 721 Java
tests, and the C++ Debug build. The Java constant is
PATCH16_TABBEGANDDISTOR now, mirroring LEGACY_TABBEGANDDISTOR.

## Goal

Plan 51's merge showed that VinsCool renamed a set of labels while
growing Patch-16 out of the unpatched player (the map is in
`asm/Patch-16/README.md`). That hurts congruence: the same variable
reads `v_speed` in `asm/Unpatched/rmt.a65` and `smc_speed` in
`asm/Patch-16/rmtplayr.a65`, so side-by-side reading and every future
comment merge must translate. Rename Vin's labels back to Raster's
original names, keeping Vin's name as a comment at the defining
location only (`;V: <name>`, mirroring the `;R:` provenance
convention).

## Scope

The verified rename pairs from `asm/Patch-16/README.md`: the zero-page
pointers (`ptr*` -> `p_*`), `trackn_TblLo/Hi` -> `trackn_db/hb`, the
self-modified operands (`smc_*` -> `v_*`), `TABLES_MSB` ->
`tabbeganddistor`, the GetSongLine/GetTrackLine locals (`gsl_*` ->
`nn*`, `gtl_*` -> `oo*`), `ri_clear_loop`/`ri_copy_loop`/`silence_loop`
-> `ri0`/`ri1`/`si1`, `pp1_b` -> `pp1b`, and `FEAT_VOLUME_ONLY` ->
`FEAT_COMMAND7VOLUMEONLY`. Vin's new labels that have no counterpart in
Raster's source (`dodex`, `v_skctl`, `do_*`, ...) keep their names -
there is nothing to realign them to.

Verified before execution: none of Raster's names occur in Patch-16
code (only inside merged `;R:` comments), so no rename can collide.
Occurrences beyond `rmtplayr.a65`/`rmt_feat.a65`: `rmtplayer.a65` and
`rmtvis.a65` use `ptrSongLines`/`smc_speed`/`smc_abeat`/
`smc_instrspeed`/`smc_silence_instrspeed`; `backup_code.a65` mentions
`TABLES_MSB`.

## Ripple (surveyed)

- `src/cpp/tracker_obx.h` is MADS-generated and pinned by
  `build/check_drivers.sh`: regenerate it (defines like
  `RMTPLAYR_SMC_SPEED` become `RMTPLAYR_V_SPEED`).
- C++ code references exactly one renamed define:
  `RMTPLAYR_TABLES_MSB` in `FrequencyTables.cpp` (2) and
  `InstrumentsTests.cpp` (1).
- Java: `FrequencyTables.RMT_TABLES_MSB` plus `GeneratedHeaderTest`'s
  define-name assertion and comments naming `TABLES_MSB`. The v1 twin
  is already `LEGACY_TABBEGANDDISTOR`, so the realignment unifies the
  naming.
- `build/feat_matrix.sha256`: the `FEAT_VOLUME_ONLY` rows change their
  flag-name column (hashes must stay identical).
- `asm/Patch-16/README.md`: the map stays as the record of Vin's names,
  reworded to describe the realignment and the `;V:` convention.

## Proof

Label renames emit no bytes: all three Patch-16 targets byte-identical
(tracker.obx == rmt_driver_v6.obx), `check_drivers.sh` green with the
regenerated header, the feature matrix green with only the flag-name
column changed, `mvn -o test` green, C++ builds.
