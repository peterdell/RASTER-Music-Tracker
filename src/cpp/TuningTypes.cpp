#include "TuningTypes.h"

void TTuningSettings::Initialize(bool ntsc) {
    basetuning = ntsc ? 444.895778867913 : 440.83751645933;
    basenote = 3;
    temperament = 0;
    cents.Initialize();
}

// The default is equal temperament: 0, 100, ..., 1100.
void TTuningCents::Initialize() {
    for (int i = 0; i < COUNT; i++) {
        values[i] = i * 100.0;
    }
}
