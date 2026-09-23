#include "StdAfx.h"

#include "Song.h"

#include "Notes.h"

#include "Atari.h"
#include "AtariTrackerDriver.h"
#include "Clipboard.h"
#include "EffectsDlg.h"
#include "Global.h"
#include "Instruments.h"
#include "IOHelpers.h"
#include "MainFrm.h"
#include "PokeyController.h"
#include "PokeyRenderer.h"

#include "SongTimer.h"

#include "PokeyStream.h"
#include "Messages.h"
#include "SongExporter.h"

extern CAtariTrackerDriver* g_AtariTrackerDriver;

extern CInstruments g_Instruments;
extern CXPokey g_Pokey;

// These two should be song attributes instead

extern int g_tracks4_8;
CSongTimer g_SongTimer;

// ----------------------------------------------------------------------------

// CSong(), ~CSong(), GetName(), GetTracks(), and IsStereo() are implemented
// in SongCore.cpp (no Global.h dependency).

// CSong::SetTracks() is implemented in SongEditing.cpp.

// IsNTSC() is implemented in SongCore.cpp.

// CSong::SetNTSC() is implemented in SongEditing.cpp.

// Force a systematic Sound Reset to correctly handle Stereo and/or NTSC switch
void CSong::ReInitSound() {
    g_Pokey.ReInitSound(IsNTSC(), IsStereo());
    g_AtariTrackerDriver->GetAtari()->Init(IsNTSC());
    g_AtariTrackerDriver->Init();
}

// GetInstrumentSpeed() is implemented in SongCore.cpp.

// TODO: Move to CSontTimer

/// <summary>
/// Stop the timer and make sure that the timer event is not running
/// </summary>
void CSong::StopTimer() {
    g_SongTimer.StopTimer();
}

/// <summary>
/// Change the timing of how often the CSong::TimerRoutine is being called.
/// Depends on PAL or NTSC timing.
/// </summary>
/// <param name="ms">ms between calls (17=NTSC, 20=PAL)</param>
void CSong::ChangeTimer(int ms) {
    g_SongTimer.SetTimer(*this, ms);
}

// CSong::ClearSong() is implemented in SongEditing.cpp.

/// <summary>
/// Pushes g_SkipLinesAfterNoteInsert into the main frame's combo box, if the
/// app's main window exists. Extracted from ClearSong() as its own method: a
/// real MFC AfxGetMainWnd()/CMainFrame call, categorically different from a
/// stubbable global.
/// </summary>
void CSong::SyncSkipLinesAfterNoteInsertComboBox() {
    CMainFrame* mf = ((CMainFrame*)AfxGetMainWnd());
    if (mf) {
        mf->m_comboSkipLinesAfterNoteInsert.SetCurSel(g_SkipLinesAfterNoteInsert);
    }
}

//---

// CSong::GetSubsongParts() is implemented in SongEditing.cpp (only touches g_Tracks/g_Instruments/g_Undo/g_TrackClipboard/g_tracks4_8, not Global.h's wider dependency graph).

/// <summary>
/// Mark all tracks that are referenced in the song as USED
/// </summary>
/// <param name="arrayTRACKSNUM">Array where each used track if marked off</param>
// CSong::MarkTF_USED() is implemented in SongEditing.cpp (only touches g_Tracks/g_Instruments/g_Undo/g_TrackClipboard/g_tracks4_8, not Global.h's wider dependency graph).

// CSong::MarkTF_NOEMPTY() is implemented in SongEditing.cpp (only touches g_Tracks/g_Instruments/g_Undo/g_TrackClipboard/g_tracks4_8, not Global.h's wider dependency graph).

/* TODO: Unused
int CSong::MakeTuningBlock(unsigned char* mem, int addr)
{
    int len = 80;				// 80 bytes of general data
    for (int i = 0; i < len; ++i) mem[addr + i] = 0;

    // Block indicator: 0xF3
    mem[addr] = 0xF3;			// Tuning block indicator

    // First 16 bytes
    mem[addr + 0x01] = IsNTSC();		    				//RMT module region, 0 -> PAL, 1 -> NTSC
    mem[addr + 0x02] = g_tuning.basenote;					//base note used in tuning calculations, eg A-4
    mem[addr + 0x03] = g_tuning.temperament;				//tuning temperament, 0 -> no temperament, any number above preset number is custom (saving ratios not yet implemented)
    mem[addr + 0x04] = g_trackLinePrimaryHighlight;	//track primary line highlight
    mem[addr + 0x05] = g_trackLineSecondaryHighlight;//track secondary line highlight
    // 6 - 0xf is unused

        /** TODO: Currently unused, so save the effort for adaptation for now.

    // 64 bytes
    memcpy((mem + addr + 0x10), &g_tuning.basetuning, 8);	//base tuning frequency, double type uses 8 bytes in memory
    memcpy((mem + addr + 0x18), &g_tuningRatios.UNISON, 2);		//tuning ratio variables, each values are truncated to use 2 bytes (16-bit precision)
    memcpy((mem + addr + 0x1A), &g_tuningRatioRight.UNISON, 2);
    memcpy((mem + addr + 0x1C), &g_tuningRatios.MIN_2ND, 2);
    memcpy((mem + addr + 0x1E), &g_tuningRatioRight.MIN_2ND, 2);
    memcpy((mem + addr + 0x20), &g_tuningRatios.MAJ_2ND, 2);
    memcpy((mem + addr + 0x22), &g_tuningRatioRight.MAJ_2ND, 2);
    memcpy((mem + addr + 0x24), &g_tuningRatios.MIN_3RD, 2);
    memcpy((mem + addr + 0x26), &g_tuningRatioRight.MIN_3RD, 2);
    memcpy((mem + addr + 0x28), &g_tuningRatios.MAJ_3RD, 2);
    memcpy((mem + addr + 0x2A), &g_tuningRatioRight.MAJ_3RD, 2);
    memcpy((mem + addr + 0x2C), &g_tuningRatios.PERF_4TH, 2);
    memcpy((mem + addr + 0x2E), &g_tuningRatioRight.PERF_4TH, 2);
    memcpy((mem + addr + 0x30), &g_tuningRatios.TRITONE, 2);
    memcpy((mem + addr + 0x32), &g_tuningRatioRight.TRITONE, 2);
    memcpy((mem + addr + 0x34), &g_tuningRatios.PERF_5TH, 2);
    memcpy((mem + addr + 0x36), &g_tuningRatioRight.PERF_5TH, 2);
    memcpy((mem + addr + 0x38), &g_tuningRatios.MIN_6TH, 2);
    memcpy((mem + addr + 0x3A), &g_tuningRatioRight.MIN_6TH, 2);
    memcpy((mem + addr + 0x3C), &g_tuningRatios.MAJ_6TH, 2);
    memcpy((mem + addr + 0x3E), &g_tuningRatioRight.MAJ_6TH, 2);
    memcpy((mem + addr + 0x40), &g_tuningRatios.MIN_7TH, 2);
    memcpy((mem + addr + 0x42), &g_tuningRatioRight.MIN_7TH, 2);
    memcpy((mem + addr + 0x44), &g_tuningRatios.MAJ_7TH, 2);
    memcpy((mem + addr + 0x46), &g_tuningRatioRight.MAJ_7TH, 2);
    memcpy((mem + addr + 0x48), &g_tuningRatios.OCTAVE, 2);
    memcpy((mem + addr + 0x4A), &g_tuningRatioRight.OCTAVE, 2);
    // 4 unused bytes at the end


    return len;
}

int CSong::DecodeTuningBlock(unsigned char* mem, int addr, int endAddr)
{
    // Check the block header
    if (mem[addr] != 0xF3)
    {
        ResetTuningVariables();
        return 0;
    }
    // Get the basics
    m_ntsc = mem[addr + 0x01];
    g_tuning.basenote = mem[addr + 0x02];
    g_tuning.temperament = mem[addr + 0x03];
    g_trackLinePrimaryHighlight = mem[addr + 0x04];
    if (!g_trackLinePrimaryHighlight) g_trackLinePrimaryHighlight = 8;	//default
    g_trackLineSecondaryHighlight = mem[addr + 0x05];
    if (!g_trackLineSecondaryHighlight) g_trackLineSecondaryHighlight = 4;	//default

    /** TODO: Currently unused, so save the effort for adaptation for now.
    memcpy(&g_tuning.basetuning, (mem + addr + 0x10), 8);

    memcpy(&g_tuningRatios.UNISON, (mem + addr + 0x18), 2);
    memcpy(&g_tuningRatioRight.UNISON, (mem + addr + 0x1A), 2);
    memcpy(&g_tuningRatios.MIN_2ND, (mem + addr + 0x1C), 2);
    memcpy(&g_tuningRatioRight.MIN_2ND, (mem + addr + 0x1E), 2);
    memcpy(&g_tuningRatios.MAJ_2ND, (mem + addr + 0x20), 2);
    memcpy(&g_tuningRatioRight.MAJ_2ND, (mem + addr + 0x22), 2);
    memcpy(&g_tuningRatios.MIN_3RD, (mem + addr + 0x24), 2);
    memcpy(&g_tuningRatioRight.MIN_3RD, (mem + addr + 0x26), 2);
    memcpy(&g_tuningRatios.MAJ_3RD, (mem + addr + 0x28), 2);
    memcpy(&g_tuningRatioRight.MAJ_3RD, (mem + addr + 0x2A), 2);
    memcpy(&g_tuningRatios.PERF_4TH, (mem + addr + 0x2C), 2);
    memcpy(&g_tuningRatioRight.PERF_4TH, (mem + addr + 0x2E), 2);
    memcpy(&g_tuningRatios.TRITONE, (mem + addr + 0x30), 2);
    memcpy(&g_tuningRatioRight.TRITONE, (mem + addr + 0x32), 2);
    memcpy(&g_tuningRatios.PERF_5TH, (mem + addr + 0x34), 2);
    memcpy(&g_tuningRatioRight.PERF_5TH, (mem + addr + 0x36), 2);
    memcpy(&g_tuningRatios.MIN_6TH, (mem + addr + 0x38), 2);
    memcpy(&g_tuningRatioRight.MIN_6TH, (mem + addr + 0x3A), 2);
    memcpy(&g_tuningRatios.MAJ_6TH, (mem + addr + 0x3C), 2);
    memcpy(&g_tuningRatioRight.MAJ_6TH, (mem + addr + 0x3E), 2);
    memcpy(&g_tuningRatios.MIN_7TH, (mem + addr + 0x40), 2);
    memcpy(&g_tuningRatioRight.MIN_7TH, (mem + addr + 0x42), 2);
    memcpy(&g_tuningRatios.MAJ_7TH, (mem + addr + 0x44), 2);
    memcpy(&g_tuningRatioRight.MAJ_7TH, (mem + addr + 0x46), 2);
    memcpy(&g_tuningRatios.OCTAVE, (mem + addr + 0x48), 2);
    memcpy(&g_tuningRatioRight.OCTAVE, (mem + addr + 0x4A), 2);

    return endAddr - addr;

}
*/

// CSong::ResetTuningVariables() is implemented in SongEditing.cpp.

/// <summary>
/// Create the RMT data in memory.
/// Sets the 'instrumentSavedFlags' and 'trackSavedFlags' if a specific instrument or track is used.
/// </summary>
/// <param name="mem">Atari 64K of memory</param>
/// <param name="addr">Where in memory the start of the module will be</param>
/// <param name="iotype"></param>
/// <param name="instrumentSavedFlags"></param>
/// <param name="trackSavedFlags"></param>
/// <returns></returns>
// CSong::MakeModule() is implemented in SongEditing.cpp.

/// <summary>
/// Decode the RMT header
/// </summary>
/// <param name="mem">Atari 64K memory</param>
/// <param name="fromAddr">Address where the header is loaded</param>
/// <param name="endAddr">Address of the first byte past the header</param>
/// <param name="instrumentLoadedFlags">64 byte memory buffer to indicate if a specific instrument was loaded</param>
/// <param name="trackLoadedFlags"></param>
/// <returns>0-If the module could not be loaded, version nr otherwise</returns>
// CSong::DecodeModule() is implemented in SongEditing.cpp.

//---

// PlayPressedTonesInit() and SetPlayPressedTonesSilence() are implemented in
// SongCore.cpp.

// CSong::PlayPressedTones() is implemented in SongEditing.cpp.

// CSong::ActiveInstrSet() is implemented in SongEditing.cpp (only touches g_Tracks/g_Instruments/g_Undo/g_TrackClipboard/g_tracks4_8, not Global.h's wider dependency graph).

// CSong::ActiveInstrPrev() is implemented in SongEditing.cpp (only touches g_Tracks/g_Instruments/g_Undo/g_TrackClipboard/g_tracks4_8, not Global.h's wider dependency graph).

// CSong::ActiveInstrNext() is implemented in SongEditing.cpp (only touches g_Tracks/g_Instruments/g_Undo/g_TrackClipboard/g_tracks4_8, not Global.h's wider dependency graph).

// GetActiveInstr(), GetActiveColumn(), GetActiveLine(), GetPlayLine(),
// SetActiveLine(), and SetPlayLine() are implemented in SongCore.cpp.

// CSong::TrackUp() is implemented in SongEditing.cpp.

// CSong::TrackDown() is implemented in SongEditing.cpp.

// CSong::TrackLeft() is implemented in SongEditing.cpp (only touches g_Tracks/g_Instruments/g_Undo/g_TrackClipboard/g_tracks4_8, not Global.h's wider dependency graph).

// CSong::TrackRight() is implemented in SongEditing.cpp (only touches g_Tracks/g_Instruments/g_Undo/g_TrackClipboard/g_tracks4_8, not Global.h's wider dependency graph).

// CSong::RespectBoundaries() is implemented in SongEditing.cpp (only touches g_Tracks/g_Instruments/g_Undo/g_TrackClipboard/g_tracks4_8, not Global.h's wider dependency graph).

// CSong::TrackGetLoopingNoteInstrVol() is implemented in SongEditing.cpp (only touches g_Tracks/g_Instruments/g_Undo/g_TrackClipboard/g_tracks4_8, not Global.h's wider dependency graph).

// CSong::GetUECursor() is implemented in SongEditing.cpp (only touches g_Tracks/g_Instruments/g_Undo/g_TrackClipboard/g_tracks4_8, not Global.h's wider dependency graph).

// CSong::SetUECursor() is implemented in SongEditing.cpp.

// UECursorIsEqual() is implemented in SongCore.cpp.

//----------

// CSong::SongJump() is implemented in SongEditing.cpp.

// CSong::SongUp() is implemented in SongEditing.cpp.

// CSong::SongDown() is implemented in SongEditing.cpp.

// CSong::SongSubsongPrev() is implemented in SongEditing.cpp.

// CSong::SongSubsongNext() is implemented in SongEditing.cpp.

// CSong::SongTrackSet() is implemented in SongEditing.cpp (only touches g_Tracks/g_Instruments/g_Undo/g_TrackClipboard/g_tracks4_8, not Global.h's wider dependency graph).

// CSong::SongTrackSetByNum() is implemented in SongEditing.cpp (only touches g_Tracks/g_Instruments/g_Undo/g_TrackClipboard/g_tracks4_8, not Global.h's wider dependency graph).

// CSong::SongTrackDec() is implemented in SongEditing.cpp (only touches g_Tracks/g_Instruments/g_Undo/g_TrackClipboard/g_tracks4_8, not Global.h's wider dependency graph).

// CSong::SongTrackInc() is implemented in SongEditing.cpp (only touches g_Tracks/g_Instruments/g_Undo/g_TrackClipboard/g_tracks4_8, not Global.h's wider dependency graph).

// CSong::SongTrackEmpty() is implemented in SongEditing.cpp (only touches g_Tracks/g_Instruments/g_Undo/g_TrackClipboard/g_tracks4_8, not Global.h's wider dependency graph).

// CSong::SongTrackGoOnOff() is implemented in SongEditing.cpp (only touches g_Tracks/g_Instruments/g_Undo/g_TrackClipboard/g_tracks4_8, not Global.h's wider dependency graph).

// SongGetGo() (both overloads), SongTrackGoDec(), and SongTrackGoInc() are
// implemented in SongCore.cpp.

// CSong::SongInsertLine() is implemented in SongEditing.cpp (only touches g_Tracks/g_Instruments/g_Undo/g_TrackClipboard/g_tracks4_8, not Global.h's wider dependency graph).

// CSong::SongDeleteLine() is implemented in SongEditing.cpp (only touches g_Tracks/g_Instruments/g_Undo/g_TrackClipboard/g_tracks4_8, not Global.h's wider dependency graph).

BOOL CSong::SongInsertCopyOrCloneOfSongLines(int& line) {
    int n = (line > 0) ? line - 1 : 0;
    CInsertCopyOrCloneOfSongLinesDlg dlg;

    dlg.m_linefrom = n;
    dlg.m_lineto = n;
    dlg.m_lineinto = line;
    dlg.m_clone = 0;
    dlg.m_tuning = 0;
    dlg.m_volumep = 100; //100%

    if (dlg.DoModal() != IDOK) {
        return 1;
    }

    return SongInsertCopyOrCloneOfSongLinesApply(line, dlg.m_linefrom, dlg.m_lineto, dlg.m_clone, dlg.m_tuning, dlg.m_volumep);
}

// CSong::SongInsertCopyOrCloneOfSongLinesApply() is implemented in SongEditing.cpp.

// CSong::SongPrepareNewLine() is implemented in SongEditing.cpp.

// FindNearTrackBySongLineAndColumn() is implemented in SongCore.cpp.

// CSong::SongPutnewemptyunusedtrack() is implemented in SongEditing.cpp.

// CSong::SongMaketracksduplicate() is implemented in SongEditing.cpp - its
// confirmation prompt (SendQuestionMessage()) is now safe to trigger in
// tests via the test-injectable answer hook (see plans/MESSAGEBOX_REFACTOR_PLAN.md).

//--clipboard functions

// CSong::TrackCopy() is implemented in SongEditing.cpp (only touches g_Tracks/g_Instruments/g_Undo/g_TrackClipboard/g_tracks4_8, not Global.h's wider dependency graph).

// CSong::TrackPaste() is implemented in SongEditing.cpp (only touches g_Tracks/g_Instruments/g_Undo/g_TrackClipboard/g_tracks4_8, not Global.h's wider dependency graph).

// CSong::TrackDelete() is implemented in SongEditing.cpp (only touches g_Tracks/g_Instruments/g_Undo/g_TrackClipboard/g_tracks4_8, not Global.h's wider dependency graph).

// CSong::TrackCut() is implemented in SongEditing.cpp (only touches g_Tracks/g_Instruments/g_Undo/g_TrackClipboard/g_tracks4_8, not Global.h's wider dependency graph).

// CSong::TrackCopyFromTo() is implemented in SongEditing.cpp (only touches g_Tracks/g_Instruments/g_Undo/g_TrackClipboard/g_tracks4_8, not Global.h's wider dependency graph).

// CSong::TrackSwapFromTo() is implemented in SongEditing.cpp (only touches g_Tracks/g_Instruments/g_Undo/g_TrackClipboard/g_tracks4_8, not Global.h's wider dependency graph).

// CSong::BlockPaste() is implemented in SongEditing.cpp (only touches g_Tracks/g_Instruments/g_Undo/g_TrackClipboard/g_tracks4_8, not Global.h's wider dependency graph).

// CSong::InstrCopy() is implemented in SongEditing.cpp (only touches g_Tracks/g_Instruments/g_Undo/g_TrackClipboard/g_tracks4_8, not Global.h's wider dependency graph).

// CSong::InstrPaste() is implemented in SongEditing.cpp.

// CSong::InstrCut() is implemented in SongEditing.cpp (only touches g_Tracks/g_Instruments/g_Undo/g_TrackClipboard/g_tracks4_8, not Global.h's wider dependency graph).

// CSong::InstrDelete() is implemented in SongEditing.cpp (only touches g_Tracks/g_Instruments/g_Undo/g_TrackClipboard/g_tracks4_8, not Global.h's wider dependency graph).

// CSong::InstrInfo() is implemented in SongEditing.cpp.

void CSong::InstrChange(int instr) {
    if (!g_Instruments.IsValidInstrument(instr)) {
        return;
    }

    CInstrumentChangeDlg dlg;

    dlg.m_combo9 = dlg.m_combo11 = instr;
    dlg.m_combo10 = dlg.m_combo12 = instr;
    dlg.m_onlytrack = SongGetActiveTrack();
    dlg.m_onlysonglinefrom = dlg.m_onlysonglineto = SongGetActiveLine();

    // Change all the instrument occurences
    if (dlg.DoModal() != IDOK) {
        return;
    }

    TInstrChangeParams p;
    p.snotefrom = dlg.m_combo1;
    p.snoteto = dlg.m_combo2;
    p.svolmin = dlg.m_combo3;
    p.svolmax = dlg.m_combo4;
    p.sinstrfrom = dlg.m_combo11;
    p.sinstrto = dlg.m_combo12;
    p.dnotefrom = dlg.m_combo5;
    p.dnoteto = dlg.m_combo6;
    p.dvolmin = dlg.m_combo7;
    p.dvolmax = dlg.m_combo8;
    p.dinstrfrom = dlg.m_combo9;
    p.dinstrto = dlg.m_combo10;
    p.onlytrack = dlg.m_onlytrack;
    p.onlychannels = dlg.m_onlychannels;
    p.onlysonglinefrom = dlg.m_onlysonglinefrom;
    p.onlysonglineto = dlg.m_onlysonglineto;

    InstrChangeApply(p);
}

// CSong::InstrChangeApply() is implemented in SongEditing.cpp.

// CSong::TrackInfo() is implemented in SongEditing.cpp.

// CSong::SongCopyLine() is implemented in SongEditing.cpp (only touches g_Tracks/g_Instruments/g_Undo/g_TrackClipboard/g_tracks4_8, not Global.h's wider dependency graph).

// CSong::SongPasteLine() is implemented in SongEditing.cpp (only touches g_Tracks/g_Instruments/g_Undo/g_TrackClipboard/g_tracks4_8, not Global.h's wider dependency graph).

// CSong::SongClearLine() is implemented in SongEditing.cpp (only touches g_Tracks/g_Instruments/g_Undo/g_TrackClipboard/g_tracks4_8, not Global.h's wider dependency graph).

void CSong::TracksOrderChange() {
    // Stop the sound first
    Stop();
    CSongTracksOrderDlg dlg;
    dlg.m_songlinefrom.Format("%02X", m_TracksOrderChange_songlinefrom);
    dlg.m_songlineto.Format("%02X", m_TracksOrderChange_songlineto);
    if (dlg.DoModal() != IDOK) {
        return;
    }

    g_Undo.ChangeSong(m_songactiveline, m_trackactivecol, UETYPE_SONGDATA, 1);

    int f = Hexstr((char*)(LPCTSTR)dlg.m_songlinefrom, 2);
    int t = Hexstr((char*)(LPCTSTR)dlg.m_songlineto, 2);

    if (f < 0 || f >= SONGLEN || t < 0 || t >= SONGLEN || t < f) {
        SendErrorMessage("Error", "Bad songline (from-to) range.");
        return;
    }

    m_TracksOrderChange_songlinefrom = f;
    m_TracksOrderChange_songlineto = t;

    int c = 0;
    for (int i = 0; i < g_tracks4_8; i++) {
        if (dlg.m_tracksorder[i] < 0) {
            c++;
        }
    }
    if (c > 0) {
        CString s;
        s.Format("Warning: %u song column(s) will be cleared completely.\nAre you sure to do it?", c);
        if (SendQuestionMessage("Warning", s, MessageButtons::YesNoCancel) != MessageAnswer::Yes) {
            return;
        }
    }

    TracksOrderChangeApply(f, t, dlg.m_tracksorder);
}

// CSong::TracksOrderChangeApply() is implemented in SongEditing.cpp.

// CSong::Songswitch4_8() is implemented in SongEditing.cpp - its
// confirmation prompt (SendQuestionMessage()) is now safe to trigger in
// tests via the test-injectable answer hook (see plans/MESSAGEBOX_REFACTOR_PLAN.md).

// CSong::GetEffectiveMaxtracklen() is implemented in SongEditing.cpp (only touches g_Tracks/g_Instruments/g_Undo/g_TrackClipboard/g_tracks4_8, not Global.h's wider dependency graph).

// CSong::GetSmallestMaxtracklen() is implemented in SongEditing.cpp (only touches g_Tracks/g_Instruments/g_Undo/g_TrackClipboard/g_tracks4_8, not Global.h's wider dependency graph).

// CSong::ChangeMaxtracklen() is implemented in SongEditing.cpp (only touches g_Tracks/g_Instruments/g_Undo/g_TrackClipboard/g_tracks4_8, not Global.h's wider dependency graph).

// CSong::TracksAllBuildLoops() and TracksAllExpandLoops() are implemented in SongEditing.cpp (only touch g_Tracks, plus a call to Stop() that's a no-op unless Play() was called first).

// CSong::SongClearUnusedTracksAndParts() is implemented in SongEditing.cpp (only touches g_Tracks/g_Instruments/g_Undo/g_TrackClipboard/g_tracks4_8, not Global.h's wider dependency graph).

// CSong::SongClearDuplicatedTracks() is implemented in SongEditing.cpp (only touches g_Tracks/g_Instruments/g_Undo/g_TrackClipboard/g_tracks4_8, not Global.h's wider dependency graph).

// CSong::SongClearUnusedTracks() is implemented in SongEditing.cpp (only touches g_Tracks/g_Instruments/g_Undo/g_TrackClipboard/g_tracks4_8, not Global.h's wider dependency graph).

// CSong::RenumberAllTracks() is implemented in SongEditing.cpp (only touches g_Tracks/g_Instruments/g_Undo/g_TrackClipboard/g_tracks4_8, not Global.h's wider dependency graph).

// CSong::ClearAllInstrumentsUnusedInAnyTrack() is implemented in SongEditing.cpp (only touches g_Tracks/g_Instruments/g_Undo/g_TrackClipboard/g_tracks4_8, not Global.h's wider dependency graph).

// CSong::RenumberAllInstruments() is implemented in SongEditing.cpp (only touches g_Tracks/g_Instruments/g_Undo/g_TrackClipboard/g_tracks4_8, not Global.h's wider dependency graph).

//
//--------------------------------------------------------------------------------------
//

// CSong::SetBookmark() is implemented in SongEditing.cpp (only touches g_Tracks/g_Instruments/g_Undo/g_TrackClipboard/g_tracks4_8, not Global.h's wider dependency graph).

// CSong::Play() is implemented in SongEditing.cpp.

// CSong::Stop() is implemented in SongEditing.cpp.

// SongPlayNextLine() is implemented in SongCore.cpp.

// CSong::PlayBeat() is implemented in SongEditing.cpp.

// CSong::PlayVBI() is implemented in SongEditing.cpp.

/// <summary>
/// Call this X times per second to handle the playing of the song
/// </summary>
void CSong::TimerRoutine() {
    // If the POKEY Stream is being recorded, the Timer Routine is bypassed entirely to run as fast as possible
    if (m_pokeyStream == nullptr || !m_pokeyStream->IsRecording()) {
        // Things that are solved 1x for vbi
        PlayVBI();

        // Play tones if there are key presses
        PlayPressedTones();

        //--- Rendered Sound ---//
        g_Pokey.RenderSound1_50(m_instrumentSpeed); // Rendering of a piece of sample (1 / 50s = 20ms)

        if (m_play) {
            g_playtime++;
        } // If the song is currently playing, increment the timer
    }

    //--- NTSC timing hack during playback ---//
    // The NTSC timing cannot be divided to an integer
    // the optimal timing would be 16.666666667ms, which is typically rounded to 17
    // unfortunately, things run too slow with 17, or too fast 16
    // a good enough compromise for now is to make use of a '17-17-16' miliseconds "groove"
    // this isn't proper, but at least, this makes the timing much closer to the actual thing
    // the only issue with this is that the sound will have very slight jitters during playback
    ChangeTimer(IsNTSC() ? m_timerRoutineTick[g_timerGlobalCount % 3] : 20);

    g_timerGlobalCount++; // Increment by one each time Timer Routine was processed
}

// CSong::BLOCKSETBEGIN() is implemented in SongEditing.cpp (only touches g_Tracks/g_Instruments/g_Undo/g_TrackClipboard/g_tracks4_8, not Global.h's wider dependency graph).

// CSong::BLOCKSETEND() is implemented in SongEditing.cpp (only touches g_Tracks/g_Instruments/g_Undo/g_TrackClipboard/g_tracks4_8, not Global.h's wider dependency graph).

// CSong::BLOCKDESELECT() is implemented in SongEditing.cpp (only touches g_Tracks/g_Instruments/g_Undo/g_TrackClipboard/g_tracks4_8, not Global.h's wider dependency graph).

// CSong::ISBLOCKSELECTED() is implemented in SongEditing.cpp (only touches g_Tracks/g_Instruments/g_Undo/g_TrackClipboard/g_tracks4_8, not Global.h's wider dependency graph).
