// TuningDlg.cpp : implementation file
//


#include "StdAfx.h"
#include "Tuning.h"
#include "TuningDialog.h"

// TODO Make parameter
#include "Global.h"

extern CSong g_Song;

#ifdef _DEBUG
#define new DEBUG_NEW
#undef THIS_FILE
static char THIS_FILE[] = __FILE__;
#endif

extern CTuning g_Tuning;

// TuningDlg dialog
TuningDlg::TuningDlg(CWnd* pParent /*=nullptr*/)
    : CDialog(IDD_TUNING, pParent)
{

    m_tuningSettings = {};
}

void TuningDlg::Show(const BOOL test) {
    this->m_test = test;
    this->DoModal();
}

void TuningDlg::DoDataExchange(CDataExchange* pDX)
{
    CDialog::DoDataExchange(pDX);
    DDX_Text(pDX, IDC_BASETUNING, m_tuningSettings.basetuning);
    DDV_MinMaxDouble(pDX, m_tuningSettings.basetuning, 6.875, 7040);	//should be more than enough...
    DDX_CBIndex(pDX, IDC_BASENOTE, m_tuningSettings.basenote);
    // The temperament combo index is not the temperament number anymore -
    // plan 59 retired seven presets, so the 23 entries map through
    // TEMPERAMENT_NUMBERS (OnInitDialog() fills the combo accordingly).
    if (pDX->m_bSaveAndValidate) {
        int sel = 0;
        DDX_CBIndex(pDX, IDC_TEMPERAMENT, sel);
        m_tuningSettings.temperament = TEMPERAMENT_NUMBERS[sel];
    } else {
        int sel = TemperamentIndexOf(m_tuningSettings.temperament);
        DDX_CBIndex(pDX, IDC_TEMPERAMENT, sel);
    }
    // The 12 custom-temperament values in musical cents (plan 59): the
    // tonic is fixed at 0, the octave at 1200 (2:1) and has no field.
    static const int CENTS_FIELD_IDS[TTuningCents::COUNT] = {
        IDC_UNISON_L, IDC_MINOR_2ND_L, IDC_MAJOR_2ND_L, IDC_MINOR_3RD_L, IDC_MAJOR_3RD_L, IDC_PERFECT_4TH_L,
        IDC_TRITONE_L, IDC_PERFECT_5TH_L, IDC_MINOR_6TH_L, IDC_MAJOR_6TH_L, IDC_MINOR_7TH_L, IDC_MAJOR_7TH_L
    };
    m_tuningSettings.cents.values[0] = 0;
    for (int i = 0; i < TTuningCents::COUNT; i++) {
        DDX_Text(pDX, CENTS_FIELD_IDS[i], m_tuningSettings.cents.values[i]);
        if (i > 0) {
            DDV_MinMaxDouble(pDX, m_tuningSettings.cents.values[i], 0, 1200);
        }
    }
}

// The combo entries in number order; must match Java's Temperament value
// set and ValueSets.properties (plan 59 retired numbers 5, 14 and 24-28).
const int TuningDlg::TEMPERAMENT_NUMBERS[TEMPERAMENT_COUNT] = {
    0, 1, 2, 3, 4, 6, 7, 8, 9, 10, 11, 12, 13, 15, 16, 17, 18, 19, 20, 21, 22, 23, CTuning::TUNING_CUSTOM
};

const char* const TuningDlg::TEMPERAMENT_NAMES[TEMPERAMENT_COUNT] = {
    "Equal Temperament (Default)",
    "Thomas Young 1799's Well Temperament no.1",
    "Thomas Young 1799's Well Temperament no.2",
    "Thomas Young 1807's Well Temperament",
    "Andreas Werckmeister's Temperament III (1681)",
    "d'Alembert and Rousseau Tempérament Ordinaire (1752/1767)",
    "Aron - Neidhardt Equal Beating Well Temperament",
    "Atom Schisma Scale",
    "12-TET Approximation with Minimal Order 17 Beats",
    "Paul Bailey's Modern Well Temperament (2002)",
    "John Barnes' Temperament (1977) Made After Analysis of Wohltemperierte Klavier",
    "Bethisy Tempérament Ordinaire",
    "Big Gulp",
    "This Scale May Also be Called the \"Wedding Cake\"",
    "Upside Down Wedding Cake (Divorce Cake)",
    "12 Tone Pythagorean Scale",
    "Robert Schneider, Scale of Log(4) ..Log(16)",
    "Zarlino Tempérament Extraordinaire",
    "Fokker's 7-Limit 12-Tone Just Scale",
    "Bach Temperament, A- = 400hz",
    "Vallotti & Young Scale (Vallotti Version), Also Known as Tartini-Vallotti (1754)",
    "Vallotti-Young and Werckmeister III, 10 Cents 5-Limit Lesfip Scale",
    "Custom Temperament in Cents"
};

int TuningDlg::TemperamentIndexOf(int temperament) {
    for (int i = 0; i < TEMPERAMENT_COUNT; i++) {
        if (TEMPERAMENT_NUMBERS[i] == temperament) {
            return i;
        }
    }
    return 0; // a retired or unknown number shows as equal
}

BEGIN_MESSAGE_MAP(TuningDlg, CDialog)
    ON_BN_CLICKED(ID_TUNING_TEST, OnTuningTest)
    ON_BN_CLICKED(ID_TUNING_RESET, OnTuningReset)
    ON_BN_CLICKED(IDCANCEL, OnCancel)
    ON_CBN_SELCHANGE(IDC_TEMPERAMENT, OnTemperamentChanged)
END_MESSAGE_MAP()

// TuningDlg message handlers
BOOL TuningDlg::OnInitDialog()
{
    CDialog::OnInitDialog();

    // Backup all current values first
    m_tuningSettingsBackup = g_Song.GetTuningSettings();

    // Refill the temperament combo: the resource's DLGINIT still lists the
    // 30 pre-plan-59 entries, the 23 current ones live in TEMPERAMENT_NAMES.
    CComboBox* temperament = (CComboBox*)GetDlgItem(IDC_TEMPERAMENT);
    temperament->ResetContent();
    for (int i = 0; i < TEMPERAMENT_COUNT; i++) {
        temperament->AddString(TEMPERAMENT_NAMES[i]);
    }
    temperament->SetCurSel(TemperamentIndexOf(m_tuningSettings.temperament));

    // The combo selection is set, enable the cents fields accordingly
    OnTemperamentChanged();

    return TRUE;  // return TRUE unless you set the focus to a control
    // EXCEPTION: OCX Property Pages should return FALSE
}

void TuningDlg::OnOK()
{
    OnTuningTest();

    CDialog::OnOK();
}

void TuningDlg::OnTuningTest()
{

    // Get current screen values.
    TuningDlg::UpdateData();

    g_Song.GetTuningSettings() = m_tuningSettings;

    g_Tuning.InitTuning();
}

void TuningDlg::OnTuningReset()
{
    // Retrieve the last backed up values.
    g_Song.GetTuningSettings() = m_tuningSettingsBackup;

    g_Tuning.InitTuning();
}

void TuningDlg::OnTemperamentChanged()
{
    // Only the custom temperament plays the cents fields, so they are only
    // enabled there; the tonic (unison) stays fixed at 0 cents.
    static const int centsFieldIds[] = {
        IDC_MINOR_2ND_L, IDC_MAJOR_2ND_L, IDC_MINOR_3RD_L, IDC_MAJOR_3RD_L, IDC_PERFECT_4TH_L, IDC_TRITONE_L,
        IDC_PERFECT_5TH_L, IDC_MINOR_6TH_L, IDC_MAJOR_6TH_L, IDC_MINOR_7TH_L, IDC_MAJOR_7TH_L
    };
    int sel = ((CComboBox*)GetDlgItem(IDC_TEMPERAMENT))->GetCurSel();
    BOOL enable = sel >= 0 && TEMPERAMENT_NUMBERS[sel] == CTuning::TUNING_CUSTOM;
    GetDlgItem(IDC_UNISON_L)->EnableWindow(FALSE);
    for (int id : centsFieldIds) {
        GetDlgItem(id)->EnableWindow(enable);
    }
}

void TuningDlg::OnCancel()
{
    // Reset the values from the last backup.
    OnTuningReset();
    // And done, nothing else to be done here.
    CDialog::OnCancel();
}
