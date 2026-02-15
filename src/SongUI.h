#pragma once

#include "StdAfx.h"

#include "Song.h"

#include "CanvasXY.h"

class CSongUI
{

public:
    CSongUI(CSong& song);

    void SetCanvas(CCanvasXY& canvasXY);

    void DrawAnalyzer();
    void DrawTracks();
    void DrawSong();				// Draw the song line info on the right
    void DrawInstrument();
    void DrawInfo();			//top left corner
    void DrawPlayTimeCounter();

private:
    CSong* m_song;

    CCanvasXY* canvasXY;
};