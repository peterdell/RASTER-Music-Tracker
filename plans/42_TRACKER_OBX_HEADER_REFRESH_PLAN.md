# Plan 42: `src/cpp/tracker_obx.h` has drifted from the player source

Status: PROPOSED 2026-10-12. Written to give this item a home: plans 39
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
| ----------------------------------------- | --- |
| defines in the checked-in header          | 360 |
| defines a fresh header has                | 350 |
| in the checked-in header, not generated now | 34 |
| generated now, not in the checked-in header | 24 |

The 34 that disappeared are mostly the player's internal branch labels,
renamed from opaque pairs to readable ones: `RMTPLAYR_NN0`,
`RMTPLAYR_NN1A2`, `RMTPLAYR_OO1X`, `RMTPLAYR_RI0`, `RMTPLAYR_SI1` and
their kin are gone, and names like `RMTPLAYR_GSL_INITTRACK`,
`RMTPLAYR_GSL_THISISAGOTO`, `RMTPLAYR_GTL_LOOPTRACKS` and
`RMTPLAYR_GTL_CHECKENDOFLOOP` have taken their place. Two feature flags
(`RMTPLAYR_FEAT_IS_TRACKER`, `RMTPLAYR_FEAT_IS_SIMPLEP`) and a handful of
`RMTPLAYR_P_*` / `RMTPLAYR_V_*` entries are also among the missing.

**Nothing the tracker uses has gone.** Twenty `RMTPLAYR_*` names are
referenced outside the header itself - in `Atari.h`, `AtariView.cpp`,
`FrequencyTables.cpp`, `Midi_Song.cpp`, `WaveFileExporter.cpp` and two
test files - and all twenty exist in a fresh header with the same
values. So this is latent, not a live defect: the stale entries are dead
weight and the new ones are simply absent, and the addresses the code
reads are right. That is why plans 39 and 40 could each set it aside and
still be correct.

What it costs today is trust. Anyone reading the header cannot tell which
entries still mean anything, and the three plans that verified
`RMTPLAYR_TABLES_MSB`, `RMTPLAYR_DISTORTIONS` and `RMTPLAYR_V_AUDCTL`
had to do so against a freshly generated header by hand, each time,
because the checked-in one was not evidence.

## Proposed steps

- **H1. Regenerate and commit the header.** Run
  `asm/Patch-16/build.cmd`, copy `out/tracker_obx.h` to `src/cpp/`, and
  check that both programs still build and all tests pass. The 20 names
  in use are unchanged, so this should be a no-op for behaviour - and
  `compare_exports` plus the driver check should confirm it.
- **H2. Make the copy automatic, or prove it is current.** The header is
  a generated artefact checked in beside hand-written code, which is how
  it drifted. The cheapest guard is the one `build/check_drivers.sh`
  already uses for the binaries: assemble into a temporary folder and
  compare, failing when `src/cpp/tracker_obx.h` is not what today's
  source produces. That script already runs in CI
  (`.github/workflows/check-drivers.yml`) and already assembles this
  exact file, so the check is a few lines inside work it is doing
  anyway.
- **H3. Decide what the Java port does with it.** The port has no
  generated header; `Atari.java` holds the handful of addresses it needs
  as its own constants, with comments naming the `RMTPLAYR_*` entry each
  one mirrors. If H2 lands, those comments become checkable too - a test
  could read the generated header and assert the Java constants agree,
  which would close the last gap where the two programs can disagree
  about the player's layout without anything failing.
- **H4. The Legacy player has never had this at all.** `asm/Legacy` is
  assembled by `build/check_drivers.sh` but produces no header, which is
  why plan 40 writes `$3182` and `$3200` into `FrequencyTables` as
  literals. Generating one for the v1 family would let those two follow
  the source the way the Patch 16 addresses do. Worth doing only
  together with H2, since a second generated header that nothing checks
  would drift the same way.

## Verification

- Both suites green and `compare_exports` byte-identical after H1: the
  header is addresses only, so any change in output would mean a name in
  use had moved, which the measurement says has not happened.
- `sh build/check_drivers.sh` still reproduces both drivers.
- After H2, the check fails on a deliberately edited header and passes
  on the regenerated one.

## Out of scope

- Renaming anything in `rmtplayr.a65`. The label renames that caused the
  drift are already done and are an improvement; this plan only catches
  the header up with them.
