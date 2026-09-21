#include "StdAfx.h"

#include "Song.h"

#include "Atari.h"
#include "PokeyController.h"
#include "PokeyStream.h"

// These CSong methods have no dependency on Global.h (unlike the rest of
// Song.cpp/IO_Song.cpp) - the constructor needs only g_Atari (cheap to
// construct/stub, see AtariTests.cpp), and everything else here only reads/
// writes CSong's own member state. Kept separate to keep this half
// testable without linking Song.cpp's much larger dependency graph - same
// pattern as Tuning.cpp/TuningTables.cpp, Tracks.cpp/TracksEdit.cpp, and
// Instruments.cpp/InstrumentsCore.cpp+InstrumentsAtaFormat.cpp.
//
// SongToAta()/AtaToSong() (originally in IO_Song.cpp) are included here too:
// they only need the trivial g_tracks4_8 global besides CSong's own m_song/
// m_songgo members.

extern CAtari g_Atari;
extern int g_tracks4_8; // TODO Move out (see Instruments.cpp/IO_Instruments.cpp)

CSong::CSong()
{
    // Attributes
    memset(m_songname, 0, SONG_NAME_MAX_LEN);

    // Initialise Timer
    m_quantization_note = -1; // init
    m_quantization_instr = -1;
    m_quantization_vol = -1;

    m_PokeyController = new CPokeyController(&g_Atari);
}

CSong::~CSong()
{
    //KillTimer();
}

CString CSong::GetName() const {
    CString result;
    result = m_songname;
    result.TrimRight();
    return result;
}

int CSong::GetTracks() const {
    return g_tracks4_8;

}

bool CSong::IsStereo() const {
    return (GetTracks() > 4);
}

BOOL CSong::IsNTSC() const {
    return m_ntsc;
}

int CSong::GetInstrumentSpeed() const {
    return m_instrumentSpeed;
}

BOOL CSong::PlayPressedTonesInit()
{
    for (int t = 0; t < SONGTRACKS; t++) {
        SetPlayPressedTonesTNIV(t, -1, -1, -1);
    }
    return TRUE;
}

BOOL CSong::SetPlayPressedTonesSilence()
{
    for (int t = 0; t < SONGTRACKS; t++) {
        SetPlayPressedTonesTNIV(t, -1, -1, 0);
    }
    return TRUE;
}

int CSong::GetActiveInstr() const
{
    return m_activeinstr;
};

int CSong::GetActiveColumn() const
{
    return m_trackactivecol;
};

int CSong::GetActiveLine() const
{
    return m_trackactiveline;
};

int CSong::GetPlayLine() const
{
    return m_trackplayline;
};
void CSong::SetActiveLine(int line)
{
    m_trackactiveline = line;
};
void CSong::SetPlayLine(int line) { m_trackplayline = line; };

BOOL CSong::UECursorIsEqual(int* cursor1, int* cursor2, Part part)
{
    int len;
    switch (part)
    {
    case Part::PART_TRACKS:
        len = 4;
        break;
    case Part::PART_SONG:
        len = 2;
        break;
    case Part::PART_INSTRUMENTS:
        len = 6;
        break;
    case Part::PART_INFO:
        len = 1;
        break;
    default:
        return 0;
    }
    for (int i = 0; i < len; i++) if (cursor1[i] != cursor2[i]) return 0;
    return 1;
}

int  CSong::SongGetGo() const
{
    return m_songgo[m_songactiveline];
};

int  CSong::SongGetGo(int songline) const
{
    return m_songgo[songline];
};

void  CSong::SongTrackGoDec()
{
    m_songgo[m_songactiveline] = (m_songgo[m_songactiveline] - 1) & 0xff;
};

void  CSong::SongTrackGoInc()
{
    m_songgo[m_songactiveline] = (m_songgo[m_songactiveline] + 1) & 0xff;
};

int CSong::FindNearTrackBySongLineAndColumn(int songline, int column, BYTE* arrayTRACKSNUM)
{
    int j, k, t;
    for (j = songline; j >= 0; j--)
    {
        if (m_songgo[j] >= 0) continue;
        if ((t = m_song[j][column]) >= 0)
        {
            //found the default track t
            for (k = t + 1; k < TRACKSNUM; k++)
            {
                if (arrayTRACKSNUM[k] == 0) return k;
            }
            //because it did not find any behind it, it will try to look in front of it instead
            for (k = t - 1; k >= 0; k--)
            {
                if (arrayTRACKSNUM[k] == 0) return k;
            }
        }
    }
    //will search for the first one usable from the beginning
    for (k = 0; k < TRACKSNUM; k++)
    {
        if (arrayTRACKSNUM[k] == 0) return k;
    }
    return -1;
}

BOOL CSong::SongPlayNextLine()
{
    m_trackplayline = 0;	//first track pattern line

    // Normal play, play from current position, or play from bookmark => shift to the next line
    if (m_play == PLAY_SONG || m_play == PLAY_FROM || m_play == PLAY_BOOKMARK)
    {
        m_songplayline++;		// Increment the song line by 1
        if (m_songplayline > 255)
            m_songplayline = 0;	// Above 255, roll over to 0
    }

    // When a goto line is encountered, the player will jump right to the defined line and continue playback from that position
    if (m_songgo[m_songplayline] >= 0)				// If a goto line is set here...
        m_songplayline = m_songgo[m_songplayline];	// goto line xy

    if (m_pokeyStream && m_pokeyStream->TrackSongLine(m_songplayline) == true)
    {
        // Song is done, so stop the play back
        m_play = PLAY_STOP;					// Stop the player
    }
    return 1;
}

int CSong::SongToAta(unsigned char* dest, int max, int adr)
{
    int j;
    int apos = 0, len = 0, go = -1;;
    for (int sline = 0; sline < SONGLEN; sline++)
    {
        apos = sline * g_tracks4_8;
        if (apos + g_tracks4_8 > max) return len;		//if it had a buffer overflow

        if ((go = m_songgo[sline]) >= 0)
        {
            //there is a goto line
            dest[apos] = 254;		//go command
            dest[apos + 1] = go;		//number where to jump
            WORD goadr = adr + (go * g_tracks4_8);
            dest[apos + 2] = goadr & 0xff;	//low byte
            dest[apos + 3] = (goadr >> 8);		//high byte
            if (g_tracks4_8 > 4)
            {
                for (int j = 4; j < g_tracks4_8; j++) dest[apos + j] = 255; //to make sure this is the correct line
            }
            len = sline * g_tracks4_8 + 4; //this is the end for now (goto has 4 bytes for 8 tracks)
        }
        else
        {
            //there are track numbers
            for (int i = 0; i < g_tracks4_8; i++)
            {
                j = m_song[sline][i];
                if (j >= 0 && j < TRACKSNUM)
                {
                    dest[apos + i] = j;
                    len = (sline + 1) * g_tracks4_8;		//this is the end for now
                }
                else
                    dest[apos + i] = 255; //--
            }
        }
    }
    return len;
}

BOOL CSong::AtaToSong(unsigned char* sour, int len, int adr)
{
    int i = 0;
    int col = 0, line = 0;
    unsigned char b;
    while (i < len)
    {
        b = sour[i];
        if (b >= 0 && b < TRACKSNUM)
        {
            m_song[line][col] = b;
        }
        else
            if (b == 254 && col == 0)		//go command only in 0 track
            {
                //m_songgo[line]=sour[i+1];  //the driver took it by the number in channel 1
                //but more importantly, it's a vector, so it's better done that way
                int ptr = sour[i + 2] | (sour[i + 3] << 8); //goto vector
                int go = (ptr - adr) / g_tracks4_8;
                if (go >= 0 && go < (len / g_tracks4_8) && go < SONGLEN)
                    m_songgo[line] = go;
                else
                    m_songgo[line] = 0;	//place of invalid jump and jump to line 0
                i += g_tracks4_8;
                if (i >= len)	return 1;		//this is the end of goto
                line++;
                if (line >= SONGLEN) return 1;
                continue;
            }
            else
                m_song[line][col] = -1;

        col++;
        if (col >= g_tracks4_8)
        {
            line++;
            if (line >= SONGLEN) return 1;	//so that it does not overflow
            col = 0;
        }
        i++;
    }
    return 1;
}
