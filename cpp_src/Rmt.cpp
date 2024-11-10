// Rmt.cpp : Defines the class behaviors for the application.
// originally made by Raster, 2002-2009
// reworked by VinsCool, 2021-2022
//

#include "stdafx.h"
#include "Rmt.h"
#include "MainFrm.h"
#include "RmtDoc.h"
#include "RmtView.h"
#include "Song.h"
#include "SongExporterTest.h"
#include "AboutDialog.h"
#include "GuiHelpers.h" // For g_statusBar

#ifdef _DEBUG
#define new DEBUG_NEW
#undef THIS_FILE
static char THIS_FILE[] = __FILE__;
#endif

// Some information for the about box is supplied by components outside this file
extern CString g_aboutpokey;
extern CString g_about6502;

extern CSong g_Song;

/////////////////////////////////////////////////////////////////////////////
// CRmtApp

BEGIN_MESSAGE_MAP(CRmtApp, CWinApp)
    //{{AFX_MSG_MAP(CRmtApp)
    ON_COMMAND(ID_APP_ABOUT, OnAppAbout)
    // NOTE - the ClassWizard will add and remove mapping macros here.
    //    DO NOT EDIT what you see in these blocks of generated code!
//}}AFX_MSG_MAP
// Standard file based document commands
ON_COMMAND(ID_FILE_NEW, CWinApp::OnFileNew)
ON_COMMAND(ID_FILE_OPEN, CWinApp::OnFileOpen)
// Standard print setup command
ON_COMMAND(ID_FILE_PRINT_SETUP, CWinApp::OnFilePrintSetup)
END_MESSAGE_MAP()

/////////////////////////////////////////////////////////////////////////////
// CRmtApp construction

CRmtApp::CRmtApp()
{
    // Place all significant initialization in InitInstance.
}

/////////////////////////////////////////////////////////////////////////////
// The one and only CRmtApp object

CRmtApp theApp;

/////////////////////////////////////////////////////////////////////////////
// CRmtApp initialization

BOOL CRmtApp::InitInstance()
{
    // Standard initialization
    // If you are not using these features and wish to reduce the size
    // of your final executable, you should remove from the following
    // the specific initialization routines you do not need.

#ifdef _AFXDLL
//	Enable3dControls();			// Call this when using MFC in a shared DLL
#else
    Enable3dControlsStatic();	// Call this when linking to MFC statically
#endif

    // Set the registry key under which our settings are stored.
    // TODO: Are we storing something there really?
    SetRegistryKey(_T("RASTER Music Tracker"));

    LoadStdProfileSettings();  // Load standard INI file options (including MRU)

    CoInitialize(NULL);
    // Register the application's document templates.  Document templates
    //  serve as the connection between documents, frame windows and views.

    CSingleDocTemplate* pDocTemplate;
    pDocTemplate = new CSingleDocTemplate(
        IDR_MAINFRAME,
        RUNTIME_CLASS(CRmtDoc),
        RUNTIME_CLASS(CMainFrame),       // main SDI frame window
        RUNTIME_CLASS(CRmtView));
    AddDocTemplate(pDocTemplate);

    g_Song.ClearSong(8);

    // TODO Control via command line
    //CSongExporterTest::Test();
    //return FALSE;

    // Parse command line for standard shell commands, DDE, file open
    CCommandLineInfo cmdInfo;
    ParseCommandLine(cmdInfo);

    // Dispatch commands specified on the command line
    if (!ProcessShellCommand(cmdInfo))
    {
        return FALSE;
    }

    // The one and only window has been initialized, so show and update it.
    CMainFrame* mainFrame = (CMainFrame*)GetMainWnd();
    g_statusBar = &mainFrame->m_wndStatusBar;
    m_pMainWnd->ShowWindow(SW_SHOW);
    m_pMainWnd->UpdateWindow();

    // Initialization of a random number
    srand((unsigned)time(NULL));

    //PostMessage(m_pMainWnd->m_hWnd,WM_COMMAND,ID_APP_ABOUT,0); //so that the dialogue can be triggered first

    return TRUE;
}

// App command to run the dialog
void CRmtApp::OnAppAbout()
{

    CAboutDialog::Show(g_about6502, g_aboutpokey);
}

/////////////////////////////////////////////////////////////////////////////
// CRmtApp message handlers

