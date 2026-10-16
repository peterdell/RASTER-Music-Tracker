#pragma once

#include "IntegerFraction.h"

// ratio used for each note => NOTE_L / NOTE_R, must be treated as doubles!!!
struct TTuningRatios {
    CIntegerFraction UNISON;
    CIntegerFraction MIN_2ND;
    CIntegerFraction MAJ_2ND;
    CIntegerFraction MIN_3RD;
    CIntegerFraction MAJ_3RD;
    CIntegerFraction PERF_4TH;
    CIntegerFraction TRITONE;
    CIntegerFraction PERF_5TH;
    CIntegerFraction MIN_6TH;
    CIntegerFraction MAJ_6TH;
    CIntegerFraction MIN_7TH;
    CIntegerFraction MAJ_7TH;
    CIntegerFraction OCTAVE;

    void Initialize();
};

struct TTuningSettings {

    double basetuning;
    int basenote; // 3 = A-
    int temperament = 0; // each preset is assigned to a number. 0 means no Temperament, any value that is not assigned defaults to custom
    TTuningRatios ratios; // the interval ratios the custom temperament is played from - part of the tuning settings, not a sibling of them

    void Initialize(bool ntsc);
};
