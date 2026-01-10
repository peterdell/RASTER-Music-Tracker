/*
    Atari6502.h
    CPU EMULATION INTERFACE + ATARI BINARY FILE FUNCTIONS
    (c) Raster/C.P.U. 2003
*/

#pragma once
#include "Memory.h"

#include "C6502.h"

#include "tracker_obx.h"				// The ASM generated C header file

// bass16bit low byte, bass 0C, bass 0E, clean tones 0A and 0,2,4,8, bass16bit hi byte, this might require different addresses? What is this even used for anyway?
static constexpr MemoryAddress RMT_FRQTABLES = RMTPLAYR_PAGE_DISTORTION_2;

static constexpr MemoryAddress RMT_INIT = RMTPLAYR_RASTERMUSICTRACKER;
static constexpr MemoryAddress RMT_PLAY = RMTPLAYR_RASTERMUSICTRACKER + 3;
static constexpr MemoryAddress RMT_P3 = RMTPLAYR_RASTERMUSICTRACKER + 6;
static constexpr MemoryAddress RMT_SILENCE = RMTPLAYR_RASTERMUSICTRACKER + 9;
static constexpr MemoryAddress RMT_SETPOKEY = RMTPLAYR_RASTERMUSICTRACKER + 12;

static constexpr MemoryAddress RMT_ATA_SETNOTEINSTR = RMTPLAYR_GETINSTRUMENTY2;
static constexpr MemoryAddress RMT_ATA_SETVOLUME = RMTPLAYR_SETINSTRUMENTVOLUME;
static constexpr MemoryAddress RMT_ATA_INSTROFF = RMTPLAYR_STOPINSTRUMENT;

// immediately after RMT_ATA_INSTROFF, there is some bytes left unused, these will be used as plaintext data to display the RMT driver version used
static constexpr MemoryAddress RMT_ATA_DRIVERVERSION = RMTPLAYR_DRIVERVERSION;


class CAtari {


public:
    static constexpr size_t MEMORY_SIZE = 0x10000;

    //maximum clock count for the entire screen in PAL (default) and NTSC region
    typedef int CycleCount;

    static CycleCount GetFrameCycleCount(boolean ntsc);

    typedef int ClockFrequency;

    // The true clock frequency for the NTSC Atari 8-bit computer is 1.7897725 MHz
    static constexpr ClockFrequency FREQ_17_NTSC = 1789773;

    // The true clock frequency for the PAL Atari 8-bit computer is 1.7734470 MHz
    static constexpr ClockFrequency FREQ_17_PAL = 1773447;

    static ClockFrequency GetClockFrequency(boolean ntsc);

    CAtari();
    ~CAtari();

    int Init();
    void DeInit();

    void ClearMemory();
    byte GetByteAt(const MemoryAddress address);
    void SetByteAt(const MemoryAddress address, const byte value);
    byte* GetMemoryAt(const MemoryAddress address);
    const byte* GetConstMemoryAt(const MemoryAddress address) const;

    void Init(const bool ntsc);
    BOOL IsNTSC() const;

    CycleCount GetFrameCycleCount() const;
    void JSR(C6502::Address& adr, C6502::Register& a, C6502::Register& x, C6502::Register& y, C6502::CycleCount& cycles);

private:

    byte m_atarimem[MEMORY_SIZE];
    // char m_debugmem[MEMORY_SIZE];	//debug display of g_atarimem bytes directly, slow and terrible, do not use unless there is a purpose for it 

    BOOL m_ntsc;

};
