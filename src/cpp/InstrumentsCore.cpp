#include "StdAfx.h"

#include "Atari.h"
#include "Instruments.h"
#include "Notes.h"

// The CInstruments methods here have no dependency on Global.h (unlike the
// rest of Instruments.cpp, which reads g_AtariTrackerDriver/g_tracks4_8/
// g_keyboard_RememberOctavesAndVolumes), so they're kept separate to keep
// this half of the class testable without linking those globals - same
// pattern as Tuning.cpp/TuningTables.cpp and Tracks.cpp/TracksEdit.cpp.
// GetFrequency() below is the one exception that does touch g_Atari, but
// only via GetByteAt() - a plain array read on CAtari's own memory buffer,
// with no coupling of its own (confirmed while scoping ExportV2 Batch C).

extern CAtari g_Atari;

/// <summary>
/// CInstruments Class Object constructor
/// The struct TInstrument will be initialised in order to use Instruments
/// TODO: Initialise more parameters, set #define values to static constants
/// </summary>
CInstruments::CInstruments() :canvasXY(nullptr)
{
    m_instr = new TInstrument[INSTRSNUM];
}

/// <summary>
/// CInstruments Class Object deconstructor.
/// The struct TInstrument will be deleted here, and set to NULL
/// A new CInstruments object must be created to use Instruments afterwards
/// </summary>
CInstruments::~CInstruments()
{
    if (m_instr)
    {
        delete[] m_instr;
    }
    m_instr = NULL;
}

void CInstruments::SetCanvas(CCanvasXY& canvasXY) {
    this->canvasXY = &canvasXY;
}

/// <summary>
/// Reset all 64 instruments to startup defaults
/// </summary>
void CInstruments::InitInstruments()
{
    for (int i = 0; i < INSTRSNUM; i++)
    {
        ClearInstrument(i);
    }
}

/// <summary>
/// Check the instrument parameters and adjust them to fit boundaries if needed
/// </summary>
/// <param name="instr"></param>
void CInstruments::CheckInstrumentParameters(int instr)
{
    TInstrument* ai = GetInstrument(instr);
    if (!ai)
    {
        return;
    }

    //ENVELOPE len-go loop control
    if (ai->parameters[PAR_ENV_GOTO] > ai->parameters[PAR_ENV_LENGTH])
    {
        ai->parameters[PAR_ENV_GOTO] = ai->parameters[PAR_ENV_LENGTH];
    }

    //TABLE len-go loop control
    if (ai->parameters[PAR_TBL_GOTO] > ai->parameters[PAR_TBL_LENGTH])
    {
        ai->parameters[PAR_TBL_GOTO] = ai->parameters[PAR_TBL_LENGTH];
    }

    //check the cursor in the envelope
    if (ai->editEnvelopeX > ai->parameters[PAR_ENV_LENGTH])
    {
        ai->editEnvelopeX = ai->parameters[PAR_ENV_LENGTH];
    }

    //check the cursor in the table
    if (ai->editNoteTableCursorPos > ai->parameters[PAR_TBL_LENGTH])
    {
        ai->editNoteTableCursorPos = ai->parameters[PAR_TBL_LENGTH];
    }

    //something changed => Save instrument "to Atari"
    // NOTE: Done from the outside
}

/// <summary>
/// Calculate some text hints for this instrument.
/// When the instrument name is rendered there will be some hints below it
/// </summary>
/// <param name="instr"></param>
void CInstruments::RecalculateFlag(int instr)
{
    TInstrument* ti = GetInstrument(instr);
    if (!ti) {
        // Todo: Rather exception in GetInstrument()
        return;
    }

    BYTE flags = 0;

    // Analyse the instrument envelope for the Autofilter, Bass16 and Portamento flags
    for (int i = 0; i <= ti->parameters[PAR_ENV_LENGTH]; i++)
    {
        // Autofilter?
        if (ti->envelope[i][EnvelopeParameter::FILTER])
        {
            flags |= IF_FILTER;
        }

        // Bass16?
        if (ti->envelope[i][EnvelopeParameter::DISTORTION] == 6)
        {
            flags |= IF_BASS16;
        }

        // Portamento?
        if (ti->envelope[i][EnvelopeParameter::PORTAMENTO])
        {
            flags |= IF_PORTAMENTO;
        }
    }

    // Analyse the instrument parameters for the AUDCTL flag
    for (int i = PAR_AUDCTL_15KHZ; i <= PAR_AUDCTL_POLY9; i++)
    {
        // AUDCTL?
        if (ti->parameters[i])
        {
            flags |= IF_AUDCTL;
        }
    }

    // Autofilter takes priority over Bass16 (RMT 1.28 driver only)
    if (flags & IF_FILTER && flags & IF_BASS16) { flags ^= IF_BASS16; }

    // Update the instrument hint flag to the new value
    ti->displayHintFlags = flags;
}

/// <summary>
/// Check if an instrument is empty.
/// Empty is defined as NO volume and all parameters are 0
/// </summary>
/// <param name="instr">Instrument #</param>
/// <returns>true if the instrument has values, False if it is in default state</returns>
BOOL CInstruments::CalculateNotEmpty(int instr)
{
    TInstrument* ti = GetInstrument(instr);
    if (!ti)
    {
        return 0;
    }

    for (int i = 0; i <= ti->parameters[PAR_ENV_LENGTH]; i++)
    {
        for (int j = 0; j < ENVROWS; j++)
        {
            if (ti->envelope[i][j] != 0)
            {
                return 1;
            }
        }
    }
    for (int i = 0; i < PARCOUNT; i++)
    {
        if (ti->parameters[i] != 0)
        {
            return 1;
        }
    }
    return 0; // Is empty
}

/// <summary>
/// Calculate the note according to distortion in the  first entry in the note table.
/// </summary>
/// <param name="instr">Instrument #</param>
/// <param name="note">which note</param>
/// <returns>note</returns>
int CInstruments::GetNote(int instr, int note)
{
    TInstrument* tt = GetInstrument(instr);
    if (!tt)
    {
        return -1;
    }

    // Only for NOTES table
    if (tt->parameters[PAR_TBL_TYPE] == 0)
    {
        // Shift notes according to table 0
        note = (note + tt->noteTable[0]) & 0xff;
    }

    // The note must be within valid boundaries
    if (!CNotes::IsValidNote(note)) { return -1; }
    return note;
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
    if (!tt)
    {
        return -1;
    }

    // Only for NOTES table
    if (tt->parameters[PAR_TBL_TYPE] == 0)
    {
        // Shift notes according to table 0
        note = (note + tt->noteTable[0]) & 0xff;
    }

    // The note must be within valid boundaries
    if (note < 0 || note >= CNotes::NOTESNUM)
    {
        return -1;
    }

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
