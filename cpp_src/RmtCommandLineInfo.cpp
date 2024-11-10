#include "stdafx.h"
#include "RmtCommandLineInfo.h"



CRmtCommandLineInfo::CRmtCommandLineInfo(void) : m_scriptFileSpecified(false) {
};

CRmtCommandLineInfo::~CRmtCommandLineInfo(void) {
};

void CRmtCommandLineInfo::ParseParam(const TCHAR* pszParam, BOOL bFlag, BOOL bLast)
{
    // Flag is true for switched
    if (bFlag) {
        CString switchString(pszParam);
        CString switchName = GetSwitchName(switchString).MakeUpper();
        if (switchName.Compare("SCRIPT") == 0) {
            m_scriptFileSpecified = true;
            m_scriptFilePath = GetSwitchValue(switchString);
            return;
        }
    }

    // Call default implementation
    CCommandLineInfo::ParseParam(pszParam, bFlag, bLast);
}

bool CRmtCommandLineInfo::IsScriptFileSpecified() const {
    return m_scriptFileSpecified;
}


CString CRmtCommandLineInfo::GetSwitchName(const CString& switchString) {
    int nPos = switchString.Find(':');
    if (nPos != -1) {
        return switchString.Left(nPos);
    }
    return switchString;
}

CString CRmtCommandLineInfo::GetSwitchValue(const CString& switchString) {
    int nPos = switchString.Find(':');
    if (nPos != -1) {
        return switchString.Mid(nPos + 1);
    }
    return "";
}

