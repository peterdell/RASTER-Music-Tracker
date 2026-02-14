#pragma once

#include "Canvas.h"

#include "Song.h"

class CPokeyView
{
public:


    CPokeyView(CCanvas& canvas);

    void Draw(CSong* m_song);

private:
    CCanvas* canvas;
};

