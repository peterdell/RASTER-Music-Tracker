#pragma once

// The twelve tonic-based values of the custom temperament, in musical cents
// (1 cent = 1/1200 octave, 100 cents = one equal-tempered semitone).
// values[0] is the tonic and always 0; the octave is fixed at 1200 (2:1) and
// not stored. Replaces the former TTuningRatios fractions (plan 59, decided
// with ivop 2026-10-26): cents are the notation of the musicological
// literature, and not every temperament is expressible as a fraction. A
// multiplier m converts as cents = 1200*log2(m); the table generation
// applies 2^(cents/1200). Mirrored by Java's TuningCents.
struct TTuningCents {
    static const int COUNT = 12;

    // cents above the tonic, by semitone; values[0] stays 0
    double values[COUNT];

    void Initialize();
};

struct TTuningSettings {

    double basetuning;
    int basenote; // 3 = A-
    int temperament = 0; // each preset is assigned to a number. 0 means no Temperament, any value that is not assigned defaults to custom
    TTuningCents cents; // the cents the custom temperament is played from - part of the tuning settings, not a sibling of them

    void Initialize(bool ntsc);
};
