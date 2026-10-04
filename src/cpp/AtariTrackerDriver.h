#pragma once

#include "Atari.h"
#include "TrackerDriverVersion.h"

class CAtariTrackerDriver {

public:
    CAtariTrackerDriver(CAtari& atari);

    CAtari* GetAtari();

    int LoadRMTRoutines(const TrackerDriverVersion trackerDriverVersion);
    // Whether the last LoadRMTRoutines() found its file and loaded it. False
    // means the emulated Atari holds no player routines, so it plays and
    // exports silence - which the caller is expected to report instead of
    // carrying on quietly.
    bool AreRoutinesLoaded() const { return m_routinesLoaded; };
    int Init();
    void Play();
    void SetPokey();
    void Silence();
    void SetTrackNoteInstrumentVolume(int t, int n, int i, int v);
    void SetTrackVolume(int t, int v);
    void InstrumentTurnOff(int instr);

    byte GetByteAt(const MemoryAddress address);

private:
    CAtari* m_atari;
    bool m_routinesLoaded = false;
};
