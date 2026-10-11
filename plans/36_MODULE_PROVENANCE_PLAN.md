# Plan 36: An .rmt does not define its own sound - the missing driver and tuning information

Status: IN PROGRESS 2026-10-11. The four original XEXs are
transferred into the fixture folders (F4, approved by the user - for
Delta both the 1.0b and the 1.28 build, as they differ) and the
anchoring F1-F3 ran. Results:

- **F1 Delta - ANCHORED.** Raster's 1.10-1.28 original XEX against
  RMT's unpatched-driver SAP-R export: 3750 of 3840 frames identical
  at one constant offset; every difference lies in the first ~115
  frames (the intro), small AUDC/AUDF deviations - early-driver
  quirks of the April-2003 player build (Raster's changelog documents
  first-tick envelope and table fixes in that era). The 1.0b original
  additionally plays AUDF2 an octave apart mid-tune: the frequency
  table reformat Raster logged on 3.2.2003. The tune body of our
  1.28 rendering is confirmed against the composer's own export.
- **F2 Captain Future Theme - NOT byte-anchored.** Buddy's Silly Venture
  2k24 SE XEX (RMT 1.34 export, Aug 2024) has the identical pass length
  (7524 frames, loop confirmed at exactly +7524) and an identical
  start, but then: silent channels keep AUDF/distortion latched in
  our chain where the 1.34 player writes $00; AUDCTL differs by the
  $20 bit (ch3 1.79 MHz) in 573 frames; and audible channels
  (volume > 0) differ in 1862 of 7524 frames (envelope detail).
  The 2024 export player generation and our Patch16 chain disagree
  beyond register hygiene - which side matches the composer's ear is
  by definition the XEX. Needs a follow-up diagnosis.
- **F3 Why Do You Dance With Me - NOT YET byte-anchored (a method
  limit, see the correction below).** The party XEX embeds the
  byte-identical module, but plays it through a 1.32-era player with
  retuned frequency tables - custom tuning. (The $3e00/$3f00
  segments first suspected as tables are display code and the title
  text; the real tables sit inside the player segment.) This tune
  demonstrates BOTH missing dimensions at once, driver generation
  and tuning.
  **Follow-up analysis of the XEX itself (2026-10-11):** the player
  segment ($3182-$3a64) is the CLASSIC v1-family driver (the
  v1/v2/v4 table layout, not Patch16) with RETUNED frequency
  tables (the pure-tone table shifted 1-2 divisor steps sharper, a
  64-byte table at $31c0 fully rebuilt), one distortion-table entry
  changed at $3189 ($c0 -> $a0) and 11 extra code bytes at the tail.
  The module is embedded byte-identically, and RMT 1.36's
  decode/re-encode round-trips it losslessly (stripped-rmt export ==
  file). So the "Patch16?" README question is ANSWERED: classic
  driver family, custom tuning.
  **The transplant experiment and the method correction
  (2026-10-11):** transplanting first the tables, then the complete
  original player into the export driver still produced no
  byte alignment - and the reason turned out to be the COMPARISON,
  not the rendering: the module has instrument speed 4, so RMT's
  SAP-R export is TICK-level (FASTPLAY 78: 4 entries per video
  frame; the tune is 6080 video frames = 121.6 s, not 24320), while
  the bridge recording samples once per video frame. After
  correcting for that, the attack grid aligns (every 10 video
  frames in both) and with the transplanted player the start states
  match exactly - but frame-boundary snapshots mostly fall between
  tick states (the XEX spreads its four calls across the frame), so
  a byte-level verdict for a FASTPLAY tune needs TICK-ACCURATE
  recording, which the bridge cannot do today (FRAME 1 is its
  finest step). The F1/F2 method and verdicts are unaffected
  (instrument speed 1 there).

Net: one of three tunes is anchored to its original. Captain Future
shows real divergence between its 1.34 export player and the current
chain; Why Do You Dance With Me is reproduced down to the recovered
driver and tables, but its byte-level verdict waits on tick-accurate
recording (open decision 3).

Scope: verification of the POKEY streams against the original XEX
files, ONLY. Fixing the format gap itself (driver/tuning fields in a
format version 2) is deliberately out of scope here and stays a
future plan; the problem statement below remains as the motivation.

## Problem statement

The RMT module format version 1 - every `.rmt` file written to date -
stores the song, tracks and instruments, but neither the **tracker
driver** the tune was composed for (Raster's classic driver versus the
Patch16 generation, with their different frequency handling) nor the
**tuning** (the frequency tables; custom tuning exists since the
VinsCool era). Both are program state: in RMT 1.36 the driver is a
setting (`TrackerDriverVersion`), not a module property, and the
tuning lives in `tuning.ini`.

The same `.rmt` therefore produces **different POKEY register streams**
depending on what it is paired with, and the file itself cannot say
which pairing the composer meant. The intended sound is only defined
by an artifact the composer rendered - an original XEX or SAP export.

This is not theoretical. It is the root cause of the Patch-16 player
incident (plans 32/33): Delta, a 1.28-driver tune, inside the Patch16
player - wrong speed, and no flag in the module could have said so.
The `Why_Do_You_Dance_With_Me` README's "Tracker Driver: Patch16? To
be checked!" marker is the same gap in another form.

What the existing verification does and does not prove:

- Plan 33 proved **internal consistency**: the Simple RMT Player
  reproduces RMT's own SAP-R export frame for frame (7524/7524).
- The cross-program comparison proves **C++ == Java**, byte for byte.
- Neither proves **composer intent**: both sides of each comparison
  are our own rendering chain.

## The good news: originals exist for all three test tunes

| Tune | Original artifact | Where |
|---|---|---|
| Delta | Raster's own `delta.xex` (1.28 player) and `delta.sap` | in the repository (`rmt/exports/rmt128`, `legacy`, `others`) |
| Captain Future Theme | Buddy's Silly Venture 2k24 SE release XEX "(Stereo-Pokey)" | user's `ASMA-Input\Buddy` folder |
| Why Do You Dance With Me? | `WHYDOYOU.XEX` from the Silly Venture 2k21 WE party release (`whydoyou.zip`, with the info text: "Requires stereo POKEY") | downloadable from the README's source URL (verified) |

The plan-33 tooling (`build/record_sapr_bridge.py`) makes each anchor
a cheap, frame-exact comparison: record the original XEX headlessly,
align against RMT's SAP-R export of the module with the matching
driver setting.

## Proposed steps

- **F1 - anchor Delta:** record Raster's original `delta.xex` via the
  bridge and compare against RMT's SAP-R export of `Delta.rmt` with
  the unpatched driver. Expected: identical after alignment; any
  difference is a finding about our 1.28 rendering.
- **F2 - anchor Captain Future Theme:** record Buddy's original
  Silly Venture 2k24 SE XEX and compare against the committed
  `Captain_Future_Theme.sapr` reference.
- **F3 - anchor Why Do You Dance With Me:** record `WHYDOYOU.XEX` and
  compare against RMT's export, once per driver setting - the match
  resolves the "Patch16? To be checked!" marker empirically.
- **F4 - pin the verdicts:** update the three fixture READMEs with the
  anchoring result; commit the original XEXs as reference exports into
  the fixture folders (they are what the anchoring consumes -
  redistribution of the party/ASMA artifacts is the user's call), and
  commit `.sapr` references for the two tunes that lack one.

## Out of scope (future plan)

The root-cause fix - a format that carries the driver and the tuning
(the `doc/rmt_format.md` version 2 draft: an `RMX1` `DRIVER` tag, the
`TUN` segment, the `PLR` segment embedding the player itself) - is
NOT part of this plan. Until it exists, the per-song README metadata
sheet ("Tracker Driver:" line) stays the manual stand-in.

## Open decisions

1. DONE 2026-10-11: the original XEXs are committed into the fixture
   folders (user-approved; for Delta both the 1.0b and 1.28 builds).
2. F2 follow-up: diagnose why the RMT 1.34 export player's envelope
   detail differs from the current chain (1862 of 7524 audible-state
   frames).
3. F3 follow-up: a byte-level verdict for FASTPLAY (instrument speed
   > 1) tunes needs tick-accurate recording - e.g. sub-frame stepping
   in AltirraBridge (an upstream follow-up in the spirit of PR #96),
   or sampling the POKEY shadow per driver call instead of per frame.
