#pragma once

#include "StdAfx.h"

#include "Canvas.h"

#include "Atari.h"
#include "PokeyController.h"
#include "Song.h"
#include "Tuning.h"

class CPokeyView
{
public:


    CPokeyView(CCanvas& canvas);

    void Draw(const CSong& m_song, const CTuning& tuning, const bool explorerMode, const CPokeyController& pokeyController, const CAtari& atari);

private:
    CCanvas* canvas;
};

