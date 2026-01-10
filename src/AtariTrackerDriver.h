#pragma once

#include "Atari.h"
#include "TrackerDriverVersion.h"

class CAtariTrackerDriver {

public:
    CAtariTrackerDriver(CAtari& atari);

    CAtari* GetAtari();

    int LoadRMTRoutines(const TrackerDriverVersion trackerDriverVersion);
    int Init();
    void Play();
    void SetPokey();
    void Silence();
    void SetTrackNoteInstrumentVolume(int t, int n, int i, int v);
    void SetTrackVolume(int t, int v);
    void InstrumentTurnOff(int instr);

private:
    CAtari* m_atari;

};



