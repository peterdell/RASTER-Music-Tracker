# Plan 39: The MIDI input path reads the RMT 1.34 table layout

Status: DONE 2026-10-12. M1, M2, M4 and M5 are done; M3 was dropped as
written, for the reasons under it. Plan 38 fixed `GetFrequency`; this
was the same defect one driver generation later, plus the
de-duplication that keeps the two from drifting apart again: the page
mapping and the AUDC bits now come from the driver's own dispatch
table in both programs, and the clock rule lives in one tested helper
per program instead of twice.

## The defect

MIDI note input turns a note into an AUDC/AUDF/AUDCTL triple itself,
in `CMidiSong`'s note handler (src/cpp/Midi_Song.cpp, from line ~450)
and its Java mirror (`MidiInput.java`, from line ~520). Both pick the
frequency table by distortion and clock, with hardcoded addresses -
and those addresses are the RMT 1.34 FIVE-page layout, from before the
Distortion 4 page was inserted into `rmtdata.a65` on 2022-08-13:

| Table         | MIDI code uses | Patch 16 has |
| ------------- | -------------- | ------------ |
| Poly5 64 kHz  | $B000          | $B000        |
| Pure 64 kHz   | $B100          | $B200        |
| Buzzy 64 kHz  | $B200          | $B300        |
| Gritty 64 kHz | $B300          | $B400        |
| Pure 15 kHz   | $B480          | $B580        |
| Buzzy 15 kHz  | $B4C0          | $B5C0        |

Every table except Poly5 therefore sits one page too low: a MIDI-played
pure tone reads the Distortion 4 page, buzzy reads pure, gritty reads
buzzy, and both 15 kHz reads land inside the gritty page. The 1.79 MHz
variants are shifted the same way ($B040 is right, $B140/$B240/$B340
are not). Audible as wrong pitch on MIDI note input, identically in
both programs, and the Java comment above the switch documents the
wrong layout as if it were current.

Three further problems in the same code, all worth fixing in one go:

- The switch is on the AUDC distortion nibble, and `$40` (distortion
  4) falls through to the pure default, so the Distortion 4 page is
  never used at all - correcting the addresses alone would not fix
  that. `$60` is grouped with `$20` onto the Poly5 page, while the
  player maps distortion 6 to the pure page.
- `JOIN_16BIT` is computed and then used only to suppress 15 kHz mode.
  No 16-bit table is ever read, although MIDI - unlike `GetFrequency`
  (plan 38) - does know the channel and therefore CAN resolve 16-bit
  and 1.79 MHz properly.
- AUDCTL is read as `memory[0x3C69]` although the symbol exists twice
  over: `RMTPLAYR_V_AUDCTL` (tracker_obx.h) and
  `CPokeyController::AUDCTL`. Verified still correct against a freshly
  generated header, but it should be the name.

## Avoiding the redundant code

This is the third place to learn the same layout - `CTuning` writes
the page, plan 38's `GetFrequency` reads it, and MIDI reads it again -
and the first two already disagreed with the driver for years. So the
fix is not "correct six more constants" but "stop keeping a private
copy of the mapping".

**The page mapping comes from the driver itself.** `rmtplayr.a65`
dispatches through `TABLES_MSB`, eight (page MSB, AUDC) pairs indexed
by distortion / 2, and tracker_obx.h names it:
`RMTPLAYR_TABLES_MSB` = `$3208` (`RMTPLAYR_DISTORTIONS` = `$3209` is
the same table's second byte). It is in the loaded driver's memory, so
both programs can simply read it. Decoded from the shipped
rmt_driver_v6.obx it says:

| Distortion | $00  | $02  | $04  | $06  | $08  | $0A  | $0C  | $0E  |
| ---------- | ---- | ---- | ---- | ---- | ---- | ---- | ---- | ---- |
| Page       | $B200| $B000| $B100| $B200| $B200| $B200| $B300| $B400|
| AUDC       | $00  | $20  | $40  | $A0  | $80  | $A0  | $C0  | $C0  |

which is exactly what plan 38 derived by hand from the assembler
source. Reading it instead of hardcoding it means the mapping exists
once, in the 6502 source, where `build/check_drivers.sh` already pins
it to the shipped binary; a future page reshuffle propagates to both
programs by itself, and neither program can disagree with the driver
or with the other.

**What cannot come from there, and so goes into one shared helper.**
The sub-table choice is in the player's code rather than a table
(`store_tables_lsb`: 64 kHz at +$00, 1.79 MHz at +$40, 16-bit at
+$80), and the two 15 kHz tables sit outside the dispatch entirely
(`check_15khz`: buzzy for distortion $0C and above, pure below, at the
named `RMTPLAYR_FRQTABPURE_15KHZ`/`..._BUZZY_15KHZ`). That rule is
small, and it is the part both call sites need. It becomes one
function per program - `FrequencyTables` in C++ (its own header beside
Atari.h) and `org.atari.raster.rmt.model.FrequencyTables` in Java:

    enum class Mode { Clock64kHz, Clock179MHz, Clock15kHz, Join16Bit };

    /// The address of the AUDF table the Patch 16 player would read for this
    /// distortion and mode, or 0 when the loaded driver has no such table.
    /// The table holds 64 bytes, except in Join16Bit mode, where it holds 64
    /// little-endian words.
    static MemoryAddress GetTableAddress(const byte* memory, int distortion, Mode mode);

A plain C++ `enum class` and a plain Java `enum`: the mode is never
shown to the user, so this is not a WUDSN Base `ValueSet` (see
CLAUDE.md).

After that both call sites shrink to picking a mode and reading one
byte:

- `GetFrequency` (plan 38) passes `Clock15kHz` or `Clock64kHz`, since
  it has no channel, and keeps its distortion-6 rule.
- The MIDI handler passes the mode it already computes from
  `clock179`/`clock15`/`join16bit`, which also gives 16-bit a correct
  answer for the first time.

The helper's scope is the Patch 16 driver, and it should say so: the
v1-family drivers never read `$B000` (their tables are inside the
player, see plan 37's separate findings), and both call sites silently
assume Patch 16 today. Returning 0 for a driver without the dispatch
table, and leaving the callers to report "no frequency", keeps that
assumption visible instead of hidden.

That is where this plan stops, and it is not the whole answer: with
any of the other six drivers selected, the ASM export now writes `XXX`
for every note and MIDI plays nothing, although those drivers carry
their own tables in memory. `HasDispatchTable()` checks all eight
dispatch entries rather than the one being read - a test asserts that
only rmt_driver_v6.obx passes, of the seven RMT ships - but it stays a
plausibility check on the bytes rather than a test of the loaded
version. Both are taken up by plan 40.

## Steps

- **M1. The shared helper. DONE 2026-10-12.** `FrequencyTables` in
  both programs, as
  above: page from `TABLES_MSB` in the Atari's memory, sub-table from
  the mode, 15 kHz from the named addresses. Tests in both programs
  assert all eight distortions against the dispatch-table bytes of the
  shipped rmt_driver_v6.obx, so the helper is pinned to the driver the
  way plan 37's table pins are.
  `CFrequencyTables` (src/cpp/FrequencyTables.h/.cpp, registered in
  both vcxproj files) and `FrequencyTables` (model package) with the
  mode enum nested in it. Two deviations from the proposal above, both
  deliberate: `Join16Bit` returns the 16-bit table's address like
  every other mode rather than 0, since the address is perfectly
  well-defined and returning 0 would make the mode useless - reading
  the little-endian word is the caller's business, and the helper for
  that lands in M4 where it is first needed, not here as dead code.
  And `GetTableAddress` takes the memory buffer rather than the
  `CAtari`, which keeps it a pure function of (memory, distortion,
  mode) and makes the two programs' signatures mirror each other
  exactly, since the Java `getFrequency` already took a `byte[]`.
  The driver-sanity check is a page-MSB range test ($B0 to $B4): that
  is deliberately not driver detection, only a guard that these bytes
  plausibly ARE the dispatch table, and the comment says so.
- **M2. Move `GetFrequency` onto it. DONE 2026-10-12.** Behaviour
  unchanged from plan
  38 - the tests written there must stay green untouched, which is
  what makes this a safe refactor rather than a second fix.
  That last expectation was wrong and the plan was wrong to state it:
  the helper reads the dispatch table out of the driver, so
  `GetFrequency` now needs a driver in memory, while plan 38's tests
  wrote table bytes into a bare buffer and asserted the page mapping
  themselves. They were restructured rather than kept, and the split
  is better for it: the per-distortion mapping is asserted in ONE
  place per program, against the real shipped driver
  (FrequencyTablesTests.cpp / FrequencyTablesTest.java), while
  `GetFrequency`'s own tests install a SYNTHETIC dispatch table that
  deliberately sends every distortion to a different page than the
  driver does, so they pin the policy - which mode when, -1 for
  distortion 6, the note-table shift - and would fail if the code went
  back to a hardcoded map. A new case covers memory with no dispatch
  table at all, where the answer is -1.
  Verified not to move the output: the ASM export's `notes=freq` file
  is byte-identical to the plan 38 build's, CrossProgramExportTest
  passes with the refactored Java against the unrefactored Rmt.exe,
  and compare_exports reports all four scripts identical.
- **M3. Make the MIDI handler testable. DROPPED as written, 2026-10-12,
  on two findings.** Extract the note-to-POKEY
  decision - note, instrument/distortion, channel and AUDCTL in, AUDC
  plus AUDF (plus the 16-bit high byte) out - as a pure function on
  the model side, leaving the handler to do MIDI message plumbing and
  memory writes. MIDI input has no automated coverage at all today,
  and this is the only way the fix gets a regression fence; it is also
  what makes M4 reviewable.
  That premise was simply false: the Java port has `MidiInputTest`,
  17 tests, and one of them -
  `aChannel16NoteInTheExplorerModeSoundsDirectlyFromTheDriversNoteTable`
  - covers this exact path and asserts the table a note sounds from.
  It failed the moment M4 landed, naming the distortion-4 page, which
  is precisely the regression fence M3 was meant to build. The second
  finding is that the block is scaffolding its own author marked as
  such ("TESTING HARDCODED DATA, THIS MUST NOT BE THE WAY TO GO!
  COMMENT THIS ENTIRE BLOCK OUT ONCE A PROPER INPUT HANDLER IS
  ADDED"), so a new model class and test suite around it would be
  structure spent on code slated for replacement. What the extraction
  was really for - getting the tricky, duplicated logic under test -
  was had by moving it into the shared helper instead: the AUDCTL and
  channel-pairing rule became `GetModeForChannel` and the AUDC bits
  `GetDistortionAudc`, both tested in both programs, and the handler
  shrank to picking a mode and reading a byte. The handler's own
  plumbing - channel allocation, held-key bookkeeping, the volume
  merge - was left untouched.
- **M4. Fix the MIDI table selection. DONE 2026-10-12.** Use the
  helper, so the
  addresses come from the driver; route distortion 4 to its own page;
  settle `$60` the way the dispatch table does; use `Join16Bit` where
  `join16bit` is true and write the word's two bytes; replace
  `memory[0x3C69]` with the named constant. Each of these gets a test
  from M3.
  Done, and the AUDC bits now come from the dispatch table too, which
  settles more than `$60`: the `$E0` case had to special-case itself
  back to AUDC `$C0` by hand, and that special case is gone because
  the driver's own table says `$C0`. 16-bit mode selects the 16-bit
  table for the first time and writes the pair's low byte to the
  channel below. A missing dispatch table (no Patch 16 driver) now
  means the note is not played rather than a read from whatever
  happens to be at those addresses. While in that file, all 26
  remaining bare driver addresses became named constants
  (`RMTPLAYR_TRACKN_AUDF`/`_AUDC`, `RMTPLAYR_V_AUDCTL`,
  `RMTPLAYR_V_SKCTL`) - the Java port already used names there, so
  this only closes the gap between the two.
- **M5. Documentation. DONE 2026-10-12.** The Java comment above the
  switch is a
  description of the old layout and must go; doc/rmt_tracker_drivers.md
  gains one sentence that the page mapping is read from the driver's
  own dispatch table rather than copied, since that is now a fact
  about the driver's interface to the tracker.
  The stale comment went with the switch it described. The driver page
  gained a paragraph: the dispatch table is part of the driver's
  interface to the tracker, both readers take the mapping from it, and
  both used to keep a copy that had fallen a page behind - one from
  RMT 1.28, one from the five-page layout.

## Verification

- Both suites green, with the new M1 pins and the M3/M4 tests; plan
  38's `GetFrequency` tests must pass unchanged through M2.
- `build/check_drivers.sh` still proves asm against obx (nothing here
  touches the driver, but the helper now depends on its bytes).
- `build/compare_exports.ps1` byte-identical: the ASM export's
  `notes=freq` output must not move in M2, which is the regression
  risk of the refactor.
- MIDI itself stays a manual check (one note per distortion, 15 kHz
  and 1.79 MHz on and off, against the tracker's own playback of the
  same instrument) - the automated part is M3's pure function.

Done: 690 Java and 442 C++ tests green, compare_exports identical for
all four scripts, the `notes=freq` export unmoved, check_drivers.sh
still green. `MidiInputTest` carries the corrected expectations and
says in place what they were before and why.

What is NOT covered automatically, and should be the manual check: the
C++ `CSong::MidiEvent` has no test of its own - it is MFC- and
global-bound, and there is no C++ counterpart to `MidiInputTest`. Its
correctness here rests on being a line-by-line mirror of the Java
handler, which is tested, plus the shared helper's own tests, which
are the same code path in both programs. The 16-bit case is
untested in either program for the same reason: nothing drives a
joined-channel AUDCTL through the MIDI handler today.

## Out of scope

- `src/cpp/tracker_obx.h` is stale against what MADS generates from
  today's `rmtplayr.a65` - many labels renamed, some entries missing.
  `RMTPLAYR_TABLES_MSB`, `RMTPLAYR_DISTORTIONS` and
  `RMTPLAYR_V_AUDCTL` were verified against a freshly generated header
  for this plan, but refreshing that file is its own change.
- The v1-family drivers' own tables, and anything about which driver
  the MIDI path should assume. Picked up by
  [plan 40](40_DRIVER_TABLE_LAYOUTS_PLAN.md) and done: the helper now
  takes the driver version, and both the ASM export and MIDI input
  read whichever of the two layouts that version uses.
