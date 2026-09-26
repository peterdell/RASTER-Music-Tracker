# test-resources/ui-reference

Reference screenshots of the real `Rmt.exe` (the C++ app), used by the
Java UI port's golden-image tests to check that the Java rendering matches
today's Windows UI pixel for pixel (see `plans/JAVA_UI_PORT_PLAN.md`,
decision 6 and batches B1/B2). Each scenario is one self-contained folder
holding the `.rmt` it shows (so the Java test can load the same module)
and its screenshots.

## Capture conditions (apply to every screenshot)

- **Windows display scaling: 150%** (the capturing machine's setting -
  fixed, not a choice). `Rmt.exe` is bitmap-scaled by Windows at that
  setting, so a screenshot at RMT's own 100% scaling is a 1.5x resample
  of the true pixels and only approximately comparable.
- **Prefer RMT scaling 200%** (Options > scaling). 200% RMT x 150%
  Windows = exactly 3 device pixels per RMT pixel, which the tests can
  reconstruct losslessly. The `-scale200` suffix marks these; no suffix
  means RMT at 100%.
- Full-desktop captures (2559x1440) are fine - the tests locate the RMT
  client area themselves. A window-only capture (Alt+PrintScreen) is
  equally fine.
- Unless the name says otherwise: cursor on song line 0 / track line 0,
  edit mode (not jam), not playing, default view options.

## Naming

`<scenario>/<view>[-<state>][-scale200].png`

- `<scenario>`: `song0-empty`, `song1-mono`, `song2-stereo`, `song3-goto`,
  `dialogs`.
- `<view>`: `tracks` (Edit Tracks screen) or `instruments` (Edit
  Instruments screen).
- `<state>` (optional): `playing`, `jam`, `goto`, `hover`.

## Present

| File | What it shows |
|---|---|
| `song0-empty/tracks.png` | No module loaded, tracks view, RMT 100% |
| `song0-empty/instruments.png` | No module loaded, instruments view, RMT 100% |
| `song1-mono/Why_Do_You_Dance_With_Me-132-$4000.rmt` | The mono (4-track) module the `song1-mono` screenshots show |
| `song1-mono/tracks.png` | `song1-mono`, tracks view, RMT 100% |
| `song1-mono/instruments.png` | `song1-mono`, instruments view, RMT 100% |

## Missing - proposed files, most valuable first

**Every file listed below already exists as a 1x1 placeholder PNG** - just
overwrite the placeholder with the real capture (same name). Tests skip
any reference image smaller than a real screen capture, so an unreplaced
placeholder is harmless. The two `.rmt` files (`song2-stereo/`,
`song3-goto/`) have no placeholder - drop the real module next to its
screenshots under whatever name it has.

Only the first two groups are needed for the pixel tests; the rest are
optional layout references.

1. **Same views again at RMT 200%** (lossless at 150% Windows scaling):
   - `song1-mono/tracks-scale200.png`
   - `song1-mono/instruments-scale200.png`
   - `song0-empty/tracks-scale200.png`
   - `song0-empty/instruments-scale200.png`
2. **A stereo (8-track) module** - the layout differs materially (wider
   grid, `R1`-`R4` columns, 1120 px minimum width):
   - `song2-stereo/<name>.rmt`
   - `song2-stereo/tracks-scale200.png`
   - `song2-stereo/instruments-scale200.png`
3. **Playback state** - the only way to see the volume analyzer bars, the
   play-time counter and the play-position highlight drawn:
   - `song1-mono/tracks-playing-scale200.png` (press Play, capture while
     it runs)
   - `song2-stereo/tracks-playing-scale200.png`
4. **A GOTO line** - cursor on a song line that has a goto set shows
   "GO TO LINE xx" instead of the tracks; any tiny throwaway song works:
   - `song3-goto/<name>.rmt`
   - `song3-goto/tracks-goto-scale200.png`
5. **Jam (prove) mode on** - flips the selection highlight from red to
   blue:
   - `song1-mono/tracks-jam-scale200.png`
6. **Optional, dialogs** (layout reference only - `Rmt.rc` has the exact
   geometry, so these are a visual sanity check for B7, not pixel tests):
   `dialogs/options.png`, `dialogs/options-paths.png`, `dialogs/tuning.png`,
   `dialogs/file-new.png`, `dialogs/change-max-track-length.png`,
   `dialogs/instrument-change.png`, `dialogs/tracks-order.png`,
   `dialogs/insert-copy-clone.png`, `dialogs/renumber-tracks.png`,
   `dialogs/renumber-instruments.png`, `dialogs/block-effects.png`,
   `dialogs/import-mod.png`, `dialogs/import-tmc.png`,
   `dialogs/export-sap.png`, `dialogs/export-xex.png`,
   `dialogs/export-stripped-rmt.png`, `dialogs/export-asm.png`,
   `dialogs/about.png`.
7. **Skip unless easy**: `song1-mono/tracks-hover-scale200.png` (mouse
   resting on a track cell, showing the hover recolor) - fiddly to capture,
   low value.

Not needed: further songs beyond one mono and one stereo, or the
help/info screens.
