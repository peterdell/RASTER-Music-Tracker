# Plan 46: The WAV export must not play through the loaded driver's memory

Status: DONE 2026-10-15, W1-W6. Found during plan 45's verification and
recorded there; fixed the same day. Verified: the new C++ regression test
loads the v1 driver and asserts $3000-$3FFF byte-identical across a real
`ExportWAV` run (452 C++ tests pass); the mono and stereo reference WAVs
with Patch 16 are SHA-256-identical before and after the fix; and the
system-level reproduction - `TRACKERDRIVERVERSION = 1`, `export wav`,
`export asm notes=freq` - exits 0 where it failed three of three runs
before.

## The bug

`CWaveFileExporter::ExportWAV` replays the recorded SAP-R stream by
writing each frame's register bytes into the **Patch 16 driver's
variables** (`RMTPLAYR_TRACKN_AUDF`/`_AUDC`, `V_AUDCTL`, `V_AUDCTL2` -
`WaveFileExporter.cpp`) and then letting `RenderPartV2` run the loaded
driver's own `SetPokey` 6502 routine to carry them to $D200, followed by
`CopyAtariMemoryToPokey`. With the Patch 16 driver loaded that works.
With any v1-family driver loaded it is wrong twice over:

- **The writes corrupt the loaded driver.** `RMTPLAYR_TRACKN_AUDC` is
  $3180-$3187, which lies across the v1 family's dispatch table at
  $3182 (`tabbeganddistor`); `TRACKN_AUDF` ($3178-$317F), `V_AUDCTL`
  ($3C69) and `V_AUDCTL2` ($3CCB) also land inside the v1 player. After
  one WAV export, the next `notes=freq` ASM export or MIDI input finds
  a clobbered dispatch table, and plan 40's plausibility check
  correctly refuses it.
- **The WAV content itself is wrong**, more quietly: the v1 driver's
  `SetPokey` reads *its own* shadow variables, not Patch 16's, so the
  rendered audio comes from whatever the last live playback left there,
  not from the recorded stream at all.

How it surfaced: `delta.rmtscript` runs `export wav` before an
`export asm ... notes=freq`, and an aborted earlier run had left
`TRACKERDRIVERVERSION = 1` in the test `rmt.ini` (the script restores
`patch16` only on its last line). Every later run then started on the
v1 driver, the WAV clobbered $3182, and the frequency export refused -
reproduced at HEAD, three of three runs, so it predates plan 45.

## The fix

The Java port never had the bug and already documents the right route:
its `WaveFileExporter` pokes the registers into the POKEY pair directly
and never touches the song's Atari memory. C++ converges:

- **W1.** `ExportWAV` writes each frame's bytes to the POKEY shadows
  themselves - $D200-$D208, and $D210-$D218 for the second POKEY (the
  stream stores the second POKEY's nine bytes first) - instead of the
  Patch 16 variables. The emulated driver memory is never written.
- **W2.** `RenderPartV2` drops its `SetPokey` call - `ExportWAV` is its
  only caller, and the registers are already at $D200 - keeping
  `CopyAtariMemoryToPokey` (which also applies the channel mutes) and
  the render. The WAV no longer needs the loaded driver at all; the
  stream it replays was recorded through the driver beforehand.
- **W3.** A regression test in the C++ suite: load the v1 driver into
  the test Atari, snapshot the player region, run the real `ExportWAV`,
  and assert the region is byte-identical afterwards - the test that
  would have failed before W1.
- **W4.** Prove the normal case unchanged: WAV exports of the mono and
  stereo reference songs with the Patch 16 driver, byte-compared
  before and after the fix - expected identical, since Patch 16's
  `SetPokey` copied the variables to $D200 verbatim.
- **W5.** The system-level reproduction passes: with
  `TRACKERDRIVERVERSION = 1`, `export wav` followed by
  `export asm notes=freq` succeeds.
- **W6.** The change log records the fix; the Java `WaveFileExporter`
  javadoc paragraph describing C++'s old route is updated to say the
  two programs use the same direct route now; plan 45's finding points
  here.

## Out of scope

- The test-state fragility that exposed it: an aborted script run
  leaves whatever `set driver`/`set ntsc` had configured in the
  program folder's `rmt.ini`. Worth its own thought (a script could
  restore the settings it changed, or the comparison could run on a
  copied `rmt.ini`), but a separate concern.
- Tick-accurate WAV rendering questions (plan 36's FASTPLAY note).
