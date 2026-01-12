#pragma once

#include "Fraction.h"

struct TTuningSettings {

    double basetuning;
    int basenote;	// 3 = A-
    int temperament = 0;	// each preset is assigned to a number. 0 means no Temperament, any value that is not assigned defaults to custom

    void Initialize(bool ntsc);

};



// ratio used for each note => NOTE_L / NOTE_R, must be treated as doubles!!!
struct TTuningRatios {
    CFraction UNISON;
    CFraction MIN_2ND;
    CFraction MAJ_2ND;
    CFraction MIN_3RD;
    CFraction MAJ_3RD;
    CFraction PERF_4TH;
    CFraction TRITONE;
    CFraction PERF_5TH;
    CFraction MIN_6TH;
    CFraction MAJ_6TH;
    CFraction MIN_7TH;
    CFraction MAJ_7TH;
    CFraction OCTAVE;

    void Initialize();
};


