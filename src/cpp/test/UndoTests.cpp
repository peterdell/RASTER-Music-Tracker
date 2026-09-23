#include "gtest/gtest.h"

#include "Undo.h"
#include "Song.h"
#include "Tracks.h"
#include "Instruments.h"

// See plans/UNDO_PLAN.md for the investigation that led to this file: all of
// CUndo's real dependencies (g_Song/g_Tracks/g_Instruments/g_activepart/
// g_changes) turned out already safe once the CSong split was done, so the
// whole class is linked for real here (..\Undo.cpp in RmtTests.vcxproj) -
// see test/UndoStub.cpp for the one remaining stub (just g_Undo's storage).

extern CUndo g_Undo;
extern CSong g_Song;
extern CTracks g_Tracks;
extern CInstruments g_Instruments;
extern int g_tracks4_8;
extern Part g_activepart;
extern BOOL g_changes;

class UndoTest : public ::testing::Test {
  protected:
    void SetUp() override {
        g_tracks4_8 = 4;
        g_Tracks.SetMaxTrackLength(64);
        g_Tracks.InitTracks();
        g_Instruments.InitInstruments();
        // CInstruments::ClearInstrument() is a no-op stub in this test binary
        // (see InstrumentsStub.cpp), so InitInstruments() alone doesn't reset
        // instrument data between tests - do it directly here instead
        // (same workaround SongEditingTests.cpp uses).
        for (int i = 0; i < INSTRSNUM; i++) {
            memset(g_Instruments.GetInstrument(i), 0, sizeof(TInstrument));
        }
        BlankSong(g_Song);
        g_Song.SongSetActiveLine(0);
        g_Song.SetActiveLine(0);

        g_Undo.Clear();
        g_activepart = Part::PART_TRACKS;
        // Avoids CUndo::InsertEvent()'s first-change SetRMTTitle() call,
        // which would otherwise reach GUI_Song.cpp's real, untested
        // AfxGetApp()->GetMainWnd() call - see plans/UNDO_PLAN.md finding
        // #3 (same documented-precondition treatment as CSong::Stop()'s
        // m_play precondition).
        g_changes = 1;
    }

    static void BlankSong(CSong& s) {
        auto* songArr = s.GetSong();
        auto* songGoArr = s.GetSongGo();
        for (int line = 0; line < SONGLEN; line++) {
            for (int col = 0; col < SONGTRACKS; col++) {
                (*songArr)[line][col] = -1;
            }
            (*songGoArr)[line] = -1;
        }
    }
};

// --- Bookkeeping-only methods (no globals, already real before this file existed) ---

TEST_F(UndoTest, GetUndoStepsIsZeroInitially) {
    EXPECT_EQ(g_Undo.GetUndoSteps(), 0);
}

TEST_F(UndoTest, GetRedoStepsIsZeroInitially) {
    EXPECT_EQ(g_Undo.GetRedoSteps(), 0);
}

TEST_F(UndoTest, DropLastOnEmptyHistoryIsANoOp) {
    g_Undo.DropLast();
    EXPECT_EQ(g_Undo.GetUndoSteps(), 0);
}

TEST_F(UndoTest, SeparatorOnEmptyHistoryIsANoOp) {
    g_Undo.Separator();
    EXPECT_EQ(g_Undo.GetUndoSteps(), 0);
}

TEST_F(UndoTest, PosIsEqualComparesGroupType0UsingTwoElements) {
    int a[2] = {1, 2}, b[2] = {1, 2}, c[2] = {1, 3};
    EXPECT_TRUE(g_Undo.PosIsEqual(a, b, UETYPE_NOTEINSTRVOL)); // 1 >> 6 == 0
    EXPECT_FALSE(g_Undo.PosIsEqual(a, c, UETYPE_NOTEINSTRVOL));
}

TEST_F(UndoTest, PosIsEqualComparesGroupType1UsingOneElement) {
    int a[1] = {5}, b[1] = {5}, c[1] = {6};
    EXPECT_TRUE(g_Undo.PosIsEqual(a, b, UETYPE_SONGDATA)); // 65 >> 6 == 1
    EXPECT_FALSE(g_Undo.PosIsEqual(a, c, UETYPE_SONGDATA));
}

TEST_F(UndoTest, PosIsEqualComparesGroupType2UsingThreeElements) {
    // No real UndoType value falls in 128-191 (highest defined is 69) - cast
    // an arbitrary value in range to exercise this group directly.
    UndoType type = (UndoType)150; // 150 >> 6 == 2
    int a[3] = {1, 2, 3}, b[3] = {1, 2, 3}, c[3] = {1, 2, 4};
    EXPECT_TRUE(g_Undo.PosIsEqual(a, b, type));
    EXPECT_FALSE(g_Undo.PosIsEqual(a, c, type));
}

TEST_F(UndoTest, PosIsEqualReturnsFalseForOutOfRangeType) {
    UndoType type = (UndoType)200; // 200 >> 6 == 3, no matching case
    int a[1] = {1}, b[1] = {1};
    EXPECT_FALSE(g_Undo.PosIsEqual(a, b, type));
}

// --- Undo()/Redo() on empty history ---

TEST_F(UndoTest, UndoOnEmptyHistoryReturnsFalse) {
    EXPECT_FALSE(g_Undo.Undo());
}

TEST_F(UndoTest, RedoOnEmptyHistoryReturnsFalse) {
    EXPECT_FALSE(g_Undo.Redo());
}

// --- ChangeTrack / PerformEvent(UETYPE_NOTEINSTRVOL/...(SPEED)/SPEED/LENGO/TRACKDATA/TRACKSALL) ---

TEST_F(UndoTest, ChangeTrackNoteInstrVolIsSwappedByUndoAndRedo) {
    TTrack* tr = g_Tracks.GetTrack(0);
    tr->note[0] = 5;
    tr->instr[0] = 2;
    tr->volume[0] = 10;

    g_Undo.ChangeTrack(0, 0, UETYPE_NOTEINSTRVOL, 1);

    tr->note[0] = 7;
    tr->instr[0] = 3;
    tr->volume[0] = 12;

    EXPECT_TRUE(g_Undo.Undo());
    EXPECT_EQ(tr->note[0], 5);
    EXPECT_EQ(tr->instr[0], 2);
    EXPECT_EQ(tr->volume[0], 10);

    EXPECT_TRUE(g_Undo.Redo());
    EXPECT_EQ(tr->note[0], 7);
    EXPECT_EQ(tr->instr[0], 3);
    EXPECT_EQ(tr->volume[0], 12);
}

TEST_F(UndoTest, ChangeTrackNoteInstrVolSpeedIsSwappedByUndoAndRedo) {
    TTrack* tr = g_Tracks.GetTrack(0);
    tr->note[0] = 5;
    tr->instr[0] = 2;
    tr->volume[0] = 10;
    tr->speed[0] = 3;

    g_Undo.ChangeTrack(0, 0, UETYPE_NOTEINSTRVOLSPEED, 1);

    tr->note[0] = 7;
    tr->instr[0] = 3;
    tr->volume[0] = 12;
    tr->speed[0] = 4;

    EXPECT_TRUE(g_Undo.Undo());
    EXPECT_EQ(tr->note[0], 5);
    EXPECT_EQ(tr->instr[0], 2);
    EXPECT_EQ(tr->volume[0], 10);
    EXPECT_EQ(tr->speed[0], 3);
}

TEST_F(UndoTest, ChangeTrackSpeedIsSwappedByUndo) {
    TTrack* tr = g_Tracks.GetTrack(0);
    tr->speed[0] = 3;

    g_Undo.ChangeTrack(0, 0, UETYPE_SPEED, 1);
    tr->speed[0] = 4;

    EXPECT_TRUE(g_Undo.Undo());
    EXPECT_EQ(tr->speed[0], 3);
}

TEST_F(UndoTest, ChangeTrackLenGoIsSwappedByUndo) {
    TTrack* tr = g_Tracks.GetTrack(0);
    tr->len = 32;
    tr->go = -1;

    g_Undo.ChangeTrack(0, 0, UETYPE_LENGO, 1);
    tr->len = 64;
    tr->go = 5;

    EXPECT_TRUE(g_Undo.Undo());
    EXPECT_EQ(tr->len, 32);
    EXPECT_EQ(tr->go, -1);
}

TEST_F(UndoTest, ChangeTrackWholeTrackDataIsSwappedByUndo) {
    TTrack* tr = g_Tracks.GetTrack(0);
    tr->note[0] = 5;

    g_Undo.ChangeTrack(0, 0, UETYPE_TRACKDATA, 1);
    tr->note[0] = 9;
    tr->note[1] = 1; // any other field changing too must also be reverted

    EXPECT_TRUE(g_Undo.Undo());
    EXPECT_EQ(tr->note[0], 5);
    EXPECT_EQ(tr->note[1], -1); // back to ClearTrack's default
}

TEST_F(UndoTest, ChangeTrackAllTracksIsSwappedByUndo) {
    g_Tracks.GetTrack(0)->note[0] = 5;
    g_Tracks.GetTrack(1)->note[0] = 6;

    g_Undo.ChangeTrack(0, 0, UETYPE_TRACKSALL, 1);

    g_Tracks.GetTrack(0)->note[0] = 9;
    g_Tracks.GetTrack(1)->note[0] = 9;

    EXPECT_TRUE(g_Undo.Undo());
    EXPECT_EQ(g_Tracks.GetTrack(0)->note[0], 5);
    EXPECT_EQ(g_Tracks.GetTrack(1)->note[0], 6);
}

TEST_F(UndoTest, ChangeTrackWithInvalidTypeIsCharacterizedAsANoOpDataEvent) {
    // Guard-only branch (SendErrorMessage("... BAD!")) - characterized as-is
    // rather than avoided, since InsertEvent()/PerformEvent() still handle
    // a NULL-data event gracefully (no crash).
    g_Undo.ChangeTrack(0, 0, (UndoType)999, 1);
    EXPECT_EQ(g_Undo.GetUndoSteps(), 1);
    EXPECT_TRUE(g_Undo.Undo());
    EXPECT_EQ(g_Undo.GetRedoSteps(), 1);
}

TEST_F(UndoTest, ChangeTrackIgnoresOutOfRangeTrackOrLine) {
    g_Undo.ChangeTrack(-1, 0, UETYPE_SPEED, 1);
    g_Undo.ChangeTrack(0, -1, UETYPE_SPEED, 1);
    EXPECT_EQ(g_Undo.GetUndoSteps(), 0);
}

// --- ChangeSong / PerformEvent(UETYPE_SONGTRACK/SONGGO/SONGDATA) ---

TEST_F(UndoTest, ChangeSongTrackIsSwappedByUndo) {
    (*g_Song.GetSong())[0][0] = 5;

    g_Undo.ChangeSong(0, 0, UETYPE_SONGTRACK, 1);
    (*g_Song.GetSong())[0][0] = 7;

    EXPECT_TRUE(g_Undo.Undo());
    EXPECT_EQ((*g_Song.GetSong())[0][0], 5);
}

TEST_F(UndoTest, ChangeSongGoIsSwappedByUndo) {
    (*g_Song.GetSongGo())[0] = -1;

    g_Undo.ChangeSong(0, 0, UETYPE_SONGGO, 1);
    (*g_Song.GetSongGo())[0] = 3;

    EXPECT_TRUE(g_Undo.Undo());
    EXPECT_EQ((*g_Song.GetSongGo())[0], -1);
}

TEST_F(UndoTest, ChangeSongWholeSongDataIsSwappedByUndo) {
    (*g_Song.GetSong())[0][0] = 5;
    (*g_Song.GetSongGo())[1] = 2;

    g_Undo.ChangeSong(0, 0, UETYPE_SONGDATA, 1);

    (*g_Song.GetSong())[0][0] = 9;
    (*g_Song.GetSongGo())[1] = 8;

    EXPECT_TRUE(g_Undo.Undo());
    EXPECT_EQ((*g_Song.GetSong())[0][0], 5);
    EXPECT_EQ((*g_Song.GetSongGo())[1], 2);
}

TEST_F(UndoTest, ChangeSongIgnoresNegativeSonglineOrColumn) {
    g_Undo.ChangeSong(-1, 0, UETYPE_SONGTRACK, 1);
    g_Undo.ChangeSong(0, -1, UETYPE_SONGTRACK, 1);
    EXPECT_EQ(g_Undo.GetUndoSteps(), 0);
}

// --- ChangeInstrument / PerformEvent(UETYPE_INSTRDATA/INSTRSALL) ---

TEST_F(UndoTest, ChangeInstrumentDataIsSwappedByUndo) {
    TInstrument* instr = g_Instruments.GetInstrument(0);
    instr->octave = 3;

    g_Undo.ChangeInstrument(0, 0, UETYPE_INSTRDATA, 1);
    instr->octave = 5;

    EXPECT_TRUE(g_Undo.Undo());
    EXPECT_EQ(instr->octave, 3);
}

TEST_F(UndoTest, ChangeInstrumentAllInstrumentsIsSwappedByUndo) {
    g_Instruments.GetInstrument(0)->octave = 3;
    g_Instruments.GetInstrument(1)->octave = 4;

    g_Undo.ChangeInstrument(0, 0, UETYPE_INSTRSALL, 1);

    g_Instruments.GetInstrument(0)->octave = 9;
    g_Instruments.GetInstrument(1)->octave = 9;

    EXPECT_TRUE(g_Undo.Undo());
    EXPECT_EQ(g_Instruments.GetInstrument(0)->octave, 3);
    EXPECT_EQ(g_Instruments.GetInstrument(1)->octave, 4);
}

// --- ChangeInfo / PerformEvent(UETYPE_INFODATA) ---

TEST_F(UndoTest, ChangeInfoDataIsSwappedByUndo) {
    TInfo info = {};
    g_Song.GetSongInfoPars(&info);
    info.speed = 6;
    g_Song.SetSongInfoPars(&info);

    g_Undo.ChangeInfo(0, UETYPE_INFODATA, 1);

    g_Song.GetSongInfoPars(&info);
    info.speed = 9;
    g_Song.SetSongInfoPars(&info);

    EXPECT_TRUE(g_Undo.Undo());
    g_Song.GetSongInfoPars(&info);
    EXPECT_EQ(info.speed, 6);
}

// --- Multi-step history / separator semantics ---

TEST_F(UndoTest, TwoCompletedChangesAreTwoIndependentUndoSteps) {
    TTrack* tr = g_Tracks.GetTrack(0);
    tr->speed[0] = 1;
    g_Undo.ChangeTrack(0, 0, UETYPE_SPEED, 1); // step 1: snapshot speed=1
    tr->speed[0] = 2;

    tr->speed[1] = 10;
    g_Undo.ChangeTrack(0, 1, UETYPE_SPEED, 1); // step 2: snapshot line 1's speed=10
    tr->speed[1] = 20;

    EXPECT_EQ(g_Undo.GetUndoSteps(), 2);

    EXPECT_TRUE(g_Undo.Undo()); // undoes step 2 only
    EXPECT_EQ(tr->speed[1], 10);
    EXPECT_EQ(tr->speed[0], 2); // step 1 untouched
    EXPECT_EQ(g_Undo.GetUndoSteps(), 1);
    EXPECT_EQ(g_Undo.GetRedoSteps(), 1);

    EXPECT_TRUE(g_Undo.Undo()); // undoes step 1
    EXPECT_EQ(tr->speed[0], 1);
    EXPECT_EQ(g_Undo.GetUndoSteps(), 0);
    EXPECT_EQ(g_Undo.GetRedoSteps(), 2);
}

TEST_F(UndoTest, RepeatedChangeAtTheSameCursorAndPositionCoalescesIntoOneStep) {
    // Default separator (0, "accumulate") at an unchanged cursor/type/position
    // coalesces into a single undo step, keeping only the first snapshot -
    // real production behavior for e.g. typing several notes in a row before
    // ever moving the cursor or calling Separator().
    TTrack* tr = g_Tracks.GetTrack(0);
    tr->speed[0] = 1;
    g_Undo.ChangeTrack(0, 0, UETYPE_SPEED); // separator defaults to 0
    tr->speed[0] = 2;
    g_Undo.ChangeTrack(0, 0, UETYPE_SPEED); // same cursor/type/pos - coalesces
    tr->speed[0] = 3;

    EXPECT_EQ(g_Undo.GetUndoSteps(), 1);
    EXPECT_TRUE(g_Undo.Undo());
    EXPECT_EQ(tr->speed[0], 1); // restores all the way back to the first snapshot
}

// --- Init / Clear ---

TEST_F(UndoTest, ClearResetsUndoAndRedoStepsToZero) {
    TTrack* tr = g_Tracks.GetTrack(0);
    tr->speed[0] = 1;
    g_Undo.ChangeTrack(0, 0, UETYPE_SPEED, 1);
    g_Undo.Undo();
    ASSERT_EQ(g_Undo.GetRedoSteps(), 1);

    g_Undo.Clear();

    EXPECT_EQ(g_Undo.GetUndoSteps(), 0);
    EXPECT_EQ(g_Undo.GetRedoSteps(), 0);
    EXPECT_FALSE(g_Undo.Undo());
    EXPECT_FALSE(g_Undo.Redo());
}

TEST_F(UndoTest, InitResetsUndoAndRedoStepsToZero) {
    TTrack* tr = g_Tracks.GetTrack(0);
    tr->speed[0] = 1;
    g_Undo.ChangeTrack(0, 0, UETYPE_SPEED, 1);

    g_Undo.Init();

    EXPECT_EQ(g_Undo.GetUndoSteps(), 0);
    EXPECT_FALSE(g_Undo.Undo());
}

// --- DropLast ---

TEST_F(UndoTest, DropLastRemovesTheMostRecentUndoStepWithoutPerformingIt) {
    TTrack* tr = g_Tracks.GetTrack(0);
    tr->speed[0] = 1;
    g_Undo.ChangeTrack(0, 0, UETYPE_SPEED, 1);
    tr->speed[0] = 2; // never undone - DropLast just discards the recording

    g_Undo.DropLast();

    EXPECT_EQ(g_Undo.GetUndoSteps(), 0);
    EXPECT_FALSE(g_Undo.Undo());
    EXPECT_EQ(tr->speed[0], 2); // unchanged - DropLast doesn't touch live data
}
