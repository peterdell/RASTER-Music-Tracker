#include "Commands.h"
#include "Messages.h"

#include "resource.h"
#include "Rmt.h"
#include <assert.h>
#include <fstream>

extern CRmtApp g_app;

void CAcceleratorTable::Clear() {
    m_entries.clear();
}

void CAcceleratorTable::Add(const UINT id) {
    // Appends: the program has two tables (the main window's and the Pokey
    // Explorer's) and both belong into one lookup. (Until 2026-09-27 this
    // cleared the previous table, so the main window's accelerators were
    // never in the generated table - it showed the menu labels' hints.)
    HACCEL hAccel = LoadAccelerators(g_app.m_hInstance, MAKEINTRESOURCE(id));
    if (hAccel) {
        int size = ::CopyAcceleratorTable(hAccel, NULL, 0);
        if (size > 0) {
            std::vector<ACCEL> entries(size);
            ::CopyAcceleratorTable(hAccel, entries.data(), size);
            m_entries.insert(m_entries.end(), entries.begin(), entries.end());
        }
    }
}

int CAcceleratorTable::GetSize() const {
    return (int)m_entries.size();
}

const ACCEL& CAcceleratorTable::GetEntry(const int index) const {
    return m_entries[index];
}

const ACCEL* CAcceleratorTable::GetEntryByCommand(const WORD cmd) const {
    for (const ACCEL& entry : m_entries) {
        if (entry.cmd == cmd) {
            return &entry;
        }
    }
    return nullptr;
}

CString CAcceleratorTable::GetText(const ACCEL& entry) {
    // MFC's CMFCAcceleratorKey::Format takes the key names from the active
    // keyboard layout ("Strg", "Umschalt", "+ (Zehnertastatur)" on a German
    // system), which is no good for a generated document that must read the
    // same everywhere and equal the Java port's. Same order as MFC:
    // Ctrl+Shift+Alt+Key; the names are the English ones of the US layout.
    CString result;
    if (entry.fVirt & FCONTROL) {
        result += "Ctrl+";
    }
    if (entry.fVirt & FSHIFT) {
        result += "Shift+";
    }
    if (entry.fVirt & FALT) {
        result += "Alt+";
    }
    if (!(entry.fVirt & FVIRTKEY)) {
        result += (char)entry.key;
        return result;
    }
    WORD key = entry.key;
    if (key >= VK_F1 && key <= VK_F24) {
        CString f;
        f.Format("F%d", key - VK_F1 + 1);
        return result + f;
    }
    if ((key >= '0' && key <= '9') || (key >= 'A' && key <= 'Z')) {
        return result + (char)key;
    }
    if (key >= VK_NUMPAD0 && key <= VK_NUMPAD9) {
        CString n;
        n.Format("Num %d", key - VK_NUMPAD0);
        return result + n;
    }
    switch (key) {
    case VK_SPACE: return result + "Space";
    case VK_ESCAPE: return result + "Esc";
    case VK_RETURN: return result + "Enter";
    case VK_TAB: return result + "Tab";
    case VK_BACK: return result + "Backspace";
    case VK_DELETE: return result + "Del";
    case VK_INSERT: return result + "Ins";
    case VK_HOME: return result + "Home";
    case VK_END: return result + "End";
    case VK_PRIOR: return result + "PgUp";
    case VK_NEXT: return result + "PgDn";
    case VK_UP: return result + "Up";
    case VK_DOWN: return result + "Down";
    case VK_LEFT: return result + "Left";
    case VK_RIGHT: return result + "Right";
    case VK_ADD: return result + "Num +";
    case VK_SUBTRACT: return result + "Num -";
    case VK_MULTIPLY: return result + "Num *";
    case VK_DIVIDE: return result + "Num /";
    case VK_DECIMAL: return result + "Num .";
    case VK_PAUSE: return result + "Pause";
    default:
        break;
    }
    CString other;
    other.Format("VK_%02X", key);
    return result + other;
}

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
    // The label without the mnemonic marker: "&File" is "File", "&&" is one
    // "&", the key after the tab is not part of it. (Before 2026-09-27 the
    // marker state was never reset, so "Clear Undo && Redo History" kept
    // both ampersands.)
    CString result;
    for (int i = 0; i < menuText.GetLength(); i++) {
        auto c = menuText[i];
        if (c == '\t') {
            break;
        }
        if (c == '&') {
            if (i + 1 < menuText.GetLength() && menuText[i + 1] == '&') {
                result.AppendChar('&');
                i++;
            }
            continue;
        }
        result.AppendChar(c);
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

CString CMenuEntry::GetPlainText() const {
    return GetPlainText(text);
}

CString CMenuEntry::GetAcceleatorKey() const {
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

CCommands::CActionInfo::~CActionInfo() {
    delete menuEntry;
}

UINT CCommands::CActionInfo::GetID() const {
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
    delete this->menuEntry;
    this->menuEntry = menuEntry;
}

CString CCommands::CActionInfo::GetToolBar() const {
    return toolBar;
}

void CCommands::CActionInfo::AddToolBar(const CString& toolBar) {
    if (!this->toolBar.IsEmpty()) {
        this->toolBar += ", ";
    }
    this->toolBar += toolBar;
}

CCommands::CCommands() {
}

CCommands::~CCommands() {
    ClearActionInfos();
}

void CCommands::ClearActionInfos() {
    for (auto it = m_actionInfoMap.begin(); it != m_actionInfoMap.end(); it++) {
        delete it->second;
    }
    m_actionInfoMap.clear();
    m_order.clear();
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
    } else {
        result = new CActionInfo(id, nullptr);
        m_actionInfoMap.emplace(result->GetID(), result);
        m_order.push_back(result);
    }

    return result;
}

namespace {

CString Escape(const CString& cell) {
    CString result = cell;
    result.Replace("|", "\\|");
    return result;
}

CString Error(const CString& message) {
    return "<br><span style=\"color:red;\">ERROR: " + message + "</span>";
}

} // namespace

CString CCommands::GetActionInfosText(int& errorCount) const {
    errorCount = 0;
    CString result;
    result += "| Access Path | Entry | Accelerator Key | Action |\n";
    result += "|---|---|---|---|\n";
    for (const CActionInfo* actionInfo : m_order) {
        const CMenuEntry* menuEntry = actionInfo->GetMenuEntry();

        // The key: the accelerator table's entry, MFC-formatted; a menu label
        // that displays another key (after its tab) is an error - the label
        // lies. A label's key without a table entry is a hint the program
        // handles itself (the tracker's own keys), shown as the menu shows it.
        CString realKey;
        const ACCEL* acceleratorEntry = m_acceleratorTable.GetEntryByCommand((WORD)actionInfo->GetID());
        if (acceleratorEntry != nullptr) {
            realKey = CAcceleratorTable::GetText(*acceleratorEntry);
        }
        CString shownKey = menuEntry != nullptr ? menuEntry->GetAcceleatorKey() : CString();
        // A button or key without a menu item: the prompt's tooltip "Label (Key)" is its label and shown key.
        CString entry = menuEntry != nullptr ? menuEntry->GetPlainText() : actionInfo->GetDescription();
        if (menuEntry == nullptr && entry.GetLength() > 2 && entry[entry.GetLength() - 1] == ')') {
            int open = entry.ReverseFind('(');
            if (open > 0 && entry[open - 1] == ' ') {
                shownKey = entry.Mid(open + 1, entry.GetLength() - open - 2);
                entry = entry.Left(open - 1);
            }
        }
        CString key = realKey.IsEmpty() ? shownKey : realKey;
        CString keyCell;
        if (!key.IsEmpty()) {
            keyCell = "`" + key + "`";
        }
        if (!realKey.IsEmpty() && !shownKey.IsEmpty() && realKey != shownKey) {
            keyCell += Error("the menu shows '" + shownKey + "'");
            errorCount++;
        }

        // The prompt: the STRINGTABLE's status text is the "Action"; its
        // tooltip part must be the plain label plus the shown key in
        // parentheses, the convention of Rmt.rc.
        CString action = actionInfo->GetText();
        CString description = actionInfo->GetDescription();
        if (!description.IsEmpty() && menuEntry != nullptr) {
            CString expected = menuEntry->GetPlainText();
            if (!shownKey.IsEmpty()) {
                expected += " (" + shownKey + ")";
            }
            if (description != expected) {
                action += Error("expected the tooltip '" + expected + "' instead of '" + description + "'");
                errorCount++;
            }
        }

        CString accessPath;
        if (menuEntry != nullptr) {
            accessPath = "Menu " + menuEntry->GetMenuTextPathString();
        }
        if (!actionInfo->GetToolBar().IsEmpty()) {
            if (!accessPath.IsEmpty()) {
                accessPath += "<br>";
            }
            accessPath += "Tool Bar " + actionInfo->GetToolBar();
        }

        CString line;
        line.Format("| %s | %s | %s | %s |\n", Escape(accessPath), Escape(entry), keyCell, Escape(action));
        result += line;
    }
    return result;
}

int CCommands::WriteActionInfos(const std::filesystem::path& file) const {
    int errorCount = 0;
    CString text = GetActionInfosText(errorCount);
    std::ofstream out(file, std::ios::binary);
    out.write((LPCTSTR)text, text.GetLength());
    out.close();
    return errorCount;
}

void CCommands::AnalyzeMenu(const CMenuEntry::MenuPath& menuIDPath, const CMenuEntry::MenuPath& menuTextPath, const CMenu& menu) {

    for (int pos = 0; pos < menu.GetMenuItemCount(); pos++) {
        CString menuItemText;
        auto menuItemID = menu.GetMenuItemID(pos);
        menu.GetMenuString(pos, menuItemText, MF_BYPOSITION);

        auto subMenu = menu.GetSubMenu(pos);
        if (subMenu == nullptr && menuItemID > 0) { // a popup reports (UINT)-1 as its ID
            auto menuEntry = new CMenuEntry(menuIDPath, menuTextPath, menuItemID, menuItemText);
            auto actionInfo = GetMutableActionInfo(menuItemID);
            actionInfo->SetMenuEntry(menuEntry);
        }

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

void CCommands::AnalyzeMenu(const UINT id, const CString& menuID, const CString& menuText) {
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

    } else {
        auto lastError = GetLastError();
        CString s;
        s.Format("LoadMenu(%d) failed with error code %d", id, lastError);
        SendErrorMessage(s);
    }
}

void CCommands::AnalyzeToolBar(const CString& name, const CToolBar& toolBar) {

    for (int pos = 0; pos < toolBar.GetCount(); pos++) {
        auto itemID = toolBar.GetItemID(pos);
        if (itemID > 0 && itemID != ID_SEPARATOR) {
            auto actionInfo = GetMutableActionInfo(itemID);
            actionInfo->AddToolBar(name);
        }
    }
}

void CCommands::AnalyzeToolBar(const UINT id, const CString& name) {
    CToolBar toolBar;
    toolBar.Create(g_app.GetMainWnd());
    if (toolBar.LoadToolBar(id)) {
        AnalyzeToolBar(name, toolBar);
    } else {
        auto lastError = GetLastError();
        CString s;
        s.Format("LoadToolBar(%d) failed with error code %d", id, lastError);
        SendErrorMessage(s);
    }
}

void CCommands::AnalyzeAccelerators() {
    // Commands that only have a key (no menu item, no button) get a row too.
    for (int i = 0; i < m_acceleratorTable.GetSize(); i++) {
        GetMutableActionInfo(m_acceleratorTable.GetEntry(i).cmd);
    }
}

void CCommands::Analyze() {
    ClearActionInfos();
    m_acceleratorTable.Clear();
    m_acceleratorTable.Add(IDR_MAIN_WINDOW);
    m_acceleratorTable.Add(IDR_POKEY_EXPLORER);

    AnalyzeMenu(IDR_MAIN_WINDOW, "Main", "");

    AnalyzeToolBar(IDR_MAIN_WINDOW, "Main");
    AnalyzeToolBar(IDR_TOOLBAR_BLOCK, "Block");

    AnalyzeAccelerators();
}
