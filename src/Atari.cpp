/*
    Atari6502.cpp
    CPU EMULATION INTERFACE + ATARI BINARY FILE FUNCTIONS
    (c) Raster/C.P.U. 2003
    Reworked by VinsCool, 2021-2022
*/

#include "StdAfx.h"

#include "Atari.h"


#include "Tuning.h"

#include "Global.h"

#include "C6502.h"

extern CTuning g_Tuning;

CAtari::CycleCount CAtari::GetFrameCycleCount(boolean ntsc) {
    static constexpr CycleCount MAXSCREENCYCLES_NTSC = 114 * 262;
    static constexpr CycleCount MAXSCREENCYCLES_PAL = 114 * 312;
    return ntsc ? MAXSCREENCYCLES_NTSC : MAXSCREENCYCLES_PAL;
}

CAtari::ClockFrequency CAtari::GetClockFrequency(boolean ntsc) {
    return ntsc ? FREQ_17_NTSC : FREQ_17_PAL;

}

CAtari::CAtari() {
    ClearMemory();
}

CAtari::~CAtari() {
    // TODO: Free memory
}

void CAtari::JSR(C6502::Address& adr, C6502::Register& a, C6502::Register& x, C6502::Register& y, C6502::CycleCount& cycles) {
    C6502::JSR(adr, a, x, y, cycles);
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

void CAtari::SetByteAt(const MemoryAddress address, const byte value) {
    g_atarimem[address] = value;
}

byte* CAtari::GetMemoryAt(const MemoryAddress address) {
    return g_atarimem + address;
}

const byte* CAtari::GetConstMemoryAt(const MemoryAddress address) const {
    return g_atarimem + address;
}

BOOL CAtari::IsNTSC() const {
    return m_ntsc;
}

CAtari::CycleCount CAtari::GetFrameCycleCount() const {
    return GetFrameCycleCount(IsNTSC());
}

void CAtari::Init(const bool ntsc)
{

    m_ntsc = ntsc;
    g_Tuning.InitTuning(m_ntsc);
}

