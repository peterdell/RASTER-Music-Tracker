#pragma once

// The twelve semitones of the 2:1 octave - the one place the 12 is defined
// (mirrors Java's Tuning.NOTES_PER_OCTAVE). After plan 59 retired the presets
// that do not fit (step 1), every selectable temperament has this many notes.
constexpr int NOTES_PER_OCTAVE = 12;

// The musical cents of the 2:1 octave (one equal-tempered semitone is
// CENTS_PER_OCTAVE / NOTES_PER_OCTAVE = 100): a multiplier m is
// CENTS_PER_OCTAVE * log2(m) cents (mirrors Java's Tuning.CENTS_PER_OCTAVE).
constexpr int CENTS_PER_OCTAVE = 1200;

// The twelve tonic-based values of the custom temperament, in musical cents
// (1 cent = 1/1200 octave, 100 cents = one equal-tempered semitone).
// values[0] is the tonic and always 0; the octave is fixed at 1200 (2:1) and
// not stored. Replaces the former TTuningRatios fractions (plan 59, decided
// with ivop 2026-10-26): cents are the notation of the musicological
// literature, and not every temperament is expressible as a fraction. A
// multiplier m converts as cents = 1200*log2(m); the table generation
// applies 2^(cents/1200). Mirrored by Java's TuningCents.
struct TTuningCents {
    static const int COUNT = NOTES_PER_OCTAVE; // one cents value per semitone

    // cents above the tonic, by semitone; values[0] stays 0
    double values[COUNT];

    void Initialize();
};

struct TTuningSettings {

    // The default A-4 reference pitch, RMT's traditional tuning rather than
    // concert pitch 440 Hz: the Patch 16 player's shipped frequency tables
    // were generated with exactly this value.
    static constexpr double PAL_A4_FREQUENCY = 440.83751645933;

    // The PAL default scaled by exactly the machine-clock ratio - the same
    // conversion CRmtView::SetNTSC() applies - so the two defaults generate
    // bit-identical frequency tables by construction (444.8957788679134,
    // which both config writers' 16 significant digits round-trip; a decimal
    // literal here was truncated once). Defined in TuningTypes.cpp, where
    // CAtari's clock constants are visible.
    static const double NTSC_A4_FREQUENCY;

    double a4Frequency;
    int tonic; // 3 = A-
    int temperament = 0; // each preset is assigned to a number. 0 means no Temperament, any value that is not assigned defaults to custom
    TTuningCents cents; // the cents the custom temperament is played from - part of the tuning settings, not a sibling of them

    void Initialize(bool ntsc);
};
