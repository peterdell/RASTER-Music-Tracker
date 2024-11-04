#include "stdafx.h"
#include "ASMFileExporter.h"


int CASMFileExporter::BuildInstrumentData(
    CString& strCode,
    CString strInstrumentsLabel,
    unsigned char* buf,
    int from,
    int to,
    int* info,
    int assemblerFormat
)
{
    strCode = "\n\n; Instrument data\n";

    int sizeInstruments = 0;
    CString str;
    if (strInstrumentsLabel.IsEmpty() == false)
    {
        // Make the instruments relocatable
        str.Format(assemblerFormat == ASSEMBLER_FORMAT_ATASM ? "* = %s\n" : "org %s\n", (LPCTSTR)strInstrumentsLabel);
        strCode += str;
    }

    for (int i = from, l = 0; i < to; i++, l++)
    {
        if (info[i])
        {
            str.Format("\n?Instrument_%d", info[i] - 1);
            strCode += str;
            info[i] = 0;
            l = 0;
        }
        if (l % 16 == 0)
            strCode += "\n    {{byte}} ";
        else
            strCode += ",";
        str.Format("$%02x", buf[i]);
        strCode += str;

        ++sizeInstruments;
    }

    return sizeInstruments;
}

int CASMFileExporter::BuildTracksData(
    CString& strCode,
    CString strTracksLabel,
    unsigned char* buf,
    int from,
    int to,
    int* track_pos,
    int assemblerFormat
)
{
    strCode = "\n\n; Track data";

    int sizeTrack = 0;
    CString str;

    if (strTracksLabel.IsEmpty() == false)
    {
        // Make the track data relocatable
        str.Format(assemblerFormat == ASSEMBLER_FORMAT_ATASM ? "\n* = %s\n" : "\norg %s\n", (LPCTSTR)strTracksLabel);
    }
    strCode += str;
    for (int i = from, l = 0; i < to; i++, l++)
    {
        if (track_pos[i])
        {
            str.Format("\n?Track_%02x", track_pos[i] - 1);
            strCode += str;
            track_pos[i] = 0;
            l = 0;
        }
        if (l % 16 == 0)
            strCode += "\n    {{byte}} ";
        else
            strCode += ",";
        str.Format("$%02x", buf[i]);
        strCode += str;

        ++sizeTrack;
    }
    for (int i = 0; i < 65536; i++)
    {
        if (track_pos[i] != 0)
            return 0;
    }
    return sizeTrack;
}

int CASMFileExporter::BuildSongData(
    CString& strCode,
    CString strSongLinesLabel,
    unsigned char* buf,
    int offsetSong,
    int len,
    int start,
    int numTracks,
    int assemblerFormat
)
{
    strCode = "\n\n; Song data\n";

    int sizeSongLines = 0;
    CString str;
    if (strSongLinesLabel.IsEmpty() == false)
    {
        str.Format(assemblerFormat == ASSEMBLER_FORMAT_ATASM ? "\n* = %s\n" : "\norg %s\n", (LPCTSTR)strSongLinesLabel);
    }
    strCode += str;

    strCode += "?SongData";
    int jmp = 0, l = 0;
    for (int i = offsetSong; i < len; i++, l++)
    {
        if (jmp == -2)
        {
            jmp = 0x10000 + buf[i];
            continue;
        }
        else if (jmp > 0)
        {
            jmp = (0xFFFF & (jmp | (buf[i] << 8))) - start;
            if (0 == ((jmp - offsetSong) % numTracks) && jmp >= offsetSong && jmp < len)
            {
                int lnum = (jmp - offsetSong) / numTracks;
                str.Format(assemblerFormat == ASSEMBLER_FORMAT_ATASM ? ",<?line_%02x,>?line_%02x" : ",l(__line_%02x),h(__line_%02x)", lnum, lnum);
                strCode += str;
            }
            else
            {
                str.Format("; ERROR malformed file(song jump bad $ % 04x[% x:% x])\n", jmp, offsetSong, len);
                strCode += str;
                str.Format(assemblerFormat == ASSEMBLER_FORMAT_ATASM ? ",<($%x+?SongData),>($%x+?SongData)" : ",l($%x+__SongData),h($%x+__SongData)", jmp, jmp);
                strCode += str;
            }
            jmp = 0;
            // Allows terminating song on last JUMP
            if (i + 1 == len && numTracks == 8)
                l += 4;

            continue;
        }
        else if (jmp == -1)
            jmp = -2;

        if (l % numTracks == 0)
        {
            str.Format("\n?Line_%02x  {{byte}} ", l / numTracks);
            strCode += str;
        }
        else
            strCode += ",";
        str.Format("$%02x", buf[i]);
        strCode += str;

        if (buf[i] == 0xfe)
        {
            if ((l % numTracks) != 0)
                return 0;
            else
                jmp = -1;
        }

        ++sizeSongLines;
    }
    strCode += "\n";

    return sizeSongLines;
}