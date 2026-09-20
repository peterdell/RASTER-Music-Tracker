#pragma once

#include "TrackTypes.h"

static constexpr int TRACKSNUM = 254; // 0-253

struct TTracksAll	        // For undo
{
    int maxtracklength;
    TTrack tracks[TRACKSNUM];
};

