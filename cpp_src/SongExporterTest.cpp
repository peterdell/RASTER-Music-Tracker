#include "stdafx.h"
#include "SongExporterTest.h"

#include "Song.h"
#include "SongContainer.h"

#include "SongExport.h"

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

    CString outFolderName = "C:\\Users\\JAC\\Desktop\\ASMA-Input\\out";

    std::ofstream os;
    //outFilePath = outFolderName + SEPARATOR + "-Compact.lzss";
    //std::ofstream os(outFilePath);
    //songExporter.ExportCompactLZSS(song, os, outFilePath);
    CString outFilePathPrefix = outFolderName + SEPARATOR + outFileName;
    CString outFilePath = outFilePathPrefix + ".lzss";
    os.open(outFilePath, std::ofstream::binary);
    songExporter.ExportLZSS(CSongExport(songContainer, outFilePath), os);
    os.close();

    outFilePath = outFilePathPrefix + "-Type-B-LZSS.sap";
    os.open(outFilePath, std::ofstream::binary);
    songExporter.ExportSAP_B_LZSS(CSongExport(songContainer, outFilePath), os);
    os.close();

    outFilePath = outFilePathPrefix + "-Type-R.sap";
    os.open(outFilePath, std::ofstream::binary);
    songExporter.ExportSAP_R(CSongExport(songContainer, outFilePath), os);
    os.close();

    // TODO: Export WAV
    outFilePath = outFilePathPrefix + ".wav";
    os.open(outFilePath, std::ofstream::binary);
    //songExporter.ExportWAV(CSongExport(songContainer, outFilePath), os);
    os.close();

    outFilePath = outFilePathPrefix + "-LZSS.xex";
    os.open(outFilePath, std::ofstream::binary);
    songExporter.ExportXEX_LZSS(CSongExport(songContainer, outFilePath), os);
    os.close();
}