//
// Pokey.h header file
// originally made by Raster, 2002-2009
// reworked by VinsCool, 2021-2022
//


#pragma once

#include "C6502.h"
#include "Pokey.h"



class CXPokey
{
    // Construction
public:

    static constexpr size_t BUFFER_SIZE = 0x8000; // Must be a power of 2

    CXPokey();
    ~CXPokey();
    BOOL InitSound(const bool ntsc);
    BOOL DeInitSound();
    BOOL ReInitSound(const bool ntsc);
    BOOL RenderSound1_50(int instrspeed);
    void RenderSoundV2(int instrspeed, BYTE* buffer, int& length);
    void MemToPokey();
    bool IsSoundDriverLoaded() { return m_pokey.IsSoundDriverLoaded(); }
    CPokey::POKEY_SoundDriver GetSoundDriver() { return m_pokey.GetSoundDriver(); }
    const WAVEFORMATEX* GetSoundFormat() const { return &m_SoundFormat; };

private:
    CPokey				m_pokey;

    int m_Latency; // Chunks

    int					m_ChunkSize;
    C6502::ClockFrequency m_ClockFrequency;
    C6502::CycleCount	m_CyclesPerFrame;
    float				m_CyclesPerSample;

    DWORD				m_LoadPos;
    WAVEFORMATEX		m_SoundFormat;
    DWORD				m_LoadSize;
    DSBUFFERDESC        dsbdesc;
    LPDIRECTSOUNDBUFFER m_SoundBuffer;
    DWORD				dwSize1, dwSize2;
    LPVOID				Data1, Data2;
    BYTE				m_PlayBuffer[BUFFER_SIZE];	// Rendered part of the swing CHUNK_SIZE +- something (but it can be much bigger)
    DWORD				m_PlayCursor;
    DWORD				m_WriteCursor;
    DWORD				m_WriteCursorStart;

    static int GetFrameRate(bool ntsc);
    static int GetCyclesPerFrame(bool ntsc);

    BOOL InitSoundInternal(const bool ntsc, const WORD channels, const DWORD samplesPerSec, const WORD bitsPerSample);

    WORD GetChannels() const;
    int GetChunkSize() const;
    int GetLatencySize() const;
};