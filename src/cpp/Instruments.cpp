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
    if (!instrument) return;

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
    if (!ti) return;

    // Validate
    if (px < 0 || px >= ti->parameters[PAR_ENV_LENGTH] + 1) return;
    if (newVolume < 0 || newVolume > 15) return;

    int ep = (right && g_tracks4_8 > 4) ? EnvelopeParameter::VOLUMER : EnvelopeParameter::VOLUMEL;
    ti->envelope[px][ep] = newVolume;

    // Recalc some info about the updated instrument
    Update(instr);
}

/// <summary>
/// Convert the note to a frequency according to distortion in first
/// envelope column or first entry in the note table.
/// </summary>
/// <param name="instr">Instrument #</param>
/// <param name="note">which note</param>
/// <returns>frequency</returns>
int CInstruments::GetFrequency(int instr, int note)
{
    TInstrument* tt = GetInstrument(instr);
    if (!tt) return -1;

    // Only for NOTES table
    if (tt->parameters[PAR_TBL_TYPE] == 0)
    {
        // Shift notes according to table 0
        note = (note + tt->noteTable[0]) & 0xff;
    }

    // The note must be within valid boundaries
    if (note < 0 || note >= CNotes::NOTESNUM) return -1;

    // IMPORTANT NOTE: Tables are not set to a constant location! 
    // The function technically returns valid data, otherwise
    switch (tt->envelope[0][EnvelopeParameter::DISTORTION])
    {
    case 0x0C:
        return g_Atari.GetByteAt(RMT_FRQTABLES + 64 + note);
    case 0x06:
    case 0x0E:
        return g_Atari.GetByteAt(RMT_FRQTABLES + 128 + note);
    default:
        return g_Atari.GetByteAt(RMT_FRQTABLES + 192 + note);
    }
}

/// <summary>
/// Save octave and volume info in the instrument.
/// </summary>
/// <param name="instr">Instrument #</param>
/// <param name="oct">Last used octave</param>
/// <param name="vol">Last used volume</param>
void CInstruments::MemorizeOctaveAndVolume(int instr, int oct, int vol)
{
    TInstrument* ti = GetInstrument(instr);
    if (!ti) return;

    if (g_keyboard_RememberOctavesAndVolumes)
    {
        if (oct >= 0) ti->octave = oct;
        if (vol >= 0) ti->volume = vol;
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
    if (!ti) return;

    if (g_keyboard_RememberOctavesAndVolumes)
    {
        oct = ti->octave;
        vol = ti->volume;
    }
}