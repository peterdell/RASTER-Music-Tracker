#include "gtest/gtest.h"

#include "Song.h"
#include "Clipboard.h"

extern int g_tracks4_8;
extern CTracks g_Tracks;
extern CInstruments g_Instruments;
extern CTrackClipboard g_TrackClipboard;
extern CSong g_Song;

// Exercises the CSong/CTrackClipboard editing methods implemented in
// SongEditing.cpp/ClipboardCore.cpp - see plans/NOTES.md for the Song.cpp/
// IO_Song.cpp triage that identified this "safe cluster" (only touches
// g_Tracks/g_Instruments/g_Undo/g_TrackClipboard/g_tracks4_8, all confirmed
// cheap). g_Undo's ChangeTrack/ChangeSong are stubbed as no-ops (see
// UndoStub.cpp) - these methods call them only to *record* an edit for
// later undo, so the tests below characterize the edit's own visible
// effect, not the undo recording. Likewise CInstruments::ClearInstrument()/
// Update()/MemorizeOctaveAndVolume()/RememberOctaveAndVolume() are stubbed
// as no-ops (see InstrumentsStub.cpp) since their real bodies touch
// g_AtariTrackerDriver/g_keyboard_RememberOctavesAndVolumes.

class SongEditingTest : public ::testing::Test {
protected:
    CSong song;

    void SetUp() override {
        g_tracks4_8 = 4;
        // CTracks::InitTracks() doesn't reset m_maxTrackLength (only clears
        // track data), so a prior test's ChangeMaxtracklen() call would
        // otherwise leak into every later test - reset it first so
        // ClearTrack() (called by InitTracks() below) resets each track's
        // len to the right default too.
        g_Tracks.SetMaxTrackLength(64);
        g_Tracks.InitTracks();
        g_Instruments.InitInstruments();
        // CInstruments::ClearInstrument() is a no-op stub in this test binary
        // (see InstrumentsStub.cpp), so InitInstruments() alone doesn't reset
        // instrument data between tests - do it directly here instead.
        for (int i = 0; i < INSTRSNUM; i++) {
            memset(g_Instruments.GetInstrument(i), 0, sizeof(TInstrument));
        }
        g_TrackClipboard.Clear();

        BlankSong(song);
        BlankSong(g_Song);
        g_Song.SongSetActiveLine(0);
        g_Song.SetActiveLine(0);
    }

    void TearDown() override {
        g_tracks4_8 = 4;
    }

    static void BlankSong(CSong& s) {
        auto* songArr = s.GetSong();
        auto* songGoArr = s.GetSongGo();
        for (int line = 0; line < SONGLEN; line++) {
            for (int col = 0; col < SONGTRACKS; col++) (*songArr)[line][col] = -1;
            (*songGoArr)[line] = -1;
        }
    }
};

// --- GetSubsongParts ---

TEST_F(SongEditingTest, GetSubsongPartsReturnsZeroWhenSongHasNoGotoLines) {
    CString result;
    EXPECT_EQ(song.GetSubsongParts(result), 0);
    EXPECT_STREQ(result, "");
}

TEST_F(SongEditingTest, GetSubsongPartsFindsOneSubsongEndingInAGotoLine) {
    (*song.GetSong())[0][0] = 5;         // a used track at line 0
    (*song.GetSongGo())[1] = 0;          // line 1: "goto line 0" - closes the loop

    CString result;
    EXPECT_EQ(song.GetSubsongParts(result), 1);
    EXPECT_STREQ(result, "00 ");
}

// --- MarkTF_USED / MarkTF_NOEMPTY ---

TEST_F(SongEditingTest, MarkTFUsedMarksTracksReferencedByNonGotoLines) {
    (*song.GetSong())[0][0] = 3;
    (*song.GetSongGo())[1] = 5;          // goto line: its track column is ignored
    (*song.GetSong())[1][0] = 7;

    BYTE flags[TRACKSNUM] = {};
    song.MarkTF_USED(flags);

    EXPECT_EQ(flags[3], TrackFlag::TF_USED);
    EXPECT_EQ(flags[7], 0);
}

TEST_F(SongEditingTest, MarkTFNoEmptyMarksTracksWithData) {
    g_Tracks.GetTrack(2)->note[0] = 0; // any valid note makes it "not empty"

    BYTE flags[TRACKSNUM] = {};
    song.MarkTF_NOEMPTY(flags);

    EXPECT_EQ(flags[2] & TrackFlag::TF_NOEMPTY, TrackFlag::TF_NOEMPTY);
    EXPECT_EQ(flags[3] & TrackFlag::TF_NOEMPTY, 0);
}

// --- ActiveInstrSet / Prev / Next ---

TEST_F(SongEditingTest, ActiveInstrSetChangesActiveInstrument) {
    song.ActiveInstrSet(5);
    EXPECT_EQ(song.GetActiveInstr(), 5);
}

TEST_F(SongEditingTest, ActiveInstrPrevAndNextStepAndWrapAt6Bits) {
    song.ActiveInstrSet(5);
    song.ActiveInstrPrev();
    EXPECT_EQ(song.GetActiveInstr(), 4);

    song.ActiveInstrNext();
    EXPECT_EQ(song.GetActiveInstr(), 5);

    song.ActiveInstrSet(0);
    song.ActiveInstrPrev();
    EXPECT_EQ(song.GetActiveInstr(), 0x3f); // (0 - 1) & 0x3f wraps to 63
}

// --- TrackLeft / TrackRight (observed via GetUECursor) ---

TEST_F(SongEditingTest, TrackLeftWrapsColumnAndCursorAtZero) {
    song.TrackLeft(false);
    int* cursor = song.GetUECursor(Part::PART_TRACKS);
    EXPECT_EQ(cursor[2], g_tracks4_8 - 1); // column wrapped
    EXPECT_EQ(cursor[3], 3);               // cursor wrapped to the previous speed column
    delete[] cursor;
}

TEST_F(SongEditingTest, TrackRightAdvancesCursorWithoutChangingColumn) {
    song.TrackRight(false);
    int* cursor = song.GetUECursor(Part::PART_TRACKS);
    EXPECT_EQ(cursor[2], 0); // column unchanged
    EXPECT_EQ(cursor[3], 1); // cursor advanced
    delete[] cursor;
}

TEST_F(SongEditingTest, TrackLeftColumnModeSkipsTheCursorAndMovesColumnDirectly) {
    song.TrackLeft(true);
    int* cursor = song.GetUECursor(Part::PART_TRACKS);
    EXPECT_EQ(cursor[2], g_tracks4_8 - 1);
    EXPECT_EQ(cursor[3], 0); // cursor untouched in column mode
    delete[] cursor;
}

// --- RespectBoundaries ---

TEST_F(SongEditingTest, RespectBoundariesClampsActiveLineToSmallestTrackLength) {
    g_Tracks.GetTrack(0)->len = 8; // active track for song line 0 is only 8 lines long
    (*song.GetSong())[0][0] = 0;
    song.SetActiveLine(20); // out of bounds for an 8-line track

    song.RespectBoundaries();

    EXPECT_EQ(song.GetActiveLine(), 7); // clamped to length - 1
}

// --- TrackGetLoopingNoteInstrVol ---

TEST_F(SongEditingTest, TrackGetLoopingNoteInstrVolReturnsMinusOneWithoutALoop) {
    g_Tracks.GetTrack(0)->len = 4;
    g_Tracks.GetTrack(0)->go = -1;
    song.SetActiveLine(10); // beyond the track's length, and no loop to fall back on

    int note, instr, vol;
    song.TrackGetLoopingNoteInstrVol(0, note, instr, vol);

    EXPECT_EQ(note, -1);
    EXPECT_EQ(instr, -1);
    EXPECT_EQ(vol, -1);
}

// --- GetUECursor (PART_SONG / PART_INFO cases; PART_TRACKS covered above) ---

TEST_F(SongEditingTest, GetUECursorPartSongReturnsSongPositionCursor) {
    song.SongSetActiveLine(9);
    int* cursor = song.GetUECursor(Part::PART_SONG);
    EXPECT_EQ(cursor[0], 9);
    EXPECT_EQ(cursor[1], 0);
    delete[] cursor;
}

// --- SongTrackSet / SetByNum / Dec / Inc / Empty / GoOnOff ---

TEST_F(SongEditingTest, SongTrackSetWritesTheActiveSongPosition) {
    song.SongTrackSet(12);
    EXPECT_EQ((*song.GetSong())[0][0], 12);
}

TEST_F(SongEditingTest, SongTrackSetByNumShiftsInALowNibbleWhenNotAGoLine) {
    song.SongTrackSet(0x02);
    song.SongTrackSetByNum(0x5); // 0x02 -> (0x2 << 4 | 0x5) = 0x25
    EXPECT_EQ((*song.GetSong())[0][0], 0x25);
}

TEST_F(SongEditingTest, SongTrackSetByNumShiftsInAGoTargetWhenOnAGoLine) {
    song.SongTrackGoOnOff(); // turns GO on for the active line (was -1, becomes 0)
    song.SongTrackSetByNum(0x7);
    EXPECT_EQ(song.SongGetGo(), 0x07);
}

TEST_F(SongEditingTest, SongTrackDecWrapsAtMinusOneToLastTrack) {
    // The active position is already -1 ("--", from the fixture's blank
    // song), and the wrap only triggers once decrementing goes past -1.
    song.SongTrackDec();
    EXPECT_EQ((*song.GetSong())[0][0], TRACKSNUM - 1);
}

TEST_F(SongEditingTest, SongTrackIncWrapsAtLastTrackToMinusOne) {
    song.SongTrackSet(TRACKSNUM - 1);
    song.SongTrackInc();
    EXPECT_EQ((*song.GetSong())[0][0], -1);
}

TEST_F(SongEditingTest, SongTrackEmptySetsTheActivePositionToMinusOne) {
    song.SongTrackSet(4);
    song.SongTrackEmpty();
    EXPECT_EQ((*song.GetSong())[0][0], -1);
}

TEST_F(SongEditingTest, SongTrackGoOnOffTogglesGoAtTheActiveLine) {
    EXPECT_EQ(song.SongGetGo(), -1);
    song.SongTrackGoOnOff();
    EXPECT_EQ(song.SongGetGo(), 0);
    song.SongTrackGoOnOff();
    EXPECT_EQ(song.SongGetGo(), -1);
}

// --- SongInsertLine / SongDeleteLine ---

TEST_F(SongEditingTest, SongInsertLineShiftsSubsequentLinesDownAndClearsInsertedLine) {
    (*song.GetSong())[0][0] = 1;
    (*song.GetSong())[1][0] = 2;

    song.SongInsertLine(1);

    EXPECT_EQ((*song.GetSong())[0][0], 1);   // untouched
    EXPECT_EQ((*song.GetSong())[1][0], -1);  // newly inserted, empty
    EXPECT_EQ((*song.GetSong())[2][0], 2);   // shifted down
}

TEST_F(SongEditingTest, SongDeleteLineShiftsSubsequentLinesUp) {
    (*song.GetSong())[0][0] = 1;
    (*song.GetSong())[1][0] = 2;

    song.SongDeleteLine(0);

    EXPECT_EQ((*song.GetSong())[0][0], 2);   // shifted up
    EXPECT_EQ((*song.GetSong())[SONGLEN - 1][0], -1); // vacated slot at the end
}

// --- TrackCopy / TrackPaste / TrackDelete / TrackCut / TrackCopyFromTo / TrackSwapFromTo ---

TEST_F(SongEditingTest, TrackCopyAndPasteRoundTripTrackData) {
    (*song.GetSong())[0][0] = 5;
    g_Tracks.GetTrack(5)->note[0] = 9;

    song.TrackCopy();

    (*song.GetSong())[0][0] = 6; // switch active track to an empty one
    song.TrackPaste();

    EXPECT_EQ(g_Tracks.GetTrack(6)->note[0], 9);
}

TEST_F(SongEditingTest, TrackDeleteClearsTheActiveTrack) {
    (*song.GetSong())[0][0] = 5;
    g_Tracks.GetTrack(5)->note[0] = 9;

    song.TrackDelete();

    EXPECT_TRUE(g_Tracks.IsEmptyTrack(5));
}

TEST_F(SongEditingTest, TrackCutCopiesThenClearsTheActiveTrack) {
    (*song.GetSong())[0][0] = 5;
    g_Tracks.GetTrack(5)->note[0] = 9;

    song.TrackCut();
    EXPECT_TRUE(g_Tracks.IsEmptyTrack(5));

    (*song.GetSong())[0][0] = 6;
    song.TrackPaste();
    EXPECT_EQ(g_Tracks.GetTrack(6)->note[0], 9); // survived via the copy step
}

TEST_F(SongEditingTest, TrackCopyFromToCopiesTrackData) {
    g_Tracks.GetTrack(3)->note[0] = 4;
    song.TrackCopyFromTo(3, 9);
    EXPECT_EQ(g_Tracks.GetTrack(9)->note[0], 4);
}

TEST_F(SongEditingTest, TrackSwapFromToExchangesTrackData) {
    g_Tracks.GetTrack(3)->note[0] = 4;
    g_Tracks.GetTrack(9)->note[0] = 7;

    song.TrackSwapFromTo(3, 9);

    EXPECT_EQ(g_Tracks.GetTrack(3)->note[0], 7);
    EXPECT_EQ(g_Tracks.GetTrack(9)->note[0], 4);
}

// --- BLOCKSETBEGIN / BLOCKSETEND / BLOCKDESELECT / ISBLOCKSELECTED / BlockPaste ---
// These reach into CTrackClipboard, which internally reads the *global*
// g_Song (a pre-existing coupling in CTrackClipboard itself - see
// ClipboardCore.cpp's header comment), so these tests call them on g_Song
// rather than the local "song" fixture member, matching real usage.

TEST_F(SongEditingTest, BlockSetBeginEndDeselectAndIsBlockSelectedRoundTrip) {
    (*g_Song.GetSong())[0][0] = 5; // a valid track at the active song position
    EXPECT_FALSE(g_Song.ISBLOCKSELECTED());

    g_Song.BLOCKSETBEGIN();
    EXPECT_TRUE(g_Song.ISBLOCKSELECTED());

    g_Song.BLOCKSETEND();
    EXPECT_TRUE(g_Song.ISBLOCKSELECTED());

    g_Song.BLOCKDESELECT();
    EXPECT_FALSE(g_Song.ISBLOCKSELECTED());
}

TEST_F(SongEditingTest, BlockPastePastesTheCopiedTrackOntoTheActiveTrack) {
    // BlockPaste() reads from CTrackClipboard's block-selection clipboard
    // (m_track, populated by BlockCopyToClipboard() after a BLOCKSETBEGIN/
    // BLOCKSETEND selection) - a separate mechanism from TrackCopy()'s whole-
    // track clipboard (m_trackcopy), which BlockPaste() does not use.
    (*g_Song.GetSong())[0][0] = 5;
    g_Tracks.GetTrack(5)->note[0] = 3;
    g_Song.BLOCKSETBEGIN();
    g_Song.BLOCKSETEND();
    g_TrackClipboard.BlockCopyToClipboard();
    g_TrackClipboard.BlockDeselect();

    (*g_Song.GetSong())[0][0] = 6; // switch the active track to an empty one

    g_Song.BlockPaste(0);

    EXPECT_EQ(g_Tracks.GetTrack(6)->note[0], 3);
}

// --- InstrCopy / InstrCut / InstrDelete ---
// CInstruments::ClearInstrument() is a no-op stub in this test binary (see
// InstrumentsStub.cpp), so these only characterize what's still observable:
// that they don't crash and leave the active instrument index unaffected.

TEST_F(SongEditingTest, InstrCopyDoesNotCrashOrChangeActiveInstrument) {
    song.ActiveInstrSet(5);
    song.InstrCopy();
    EXPECT_EQ(song.GetActiveInstr(), 5);
}

TEST_F(SongEditingTest, InstrDeleteDoesNotCrash) {
    song.ActiveInstrSet(5);
    song.InstrDelete();
    EXPECT_EQ(song.GetActiveInstr(), 5);
}

TEST_F(SongEditingTest, InstrCutDoesNotCrash) {
    song.ActiveInstrSet(5);
    song.InstrCut();
    EXPECT_EQ(song.GetActiveInstr(), 5);
}

// --- SongCopyLine / SongPasteLine / SongClearLine ---

TEST_F(SongEditingTest, SongCopyLineAndPasteLineRoundTripLineData) {
    (*song.GetSong())[0][0] = 5;
    song.SongCopyLine();

    (*song.GetSong())[0][0] = -1; // overwrite before pasting back

    song.SongPasteLine();

    EXPECT_EQ((*song.GetSong())[0][0], 5);
}

TEST_F(SongEditingTest, SongClearLineBlanksTheActiveLine) {
    (*song.GetSong())[0][0] = 5;
    song.SongTrackGoOnOff(); // also set a GO to confirm it gets cleared too

    song.SongClearLine();

    EXPECT_EQ((*song.GetSong())[0][0], -1);
    EXPECT_EQ(song.SongGetGo(), -1);
}

// --- GetEffectiveMaxtracklen / GetSmallestMaxtracklen / ChangeMaxtracklen ---

TEST_F(SongEditingTest, GetSmallestMaxtracklenReturnsShortestTrackOnTheLine) {
    (*song.GetSong())[0][0] = 0;
    (*song.GetSong())[0][1] = 1;
    g_Tracks.GetTrack(0)->len = 32;
    g_Tracks.GetTrack(1)->len = 16;

    EXPECT_EQ(song.GetSmallestMaxtracklen(0), 16);
}

TEST_F(SongEditingTest, GetSmallestMaxtracklenReturnsZeroForAGotoLine) {
    song.SongTrackGoOnOff();
    EXPECT_EQ(song.GetSmallestMaxtracklen(0), 0);
}

TEST_F(SongEditingTest, GetEffectiveMaxtracklenReturnsTheLargestShortestLineAcrossTheSong) {
    (*song.GetSong())[0][0] = 0;
    g_Tracks.GetTrack(0)->len = 40;

    EXPECT_EQ(song.GetEffectiveMaxtracklen(), 40);
}

TEST_F(SongEditingTest, ChangeMaxtracklenShortensLongerTracksAndUpdatesTheGlobalLength) {
    g_Tracks.GetTrack(0)->len = 64;
    g_Tracks.GetTrack(0)->note[50] = 1;

    song.ChangeMaxtracklen(32);

    EXPECT_EQ(g_Tracks.GetTrack(0)->len, 32);
    EXPECT_EQ(g_Tracks.GetTrack(0)->go, -1);
    EXPECT_EQ(g_Tracks.GetMaxTrackLength(), 32);
}

// --- SongClearUnusedTracksAndParts / SongClearDuplicatedTracks / SongClearUnusedTracks ---

TEST_F(SongEditingTest, SongClearUnusedTracksAndPartsDeletesTracksNotReferencedInTheSong) {
    // Track 0 is referenced by the song; track 1 is not and has data.
    (*song.GetSong())[0][0] = 0;
    g_Tracks.GetTrack(1)->note[0] = 1;

    int cleared = 0, truncated = 0, truncatedBeats = 0;
    song.SongClearUnusedTracksAndParts(cleared, truncated, truncatedBeats);

    EXPECT_EQ(cleared, 1);
    EXPECT_TRUE(g_Tracks.IsEmptyTrack(1));
}

TEST_F(SongEditingTest, SongClearDuplicatedTracksMergesIdenticalTracksAndRemapsTheSong) {
    g_Tracks.GetTrack(0)->note[0] = 5;
    g_Tracks.GetTrack(1)->note[0] = 5; // identical to track 0
    (*song.GetSong())[0][0] = 1;       // song points at the duplicate

    int cleared = song.SongClearDuplicatedTracks();

    EXPECT_EQ(cleared, 1);
    EXPECT_EQ((*song.GetSong())[0][0], 0); // remapped to the surviving track
}

TEST_F(SongEditingTest, SongClearUnusedTracksDeletesTracksNotReferencedInTheSong) {
    (*song.GetSong())[0][0] = 0;
    g_Tracks.GetTrack(1)->note[0] = 1;

    int cleared = song.SongClearUnusedTracks();

    EXPECT_EQ(cleared, 1);
    EXPECT_TRUE(g_Tracks.IsEmptyTrack(1));
}

// --- TracksAllBuildLoops / TracksAllExpandLoops ---
// Both call Stop() first, which is a no-op here since Play() is never
// called on "song" first (see SongEditing.cpp's header comment there).

TEST_F(SongEditingTest, TracksAllBuildLoopsFindsARepeatingPatternAndShortensTheTrack) {
    TTrack* tr = g_Tracks.GetTrack(0);
    for (int i = 0; i < TRACKLEN; i++) tr->note[i] = 5; // a full-length track repeating every line

    int tracksmodified = 0, beatsreduced = 0;
    song.TracksAllBuildLoops(tracksmodified, beatsreduced);

    EXPECT_EQ(tracksmodified, 1);
    EXPECT_EQ(beatsreduced, 63);
    EXPECT_EQ(tr->len, 1);
    EXPECT_EQ(tr->go, 0);
}

TEST_F(SongEditingTest, TracksAllExpandLoopsExpandsALoopingTrackToFullLength) {
    TTrack* tr = g_Tracks.GetTrack(0);
    tr->len = 2;
    tr->go = 0;
    tr->note[0] = 7;
    tr->note[1] = 8;

    int tracksmodified = 0, loopsexpanded = 0;
    song.TracksAllExpandLoops(tracksmodified, loopsexpanded);

    EXPECT_EQ(tracksmodified, 1);
    EXPECT_EQ(loopsexpanded, 62);
    EXPECT_EQ(tr->len, g_Tracks.GetMaxTrackLength());
    EXPECT_EQ(tr->go, -1);
    EXPECT_EQ(tr->note[63], 8); // (63 - 2) % 2 == 1 -> repeats note[1]
}

// --- RenumberAllTracks ---

TEST_F(SongEditingTest, RenumberAllTracksByColumnsMovesTheFirstUsedTrackToZero) {
    (*song.GetSong())[0][0] = 5;
    g_Tracks.GetTrack(5)->note[0] = 9;

    song.RenumberAllTracks(1); // 1 = vertically in columns

    EXPECT_EQ(g_Tracks.GetTrack(0)->note[0], 9);
    EXPECT_EQ((*song.GetSong())[0][0], 0);
}

// --- ClearAllInstrumentsUnusedInAnyTrack ---

TEST_F(SongEditingTest, ClearAllInstrumentsUnusedInAnyTrackCountsInstrumentsNotReferencedByAnyTrack) {
    g_Tracks.GetTrack(0)->len = 4;
    g_Tracks.GetTrack(0)->instr[0] = 2; // instrument 2 is used

    g_Instruments.GetInstrument(7)->parameters[0] = 1; // instrument 7 is unused but not empty (CalculateNotEmpty checks parameters/envelope, not name)

    int cleared = song.ClearAllInstrumentsUnusedInAnyTrack();

    EXPECT_EQ(cleared, 1);
}

// --- RenumberAllInstruments ---

TEST_F(SongEditingTest, RenumberAllInstrumentsType1RemovesGapsInUsageOrder) {
    g_Tracks.GetTrack(0)->len = 4;
    g_Tracks.GetTrack(0)->instr[0] = 5; // only instrument 5 is used, elsewhere is a gap

    memcpy(g_Instruments.GetInstrument(5)->name, "Lead", 4);

    song.RenumberAllInstruments(1); // 1 = remove gaps

    EXPECT_STREQ(g_Instruments.GetInstrument(0)->name, "Lead");
    EXPECT_EQ(g_Tracks.GetTrack(0)->instr[0], 0); // remapped to the new index
}

TEST_F(SongEditingTest, RenumberAllInstrumentsType2OrdersByFirstUseInTracks) {
    g_Tracks.GetTrack(0)->len = 4;
    g_Tracks.GetTrack(0)->instr[0] = 3;
    g_Tracks.GetTrack(0)->instr[1] = 1; // used second, but has a lower instrument number

    memcpy(g_Instruments.GetInstrument(3)->name, "First", 5);
    memcpy(g_Instruments.GetInstrument(1)->name, "Second", 6);

    song.RenumberAllInstruments(2); // 2 = order by usage in tracks

    EXPECT_STREQ(g_Instruments.GetInstrument(0)->name, "First");
    EXPECT_STREQ(g_Instruments.GetInstrument(1)->name, "Second");
    EXPECT_EQ(g_Tracks.GetTrack(0)->instr[0], 0);
    EXPECT_EQ(g_Tracks.GetTrack(0)->instr[1], 1);
}

// --- SetBookmark ---

TEST_F(SongEditingTest, SetBookmarkStoresCurrentPositionWhenSpeedIsValid) {
    song.SongSetActiveLine(3);
    song.SetActiveLine(4);
    TInfo info = {};
    song.GetSongInfoPars(&info);
    info.speed = 6;
    song.SetSongInfoPars(&info);

    EXPECT_TRUE(song.SetBookmark());
    EXPECT_EQ(song.GetBookmark()->songline, 3);
    EXPECT_EQ(song.GetBookmark()->trackline, 4);
    EXPECT_EQ(song.GetBookmark()->speed, 6);
}

TEST_F(SongEditingTest, SetBookmarkFailsWhenTheActiveTrackLineIsOutOfBounds) {
    song.SetActiveLine(100); // beyond g_Tracks.GetMaxTrackLength()'s default of 64
    EXPECT_FALSE(song.SetBookmark());
}
