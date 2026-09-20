#pragma once

#include "StdAfx.h"

#include "Song.h"

#include "CanvasXY.h"

class CSongUI
{

public:
    CSongUI(CSong& song);

    void SetCanvas(CCanvasXY& canvasXY);

    void DrawVolumeAnalyzer();
    void DrawTracks();
    void DrawSong();				// Draw the song line info on the right
    void DrawInstrument();
    void DrawInfo();			//top left corner
    void DrawPlayTimeCounter();

private:
    CSong* m_song;

    CCanvasXY* canvasXY;

    void GetTracklineText(char* dest, int line);
    void DrawTracksHook(int ANALYZER_X, int ANALYZER_Y, int g1, int g2, int yUp);
    void DrawInstrumentHook(int ANALYZER2_X, int ANALYZER_Y, int g1, int g2, int yUp);

};