#pragma once

// The command table of the program - every menu item, toolbar button and
// accelerator of Rmt.rc joined per command ID with its STRINGTABLE prompt -
// as data: CCommands::Analyze() reads the compiled resources of the running
// program, WriteActionInfos() writes the table doc/rmt_action_infos.md is
// generated from ("dump actions <file>" in a script, see
// doc/rmt_scripting.md and plans/23_DOC_GENERATION_PLAN.md). The table doubles
// as a consistency check of Rmt.rc: a menu label whose displayed key is not
// the real accelerator, or a tooltip that does not match the label, is an
// ERROR marker in the table and a failure of the command.

#include "StdAfx.h"
#include <filesystem>
#include <list>
#include <map>
#include <vector>

class CAcceleratorTable {
public:
    void Clear();
    // Appends the entries of the ACCELERATORS resource id.
    void Add(const UINT id);

    int GetSize() const;
    const ACCEL& GetEntry(const int index) const;
    const ACCEL* GetEntryByCommand(const WORD cmd) const;
    // "Ctrl+Shift+F4": MFC's modifier order, English key names (see the .cpp).
    static CString GetText(const ACCEL& entry);

private:
    std::vector<ACCEL> m_entries;
};

class CMenuEntry {
public:
    typedef CStringArray MenuPath;

    static CString GetPlainText(const CString& menuText);
    static CString GetAcceleatorKey(const CString& menuText);

    static CString GetMenuPathString(const MenuPath& menuPath);

    CMenuEntry(const MenuPath& menuIdPath, const MenuPath& menuTextPath, const UINT id, const CString& text);

    void GetMenuIDPath(MenuPath& result) const;
    CString GetMenuIDPathString() const;

    void GetMenuTextPath(MenuPath& result) const;
    CString GetMenuTextPathString() const;

    INT_PTR GetMenuLevel() const;

    UINT GetID() const;

    // Get text including accelerator and shortchut.
    CString GetText() const;
    CString GetPlainText() const;
    CString GetAcceleatorKey() const;

private:
    MenuPath menuIDPath;
    MenuPath menuTextPath;
    UINT id;
    CString text;
};

class CCommands {

public:
    CCommands();
    ~CCommands();

    // Reads the main menu, the main and block toolbars and the two
    // accelerator tables.
    void Analyze();

    // Writes the table (Markdown, LF line ends) to file; returns the number
    // of ERROR markers in it. Analyze() first.
    int WriteActionInfos(const std::filesystem::path& file) const;

    // The table as text, for tests and WriteActionInfos().
    CString GetActionInfosText(int& errorCount) const;

    class CActionInfo {
    public:
        CActionInfo(const UINT id, const CMenuEntry* menuEntry);
        ~CActionInfo();

        UINT GetID() const;
        // The status bar text of the STRINGTABLE prompt (before the '\n').
        CString GetText() const;
        // The tooltip text of the STRINGTABLE prompt (after the '\n').
        CString GetDescription() const;
        const CMenuEntry* GetMenuEntry() const;
        void SetMenuEntry(const CMenuEntry* menuEntry);

        CString GetToolBar() const;
        void AddToolBar(const CString& toolBar);

    private:
        UINT id;
        CString text;
        CString description;
        const CMenuEntry* menuEntry;
        CString toolBar;
    };

    typedef std::map<UINT, CActionInfo*> ActionInfoMap;
    typedef std::list<CActionInfo*> ActionInfoList;

private:
    CAcceleratorTable m_acceleratorTable;
    ActionInfoMap m_actionInfoMap;
    ActionInfoList m_order; // the rows in the order they are met: menus, toolbars, accelerators

    void ClearActionInfos();
    const CActionInfo* GetActionInfo(UINT id) const;
    CActionInfo* GetMutableActionInfo(UINT id);

    void AnalyzeMenu(const CMenuEntry::MenuPath& menuIDPath, const CMenuEntry::MenuPath& menuTextPath, const CMenu& menu);
    void AnalyzeMenu(const UINT id, const CString& menuID, const CString& menuText);

    void AnalyzeToolBar(const CString& name, const CToolBar& toolBar);
    void AnalyzeToolBar(const UINT id, const CString& name);

    void AnalyzeAccelerators();
};
