#include "gtest/gtest.h"

#include "AtariBinaries.h"
#include "AtariIO.h"
#include "AtariTrackerDriver.h"
#include "SongTypes.h"
#include "General.h"
#include "TuningTypes.h"

#include <vector>

// See plans/04_BROADER_SURVEY_PLAN.md's "AtariTrackerDriver.cpp remainder"
// candidate: LoadRMTRoutines()/Init()/Play()/SetPokey()/Silence() only need
// g_rmtinstr (already real), CAtari::JSR() (already a no-op stub - see
// AtariStub.cpp), IsSpecialProveMode() (real, trivial - see
// SongEditingStub.cpp), and CRmtAtariBinaries (real on-disk resource
// loading, already unlocked for ExportSAP_B_LZSS/ExportXEX_LZSS - see
// plans/05_SAP_LZSS_WAV_XEX_PLAN.md). CAtari/CAtariTrackerDriver are cheap to
// construct locally (see AtariTests.cpp), so every test here uses its own
// isolated instances rather than the real g_Atari/g_AtariTrackerDriver
// globals.
//
// LoadRMTRoutinesReturnsZeroForAMissingDriverVersion below caught a real
// bug while writing it: LoadRMTRoutines() used to ignore its own
// trackerDriverVersion parameter entirely, always passing the global
// g_trackerDriverVersion to GetTrackerDriverBinary() instead. Harmless in
// today's production code (both real call sites, Rmt.cpp/RmtView.cpp,
// always pass g_trackerDriverVersion as the argument anyway - so the bug
// never changed observable behavior), but fixed outright in
// AtariTrackerDriver.cpp since it made the parameter dead and the fix
// changes nothing for any real caller.

extern int g_rmtinstr[SONGTRACKS];
extern EditMode volatile g_prove;
extern TTuningSettings g_tuning;
extern TTuningRatios g_tuningRatios;

namespace {
class AtariTrackerDriverTest : public ::testing::Test {
  protected:
    CAtari atari;
    CAtariTrackerDriver driver{atari};

    // LoadRMTRoutines() regenerates the tuning tables after the load (the
    // driver binary carries a static copy it must not win with - plan 37,
    // T2), and CTuning::InitTuning() exits the process on an uninitialized
    // basetuning, so the tuning globals are set up like AtariTests.cpp does.
    void SetUp() override {
        g_tuning.Initialize(false); // PAL
        g_tuningRatios.Initialize();
    }

    void TearDown() override {
        g_prove = EditMode::EDIT_MODE;
    }
};
} // namespace

// --- LoadRMTRoutines ---

TEST_F(AtariTrackerDriverTest, LoadRMTRoutinesLoadsTheDefaultDriverBinaryIntoMemory) {
    int bytesRead = driver.LoadRMTRoutines(TrackerDriverVersion::PATCH16);

    EXPECT_GT(bytesRead, 0);
    // rmt/resources/drivers/rmt_driver_v6.obx's first real block is
    // fromAddr=$3200, toAddr=$3245, first data byte 0x80 - hand-verified
    // against the checked-in file's own bytes.
    EXPECT_EQ(driver.GetByteAt(0x3200), 0x80);
}

TEST_F(AtariTrackerDriverTest, LoadRMTRoutinesReturnsZeroForAMissingDriverVersion) {
    // TrackerDriverVersion::NONE has no matching rmt_driver_v0.obx file.
    EXPECT_EQ(driver.LoadRMTRoutines(TrackerDriverVersion::NONE), 0);
}

TEST_F(AtariTrackerDriverTest, LoadRMTRoutinesRegeneratesTheTuningTablesOverTheDriversStaticCopy) {
    // The driver binary ships its own copy of the table page, and the load
    // writes it into the Atari's memory - so the regeneration after the load
    // must win, as it did in RMT 1.34.00 (plan 37, T1/T2). The two copies are
    // deliberately equal at the default settings (T7), which makes a changed
    // setting the only tell: doubling the base tuning halves every frequency,
    // taking the bell-1.79 table's first byte from the shipped $D6 to $69.
    g_tuning.basetuning *= 2;

    EXPECT_GT(driver.LoadRMTRoutines(TrackerDriverVersion::PATCH16), 0);

    EXPECT_EQ(driver.GetByteAt(RMT_FRQTABLES + 0x040), 0x69);
}

TEST_F(AtariTrackerDriverTest, TheRegeneratedPageMatchesTheDriverBinarysOwnCopyAtTheDefaultSettings) {
    // asm/Patch-16/rmtdata.a65's page is kept equal to what the generator
    // produces for the PAL defaults, so that a module assembled with the
    // standalone player sounds as it did to the composer in RMT (plan 37,
    // T7). This compares against the shipped binary itself, and
    // check-drivers.yml pins that binary to the assembler source, which
    // closes the chain rmtdata.a65 -> obx -> generator. The sawtooth range
    // ($B500-$B57F) is the one part no formula produces: the generator holds
    // those bytes as constants and copies them (T6).
    byte* bin;
    WORD size;
    ASSERT_TRUE(CRmtAtariBinaries::GetTrackerDriverBinary(TrackerDriverVersion::PATCH16, bin, size));
    std::vector<byte> shipped(0x10000, 0);
    WORD min, max;
    ASSERT_GT(CAtariIO::LoadDataAsBinaryFile(bin, size, shipped.data(), min, max), 0);

    EXPECT_GT(driver.LoadRMTRoutines(TrackerDriverVersion::PATCH16), 0);

    for (int offset = 0x000; offset < 0x600; offset++) {
        EXPECT_EQ(driver.GetByteAt(RMT_FRQTABLES + offset), shipped[RMT_FRQTABLES + offset]) << "offset " << offset;
    }
}

// --- AreRoutinesLoaded ---
// The return value above was ignored by all three production call sites, so a
// driver file that wasn't there left the emulated Atari playing silence and
// every export quiet, with nothing said about it. The state is asked for by
// name now, and reported (Rmt.cpp at start-up, RmtView.cpp when the Options
// switch the version, CScriptRunner::Export() before it writes a sound file).

TEST_F(AtariTrackerDriverTest, NoRoutinesAreLoadedBeforeTheFirstLoad) {
    EXPECT_FALSE(driver.AreRoutinesLoaded());
}

TEST_F(AtariTrackerDriverTest, AreRoutinesLoadedFollowsTheLastLoad) {
    EXPECT_GT(driver.LoadRMTRoutines(TrackerDriverVersion::PATCH16), 0);
    EXPECT_TRUE(driver.AreRoutinesLoaded());

    // Switching to a version whose file is missing clears it again - the case
    // the Options dialog and the script's "set driver" now report.
    EXPECT_EQ(driver.LoadRMTRoutines(TrackerDriverVersion::NONE), 0);
    EXPECT_FALSE(driver.AreRoutinesLoaded());
}

// --- the driver's file path and the message naming it ---

TEST(RmtAtariBinariesTest, TheDriverFilePathIsTheVersionsFileUnderResourcesDrivers) {
    CString filePath = CRmtAtariBinaries::GetTrackerDriverFilePath(TrackerDriverVersion::PATCH16);

    EXPECT_NE(filePath.Find("resources"), -1) << (LPCTSTR)filePath;
    EXPECT_NE(filePath.Find("drivers"), -1) << (LPCTSTR)filePath;
    EXPECT_NE(filePath.Find("rmt_driver_v6.obx"), -1) << (LPCTSTR)filePath;
}

TEST(RmtAtariBinariesTest, TheMessageNamesTheFileAndSaysWhatIsLost) {
    CString message = CRmtAtariBinaries::GetMissingTrackerDriverMessage(TrackerDriverVersion::PATCH8);

    EXPECT_NE(message.Find("rmt_driver_v5.obx"), -1) << (LPCTSTR)message;
    EXPECT_NE(message.Find("silent"), -1) << (LPCTSTR)message;
}

// --- Init ---

TEST_F(AtariTrackerDriverTest, InitResetsEveryChannelsInstrumentAndReturnsZero) {
    for (int i = 0; i < SONGTRACKS; i++) {
        g_rmtinstr[i] = 5;
    }

    EXPECT_EQ(driver.Init(), 0); // 'a' register stays 0 - JSR() is a no-op stub

    for (int i = 0; i < SONGTRACKS; i++) {
        EXPECT_EQ(g_rmtinstr[i], -1);
    }
}

// --- Play / SetPokey / Silence ---
// All three only ever call the already-stubbed no-op CAtari::JSR()/
// C6502::JSR() - characterized as non-crashing rather than asserting on
// any observable state change, since there is none to observe.

TEST_F(AtariTrackerDriverTest, PlayDoesNotCrashInNormalMode) {
    g_prove = EditMode::EDIT_MODE;
    driver.Play();
}

TEST_F(AtariTrackerDriverTest, PlayDoesNotCrashInSpecialProveMode) {
    g_prove = EditMode::POKEY_EXPLORER_MODE;
    driver.Play();
}

TEST_F(AtariTrackerDriverTest, SetPokeyDoesNotCrash) {
    driver.SetPokey();
}

TEST_F(AtariTrackerDriverTest, SilenceDoesNotCrash) {
    driver.Silence();
}
