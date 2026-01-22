#include "RmtTest.h"

#include <map>
#include "StdAfx.h"
#include "Rmt.h"

#include "asap\wasap.h"


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
void CRmtTest::RunFor(const CRmtApp& app, const CString fileName) {

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