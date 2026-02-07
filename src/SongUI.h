#pragma once

#include "StdAfx.h"

#include "Song.h"

class CSongUI
{

public:
    CSongUI(CSong& song);

    void DrawAnalyzer();
    void DrawTracks();
    void DrawSong();				// Draw the song line info on the right
    void DrawInstrument();
    void DrawInfo();			//top left corner
    void DrawPlayTimeCounter();

private:
    CSong* m_song;
};