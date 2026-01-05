/*
    Wrapper for "sa_pokey.dll" by Avery Lee (phaeron).
    See Altirra source code https://www.virtualdub.org/altirra.html in the folder "src/AltirraRMTPOKEY".
*/

#pragma once


typedef enum
{
    ASAP_FORMAT_U8 = 8,       /* unsigned char */
    ASAP_FORMAT_S16_LE = 16,  /* signed short, little-endian */
    ASAP_FORMAT_S16_BE = -16  /* signed short, big-endian */
} ASAP_SampleFormat;

typedef int APokeySound_abool;

typedef void (*APokeySound_Initialize_PROC)(APokeySound_abool stereo);
typedef void (*APokeySound_PutByte_PROC)(int addr, int data);
typedef int  (*APokeySound_GetRandom_PROC)(int addr, int cycle);
typedef int  (*APokeySound_Generate_PROC)(int cycles, byte buffer[], ASAP_SampleFormat format);
typedef void (*APokeySound_About_PROC)(const char** name, const char** author, const char** description);

extern APokeySound_Initialize_PROC APokeySound_Initialize;
extern APokeySound_PutByte_PROC APokeySound_PutByte;
extern APokeySound_GetRandom_PROC APokeySound_GetRandom;	// Unused?
extern APokeySound_Generate_PROC APokeySound_Generate;
extern APokeySound_About_PROC APokeySound_About;

typedef void (*Pokey_Initialise_PROC)(int*, char**);
typedef void (*Pokey_SoundInit_PROC)(DWORD, WORD, BYTE);
typedef void (*Pokey_Process_PROC)(BYTE*, const WORD);
typedef BYTE(*Pokey_GetByte_PROC)(WORD);
typedef void (*Pokey_PutByte_PROC)(WORD, BYTE);
typedef void (*Pokey_About_PROC)(char**, char**, char**);

extern Pokey_Initialise_PROC Pokey_Initialise;
extern Pokey_SoundInit_PROC Pokey_SoundInit;
extern Pokey_Process_PROC Pokey_Process;
extern Pokey_GetByte_PROC Pokey_GetByte;	// Unused?
extern Pokey_PutByte_PROC Pokey_PutByte;
extern Pokey_About_PROC Pokey_About;

class CPokey
{
public:

    typedef enum
    {
        NONE,
        APOKEYSND,
        SA_POKEY
    } SoundDriver;

    CPokey();
    ~CPokey();

    void InitSound();
    void DeInitSound();
    CString GetAbout() const;

    SoundDriver GetSoundDriver() const;
    bool IsSoundDriverLoaded()  const;

    void InitPokeys(const bool ntsc, const bool stereo, const DWORD samplesPerSec);
    void PutByte(const byte address, const byte value);

private:
    SoundDriver    m_soundDriver;
    HINSTANCE      m_pokey_dll;
    CString m_about;

    bool m_initialized;
    bool m_ntsc;
    bool m_stereo;
    DWORD m_samplesPerSec;

    SoundDriver InitPokeyDll();	// The function will return the m_soundDriverId value
    void DeInitPokeyDll();
};