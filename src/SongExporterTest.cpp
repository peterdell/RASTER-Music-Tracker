#include "StdAfx.h"
#include "SongExporterTest.h"

#include <string>
#include "Song.h"
#include "SongContainer.h"

#include "SongExport.h"

#include "PokeyRederer.h"

#include "Global.h"

#include "GuiHelpers.h"

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

bool OpenOutputStream(const CString filePath, const int mode, std::ofstream& os) {
    SendInfoMessage("Opening '" + filePath + "' for output.");
    os.open(filePath, mode);
    if (os.fail()) {
        SendErrorMessage("Cannot open the file for writing.");
        return false;
    }
    return true;
}

void CloseOutputStream(const CString filePath, const bool result, std::ofstream& os) {
    os.close();
    if (result) {
        CFile file(filePath, CFile::modeRead);
        SendInfoMessage("Output file '" + filePath + "' created with " + std::to_string(file.GetLength()).c_str() + " bytes.");
    }
    else {
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

    /*
    outFilePath = outFilePathPrefix + ".lzss";
    if (OpenOutputStream(outFilePath, modeBinary, os)) {
        {
            CSongExport songExport(songContainer, outFilePath);
            result = songExporter.ExportLZSS(songExport, os);
        }
        CloseOutputStream(outFilePath, result, os);
    }

    outFilePath = outFilePathPrefix + "-Type-B-LZSS.sap";
    if (OpenOutputStream(outFilePath, modeBinary, os)) {
        {
            CSongExport songExport(songContainer, outFilePath);
            songExporter.ExportSAP_B_LZSS(songExport, os);
        }
        CloseOutputStream(outFilePath, result, os);
    }

    */

    /*
    outFilePath = outFilePathPrefix + "-Type-R.sap";
    if (OpenOutputStream(outFilePath, modeBinary, os)) {
        {
            CSongExport songExport(songContainer, outFilePath);
            songExporter.ExportSAP_R(songExport, os);
        }
        CloseOutputStream(outFilePath, result, os);
    }
    */

    /* TODO: Make it work for WAV
    https://github.com/raster-atari-org/RASTER-Music-Tracker/issues/10
    outFilePath = outFilePathPrefix + ".wav";
    os.open(outFilePath, std::ofstream::binary);
    {
        CSongExport songExport(songContainer, outFilePath);
        songExporter.ExportWAV(songExport, os, g_Pokey, g_AtariTrackerDriver->GetAtari()->GetMemoryAt(0));
    }
    os.close();
    */

    outFilePath = outFilePathPrefix + "-LZSS.xex";
    if (OpenOutputStream(outFilePath, std::ofstream::binary, os)) {
        {
            CSongExport songExport(songContainer, outFilePath);
            result=songExporter.ExportXEX_LZSS(songExport, os);
        }
        CloseOutputStream(outFilePath, result, os);
    }
}