//
// Pokey.h header file
// originally made by Raster, 2002-2009
// reworked by VinsCool, 2021-2022
//


#pragma once

#include "Atari.h"
#include "Pokey.h"


#define CHANNELS		2
#define BITRESOLUTION	8
#define OUTPUTFREQ		44100		//22050		//44100
#define BUFFER_SIZE		0x8000		//must be a power of 2
#define FRAMERATE		((g_ntsc) ? 60 : 50)
#define CHUNK_SIZE		(BITRESOLUTION / 8 * CHANNELS * OUTPUTFREQ / FRAMERATE)
#define LATENCY			3			//3/50sec
#define LATENCY_SIZE	(LATENCY * CHUNK_SIZE)

#define CYCLESPERSCREEN	((float)FREQ_17() / FRAMERATE)
#define CYCLESPERSAMPLE	((float)FREQ_17() / 44100)

class CXPokey
{
// Construction
public:
	CXPokey();
	~CXPokey();
	BOOL InitSound();
	BOOL DeInitSound();
	BOOL ReInitSound();
	BOOL RenderSound1_50(int instrspeed);
	void RenderSoundV2(int instrspeed, BYTE* buffer, int& length);
	void MemToPokey();
    bool IsSoundDriverLoaded() { return m_pokey.IsSoundDriverLoaded(); }
	const WAVEFORMATEX* GetSoundFormat() const  { return &m_SoundFormat; };

private:
    CPokey              m_pokey;
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


    static CAtari::ClockFrequency FREQ_17();

};