#include "AtariIO.h"
#include "Instruments.h"
#include "RmtExporter.h"

// CRmtExporter::ExportAsRMT() has no dialog/hazard of its own - only
// CAtariIO::SaveBinaryBlock() (AtariIO.cpp, confirmed zero global
// references) and CSong::GetName()/CInstruments::GetName() (both already
// safe). Kept separate from RmtExporter.cpp's ExportAsStrippedRMT(), which
// instantiates a real MFC dialog - same pattern as Song.cpp/SongEditing.cpp
// etc.

extern CInstruments g_Instruments;

/// <summary>
/// Export the song data as an RMT module with full instrument and song names.
/// Writes two data blocks.
/// </summary>
/// <param name="ou">Output stream</param>
/// <param name="exportDesc">Data about the packed RMT module</param>
/// <returns>true = saved ok</returns>
bool CRmtExporter::ExportAsRMT(CSong& song, std::ostream& ou, TExportDescription* exportDesc)
{
    // Save the 1st RMT module block: Song, Tracks & Instruments
    CAtariIO::SaveBinaryBlock(ou, exportDesc->mem, exportDesc->targetAddrOfModule, exportDesc->firstByteAfterModule - 1, TRUE);

    // Save the 2nd RMT module block: Song and Instrument Names
    // The individual names are truncated by spaces and terminated by a zero
    // Song name (0 terminated)
    CString name;
    int addrOfSongName = exportDesc->firstByteAfterModule;
    name = song.GetName();
    int len = name.GetLength() + 1;	// including 0 after the string
    strncpy((char*)(exportDesc->mem + addrOfSongName), (LPCSTR)name, len);

    // Each saved instrument's name is written to the second module
    int addrInstrumentNames = addrOfSongName + len;
    for (int i = 0; i < INSTRSNUM; i++)
    {
        if (exportDesc->instrumentSavedFlags[i])
        {
            name = g_Instruments.GetName(i);
            name.TrimRight();
            len = name.GetLength() + 1;	//including 0 after the string
            strncpy((char*)(exportDesc->mem + addrInstrumentNames), name, len);
            addrInstrumentNames += len;
        }
    }
    // and now, save the 2nd block
    CAtariIO::SaveBinaryBlock(ou, exportDesc->mem, addrOfSongName, addrInstrumentNames - 1, FALSE);

    return true;
}
