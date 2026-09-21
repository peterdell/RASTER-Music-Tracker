#include "gtest/gtest.h"

#include "Tracks.h"

namespace {
    constexpr CTracks::TrackNumber kTrackA = 0;
    constexpr CTracks::TrackNumber kTrackB = 1;
}

class TracksTest : public ::testing::Test {
protected:
    CTracks tracks;

    void SetUp() override {
        tracks.InitTracks();
    }
};

TEST_F(TracksTest, NewlyInitializedTrackIsEmpty) {
    EXPECT_TRUE(tracks.IsEmptyTrack(kTrackA));
}

TEST_F(TracksTest, ClearTrackResetsFieldsToInvalid) {
    TTrack* tr = tracks.GetTrack(kTrackA);
    tr->note[0] = 5;
    tr->volume[0] = 10;
    tracks.ClearTrack(kTrackA);
    EXPECT_EQ(tracks.GetNote(kTrackA, 0), -1);
    EXPECT_EQ(tracks.GetVol(kTrackA, 0), -1);
}

TEST_F(TracksTest, InsertLineShiftsSubsequentLinesDownAndClearsInsertedLine) {
    TTrack* tr = tracks.GetTrack(kTrackA);
    tr->len = 4;
    tr->note[0] = 1; tr->note[1] = 2; tr->note[2] = 3;

    tracks.InsertLine(kTrackA, 1);

    EXPECT_EQ(tr->note[0], 1);
    EXPECT_EQ(tr->note[1], -1);
    EXPECT_EQ(tr->note[2], 2);
    EXPECT_EQ(tr->note[3], 3);
}

TEST_F(TracksTest, DeleteLineShiftsSubsequentLinesUpAndClearsLastLine) {
    TTrack* tr = tracks.GetTrack(kTrackA);
    tr->len = 4;
    tr->note[0] = 1; tr->note[1] = 2; tr->note[2] = 3; tr->note[3] = 4;

    tracks.DeleteLine(kTrackA, 1);

    EXPECT_EQ(tr->note[0], 1);
    EXPECT_EQ(tr->note[1], 3);
    EXPECT_EQ(tr->note[2], 4);
    EXPECT_EQ(tr->note[3], -1);
}

TEST_F(TracksTest, CalculateNotEmptyDetectsNoteData) {
    EXPECT_FALSE(tracks.CalculateNotEmpty(kTrackA));
    tracks.GetTrack(kTrackA)->note[0] = 5;
    EXPECT_TRUE(tracks.CalculateNotEmpty(kTrackA));
}

TEST_F(TracksTest, CompareTracksDetectsDifference) {
    EXPECT_TRUE(tracks.CompareTracks(kTrackA, kTrackB)); // both freshly cleared, identical
    tracks.GetTrack(kTrackB)->note[0] = 3;
    EXPECT_FALSE(tracks.CompareTracks(kTrackA, kTrackB));
}

// Characterizes a moderately intricate cleanup pass: a "volume 0" line with no
// note/instrument sitting directly between two other zero-volume lines is
// redundant and gets cancelled (set to -1), while a zero-volume line that
// *does* carry a note/instrument (line 1 here) is dropped once a second
// zero-volume line closes the gap after it.
TEST_F(TracksTest, TrackOptimizeVol0RemovesRedundantZeroVolumeEntries) {
    TTrack* tr = tracks.GetTrack(kTrackA);
    tr->len = 4;
    tr->volume[0] = 0;
    tr->note[1] = 5; tr->instr[1] = 0; tr->volume[1] = 0;
    tr->volume[2] = 0;

    tracks.TrackOptimizeVol0(kTrackA);

    EXPECT_EQ(tr->volume[0], 0);
    EXPECT_EQ(tr->note[1], -1);
    EXPECT_EQ(tr->instr[1], -1);
    EXPECT_EQ(tr->volume[1], -1);
    EXPECT_EQ(tr->volume[2], -1);
}

TEST(TracksModifiedValueTest, GetModifiedNoteTransposesWithinRange) {
    CTracks tracks;
    EXPECT_EQ(tracks.GetModifiedNote(5, 3), 8);
}

TEST(TracksModifiedValueTest, GetModifiedNoteWrapsBelowZero) {
    CTracks tracks;
    EXPECT_EQ(tracks.GetModifiedNote(0, -1), 11);
}

TEST(TracksModifiedValueTest, GetModifiedNoteWrapsAboveNotesnum) {
    CTracks tracks;
    EXPECT_EQ(tracks.GetModifiedNote(59, 5), 52);
}

TEST(TracksModifiedValueTest, GetModifiedNoteRejectsInvalidNote) {
    CTracks tracks;
    EXPECT_EQ(tracks.GetModifiedNote(-1, 3), -1);
}

TEST(TracksModifiedValueTest, GetModifiedInstrWrapsAroundInstrsnum) {
    CTracks tracks;
    EXPECT_EQ(tracks.GetModifiedInstr(0, -1), 63);
    EXPECT_EQ(tracks.GetModifiedInstr(63, 5), 4);
}

TEST(TracksModifiedValueTest, GetModifiedVolumePScalesAndClamps) {
    CTracks tracks;
    EXPECT_EQ(tracks.GetModifiedVolumeP(10, 100), 10);
    EXPECT_EQ(tracks.GetModifiedVolumeP(10, 50), 5);
    EXPECT_EQ(tracks.GetModifiedVolumeP(15, 200), 15); // clamped to MAXVOLUME
    EXPECT_EQ(tracks.GetModifiedVolumeP(-1, 100), -1);
    EXPECT_EQ(tracks.GetModifiedVolumeP(10, 0), 0);
}

// TrackToAta()/AtaToTrack() round-trip the compact on-Atari track encoding.
// Expected byte values were hand-derived from the format comments in
// IO_Tracks.cpp, then confirmed to round-trip back to the original track data.
class TrackAtaFormatTest : public ::testing::Test {
protected:
    CTracks tracks;

    void SetUp() override {
        tracks.InitTracks();
    }
};

TEST_F(TrackAtaFormatTest, SingleNoteEncodesAndDecodesRoundTrip) {
    TTrack* src = tracks.GetTrack(kTrackA);
    src->len = 1;
    src->note[0] = 0; src->instr[0] = 0; src->volume[0] = 10;

    unsigned char buffer[16] = { 0 };
    int size = tracks.TrackToAta(kTrackA, buffer, sizeof(buffer));

    ASSERT_EQ(size, 3);
    EXPECT_EQ(buffer[0], 0x80);
    EXPECT_EQ(buffer[1], 0x02);
    EXPECT_EQ(buffer[2], 0xFF); // end marker

    ASSERT_TRUE(tracks.AtaToTrack(buffer, size, kTrackB));
    TTrack* decoded = tracks.GetTrack(kTrackB);
    EXPECT_EQ(decoded->len, 1);
    EXPECT_EQ(decoded->note[0], 0);
    EXPECT_EQ(decoded->instr[0], 0);
    EXPECT_EQ(decoded->volume[0], 10);
}

TEST_F(TrackAtaFormatTest, LeadingPauseThenNoteEncodesAndDecodesRoundTrip) {
    TTrack* src = tracks.GetTrack(kTrackA);
    src->len = 2;
    // Line 0 stays fully empty (a 1-beat pause); line 1 has a note.
    src->note[1] = 5; src->instr[1] = 2; src->volume[1] = 7;

    unsigned char buffer[16] = { 0 };
    int size = tracks.TrackToAta(kTrackA, buffer, sizeof(buffer));

    ASSERT_EQ(size, 4);
    EXPECT_EQ(buffer[0], 0x7E); // 1-beat pause
    EXPECT_EQ(buffer[1], 0xC5);
    EXPECT_EQ(buffer[2], 0x09);
    EXPECT_EQ(buffer[3], 0xFF); // end marker

    ASSERT_TRUE(tracks.AtaToTrack(buffer, size, kTrackB));
    TTrack* decoded = tracks.GetTrack(kTrackB);
    EXPECT_EQ(decoded->len, 2);
    EXPECT_EQ(decoded->note[0], -1);
    EXPECT_EQ(decoded->note[1], 5);
    EXPECT_EQ(decoded->instr[1], 2);
    EXPECT_EQ(decoded->volume[1], 7);
}
