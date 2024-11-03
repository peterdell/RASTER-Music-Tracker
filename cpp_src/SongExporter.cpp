#include "stdafx.h"
#include "SongExporter.h"


#include "exportdlgs.h"
#include "SAPFile.h"
#include "SAPFileExporter.h"


bool CSongExporter::ExportSAP_R(CSong& song, std::ofstream& ou)
{

    CSAPFile sapFile;
    sapFile.m_type = "R";
    if (!CExpSAPDlg::Show(song, sapFile)) {
        return false;
    }

    CPokeyStream pokeyStream;
    song.DumpSongToPokeyStream(pokeyStream);

    bool result = CSAPFileExporter::ExportSAP_R(song, pokeyStream, sapFile, ou);

    // Clear the memory and reset the dumper to its initial setup for the next time it will be called
    pokeyStream.FinishedRecording();

    return result;
}


bool CSongExporter::ExportSAP_B_LZSS(CSong& song, std::ofstream& ou)
{
    CSAPFile sapFile;
    sapFile.m_type = "B";
    if (!CExpSAPDlg::Show(song, sapFile)) {
        return false;
    }

    CPokeyStream pokeyStream;
    song.DumpSongToPokeyStream(pokeyStream);

    bool result = CSAPFileExporter::ExportSAP_B_LZSS(song, pokeyStream, sapFile, ou);

    pokeyStream.FinishedRecording();	// Clear the SAP-R dumper memory and reset RMT routines
    return result;
}

