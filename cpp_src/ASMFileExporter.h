#pragma once

#include "Song.h"
#include "ASMFile.h"

class CASMFileExporter
{

public:

    /// <summary>
    /// Export the RMT module as assembler
    /// </summary>
    /// <param name="ou">Output stream</param>
    /// <param name="exportStrippedDesc">Data about the packed RMT module</param>
    /// <returns>true if the save went OK</returns>
    static bool ExportAsAsm(const CSong& song, std::ofstream& ou, TExportDescription* exportStrippedDesc);

    /// <summary>
    /// Export the RMT data for the RMTPlayer assembler player.
    /// All data is exported: instruments, tracks, song lines
    /// </summary>
    /// <param name="ou">Output stream</param>
    /// <param name="exportDescStripped">Data about the packed RMT module</param>
    /// <returns>true if the save went OK</returns>
    static bool ExportAsRelocatableAsmForRmtPlayer(CSong& song, std::ofstream& ou, TExportDescription* exportStrippedDesc);


    // TODO: Used by export dialog
    void static ComposeRMTFEATstring(const CSong& song, CString& dest, const char* filename, BYTE* instrumentSavedFlags, BYTE* trackSavedFlags, BOOL sfx, BOOL gvf, BOOL nos, int assemblerFormat);

    static BOOL BuildRelocatableAsm(
        const CSong& song, CString& dest,
        TExportDescription* exportDesc,
        CString strAsmStartLabel,
        CString strTracksLabel,
        CString strSongLinesLabel,
        CString strInstrumentsLabel,
        int assemblerFormat,
        BOOL sfx,
        BOOL gvf,
        BOOL nos,
        bool bWantSizeInfoOnly);
private:

};

