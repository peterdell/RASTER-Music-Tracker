#include "AtariIO.h"
#include "exportdlgs.h"
#include "Instruments.h"
#include "RmtExporter.h"


extern CInstruments g_Instruments;
extern AssemblerFormat g_AsmFormat;

extern WORD g_rmtstripped_adr_module;	//address for export RMT stripped file
extern BOOL g_rmtstripped_sfx;			//sfx offshoot RMT stripped file
extern BOOL g_rmtstripped_gvf;			//gvs GlobalVolumeFade for feat
extern BOOL g_rmtstripped_nos;			//nos NoStartingSongline for feat


/// <summary>
/// Export the song data as an RMT module with full instrument and song names.
/// Writes two data blocks.
/// </summary>
/// <param name="ou">Output stream</param>
/// <param name="exportDesc">Data about the packed RMT module</param>
/// <returns>true = saved ok</returns>
bool CRmtExporter::ExportAsRMT(CSong& song, std::ofstream& ou, TExportDescription* exportDesc)
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


/// <summary>
/// Export the song data as assembler source code.
/// </summary>
/// <param name="ou">Output stream</param>
/// <param name="exportStrippedDesc">Data about the packed RMT module</param>
/// <param name="filename"></param>
/// <returns>true is the save went ok</returns>
bool CRmtExporter::ExportAsStrippedRMT(CSong& song, std::ofstream& ou, TExportDescription* exportStrippedDesc, LPCTSTR filename)
{
    TExportDescription exportTempDescription;
    memset(&exportTempDescription, 0, sizeof(TExportDescription));
    exportTempDescription.targetAddrOfModule = 0x4000;		// Standard RMT modules are set to start @ $4000

    // Create a variant for SFX (ie. including unused instruments and tracks)
    exportTempDescription.firstByteAfterModule = song.MakeModule(exportTempDescription.mem, exportTempDescription.targetAddrOfModule, SongIOType::RMT, exportTempDescription.instrumentSavedFlags, exportTempDescription.trackSavedFlags);
    if (exportTempDescription.firstByteAfterModule < 0) return false;	// if the module could not be created

    // Show the dialog to control the stripped output parameters
    CExportStrippedRMTDialog dlg;
    // Common data
    dlg.m_exportAddr = g_rmtstripped_adr_module;	//global, so that it remains the same on repeated export
    dlg.m_globalVolumeFade = g_rmtstripped_gvf;
    dlg.m_noStartingSongLine = g_rmtstripped_nos;
    dlg.m_song = &song;
    dlg.m_filename = (char*)filename;
    dlg.m_sfxSupport = g_rmtstripped_sfx;
    dlg.m_assemblerFormat = g_AsmFormat;

    // Stripped RMT data
    dlg.m_moduleLengthForStrippedRMT = exportStrippedDesc->firstByteAfterModule - exportStrippedDesc->targetAddrOfModule;
    dlg.m_savedInstrFlagsForStrippedRMT = exportStrippedDesc->instrumentSavedFlags;
    dlg.m_savedTracksFlagsForStrippedRMT = exportStrippedDesc->trackSavedFlags;

    // Full RMT/SFX data
    dlg.m_moduleLengthForSFX = exportTempDescription.firstByteAfterModule - exportTempDescription.targetAddrOfModule;
    dlg.m_savedInstrFlagsForSFX = exportTempDescription.instrumentSavedFlags;
    dlg.m_savedTracksFlagsForSFX = exportTempDescription.trackSavedFlags;

    // Show the dialog and get the stripped RMT configuration parameters
    if (dlg.DoModal() != IDOK) return false;

    // Save the configurations for later reuse
    int targetAddrOfModule = dlg.m_exportAddr;

    g_rmtstripped_adr_module = dlg.m_exportAddr;
    g_rmtstripped_sfx = dlg.m_sfxSupport;
    g_rmtstripped_gvf = dlg.m_globalVolumeFade;
    g_rmtstripped_nos = dlg.m_noStartingSongLine;
    g_AsmFormat = dlg.m_assemblerFormat;

    // Now we can regenerate the RMT module with the selected configuration
    // - known start address
    // - know if we want to strip out unused instruments and tracks => RMTSTRIPPED : RMT
    memset(&exportTempDescription, 0, sizeof(TExportDescription));			// Clear it all again
    exportTempDescription.targetAddrOfModule = g_rmtstripped_adr_module;	// Standard RMT modules are set to start @ $4000

    exportTempDescription.firstByteAfterModule =
        song.MakeModule(
            exportTempDescription.mem,
            exportTempDescription.targetAddrOfModule,
            g_rmtstripped_sfx ? SongIOType::RMTSTRIPPED : SongIOType::RMT,
            exportTempDescription.instrumentSavedFlags,
            exportTempDescription.trackSavedFlags
        );
    if (exportTempDescription.firstByteAfterModule < 0)
    {
        return false;	// if the module could not be created
    }

    // And save the RMT module block
    CAtariIO::SaveBinaryBlock(ou, exportTempDescription.mem, exportTempDescription.targetAddrOfModule, exportTempDescription.firstByteAfterModule, TRUE);

    return true;		// Indicate that data was saved
}
