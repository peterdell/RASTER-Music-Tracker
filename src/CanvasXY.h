#pragma once

#include "StdAfx.h"
#include "TextColors.h"

class CCanvasXY {
public:

    static CDC* g_gfx_dc;

    CCanvasXY();
    ~CCanvasXY();

    void SetCDC(CDC& mem_dc);

    CPoint MoveTo(int x, int y);
    BOOL LineTo(int x, int y);

    CGdiObject* SelectObject(CGdiObject* pObject);
    void FillSolidRect(int x, int y, int cx, int cy, COLORREF clr);

    void TextXY(const char* txt, int x, int y, TextColor color = TextColor::WHITE);
    // Draw 8x16 chars with given color array per char position
    void TextXYCol(const char* txt, int x, int y, int acu, TextColor color = TextColor::WHITE);
    // Draw 8x16 chars vertically (one below the other)
    void TextDownXY(const char* txt, int x, int y, TextColor color = TextColor::WHITE);

    void TextXYFull(const char* txt, int& x, int& y);

    void TextXYSelN(const char* txt, int n, int x, int y, TextColor color = TextColor::WHITE);

    // Mini Texts
    void NumberMiniXY(const BYTE num, int x, int y, TextMiniColor color = TextMiniColor::GRAY);
    void TextMiniXY(const char* txt, int x, int y, TextMiniColor color = TextMiniColor::GRAY);
    void IconMiniXY(const int icon, int x, int y);


private:
    CDC* mem_dc;

    void BitBltText(int x, int y, int nWidth, int nHeight, int xSrc, int ySrc);
};



