#pragma once

// Use Accelerator Key Mapping in Rmt.rc

#include "StdAfx.h"
#include <map>


class CMenuEntry {
public:
    typedef CStringArray MenuPath;

    static CString GetPlainText(const CString& menuText);
    static CString GetAcceleatorKey(const CString& menuText);
    static CString GetMenuPathString(const MenuPath& menuPath);

    CMenuEntry(const MenuPath& menuPath, const UINT id, const CString& text);

    void GetMenuPath(MenuPath& result) const;
    CString GetMenuPathString() const;

    INT_PTR GetMenuLevel() const;


    UINT GetID() const;

    // Get text including accelerator and shortchut.
    CString GetText() const;
    CString GetPlainText() const;
    CString GetAcceleatorKey() const;

private:
    MenuPath menuPath;
    UINT id;
    CString text;

};

class CCommands
{

public:
    void Analyze();


    class CActionInfo {
    public:
        UINT id;
        CMenuEntry* menuEntry;
    };

    typedef std::map<UINT, CActionInfo> ActionInfoMap;

private:
    void AnalyzeMenu(const CMenuEntry::MenuPath& menuPath, CMenu& menu);

};

