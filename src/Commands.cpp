#include "Commands.h"
#include "GuiHelpers.h"

#include "resource.h"


CMenuEntry::CMenuEntry(const MenuPath& menuPath, const UINT id, const CString& text) {
    this->menuPath.Append(menuPath);
    this->id = id;
    this->text = text;
}

void CMenuEntry::GetMenuPath(CMenuEntry::MenuPath& result) const {
    result.RemoveAll();
    result.Append(menuPath);
}

CString CMenuEntry::GetMenuPathString() const {
    return GetMenuPathString(menuPath);
}


INT_PTR CMenuEntry::GetMenuLevel() const {
    return menuPath.GetSize();
}

UINT CMenuEntry::GetID() const {
    return id;
}

CString CMenuEntry::GetText() const {
    return text;
}

CString CMenuEntry::GetPlainText(const CString& menuText) {
    CString result;
    bool ampersand = false;
    bool end = false;
    for (int i = 0; i < menuText.GetLength() && !end; i++) {
        auto c = menuText[i];
        switch (c) {
        case '&':
            if (ampersand) {
                result.AppendChar(c);
            }
            else {
                ampersand = !ampersand;
            }
            break;

        case '\t':
            end = true;
            break;

        default:
            result.AppendChar(c);
        }
    }

    return result;
}

CString CMenuEntry::GetAcceleatorKey(const CString& menuText) {
    CString result;
    auto startIndex = menuText.Find("\t(");
    if (startIndex >= 0) {
        startIndex += 2;
        auto endIndex = menuText.Find(')', startIndex);
        if (endIndex >= 0) {
            result = menuText.Mid(startIndex, endIndex);
        };
    }
    return result;
}

CString CMenuEntry::GetMenuPathString(const MenuPath& menuPath) {
    CString result;
    for (int i = 0; i < menuPath.GetSize(); i++) {
        if (i > 0) {
            result += " / ";
        }
        result += GetPlainText(menuPath[i]);
    }
    return result;
}

CString  CMenuEntry::GetPlainText() const {
    return GetPlainText(text);
}

CString  CMenuEntry::GetAcceleatorKey() const {
    return GetAcceleatorKey(text);

}






void CCommands::AnalyzeMenu (const CMenuEntry::MenuPath& menuPath, CMenu& menu) {

    CActionInfo actionInfo = {};

    CString s;
    s.Format("Anayzing level %d, menu %s: %p with %d entries", menuPath.GetSize(), CMenuEntry::GetMenuPathString(menuPath), &menu, menu.GetMenuItemCount());
    SendInfoMessage(s);


    for (int pos = 0; pos < menu.GetMenuItemCount(); pos++) {
        CString posString;
        posString.Format("%d", pos);

        CString menuItemText;
        auto menuItemId = menu.GetMenuItemID(pos);
        menu.GetMenuString(pos, menuItemText, MF_BYPOSITION);

        s.Format("Menu %s, Position %s: %d %s", CMenuEntry::GetMenuPathString(menuPath), posString, menuItemId, menuItemText);
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
            CMenuEntry::MenuPath subMenuPath;
            subMenuPath.Append(menuPath);
            subMenuPath.Add(menuItemText);

            AnalyzeMenu(subMenuPath, *subMenu);
        }
    }

}
void CCommands::Analyze() {

    // TestASAP(app, fileName);
    CString s;

    s.LoadString(IDS_RMTAUTHOR);
    SendInfoMessage(s);
    CMenu menu;
    if (menu.LoadMenu(IDR_MAIN_WINDOW)) {
        CMenuEntry::MenuPath menuPath;
        menuPath.Add("Main");
        AnalyzeMenu(menuPath, menu);
    }
}
