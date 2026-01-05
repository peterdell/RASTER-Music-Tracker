//
// PokeyRender.h header file
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

    bool IsSoundDriverLoaded() const;
    CPokey::SoundDriver GetSoundDriver() const;
    const WAVEFORMATEX* GetSoundFormat() const;

    // Called by Song
    BOOL RenderSound1_50(int instrspeed);

    // Called by WaveFileExporter
    void RenderSoundV2(int instrspeed, BYTE* buffer, int& length);


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

    void MemToPokey();

};