#include "CanvasXY.h"

#include "GuiHelpers.h"

#include "Global.h" 

CCanvasXY::CCanvasXY() : mem_dc(nullptr) {
}

CCanvasXY::~CCanvasXY() {

}


void CCanvasXY::SetCDC(CDC& mem_dc) {
    this->mem_dc = &mem_dc;
}

CPoint CCanvasXY::MoveTo(int x, int y) {
    return mem_dc->MoveTo(x, y);
}

BOOL CCanvasXY::LineTo(int x, int y) {
    return mem_dc->LineTo(x, y);
}


CGdiObject* CCanvasXY::SelectObject(CGdiObject* pObject) {
    return mem_dc->SelectObject(pObject);
}

void CCanvasXY::FillSolidRect(int x, int y, int cx, int cy, COLORREF clr) {
    mem_dc->FillSolidRect(x, y, cx, cy, clr);
}

// Every text color is a line of 16 pixels height
int  GetColorY(const TextColor color) {
    return ((int)color) << 4;

}

// Every text color is a line of 8 pixels height
int  GetColorY(const TextMiniColor color) {
    return ((int)color) << 3;

}


CDC* CCanvasXY::g_gfx_dc;

void CCanvasXY::BitBltText(int x, int y, int nWidth, int nHeight, int xSrc, int ySrc) {
    mem_dc->BitBlt(x, y, nWidth, nHeight, g_gfx_dc, xSrc, ySrc, SRCCOPY);
}


void CCanvasXY::TextXY(const char* txt, int x, int y, TextColor color)
{
    char charToDraw;
    auto colorY = GetColorY(color);
    for (int i = 0; charToDraw = (txt[i]); i++, x += 8)
    {
        if (charToDraw == 32) { continue; } // Don't draw the space
        BitBltText(x, y, 8, 16, (charToDraw & 0x7f) << 3, colorY);
    }
}

void CCanvasXY::TextXYFull(const char* txt, int& x, int& y)
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

void CCanvasXY::TextXYSelN(const char* txt, int n, int x, int y, TextColor color)
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


void CCanvasXY::TextXYCol(const char* txt, int x, int y, int acu, TextColor color)
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

void CCanvasXY::TextDownXY(const char* txt, int x, int y, TextColor color)
{
    char charToDraw;
    auto colorY = GetColorY(color);
    for (int i = 0; charToDraw = (txt[i]); i++, y += 16)
    {
        BitBltText(x, y, 8, 16, (charToDraw & 0x7f) << 3, colorY);
    }
}

void  CCanvasXY::NumberMiniXY(const BYTE num, int x, int y, TextMiniColor color)
{
    auto colorY = 112 + GetColorY(color);
    BitBltText(x, y, 8, 8, (num & 0xf0) >> 1, colorY);
    BitBltText(x + 8, y, 8, 8, (num & 0x0f) << 3, colorY);
}

void  CCanvasXY::TextMiniXY(const char* txt, int x, int y, TextMiniColor color)
{
    char charToDraw;
    auto colorY = 112 + GetColorY(color);
    for (int i = 0; charToDraw = (txt[i]); i++, x += 8)
    {
        if (charToDraw == 32) { continue; } // Don't draw the space
        BitBltText(x, y, 8, 8, (charToDraw & 0x7f) << 3, colorY);
    }
}

void  CCanvasXY::IconMiniXY(const int icon, int x, int y)
{
    static constexpr int c = 128 - 6;
    if (icon >= 1 && icon <= 4)
    {
        mem_dc->BitBlt(x, y, 32, 6, g_gfx_dc, (icon - 1) * 32, c, SRCCOPY);

    }
}

