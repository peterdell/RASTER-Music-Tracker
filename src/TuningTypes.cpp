#include "TuningTypes.h"

void TTuningSettings::Initialize(bool ntsc) {
    *this = { ntsc ? 444.895778867913 : 440.83751645933,3,0 };
}

void TTuningRatios::Initialize() {
    *this = { {1,1},{40,38},{10,9},{20,17},{5,4},{4,3},{60,43},{3,2},{30,19},{5,3},{30,17},{15,8},{2,1}
    };
}