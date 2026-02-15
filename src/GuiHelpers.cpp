#include "General.h"
#include "StdAfx.h"

#include "GuiHelpers.h"

#include "Global.h"

#include "RuntimeException.h"
#include <cassert>

#include "Winuser.h"


CStatusBar* g_statusBar = nullptr;


void ClearStatusBar() {
    SetStatusBarText("");
}

void SetStatusBarText(const char* text)
{
    if (g_statusBar == nullptr) {
        OutputDebugString("INFO: ");
        OutputDebugString(text);
        OutputDebugString("\n");
    }
    else {
        g_statusBar->SetWindowText(text);
    }
}

void SendInfoMessage(const char* message) {
    SetStatusBarText(message);
}

void SendErrorMessage(const char* message) {
    SendErrorMessage(nullptr, message);
}

void SendErrorMessage(const char* title, const char* message) {
    if (g_statusBar == nullptr) {
        OutputDebugString("ERROR: ");
        if (title) {
            OutputDebugString(title);
            OutputDebugString("\n");
        }

        OutputDebugString(message);
        OutputDebugString("\n");
    }
    else {
        MessageBox(g_hwnd, message, title, MB_ICONERROR);
    }
}



int DisableEventSection::eventsDisabledCounter = 0;
HCURSOR DisableEventSection::oldCursor = NULL;

DisableEventSection::DisableEventSection() {
    DisableEvents();
}

DisableEventSection::~DisableEventSection() {
    EnableEvents();
}

void DisableEventSection::DisableEvents() {
    if (eventsDisabledCounter == 0) {
        oldCursor = SetCursor(LoadCursor(0, IDC_WAIT));
        EnableWindow(g_hwnd, FALSE);
    }
    eventsDisabledCounter++;
}

void DisableEventSection::EnableEvents() {
    if (eventsDisabledCounter == 0) {
        ThrowRuntimeException("Field eventsDisabledCounter is already 0.");
    }
    eventsDisabledCounter--;
    if (eventsDisabledCounter == 0) {
        SetCursor(oldCursor);
        EnableWindow(g_hwnd, TRUE);
    }
}

static int lastTick;

BOOL RefreshScreen(int frameskip)
{
    // Bail out of this function if it couldn't be performed
    if (!g_hwnd || !g_viewhwnd || g_closeApplication)
        return 0;

    // Frameskip of 1 or higher
    if (frameskip > 0)
    {
        // Frame was already processed
        if (lastTick == g_timerGlobalCount)
            return 0;

        // Skip frame with modulo
        if ((g_timerGlobalCount % frameskip))
            return 0;

        // Remember the last time a frame was processed
        lastTick = g_timerGlobalCount;
    }

    // Force a screen update if the condition is met for it
    AfxGetApp()->GetMainWnd()->Invalidate();
    SCREENUPDATE;
    UpdateWindow(g_viewhwnd);

    // Screen was refreshed
    return 1;
}

int EditText(int vk, int shift, int control, char* txt, int& cur, int max)
{
    //returns 1 if TAB or ENTER was pressed
    max--;
    if (vk == VK_BACK)
    {
        if (cur > 0)
        {
            cur--;
            for (int j = cur; j <= max - 1; j++) txt[j] = txt[j + 1];
            txt[max] = ' ';
        }
    }
    else if (vk == VK_TAB || vk == VK_RETURN)
    {
        return 1;
    }
    else if (vk == VK_INSERT)
    {
        for (int j = max - 1; j >= cur; j--) txt[j + 1] = txt[j];
        txt[cur] = ' ';
    }
    else if (vk == VK_DELETE)
    {
        for (int j = cur; j <= max - 1; j++) txt[j] = txt[j + 1];
        txt[max] = ' ';
    }
    else
    {
        if (control) return 0;
        char a = 0;
        if (vk >= 'A' && vk <= 'Z') { a = (shift) ? vk : vk + 32; }						//letters - uppercase with SHIFT
        else if (vk >= '0' && vk <= '9') { a = (shift) ? *(")!@#$%^&*(" + vk - 48) : vk; }	//numbers - special characters with SHIFT
        else if (vk == ' ')			a = ' ';	//space
        else if (vk == 189)	a = (shift) ? '_' : '-';
        else if (vk == 187)	a = (shift) ? '+' : '=';
        else if (vk == 219)	a = (shift) ? '{' : '[';
        else if (vk == 221)	a = (shift) ? '}' : ']';
        else if (vk == 186)	a = (shift) ? ':' : ';';
        else if (vk == 222)	a = (shift) ? '"' : '\'';
        else if (vk == 188)	a = (shift) ? '<' : ',';
        else if (vk == 190)	a = (shift) ? '>' : '.';
        else if (vk == 191)	a = (shift) ? '?' : '/';
        else if (vk == 220)	a = (shift) ? '|' : '\\';
        else if (vk == VK_RIGHT)
        {
            if (cur < max) cur++;
        }
        else if (vk == VK_LEFT)
        {
            if (cur > 0) cur--;
        }
        else if (vk == VK_HOME) cur = 0;
        else if (vk == VK_END)
        {
            int j;
            for (j = max; j >= 0 && (txt[j] == ' '); j--);
            cur = (j < max) ? j + 1 : max;
        }

        if (a > 0)
        {
            for (int j = max - 1; j >= cur; j--) txt[j + 1] = txt[j];
            txt[cur] = a;
            if (cur < max) cur++;
        }
    }
    return 0;
}

BOOL IsHoveredXY(int x, int y, int xLength, int yLength)
{
    int px = g_mouse.pointX, py = g_mouse.pointY;
    int xTo = x + xLength, yTo = y + yLength;

    return (px >= x && px < xTo) && (py >= y && py < yTo);
}

