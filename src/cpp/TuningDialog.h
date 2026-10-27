#pragma once

#include "resource.h"
#include "TuningTypes.h"

// TuningDlg dialog
class TuningDlg : public CDialog {
    // Construction
public:
    TuningDlg(CWnd* pParent = NULL); // standard constructor

    // Dialog Data
    enum { IDD = IDD_TUNING };

    TTuningSettings m_tuningSettings; // the cents inside included

    // The temperament combo's 23 entries (plan 59 retired seven presets):
    // the combo index maps to the stable temperament number through
    // TEMPERAMENT_NUMBERS; names match Java's ValueSets.properties.
    static const int TEMPERAMENT_COUNT = 23;
    static const int TEMPERAMENT_NUMBERS[TEMPERAMENT_COUNT];
    static const char* const TEMPERAMENT_NAMES[TEMPERAMENT_COUNT];
    static int TemperamentIndexOf(int temperament);

    // Show the dialog
    void Show(const BOOL test);

private:
    // Display "Test" button in Song mode
    BOOL m_test;

protected:
    virtual void DoDataExchange(CDataExchange* pDX); // DDX/DDV support

    // backup values loaded during initialization, which can then be retrieved in case the dialog was canceled
    TTuningSettings m_tuningSettingsBackup;

    // Implementation
protected:
    virtual BOOL OnInitDialog();
    virtual void OnOK();
    DECLARE_MESSAGE_MAP()
public:
    afx_msg void OnTuningTest();
    afx_msg void OnTuningReset();
    afx_msg void OnCancel();
    afx_msg void OnTemperamentChanged();
};
