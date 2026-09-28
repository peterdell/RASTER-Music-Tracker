#include "SongExporter.h"
#include "StdAfx.h"
#include <iomanip>

#include "AtariBinaries.h"
#include "AtariIO.h"

#include "GuiHelpers.h"
#include "Messages.h"

#include "ExportDlgs.h"
#include "SAPFileExportDialog.h"

#include "lzss_sap.h"
#include "LZSSFile.h"

#include "SAPFile.h"
#include "SAPFileExporter.h"

#include "WaveFileExporter.h"

// TODO: Find a better place and name
CString g_rmtmsxtext;

// CXEXFile::InitFromSong()/CSongExporter::CSongExporter()/StrToAtariVideo/
// BruteforceOptimalLZSS/ExportXEX_LZSS (xexFile overload)/ExportLZSS/
// ExportCompactLZSS are implemented in SongExporterCore.cpp.

bool CSongExporter::ExportSAP_R(CSongExport& songExport, std::ofstream& ou) {

    CSAPFile sapFile;
    sapFile.SetType("R");
    if (!CSAPFileExportDialog::Show(songExport.GetSong(), sapFile)) {
        return false;
    };

    bool result = CSAPFileExporter::ExportSAP_R(songExport, sapFile, ou);
    return result;
}

bool CSongExporter::ExportSAP_B_LZSS(CSongExport& songExport, std::ofstream& ou) {
    CSAPFile sapFile;
    sapFile.SetType("B");
    if (!CSAPFileExportDialog::Show(songExport.GetSong(), sapFile)) {
        return false;
    }

    bool result = CSAPFileExporter::ExportSAP_B_LZSS(songExport, sapFile, ou);
    return result;
}

bool CSongExporter::ExportWAV(CSongExport& songExport, std::ofstream& ou, CXPokey& pokey, byte* memory) {
    return CWaveFileExporter::ExportWAV(songExport, ou, pokey, memory);
}

bool CSongExporter::ExportXEX_LZSS(CSongExport& songExport, std::ofstream& ou) {
    // Create the export metadata for songname, Atari text, parameters, etc
    CXEXFile xexFile;

    if (!ShowXEXExportDialog(songExport.GetSong(), xexFile)) {
        return false;
    }
    return ExportXEX_LZSS(songExport, xexFile, ou);
}

// bool overload (CSongExport&, CXEXFile, std::ostream&) is implemented in
// SongExporterCore.cpp.

// The XEX dialog process was split to a different function for clarity, same will be done for SAP later...
bool CSongExporter::ShowXEXExportDialog(const CSong& song, CXEXFile& xexFile) {

    xexFile.InitFromSong(song);

    CExpMSXDlg dlg;
    CString str;
    dlg.m_txt = DefaultXexText(xexFile);

    str.Format("Playback speed will be adjusted to %s Hz on both PAL and NTSC systems.", (xexFile.isNTSC ? "60" : "50"));
    dlg.m_speedinfo = str;

    if (dlg.DoModal() != IDOK) {
        return false;
    }

    g_rmtmsxtext = dlg.m_txt;
    g_rmtmsxtext.Replace("\x0d\x0d", "\x0d"); //13, 13 => 13

    SetXexText(xexFile, dlg.m_txt);

    xexFile.rasterbarColor = dlg.m_metercolor;
    xexFile.displayRasterbar = dlg.m_meter;
    xexFile.autoRegion = dlg.m_region_auto;

    return true;
}
CString CSongExporter::DefaultXexText(const CXEXFile& xexFile) {
    CString EOL = "\r\n";
    if (g_rmtmsxtext != "") {
        return g_rmtmsxtext; // same from last time, making repeated exports faster
    }
    // 5 lines of text
    CString txt = CString(xexFile.songname) + EOL;
    if (xexFile.isStereo) {
        txt += "STEREO";
    }
    txt += EOL;
    txt += xexFile.currentTime.Format("%d/%m/%Y") + EOL;
    txt += "Author: (press SHIFT key)" + EOL;
    txt += "Author: ???";
    return txt;
}

void CSongExporter::SetXexText(CXEXFile& xexFile, const CString& text) {
    // This block of code will handle all the user input text that will be inserted in the binary during the export process
    memset(xexFile.atariText, ' ', CXEXFile::ATARI_TEXT_SIZE);
    int p = 0, q = 0;
    char a;
    for (int i = 0; i < text.GetLength(); i++) {
        a = text.GetAt(i);
        if (a == '\n') {
            p += 40;
            q = 0;
        } else {
            xexFile.atariText[p + q] = a;
            q++;
        }
        if (p + q >= 5 * 40) {
            break;
        }
    }
    StrToAtariVideo((char*)xexFile.atariText, CXEXFile::ATARI_TEXT_SIZE);
}
