#include "gtest/gtest.h"

#include "Atari.h"

// CAtari::Init()/DeInit()/JSR() (real 6502 DLL interop, see AtariStub.cpp)
// and Init(bool ntsc) (calls CTuning::InitTuning(), hazardous - see
// TuningTests.cpp) are all avoided here. Everything else is a plain memory
// buffer, safe and cheap to construct and exercise directly.

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
