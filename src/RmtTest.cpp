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

void CRmtTest::SaveBinaries() {
    /*
    SendInfoMessage("Test - SaveBinaries");
    TrackerDriverVersion trackerDrivers[] = {
    UNPATCHED ,
UNPATCHED_WITH_TUNING ,
      PATCH3,
        PATCH6,
        PATCH8 ,
        PATCH16 ,
       PATCH_PRINCE_OF_PERSIA };

    SendInfoMessage((std::stringstream() << "Current directoy: " << std::filesystem::current_path().string()).str().c_str());

    CString fileName;
    byte* buffer = nullptr;;
    WORD size = 0;

    auto directoryPath = GetResourceFolderPath("players");
    std::filesystem::create_directory(directoryPath.GetString());
    for (const auto trackerDriver : trackerDrivers) {
        CRmtAtariBinaries::GetTrackerDriverBinary(trackerDriver, buffer, size);
        assert(buffer != nullptr && size > 0);
        fileName.Format("Player-V%d.obx", trackerDriver);
        auto filePath = GetResourceFilePath("players", fileName);
        CFileUtility::SaveFile(filePath, buffer, size);
    }
    CRmtAtariBinaries::GetVUPlayerBinary(buffer, size);
    assert(buffer != nullptr && size > 0);
    fileName = "VUPlayer.obx";
    auto filePath = GetResourceFilePath("players", fileName);

    CFileUtility::SaveFile(filePath, buffer, size);
    */
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

    SaveBinaries();
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