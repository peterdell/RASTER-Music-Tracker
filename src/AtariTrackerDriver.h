#pragma once

#include "Atari.h"

class CAtariTrackerDriver {

public:
    CAtariTrackerDriver(CAtari& atari);

    CAtari* GetAtari();


    int LoadRMTRoutines();
    int InitRMTRoutine(); // Init
    void PlayRMT(); // Play
    void SetPokey();
    void Silence();
    void SetTrack_NoteInstrVolume(int t, int n, int i, int v);
    void SetTrack_Volume(int t, int v);
    void InstrumentTurnOff(int instr);

private:
    CAtari* m_atari;

};



