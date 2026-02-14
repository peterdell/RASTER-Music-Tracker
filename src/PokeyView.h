#pragma once

#include "GuiHelpers.h"

#include "Song.h"

class CPokeyView
{
public:


    CPokeyView(CCanvas& canvas);

    void Draw(CSong* m_song, int a);

private:
    CCanvas* canvas;
};

