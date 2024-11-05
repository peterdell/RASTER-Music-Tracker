// MainFrm.cpp : implementation of the CMainFrame class
// originally made by Raster 2002-2009
// reworked by VinsCool, 2021-2022
//

#include "stdafx.h"
#include "MainFrm.h"
#include "RmtView.h"
#include "Song.h"
#include "Global.h"
#include "Resource.h"

#ifdef _DEBUG
#define new DEBUG_NEW
#undef THIS_FILE
static char THIS_FILE[] = __FILE__;
#endif

extern CSong g_Song;

/////////////////////////////////////////////////////////////////////////////
// CMainFrame

IMPLEMENT_DYNCREATE(CMainFrame, CFrameWnd)

BEGIN_MESSAGE_MAP(CMainFrame, CFrameWnd)
	//{{AFX_MSG_MAP(CMainFrame)
	ON_WM_CREATE()
	ON_WM_CLOSE()
	ON_WM_GETMINMAXINFO()
	ON_CBN_SELCHANGE(IDC_COMBO_LINESAFTER, OnSelChangedComboSkipLinesAfterNoteInsert)
	ON_CBN_KILLFOCUS(IDC_COMBO_LINESAFTER, OnRestoreFocusToMainWindow)
	ON_COMMAND(1, OnRestoreFocusToMainWindow)	//when you press Enter in the ComboBox
	ON_COMMAND(2, OnRestoreFocusToMainWindow)	//when you press ESCape in ComboBox
	ON_CBN_CLOSEUP(IDC_COMBO_LINESAFTER, OnRestoreFocusToMainWindow)	//at the ComboBox closeup
	//}}AFX_MSG_MAP
END_MESSAGE_MAP()

static UINT indicators[] =
{
	ID_SEPARATOR,           // status line indicator
	ID_INDICATOR_CAPS
};

/////////////////////////////////////////////////////////////////////////////
// CMainFrame construction/destruction

CMainFrame::CMainFrame()
{
	// TODO: add member initialization code here
}

CMainFrame::~CMainFrame()
{

}

int CMainFrame::OnCreate(LPCREATESTRUCT lpCreateStruct)
{
	if (CFrameWnd::OnCreate(lpCreateStruct) == -1)
		return -1;

	if (!m_wndToolBar.CreateEx(this)|| !m_wndToolBar.LoadToolBar(IDR_MAINFRAME))
	{
		TRACE0("Failed to create main toolbar\n");
		return -1;      // fail to create
	}

	// Put the skip # lines after note insert combo box into the tool bar
	// Allow for 0 -> 8 skips
	CRect rect;
	int index = m_wndToolBar.CommandToIndex(ID_BUTTONCOMBO1);
	m_wndToolBar.SetButtonInfo(index, ID_BUTTONCOMBO1, TBBS_SEPARATOR, 40);
	m_wndToolBar.GetItemRect(index, &rect);
	rect.bottom += 300;	// the height of the clicked combobox
	if (!m_comboSkipLinesAfterNoteInsert.Create(WS_CHILD | WS_VISIBLE | WS_VSCROLL | CBS_DROPDOWNLIST, rect, &m_wndToolBar, IDC_COMBO_LINESAFTER))
		return -1;

	char ss[2];
	ss[1]=0;
	for(char i='0'; i<='8'; i++)	//0..8
	{
		ss[0]=i;
		m_comboSkipLinesAfterNoteInsert.AddString(ss);
	}

	m_comboSkipLinesAfterNoteInsert.SetCurSel(1);

	// Create a toolbar with items for block editing mode
	if (!m_ToolBarBlock.CreateEx(this) || !m_ToolBarBlock.LoadToolBar(IDR_TOOLBARBLOCK))
	{
		TRACE0("Failed to create toolbar block\n");
		return -1;      // fail to create
	}

	if (!m_wndStatusBar.Create(this)
		|| !m_wndStatusBar.SetIndicators(indicators, sizeof(indicators) / sizeof(UINT)))
	{
		TRACE0("Failed to create status bar\n");
		return -1;      // fail to create
	}

	if (!m_wndReBar.Create(this) ||
		!m_wndReBar.AddBar(&m_wndToolBar) ||
		!m_wndReBar.AddBar(&m_ToolBarBlock))
	{
		TRACE0("Failed to create rebar\n");
		return -1;      // fail to create
	}

	m_wndToolBar.SetBarStyle(m_wndToolBar.GetBarStyle() | CBRS_TOOLTIPS | CBRS_FLYBY);

	m_ToolBarBlock.SetBarStyle(m_ToolBarBlock.GetBarStyle() | CBRS_TOOLTIPS | CBRS_FLYBY);

	g_viewBlockToolbar	= 1;
	ShowControlBar((CControlBar*)&m_ToolBarBlock,g_viewBlockToolbar,0); //Show the block edit toolbar

	return 0;
}

BOOL CMainFrame::PreCreateWindow(CREATESTRUCT& cs)
{
	//==== Restore main window position
	CWinApp* app = AfxGetApp();
	int s, t, b, r, l;

	// only restore if there is a previously saved position
	if (-1 != (s = app->GetProfileInt("Frame", "Status", -1)) &&
		-1 != (t = app->GetProfileInt("Frame", "Top", -1)) &&
		-1 != (l = app->GetProfileInt("Frame", "Left", -1)) &&
		-1 != (b = app->GetProfileInt("Frame", "Bottom", -1)) &&
		-1 != (r = app->GetProfileInt("Frame", "Right", -1))
		) {

		// restore the window's status
		app->m_nCmdShow = s;

		// restore the window's width and height
		cs.cx = r - l;
		cs.cy = b - t;

		// the following correction is needed when the taskbar is
		// at the left or top and it is not "auto-hidden"
		RECT workArea;
		SystemParametersInfo(SPI_GETWORKAREA, 0, &workArea, 0);
		l += workArea.left;
		t += workArea.top;

		// make sure the window is not completely out of sight
		int max_x = GetSystemMetrics(SM_CXSCREEN) -	GetSystemMetrics(SM_CXICON);
		int max_y = GetSystemMetrics(SM_CYSCREEN) -	GetSystemMetrics(SM_CYICON);
		cs.x = min(l, max_x);
		cs.y = min(t, max_y);
	}

	if( !CFrameWnd::PreCreateWindow(cs) )
		return FALSE;

	cs.style = WS_OVERLAPPED | WS_SYSMENU | WS_BORDER | WS_MINIMIZEBOX | WS_MAXIMIZEBOX | WS_SIZEBOX;
	return TRUE;
}

void CMainFrame::OnSelChangedComboSkipLinesAfterNoteInsert()
{
	g_linesafter = m_comboSkipLinesAfterNoteInsert.GetCurSel();
}

/////////////////////////////////////////////////////////////////////////////
// CMainFrame diagnostics

#ifdef _DEBUG
void CMainFrame::AssertValid() const
{
	CFrameWnd::AssertValid();
}

void CMainFrame::Dump(CDumpContext& dc) const
{
	CFrameWnd::Dump(dc);
}

#endif //_DEBUG

/////////////////////////////////////////////////////////////////////////////
// CMainFrame message handlers

void CMainFrame::OnClose() 
{
	// Save main window position
	CWinApp* app = AfxGetApp();
	WINDOWPLACEMENT wp;
	GetWindowPlacement(&wp);
	app->WriteProfileInt("Frame", "Status", wp.showCmd);
	app->WriteProfileInt("Frame", "Top", wp.rcNormalPosition.top);
	app->WriteProfileInt("Frame", "Left", wp.rcNormalPosition.left);
	app->WriteProfileInt("Frame", "Bottom", wp.rcNormalPosition.bottom);
	app->WriteProfileInt("Frame", "Right", wp.rcNormalPosition.right);

	if (g_closeApplication)
	{
		CFrameWnd::OnClose();
	}
	else
	{
		//it sends a message that it wants to quit
		CRmtView* fw = (CRmtView*)(AfxGetApp()->GetMainWnd());
		fw->PostMessage(WM_COMMAND,ID_WANTEXIT,0);
	}
}

void CMainFrame::OnRestoreFocusToMainWindow()
{
	//removes focus from the ComboBox and puts it in the main window
	CRmtView* fw = (CRmtView*)(AfxGetApp()->GetMainWnd());
	if (fw) fw->SetFocus();
}

void CMainFrame::OnGetMinMaxInfo(MINMAXINFO* lpMMI)
{
	// Not perfect... manual resizing is required if the window is smaller than minimal
	lpMMI->ptMinTrackSize.x = (g_tracks4_8 == 8) ? 1120 : 800;
	lpMMI->ptMinTrackSize.y = 600;
}
