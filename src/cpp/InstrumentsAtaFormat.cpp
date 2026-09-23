#include "StdAfx.h"

#include "Instruments.h"

#include "Global.h"

// InstrToAta()/AtaToInstr()/AtaV0ToInstr() only need g_tracks4_8 (mono/stereo
// envelope volume packing) from Global.h, unlike the rest of
// IO_Instruments.cpp (Update() needs g_Atari), so they're kept separate to
// keep this half directly testable - same pattern as
// Tuning.cpp/TuningTables.cpp and Tracks.cpp/TracksEdit.cpp.

/// <summary>
/// Define information about each instrument parameter (not envelope table)
/// </summary>
const Tshpar shpar[NUMBER_OF_PARAMS] =
    {
        //Nr, Draw Position															NAME			AND		MAX			Offset	"next param on cursor movement", Fieldname in txt file
        //TABLE: LEN GO SPD TYPE MODE
        {PAR_TBL_LENGTH, InstrumentGUIPosition::PARAM_X + 16 * 8, InstrumentGUIPosition::PARAM_Y + 9 * 16, "LENGTH:", 0x1f, 0x1f, 1, 8, 1, 15, 15, "LENGTH:"},
        {PAR_TBL_GOTO, InstrumentGUIPosition::PARAM_X + 18 * 8, InstrumentGUIPosition::PARAM_Y + 10 * 16, "GOTO:", 0x1f, 0x1f, 0, 0, 2, 16, 16, "GOTO:"},
        {PAR_TBL_SPEED, InstrumentGUIPosition::PARAM_X + 17 * 8, InstrumentGUIPosition::PARAM_Y + 11 * 16, "SPEED:", 0x3f, 0x3f, 1, 1, 3, 17, 17, "SPEED:"},
        {PAR_TBL_TYPE, InstrumentGUIPosition::PARAM_X + 18 * 8, InstrumentGUIPosition::PARAM_Y + 12 * 16, "TYPE:", 0x01, 0x01, 0, 2, 4, 18, 18, "TYPE:"},
        {PAR_TBL_MODE, InstrumentGUIPosition::PARAM_X + 18 * 8, InstrumentGUIPosition::PARAM_Y + 13 * 16, "MODE:", 0x01, 0x01, 0, 3, 5, 19, 19, "MODE:"},
        //ENVELOPE: LEN GO VSLIDE VMIN
        {PAR_ENV_LENGTH, InstrumentGUIPosition::PARAM_X + 16 * 8, InstrumentGUIPosition::PARAM_Y + 2 * 16, "LENGTH:", 0x3f, 0x2f, 1, 4, 6, 9, 9, "ENV_LENGTH:"},
        {PAR_ENV_GOTO, InstrumentGUIPosition::PARAM_X + 18 * 8, InstrumentGUIPosition::PARAM_Y + 3 * 16, "GOTO:", 0x3f, 0x2f, 0, 5, 7, 10, 10, "ENV_GOTO:"},
        {PAR_VOL_FADEOUT, InstrumentGUIPosition::PARAM_X + 15 * 8, InstrumentGUIPosition::PARAM_Y + 4 * 16, "FADEOUT:", 0xff, 0xff, 0, 6, 8, 11, 11, "FADEOUT:"},
        {PAR_VOL_MIN, InstrumentGUIPosition::PARAM_X + 15 * 8, InstrumentGUIPosition::PARAM_Y + 5 * 16, "VOL MIN:", 0x0f, 0x0f, 0, 7, 0, 11, 11, "VOL_MIN:"},
        //EFFECT: DELAY VIBRATO FSHIFT
        {PAR_DELAY, InstrumentGUIPosition::PARAM_X + 3 * 8, InstrumentGUIPosition::PARAM_Y + 2 * 16, "DELAY:", 0xff, 0xff, 0, 19, 10, 5, 5, "EFF_DELAY:"},
        {PAR_VIBRATO, InstrumentGUIPosition::PARAM_X + 1 * 8, InstrumentGUIPosition::PARAM_Y + 3 * 16, "VIBRATO:", 0x03, 0x03, 0, 9, 11, 6, 6, "EFF_VIBRATO:"},
        {PAR_FREQ_SHIFT, InstrumentGUIPosition::PARAM_X + -1 * 8, InstrumentGUIPosition::PARAM_Y + 4 * 16, "FREQSHIFT:", 0xff, 0xff, 0, 10, 12, 7, 7, "EFF_FREQSHIFT:"},
        //AUDCTL: 00-07
        {PAR_AUDCTL_15KHZ, InstrumentGUIPosition::PARAM_X + 3 * 8, InstrumentGUIPosition::PARAM_Y + 6 * 16, "15KHZ:", 0x01, 0x01, 0, 11, 13, 0, 0, "AUD_15KHZ:"},
        {PAR_AUDCTL_HPF_CH2, InstrumentGUIPosition::PARAM_X + 1 * 8, InstrumentGUIPosition::PARAM_Y + 7 * 16, "HPF 2+4:", 0x01, 0x01, 0, 12, 14, 0, 0, "AUD_HPF_CH2:"},
        {PAR_AUDCTL_HPF_CH1, InstrumentGUIPosition::PARAM_X + 1 * 8, InstrumentGUIPosition::PARAM_Y + 8 * 16, "HPF 1+3:", 0x01, 0x01, 0, 13, 15, 0, 0, "AUD_HPF_CH1:"},
        {PAR_AUDCTL_JOIN_3_4, InstrumentGUIPosition::PARAM_X + 0 * 8, InstrumentGUIPosition::PARAM_Y + 9 * 16, "JOIN 3+4:", 0x01, 0x01, 0, 14, 16, 0, 0, "AUD_JOIN34:"},
        {PAR_AUDCTL_JOIN_1_2, InstrumentGUIPosition::PARAM_X + 0 * 8, InstrumentGUIPosition::PARAM_Y + 10 * 16, "JOIN 1+2:", 0x01, 0x01, 0, 15, 17, 1, 1, "AUD_JOIN12:"},
        {PAR_AUDCTL_179_CH3, InstrumentGUIPosition::PARAM_X + 0 * 8, InstrumentGUIPosition::PARAM_Y + 11 * 16, "1.79 CH3:", 0x01, 0x01, 0, 16, 18, 2, 2, "AUD_179_CH3:"},
        {PAR_AUDCTL_179_CH1, InstrumentGUIPosition::PARAM_X + 0 * 8, InstrumentGUIPosition::PARAM_Y + 12 * 16, "1.79 CH1:", 0x01, 0x01, 0, 17, 19, 3, 3, "AUD_179_CH1:"},
        {PAR_AUDCTL_POLY9, InstrumentGUIPosition::PARAM_X + 3 * 8, InstrumentGUIPosition::PARAM_Y + 13 * 16, "POLY9:", 0x01, 0x01, 0, 18, 9, 4, 4, "AUD_POLY9:"}};

/// <summary>
/// Define information about envelope table
/// </summary>
const Tshenv shenv[ENVROWS] =
    {
        //ENVELOPE
        {0, 0x0f, 1, -1, "VOLUME R:", InstrumentGUIPosition::ENV_X + 2 * 8, InstrumentGUIPosition::ENV_Y + 2 * 16, "ENV_VOLUME_R:"}, //volume right
        {0, 0x0f, 1, -1, "VOLUME L:", InstrumentGUIPosition::ENV_X + 2 * 8, InstrumentGUIPosition::ENV_Y + 8 * 16, "ENV_VOLUME_L:"}, //volume left
        {0, 0x0e, 2, -2, "DISTORTION:", InstrumentGUIPosition::ENV_X + 0 * 8, InstrumentGUIPosition::ENV_Y + 9 * 16, "ENV_DISTORTION:"}, //distortion 0,2,4,6,...
        {0, 0x07, 1, -1, "COMMAND:", InstrumentGUIPosition::ENV_X + 3 * 8, InstrumentGUIPosition::ENV_Y + 10 * 16, "ENV_COMMNAND:"}, //command 0-7
        {0, 0x0f, 1, -1, "X/:", InstrumentGUIPosition::ENV_X + 8 * 8, InstrumentGUIPosition::ENV_Y + 11 * 16, "ENV_X:"}, //X
        {0, 0x0f, 1, -1, "Y\\:", InstrumentGUIPosition::ENV_X + 8 * 8, InstrumentGUIPosition::ENV_Y + 12 * 16, "ENV_Y:"}, //Y
        {9, 0x01, 1, -1, "AUTOFILTER:", InstrumentGUIPosition::ENV_X + 0 * 8, InstrumentGUIPosition::ENV_Y + 13 * 16, "ENV_AUTOFILTER:"}, //filter *
        {9, 0x01, 1, -1, "PORTAMENTO:", InstrumentGUIPosition::ENV_X + 0 * 8, InstrumentGUIPosition::ENV_Y + 14 * 16, "ENV_PORTAMENTO:"} //portamento *
};

BYTE CInstruments::InstrToAta(int instr, unsigned char* ata, int max) {
    TInstrument* ai = GetInstrument(instr);
    int i, j;
    int* par = ai->parameters;

    /*
      0 IDXTABLEEND
      1 IDXTABLEGO
      2 IDXENVEND
      3 IDXENVGO
      4 TABTYPEMODESPEED
      5 AUDCTL
      6 VSLIDE
      7 VMIN
      8 EFFDELAY
      9 EFVIBRATO
     10 FSHIFT
     11 0 (unused)
    */

    const int INSTRPAR = 12; //12th byte starts the table

    int tablelast = par[PAR_TBL_LENGTH] + INSTRPAR;
    ata[0] = tablelast;
    ata[1] = par[PAR_TBL_GOTO] + INSTRPAR;
    ata[2] = par[PAR_ENV_LENGTH] * 3 + tablelast + 1; //behind the table is the envelope
    ata[3] = par[PAR_ENV_GOTO] * 3 + tablelast + 1;
    //
    ata[4] = (par[PAR_TBL_TYPE] << 7) | (par[PAR_TBL_MODE] << 6) | (par[PAR_TBL_SPEED]);
    //
    ata[5] = par[PAR_AUDCTL_15KHZ] | (par[PAR_AUDCTL_HPF_CH2] << 1) | (par[PAR_AUDCTL_HPF_CH1] << 2) | (par[PAR_AUDCTL_JOIN_3_4] << 3) | (par[PAR_AUDCTL_JOIN_1_2] << 4) | (par[PAR_AUDCTL_179_CH3] << 5) | (par[PAR_AUDCTL_179_CH1] << 6) | (par[PAR_AUDCTL_POLY9] << 7);
    ata[6] = par[PAR_VOL_FADEOUT];
    ata[7] = par[PAR_VOL_MIN] << 4;
    ata[8] = par[PAR_DELAY];
    ata[9] = par[PAR_VIBRATO] & 0x03;
    ata[10] = par[PAR_FREQ_SHIFT];
    ata[11] = 0; //unused, for now

    //the entire table length gets the data copied
    for (i = 0; i <= par[PAR_TBL_LENGTH]; i++) {
        ata[INSTRPAR + i] = ai->noteTable[i];
    }

    //envelope is behind the table
    BOOL stereo = (g_tracks4_8 > 4);
    int len = par[PAR_ENV_LENGTH];
    for (i = 0, j = tablelast + 1; i <= len; i++, j += 3) {
        int* env = (int*)&ai->envelope[i];
        ata[j] = (stereo) ? (env[EnvelopeParameter::VOLUMER] << 4) | (env[EnvelopeParameter::VOLUMEL]) //stereo
                          : (env[EnvelopeParameter::VOLUMEL] << 4) | (env[EnvelopeParameter::VOLUMEL]); //mono, VOLUME R = VOLUME L

        ata[j + 1] = (env[EnvelopeParameter::FILTER] << 7) | (env[EnvelopeParameter::COMMAND] << 4) //0-7
                     | (env[EnvelopeParameter::DISTORTION]) //0,2,4,6,8,A,C,E
                     | (env[EnvelopeParameter::PORTAMENTO]);
        ata[j + 2] = (env[EnvelopeParameter::X] << 4) | (env[EnvelopeParameter::Y]);
    }
    return tablelast + 1 + (len + 1) * 3; //returns the data length of the instrument
}

BOOL CInstruments::AtaV0ToInstr(unsigned char* ata, int instr) {
    //OLD INSTRUMENT VERSION
    TInstrument* ai = GetInstrument(instr);
    int i, j;
    //0-7 table
    for (i = 0; i <= 7; i++) {
        ai->noteTable[i] = ata[i];
    }
    //8 ;instr len  0-31 *8, table len  0-7  (iiii ittt)
    int* par = ai->parameters;
    int len = par[PAR_ENV_LENGTH] = ata[8] >> 3;
    par[PAR_TBL_LENGTH] = ata[8] & 0x07;
    par[PAR_ENV_GOTO] = ata[9] >> 3;
    par[PAR_TBL_GOTO] = ata[9] & 0x07;
    par[PAR_TBL_TYPE] = ata[10] >> 7;
    par[PAR_TBL_MODE] = (ata[10] >> 6) & 0x01;
    par[PAR_TBL_SPEED] = ata[10] & 0x3f;
    par[PAR_VOL_FADEOUT] = ata[11];
    par[PAR_VOL_MIN] = ata[12] >> 4;
    //par[PAR_POLY9]	= (ata[12]>>1) & 0x01;
    //par[PAR_15KHZ]	= ata[12] & 0x01;
    par[PAR_AUDCTL_15KHZ] = ata[12] & 0x01;
    par[PAR_AUDCTL_HPF_CH2] = 0;
    par[PAR_AUDCTL_HPF_CH1] = 0;
    par[PAR_AUDCTL_JOIN_3_4] = 0;
    par[PAR_AUDCTL_JOIN_1_2] = 0;
    par[PAR_AUDCTL_179_CH3] = 0;
    par[PAR_AUDCTL_179_CH1] = 0;
    par[PAR_AUDCTL_POLY9] = (ata[12] >> 1) & 0x01;
    //
    par[PAR_DELAY] = ata[13];
    par[PAR_VIBRATO] = ata[14] & 0x03;
    par[PAR_FREQ_SHIFT] = ata[15];
    //
    BOOL stereo = (g_tracks4_8 > 4);
    for (i = 0, j = 16; i <= len; i++, j += 3) {
        int* env = ai->envelope[i];
        env[EnvelopeParameter::VOLUMER] = (stereo) ? (ata[j] >> 4) : (ata[j] & 0x0f); //if mono, then VOLUME R = VOLUME L
        env[EnvelopeParameter::VOLUMEL] = ata[j] & 0x0f;
        env[EnvelopeParameter::FILTER] = ata[j + 1] >> 7;
        env[EnvelopeParameter::COMMAND] = (ata[j + 1] >> 4) & 0x07;
        env[EnvelopeParameter::DISTORTION] = ata[j + 1] & 0x0e; //even numbers 0,2,4, .., 14
        env[EnvelopeParameter::PORTAMENTO] = ata[j + 1] & 0x01;
        env[EnvelopeParameter::X] = ata[j + 2] >> 4;
        env[EnvelopeParameter::Y] = ata[j + 2] & 0x0f;
    }
    return 1;
}

/// <summary>
/// Load an instrument from a binary location and parse the data.
/// </summary>
/// <param name="mem">Start of the instrument definition structure</param>
/// <param name="instrumentNr">Which instrument # is this</param>
/// <returns></returns>
BOOL CInstruments::AtaToInstr(unsigned char* mem, int instrumentNr) {
    TInstrument* ai = GetInstrument(instrumentNr);

    int noteTableLength = mem[0] - 12;
    int noteTableGoto = mem[1] - 12;
    int envelopeLength = (mem[2] - (mem[0] + 1)) / 3;
    int envelopeGoto = (mem[3] - (mem[0] + 1)) / 3;

    // Check the scope of the tables and envelope
    if (noteTableLength >= NOTE_TABLE_MAX_LEN || noteTableGoto > noteTableLength ||
        envelopeLength >= ENVELOPE_MAX_COLUMNS || envelopeGoto > envelopeLength) {
        // Note table and evelope parameters are out of bounds
        return 0;
    }

    // Transfer the Atari memory data into the instrument C-structures
    int* par = ai->parameters;
    par[PAR_TBL_LENGTH] = noteTableLength;
    par[PAR_TBL_GOTO] = noteTableGoto;
    par[PAR_ENV_LENGTH] = envelopeLength;
    par[PAR_ENV_GOTO] = envelopeGoto;
    // Set the Note table speed, type and mode. 0 <= speed <= 63, type
    par[PAR_TBL_TYPE] = mem[4] >> 7; // 0 = notes, 1 = frequencies
    par[PAR_TBL_MODE] = (mem[4] >> 6) & 0x01; // 0 = set, 1 = add
    par[PAR_TBL_SPEED] = mem[4] & 0x3f; // play speed
    // Set the AUDCTL register
    par[PAR_AUDCTL_15KHZ] = mem[5] & 0x01;
    par[PAR_AUDCTL_HPF_CH2] = (mem[5] >> 1) & 0x01;
    par[PAR_AUDCTL_HPF_CH1] = (mem[5] >> 2) & 0x01;
    par[PAR_AUDCTL_JOIN_3_4] = (mem[5] >> 3) & 0x01;
    par[PAR_AUDCTL_JOIN_1_2] = (mem[5] >> 4) & 0x01;
    par[PAR_AUDCTL_179_CH3] = (mem[5] >> 5) & 0x01;
    par[PAR_AUDCTL_179_CH1] = (mem[5] >> 6) & 0x01;
    par[PAR_AUDCTL_POLY9] = (mem[5] >> 7) & 0x01;
    //
    par[PAR_VOL_FADEOUT] = mem[6];
    par[PAR_VOL_MIN] = mem[7] >> 4;
    par[PAR_DELAY] = mem[8];
    par[PAR_VIBRATO] = mem[9] & 0x03;
    par[PAR_FREQ_SHIFT] = mem[10];

    // 0-31 table
    for (int i = 0; i <= par[PAR_TBL_LENGTH]; i++) {
        ai->noteTable[i] = mem[12 + i];
    }

    // Envelope
    BOOL stereo = (g_tracks4_8 > 4);
    int ptrEnvelopeEntry = mem[0] + 1; // location in Atari memory where envelope data is parsed from

    for (int i = 0; i <= par[PAR_ENV_LENGTH]; i++, ptrEnvelopeEntry += 3) {
        // Take the 3 bytes of envelope data and parse them into the 8 data fields
        int* env = ai->envelope[i];
        env[EnvelopeParameter::VOLUMER] = (stereo) ? (mem[ptrEnvelopeEntry] >> 4) : (mem[ptrEnvelopeEntry] & 0x0f); //if mono, then VOLUME R = VOLUME L
        env[EnvelopeParameter::VOLUMEL] = mem[ptrEnvelopeEntry] & 0x0f;

        env[EnvelopeParameter::FILTER] = mem[ptrEnvelopeEntry + 1] >> 7;
        env[EnvelopeParameter::COMMAND] = (mem[ptrEnvelopeEntry + 1] >> 4) & 0x07;
        env[EnvelopeParameter::DISTORTION] = mem[ptrEnvelopeEntry + 1] & 0x0e; //even numbers 0,2,4,...E
        env[EnvelopeParameter::PORTAMENTO] = mem[ptrEnvelopeEntry + 1] & 0x01;

        env[EnvelopeParameter::X] = mem[ptrEnvelopeEntry + 2] >> 4;
        env[EnvelopeParameter::Y] = mem[ptrEnvelopeEntry + 2] & 0x0f;
    }
    return 1;
}
