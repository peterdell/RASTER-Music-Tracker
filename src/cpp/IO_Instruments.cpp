#include "StdAfx.h"
#include <fstream>

#include "Atari.h"
#include "IOHelpers.h"

#include "Instruments.h"
#include "SongTypes.h"

extern CAtari g_Atari;



int CInstruments::SaveAll(std::ostream& ou, InstrumentIOType iotype)
{
    for (int i = 0; i < INSTRSNUM; i++)
    {
        if (iotype == InstrumentIOType::TXT && !CalculateNotEmpty(i)) { continue; } // to TXT only non-empty instruments
        SaveInstrument(i, ou, iotype);	//,RMW);
    }
    return 1;
}

int CInstruments::LoadAll(std::istream& in, InstrumentIOType iotype)
{
    for (int i = 0; i < INSTRSNUM; i++)
    {
        LoadInstrument(i, in, iotype);	//RMW);
    }

    return 1;
}

int CInstruments::SaveInstrument(int instr, std::ostream& ou, InstrumentIOType iotype)
{
    TInstrument* ai = GetInstrument(instr);

    int j, k;

    switch (iotype)
    {
    case InstrumentIOType::RTI:
    {
        //RTI file
        static char head[4] = "RTI";
        head[3] = 1;		// Type 1
        ou.write(head, 4);	// 4 bytes header RTI1 (binary 1)
        ou.write(ai->name, sizeof(ai->name)); //name 32 byte + 33 is a binary zero terminating string
        const auto length = ATARI_MAX_INSTR_LENGTH;
        unsigned char ibf[length];
        BYTE len = InstrToAta(instr, ibf, length);
        ou.write((char*)&len, sizeof(len));				    // instrument length in Atari bytes
        if (len > 0) { ou.write((const char*)&ibf, len); }	// instrument data
    }
    break;

    case InstrumentIOType::RMW:
        // instrument name
        ou.write(ai->name, sizeof(ai->name));

        char bfpar[PARCOUNT], bfenv[ENVELOPE_MAX_COLUMNS][ENVROWS], bftab[NOTE_TABLE_MAX_LEN];
        //
        for (j = 0; j < PARCOUNT; j++)
        {
            bfpar[j] = ai->parameters[j];
        }
        ou.write(bfpar, sizeof(bfpar));
        //
        for (k = 0; k < ENVROWS; k++)
        {
            for (j = 0; j < ENVELOPE_MAX_COLUMNS; j++)
            {
                bfenv[j][k] = ai->envelope[j][k];
            }
        }
        ou.write((char*)bfenv, sizeof(bfenv));
        //
        for (j = 0; j < NOTE_TABLE_MAX_LEN; j++)
        {
            bftab[j] = ai->noteTable[j];
        }
        ou.write(bftab, sizeof(bftab));
        //
        // plus editing options:
        ou.write((char*)&ai->activeEditSection, sizeof(ai->activeEditSection));
        ou.write((char*)&ai->editNameCursorPos, sizeof(ai->editNameCursorPos));
        ou.write((char*)&ai->editParameterNr, sizeof(ai->editParameterNr));
        ou.write((char*)&ai->editEnvelopeX, sizeof(ai->editEnvelopeX));
        ou.write((char*)&ai->editEnvelopeY, sizeof(ai->editEnvelopeY));
        ou.write((char*)&ai->editNoteTableCursorPos, sizeof(ai->editNoteTableCursorPos));
        // octaves and volumes
        ou.write((char*)&ai->octave, sizeof(ai->octave));
        ou.write((char*)&ai->volume, sizeof(ai->volume));
        break;

    case InstrumentIOType::TXT:
        // TXT file
        CString s, nambf;
        nambf = ai->name;
        nambf.TrimRight();
        s.Format("[INSTRUMENT]\n%02X: %s\n", instr, (LPCTSTR)nambf);
        ou << (LPCTSTR)s;
        //instrument parameters
        for (j = 0; j < NUMBER_OF_PARAMS; j++)
        {
            s.Format("%s %X\n", shpar[j].fieldName, ai->parameters[j] + shpar[j].displayOffset);
            ou << (LPCTSTR)s;
        }
        //table
        ou << "TABLE: ";
        for (j = 0; j <= ai->parameters[PAR_TBL_LENGTH]; j++)
        {
            s.Format("%02X ", ai->noteTable[j]);
            ou << (LPCTSTR)s;
        }
        ou << std::endl;
        //envelope
        for (k = 0; k < ENVROWS; k++)
        {
            char bf[ENVELOPE_MAX_COLUMNS + 1];
            for (j = 0; j <= ai->parameters[PAR_ENV_LENGTH]; j++)
            {
                bf[j] = CharL4(ai->envelope[j][k]);
            }
            bf[ai->parameters[PAR_ENV_LENGTH] + 1] = 0; //buffer termination
            s.Format("%s %s\n", shenv[k].fieldName, bf);
            ou << (LPCTSTR)s;
        }
        ou << "\n"; //gap
        break;
    }
    return 1;
}

int CInstruments::LoadInstrument(int instr, std::istream& in, InstrumentIOType iotype)
{
    switch (iotype)
    {
    case InstrumentIOType::RTI:
    {
        //RTI
        if (instr < 0 || instr >= INSTRSNUM) { return 0; }
        ClearInstrument(instr);	//it will first delete it before it reads
        TInstrument* ai = GetInstrument(instr);
        char head[4];
        in.read(head, 4);	//4 bytes header
        if (strncmp(head, "RTI", 3) != 0) { return 0; }		// if there is no RTI header
        int version = head[3];
        if (version >= 2) { return 0; }			// it's version 2 and more (only 0 and 1 are supported)

        in.read(ai->name, sizeof(ai->name));	// name 32 bytes + 33rd byte terminating zero

        BYTE len;
        in.read((char*)&len, sizeof(len));			//instrument length in Atari bytes
        if (len > 0)
        {
            unsigned char ibf[ATARI_MAX_INSTR_LENGTH];
            in.read((char*)&ibf, len);
            BOOL r;
            if (version == 0)
            {
                r = AtaV0ToInstr(ibf, instr);
            }
            else
            {
                r = AtaToInstr(ibf, instr);
            }
            Update(instr);	// writes to Atari RAM
            if (!r)
            {
                return 0; // if there was some problem with the instrument, return 0
            }
        }
    }
    break;

    case InstrumentIOType::RMW:
    {
        //RMW
        if (instr < 0 || instr >= INSTRSNUM) { return 0; }
        ClearInstrument(instr);	//it will first delete it before it reads
        TInstrument* ai = GetInstrument(instr);
        //instrument name
        in.read((char*)ai->name, sizeof(ai->name));

        char bfpar[PARCOUNT], bfenv[ENVELOPE_MAX_COLUMNS][ENVROWS], bftab[NOTE_TABLE_MAX_LEN];
        int j, k;
        //
        in.read(bfpar, sizeof(bfpar));
        for (j = 0; j < PARCOUNT; j++)
        {
            ai->parameters[j] = bfpar[j];
        }
        //
        in.read((char*)bfenv, sizeof(bfenv));
        for (j = 0; j < ENVELOPE_MAX_COLUMNS; j++)
        {
            for (k = 0; k < ENVROWS; k++)
            {
                ai->envelope[j][k] = bfenv[j][k];
            }
        }
        //
        in.read((char*)bftab, sizeof(bftab));
        for (j = 0; j < NOTE_TABLE_MAX_LEN; j++)
        {
            ai->noteTable[j] = bftab[j];
        }
        //
        Update(instr);	//writes to Atari mem
        //
        //+editing options:
        in.read((char*)&ai->activeEditSection, sizeof(ai->activeEditSection));
        in.read((char*)&ai->editNameCursorPos, sizeof(ai->editNameCursorPos));
        in.read((char*)&ai->editParameterNr, sizeof(ai->editParameterNr));
        in.read((char*)&ai->editEnvelopeX, sizeof(ai->editEnvelopeX));
        in.read((char*)&ai->editEnvelopeY, sizeof(ai->editEnvelopeY));
        in.read((char*)&ai->editNoteTableCursorPos, sizeof(ai->editNoteTableCursorPos));
        //octaves and volumes
        in.read((char*)&ai->octave, sizeof(ai->octave));
        in.read((char*)&ai->volume, sizeof(ai->volume));
    }
    break;

    case InstrumentIOType::TXT:
    {
        char a;
        char b;
        char line[1025];
        in.getline(line, 1024); //first row of the instrument
        int iins = Hexstr(line, 2);

        if (instr == -1)
        {
            instr = iins; //takes over the instrument number
        }

        if (instr < 0 || instr >= INSTRSNUM)
        {
            NextSegment(in);
            return 1;
        }

        ClearInstrument(instr);	//it will first delete it before it reads
        TInstrument* ai = GetInstrument(instr);

        char* value = line + 4;
        Trimstr(value);
        memset(ai->name, ' ', INSTRUMENT_NAME_MAX_LEN);
        int lname = INSTRUMENT_NAME_MAX_LEN;
        if (strlen(value) <= INSTRUMENT_NAME_MAX_LEN)
        {
            lname = (int)strlen(value);
        }
        strncpy(ai->name, value, lname);

        int v, j, k, vlen;
        while (!in.eof())
        {
            in.read((char*)&b, 1);
            if (b == '[')
            {
                goto InstrEnd; //end of instrument (beginning of something else)
            }
            line[0] = b;
            in.getline(line + 1, 1024);

            value = strstr(line, ": ");
            if (value)
            {
                value[1] = 0;	//close the gap by closing
                value += 2;	//the first character after the space
            }
            else
            {
                continue;
            }

            for (j = 0; j < NUMBER_OF_PARAMS; j++)
            {
                if (strcmp(line, shpar[j].fieldName) == 0)
                {
                    v = Hexstr(value, 2) - shpar[j].displayOffset;
                    if (v < 0)
                    {
                        goto NextInstrLine;
                    }
                    v &= shpar[j].parameterAND;
                    if (v > shpar[j].maxParameterValue)
                    {
                        v = 0;
                    }
                    ai->parameters[shpar[j].paramIndex] = v;
                    goto NextInstrLine;
                }
            }
            if (strcmp(line, "TABLE:") == 0)
            {
                Trimstr(value);
                vlen = (int)strlen(value);
                for (j = 0; j < vlen; j += 3)
                {
                    v = Hexstr(value + j, 2);
                    if (v < 0)
                    {
                        goto NextInstrLine;
                    }
                    ai->noteTable[j / 3] = v;
                }
                goto NextInstrLine;
            }

            for (j = 0; j < ENVROWS; j++)
            {
                if (strcmp(line, shenv[j].fieldName) == 0)
                {
                    for (k = 0; (a = value[k]) && k < ENVELOPE_MAX_COLUMNS; k++)
                    {
                        v = Hexstr(&a, 1);
                        if (v < 0)
                        {
                            goto NextInstrLine;
                        }
                        v &= shenv[j].pand;
                        ai->envelope[k][j] = v;
                    }
                    goto NextInstrLine;
                }
            }
        NextInstrLine: {}
        }
    }
InstrEnd:
    Update(instr);	//write to Atari mem
    break;

    }

    return 1;
}



/// <summary>
/// The instrument was modified in some way.
/// Push the new instrument data to Atari
/// and update the display hint flag.
/// </summary>
/// <param name="instr">Instrument #</param>
/// <returns></returns>
void CInstruments::Update(int instr)
{
    auto memory = g_Atari.GetMemoryAt(0x4000 + instr * 256);
    //g_rmtroutine = FALSE;			//turn off RMT routines	// editing in real time is smoother without this switch
    InstrToAta(instr, memory, ATARI_MAX_INSTR_LENGTH);
    //g_rmtroutine = TRUE;			//RMT routines are turned on

    RecalculateFlag(instr);
}


