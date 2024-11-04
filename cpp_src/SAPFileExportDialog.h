#pragma once

#include "Resource.h"

#include "SAPFile.h"

class CSong;


/////////////////////////////////////////////////////////////////////////////
// CSAPFileExportDialog dialog

class CSAPFileExportDialog : public CDialog
{
    // Construction
public:
    CSAPFileExportDialog(CWnd* pParent = NULL);   // standard constructor

    static bool Show(const CSong& song, CSAPFile& sapFile);

    // Dialog Data
    CString m_title;
    //{{AFX_DATA(CSAPFileExportDialog)
    enum { IDD = IDD_EXPSAP };
    CString	m_author;
    CString	m_date;
    CString	m_name;
    CString	m_subsongs;
    //}}AFX_DATA


// Overrides
    // ClassWizard generated virtual function overrides
    //{{AFX_VIRTUAL(CSAPFileExportDialog)
protected:
    virtual void DoDataExchange(CDataExchange* pDX);    // DDX/DDV support
    //}}AFX_VIRTUAL

// Implementation
protected:

    // Generated message map functions
    //{{AFX_MSG(CSAPFileExportDialog)
        // NOTE: the ClassWizard will add member functions here
    //}}AFX_MSG
    DECLARE_MESSAGE_MAP()

    virtual BOOL OnInitDialog() override {
        CDialog::OnInitDialog();
        SetWindowText(m_title);
        return TRUE;
    }
};
