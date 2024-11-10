#include "stdafx.h"
#include "AboutDialog.h"


CAboutDialog::CAboutDialog() : CDialog(CAboutDialog::IDD)
{
    //{{AFX_DATA_INIT(CAboutDialog)
    m_rmtversion = _T("");
    m_rmtauthor = _T("");
    m_credits = _T("");
    m_about6502 = _T("");
    m_aboutpokey = _T("");
    //}}AFX_DATA_INIT
}

void CAboutDialog::DoDataExchange(CDataExchange* pDX)
{
    CDialog::DoDataExchange(pDX);
    //{{AFX_DATA_MAP(CAboutDialog)
    DDX_Text(pDX, IDC_RMTVERSION, m_rmtversion);
    DDX_Text(pDX, IDC_RMTAUTHOR, m_rmtauthor);
    DDX_Text(pDX, IDC_CREDITS, m_credits);
    DDX_Text(pDX, IDC_ABOUT6502, m_about6502);
    DDX_Text(pDX, IDC_ABOUTPOKEY, m_aboutpokey);
    //}}AFX_DATA_MAP
}

BEGIN_MESSAGE_MAP(CAboutDialog, CDialog)
    //{{AFX_MSG_MAP(CAboutDialog)
        // No message handlers
    //}}AFX_MSG_MAP
END_MESSAGE_MAP()

void CAboutDialog::Show(const CString& about6502, const CString& aboutPokey)
{
    CAboutDialog aboutDlg;
    aboutDlg.m_rmtversion.LoadString(IDS_RMTVERSION);
    aboutDlg.m_rmtauthor.LoadString(IDS_RMTAUTHOR);
    aboutDlg.m_credits =
        "Bob!k/C.P.U., JirkaS/C.P.U., PG, Fandal, Atari800 emulator developer team, Fox/Taquart, Jaskier/Taquart, Tatqoo/Taquart, Sack/Cosine, Grayscale music band, LiSU, Miker, Dely\n\n"
        "RASTER Music Tracker is now open source! Contributions are welcome, and appreciated, for making this program better in any way!\n\n"
        "Special thanks to Bob!k and Fandal for originally granting me the permission to access the original sources, and ultimately agree to allow me setting up a Github repository.\n\n"
        "Also thanks to Mathy for the original email communication, this is where this adventure took a new and unexpected twist!\n\n"
        "I also want to thank Ivop for helping me figure out how to use git, and how to set up the repository from the original sources, where I have then added my own contributions for the last months.\n\n"
        "--------------------------------------------------------------------------------------\n\n"
        "Additional credits since version 1.31.0:\n\n"
        "New features, bugfixes and improvements by VinsCool\n\n"
        "POKEY Tuning Calculations programming by VinsCool, with helpful advices from synthpopalooza and OPNA2608\n\n"
        "SAP-R Dumper and VUPlayer programming by VinsCool\n\n"
        "LZSS compression programming by DMSC, C++ port by VinsCool\n\n"
        "Unrolled LZSS music driver by Rensoupp, with few changes and new features by VinsCool\n\n"
        "New Bitmap graphics, ideas and beta testing by PG\n\n"
        "Ideas, features suggestions and inspiration by PG, Enderdude, Spring, Ivop, Tatqoo, Miker\n\n"
        "Spiteful inspiration by Rensoupp, Emkay, and anyone who challenged me to try doing things believed impossible or outside of my abilities ;)\n\n"
        "Special thanks to everyone from The Chiptune Café, Atari Age, and GBAtemp who motivated me to work harder on the revival of RMT!\n\n"
        "If you think anyone was forgotten or credits were incorrectly attributed, feel free to pester VinsCool about it :D\n\n"
        "And now, time to make some Atari 8-bit music! Thanks to all dedicated fans for keeping the scene alive!";

    aboutDlg.m_credits.Replace("\n", "\x0d\x0a");
    aboutDlg.m_aboutpokey = aboutPokey;
    aboutDlg.m_aboutpokey.Replace("\n", "\x0d\x0a");
    aboutDlg.m_about6502 = about6502;
    aboutDlg.m_about6502.Replace("\n", "\x0d\x0a");

    aboutDlg.DoModal();
}
