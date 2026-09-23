

// TODO: Replace the plugin interface with a permanent emulation core

#include "Atari.h"
#include "Pokey.h"
#include "StdAfx.h"
#include "Messages.h"

// CPokey's constructor/destructor and pure bookkeeping methods are
// implemented in PokeyCore.cpp (only InitSound()/InitPokeyDll() below
// actually call LoadLibrary()/GetProcAddress() - a real DLL-loading hazard,
// see plans/EXPORTWAV_PLAN.md).

void CPokey::InitSound() {
    m_soundDriver = InitPokeyDll();
}

//TODO: Add a method for letting the user chose which plugin they would like to use instead of the current default/fallback setup
CPokey::SoundDriver CPokey::InitPokeyDll() {

    m_about = "";

    // apokeysnd.dll is first loaded, will be used in priority if it is found
    if (m_pokey_dll = LoadLibrary("apokeysnd.dll")) {
        CString warningMessage = "";

        APokeySound_Initialize = (APokeySound_Initialize_PROC)GetProcAddress(m_pokey_dll, "APokeySound_Initialize");
        if (!APokeySound_Initialize) {
            warningMessage += "APokeySound_Initialize\n";
        }

        APokeySound_PutByte = (APokeySound_PutByte_PROC)GetProcAddress(m_pokey_dll, "APokeySound_PutByte");
        if (!APokeySound_PutByte) {
            warningMessage += "APokeySound_PutByte\n";
        }

        APokeySound_GetRandom = (APokeySound_GetRandom_PROC)GetProcAddress(m_pokey_dll, "APokeySound_GetRandom");
        if (!APokeySound_GetRandom) {
            warningMessage += "APokeySound_GetRandom\n";
        }

        APokeySound_Generate = (APokeySound_Generate_PROC)GetProcAddress(m_pokey_dll, "APokeySound_Generate");
        if (!APokeySound_Generate) {
            warningMessage += "APokeySound_Generate\n";
        }

        APokeySound_About = (APokeySound_About_PROC)GetProcAddress(m_pokey_dll, "APokeySound_About");
        if (!APokeySound_About) {
            warningMessage += "APokeySound_About\n";
        }

        // Get "About" data from apokeysnd driver, then finalise the inisialisation
        if (warningMessage.IsEmpty()) {
            const char *name, *author, *description;
            APokeySound_About(&name, &author, &description);
            m_about.Format("%s\n%s\n%s", name, author, description);
            return APOKEYSND;
        }

        // If an error is caught, the plugin will be unloaded with an error message showing the problematic procedures
        SendWarningMessage("Pokey library error", "Error:\nNo compatible 'apokeysnd.dll',\ntherefore the Pokey sound can't be performed.\nIncompatibility with:" + warningMessage);
        DeInitPokeyDll();
    }

    // sa_pokey.dll will be loaded next if apokeysnd.dll was not found or had an error, as a fallback
    if (m_pokey_dll = LoadLibrary("sa_pokey.dll")) {
        CString warningMessage = "";

        Pokey_Initialise = (Pokey_Initialise_PROC)GetProcAddress(m_pokey_dll, "Pokey_Initialise");
        if (!Pokey_Initialise) {
            warningMessage += "Pokey_Initialise\n";
        }

        Pokey_SoundInit = (Pokey_SoundInit_PROC)GetProcAddress(m_pokey_dll, "Pokey_SoundInit");
        if (!Pokey_SoundInit) {
            warningMessage += "Pokey_SoundInit\n";
        }

        Pokey_Process = (Pokey_Process_PROC)GetProcAddress(m_pokey_dll, "Pokey_Process");
        if (!Pokey_Process) {
            warningMessage += "Pokey_Process\n";
        }

        Pokey_GetByte = (Pokey_GetByte_PROC)GetProcAddress(m_pokey_dll, "Pokey_GetByte");
        if (!Pokey_GetByte) {
            warningMessage += "Pokey_GetByte\n";
        }

        Pokey_PutByte = (Pokey_PutByte_PROC)GetProcAddress(m_pokey_dll, "Pokey_PutByte");
        if (!Pokey_PutByte) {
            warningMessage += "Pokey_PutByte\n";
        }

        Pokey_About = (Pokey_About_PROC)GetProcAddress(m_pokey_dll, "Pokey_About");
        if (!Pokey_About) {
            warningMessage += "Pokey_About\n";
        }

        // Get "About" data from sa_pokey driver, then finalise the inisialisation
        if (warningMessage.IsEmpty()) {
            char *name, *author, *description;
            Pokey_About(&name, &author, &description);
            m_about.Format("%s\n%s\n%s", name, author, description);
            Pokey_Initialise(0, 0);
            return SA_POKEY;
        }

        // If an error is caught, the plugin will be unloaded with an error message showing the problematic procedures
        SendWarningMessage("Pokey library error", "Error:\nNo compatible 'sa_pokey.dll',\ntherefore the Pokey sound can't be performed.\nIncompatibility with:" + warningMessage);
        DeInitPokeyDll();
    }

    // If no POKEY emulation plugin was found, no sound emulation will be output
    SendWarningMessage("LoadLibrary error", "Warning:\nNone of 'apokeysnd.dll' or 'sa_pokey.dll' found,\ntherefore the Pokey sound can't be performed.");

    return NONE;
}

// CPokey::DeInitPokeyDll()/GetSoundDriver()/IsSoundDriverLoaded()/
// InitPokeys()/PutByte() are implemented in PokeyCore.cpp.