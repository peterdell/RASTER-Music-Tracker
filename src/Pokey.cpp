// Original code by Raster, 2002-2009
// Experimental changes and additions by VinsCool, 2021-2023
// TODO: Replace the plugin interface with a permanent emulation core
// FIXME: Use a better backend (DirectSound is outdated...)

#include "stdafx.h"
#include "Pokey.h"
#include "Atari.h"

extern HWND g_hwnd;
extern CString g_aboutpokey;
extern BOOL g_ntsc;
extern int g_tracks4_8;

// TOD Copied from PokeyRenderer
#define OUTPUTFREQ		44100		//22050		//44100

CAtari::ClockFrequency FREQ_17() {
    return CAtari::GetClockFrequency(g_ntsc);
}


APokeySound_Initialize_PROC APokeySound_Initialize;
APokeySound_PutByte_PROC APokeySound_PutByte;
APokeySound_GetRandom_PROC APokeySound_GetRandom;	// Unused?
APokeySound_Generate_PROC APokeySound_Generate;
APokeySound_About_PROC APokeySound_About;


Pokey_Initialise_PROC Pokey_Initialise;
Pokey_SoundInit_PROC Pokey_SoundInit;
Pokey_Process_PROC Pokey_Process;
Pokey_GetByte_PROC Pokey_GetByte;	// Unused?
Pokey_PutByte_PROC Pokey_PutByte;
Pokey_About_PROC Pokey_About;

CPokey::CPokey()
{
    m_soundDriverId = SOUND_DRIVER_NONE;
    m_pokey_dll = NULL;

    m_soundDriverId = InitPokeyDll();
}

CPokey::~CPokey()
{
    DeInitPokeyDll();
}


//TODO: Add a method for letting the user chose which plugin they would like to use instead of the current default/fallback setup
CPokey::POKEY_SoundDriver CPokey::InitPokeyDll()
{


    // apokeysnd.dll is first loaded, will be used in priority if it is found
    if (m_pokey_dll = LoadLibrary("apokeysnd.dll"))
    {
        CString warningMessage = "";

        APokeySound_Initialize = (APokeySound_Initialize_PROC)GetProcAddress(m_pokey_dll, "APokeySound_Initialize");
        if (!APokeySound_Initialize) warningMessage += "APokeySound_Initialize\n";

        APokeySound_PutByte = (APokeySound_PutByte_PROC)GetProcAddress(m_pokey_dll, "APokeySound_PutByte");
        if (!APokeySound_PutByte) warningMessage += "APokeySound_PutByte\n";

        APokeySound_GetRandom = (APokeySound_GetRandom_PROC)GetProcAddress(m_pokey_dll, "APokeySound_GetRandom");
        if (!APokeySound_GetRandom) warningMessage += "APokeySound_GetRandom\n";

        APokeySound_Generate = (APokeySound_Generate_PROC)GetProcAddress(m_pokey_dll, "APokeySound_Generate");
        if (!APokeySound_Generate) warningMessage += "APokeySound_Generate\n";

        APokeySound_About = (APokeySound_About_PROC)GetProcAddress(m_pokey_dll, "APokeySound_About");
        if (!APokeySound_About) warningMessage += "APokeySound_About\n";

        // Get "About" data from apokeysnd driver, then finalise the inisialisation
        if (warningMessage.IsEmpty())
        {
            const char* name, * author, * description;
            APokeySound_About(&name, &author, &description);
            g_aboutpokey.Format("%s\n%s\n%s", name, author, description);
            APokeySound_Initialize(g_tracks4_8 == 8);	// STEREO enabled
            return SOUND_DRIVER_APOKEYSND;
        }

        // If an error is caught, the plugin will be unloaded with an error message showing the problematic procedures
        MessageBox(g_hwnd, "Error:\nNo compatible 'apokeysnd.dll',\ntherefore the Pokey sound can't be performed.\nIncompatibility with:" + warningMessage, "Pokey library error", MB_ICONEXCLAMATION);
        FreeLibrary(m_pokey_dll);
    }

    // sa_pokey.dll will be loaded next if apokeysnd.dll was not found or had an error, as a fallback
    if (m_pokey_dll = LoadLibrary("sa_pokey.dll"))
    {
        CString warningMessage = "";

        Pokey_Initialise = (Pokey_Initialise_PROC)GetProcAddress(m_pokey_dll, "Pokey_Initialise");
        if (!Pokey_Initialise) warningMessage += "Pokey_Initialise\n";

        Pokey_SoundInit = (Pokey_SoundInit_PROC)GetProcAddress(m_pokey_dll, "Pokey_SoundInit");
        if (!Pokey_SoundInit) warningMessage += "Pokey_SoundInit\n";

        Pokey_Process = (Pokey_Process_PROC)GetProcAddress(m_pokey_dll, "Pokey_Process");
        if (!Pokey_Process) warningMessage += "Pokey_Process\n";

        Pokey_GetByte = (Pokey_GetByte_PROC)GetProcAddress(m_pokey_dll, "Pokey_GetByte");
        if (!Pokey_GetByte) warningMessage += "Pokey_GetByte\n";

        Pokey_PutByte = (Pokey_PutByte_PROC)GetProcAddress(m_pokey_dll, "Pokey_PutByte");
        if (!Pokey_PutByte) warningMessage += "Pokey_PutByte\n";

        Pokey_About = (Pokey_About_PROC)GetProcAddress(m_pokey_dll, "Pokey_About");
        if (!Pokey_About) warningMessage += "Pokey_About\n";

        // Get "About" data from sa_pokey driver, then finalise the inisialisation
        if (warningMessage.IsEmpty())
        {
            char* name, * author, * description;
            Pokey_About(&name, &author, &description);
            g_aboutpokey.Format("%s\n%s\n%s", name, author, description);
            Pokey_Initialise(0, 0);

            // Specify the machine region and if it uses Stereo or Mono, as well as the frequency for the sound output
            Pokey_SoundInit(FREQ_17(), OUTPUTFREQ, (g_tracks4_8 == 8) + 1);
            return SOUND_DRIVER_SA_POKEY;
        }

        // If an error is caught, the plugin will be unloaded with an error message showing the problematic procedures
        MessageBox(g_hwnd, "Error:\nNo compatible 'sa_pokey.dll',\ntherefore the Pokey sound can't be performed.\nIncompatibility with:" + warningMessage, "Pokey library error", MB_ICONEXCLAMATION);
        FreeLibrary(m_pokey_dll);
    }

    // If no POKEY emulation plugin was found, no sound emulation will be output
    MessageBox(g_hwnd, "Warning:\nNone of 'apokeysnd.dll' or 'sa_pokey.dll' found,\ntherefore the Pokey sound can't be performed.", "LoadLibrary error", MB_ICONEXCLAMATION);

    return SOUND_DRIVER_NONE;
}

void CPokey::DeInitPokeyDll() {

    if (m_pokey_dll)
    {
        FreeLibrary(m_pokey_dll);
        m_pokey_dll = NULL;
    }

}

