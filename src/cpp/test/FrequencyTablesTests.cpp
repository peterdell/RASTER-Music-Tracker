#include "gtest/gtest.h"

#include "Atari.h"
#include "AtariTrackerDriver.h"
#include "FrequencyTables.h"
#include "TuningTypes.h"

extern TTuningSettings g_tuning;
extern TTuningRatios g_tuningRatios;

// CFrequencyTables reads the page half of its answer out of the driver's own
// TABLES_MSB dispatch table, so these tests run against a really loaded
// rmt_driver_v6.obx - and the expected pages below are the ONE place in this
// codebase that writes the mapping down. If asm/Patch-16/rmtplayr.a65 ever
// reshuffles the pages, this test fails and says so, instead of the two
// programs silently disagreeing with the driver the way they did for years
// (plans/38_GETFREQUENCY_TABLE_OFFSETS_PLAN.md,
// plans/39_MIDI_TABLE_OFFSETS_PLAN.md).

namespace {
class FrequencyTablesTest : public ::testing::Test {
  protected:
    CAtari atari;
    CAtariTrackerDriver driver{atari};

    void SetUp() override {
        // The load regenerates the tuning tables (plan 37, T2), and
        // CTuning::InitTuning() exits the process on an uninitialized
        // basetuning, so the tuning globals are set up as AtariTests.cpp does.
        g_tuning.Initialize(false); // PAL
        g_tuningRatios.Initialize();
        ASSERT_GT(driver.LoadRMTRoutines(TrackerDriverVersion::PATCH16), 0);
    }

    const byte* Memory() const {
        return atari.GetConstMemoryAt(0);
    }
};
} // namespace

TEST_F(FrequencyTablesTest, EveryDistortionTakesThePageTheDriverDispatchesItTo) {
    // distortion -> page, as asm/Patch-16/rmtplayr.a65's TABLES_MSB has it.
    const MemoryAddress expected[8] = {0xB200, 0xB000, 0xB100, 0xB200, 0xB200, 0xB200, 0xB300, 0xB400};

    for (int i = 0; i < 8; i++) {
        const int distortion = i * 2;
        EXPECT_EQ(CFrequencyTables::GetTableAddress(Memory(), distortion, CFrequencyTables::Mode::Clock64kHz), expected[i])
            << "distortion " << distortion;
    }
}

TEST_F(FrequencyTablesTest, TheClockAndWidthSelectTheSliceInsideThatPage) {
    // store_tables_lsb: 64 kHz at +$00, 1.79 MHz at +$40, 16-bit at +$80.
    EXPECT_EQ(CFrequencyTables::GetTableAddress(Memory(), 0x0A, CFrequencyTables::Mode::Clock64kHz), RMTPLAYR_FRQTABPURE_64KHZ);
    EXPECT_EQ(CFrequencyTables::GetTableAddress(Memory(), 0x0A, CFrequencyTables::Mode::Clock179MHz), RMTPLAYR_FRQTABPURE_179MHZ);
    EXPECT_EQ(CFrequencyTables::GetTableAddress(Memory(), 0x0A, CFrequencyTables::Mode::Join16Bit), RMTPLAYR_FRQTABPURE_16BIT);
}

TEST_F(FrequencyTablesTest, FifteenKhzComesFromPageExtraZeroAndNotFromTheDispatchTable) {
    // check_15khz: distortion C and E take the buzzy table, the rest the pure
    // one - so the gritty page has no 15 kHz table of its own.
    for (int distortion : {0x00, 0x02, 0x04, 0x06, 0x08, 0x0A}) {
        EXPECT_EQ(CFrequencyTables::GetTableAddress(Memory(), distortion, CFrequencyTables::Mode::Clock15kHz), RMTPLAYR_FRQTABPURE_15KHZ)
            << "distortion " << distortion;
    }
    EXPECT_EQ(CFrequencyTables::GetTableAddress(Memory(), 0x0C, CFrequencyTables::Mode::Clock15kHz), RMTPLAYR_FRQTABBUZZY_15KHZ);
    EXPECT_EQ(CFrequencyTables::GetTableAddress(Memory(), 0x0E, CFrequencyTables::Mode::Clock15kHz), RMTPLAYR_FRQTABBUZZY_15KHZ);
}

TEST_F(FrequencyTablesTest, EveryDistortionTakesTheAudcTheDriverDispatchesWithIt) {
    // distortion -> AUDC, the second byte of each TABLES_MSB pair. Not simply
    // the distortion shifted up: $06 plays with $A0 and $0E with $C0, which is
    // why the MIDI path used to get those two wrong.
    const int expected[8] = {0x00, 0x20, 0x40, 0xA0, 0x80, 0xA0, 0xC0, 0xC0};

    for (int i = 0; i < 8; i++) {
        const int distortion = i * 2;
        EXPECT_EQ(CFrequencyTables::GetDistortionAudc(Memory(), distortion), expected[i]) << "distortion " << distortion;
    }
}

TEST_F(FrequencyTablesTest, ThereIsNoAudcForADistortionOutsideZeroToEOrWithoutADriver) {
    EXPECT_EQ(CFrequencyTables::GetDistortionAudc(Memory(), -1), -1);
    EXPECT_EQ(CFrequencyTables::GetDistortionAudc(Memory(), 0x10), -1);

    atari.ClearMemory();
    EXPECT_EQ(CFrequencyTables::GetDistortionAudc(Memory(), 0x0A), -1);
}

TEST_F(FrequencyTablesTest, TheChannelDecidesWhatTheAudctlBitsMean) {
    // Only channels 1 and 3 of each chip can run at 1.79 MHz, and a 16-bit
    // pair is addressed through its high byte, the channel above.
    EXPECT_EQ(CFrequencyTables::GetModeForChannel(0x00, 0), CFrequencyTables::Mode::Clock64kHz);
    EXPECT_EQ(CFrequencyTables::GetModeForChannel(0x01, 0), CFrequencyTables::Mode::Clock15kHz);

    for (int track : {0, 4}) {
        EXPECT_EQ(CFrequencyTables::GetModeForChannel(0x40, track), CFrequencyTables::Mode::Clock179MHz) << "track " << track;
    }
    for (int track : {1, 2, 3, 5, 6, 7}) {
        EXPECT_EQ(CFrequencyTables::GetModeForChannel(0x40, track), CFrequencyTables::Mode::Clock64kHz) << "track " << track;
    }
    for (int track : {2, 6}) {
        EXPECT_EQ(CFrequencyTables::GetModeForChannel(0x20, track), CFrequencyTables::Mode::Clock179MHz) << "track " << track;
    }

    // join 1+2 with channel 1 at 1.79 MHz: the pair answers on channel 2.
    EXPECT_EQ(CFrequencyTables::GetModeForChannel(0x50, 1), CFrequencyTables::Mode::Join16Bit);
    EXPECT_EQ(CFrequencyTables::GetModeForChannel(0x50, 5), CFrequencyTables::Mode::Join16Bit);
    EXPECT_EQ(CFrequencyTables::GetModeForChannel(0x50, 0), CFrequencyTables::Mode::Clock179MHz);
    // join 3+4 with channel 3 at 1.79 MHz: on channel 4.
    EXPECT_EQ(CFrequencyTables::GetModeForChannel(0x28, 3), CFrequencyTables::Mode::Join16Bit);
    EXPECT_EQ(CFrequencyTables::GetModeForChannel(0x28, 7), CFrequencyTables::Mode::Join16Bit);
    EXPECT_EQ(CFrequencyTables::GetModeForChannel(0x28, 2), CFrequencyTables::Mode::Clock179MHz);
}

TEST_F(FrequencyTablesTest, OneSeventyNineAndSixteenBitOverrideTheFifteenKhzClock) {
    EXPECT_EQ(CFrequencyTables::GetModeForChannel(0x41, 0), CFrequencyTables::Mode::Clock179MHz);
    EXPECT_EQ(CFrequencyTables::GetModeForChannel(0x51, 1), CFrequencyTables::Mode::Join16Bit);
    // ...but a channel that cannot do either keeps the 15 kHz clock.
    EXPECT_EQ(CFrequencyTables::GetModeForChannel(0x41, 3), CFrequencyTables::Mode::Clock15kHz);
}

TEST_F(FrequencyTablesTest, ThereIsNoTableForADistortionOutsideZeroToE) {
    EXPECT_EQ(CFrequencyTables::GetTableAddress(Memory(), -1, CFrequencyTables::Mode::Clock64kHz), 0);
    EXPECT_EQ(CFrequencyTables::GetTableAddress(Memory(), 0x10, CFrequencyTables::Mode::Clock64kHz), 0);
}

TEST_F(FrequencyTablesTest, OnlyThePatchSixteenDriverIsAcceptedAsCarryingADispatchTable) {
    // The check is a plausibility test on the bytes, not a version test, so
    // the thing worth asserting is that it separates the drivers RMT really
    // ships: in the other six, $3208 holds their own frequency-table data, far
    // outside the $B0-$B4 the pages need. Each version gets its own CAtari so
    // no earlier load can leave bytes behind.
    for (TrackerDriverVersion version : {TrackerDriverVersion::UNPATCHED, TrackerDriverVersion::UNPATCHED_WITH_TUNING,
                                         TrackerDriverVersion::PATCH3, TrackerDriverVersion::PATCH6,
                                         TrackerDriverVersion::PATCH8, TrackerDriverVersion::PATCH16,
                                         TrackerDriverVersion::PATCH_PRINCE_OF_PERSIA}) {
        CAtari own;
        CAtariTrackerDriver ownDriver{own};
        ASSERT_GT(ownDriver.LoadRMTRoutines(version), 0) << "driver " << (int)version;

        EXPECT_EQ(CFrequencyTables::HasDispatchTable(own.GetConstMemoryAt(0)), version == TrackerDriverVersion::PATCH16)
            << "driver " << (int)version;
    }
}

TEST_F(FrequencyTablesTest, ThereIsNoTableWithoutADriverThatHasADispatchTable) {
    // The v1-family drivers keep their tables inside the player and never
    // read this region, so nothing writes a dispatch table for them. The
    // 15 kHz addresses are fixed and answer regardless - the caller decides
    // whether that is useful.
    atari.ClearMemory();

    EXPECT_EQ(CFrequencyTables::GetTableAddress(Memory(), 0x0A, CFrequencyTables::Mode::Clock64kHz), 0);
    EXPECT_EQ(CFrequencyTables::GetTableAddress(Memory(), 0x0A, CFrequencyTables::Mode::Clock179MHz), 0);
}
