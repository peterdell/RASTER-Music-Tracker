#include "TuningTypes.h"

#include "Atari.h"

const double TTuningSettings::NTSC_A4_FREQUENCY = TTuningSettings::PAL_A4_FREQUENCY * CAtari::FREQ_17_NTSC / CAtari::FREQ_17_PAL;

void TTuningSettings::Initialize(bool ntsc) {
    basetuning = ntsc ? NTSC_A4_FREQUENCY : PAL_A4_FREQUENCY;
    basenote = 3;
    temperament = 0;
    cents.Initialize();
}

// The default is equal temperament: 0, 100, ..., 1100.
void TTuningCents::Initialize() {
    for (int i = 0; i < COUNT; i++) {
        values[i] = i * CENTS_PER_OCTAVE / NOTES_PER_OCTAVE;
    }
}
