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
#include "RmtCommandLineInfo.h"

#ifdef _DEBUG
#define new DEBUG_NEW
#undef THIS_FILE
static char THIS_FILE[] = __FILE__;
#endif


extern CString g_prgpath;					//path to the directory from which the program was started (including a slash at the end)


// Some information for the about box is supplied by components outside this file
extern CString g_aboutpokey;
extern CString g_about6502;

extern CSong g_Song;


//bool IsSwitch(const CString& switchString, const CString& switchName)



CString CRmtCommandLineInfo::GetScriptFilePath() const
{
    return m_scriptFilePath;
}

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


    CString fullPath;
    DWORD pathLen = ::GetModuleFileName(NULL, fullPath.GetBufferSetLength(MAX_PATH + 1), MAX_PATH);
    fullPath.ReleaseBuffer(pathLen); // Note that ReleaseBuffer doesn't need a +1 for the null byte.
    int nPos = fullPath.ReverseFind('\\');
    if (nPos != -1) {
        g_prgpath = fullPath.Left(nPos + 1);

    }
    else {
        g_prgpath = fullPath;
    }

    // Parse command line for standard shell commands, DDE, file open
    CRmtCommandLineInfo cmdInfo;
    ParseCommandLine(cmdInfo);

    // Dispatch standard commands specified on the command line.
    // Will return FALSE if app was launched with /RegServer, /Register, /Unregserver or /Unregister.
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


    // Dispatch additional interactive commands specified on the command line.
    switch (cmdInfo.m_nShellCommand) {
    case CCommandLineInfo::FileOpen: {
        g_Song.FileOpen(cmdInfo.m_strFileName, FALSE);
        break;
    }
    }

    // Dispatch additional commands specified on the command line.
    if (cmdInfo.IsScriptFileSpecified()) {
        CFile scriptFile;
        if (!scriptFile.Open(cmdInfo.GetScriptFilePath(), CFile::modeRead)) {
            SendErrorMessage("Command Line Parameter Invalid", "The script file \"" + scriptFile.GetFilePath() + "\" specified via the command line switch /SCRIPT cannot be opened for reading.");
            return FALSE;
        }
        CSongExporterTest::Test(g_Song);
        return FALSE;
    }

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

