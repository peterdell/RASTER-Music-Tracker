#include "StdAfx.h"

#include "Song.h"

#include "Clipboard.h"
#include "TuningTypes.h"
#include "AtariIO.h"
#include "IOHelpers.h"
#include "RmtVersion.h"
#include <fstream>

// These CSong methods only touch g_Tracks/g_Instruments/g_Undo/
// g_TrackClipboard/g_tracks4_8 (all confirmed cheap to construct - see
// TracksTests.cpp/InstrumentsTests.cpp/AtariTests.cpp and the CUndo/
// CTrackClipboard constructors), not Song.cpp's much wider Global.h/
// EffectsDlg.h/MainFrm.h dependency graph (MFC dialogs, the live timer,
// real Atari hardware access). Kept separate to keep this half testable -
// same pattern as Tuning.cpp/TuningTables.cpp, Tracks.cpp/TracksEdit.cpp,
// Instruments.cpp/InstrumentsCore.cpp+InstrumentsAtaFormat.cpp, and
// Song.cpp/SongCore.cpp.
//
// Note: BLOCKSETBEGIN()/BlockPaste() call into CTrackClipboard methods that
// internally read the *global* g_Song (not necessarily the CSong instance
// they were called on) - a pre-existing coupling in CTrackClipboard itself,
// unrelated to this split. Tests exercising those specific methods use
// g_Song as the instance under test to match real usage.
//
// SaveRMW()/SaveTxt()/LoadRMT() (originally in IO_Song.cpp) are included
// here too: SaveRMW()/SaveTxt() used to call CString::LoadString(
// IDS_RMT_VERSION), an MFC resource-string load needing the app's compiled
// resources - replaced with a compile-time RMT_VERSION_STRING constant
// (see RmtVersion.h), removing the dependency everywhere it was used, not
// just here. LoadRMW()/LoadTxt() stay behind in IO_Song.cpp: both call
// ClearSong() first, which needs its own dedicated decision (real
// AfxGetMainWnd()/g_AtariTrackerDriver hazards, see
// plans/SONG_IO_SONG_REMAINING_PLAN.md).

extern CTrackClipboard g_TrackClipboard;
extern CInstruments g_Instruments;
extern int g_tracks4_8;
extern TTuningSettings g_tuning;
extern TTuningRatios g_tuningRatios;
extern HWND g_hwnd;
extern WORD g_rmtstripped_adr_module;

// DEFINE_MAINPARAMS (see below, used by SaveRMW()) takes the address of
// each of these - all plain ints/enums/bools with no constructor or
// hazard of their own.
extern Part g_activepart;
extern Part g_active_ti;
extern EditMode volatile g_prove;
extern BOOL volatile g_respectvolume;
extern int g_trackLinePrimaryHighlight;
extern BOOL g_tracklinealtnumbering;
extern BOOL g_displayflatnotes;
extern BOOL g_usegermannotation;
extern int g_cursoractview;
extern KeyboardLayout g_keyboard_layout;
extern BOOL g_keyboard_escresetatarisound;
extern BOOL g_keyboard_swapenter;
extern BOOL g_keyboard_playautofollow;
extern BOOL g_keyboard_updowncontinue;
extern BOOL g_keyboard_RememberOctavesAndVolumes;

int CSong::GetSubsongParts(CString& resultstr) const
{
    CString s;
    int songp[SONGLEN]{};
    int i, j, n, lastgo, apos, asub;
    BOOL ok;
    lastgo = -1;
    for (i = 0; i < SONGLEN; i++)
    {
        songp[i] = -1;
        if (m_songgo[i] >= 0) lastgo = i;
    }

    resultstr = "";
    apos = 0;
    asub = 0;
    ok = 0;	//if it found any non-zero tracks in the given subsong (different from --)

    for (i = 0; i <= lastgo; i++)
    {
        if (songp[i] < 0)
        {
            apos = i;
            while (songp[apos] < 0)
            {
                n = m_songgo[apos];
                songp[apos] = asub;
                if (n >= 0) //jump to another line
                    apos = n;
                else
                {
                    if (!ok)
                    {	//has not found any tracks in this subsong yet
                        for (j = 0; j < g_tracks4_8; j++)
                        {
                            if (m_song[apos][j] >= 0)
                            {	//if then found, this will be the beginning of the subsong
                                s.Format("%02X ", apos);
                                resultstr += s;
                                ok = 1;		//the beginning of this subsong is already written
                                break;
                            }
                        }
                    }
                    apos++;
                    if (apos >= SONGLEN) break;
                }
            }
            if (ok) asub++;	//will move to the next if the subsong contains anything at all
            ok = 0; //initialization for further search
        }
    }
    return asub;
}

void CSong::MarkTF_USED(BYTE* arrayTRACKSNUM) const
{
    //all tracks used in the song
    for (int i = 0; i < SONGLEN; i++)
    {
        if (m_songgo[i] < 0)
        {
            for (int channelNr = 0; channelNr < g_tracks4_8; channelNr++)
            {
                int tr = m_song[i][channelNr];
                if (tr >= 0 && tr < TRACKSNUM)
                {
                    arrayTRACKSNUM[tr] = TrackFlag::TF_USED;
                }
            }
        }
    }
}

void CSong::MarkTF_NOEMPTY(BYTE* arrayTRACKSNUM) const
{
    for (int i = 0; i < TRACKSNUM; i++)
    {
        if (g_Tracks.CalculateNotEmpty(i))
        {
            arrayTRACKSNUM[i] |= TrackFlag::TF_NOEMPTY;
        }
    }
}

void CSong::ActiveInstrSet(int instr)
{
    g_Instruments.MemorizeOctaveAndVolume(m_activeinstr, m_octave, m_volume);
    m_activeinstr = instr;
    g_Instruments.RememberOctaveAndVolume(m_activeinstr, m_octave, m_volume);
}

void CSong::ActiveInstrPrev()
{
    g_Undo.Separator(); int instr = (m_activeinstr - 1) & 0x3f; ActiveInstrSet(instr);
};

void CSong::ActiveInstrNext()
{
    g_Undo.Separator(); int instr = (m_activeinstr + 1) & 0x3f; ActiveInstrSet(instr);
};

BOOL CSong::TrackLeft(BOOL column)
{
    g_Undo.Separator();
    if (column) goto track_leftcolumn;
    m_trackactivecur--;
    if (m_trackactivecur < 0)
    {
        m_trackactivecur = 3;	//previous speed column
    track_leftcolumn:
        m_trackactivecol--;
        if (m_trackactivecol < 0) m_trackactivecol = g_tracks4_8 - 1;
    }
    return 1;
}

BOOL CSong::TrackRight(BOOL column)
{
    g_Undo.Separator();
    if (column) goto track_rightcolumn;
    m_trackactivecur++;
    if (m_trackactivecur > 3)	//speed column
    {
        m_trackactivecur = 0;
    track_rightcolumn:
        m_trackactivecol++;
        if (m_trackactivecol >= g_tracks4_8) m_trackactivecol = 0;
    }
    return 1;
}

void CSong::RespectBoundaries()
{
    int songline = SongGetActiveLine();

    if (songline > SONGLEN) songline = SONGLEN - 1;
    if (songline < 0) songline = 0;

    int length = GetSmallestMaxtracklen(songline);
    int line = GetActiveLine();

    if (line > length) line = length - 1;
    if (line < 0) line = 0;

    SetActiveLine(line);
    SongSetActiveLine(songline);
}

void CSong::TrackGetLoopingNoteInstrVol(int track, int& note, int& instr, int& vol) const
{
    // Set the current visible note to a possible goto loop
    int line, len, go;
    len = g_Tracks.GetLastLine(track) + 1;
    go = g_Tracks.GetGoLine(track);
    if (m_trackactiveline < len)
        line = m_trackactiveline;
    else
    {
        int loop = (go - len) + go;
        if (go >= 0 && loop)
        {
            line = (m_trackactiveline - len) % loop;
        }
        else
        {
            note = instr = vol = -1;
            return;
        }
    }
    note = g_Tracks.GetNote(track, line);
    instr = g_Tracks.GetInstr(track, line);
    vol = g_Tracks.GetVol(track, line);
}

int* CSong::GetUECursor(Part part)
{
    int* cursor;
    switch (part)
    {
    case Part::PART_TRACKS:
        cursor = new int[4];
        cursor[0] = m_songactiveline;
        cursor[1] = m_trackactiveline;
        cursor[2] = m_trackactivecol;
        cursor[3] = m_trackactivecur;
        break;

    case Part::PART_SONG:
        cursor = new int[2];
        cursor[0] = m_songactiveline;
        cursor[1] = m_trackactivecol;
        break;

    case Part::PART_INSTRUMENTS:
    {
        cursor = new int[6];
        cursor[0] = m_activeinstr;
        TInstrument* in = g_Instruments.GetInstrument(m_activeinstr);
        cursor[1] = (int)in->activeEditSection;
        cursor[2] = in->editEnvelopeX;
        cursor[3] = in->editEnvelopeY;
        cursor[4] = in->editParameterNr;
        cursor[5] = in->editNoteTableCursorPos;
        //=in->activenam; It omits that any change in the cursor position in the name is not a reason for undo separation
    }
    break;

    case Part::PART_INFO:
    {
        cursor = new int[1];
        cursor[0] = (int)m_infoact;
    }
    break;

    default:
        cursor = NULL;
    }
    return cursor;
}

BOOL CSong::SongTrackSet(int t)
{
    if (t >= -1 && t < TRACKSNUM)
    {
        g_Undo.ChangeSong(m_songactiveline, m_trackactivecol, UETYPE_SONGTRACK);
        m_song[m_songactiveline][m_trackactivecol] = t;
    }
    return 1;
}

BOOL CSong::SongTrackSetByNum(int num)
{
    int i;
    if (m_songgo[m_songactiveline] < 0) // GO ?
    {	//changes track
        i = SongGetActiveTrack();
        if (i < 0) i = 0;
        i &= 0x0f;	//just the lower digit
        i = (i << 4) | num;
        if (i >= TRACKSNUM) i &= 0x0f;
        return SongTrackSet(i);
    }
    else
    {	//changes GO parameter
        i = m_songgo[m_songactiveline];
        if (i < 0) i = 0;
        i &= 0x0f;	//just the lower digit
        i = (i << 4) | num;
        if (i >= SONGLEN) i &= 0x0f;
        g_Undo.ChangeSong(m_songactiveline, m_trackactivecol, UETYPE_SONGGO);
        m_songgo[m_songactiveline] = i;
        return 1;
    }
}

BOOL CSong::SongTrackDec()
{
    if (m_songgo[m_songactiveline] < 0)
    {
        int t = m_song[m_songactiveline][m_trackactivecol] - 1;
        if (t < -1) t = TRACKSNUM - 1;
        g_Undo.ChangeSong(m_songactiveline, m_trackactivecol, UETYPE_SONGTRACK);
        m_song[m_songactiveline][m_trackactivecol] = t;
    }
    else
    {	//GO is there
        int g = m_songgo[m_songactiveline] - 1;
        if (g < 0) g = SONGLEN - 1;
        g_Undo.ChangeSong(m_songactiveline, m_trackactivecol, UETYPE_SONGGO);
        m_songgo[m_songactiveline] = g;
    }
    return 1;
}

BOOL CSong::SongTrackInc()
{
    if (m_songgo[m_songactiveline] < 0)
    {
        int t = m_song[m_songactiveline][m_trackactivecol] + 1;
        if (t >= TRACKSNUM) t = -1;
        g_Undo.ChangeSong(m_songactiveline, m_trackactivecol, UETYPE_SONGTRACK);
        m_song[m_songactiveline][m_trackactivecol] = t;
    }
    else
    {	//GO is there
        int g = m_songgo[m_songactiveline] + 1;
        if (g >= SONGLEN) g = 0;
        g_Undo.ChangeSong(m_songactiveline, m_trackactivecol, UETYPE_SONGGO);
        m_songgo[m_songactiveline] = g;
    }
    return 1;
}

BOOL CSong::SongTrackEmpty()
{
    g_Undo.ChangeSong(m_songactiveline, m_trackactivecol, UETYPE_SONGTRACK);
    m_song[m_songactiveline][m_trackactivecol] = -1;
    return 1;
}

BOOL CSong::SongTrackGoOnOff()
{
    //GO on/off
    g_Undo.ChangeSong(m_songactiveline, m_trackactivecol, UETYPE_SONGGO);
    m_songgo[m_songactiveline] = (m_songgo[m_songactiveline] < 0) ? 0 : -1;
    return 1;
}

BOOL CSong::SongInsertLine(int line)
{
    g_Undo.ChangeSong(line, m_trackactivecol, UETYPE_SONGDATA, 0);
    int j, go;
    for (int i = SONGLEN - 2; i >= line; i--)
    {
        for (j = 0; j < g_tracks4_8; j++) m_song[i + 1][j] = m_song[i][j];
        go = m_songgo[i];
        if (go > 0 && go >= line) go++;
        m_songgo[i + 1] = go;
    }
    for (j = 0; j < g_tracks4_8; j++) m_song[line][j] = -1;
    m_songgo[line] = -1;
    for (int i = 0; i < line; i++)
    {
        if (m_songgo[i] >= line) m_songgo[i]++;
    }
    if (IsBookmark() && m_bookmark.songline >= line)
    {
        m_bookmark.songline++;
        if (m_bookmark.songline >= SONGLEN) ClearBookmark(); //just pushed the bookmark out of the song => cancel the bookmark
    }
    return 1;
}

BOOL CSong::SongDeleteLine(int line)
{
    g_Undo.ChangeSong(line, m_trackactivecol, UETYPE_SONGDATA, 0);
    int j, go;
    for (int i = line; i < SONGLEN - 1; i++)
    {
        for (j = 0; j < g_tracks4_8; j++) m_song[i][j] = m_song[i + 1][j];
        go = m_songgo[i + 1];
        if (go > 0 && go > line) go--;
        m_songgo[i] = go;
    }
    for (int i = 0; i < line; i++)
    {
        if (m_songgo[i] > line) m_songgo[i]--;
    }
    for (j = 0; j < g_tracks4_8; j++) m_song[SONGLEN - 1][j] = -1;
    m_songgo[SONGLEN - 1] = -1;
    if (IsBookmark() && m_bookmark.songline >= line)
    {
        m_bookmark.songline--;
        if (m_bookmark.songline < line) ClearBookmark(); //just deleted the songline with the bookmark
    }
    return 1;
}

void CSong::TrackCopy()
{
    TTrack* at = g_Tracks.GetTrack(SongGetActiveTrack()), * tot = &g_TrackClipboard.m_trackcopy;

    if (at && tot)
    {
        *tot = *at;
    }
}

void CSong::TrackPaste()
{
    TTrack* at = g_Tracks.GetTrack(SongGetActiveTrack()), * fro = &g_TrackClipboard.m_trackcopy;

    if (at && fro)
    {
        if (g_Tracks.IsValidLength(fro->len)) *at = *fro;
    }
}

void CSong::TrackDelete()
{
    g_Tracks.ClearTrack(SongGetActiveTrack());
}

void CSong::TrackCut()
{
    TrackCopy();
    TrackDelete();
}

void CSong::TrackCopyFromTo(int fromtrack, int totrack)
{
    TTrack* at = g_Tracks.GetTrack(fromtrack), * tot = g_Tracks.GetTrack(totrack);

    if (at && tot)
    {
        *tot = *at;
    }
}

void CSong::TrackSwapFromTo(int fromtrack, int totrack)
{
    TTrack buf;
    TTrack* at = g_Tracks.GetTrack(fromtrack), * tot = g_Tracks.GetTrack(totrack);

    if (at && tot)
    {
        buf = *tot;
        *tot = *at;
        *at = buf;
    }
}

void CSong::BlockPaste(int special)
{
    g_Undo.ChangeTrack(SongGetActiveTrack(), m_trackactiveline, UETYPE_TRACKDATA, 1);
    int lines = g_TrackClipboard.BlockPasteToTrack(SongGetActiveTrack(), m_trackactiveline, special);
    if (lines > 0)
    {
        int lastl = m_trackactiveline + lines - 1;
        //resets the beginning of the block to this location
        g_TrackClipboard.BlockDeselect();
        g_TrackClipboard.BlockSetBegin(m_trackactivecol, SongGetActiveTrack(), m_trackactiveline);
        g_TrackClipboard.BlockSetEnd(lastl);
        //moves the current line to the last bottom row of the pasted block
        m_trackactiveline = lastl;
    }
}

void CSong::InstrCopy()
{
    int i = GetActiveInstr();
    memcpy(&m_instrclipboard, g_Instruments.GetInstrument(i), sizeof(TInstrument));
}

void CSong::InstrCut()
{
    InstrCopy();
    InstrDelete();
}

void CSong::InstrDelete()
{
    g_Instruments.ClearInstrument(GetActiveInstr());
}

void CSong::SongCopyLine()
{
    for (int i = 0; i < g_tracks4_8; i++) m_songlineclipboard[i] = m_song[m_songactiveline][i];
    m_songgoclipboard = m_songgo[m_songactiveline];
}

void CSong::SongPasteLine()
{
    if (m_songgoclipboard < -1) return;
    g_Undo.ChangeSong(m_songactiveline, m_trackactivecol, UETYPE_SONGDATA);
    for (int i = 0; i < g_tracks4_8; i++) m_song[m_songactiveline][i] = m_songlineclipboard[i];
    m_songgo[m_songactiveline] = m_songgoclipboard;
}

void CSong::SongClearLine()
{
    g_Undo.ChangeSong(m_songactiveline, m_trackactivecol, UETYPE_SONGDATA);
    for (int i = 0; i < g_tracks4_8; i++) m_song[m_songactiveline][i] = -1;
    m_songgo[m_songactiveline] = -1;
}

int CSong::GetEffectiveMaxtracklen()
{
    //calculate the largest track length used
    int so, i, max = 1;
    for (so = 0; so < SONGLEN; so++)
    {
        if (m_songgo[so] >= 0) continue; //go to line is ignored
        int min = g_Tracks.GetMaxTrackLength();
        int p = 0;
        for (i = 0; i < g_tracks4_8; i++)
        {
            int t = m_song[so][i];
            int m = g_Tracks.GetLength(t);
            if (m < 0) continue;
            p++;
            if (m < min) min = m;
        }
        //min = the shortest track length on this songline
        if (p > 0 && min > max) max = min;
    }
    return max;
}

int CSong::GetSmallestMaxtracklen(int songline)
{
    //calculate the smallest track length used in this songline
    int so = songline;
    int max = 256;
    int min = g_Tracks.GetMaxTrackLength();
    int p = 0;

    if (m_songgo[so] >= 0)	return 0; //go to line is ignored

    for (int i = 0; i < g_tracks4_8; i++)
    {
        int t = m_song[so][i];
        int m = g_Tracks.GetLength(t);
        if (m < 0) continue;
        if (m < max) max = m;
        p++;
    }
    if (!p) return min;	//return 0;	//cannot be from empty tracks

    //min = the shortest track length on this songline
    if (p > 0 && min < max) max = min;

    return max;
}

void CSong::ChangeMaxtracklen(int maxtracklen)
{
    if (!g_Tracks.IsValidLength(maxtracklen)) return;

    int i, j;
    TTrack* tt;

    for (i = 0; i < TRACKSNUM; i++)
    {
        tt = g_Tracks.GetTrack(i);
        //clear
        for (j = tt->len; j < TRACKLEN; j++)
        {
            tt->note[j] = tt->instr[j] = tt->volume[j] = tt->speed[j] = -1;
        }
        //
        if (tt->len >= maxtracklen)
        {
            tt->go = -1; //cancel GO
            tt->len = maxtracklen; //adjust length
        }
    }

    g_Tracks.SetMaxTrackLength(maxtracklen);
}

void CSong::SongClearUnusedTracksAndParts(int& clearedtracks, int& truncatedtracks, int& truncatedbeats)
{
    int i, j, ch;
    int ttracks = 0, tbeats = 0, ctracks = 0;
    int tracklen[TRACKSNUM]{};
    BOOL trackused[TRACKSNUM]{};
    TTrack* tr;

    // Initialise
    for (i = 0; i < TRACKSNUM; i++)
    {
        tracklen[i] = -1;
        trackused[i] = FALSE;
    }

    for (int sline = 0; sline < SONGLEN; sline++)
    {
        if (IsSongGo(sline)) continue;	// Goto line is ignored

        int nejkratsi = g_Tracks.GetMaxTrackLength();

        for (ch = 0; ch < g_tracks4_8; ch++)
        {
            int n = m_song[sline][ch];

            if (!g_Tracks.IsValidTrack(n)) continue;	// Invalid track is ignored

            trackused[n] = 1;
            tr = g_Tracks.GetTrack(n);

            if (g_Tracks.IsValidGo(tr->go)) continue;	// There is a loop => it has a maximum length

            if (tr->len < nejkratsi) nejkratsi = tr->len;
        }

        // "nejkratsi" is the shortest track in this song line
        for (ch = 0; ch < g_tracks4_8; ch++)
        {
            int n = m_song[sline][ch];

            if (!g_Tracks.IsValidTrack(n)) continue;	// Invalid track is ignored

            if (tracklen[n] < nejkratsi) tracklen[n] = nejkratsi; // If it needs a longer size, it will expand to the length it needs
        }
    }

    // And now it cuts those tracks
    for (i = 0; i < TRACKSNUM; i++)
    {
        int nlen = tracklen[i];

        if (nlen < 1) continue;	// If they don't have the length of at least 1 they are skipped

        tr = g_Tracks.GetTrack(i);

        // There is no loop
        if (!g_Tracks.IsValidGo(tr->go))
        {
            if (nlen < tr->len)
            {
                // For what must cut, is there anything at all?
                for (j = nlen; j < tr->len; j++)
                {
                    if (g_Tracks.IsValidNote(tr->note[j]) || g_Tracks.IsValidInstrument(tr->instr[j]) || g_Tracks.IsValidVolume(tr->volume[j]) || g_Tracks.IsValidSpeed(tr->speed[j]))
                    {
                        // Yeah, there's something, so cut it
                        ttracks++;
                        tbeats += tr->len - nlen;
                        tr->len = nlen; // Cut what is not needed
                        break;
                    }
                }
                // There is no break;
            }
        }
        // There is a loop
        else
        {
            // The beginning of the loop is further than the required track length
            if (tr->len >= nlen)
            {
                ttracks++;
                tbeats += tr->len - nlen;
                tr->len = nlen;	// Cut the track
                tr->go = -1;	// Disable loop
            }
        }
    }

    // Delete empty tracks not used in the song
    for (i = 0; i < TRACKSNUM; i++)
    {
        if (!trackused[i] && !g_Tracks.IsEmptyTrack(i))
        {
            g_Tracks.ClearTrack(i);
            ctracks++;
        }
    }

    clearedtracks = ctracks;
    truncatedtracks = ttracks;
    truncatedbeats = tbeats;
}

int CSong::SongClearDuplicatedTracks()
{
    int i, j, ch;
    int trackto[TRACKSNUM];

    for (i = 0; i < TRACKSNUM; i++) trackto[i] = -1;

    int clearedtracks = 0;
    for (i = 0; i < TRACKSNUM - 1; i++)
    {
        if (g_Tracks.IsEmptyTrack(i)) continue;	//does not compare empty
        for (j = i + 1; j < TRACKSNUM; j++)
        {
            if (g_Tracks.IsEmptyTrack(j)) continue;
            if (g_Tracks.CompareTracks(i, j))
            {
                g_Tracks.ClearTrack(j);	//j is the same as i, so j is deleted.
                trackto[j] = i;			//these tracks have to be replaced by tracks i
                clearedtracks++;
            }
        }
    }

    //analyse the song and make changes to the deleted tracks
    for (int sline = 0; sline < SONGLEN; sline++)
    {
        for (ch = 0; ch < g_tracks4_8; ch++)
        {
            int n = m_song[sline][ch];
            if (n < 0 || n >= TRACKSNUM) continue;	//--
            if (trackto[n] >= 0) m_song[sline][ch] = trackto[n];
        }
    }

    return clearedtracks;
}

int CSong::SongClearUnusedTracks()
{
    int i, ch;
    BOOL trackused[TRACKSNUM];

    for (i = 0; i < TRACKSNUM; i++) trackused[i] = 0;

    for (int sline = 0; sline < SONGLEN; sline++)
    {
        if (m_songgo[sline] >= 0) continue;	//goto line is ignored

        for (ch = 0; ch < g_tracks4_8; ch++)
        {
            int n = m_song[sline][ch];
            if (n < 0 || n >= TRACKSNUM) continue;	//--
            trackused[n] = 1;
        }
    }

    // Delete all tracks unused in the song
    int clearedtracks = 0;
    for (i = 0; i < TRACKSNUM; i++)
    {
        if (!trackused[i])
        {
            if (!g_Tracks.IsEmptyTrack(i)) clearedtracks++;
            g_Tracks.ClearTrack(i);
        }
    }

    return clearedtracks;
}

void CSong::RenumberAllTracks(int type) //1..after columns, 2..after lines
{
    int i, j, sline;
    int movetrackfrom[TRACKSNUM], movetrackto[TRACKSNUM];

    for (i = 0; i < TRACKSNUM; i++) movetrackfrom[i] = movetrackto[i] = -1;

    int order = 0;

    // Test the song
    if (type == 2)
    {
        //horizontally along the lines
        for (sline = 0; sline < SONGLEN; sline++)
        {
            if (m_songgo[sline] >= 0) continue;	//goto line is ignored
            for (i = 0; i < g_tracks4_8; i++)
            {
                int n = m_song[sline][i];
                if (n < 0 || n >= TRACKSNUM) continue;	//--
                if (movetrackfrom[n] < 0)
                {
                    movetrackfrom[n] = order;
                    movetrackto[order] = n;
                    order++;
                }
            }
        }
    }
    else
        if (type == 1)
        {
            //vertically in columns
            for (i = 0; i < g_tracks4_8; i++)
            {
                for (sline = 0; sline < SONGLEN; sline++)
                {
                    if (m_songgo[sline] >= 0) continue;	//goto line is ignored
                    int n = m_song[sline][i];
                    if (n < 0 || n >= TRACKSNUM) continue;	//--
                    if (movetrackfrom[n] < 0)
                    {
                        movetrackfrom[n] = order;
                        movetrackto[order] = n;
                        order++;
                    }
                }
            }
        }
        else
            return;	//unknown type

    //then add empty tracks not used in the song
    for (i = 0; i < TRACKSNUM; i++)
    {
        if (movetrackfrom[i] < 0 && !g_Tracks.IsEmptyTrack(i))
        {
            movetrackfrom[i] = order;
            movetrackto[order] = i;
            order++;
        }
    }

    //precisely numbered in the song
    for (sline = 0; sline < SONGLEN; sline++)
    {
        //if (m_songgo[sline]>=0) continue;	//goto line is not omitted here (the numbers mentioned below it will also change)
        for (i = 0; i < g_tracks4_8; i++)
        {
            int n = m_song[sline][i];
            if (n < 0 || n >= TRACKSNUM) continue;	//--
            m_song[sline][i] = movetrackfrom[n];
        }
    }

    // physical data transfer in tracks
    for (i = 0; i < order; i++)
    {
        int n = movetrackto[i];	// swap i <--> n
        if (n == i) continue;	// they are the same, so they don't have to shuffle anything

        TrackSwapFromTo(i, n);

        for (j = i; j < order; j++)
        {
            if (movetrackto[j] == i) movetrackto[j] = n;
        }
    }
}

int CSong::ClearAllInstrumentsUnusedInAnyTrack()
{
    //go through all existing tracks and find unused instruments

    int i, j, t;
    BOOL instrused[INSTRSNUM];
    TTrack* tr;

    for (i = 0; i < INSTRSNUM; i++) instrused[i] = 0;
    for (i = 0; i < TRACKSNUM; i++)
    {
        tr = g_Tracks.GetTrack(i);
        int nlen = tr->len;
        for (j = 0; j < nlen; j++)
        {
            t = tr->instr[j];
            if (t >= 0 && t < INSTRSNUM) instrused[t] = 1;	//instrument "t" is used
        }
    }

    //delete unused instruments here
    int clearedinstruments = 0;
    for (i = 0; i < INSTRSNUM; i++)
    {
        if (!instrused[i])
        {
            //unused
            if (g_Instruments.CalculateNotEmpty(i)) clearedinstruments++;	//is it empty? yes => it will be deleted
            g_Instruments.ClearInstrument(i);
        }
    }

    return clearedinstruments;
}

void CSong::RenumberAllInstruments(int type)
{
    //type=1...remove gaps, 2=order by using in tracks, type=3...order by instrument names

    int i, j, k, ins;
    int moveinstrfrom[INSTRSNUM], moveinstrto[INSTRSNUM];
    TTrack* tr;

    for (i = 0; i < INSTRSNUM; i++) moveinstrfrom[i] = moveinstrto[i] = -1;

    int order = 0;

    //analyse all tracks
    for (i = 0; i < TRACKSNUM; i++)
    {
        tr = g_Tracks.GetTrack(i);
        int tlen = tr->len;
        for (j = 0; j < tlen; j++)
        {
            ins = tr->instr[j];
            if (ins < 0 || ins >= INSTRSNUM) continue;
            if (moveinstrfrom[ins] < 0)
            {
                moveinstrfrom[ins] = order;
                moveinstrto[order] = ins;
                order++;
            }
        }
    }

    //and now it adds even those that are not used in any track
    for (i = 0; i < INSTRSNUM; i++)
    {
        if (moveinstrfrom[i] < 0 && g_Instruments.CalculateNotEmpty(i))
        {
            moveinstrfrom[i] = order;
            moveinstrto[order] = i;
            order++;
        }
    }

    TInstrument bufi;
    if (type == 1)
    {
        //remove gaps
        int di = 0;
        for (i = 0; i < INSTRSNUM; i++)
        {
            if (moveinstrfrom[i] >= 0) //this instrument is used somewhere or is empty
            {
                //move to
                if (i != di)
                {
                    memcpy(g_Instruments.GetInstrument(di), g_Instruments.GetInstrument(i), sizeof(TInstrument));
                    //and delete instrument i
                    g_Instruments.ClearInstrument(i);
                }
                //change table accordingly
                moveinstrfrom[i] = di;
                moveinstrto[di] = i;
                di++;
            }
        }
    }
    else
        if (type == 2)
        {
            //order by using in tracks
            //moveinstrfrom [instr] and moveinstrto [order] have it ready, so it can physically switch straight away
            for (i = 0; i < order; i++)
            {
                int n = moveinstrto[i];	//swap i <--> n
                if (n == i) continue;
                memcpy(&bufi, g_Instruments.GetInstrument(i), sizeof(TInstrument)); // i -> buffer
                memcpy(g_Instruments.GetInstrument(i), g_Instruments.GetInstrument(n), sizeof(TInstrument)); // n -> i
                memcpy(g_Instruments.GetInstrument(n), &bufi, sizeof(TInstrument)); // buffer -> n
                //
                for (j = i; j < order; j++)
                {
                    if (moveinstrto[j] == i) moveinstrto[j] = n;
                }
            }
            //and now delete the others (due to the corresponding names of unused empty instruments)
            for (i = order; i < INSTRSNUM; i++) g_Instruments.ClearInstrument(i);
        }
        else
            if (type == 3)
            {
                //order by instrument name
                BOOL iused[INSTRSNUM];
                for (i = 0; i < INSTRSNUM; i++)
                {
                    iused[i] = (moveinstrfrom[i] >= 0);
                    moveinstrfrom[i] = i;	//the default is to keep the same order
                }
                //and now bubblesort arrange those that are iused [i]
                for (i = INSTRSNUM - 1; i > 0; i--)
                {
                    for (j = 0; j < i; j++)
                    {
                        k = j + 1;
                        //compare instrument j and k and either let or swap
                        BOOL swap = 0;

                        if (iused[j] != iused[k])
                        {
                            //one is used and one is unused
                            if (iused[k]) swap = 1; //the second is used (=> the first is the one used), so swap
                        }
                        else
                        {
                            //both are used or both are not used
                            char* name1 = g_Instruments.GetName(j);
                            char* name2 = g_Instruments.GetName(k);
                            if (_strcmpi(name1, name2) > 0) swap = 1; //they are the other way around, so they are swapped
                        }

                        if (swap)
                        {
                            //swap j and k
                            memcpy(&bufi, g_Instruments.GetInstrument(j), sizeof(TInstrument)); // j -> buffer
                            memcpy(g_Instruments.GetInstrument(j), g_Instruments.GetInstrument(k), sizeof(TInstrument)); // k -> j
                            memcpy(g_Instruments.GetInstrument(k), &bufi, sizeof(TInstrument)); // buffer -> k
                            //adjust table accordingly
                            int p;
                            for (p = 0; p < INSTRSNUM; p++)
                            {
                                if (moveinstrfrom[p] == k) moveinstrfrom[p] = j;
                                else
                                    if (moveinstrfrom[p] == j) moveinstrfrom[p] = k;
                            }

                            BOOL b = iused[j];
                            iused[j] = iused[k];
                            iused[k] = b;
                        }
                    }
                }
                //still used unused empty instruments (due to their shift, so the name of their number 20: Instrument 21 did not match)
                for (i = 0; i < INSTRSNUM; i++)
                {
                    if (!iused[i]) g_Instruments.ClearInstrument(i);
                }
            }
            else
                return;


    //and now it has to be renumbered in all tracks according to the moveinstrfrom [instr] table
    for (i = 0; i < TRACKSNUM; i++)
    {
        tr = g_Tracks.GetTrack(i);
        int tlen = tr->len;
        for (j = 0; j < tlen; j++)
        {
            ins = tr->instr[j];
            if (ins < 0 || ins >= INSTRSNUM) continue;
            tr->instr[j] = moveinstrfrom[ins];
        }
    }

    //and finally write all the instruments in Atari memory
    for (i = 0; i < INSTRSNUM; i++) g_Instruments.Update(i); //writes to Atari

    //Hooray, done
}

BOOL CSong::SetBookmark()
{
    if (m_songactiveline >= 0 && m_songactiveline < SONGLEN
        && m_trackactiveline >= 0 && m_trackactiveline < g_Tracks.GetMaxTrackLength()
        && m_speed >= 0)
    {
        m_bookmark.songline = m_songactiveline;
        m_bookmark.trackline = m_trackactiveline;
        m_bookmark.speed = m_speed;
        return 1;
    }
    return 0;
}

void CSong::BLOCKSETBEGIN() {
    g_TrackClipboard.BlockSetBegin(m_trackactivecol, SongGetActiveTrack(), m_trackactiveline);
}

void CSong::BLOCKSETEND() {
    g_TrackClipboard.BlockSetEnd(m_trackactiveline);
}

void CSong::BLOCKDESELECT() {
    g_TrackClipboard.BlockDeselect();
}

BOOL CSong::ISBLOCKSELECTED() {
    return g_TrackClipboard.IsBlockSelected();
}

// Both call Stop() first ("Stop the music first"), which only has any
// effect if GetPlayMode() != PLAY_STOP (see Song.cpp) - a no-op as long as
// Play() was never called on this instance, which is the only way these
// are exercised in tests (see plans/SONG_IO_SONG_REMAINING_PLAN.md).

void CSong::TracksAllBuildLoops(int& tracksmodified, int& beatsreduced)
{
    Stop();

    int i;
    int p = 0, u = 0;
    for (i = 0; i < TRACKSNUM; i++)
    {
        int r = g_Tracks.TrackBuildLoop(i);
        if (r > 0) { p++; u += r; }
    }
    tracksmodified = p;
    beatsreduced = u;
}

void CSong::TracksAllExpandLoops(int& tracksmodified, int& loopsexpanded)
{
    Stop();

    int i;
    int p = 0, u = 0;
    for (i = 0; i < TRACKSNUM; i++)
    {
        int r = g_Tracks.TrackExpandLoop(i);
        if (r > 0) { p++; u += r; }
    }
    tracksmodified = p;
    loopsexpanded = u;
}

// ReInitSound() stays in Song.cpp (real g_AtariTrackerDriver/g_Pokey
// hardware-simulation hazard) and is given a link-only no-op stub in the
// test project - identical treatment to Stop() above.

void CSong::SetTracks(const int tracksNum) {
    if (tracksNum != g_tracks4_8) {
        g_tracks4_8 = tracksNum;
        ReInitSound();
    }
}

void CSong::ResetTuningVariables()
{
    // Reset all tuning variables 
    g_tuning.Initialize(IsNTSC());
    g_tuningRatios.Initialize();
}

int CSong::MakeModule(unsigned char* mem, int addr, SongIOType iotype, BYTE* instrumentSavedFlags, BYTE* trackSavedFlags)
{
    int i, j;
    TTrack* tr;

    // Clear the instrument and tracks used flags
    memset(instrumentSavedFlags, 0, INSTRSNUM);
    memset(trackSavedFlags, 0, TRACKSNUM);

    // Write out the RMT header (part 1)
    // 0: RMT4 or RMT8
    // 4: Track length
    // 5: Song speed
    // 6: Instrument speed
    // 7: RMT version (1 for now)
    strncpy((char*)(mem + addr), "RMT", 3);
    mem[addr + 3] = g_tracks4_8 + '0';			// 4 or 8
    mem[addr + 4] = g_Tracks.GetMaxTrackLength() & 0xff;
    mem[addr + 5] = m_mainSpeed & 0xff;
    mem[addr + 6] = m_instrumentSpeed;			// 1-4 player calls per frame
    mem[addr + 7] = RMTFormatVersion::V1;			// RMT format version number

    // Note:
    // When saving in RMT format ALL non-empty tracks and non-empty instruments will be stored
    // In other formats only the USED tracks and USED instruments will be stored

    MarkTF_USED(trackSavedFlags);			// Mark all tracks as used
    if (iotype == SongIOType::RMT)
    {
        MarkTF_NOEMPTY(trackSavedFlags);	// In addition to the used ones, all non-empty tracks are added to the RMT, all non-empty tracks
    }

    // Mark all used instruments in the tracks that will be saved
    for (i = 0; i < TRACKSNUM; i++)
    {
        if (trackSavedFlags[i] > 0)
        {
            tr = g_Tracks.GetTrack(i);
            for (j = 0; j < tr->len; j++)
            {
                if (g_Tracks.IsValidInstrument(tr->instr[j])) instrumentSavedFlags[tr->instr[j]] = IF_USED;
            }
        }
    }

    if (iotype == SongIOType::RMT)
    {
        // In addition to the instruments used in the tracks that are in the song, all non-empty instruments are stored in the RMT
        for (i = 0; i < INSTRSNUM; i++)
        {
            if (g_Instruments.CalculateNotEmpty(i)) instrumentSavedFlags[i] |= IF_NOEMPTY;
        }
    }

    //---
    // Find how many tracks and instruments to save
    int numTracks = 0;
    for (i = TRACKSNUM - 1; i >= 0; i--)
    {
        if (trackSavedFlags[i] > 0) { numTracks = i + 1; break; }
    }

    int numInstruments = 0;
    for (i = INSTRSNUM - 1; i >= 0; i--)
    {
        if (instrumentSavedFlags[i] > 0) { numInstruments = i + 1; break; }
    }

    // Calculate the offsets for instruments, tracks (lo & hi) and song lines
    // RMT header is 16 bytes, so instrument ptrs start there
    // Each instrument ptr is 2 bytes, and the track pointers are 2 bytes
    // but split into low and high storage areas
    int ptrInstruments = addr + 16;
    int ptrTracksLoBytes = ptrInstruments + numInstruments * 2;
    int ptrTracksHiBytes = ptrTracksLoBytes + numTracks;
    int ptrInstrumentData = ptrTracksHiBytes + numTracks; // behind the track byte table

    // Saves instrument data and writes their beginnings to the table
    for (i = 0; i < numInstruments; i++)
    {
        if (instrumentSavedFlags[i])
        {
            // Create instrument data
            int thisInstrumentLength = g_Instruments.InstrToAta(i, mem + ptrInstrumentData, ATARI_MAX_INSTR_LENGTH);

            // Save where the instrument data is to be found
            mem[ptrInstruments + i * 2] = ptrInstrumentData & 0xff;	// lo byte
            mem[ptrInstruments + i * 2 + 1] = ptrInstrumentData >> 8;	// hi byte

            // Move where the next instruments data is to be saved
            ptrInstrumentData += thisInstrumentLength;
        }
        else
        {
            // Oi, nothing to save here, just emit 0
            // This happens if there are unused instruments between the used ones.
            // Best to rearrange the instruments to be one continous block
            mem[ptrInstruments + i * 2] = mem[ptrInstruments + i * 2 + 1] = 0;
        }
    }

    // Just after of the instrument data we start with the track data
    int ptrTrackData = ptrInstrumentData;

    // Saves track data and write their beginnings to the table
    for (i = 0; i < numTracks; i++)
    {
        if (trackSavedFlags[i])
        {
            // Create the track data
            int thisTrackLength = g_Tracks.TrackToAta(i, mem + ptrTrackData, ATARI_MAX_TRACK_LENGTH);

            // Check that the track data is valid
            if (thisTrackLength < 1)
            {
                // Track cannot be saved to RMT
                CString msg;
                msg.Format("Fatal error in track %02X.\n\nThis track contains too many events (notes and speed commands),\nthat's why it can't be coded to RMT internal code format.", i);
                MessageBox(g_hwnd, msg, "Internal format problem.", MB_ICONERROR);
                return -1;
            }

            // Save where the track data is to be found
            mem[ptrTracksLoBytes + i] = ptrTrackData & 0xff;	// lo byte
            mem[ptrTracksHiBytes + i] = ptrTrackData >> 8;		// hi byte

            // Move where the next track's data is to be saved
            ptrTrackData += thisTrackLength;
        }
        else
        {
            // Oi, nothing to save here, just emit 0
            mem[ptrTracksLoBytes + i] = mem[ptrTracksHiBytes + i] = 0;
        }
    }

    // Just after the track data we store the song lines
    int ptrSongData = ptrTrackData;

    int thisSongLength = SongToAta(mem + ptrSongData, 0x10000 - ptrSongData, ptrSongData);

    int endOfModule = ptrSongData + thisSongLength;

    // Writes computed pointers to the header
    mem[addr + 8] = ptrInstruments & 0xff;		// lo byte pointer to instrument table
    mem[addr + 9] = ptrInstruments >> 8;		// hi byte
    //
    mem[addr + 10] = ptrTracksLoBytes & 0xff;	// lo byte pointer to low bytes of track data table
    mem[addr + 11] = ptrTracksLoBytes >> 8;		// hi byte
    mem[addr + 12] = ptrTracksHiBytes & 0xff;	// lo byte pointer to high bytes of track data table
    mem[addr + 13] = ptrTracksHiBytes >> 8;		// hi byte
    //
    mem[addr + 14] = ptrSongData & 0xff;		// lo byte pointer to song data (arrangements of tracks)
    mem[addr + 15] = ptrSongData >> 8;			// hi byte

    // Return the address of the first byte past the last one used
    return endOfModule;
}

int CSong::DecodeModule(unsigned char* mem, int fromAddr, int endAddr, BYTE* instrumentLoadedFlags, BYTE* trackLoadedFlags)
{
    int addr = fromAddr;

    memset(instrumentLoadedFlags, 0, INSTRSNUM);
    memset(trackLoadedFlags, 0, TRACKSNUM);

    unsigned char data;
    int i, j;

    BOOL loadState;

    // Check that the header starts with "RMT"
    if (strncmp((char*)(mem + addr), "RMT", 3) != 0) return 0; //there is no RMT

    // 4th byte: # of channels (4 or 8)
    data = mem[addr + 3];
    if (data != '4' && data != '8') return 0;	//it is not RMT4 or RMT8
    SetTracks(data & 0x0F); // Store how many channels this module uses

    // 5th byte: track length
    data = mem[addr + 4];
    g_Tracks.SetMaxTrackLength((data > 0) ? data : 256);	//0 => 256

    // 6th byte: song speed
    data = mem[addr + 5];
    m_mainSpeed = data;
    if (data < 1) return 0;						// there can be no zero speed

    // 7th byte: Instrument speed
    data = mem[addr + 6];
    if (data < 1 || data > 8) return 0;			// Instrument speed is less than 1 or greater than 8 (note: should be max 4, but allows up to 8 and will only display a warning)
    m_instrumentSpeed = data;

    // 8th byte: RMT format version nr.
    int version = mem[addr + 7];
    // TODO: Make "case" and support V2
    if (version > RMTFormatVersion::V1)
    {
        // the byte version is above the currently supported one
        return 0;
    }

    // Now g_Tracks.m_maxTrackLength is set to the value in the RMT header, 
    // so re-initialize the tracks to set all tracks to this new length
    g_Tracks.InitTracks();

    // Get various pointers
    int ptrInstruments = mem[addr + 8] + (mem[addr + 9] << 8);			// Get ptr to the instuments
    int ptrTracksLow = mem[addr + 10] + (mem[addr + 11] << 8);			// Get ptr to tracks table low
    int ptrTracksHigh = mem[addr + 12] + (mem[addr + 13] << 8);			// Get ptr to tracks table high
    int ptrSong = mem[addr + 14] + (mem[addr + 15] << 8);				// Get ptr to track list (the song)

    // Calculate how long each of the sections are
    int numInstruments = (ptrTracksLow - ptrInstruments) / 2;
    int numTracks = (ptrTracksHigh - ptrTracksLow);
    int lengthSong = endAddr - ptrSong;

    // Decoding of individual instruments
    for (int instrumentNr = 0; instrumentNr < numInstruments; instrumentNr++)
    {
        // Get the ptr to an instruments configuration data
        int ptrOneInstrument = mem[ptrInstruments + instrumentNr * 2] + (mem[ptrInstruments + instrumentNr * 2 + 1] << 8);

        // Skip over empty instruments
        if (ptrOneInstrument == 0) continue; // Empty instruments have a NULL ptr

        // Depending on the file version load the instrument data into g_Instruments
        if (version == 0)
            loadState = g_Instruments.AtaV0ToInstr(mem + ptrOneInstrument, instrumentNr);
        else
            loadState = g_Instruments.AtaToInstr(mem + ptrOneInstrument, instrumentNr);

        g_Instruments.Update(instrumentNr);	//writes to Atari ram

        if (!loadState) return 0; // some problem with the instrument => END

        // Mark the instrument as loaded
        instrumentLoadedFlags[instrumentNr] = 1;
    }

    // Track data ptrs are split over two tables.  Low and high bytes, each indexed by the track number
    // Decoding individual tracks
    for (i = 0; i < numTracks; i++)
    {
        int trackNr = i;
        int ptrTrack = mem[ptrTracksLow + i] + (mem[ptrTracksHigh + i] << 8);
        if (ptrTrack == 0) continue; // Omitted tracks have pointer of 0

        // Identify the end of the track by the starting address of the next track,
        // and at the end by the starting address of the song data that follows the data of the last track
        int ptrTrackEnd = 0;
        for (j = i; j < numTracks; j++)
        {
            ptrTrackEnd = (j + 1 == numTracks) ? ptrSong : mem[ptrTracksLow + j + 1] + (mem[ptrTracksHigh + j + 1] << 8);
            if (ptrTrackEnd != 0) break;
            i++;	//continue from the next and skip the omitted one
        }

        int trackLength = ptrTrackEnd - ptrTrack;
        if (!g_Tracks.AtaToTrack(mem + ptrTrack, trackLength, trackNr)) return 0; //some problem with the track => END

        // Mark the track as loaded
        trackLoadedFlags[trackNr] = 1;
    }

    // Decoded song
    if (!AtaToSong(mem + ptrSong, lengthSong, ptrSong)) return 0; //some problem with the song => END

    return version;
}

void CSong::InstrInfo(int instr, TInstrInfo* iinfo, int instrto)
{
    if (!g_Instruments.IsValidInstrument(instr)) return;

    TTrack* at;
    int i, j, ain;
    int inttrack;
    int intrack[TRACKSNUM];
    int noftrack = 0;
    int globallytimes = 0;
    int withnote[CNotes::NOTESNUM];
    for (i = 0; i < CNotes::NOTESNUM; i++) withnote[i] = 0;
    int minnote = CNotes::NOTESNUM, maxnote = -1;
    int minvol = 16, maxvol = -1;
    int infrom = INSTRSNUM, into = -1;

    if (instrto < instr) instrto = instr;

    for (i = 0; i < TRACKSNUM; i++)
    {
        inttrack = 0;
        at = g_Tracks.GetTrack(i);
        ain = -1;
        for (j = 0; j < at->len; j++)
        {
            if (at->instr[j] >= 0) ain = at->instr[j];
            if (ain >= instr && ain <= instrto)
            {
                inttrack = 1;
                if (ain > into) into = ain;
                if (ain < infrom) infrom = ain;
                int note = at->note[j];
                if (note >= 0 && note < CNotes::NOTESNUM)
                {
                    globallytimes++; //some note with this instrument => started
                    withnote[note]++;
                    if (note > maxnote) maxnote = note;
                    if (note < minnote) minnote = note;
                }
                int vol = at->volume[j];
                if (vol >= 0 && vol <= 15)
                {
                    if (vol > maxvol) maxvol = vol;
                    if (vol < minvol) minvol = vol;
                }
            }
        }
        intrack[i] = inttrack;
        if (inttrack) noftrack++;
    }

    if (iinfo)
    {	//iinfo != NULL => set values
        iinfo->count = globallytimes;
        iinfo->usedintracks = noftrack;
        iinfo->instrfrom = infrom;
        iinfo->instrto = into;
        iinfo->minnote = minnote;
        iinfo->maxnote = maxnote;
        iinfo->minvol = minvol;
        iinfo->maxvol = maxvol;
    }
    else
    {	//iinfo == NULL => shows dialog
        CString s, s2;
        s.Format("Instrument: %02X\nName: %s\nUsed in %i tracks, globally %i times.\nFrom note: %s\nTo note: %s\nMin volume: %X\nMax volume: %X",
            instr, g_Instruments.GetName(instr), noftrack, globallytimes,
            minnote < CNotes::NOTESNUM ? CNotes::GetNote(minnote) : "-",
            maxnote >= 0 ? CNotes::GetNote(maxnote) : "-",
            minvol <= 15 ? minvol : 0,
            maxvol >= 0 ? maxvol : 0);

        if (globallytimes > 0)
        {
            s += "\n\nNote listing:\n";
            int lc = 0;
            for (i = 0; i < CNotes::NOTESNUM; i++)
            {
                if (withnote[i])
                {
                    s += CNotes::GetNote(i);
                    lc++;
                    if (lc < 12)
                        s += " ";
                    else
                    {
                        s += "\n"; lc = 0;
                    }
                }
            }
            s += "\n\nTrack listing:\n";
            lc = 0;
            for (i = 0; i < TRACKSNUM; i++)
            {
                if (intrack[i])
                {
                    s2.Format("%02X", i);
                    s += s2;
                    lc++;
                    if (lc < 16)
                        s += " ";
                    else
                    {
                        s += "\n"; lc = 0;
                    }
                }
            }
        }
        MessageBox(g_hwnd, (LPCTSTR)s, "Instrument info", MB_ICONINFORMATION);
    }
}


void CSong::SetNTSC(const BOOL ntsc) {
    if (ntsc != m_ntsc) {
        m_ntsc = ntsc;
        ReInitSound();
    }
}

#define RMWMAINPARAMSCOUNT		31		//
#define DEFINE_MAINPARAMS int* mainparams[RMWMAINPARAMSCOUNT]= {\
	&g_tracks4_8,												\
	(int*)&m_speed,(int*)&m_mainSpeed,(int*)&m_instrumentSpeed,	\
	(int*)&m_songactiveline,(int*)&m_songplayline,				\
	(int*)&m_trackactiveline,(int*)&m_trackplayline,			\
	(int*)&g_activepart,(int*)&g_active_ti,						\
	(int*)&g_prove,(int*)&g_respectvolume,						\
	&g_trackLinePrimaryHighlight,								\
	&g_tracklinealtnumbering,									\
	&g_displayflatnotes,										\
	&g_usegermannotation,										\
	&g_cursoractview,											\
	(int*)&g_keyboard_layout,											\
	&g_keyboard_escresetatarisound,								\
	&g_keyboard_swapenter,										\
	&g_keyboard_playautofollow,									\
	&g_keyboard_updowncontinue,									\
	&g_keyboard_RememberOctavesAndVolumes,						\
	&g_keyboard_escresetatarisound,								\
	&m_trackactivecol,&m_trackactivecur,						\
	&m_activeinstr,&m_volume,&m_octave,							\
	(int*)&m_infoact,&m_songnamecur									\
}

bool CSong::SaveRMW(std::ostream& ou)
{
    CString version = RMT_VERSION_STRING;
    ou << (unsigned char*)(LPCSTR)version << std::endl;
    //
    ou.write((char*)m_songname, sizeof(m_songname));
    //
    DEFINE_MAINPARAMS;

    int p = RMWMAINPARAMSCOUNT;			// Number of stored parameters
    ou.write((char*)&p, sizeof(p));		// Write the number of main parameters
    for (int i = 0; i < p; i++)
        ou.write((char*)mainparams[i], sizeof(mainparams[0]));

    // Write a complete song and songgo
    ou.write((char*)m_song, sizeof(m_song));
    ou.write((char*)m_songgo, sizeof(m_songgo));

    g_Instruments.SaveAll(ou, InstrumentIOType::RMW);
    g_Tracks.SaveAll(ou, SongIOType::RMW);

    return true;
}

bool CSong::SaveTxt(std::ostream& ou)
{
    CString s, nambf;
    char bf[16];
    nambf = m_songname;
    nambf.TrimRight();
    s.Format("[MODULE]\nRMT: %X\nNAME: %s\nMAXTRACKLEN: %02X\nMAINSPEED: %02X\nINSTRSPEED: %X\nVERSION: %02X\n", g_tracks4_8, (LPCTSTR)nambf, g_Tracks.GetMaxTrackLength(), m_mainSpeed, m_instrumentSpeed, RMTFormatVersion::V1);
    ou << s << "\n"; //gap
    ou << "[SONG]\n";
    int i, j;
    // Looking for the length of the song
    int songLength = -1;
    for (i = 0; i < SONGLEN; i++)
    {
        if (m_songgo[i] >= 0) { songLength = i; continue; }
        for (j = 0; j < g_tracks4_8; j++)
        {
            if (m_song[i][j] >= 0 && m_song[i][j] < TRACKSNUM)
            {
                songLength = i;
                break;
            }
        }
    }

    // Write the song
    for (i = 0; i <= songLength; i++)
    {
        if (m_songgo[i] >= 0)
        {
            s.Format("Go to line %02X\n", m_songgo[i]);
            ou << s;
            continue;
        }
        for (j = 0; j < g_tracks4_8; j++)
        {
            int t = m_song[i][j];
            if (t >= 0 && t < TRACKSNUM)
            {
                bf[0] = CharH4(t);
                bf[1] = CharL4(t);
            }
            else
            {
                bf[0] = bf[1] = '-';
            }
            bf[2] = 0;
            ou << bf;
            if (j + 1 == g_tracks4_8)
                ou << "\n";			//for the last end of the line
            else
                ou << " ";			//between them
        }
    }

    ou << "\n"; // gap

    // Now save the instruments and tracks to the output
    g_Instruments.SaveAll(ou, InstrumentIOType::TXT);
    g_Tracks.SaveAll(ou, SongIOType::TXT);

    return true;
}

bool CSong::LoadRMT(std::istream& in)
{
    byte mem[RAM_SIZE]{};
    WORD fromAddr, toAddr;
    WORD bto_mainblock;

    BYTE instrumentLoadedFlags[INSTRSNUM];
    BYTE trackLoadedFlags[TRACKSNUM];

    int len, i, idx, k;
    int loadResult;

    // RMT header+song data is the first main block of an RMT song
    // There has to be 1 binary block with the header, song, instrument and track data
    // Optional block with instrument and song name information
    len = CAtariIO::LoadBinaryBlock(in, mem, fromAddr, toAddr);

    if (len > 0)
    {
        loadResult = DecodeModule(mem, fromAddr, toAddr + 1, instrumentLoadedFlags, trackLoadedFlags);
        if (loadResult == 0)
        {
            MessageBox(g_hwnd, "Bad RMT data format or old tracker version.", "Open error", MB_ICONERROR);
            return false;
        }
        // The main block of the module is OK => take its boot address
        g_rmtstripped_adr_module = fromAddr;
        bto_mainblock = toAddr;
    }
    else
    {
        MessageBox(g_hwnd, "Corrupted file or unsupported format version.", "Open error", MB_ICONERROR);
        return false;	// Did not retrieve any data in the first block
    }

    // RMT - now read the second block with names
    len = CAtariIO::LoadBinaryBlock(in, mem, fromAddr, toAddr);
    if (len < 1)
    {
        CString msg;
        msg.Format("This file appears to be a stripped RMT module.\nThe song and instruments names are missing.\n\nMemory addresses: $%04X - $%04X.", g_rmtstripped_adr_module, bto_mainblock);
        MessageBox(g_hwnd, (LPCTSTR)msg, "Info", MB_ICONINFORMATION);
        return true;
    }

    char ch;
    // Parse the song name (until we hit the terminating zero)
    for (idx = 0; idx < SONG_NAME_MAX_LEN && (ch = mem[fromAddr + idx]); idx++)
        m_songname[idx] = ch;

    for (k = idx; k < SONG_NAME_MAX_LEN; k++) m_songname[k] = ' '; // fill in the gaps

    int addrInstrumentNames = fromAddr + idx + 1; // +1 that's the zero behind the name
    for (i = 0; i < INSTRSNUM; i++)
    {
        // Check if this instrument has been loaded
        if (instrumentLoadedFlags[i])
        {
            // Yes its loaded, parse its name
            for (idx = 0; idx < INSTRUMENT_NAME_MAX_LEN && (ch = mem[addrInstrumentNames + idx]); idx++)
                //g_Instruments.m_instr[i].name[idx] = ch;
                g_Instruments.GetName(i)[idx] = ch;

            for (k = idx; k < INSTRUMENT_NAME_MAX_LEN; k++) //g_Instruments.m_instr[i].name[k] = ' '; //fill in the gaps
                g_Instruments.GetName(i)[k] = ' '; // Fill in the gaps

            // Move to source of the next instrument's name
            addrInstrumentNames += idx + 1; //+1 is zero behind the name
        }
    }

    return true;
}
