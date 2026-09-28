#include "Atari.h"
#include "Pokey.h"
#include "StdAfx.h"

// CPokey's constructor/destructor and pure bookkeeping methods (DeInitSound/
// DeInitPokeyDll/GetAbout/GetSoundDriver/IsSoundDriverLoaded/InitPokeys/
// PutByte) only touch CPokey's own state and the function-pointer globals
// below - they never call LoadLibrary/GetProcAddress. m_soundDriver
// defaults to NONE and only InitPokeyDll() (kept in Pokey.cpp, the real
// LoadLibrary("apokeysnd.dll")/LoadLibrary("sa_pokey.dll") hazard) ever
// changes it away from NONE, so every switch on GetSoundDriver() here stays
// a provably safe no-op unless InitSound() is called first - same pattern
// as CSongTimer's m_timerRoutine guard. Kept separate to keep this half
// testable, same split as Song.cpp/SongCore.cpp etc.

APokeySound_Initialize_PROC APokeySound_Initialize;
APokeySound_PutByte_PROC APokeySound_PutByte;
APokeySound_GetRandom_PROC APokeySound_GetRandom; // Unused?
APokeySound_Generate_PROC APokeySound_Generate;
APokeySound_About_PROC APokeySound_About;

Pokey_Initialise_PROC Pokey_Initialise;
Pokey_SoundInit_PROC Pokey_SoundInit;
Pokey_Process_PROC Pokey_Process;
Pokey_GetByte_PROC Pokey_GetByte; // Unused?
Pokey_PutByte_PROC Pokey_PutByte;
Pokey_About_PROC Pokey_About;

CPokey::CPokey() {
    m_soundDriver = NONE;
    m_pokey_dll = NULL;

    m_initialized = false;
    m_ntsc = false;
    m_stereo = false;
}

CPokey::~CPokey() {
    DeInitSound();
}

void CPokey::DeInitSound() {
    m_soundDriver = NONE;
    m_about = "No POKEY emulation loaded";

    m_initialized = false;
    m_ntsc = false;
    m_stereo = false;

    DeInitPokeyDll();
}

CString CPokey::GetAbout() const {
    return m_about;
}

void CPokey::DeInitPokeyDll() {

    if (m_pokey_dll) {
        FreeLibrary(m_pokey_dll);
        m_pokey_dll = NULL;
    }
}

CPokey::SoundDriver CPokey::GetSoundDriver() const {
    return m_soundDriver;
}

bool CPokey::IsSoundDriverLoaded() const {
    return m_soundDriver != NONE;
}

void CPokey::InitPokeys(const bool ntsc, const bool stereo, const DWORD samplesPerSec) {

    if (!m_initialized || m_ntsc != ntsc || m_stereo != stereo || m_samplesPerSec != samplesPerSec) {
        switch (GetSoundDriver()) {
        case CPokey::SoundDriver::APOKEYSND:
            APokeySound_Initialize(stereo);

            break;

        case CPokey::SoundDriver::SA_POKEY:
            // Currently cast to WORD, because no rates above 64kHz are supported.
            Pokey_SoundInit(CAtari::GetClockFrequency(ntsc), (WORD)samplesPerSec, stereo ? 2 : 1);
            break;
        }

        m_initialized = true;
        m_ntsc = ntsc;
        m_stereo = stereo;
        m_samplesPerSec = samplesPerSec;
    }
}

void CPokey::PutByte(const byte address, const byte value) {
    switch (GetSoundDriver()) {
    case CPokey::SoundDriver::APOKEYSND:

        APokeySound_PutByte(address, value);
        break;

    case CPokey::SoundDriver::SA_POKEY:

        Pokey_PutByte(address, value);
        break;
    }
}
