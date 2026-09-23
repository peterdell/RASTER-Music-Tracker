#include "AtariTrackerDriver.h"
#include "ChannelControl.h"
#include "Global.h"
#include "PokeyRenderer.h"
#include "StdAfx.h"

// CXPokey's constructor/destructor and the methods that never touch
// DirectSound (GetSoundFormat/GetChannels/GetChunkSize/GetLatencySize/
// GetSoundDriver/IsSoundDriverLoaded/RenderSoundV2/CopyAtariMemoryToPokey)
// are kept here - only InitSoundInternal()/InitSound()/ReInitSound()/
// RenderSound1_50() (PokeyRenderer.cpp) actually create/use a real
// LPDIRECTSOUND(BUFFER). RenderSoundV2() (the method CWaveFileExporter::
// ExportWAV() uses, unlike RenderSound1_50()) only drives m_pokey - whose
// GetSoundDriver() defaults to NONE until CPokey::InitSound() explicitly
// loads a POKEY DLL, never called here - so every switch on GetSoundDriver()
// in this file stays a provably safe no-op unless InitSound() runs first.
// g_lpds/g_lpdsbPrimary are shared with PokeyRenderer.cpp's real
// DirectSound calls, so they're plain externs here rather than this file's
// own file-static copies. See plans/EXPORTWAV_PLAN.md.

extern BOOL volatile g_rmtroutine; // From Global.h
extern CAtariTrackerDriver* g_AtariTrackerDriver;

LPDIRECTSOUND g_lpds;
LPDIRECTSOUNDBUFFER g_lpdsbPrimary;

int CXPokey::GetFrameRate(bool ntsc) {
    // TODO: This is not the exacty framerate, so maybe that's why the frequencies are a little off in CPokey?
    return ntsc ? 60 : 50;
}

int CXPokey::GetCyclesPerFrame(bool ntsc) {
    return (int)(((float)CAtari::GetClockFrequency(ntsc)) / GetFrameRate(ntsc));
}

CXPokey::CXPokey() {
}

CXPokey::~CXPokey() {
    DeInitSound();
}

const CPokey* CXPokey::GetPokey() const {
    return &m_pokey;
}

BOOL CXPokey::DeInitSound() {

    m_pokey.DeInitSound();

    if (m_SoundBuffer) {
        m_SoundBuffer->Stop();
        m_SoundBuffer->Release();
    }
    m_SoundBuffer = NULL;

    if (g_lpdsbPrimary) {
        g_lpdsbPrimary->Release();
        g_lpdsbPrimary = NULL;
    }

    if (g_lpds) {
        g_lpds->Release();
        g_lpds = NULL;
    }

    return 1;
}

bool CXPokey::IsSoundDriverLoaded() const {
    return m_pokey.IsSoundDriverLoaded();
}

CPokey::SoundDriver CXPokey::GetSoundDriver() const {
    return m_pokey.GetSoundDriver();
}

const WAVEFORMATEX* CXPokey::GetSoundFormat() const {
    return &m_SoundFormat;
};

WORD CXPokey::GetChannels() const {
    return m_SoundFormat.nChannels;
}

int CXPokey::GetChunkSize() const {
    return m_ChunkSize;
}

int CXPokey::GetLatencySize() const {
    return (m_Latency * GetChunkSize());
}

// Initial WAV recorder process
// NOTE: This does NOT work with the Altirra plugin due to it hijacking the soundbuffer with its own thing...
void CXPokey::RenderSoundV2(int instrspeed, BYTE* buffer, int& length) {
    int rendersize = GetChunkSize();
    int renderpartsize = 0;
    int renderoffset = 0;

    for (; instrspeed > 0; instrspeed--) {
        g_AtariTrackerDriver->SetPokey();
        CopyAtariMemoryToPokey();
        renderpartsize = (rendersize / instrspeed) & 0xfffe;

        switch (GetSoundDriver()) {
        case CPokey::SoundDriver::SA_POKEY:
            Pokey_Process(buffer + renderoffset, (unsigned short)renderpartsize);
            rendersize -= renderpartsize;
            renderoffset += renderpartsize;
            break;
        }
    }

    // Copy the actually generated sample data to buffer
    length = renderoffset;
}

/// <summary>
/// Transfer 9/18 Pokey registers values into the sound driver.
/// Mono: D200-D208
/// Stereo: D200-D208 and D210-D218
/// </summary>
void CXPokey::CopyAtariMemoryToPokey() {
    // Write bytes 0-7. Write 0x00 if the channel is inactive.
    for (int i = 0; i < 8; i++) { //
        const auto channel = i / 2;
        auto on = g_ChannelControl.IsChannelOn(channel);
        auto b = on ? g_AtariTrackerDriver->GetAtari()->GetByteAt(0xd200 + i) : 0x00; // TODO: Have GetPOKEYRegister()
        m_pokey.PutByte(i, b);
        if (stereo) {
            auto rightChannel = channel + 4;
            auto on = g_ChannelControl.IsChannelOn(rightChannel);
            b = on ? g_AtariTrackerDriver->GetAtari()->GetByteAt(0xd210 + i) : 0x00;
            m_pokey.PutByte(i + 16, b);
        }
    }

    // AUDCTL
    m_pokey.PutByte(0x08, g_AtariTrackerDriver->GetAtari()->GetByteAt(0xd208));
    if (stereo) {
        m_pokey.PutByte(0x08, g_AtariTrackerDriver->GetAtari()->GetByteAt(0xd218));
    }
}
