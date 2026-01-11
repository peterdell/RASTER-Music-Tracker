#include "AtariTrackerDriver.h"
#include "AtariBinaries.h"

#include "AtariIO.h"

#include "Global.h"


CAtariTrackerDriver::CAtariTrackerDriver(CAtari& atari) {
    m_atari = &atari;
}

CAtari* CAtariTrackerDriver::GetAtari() {
    return m_atari;

}

// Load RMT routine to $3400, setnoteinstrvol to $3d00, and setvol to $3e00
int CAtariTrackerDriver::LoadRMTRoutines(const TrackerDriverVersion trackerDriverVersion)
{
    WORD min, max;
    WORD size;
    byte* bin;
    if (!CRmtAtariBinaries::GetTrackerDriverBinary(g_trackerDriverVersion, bin, size)) {
        return 0;
    }

    return CAtariIO::LoadDataAsBinaryFile(bin, size, m_atari->GetMemoryAt(0), min, max);
}


int CAtariTrackerDriver::Init() {


    WORD adr = RMT_INIT;
    BYTE a = 0, x = 0x00, y = 0x3f;
    auto cycles = m_atari->GetFrameCycleCount();
    m_atari->JSR(adr, a, x, y, cycles);
    for (int i = 0; i < SONGTRACKS; i++) { g_rmtinstr[i] = -1; }

    return (int)a;
}

void CAtariTrackerDriver::Play()
{
    auto cycles = m_atari->GetFrameCycleCount();
 
    auto adr = RMT_P3; //(without SetPokey) one run of RMT routine but from rmt_p3 (wrap processing)
    BYTE a = 0, x = 0, y = 0;
    if (g_prove < EditMode::EDIT_AND_JAM_MODES) { 
        // this is only good for tests, this trigger prevents the RMT driver running at all, leaving only SetPokey available
        C6502::JSR(adr, a, x, y, cycles);
    }
    adr = RMT_SETPOKEY;
    a = x = y = 0;
    C6502::JSR(adr, a, x, y, cycles);
}

void CAtariTrackerDriver::SetPokey()
{

    auto adr = RMT_SETPOKEY;
    BYTE a = 0, x = 0, y = 0;
    auto cycles = m_atari->GetFrameCycleCount();
    m_atari->JSR(adr, a, x, y, cycles);
}

void CAtariTrackerDriver::Silence()
{

    // Silence routine
    auto adr = RMT_SILENCE;
    BYTE a = 0, x = 0, y = 0;
    auto cycles = m_atari->GetFrameCycleCount();
    m_atari->JSR(adr, a, x, y, cycles);
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
