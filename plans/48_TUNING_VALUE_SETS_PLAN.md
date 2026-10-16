# Plan 48: Temperament and BaseNote become value sets

## Context

The Java `TuningDialog` still carries four constants that are not dialog
details but tuning domain knowledge:

- `TEMPERAMENTS` - the 30 display names of `IDC_TEMPERAMENT`'s entries
  (Rmt.rc's DLGINIT), index = the stored `temperament` number (0 equal,
  1-28 the presets of `Tuning`, 29 custom).
- `BASE_NOTES` - the 12 display names of `IDC_BASENOTE`'s entries, index =
  the stored `basenote` number (3 = "A-" is the default).
- `BASE_TUNING_MIN` / `BASE_TUNING_MAX` - the 6.875-7040 range of
  `DDV_MinMaxDouble`.

The repository's convention for an enumeration whose values are shown to
the user is a WUDSN Base `ValueSet` (`AssemblerFormat`,
`TrackerDriverVersion`, `KeyboardLayout` - see plans/28_VALUE_SETS_PLAN.md):
display texts in `model/ValueSets.properties` keyed `<ClassName>_<id>`, the
C++ number as an explicit attribute (a value set has no `ordinal()`), the
dialog holding the instance through a `ValueSetField`, and `ValueSetsTest`
asserting every text. `Temperament` and `BaseNote` qualify; this plan
converts them and moves the two numeric limits into the model.

The combo order stays the number order, not alphabetical: these values
carry a natural order of their own (the stored number, which is also the
C++ combo index), the same exception `TrackerDriverVersion` already uses.

The C++ program is not changed: its dialog already holds the texts in the
IDD_TUNING DLGINIT resource and stores plain ints, and the phase-out rule
forbids C++-only rework. `OptionsDialogsTest.theComboEntriesMatchTheCppResources`
keeps pinning the Java texts against the C++ resource.

## Decisions

1. **Two new value sets in `org.atari.raster.rmt.model`**, built exactly
   like `TrackerDriverVersion` (instances in a static block over a
   `LinkedHashMap`, `getNumber()`, `getValues()`, `getInstance(String)`,
   `getInstance(int)`, `initializeClass(X.class, ValueSets.class)`):
   - `Temperament`, 30 instances, numbers 0-29.
   - `BaseNote`, 12 instances, numbers 0-11.
2. **`TuningSettings` holds the instances**, not the ints (the pattern
   `Song.trackerDriverVersion` set): `BaseNote basenote` and
   `Temperament temperament` replace the two `int` fields;
   `initialize(boolean)` sets `BaseNote.A` and `Temperament.EQUAL`.
3. **The math layer keeps ints.** `Tuning.getTruePitch(double, int, int,
   int)`, `computeNotesPerOctave(int)` and the `NO_TEMPERAMENT`/
   `TUNING_PRESETS`/`TUNING_CUSTOM` constants are per-call computation on
   the backing number and stay as they are; callers pass
   `...getNumber()`. Only the *stored* value becomes an instance.
4. **`BASE_TUNING_MIN`/`BASE_TUNING_MAX` move to `TuningSettings`** - the
   class whose `basetuning` field they constrain - as
   `BASETUNING_MIN`/`BASETUNING_MAX`. The dialog validation reads them
   from there.
5. **Reading a number nobody owns** (`rmt.ini` can carry any int, C++
   indexes its arrays with it unchecked):
   - `Temperament`: a number >= 29 maps to `CUSTOM` - that is C++'s own
     semantic ("any value that is not assigned defaults to custom",
     `temperament >= TUNING_CUSTOM`). A negative number maps to `EQUAL`
     as a deliberate guard where C++ reads out of bounds.
   - `BaseNote`: an unknown number maps to the default `A` (3), a
     deliberate guard where C++ reads out of bounds. (The old dialog
     clamped the index; the clamp moves from the dialog to the read.)
   These mappings live in a static `getInstanceWithFallback(int)` (exact
   name free) on each class so `RmtConfig` stays a one-liner per key.

### The instances

`Temperament` ids and numbers (texts verbatim from today's `TEMPERAMENTS`
array, which matches the C++ DLGINIT):

| # | Id | Text |
|---|----|------|
| 0 | `EQUAL` | Equal Temperament (Default) |
| 1 | `YOUNG_1799_NO1` | Thomas Young 1799's Well Temperament no.1 |
| 2 | `YOUNG_1799_NO2` | Thomas Young 1799's Well Temperament no.2 |
| 3 | `YOUNG_1807` | Thomas Young 1807's Well Temperament |
| 4 | `WERCKMEISTER_III` | Andreas Werckmeister's Temperament III (1681) |
| 5 | `EGAL_QUINTES_JUSTES` | Tempérament Égal a Quintes Justes |
| 6 | `ALEMBERT_ROUSSEAU` | d'Alembert and Rousseau Tempérament Ordinaire (1752/1767) |
| 7 | `ARON_NEIDHARDT` | Aron - Neidhardt Equal Beating Well Temperament |
| 8 | `ATOM_SCHISMA` | Atom Schisma Scale |
| 9 | `TET12_MINIMAL_ORDER_17` | 12-TET Approximation with Minimal Order 17 Beats |
| 10 | `BAILEY` | Paul Bailey's Modern Well Temperament (2002) |
| 11 | `BARNES` | John Barnes' Temperament (1977) Made After Analysis of Wohltemperierte Klavier |
| 12 | `BETHISY` | Bethisy Tempérament Ordinaire |
| 13 | `BIG_GULP` | Big Gulp |
| 14 | `BOHLEN` | 12 Tone Scale by Bohlen Generated from the 4:7 : 10 Triad, Acustica 39/2/1978 |
| 15 | `WEDDING_CAKE` | This Scale May Also be Called the "Wedding Cake" |
| 16 | `UPSIDE_DOWN_WEDDING_CAKE` | Upside Down Wedding Cake (Divorce Cake) |
| 17 | `PYTHAGOREAN` | 12 Tone Pythagorean Scale |
| 18 | `SCHNEIDER` | Robert Schneider, Scale of Log(4) ..Log(16) |
| 19 | `ZARLINO` | Zarlino Tempérament Extraordinaire |
| 20 | `FOKKER` | Fokker's 7-Limit 12-Tone Just Scale |
| 21 | `BACH` | Bach Temperament, A- = 400hz |
| 22 | `VALLOTTI_YOUNG` | Vallotti & Young Scale (Vallotti Version), Also Known as Tartini-Vallotti (1754) |
| 23 | `VALLOTTI_YOUNG_WERCKMEISTER_III` | Vallotti-Young and Werckmeister III, 10 Cents 5-Limit Lesfip Scale |
| 24 | `DELAUBENFELS` | Optimally Consonant Major Pentatonic, John deLaubenfels (2001) |
| 25 | `AEOLIC` | Ancient Greek Aeolic, Also Tritriaic Scale of the 54:64 : 81 Triad |
| 26 | `BAPARE` | African Bapare Xylophone (Idiophone, Loose Log) |
| 27 | `YASWA` | African Yaswa Xylophone (Idiophone, Calbas Resonators with Membrane) |
| 28 | `EDO19` | 19-EDO Generated Using Scale Workshop |
| 29 | `CUSTOM` | Custom Temperament with RA/TIO |

`Temperament` also gets `isCustom()` (`this == CUSTOM`), which the tuning
dialog's ratio-field enablement reads instead of comparing combo indexes.

`BaseNote` ids and numbers (texts verbatim from today's `BASE_NOTES`):

| # | Id | Text | | # | Id | Text |
|---|----|------|-|---|----|------|
| 0 | `C` | C- | | 6 | `F_SHARP` | F# |
| 1 | `B` | B- | | 7 | `F` | F- |
| 2 | `A_SHARP` | A# | | 8 | `E` | E- |
| 3 | `A` | A- | | 9 | `D_SHARP` | D# |
| 4 | `G_SHARP` | G# | | 10 | `D` | D- |
| 5 | `G` | G- | | 11 | `C_SHARP` | C# |

## Steps

- **V1 - `Temperament.java` and `BaseNote.java`** in `model`, per the
  table above and the `TrackerDriverVersion` pattern. No
  `getSelectableValues()` - every value is offered.
- **V2 - `ValueSets.properties`**: a `# Temperaments (the tuning dialog's
  "Temperament" combo box ...)` block with the 30 keys
  `Temperament_<id>` and a `# Base notes ...` block with the 12 keys
  `BaseNote_<id>`, texts from the tables (the file is ISO-8859-1 as Java
  properties; "Tempérament"/"Égal" must survive - escape as `é` if
  the encoding check says so).
- **V3 - `TuningSettings`**: the two fields become instances (decision 2),
  the two limits move in (decision 4), `initialize` adapts.
- **V4 - consumers of the backing number** switch to `getNumber()`:
  - `Tuning.initTuning()`/`generate*` call sites (`tuningSettings.temperament`,
    `.basenote` at lines ~404 and ~498-500).
  - `PokeyView` (`tuningSettings.basenote`, line ~76).
  - `RmtConfig`: read `BASENOTE`/`TEMPERAMENT` through the fallback
    mappings (decision 5), write `getNumber()`.
- **V5 - `TuningDialog`**: `ValueSetField<BaseNote>` and
  `ValueSetField<Temperament>` replace the two `JComboBox<String>`;
  `dataToUi`/`dataFromUi` use `setValue`/`getValue` (the index clamps
  disappear - the model now always holds a valid instance);
  `updateRatioFieldsEnabled` reads `getValue().isCustom()`; the four
  constants are deleted. `DataTypes.TuningDialog_BaseNote`/`_Temperament`
  change from `DataType(String.class)` to the value set classes, as
  `ModulePropertiesDialog_TrackerDriverVersion` does. The labels in
  `DataTypes.properties` stay.
- **V6 - tests**:
  - `ValueSetsTest`: two new methods asserting all 30/12 texts are loaded
    and spot-checking first/default/last (`EQUAL`, `CUSTOM`, `A`).
  - `OptionsDialogsTest.theComboEntriesMatchTheCppResources`: asserts on
    `Temperament.getValues()`/`BaseNote.getValues()` instead of the
    deleted arrays (30 values, number = position, `CUSTOM` last, "A-" at
    number 3) - still the pin against the C++ DLGINIT entries.
  - `tuningDialogRoundTripsAndOnlyTestOrOKReachTheSession` and
    `tuningDialogEnablesTheRatioFieldsOnlyForTheCustomTemperament`: the
    combos now hold instances; selection by instance, not index.
  - `RmtConfigTest`: `TEMPERAMENT = 35` reads as `CUSTOM`, `-1` as
    `EQUAL`, `BASENOTE = 99` as `A`; a round trip keeps the numbers.

## Verification

1. `mvn -o test` - full Java suite green (ValueSetsTest guards every new
   text; a missing properties key is only logged by the library, never
   thrown).
2. The C++ program is untouched; no rebuild needed beyond confirming the
   working tree holds no C++ change.
3. `build/compare_exports.ps1` - unchanged exports (the tuning values
   flow through the same numbers).

## Out of scope

- The C++ side keeps its ints and DLGINIT texts (phase-out rule).
- The per-module persistence of the tuning (plan 44's "Open afterwards",
  RMT 2.0) is untouched - the values still live in `rmt.ini`.
- `TuningRatios`/`IntegerFraction` are not value sets (free-form values,
  not an enumeration) and stay as they are.
