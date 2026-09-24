#include "gtest/gtest.h"

#include <memory>

#include "Tracks.h"

namespace {
constexpr CTracks::TrackNumber kTrackA = 0;
constexpr CTracks::TrackNumber kTrackB = 1;
} // namespace

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
    tr->note[0] = 1;
    tr->note[1] = 2;
    tr->note[2] = 3;

    tracks.InsertLine(kTrackA, 1);

    EXPECT_EQ(tr->note[0], 1);
    EXPECT_EQ(tr->note[1], -1);
    EXPECT_EQ(tr->note[2], 2);
    EXPECT_EQ(tr->note[3], 3);
}

TEST_F(TracksTest, DeleteLineShiftsSubsequentLinesUpAndClearsLastLine) {
    TTrack* tr = tracks.GetTrack(kTrackA);
    tr->len = 4;
    tr->note[0] = 1;
    tr->note[1] = 2;
    tr->note[2] = 3;
    tr->note[3] = 4;

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
    tr->note[1] = 5;
    tr->instr[1] = 0;
    tr->volume[1] = 0;
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
    src->note[0] = 0;
    src->instr[0] = 0;
    src->volume[0] = 10;

    unsigned char buffer[16] = {0};
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
    src->note[1] = 5;
    src->instr[1] = 2;
    src->volume[1] = 7;

    unsigned char buffer[16] = {0};
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

// --- TrackBuildLoop ---
// Searches for the earliest/shortest repeating suffix (at least 2 lines,
// matching an earlier segment of the track, with more than 1 non-empty line
// inside it) and, if found, truncates the track to a loop instead. Golden
// master values below were captured by running the actual implementation,
// not hand-derived, since the triple-nested search is too easy to
// mis-trace by hand.

class TrackBuildLoopTest : public ::testing::Test {
  protected:
    CTracks tracks;

    void SetUp() override {
        tracks.InitTracks();
        tracks.SetMaxTrackLength(4);
    }
};

TEST_F(TrackBuildLoopTest, ReturnsZeroForEmptyTrack) {
    TTrack* tr = tracks.GetTrack(kTrackA);
    tr->len = 4;
    EXPECT_EQ(tracks.TrackBuildLoop(kTrackA), 0);
}

TEST_F(TrackBuildLoopTest, ReturnsZeroWhenTrackAlreadyHasALoop) {
    TTrack* tr = tracks.GetTrack(kTrackA);
    tr->len = 4;
    tr->go = 0;
    tr->note[0] = 5; // non-empty, so the empty-track guard doesn't short-circuit first
    EXPECT_EQ(tracks.TrackBuildLoop(kTrackA), 0);
}

TEST_F(TrackBuildLoopTest, ReturnsZeroWhenNotFullLength) {
    TTrack* tr = tracks.GetTrack(kTrackA);
    tr->len = 3; // maxTrackLength is 4
    tr->note[0] = 5;
    EXPECT_EQ(tracks.TrackBuildLoop(kTrackA), 0);
}

// Lines 2-3 exactly repeat lines 0-1 (2 distinct non-empty lines in the
// repeated segment), so a 2-line loop back to line 0 should be found.
TEST_F(TrackBuildLoopTest, FindsASimpleRepeatingSuffix) {
    TTrack* tr = tracks.GetTrack(kTrackA);
    tr->len = 4;
    tr->note[0] = 5;
    tr->instr[0] = 0;
    tr->volume[0] = 10;
    tr->note[1] = 6;
    tr->instr[1] = 0;
    tr->volume[1] = 10;
    tr->note[2] = 5;
    tr->instr[2] = 0;
    tr->volume[2] = 10;
    tr->note[3] = 6;
    tr->instr[3] = 0;
    tr->volume[3] = 10;

    EXPECT_EQ(tracks.TrackBuildLoop(kTrackA), 2);
    EXPECT_EQ(tr->len, 2);
    EXPECT_EQ(tr->go, 0);
}

TEST_F(TrackBuildLoopTest, ReturnsZeroWhenNoRepeatingPatternExists) {
    TTrack* tr = tracks.GetTrack(kTrackA);
    tr->len = 4;
    tr->note[0] = 1;
    tr->note[1] = 2;
    tr->note[2] = 3;
    tr->note[3] = 4;

    EXPECT_EQ(tracks.TrackBuildLoop(kTrackA), 0);
}

// The whole suffix [1,4) matches [2,5)... rather, lines 1-3 are all fully
// empty and exactly match each other, but the only non-empty line (line 0)
// falls outside every candidate loop region, so no candidate ever has more
// than 1 non-empty line inside it - the "not just 0-1 nonzero lines" guard
// should reject every candidate, leaving the track unmodified.
TEST_F(TrackBuildLoopTest, IgnoresAnAllEmptyMatchingSuffix) {
    tracks.SetMaxTrackLength(5);
    TTrack* tr = tracks.GetTrack(kTrackA);
    tr->len = 5;
    tr->note[0] = 5; // keeps the track non-empty; lines 1-4 stay fully cleared (-1)

    EXPECT_EQ(tracks.TrackBuildLoop(kTrackA), 0);
    EXPECT_EQ(tr->len, 5);
    EXPECT_EQ(tr->go, -1);
}

// --- TrackExpandLoop ---
// The inverse of TrackBuildLoop: expands a track with a go-loop back out to
// full length by cyclically repeating the [go, len) segment.

class TrackExpandLoopTest : public ::testing::Test {
  protected:
    CTracks tracks;

    void SetUp() override {
        tracks.InitTracks();
        tracks.SetMaxTrackLength(6);
    }
};

TEST_F(TrackExpandLoopTest, ReturnsZeroForEmptyTrack) {
    EXPECT_EQ(tracks.TrackExpandLoop(kTrackA), 0);
}

TEST_F(TrackExpandLoopTest, ReturnsZeroWhenThereIsNoLoop) {
    TTrack* tr = tracks.GetTrack(kTrackA);
    tr->len = 2;
    tr->go = -1;
    tr->note[0] = 5;
    EXPECT_EQ(tracks.TrackExpandLoop(kTrackA), 0);
}

TEST_F(TrackExpandLoopTest, NullTrackPointerOverloadReturnsZero) {
    EXPECT_EQ(tracks.TrackExpandLoop(static_cast<TTrack*>(nullptr)), 0);
}

// Cyclically repeats the 2-line [0,2) loop segment out to the full 6-line
// track length, including reading back its own just-written expansion
// (line 4 copies from line 2, which this same call already wrote).
TEST_F(TrackExpandLoopTest, ExpandsALoopCyclicallyToFullLength) {
    TTrack* tr = tracks.GetTrack(kTrackA);
    tr->len = 2;
    tr->go = 0;
    tr->note[0] = 5;
    tr->instr[0] = 0;
    tr->volume[0] = 10;
    tr->note[1] = 6;
    tr->instr[1] = 0;
    tr->volume[1] = 10;

    EXPECT_EQ(tracks.TrackExpandLoop(kTrackA), 4);
    EXPECT_EQ(tr->len, 6);
    EXPECT_EQ(tr->go, -1);
    int expectedNotes[6] = {5, 6, 5, 6, 5, 6};
    for (int i = 0; i < 6; i++) {
        EXPECT_EQ(tr->note[i], expectedNotes[i]) << "at line " << i;
    }
}

// --- GetTracksAll / SetTracksAll ---
// A plain deep-copy round trip - used by CUndo (not yet ported) to snapshot
// and restore every track at once.
//
// TTracksAll is ~1MB (254 TTrack entries at ~4KB each) - too large for a
// stack-allocated local (the first version of this test crashed with a
// stack overflow), so it's heap-allocated here via unique_ptr, matching how
// CTracks itself always heap-allocates m_track.

TEST(TracksAllTest, RoundTripsMaxTrackLengthAndAllTrackData) {
    CTracks tracks;
    tracks.InitTracks();
    tracks.SetMaxTrackLength(4);
    tracks.GetTrack(kTrackA)->note[0] = 5;
    tracks.GetTrack(kTrackB)->note[1] = 6;

    auto saved = std::make_unique<TTracksAll>();
    tracks.GetTracksAll(saved.get());

    // Mutate the live tracks after the snapshot, to prove SetTracksAll()
    // actually overwrites rather than SetUp() coincidentally matching.
    tracks.SetMaxTrackLength(10);
    tracks.GetTrack(kTrackA)->note[0] = 99;

    tracks.SetTracksAll(saved.get());

    EXPECT_EQ(tracks.GetMaxTrackLength(), 4);
    EXPECT_EQ(tracks.GetNote(kTrackA, 0), 5);
    EXPECT_EQ(tracks.GetNote(kTrackB, 1), 6);
}

// --- ModifyTrack ---
// Applies a transposition/instrument-shift/volume-percentage change across a
// line range, optionally filtered to only lines carrying a specific
// instrument (instrnumonly), using the already-tested GetModifiedNote/
// GetModifiedInstr/GetModifiedVolumeP.

class ModifyTrackTest : public ::testing::Test {
  protected:
    CTracks tracks;

    void SetUp() override {
        tracks.InitTracks();
    }
};

TEST_F(ModifyTrackTest, ReturnsFalseForNullTrack) {
    EXPECT_FALSE(tracks.ModifyTrack(nullptr, 0, 0, -1, 0, 0, 100));
}

TEST_F(ModifyTrackTest, TransposesNotesAcrossRangeWhenInstrnumonlyIsNegative) {
    TTrack* tr = tracks.GetTrack(kTrackA);
    tr->note[0] = 0;
    tr->instr[0] = 2;
    tr->volume[0] = 10;
    tr->note[1] = 1;
    tr->instr[1] = 2;
    tr->volume[1] = 10;

    EXPECT_TRUE(tracks.ModifyTrack(tr, 0, 1, -1, 3, 0, 100));

    EXPECT_EQ(tr->note[0], 3); // GetModifiedNote(0, 3)
    EXPECT_EQ(tr->note[1], 4); // GetModifiedNote(1, 3)
}

// instrnumonly filters by the *active* instrument at each line (the most
// recent instr[] value seen at or after `from`, tracked as the loop runs) -
// not by whether that exact line itself sets an instrument.
TEST_F(ModifyTrackTest, FiltersByActiveInstrumentNumberOnly) {
    TTrack* tr = tracks.GetTrack(kTrackA);
    tr->note[0] = 0;
    tr->instr[0] = 1; // active instrument becomes 1 from here on
    tr->volume[0] = 10;
    tr->note[1] = 1; // no instrument change on this line - stays active instrument 1
    tr->instr[1] = -1;
    tr->volume[1] = 10;
    tr->note[2] = 2;
    tr->instr[2] = 5; // active instrument becomes 5 from here on
    tr->volume[2] = 10;

    EXPECT_TRUE(tracks.ModifyTrack(tr, 0, 2, 1, 3, 0, 100));

    EXPECT_EQ(tr->note[0], 3); // instrument 1 active - modified
    EXPECT_EQ(tr->note[1], 4); // instrument 1 still active - modified
    EXPECT_EQ(tr->note[2], 2); // instrument 5 active - left unmodified
}

TEST_F(ModifyTrackTest, ClampsToWithinTrackLength) {
    TTrack* tr = tracks.GetTrack(kTrackA);
    tr->note[255] = 0;
    tr->instr[255] = 0;
    tr->volume[255] = 10;

    // "to" of 300 is past TRACKLEN (256) and should clamp to 255, not
    // crash writing out of bounds.
    EXPECT_TRUE(tracks.ModifyTrack(tr, 255, 300, -1, 3, 0, 100));

    EXPECT_EQ(tr->note[255], 3); // GetModifiedNote(0, 3)
}
