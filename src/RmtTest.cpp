#include "RmtTest.h"

#include "Rmt.h"
#include "StdAfx.h"
#include <map>

#include "asap\wasap.h"

#include "Global.h"
#include "StringUtility.h"
#include <assert.h>
#include <filesystem>
#include <fstream>
#include <iostream>
#include <sstream>
#include <string>

#include "lzss_sap.h"

using std::ios;

#include "AtariBinaries.h"

#include "Messages.h"

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

class CActionInfo {
public:
    UINT id;
    int menuLevel;
    CString CStringArray;
    CString menuText;
};

typedef std::map<UINT, CActionInfo> ActionInfoMap;

CString GetPlainMenuText(CString menuText) {
    CString result;
    bool ampersand = false;
    for (int i = 0; i < menuText.GetLength(); i++) {
        auto c = menuText[i];
        if (c == '&') {
            if (ampersand) {
                result.AppendChar(c);
            }
            else {
                ampersand = !ampersand;
            }
        }
        else {
            result.AppendChar(c);
        }
    }

    return result;
}
CString GetPathString(const CStringArray& menuPath) {
    CString result;
    for (int i = 0; i < menuPath.GetSize(); i++) {
        if (i > 0) {
            result += " / ";
        }
        result += GetPlainMenuText(menuPath[i]);
    }
    return result;
}

void Analyze(const CStringArray& menuPath, CMenu& menu) {

    CActionInfo actionInfo = {};

    CString s;
    s.Format("Anayzing level %d, menu %s: %p with %d entries", menuPath.GetSize(), GetPathString(menuPath), &menu, menu.GetMenuItemCount());
    SendInfoMessage(s);


    for (int pos = 0; pos < menu.GetMenuItemCount(); pos++) {
        CString posString;
        posString.Format("%d", pos);

        CString menuItemText;
        auto menuItemId = menu.GetMenuItemID(pos);
        menu.GetMenuString(pos, menuItemText, MF_BYPOSITION);

        s.Format("Menu %s, Position %s: %d %s", GetPathString(menuPath), posString, menuItemId, menuItemText);
        SendInfoMessage(s);


        /*
        MENUITEMINFO menuItemInfo;
        menuItemInfo = {};
        menuItemInfo.cbSize = sizeof(MENUITEMINFO);
        menuItemInfo.fMask = MIIM_TYPE;
        if (menu.GetMenuItemInfo(pos, &menuItemInfo, TRUE)) {

            s.Format("Position %d: Menu Item %d", pos, menuItemInfo.wID);
            SendInfoMessage(s);
        }
        */

        auto subMenu = menu.GetSubMenu(pos);
        if (subMenu != nullptr) {
            CStringArray subMenuPath;
            subMenuPath.Append(menuPath);
            subMenuPath.Add(menuItemText);

            Analyze(subMenuPath, *subMenu);
        }
    }

}

void WriteByteArray(const CString fileName, const byte* buffer, const size_t bufferSize) {
    std::string ofname = (const char*)fileName;
    std::ofstream ostream(ofname, std::ios_base::binary);
    ostream.write((const char*)buffer, bufferSize);
    ostream.close();

}

void TestLZSS(CString fileName) {

    CCompressLzss compressLzss;
    CString message;

    auto sapr = CStringUtility::EndsWithNoCase(fileName, ".sapr");

    std::string stdFileName = (const char*)fileName;
    std::filesystem::path inputFilePath{ stdFileName };
    if (!std::filesystem::exists(inputFilePath)) {
        message.Format("File '%s' not found.", stdFileName.c_str());
        SendErrorMessage(message);
        return;
    }
    auto inputFileLength = std::filesystem::file_size(inputFilePath);
    size_t headerSize = 0;
    if (inputFileLength > 0) {
        std::vector<std::byte> buffer(inputFileLength);
        std::ifstream istream(stdFileName, std::ios_base::binary);
        istream >> std::noskipws;

        size_t dataSize = inputFileLength;

        if (sapr) {
            bool text = true;
            char c1, c2, c3, c4;
            c1 = c2 = c3 = c4 = 0;
            while (text) {
                byte c;
                if (istream.eof()) {
                    text = false;
                }
                istream >> c;

                c1 = c2; c2 = c3; c3 = c4; c4 = c;
                if (c1 == 0x0d && c2 == 0x0a && c3 == 0x0d && c4 == 0x0a) {
                    text = false;
                }
                headerSize += 1;
            }
            dataSize -= headerSize;
            message.Format("Skipped %lu bytes of SAP header skipped in '%s'.", headerSize, stdFileName.c_str(), dataSize);
            SendInfoMessage(message);
        }

        int registers = REGISTERS;
        size_t states = dataSize / registers;
        int leftover = dataSize % registers;
        message.Format(" %lu bytes of data. %lu (0x%lx) states of %d bytes. %d leftover bytes.", dataSize, states, states, registers, leftover);
        SendInfoMessage(message);

        istream.read(reinterpret_cast<char*>(buffer.data()), dataSize);
        istream.close();
        const byte* src = (const byte*)buffer.data();
        auto  srclen = buffer.size();
        byte* dest = new byte[dataSize];
        auto destSize = compressLzss.LZSS_SAP(src, srclen, dest, SAPROptimization::NONE);
        message.Format("LZSS compression of %lu (0x%lx) bytes completed result %u (0x%x) bytes.", dataSize, dataSize, destSize, destSize);
        SendInfoMessage(message);


        WriteByteArray(fileName + ".raw", src, srclen);

        WriteByteArray(fileName + ".raw.lzss", dest, destSize);

        delete dest;

    }
}

void CRmtTest::RunFor(const CRmtApp& app, const CString fileName) {

    // All these variables are initialized with their defaults.
    // - g_AtariTrackerDriver 
    // - g_tuning
    // - g_tuningRatios
    // - g_Song


    if (!CStringUtility::EndsWithNoCase(fileName, ".sapr")) {
        SendInfoMessage("Test - Open and Export");
        if (!g_Song.FileOpen(fileName, FALSE)) {
            SendErrorMessage("Cannot load song from file '" + fileName + '.');
            return;
        }
        CSongExporterTest::Test(g_Song);

    }
    else {
        TestLZSS(fileName);
    }


}