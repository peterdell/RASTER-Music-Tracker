/*
    Atari6502.cpp
    CPU EMULATION INTERFACE + ATARI BINARY FILE FUNCTIONS
    (c) Raster/C.P.U. 2003
    Reworked by VinsCool, 2021-2022
*/

#include "stdafx.h"

#include "Atari.h"
#include "AtariIO.h"

#include "Tuning.h"

#include "RmtAtariBinaries.h"
#include "General.h"
#include "Global.h"

#include "C6502.h"

CAtari::CycleCount CAtari::GetFrameCycleCount(boolean ntsc) {
    static constexpr CycleCount MAXSCREENCYCLES_NTSC = 114 * 262;
    static constexpr CycleCount MAXSCREENCYCLES_PAL = 114 * 312;
    return ntsc ? MAXSCREENCYCLES_NTSC : MAXSCREENCYCLES_PAL;
}

CAtari::ClockFrequency CAtari::GetClockFrequency(boolean ntsc) {
    return ntsc ? FREQ_17_NTSC : FREQ_17_PAL;

}

int CAtari::Init() {
    return C6502::Init();
}

void CAtari::DeInit() {
    C6502::DeInit();
}

void CAtari::ClearMemory()
{
    memset(g_atarimem, 0, RAM_SIZE);
}

byte CAtari::GetByteAt(const MemoryAddress address) {
    return g_atarimem[address];
}

// Load RMT routine to $3400, setnoteinstrvol to $3d00, and setvol to $3e00
int CAtari::LoadRMTRoutines()
{
    WORD min, max;
    WORD size;
    byte* bin;
    if (!CRmtAtariBinaries::GetTrackerDriverBinary(g_trackerDriverVersion, bin, size)) {
        return 0;
    }

    return CAtariIO::LoadDataAsBinaryFile(bin, size, g_atarimem, min, max);
}

int CAtari::InitRMTRoutine()
{
    if (!g_is6502) {
        return 0;
    }

    g_Tuning.InitTuning();

    WORD adr = RMT_INIT;
    BYTE a = 0, x = 0x00, y = 0x3f;
    auto cycles = GetFrameCycleCount(g_ntsc);
    C6502::JSR(adr, a, x, y, cycles);
    for (int i = 0; i < SONGTRACKS; i++) { g_rmtinstr[i] = -1; }

    return (int)a;
}

void CAtari::PlayRMT()
{
    if (!g_is6502) {
        return;
    }

    WORD adr = RMT_P3; //(without SetPokey) one run of RMT routine but from rmt_p3 (wrap processing)
    BYTE a = 0, x = 0, y = 0;
    auto cycles = GetFrameCycleCount(g_ntsc);
    if (g_prove < EditMode::EDIT_AND_JAM_MODES) { // this is only good for tests, this trigger prevents the RMT driver running at all, leaving only SetPokey available
        C6502::JSR(adr, a, x, y, cycles);
    }
    adr = RMT_SETPOKEY;
    a = x = y = 0;
    C6502::JSR(adr, a, x, y, cycles);
}

void CAtari::SetPokey()
{
    if (!g_is6502) {
        return;
    }

    WORD adr = RMT_SETPOKEY;
    BYTE a = 0, x = 0, y = 0;
    auto cycles = GetFrameCycleCount(g_ntsc);
    C6502::JSR(adr, a, x, y, cycles);
}

void CAtari::Silence()
{
    if (!g_is6502) {
        return;
    }

    //Silence routine
    WORD adr = RMT_SILENCE;
    BYTE a = 0, x = 0, y = 0;
    auto cycles = GetFrameCycleCount(g_ntsc);
    C6502::JSR(adr, a, x, y, cycles);
}

void CAtari::SetTrack_NoteInstrVolume(int t, int n, int i, int v)
{
    if (!g_is6502) {
        return;
    }

    WORD adr = RMT_ATA_SETNOTEINSTR;
    BYTE a = n, x = t, y = i;
    auto cycles = GetFrameCycleCount(g_ntsc);
    C6502::JSR(adr, a, x, y, cycles);
    //
    adr = RMT_ATA_SETVOLUME;
    a = v; x = t; y = 0;
    cycles = GetFrameCycleCount(g_ntsc);
    C6502::JSR(adr, a, x, y, cycles);

    g_rmtinstr[t] = i;
}

void CAtari::SetTrack_Volume(int t, int v)
{
    if (!g_is6502) {
        return;
    }

    WORD adr = RMT_ATA_SETVOLUME;
    BYTE a = v, x = t, y = 0;
    auto cycles = GetFrameCycleCount(g_ntsc);
    C6502::JSR(adr, a, x, y, cycles);
}

void CAtari::InstrumentTurnOff(int instr)
{
    if (!g_is6502) {
        return;
    }

    auto cycles = GetFrameCycleCount(g_ntsc);
    for (int i = 0; i < SONGTRACKS; i++)
    {
        if (g_rmtinstr[i] == instr)
        {
            WORD adr = RMT_ATA_INSTROFF;
            BYTE a = 0, x = i, y = 0;
            C6502::JSR(adr, a, x, y, cycles);
            g_atarimem[0xd200 + i * 2 + 1 + (i >= 4) * 16] = 0;		//resets POKEY audctl memory
            g_rmtinstr[i] = -1;
        }
    }
}
