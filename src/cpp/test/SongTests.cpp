#include "gtest/gtest.h"

#include "Song.h"

extern int g_tracks4_8; // TODO Move out (see Instruments.cpp/IO_Instruments.cpp)

// Only the CSong methods implemented in SongCore.cpp are exercised here (see
// plans/NOTES.md for the Song.cpp/IO_Song.cpp triage) - everything else
// needs Global.h's much larger dependency graph and stays untested until a
// deliberate, larger split of the rest of Song.cpp happens.

class SongCoreTest : public ::testing::Test {
protected:
    CSong song;

    void SetUp() override {
        g_tracks4_8 = 4;
        // CSong's constructor doesn't fully reset song data (that's
        // ClearSong()'s job, not moved to SongCore.cpp), and the default
        // member initializers added to Song.h zero everything rather than
        // marking song lines/goto slots "empty" (-1) - so tests that care
        // about m_song/m_songgo content explicitly blank them out first via
        // the public GetSong()/GetSongGo() accessors.
        auto* songArr = song.GetSong();
        auto* songGoArr = song.GetSongGo();
        for (int line = 0; line < SONGLEN; line++) {
            for (int col = 0; col < SONGTRACKS; col++) (*songArr)[line][col] = -1;
            (*songGoArr)[line] = -1;
        }
    }

    void TearDown() override {
        g_tracks4_8 = 4; // restore the default
    }
};

TEST_F(SongCoreTest, NewSongHasEmptyNameAndZeroedCursorState) {
    EXPECT_STREQ(song.GetName(), "");
    EXPECT_EQ(song.GetActiveColumn(), 0);
    EXPECT_EQ(song.GetActiveLine(), 0);
    EXPECT_EQ(song.GetPlayLine(), 0);
    EXPECT_FALSE(song.IsNTSC());
}

TEST_F(SongCoreTest, GetTracksAndIsStereoFollowGlobalTrackCount) {
    g_tracks4_8 = 4;
    EXPECT_EQ(song.GetTracks(), 4);
    EXPECT_FALSE(song.IsStereo());

    g_tracks4_8 = 8;
    EXPECT_EQ(song.GetTracks(), 8);
    EXPECT_TRUE(song.IsStereo());
}

TEST_F(SongCoreTest, GetInstrumentSpeedReflectsSongInfoPars) {
    TInfo info = {};
    song.GetSongInfoPars(&info);
    info.instrspeed = 3;
    song.SetSongInfoPars(&info);

    EXPECT_EQ(song.GetInstrumentSpeed(), 3);
}

TEST_F(SongCoreTest, ActiveAndPlayLineSettersRoundTrip) {
    song.SetActiveLine(42);
    EXPECT_EQ(song.GetActiveLine(), 42);

    song.SetPlayLine(17);
    EXPECT_EQ(song.GetPlayLine(), 17);
}

TEST_F(SongCoreTest, PlayPressedTonesInitAndSilenceReturnTrue) {
    // Both only write to the private m_playpt* arrays, with no public getter
    // to assert on directly; this just characterizes the documented return
    // contract and confirms neither crashes on a freshly-constructed song.
    EXPECT_TRUE(song.PlayPressedTonesInit());
    EXPECT_TRUE(song.SetPlayPressedTonesSilence());
}

TEST_F(SongCoreTest, UECursorIsEqualComparesTheRightNumberOfIntsPerPart) {
    int a4[4] = { 1, 2, 3, 4 };
    int b4[4] = { 1, 2, 3, 4 };
    int c4[4] = { 1, 2, 3, 9 };
    EXPECT_TRUE(song.UECursorIsEqual(a4, b4, Part::PART_TRACKS));
    EXPECT_FALSE(song.UECursorIsEqual(a4, c4, Part::PART_TRACKS));

    int a2[2] = { 5, 6 };
    int b2[2] = { 5, 6 };
    EXPECT_TRUE(song.UECursorIsEqual(a2, b2, Part::PART_SONG));

    int a6[6] = { 1, 2, 3, 4, 5, 6 };
    int b6[6] = { 1, 2, 3, 4, 5, 7 };
    EXPECT_FALSE(song.UECursorIsEqual(a6, b6, Part::PART_INSTRUMENTS));

    int a1[1] = { 9 };
    int b1[1] = { 9 };
    EXPECT_TRUE(song.UECursorIsEqual(a1, b1, Part::PART_INFO));
}

TEST_F(SongCoreTest, SongGetGoReadsCurrentAndGivenLine) {
    (*song.GetSongGo())[0] = 3;
    (*song.GetSongGo())[5] = -1;

    song.SongSetActiveLine(0);
    EXPECT_EQ(song.SongGetGo(), 3);
    EXPECT_EQ(song.SongGetGo(5), -1);
}

TEST_F(SongCoreTest, SongTrackGoDecAndIncWrapAtByteBoundaries) {
    song.SongSetActiveLine(0);
    (*song.GetSongGo())[0] = 0;

    song.SongTrackGoDec();
    EXPECT_EQ(song.SongGetGo(), 0xff); // (0 - 1) & 0xff wraps to 255

    song.SongTrackGoInc();
    EXPECT_EQ(song.SongGetGo(), 0); // back to 0

    song.SongTrackGoInc();
    EXPECT_EQ(song.SongGetGo(), 1);
}

TEST_F(SongCoreTest, FindNearTrackBySongLineAndColumnPrefersTrackAfterDefault) {
    (*song.GetSongGo())[0] = -1; // not a goto line
    (*song.GetSong())[0][2] = 10; // column 2's default track is 10

    BYTE used[TRACKSNUM] = {};
    used[10] = TrackFlag::TF_USED; // 10 itself is taken
    used[11] = TrackFlag::TF_USED; // so is the next one

    // Should skip 10 and 11 (both used) and return the next free track after 10.
    EXPECT_EQ(song.FindNearTrackBySongLineAndColumn(0, 2, used), 12);
}

TEST_F(SongCoreTest, FindNearTrackBySongLineAndColumnFallsBackToFirstFreeTrack) {
    // No song line has a default track for this column (all -1 from SetUp,
    // and no goto lines either), so it falls through to "first free track".
    BYTE used[TRACKSNUM] = {};
    used[0] = TrackFlag::TF_USED;
    used[1] = TrackFlag::TF_USED;

    EXPECT_EQ(song.FindNearTrackBySongLineAndColumn(0, 0, used), 2);
}

TEST_F(SongCoreTest, SongPlayNextLineAdvancesAndFollowsGotoLine) {
    song.SetPlayMode(PLAY_SONG);
    song.SongSetPlayLine(4);
    (*song.GetSongGo())[5] = -1; // plain advance to line 5, no goto

    EXPECT_TRUE(song.SongPlayNextLine());
    EXPECT_EQ(song.SongGetPlayLine(), 5);
    EXPECT_EQ(song.GetPlayLine(), 0); // the *track* play line always resets

    (*song.GetSongGo())[6] = 2; // line 6 says "goto line 2"
    EXPECT_TRUE(song.SongPlayNextLine());
    EXPECT_EQ(song.SongGetPlayLine(), 2); // advanced to 6, then jumped to 2
}

TEST_F(SongCoreTest, SongPlayNextLineDoesNotAdvanceWhenStopped) {
    song.SetPlayMode(PLAY_STOP);
    song.SongSetPlayLine(7);
    (*song.GetSongGo())[7] = -1;

    EXPECT_TRUE(song.SongPlayNextLine());
    EXPECT_EQ(song.SongGetPlayLine(), 7); // unchanged: PLAY_STOP doesn't advance
}

TEST_F(SongCoreTest, SongToAtaEncodesTrackDataThenFillsRestAsUnused) {
    (*song.GetSong())[0][0] = 5;
    (*song.GetSong())[0][1] = 10;
    (*song.GetSong())[0][2] = 15;
    (*song.GetSong())[0][3] = 20;

    unsigned char dest[SONGLEN * 4] = { 0 };
    int size = song.SongToAta(dest, sizeof(dest), 0x4000);

    ASSERT_EQ(size, 4);
    EXPECT_EQ(dest[0], 5);
    EXPECT_EQ(dest[1], 10);
    EXPECT_EQ(dest[2], 15);
    EXPECT_EQ(dest[3], 20);
    // Every other line is empty (-1), encoded as 255 per track slot.
    EXPECT_EQ(dest[4], 255);
    EXPECT_EQ(dest[SONGLEN * 4 - 1], 255);
}

TEST_F(SongCoreTest, SongToAtaAndAtaToSongRoundTripTrackData) {
    (*song.GetSong())[0][0] = 5;
    (*song.GetSong())[0][1] = 10;
    (*song.GetSong())[0][2] = 15;
    (*song.GetSong())[0][3] = 20;

    unsigned char dest[SONGLEN * 4] = { 0 };
    int size = song.SongToAta(dest, sizeof(dest), 0x4000);

    CSong decoded;
    ASSERT_TRUE(decoded.AtaToSong(dest, size, 0x4000));
    EXPECT_EQ((*decoded.GetSong())[0][0], 5);
    EXPECT_EQ((*decoded.GetSong())[0][1], 10);
    EXPECT_EQ((*decoded.GetSong())[0][2], 15);
    EXPECT_EQ((*decoded.GetSong())[0][3], 20);
}

TEST_F(SongCoreTest, SongToAtaAndAtaToSongRoundTripGotoLine) {
    (*song.GetSongGo())[0] = 1; // line 0: "goto line 1"

    unsigned char dest[SONGLEN * 4] = { 0 };
    int size = song.SongToAta(dest, sizeof(dest), 0x4000);

    ASSERT_EQ(size, 4);
    EXPECT_EQ(dest[0], 254); // go command marker

    // Decode using a byte range covering (at least) 2 lines, so the decoded
    // goto target (line 1) is within bounds - AtaToSong()'s "len" parameter
    // represents how much of the module's song section is being decoded,
    // not SongToAta()'s own (smaller) "bytes actually used" return value.
    CSong decoded;
    ASSERT_TRUE(decoded.AtaToSong(dest, 8, 0x4000));
    EXPECT_EQ(decoded.SongGetGo(0), 1);
}
