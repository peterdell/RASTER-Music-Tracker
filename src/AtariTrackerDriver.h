#pragma once

#include "Atari.h"

class CAtariTrackerDriver {

public:
    CAtariTrackerDriver(CAtari& atari);

    CAtari* GetAtari();


    int LoadRMTRoutines();
    int Init(); // Init
    void Play(); // Play
    void SetPokey();
    void Silence();
    void SetTrackNoteInstrumentVolume(int t, int n, int i, int v);
    void SetTrackVolume(int t, int v);
    void InstrumentTurnOff(int instr);

private:
    CAtari* m_atari;

};



