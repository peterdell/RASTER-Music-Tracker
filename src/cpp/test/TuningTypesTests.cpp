#include "gtest/gtest.h"

#include "TuningTypes.h"

TEST(TuningSettingsTest, InitializePal) {
    TTuningSettings settings;
    settings.Initialize(false);
    EXPECT_DOUBLE_EQ(settings.basetuning, 440.83751645933);
    EXPECT_EQ(settings.basenote, 3);
    EXPECT_EQ(settings.temperament, 0);
}

TEST(TuningSettingsTest, InitializeNtsc) {
    TTuningSettings settings;
    settings.Initialize(true);
    EXPECT_DOUBLE_EQ(settings.basetuning, 444.895778867913);
    EXPECT_EQ(settings.basenote, 3);
    EXPECT_EQ(settings.temperament, 0);
}

class TuningRatiosTest : public ::testing::Test {
  protected:
    TTuningRatios ratios;
    void SetUp() override {
        ratios.Initialize();
    }
};

TEST_F(TuningRatiosTest, UnisonAndOctaveAreWholeNumberRatios) {
    EXPECT_EQ(ratios.UNISON.numerator, 1);
    EXPECT_EQ(ratios.UNISON.denominator, 1);
    EXPECT_EQ(ratios.OCTAVE.numerator, 2);
    EXPECT_EQ(ratios.OCTAVE.denominator, 1);
}

// Characterization test: the literal for MIN_2ND is written as 40/38 in
// TuningTypes.cpp, but CFraction's constructor always reduces to lowest
// terms, so the stored value is actually 20/19.
TEST_F(TuningRatiosTest, MinorSecondIsStoredReduced) {
    EXPECT_EQ(ratios.MIN_2ND.numerator, 20);
    EXPECT_EQ(ratios.MIN_2ND.denominator, 19);
}

TEST_F(TuningRatiosTest, RemainingRatiosMatchTheirLiterals) {
    EXPECT_EQ(ratios.MAJ_2ND.numerator, 10);
    EXPECT_EQ(ratios.MAJ_2ND.denominator, 9);

    EXPECT_EQ(ratios.MIN_3RD.numerator, 20);
    EXPECT_EQ(ratios.MIN_3RD.denominator, 17);

    EXPECT_EQ(ratios.MAJ_3RD.numerator, 5);
    EXPECT_EQ(ratios.MAJ_3RD.denominator, 4);

    EXPECT_EQ(ratios.PERF_4TH.numerator, 4);
    EXPECT_EQ(ratios.PERF_4TH.denominator, 3);

    EXPECT_EQ(ratios.TRITONE.numerator, 60);
    EXPECT_EQ(ratios.TRITONE.denominator, 43);

    EXPECT_EQ(ratios.PERF_5TH.numerator, 3);
    EXPECT_EQ(ratios.PERF_5TH.denominator, 2);

    EXPECT_EQ(ratios.MIN_6TH.numerator, 30);
    EXPECT_EQ(ratios.MIN_6TH.denominator, 19);

    EXPECT_EQ(ratios.MAJ_6TH.numerator, 5);
    EXPECT_EQ(ratios.MAJ_6TH.denominator, 3);

    EXPECT_EQ(ratios.MIN_7TH.numerator, 30);
    EXPECT_EQ(ratios.MIN_7TH.denominator, 17);

    EXPECT_EQ(ratios.MAJ_7TH.numerator, 15);
    EXPECT_EQ(ratios.MAJ_7TH.denominator, 8);
}
