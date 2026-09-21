#include "StdAfx.h"

#include "Song.h"

#include "Clipboard.h"

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
// SaveRMW()/SaveTxt() in IO_Song.cpp also only touch g_Tracks/g_Instruments/
// g_tracks4_8 directly, but were deliberately left out of this slice: they
// call CString::LoadString(IDS_RMT_VERSION), an MFC resource-string load
// that needs the app's compiled resources and would be unreliable in a
// console test binary.

extern CTrackClipboard g_TrackClipboard;
extern CInstruments g_Instruments;
extern int g_tracks4_8;

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
