#include "gtest/gtest.h"

#include "Song.h"
#include "Clipboard.h"
#include "TuningTypes.h"
#include "RmtVersion.h"
#include "RmtExporter.h"
#include "ASMFileExporter.h"
#include "AtariIO.h"
#include "SongContainer.h"
#include "SAPFileExporter.h"
#include "SongExporter.h"
#include "Messages.h"
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
    out.write(&lo, 1);
    out.write(&hi, 1);
    lo = (char)(toAddr & 0xff);
    hi = (char)((toAddr >> 8) & 0xff);
    out.write(&lo, 1);
    out.write(&hi, 1);
    out.write((const char*)mem + fromAddr, toAddr - fromAddr + 1);
}
} // namespace

// Exercises the CSong/CTrackClipboard editing methods implemented in
// SongEditing.cpp/ClipboardCore.cpp - see plans/NOTES.md for the Song.cpp/
// IO_Song.cpp triage that identified this "safe cluster" (only touches
// g_Tracks/g_Instruments/g_Undo/g_TrackClipboard/g_tracks4_8, all confirmed
// cheap). g_Undo's ChangeTrack/ChangeSong now have real behavior too (see
// plans/UNDO_PLAN.md, UndoTests.cpp) - these methods call them only to
// *record* an edit for later undo, so the tests below still only assert on
// the edit's own visible effect, not the undo recording (UndoTests.cpp
// covers that separately). Likewise CInstruments::ClearInstrument()/
// MemorizeOctaveAndVolume()/RememberOctaveAndVolume() are stubbed as no-ops
// (see InstrumentsStub.cpp) since their real bodies touch
// g_AtariTrackerDriver/g_keyboard_RememberOctavesAndVolumes - but
// CInstruments::Update() now has real behavior (IO_Instruments.cpp needed no
// Global.h dependency at all, see plans/SONG_IO_SONG_REMAINING_PLAN.md).

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
        for (int i = 0; i < SONGTRACKS; i++) {
            g_rmtinstr[i] = -1;
        }

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
            for (int col = 0; col < SONGTRACKS; col++) {
                (*songArr)[line][col] = -1;
            }
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
    (*song.GetSong())[0][0] = 5; // a used track at line 0
    (*song.GetSongGo())[1] = 0; // line 1: "goto line 0" - closes the loop

    CString result;
    EXPECT_EQ(song.GetSubsongParts(result), 1);
    EXPECT_STREQ(result, "00 ");
}

// --- MarkTF_USED / MarkTF_NOEMPTY ---

TEST_F(SongEditingTest, MarkTFUsedMarksTracksReferencedByNonGotoLines) {
    (*song.GetSong())[0][0] = 3;
    (*song.GetSongGo())[1] = 5; // goto line: its track column is ignored
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
    EXPECT_EQ(cursor[3], 3); // cursor wrapped to the previous speed column
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

    EXPECT_EQ((*song.GetSong())[0][0], 1); // untouched
    EXPECT_EQ((*song.GetSong())[1][0], -1); // newly inserted, empty
    EXPECT_EQ((*song.GetSong())[2][0], 2); // shifted down
}

TEST_F(SongEditingTest, SongDeleteLineShiftsSubsequentLinesUp) {
    (*song.GetSong())[0][0] = 1;
    (*song.GetSong())[1][0] = 2;

    song.SongDeleteLine(0);

    EXPECT_EQ((*song.GetSong())[0][0], 2); // shifted up
    EXPECT_EQ((*song.GetSong())[SONGLEN - 1][0], -1); // vacated slot at the end
}

// --- SongInsertCopyOrCloneOfSongLinesApply ---
// Extracted from SongInsertCopyOrCloneOfSongLines() - its two MessageBox
// calls are guard-only errors on song/track-range overrun, avoided here by
// using valid from/to/line values and enough free tracks.

TEST_F(SongEditingTest, SongInsertCopyOrCloneOfSongLinesApplyCopiesTheSourceLineWhenNotCloning) {
    (*song.GetSong())[0][0] = 5;

    int line = 1;
    EXPECT_TRUE(song.SongInsertCopyOrCloneOfSongLinesApply(line, 0, 0, FALSE, 0, 100));

    EXPECT_EQ((*song.GetSong())[0][0], 5); // source untouched
    EXPECT_EQ((*song.GetSong())[1][0], 5); // copy landed at the insert point, same track number
}

TEST_F(SongEditingTest, SongInsertCopyOrCloneOfSongLinesApplyClonesIntoANewTrackWhenCloning) {
    (*song.GetSong())[0][0] = 5;
    TTrack* src = g_Tracks.GetTrack(5);
    src->len = 2;
    src->note[0] = 10;
    src->instr[0] = 1;
    src->volume[0] = 8;

    int line = 1;
    // tuning=0, volumep=100 - no actual edit, just characterizes that cloning
    // creates a distinct track rather than reusing track 5.
    EXPECT_TRUE(song.SongInsertCopyOrCloneOfSongLinesApply(line, 0, 0, TRUE, 0, 100));

    EXPECT_EQ((*song.GetSong())[0][0], 5); // source untouched
    int clonedTrack = (*song.GetSong())[1][0];
    EXPECT_NE(clonedTrack, 5); // cloned into a different, previously-unused track
    EXPECT_NE(clonedTrack, -1);

    TTrack* dst = g_Tracks.GetTrack(clonedTrack);
    EXPECT_EQ(dst->note[0], 10);
    EXPECT_EQ(dst->instr[0], 1);
    EXPECT_EQ(dst->volume[0], 8);
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
    (*song.GetSong())[0][0] = 1; // song points at the duplicate

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
    for (int i = 0; i < TRACKLEN; i++) {
        tr->note[i] = 5; // a full-length track repeating every line
    }

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

// --- InstrChangeApply ---
// Extracted from InstrChange() - dual-mode like InstrInfo/TrackInfo, called
// here with a non-null resultMsg so it never touches the
// MessageBox("Instrument changes") branch.

TEST_F(SongEditingTest, InstrChangeApplyRemapsMatchingNotesInstrumentsAndVolumes) {
    TTrack* tr = g_Tracks.GetTrack(0);
    tr->len = 1;
    tr->note[0] = 10;
    tr->instr[0] = 2;
    tr->volume[0] = 8;

    TInstrChangeParams p = {};
    p.snotefrom = 10;
    p.snoteto = 10;
    p.svolmin = 8;
    p.svolmax = 8;
    p.sinstrfrom = 2;
    p.sinstrto = 2;
    p.dnotefrom = 15;
    p.dnoteto = 15;
    p.dvolmin = 5;
    p.dvolmax = 5;
    p.dinstrfrom = 3;
    p.dinstrto = 3;
    p.onlytrack = -1;
    p.onlychannels = -1;
    p.onlysonglinefrom = -1;
    p.onlysonglineto = -1;

    CString resultMsg;
    song.InstrChangeApply(p, &resultMsg);

    EXPECT_EQ(tr->note[0], 15);
    EXPECT_EQ(tr->instr[0], 3);
    EXPECT_EQ(tr->volume[0], 5);
    EXPECT_NE(resultMsg.Find("successfully"), -1);
}

TEST_F(SongEditingTest, InstrChangeApplyOnlyTrackRestrictsTheChangeToOneTrack) {
    TTrack* tr0 = g_Tracks.GetTrack(0);
    tr0->len = 1;
    tr0->note[0] = 10;
    tr0->instr[0] = 2;
    tr0->volume[0] = 8;

    TTrack* tr1 = g_Tracks.GetTrack(1);
    tr1->len = 1;
    tr1->note[0] = 10;
    tr1->instr[0] = 2;
    tr1->volume[0] = 8;

    TInstrChangeParams p = {};
    p.snotefrom = 10;
    p.snoteto = 10;
    p.svolmin = 8;
    p.svolmax = 8;
    p.sinstrfrom = 2;
    p.sinstrto = 2;
    p.dnotefrom = 15;
    p.dnoteto = 15;
    p.dvolmin = 5;
    p.dvolmax = 5;
    p.dinstrfrom = 3;
    p.dinstrto = 3;
    p.onlytrack = 0; // restrict to track 0 only
    p.onlychannels = -1;
    p.onlysonglinefrom = -1;
    p.onlysonglineto = -1;

    CString resultMsg;
    song.InstrChangeApply(p, &resultMsg);

    EXPECT_EQ(tr0->note[0], 15); // changed
    EXPECT_EQ(tr1->note[0], 10); // untouched, restricted to track 0 only
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
    TTrackInfo info = {99, 99, {1, 1, 1, 1, 1, 1, 1, 1}};

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

// --- LoadTxt ---
// Round-trips through SaveTxt, same philosophy as LoadRMT's round trip
// below. LoadTxt() has no unconditional-success dialog to avoid (unlike
// LoadRMW's version-mismatch MessageBox) - it only ever fails silently by
// leaving fields at their ClearSong() defaults for a segment it doesn't
// recognize.
//
// BUG (pre-existing, not introduced by this move - confirmed by reading
// SaveTxt's exact byte output against LoadTxt's parser): SaveTxt() writes a
// blank "gap" line between the [MODULE] header block and "[SONG]" (and
// likely before "[INSTRUMENT]"/"[TRACK]" too, via CInstruments::SaveAll()/
// CTracks::SaveAll()'s TXT format). LoadTxt()'s inner [MODULE]-segment loop
// detects the next segment by reading one byte at a time and checking for
// '[' - but that gap's '\n' is read as that byte first, not '[', so the
// '[' that starts "[SONG]" is never recognized as a segment boundary and
// the whole segment is silently skipped. Net effect: loading a .txt file
// that RMT itself just saved does not restore any song data. This is
// characterized as-is (the header fields it does parse correctly, and the
// song data it doesn't) rather than fixed, per this effort's "lock in
// current behavior first" scope - see plans/NOTES.md.

TEST_F(SongEditingTest, LoadTxtParsesTheModuleHeaderButNotTheSongDataDueToAPreExistingBug) {
    TInfo info = {};
    song.GetSongInfoPars(&info);
    info.mainspeed = 6;
    info.instrspeed = 2;
    strncpy(info.songname, "TestSong", SONG_NAME_MAX_LEN);
    song.SetSongInfoPars(&info);

    (*song.GetSong())[0][0] = 5;

    std::ostringstream out;
    ASSERT_TRUE(song.SaveTxt(out));

    std::istringstream in(out.str());
    CSong loaded;
    ASSERT_TRUE(loaded.LoadTxt(in));

    // The [MODULE] header block parses correctly...
    EXPECT_EQ(g_tracks4_8, 4); // RMT: 04 round-trips g_tracks4_8 via SetTracks()
    TInfo loadedInfo = {};
    loaded.GetSongInfoPars(&loadedInfo);
    EXPECT_EQ(loadedInfo.mainspeed, 6);
    EXPECT_EQ(loadedInfo.instrspeed, 2);
    CString name(loadedInfo.songname, SONG_NAME_MAX_LEN);
    name.TrimRight();
    EXPECT_STREQ(name, "TestSong");

    // ...but "[SONG]" itself is never recognized as a segment boundary (see
    // the BUG comment above), so the song grid stays at ClearSong()'s -1
    // default instead of the saved track 5.
    EXPECT_EQ((*loaded.GetSong())[0][0], -1);
}

// --- SaveRMW ---

TEST_F(SongEditingTest, SaveRMWWritesTheVersionStringFirst) {
    std::ostringstream out;
    EXPECT_TRUE(song.SaveRMW(out));

    std::string content = out.str();
    ASSERT_GE(content.size(), strlen(RMT_VERSION_STRING));
    EXPECT_EQ(content.substr(0, strlen(RMT_VERSION_STRING)), RMT_VERSION_STRING);
}

// --- LoadRMW ---
// Round-trips through SaveRMW. LoadRMW's version-mismatch branch (a real
// MessageBox, unconditional on the error path) is deliberately never
// exercised - only ever fed a stream that starts with a matching version
// string, same "avoidable with valid test data" treatment as LoadRMT's
// guard-only error branches.

TEST_F(SongEditingTest, LoadRMWRoundTripsSongDataThroughSaveRMW) {
    TInfo info = {};
    song.GetSongInfoPars(&info);
    info.mainspeed = 6;
    info.instrspeed = 2;
    strncpy(info.songname, "TestSong", SONG_NAME_MAX_LEN);
    song.SetSongInfoPars(&info);

    (*song.GetSong())[0][0] = 5;
    (*song.GetSongGo())[2] = 7;

    std::ostringstream out;
    ASSERT_TRUE(song.SaveRMW(out));

    std::istringstream in(out.str());
    CSong loaded;
    ASSERT_TRUE(loaded.LoadRMW(in));

    EXPECT_EQ((*loaded.GetSong())[0][0], 5);
    EXPECT_EQ((*loaded.GetSongGo())[2], 7);

    TInfo loadedInfo = {};
    loaded.GetSongInfoPars(&loadedInfo);
    EXPECT_EQ(loadedInfo.mainspeed, 6);
    EXPECT_EQ(loadedInfo.instrspeed, 2);

    CString name(loadedInfo.songname, SONG_NAME_MAX_LEN);
    name.TrimRight();
    EXPECT_STREQ(name, "TestSong");
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

// --- CRmtExporter::ExportAsRMT ---
// Round-trips through LoadRMT - the two blocks it writes are exactly what
// LoadRMT expects, so this exercises ExportAsRMT for real rather than
// hand-deriving the RMT header's byte layout, same philosophy as LoadRMT's
// own test above (which instead builds those blocks by hand).

TEST_F(SongEditingTest, ExportAsRMTRoundTripsThroughLoadRMT) {
    TInfo info = {};
    song.GetSongInfoPars(&info);
    info.mainspeed = 6; // DecodeModule() rejects a zero speed byte as invalid
    info.instrspeed = 2;
    strncpy(info.songname, "TestSong", SONG_NAME_MAX_LEN);
    song.SetSongInfoPars(&info);

    (*song.GetSong())[0][0] = 5;
    g_Tracks.GetTrack(5)->len = 4;
    g_Tracks.GetTrack(5)->note[0] = 10;
    g_Tracks.GetTrack(5)->instr[0] = 2;
    memcpy(g_Instruments.GetInstrument(2)->name, "Lead", 4);

    TExportDescription exportDesc{};
    exportDesc.targetAddrOfModule = 0x4000;
    int maxAddr = song.MakeModule(exportDesc.mem, exportDesc.targetAddrOfModule, SongIOType::RMT, exportDesc.instrumentSavedFlags, exportDesc.trackSavedFlags);
    ASSERT_GT(maxAddr, 0);
    exportDesc.firstByteAfterModule = maxAddr;

    std::ostringstream out;
    ASSERT_TRUE(CRmtExporter::ExportAsRMT(song, out, &exportDesc));

    std::istringstream in(out.str());
    CSong decoded;
    ASSERT_TRUE(decoded.LoadRMT(in));

    EXPECT_STREQ(decoded.GetName(), "TestSong");
    CString instrName = g_Instruments.GetInstrument(2)->name;
    instrName.TrimRight();
    EXPECT_STREQ(instrName, "Lead");
}

// --- CRmtExporter::ExportAsStrippedRMTApply ---
// Extracted from ExportAsStrippedRMT() - decoded directly via
// CAtariIO::LoadBinaryBlock()/CSong::DecodeModule() rather than LoadRMT():
// ExportAsStrippedRMTApply() only ever writes a single block (no names
// block), and LoadRMT() shows a real, blocking "Info" MessageBox when it
// doesn't find a second block - a hazard confirmed firsthand while testing
// ExportAsRMT (see plans/NOTES.md), so it's never fed a single-block input.

TEST_F(SongEditingTest, ExportAsStrippedRMTApplyWritesADecodableModuleBlock) {
    TInfo info = {};
    song.GetSongInfoPars(&info);
    info.mainspeed = 6; // DecodeModule() rejects a zero speed byte as invalid
    info.instrspeed = 2;
    song.SetSongInfoPars(&info);

    (*song.GetSong())[0][0] = 5;
    g_Tracks.GetTrack(5)->len = 4;
    g_Tracks.GetTrack(5)->note[0] = 10;
    g_Tracks.GetTrack(5)->instr[0] = 2;

    std::ostringstream out;
    ASSERT_TRUE(CRmtExporter::ExportAsStrippedRMTApply(song, out, 0x4000, FALSE));

    std::istringstream in(out.str());
    static unsigned char mem[65536] = {};
    WORD fromAddr, toAddr;
    int len = CAtariIO::LoadBinaryBlock(in, mem, fromAddr, toAddr);
    ASSERT_GT(len, 0);
    EXPECT_EQ(fromAddr, 0x4000);

    BYTE instrLoadedFlags[INSTRSNUM] = {};
    BYTE trackLoadedFlags[TRACKSNUM] = {};
    CSong decoded;
    EXPECT_GT(decoded.DecodeModule(mem, fromAddr, toAddr + 1, instrLoadedFlags, trackLoadedFlags), 0);
}

TEST_F(SongEditingTest, ExportAsStrippedRMTApplyWritesADecodableModuleBlockWhenSfxSupportIsOn) {
    TInfo info = {};
    song.GetSongInfoPars(&info);
    info.mainspeed = 6;
    info.instrspeed = 2;
    song.SetSongInfoPars(&info);

    (*song.GetSong())[0][0] = 5;
    g_Tracks.GetTrack(5)->len = 4;
    g_Tracks.GetTrack(5)->note[0] = 10;
    g_Tracks.GetTrack(5)->instr[0] = 2;

    std::ostringstream out;
    ASSERT_TRUE(CRmtExporter::ExportAsStrippedRMTApply(song, out, 0x5000, TRUE));

    std::istringstream in(out.str());
    static unsigned char mem[65536] = {};
    WORD fromAddr, toAddr;
    int len = CAtariIO::LoadBinaryBlock(in, mem, fromAddr, toAddr);
    ASSERT_GT(len, 0);
    EXPECT_EQ(fromAddr, 0x5000); // sfxSupport doesn't affect the target address

    BYTE instrLoadedFlags[INSTRSNUM] = {};
    BYTE trackLoadedFlags[TRACKSNUM] = {};
    CSong decoded;
    EXPECT_GT(decoded.DecodeModule(mem, fromAddr, toAddr + 1, instrLoadedFlags, trackLoadedFlags), 0);
}

// --- CASMFileExporter::ExportAsAsmApply ---
// Extracted from ExportAsAsm() - g_PrefixForAllAsmLabels is left at its
// default empty value here (the wrapper writes the dialog's confirmed
// value into it before calling this, so this Apply function just reads
// whatever is already there).

TEST_F(SongEditingTest, ExportAsAsmApplyWritesTracksOnlyOutput) {
    (*song.GetSong())[0][0] = 5; // marks track 5 as "used" via MarkTF_USED
    TTrack* tr = g_Tracks.GetTrack(5);
    tr->len = 2;
    tr->note[0] = 10;
    tr->instr[0] = 2;

    std::ostringstream out;
    EXPECT_TRUE(CASMFileExporter::ExportAsAsmApply(song, out, 1 /* Tracks only */, 1 /* notes */, 1 /* notes only */));

    std::string text = out.str();
    EXPECT_NE(text.find(";ASM notation source"), std::string::npos);
    EXPECT_NE(text.find(";Track $05"), std::string::npos);
}

// --- CASMFileExporter::BuildRelocatableAsm ---
// Already a pure, dialog-independent function - no split needed, just
// linked directly (see ASMFileExporterCore.cpp).

TEST_F(SongEditingTest, BuildRelocatableAsmProducesAssemblerSourceForAValidModule) {
    TInfo info = {};
    song.GetSongInfoPars(&info);
    info.mainspeed = 6;
    info.instrspeed = 2;
    song.SetSongInfoPars(&info);

    (*song.GetSong())[0][0] = 5;
    g_Tracks.GetTrack(5)->len = 4;
    g_Tracks.GetTrack(5)->note[0] = 10;
    g_Tracks.GetTrack(5)->instr[0] = 2;

    TExportDescription exportDesc{};
    exportDesc.targetAddrOfModule = 0x4000;
    int maxAddr = song.MakeModule(exportDesc.mem, exportDesc.targetAddrOfModule, SongIOType::RMT, exportDesc.instrumentSavedFlags, exportDesc.trackSavedFlags);
    ASSERT_GT(maxAddr, 0);
    exportDesc.firstByteAfterModule = maxAddr;

    CString asmCode;
    BOOL ok = CASMFileExporter::BuildRelocatableAsm(song, asmCode, &exportDesc, "MY_SONG", "", "", "", XASM, FALSE, FALSE, FALSE, false);
    ASSERT_TRUE(ok);

    EXPECT_NE(asmCode.Find("MY_SONG"), -1);
    EXPECT_NE(asmCode.Find("RMT4"), -1); // matches the module header's "RMTx" marker (4 tracks)
}

// --- CASMFileExporter::ExportAsRelocatableAsmForRmtPlayerApply ---
// Extracted from ExportAsRelocatableAsmForRmtPlayer() - mostly a thin
// wrapper around the already-tested BuildRelocatableAsm() above.

TEST_F(SongEditingTest, ExportAsRelocatableAsmForRmtPlayerApplyWritesToStream) {
    TInfo info = {};
    song.GetSongInfoPars(&info);
    info.mainspeed = 6;
    info.instrspeed = 2;
    song.SetSongInfoPars(&info);

    (*song.GetSong())[0][0] = 5;
    g_Tracks.GetTrack(5)->len = 4;
    g_Tracks.GetTrack(5)->note[0] = 10;
    g_Tracks.GetTrack(5)->instr[0] = 2;

    TExportDescription exportDescStripped{};
    exportDescStripped.targetAddrOfModule = 0x4000;
    int maxAddr = song.MakeModule(exportDescStripped.mem, exportDescStripped.targetAddrOfModule, SongIOType::RMT, exportDescStripped.instrumentSavedFlags, exportDescStripped.trackSavedFlags);
    ASSERT_GT(maxAddr, 0);
    exportDescStripped.firstByteAfterModule = maxAddr;

    TExportDescription exportDescWithSFX = exportDescStripped; // content doesn't matter here - sfxSupport is FALSE below

    TRelocatableAsmExportParams params = {};
    params.strAsmLabelForStartOfSong = "MY_SONG";
    params.assemblerFormat = XASM;
    params.sfxSupport = FALSE;

    std::ostringstream out;
    EXPECT_TRUE(CASMFileExporter::ExportAsRelocatableAsmForRmtPlayerApply(song, out, &exportDescStripped, &exportDescWithSFX, params));

    EXPECT_NE(out.str().find("MY_SONG"), std::string::npos);
}

// --- CSong::DumpSongToPokeyStream / CSongContainer::GetPokeyStream ---
// The real Atari-hardware-adjacent piece of the SAP/LZSS/WAV/XEX export
// family (plans/EXPORTV2_PLAN.md's Tier 2), previously deferred without
// investigation. DumpSongToPokeyStream() runs a real "while (m_play !=
// PLAY_STOP) { PlayVBI(); ... }" playback loop - traced by hand and
// confirmed bounded: SongPlayNextLine() (SongCore.cpp) sets m_play =
// PLAY_STOP as soon as CPokeyStream::TrackSongLine() detects a revisited
// songline, and m_songplayline always advances (or wraps at 255) for
// PLAY_SONG/PLAY_FROM - the only two modes DumpSongToPokeyStream() is ever
// called with in production (SongContainer.cpp/SongExporter.cpp) - so a
// revisit, and therefore a stop, is guaranteed within a small, bounded
// number of songline advances regardless of song content. Independently
// confirmed by PokeyStreamTests.cpp's own
// TrackSongLineDetectsLoopOnSecondFullPassAndResolvesOnThird test, which
// traces the same state machine in isolation.
//
// CSongContainer's constructor calls ThrowRuntimeException() (a real,
// blocking MessageBox followed by exit(2), see RuntimeException.h) if the
// song isn't PLAY_STOP - song.Stop() is called defensively first, on top
// of the fixture's already-stopped default, given how severe that failure
// mode would be.

TEST_F(SongEditingTest, DumpSongToPokeyStreamRecordsFramesUntilTheSongLoops) {
    song.Stop(); // defensive - see CSongContainer hazard note above

    TInfo info = {};
    song.GetSongInfoPars(&info);
    info.mainspeed = 6;
    info.instrspeed = 1; // 0 (the fixture default) means the recording loop's "for (i < m_instrumentSpeed)" never runs
    song.SetSongInfoPars(&info);

    (*song.GetSong())[0][0] = 5;
    g_Tracks.GetTrack(5)->len = 2;
    g_Tracks.GetTrack(5)->note[0] = 10;
    g_Tracks.GetTrack(5)->instr[0] = 2;
    (*song.GetSongGo())[1] = 0; // songline 1 goes back to songline 0 - guarantees a fast loop

    CSongContainer container(song);
    const CPokeyStream& stream = container.GetPokeyStream();

    EXPECT_FALSE(stream.IsRecording()); // finished and stopped, not still recording
    EXPECT_GT(stream.GetCurrentFrame(), 0);
    // Songline 1 is a pure GOTO pass-through (redirected back to 0 before it's
    // ever "landed on" by SongPlayNextLine()), so only songline 0 is ever
    // handed to TrackSongLine() - it alone closes the loop.
    EXPECT_EQ(stream.GetSonglineCount(), 1);
}

// --- CSAPFileExporter::ExportSAP_R ---
// Extracted implicitly: CSongExporter::ExportSAP_R() (SongExporter.cpp)
// shows a real dialog (CSAPFileExportDialog::Show()) then delegates to this
// dialog-independent method with an already-populated CSAPFile - same
// "dialog gathers params, real work happens independently" shape as the
// RMT/ASM exporters, just without needing an explicit *Apply() split since
// CSAPFileExporter::ExportSAP_R() was already its own separate,
// dialog-free method. Only reachable now that CSongContainer/CSongExport
// (and the CSong::DumpSongToPokeyStream() they lazily trigger) are linked.

TEST_F(SongEditingTest, ExportSAPRWritesTheHeaderAndPokeyStreamData) {
    song.Stop(); // defensive - see the CSongContainer hazard note above

    TInfo info = {};
    song.GetSongInfoPars(&info);
    info.mainspeed = 6;
    info.instrspeed = 1;
    song.SetSongInfoPars(&info);

    (*song.GetSong())[0][0] = 5;
    g_Tracks.GetTrack(5)->len = 2;
    g_Tracks.GetTrack(5)->note[0] = 10;
    g_Tracks.GetTrack(5)->instr[0] = 2;
    (*song.GetSongGo())[1] = 0; // guarantees a fast loop, see above

    CSongContainer container(song);
    CSongExport songExport(container, "test");

    CSAPFile sapFile;
    sapFile.SetAuthor("RCoder");
    sapFile.SetName("RSong");
    sapFile.SetDate("01/01/2000");

    std::ostringstream out;
    EXPECT_TRUE(CSAPFileExporter::ExportSAP_R(songExport, sapFile, out));

    std::string text = out.str();
    EXPECT_NE(text.find("TYPE R"), std::string::npos);
    // The PokeyStream data appended after the header is raw binary, not
    // text - just confirm WriteToFile() actually appended some bytes past
    // the header (frameSize=9, stubbed - see PokeyStreamStub.cpp).
    EXPECT_GT(text.size(), (size_t)64);
}

// --- CSAPFileExporter::ExportSAP_B_LZSS ---
// Same dialog-then-delegate shape as ExportSAP_R above, but needs a real
// on-disk resource file too (resources/players/vu_player_v2.obx) - the
// first such dependency in this test suite. g_prgpath is set once, at
// static-init time, to this repo's own checked-in rmt/ folder (see
// test/AtariBinariesStub.cpp), so the real file loads for real.

TEST_F(SongEditingTest, ExportSAPBLZSSLoadsTheRealResourceAndWritesCompressedData) {
    song.Stop(); // defensive - see the CSongContainer hazard note above

    TInfo info = {};
    song.GetSongInfoPars(&info);
    info.mainspeed = 6;
    info.instrspeed = 1;
    song.SetSongInfoPars(&info);

    (*song.GetSong())[0][0] = 5;
    g_Tracks.GetTrack(5)->len = 2;
    g_Tracks.GetTrack(5)->note[0] = 10;
    g_Tracks.GetTrack(5)->instr[0] = 2;
    (*song.GetSongGo())[1] = 0; // guarantees a fast loop, see above

    CSongContainer container(song);
    CSongExport songExport(container, "test");

    CSAPFile sapFile;
    sapFile.SetType("B"); // ExportSAP_B_LZSS(), unlike ExportSAP_R(), doesn't set this itself
    sapFile.SetAuthor("BCoder");
    sapFile.SetName("BSong");
    sapFile.SetDate("01/01/2000");

    std::ostringstream out;
    ASSERT_TRUE(CSAPFileExporter::ExportSAP_B_LZSS(songExport, sapFile, out));

    std::string text = out.str();
    EXPECT_NE(text.find("TYPE B"), std::string::npos);
    // The reconstructed VUPlayer binary appended after the header is raw
    // binary, not text - just confirm the two fixed-size SaveBinaryBlock()
    // calls (0x1900-0x1EFF and 0x2000-0x27FF, 1536+2048 data bytes plus
    // their block headers) actually landed, i.e. the real resource file
    // loaded successfully rather than silently no-op'ing via the
    // "!LoadBinaryFile(...)" guard.
    EXPECT_GT(text.size(), (size_t)3500);
}

// --- CSongExporter::ExportXEX_LZSS (CXEXFile overload) ---
// Unlike ExportSAP_R/ExportSAP_B_LZSS (which delegate to the already
// dialog-free CSAPFileExporter class), this overload of ExportXEX_LZSS IS
// the dialog-independent real work itself - split into SongExporterCore.cpp
// along with its own private helpers (StrToAtariVideo/BruteforceOptimalLZSS)
// so it links without SongExporter.cpp's dialog-showing 1-arg overload
// (ShowXEXExportDialog()). Needs the same real on-disk resource file as
// ExportSAP_B_LZSS (resources/players/vu_player_v2.obx), loaded here via a
// different route (CRmtAtariBinaries::GetVUPlayerBinary() ->
// LoadResourceByteArray() -> LoadByteArray(), MFC CFile-based rather than
// std::ifstream-based) - already satisfied by the same g_prgpath test setup
// (see test/AtariBinariesStub.cpp). Calls CSong::DumpSongToPokeyStream()
// directly (not via CSongContainer) with PLAY_FROM, confirmed safe.

TEST_F(SongEditingTest, ExportXEXLZSSLoadsTheRealResourceAndWritesReconstructedBinary) {
    song.Stop(); // defensive - see the CSongContainer hazard note above

    TInfo info = {};
    song.GetSongInfoPars(&info);
    info.mainspeed = 6;
    info.instrspeed = 1;
    song.SetSongInfoPars(&info);

    (*song.GetSong())[0][0] = 5;
    g_Tracks.GetTrack(5)->len = 2;
    g_Tracks.GetTrack(5)->note[0] = 10;
    g_Tracks.GetTrack(5)->instr[0] = 2;
    (*song.GetSongGo())[1] = 0; // guarantees a fast loop, see above

    CSongContainer container(song);
    CSongExport songExport(container, "test");

    CXEXFile xexFile;
    xexFile.InitFromSong(song);
    xexFile.autoRegion = true; // skips the NOP-patching branch, simplifying the test
    xexFile.displayRasterbar = false;
    xexFile.rasterbarColor = 0;
    memset(xexFile.atariText, ' ', CXEXFile::ATARI_TEXT_SIZE);

    CSongExporter exporter;
    std::ostringstream out;
    ASSERT_TRUE(exporter.ExportXEX_LZSS(songExport, xexFile, out));

    // The output is a reconstructed Atari binary (headers + raw data, not
    // text) - just confirm a substantial amount of it actually landed, i.e.
    // the real resource file loaded and the LZSS/DumpSongToPokeyStream
    // pipeline produced real data rather than silently failing.
    std::string data = out.str();
    EXPECT_GT(data.size(), (size_t)3500);
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
    int cursor[4] = {3, 4, 1, 2};
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

// --- SongMaketracksduplicate / Songswitch4_8 ---
// Both were deferred (plans/SONG_IO_SONG_REMAINING_PLAN.md's "Decisions
// (resolved) #3") solely because of their confirmation prompt, previously
// a real, unavoidable-in-tests MessageBox(). Now testable on every branch
// via SendQuestionMessage()'s test-injectable answer (SetTestQuestionAnswer(),
// see plans/MESSAGEBOX_REFACTOR_PLAN.md).

TEST_F(SongEditingTest, SongMaketracksduplicateReturnsZeroOnAGotoLine) {
    song.SongSetActiveLine(0);
    (*song.GetSongGo())[0] = 1; // songline 0 is a goto line

    EXPECT_FALSE(song.SongMaketracksduplicate());
}

TEST_F(SongEditingTest, SongMaketracksduplicateReturnsZeroWhenNoTrackSelected) {
    song.SongSetActiveLine(0); // GetActiveColumn() defaults to column 0
    (*song.GetSong())[0][0] = -1; // no track at the active position

    EXPECT_FALSE(song.SongMaketracksduplicate());
}

TEST_F(SongEditingTest, SongMaketracksduplicateDuplicatesTheTrackWhenConfirmed) {
    song.SongSetActiveLine(0); // GetActiveColumn() defaults to column 0
    (*song.GetSong())[0][0] = 5; // used only here -> triggers the confirm prompt
    g_Tracks.GetTrack(5)->len = 2;
    g_Tracks.GetTrack(5)->note[0] = 10;
    g_Tracks.GetTrack(5)->instr[0] = 2;

    SetTestQuestionAnswer(MessageAnswer::Ok);
    EXPECT_TRUE(song.SongMaketracksduplicate());

    int newTrack = (*song.GetSong())[0][0];
    EXPECT_NE(newTrack, 5); // moved to a different, free track
    EXPECT_EQ(g_Tracks.GetTrack(newTrack)->note[0], 10); // content duplicated
    EXPECT_EQ(g_Tracks.GetTrack(newTrack)->instr[0], 2);
}

TEST_F(SongEditingTest, SongMaketracksduplicateLeavesTheTrackUnchangedWhenCancelled) {
    song.SongSetActiveLine(0);
    (*song.GetSong())[0][0] = 5;
    g_Tracks.GetTrack(5)->len = 2;
    g_Tracks.GetTrack(5)->note[0] = 10;

    SetTestQuestionAnswer(MessageAnswer::Cancel);
    EXPECT_FALSE(song.SongMaketracksduplicate());

    EXPECT_EQ((*song.GetSong())[0][0], 5); // unchanged
}

TEST_F(SongEditingTest, Songswitch4_8LeavesStateUnchangedWhenCancelled) {
    song.SetTracks(8);
    (*song.GetSong())[0][4] = 5; // an R1 column entry that would be erased if confirmed

    SetTestQuestionAnswer(MessageAnswer::Cancel);
    song.Songswitch4_8(4);

    EXPECT_EQ(g_tracks4_8, 8); // unchanged
    EXPECT_EQ((*song.GetSong())[0][4], 5); // unchanged
}

TEST_F(SongEditingTest, Songswitch4_8ClearsStereoColumnsWhenConfirmed) {
    song.SetTracks(8);
    (*song.GetSong())[0][4] = 5; // R1 column entry

    SetTestQuestionAnswer(MessageAnswer::Yes);
    song.Songswitch4_8(4);

    EXPECT_EQ(g_tracks4_8, 4);
    EXPECT_EQ((*song.GetSong())[0][4], -1); // R1-R4 columns cleared
}

TEST_F(SongEditingTest, Songswitch4_8SwitchesToStereoWhenConfirmed) {
    SetTestQuestionAnswer(MessageAnswer::Yes);
    song.Songswitch4_8(8);

    EXPECT_EQ(g_tracks4_8, 8);
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
    EXPECT_EQ(song.GetPlayLine(), 0); // special=0 (default) -> m_trackplayline = 0
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

// --- TracksOrderChangeApply ---
// Extracted from TracksOrderChange() - the dialog and its mid-function
// confirmation prompt (for clearing columns) both stay in the wrapper;
// this only reorders/clears song columns once the range and column
// mapping are known.

TEST_F(SongEditingTest, TracksOrderChangeApplyReordersAndClearsColumnsPerMapping) {
    (*song.GetSong())[0][0] = 10;
    (*song.GetSong())[0][1] = 20;
    (*song.GetSong())[0][2] = 30;
    (*song.GetSong())[0][3] = 40;
    (*song.GetSong())[1][0] = 11;
    (*song.GetSong())[1][1] = 21;
    (*song.GetSong())[1][2] = 31;
    (*song.GetSong())[1][3] = 41;

    int tracksorder[SONGTRACKS] = {1, 0, -1, 3, -1, -1, -1, -1};
    song.TracksOrderChangeApply(0, 1, tracksorder);

    EXPECT_EQ((*song.GetSong())[0][0], 20); // new col0 <- old col1
    EXPECT_EQ((*song.GetSong())[0][1], 10); // new col1 <- old col0
    EXPECT_EQ((*song.GetSong())[0][2], -1); // cleared
    EXPECT_EQ((*song.GetSong())[0][3], 40); // new col3 <- old col3 (unchanged)

    EXPECT_EQ((*song.GetSong())[1][0], 21);
    EXPECT_EQ((*song.GetSong())[1][1], 11);
    EXPECT_EQ((*song.GetSong())[1][2], -1);
    EXPECT_EQ((*song.GetSong())[1][3], 41);
}

// --- CSong::ImportTMCParseHeader / ImportTMCApply ---
// ImportTMC()'s two-phase split (see plans/IO_IMPORTER_PLAN.md): its options
// dialog needs the song name parsed from the file header to build its own
// text, so ParseHeader() does that unconditional real work (also ClearSong,
// per the original method), and Apply() does the rest of the real
// conversion given the dialog's flags. The thin wrapper (still showing two
// real dialogs) stays untested, same as every other dialog wrapper in this
// suite.

TEST_F(SongEditingTest, ImportTMCParseHeaderFailsOnATruncatedFile) {
    std::istringstream in(""); // not even a valid 4-byte header

    TImportTMCHeader header;
    EXPECT_FALSE(song.ImportTMCParseHeader(in, header));
    EXPECT_FALSE(header.ok);
}

TEST_F(SongEditingTest, ImportTMCParseHeaderSetsTheSongName) {
    unsigned char mem[1] = {'H'}; // song name field stops at the first 0 byte (from memset)
    std::ostringstream out;
    WriteBinaryBlock(out, mem, 0, 0);
    std::istringstream in(out.str());

    TImportTMCHeader header;
    ASSERT_TRUE(song.ImportTMCParseHeader(in, header));
    EXPECT_TRUE(header.ok);
    EXPECT_EQ(header.bfrom, 0);

    CString name = song.GetName();
    name.TrimRight();
    EXPECT_STREQ(name, "H");
}

TEST_F(SongEditingTest, ImportTMCApplyConvertsANoteIntoTheDestinationTrack) {
    // Hand-derived minimal TMC buffer (all offsets relative to bfrom=0):
    //  [0]       = 0xFF - doubles as the song name's first (blanked) char
    //              and the "mem[adr]==0xFF" empty-track sentinel that all
    //              127 unused track pointers (defaulting to address 0) hit.
    //  [30]      = 5    -> mainspeed = 6
    //  [31]      = 1    -> instrspeed = 1
    //  [32..159] = 0    -> all 64 instrument pointers undefined
    //  [160]     = 0xB0, [288] = 0x01 -> track_ptr[0] = 0x01B0 = 432
    //  [161..287], [289..415] = 0     -> track_ptr[1..127] = 0 (sentinel)
    //  [416..431] = songline 0: column 0 -> track 0, shift 0; columns 1-7
    //               skipped (track byte 0xFF, out of the valid 0-127 range)
    //  [432..434] = track 0's data: note 0 (byte 0x01), volume 15/15 (byte
    //               0x00), end-of-track marker (byte 0xFF, "space=64")
    unsigned char mem[435] = {};
    mem[0] = 0xFF;
    mem[30] = 5;
    mem[31] = 1;
    mem[160] = 0xB0;
    mem[288] = 0x01;
    mem[431] = 0x00;
    mem[430] = 0x00; // column 0: track 0, shift 0 (also: not a goto line)
    mem[429] = 0xFF;
    mem[428] = 0x00; // column 1: skipped
    mem[427] = 0xFF;
    mem[426] = 0x00; // column 2: skipped
    mem[425] = 0xFF;
    mem[424] = 0x00; // column 3: skipped
    mem[423] = 0xFF;
    mem[422] = 0x00; // column 4: skipped
    mem[421] = 0xFF;
    mem[420] = 0x00; // column 5: skipped
    mem[419] = 0xFF;
    mem[418] = 0x00; // column 6: skipped
    mem[417] = 0xFF;
    mem[416] = 0x00; // column 7: skipped
    mem[432] = 0x01; // note = (0x01 & 0x3f) - 1 = 0
    mem[433] = 0x00; // volume: volL = volR = 15
    mem[434] = 0xFF; // end of track (space = 64)

    std::ostringstream out;
    WriteBinaryBlock(out, mem, 0, 434);
    std::istringstream in(out.str());

    TImportTMCHeader header;
    ASSERT_TRUE(song.ImportTMCParseHeader(in, header));

    TImportTMCResult result;
    song.ImportTMCApply(header, /*usetable=*/FALSE, /*optimizeloops=*/FALSE, /*truncateunusedparts=*/FALSE, result);

    EXPECT_EQ(result.songlines, 1);
    EXPECT_EQ(result.nonemptyinstruments, 0); // no instrument pointers defined
    // numoftracks tracks the highest track *index* used (a pre-existing
    // naming quirk, not something this effort changes) - index 0 is the
    // only one used here, so it never exceeds its own initial value of 0.
    EXPECT_EQ(result.numoftracks, 0);

    EXPECT_EQ(g_tracks4_8, 4); // no stereo columns (4-7) used -> mono module
    EXPECT_EQ((*song.GetSong())[0][0], 0); // track 0 placed at songline 0, column 0

    TTrack* track0 = g_Tracks.GetTrack(0);
    EXPECT_EQ(track0->note[0], 0);
    EXPECT_EQ(track0->instr[0], 0);
    // Volume gets normalized against the instrument's own max envelope
    // volume (MakeOrFindTrackShiftLR() in IO_ImporterCore.cpp) - since
    // instrument 0 is undefined here (no instrument pointers were set up),
    // its tracked max volume defaults to 0, which floors this note's volume
    // to 0 too. This is real, faithful TMC-import behavior, not a test bug.
    EXPECT_EQ(track0->volume[0], 0);
}

// --- CSong::ImportMODParseHeader / ImportMODApply ---
// Same two-phase split as ImportTMC above, for the same reason (the options
// dialog needs the parsed channel/sample count to build its own text) - see
// plans/IO_IMPORTER_PLAN.md. Unlike ImportTMC, ImportMODApply() also needs
// continued access to the input stream (sample data lives beyond what
// ParseHeader() loads), so both calls below share the same stream object.

TEST_F(SongEditingTest, ImportMODParseHeaderFailsOnATruncatedHeader) {
    std::istringstream in(""); // shorter than the required 1084-byte header

    TImportMODHeader header;
    EXPECT_FALSE(song.ImportMODParseHeader(in, header));
    EXPECT_EQ(header.errorCode, 1);
}

TEST_F(SongEditingTest, ImportMODParseHeaderFailsOnUnrecognizedIdentification) {
    // "2CHN" parses as a 2-channel module (chnls = '2' - '0') - out of the
    // supported 4-8 range. An all-zero identification doesn't trigger this
    // guard: bytes outside the printable "space".."Z" range are treated as
    // an older, un-identified 15-sample module instead (chnls hardcoded to
    // 4, always valid) - see ImportMODParseHeader()'s fallback branch.
    std::string data(1084, '\0');
    data[1080] = '2';
    data[1081] = 'C';
    data[1082] = 'H';
    data[1083] = 'N';
    std::istringstream in(data);

    TImportMODHeader header;
    EXPECT_FALSE(song.ImportMODParseHeader(in, header));
    EXPECT_EQ(header.errorCode, 2);
}

TEST_F(SongEditingTest, ImportMODApplyConvertsANoteIntoTheDestinationTrack) {
    // Hand-derived minimal standard ProTracker ("M.K.", 31-sample, 4-channel)
    // module buffer. Total size (2116 bytes) is exact - ImportMODApply()'s
    // final guard-only warning fires if the file is shorter/longer than the
    // sample data it expects, so the layout below must add up precisely:
    //  [0..19]      song name (blank)
    //  [20..49]     sample #1's 30-byte header: [42..43] length word (BE,
    //               in 16-bit words) = 4 -> 8 bytes of real sample data;
    //               [45] volume = 0x40; repeat point/length left at 0 (no
    //               loop)
    //  [50..949]    samples #2-31's headers, all zero (length 0 -> skipped)
    //  [950]        songlen = 1 (one song order position)
    //  [951]        restart position = 0
    //  [952]        song order[0] = pattern 0
    //  [1080..1083] "M.K." identification (standard 4-channel module)
    //  [1084..2107] pattern 0's 1024 bytes (4 channels * 256), all empty
    //               cells except row 0/channel 0: period 0x06B0 (the
    //               lowest note, "C3"), sample #1, no effect
    //  [2108..2115] sample #1's 8 bytes of real (non-silent) data
    std::vector<unsigned char> buf(2116, 0);
    buf[42] = 0x00;
    buf[43] = 0x04; // sample #1 length = 4 words = 8 bytes
    buf[45] = 0x40; // sample #1 volume
    buf[950] = 1; // songlen
    buf[951] = 0; // restartpos
    buf[952] = 0; // song order[0] -> pattern 0
    buf[1080] = 'M';
    buf[1081] = '.';
    buf[1082] = 'K';
    buf[1083] = '.';
    buf[1084] = 0x06;
    buf[1085] = 0xB0;
    buf[1086] = 0x10;
    buf[1087] = 0x00; // row0/ch0: period 0x6B0, sample 1
    buf[2108] = 0;
    buf[2109] = 50;
    buf[2110] = 0;
    buf[2111] = 50;
    buf[2112] = 0;
    buf[2113] = 50;
    buf[2114] = 0;
    buf[2115] = 50;

    std::string data(reinterpret_cast<char*>(buf.data()), buf.size());
    std::istringstream in(data);

    TImportMODHeader header;
    ASSERT_TRUE(song.ImportMODParseHeader(in, header));
    EXPECT_EQ(header.chnls, 4);
    EXPECT_EQ(header.modsamples, 31);
    EXPECT_EQ(header.songlen, 1);
    EXPECT_EQ(header.modulelength, (int)buf.size());

    BYTE trackorder[8] = {0, 1, 2, 3, 4, 5, 6, 7};
    TImportMODResult result;
    song.ImportMODApply(in, header, /*rmttype=*/4, trackorder,
                        /*shiftdownoctave=*/FALSE, /*portamento=*/FALSE, /*fullvolumerange=*/FALSE,
                        /*volumeincrease=*/FALSE, /*decreaseinstrument=*/FALSE,
                        /*optimizeloops=*/FALSE, /*truncateunusedparts=*/FALSE, result);

    EXPECT_EQ(result.destnum, 1); // only channel 0 produced a non-empty track
    EXPECT_EQ(result.nonemptysamples, 1); // only sample #1 has real length
    EXPECT_EQ(g_tracks4_8, 4); // rmttype=4

    EXPECT_EQ((*song.GetSong())[0][0], 0); // track 0 placed at songline 0, column 0
    EXPECT_EQ((*song.GetSong())[0][1], -1);

    TTrack* track0 = g_Tracks.GetTrack(0);
    EXPECT_EQ(track0->note[0], 0);
    EXPECT_EQ(track0->instr[0], 1);
    EXPECT_EQ(track0->volume[0], 15); // AtariVolume(0x40) = 15 (max)

    TInstrument* instr1 = g_Instruments.GetInstrument(1);
    EXPECT_EQ(instr1->parameters[PAR_ENV_LENGTH], 1);
    EXPECT_EQ(instr1->parameters[PAR_ENV_GOTO], 1);
}
