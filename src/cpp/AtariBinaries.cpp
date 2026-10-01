#include "StdAfx.h"
#include "AtariBinaries.h"
#include <filesystem>
#include "Global.h"

#include <map>

static std::map<TrackerDriverVersion, CByteArray*> m_trackerDriverVersionBinary;

// TODO Move to CResourceUtility class

CByteArray* LoadByteArray(const CString& filePath) {
    CFileStatus status;
    if (CFile::GetStatus(filePath, status) == 0) {
        return nullptr;
    }
    if (status.m_size > CAtari::MEMORY_SIZE) {
        return nullptr;
    }
    auto byteArray = new CByteArray();
    byteArray->SetSize(status.m_size);
    CFile file(filePath, CFile::modeRead);
    auto sizeRead = file.Read(byteArray->GetData(), (UINT)byteArray->GetSize());
    if (!sizeRead == status.m_size) {
        delete byteArray;
        return nullptr;
    }
    return byteArray;
}

CByteArray* LoadResourceByteArray(const std::filesystem::path& relativePath, const CString& fileName) {
    CString filePath = GetResourceFilePath(relativePath, fileName);
    return LoadByteArray(filePath);
}

CString CRmtAtariBinaries::GetTrackerDriverFilePath(TrackerDriverVersion trackerDriverVersion) {
    CString fileName;
    fileName.Format("rmt_driver_v%d.obx", (int)trackerDriverVersion);
    // Assembled with the separator operator, not as one "resources/drivers"
    // literal: this path is shown to the user, and a literal forward slash
    // survives into the middle of an otherwise backslashed Windows path.
    return GetResourceFilePath(std::filesystem::path("resources") / "drivers", fileName);
}

CString CRmtAtariBinaries::GetMissingTrackerDriverMessage(TrackerDriverVersion trackerDriverVersion) {
    CString message;
    message.Format("Could not load the player routines:\n'%s'\n\nRMT stays silent and exports no sound until that file is back.\n",
                   (LPCTSTR)GetTrackerDriverFilePath(trackerDriverVersion));
    return message;
}

bool CRmtAtariBinaries::GetTrackerDriverBinary(TrackerDriverVersion trackerDriverVersion, byte*& binary, WORD& size) {
    binary = nullptr;
    size = 0;

    auto byteArrayIt = m_trackerDriverVersionBinary.find(trackerDriverVersion);
    CByteArray* byteArray;
    if (byteArrayIt != m_trackerDriverVersionBinary.end()) {
        byteArray = (*byteArrayIt).second;
    } else {

        byteArray = LoadByteArray(GetTrackerDriverFilePath(trackerDriverVersion));
        if (!byteArray) {
            return false;
        }
        m_trackerDriverVersionBinary.insert({trackerDriverVersion, byteArray});
    }

    binary = byteArray->GetData();
    size = (WORD)byteArray->GetSize(); // TODO: Return *CByteArray
    return true;
}

bool CRmtAtariBinaries::GetVUPlayerBinary(byte*& binary, WORD& size) {
    binary = nullptr;
    size = 0;

    auto byteArray = LoadResourceByteArray(std::filesystem::path("resources/players"), "vu_player_v2.obx");
    if (!byteArray) {
        return false;
    }
    binary = byteArray->GetData();
    size = (WORD)byteArray->GetSize(); // TODO: Return *CByteArray
    return true;
}
