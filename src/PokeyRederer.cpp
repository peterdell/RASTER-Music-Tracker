// Original code by Raster, 2002-2009
// Experimental changes and additions by VinsCool, 2021-2023
// TODO: Replace the plugin interface with a permanent emulation core
// FIXME: Use a better backend (DirectSound is outdated...)

#include "stdafx.h"
#include "PokeyRederer.h"
#include "Atari.h"
#include "Global.h"
#include "ChannelControl.h"

// Needed for proper Machine Region and Stereo detection with POKEY plugins
// TODO: Rework, see https://forums.atariage.com/topic/325338-altirra-emulation-core-for-rmt/
int numTracksSetOnDriver = g_tracks4_8;
int ntscRegionSetOnDriver = g_ntsc;

static LPDIRECTSOUND          g_lpds;
static LPDIRECTSOUNDBUFFER    g_lpdsbPrimary;






int CXPokey::GetFrameRate(bool ntsc) {
    // TODO: This is not the exacty framerate, so maybe that's why the frequencies are a little off in CPokey?
    return ntsc ? 60 : 50;
}

int CXPokey::GetCyclesPerFrame(bool ntsc) {
    return (int)(((float)CAtari::GetClockFrequency(ntsc)) / GetFrameRate(ntsc));
}

CXPokey::CXPokey()
{
    m_SoundBuffer = NULL;
}

CXPokey::~CXPokey()
{
    DeInitSound();
}


BOOL CXPokey::InitSoundInternal(const bool ntsc, const WORD channels, const DWORD samplesPerSec, const WORD bitsPerSample)
{

    DeInitSound();	// Just in case, everything must be cleared before initialising

    m_Latency = 3; //3 Chunks

    m_ClockFrequency = CAtari::GetClockFrequency(ntsc);
    m_ChunkSize = (bitsPerSample / 8 * channels * samplesPerSec / GetFrameRate(ntsc));
    m_CyclesPerSample = ((float)m_ClockFrequency / samplesPerSec); // TODO Really float required?

    if (DirectSoundCreate(NULL, &g_lpds, NULL) != DS_OK)
    {
        MessageBox(g_hwnd, "Error: DirectSoundCreate", "DirectSound Error!", MB_OK | MB_ICONSTOP);
        return FALSE;
    }

    // Set cooperative level
    if (g_lpds->SetCooperativeLevel(AfxGetApp()->GetMainWnd()->m_hWnd, DSSCL_PRIORITY) != DS_OK)
    {
        MessageBox(g_hwnd, "Error: SetCooperativeLevel", "DirectSound Error!", MB_OK | MB_ICONSTOP);
        return FALSE;
    }

    // Set the emulated Machine Region and if Stereo is used
    numTracksSetOnDriver = g_tracks4_8;
    ntscRegionSetOnDriver = g_ntsc;

    // Set primary buffer format
    ZeroMemory(&m_SoundFormat, sizeof(WAVEFORMATEX));
    m_SoundFormat.wFormatTag = WAVE_FORMAT_PCM;
    m_SoundFormat.nChannels = channels;
    m_SoundFormat.nSamplesPerSec = samplesPerSec;
    m_SoundFormat.wBitsPerSample = bitsPerSample;
    m_SoundFormat.nBlockAlign = m_SoundFormat.wBitsPerSample / 8 * m_SoundFormat.nChannels;
    m_SoundFormat.nAvgBytesPerSec = m_SoundFormat.nSamplesPerSec * m_SoundFormat.nBlockAlign;
    m_SoundFormat.cbSize = 0;

    // Create primary buffer.
    ZeroMemory(&dsbdesc, sizeof(DSBUFFERDESC));
    dsbdesc.dwSize = sizeof(DSBUFFERDESC);
    dsbdesc.dwFlags = DSBCAPS_PRIMARYBUFFER;

    if (g_lpds->CreateSoundBuffer(&dsbdesc, &g_lpdsbPrimary, NULL) != DS_OK)
    {
        MessageBox(g_hwnd, "Error: CreatePrimarySoundBuffer", "DirectSound Error!", MB_OK | MB_ICONSTOP);
        return FALSE;
    }

    if (g_lpdsbPrimary->SetFormat(&m_SoundFormat) != DS_OK)
    {
        MessageBox(g_hwnd, "Error: SetFormat", "DirectSound Error!", MB_OK | MB_ICONSTOP);
        return FALSE;
    }

    ZeroMemory(&dsbdesc, sizeof(DSBUFFERDESC));
    dsbdesc.dwSize = sizeof(DSBUFFERDESC);
    dsbdesc.dwFlags = DSBCAPS_GETCURRENTPOSITION2 | DSBCAPS_LOCHARDWARE | DSBCAPS_GLOBALFOCUS | DSBCAPS_STICKYFOCUS;
    dsbdesc.dwBufferBytes = BUFFER_SIZE;
    dsbdesc.lpwfxFormat = &m_SoundFormat;

    if (g_nohwsoundbuffer ||
        g_lpds->CreateSoundBuffer(&dsbdesc, &m_SoundBuffer, NULL) != DS_OK)
    {
        dsbdesc.dwFlags = DSBCAPS_GETCURRENTPOSITION2 | DSBCAPS_LOCSOFTWARE | DSBCAPS_GLOBALFOCUS | DSBCAPS_STICKYFOCUS;
        if (g_lpds->CreateSoundBuffer(&dsbdesc, &m_SoundBuffer, NULL) != DS_OK)
        {
            MessageBox(g_hwnd, "Error: CreateSoundBuffer", "DirectSound Error!", MB_OK | MB_ICONSTOP);
            return FALSE;
        }
    }

    DSBCAPS bc;
    bc.dwSize = sizeof(bc);
    m_SoundBuffer->GetCaps(&bc);

    int r = m_SoundBuffer->Lock(0, BUFFER_SIZE, &Data1, &dwSize1, &Data2, &dwSize2, DSBLOCK_FROMWRITECURSOR);
    if (r == DS_OK)
    {
        memset(Data1, 0x80, dwSize1);
        if (Data2) memset(Data2, 0x80, dwSize2);
        m_SoundBuffer->Unlock(Data1, dwSize1, Data2, dwSize2);
    }

    m_SoundBuffer->Play(0, 0, DSBPLAY_LOOPING);
    m_SoundBuffer->GetCurrentPosition(&m_PlayCursor, &m_WriteCursorStart);
    const auto latencySize = m_Latency * m_ChunkSize;

    m_LoadPos = (m_WriteCursorStart + latencySize) & (BUFFER_SIZE - 1);  //initial latency (in hundredths of a second)

    // Initialise the POKEY emulation plugin once the sound interface is ready
    m_pokey.InitSound();

    return 1;
}

BOOL CXPokey::InitSound(const bool ntsc)
{
    return InitSoundInternal(ntsc, 2, 44100, 8);
}

BOOL CXPokey::DeInitSound()
{

    m_pokey.DeInitSound();
    g_aboutpokey = "No Pokey sound emulation.";

    if (m_SoundBuffer)
    {
        m_SoundBuffer->Stop();
        m_SoundBuffer->Release();
    }
    m_SoundBuffer = NULL;

    if (g_lpdsbPrimary) g_lpdsbPrimary->Release();
    g_lpdsbPrimary = NULL;

    if (g_lpds) g_lpds->Release();
    g_lpds = NULL;

    return 1;
}

BOOL CXPokey::ReInitSound(const bool ntsc)
{
    DeInitSound();
    return InitSound(ntsc);
}

WORD   CXPokey::GetChannels() const {
    return m_SoundFormat.nChannels;
}


int CXPokey::GetChunkSize() const {
    return m_ChunkSize;
}

int CXPokey::GetLatencySize() const {
    return 	(m_Latency * GetChunkSize());
}


BOOL CXPokey::RenderSound1_50(int instrspeed)
{
    if (!IsSoundDriverLoaded()) {
        return FALSE;
    }
    if (!m_SoundBuffer) {
        return FALSE;
    }

    m_SoundBuffer->GetCurrentPosition(&m_PlayCursor, &m_WriteCursor);

    //|||||||||||||||||||||||||||||||||||||||||
    //           ^|-------delta------->^
    //      m_WriteCursor           m_LoadPos

    int delta = (m_LoadPos - m_WriteCursor) & (BUFFER_SIZE - 1);

    const auto chunkSize = GetChunkSize();
    const auto latencySize = GetLatencySize();

    m_LoadSize = chunkSize;	// 1764  (882 samples * 2 channels)
    if (delta > latencySize)
    {
        if (delta > (BUFFER_SIZE / 2))
        {
            //we missed it, we're more than half a buffer late, so get to it and move on to what we should be right
            m_LoadPos = (m_WriteCursor + latencySize) & (BUFFER_SIZE - 1);
        }
        else
        {
            //we ran too far ahead so we slow down a bit (we will render smaller pieces than CHUNK_SIZE)
            m_LoadSize = chunkSize - (((delta - latencySize) / 16) & (BUFFER_SIZE - 1 - 1));	//-1-1 <=Just the numbers!
            //watched
            if (m_LoadSize <= 0) return 0; //we are so far ahead that it will not render at all
        }
    }
    else // delta <=LATENCY_SIZE
    {
        //we are closer than the required latency, that's great, but we'd rather speed up so that m_WriteCursor doesn't catch up with us (we'll render bigger chunks than CHUNK_SIZE)
        m_LoadSize = chunkSize + (((latencySize - delta) / 16) & (BUFFER_SIZE - 1 - 1));	//-1-1 <=Just the numbers!

        //watched
        if (m_LoadSize >= BUFFER_SIZE / 2) {
            return 0; //that would be a bigger piece than the size of a buffer pulse
        }
    }

    int rendersize = m_LoadSize;
    int renderpartsize = 0;
    int renderoffset = 0;

    for (; instrspeed > 0; instrspeed--)
    {
        //--- RMT - instrument play ---/
        if (g_rmtroutine) { CAtari::PlayRMT(); }	//one run RMT routine (instruments)
        MemToPokey();			//transfer from g_atarimem to POKEY (mono or stereo)
        renderpartsize = (rendersize / instrspeed) & 0xfffe;	//just the numbers

        switch (GetSoundDriver())
        {
        case CPokey::POKEY_SoundDriver::SOUND_DRIVER_APOKEYSND:
            // FIXME: Mono POKEY sound generation is broken, currently the reason for this is unclear...
        {

            int cycles = (unsigned short)((float)renderpartsize / GetChannels() * m_CyclesPerSample);
            while (cycles > 0 && renderpartsize > 0)
            {
                // The maximum number of cycles that can be generated is CYCLESPERSCREEN
                auto cyclesPerFrame = GetCyclesPerFrame(g_ntsc);
                int rencyc = (cycles > cyclesPerFrame ? cyclesPerFrame : cycles);
                renderpartsize = APokeySound_Generate(rencyc, (unsigned char*)&m_PlayBuffer + renderoffset, ASAP_FORMAT_U8);
                rendersize -= renderpartsize;
                renderoffset += renderpartsize;
                cycles -= rencyc;
            }
        }
        break;

        case CPokey::POKEY_SoundDriver::SOUND_DRIVER_SA_POKEY:
            Pokey_Process((unsigned char*)&m_PlayBuffer + renderoffset, (unsigned short)renderpartsize);
            rendersize -= renderpartsize;
            renderoffset += renderpartsize;
            break;
        }
    }

    if (!m_SoundBuffer) {
        return FALSE;	// Should help preventing crashes from reading NULL pointer when data is read faster than it could be processed
    }

    m_LoadSize = renderoffset; // Actually generated sample data

    int r = m_SoundBuffer->Lock(m_LoadPos, m_LoadSize, &Data1, &dwSize1, &Data2, &dwSize2, 0);

    if (r == DS_OK)
    {
        // Render the whole m_LoadSize into the buffer at once
        m_LoadPos = (m_LoadPos + m_LoadSize) & (BUFFER_SIZE - 1);

        // Transfer the first bit from the buffer to Data1
        memcpy(Data1, m_PlayBuffer, dwSize1);

        if (Data2)
        {
            // If it is divided, now transfer the remaining bits
            memcpy(Data2, m_PlayBuffer + dwSize1, dwSize2);
        }
        m_SoundBuffer->Unlock(Data1, dwSize1, Data2, dwSize2);

        return 1;
    }

    return 0;
}

// Initial WAV recorder process
// NOTE: This does NOT work with the Altirra plugin due to it hijacking the soundbuffer with its own thing...
void CXPokey::RenderSoundV2(int instrspeed, BYTE* buffer, int& length)
{
    int rendersize = GetChunkSize();
    int renderpartsize = 0;
    int renderoffset = 0;

    for (; instrspeed > 0; instrspeed--)
    {
        CAtari::SetPokey();
        MemToPokey();
        renderpartsize = (rendersize / instrspeed) & 0xfffe;

        switch (GetSoundDriver())
        {
        case CPokey::POKEY_SoundDriver::SOUND_DRIVER_SA_POKEY:
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
void CXPokey::MemToPokey()
{
    // If the variabes no longer match the last known parameters, the POKEY plugins must be re-initialised first
    bool resetPokey = false;
    if (numTracksSetOnDriver != g_tracks4_8 || ntscRegionSetOnDriver != g_ntsc)
    {
        numTracksSetOnDriver = g_tracks4_8;
        ntscRegionSetOnDriver = g_ntsc;
        resetPokey = true;
        //ReInitSound();
        //return;
    }

    // Check for which POKEY plugin to use, and process whichever is currently active
    switch (m_pokey.GetSoundDriver())
    {
    case CPokey::POKEY_SoundDriver::SOUND_DRIVER_APOKEYSND:
        if (resetPokey)
            APokeySound_Initialize(g_tracks4_8 == 8);
        for (int i = 0; i <= 8; i++)	// 0-7 + 8 (AUDCTL)
        {
            APokeySound_PutByte(i, (i & 0x01) && !GetChannelOnOff(i / 2) ? 0 : g_atarimem[0xd200 + i]);
            if (numTracksSetOnDriver == 8)
                APokeySound_PutByte(i + 16, (i & 0x01) && !GetChannelOnOff(i / 2 + 4) ? 0 : g_atarimem[0xd210 + i]);	// Stereo
        }
        break;

    case CPokey::POKEY_SoundDriver::SOUND_DRIVER_SA_POKEY:
        if (resetPokey) {
            Pokey_SoundInit(m_ClockFrequency, (WORD)m_SoundFormat.nSamplesPerSec, (g_tracks4_8 == 8) + 1);
        }
        for (int i = 0; i <= 8; i++)	// 0-7 + 8 (AUDCTL)
        {
            Pokey_PutByte(i, (i & 0x01) && !GetChannelOnOff(i / 2) ? 0 : g_atarimem[0xd200 + i]);
            if (numTracksSetOnDriver == 8)
                Pokey_PutByte(i + 16, (i & 0x01) && !GetChannelOnOff(i / 2 + 4) ? 0 : g_atarimem[0xd210 + i]);		// Stereo
        }
        break;
    }
}
