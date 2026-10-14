#include "gtest/gtest.h"

#include "Atari.h"
#include "AtariTrackerDriver.h"
#include "FrequencyTables.h"
#include "TuningTypes.h"
#include "Song.h"

extern CSong g_Song;

// CFrequencyTables reads both halves of its answer out of the driver's own
// dispatch table, so these tests run against really loaded driver binaries -
// and the expected values below are the ONE place in this codebase that writes
// the two mappings down. If asm/Patch-16/rmtplayr.a65 or asm/Legacy/rmt.a65
// ever reshuffles its tables, these tests fail and say so, instead of the two
// programs silently disagreeing with the driver the way they did for years
// (plans/38_GETFREQUENCY_TABLE_OFFSETS_PLAN.md,
// plans/39_MIDI_TABLE_OFFSETS_PLAN.md,
// plans/40_DRIVER_TABLE_LAYOUTS_PLAN.md).

namespace {
// Every driver RMT ships, in TrackerDriverVersion order. NONE has no binary.
const TrackerDriverVersion kAllVersions[7] = {
    TrackerDriverVersion::UNPATCHED,  TrackerDriverVersion::UNPATCHED_WITH_TUNING, TrackerDriverVersion::PATCH3,
    TrackerDriverVersion::PATCH6,     TrackerDriverVersion::PATCH8,                TrackerDriverVersion::PATCH16,
    TrackerDriverVersion::PATCH_PRINCE_OF_PERSIA,
};

class FrequencyTablesTest : public ::testing::Test {
  protected:
    CAtari atari;
    CAtariTrackerDriver driver{atari};

    void SetUp() override {
        // The load regenerates the tuning tables (plan 37, T2), and
        // CTuning::InitTuning() exits the process on an uninitialized
        // basetuning, so the tuning globals are set up as AtariTests.cpp does.
        g_Song.GetTuning().Initialize(false); // PAL
        g_Song.GetTuningRatios().Initialize();
        ASSERT_GT(driver.LoadRMTRoutines(TrackerDriverVersion::PATCH16), 0);
    }

    const byte* Memory() const {
        return atari.GetConstMemoryAt(0);
    }
};
} // namespace

// --- the Patch 16 layout ---

// --- IsTuningSupported ---
// The tuning system only ever writes the $B000 page (RMT_FRQTABLES), which is
// where Patch 16 keeps its tables; every other driver carries its own four
// inside the player and plays those whatever the tuning says. The Java port's
// TrackerDriverVersion.isTuningSupported() asserts the same list.

TEST(TrackerDriverVersionTest, OnlyPatch16SupportsTuning) {
    EXPECT_TRUE(IsTuningSupported(TrackerDriverVersion::PATCH16));
    EXPECT_FALSE(IsTuningSupported(TrackerDriverVersion::NONE));
    EXPECT_FALSE(IsTuningSupported(TrackerDriverVersion::UNPATCHED));
    EXPECT_FALSE(IsTuningSupported(TrackerDriverVersion::UNPATCHED_WITH_TUNING));
    EXPECT_FALSE(IsTuningSupported(TrackerDriverVersion::PATCH3));
    EXPECT_FALSE(IsTuningSupported(TrackerDriverVersion::PATCH6));
    EXPECT_FALSE(IsTuningSupported(TrackerDriverVersion::PATCH8));
    EXPECT_FALSE(IsTuningSupported(TrackerDriverVersion::PATCH_PRINCE_OF_PERSIA));
}

TEST_F(FrequencyTablesTest, EveryDistortionTakesThePageTheDriverDispatchesItTo) {
    // distortion -> page, as asm/Patch-16/rmtplayr.a65's TABLES_MSB has it.
    const MemoryAddress expected[8] = {0xB200, 0xB000, 0xB100, 0xB200, 0xB200, 0xB200, 0xB300, 0xB400};

    for (int i = 0; i < 8; i++) {
        const int distortion = i * 2;
        EXPECT_EQ(CFrequencyTables::GetTableAddress(Memory(), TrackerDriverVersion::PATCH16, distortion, CFrequencyTables::Mode::Clock64kHz),
                  expected[i])
            << "distortion " << distortion;
    }
}

TEST_F(FrequencyTablesTest, TheClockAndWidthSelectTheSliceInsideThatPage) {
    // store_tables_lsb: 64 kHz at +$00, 1.79 MHz at +$40, 16-bit at +$80.
    EXPECT_EQ(CFrequencyTables::GetTableAddress(Memory(), TrackerDriverVersion::PATCH16, 0x0A, CFrequencyTables::Mode::Clock64kHz),
              RMTPLAYR_FRQTABPURE_64KHZ);
    EXPECT_EQ(CFrequencyTables::GetTableAddress(Memory(), TrackerDriverVersion::PATCH16, 0x0A, CFrequencyTables::Mode::Clock179MHz),
              RMTPLAYR_FRQTABPURE_179MHZ);
    EXPECT_EQ(CFrequencyTables::GetTableAddress(Memory(), TrackerDriverVersion::PATCH16, 0x0A, CFrequencyTables::Mode::Join16Bit),
              RMTPLAYR_FRQTABPURE_16BIT);
}

TEST_F(FrequencyTablesTest, FifteenKhzComesFromPageExtraZeroAndNotFromTheDispatchTable) {
    // check_15khz: distortion C and E take the buzzy table, the rest the pure
    // one - so the gritty page has no 15 kHz table of its own.
    for (int distortion : {0x00, 0x02, 0x04, 0x06, 0x08, 0x0A}) {
        EXPECT_EQ(CFrequencyTables::GetTableAddress(Memory(), TrackerDriverVersion::PATCH16, distortion, CFrequencyTables::Mode::Clock15kHz),
                  RMTPLAYR_FRQTABPURE_15KHZ)
            << "distortion " << distortion;
    }
    EXPECT_EQ(CFrequencyTables::GetTableAddress(Memory(), TrackerDriverVersion::PATCH16, 0x0C, CFrequencyTables::Mode::Clock15kHz),
              RMTPLAYR_FRQTABBUZZY_15KHZ);
    EXPECT_EQ(CFrequencyTables::GetTableAddress(Memory(), TrackerDriverVersion::PATCH16, 0x0E, CFrequencyTables::Mode::Clock15kHz),
              RMTPLAYR_FRQTABBUZZY_15KHZ);
}

TEST_F(FrequencyTablesTest, EveryDistortionTakesTheAudcTheDriverDispatchesWithIt) {
    // distortion -> AUDC, the second byte of each TABLES_MSB pair. Not simply
    // the distortion shifted up: $06 plays with $A0 and $0E with $C0, which is
    // why the MIDI path used to get those two wrong.
    const int expected[8] = {0x00, 0x20, 0x40, 0xA0, 0x80, 0xA0, 0xC0, 0xC0};

    for (int i = 0; i < 8; i++) {
        const int distortion = i * 2;
        EXPECT_EQ(CFrequencyTables::GetDistortionAudc(Memory(), TrackerDriverVersion::PATCH16, distortion), expected[i])
            << "distortion " << distortion;
    }
}

// --- the v1-family layout ---

TEST_F(FrequencyTablesTest, TheVOneFamilyTakesItsTablesFromTabbeganddistor) {
    // offset into frqtab ($3200) and AUDC per distortion, as
    // asm/Legacy/rmt.a65's tabbeganddistor has it: frqtabbass1 at +$00 for
    // distortions $06 and $0C, frqtabbass2 at +$40 for $0E, frqtabpure at
    // +$80 for the rest.
    const MemoryAddress expectedTable[8] = {0x3280, 0x3280, 0x3280, 0x3200, 0x3280, 0x3280, 0x3200, 0x3240};
    const int expectedAudc[8] = {0x00, 0x20, 0x40, 0xC0, 0x80, 0xA0, 0xC0, 0xC0};

    CAtari own;
    CAtariTrackerDriver ownDriver{own};
    ASSERT_GT(ownDriver.LoadRMTRoutines(TrackerDriverVersion::UNPATCHED), 0);
    const byte* memory = own.GetConstMemoryAt(0);

    for (int i = 0; i < 8; i++) {
        const int distortion = i * 2;
        EXPECT_EQ(CFrequencyTables::GetTableAddress(memory, TrackerDriverVersion::UNPATCHED, distortion, CFrequencyTables::Mode::Clock64kHz),
                  expectedTable[i])
            << "distortion " << distortion;
        EXPECT_EQ(CFrequencyTables::GetDistortionAudc(memory, TrackerDriverVersion::UNPATCHED, distortion), expectedAudc[i])
            << "distortion " << distortion;
    }
}

TEST_F(FrequencyTablesTest, TheVOneFamilyReadsTheSameTableWhateverTheClock) {
    // That player has no 1.79 MHz and no 15 kHz tables at all: it builds its
    // pointer from >frqtab and the tabbeganddistor offset and indexes it by
    // the note, whatever clock the channel runs at. So those two clocks play
    // out of tune there rather than playing nothing - and reporting the same
    // table is what the player really does.
    CAtari own;
    CAtariTrackerDriver ownDriver{own};
    ASSERT_GT(ownDriver.LoadRMTRoutines(TrackerDriverVersion::UNPATCHED), 0);
    const byte* memory = own.GetConstMemoryAt(0);

    for (auto mode : {CFrequencyTables::Mode::Clock64kHz, CFrequencyTables::Mode::Clock179MHz, CFrequencyTables::Mode::Clock15kHz}) {
        EXPECT_EQ(CFrequencyTables::GetTableAddress(memory, TrackerDriverVersion::UNPATCHED, 0x0A, mode), 0x3280) << "mode " << (int)mode;
    }

    // ...but its 16-bit bass reads frqtabbasslo and frqtabbasshi, two tables a
    // page apart, which no single address can name.
    EXPECT_EQ(CFrequencyTables::GetTableAddress(memory, TrackerDriverVersion::UNPATCHED, 0x06, CFrequencyTables::Mode::Join16Bit), 0);
}

TEST_F(FrequencyTablesTest, EachPatchedDriverIsReadWithItsOwnTableAndNotVersionOnesCopy) {
    // The five patched binaries have no source in this repository, and each
    // one changed the table: PATCH6 plays distortion $06 with AUDC $A0 where
    // UNPATCHED plays it with $C0, and PATCH8 and PATCH_PRINCE_OF_PERSIA do
    // not even keep their tables on $40 boundaries. Reading the bytes is the
    // only way to be right about all of them - these are the shipped values,
    // measured, and this test is what catches a driver binary being replaced.
    struct Expected {
        TrackerDriverVersion version;
        MemoryAddress table06;
        int audc06;
    };
    const Expected expected[] = {
        {TrackerDriverVersion::UNPATCHED, 0x3200, 0xC0},
        {TrackerDriverVersion::UNPATCHED_WITH_TUNING, 0x3200, 0xC0},
        {TrackerDriverVersion::PATCH3, 0x3200, 0x80},
        {TrackerDriverVersion::PATCH6, 0x3200, 0xA0},
        {TrackerDriverVersion::PATCH8, 0x3200, 0xC0},
        {TrackerDriverVersion::PATCH_PRINCE_OF_PERSIA, 0x3270, 0xC0},
    };

    for (const auto& e : expected) {
        CAtari own;
        CAtariTrackerDriver ownDriver{own};
        ASSERT_GT(ownDriver.LoadRMTRoutines(e.version), 0) << "driver " << (int)e.version;
        const byte* memory = own.GetConstMemoryAt(0);

        EXPECT_EQ(CFrequencyTables::GetTableAddress(memory, e.version, 0x06, CFrequencyTables::Mode::Clock64kHz), e.table06)
            << "driver " << (int)e.version;
        EXPECT_EQ(CFrequencyTables::GetDistortionAudc(memory, e.version, 0x06), e.audc06) << "driver " << (int)e.version;
    }
}

// --- the mode, which is the layouts' shared question ---

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

// --- what neither layout answers ---

TEST_F(FrequencyTablesTest, ThereIsNoTableForADistortionOutsideZeroToE) {
    for (TrackerDriverVersion version : kAllVersions) {
        EXPECT_EQ(CFrequencyTables::GetTableAddress(Memory(), version, -1, CFrequencyTables::Mode::Clock64kHz), 0) << "driver " << (int)version;
        EXPECT_EQ(CFrequencyTables::GetTableAddress(Memory(), version, 0x10, CFrequencyTables::Mode::Clock64kHz), 0)
            << "driver " << (int)version;
    }
}

TEST_F(FrequencyTablesTest, ThereIsNoAudcForADistortionOutsideZeroToEOrWithoutADriver) {
    EXPECT_EQ(CFrequencyTables::GetDistortionAudc(Memory(), TrackerDriverVersion::PATCH16, -1), -1);
    EXPECT_EQ(CFrequencyTables::GetDistortionAudc(Memory(), TrackerDriverVersion::PATCH16, 0x10), -1);

    atari.ClearMemory();
    for (TrackerDriverVersion version : kAllVersions) {
        EXPECT_EQ(CFrequencyTables::GetDistortionAudc(Memory(), version, 0x0A), -1) << "driver " << (int)version;
    }
}

TEST_F(FrequencyTablesTest, VersionNoneHasNoLayoutAtAll) {
    // Not a driver, so neither layout applies - including the 15 kHz
    // addresses, which the Patch 16 branch otherwise answers from constants.
    for (auto mode : {CFrequencyTables::Mode::Clock64kHz, CFrequencyTables::Mode::Clock179MHz, CFrequencyTables::Mode::Clock15kHz,
                      CFrequencyTables::Mode::Join16Bit}) {
        EXPECT_EQ(CFrequencyTables::GetTableAddress(Memory(), TrackerDriverVersion::NONE, 0x0A, mode), 0) << "mode " << (int)mode;
    }
    EXPECT_FALSE(CFrequencyTables::HasDispatchTable(Memory(), TrackerDriverVersion::NONE));
}

TEST_F(FrequencyTablesTest, EachDriverIsAcceptedOnlyForItsOwnFamilysLayout) {
    // The check is a plausibility test on the bytes, not a version test, so
    // the thing worth asserting is that it separates the two families in the
    // drivers RMT really ships: a v1-family driver's $3208 holds its own
    // frequency-table data, far outside the $B0-$B4 a page needs, and the
    // Patch 16 driver loads nothing at all into the v1 family's $3182. Each
    // version gets its own CAtari so no earlier load can leave bytes behind.
    for (TrackerDriverVersion version : kAllVersions) {
        CAtari own;
        CAtariTrackerDriver ownDriver{own};
        ASSERT_GT(ownDriver.LoadRMTRoutines(version), 0) << "driver " << (int)version;
        const byte* memory = own.GetConstMemoryAt(0);

        EXPECT_TRUE(CFrequencyTables::HasDispatchTable(memory, version)) << "driver " << (int)version;

        const TrackerDriverVersion otherFamily =
            version == TrackerDriverVersion::PATCH16 ? TrackerDriverVersion::UNPATCHED : TrackerDriverVersion::PATCH16;
        EXPECT_FALSE(CFrequencyTables::HasDispatchTable(memory, otherFamily)) << "driver " << (int)version;
    }
}

TEST_F(FrequencyTablesTest, ThereIsNoTableWithoutADriverThatHasADispatchTable) {
    atari.ClearMemory();

    for (TrackerDriverVersion version : kAllVersions) {
        EXPECT_FALSE(CFrequencyTables::HasDispatchTable(Memory(), version)) << "driver " << (int)version;
        EXPECT_EQ(CFrequencyTables::GetTableAddress(Memory(), version, 0x0A, CFrequencyTables::Mode::Clock64kHz), 0)
            << "driver " << (int)version;
        EXPECT_EQ(CFrequencyTables::GetTableAddress(Memory(), version, 0x0A, CFrequencyTables::Mode::Clock179MHz), 0)
            << "driver " << (int)version;
    }

    // The Patch 16 15 kHz addresses are fixed and answer regardless - they are
    // not in the dispatch table, so its absence cannot veto them. The caller
    // decides whether that is useful.
    EXPECT_EQ(CFrequencyTables::GetTableAddress(Memory(), TrackerDriverVersion::PATCH16, 0x0A, CFrequencyTables::Mode::Clock15kHz),
              RMTPLAYR_FRQTABPURE_15KHZ);
}
