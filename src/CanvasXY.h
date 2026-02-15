#pragma once

#include "StdAfx.h"
#include "TextColors.h"

class CCanvasXY {
public:

    static CDC* g_mem_dc;
    static CDC* g_gfx_dc;

    static void TextXY(const char* txt, int x, int y, TextColor color = TextColor::WHITE);
    // Draw 8x16 chars with given color array per char position
    static void TextXYCol(const char* txt, int x, int y, int acu, TextColor color = TextColor::WHITE);
    // // Draw 8x16 chars vertically (one below the other)
    static void TextDownXY(const char* txt, int x, int y, TextColor color = TextColor::WHITE);

    static void TextXYFull(const char* txt, int& x, int& y);
    static void TextXYSelN(const char* txt, int n, int x, int y, TextColor color = TextColor::WHITE);

    // Mini Texts
    static void NumberMiniXY(const BYTE num, int x, int y, TextMiniColor color = TextMiniColor::GRAY);
    static void TextMiniXY(const char* txt, int x, int y, TextMiniColor color = TextMiniColor::GRAY);
    static void IconMiniXY(const int icon, int x, int y);


private:
    static void BitBltText(int x, int y, int nWidth, int nHeight, int xSrc, int ySrc);
};


