# Plan 59: Tuning by key - fix the temperament definition, logic and dialog

Status: IN PROGRESS (2026-10-27) - step 1 is committed (23ecea8); step 2
is defined below: the base tuning field becomes the A4 frequency, with
the tonic frequency derived from it inside the table generation
(ivop's new poc, `others/Ivop/tuning_poc2.c`, 2026-10-27).
Earlier history: ivop's feedback arrived (see
others/Ivop/tuning_poc.md, "Continued conversation"): decisions 2 and 3
are settled - drop all seven non-fitting presets (pentatonic and Aeolic
are scales, not temperaments, so they are dropped rather than mapped),
and the dialog becomes key-only. The user overruled one part of ivop's
recommendation the same day: the custom tuning stays, redefined to store
the 12 tonic-based multipliers directly as doubles instead of fractions
(see open decision 3 below). A transposition field or a transpose-song
editor dialog can come later. All decisions were settled; K1 then FALSIFIED the premise (2026-10-26):
the current getTruePitch() produces exactly ivop's poc output for Thomas
Young no.1 in key A AND key C (verified to every displayed digit; probe
in out/k1, poc as reference). BaseNote's numbers DESCEND (C=0, B=1,
A#=2, A=3), so (semitone + basenote) % 12 is precisely the distance
above the tonic - the tonic reads ratio[0] in every key, and
octave^((s+b) div 12) anchors the tonic at the base frequency. The
thread's analysis assumed basenote B = 2. The key parameter works today;
the "out of tune in every key but A" bug does not exist. Implementation
was then rescoped: the user chose the simplification subset ("step 1"),
DONE 2026-10-26 in both programs - the seven non-fitting presets are
retired (their numbers stay reserved; persisted selections fall back to
equal in both programs), and the custom temperament stores 12
tonic-based cents doubles instead of the 13 fractions (dialogs reworked,
Tuning.ini migrates old fraction lines via 1200*log2(n/d), the custom
display text is "Custom Temperament in Cents"). The internals rewrite
(MIDI table, uniform rows) stays skipped - the key handling was proven
correct; the documentation repairs (rmtdata.a65's wrong "(C-)" aside,
the semitone/note naming confusion) remain open as a possible follow-up.
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
      rows; drop the seven non-fitting presets (ivop, 2026-10-26: the five
      incompatible ones, plus pentatonic and Aeolic - scales, not
      temperaments); redefine the custom temperament to 12 tonic-based
      cents doubles (tonic 0, octave 1200), persisted in Tuning.ini as
      ASCII doubles, with existing fractions migrated via
      1200*log2(n/d); adjust the `Temperament` value set and
      `ValueSets.properties`; a persisted dropped preset falls back to
      equal, with a changelog note.
- [ ] K3. Core logic, C++ and Java in lockstep (`Tuning.cpp`,
      `Tuning.java`): MIDI frequency table from tonic + multipliers,
      lookup replacing `getTruePitch()`, divider conversion in the copy
      step, transposition as copy offset; `basenote` splits into key and
      transposition; the C- notation offset 3 becomes one named, documented
      constant.
- [ ] K4. Dialog (`TuningDialog.java` and the C++ tuning dialog): the
      current "Base Note" field becomes Key (tonic); no transposition
      field for now. The 13 RA/TIO fraction pairs become 12 multiplier
      cents fields holding doubles (tonic 0 read-only, octave fixed at
      1200), enabled
      for the custom temperament only; existing custom fractions migrate
      to their evaluated double values (user's overrule of ivop's
      leave-it-out recommendation, 2026-10-26).
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

## Step 2: the base tuning field becomes the A4 frequency (2026-10-27)

Decided by the user 2026-10-27, prompted by ivop's new poc version
(`others/Ivop/tuning_poc2.c`; thread print
`others/Ivop/RMT Tuning - AtariAge Forums.pdf`), which adds `-a`: specify
the tonic note plus the frequency of **A4** instead of the tonic's own
frequency, and derive the tonic frequency through the temperament.

**The finding**: today's "Base tuning (Hz)" field is mathematically the
frequency of the *base note* (the formula anchors the index where
`(semitone + basenote) % 12 == 0`, the unison), but everything about it
says VinsCool meant the A4 reference pitch: the dialog's example values
(440, 432, 443.9, 440.83751645933, 444.895778867913) are all historical
or POKEY-friendly A4 pitches, his parameter comment reads "usually the
A-4 note" (`Tuning.cpp`), and the Patch-16 history says "base tuning
(440hz currently)". The two readings coincide only while the base note
is A - the default, which is why the difference was never noticed. With
a non-A key, the user today has to compute the tonic frequency by hand
(Kirnberger III in C at concert pitch = enter 263.18).

**The design**: keep exactly the two existing fields - no radio button,
no second entry style (ivop's old-style `-f` stays unexposed):

- The field means the **frequency of A4**, always. Rename its label to
  say so (`DataTypes.properties`, `Rmt.rc`); the example text already
  fits.
- Inside the table generation (`InitTuning()` in both programs, in
  lockstep), derive the tonic frequency from A4 with ivop's
  `calculate_base_frequency()`: normalize the tonic into the octave
  at/below A4 (remembering the power-of-two correction - octaves are
  exactly 2:1, so the tonic's octave is irrelevant), take the
  temperament's multiplier for the tonic-to-A4 interval,
  `tonic = A4 / m[interval]`, correction back on. Switching key or
  temperament thereby re-derives the tonic with A4 held fixed - the
  recomputation lives in the model, invisible to the dialog.

**Behavior changes** (both by design, changelog-worthy):

1. Non-A key under a non-equal temperament: the absolute pitch changes -
   previously the entered number silently became the tonic's frequency.
2. Equal temperament: the base note stops transposing the instrument
   (equal is key-invariant, so with A4 fixed the key is audibly a
   no-op). Today `basenote` acts as a global transposition there; a
   saved non-A equal-temperament setup changes pitch. A dedicated
   transpose feature stays a possible later complement (open decision 4).

For the default base note A nothing changes at all (`m[0] = 1`), which
covers virtually every existing module and Tuning.ini.

**Steps**:

- [ ] A1. Confirm the intent with VinsCool in the AtariAge conversation
      (expected yes; the K1 probe numbers and the field's example values
      make the case).
- [ ] A2. Core, lockstep: the A4-to-tonic derivation in
      `TuningTables.cpp`/`Tuning.java` per `calculate_base_frequency()`;
      the C- notation offset and the descending BaseNote numbers are
      already correct (K1) and stay.
- [ ] A3. Dialog texts: relabel "Base tuning (Hz)" as the A4 frequency
      in both programs; no field or layout changes.
- [ ] A4. Tests: poc2-verified goldens (Kirnberger III, key C, A4 440 ->
      tonic 263.18...; the three `-b 48/60/72` invocations produce the
      same table, i.e. the tonic's octave is irrelevant); base note A
      stays bit-identical to step 1's output; C++/Java parity.
- [ ] A5. Changelog (Drivers and Tuning) covering both behavior changes;
      Tuning.ini keeps its format (`BASETUNING` keeps its name, the
      meaning change is documented).

## Step 3: consistent temperament names and ordering (2026-10-27)

Requested by the user 2026-10-27: the texts are inconsistent (some start
with a number, some with a name) and so is the sorting; the custom
temperament must always be the last entry. IMPLEMENTED 2026-10-27 in
both programs: `ValueSets.properties` carries the names below;
`TuningDialog.temperamentsInDisplayOrder()` pins Equal first and Custom
last and sorts the 21 presets by display text (Collator, é as e); C++'s
`TEMPERAMENT_NAMES`/`TEMPERAMENT_NUMBERS` are hard-coded to the same
order, pinned cross-program by
`OptionsDialogsTest.theTemperamentComboOrderIsPinnedAndMatchesTheCpp`.
The data-row comments in `Tuning.java`/`Tuning.h` keep the verbatim
Scala description lines as the source record, with a pointer comment
above each table; changelog entry Build 2026-10-27.

**The finding**: the 21 preset texts are verbatim *description lines*
from the Scala scale archive (huygens-fokker.org/docs/scalesdir.txt),
inherited through VinsCool's POKEY Frequencies Calculator - they were
never designed as names, which is why they read like prose ("This Scale
May Also be Called the..."). Every preset was located in the archive;
the most notable find: "Bach Temperament, A- = 400hz" is
`sparschuh-squiggle_clavichord.scl` - Andreas Sparschuh's "squiggle"
Bach reconstruction, hence the odd 400 Hz, not a temperament by Bach.

All 21 rows were verified NUMERICALLY against the archive (2026-10-27,
user's suggestion): each `TEMPERAMENT_PRESETS` row equals its .scl
file's degrees converted to multipliers and rounded to 6 decimals (max
difference < 1e-6 everywhere; script kept in the job tmp folder, the
archive is https://www.huygens-fokker.org/docs/scales.zip). So every
grounding below is exact, including Zarlino.

**Naming convention** (applies to `ValueSets.properties` and C++
`TuningDialog.cpp`'s `TEMPERAMENT_NAMES`, in lockstep):

1. The name leads: person or scale name first, never a leading number
   or an article of prose.
2. The kind follows (Well Temperament / Temperament / Scale as the
   source calls it); the year last, in parentheses.
3. No possessives or description phrasing ("Thomas Young 1799's",
   "Made After Analysis of", "Also Known as").
4. Typography: "No. 1" (not "no.1"), "A4 = 400 Hz" (not "A- = 400hz"),
   Title Case, French accents kept (é).

**Proposed texts** (number: current -> proposed, grounding):

| # | Proposed | Grounding (Scala file / Wikipedia) |
|---|----------|------------------------------------|
| 0 | Equal Temperament (Default) | unchanged |
| 1 | Young Well Temperament No. 1 (1800) | young1.scl "Thomas Young well temperament no.1 (1800), 1/12 and 3/16 synt. comma" - verified numerically; Wikipedia: described in Young's July 1799 letter, read/published 1800 |
| 2 | Young Well Temperament No. 2 (1799) | young2.scl "Thomas Young well temperament no.2 (1799)" - verified numerically |
| 3 | Young Well Temperament (1807) | young.scl: "(1807), also Luigi Malerbi nr.2 (1794)" |
| 4 | Werckmeister III Well Temperament (1691) | werck3.scl says 1681; Wikipedia: Musikalische Temperatur is 1691 - propose 1691 |
| 6 | d'Alembert-Rousseau Tempérament Ordinaire (1752/1767) | alembert-rousseau.scl |
| 7 | Aron-Neidhardt Equal Beating Well Temperament | aron-neidhardt.scl |
| 8 | Atom Schisma Scale | atomschis.scl, unchanged |
| 9 | Equal Temperament Approximation (Minimal Order 17 Beats) | b12_17.scl "12-tET approximation with minimal order 17 beats" - the number moves out of first position, and it sorts next to Equal Temperament |
| 10 | Bailey Modern Well Temperament (2002) | bailey_well2.scl "Paul Bailey's modern well temperament (2002)" |
| 11 | Barnes Well-Tempered Clavier Temperament (1977) | barnes.scl "...made after analysis of Wohltemperierte Klavier, 1/6 P" |
| 12 | Béthizy Tempérament Ordinaire (1754) | bethizy.scl "Jean Laurent de Béthizy temperament ordinaire (1754)" - note the z, and the year was missing |
| 13 | Big Gulp | biggulp.scl, unchanged |
| 15 | Wedding Cake | pyramid.scl "This scale may also be called the 'Wedding Cake'" |
| 16 | Upside-Down Wedding Cake (Divorce Cake) | pyramid_down.scl |
| 17 | Pythagorean Scale | pyth_12.scl "12-tone Pythagorean scale" - "12 Tone" dropped, all presets are 12-tone |
| 18 | Schneider Logarithmic Scale, log(4) to log(16) | schneider_log.scl "Robert Schneider, scale of log(4) .. log(16), 1/1=264Hz" |
| 19 | Zarlino Tempérament Extraordinaire (Schulter) | schulter_zarte84n.scl "Zarlino temperament extraordinaire, 1024-tET mapping" - verified numerically; Margo Schulter's 12-note temperament extraordinaire on Zarlino's 2/7-comma meantone (Tuning.java's row comment even names the 1024-tET mapping), not a tuning by Zarlino himself, hence the attribution |
| 20 | Fokker 7-Limit Just Scale | fokker_12.scl "Fokker's 7-limit 12-tone just scale" |
| 21 | Bach Temperament by Sparschuh (A4 = 400 Hz) | sparschuh-squiggle_clavichord.scl "Bach temperament, a'=400 Hz" - the attribution was missing entirely |
| 22 | Vallotti Temperament (1754) | vallotti.scl "Vallotti & Young scale (Vallotti version) also known as Tartini-Vallotti (1754)"; Wikipedia: "Vallotti" is the common name |
| 23 | Vallotti-Young/Werckmeister III Lesfip Scale (5-Limit, 10 Cents) | val-werck.scl |
| 29 | Custom Temperament in Cents | unchanged, always the last entry |

**Ordering** (user, 2026-10-27): Equal Temperament (Default) is pinned
as the first entry, Custom Temperament in Cents as the last; the 21
presets in between are alphabetical by display text, case-insensitive
(the standing rule for RMT's value lists). The order is display-only -
the stored numbers
and Tuning.ini stay untouched; Java keeps `Temperament.getValues()` in
number order and the dialogs sort for display, C++ sorts
`TEMPERAMENT_NAMES`/`TEMPERAMENT_NUMBERS` jointly.

**Sub-decisions, all resolved 2026-10-27 (user)**: Werckmeister III
carries 1691 (Wikipedia's Musikalische Temperatur date; Scala's 1681 is
not corroborated); the spelling is Béthizy with z, per the source file.
Resolved earlier the same day by the numeric verification: Young No. 1
carries 1800 and No. 2 carries 1799, per their verified source files;
Zarlino is Schulter's schulter_zarte84n.scl; Equal pinned first.

## Open decisions

1. RESOLVED 2026-10-26 (user): lockstep - the fix lands in Tuning.cpp and
   Tuning.java identically while both programs ship.
2. RESOLVED 2026-10-26 (ivop): drop all seven non-fitting presets -
   the five incompatible ones, and pentatonic/Aeolic too, because they
   are scales (note selections), not temperaments.
3. RESOLVED 2026-10-26 (ivop, amended by the user's overrule the same
   day): key only, no transposition field (that part stands - a
   transpose field or transpose-song dialog can come later). But the
   custom tuning STAYS, redefined (user, final): it stores 12
   tonic-based values in MUSICAL CENTS as doubles (tonic fixed at 0,
   octave fixed at 1200 = 2:1), the notation of the musicological
   literature (Kirnberger III: 0, 90.225, 193.157, ...; equal is 0, 100,
   ..., 1100), converted to multipliers by 2^(cents/1200) at table
   generation. The dialog's 13 fraction pairs become 12 cents fields
   (the first read-only 0); Tuning.ini persists the doubles as ASCII;
   existing custom fractions migrate via 1200*log2(numerator/denominator).
4. **Retune vs transpose semantics**: resolved by 3 - the table is
   *retuned* in the key; the transpose-all-patterns editor command
   (skipping percussion/fixed-frequency instruments via an instrument
   flag) stays a possible later complement, out of scope here.

## References

- `others/Ivop/tuning_poc.md` (full conversation extract),
  `others/Ivop/tuning_poc.c` (the poc), `others/Ivop/tuning_poc2.c`
  (poc with the `-a` A4-frequency option and
  `calculate_base_frequency()`), `others/Ivop/RMT Tuning - AtariAge
  Forums.pdf` (thread print, 2026-10-27), all local and untracked.
- https://www.dolmetsch.com/musictheory27t.htm (ivop's cross-check:
  always tunes the key of C to a given A4 pitch; its Kirnberger III
  (1776) (IT & D 12.04 (DH)) matches poc `-t 1`).
- `src/cpp/Tuning.cpp`/`.h`, `src/java/org/atari/raster/rmt/model/Tuning.java`,
  `TuningSettings.java`, `src/java/org/atari/raster/rmt/ui/TuningDialog.java`.
- plans/37_TUNING_COMPATIBILITY_PLAN.md (tuning table compatibility
  groundwork), plans/28_VALUE_SETS_PLAN.md (value-set rules for the
  `Temperament`/`BaseNote` lists).
