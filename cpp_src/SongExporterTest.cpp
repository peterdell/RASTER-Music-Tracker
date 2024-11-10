#include "stdafx.h"
#include "SongExporterTest.h"

#include "Song.h"

void AssertTrue(bool actual) {
    if (actual != true) {
        exit(1);
    }
}

CString GetNameWithoutExtension(const CString& fileName) {
    int nPos = fileName.Find('.');
    if (nPos != -1) {
        return fileName.Left(nPos + 1);
    }
    return fileName;
}

void CSongExporterTest::Test() {

    static const CString SEPARATOR = "\\";

    CSong song;
    CSongExporter songExporter;
    std::ifstream in;
    CString inFilePath = "C:\\Users\\JAC\\Desktop\\ASMA-Input\\Test.rmt";
    in.open(inFilePath);
    AssertTrue(song.LoadRMT(in));
    in.close();

    CString inFileName = CFile(inFilePath, CFile::modeRead).GetFileName();
    CString outFileName = GetNameWithoutExtension(inFileName);

    CString outFolderName = "C:\\Users\\JAC\\Desktop\\ASMA-Input\\out";

    std::ofstream os;
    //outFilePath = outFolderName + SEPARATOR + "-Compact.lzss";
    //std::ofstream os(outFilePath);
    //songExporter.ExportCompactLZSS(song, os, outFilePath);
    CString outFilePathPrefix = outFolderName + SEPARATOR + outFileName;
    CString outFilePath = outFilePathPrefix + ".lzss";
    os.open(outFilePath);
    songExporter.ExportLZSS(song, os, outFilePath);
    os.close();

    outFilePath = outFilePathPrefix + "-Type-B-LZSS.sap";
    os.open(outFilePath);
    songExporter.ExportSAP_B_LZSS(song, os);
    os.close();

    outFilePath = outFilePathPrefix + "-Type-R.sap";
    os.open(outFilePath);
    songExporter.ExportSAP_R(song, os);
    os.close();

    // TODO: Export WAV
    outFilePath = outFilePathPrefix + ".wav";
    os.open(outFilePath);
    //songExporter.ExportWAV(song, os, outFileName);
    os.close();

    outFilePath = outFilePathPrefix + "-LZSS.xex";
    os.open(outFilePath);
    songExporter.ExportXEX_LZSS(song, os);
    os.close();
}