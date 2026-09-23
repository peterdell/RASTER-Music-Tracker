#include "gtest/gtest.h"

#include "AtariTrackerDriver.h"
#include "SongTypes.h"
#include "General.h"

// See plans/BROADER_SURVEY_PLAN.md's "AtariTrackerDriver.cpp remainder"
// candidate: LoadRMTRoutines()/Init()/Play()/SetPokey()/Silence() only need
// g_rmtinstr (already real), CAtari::JSR() (already a no-op stub - see
// AtariStub.cpp), IsSpecialProveMode() (real, trivial - see
// SongEditingStub.cpp), and CRmtAtariBinaries (real on-disk resource
// loading, already unlocked for ExportSAP_B_LZSS/ExportXEX_LZSS - see
// plans/SAP_LZSS_WAV_XEX_PLAN.md). CAtari/CAtariTrackerDriver are cheap to
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

namespace {
class AtariTrackerDriverTest : public ::testing::Test {
  protected:
    CAtari atari;
    CAtariTrackerDriver driver{atari};

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
