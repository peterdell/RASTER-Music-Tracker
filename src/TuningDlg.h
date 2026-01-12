#pragma once


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
    afx_msg void OnClickedIdtestnow();
    afx_msg void OnClickedIdreset();
    afx_msg void OnBnClickedCancel();
};
