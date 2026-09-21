#include "StdAfx.h"

#include "Undo.h"

#include "Tracks.h"

#include "Global.h"

// These CTracks methods are the only ones that record undo history
// (g_Undo) and consult the global "respect volume" setting
// (g_respectvolume), so they're kept separate from the rest of Tracks.cpp
// (which has no dependency on Global.h) - see Tracks.cpp for the plain
// track-data methods.

BOOL CTracks::DelNoteInstrVolSpeed(int noteinstrvolspeed, TrackNumber track, int line)
{
    TTrack* tr = GetTrack(track);
    if (!tr) return 0;

    g_Undo.ChangeTrack(track, line, UETYPE_NOTEINSTRVOLSPEED);
    g_Undo.Separator();

    // If the line on track is within boundaries, continue
    if (line >= 0 && line < tr->len)
    {
        if (noteinstrvolspeed & 1) tr->note[line] = -1;
        if (noteinstrvolspeed & 2) tr->instr[line] = -1;
        if (noteinstrvolspeed & 4) tr->volume[line] = -1;
        if (noteinstrvolspeed & 8) tr->speed[line] = -1;
        return 1;
    }

    // Else, nothing will be deleted
    return 0;
}

BOOL CTracks::SetNoteInstrVol(int note, int instr, int vol, TrackNumber track, int line)
{
    TTrack* tr = GetTrack(track);
    if (!tr) return 0;

    g_Undo.ChangeTrack(track, line, UETYPE_NOTEINSTRVOL);
    g_Undo.Separator();

    // If the line on track is within boundaries, continue
    if (line >= 0 && line < tr->len)
    {
        if (note < 0) instr = vol = -1;

        if (!g_respectvolume || (g_respectvolume && (vol < 0 || tr->volume[line] < 0)))
            tr->volume[line] = vol;

        tr->note[line] = note;
        tr->instr[line] = instr;
        return 1;
    }

    // Else, nothing will be set
    return 0;
}

BOOL CTracks::SetInstr(int instr, TrackNumber track, int line)
{
    TTrack* tr = GetTrack(track);
    if (!tr) return 0;

    g_Undo.ChangeTrack(track, line, UETYPE_NOTEINSTRVOL);
    //g_Undo.Separator();	// Why no undo separator?

    // If the line on track is within boundaries, continue
    if (line >= 0 && line < tr->len)
    {
        tr->instr[line] = instr;
        return 1;
    }

    // Else, nothing will be set
    return 0;
}

BOOL CTracks::SetVol(int vol, TrackNumber track, int line)
{
    TTrack* tr = GetTrack(track);
    if (!tr) return 0;

    g_Undo.ChangeTrack(track, line, UETYPE_NOTEINSTRVOL);
    //g_Undo.Separator();	// Why no undo separator?

    // If the line on track is within boundaries, continue
    if (line >= 0 && line < tr->len)
    {
        tr->volume[line] = vol;
        return 1;
    }

    // Else, nothing will be set
    return 0;
}

BOOL CTracks::SetSpeed(int speed, TrackNumber track, int line)
{
    TTrack* tr = GetTrack(track);
    if (!tr) return 0;

    g_Undo.ChangeTrack(track, line, UETYPE_SPEED);
    //g_Undo.Separator();	// Why no undo separator?

    // If the line on track is within boundaries, continue
    if (line >= 0 && line < tr->len)
    {
        tr->speed[line] = speed;
        return 1;
    }

    // Else, nothing will be set
    return 0;
}

BOOL CTracks::SetEnd(TrackNumber track, int line)
{
    TTrack* tr = GetTrack(track);
    if (!tr) return 0;

    g_Undo.ChangeTrack(track, line, UETYPE_LENGO, 1);
    //g_Undo.Separator();	// Why no undo separator?

    // Set the track length to
    tr->len = (line > 0 && tr->len != line) ? line : m_maxTrackLength;
    if (tr->go >= tr->len) tr->go = -1;
    return 1;
}

BOOL CTracks::SetGo(TrackNumber track, int line)
{
    TTrack* tr = GetTrack(track);
    if (!tr) return 0;
    if (line >= tr->len) return 0;
    g_Undo.ChangeTrack(track, line, UETYPE_LENGO, 1);
    tr->go = tr->go == line ? -1 : line;
    return 1;
}
