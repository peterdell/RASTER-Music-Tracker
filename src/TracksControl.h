#pragma once
#include "StdAfx.h"

#include "Tracks.h"

#include "Canvas.h"
#include "TextColors.h"

class CTracksControl
{
public:
    CTracksControl(CCanvas& canvas);
    ~CTracksControl();

    void DrawTrackHeader(const CTracks& tracks, int x, int y, int tr, TextColor col);
    void DrawTrackLine(const CTracks& tracks, int col, int x, int y, int tr, int line, int aline, int cactview, int pline, BOOL isactive, int acu, int oob);

private:
    CCanvas* canvas;
};