// ModulePropertiesDialog.cpp : implementation file
//

#include "Global.h"
#include "ModulePropertiesDialog.h"
#include "StdAfx.h"
#include "TuningDialog.h"

extern CSong g_Song;

#ifdef _DEBUG
#define new DEBUG_NEW
#undef THIS_FILE
static char THIS_FILE[] = __FILE__;
#endif

/////////////////////////////////////////////////////////////////////////////
// CModulePropertiesDialog dialog

CModulePropertiesDialog::CModulePropertiesDialog(CWnd* pParent /*=NULL*/)
    : CDialog(CModulePropertiesDialog::IDD, pParent)
{
    //{{AFX_DATA_INIT(CModulePropertiesDialog)
    m_ntsc = FALSE;
    m_trackerDriverVersion = TrackerDriverVersion::NONE;
    //}}AFX_DATA_INIT
}

void CModulePropertiesDialog::DoDataExchange(CDataExchange* pDX)
{
    CDialog::DoDataExchange(pDX);
    //{{AFX_DATA_MAP(CModulePropertiesDialog)
    DDX_Check(pDX, IDC_NTSC, m_ntsc);
    DDX_Control(pDX, IDC_TRACKERDRIVERVERSION, m_trackerDriverVersionComboBox);
    //}}AFX_DATA_MAP
}

BEGIN_MESSAGE_MAP(CModulePropertiesDialog, CDialog)
    //{{AFX_MSG_MAP(CModulePropertiesDialog)
    ON_BN_CLICKED(ID_MODULE_TUNING, OnClickedModuleTuning)
    //}}AFX_MSG_MAP
END_MESSAGE_MAP()

/////////////////////////////////////////////////////////////////////////////
// CModulePropertiesDialog message handlers

BOOL CModulePropertiesDialog::OnInitDialog()
{
    CDialog::OnInitDialog();

    m_trackerDriverVersionComboBox.AddItem(TrackerDriverVersion::UNPATCHED, "RMT 1.28 Unpatched by Raster");
    m_trackerDriverVersionComboBox.AddItem(TrackerDriverVersion::PATCH3, "RMT 1.25 Patch 3 by Analmux");
    m_trackerDriverVersionComboBox.AddItem(TrackerDriverVersion::PATCH6, "RMT 1.27 Patch 6 by Analmux");
    m_trackerDriverVersionComboBox.AddItem(TrackerDriverVersion::PATCH8, "RMT 1.28 Patch 8 by Analmux");
    m_trackerDriverVersionComboBox.AddItem(TrackerDriverVersion::PATCH16, "RMT 1.28 Patch 16 by VinsCool");
    m_trackerDriverVersionComboBox.AddItem(TrackerDriverVersion::PATCH_PRINCE_OF_PERSIA, "RMT 1.28 Patch Prince of Persia by VinsCool");

    m_trackerDriverVersionComboBox.SetSelectedItem(m_trackerDriverVersion);

    return TRUE;
}

/// <summary>
/// The tuning of this module, as COptionsDialog::OnClickedOptionsTuning() did
/// it from the Options dialog: the tuning dialog writes the song's tuning
/// itself, outside this dialog's OK/Cancel round trip.
/// </summary>
void CModulePropertiesDialog::OnClickedModuleTuning()
{
    TuningDlg dlg;
    dlg.m_tuningSettings = g_Song.GetTuning();
    dlg.m_tuningRatios = g_Song.GetTuningRatios();

    dlg.DoModal();
}

/// <summary>
/// Apply the changes not handled by the DoDataExchange function:
/// the tracker driver version combo
/// </summary>
void CModulePropertiesDialog::OnOK()
{
    m_trackerDriverVersion = m_trackerDriverVersionComboBox.GetSelectedItem(TrackerDriverVersion::NONE);
    CDialog::OnOK();
}
