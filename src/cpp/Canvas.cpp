#include "Canvas.h"


#include "CanvasXY.h"



CCanvas::CCanvas(CCanvasXY& canvasXY, const  int originX, const int originY) : canvasXY(&canvasXY), originX(originX), originY(originY) {
}


int  CCanvas::GetOriginX() const {
    return originX;
}

int  CCanvas::GetOriginY() const {
    return originY;
}

CCanvas& CCanvas::ColorMini(const TextMiniColor colorMini) {
    this->colorMini = colorMini;
    return *this;
}

CCanvas& CCanvas::At(const int column, const int row) {
    this->column = column;
    this->row = row;
    return *this;
}

CCanvas& CCanvas::AtColumn(const int column) {
    this->column = column;
    return *this;
}

CCanvas& CCanvas::NextRow() {
    this->row++;
    return *this;
}


CCanvas& CCanvas::PrintMini(const char* txt) {
    TextMiniAt(txt, column, row, colorMini);
    return *this;
}

CCanvas& CCanvas::PrintfMini(
    size_t      const size,
    char const* const format, ...) {

    va_list ap;
    va_start(ap, format);
    vsnprintf(buffer, size + 1, format, ap);
    va_end(ap);
    PrintMini(buffer);
    return *this;
}

CCanvas& CCanvas::PrintNibble(const byte value) {
    return PrintfMini(1, "%01hX", value << 4);
}

CCanvas& CCanvas::PrintByte(const byte value) {
    return PrintfMini(2, "%02hX", value);
}

void CCanvas::TextMiniAt(const char* txt, int row, int column, TextMiniColor color) {
    canvasXY->TextMiniXY(txt, originX + row * charWidth, originY + column * charHeight, color);
}

void CCanvas::FillSolidRect(int x, int y, int width, int height, COLORREF color) {
    canvasXY->FillSolidRect(originX + x, originY + y, width, height, color);
}