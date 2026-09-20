#pragma once


#include "resource.h"
#include "TuningTypes.h"

// TuningDlg dialog
class TuningDlg : public CDialog
{
    // Construction
public:
    TuningDlg(CWnd* pParent = NULL);   // standard constructor

    // Dialog Data
    enum { IDD = IDD_TUNING };

    TTuningSettings m_tuningSettings;
    TTuningRatios m_tuningRatios;

    // Show the dialog
    void Show(const BOOL test);

private:
    // Display "Test" button in Song mode
    BOOL m_test;

protected:
    virtual void DoDataExchange(CDataExchange* pDX);    // DDX/DDV support

    // backup values loaded during initialisation, which can then be retrieved in case the dialog was canceled
    TTuningSettings m_tuningSettingsBackup;
    TTuningRatios m_tuningRatiosBackup;

    // Implementation
protected:
    virtual BOOL OnInitDialog();
    virtual void OnOK();
    DECLARE_MESSAGE_MAP()
public:
    afx_msg void OnTuningTest();
    afx_msg void OnTuningReset();
    afx_msg void OnCancel();
};
