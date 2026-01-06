#pragma once

#include "stdafx.h"


class CRmtCommandLineInfo : public CCommandLineInfo
{
public:
    CRmtCommandLineInfo(void);
    virtual ~CRmtCommandLineInfo(void);
    /*  pszParam
        The parameter or flag.

        bFlag
        Indicates whether pszParam is a parameter or a flag.

        bLast
        Indicates if this is the last parameter or flag on the command line.
    */
    void ParseParam(const TCHAR* pszParam, BOOL bFlag, BOOL bLast) override;

    bool IsScriptFileSpecified() const;
    CString GetScriptFilePath() const;

    bool IsTestFileSpecified() const;
    CString GetTestFilePath() const;

private:
    bool m_scriptFileSpecified;
    CString m_scriptFilePath;

    bool m_testFileSpecified;
    CString m_testFilePath;

    static CString GetSwitchName(const CString& switchString);
    static CString GetSwitchValue(const CString& switchString);
};

