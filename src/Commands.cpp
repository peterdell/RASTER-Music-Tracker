#include "Commands.h"
#include "GuiHelpers.h"

#include "Rmt.h"
#include "resource.h"
#include <iostream>
#include <fstream>

extern CRmtApp g_app;


CMenuEntry::CMenuEntry(const MenuPath& menuIDPath, const MenuPath& menuTextPath, const UINT id, const CString& text) {
    this->menuIDPath.Append(menuIDPath);
    this->menuTextPath.Append(menuTextPath);
    this->id = id;
    this->text = text;
}

void CMenuEntry::GetMenuIDPath(CMenuEntry::MenuPath& result) const {
    result.RemoveAll();
    result.Append(menuIDPath);
}

CString CMenuEntry::GetMenuIDPathString() const {
    return GetMenuPathString(menuIDPath);
}

void CMenuEntry::GetMenuTextPath(CMenuEntry::MenuPath& result) const {
    result.RemoveAll();
    result.Append(menuTextPath);
}

CString CMenuEntry::GetMenuTextPathString() const {
    return GetMenuPathString(menuTextPath);
}


INT_PTR CMenuEntry::GetMenuLevel() const {
    return menuIDPath.GetSize();
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
    auto startIndex = menuText.Find("\t");
    if (startIndex >= 0) {
        startIndex += 1;
        result = menuText.Mid(startIndex);
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


CCommands::CActionInfo::CActionInfo(const UINT id, const CMenuEntry* menuEntry) {
    this->id = id;
    this->text.LoadString(id);
    auto index = text.Find("\n");
    if (index >= 0) {
        description = text.Mid(index + 1);
        text = text.Left(index);
    }
    this->menuEntry = menuEntry;
}

UINT  CCommands::CActionInfo::GetID() const {
    return id;
}

CString CCommands::CActionInfo::GetText() const {
    return text;
}

CString CCommands::CActionInfo::GetDescription() const {
    return description;
}

const CMenuEntry* CCommands::CActionInfo::GetMenuEntry() const {
    return menuEntry;
}

void CCommands::CActionInfo::SetMenuEntry(const CMenuEntry* menuEntry) {
    this->menuEntry = menuEntry;
}


CString CCommands::CActionInfo::GetToolBar() const {
    return toolBar;
}

void CCommands::CActionInfo::SetToolBar(const CString& toolBar) {
    this->toolBar = toolBar;
}



bool CCommands::CActionInfo::Compare(const CCommands::CActionInfo* first, CCommands::CActionInfo* second)
{
    if (first == second) {
        return 0;
    }
    auto menuEntry1 = first->GetMenuEntry();
    auto menuEntry2 = second->GetMenuEntry();

    if (menuEntry1 == nullptr || menuEntry2 == nullptr) {
        return menuEntry1;
    }
    auto menuPositon1 = menuEntry1->GetMenuIDPathString();
    auto menuPositon2 = menuEntry2->GetMenuIDPathString();
    auto result = menuPositon1 < menuPositon2;
    CString s;
    s.Format("Compare(%s, %s)=%d", menuPositon1, menuPositon2, result);
    SendInfoMessage(s);
    return result;
}


void  CCommands::ClearActionInfos() {


    for (auto it = m_actionInfoMap.begin(); it != m_actionInfoMap.end(); it++) {
        CString s;
        auto actionEntry = it->second;
        auto menuEntry = actionEntry->GetMenuEntry();
        if (menuEntry != nullptr) {
            delete menuEntry;
        }
        delete actionEntry;

    }
}


const CCommands::CActionInfo* CCommands::GetActionInfo(UINT id) const {
    CCommands::CActionInfo* result = nullptr;
    if (id > 0) {
        auto it = m_actionInfoMap.find(id);
        if (it != m_actionInfoMap.end()) {
            result = it->second;
        }
    }
    return result;
}

CCommands::CActionInfo* CCommands::GetMutableActionInfo(UINT id) {

    CCommands::CActionInfo* result = nullptr;

    assert(id > 0);

    auto it = m_actionInfoMap.find(id);
    if (it != m_actionInfoMap.end()) {
        result = it->second;
    }
    else {
        result = new CActionInfo(id, nullptr);
        m_actionInfoMap.emplace(result->GetID(), result);
    }

    return result;
}


void  CCommands::PrintActionInfos() const {

    ActionInfoList actionInfoList;

    std::ofstream myfile;

    for (auto it = m_actionInfoMap.begin(); it != m_actionInfoMap.end(); it++) {
        CString s;
        auto actionInfo = it->second;
        actionInfoList.push_back(actionInfo);
    }
    actionInfoList.sort(CActionInfo::Compare);

    myfile.open("../doc/rmt_action_infos.md");
    myfile << "| Action | Access Path | Entry | Accelerator Key | \n";
    myfile << "|--------|-----------|------------|-----------------| \n";
    for (auto it = actionInfoList.begin(); it != actionInfoList.end(); it++) {
        CString s;
        auto actionInfo = (*it);
        auto menuEntry = actionInfo->GetMenuEntry();
        CString acceleratorKeyFormatted;
        if (menuEntry != nullptr) {
            auto acceleratorKeyFormatted = menuEntry->GetAcceleatorKey();
            if (!acceleratorKeyFormatted.IsEmpty()) {
                acceleratorKeyFormatted = "`" + acceleratorKeyFormatted + "`";
            }
        }

        auto text = actionInfo->GetText();
        auto description = actionInfo->GetDescription();
        if (!description.IsEmpty() && menuEntry != nullptr) {
            CString expected = menuEntry->GetPlainText();
            auto acceleratorKey = menuEntry->GetAcceleatorKey();
            if (!acceleratorKey.IsEmpty()) {
                expected += " (" + acceleratorKey + ")";
            }
            if (description != expected) {
                text += "<br><span style=\"color:red;\">ERROR: Expected description '" + expected + "' instead of '" + description + "'</span>";
            }
        }
        CString accessPath;
        if (menuEntry != nullptr) {
            accessPath = menuEntry->GetMenuTextPathString();
            if (!actionInfo->GetToolBar().IsEmpty()) {
                accessPath += "<br>" + actionInfo->GetToolBar();
            }
        }
        CString accessText;
        if (menuEntry != nullptr) {
            accessText = menuEntry->GetPlainText();
        }


        s.Format("| %s | %s | %s | %s |", text, accessPath, accessText, acceleratorKeyFormatted);


        SendInfoMessage(s);
        myfile << s << "\n";
    }

    myfile.close();

}



void CCommands::AnalyzeMenu(const CMenuEntry::MenuPath& menuIDPath, const CMenuEntry::MenuPath& menuTextPath, const CMenu& menu) {


    for (int pos = 0; pos < menu.GetMenuItemCount(); pos++) {
        CString posString;
        posString.Format("%d", pos);

        CString menuItemText;
        auto menuItemID = menu.GetMenuItemID(pos);
        menu.GetMenuString(pos, menuItemText, MF_BYPOSITION);

        if (menuItemID > 0) {
            auto menuEntry = new CMenuEntry(menuIDPath, menuTextPath, menuItemID, menuItemText);
            auto actionInfo = GetMutableActionInfo(menuItemID);
            actionInfo->SetMenuEntry(menuEntry);
        }

        auto subMenu = menu.GetSubMenu(pos);
        if (subMenu != nullptr) {
            CString menuItemIDText;
            CMenuEntry::MenuPath subMenuIDPath;
            menuItemIDText.Format("[%d]", pos);
            subMenuIDPath.Append(menuIDPath);
            subMenuIDPath.Add(menuItemIDText);

            CMenuEntry::MenuPath subMenuTextPath;
            subMenuTextPath.Append(menuTextPath);
            subMenuTextPath.Add(menuItemText);

            AnalyzeMenu(subMenuIDPath, subMenuTextPath, *subMenu);
        }
    }

}

void  CCommands::AnalyzeMenu(const UINT id, const CString& menuID, const CString& menuText) {
    CMenu menu;
    if (menu.LoadMenu(id)) {
        CMenuEntry::MenuPath menuIDPath;
        CMenuEntry::MenuPath menuTextPath;

        if (!menuID.IsEmpty()) {
            menuIDPath.Add(menuID);
        }
        if (!menuText.IsEmpty()) {
            menuTextPath.Add(menuText);
        }
        AnalyzeMenu(menuIDPath, menuTextPath, menu);

    }
    else {
        auto lastError = GetLastError();
        CString s;
        s.Format("LoadMenu(%d) failed with error code %d", id, lastError);
        SendErrorMessage(s);
    }

}

void CCommands::AnalyzeToolBar(const CString& name, const CToolBar& toolBar) {


    for (int pos = 0; pos < toolBar.GetCount(); pos++) {

        CString menuItemText;
        auto itemID = toolBar.GetItemID(pos);

        if (itemID > 0) {
            auto actionInfo = GetMutableActionInfo(itemID);
            actionInfo->SetToolBar(name);
        }

    }

}


void  CCommands::AnalyzeToolBar(const UINT id, const CString& name) {
    CToolBar toolBar;
    toolBar.Create(g_app.GetMainWnd());
    if (toolBar.LoadToolBar(id)) {
        AnalyzeToolBar(name, toolBar);
    }
    else {
        auto lastError = GetLastError();
        CString s;
        s.Format("LoadToolBar(%d) failed with error code %d", id, lastError);
        SendErrorMessage(s);
    }
}

void CCommands::AnalyeAcceleratorTable(const UINT id) {
    // https://learn.microsoft.com/en-us/windows/win32/learnwin32/accelerator-tables
    HACCEL hAccel = LoadAccelerators(g_app.m_hInstance, MAKEINTRESOURCE(id));
    if (hAccel) {
        // TODO Mail sent to CycoPH hA
    }
}

CCommands::CCommands() {

}

void CCommands::Analyze() {


    ClearActionInfos();

    AnalyzeMenu(IDR_MAIN_WINDOW, "Main", "");

    AnalyzeToolBar(IDR_TOOLBAR_BLOCK, "Block Toolbar");

    AnalyeAcceleratorTable(IDR_MAIN_WINDOW);

    PrintActionInfos();

    ClearActionInfos();

}
