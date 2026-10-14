# Plan 42: `src/cpp/tracker_obx.h` has drifted from the player source

Status: DONE 2026-10-12, H1-H4. Written to give this item a home: plans 39
and 40 each set it aside in their "out of scope" section, so it existed
only as a footnote to two finished plans and nothing listed it as work.
Plan 38 is where the C++ side started reading these names rather than
bare addresses, which is what makes the header worth keeping honest.

## What it is

`src/cpp/tracker_obx.h` is the C header MADS writes beside the Patch 16
player, one `#define` per label: `asm/Patch-16/build.cmd` runs
`mads -hc:out\tracker_obx.h ... rmtplayr.a65`, and the result is copied
into `src/cpp/`. It is how the tracker addresses the player's variables
by name - `RMTPLAYR_TABLES_MSB`, `RMTPLAYR_V_AUDCTL`,
`RMTPLAYR_TRACKN_AUDF` and the rest - instead of by number.

The checked-in copy was not regenerated the last time the player's
labels changed.

## The drift, measured 2026-10-12

A fresh `mads -hc:` of today's `asm/Patch-16/rmtplayr.a65`, compared with
the checked-in header:

| | |
| ------------------------------------------- | --- |
| address defines in the checked-in header    | 359 |
| address defines a fresh header has          | 349 |
| in the checked-in header, not generated now | 34  |
| generated now, not in the checked-in header | 24  |

(Both files hold one `#define` more than that - the include guard, which
carries no address. `build/check_drivers.sh` counts those too and so
reports 360 and 350.)

The 34 that disappeared are mostly the player's internal branch labels,
renamed from opaque pairs to readable ones: `RMTPLAYR_NN0`,
`RMTPLAYR_NN1A2`, `RMTPLAYR_OO1X`, `RMTPLAYR_RI0`, `RMTPLAYR_SI1` and
their kin are gone, and names like `RMTPLAYR_GSL_INITTRACK`,
`RMTPLAYR_GSL_THISISAGOTO`, `RMTPLAYR_GTL_LOOPTRACKS` and
`RMTPLAYR_GTL_CHECKENDOFLOOP` have taken their place. The rest pair up
one-to-one as renames of the same thing: `RMTPLAYR_P_SONG` is now
`RMTPLAYR_PTRSONGLINES`, `RMTPLAYR_P_INSTRSTABLE` is
`RMTPLAYR_PTRINSTRUMENTTBL`, `RMTPLAYR_TRACKN_HB`/`_DB` are
`RMTPLAYR_TRACKN_TBLHI`/`_TBLLO`, and the self-modifying-code operands
`RMTPLAYR_V_SPEED`, `_V_ABEAT`, `_V_BSPEED`, `_V_INSTRSPEED`,
`_V_MAXTRACKLEN` are now `RMTPLAYR_SMC_*`.

**Nothing the tracker uses has gone.** Twenty `RMTPLAYR_*` names are
referenced outside the header itself - in `Atari.h`, `AtariView.cpp`,
`FrequencyTables.cpp`, `Midi_Song.cpp`, `WaveFileExporter.cpp` and two
test files - and all twenty exist in a fresh header with the same
values. So this was latent, not a live defect: the stale entries were
dead weight and the new ones simply absent, and the addresses the code
reads were right. That is why plans 39 and 40 could each set it aside and
still be correct.

What it cost was trust. Anyone reading the header could not tell which
entries still meant anything, and the three plans that verified
`RMTPLAYR_TABLES_MSB`, `RMTPLAYR_DISTORTIONS` and `RMTPLAYR_V_AUDCTL`
had to do so against a freshly generated header by hand, each time,
because the checked-in one was not evidence.

### Two things the measurement corrected

- **`RMTPLAYR_FEAT_IS_TRACKER` and `RMTPLAYR_FEAT_IS_SIMPLEP` did not go
  away.** They left the header because they moved inside the `.ifndef`
  blocks in `rmt_feat.a65` that let `build.cmd` override them with `-d:`
  for the Simple RMT Player, and MADS does not export a label defined
  inside conditional assembly. A plain `equ` like `FEAT_SFX` still
  appears. Both flags still exist with the same values; only the header
  stopped mentioning them.
- **A twenty-first reference is not a name.** `Atari.java` names
  `RMTPLAYR_FRQTAB*` in a comment - a wildcard for the family, not an
  entry - so a search for referenced names finds 21 strings and 20
  actual defines. The one that does not exist is the glob.

## Steps

- **H1. Regenerate and commit the header.** DONE. Reassembled with the
  vendored MADS 2.1.9 and copied in: 24 insertions, 34 deletions, no
  change to any value in use. Both programs build with no warnings, both
  suites pass and `compare_exports` is byte-identical, as predicted - the
  header is addresses only.
- **H2. Make the copy automatic, or prove it is current.** DONE, the
  second way. `build/check_drivers.sh` now compares each header the way
  it already compares the driver binaries. One MADS run produces both
  artefacts: `-hc:` writes the header and leaves the object file
  untouched, verified by assembling both drivers with it and getting the
  shipped bytes, so the check costs nothing but the comparison. The
  comparison strips CRs first - MADS writes CRLF on Windows while the
  working copy's endings follow `core.autocrlf`, and neither says
  anything about an address. A missing header fails too, with its own
  message.
- **H3. Decide what the Java port does with it.** DONE: the port keeps
  its own constants and a test holds them to the header.
  `GeneratedHeaderTest` (4 tests) parses both headers and asserts every
  address the port mirrors - `Atari`'s eight frequency-table addresses,
  `FrequencyTables.RMT_TABLES_MSB` and both v1-family entries. C++ needs
  no equivalent: it `#include`s the headers and uses the `#define`s, so
  its compiler is the check. The test reads the checked-in headers rather
  than assembling, because H2 already proves those are what the sources
  generate; the two checks together reach from the 6502 source to both
  programs. Verified non-vacuous by changing one Java constant by one and
  watching it fail with the names and both values in the message.
- **H4. The Legacy player has never had this at all.** DONE.
  `asm/Legacy/rmt_ata.a65` now generates `src/cpp/legacy_obx.h` (242
  defines, prefix `RMT_ATA_` after the file name), checked in beside
  `tracker_obx.h` and compared by H2's check. `FrequencyTables`'
  `LEGACY_TABBEGANDDISTOR` and `LEGACY_FRQTAB` are now
  `RMT_ATA_TABBEGANDDISTOR` and `RMT_ATA_FRQTAB` instead of `$3182` and
  `$3200`, so plan 40's two literals follow the source like the Patch 16
  addresses do.

  The generated header also confirms plan 40's measurements and
  `doc/rmt_tracker_drivers.md`'s table independently, from the source
  rather than from the loaded binary: `tabbeganddistor` at `$3182`,
  `frqtab`/`frqtabbass1` at `$3200`, `frqtabbass2` `$3240`, `frqtabpure`
  `$3280`, `frqtabbasshi` `$32C0` and `frqtabbasslo` `$31C0`, a page
  below its high half. `GeneratedHeaderTest` asserts those too, including
  that every table a dispatch offset can reach stays inside the `frqtab`
  page.

## Found while doing it

`AtariView`'s memory view, in both programs, read
`ADDRESS = 0x3000; // RMTPLAYR_TABLES;` - but `RMTPLAYR_TABLES` is
`$B000`. The address is right and deliberate (the view shows the driver's
own code and variables); the comment pointed at an unrelated name.
Corrected in both, with no change in behaviour. It is the kind of claim
H3's test now makes impossible to leave wrong where a real constant is
involved.

## Verification

- Both suites green: 450 C++ tests, 702 Java tests (698 + H3's 4).
- `compare_exports` byte-identical across all four scripts, re-run
  against a freshly packaged `rmt.jar`.
- `sh build/check_drivers.sh` reproduces both drivers and both headers:
  four OK lines, exit 0.
- The header check fails as intended on a header with one `#define`
  added and on a deleted header, and passes again once restored.

## Out of scope

- Renaming anything in `rmtplayr.a65`. The label renames that caused the
  drift are already done and are an improvement; this plan only caught
  the header up with them.
- Giving the Java port a generated header of its own. It mirrors a
  handful of addresses, which H3's test now pins; generating Java
  constants from the 6502 source would be a build-step change for no
  present gain.
