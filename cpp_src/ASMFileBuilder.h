#pragma once
#include "General.h"

class CASMFileBuilder
{

public:
    static int BuildInstrumentData(
        CString& strCode,
        CString strInstrumentsLabel,
        unsigned char* buf,
        int from,
        int to,
        int* info,
        AssemblerFormat assemblerFormat
    );

    static int BuildTracksData(
        CString& strCode,
        CString strTracksLabel,
        unsigned char* buf,
        int from,
        int to,
        int* track_pos,
        AssemblerFormat assemblerFormat);

    static int BuildSongData(
        CString& strCode,
        CString strSongLinesLabel,
        unsigned char* buf,
        int offsetSong,
        int len,
        int start,
        int numTracks,
        AssemblerFormat assemblerFormat
    );
};

