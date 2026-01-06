#include "stdafx.h"
#include "RmtCommandLineInfo.h"



CRmtCommandLineInfo::CRmtCommandLineInfo(void) : m_scriptFileSpecified(false), m_testFileSpecified(false) {
};

CRmtCommandLineInfo::~CRmtCommandLineInfo(void) {
};


bool CRmtCommandLineInfo::IsScriptFileSpecified() const
{
    return m_scriptFileSpecified;
}

CString CRmtCommandLineInfo::GetScriptFilePath() const
{
    return m_scriptFilePath;
}

bool CRmtCommandLineInfo::IsTestFileSpecified() const
{
    return m_testFileSpecified;
}

CString CRmtCommandLineInfo::GetTestFilePath() const
{
    return m_testFilePath;
}


void CRmtCommandLineInfo::ParseParam(const TCHAR* pszParam, BOOL bFlag, BOOL bLast)
{
    // The bFlag is true for parameters of the form "/EXAMPLE:TEST"
    if (bFlag) {
        CString switchString(pszParam);
        CString switchName = GetSwitchName(switchString).MakeUpper();
        if (switchName.Compare("SCRIPT") == 0) {
            m_scriptFileSpecified = true;
            m_scriptFilePath = GetSwitchValue(switchString);
        }
        switchName = GetSwitchName(switchString).MakeUpper();
        if (switchName.Compare("TEST") == 0) {
            m_testFileSpecified = true;
            m_testFilePath = GetSwitchValue(switchString);
        }

        if (m_scriptFileSpecified || m_testFileSpecified) {
            return;
        }
    }

    // Call default implementation
    CCommandLineInfo::ParseParam(pszParam, bFlag, bLast);
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

