#pragma once

#include "CanvasXY.h"
#include "StdAfx.h"

#include "TextColors.h"

class CCanvas {
public:
    CCanvas(CCanvasXY& canvasXY, const int originX, const int originY);

    int GetOriginX() const;
    int GetOriginY() const;

    CCanvas& ColorMini(const TextMiniColor colorMini);

    CCanvas& At(const int column, const int row);
    CCanvas& AtColumn(const int column);
    CCanvas& NextRow();

    void TextMiniAt(const char* txt, int row, int column, TextMiniColor color = TextMiniColor::GRAY);

    CCanvas& PrintMini(const char* txt);

    // Pribt a value between $0 and $F
    CCanvas& PrintNibble(const byte value);

    // Pribt a value between $00 and $FF
    CCanvas& PrintByte(const byte value);

    CCanvas& PrintfMini(const size_t  size, char const* const format, ...);

    void FillSolidRect(int x, int y, int width, int height, COLORREF color);
private:
    CCanvasXY* canvasXY;
    int originX;
    int originY;
    int charWidth = 8;
    int charHeight = 8;

    TextMiniColor colorMini = TextMiniColor::WHITE;
    int column = 0;
    int row = 0;

    char buffer[1024];

};