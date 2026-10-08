#include "StdAfx.h"
#include "SAPFileExportDialog.h"
#include "Song.h"

#ifdef _DEBUG
#define new DEBUG_NEW
#undef THIS_FILE
static char THIS_FILE[] = __FILE__;
#endif

/////////////////////////////////////////////////////////////////////////////
// CSAPFileExportDialog dialog

CSAPFileExportDialog::CSAPFileExportDialog(CWnd* pParent /*=NULL*/)
    : CDialog(CSAPFileExportDialog::IDD, pParent) {
    //{{AFX_DATA_INIT(CSAPFileExportDialog)
    m_author = _T("");
    m_date = _T("");
    m_name = _T("");
    m_subsongs = _T("");
    //}}AFX_DATA_INIT
}

void CSAPFileExportDialog::DoDataExchange(CDataExchange* pDX) {
    CDialog::DoDataExchange(pDX);
    //{{AFX_DATA_MAP(CSAPFileExportDialog)
    DDX_Text(pDX, IDC_AUTHOR, m_author);
    DDX_Text(pDX, IDC_DATE, m_date);
    DDX_Text(pDX, IDC_NAME, m_name);
    DDX_Text(pDX, IDC_SUBSONGS, m_subsongs);
    //}}AFX_DATA_MAP
}

BEGIN_MESSAGE_MAP(CSAPFileExportDialog, CDialog)
//{{AFX_MSG_MAP(CSAPFileExportDialog)
// NOTE: the ClassWizard will add message map macros here
//}}AFX_MSG_MAP
END_MESSAGE_MAP()

bool CSAPFileExportDialog::Show(const CSong& song, CSAPFile& sapFile) {
    CSAPFileExportDialog dlg;
    sapFile.Init(song);

    dlg.m_author = sapFile.GetAuthor();
    dlg.m_name = sapFile.GetName();
    dlg.m_date = sapFile.GetDate();

    song.GetSubsongParts(dlg.m_subsongs);

    dlg.m_title.Format("Export as SAP File of Type '%s'", sapFile.GetType());
    if (dlg.DoModal() != IDOK) {
        return false;
    }

    sapFile.SetAuthor(dlg.m_author);
    sapFile.SetName(dlg.m_name);
    sapFile.SetDate(dlg.m_date);

    std::vector<int> positions;
    sapFile.SetSongs(CSAPFile::ParseSubsongs(dlg.m_subsongs, &positions)); // Parses the "Subsongs" line
    sapFile.SetSubsongPositions(positions);
    return true;
}

/////////////////////////////////////////////////////////////////////////////
// CSAPFileExportDialog message handlers