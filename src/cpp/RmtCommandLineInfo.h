#pragma once

#include "StdAfx.h"

class CRmtCommandLineInfo : public CCommandLineInfo {
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

    // /SCRIPT:<file> - see ScriptRunner.h. (The former /TEST switch and the
    // developer routines behind it were replaced by scripts on 2026-09-27.)
    bool IsScriptFileSpecified() const;
    CString GetScriptFilePath() const;

private:
    bool m_scriptFileSpecified;
    CString m_scriptFilePath;

    static CString GetSwitchName(const CString& switchString);
    static CString GetSwitchValue(const CString& switchString);
};
