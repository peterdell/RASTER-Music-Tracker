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
    // tonic is fixed at 0, the octave at 1200 (2:1) and has no field. The
    // fields show the selected temperament (ShowCents() fills them, also
    // initially), so only saving reads them - and only the custom values,
    // whichever temperament is shown.
    if (!pDX->m_bSaveAndValidate) {
        return;
    }
    m_tuningSettings.cents.values[0] = 0;
    if (m_shownTemperament != CTuning::TUNING_CUSTOM) {
        if (CustomCentsTextsValid()) {
            for (int i = 1; i < TTuningCents::COUNT; i++) {
                ParseCents(m_customCentsText[i], m_tuningSettings.cents.values[i]);
            }
            return;
        }
        // An invalid custom value is hidden while another temperament is
        // shown: switch back to the custom temperament, so the DDV prompt
        // below can point at the offending field.
        ((CComboBox*)GetDlgItem(IDC_TEMPERAMENT))->SetCurSel(TemperamentIndexOf(CTuning::TUNING_CUSTOM));
        ShowCents();
        m_tuningSettings.temperament = CTuning::TUNING_CUSTOM;
    }
    for (int i = 1; i < TTuningCents::COUNT; i++) {
        DDX_Text(pDX, CENTS_FIELD_IDS[i], m_tuningSettings.cents.values[i]);
        DDV_MinMaxDouble(pDX, m_tuningSettings.cents.values[i], 0, CENTS_PER_OCTAVE);
    }
}

const int TuningDlg::CENTS_FIELD_IDS[TTuningCents::COUNT] = {
    IDC_UNISON_L, IDC_MINOR_2ND_L, IDC_MAJOR_2ND_L, IDC_MINOR_3RD_L, IDC_MAJOR_3RD_L, IDC_PERFECT_4TH_L,
    IDC_TRITONE_L, IDC_PERFECT_5TH_L, IDC_MINOR_6TH_L, IDC_MAJOR_6TH_L, IDC_MINOR_7TH_L, IDC_MAJOR_7TH_L
};

// The combo entries (plan 59 step 3): Equal pinned first, Custom pinned
// last, the 21 presets in between alphabetical by name - the same order
// Java's TuningDialog.temperamentsInDisplayOrder() computes, pinned by
// OptionsDialogsTest. The names match ValueSets.properties; each preset's
// values were verified against its Scala archive source file (see
// plans/59_TUNING_KEY_FIX_PLAN.md, step 3).
const int TuningDlg::TEMPERAMENT_NUMBERS[TEMPERAMENT_COUNT] = {
    0, 7, 8, 21, 10, 11, 12, 13, 6, 9, 20, 17, 18, 16, 22, 23, 15, 4, 3, 1, 2, 19, CTuning::TUNING_CUSTOM
};

const char* const TuningDlg::TEMPERAMENT_NAMES[TEMPERAMENT_COUNT] = {
    "Equal Temperament (Default)",
    "Aron-Neidhardt Equal Beating Well Temperament",
    "Atom Schisma Scale",
    "Bach Temperament by Sparschuh (A4 = 400 Hz)",
    "Bailey Modern Well Temperament (2002)",
    "Barnes Well-Tempered Clavier Temperament (1977)",
    "Béthizy Tempérament Ordinaire (1754)",
    "Big Gulp",
    "d'Alembert-Rousseau Tempérament Ordinaire (1752/1767)",
    "Equal Temperament Approximation (Minimal Order 17 Beats)",
    "Fokker 7-Limit Just Scale",
    "Pythagorean Scale",
    "Schneider Logarithmic Scale, log(4) to log(16)",
    "Upside-Down Wedding Cake (Divorce Cake)",
    "Vallotti Temperament (1754)",
    "Vallotti-Young/Werckmeister III Lesfip Scale (5-Limit, 10 Cents)",
    "Wedding Cake",
    "Werckmeister III Well Temperament (1691)",
    "Young Well Temperament (1807)",
    "Young Well Temperament No. 1 (1800)",
    "Young Well Temperament No. 2 (1799)",
    "Zarlino Tempérament Extraordinaire (Schulter)",
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

    // The custom cents as the fields will show them when the custom
    // temperament is selected - in DDX_Text's own number format
    for (int i = 0; i < TTuningCents::COUNT; i++) {
        m_customCentsText[i].Format("%.*g", DBL_DIG, m_tuningSettings.cents.values[i]);
    }

    // The combo selection is set, show its cents
    m_shownTemperament = -1;
    ShowCents();

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
    ShowCents();
}

// The fields show the cents of the selected temperament: computed and
// read-only for equal temperament and the presets, the user's own and
// editable for the custom temperament (the tonic stays fixed at 0). What was
// typed for the custom temperament is kept while another temperament is
// shown, and comes back when it is selected again.
void TuningDlg::ShowCents()
{
    int sel = ((CComboBox*)GetDlgItem(IDC_TEMPERAMENT))->GetCurSel();
    if (sel < 0) {
        return;
    }
    int selected = TEMPERAMENT_NUMBERS[sel];
    if (selected == m_shownTemperament) {
        return;
    }
    if (m_shownTemperament == CTuning::TUNING_CUSTOM) {
        for (int i = 0; i < TTuningCents::COUNT; i++) {
            GetDlgItemText(CENTS_FIELD_IDS[i], m_customCentsText[i]);
        }
    }
    bool custom = selected == CTuning::TUNING_CUSTOM;
    for (int i = 0; i < TTuningCents::COUNT; i++) {
        SetDlgItemText(CENTS_FIELD_IDS[i], custom ? m_customCentsText[i] : FormatCents(g_Tuning.GetTemperamentCents(selected, i)));
        GetDlgItem(CENTS_FIELD_IDS[i])->EnableWindow(custom && i > 0);
    }
    m_shownTemperament = selected;
}

// The custom temperament's cents text of note i: the field while the custom
// temperament is shown, the kept text otherwise.
CString TuningDlg::CustomCentsText(int i)
{
    if (m_shownTemperament == CTuning::TUNING_CUSTOM) {
        CString text;
        GetDlgItemText(CENTS_FIELD_IDS[i], text);
        return text;
    }
    return m_customCentsText[i];
}

bool TuningDlg::CustomCentsTextsValid()
{
    for (int i = 1; i < TTuningCents::COUNT; i++) {
        double cents;
        if (!ParseCents(CustomCentsText(i), cents) || cents < 0 || cents > CENTS_PER_OCTAVE) {
            return false;
        }
    }
    return true;
}

// A computed cents value for display, rounded to 3 decimals: the presets'
// multipliers carry 6 decimals (their Scala sources' rounding), which is
// worth about 0.001 cents - more digits would be noise.
CString TuningDlg::FormatCents(double cents)
{
    CString text;
    text.Format("%.3f", cents);
    text.TrimRight('0');
    text.TrimRight('.');
    return text;
}

bool TuningDlg::ParseCents(const CString& text, double& cents)
{
    CString trimmed = text;
    trimmed.Trim();
    if (trimmed.IsEmpty()) {
        return false;
    }
    char* end = nullptr;
    cents = strtod(trimmed, &end);
    return end != nullptr && *end == 0 && !isnan(cents);
}

void TuningDlg::OnCancel()
{
    // Reset the values from the last backup.
    OnTuningReset();
    // And done, nothing else to be done here.
    CDialog::OnCancel();
}
