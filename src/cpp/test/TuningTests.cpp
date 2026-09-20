#include "gtest/gtest.h"

#include "Tuning.h"

// CTuning::GetPitch()/GetAUDF()/GetPOKEYPitch() are pure functions of their
// parameters and the object's m_clockFrequency. The test-only constructor
// (see Tuning.h) sets that directly, avoiding InitTuning()'s dependency on
// global tuning state and its MessageBox+exit(1) guard for uninitialized
// state.
//
// PAL POKEY clock, matching CAtari::FREQ_17_PAL in Atari.h.
static constexpr int PAL_CLOCK = 1773447;

class TuningPitchTest : public ::testing::Test {
protected:
    CTuning tuning{ PAL_CLOCK };
};

// Expected values below were captured by running the actual implementation
// (a "golden master"/characterization approach), not hand-derived, since the
// point is to lock in current behavior rather than re-derive the formula.

TEST_F(TuningPitchTest, GetPitchNoDivisorNoCycle) {
    EXPECT_DOUBLE_EQ(tuning.GetPitch(0, 28, 1, 1), 31668.696428571428);
}

TEST_F(TuningPitchTest, GetPitchWithCoarseDivisor64khz) {
    EXPECT_DOUBLE_EQ(tuning.GetPitch(100, 28, 1, 1), 313.55144978783591);
}

TEST_F(TuningPitchTest, GetPitchWithCoarseDivisor15khz) {
    EXPECT_DOUBLE_EQ(tuning.GetPitch(100, 114, 1, 1), 77.012636789994787);
}

TEST_F(TuningPitchTest, GetPitch179mhzMode) {
    EXPECT_DOUBLE_EQ(tuning.GetPitch(1000, 1, 1, 4), 883.19073705179278);
}

TEST_F(TuningPitchTest, GetAUDFRoundTripsWithGetPitch) {
    // 440 Hz (concert A), 64kHz mode, no fine divisor.
    EXPECT_EQ(tuning.GetAUDF(440.0, 28, 1, 1), 71);
}

TEST_F(TuningPitchTest, GetAUDFWithFineDivisor) {
    EXPECT_EQ(tuning.GetAUDF(440.0, 28, 7.5, 1), 9);
}

TEST_F(TuningPitchTest, GetPOKEYPitchPureToneDistortionA) {
    // AUDC 0xA0 = Distortion A (Pure), no volume-only bit.
    EXPECT_DOUBLE_EQ(tuning.GetPOKEYPitch(0xA0, 100, 0x00, 0), 313.55144978783591);
}

TEST_F(TuningPitchTest, GetPOKEYPitchDistortionCBuzzy64khz) {
    // AUDC 0xC1 = Distortion C variant, 64kHz clock (audctl 0x00).
    EXPECT_DOUBLE_EQ(tuning.GetPOKEYPitch(0xC1, 100, 0x00, 0), 41.806859971711461);
}

TEST_F(TuningPitchTest, GetPOKEYPitch179mhzChannel0) {
    // CH1_179 (audctl 0x40) with channel 0 selects the 1.79MHz clock path.
    EXPECT_DOUBLE_EQ(tuning.GetPOKEYPitch(0xA0, 1000, 0x40, 0), 883.19073705179278);
}

TEST_F(TuningPitchTest, GetPOKEYPitchPolyNoiseModeReturnsZeroOnInvalidModulo) {
    // AUDC 0x08 = distortion 0x00 with POLY9 bit (0x80) set on the AUDC
    // value's low nibble volume bits is irrelevant; POLY9 comes from
    // audctl here. audf chosen so (audf+cycle) % 31 == 0, which this
    // distortion path treats as an invalid frequency and returns 0.
    EXPECT_DOUBLE_EQ(tuning.GetPOKEYPitch(0x00, 30, 0x80, 0), 0.0);
}
