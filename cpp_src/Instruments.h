#pragma once
#include "stdafx.h"
#include <iosfwd>
#include "General.h"


struct Tshpar
{
	int paramIndex;				// Which parameter does this entry represent
	int x, y;					// Screen display position
	const char* name;			// Name
	int parameterAND;			// AND with a text loaded value to limit its range.  TODO: WHY??
	int maxParameterValue;		// This is the maximum value the parameter can be
	int displayOffset;			// Some parameters are 0..x but 1..x + 1 is displayed
	// If the up, down, left or right keys are pressed which parameter is the next
	// one to be edited
	int gotoUp, gotoDown, gotoLeft, gotoRight;
	const char* fieldName;		// Name to be used in TXT instrument load/save
};

extern const Tshpar shpar[NUMBER_OF_PARAMS];



struct Tshenv
{
	char ch;
	int pand;
	int padd;
	int psub;
	const char* name;
	int xpos;
	int ypos;
	const char* fieldName;		// Name to be used in TXT instrument load/save
};

extern const Tshenv shenv[ENVROWS];

struct TInstrInfo
{
	int count;
	int usedintracks;
	int instrfrom, instrto;
	int minnote, maxnote;
	int minvol, maxvol;
};


typedef struct TInstrument
{
	int activeEditSection;					// Which section (name, parameters, envelope, note table) is being edited

	// Name section
	char name[INSTRUMENT_NAME_MAX_LEN + 1];	// Instrument name
	int editNameCursorPos;					// Where is the edit cursor 0 - 31

	// Parameter section
	int parameters[PARCOUNT];				// 24 parameters (20 used, 4 spare)
	int editParameterNr;					// which parameter is being edited

	// Envelope section
	int envelope[ENVELOPE_MAX_COLUMNS][ENVROWS];			//[32][8]
	int editEnvelopeX;
	int editEnvelopeY;

	// Note table section
	int noteTable[NOTE_TABLE_MAX_LEN];
	int editNoteTableCursorPos;				// Which note table entry is being edited

	int octave;								// Last used Octave and Volume
	int volume;

	int displayHintFlag;					// Some flags that give hints to what is happening with this instrument
} TInstrument;


struct TInstrumentsAll		//for undo
{
	TInstrument instruments[INSTRSNUM];
};

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

	BYTE GetFlag(int instr) { return IsValidInstrument(instr) ? m_instr[instr].displayHintFlag : -1; };
	BYTE GetParameter(int instr, int param) { return IsValidInstrument(instr) ? m_instr[instr].parameters[param] : -1; };
	int GetParameterNumber(int instr) { return IsValidInstrument(instr) ? m_instr[instr].editParameterNr : -1; };
	int GetActiveEditSection(int instr) { return IsValidInstrument(instr) ? m_instr[instr].activeEditSection : -1; };
	int GetNameCursorPosition(int instr) { return IsValidInstrument(instr) ? m_instr[instr].editNameCursorPos : -1; };
	char* GetName(int instr) { return IsValidInstrument(instr) ? m_instr[instr].name : NULL; };

	TInstrument* GetInstrument(int instr) { return IsValidInstrument(instr) ? &m_instr[instr] : NULL; };
	TInstrumentsAll* GetInstrumentsAll() { return (TInstrumentsAll*)m_instr; };

	// GUI
	void DrawInstrument(int it);

	BOOL GetGUIArea(int instr, int zone, CRect& rect);
	BOOL CursorGoto(int instr, CPoint point, int pzone);

	// IO
	void WasModified(int it);

	int SaveAll(std::ofstream& ou, int iotype);
	int LoadAll(std::ifstream& in, int iotype);

	int SaveInstrument(int instr, std::ofstream& ou, int iotype);
	int LoadInstrument(int instr, std::ifstream& in, int iotype);

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

extern CInstruments g_Instruments;