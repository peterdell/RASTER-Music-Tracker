# Plan 59: Tuning by key - fix the temperament definition, logic and dialog

Status: PAUSED (2026-10-25) - awaiting ivop's feedback before the open
decisions are settled and implementation starts. The K2 preset inventory
is already done (see "Preset inventory" below).
Direction decided in the AtariAge conversation
"RMT Tuning" (JAC!, ivop, VinsCool, 2026-10-15 to 10-23; prints, ivop's
proof of concept `tuning_poc.c` and the full extract `tuning_poc.md` are
archived locally in the untracked `others/Ivop/` folder).

## The problem

RMT's non-equal temperaments have no key: the ratios are always generated
relative to A, whatever key the tune is in. Just-intonation ratios must be
relative to the **tonic of the key** - E relative to A is a fifth, but in
the key of C it is a major third; well-tempered that is the same frequency,
in a just intonation it is not. So every preset temperament is out of tune
for any key but A.

The faulty spot, identical in both programs (`CTuning::GetTruePitch`,
`src/cpp/Tuning.cpp:268`; `Tuning.getTruePitch`,
`src/java/org/atari/raster/rmt/model/Tuning.java:183`):

```java
note = (semitone + basenote) % notesnum;
ratio = TEMPERAMENT_PRESETS[temperament][note];
```

`basenote` here acts as a *transposition* (a semitone shift), not as a
*key*: in the key of B the tonic must read ratio index 0, but with
basenote B = 2 it reads index 2. Equal temperament is key-invariant, which
is why the bug went unnoticed - VinsCool (the author) only ever used equal
temperament with key transposition, and tested other temperaments in the
key of A. Two further warts: `semitone`/`note` mean different things in
`generateTable()` than in `getTruePitch()`, and the hardcoded offset 3
(pattern notation starts at C-, the tuning is derived from A-) is buried in
the calculation.

## The design (ivop's proof of concept)

1. **Uniform temperament data**: every temperament is a row of 12
   multipliers relative to the tonic (plus the 2:1 octave, which is fixed -
   stretched octaves are deliberately unsupported). Equal temperament is
   not special-cased; its row is 2^(n/12) for n = 0..11. Presets with more
   than 12 notes per octave are dropped; the pentatonic scale's multipliers
   map to their proper places in a row of 12 (other entries zero).
2. **One frequency table, computed once**: from the tonic frequency and the
   12 multipliers, calculate the full frequency table for all MIDI note
   numbers. The MIDI octave digit is only meaningful when the tonic is C.
3. **Lookup instead of calculation**: `getTruePitch()` is replaced by a
   lookup into that table. The POKEY note tables are filled by *copying*
   the needed range and converting each pitch to the POKEY divider with the
   existing per-distortion algorithms.
4. **Transposition is a copy offset only** - it no longer participates in
   the frequency math. Today's instant, file-untouched transposition
   behavior is preserved (A- is technically offset 3 plus the default, as
   today); the known caveat stays that a transposed table makes pattern
   note names differ from the sounding pitch.
5. **Two separate parameters**: Key (tonic - which ratio each note gets;
   irrelevant for equal temperament) and Transposition (the shift that
   `basenote` implements today).

`others/Ivop/tuning_poc.c` implements 1-3 (functions above its `======`
line; three temperament input forms: 12 multipliers, cents 0-1199, or a
shift matrix).

## The three scale notations (goes into the tests' documentation)

The poc accepts a scale in three notations, which are the same 12 numbers
in different units; it converts them all to one canonical form before any
frequency is computed (shift matrix -> temperament -> tuning ->
frequencies):

1. **Tuning - multipliers** (the canonical form the engine consumes): 12
   frequency multipliers relative to the tonic, `m[0] = 1.0` to
   `m[11] < 2.0`; note n of the tonic's octave is `tonic * m[n]`, other
   octaves are powers of 2 of that. Just-intonation scales are naturally
   written this way as fractions (Pythagorean `9/8`, `4/3`, `3/2`, ...;
   the two Pythagorean variants differ only at index 6, the tritone as
   `1024/729` diminished fifth vs `729/512` augmented fourth - the "wolf"
   placement). RMT's custom RATIO fields are this form as fractions.
2. **Temperament - absolute cents**: the same scale logarithmically, one
   cent = 1/1200 octave, 100 cents = one equal semitone; `cents[n]` is the
   distance from the tonic, converted by `m = 2^(cents/1200)`. Equal
   temperament is exactly 0, 100, ..., 1100. Musicological references
   specify historical temperaments in this form (Kirnberger III:
   0, 90.225, 193.157, ...).
3. **Shift matrix - cents as deviation from equal**:
   `cents[n] = n*100 + shift[n]`. Equal temperament is all zeros; modern
   sources and synthesizer manuals publish historic temperaments this way
   (Thomas Young 1800: C# +6.0, D -2.0, ...), the most human-readable form.

All three lists in the poc contain an "Equal Temperament" entry, which
makes the equivalence testable: `{1.0, 1.0595...}`, `{0, 100, ..., 1100}`
and `{0, 0, ..., 0}` must produce the identical frequency table.

## Preset inventory (K2 fact-finding, done 2026-10-25)

Of the 28 presets in `TEMPERAMENT_PRESETS` (index 0 is the empty
"No Temperament" slot), seven do not fit the 12-multiplier/2:1-octave
model:

- **More than 12 notes**: 19-EDO (19 notes).
- **Octave not 2:1**: Bohlen 4:7:10 (octave 3, a tritave), African Bapare
  xylophone (2.7384), African Yaswa xylophones (3.22098), Temperament
  Egal a Quintes Justes (2.003875).
- **Fewer than 12 notes, 2:1 octave - mappable per the poc**: Optimally
  consonant major pentatonic (5 notes -> 12-TET slots 0, 2, 4, 7, 9) and
  Ancient Greek Aeolic (7 notes -> slots 0, 2, 3, 5, 7, 8, 10, the minor
  scale).

The remaining 21 presets are plain 12-note, 2:1-octave rows. Persistence:
`Tuning.ini` (`BASENOTE` stored as number, C = 0 counting downward, A = 3 -
the number that doubles as the C-notation offset 3; read/written by
`RmtView.cpp` and `RmtConfig.java`).

## Steps

- [ ] K1. Characterization: pin today's generated tables in both programs
      (equal temperament must come out bit-identical after the fix; the
      non-equal presets change *by design* for keys other than A - capture
      before/after examples for the commit message and changelog).
- [ ] K2. Data model: convert `TEMPERAMENT_PRESETS` to uniform 12-multiplier
      rows; inventory which of the 29 presets exceed 12 notes and drop them;
      map the pentatonic preset; adjust the `Temperament` value set and
      `ValueSets.properties`; migrate persisted temperament selections
      (rmt.ini/Tuning.ini and the module's tuning block).
- [ ] K3. Core logic, C++ and Java in lockstep (`Tuning.cpp`,
      `Tuning.java`): MIDI frequency table from tonic + multipliers,
      lookup replacing `getTruePitch()`, divider conversion in the copy
      step, transposition as copy offset; `basenote` splits into key and
      transposition; the C- notation offset 3 becomes one named, documented
      constant.
- [ ] K4. Dialog (`TuningDialog.java` and the C++ tuning dialog): the
      current "Base Note" field becomes Key (tonic), with transposition
      exposed separately (open decision 3); ratio fields stay enabled for
      the custom temperament only (already done, Build 2026-10-17).
- [ ] K5. Tests: port `tuning_poc.c` to Java as the tests' reference
      implementation (a test-source class, e.g. `TuningReference`, with
      the poc's data and conversions and the "three scale notations"
      explanation above as its documentation). The tests assert:
      the three notations of Equal Temperament produce the identical
      table; RMT's table generation matches the reference for every
      preset; ivop's key-of-A vs key-of-C index example and the E
      fifth-vs-third case (Kirnberger III, tonic A: E4 = 328.98 Hz,
      tonic C: E4 = 327.04 Hz, verified with the compiled poc); the
      equal-temperament output stays bit-identical to today's; pentatonic
      mapping; octave 2:1; and C++/Java parity.
- [ ] K6. Changelog (Drivers and Tuning), documentation, and a reply in the
      AtariAge conversation.

## Open decisions

1. **C++ scope**: C++ is being phased out, but tuning output feeds the
   cross-program equality tests, so while both programs live the fix must
   land in both - confirm, or decide to freeze the C++ tuning (and exclude
   it from the affected comparisons) instead.
2. **Dropped presets**: which of the 29 presets have more than 12 notes,
   and is dropping them acceptable to users (K2 inventories them first).
3. **Dialog shape**: whether Transposition gets its own field in the tuning
   dialog or stays where today's base-note shift is used; the 13th custom
   ratio pair (the octave) in light of the fixed 2:1 octave; VinsCool's
   idea of showing the decimal value beside each fraction, or allowing a
   fixed value to override a fraction (optional, may be deferred).
4. **Retune vs transpose semantics**: the table is *retuned* in the key
   (note names keep their meaning); a possible later complement is a
   transpose-all-patterns command that skips percussion/fixed-frequency
   instruments via an instrument flag (ivop's suggestion, out of scope
   here).

## References

- `others/Ivop/tuning_poc.md` (full conversation extract),
  `others/Ivop/tuning_poc.c` (the poc), both local and untracked.
- `src/cpp/Tuning.cpp`/`.h`, `src/java/org/atari/raster/rmt/model/Tuning.java`,
  `TuningSettings.java`, `src/java/org/atari/raster/rmt/ui/TuningDialog.java`.
- plans/37_TUNING_COMPATIBILITY_PLAN.md (tuning table compatibility
  groundwork), plans/28_VALUE_SETS_PLAN.md (value-set rules for the
  `Temperament`/`BaseNote` lists).
