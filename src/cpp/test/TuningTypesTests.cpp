#include "gtest/gtest.h"

#include "Atari.h"
#include "TuningTypes.h"

// Mirrored by Java's TuningCentsTest.
class TuningCentsTest : public ::testing::Test {
protected:
    TTuningCents cents;
    void SetUp() override {
        cents.Initialize();
    }
};

TEST_F(TuningCentsTest, DefaultsAreEqualTemperament) {
    for (int i = 0; i < TTuningCents::COUNT; i++) {
        EXPECT_DOUBLE_EQ(cents.values[i], i * CENTS_PER_OCTAVE / NOTES_PER_OCTAVE) << "semitone " << i;
    }
}

class TuningSettingsTest : public ::testing::Test {
protected:
    TTuningSettings settings;
};

TEST_F(TuningSettingsTest, InitializePal) {
    settings.Initialize(false);
    EXPECT_DOUBLE_EQ(settings.a4Frequency, TTuningSettings::PAL_A4_FREQUENCY);
    EXPECT_EQ(settings.tonic, 3);
    EXPECT_EQ(settings.temperament, 0);
    EXPECT_DOUBLE_EQ(settings.cents.values[11], 1100);
}

TEST_F(TuningSettingsTest, InitializeNtsc) {
    settings.Initialize(true);
    EXPECT_DOUBLE_EQ(settings.a4Frequency, TTuningSettings::NTSC_A4_FREQUENCY);
}
