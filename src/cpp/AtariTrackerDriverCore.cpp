#include "StdAfx.h"

#include "AtariTrackerDriver.h"
#include "SongTypes.h"

// These CAtariTrackerDriver methods only need g_rmtinstr (a plain int
// array) and CAtari::JSR() - which just delegates to C6502::JSR(), already
// a link-only no-op stub in the test project (see AtariStub.cpp's header
// comment: C6502::Init() would load a real DLL, a genuine hazard, but
// JSR() itself is a harmless no-op stub) - unlike LoadRMTRoutines()/Init()/
// Play()/SetPokey()/Silence(), which stay in AtariTrackerDriver.cpp: they
// need g_trackerDriverVersion/CRmtAtariBinaries (real driver binary
// loading) or IsSpecialProveMode() (via Global.h). Kept separate to keep
// this half testable - same pattern as Song.cpp/SongCore.cpp etc.

extern int g_rmtinstr[SONGTRACKS];

CAtariTrackerDriver::CAtariTrackerDriver(CAtari& atari) {
    m_atari = &atari;
}

CAtari* CAtariTrackerDriver::GetAtari() {
    return m_atari;
}

void CAtariTrackerDriver::SetTrackNoteInstrumentVolume(int t, int n, int i, int v)
{

    auto adr = RMT_ATA_SETNOTEINSTR;
    BYTE a = n, x = t, y = i;
    auto cycles = m_atari->GetFrameCycleCount();
    m_atari->JSR(adr, a, x, y, cycles);
    //
    adr = RMT_ATA_SETVOLUME;
    a = v; x = t; y = 0;
    cycles = m_atari->GetFrameCycleCount();
    m_atari->JSR(adr, a, x, y, cycles);

    g_rmtinstr[t] = i;
}

void CAtariTrackerDriver::SetTrackVolume(int t, int v)
{

    auto adr = RMT_ATA_SETVOLUME;
    BYTE a = v, x = t, y = 0;
    auto cycles = m_atari->GetFrameCycleCount();
    m_atari->JSR(adr, a, x, y, cycles);
}


void CAtariTrackerDriver::InstrumentTurnOff(int instr)
{
    auto cycles = m_atari->GetFrameCycleCount();
    for (int i = 0; i < SONGTRACKS; i++)
    {
        // Does this POKEY channel have the instrument assigned?
        if (g_rmtinstr[i] == instr)
        {
            auto adr = RMT_ATA_INSTROFF;
            BYTE a = 0, x = i, y = 0;
            m_atari->JSR(adr, a, x, y, cycles);
            m_atari->SetByteAt(0xd200 + i * 2 + 1 + (i >= 4) * 16, 0); // Reset POKEY AUDCx memory
            g_rmtinstr[i] = -1;
        }
    }
}


byte CAtariTrackerDriver::GetByteAt(const MemoryAddress address) {
    return m_atari->GetByteAt(address);
}
