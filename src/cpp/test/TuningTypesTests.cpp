#include "gtest/gtest.h"

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
        EXPECT_DOUBLE_EQ(cents.values[i], i * 100.0) << "semitone " << i;
    }
}

class TuningSettingsTest : public ::testing::Test {
protected:
    TTuningSettings settings;
};

TEST_F(TuningSettingsTest, InitializePal) {
    settings.Initialize(false);
    EXPECT_DOUBLE_EQ(settings.basetuning, 440.83751645933);
    EXPECT_EQ(settings.basenote, 3);
    EXPECT_EQ(settings.temperament, 0);
    EXPECT_DOUBLE_EQ(settings.cents.values[11], 1100);
}

TEST_F(TuningSettingsTest, InitializeNtsc) {
    settings.Initialize(true);
    EXPECT_DOUBLE_EQ(settings.basetuning, 444.895778867913);
}
