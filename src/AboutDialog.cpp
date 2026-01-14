#include "StdAfx.h"
#include "AboutDialog.h"


CAboutDialog::CAboutDialog() : CDialog(CAboutDialog::IDD)
{
    //{{AFX_DATA_INIT(CAboutDialog)
    m_rmtversion = _T("");
    m_rmtauthor = _T("");
    m_about6502 = _T("");
    m_aboutpokey = _T("");
    //}}AFX_DATA_INIT
}

void CAboutDialog::DoDataExchange(CDataExchange* pDX)
{
    CDialog::DoDataExchange(pDX);
    //{{AFX_DATA_MAP(CAboutDialog)
    DDX_Text(pDX, IDC_RMTVERSION, m_rmtversion);
    DDX_Text(pDX, IDC_RMTAUTHOR, m_rmtauthor);
    DDX_Text(pDX, IDC_ABOUT6502, m_about6502);
    DDX_Text(pDX, IDC_ABOUTPOKEY, m_aboutpokey);
    //}}AFX_DATA_MAP
}

BEGIN_MESSAGE_MAP(CAboutDialog, CDialog)
    //{{AFX_MSG_MAP(CAboutDialog)
        // No message handlers
    //}}AFX_MSG_MAP
END_MESSAGE_MAP()

void CAboutDialog::Show(const CString& about6502, const CString& aboutPokey)
{
    CAboutDialog aboutDlg;
    aboutDlg.m_rmtversion.LoadString(IDS_RMTVERSION);
    aboutDlg.m_rmtauthor.LoadString(IDS_RMTAUTHOR);

    aboutDlg.m_aboutpokey = aboutPokey;
    aboutDlg.m_aboutpokey.Replace("\n", "\x0d\x0a");
    aboutDlg.m_about6502 = about6502;
    aboutDlg.m_about6502.Replace("\n", "\x0d\x0a");

    aboutDlg.DoModal();
}
