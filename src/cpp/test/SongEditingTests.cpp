#include "gtest/gtest.h"

#include "Song.h"
#include "Clipboard.h"
#include "TuningTypes.h"
#include "RmtVersion.h"
#include <sstream>

extern int g_tracks4_8;
extern CTracks g_Tracks;
extern CInstruments g_Instruments;
extern CTrackClipboard g_TrackClipboard;
extern CSong g_Song;
extern TTuningSettings g_tuning;
extern TTuningRatios g_tuningRatios;
extern int g_rmtinstr[SONGTRACKS];

namespace {
    // Writes one "binary block" in CAtariIO::LoadBinaryBlock()'s expected
    // format (no optional 0xFFFF header): fromAddr, toAddr (little-endian
    // words), then the bytes in between.
    void WriteBinaryBlock(std::ostream& out, const unsigned char* mem, WORD fromAddr, WORD toAddr) {
        char lo = (char)(fromAddr & 0xff), hi = (char)((fromAddr >> 8) & 0xff);
        out.write(&lo, 1); out.write(&hi, 1);
        lo = (char)(toAddr & 0xff); hi = (char)((toAddr >> 8) & 0xff);
        out.write(&lo, 1); out.write(&hi, 1);
        out.write((const char*)mem + fromAddr, toAddr - fromAddr + 1);
    }
}

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

        // g_rmtinstr persists across tests like g_Instruments' data above -
        // reset it too (see PlayPressedTones/InstrPaste tests).
        for (int i = 0; i < SONGTRACKS; i++) g_rmtinstr[i] = -1;

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

// --- SetTracks / SetNTSC ---
// Both call ReInitSound() when the value actually changes, which is a
// no-op stub here (see SongEditingStub.cpp) - real g_AtariTrackerDriver/
// g_Pokey hardware simulation, not something these tests exercise.

TEST_F(SongEditingTest, SetTracksUpdatesTheGlobalTrackCountWhenChanged) {
    song.SetTracks(8);
    EXPECT_EQ(g_tracks4_8, 8);
}

TEST_F(SongEditingTest, SetTracksLeavesTheGlobalTrackCountUnchangedWhenSame) {
    song.SetTracks(4); // SetUp() already set g_tracks4_8 to 4
    EXPECT_EQ(g_tracks4_8, 4);
}

// --- ResetTuningVariables ---

TEST_F(SongEditingTest, ResetTuningVariablesUsesTheNtscOrPalBaseTuning) {
    song.SetNTSC(TRUE);
    song.ResetTuningVariables();
    EXPECT_DOUBLE_EQ(g_tuning.basetuning, 444.895778867913);
    EXPECT_EQ(g_tuning.basenote, 3);
    EXPECT_EQ(g_tuning.temperament, 0);

    song.SetNTSC(FALSE);
    song.ResetTuningVariables();
    EXPECT_DOUBLE_EQ(g_tuning.basetuning, 440.83751645933);
}

// --- InstrInfo ---
// Called with a non-null iinfo, per its own iinfo-guarded design - never
// touches the MessageBox("Instrument info") branch.

TEST_F(SongEditingTest, InstrInfoPopulatesTheOutputStructWithoutShowingAMessageBox) {
    TTrack* tr = g_Tracks.GetTrack(0);
    tr->len = 4;
    tr->instr[0] = 2;
    tr->note[0] = 10;
    tr->volume[0] = 8;

    TInstrInfo info = {};
    song.InstrInfo(2, &info);

    EXPECT_EQ(info.count, 1);
    EXPECT_EQ(info.usedintracks, 1);
    EXPECT_EQ(info.instrfrom, 2);
    EXPECT_EQ(info.instrto, 2);
    EXPECT_EQ(info.minnote, 10);
    EXPECT_EQ(info.maxnote, 10);
    EXPECT_EQ(info.minvol, 8);
    EXPECT_EQ(info.maxvol, 8);
}

// --- TrackInfo ---
// Called with a non-null tinfo, per its own tinfo-guarded design (mirroring
// InstrInfo) - never touches the MessageBox("Track Info") branch.

TEST_F(SongEditingTest, TrackInfoPopulatesTheOutputStructWithoutShowingAMessageBox) {
    (*song.GetSong())[0][0] = 5;
    (*song.GetSong())[1][2] = 5;

    TTrackInfo info = {};
    song.TrackInfo(5, &info);

    EXPECT_EQ(info.count, 2);
    EXPECT_EQ(info.lines, 2);
    EXPECT_EQ(info.usedincolumn[0], 1);
    EXPECT_EQ(info.usedincolumn[1], 0);
    EXPECT_EQ(info.usedincolumn[2], 1);
    EXPECT_EQ(info.usedincolumn[3], 0);
}

TEST_F(SongEditingTest, TrackInfoLeavesTheOutputStructUntouchedForAnOutOfRangeTrack) {
    TTrackInfo info = { 99, 99, {1,1,1,1,1,1,1,1} };

    song.TrackInfo(-1, &info);
    EXPECT_EQ(info.count, 99);

    song.TrackInfo(TRACKSNUM, &info);
    EXPECT_EQ(info.count, 99);
}

// --- MakeModule / DecodeModule ---
// Round-trip test, mirroring SongToAta/AtaToSong's approach in SongTests.cpp:
// lets the real encode/decode logic prove itself internally consistent
// rather than hand-deriving the RMT header's byte layout.

TEST_F(SongEditingTest, MakeModuleAndDecodeModuleRoundTripASimpleSong) {
    // m_mainSpeed/m_instrumentSpeed default to 0, but DecodeModule() rejects
    // a decoded speed byte of 0 as invalid (there can be no zero speed) -
    // give them valid values first.
    TInfo info = {};
    song.GetSongInfoPars(&info);
    info.mainspeed = 6;
    info.instrspeed = 2;
    song.SetSongInfoPars(&info);

    (*song.GetSong())[0][0] = 5; // song line 0, column 0 references track 5

    TTrack* tr = g_Tracks.GetTrack(5);
    tr->len = 4;
    tr->note[0] = 10;
    tr->instr[0] = 2;
    tr->volume[0] = 8;

    memcpy(g_Instruments.GetInstrument(2)->name, "Lead", 4);
    g_Instruments.GetInstrument(2)->parameters[0] = 5;

    static unsigned char mem[8192] = {};
    BYTE instrSavedFlags[INSTRSNUM] = {};
    BYTE trackSavedFlags[TRACKSNUM] = {};

    int endAddr = song.MakeModule(mem, 0, SongIOType::RMT, instrSavedFlags, trackSavedFlags);
    ASSERT_GT(endAddr, 0);
    ASSERT_LE((size_t)endAddr, sizeof(mem));

    CSong decoded;
    BYTE instrLoadedFlags[INSTRSNUM] = {};
    BYTE trackLoadedFlags[TRACKSNUM] = {};
    int version = decoded.DecodeModule(mem, 0, endAddr, instrLoadedFlags, trackLoadedFlags);

    EXPECT_EQ(version, RMTFormatVersion::V1);
    EXPECT_EQ((*decoded.GetSong())[0][0], 5);
    // DecodeModule() decodes back into the same global g_Tracks/g_Instruments
    // it was encoded from (there's only one in production too).
    EXPECT_EQ(g_Tracks.GetTrack(5)->note[0], 10);
    EXPECT_EQ(g_Tracks.GetTrack(5)->instr[0], 2);
    EXPECT_EQ(g_Tracks.GetTrack(5)->volume[0], 8);
    EXPECT_STREQ(g_Instruments.GetInstrument(2)->name, "Lead");
    EXPECT_EQ(g_Instruments.GetInstrument(2)->parameters[0], 5);
}

// --- SaveTxt ---

TEST_F(SongEditingTest, SaveTxtWritesModuleHeaderAndSongLineData) {
    TInfo info = {};
    song.GetSongInfoPars(&info);
    info.mainspeed = 6;
    info.instrspeed = 2;
    song.SetSongInfoPars(&info);

    (*song.GetSong())[0][0] = 5; // only line 0 has data - the rest stay "--"

    std::ostringstream out;
    EXPECT_TRUE(song.SaveTxt(out));

    std::string text = out.str();
    EXPECT_NE(text.find("[MODULE]"), std::string::npos);
    EXPECT_NE(text.find("[SONG]"), std::string::npos);
    EXPECT_NE(text.find("05 -- -- --\n"), std::string::npos); // track 05 in column 0, columns 1-3 empty
}

// --- SaveRMW ---
// Only characterizes what's directly observable without a LoadRMW round
// trip (LoadRMW stays deferred, see plans/SONG_IO_SONG_REMAINING_PLAN.md):
// that it succeeds and starts with the (now compile-time) version string.

TEST_F(SongEditingTest, SaveRMWWritesTheVersionStringFirst) {
    std::ostringstream out;
    EXPECT_TRUE(song.SaveRMW(out));

    std::string content = out.str();
    ASSERT_GE(content.size(), strlen(RMT_VERSION_STRING));
    EXPECT_EQ(content.substr(0, strlen(RMT_VERSION_STRING)), RMT_VERSION_STRING);
}

// --- LoadRMT ---
// Builds a valid two-block RMT file in memory (module block via MakeModule,
// names block by hand) rather than hand-deriving the RMT header's byte
// layout - same round-trip philosophy as MakeModule/DecodeModule above.

TEST_F(SongEditingTest, LoadRMTDecodesTheModuleAndNamesBlocks) {
    TInfo info = {};
    song.GetSongInfoPars(&info);
    info.mainspeed = 6;
    info.instrspeed = 2;
    song.SetSongInfoPars(&info);

    (*song.GetSong())[0][0] = 5;
    g_Tracks.GetTrack(5)->len = 4;
    g_Tracks.GetTrack(5)->note[0] = 10;
    g_Tracks.GetTrack(5)->instr[0] = 2;
    memcpy(g_Instruments.GetInstrument(2)->name, "Lead", 4);

    static unsigned char mem[8192] = {};
    BYTE instrSavedFlags[INSTRSNUM] = {};
    BYTE trackSavedFlags[TRACKSNUM] = {};
    WORD fromAddr = 0x100;
    int endAddr = song.MakeModule(mem, fromAddr, SongIOType::RMT, instrSavedFlags, trackSavedFlags);
    ASSERT_GT(endAddr, 0);

    std::ostringstream blocks;
    WriteBinaryBlock(blocks, mem, fromAddr, (WORD)(endAddr - 1));

    // Names block: song name, then the name of each *loaded* instrument (in
    // index order) - here just instrument 2, since it's the only one used.
    std::string namesData("TestSong", 9); // includes the trailing '\0'
    namesData += "Lead";
    namesData += '\0';
    unsigned char namesMem[64] = {};
    memcpy(namesMem, namesData.data(), namesData.size());
    WriteBinaryBlock(blocks, namesMem, 0, (WORD)(namesData.size() - 1));

    std::istringstream in(blocks.str());
    CSong decoded;
    ASSERT_TRUE(decoded.LoadRMT(in));

    EXPECT_STREQ(decoded.GetName(), "TestSong");
    // Unlike GetName(), the raw instrument name field isn't trimmed - LoadRMT
    // fills the remainder with spaces up to INSTRUMENT_NAME_MAX_LEN.
    CString instrName = g_Instruments.GetInstrument(2)->name;
    instrName.TrimRight();
    EXPECT_STREQ(instrName, "Lead");
}

// --- SongJump / SongUp / SongDown / SongSubsongPrev / SongSubsongNext ---
// All conditionally call Stop()/Play() only inside "if (m_play &&
// m_followplay)", never taken here (m_play defaults to PLAY_STOP).

TEST_F(SongEditingTest, SongJumpMovesForwardViaSongDown) {
    song.SongSetActiveLine(5);
    song.SongJump(3); // toline=8>5 -> SongSetActiveLine(7) then SongDown() -> 8
    EXPECT_EQ(song.SongGetActiveLine(), 8);
}

TEST_F(SongEditingTest, SongJumpMovesBackwardViaSongUp) {
    song.SongSetActiveLine(5);
    song.SongJump(-3); // toline=2<5 -> SongSetActiveLine(3) then SongUp() -> 2
    EXPECT_EQ(song.SongGetActiveLine(), 2);
}

TEST_F(SongEditingTest, SongUpWrapsToTheLastLineFromLineZero) {
    song.SongSetActiveLine(0);
    song.SongUp();
    EXPECT_EQ(song.SongGetActiveLine(), SONGLEN - 1);
}

TEST_F(SongEditingTest, SongDownWrapsToLineZeroFromTheLastLine) {
    song.SongSetActiveLine(SONGLEN - 1);
    song.SongDown();
    EXPECT_EQ(song.SongGetActiveLine(), 0);
}

TEST_F(SongEditingTest, SongSubsongNextJumpsToTheLineAfterTheNextGotoMarker) {
    song.SongSetActiveLine(5);
    (*song.GetSongGo())[7] = 2;
    song.SongSubsongNext();
    EXPECT_EQ(song.SongGetActiveLine(), 8);
}

TEST_F(SongEditingTest, SongSubsongPrevJumpsToTheLineAfterThePreviousGotoMarker) {
    song.SongSetActiveLine(10);
    song.SetActiveLine(5); // nonzero trackactiveline avoids the extra "i--" that only applies when it's exactly 0
    (*song.GetSongGo())[7] = 3;
    song.SongSubsongPrev();
    EXPECT_EQ(song.SongGetActiveLine(), 8);
    EXPECT_EQ(song.GetActiveLine(), 0); // trackactiveline always resets
}

// --- TrackUp / TrackDown ---

TEST_F(SongEditingTest, TrackUpMovesActiveLineUpWithinBounds) {
    song.SetActiveLine(5);
    song.TrackUp(2);
    EXPECT_EQ(song.GetActiveLine(), 3);
}

TEST_F(SongEditingTest, TrackUpWrapsToTheBottomWhenGoingBelowZero) {
    song.SetActiveLine(1);
    song.TrackUp(3); // 1-3=-2, g_keyboard_updowncontinue is off, so -2 + trlen(64) = 62
    EXPECT_EQ(song.GetActiveLine(), 62);
}

TEST_F(SongEditingTest, TrackDownMovesActiveLineDownWithinBounds) {
    song.SetActiveLine(3);
    song.TrackDown(2, FALSE); // stoponlastline=FALSE avoids TrackGetLastLine()'s -1-for-no-track edge case
    EXPECT_EQ(song.GetActiveLine(), 5);
}

// --- SetUECursor ---

TEST_F(SongEditingTest, SetUECursorPartTracksUpdatesTrackCursorFields) {
    int cursor[4] = { 3, 4, 1, 2 };
    song.SetUECursor(Part::PART_TRACKS, cursor);

    EXPECT_EQ(song.SongGetActiveLine(), 3);
    EXPECT_EQ(song.GetActiveLine(), 4);
    EXPECT_EQ(song.GetActiveColumn(), 1);

    // m_trackactivecur has no direct public getter - read it back via GetUECursor().
    int* readback = song.GetUECursor(Part::PART_TRACKS);
    EXPECT_EQ(readback[3], 2);
    delete[] readback;
}

// --- SongPrepareNewLine / SongPutnewemptyunusedtrack ---

TEST_F(SongEditingTest, SongPrepareNewLineFillsTheNewLineWithUnusedTracks) {
    int line = 2;
    EXPECT_TRUE(song.SongPrepareNewLine(line, -1, TRUE));

    // With nothing else in the song, each column gets the next free track.
    EXPECT_EQ((*song.GetSong())[2][0], 0);
    EXPECT_EQ((*song.GetSong())[2][1], 1);
    EXPECT_EQ((*song.GetSong())[2][2], 2);
    EXPECT_EQ((*song.GetSong())[2][3], 3);
}

TEST_F(SongEditingTest, SongPutnewemptyunusedtrackAssignsAFreeTrackToTheActivePosition) {
    song.SongSetActiveLine(0); // GetActiveColumn() defaults to column 0

    EXPECT_TRUE(song.SongPutnewemptyunusedtrack());

    EXPECT_EQ((*song.GetSong())[0][0], 0); // first free track assigned
}

// --- PlayPressedTones ---
// Confirmed safe by reading CAtariTrackerDriver's methods: they only need
// g_rmtinstr and CAtari::JSR(), which delegates to the already-stubbed
// no-op C6502::JSR() (see AtariTrackerDriverCore.cpp's header comment).

TEST_F(SongEditingTest, PlayPressedTonesRecordsTheInstrumentAndConsumesThePendingState) {
    song.SetPlayPressedTonesTNIV(0, 5, 2, 10); // track 0: note 5, instr 2, volume 10

    EXPECT_TRUE(song.PlayPressedTones());
    EXPECT_EQ(g_rmtinstr[0], 2);

    // The pending state was consumed (volume reset to -1) - a second call
    // has nothing left to play.
    g_rmtinstr[0] = -99;
    EXPECT_TRUE(song.PlayPressedTones());
    EXPECT_EQ(g_rmtinstr[0], -99);
}

// --- InstrPaste ---

TEST_F(SongEditingTest, InstrPasteNormalPasteCopiesTheClipboardIntoTheActiveInstrument) {
    song.ActiveInstrSet(3);
    memcpy(g_Instruments.GetInstrument(3)->name, "Lead", 4);
    song.InstrCopy(); // populates m_instrclipboard for real

    song.ActiveInstrSet(5); // switch to a different, empty instrument
    song.InstrPaste(0); // 0 = normal paste

    CString pastedName = g_Instruments.GetInstrument(5)->name;
    pastedName.TrimRight();
    EXPECT_STREQ(pastedName, "Lead");
    EXPECT_EQ(g_Instruments.GetInstrument(5)->activeEditSection, InstrumentSection::NAME);
}

// --- Stop ---
// Calls the real g_SongTimer.WaitForTimerRoutineProcessed(), confirmed a
// safe no-op as long as SetTimer() is never called (see
// SongEditingStub.cpp's g_SongTimer comment) - which nothing in this test
// binary does.

TEST_F(SongEditingTest, StopSetsPlayModeToStopFromAnyOtherMode) {
    song.SetPlayMode(PLAY_TRACK);
    song.Stop();
    EXPECT_EQ(song.GetPlayMode(), PLAY_STOP);
}

// --- Play ---
// Also calls the real g_SongTimer.WaitForTimerRoutineProcessed() (same
// safety argument as Stop() above) and g_Atari.Init() (PLAY_SONG mode only -
// delegates to the already-stubbed no-op C6502::Init()).

TEST_F(SongEditingTest, PlaySetsPlayModeAndInitializesPlayLines) {
    song.SongSetActiveLine(3);
    song.SetActiveLine(4);

    EXPECT_TRUE(song.Play(PLAY_TRACK, FALSE));

    EXPECT_EQ(song.GetPlayMode(), PLAY_TRACK);
    EXPECT_EQ(song.SongGetPlayLine(), 3); // m_songplayline = m_songactiveline
    EXPECT_EQ(song.GetPlayLine(), 0);     // special=0 (default) -> m_trackplayline = 0
}

// --- PlayBeat ---

TEST_F(SongEditingTest, PlayBeatSendsTheNoteAndInstrumentFromTheCurrentTrackLine) {
    (*song.GetSong())[0][0] = 5; // song line 0, column 0 -> track 5
    song.SongSetActiveLine(0);
    song.SetPlayMode(PLAY_TRACK);
    song.SongSetPlayLine(0);
    song.SetPlayLine(0);

    TTrack* tr = g_Tracks.GetTrack(5);
    tr->len = 4;
    tr->note[0] = 20;
    tr->instr[0] = 3;
    tr->volume[0] = 10;

    EXPECT_TRUE(song.PlayBeat());
    EXPECT_EQ(g_rmtinstr[0], 3);
}

// --- PlayVBI ---

TEST_F(SongEditingTest, PlayVBIAdvancesTheTrackPlayLineOnceSpeedElapses) {
    song.SetPlayMode(PLAY_TRACK);
    song.SongSetPlayLine(0);
    song.SetPlayLine(5); // m_speeda defaults to 0, so "m_speeda--" makes the "too soon" check fail and it proceeds

    EXPECT_TRUE(song.PlayVBI());
    EXPECT_EQ(song.GetPlayLine(), 6);
}

// --- ClearSong ---
// ClearSong()'s only real hazard - a real MFC AfxGetMainWnd()/CMainFrame
// call to sync a UI combo box - was extracted into its own
// SyncSkipLinesAfterNoteInsertComboBox(), stubbed as a no-op here (see
// SongEditingStub.cpp). Everything else it touches (g_Tracks/g_Instruments/
// g_Undo/g_TrackClipboard/g_Atari.Init()/g_AtariTrackerDriver->Init(), plus
// a handful of trivial BOOL/int/CString globals) is real in this test
// binary, confirmed while scoping this move (see plans/NOTES.md).

TEST_F(SongEditingTest, ClearSongResetsSongDataAndPositionBackToDefaults) {
    (*song.GetSong())[0][0] = 5;
    (*song.GetSongGo())[2] = 7;

    song.SongSetActiveLine(3);
    song.SetActiveLine(4);
    song.SongSetPlayLine(3);
    song.SetPlayLine(4);
    song.ActiveInstrSet(5);
    song.SetFollowPlayMode(FALSE);

    TInfo info = {};
    song.GetSongInfoPars(&info);
    info.speed = 6;
    info.mainspeed = 6;
    info.instrspeed = 3;
    strncpy(info.songname, "Custom", SONG_NAME_MAX_LEN);
    song.SetSongInfoPars(&info);

    EXPECT_TRUE(song.SetBookmark());

    song.ClearSong(4);

    EXPECT_EQ((*song.GetSong())[0][0], -1);
    EXPECT_EQ((*song.GetSongGo())[2], -1);

    EXPECT_EQ(song.SongGetActiveLine(), 0);
    EXPECT_EQ(song.GetActiveLine(), 0);
    EXPECT_EQ(song.SongGetPlayLine(), 0);
    EXPECT_EQ(song.GetPlayLine(), 0);
    EXPECT_EQ(song.GetActiveInstr(), 0);
    EXPECT_TRUE(song.GetFollowPlayMode());

    TInfo cleared = {};
    song.GetSongInfoPars(&cleared);
    EXPECT_EQ(cleared.speed, 16);
    EXPECT_EQ(cleared.mainspeed, 16);
    EXPECT_EQ(cleared.instrspeed, 1);
    CString name(cleared.songname, SONG_NAME_MAX_LEN);
    name.TrimRight();
    EXPECT_STREQ(name, "Noname song");

    EXPECT_EQ(song.GetBookmark()->songline, -1);
    EXPECT_EQ(song.GetBookmark()->trackline, -1);
    EXPECT_EQ(song.GetBookmark()->speed, -1);

    EXPECT_STREQ(song.GetFilename(), "");
    EXPECT_EQ(song.GetIOType(), SongIOType::NONE);
}

TEST_F(SongEditingTest, ClearSongSetsTheTrackCount) {
    song.ClearSong(8);
    EXPECT_EQ(g_tracks4_8, 8);

    song.ClearSong(4);
    EXPECT_EQ(g_tracks4_8, 4);
}
