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


// Every text color is a line of 16 pixels height
int  GetColorY(const TextColor color) {
    return ((int)color) << 4;

}

// Every text color is a line of 8 pixels height
int  GetColorY(const TextMiniColor color) {
    return ((int)color) << 3;

}

void BitBltText(int x, int y, int nWidth, int nHeight, int xSrc, int ySrc) {
    g_mem_dc->BitBlt(x, y, nWidth, nHeight, g_gfx_dc, xSrc, ySrc, SRCCOPY);
}


void TextXY(const char* txt, int x, int y, TextColor color)
{
    char charToDraw;
    auto colorY = GetColorY(color);
    for (int i = 0; charToDraw = (txt[i]); i++, x += 8)
    {
        if (charToDraw == 32) { continue; } // Don't draw the space
        BitBltText(x, y, 8, 16, (charToDraw & 0x7f) << 3, colorY);
    }
}

void TextXYFull(const char* txt, int& x, int& y)
{
    auto color = TextColor::WHITE;
    int ori_x = x, ori_y = y;

    for (int i = 0; char charToDraw = (txt[i]); i++)
    {
        switch (charToDraw)
        {
        case '\n': x = ori_x; y += 16; continue;
        case ' ': x += 8; continue;
        case '\x80': color = TextColor::WHITE; continue;
        case '\x82': color = TextColor::YELLOW; continue;
        case '\x83': color = LogicalTextColor::SELECTED_PROVE; continue;
        case '\x85': color = TextColor::CYAN; continue;
        case '\x86': color = TextColor::RED; continue;
        case '\x89': color = LogicalTextColor::SELECTED; continue;
        case '\x8B': color = TextColor::GREEN; continue;
        case '\x8C': color = TextColor::DARK_GRAY; continue;
        case '\x8D': color = TextColor::BLUE; continue;
        }

        BitBltText(x, y, 8, 16, (charToDraw & 0x7f) << 3, GetColorY(color));
        x += 8;
    }
}

void TextXYSelN(const char* txt, int n, int x, int y, TextColor color)
{
    auto colorY = GetColorY(color);

    int col = GetColorY(IsProveMode() ? LogicalTextColor::SELECTED_PROVE : LogicalTextColor::SELECTED);
    int cur = GetColorY(LogicalTextColor::HOVERED);

    // The characters 'n' will use the "select" color, everything else will use the 'color' parameter, unless they are hovered by the mouse cursor
    for (int i = 0; char charToDraw = txt[i]; i++, x += 8)
    {
        BitBltText(x, y, 8, 16, (charToDraw & 0x7F) << 3, IsHoveredXY(x, y, 8, 16) ? cur : i == n ? col : colorY);
    }
}


// Draw 8x16 chars with given color array per char position
void TextXYCol(const char* txt, int x, int y, int acu, TextColor color)
{
    auto colorY = GetColorY(color);

    int num = 0, curnum = 0, curoff = 0;
    auto col = GetColorY(IsProveMode() ? LogicalTextColor::SELECTED_PROVE : LogicalTextColor::SELECTED);
    auto cur = GetColorY(LogicalTextColor::HOVERED);

    switch (acu)
    {
    case 0: acu = 1; num = 3; break;	// Note
    case 1: acu = 5; num = 2; break;	// Instrument
    case 2: acu = 8; num = 2; break;	// Volume
    case 3: acu = 11; num = 3; break;	// Effect(s)
    default: acu = -1;
    }

    for (int i = 0; char charToDraw = txt[i]; i++, x += 8)
    {
        if (charToDraw == 32) { continue; }	// Don't draw the space

        BitBltText(x, y, 8, 16, (charToDraw & 0x7F) << 3, IsHoveredXY(x, y, 8, 16) ? cur : i >= acu && i < acu + num ? col : colorY);
    }
}

// Draw 8x16 chars vertically (one below the other)
void TextDownXY(const char* txt, int x, int y, TextColor color)
{
    char charToDraw;
    auto colorY = GetColorY(color);
    for (int i = 0; charToDraw = (txt[i]); i++, y += 16)
    {
        BitBltText(x, y, 8, 16, (charToDraw & 0x7f) << 3, colorY);
    }
}

void NumberMiniXY(const BYTE num, int x, int y, TextMiniColor color)
{
    auto colorY = 112 + GetColorY(color);
    BitBltText(x, y, 8, 8, (num & 0xf0) >> 1, colorY);
    BitBltText(x + 8, y, 8, 8, (num & 0x0f) << 3, colorY);
}

void TextMiniXY(const char* txt, int x, int y, TextMiniColor color)
{
    char charToDraw;
    auto colorY = 112 + GetColorY(color);
    for (int i = 0; charToDraw = (txt[i]); i++, x += 8)
    {
        if (charToDraw == 32) { continue; } // Don't draw the space
        BitBltText(x, y, 8, 8, (charToDraw & 0x7f) << 3, colorY);
    }
}

void IconMiniXY(const int icon, int x, int y)
{
    static constexpr int c = 128 - 6;
    if (icon >= 1 && icon <= 4)
    {
        g_mem_dc->BitBlt(x, y, 32, 6, g_gfx_dc, (icon - 1) * 32, c, SRCCOPY);

    }
}

CCanvas::CCanvas(int originRow, int originColumn) : originX(originRow), originY(originColumn) {
}


void CCanvas::TextMiniAt(const char* txt, int row, int column, TextMiniColor color) {
    TextMiniXY(txt, originX + row * charWidth, originY + column * charHeight, color);
}

void CCanvas::FillSolidRect(int x, int y, int width, int height, COLORREF color) {
    g_mem_dc->FillSolidRect(originX + x, originY + y, width, height, color);
}

