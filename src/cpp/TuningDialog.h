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
    // TEMPERAMENT_NUMBERS; names match Java's ValueSets.properties. Display
    // order (plan 59 step 3): Equal pinned first, Custom pinned last, the
    // presets in between alphabetical by name.
    static const int TEMPERAMENT_COUNT = 23;
    static const int TEMPERAMENT_NUMBERS[TEMPERAMENT_COUNT];
    static const char* const TEMPERAMENT_NAMES[TEMPERAMENT_COUNT];
    static int TemperamentIndexOf(int temperament);

    // Show the dialog
    void Show(const BOOL test);

private:
    // Display "Test" button in Song mode
    BOOL m_test;

    // The cents fields show the selected temperament's cents: computed and
    // read-only for equal temperament and the presets, editable only for the
    // custom temperament. What was typed for the custom temperament is kept
    // here while another temperament is shown.
    static const int CENTS_FIELD_IDS[TTuningCents::COUNT];
    CString m_customCentsText[TTuningCents::COUNT];
    int m_shownTemperament = -1; // the temperament number the fields show, -1 for none yet
    void ShowCents();
    CString CustomCentsText(int i);
    bool CustomCentsTextsValid();
    static CString FormatCents(double cents);
    static bool ParseCents(const CString& text, double& cents);

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
