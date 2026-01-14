#include "RmtTest.h"

#include "StdAfx.h"
#include "Rmt.h"

#include "wasap.h"


#include <iostream>
#include <fstream>
#include <sstream>
#include <string>
#include <filesystem>
#include "Global.h"
#include <assert.h>

using std::ios;

#include "AtariBinaries.h"

#include "GuiHelpers.h"

#include "Global.h"
#include "SongExporterTest.h"


extern CSong g_Song;

class CFileUtility {
public:

    static void SaveFile(const CString& fileName, const byte* buffer,
        const size_t size);

};

void CFileUtility::SaveFile(const CString& filePath, const byte* buffer,
    const size_t size) {

    SendInfoMessage((std::stringstream() << "Saving '" << filePath << "' with " << size << " bytes.\n").str().c_str());
    std::ofstream fos;
    fos.open(filePath, ios::out | ios::binary | ios::trunc);
    for (auto i = 0; i < size; i++) {
        fos << buffer[i];
    }
    fos.close();
};

CRmtTest::CRmtTest() {
}


void CRmtTest::TestASAP(const CRmtApp& app, const CString fileName) {
    SendInfoMessage("Test - TestASAP");
    int sizeOfString = (fileName.GetLength() + 1);
    LPTSTR lpsz = new TCHAR[sizeOfString];
    _tcscpy_s(lpsz, sizeOfString, fileName);
    //... modify lpsz as much as you want   
    WASAP_WinMain(app.m_hInstance, NULL, lpsz, 0);
    delete lpsz;
}

void CRmtTest::RunFor(const CRmtApp& app, const CString fileName) {

    // TestASAP(app, fileName);

    // All these variables are initialized with their defaults.
    // - g_AtariTrackerDriver 
    // - g_tuning
    // - g_tuningRatios
    // - g_Song

    SendInfoMessage("Test - Open and Export");

    if (!g_Song.FileOpen(fileName, FALSE)) {
        SendErrorMessage("Cannot load sonf from file '" + fileName + '.');
        return;
    }

    CSongExporterTest::Test(g_Song);

}