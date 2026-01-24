#pragma once

// Use Accelerator Key Mapping in Rmt.rc

#include "StdAfx.h"
#include <map>
#include <list>


class CMenuEntry {
public:
    typedef CStringArray MenuPath;
    typedef CString MenuPosition;

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

class CCommands
{

public:
    CCommands();
    void Analyze();


    class CActionInfo {
    public:
        static bool Compare(const CActionInfo* first, CActionInfo* second);

        CActionInfo(const UINT id, const CMenuEntry* menuEntry);

        UINT GetID() const;
        CString GetText() const;
        CString GetDescription() const;
        const CMenuEntry* GetMenuEntry() const;
        void SetMenuEntry(const CMenuEntry* menuEntry);

        CString GetToolBar() const;
        void SetToolBar(const CString& toolBar);

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

    ActionInfoMap m_actionInfoMap;

    void ClearActionInfos();
    const CActionInfo* GetActionInfo(UINT id) const;
    CActionInfo* GetMutableActionInfo(UINT id);

    void PrintActionInfos() const;

    void AnalyzeMenu(const CMenuEntry::MenuPath& menuIDPath, const CMenuEntry::MenuPath& menuTextPath, const CMenu& menu);
    void AnalyzeMenu(const UINT id, const CString& menuID, const CString& menuText);

    void AnalyzeToolBar(const CString& namne, const CToolBar& toolBar);
    void AnalyzeToolBar(const UINT id, const CString& name);

    void AnalyeAcceleratorTable(const UINT id);

};

