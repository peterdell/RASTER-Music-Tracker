# Plan 62: The tuning fields are named for what they mean

Status: DONE 2026-10-29. Follows plan 59 (closed), which sharpened
and fixed the meaning of the tuning fields; this plan makes the names say
it. Decided with the user the same day, both programs.

## Why

Since plan 59 the three tuning concepts have one meaning each, but the
names still describe their history:

- The field `basetuning` is the A4 frequency for every key (plan 59 step 2);
  only its label says so ("A4 Frequency (Hz)"). The Tuning.ini key is
  `TUNING`.
- The "Base Note" is the tonic: the key the tables are tuned to, the note
  that receives the temperament's 0 cents. The Tuning.ini key is
  `BASENOTE`.
- The first cents row, "Unison", is that tonic itself - always 0 cents.

Renaming "Base Note" to "Tonic" and "Unison" to "Tonic" makes visible that
both choose the same thing: the key the tables are tuned to.

## Decisions (user, 2026-10-29)

1. **`basetuning` becomes the A4 frequency** everywhere: Java
   `TuningSettings.a4Frequency` with `MIN_A4_FREQUENCY`/`MAX_A4_FREQUENCY`,
   C++ `TTuningSettings::a4Frequency`, the data type
   `TuningDialog_A4Frequency`, the C++ control `IDC_A4_FREQUENCY`, the
   Tuning.ini key `A4_FREQUENCY`.
2. **`calculateBaseFrequency()` is not the A4 frequency**: it computes the
   tonic's anchor frequency from it (the two coincide only for the tonic
   A). It becomes `calculateTonicFrequency()` (C++
   `CalculateTonicFrequency()`), its result the tonic frequency.
3. **"Base Note" becomes "Tonic"**: the value set `BaseNote` becomes
   `Tonic` (texts `Tonic_*` in `ValueSets.properties`), the field
   `basenote` becomes `tonic`, the label "&Tonic", the C++ control
   `IDC_TONIC`, the Tuning.ini key `TONIC`.
4. **"Unison" becomes "Tonic"** as the first cents row's label (C++
   control `IDC_TONIC_L`). In Tuning.ini the key `TONIC` now means the key
   (decision 3), so the row gets no key at all: its value is always 0, it
   is no longer written, and an old `UNISON` line is read and ignored - as
   the `OCTAVE` line is since plan 59.
5. **Reading the old names**: Tuning.ini (soon obsolete, the settings move
   into the module) still reads `TUNING` and `BASENOTE` as the A4
   frequency and the tonic; writing uses only the new names.
6. **Untouched**: "base note" in the instrument documentation
   (`rmt_en.md`: table mode, envelope commands) is the note an instrument
   plays - a different concept.
7. **The ratio fields' resource IDs** (added after S3, user): the 13
   `IDC_*_R` IDs (the retired fractions' denominators) and `IDC_OCTAVE_L`
   (the octave has been fixed since plan 59) were referenced nowhere and
   are removed; the 12 remaining cents fields drop the meaningless `_L`
   suffix and become `IDC_*_CENTS` (`IDC_TONIC_CENTS` ...) - a suffix is
   still needed, `IDC_TONIC` is the tonic combo.

## Steps

- [x] S1. Java (747 tests, one new: an old-format Tuning.ini still reads): the renames of decisions 1-4, `RmtConfig` reading both
      the new and the old names and writing the new ones, the tests
      (`RmtConfigTest` gets a case for an old-format file).
- [x] S2. C++ (468 tests): the same renames (`TuningTypes`, `Tuning`, `TuningTables`,
      `TuningDialog`, `PokeyView`, `RmtView`'s `ReadTuningConfig()`/
      `WriteTuningConfig()`, `ScriptRunner`, `Rmt.rc`, `resource.h`) and
      the tests.
- [x] S3. Texts: doc/rmt_tracker_drivers.md's Tuning section, the version
      2 proposal in doc/rmt_format.md, a changelog entry for the new
      labels and the Tuning.ini keys.
- [x] S4. Decision 7 in `resource.h`, `Rmt.rc` and `TuningDialog.cpp`;
      the Release build and 468 C++ tests green.

Proof: `mvn -o test`, the C++ Release build and `RmtTests.exe`, the
cross-program export comparison (unchanged output - a rename changes no
table) - all green 2026-10-29. The new label's mnemonic is "To&nic": T,
o and C are taken by &Test, &Temperament, &OK and &Cancel, and the n is free
since "Base &Note" is gone.
