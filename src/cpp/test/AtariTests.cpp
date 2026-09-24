#include "gtest/gtest.h"

#include "Atari.h"
#include "TuningTypes.h"

// CAtari::Init()/DeInit()/JSR() (real 6502 DLL interop, see AtariStub.cpp)
// are avoided here - genuinely hazardous, real native DLL/hardware access.
// Everything else, including Init(bool) (see below), is safe and cheap to
// exercise directly.

extern TTuningSettings g_tuning;
extern TTuningRatios g_tuningRatios;

TEST(AtariTest, NewInstanceHasZeroedMemoryAndIsNotNtsc) {
    CAtari atari;
    EXPECT_EQ(atari.GetByteAt(0), 0);
    EXPECT_EQ(atari.GetByteAt(CAtari::MEMORY_SIZE - 1), 0);
    EXPECT_FALSE(atari.IsNTSC());
}

TEST(AtariTest, SetByteAtAndGetByteAtRoundTrip) {
    CAtari atari;
    atari.SetByteAt(0x1234, 0xAB);
    EXPECT_EQ(atari.GetByteAt(0x1234), 0xAB);
    EXPECT_EQ(atari.GetByteAt(0x1233), 0); // neighboring bytes untouched
}

TEST(AtariTest, GetMemoryAtReturnsPointerIntoTheSameBuffer) {
    CAtari atari;
    atari.SetByteAt(0x4000, 0x42);
    EXPECT_EQ(*atari.GetMemoryAt(0x4000), 0x42);
    EXPECT_EQ(*atari.GetConstMemoryAt(0x4000), 0x42);

    *atari.GetMemoryAt(0x4001) = 0x99;
    EXPECT_EQ(atari.GetByteAt(0x4001), 0x99);
}

TEST(AtariTest, ClearMemoryResetsAllBytes) {
    CAtari atari;
    atari.SetByteAt(0x100, 0xFF);
    atari.ClearMemory();
    EXPECT_EQ(atari.GetByteAt(0x100), 0);
}

TEST(AtariTest, StaticClockFrequencyMatchesRegion) {
    EXPECT_EQ(CAtari::GetClockFrequency(true), CAtari::FREQ_17_NTSC);
    EXPECT_EQ(CAtari::GetClockFrequency(false), CAtari::FREQ_17_PAL);
    EXPECT_EQ(CAtari::GetClockFrequency(true), 1789773);
    EXPECT_EQ(CAtari::GetClockFrequency(false), 1773447);
}

TEST(AtariTest, StaticFrameCycleCountMatchesRegion) {
    EXPECT_EQ(CAtari::GetFrameCycleCount(true), 114 * 262);
    EXPECT_EQ(CAtari::GetFrameCycleCount(false), 114 * 312);
}

TEST(AtariTest, InstanceClockAndCycleCountFollowIsNtscDefaultingToPal) {
    CAtari atari; // IsNTSC() is FALSE by default (see the m_ntsc fix in Atari.h)
    EXPECT_EQ(atari.GetClockFrequency(), CAtari::FREQ_17_PAL);
    EXPECT_EQ(atari.GetFrameCycleCount(), 114 * 312);
}

// CAtari::Init(bool) calls g_Tuning.InitTuning() to populate *this
// instance's own* memory (via GetMemoryAt(), not the shared g_Atari global)
// with pitch tables - safe as long as g_tuning.basetuning is set first (the
// same guard already characterized in TuningTests.cpp). These reuse the
// exact golden-master byte values already captured there for the
// Distortion-2 (Bell) table at RMT_FRQTABLES+0x000/0x001, since the point
// here is to characterize Init(bool)'s own wiring (right clock, right
// instance, right memory offset) - InitTuning()'s arithmetic itself is
// already covered.
TEST(AtariTest, InitPalPopulatesOwnMemoryWithTuningTables) {
    g_tuning.Initialize(false); // PAL
    g_tuningRatios.Initialize();

    CAtari atari;
    atari.Init(false);

    EXPECT_FALSE(atari.IsNTSC());
    EXPECT_EQ(atari.GetClockFrequency(), CAtari::FREQ_17_PAL);
    EXPECT_EQ(atari.GetByteAt(RMT_FRQTABLES + 0x000), 62);
    EXPECT_EQ(atari.GetByteAt(RMT_FRQTABLES + 0x001), 58);
}

TEST(AtariTest, InitNtscPopulatesOwnMemoryWithTuningTables) {
    g_tuning.Initialize(true); // NTSC
    g_tuningRatios.Initialize();

    CAtari atari;
    atari.Init(true);

    EXPECT_TRUE(atari.IsNTSC());
    EXPECT_EQ(atari.GetClockFrequency(), CAtari::FREQ_17_NTSC);
    EXPECT_EQ(atari.GetByteAt(RMT_FRQTABLES + 0x000), 62);
    EXPECT_EQ(atari.GetByteAt(RMT_FRQTABLES + 0x001), 58);
}
