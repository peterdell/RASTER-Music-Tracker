#include "StdAfx.h"
#include <ctime> 
#include "SongExporterTest.h"

#include <string>
#include "Song.h"
#include "SongContainer.h"

#include "SongExport.h"

#include "PokeyRenderer.h"

#include "Global.h"

#include "GuiHelpers.h"

#include "SAPFile.h"
#include "SAPFileExporter.h"


extern CXPokey g_Pokey;



void AssertTrue(bool actual) {
    if (actual != true) {
        exit(1);
    }
}

CString GetFileNameWithoutExtension(const CString& fileName) {
    int nPos = fileName.Find('.');
    if (nPos != -1) {
        return fileName.Left(nPos);
    }
    return fileName;
}

static   time_t startTimestamp;

bool OpenOutputStream(const CString filePath, const int mode, std::ofstream& os) {
    SendInfoMessage("Opening '" + filePath + "' for output.");
    os.open(filePath, mode);
    if (os.fail()) {
        SendErrorMessage("Cannot open the file for writing.");
        return false;
    }
    startTimestamp = time(NULL);
    return true;
}

void CloseOutputStream(const CString filePath, const bool result, std::ofstream& os) {
    os.close();
    time_t endTimestamp = time(NULL);
    long diff = (long)difftime(endTimestamp, startTimestamp);
    auto seconds = std::to_string(diff);
    if (result) {
        CFile file(filePath, CFile::modeRead);
        SendInfoMessage("Output file '" + filePath + "' created in " + seconds.c_str() + " seconds with " + std::to_string(file.GetLength()).c_str() + " bytes.");
    }
    else {
        SendInfoMessage("Creation ofutput file '" + filePath + "' failed within " + seconds.c_str() + " seconds.");
        CFile::Remove(filePath);
    }
}


void CSongExporterTest::Test(CSong& song) {

    static const CString SEPARATOR = "\\";

    CSongContainer songContainer(song);
    // TODO: Some global state and references point to g_Song. Therefore using a local song does not yet work.
    //CSong song;
    //std::ifstream in;
    //CString inFilePath = "C:\\Users\\JAC\\Desktop\\ASMA-Input\\Test.rmt";
    //in.open(inFilePath);
    //AssertTrue(song.LoadRMT(in));
    //in.close();

    CSongExporter songExporter;

    CString inFilePath = song.GetFilename();
    CString inFileName = CFile(inFilePath, CFile::modeRead).GetFileName();
    CString outFileName = GetFileNameWithoutExtension(inFileName);

    CString outFolderName = "C:\\Users\\JAC\\Desktop\\ASMA-Input\\Test\\out";

    CString outFilePath;
    std::ofstream os;
    //outFilePath = outFolderName + SEPARATOR + "-Compact.lzss";
    //std::ofstream os(outFilePath);
    //songExporter.ExportCompactLZSS(song, os, outFilePath);
    CString outFilePathPrefix = outFolderName + SEPARATOR + outFileName;
    bool result = false;
    const auto modeBinary = std::ofstream::out | std::ofstream::binary;

    bool LZSS = false;
    bool XEX_LZSS = true;
    bool SAP_B_LZSS = false;
    bool SAP_R = true;
    bool SAP_R_LZSS = false;
    bool WAV = false;

    if (LZSS) {
        outFilePath = outFilePathPrefix + ".lzss";
        if (OpenOutputStream(outFilePath, modeBinary, os)) {
            {
                CSongExport songExport(songContainer, outFilePath);
                result = songExporter.ExportLZSS(songExport, os);
            }
            CloseOutputStream(outFilePath, result, os);
        }
    }

    if (XEX_LZSS) {
        outFilePath = outFilePathPrefix + "-LZSS.xex";
        if (OpenOutputStream(outFilePath, std::ofstream::binary, os)) {
            {
                CSongExport songExport(songContainer, outFilePath);
                CXEXFile xexFile;
                xexFile.InitFromSong(song);
                result = songExporter.ExportXEX_LZSS(songExport, xexFile, os);
            }
            CloseOutputStream(outFilePath, result, os);
        }
    }

    if (SAP_B_LZSS) {
        outFilePath = outFilePathPrefix + "-Type-B-LZSS.sap";
        if (OpenOutputStream(outFilePath, modeBinary, os)) {
            {
                CSongExport songExport(songContainer, outFilePath);
                result = songExporter.ExportSAP_B_LZSS(songExport, os);
            }
            CloseOutputStream(outFilePath, result, os);
        }
    }

    if (SAP_R) {
        outFilePath = outFilePathPrefix + "-Type-R.sap";
        if (OpenOutputStream(outFilePath, modeBinary, os)) {
            {
                CSongExport songExport(songContainer, outFilePath);
                CSAPFile sapFile;
                sapFile.Init(song);
                result = CSAPFileExporter::ExportSAP_R(songExport, sapFile, os);
            }
            CloseOutputStream(outFilePath, result, os);
        }
    }


    if (WAV) {
        /* TODO: Make it work for WAV
        https://github.com/raster-atari-org/RASTER-Music-Tracker/issues/10
        */
        outFilePath = outFilePathPrefix + ".wav";
        if (OpenOutputStream(outFilePath, modeBinary, os)) {
            {
                CSongExport songExport(songContainer, outFilePath);
                result = songExporter.ExportWAV(songExport, os, g_Pokey, g_AtariTrackerDriver->GetAtari()->GetMemoryAt(0));
            }
            CloseOutputStream(outFilePath, result, os);
        }

    }

}