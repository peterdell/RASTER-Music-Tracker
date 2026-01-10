/*
    Atari6502.h
    CPU EMULATION INTERFACE + ATARI BINARY FILE FUNCTIONS
    (c) Raster/C.P.U. 2003
*/

#pragma once
#include "Memory.h"
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


    //maximum clock count for the entire screen in PAL (default) and NTSC region
    typedef int CycleCount;

    static CycleCount GetFrameCycleCount(boolean ntsc);

    typedef int ClockFrequency;

    // The true clock frequency for the NTSC Atari 8-bit computer is 1.7897725 MHz
    static constexpr ClockFrequency FREQ_17_NTSC = 1789773;

    // The true clock frequency for the PAL Atari 8-bit computer is 1.7734470 MHz
    static constexpr ClockFrequency FREQ_17_PAL = 1773447;

    static ClockFrequency GetClockFrequency(boolean ntsc);

    int Init();
    void DeInit();

    void ClearMemory();
    byte GetByteAt(const MemoryAddress address);

    int LoadRMTRoutines();
    int InitRMTRoutine(); // Without changing the NTSC/PAL flag
    int InitRMTRoutine(const bool ntsc);
    void PlayRMT();
    void SetPokey();
    void Silence();
    void SetTrack_NoteInstrVolume(int t, int n, int i, int v);
    void SetTrack_Volume(int t, int v);
    void InstrumentTurnOff(int instr);


private:
    BOOL m_ntsc;

};


