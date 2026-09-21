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

// BuildSongData() encodes song lines (numTracks bytes each) plus an optional
// 4-byte "goto" sequence: 0xFE, an unused filler byte (the byte count always
// matches numTracks, mirroring CSong::SongToAta()/AtaToSong()'s own 4-byte
// goto encoding for an 8-track song halved for 4 tracks here), then a
// little-endian absolute target address (relative to "start") that gets
// converted back to a "?line_NN" label reference.

TEST(BuildSongDataTest, PlainLinesWithNoGotoEmitsByteRows) {
    unsigned char buf[8] = { 1, 2, 3, 4, 5, 6, 7, 8 }; // 2 lines of 4 tracks
    CString code;

    int size = CASMFileBuilder::BuildSongData(code, "", buf, 0, 8, 0, 4, AssemblerFormat::ATASM);

    EXPECT_EQ(size, 8);
    EXPECT_STREQ(code,
        "\n\n; Song data\n?SongData"
        "\n?Line_00  {{byte}} $01,$02,$03,$04"
        "\n?Line_01  {{byte}} $05,$06,$07,$08"
        "\n");
}

TEST(BuildSongDataTest, GotoToLineZeroEmitsLineLabelReference) {
    // Line 0 (4 bytes), then a goto sequence (0xFE, unused filler, low byte,
    // high byte of target address 0) pointing back at line 0.
    unsigned char buf[8] = { 1, 2, 3, 4, 0xFE, 0x00, 0x00, 0x00 };
    CString code;

    int size = CASMFileBuilder::BuildSongData(code, "", buf, 0, 8, 0, 4, AssemblerFormat::ATASM);

    // The goto's filler + address bytes don't all count towards sizeSongLines
    // (only the 0xFE marker and the filler byte do; the 2 address bytes are
    // consumed purely for the jump calculation).
    EXPECT_EQ(size, 6);
    EXPECT_STREQ(code,
        "\n\n; Song data\n?SongData"
        "\n?Line_00  {{byte}} $01,$02,$03,$04"
        "\n?Line_01  {{byte}} $fe,$00,<?line_00,>?line_00"
        "\n");
}

TEST(BuildSongDataTest, MisalignedGotoTargetEmitsErrorComment) {
    // Same shape as above, but the target address (3) isn't a multiple of
    // numTracks (4) relative to offsetSong, so it can't map to a line
    // number. The error message's format string is
    // "$ % 04x[% x:% x]" - the space right after each '%' is consumed as
    // the (no-op, for 'x') space flag rather than printed literally, so
    // this behaves like "$ %04x[%x:%x]" with the visible spaces coming
    // only from the literal ones already in the string before each '%'.
    unsigned char buf[8] = { 1, 2, 3, 4, 0xFE, 0x00, 0x03, 0x00 };
    CString code;

    int size = CASMFileBuilder::BuildSongData(code, "", buf, 0, 8, 0, 4, AssemblerFormat::ATASM);

    EXPECT_EQ(size, 6);
    EXPECT_STREQ(code,
        "\n\n; Song data\n?SongData"
        "\n?Line_00  {{byte}} $01,$02,$03,$04"
        "\n?Line_01  {{byte}} $fe,$00; ERROR malformed file(song jump bad $ 0003[0:8])\n,<($3+?SongData),>($3+?SongData)"
        "\n");
}
