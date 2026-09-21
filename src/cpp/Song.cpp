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
#include "SongExporter.h"

extern CAtariTrackerDriver* g_AtariTrackerDriver;

extern CInstruments g_Instruments;
extern CTrackClipboard g_TrackClipboard;
extern CXPokey g_Pokey;
extern CString g_PrefixForAllAsmLabels;

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
void CSong::StopTimer()
{
    g_SongTimer.StopTimer();
}

/// <summary>
/// Change the timing of how often the CSong::TimerRoutine is being called.
/// Depends on PAL or NTSC timing.
/// </summary>
/// <param name="ms">ms between calls (17=NTSC, 20=PAL)</param>
void CSong::ChangeTimer(int ms)
{
    g_SongTimer.SetTimer(*this, ms);
}

/// <summary>
/// Reset the song data to empty and return RMT into a default state
/// </summary>
/// <param name="numOfTracks">How many tracks are supported 4 or 8</param>
void CSong::ClearSong(int numOfTracks)
{
    Stop();

    //g_tracks4_8 = numOfTracks;			// Track for 4/8 channels
    SetTracks(numOfTracks);
    g_rmtroutine = TRUE;				// RMT routine execution enabled
    SetEditMode(EditMode::EDIT_MODE);
    g_respectvolume = FALSE;
    g_rmtstripped_adr_module = 0x4000;	// Default standard address for stripped RMT modules
    g_rmtstripped_sfx = FALSE;			// Is not a standard sfx variety stripped RMT
    g_rmtstripped_gvf = FALSE;			// Default does not use Feat GlobalVolumeFade
    g_rmtmsxtext = "";					// Clear the text for XEX export
    g_PrefixForAllAsmLabels = "MUSIC";	// Default label prefix for exporting simple ASM notation

    PlayPressedTonesInit();

    g_playtime = 0;
    m_followplay = 1;
    m_mainSpeed = m_speed = m_speeda = 16;
    m_instrumentSpeed = 1;

    g_activepart = g_active_ti = Part::PART_TRACKS;

    m_songplayline = m_songactiveline = 0;
    m_trackactiveline = m_trackplayline = 0;
    m_trackactivecol = m_trackactivecur = 0;
    m_activeinstr = 0;
    m_octave = 0;
    m_volume = MAXVOLUME;

    ClearBookmark();

    m_infoact = EditArea::NAME;

    memset(m_songname, ' ', SONG_NAME_MAX_LEN);
    strncpy(m_songname, "Noname song", 11);
    m_songname[SONG_NAME_MAX_LEN] = 0;

    m_songnamecur = 0;

    m_filename = "";
    m_ioType = SongIOType::NONE;
    m_lastExportIOType = SongIOType::NONE;

    m_TracksOrderChange_songlinefrom = 0x00;
    m_TracksOrderChange_songlineto = SONGLEN - 1;

    // Number of lines after inserting a note/space
    g_SkipLinesAfterNoteInsert = 1; // Initial value
    CMainFrame* mf = ((CMainFrame*)AfxGetMainWnd());
    if (mf) mf->m_comboSkipLinesAfterNoteInsert.SetCurSel(g_SkipLinesAfterNoteInsert);

    for (int i = 0; i < SONGLEN; i++)
    {
        for (int j = 0; j < SONGTRACKS; j++)
        {
            m_song[i][j] = -1;	// TRACK --
        }
        m_songgo[i] = -1;		// Is not GO
    }

    // Empty clipboards
    g_TrackClipboard.Clear();
    m_instrclipboard.activeEditSection = InstrumentSection::NONE;	// According to -1 it knows that it is empty
    m_songgoclipboard = -2;						// According to -2 it knows that it is empty

    // Delete all tracks and instruments
    g_Tracks.InitTracks();
    g_Instruments.InitInstruments();

    // Undo initialization
    g_Undo.Init();

    // Changes in the module
    g_changes = 0;

    // Initialise RMT routine
    g_Atari.Init(IsNTSC());
    g_AtariTrackerDriver->Init();
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

BOOL CSong::PlayPressedTones()
{
    int t, n, i, v;
    for (t = 0; t < SONGTRACKS; t++)
    {
        if ((v = m_playptvolume[t]) >= 0) //volume is set last
        {
            n = m_playptnote[t];
            i = m_playptinstr[t];
            if (n >= 0 && i >= 0)
            {
                g_AtariTrackerDriver->SetTrackNoteInstrumentVolume(t, n, i, v);
            }
            else
            {
                g_AtariTrackerDriver->SetTrackVolume(t, v);
            }
            SetPlayPressedTonesTNIV(t, -1, -1, -1);
        }
    }
    return TRUE;
}


// CSong::ActiveInstrSet() is implemented in SongEditing.cpp (only touches g_Tracks/g_Instruments/g_Undo/g_TrackClipboard/g_tracks4_8, not Global.h's wider dependency graph).

// CSong::ActiveInstrPrev() is implemented in SongEditing.cpp (only touches g_Tracks/g_Instruments/g_Undo/g_TrackClipboard/g_tracks4_8, not Global.h's wider dependency graph).

// CSong::ActiveInstrNext() is implemented in SongEditing.cpp (only touches g_Tracks/g_Instruments/g_Undo/g_TrackClipboard/g_tracks4_8, not Global.h's wider dependency graph).


// GetActiveInstr(), GetActiveColumn(), GetActiveLine(), GetPlayLine(),
// SetActiveLine(), and SetPlayLine() are implemented in SongCore.cpp.

BOOL CSong::TrackUp(int lines)
{
    if (m_play && m_followplay)	//prevents moving at all during play+follow
        return 0;

    g_Undo.Separator();
    m_trackactiveline -= lines;	//subtract the number of lines from active track line 

    //GetSmallestMaxtracklen() seems to do a really good job for the navigation within the "compact" tracks display so far
    int trlen = GetSmallestMaxtracklen(m_songactiveline);

    if (m_trackactiveline < 0)	//track line is below 0
    {
        if (ISBLOCKSELECTED())	//a selection block is currently in use
        {
            m_trackactiveline = 0;	//prevent moving anywhere else
            return 1;
        }
        if (g_keyboard_updowncontinue)	//navigation between tracks is enabled
        {
            BLOCKDESELECT();
            SongUp();	//go to the next songline with current trackline position
            trlen = GetSmallestMaxtracklen(m_songactiveline);	//fetch the new pattern length as well
        }
        m_trackactiveline = m_trackactiveline + trlen;	//active line should appear at the bottom line, from the previous pattern movement 
        if (m_trackactiveline < 0)	//active line is still below 0? assume max track length to be the correct position, so the next movement up will rectify itself
            m_trackactiveline = trlen - lines;
    }
    if (m_trackactiveline > trlen)
        m_trackactiveline = trlen - lines;	//above max track length, snap back in-bounds, and take the number of used for movements as well 

    return 1;
}

BOOL CSong::TrackDown(int lines, BOOL stoponlastline)
{
    if (m_play && m_followplay)	//prevents moving at all during play+follow
        return 0;

    if (!g_keyboard_updowncontinue && stoponlastline && m_trackactiveline + lines > TrackGetLastLine()) // an invalid combination should be ignored
        return 0;

    g_Undo.Separator();
    m_trackactiveline += lines;	//add the number of lines to move down to the current active trackline

    //GetSmallestMaxtracklen() seems to do a really good job for the navigation within the "compact" tracks display so far
    int trlen = GetSmallestMaxtracklen(m_songactiveline);	//identify the true track length in song line 
    if (!trlen) trlen = g_Tracks.GetMaxTrackLength();	//in case the smallest max track length returned zero (eg from a goto line)

    if (m_trackactiveline >= trlen)	//active line is equal or above max track length
    {
        if (ISBLOCKSELECTED())
        {
            //m_trackactiveline = g_Tracks.m_maxtracklen - 1;	//prevent moving anywhere else
            m_trackactiveline = trlen - 1;	//prevent moving anywhere else
            return 1;
        }
        //m_trackactiveline = m_trackactiveline % g_Tracks.m_maxtracklen;
        m_trackactiveline = m_trackactiveline % trlen;	//active line is modulo of track length, it will roll over 
        if (g_keyboard_updowncontinue)	//navigation between tracks is enabled
        {
            BLOCKDESELECT();
            SongDown();	//go to the next songline with current trackline position
            trlen = GetSmallestMaxtracklen(m_songactiveline);	//fetch the new pattern length as well
        }
        if (m_trackactiveline < 0)	//active line is still below 0? assume max track length to be the correct position, so the next movement up will rectify itself
            m_trackactiveline = 0 + lines;
    }
    if (m_trackactiveline > trlen)
        m_trackactiveline = 0 + lines;	//above max track length, snap back in-bounds, and take the number of used for movements as well 

    return 1;
}

// CSong::TrackLeft() is implemented in SongEditing.cpp (only touches g_Tracks/g_Instruments/g_Undo/g_TrackClipboard/g_tracks4_8, not Global.h's wider dependency graph).

// CSong::TrackRight() is implemented in SongEditing.cpp (only touches g_Tracks/g_Instruments/g_Undo/g_TrackClipboard/g_tracks4_8, not Global.h's wider dependency graph).

// CSong::RespectBoundaries() is implemented in SongEditing.cpp (only touches g_Tracks/g_Instruments/g_Undo/g_TrackClipboard/g_tracks4_8, not Global.h's wider dependency graph).

// CSong::TrackGetLoopingNoteInstrVol() is implemented in SongEditing.cpp (only touches g_Tracks/g_Instruments/g_Undo/g_TrackClipboard/g_tracks4_8, not Global.h's wider dependency graph).

// CSong::GetUECursor() is implemented in SongEditing.cpp (only touches g_Tracks/g_Instruments/g_Undo/g_TrackClipboard/g_tracks4_8, not Global.h's wider dependency graph).

void CSong::SetUECursor(Part part, int* cursor)
{
    switch (part)
    {
    case Part::PART_TRACKS:
        m_songactiveline = cursor[0];
        m_trackactiveline = cursor[1];
        m_trackactivecol = cursor[2];
        m_trackactivecur = cursor[3];
        g_activepart = g_active_ti = Part::PART_TRACKS;
        break;

    case Part::PART_SONG:
        m_songactiveline = cursor[0];
        m_trackactivecol = cursor[1];
        g_activepart = Part::PART_SONG;
        break;

    case Part::PART_INSTRUMENTS:
        m_activeinstr = cursor[0];
        //the other parameters 1-5 are within the instrument (TInstrument structure), so it is not necessary to set
        g_activepart = g_active_ti = Part::PART_INSTRUMENTS;
        break;

    case Part::PART_INFO:
        m_infoact = (EditArea)cursor[0];
        g_activepart = Part::PART_INFO;
        break;

    default:
        return;	//don't change g_activepart !!!

    }
}

// UECursorIsEqual() is implemented in SongCore.cpp.


//----------

void CSong::SongJump(int lines)
{
    int songline = SongGetActiveLine();
    int toline = songline + lines;

    if (toline > songline)
    {
        SongSetActiveLine(toline - 1);
        SongDown();
    }
    else
    {
        SongSetActiveLine(toline + 1);
        SongUp();
    }
}

BOOL CSong::SongUp()
{
    BLOCKDESELECT();
    g_Undo.Separator();

    m_songactiveline--;

    if (!IsValidSongline(m_songactiveline))
        m_songactiveline = SONGLEN - 1;

    if (m_play && m_followplay)
    {
        // Play track in loop, else, play from cursor position
        auto mode = (m_play == PLAY_TRACK) ? PLAY_TRACK : PLAY_FROM;
        Stop();

        // This is a Gotoline, skip another line above it
        if (IsSongGo(m_songactiveline)) {
            m_songactiveline--;
        }

        // If the line is no longer valid, force it to the last line instead
        if (!IsValidSongline(m_songactiveline)) {
            m_songactiveline = SONGLEN - 1;
        }

        m_songplayline = m_songactiveline;
        m_trackplayline = m_trackactiveline = 0;

        // Continue playing using the correct parameters
        Play(mode, m_followplay);
    }
    return 1;
}

BOOL CSong::SongDown()
{
    BLOCKDESELECT();
    g_Undo.Separator();

    m_songactiveline++;

    if (!IsValidSongline(m_songactiveline))
        m_songactiveline = 0;

    if (m_play && m_followplay)
    {
        // Play track in loop, else, play from cursor position
        auto mode = (m_play == PLAY_TRACK) ? PLAY_TRACK : PLAY_FROM;
        Stop();

        // This is a Gotoline, skip another line below it
        if (IsSongGo(m_songactiveline)) {
            m_songactiveline++;
        }

        // If the line is no longer valid, force it to the first line instead
        if (!IsValidSongline(m_songactiveline)) {
            m_songactiveline = 0;
        }

        m_songplayline = m_songactiveline;
        m_trackplayline = m_trackactiveline = 0;

        // Continue playing using the correct parameters
        Play(mode, m_followplay);
    }
    return 1;
}

BOOL CSong::SongSubsongPrev()
{
    g_Undo.Separator();
    int i = m_songactiveline - 1;

    //only few lines in track have been played, or active line is 0, search for 1 subsong earlier to avoid being sent back to the same line each time 
    if ((m_play && m_followplay && m_trackplayline < 16) || m_trackactiveline == 0)
        i--;
    for (; i >= 0; i--)
    {
        if (m_songgo[i] >= 0)
        {
            m_songactiveline = i + 1;
            break;
        }
    }
    if (i < 0) m_songactiveline = 0;
    m_trackactiveline = 0;
    if (m_play && m_followplay)
    {
        auto mode = (m_play == PLAY_TRACK) ? PLAY_TRACK : PLAY_FROM;	//play track in loop, else, play from cursor position
        Stop();
        m_songplayline = m_songactiveline;
        m_trackplayline = m_trackactiveline = 0;
        Play(mode, m_followplay); // continue playing using the correct parameters
    }
    return 1;
}

BOOL CSong::SongSubsongNext()
{
    g_Undo.Separator();
    int i;
    for (i = m_songactiveline; i < SONGLEN; i++)
    {
        if (m_songgo[i] >= 0)
        {
            if (i < (SONGLEN - 1))
                m_songactiveline = i + 1;
            else
                m_songactiveline = SONGLEN - 1; //Goto on the last songline (=> it is not possible to set a line below it!)
            m_trackactiveline = 0;
            break;
        }
    }
    if (m_play && m_followplay)
    {
        auto mode = (m_play == PLAY_TRACK) ? PLAY_TRACK : PLAY_FROM;	//play track in loop, else, play from cursor position
        Stop();
        m_songplayline = m_songactiveline;
        m_trackplayline = m_trackactiveline = 0;
        Play(mode, m_followplay); // continue playing using the correct parameters
    }
    return 1;
}

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

BOOL CSong::SongInsertCopyOrCloneOfSongLines(int& line)
{
    int i, j, k, d, n, sou, des;
    n = (line > 0) ? line - 1 : 0;
    CInsertCopyOrCloneOfSongLinesDlg dlg;

    dlg.m_linefrom = n;
    dlg.m_lineto = n;
    dlg.m_lineinto = line;
    dlg.m_clone = 0;
    dlg.m_tuning = 0;
    dlg.m_volumep = 100;	//100%

    if (dlg.DoModal() != IDOK) return 1;
    BLOCKDESELECT();					//the block is deselected only if it is OK

    BYTE tracks[TRACKSNUM];
    memset(tracks, 0, TRACKSNUM); //init
    MarkTF_USED(tracks);
    MarkTF_NOEMPTY(tracks);
    int clonedto[TRACKSNUM];
    for (i = 0; i < TRACKSNUM; i++) clonedto[i] = -1; //init

    for (i = dlg.m_linefrom; i <= dlg.m_lineto; i++)
    {
        n = i - dlg.m_linefrom;
        sou = i;
        des = line + n;
        BOOL diss = (des <= sou);
        if (diss) sou += n;
        BOOL sngo = 0;
        if (sou < SONGLEN) sngo = (m_songgo[sou] >= 0);

        if (diss) sou++;
        if (sou < 0 || sou >= SONGLEN || des < 0 || des >= SONGLEN)
        {
            CString s;
            s.Format("Copy/clone operation had to be aborted\nbecause overrun of song range occured.\nThere was %i song lines inserted only.", n);
            MessageBox(g_hwnd, s, "Warning", MB_ICONSTOP);
            return 0;
        }

        SongInsertLine(des);	//inserted blank line

        if (dlg.m_clone && !sngo)
        {
            //clones
            //SongPrepareNewLine(des,sou,0);	//omits empty columns

            g_Undo.Separator(-1); //associates the previous insert lines to the next change
            g_Undo.ChangeTrack(0, 0, UETYPE_TRACKSALL, 1); //with separator

            for (j = 0; j < g_tracks4_8; j++)
            {
                k = m_song[sou][j]; //original track
                d = -1;				//resulting track (initial initialization)
                if (k < 0) continue;  //is there --
                if (clonedto[k] >= 0)
                {
                    d = clonedto[k];	//this one has already been cloned, so it will also use it
                }
                else
                {
                    d = FindNearTrackBySongLineAndColumn(sou, j, tracks);
                    if (d >= 0)
                    {
                        tracks[d] = TrackFlag::TF_USED;
                        clonedto[k] = d;
                        TrackCopyFromTo(k, d);
                        //edit cloned track according to dlg.m_tuning and dlg.m_volumep
                        g_Tracks.ModifyTrack(g_Tracks.GetTrack(d), 0, TRACKLEN - 1, -1, dlg.m_tuning, 0, dlg.m_volumep);
                    }
                    else
                    {
                        CString s;
                        s.Format("Clone operation had to be aborted\nbecause out of unused empty tracks.\nThere was %i song line(s) inserted only.", n + 1);
                        MessageBox(g_hwnd, s, "Warning", MB_ICONSTOP);
                        return 0;
                    }
                }
                m_song[des][j] = d;
            }
        }
        else
        {
            //copies
            m_songgo[des] = m_songgo[sou];
            for (j = 0; j < g_tracks4_8; j++) m_song[des][j] = m_song[sou][j];
        }
    }

    return 1;
}

BOOL CSong::SongPrepareNewLine(int& line, int sourceline, BOOL alsoemptycolumns) //Inserts a songline with unused empty tracks
{
    int i, k;

    if (sourceline < 0) sourceline = line + sourceline; //for -1 it is line-1

    SongInsertLine(line);	//inserts a blank line

    //prepares an online set of unused empty tracks

    BYTE tracks[TRACKSNUM];
    memset(tracks, 0, TRACKSNUM); //init
    MarkTF_USED(tracks);
    MarkTF_NOEMPTY(tracks);

    int count = 0;
    for (i = 0; i < g_tracks4_8; i++)
    {
        if (!alsoemptycolumns && sourceline >= 0 && m_song[sourceline][i] < 0) continue;

        k = FindNearTrackBySongLineAndColumn(sourceline, i, tracks);
        if (k >= 0)
        {
            m_song[line][i] = k;
            tracks[k] = TrackFlag::TF_USED;
            count++;
        }
    }

    if (count < g_tracks4_8)
    {
        if (count == 0)
            MessageBox(g_hwnd, "There isn't any empty unused track in song.", "Error", MB_ICONERROR);
        else
            MessageBox(g_hwnd, "Not enough empty unused tracks in song.", "Error", MB_ICONERROR);
        return 0;
    }

    return 1;
}

// FindNearTrackBySongLineAndColumn() is implemented in SongCore.cpp.

BOOL CSong::SongPutnewemptyunusedtrack()
{
    int line = SongGetActiveLine();
    if (m_songgo[line] >= 0) return 0;		//it can't be done on the "GO TO LINE" line

    g_Undo.ChangeSong(line, m_trackactivecol, UETYPE_SONGTRACK, 0);

    int cl = GetActiveColumn();
    int act = m_song[line][cl];
    int k = -1;
    m_song[line][cl] = -1;	//at current position in song --

    BYTE tracks[TRACKSNUM];
    memset(tracks, 0, TRACKSNUM); //init
    MarkTF_USED(tracks);
    MarkTF_NOEMPTY(tracks);

    if (act >= 0 && !tracks[act])
        k = act;
    else
        k = FindNearTrackBySongLineAndColumn(line, cl, tracks);

    if (k < 0)
    {
        m_song[line][cl] = act;
        MessageBox(g_hwnd, "There isn't any empty unused track in song.", "Error", MB_ICONERROR);
        //UpdateShiftControlKeys();
        return 0;
    }

    m_song[line][cl] = k;
    return 1;
}

BOOL CSong::SongMaketracksduplicate()
{
    int line = SongGetActiveLine();
    if (m_songgo[line] >= 0) return 0;		//it can't be done on the "GO TO LINE" line

    int cl = GetActiveColumn();
    int act = m_song[line][cl];
    if (act < 0) return 0;			//cannot be duplicated, no track selected

    g_Undo.ChangeSong(line, cl, UETYPE_SONGTRACK, -1); //just cast

    int k = -1;
    m_song[line][cl] = -1;	//at current position in song --

    BYTE tracks[TRACKSNUM];
    memset(tracks, 0, TRACKSNUM); //init
    MarkTF_USED(tracks);
    MarkTF_NOEMPTY(tracks);

    if (!(tracks[act] & TrackFlag::TF_USED))
    {
        //not used anywhere else
        m_song[line][cl] = act;
        int r = MessageBox(g_hwnd, "This track is used only once in song.\nAre you sure to make duplicate?", "Make track's duplicate...", MB_OKCANCEL | MB_ICONQUESTION);
        if (r == IDOK)
            k = FindNearTrackBySongLineAndColumn(line, cl, tracks);
        else
        {
            g_Undo.DropLast();
            return 0;
        }
    }
    else
        k = FindNearTrackBySongLineAndColumn(line, cl, tracks);

    if (k < 0)
    {
        m_song[line][cl] = act;
        MessageBox(g_hwnd, "There isn't any empty unused track in song.", "Error", MB_ICONERROR);
        //UpdateShiftControlKeys();
        g_Undo.DropLast();
        return 0;
    }

    g_Undo.ChangeTrack(k, m_trackactiveline, UETYPE_TRACKDATA, 1);

    //copies source track act to k
    TrackCopyFromTo(act, k);

    m_song[line][cl] = k;

    return 1;
}


//--clipboard functions

// CSong::TrackCopy() is implemented in SongEditing.cpp (only touches g_Tracks/g_Instruments/g_Undo/g_TrackClipboard/g_tracks4_8, not Global.h's wider dependency graph).

// CSong::TrackPaste() is implemented in SongEditing.cpp (only touches g_Tracks/g_Instruments/g_Undo/g_TrackClipboard/g_tracks4_8, not Global.h's wider dependency graph).

// CSong::TrackDelete() is implemented in SongEditing.cpp (only touches g_Tracks/g_Instruments/g_Undo/g_TrackClipboard/g_tracks4_8, not Global.h's wider dependency graph).

// CSong::TrackCut() is implemented in SongEditing.cpp (only touches g_Tracks/g_Instruments/g_Undo/g_TrackClipboard/g_tracks4_8, not Global.h's wider dependency graph).

// CSong::TrackCopyFromTo() is implemented in SongEditing.cpp (only touches g_Tracks/g_Instruments/g_Undo/g_TrackClipboard/g_tracks4_8, not Global.h's wider dependency graph).

// CSong::TrackSwapFromTo() is implemented in SongEditing.cpp (only touches g_Tracks/g_Instruments/g_Undo/g_TrackClipboard/g_tracks4_8, not Global.h's wider dependency graph).

// CSong::BlockPaste() is implemented in SongEditing.cpp (only touches g_Tracks/g_Instruments/g_Undo/g_TrackClipboard/g_tracks4_8, not Global.h's wider dependency graph).

// CSong::InstrCopy() is implemented in SongEditing.cpp (only touches g_Tracks/g_Instruments/g_Undo/g_TrackClipboard/g_tracks4_8, not Global.h's wider dependency graph).

void CSong::InstrPaste(int special)
{
    if (m_instrclipboard.activeEditSection == InstrumentSection::NONE) {
        return;	// it has never been filled with anything
    }

    int i = GetActiveInstr();

    g_Undo.ChangeInstrument(i, 0, UETYPE_INSTRDATA, 1);

    TInstrument* ai = g_Instruments.GetInstrument(i);

    g_AtariTrackerDriver->InstrumentTurnOff(i); //turns off this instrument on all channels

    int x, y;
    BOOL bl = 0, br = 0, ep = 0;
    BOOL bltor = 0, brtol = 0;

    switch (special)
    {
    case 0: //normal paste
        memcpy(ai, &m_instrclipboard, sizeof(TInstrument));
        ai->activeEditSection = InstrumentSection::NAME;
        ai->editNameCursorPos = 0; //so that the cursor is at the beginning of the instrument name
        break;

    case 1: //volume L/R
        bl = br = 1;
        goto InstrPaste_Envelopes;
    case 2: //volume R
        br = 1;
        goto InstrPaste_Envelopes;
    case 3: //volume L
        bl = 1;
        goto InstrPaste_Envelopes;
    case 4: //envelope parameters
        ep = 1;
    InstrPaste_Envelopes:
        for (x = 0; x <= m_instrclipboard.parameters[PAR_ENV_LENGTH]; x++)
        {
            if (br) ai->envelope[x][EnvelopeParameter::VOLUMER] = m_instrclipboard.envelope[x][EnvelopeParameter::VOLUMER];
            if (bl) ai->envelope[x][EnvelopeParameter::VOLUMEL] = m_instrclipboard.envelope[x][EnvelopeParameter::VOLUMEL];
            if (bltor) ai->envelope[x][EnvelopeParameter::VOLUMER] = m_instrclipboard.envelope[x][EnvelopeParameter::VOLUMEL];
            if (brtol) ai->envelope[x][EnvelopeParameter::VOLUMEL] = m_instrclipboard.envelope[x][EnvelopeParameter::VOLUMER];
            if (ep)
            {
                for (y = EnvelopeParameter::DISTORTION; y < ENVROWS; y++) ai->envelope[x][y] = m_instrclipboard.envelope[x][y];
            }
        }
        ai->parameters[PAR_ENV_LENGTH] = m_instrclipboard.parameters[PAR_ENV_LENGTH];
        ai->parameters[PAR_ENV_GOTO] = m_instrclipboard.parameters[PAR_ENV_GOTO];
        ai->editEnvelopeX = 0;
        break;

    case 5: //TABLE
        for (x = 0; x <= m_instrclipboard.parameters[PAR_TBL_LENGTH]; x++) ai->noteTable[x] = m_instrclipboard.noteTable[x];
        ai->parameters[PAR_TBL_LENGTH] = m_instrclipboard.parameters[PAR_TBL_LENGTH];
        ai->parameters[PAR_TBL_GOTO] = m_instrclipboard.parameters[PAR_TBL_GOTO];
        ai->editNoteTableCursorPos = 0;
        break;

    case 6: //vol+env
        br = bl = ep = 1;
        goto InstrPaste_Envelopes;
    case 8: //volume L to R
        bltor = 1;
        goto InstrPaste_Envelopes;
    case 9: //volume R to L
        brtol = 1;
        goto InstrPaste_Envelopes;

    case 7: //vol+env insert to cursor
        int sx = m_instrclipboard.parameters[PAR_ENV_LENGTH] + 1;
        if (ai->editEnvelopeX + sx > ENVELOPE_MAX_COLUMNS) sx = ENVELOPE_MAX_COLUMNS - ai->editEnvelopeX;
        for (x = ENVELOPE_MAX_COLUMNS - 2; x >= ai->editEnvelopeX; x--) //offset
        {
            int i = x + sx;
            if (i >= ENVELOPE_MAX_COLUMNS) continue;
            for (y = 0; y < ENVROWS; y++) ai->envelope[i][y] = ai->envelope[x][y];
        }
        for (x = 0; x < sx; x++) //insertion
        {
            int i = ai->editEnvelopeX + x;
            for (y = 0; y < ENVROWS; y++) ai->envelope[i][y] = m_instrclipboard.envelope[x][y];
        }
        int i = ai->parameters[PAR_ENV_LENGTH] + sx;
        if (i >= ENVELOPE_MAX_COLUMNS) i = ENVELOPE_MAX_COLUMNS - 1;
        ai->parameters[PAR_ENV_LENGTH] = i;
        if (ai->parameters[PAR_ENV_GOTO] > ai->editEnvelopeX)
        {
            i = ai->parameters[PAR_ENV_GOTO] + sx;
            if (i >= ENVELOPE_MAX_COLUMNS) i = ENVELOPE_MAX_COLUMNS - 1;
            ai->parameters[PAR_ENV_GOTO] = i;
        }
        i = ai->editEnvelopeX + sx;
        if (i >= ENVELOPE_MAX_COLUMNS) i = ENVELOPE_MAX_COLUMNS - 1;
        ai->editEnvelopeX = i;
        break;

    }
    g_Instruments.Update(i); //write to Atari RAM
}

// CSong::InstrCut() is implemented in SongEditing.cpp (only touches g_Tracks/g_Instruments/g_Undo/g_TrackClipboard/g_tracks4_8, not Global.h's wider dependency graph).

// CSong::InstrDelete() is implemented in SongEditing.cpp (only touches g_Tracks/g_Instruments/g_Undo/g_TrackClipboard/g_tracks4_8, not Global.h's wider dependency graph).

// CSong::InstrInfo() is implemented in SongEditing.cpp.

void CSong::InstrChange(int instr)
{
    if (!g_Instruments.IsValidInstrument(instr)) return;

    CInstrumentChangeDlg dlg;

    CString s = "";

    TTrack* st;						// Pointer to original track
    TTrack* nt;						// Pointer to new track
    TTrack at;						// Temporary track

    BYTE tracks[TRACKSNUM];			// New tracks with changes 
    BYTE track_yn[TRACKSNUM];		// Tracks to apply changes

    int track_column[TRACKSNUM];	// The first occurrence in the selected area of the song
    int track_line[TRACKSNUM];		// The first occurrence in the selected area of the song
    int track_changeto[TRACKSNUM];	// Changed tracks to replace in song

    int onlysomething = 0;			// Only apply changes to specific things
    int trackcreated = 0;			// Number of newly created songs
    int songchanges = 0;			// Number of changes in the song

    int i, j, k, t, r, lasti, lastn, changes, note, ins, vol;

    bool error = 0;

    dlg.m_combo9 = dlg.m_combo11 = instr;
    dlg.m_combo10 = dlg.m_combo12 = instr;
    dlg.m_onlytrack = SongGetActiveTrack();
    dlg.m_onlysonglinefrom = dlg.m_onlysonglineto = SongGetActiveLine();

    // Change all the instrument occurences
    if (dlg.DoModal() == IDOK)
    {
        Stop();	// Stop playing before processing further

        // Hide all tracks and the whole song
        g_Undo.ChangeTrack(0, 0, UETYPE_TRACKSALL, -1);
        g_Undo.ChangeSong(0, 0, UETYPE_SONGDATA, 1);

        // Get the parameters from the dialog box
        int snotefrom = dlg.m_combo1;
        int snoteto = dlg.m_combo2;
        int svolmin = dlg.m_combo3;
        int svolmax = dlg.m_combo4;
        int sinstrfrom = dlg.m_combo11;
        int sinstrto = dlg.m_combo12;
        int dnotefrom = dlg.m_combo5;
        int dnoteto = dlg.m_combo6;
        int dvolmin = dlg.m_combo7;
        int dvolmax = dlg.m_combo8;
        int dinstrfrom = dlg.m_combo9;
        int dinstrto = dlg.m_combo10;
        int onlytrack = dlg.m_onlytrack;
        int onlychannels = dlg.m_onlychannels;
        int onlysonglinefrom = dlg.m_onlysonglinefrom;
        int onlysonglineto = dlg.m_onlysonglineto;

        // Initialise memory
        memset(track_yn, 0, TRACKSNUM);
        for (i = 0; i < TRACKSNUM; i++) track_column[i] = track_line[i] = -1;

        if (onlychannels >= 0 || (onlysonglinefrom >= 0 && onlysonglineto >= 0))
        {
            if (onlychannels <= 0) onlychannels = 0xff;				// All channels
            if (onlysonglinefrom < 0) onlysonglinefrom = 0;			// From the beginning
            if (onlysonglineto < 0) onlysonglineto = SONGLEN - 1;	// To the end
            onlysomething = 1;										// Something specific to change

            for (j = 0; j < SONGLEN; j++)
            {
                if (IsSongGo(j)) continue;

                for (i = 0; i < g_tracks4_8; i++)
                {
                    t = m_song[j][i];

                    if (!g_Tracks.IsValidTrack(t)) continue;

                    r = (onlychannels & (1 << i)) && j >= onlysonglinefrom && j <= onlysonglineto;
                    track_yn[t] |= (r) ? 1 : 2;	// 1 = yes, 2 = no, 3 = yesno (copy)

                    // The first occurrence in the selected area of the song
                    if (r && track_column[t] < 0)
                    {
                        track_column[t] = i;
                        track_line[t] = j;
                    }
                }
            }
        }

        else if (onlytrack >= 0)
        {
            track_yn[onlytrack] = 1;	// 1 = yes
            onlysomething = 1;
        }

        if (!g_Tracks.IsValidNote(dnoteto)) dnoteto = dnotefrom + (snoteto - snotefrom);
        if (!g_Tracks.IsValidVolume(dvolmax)) dvolmax = dvolmin + (svolmax - svolmin);
        if (!g_Tracks.IsValidInstrument(dinstrto)) dinstrto = dinstrfrom + (sinstrto - sinstrfrom);

        double notecoef = (snoteto - snotefrom > 0) ? (double)(dnoteto - dnotefrom) / (snoteto - snotefrom) : 0;
        double volcoef = (svolmax - svolmin > 0) ? (double)(dvolmax - dvolmin) / (svolmax - svolmin) : 0;
        double instrcoef = (sinstrto - sinstrfrom > 0) ? (double)(dinstrto - dinstrfrom) / (sinstrto - sinstrfrom) : 0;

        for (i = 0; i < TRACKSNUM; i++)
        {
            track_changeto[i] = -1; // initialise

            // It wants to change only some and this one is not
            if (onlysomething && ((track_yn[i] & 1) != 1)) continue;

            // Copy the original track to temporary track
            st = g_Tracks.GetTrack(i);
            at = *st;

            changes = 0;
            lasti = lastn = -1;

            for (j = 0; j < at.len; j++)
            {
                if (g_Tracks.IsValidInstrument(at.instr[j])) lasti = at.instr[j];
                if (g_Tracks.IsValidNote(at.note[j])) lastn = at.note[j];

                if (lasti >= sinstrfrom && lasti <= sinstrto && lastn >= snotefrom && lastn <= snoteto && at.volume[j] >= svolmin && at.volume[j] <= svolmax)
                {
                    if (g_Tracks.IsValidNote(at.note[j]))
                    {
                        note = dnotefrom + (int)((double)(at.note[j] - snotefrom) * notecoef + 0.5);
                        while (!g_Tracks.IsValidNote(note)) note -= 12;
                        if (note != at.note[j])
                        {
                            at.note[j] = note;
                            changes = 1;
                        }
                    }

                    if (g_Tracks.IsValidInstrument(at.instr[j]))
                    {
                        ins = dinstrfrom + (int)((double)(at.instr[j] - sinstrfrom) * instrcoef + 0.5);
                        if (!g_Tracks.IsValidInstrument(ins)) ins = INSTRSNUM - 1;
                        if (ins != at.instr[j])
                        {
                            at.instr[j] = ins;
                            changes = 1;
                        }
                    }

                    if (g_Tracks.IsValidVolume(at.volume[j]))
                    {
                        vol = dvolmin + (int)((double)(at.volume[j] - svolmin) * volcoef + 0.5);
                        if (!g_Tracks.IsValidVolume(vol)) vol = MAXVOLUME;
                        if (vol != at.volume[j])
                        {
                            at.volume[j] = vol;
                            changes = 1;
                        }
                    }
                }
            }

            // There was something changed
            if (changes)
            {
                // Create a new track if the track occurs both inside and outside the area
                if (track_yn[i] & 2)
                {
                    memset(tracks, 0, TRACKSNUM);	// Initialise memory
                    MarkTF_USED(tracks);
                    MarkTF_NOEMPTY(tracks);
                    k = FindNearTrackBySongLineAndColumn(track_line[i], track_column[i], tracks);

                    // The process is aborted if there is no unused track available
                    if (k < 0)
                    {
                        error = 1;
                        s.AppendFormat("There aren't any more empty unused tracks in song, further changes could not be applied!\n\n");
                        s.AppendFormat("Process halted in Track %02X, in Channel %u\n\n", track_line[i], track_column[i]);
                        goto abortchanges;
                    }

                    // Copy the changed track (at) to the new track (nt)
                    nt = g_Tracks.GetTrack(k);
                    *nt = at;

                    trackcreated++;

                    // Put it in the song at least once (due to the search in the song used tracks)
                    m_song[track_line[i]][track_column[i]] = k;
                    songchanges++;

                    // Will change all occurrences
                    track_changeto[i] = k;
                }

                // Copy the changed track (at) back to the original track (st)
                else
                {
                    *st = at;
                }
            }

        }

        // Subsequent changes in the song
        if (onlysomething)
        {
            for (j = 0; j < SONGLEN; j++)
            {
                if (IsSongGo(j)) continue;

                for (i = 0; i < g_tracks4_8; i++)
                {
                    t = m_song[j][i];

                    if (!g_Tracks.IsValidTrack(t)) continue;

                    r = (onlychannels & (1 << i)) && j >= onlysonglinefrom && j <= onlysonglineto;

                    if (r && track_changeto[t] >= 0)
                    {
                        m_song[j][i] = track_changeto[t];
                        songchanges++;
                    }
                }
            }
        }

    abortchanges:
        s.AppendFormat("Instrument changes were applied ");
        s.AppendFormat(error ? "with errors, beware of data loss!\n\n" : "successfully!\n\n");

        if (trackcreated || songchanges)
        {
            s.AppendFormat("Additional actions were also performed to accommodate the chosen parameters:\n\n");
            s.AppendFormat("New tracks created: %u\n", trackcreated);
            s.AppendFormat("Total changes in song: %u\n", songchanges);
        }

        MessageBox(g_hwnd, s, "Instrument changes", MB_ICONINFORMATION);
    }
}

void CSong::TrackInfo(int track)
{
    if (track < 0 || track >= TRACKSNUM) return;

    const char* cnames[] = { "L1","L2","L3","L4","R1","R2","R3","R4" };

    int i, ch;
    int trackusedincolumn[SONGTRACKS];
    int lines = 0, total = 0;

    for (ch = 0; ch < SONGTRACKS; ch++) trackusedincolumn[ch] = 0;

    for (int sline = 0; sline < SONGLEN; sline++)
    {
        if (m_songgo[sline] >= 0) continue;	//goto line is ignored

        BOOL thisline = 0;
        for (ch = 0; ch < g_tracks4_8; ch++)
        {
            int n = m_song[sline][ch];
            if (n == track) { trackusedincolumn[ch]++; total++; thisline = 1; }
        }

        if (thisline) lines++;
    }

    CString s, s2;
    s.Format("Track: %02X\nUsing in song:\n", track);
    for (ch = 0; ch < g_tracks4_8; ch++)
    {
        i = trackusedincolumn[ch];
        s2.Format("%s: %i   ", cnames[ch], i);
        s += s2;
    }

    s2.Format("\nUsed in %i songlines, globally %i times.", lines, total);
    s += s2;

    MessageBox(g_hwnd, (LPCTSTR)s, "Track Info", MB_ICONINFORMATION);
}

// CSong::SongCopyLine() is implemented in SongEditing.cpp (only touches g_Tracks/g_Instruments/g_Undo/g_TrackClipboard/g_tracks4_8, not Global.h's wider dependency graph).

// CSong::SongPasteLine() is implemented in SongEditing.cpp (only touches g_Tracks/g_Instruments/g_Undo/g_TrackClipboard/g_tracks4_8, not Global.h's wider dependency graph).

// CSong::SongClearLine() is implemented in SongEditing.cpp (only touches g_Tracks/g_Instruments/g_Undo/g_TrackClipboard/g_tracks4_8, not Global.h's wider dependency graph).

void CSong::TracksOrderChange()
{
    // Stop the sound first
    Stop();
    CSongTracksOrderDlg dlg;
    dlg.m_songlinefrom.Format("%02X", m_TracksOrderChange_songlinefrom);
    dlg.m_songlineto.Format("%02X", m_TracksOrderChange_songlineto);
    if (dlg.DoModal() == IDOK)
    {
        g_Undo.ChangeSong(m_songactiveline, m_trackactivecol, UETYPE_SONGDATA, 1);

        int m_buff[8];
        int i, j;

        int f = Hexstr((char*)(LPCTSTR)dlg.m_songlinefrom, 2);
        int t = Hexstr((char*)(LPCTSTR)dlg.m_songlineto, 2);

        if (f < 0 || f >= SONGLEN || t < 0 || t >= SONGLEN || t < f)
        {
            MessageBox(g_hwnd, "Bad songline (from-to) range.", "Error", MB_ICONERROR);
            return;
        }

        m_TracksOrderChange_songlinefrom = f;
        m_TracksOrderChange_songlineto = t;

        int c = 0;
        for (i = 0; i < g_tracks4_8; i++) if (dlg.m_tracksorder[i] < 0) c++;
        if (c > 0)
        {
            CString s;
            s.Format("Warning: %u song column(s) will be cleared completely.\nAre you sure to do it?", c);
            if (MessageBox(g_hwnd, s, "Warning", MB_YESNOCANCEL | MB_ICONWARNING) != IDYES) return;
        }

        for (i = m_TracksOrderChange_songlinefrom; i <= m_TracksOrderChange_songlineto; i++)
        {
            for (j = 0; j < g_tracks4_8; j++)
            {
                m_buff[j] = m_song[i][j];
                m_song[i][j] = -1;
            }
            for (j = 0; j < g_tracks4_8; j++)
            {
                int z = dlg.m_tracksorder[j];
                if (z >= 0)
                    m_song[i][j] = m_buff[z];
                else
                    m_song[i][j] = -1;
            }
        }
    }
}

void CSong::Songswitch4_8(int tracks4_8)
{
    // Stop the music first
    Stop();

    CString wrn = "Warning: Undo operation won't be possible!!!\n";
    int i, j;
    if (tracks4_8 == 4)
    {
        int p = 0;
        for (i = 0; i < SONGLEN; i++)
        {
            for (j = 4; j < 8; j++) if (m_song[i][j] >= 0) p++;
        }

        if (p > 0) wrn += "\nWarning: Song switch to mono 4 tracks will erase all the R1,R2,R3,R4 entries in song list.\n";
    }

    wrn += "\nAre you sure to do it?";
    int res = MessageBox(g_hwnd, wrn, "Song switch mono/stereo", MB_YESNOCANCEL | MB_ICONEXCLAMATION);
    if (res != IDYES) return;

    g_Undo.Clear();

    if (tracks4_8 == 4)
    {
        if (m_trackactivecol >= 4) { m_trackactivecol = 3; m_trackactivecur = 0; }
        SetTracks(4);
        for (i = 0; i < SONGLEN; i++)
        {
            for (j = 4; j < 8; j++) m_song[i][j] = -1;
        }
    }
    else
    {
        if (tracks4_8 == 8)
        {
            SetTracks(8);
        }
    }

    g_Atari.Init(IsNTSC());
}

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

BOOL CSong::Play(PlayMode mode, BOOL follow, int special)
{
    g_Undo.Separator();

    if (mode == PLAY_BOOKMARK && !IsBookmark()) return 0; //if there is no bookmark, then nothing.

    if (m_play)
    {
        if (mode != PLAY_FROM) Stop(); //already playing and wants something other than play from edited pos.
        else if (!m_followplay) Stop(); //is playing and wants to play from edited pos. but not followplay
    }

    m_quantization_note = m_quantization_instr = m_quantization_vol = -1;

    switch (mode)
    {
    case PLAY_SONG: //whole song from the beginning including initialization (due to portamentum etc.)
        g_Atari.Init(IsNTSC());
        m_songplayline = 0;
        m_trackplayline = 0;
        m_speed = m_mainSpeed;
        break;
    case PLAY_FROM: //song from the current position
        if (m_play && m_followplay) //is playing with follow play
        {
            m_play = PLAY_FROM;
            m_followplay = follow;
            return 1;
        }
        m_songplayline = m_songactiveline;
        m_trackplayline = m_trackactiveline;
        break;
    case PLAY_TRACK: //just the current tracks around
    Play3:
        m_songplayline = m_songactiveline;
        m_trackplayline = (special == 0) ? 0 : m_trackactiveline;
        break;
    case PLAY_BLOCK: //only in the block
        if (!g_TrackClipboard.IsBlockSelected())
        {	//no block is selected, so the track plays
            mode = PLAY_TRACK;
            goto Play3;
        }
        else
        {
            int bfro, bto;
            g_TrackClipboard.GetFromTo(bfro, bto);
            m_songplayline = g_TrackClipboard.m_selsongline;
            m_trackplayline = m_trackplayblockstart = bfro;
            m_trackplayblockend = bto;
        }
        break;
    case PLAY_BOOKMARK: //from the bookmark
        m_songplayline = m_bookmark.songline;
        m_trackplayline = m_bookmark.trackline;
        //m_speed = m_bookmark.speed; //comment out so bookmark keep the same speed in memory, won't force it to reset it each time
        break;

    case PLAY_SEEK_NEXT: //from seeking next
        m_songactiveline++;
        if (m_songactiveline > 255) m_songactiveline = 255;
        m_songplayline = m_songactiveline;
        m_trackplayline = m_trackactiveline = 0;
        if (mode == PLAY_SEEK_NEXT) { mode = PLAY_FROM; }
        break;

    case PLAY_SEEK_PREV: //from seeking prev
        m_songactiveline--;
        if (m_songactiveline < 0) m_songactiveline = 0;
        m_songplayline = m_songactiveline;
        m_trackplayline = m_trackactiveline = 0;
        if (mode == PLAY_SEEK_PREV) { mode = PLAY_FROM; }
        break;

    }

    if (m_songgo[m_songplayline] >= 0)	//there is a goto
    {
        m_songplayline = m_songgo[m_songplayline];	//goto where
        m_trackplayline = 0;							//from the beginning of that track
        if (m_songgo[m_songplayline] >= 0)
        {
            //goto into another goto
            MessageBox(g_hwnd, "There is recursive \"Go to line\" to other \"Go to line\" in song.", "Recursive \"Go to line\"...", MB_ICONSTOP);
            return 0;
        }
    }

    g_SongTimer.WaitForTimerRoutineProcessed();
    m_followplay = follow;
    PlayBeat();						//sets m_speeda
    m_speeda++;						//(Original comment by Raster, April 27, 2003) adds 1 to m_speed, for what the real thing will take place in Init
    if (m_followplay)	//cursor following the player
    {
        m_trackactiveline = m_trackplayline;
        m_songactiveline = m_songplayline;
    }
    g_playtime = 0;
    m_play = mode;

    if (m_pokeyStream) {
        m_pokeyStream->CallFromPlay(m_play, m_trackplayline, m_songplayline);
    }
    return 1;
}

void CSong::Stop()
{
    if (GetPlayMode() != PLAY_STOP)
    {
        SetPlayMode(PLAY_STOP);
        g_Undo.Separator();
        m_quantization_note = m_quantization_instr = m_quantization_vol = -1;
        SetPlayPressedTonesSilence();
        g_SongTimer.WaitForTimerRoutineProcessed(); // The Timer Routine will run at least once
    }
}

// SongPlayNextLine() is implemented in SongCore.cpp.

BOOL CSong::PlayBeat()
{
    int t, tt, xline, len, go, speed;
    int note[SONGTRACKS], instr[SONGTRACKS], vol[SONGTRACKS];
    TTrack* tr;

    for (t = 0; t < g_tracks4_8; t++)		//done here to make it behave the same as in the routine
    {
        note[t] = -1;
        instr[t] = -1;
        vol[t] = -1;
    }

TrackLine:
    speed = m_speed;

    for (t = 0; t < g_tracks4_8; t++)
    {
        tt = SongGetTrack(m_songplayline, t);
        tr = g_Tracks.GetTrack(tt);
        if (!tr) continue;	// Invalid track pointer
        len = tr->len;
        go = tr->go;
        if (m_trackplayline >= len)
        {
            if (go >= 0)
                xline = ((m_trackplayline - len) % (len - go)) + go;
            else
            {
                //if it is the end of the track, but it is a block play or the first PlayBeat call (when m_play = 0)
                if (m_play == PLAY_BLOCK || m_play == PLAY_STOP) { note[t] = -1; instr[t] = -1; vol[t] = -1; continue; }
                //otherwise a normal predecision to the next line in the song
                SongPlayNextLine();
                goto TrackLine;
            }
        }
        else
            xline = m_trackplayline;

        if (tr->note[xline] >= 0)	note[t] = tr->note[xline];
        instr[t] = tr->instr[xline];	//due to the same behavior as in the routine
        if (tr->volume[xline] >= 0) vol[t] = tr->volume[xline];
        if (tr->speed[xline] > 0) speed = tr->speed[xline];
    }

    //only now is the changed speed set
    m_speeda = m_speed = speed;

    //active note, instrument and volume settings
    for (t = 0; t < g_tracks4_8; t++)
    {
        int n = note[t];
        int i = instr[t];
        int v = vol[t];
        if (v >= 0 && v < 16)
        {
            if (n >= 0 && n < CNotes::NOTESNUM /*&& i>=0 && i<INSTRSNUM*/)		// adjustment for routine compatibility
            {
                if (i < 0 || i >= INSTRSNUM) { i = 255; }				// adjustment for routine compatibility
                g_AtariTrackerDriver->SetTrackNoteInstrumentVolume(t, n, i, v);
            }
            else
            {
                g_AtariTrackerDriver->SetTrackVolume(t, v);
            }
        }
    }

    if (m_play == PLAY_BLOCK)
    {
        if (m_pokeyStream && m_pokeyStream->CallFromPlayBeat(m_trackplayline) == true)
        {
            // Song is done, so stop the play back
            m_play = PLAY_STOP;					// Stop the player
        }
    }

    return 1;
}

BOOL CSong::PlayVBI()
{
    if (!m_play) { return 0; }	//not playing

    m_speeda--;
    if (m_speeda > 0) { return 0; }	//too soon to update

    m_trackplayline++;

    //m_play mode 4 => only plays range in block
    if (m_play == PLAY_BLOCK && m_trackplayline > m_trackplayblockend) { m_trackplayline = m_trackplayblockstart; }

    // If none of the tracks end with "end", then it will end when reaching m_maxtracklen
    if (m_trackplayline >= g_Tracks.GetMaxTrackLength()) {
        SongPlayNextLine();
    }

    PlayBeat();	//1 pattern track line play

    if (m_speeda == m_speed && m_followplay)	//playing and following the player
    {
        m_trackactiveline = m_trackplayline;
        m_songactiveline = m_songplayline;

        //Quantization
        if (m_quantization_note >= 0 && m_quantization_note < CNotes::NOTESNUM
            && m_quantization_instr >= 0 && m_quantization_instr < INSTRSNUM
            )
        {
            int vol = m_quantization_vol;
            if (g_respectvolume)
            {
                int v = TrackGetVol();
                if (v >= 0 && v <= MAXVOLUME) { vol = v; }
            }

            if (TrackSetNoteInstrVol(m_quantization_note, m_quantization_instr, vol))
            {
                SetPlayPressedTonesTNIV(m_trackactivecol, m_quantization_note, m_quantization_instr, vol);
            }
        }
        else
            if (m_quantization_note == -2) //Special case (midi NoteOFF)
            {
                TrackSetNoteActualInstrVol(-1);
                TrackSetVol(0);
            }
        m_quantization_note = -1; //cancel the quantized note
        //end of Q
    }

    return 1;
}

/// <summary>
/// Call this X times per second to handle the playing of the song
/// </summary>
void CSong::TimerRoutine()
{
    // If the POKEY Stream is being recorded, the Timer Routine is bypassed entirely to run as fast as possible
    if (m_pokeyStream == nullptr || !m_pokeyStream->IsRecording())
    {
        // Things that are solved 1x for vbi
        PlayVBI();

        // Play tones if there are key presses
        PlayPressedTones();

        //--- Rendered Sound ---//
        g_Pokey.RenderSound1_50(m_instrumentSpeed);		// Rendering of a piece of sample (1 / 50s = 20ms)

        if (m_play) { g_playtime++; }					// If the song is currently playing, increment the timer
    }

    //--- NTSC timing hack during playback ---//
    // The NTSC timing cannot be divided to an integer
    // the optimal timing would be 16.666666667ms, which is typically rounded to 17
    // unfortunately, things run too slow with 17, or too fast 16
    // a good enough compromise for now is to make use of a '17-17-16' miliseconds "groove"
    // this isn't proper, but at least, this makes the timing much closer to the actual thing
    // the only issue with this is that the sound will have very slight jitters during playback 
    ChangeTimer(IsNTSC() ? m_timerRoutineTick[g_timerGlobalCount % 3] : 20);

    g_timerGlobalCount++;			// Increment by one each time Timer Routine was processed
}


// CSong::BLOCKSETBEGIN() is implemented in SongEditing.cpp (only touches g_Tracks/g_Instruments/g_Undo/g_TrackClipboard/g_tracks4_8, not Global.h's wider dependency graph).

// CSong::BLOCKSETEND() is implemented in SongEditing.cpp (only touches g_Tracks/g_Instruments/g_Undo/g_TrackClipboard/g_tracks4_8, not Global.h's wider dependency graph).


// CSong::BLOCKDESELECT() is implemented in SongEditing.cpp (only touches g_Tracks/g_Instruments/g_Undo/g_TrackClipboard/g_tracks4_8, not Global.h's wider dependency graph).

// CSong::ISBLOCKSELECTED() is implemented in SongEditing.cpp (only touches g_Tracks/g_Instruments/g_Undo/g_TrackClipboard/g_tracks4_8, not Global.h's wider dependency graph).
