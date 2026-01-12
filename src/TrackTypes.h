#pragma once

static constexpr int TRACKLEN = 256; // Driver 128

static constexpr int TRACKMAXSPEED = 256;    // Maximum speed value; the highesr the slower

struct TTrack
{
    int len;				// Length of the track
    int go;
    int note[TRACKLEN];
    int instr[TRACKLEN];
    int volume[TRACKLEN];
    int speed[TRACKLEN];
};

