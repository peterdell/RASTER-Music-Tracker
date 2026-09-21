#include "gtest/gtest.h"

#include "StdAfx.h" // ASMFileBuilder.h uses CString but doesn't include it itself
#include "ASMFileBuilder.h"

#include <vector>

TEST(BuildInstrumentDataTest, NoLabelEmitsPlainByteList) {
    unsigned char buf[4] = { 0x01, 0x02, 0x03, 0x04 };
    int info[4] = { 0, 0, 0, 0 };
    CString code;

    int size = CASMFileBuilder::BuildInstrumentData(code, "", buf, 0, 4, info, AssemblerFormat::ATASM);

    EXPECT_EQ(size, 4);
    EXPECT_STREQ(code, "\n\n; Instrument data\n\n    {{byte}} $01,$02,$03,$04");
}

TEST(BuildInstrumentDataTest, NonEmptyInfoEntryInsertsLabelAndRestartsRow) {
    unsigned char buf[3] = { 0xAA, 0xBB, 0xCC };
    int info[3] = { 0, 5, 0 }; // info[1]=5 -> label "?Instrument_4" (5-1), row restarts
    CString code;

    int size = CASMFileBuilder::BuildInstrumentData(code, "", buf, 0, 3, info, AssemblerFormat::ATASM);

    EXPECT_EQ(size, 3);
    EXPECT_STREQ(code, "\n\n; Instrument data\n\n    {{byte}} $aa\n?Instrument_4\n    {{byte}} $bb,$cc");
    EXPECT_EQ(info[1], 0); // consumed
}

TEST(BuildInstrumentDataTest, NonEmptyLabelEmitsOrgLineInEachAssemblerFormat) {
    unsigned char buf[1] = { 0x00 };
    int info[1] = { 0 };
    CString atasmCode, xasmCode;

    CASMFileBuilder::BuildInstrumentData(atasmCode, "INSTR_START", buf, 0, 1, info, AssemblerFormat::ATASM);
    EXPECT_STREQ(atasmCode, "\n\n; Instrument data\n* = INSTR_START\n\n    {{byte}} $00");

    info[0] = 0;
    CASMFileBuilder::BuildInstrumentData(xasmCode, "INSTR_START", buf, 0, 1, info, AssemblerFormat::XASM);
    EXPECT_STREQ(xasmCode, "\n\n; Instrument data\norg INSTR_START\n\n    {{byte}} $00");
}

// BuildTracksData()'s final validity check scans track_pos[0..65535]
// unconditionally (not just the [from,to) range actually processed), so the
// array passed in must always have at least 65536 entries or this reads out
// of bounds - a real fragility in the function's contract, not something a
// caller would guess from its signature. Characterized here, not changed.
namespace {
    constexpr int kTrackPosSize = 65536;
}

TEST(BuildTracksDataTest, NoLabelEmitsPlainByteList) {
    unsigned char buf[4] = { 0x11, 0x22, 0x33, 0x44 };
    std::vector<int> trackPos(kTrackPosSize, 0);
    CString code;

    int size = CASMFileBuilder::BuildTracksData(code, "", buf, 0, 4, trackPos.data(), AssemblerFormat::ATASM);

    EXPECT_EQ(size, 4);
    EXPECT_STREQ(code, "\n\n; Track data\n    {{byte}} $11,$22,$33,$44");
}

TEST(BuildTracksDataTest, NonZeroTrackPosOutsideProcessedRangeFailsValidation) {
    unsigned char buf[2] = { 0x01, 0x02 };
    std::vector<int> trackPos(kTrackPosSize, 0);
    trackPos[100] = 1; // left set outside the [0,2) range actually consumed
    CString code;

    int size = CASMFileBuilder::BuildTracksData(code, "", buf, 0, 2, trackPos.data(), AssemblerFormat::ATASM);

    EXPECT_EQ(size, 0); // signals failure: not every entry was consumed back to 0
}
