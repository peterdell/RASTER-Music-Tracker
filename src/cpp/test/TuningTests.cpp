#include "gtest/gtest.h"

#include "Tuning.h"
#include "TuningTypes.h"
#include "Song.h"

#include <vector>

// Real, linked globals (see TuningTablesStub.cpp/SongEditingStub.cpp) -
// GenerateTable()/InitTuning() read these directly, matching production.
extern CSong g_Song;

// CTuning::GetPitch()/GetAUDF()/GetPOKEYPitch() are pure functions of their
// parameters and the object's m_clockFrequency. The test-only constructor
// (see Tuning.h) sets that directly, avoiding InitTuning()'s dependency on
// global tuning state and its MessageBox+exit(1) guard for uninitialized
// state.
//
// PAL POKEY clock, matching CAtari::FREQ_17_PAL in Atari.h.
static constexpr int PAL_CLOCK = 1773447;
// NTSC POKEY clock, matching CAtari::FREQ_17_NTSC in Atari.h.
static constexpr int NTSC_CLOCK = 1789773;

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
// reasoning/pattern as CCompressLzss::Optimize_AUDC/AUDCTL/AUDF). Reuse the
// same test-only-constructed CTuning instance/fixture as above.

TEST_F(TuningPitchTest, GetTruePitchEqualTemperamentBaseNote) {
    EXPECT_DOUBLE_EQ(tuning.GetTruePitch(TTuningSettings::PAL_A4_FREQUENCY, CTuning::NO_TEMPERAMENT, 3, 0), 8.1913611114619442);
}

TEST_F(TuningPitchTest, GetTruePitchEqualTemperamentOneOctaveUpIsDouble) {
    // semitone=12 is exactly one octave above semitone=0 (same note, multi
    // doubles) - characterizes the octave-doubling relationship explicitly.
    EXPECT_DOUBLE_EQ(tuning.GetTruePitch(TTuningSettings::PAL_A4_FREQUENCY, CTuning::NO_TEMPERAMENT, 3, 12), 16.382722222923899);
}

TEST_F(TuningPitchTest, GetTruePitchPresetTemperamentUsesTwelveNotePresetRow) {
    // Temperament 1 = Thomas Young 1799's Well Temperament no.1, a full
    // 12-note-per-octave preset row.
    EXPECT_DOUBLE_EQ(tuning.GetTruePitch(TTuningSettings::PAL_A4_FREQUENCY, 1, 3, 0), 8.1809110925559629);
}

TEST_F(TuningPitchTest, GetTruePitchPresetTemperamentDetectsShorterPresetRow) {
    // Temperament 24 = "Optimally consonant major pentatonic" - only 6
    // entries (indices 0..5) instead of the usual 13, characterizing the
    // ragged-row notesnum-detection scan (GetTruePitch's own for-loop that
    // finds the first zero/padding entry) rather than always assuming 12.
    EXPECT_DOUBLE_EQ(tuning.GetTruePitch(TTuningSettings::PAL_A4_FREQUENCY, 24, 3, 0), 10.31063157500196);
}

// Plan 59 step 2: whatever the key and temperament, the note A (index 9 and
// its octaves) sounds at the A4 reference pitch.
TEST_F(TuningPitchTest, CalculateBaseFrequencyAnchorsTheNoteAAtTheA4Frequency) {
    for (int basenote = 0; basenote < NOTES_PER_OCTAVE; basenote++) {
        for (CTuning::Temperament temperament : {CTuning::NO_TEMPERAMENT, 1, 17}) {
            TTuningSettings settings;
            settings.Initialize(false);
            settings.basenote = basenote;
            settings.temperament = temperament;
            double anchor = tuning.CalculateBaseFrequency(settings);
            EXPECT_NEAR(tuning.GetTruePitch(anchor, temperament, basenote, CTuning::A_SEMITONES_ABOVE_C), settings.basetuning / 32, 1e-9)
                << "temperament " << temperament << " basenote " << basenote;
        }
    }
}

// Plan 59 step 2: for the default base note A- the anchor is the A4 frequency
// itself, bit-exactly - pre-step-2 modules are unchanged.
TEST_F(TuningPitchTest, CalculateBaseFrequencyIsTheIdentityForTheBaseNoteA) {
    TTuningSettings settings;
    settings.Initialize(false);
    settings.temperament = 4; // Werckmeister III
    EXPECT_EQ(tuning.CalculateBaseFrequency(settings), settings.basetuning);
}

// tuning_poc2: ./poc -T 3 -b 60 -a 440.0 (Young No. 1, key C, A4 = 440)
// prints C4 = 262.57, E4 = 329.24, A4 = 440.00. Index 0 is C, 4 is E, 9 is A;
// RMT's pitches sit five octaves below the poc's MIDI octave 4 (times 32).
TEST_F(TuningPitchTest, KeyCWithA4FourFortyMatchesIvopsPoc) {
    TTuningSettings settings;
    settings.Initialize(false);
    settings.basetuning = 440;
    settings.basenote = 0; // C-
    settings.temperament = 1; // Young No. 1
    double anchor = tuning.CalculateBaseFrequency(settings);
    EXPECT_NEAR(tuning.GetTruePitch(anchor, settings.temperament, 0, 0) * 32, 262.57, 0.005);
    EXPECT_NEAR(tuning.GetTruePitch(anchor, settings.temperament, 0, 4) * 32, 329.24, 0.005);
    EXPECT_NEAR(tuning.GetTruePitch(anchor, settings.temperament, 0, CTuning::A_SEMITONES_ABOVE_C) * 32, 440.00, 0.005);
}

// Plan 59 step 2's deliberate behavior change: with A4 anchoring, equal
// temperament is key-invariant - the base note no longer transposes.
TEST_F(TuningPitchTest, EqualTemperamentIsKeyInvariantWithA4Anchoring) {
    TTuningSettings reference;
    reference.Initialize(false); // base note A-
    double referenceAnchor = tuning.CalculateBaseFrequency(reference);
    for (int basenote = 0; basenote < NOTES_PER_OCTAVE; basenote++) {
        TTuningSettings settings;
        settings.Initialize(false);
        settings.basenote = basenote;
        double anchor = tuning.CalculateBaseFrequency(settings);
        for (int semitone = 0; semitone < 2 * NOTES_PER_OCTAVE; semitone++) {
            EXPECT_NEAR(tuning.GetTruePitch(anchor, CTuning::NO_TEMPERAMENT, basenote, semitone),
                        tuning.GetTruePitch(referenceAnchor, CTuning::NO_TEMPERAMENT, reference.basenote, semitone), 1e-9)
                << "basenote " << basenote << " semitone " << semitone;
        }
    }
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

// GenerateTable() reads g_Song.GetTuningSettings() directly (see TuningTables.cpp's own header
// comment for why); it's genuinely coupled to that global, not just
// private-but-pure like GetTruePitch()/CalculateDeltaAUDF() above. As long as
// g_Song.GetTuningSettings().basetuning is set to something nonzero before calling it (as
// production does via Options), this is perfectly safe to call directly -
// same technique SongEditingTests.cpp already uses.
class TuningGenerateTableTest : public ::testing::Test {
  protected:
    CTuning tuning{PAL_CLOCK};
    void SetUp() override {
        g_Song.GetTuningSettings().Initialize(false); // PAL
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
// SetUp() always initializes g_Song.GetTuningSettings() (the cents inside included) first - deliberately
// not characterized directly since that would terminate the whole test
// process (same hazard already documented in AtariStub.cpp/13_JAVA_PORT_PLAN.md
// for CAtari::Init(bool)).
class TuningInitTuningTest : public ::testing::Test {
  protected:
    CTuning tuning{PAL_CLOCK};
    std::vector<byte> memory = std::vector<byte>(0x600, 0);
    void SetUp() override {
        g_Song.GetTuningSettings().Initialize(false); // PAL
        tuning.InitTuning(PAL_CLOCK, memory.data());
    }
};

TEST_F(TuningInitTuningTest, CalculateBaseFrequencyUsesTheCustomCents) {
    // Only valid after InitTuning() filled CUSTOM[] (SetUp() did, from the
    // default cents = equal temperament), so the custom anchor for the key of
    // C equals the equal-temperament one.
    TTuningSettings settings = g_Song.GetTuningSettings();
    settings.basenote = 0; // C-
    settings.temperament = CTuning::TUNING_CUSTOM;
    double anchor = tuning.CalculateBaseFrequency(settings);
    EXPECT_NEAR(anchor, 2 * settings.basetuning / pow(2.0, (double)CTuning::A_SEMITONES_ABOVE_C / NOTES_PER_OCTAVE), 1e-9);
    settings.temperament = CTuning::NO_TEMPERAMENT;
    EXPECT_NEAR(anchor, tuning.CalculateBaseFrequency(settings), 1e-9);
}

TEST_F(TuningInitTuningTest, GeneratesTheIdenticalPageForTheNtscDefaultsOnTheNtscClock) {
    // The NTSC default is the PAL default scaled by exactly the clock ratio
    // (TTuningSettings::NTSC_A4_FREQUENCY): every pitch is proportional to
    // the base tuning and every AUDF is computed from clock / pitch, so the
    // scaling cancels before any rounding and the whole page is
    // bit-identical - the invariant that lets one player binary serve both
    // systems (doc/rmt_tracker_drivers.md, "Tuning"). It holds for the
    // custom temperament's cents too, which Initialize() included.
    g_Song.GetTuningSettings().Initialize(true); // NTSC
    CTuning ntscTuning{NTSC_CLOCK};
    std::vector<byte> ntscMemory = std::vector<byte>(0x600, 0);
    ntscTuning.InitTuning(NTSC_CLOCK, ntscMemory.data());
    EXPECT_EQ(memory, ntscMemory);
}

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

TEST_F(TuningInitTuningTest, GetTruePitchCustomTemperamentUsesCentsPopulatedByInitTuning) {
    // TUNING_CUSTOM reads CTuning's private CUSTOM[] array, only ever
    // populated as a side effect of InitTuning() (from the settings'
    // cents since plan 59) -
    // there's no public setter, so this can only be characterized here,
    // after SetUp()'s InitTuning() call.
    // The cents defaults are equal temperament (0, 100, ..., 1100), so the
    // custom path reproduces the equal-temperament pitch.
    EXPECT_DOUBLE_EQ(tuning.GetTruePitch(TTuningSettings::PAL_A4_FREQUENCY, CTuning::TUNING_CUSTOM, 3, 0), 8.191361111461944);
    EXPECT_DOUBLE_EQ(tuning.GetTruePitch(TTuningSettings::PAL_A4_FREQUENCY, CTuning::TUNING_CUSTOM, 3, 0), tuning.GetTruePitch(TTuningSettings::PAL_A4_FREQUENCY, 0, 3, 0));
}
