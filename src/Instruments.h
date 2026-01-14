#pragma once
#include "StdAfx.h"

#include <iosfwd>

#include "General.h"

#include "InstrumentTypes.h"


extern const Tshpar shpar[NUMBER_OF_PARAMS];

extern const Tshenv shenv[ENVROWS];

class CInstruments
{
public:
    CInstruments();
    ~CInstruments();

    void InitInstruments();
    void ClearInstrument(int it);

    void CheckInstrumentParameters(int instr);
    void RecalculateFlag(int instr);
    BOOL CalculateNotEmpty(int instr);
    void SetEnvelopeVolume(int instr, BOOL right, int px, int py);
    int GetFrequency(int instr, int note);
    int GetNote(int instr, int note);
    void MemorizeOctaveAndVolume(int instr, int oct, int vol);
    void RememberOctaveAndVolume(int instr, int& oct, int& vol);

    BOOL IsValidInstrument(int instr) { return instr >= 0 && instr < INSTRSNUM; };

    BYTE GetFlag(int instr) { return IsValidInstrument(instr) ? m_instr[instr].displayHintFlags : -1; };
    BYTE GetParameter(int instr, int param) { return IsValidInstrument(instr) ? m_instr[instr].parameters[param] : -1; };
    int GetParameterNumber(int instr) { return IsValidInstrument(instr) ? m_instr[instr].editParameterNr : -1; };
    InstrumentSection GetActiveEditSection(int instr) { return IsValidInstrument(instr) ? m_instr[instr].activeEditSection : InstrumentSection::NONE; };
    int GetNameCursorPosition(int instr) { return IsValidInstrument(instr) ? m_instr[instr].editNameCursorPos : -1; };
    char* GetName(int instr) { return IsValidInstrument(instr) ? m_instr[instr].name : NULL; };

    TInstrument* GetInstrument(int instr) { return IsValidInstrument(instr) ? &m_instr[instr] : NULL; };
    TInstrumentsAll* GetInstrumentsAll() { return (TInstrumentsAll*)m_instr; };

    // GUI
    void DrawInstrument(int it);

    BOOL GetGUIArea(int instr, InstrumentGUIZone zone, CRect& rect);
    BOOL CursorGoto(int instr, CPoint point, int pzone);

    // IO
    void Update(int it);

    int SaveAll(std::ofstream& ou, InstrumentIOType iotype);
    int LoadAll(std::ifstream& in, InstrumentIOType iotype);

    int SaveInstrument(int instr, std::ofstream& ou, InstrumentIOType iotype);
    int LoadInstrument(int instr, std::ifstream& in, InstrumentIOType iotype);

    BYTE InstrToAta(int instr, unsigned char* ata, int max);
    BYTE InstrToAtaRMF(int instr, unsigned char* ata, int max);
    BOOL AtaToInstr(unsigned char* ata, int instr);

    BOOL AtaV0ToInstr(unsigned char* ata, int instr);	// Due to the loading of the old version

private:
    TInstrument* m_instr;					// Pointer to TInstrument struct, used for instruments data
    void DrawName(int instrNr);				// Draw the instrument name (Show edit state with cursor position)
    void DrawParameter(int p, int instrNr);
    void DrawEnv(int e, int instrNr);
    void DrawNoteTableValue(int p, int instrNr);
};
