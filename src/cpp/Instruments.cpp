#include "StdAfx.h"

#include "AtariTrackerDriver.h"
#include "Global.h"
#include "Instruments.h"
#include "Notes.h"

extern CAtariTrackerDriver* g_AtariTrackerDriver;
extern int g_tracks4_8; // TODO Move out

// shpar[]/shenv[] (parameter/envelope-row descriptor tables) moved to
// InstrumentsAtaFormat.cpp: pure compile-time data (InstrumentGUIPosition's
// members are all constexpr layout constants, see General.h) needed by
// IO_Instruments.cpp's SaveInstrument()/LoadInstrument(), which has no
// other reason to depend on this file.

/// <summary>
/// Reset an instrument to startup defaults
/// </summary>
/// <param name="instrumentNr">Index of the instrument 0-63</param>
void CInstruments::ClearInstrument(int instrNr)
{
    TInstrument* instrument = GetInstrument(instrNr);
    if (!instrument)
    {
        return;
    }

    // Turn off this instrument on all channels
    g_AtariTrackerDriver->InstrumentTurnOff(instrNr);

    // Clear everything/All zero
    memset(instrument, 0, sizeof(TInstrument));

    // Init the name "Instrument XX"
    int len = sprintf(instrument->name, "Instrument %02X", instrNr);

    // Replace all the remaining characters with spaces
    memset(instrument->name + len, ' ', INSTRUMENT_NAME_MAX_LEN - len);

    // Set some initial values
    instrument->activeEditSection = InstrumentSection::ENVELOPE;	// Activate on the Envelope, so testing instruments wouldn't cause accidental rename
    instrument->editNameCursorPos = 0;								// 0 character name
    instrument->editParameterNr = PAR_ENV_LENGTH;					// Envelope length is the default parameter to edit
    instrument->editEnvelopeX = 0;
    instrument->editEnvelopeY = 1;									// Volume left
    instrument->editNoteTableCursorPos = 0;							// 0 element in the table
    instrument->octave = 0;
    instrument->volume = MAXVOLUME;

    // Apply to Atari Mem
    Update(instrNr);
}

/// <summary>
/// Set the volume level for a channel
/// </summary>
/// <param name="instr">Instrument #</param>
/// <param name="right">True - then use the stereo/right channels</param>
/// <param name="px">X position in the envelope</param>
/// <param name="newVolume">volume level to set</param>
void CInstruments::SetEnvelopeVolume(int instr, BOOL right, int px, int newVolume)
{
    TInstrument* ti = GetInstrument(instr);
    if (!ti)
    {
        return;
    }

    // Validate
    if (px < 0 || px >= ti->parameters[PAR_ENV_LENGTH] + 1)
    {
        return;
    }
    if (newVolume < 0 || newVolume > 15)
    {
        return;
    }

    int ep = (right && g_tracks4_8 > 4) ? EnvelopeParameter::VOLUMER : EnvelopeParameter::VOLUMEL;
    ti->envelope[px][ep] = newVolume;

    // Recalc some info about the updated instrument
    Update(instr);
}

// CInstruments::GetFrequency() is implemented in InstrumentsCore.cpp (only
// needs g_Atari.GetByteAt(), a plain array read on CAtari's own memory
// buffer with no coupling of its own).

/// <summary>
/// Save octave and volume info in the instrument.
/// </summary>
/// <param name="instr">Instrument #</param>
/// <param name="oct">Last used octave</param>
/// <param name="vol">Last used volume</param>
void CInstruments::MemorizeOctaveAndVolume(int instr, int oct, int vol)
{
    TInstrument* ti = GetInstrument(instr);
    if (!ti)
    {
        return;
    }

    if (g_keyboard_RememberOctavesAndVolumes)
    {
        if (oct >= 0)
        {
            ti->octave = oct;
        }
        if (vol >= 0)
        {
            ti->volume = vol;
        }
    }
}

/// <summary>
/// Load octave and volume info from the instrument
/// </summary>
/// <param name="instr">Instrument #</param>
/// <param name="oct">Last used octave</param>
/// <param name="vol">Last used volume</param>
void CInstruments::RememberOctaveAndVolume(int instr, int& oct, int& vol)
{
    TInstrument* ti = GetInstrument(instr);
    if (!ti)
    {
        return;
    }

    if (g_keyboard_RememberOctavesAndVolumes)
    {
        oct = ti->octave;
        vol = ti->volume;
    }
}