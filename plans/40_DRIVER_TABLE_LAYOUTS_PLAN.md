# Plan 40: The tracker knows only one driver's frequency tables

Status: DONE 2026-10-12, L1-L4. Follows plan 39, which concentrated the
table knowledge in one helper per program - which is what made this
gap visible as a single missing branch rather than a scatter of
addresses.

## The gap

`CFrequencyTables`/`FrequencyTables` answered for the Patch 16 driver
only. They read the page out of that driver's `TABLES_MSB` dispatch
table, and when the loaded driver had no such table they returned 0,
which both callers turned into "no frequency":

- `export asm ... notes=freq` wrote `XXX` for every note.
- MIDI note input played nothing at all.

RMT ships seven drivers, and six of them have no `TABLES_MSB`. So with
any driver but Patch 16 selected, two features silently did nothing.
Before plan 38 they did something wrong instead (they read the Poly5
page whatever the distortion), so this was not a regression - but
"nothing" was not the right answer either, because the answer is
available.

**The other drivers carry their own tables, in memory, intact.** The
v1-family layout is one page of 64-byte tables below the player,
verified present in the loaded `rmt_driver_v1.obx` image and identical
to `asm/Legacy/rmt.a65`:

| Address | Table        | Used for                     |
| ------- | ------------ | ---------------------------- |
| $31c0   | frqtabbasslo | 16-bit bass, low bytes       |
| $3200   | frqtabbass1  | distortions $06 and $0C      |
| $3240   | frqtabbass2  | distortion $0E               |
| $3280   | frqtabpure   | distortions $00-$04, $08-$0A |
| $32c0   | frqtabbasshi | 16-bit bass, high bytes      |

RMT's tuning generator only ever writes $B000-$B5FF, so it never
touches them: they are exactly what those drivers play.

There is an irony worth recording. Base $31c0 with offsets
+64/+128/+192 is precisely what `GetFrequency` read before plan 38
fixed it. The function has always implemented exactly one driver
generation - it used to implement the one that was not loaded, and now
implements both.

## What the measurement changed about this plan

Three of L1-L4's premises, written before the driver images were read
byte by byte, turned out to be wrong. All three are recorded here
rather than quietly fixed, because each one is a fact about the
drivers that the next reader needs:

1. **The v1 family is not one layout's worth of data.** Only the
   unpatched driver has a source in this repository, and all five
   patched binaries changed the dispatch table's contents. PATCH6
   plays distortion $06 with AUDC $A0 where the unpatched driver plays
   it with $C0. PATCH3 sends distortion $04 to frqtabbass2. PATCH8 and
   PATCH_PRINCE_OF_PERSIA do not even keep their tables on $40
   boundaries - their offsets include $3c, $6c, $9c and $70. So the
   table **must** be read from memory; writing out "the" v1 mapping,
   as L2 and L3 proposed as an option, would have been wrong for four
   of the six drivers. What the code holds is the two addresses the
   `org` statements fix ($3182 and $3200) and nothing else.
2. **The v1 family does not answer "no table" for 1.79 MHz and
   15 kHz.** L2 guessed it should, "since the classic driver cannot
   play them". It can: `trackn_audctl` is a real instrument parameter
   there (`FEAT_AUDCTLMANUALSET`), and the player has no clock-specific
   tables, so it reads the same table whatever the clock. The faithful
   answer, and now the implemented one, is that same table - which is
   why those two clocks play out of tune on those drivers rather than
   playing nothing. The visible proof is in the verification below: the
   same Delta note reads $66 from frqtabpure under the v1 driver and
   $0B from the 15 kHz table under Patch 16.
3. **16-bit is the mode that genuinely cannot be answered.** The v1
   family's 16-bit bass reads `frqtabbasslo` and `frqtabbasshi`, two
   tables a page apart, which no single address can name - and which
   only the two drivers built from the checked-in source can be shown
   to still hold ($31c0 holds something else entirely in
   PATCH_PRINCE_OF_PERSIA). `GetTableAddress` answers 0 for
   `Join16Bit` on that family, documented as such. Patch 16 is
   unaffected: its 16-bit tables are interleaved little-endian words
   inside the distortion page.

## The validation question

Plan 39 shipped a plausibility check, not a driver-version test, and
L1 kept it that way on purpose: the version now selects the layout,
and `HasDispatchTable()` only says whether the bytes where that layout
expects its table could be one. It is what separates a loaded driver
from an empty Atari, and a driver of one family from the other's
layout.

The two checks differ in what can be checked. Patch 16's requires every
page MSB to name one of the five distortion pages and every AUDC to
have an empty volume nibble. The v1 family's cannot check the offsets
at all, for the reason above, so it checks the AUDC half and that the
table is there - the Patch 16 driver loads nothing into $3182, so its
bytes stay zero, and a real table cannot be all zeros. Measured across
all seven shipped drivers, each one passes its own family's check and
fails the other's, and a test asserts exactly that.

## Steps

- **L1. Give the helper the driver version.** DONE. `TrackerDriverVersion`
  is now the second parameter of `HasDispatchTable`, `GetTableAddress`
  and `GetDistortionAudc`, and the layout is selected from it. It is
  passed in rather than read from the global at three levels:
  `CInstruments::GetFrequency(instr, note, version)`, so
  `InstrumentsCore.cpp` stays free of `Global.h` as its header comment
  promises; `ExportAsAsmApply(song, ou, version, ...)`, because that
  half of the exporter is linked into the test project, which does not
  link `Global.cpp` (the first attempt read the global there and failed
  to link); and MIDI, where `Midi_Song.cpp` already has `Global.h` and
  `MidiInput` has the session. Java passes
  `session.options.trackerDriverVersion` from its two funnels.
- **L2. Add the v1-family layout.** DONE, as `$3200 +
  memory[$3182 + distortion]`, with the two addresses pinned against
  the shipped `rmt_driver_v1.obx` and the per-driver values of all six
  measured into a test.
- **L3. Check the AUDC side.** DONE, from the second byte of each
  `tabbeganddistor` pair - the same shape as Patch 16's, so
  `GetDistortionAudc` needed one address to vary and nothing else.
- **L4. Report instead of staying silent.** DONE. `notes=freq` with no
  readable tables now refuses the export and says why, instead of
  writing a file of `XXX`, in the spirit of plan 30's "a missing driver
  file must not produce a silent export". The check sits in the one
  place each program's ASM export passes through
  (`ExportAsAsmApply`, which returns `false`/`null` with the reason
  sent through `SendErrorMessage`/`Messages`), so the GUI shows a box
  and the script turns it into a `ScriptError` through the problem
  collector that was already there. MIDI needs nothing beyond not
  playing: it is a live note, with no file to mistake for a good one.

## Verification

- 697 Java and 449 C++ tests green (from 691 and 443): the v1
  addresses pinned against the shipped binary, the per-driver
  distortion $06 table and AUDC measured for all six, the
  same-table-whatever-the-clock rule, `NONE` answering nothing, each
  driver accepted only for its own family's layout, the version
  deciding between two dispatch tables present in memory at once, and
  L4's refusal.
- `export asm type=tracks notes=freq` on Delta through `Rmt.exe`,
  once with `set driver unpatched` and once with `patch16`: the v1 run
  now writes real values where it used to write `XXX`, and every one of
  them is a byte of `asm/Legacy/rmt.a65`'s `frqtabpure` ($66, $88, $44,
  $5B, $99, $40, $55, $4C, checked by hand against the source). Both
  files have the same 329 `XXX` - the rests, which are `XXX` by design.
- `compare_exports`: 4 scripts, every exported file identical in both
  programs, so L1 moved nothing on the Patch 16 path that plans 38 and
  39 verified.

## Out of scope

- The tuning settings' reach. These drivers' tables are assembled into
  the binary, so the tuning dialog cannot affect them; that is plan
  37's separate finding, and nothing here changes it. Reading the
  tables gives the right answer for the driver as shipped, not a
  retunable one. `doc/rmt_tracker_drivers.md` now says so.
- The v1 family's 16-bit bass, for the reason in point 3 above. It
  would need the two addresses hardcoded for the two drivers that have
  a source, which is a trade worth making only if someone wants 16-bit
  MIDI input on a 1.28 driver.
- `src/cpp/tracker_obx.h` being stale against today's `rmtplayr.a65`
  (plan 39, out of scope) - still its own change, and the Legacy
  sources have never had the generated-header treatment at all, which
  is why $3182 and $3200 are written out in `FrequencyTables` instead.
