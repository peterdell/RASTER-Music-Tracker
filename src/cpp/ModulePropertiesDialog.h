#pragma once

#include "resource.h"

#include "General.h"
#include "TrackerDriverVersion.h"
#include "TypedComboBox.h"

/////////////////////////////////////////////////////////////////////////////
// CModulePropertiesDialog dialog
//
// "Module Properties" (IDD_FILE_PROPERTIES), behind File > Properties... and
// Alt+Enter: the properties of the module being edited - the video standard,
// the tracker driver version and, through the Tuning... button, how its notes
// are tuned. All three were in the Options dialog's "Module Defaults" group
// until plan 44; they belong to the song (CSong::IsNTSC(),
// GetTrackerDriverVersion(), GetTuning()), not to the program. They left the
// Options dialog as they arrived here, so there is still exactly one control
// for each. Where they are persisted is unchanged (rmt.ini and tuning.ini).
//
// The dialog template was already in Rmt.rc, designed and never used; this is
// the class it was waiting for. CRmtView::OnFileProperties() fills it in and
// applies what it returns, as OnToolsOptions() does for the Options dialog.

class CModulePropertiesDialog : public CDialog {
    // Construction
public:
    CModulePropertiesDialog(CWnd* pParent = NULL); // standard constructor

    // Dialog Data
    //{{AFX_DATA(CModulePropertiesDialog)
    enum { IDD = IDD_FILE_PROPERTIES };
    CTypedComboBox<TrackerDriverVersion> m_trackerDriverVersionComboBox;
    BOOL m_ntsc;
    TrackerDriverVersion m_trackerDriverVersion;
    //}}AFX_DATA

    // Overrides
    // ClassWizard generated virtual function overrides
    //{{AFX_VIRTUAL(CModulePropertiesDialog)
protected:
    virtual void DoDataExchange(CDataExchange* pDX); // DDX/DDV support
    //}}AFX_VIRTUAL

    // Implementation
protected:
    // Generated message map functions
    //{{AFX_MSG(CModulePropertiesDialog)
    virtual BOOL OnInitDialog();
    virtual void OnOK();
    //}}AFX_MSG

    afx_msg void OnClickedModuleTuning();
    afx_msg void OnTrackerDriverVersionChanged();
    DECLARE_MESSAGE_MAP()

private:
    void UpdateTuningButton();
};
