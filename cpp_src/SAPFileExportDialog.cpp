#include "stdafx.h"
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
    : CDialog(CSAPFileExportDialog::IDD, pParent)
{
    //{{AFX_DATA_INIT(CSAPFileExportDialog)
    m_author = _T("");
    m_date = _T("");
    m_name = _T("");
    m_subsongs = _T("");
    //}}AFX_DATA_INIT
}


void CSAPFileExportDialog::DoDataExchange(CDataExchange* pDX)
{
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

    dlg.m_author = sapFile.m_author;
    dlg.m_name = sapFile.m_name;
    dlg.m_date = sapFile.m_date;

    song.GetSubsongParts(dlg.m_subsongs);

    dlg.m_title.Format("Export as SAP File of Type '%s'", sapFile.m_type);
    if (dlg.DoModal() != IDOK)
    {
        return false;
    }

    sapFile.m_author = dlg.m_author;
    sapFile.m_name = dlg.m_name;
    sapFile.m_date = dlg.m_date;

    // Parses the "Subsongs" line
    CString str = dlg.m_subsongs + " ";	// Add space after the last character for parsing
    str.MakeUpper();
    int subsongs = 0;
    byte subpos[CSAPFile::MAXSUBSONGS]{};
    subpos[0] = 0;					// Start at songline 0 by default
    byte n = 0, isn = 0;

    for (int i = 0; i < str.GetLength(); i++)
    {
        char a = str.GetAt(i);
        if (a >= '0' && a <= '9') { n = (n << 4) + (a - '0'); isn = 1; }
        else
            if (a >= 'A' && a <= 'F') { n = (n << 4) + (a - 'A' + 10); isn = 1; }
            else
            {
                if (isn)
                {
                    subpos[subsongs] = n;
                    subsongs++;
                    if (subsongs >= CSAPFile::MAXSUBSONGS)
                    {
                        break;
                    }
                    isn = 0;
                }
            }
    }
    sapFile.m_songs = subsongs;
    return true;
}

/////////////////////////////////////////////////////////////////////////////
// CSAPFileExportDialog message handlers