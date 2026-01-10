#pragma once

#define INSTRUMENT_NAME_MAX_LEN	32		// maximum length of instrument name
#define PARCOUNT	24			//24 instrument parameters
#define ENVELOPE_MAX_COLUMNS	48			// 48 columns in envelope (drive 32) (48 from version 1.25)
#define ENVROWS		8			//8 line (parameter) in the envelope
#define INSTRSNUM	64
#define NOTE_TABLE_MAX_LEN		32		// maximum 32 steps in the note table
#define NUMBER_OF_PARAMS	20

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



struct TInstrInfo
{
    int count;
    int usedintracks;
    int instrfrom, instrto;
    int minnote, maxnote;
    int minvol, maxvol;
};

// Which section of an instrument's data is currently being editied (is active)
enum class InstrumentSection : int
{
    NONE = -1,
    NAME = 0,
    PARAMETERS = 1,
    ENVELOPE = 2,
    NOTETABLE = 3
};


typedef struct TInstrument
{
    InstrumentSection activeEditSection;					// Which section (name, parameters, envelope, note table) is being edited

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


enum class InstrumentIOType : int {
    IOINSTR_RTI = 1,	// corresponding IOTYPE_RMT
    IOINSTR_RMW = 2,	// corresponding IOTYPE_RMW
    IOINSTR_TXT = 6		// corresponding IOTYPE_TXT
};