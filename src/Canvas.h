#pragma once

#include "StdAfx.h"

#include "TextColors.h"

class CCanvas {
public:
    CCanvas(const int originX, const int originY);

    int GetOriginX() const;
    int GetOriginY() const;

    CCanvas& ColorMini(const TextMiniColor colorMini);

    CCanvas& At(const int column, const int row);
    CCanvas& AtColumn(const int column);
    CCanvas& NextRow();

    void TextMiniAt(const char* txt, int row, int column, TextMiniColor color = TextMiniColor::GRAY);

    CCanvas& PrintMini(const char* txt);

    CCanvas& PrintByte(const byte value);

    CCanvas& PrintfMini(const size_t  size, char const* const format, ...);

    void FillSolidRect(int x, int y, int width, int height, COLORREF color);
private:
    int originX;
    int originY;
    int charWidth = 8;
    int charHeight = 8;

    TextMiniColor colorMini;
    int column;
    int row;

    char buffer[1024];

};