#include "AtariBinaries.h"
#include "AtariTrackerDriver.h"

#include "AtariIO.h"

#include "Global.h"

// CAtariTrackerDriver's constructor, GetAtari(), SetTrackNoteInstrumentVolume(),
// SetTrackVolume(), InstrumentTurnOff(), and GetByteAt() are implemented in
// AtariTrackerDriverCore.cpp (only need g_rmtinstr and CAtari::JSR(), which
// delegates to the already-stubbed C6502::JSR() in tests).

// Load RMT routine to $3400, setnoteinstrvol to $3d00, and setvol to $3e00
int CAtariTrackerDriver::LoadRMTRoutines(const TrackerDriverVersion trackerDriverVersion) {
    WORD min, max;
    WORD size;
    byte* bin;
    if (!CRmtAtariBinaries::GetTrackerDriverBinary(trackerDriverVersion, bin, size)) {
        return 0;
    }

    return CAtariIO::LoadDataAsBinaryFile(bin, size, m_atari->GetMemoryAt(0), min, max);
}

int CAtariTrackerDriver::Init() {

    WORD adr = RMT_INIT;
    BYTE a = 0, x = 0x00, y = 0x3f;
    auto cycles = m_atari->GetFrameCycleCount();
    m_atari->JSR(adr, a, x, y, cycles);
    for (int i = 0; i < SONGTRACKS; i++) {
        g_rmtinstr[i] = -1;
    }

    return (int)a;
}

void CAtariTrackerDriver::Play() {
    auto cycles = m_atari->GetFrameCycleCount();

    auto adr = RMT_P3; //(without SetPokey) one run of RMT routine but from rmt_p3 (wrap processing)
    BYTE a = 0, x = 0, y = 0;

    // this is only good for tests, this trigger prevents the RMT driver running at all, leaving only SetPokey available
    if (!IsSpecialProveMode()) {
        C6502::JSR(adr, a, x, y, cycles);
    }
    adr = RMT_SETPOKEY;
    a = x = y = 0;
    C6502::JSR(adr, a, x, y, cycles);
}

void CAtariTrackerDriver::SetPokey() {

    auto adr = RMT_SETPOKEY;
    BYTE a = 0, x = 0, y = 0;
    auto cycles = m_atari->GetFrameCycleCount();
    m_atari->JSR(adr, a, x, y, cycles);
}

void CAtariTrackerDriver::Silence() {

    // Silence routine
    auto adr = RMT_SILENCE;
    BYTE a = 0, x = 0, y = 0;
    auto cycles = m_atari->GetFrameCycleCount();
    m_atari->JSR(adr, a, x, y, cycles);
}
