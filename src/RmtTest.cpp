#include "RmtTest.h"

#include "StdAfx.h"
#include "Rmt.h"

#include "wasap.h"

#include "General.h"
#include <iostream>
#include <fstream>
#include <sstream>
#include <string>
#include <filesystem>
#include "Global.h"
#include <assert.h>

using std::ios;

#include "RmtAtariBinaries.h"

class CFileUtility {
public:

    static void SaveFile(const CString& fileName, const byte* buffer,
        const size_t size);

};

void LogInfo(const CString& ss) {
    OutputDebugString(ss.GetString());
}
void LogInfo(const std::stringstream& ss) {
    LogInfo(CString(ss.str().c_str()));
}

void CFileUtility::SaveFile(const CString& filePath, const byte* buffer,
    const size_t size) {

    LogInfo(std::stringstream() << "Saving '" << filePath << "' with " << size << " bytes.\n'");
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
    TrackerDriverVersion trackerDrivers[] = {
    UNPATCHED ,
UNPATCHED_WITH_TUNING ,
      PATCH3_INSTRUMENTARIUM,
        PATCH6,
        PATCH8 ,
        PATCH16 ,
       PATCH_PRINCE_OF_PERSIA };

    LogInfo(std::stringstream() << "Current directoy: " << std::filesystem::current_path().string() << "\n");

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
}


void CRmtTest::RunFor(const CRmtApp& app, const CString fileName) {

    SaveBinaries();

    int sizeOfString = (fileName.GetLength() + 1);
    LPTSTR lpsz = new TCHAR[sizeOfString];
    _tcscpy_s(lpsz, sizeOfString, fileName);
    //... modify lpsz as much as you want   
    WASAP_WinMain(app.m_hInstance, NULL, lpsz, 0);
    delete lpsz;
}