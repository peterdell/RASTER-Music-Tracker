#include "gtest/gtest.h"

#include "Tuning.h"
#include "TuningTypes.h"

#include <vector>

// Real, linked globals (see TuningTablesStub.cpp/SongEditingStub.cpp) -
// GenerateTable()/InitTuning() read these directly, matching production.
extern TTuningSettings g_tuning;
extern TTuningRatios g_tuningRatios;

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
    CTuning tuning{PAL_CLOCK};
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

// GetTruePitch()/CalculateDeltaAUDF() were private but pure (no global
// reads - see Tuning.h), made public purely for testability (same
// reasoning/pattern as CCompressLzss::Optimise_AUDC/AUDCTL/AUDF). Reuse the
// same test-only-constructed CTuning instance/fixture as above.

TEST_F(TuningPitchTest, GetTruePitchEqualTemperamentBaseNote) {
    EXPECT_DOUBLE_EQ(tuning.GetTruePitch(440.83751645933, CTuning::NO_TEMPERAMENT, 3, 0), 8.1913611114619442);
}

TEST_F(TuningPitchTest, GetTruePitchEqualTemperamentOneOctaveUpIsDouble) {
    // semitone=12 is exactly one octave above semitone=0 (same note, multi
    // doubles) - characterizes the octave-doubling relationship explicitly.
    EXPECT_DOUBLE_EQ(tuning.GetTruePitch(440.83751645933, CTuning::NO_TEMPERAMENT, 3, 12), 16.382722222923899);
}

TEST_F(TuningPitchTest, GetTruePitchPresetTemperamentUsesTwelveNotePresetRow) {
    // Temperament 1 = Thomas Young 1799's Well Temperament no.1, a full
    // 12-note-per-octave preset row.
    EXPECT_DOUBLE_EQ(tuning.GetTruePitch(440.83751645933, 1, 3, 0), 8.1809110925559629);
}

TEST_F(TuningPitchTest, GetTruePitchPresetTemperamentDetectsShorterPresetRow) {
    // Temperament 24 = "Optimally consonant major pentatonic" - only 6
    // entries (indices 0..5) instead of the usual 13, characterizing the
    // ragged-row notesnum-detection scan (GetTruePitch's own for-loop that
    // finds the first zero/padding entry) rather than always assuming 12.
    EXPECT_DOUBLE_EQ(tuning.GetTruePitch(440.83751645933, 24, 3, 0), 10.31063157500196);
}

TEST_F(TuningPitchTest, CalculateDeltaAUDFDefaultBranchStepsByOne) {
    // Timbre::BELL - distortion 0x20, neither the 0x40 nor 0xC0 special
    // cases, so the "simplest delta method" (+-1) applies.
    EXPECT_EQ(tuning.CalculateDeltaAUDF(313.55144978783591, 100, 28, 31, 1, Timbre::BELL), 99);
}

TEST_F(TuningPitchTest, CalculateDeltaAUDFSmoothFourVerifiesMod3Integrity) {
    EXPECT_EQ(tuning.CalculateDeltaAUDF(313.55144978783591, 100, 28, 77.5, 1, Timbre::SMOOTH_4), 98);
}

TEST_F(TuningPitchTest, CalculateDeltaAUDFBuzzyFourAvoidsMod3Mod5Mod31) {
    EXPECT_EQ(tuning.CalculateDeltaAUDF(313.55144978783591, 100, 28, 232.5, 1, Timbre::BUZZY_4), 100);
}

TEST_F(TuningPitchTest, CalculateDeltaAUDFDistortionFourInvalidTimbreReturnsZero) {
    // No real Timbre enumerator has a 0x40 high nibble other than BUZZY_4/
    // SMOOTH_4; this exercises the "invalid parameter" fallback with a
    // synthesized value, matching the enum's own documented bit-packing.
    EXPECT_EQ(tuning.CalculateDeltaAUDF(313.55144978783591, 100, 28, 232.5, 1, static_cast<Timbre>(0x45)), 0);
}

TEST_F(TuningPitchTest, CalculateDeltaAUDFFifteenKhzModeAvoidsMod5) {
    // coarse_divisor == 114 selects the 15kHz-mode branch regardless of
    // which distortion-C timbre is passed.
    EXPECT_EQ(tuning.CalculateDeltaAUDF(77.012636789994787, 100, 114, 7.5, 1, Timbre::BUZZY_C), 100);
}

TEST_F(TuningPitchTest, CalculateDeltaAUDFBuzzyCVerifiesMod3Integrity) {
    EXPECT_EQ(tuning.CalculateDeltaAUDF(313.55144978783591, 100, 28, 2.5, 1, Timbre::BUZZY_C), 98);
}

TEST_F(TuningPitchTest, CalculateDeltaAUDFGrittyCAvoidsMod3AndMod5) {
    EXPECT_EQ(tuning.CalculateDeltaAUDF(313.55144978783591, 100, 28, 7.5, 1, Timbre::GRITTY_C), 100);
}

TEST_F(TuningPitchTest, CalculateDeltaAUDFUnstableCVerifiesMod5Integrity) {
    EXPECT_EQ(tuning.CalculateDeltaAUDF(313.55144978783591, 100, 28, 1.5, 1, Timbre::UNSTABLE_C), 99);
}

TEST_F(TuningPitchTest, CalculateDeltaAUDFDistortionCInvalidTimbreReturnsZero) {
    EXPECT_EQ(tuning.CalculateDeltaAUDF(313.55144978783591, 100, 28, 7.5, 1, static_cast<Timbre>(0xC5)), 0);
}

// GenerateTable() reads g_tuning directly (see TuningTables.cpp's own header
// comment for why); it's genuinely coupled to that global, not just
// private-but-pure like GetTruePitch()/CalculateDeltaAUDF() above. As long as
// g_tuning.basetuning is set to something nonzero before calling it (as
// production does via Options), this is perfectly safe to call directly -
// same technique SongEditingTests.cpp already uses.
class TuningGenerateTableTest : public ::testing::Test {
  protected:
    CTuning tuning{PAL_CLOCK};
    void SetUp() override {
        g_tuning.Initialize(false); // PAL
    }
};

TEST_F(TuningGenerateTableTest, GeneratesAnEightBitBellTable) {
    byte table[4] = {};
    tuning.GenerateTable(table, 4, 0, Timbre::BELL, 0x00);
    EXPECT_EQ(table[0], 124);
    EXPECT_EQ(table[1], 117);
    EXPECT_EQ(table[2], 110);
    EXPECT_EQ(table[3], 104);
}

TEST_F(TuningGenerateTableTest, GeneratesAnEightBitPureATable) {
    // Semitone offset 48 matches how InitTuning() actually calls this table
    // (dist_a_pure.table_64khz(4) * g_notesperoctave(12)) - semitone 0
    // directly would ask for an implausibly low pitch that clamps to 0xFF
    // for every entry, which characterizes the clamp but nothing else.
    byte table[4] = {};
    tuning.GenerateTable(table, 4, 48, Timbre::PURE_A, 0x00);
    EXPECT_EQ(table[0], 241);
    EXPECT_EQ(table[1], 227);
    EXPECT_EQ(table[2], 214);
    EXPECT_EQ(table[3], 202);
}

TEST_F(TuningGenerateTableTest, GeneratesASixteenBitJoinedTableUsingTwoBytesPerEntry) {
    // audctl 0x50 = JOIN_12 (0x10) | CH1_179 (0x40) -> JOIN_16BIT for
    // channel-agnostic table generation, so each entry is 2 bytes (LSB/MSB).
    // Semitone offset 24 matches dist_a_pure.table_16bit(2) * g_notesperoctave(12).
    byte table[8] = {};
    tuning.GenerateTable(table, 4, 24, Timbre::PURE_A, 0x50);
    EXPECT_EQ(table[0], 176);
    EXPECT_EQ(table[1], 105);
    EXPECT_EQ(table[2], 193);
    EXPECT_EQ(table[3], 99);
    EXPECT_EQ(table[4], 39);
    EXPECT_EQ(table[5], 94);
    EXPECT_EQ(table[6], 222);
    EXPECT_EQ(table[7], 88);
}

// InitTuning() orchestrates GenerateTable() across all 13 lookup tables. Its
// MessageBox+exit(1) guard (basetuning == 0) is never reached here because
// SetUp() always initializes g_tuning/g_tuningRatios first - deliberately
// not characterized directly since that would terminate the whole test
// process (same hazard already documented in AtariStub.cpp/JAVA_PORT_PLAN.md
// for CAtari::Init(bool)).
class TuningInitTuningTest : public ::testing::Test {
  protected:
    CTuning tuning{PAL_CLOCK};
    std::vector<byte> memory = std::vector<byte>(0x600, 0);
    void SetUp() override {
        g_tuning.Initialize(false); // PAL
        g_tuningRatios.Initialize();
        tuning.InitTuning(PAL_CLOCK, memory.data());
    }
};

TEST_F(TuningInitTuningTest, PopulatesTheDistortionTwoBellTableAtOffsetZero) {
    EXPECT_EQ(memory[0x000], 62);
    EXPECT_EQ(memory[0x001], 58);
}

TEST_F(TuningInitTuningTest, PopulatesTheDistortionFourSmoothTableAtOffsetHundred) {
    EXPECT_EQ(memory[0x100], 23);
    EXPECT_EQ(memory[0x101], 23);
}

TEST_F(TuningInitTuningTest, PopulatesTheDistortionAPureTableAtOffsetTwoHundred) {
    EXPECT_EQ(memory[0x200], 241);
    EXPECT_EQ(memory[0x201], 227);
}

TEST_F(TuningInitTuningTest, PopulatesTheDistortionCBuzzyTableAtOffsetThreeHundred) {
    EXPECT_EQ(memory[0x300], 127);
    EXPECT_EQ(memory[0x301], 121);
}

TEST_F(TuningInitTuningTest, PopulatesTheDistortionCGrittyTableAtOffsetFourHundred) {
    // The first entry clamps to 0xFF (255) - real, captured behavior at
    // this table's actual starting semitone offset, not a test artifact.
    EXPECT_EQ(memory[0x400], 255);
    EXPECT_EQ(memory[0x401], 243);
}

TEST_F(TuningInitTuningTest, PopulatesTheFifteenKhzPureATableAtOffsetFiveEightyOne) {
    // dist_a_pure.table_15khz is written only at 0x580 (no 15kHz table
    // exists for Distortion 2/4/C - see InitTuning()'s own comments).
    EXPECT_EQ(memory[0x580], 236);
    EXPECT_EQ(memory[0x581], 223);
}

TEST_F(TuningInitTuningTest, PopulatesTheFifteenKhzBuzzyCTableAtOffsetFiveC0) {
    EXPECT_EQ(memory[0x5C0], 188);
    EXPECT_EQ(memory[0x5C1], 178);
}

TEST_F(TuningInitTuningTest, GetTruePitchCustomTemperamentUsesRatiosPopulatedByInitTuning) {
    // TUNING_CUSTOM reads CTuning's private CUSTOM[] array, only ever
    // populated as a side effect of InitTuning() (from g_tuningRatios) -
    // there's no public setter, so this can only be characterized here,
    // after SetUp()'s InitTuning() call.
    EXPECT_DOUBLE_EQ(tuning.GetTruePitch(440.83751645933, CTuning::TUNING_CUSTOM, 3, 0), 8.1036308172670957);
}
