#pragma once

#include "stdafx.h"


class CRmtCommandLineInfo : public CCommandLineInfo
{
public:
    CRmtCommandLineInfo(void);
    virtual ~CRmtCommandLineInfo(void);
    void ParseParam(const TCHAR* pszParam, BOOL bFlag, BOOL bLast) override;

    bool IsScriptFileSpecified() const;
    CString GetScriptFilePath() const;

private:
    bool m_scriptFileSpecified;
    CString m_scriptFilePath;

    static CString GetSwitchName(const CString& switchString);
    static CString GetSwitchValue(const CString& switchString);
};

