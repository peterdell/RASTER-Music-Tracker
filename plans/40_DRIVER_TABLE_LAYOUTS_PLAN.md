# Plan 40: The tracker knows only one driver's frequency tables

Status: PROPOSED 2026-10-12. Follows plan 39, which concentrated the
table knowledge in one helper per program - which is what made this
gap visible as a single missing branch rather than a scatter of
addresses.

## The gap

`CFrequencyTables`/`FrequencyTables` answer for the Patch 16 driver
only. They read the page out of that driver's `TABLES_MSB` dispatch
table, and when the loaded driver has no such table they return 0,
which both callers turn into "no frequency":

- `export asm ... notes=freq` writes `XXX` for every note.
- MIDI note input plays nothing at all.

RMT ships seven drivers, and six of them have no dispatch table. So
with any driver but Patch 16 selected, two features silently do
nothing. Before plan 38 they did something wrong instead (they read
the Poly5 page whatever the distortion), so this is not a regression -
but "nothing" is not the right answer either, because the answer is
available.

**The other drivers carry their own tables, in memory, intact.** The
v1-family layout is one page of four 64-byte tables below the player,
verified present in the loaded `rmt_driver_v1.obx` image and identical
to `asm/Legacy/rmt.a65`:

| Address | Table          | Used for                           |
| ------- | -------------- | ---------------------------------- |
| $31c0   | frqtabbasslo   | 16-bit bass, low bytes             |
| $3200   | frqtabbass1    | distortions $06 and $0C            |
| $3240   | frqtabbass2    | distortion $0E                     |
| $3280   | frqtabpure     | distortions $00, $02, $04, $08, $0A |
| $32c0   | frqtabbasshi   | 16-bit bass, high bytes            |

RMT's tuning generator only ever writes $B000-$B5FF, so it never
touches them: they are exactly what those drivers play.

There is an irony worth recording. Base $31c0 with offsets
+64/+128/+192 is precisely what `GetFrequency` read before plan 38
fixed it. The function has always implemented exactly one driver
generation - it used to implement the one that was not loaded, and now
implements the one that usually is.

## The validation question

Plan 39 shipped a plausibility check, not a driver-version test:
`HasDispatchTable()` accepts the bytes if all eight page MSBs name one
of the five distortion pages and all eight AUDC bytes have an empty
volume nibble. Measured against every driver RMT ships, only
rmt_driver_v6.obx passes, and a test now asserts exactly that - in the
other six, those bytes are their own frequency-table data, far outside
$B0-$B4. So nothing misfires today.

It is still a heuristic. A future Patch 17, or a third-party driver,
could place something there that passes while meaning something else.
The programs do know which driver is loaded (`g_trackerDriverVersion`,
`RmtOptions.trackerDriverVersion`), so the fix is to say so rather
than to guess - and once the version is a parameter, the second layout
follows naturally from it.

## Proposed steps

- **L1. Give the helper the driver version.** Add a
  `TrackerDriverVersion` parameter to `GetTableAddress`/
  `GetDistortionAudc` and select the layout from it, keeping
  `HasDispatchTable()` as an assertion about the loaded bytes rather
  than as the decision. Pass the version in rather than reading the
  global: `InstrumentsCore.cpp` is deliberately free of `Global.h`
  (its header comment says so, and the test setup depends on it), and
  both callers have the version to hand - `MidiInput` through the
  session, the exporters through their own callers.
- **L2. Add the v1-family layout.** For `UNPATCHED`,
  `UNPATCHED_WITH_TUNING`, `PATCH3`, `PATCH6`, `PATCH8` and
  `PATCH_PRINCE_OF_PERSIA`, answer from the table above: bass1 for
  distortions $06 and $0C, bass2 for $0E, pure for the rest, with the
  16-bit pair ($31c0 low, $32c0 high) for the modes that need it.
  There is no 1.79 MHz and no 15 kHz variant in that layout, which the
  mode parameter has to acknowledge rather than silently ignore - most
  likely by answering "no table" for those two modes, since the
  classic driver cannot play them. Pin the addresses against the
  shipped rmt_driver_v1.obx the way plan 39 pins the dispatch table,
  so a reshuffle in `asm/Legacy` fails a test by name.
- **L3. Check the AUDC side.** The v1 layout has no dispatch table, so
  `GetDistortionAudc` needs the classic mapping too -
  `asm/Legacy/rmt.a65`'s `tabbeganddistor` holds it (distortion $06
  plays with AUDC $C0 there, not $A0). Read it from the driver like
  Patch 16's, if it sits at a stable address, rather than writing it
  out again.
- **L4. Report instead of staying silent.** Where no layout can
  answer - a driver version the helper does not know - the ASM export
  should say so once rather than writing a file full of `XXX`, in the
  spirit of plan 30's "a missing driver file must not produce a silent
  export". Decide whether MIDI does anything beyond not playing.

## Verification

- Both suites green, with the v1 addresses pinned against the shipped
  binary and the per-version behaviour asserted for all seven drivers,
  as plan 39's `OnlyThePatchSixteenDriverIsAcceptedAsCarryingADispatchTable`
  already does for the dispatch table.
- `export asm notes=freq` against Delta with the v1 driver selected:
  the values must match the legacy tables in the driver image, which
  is checkable by hand against `asm/Legacy/rmt.a65` for a few notes.
- `compare_exports` byte-identical, and the `notes=freq` output for
  Patch 16 unmoved - L1 must not disturb the path plan 38 and 39
  verified.

## Out of scope

- The tuning settings' reach. These drivers' tables are assembled into
  the binary, so the tuning dialog cannot affect them; that is plan
  37's separate finding, and nothing here changes it. Reading the
  tables gives the right answer for the driver as shipped, not a
  retunable one.
- `src/cpp/tracker_obx.h` being stale against today's `rmtplayr.a65`
  (plan 39, out of scope) - still its own change, and L2/L3 may want
  the Legacy sources to get the same generated-header treatment, which
  they have never had.
