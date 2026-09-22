#include "StdAfx.h"

#include "AtariIO.h"

#include "importdlgs.h"

#include "Tracks.h"
#include "Song.h"
#include "Instruments.h"
#include "Notes.h"

#include "Global.h"

extern CInstruments	g_Instruments;

// CConvertTracks (TMC-only helper) and CSong::ImportTMCParseHeader()/
// ImportTMCApply() (the real conversion work) are implemented in
// IO_ImporterCore.cpp - see plans/IO_IMPORTER_PLAN.md. ImportTMC() below is
// now a thin wrapper around them, showing its two real dialogs.

//----------------------------------------------


int CSong::ImportTMC(std::ifstream& in)
{
    auto originalg_tracks4_8 = GetTracks();

    TImportTMCHeader header;
    if (!ImportTMCParseHeader(in, header))
    {
        MessageBox(g_hwnd, "Corrupted TMC file or unsupported format version.", "Open error", MB_ICONERROR);
        return 0;
    }

    CImportTmcDlg importdlg;
    CString s = m_songname;
    s.TrimRight();
    importdlg.m_info.Format("TMC module: %s", (LPCTSTR)s);

    if (importdlg.DoModal() != IDOK) { return 0; }

    BOOL x_usetable = importdlg.m_check1;
    BOOL x_optimizeloops = importdlg.m_check6;
    BOOL x_truncateunusedparts = importdlg.m_check7;

    TImportTMCResult result;
    ImportTMCApply(header, x_usetable, x_optimizeloops, x_truncateunusedparts, result);

    //FINAL DIALOGUE AFTER IMPORT
    CImportTmcFinishedDlg imfdlg;
    imfdlg.m_info.Format("%i tracks, %i instruments, %i songlines", result.numoftracks, result.nonemptyinstruments, result.songlines);

    //OPTIMIZATIONS
    if (x_optimizeloops)
    {
        CString s;
        s.Format("\x0d\x0aOptimization: Loops in %i tracks (%i beats/lines)", result.optitracks, result.optibeats);
        imfdlg.m_info += s;
    }

    if (x_truncateunusedparts)
    {
        CString s;
        s.Format("\x0d\x0aOptimization: Cleared %i, truncated %i tracks (%i beats/lines)", result.clearedtracks, result.truncatedtracks, result.truncatedbeats);
        imfdlg.m_info += s;
    }

    if (imfdlg.DoModal() != IDOK)
    {
        //did not give Ok, so it deletes
        ClearSong(originalg_tracks4_8); //returns the original value
        MessageBox(g_hwnd, "Module import aborted.", "Import...", MB_ICONINFORMATION);
    }

    return 1;
}


/********************************************************************************************/

//September 27, 2003 8:38 PM ... I just imported aurora.mod, released it without editing and I'm amazed !!!
//							That's absolutely AMAZING! AMAZING! ABSOLUTELY AWESOME !!!


struct TMODInstrumentMark
{
    BYTE used;			//bits determine in which column (on which channel) it is used
    int volume;
    int minnote;
    int maxnote;
    int samplen;
    int reppoint;
    int replen;
    double trackvolumeincrease;
    int trackvolumemax;
};

int AtariVolume(int volume0_64)
{
    int	avol = (int)((double)volume0_64 / 4 + 0.5);	//conversion to atari volume
    if (volume0_64 < 1) avol = 0;
    else
        if (volume0_64 == 1) avol = 1;
        else
            if (avol > 0x0f) avol = 0x0f;
    return avol;
}

int CSong::ImportMOD(std::ifstream& in)
{
    //deletes the current song
    int originalg_tracks4_8 = GetTracks();	//keeps the original value for Abort
    g_Tracks.SetMaxTrackLength(64);	//track length 64
    ClearSong(8);			//prepares 8 channels, clear existing data

    int i, j;
    BYTE a;
    BYTE head[1085];

    in.read((char*)&head, 1084);
    int flen = (int)in.gcount();

    head[1084] = 0;	//finished behind the header

    if (flen != 1084)
    {
        MessageBox(g_hwnd, "Bad file format.", "Error", MB_ICONSTOP);
        return 0;
    }

    in.seekg(0, std::ios::end);
    int modulelength = (int)in.tellg();

    int chnls = 0;
    int song = 950;		//where the song starts at the 31 sample module
    int patstart = 1084;	//the beginning of the pattern at the 31 sample module
    int modsamples = 31;	//31 samples
    if (strncmp((char*)(head + 1080), "M.K.", 4) == 0)
        chnls = 4;					//M.K.
    else
        if (strncmp((char*)(head + 1081), "CHN", 3) == 0)
            chnls = head[1080] - '0';	//xCHN
        else
        {
            for (int i = 0; i < 4; i++)
            {
                a = head[1080 + i];
                if (a < 32 || a>90) //it's outside " " and "Z" (ie it's not a letter or a space)
                {
                    //=> 15 samples MOD
                    chnls = 4;
                    modsamples = 15;
                    song = 470;
                    patstart = 600;
                    break;
                }
            }
        }

    if (chnls < 4 || chnls>8)
    {
        CString es;
        es.Format("There isn't ProTracker identification header bytes.\nAllowed headers are \"M.K.\" or from \"4CHN\" to \"8CHN\",\nbut there is \"%s\".", head + 1080);
        MessageBox(g_hwnd, (LPCTSTR)es, "Error", MB_ICONSTOP);
        return 0;
    }
    int patternsize = chnls * 256;

    int songlen = head[song + 0];
    int restartpos = head[song + 1];
    if (restartpos >= songlen) restartpos = 0;

    int maxpat = 0;
    if (songlen > SONGLEN - 1) songlen = SONGLEN - 1;
    for (i = 0; i < songlen; i++)
    {
        int patnum = head[song + 2 + i];
        if (patnum > maxpat) maxpat = patnum;
    }
    int memlen = song + 130 + patternsize * (maxpat + 1);				//130 = 1 length +1 repeat + 128 song
    if (song >= 950) memlen += 4;		//in addition 4 identification letters (eg "M.K.") // song + 130 (1084)

    //now it is allocating memory
    BYTE* mem = new BYTE[memlen];
    if (!mem)
    {
        MessageBox(g_hwnd, "Can't allocate memory for module.", "Error", MB_ICONSTOP);
        return 0;
    }

    in.clear();
    in.seekg(0);		//again at the beginning
    in.read((char*)mem, memlen);	//load module
    flen = (int)in.gcount();
    if (flen != memlen)
    {
        MessageBox(g_hwnd, "Bad file.", "Error", MB_ICONSTOP);
        if (mem) delete[] mem;
        return 0;
    }

    BYTE trackorder[8] = { 0,1,2,3,4,5,6,7 };	//track layout
    int rmttype = 0;

    CImportModDlg importdlg;
    importdlg.m_info.Format("%i channels %i samples ProTracker module detected.\n(Header bytes \"%s\".)", chnls, modsamples, head + 1080);
    if (chnls == 4)
    {	//4 channels module
        importdlg.m_txtradio1 = "RMT4 with 1,2,3,4 tracks order";
        importdlg.m_txtradio2 = "RMT8 with 1,4 / 2,3 tracks order";
        if (importdlg.DoModal() == IDOK)
        {
            if (importdlg.m_txtradio1 != "")
            {	//first choice
                rmttype = 4;
            }
            else
            {	//second choice
                rmttype = 8;
                trackorder[0] = 0;
                trackorder[1] = 4;
                trackorder[2] = 5;
                trackorder[3] = 1;
            }
        }
    }
    else
    {	//5-8 channels module
        importdlg.m_txtradio1 = "RMT8 with 1,4,5,8 / 2,3,6,7 tracks order";
        importdlg.m_txtradio2 = "RMT8 with 1,2,3,4 / 5,6,7,8 tracks order";
        if (importdlg.DoModal() == IDOK)
        {
            if (importdlg.m_txtradio1 != "")
            {	//first choice
                rmttype = 8;
                trackorder[0] = 0;
                trackorder[1] = 4;
                trackorder[2] = 5;
                trackorder[3] = 1;
                trackorder[4] = 2;
                trackorder[5] = 6;
                trackorder[6] = 7;
                trackorder[7] = 3;
            }
            else
            {	//second choice
                rmttype = 8;
            }
        }
    }

    if (rmttype != 4 && rmttype != 8)
    {	//did not select the back option (cancel in the dialog)
        if (mem) delete[] mem;
        return 0;
    }

    BOOL x_shiftdownoctave = importdlg.m_check1;
    BOOL x_portamento = importdlg.m_check5;
    BOOL x_fullvolumerange = importdlg.m_check2;
    BOOL x_volumeincrease = importdlg.m_check3;
    BOOL x_decreaseinstrument = importdlg.m_check4;
    BOOL x_optimizeloops = importdlg.m_check6;
    BOOL x_truncateunusedparts = importdlg.m_check7;
    BOOL x_fourier = importdlg.m_check8;

    SetTracks(rmttype); //produce RMT4 or RMT8

    //song name
    for (j = 0; j < 20 && (a = mem[j]); j++) m_songname[j] = a;

    //speeds
    m_mainSpeed = m_speed = 6;			//default speed
    m_instrumentSpeed = 1;

    int maxsmplen = 0;			//maximum sample length

    //instruments 1-31
    TMODInstrumentMark imark[32];
    for (i = 1; i <= modsamples; i++)
    {
        //name 
        BYTE* sdata = mem + (20 + (i - 1) * 30);	//sample header data
        TInstrument* ti = g_Instruments.GetInstrument(i);
        char* dname = ti->name;
        for (j = 0; j < 22; j++)	//0-21 name
        {
            a = sdata[j];
            if (a >= 32 && a <= 126)
                dname[j] = a;
            else
                dname[j] = ' ';
        }
        for (; j < INSTRUMENT_NAME_MAX_LEN; j++) dname[j] = ' '; //deletes the rest of the instrument name
        int samplen = (sdata[23] | (sdata[22] << 8)) * 2;
        //BYTE finetune=sdata[24]&0x0f;		//0-15
        //if (finetune>=8) finetune+=240;		//0-7 or 248-255

        int volume = sdata[25];
        if (volume > 0x3f) volume = 0x3f;		//00-3f

        int reppoint = (sdata[27] | (sdata[26] << 8)) * 2;

        int replen = (sdata[29] | (sdata[28] << 8)) * 2;

        //ti->env[0][EnvelopeParameter::VOLUMEL]= (volume>>2);	//0-15
        //ti->env[0][EnvelopeParameter::VOLUMER]= (volume>>2);	//0-15
        //ti->env[0][ENV_DISTORTION]= 0x0a;		//clean tone
        /*if (finetune!=0)
        {
            ti->env[0][EnvelopeParameter::COMMAND]=0x02;		//frequency shift
            ti->env[0][EnvelopeParameter::X]=(finetune>>4);
            ti->env[0][EnvelopeParameter::Y]=(finetune&0x0f);
        }
        */
        //if (!reppoint) ti->par[PAR_VSLIDE]=255-(samplen>>8);

        imark[i].volume = volume;
        imark[i].minnote = CNotes::NOTESNUM - 1;
        imark[i].maxnote = 0;
        imark[i].used = 0;			//do not use on any channel
        imark[i].samplen = samplen;
        imark[i].reppoint = reppoint;
        imark[i].replen = replen;
        imark[i].trackvolumeincrease = 1;
        imark[i].trackvolumemax = 0;

        if (samplen > maxsmplen) maxsmplen = samplen;	//maximum sample length

        //sending into Atari memory is at the end of adding things to the instrument
    }
    imark[0].trackvolumeincrease = 1; //due to volume slide, if it is performed without specifying a sample (ie sample number 0)

    const int TABLENOTES = 73;
    const int pertable[TABLENOTES] = {															//period table
    0x06B0,0x0650,0x05F4,0x05A0,0x054C,0x0500,0x04B8,0x0474,0x0434,0x03F8,0x03C0,0x038B,	//C3-B3
    0x0358,0x0328,0x02FA,0x02D0,0x02A6,0x0280,0x025C,0x023A,0x021A,0x01FC,0x01E0,0x01C5,	//C4-B4
    0x01AC,0x0194,0x017D,0x0168,0x0153,0x0140,0x012E,0x011D,0x010D,0x00FE,0x00F0,0x00E2,	//C5-B5
    0x00D6,0x00CA,0x00BE,0x00B4,0x00AA,0x00A0,0x0097,0x008F,0x0087,0x007F,0x0078,0x0071,	//C6-B6
    0x006B,0x0065,0x005F,0x005A,0x0055,0x0050,0x004B,0x0047,0x0043,0x003F,0x003C,0x0038,	//C7-B7
    0x0035,0x0032,0x002F,0x002D,0x002A,0x0028,0x0025,0x0023,0x0021,0x001F,0x001E,0x001C,	//C8-B8
    0x001B }; //calculated value															//C9

    //make a table
    BYTE pertonote[4096];	//convert period to note
    int n1 = 0, n2 = 0, n12 = 0, lastp = 4095;
    for (i = 0; i < TABLENOTES; i++)
    {
        n1 = pertable[i];
        if (i < TABLENOTES - 1)
        {
            n2 = pertable[i + 1];
            n12 = (int)(((float)(n1 + n2)) / 2 + 0.5);
        }
        else
        {
            n12 = 0;
        }
        BYTE note = (BYTE)i;
        while (note >= CNotes::NOTESNUM) note -= 12;
        for (j = lastp; j >= n12; j--) pertonote[j] = note;
        lastp = n12 - 1;
    }

    //BEGINNING OF PROCESSING THE ENTIRE SONG AND PATTERN
    int dsline;		//destination song line
    int destnum;	//destination track num

    int nofpass = (x_fullvolumerange) ? 1 : 0;
    for (int pass = 0; pass <= nofpass; pass++)
    { //---TRANSITION 0/1---

        destnum = 0;		//destination track num
        int tnot[8] = { -1,-1,-1,-1,-1,-1,-1,-1 };	//last edge note (used for portamento)
        int tporon[8] = { 0,0,0,0,0,0,0,0 };	//portamento yes/no
        int tporperiod[8] = { 0,0,0,0,0,0,0,0 };	//target period for portamento
        int tporspeed[8] = { 0,0,0,0,0,0,0,0 };		//portamento speed
        int tper[8] = { 0,0,0,0,0,0,0,0 };	//current period
        int tvol[8] = { 0,0,0,0,0,0,0,0 };	//volume of individual tracks (used for volume slide)
        int tvolslidedebt[8] = { 0,0,0,0,0,0,0,0 };		//debt at volume slide
        int tins[8] = { 0,0,0,0,0,0,0,0 };	//individual track instruments (used when the sample is == 0)
        int ticks = m_mainSpeed;		//songspeed (for volume slide), default 6
        int beats = 125;				//default beats/min speed

        int LastRowTicks = ticks;
        int LastRowBeats = beats;

        //song
        dsline = 0;		//destination song line
        int ThisPatternFromRow = 0;	//initial pattern line (due to Dxx effect)
        int ThisPatternFromColumn = -1;	//none
        int NextPatternFromRow = 0;
        int NextPatternFromColumn = -1;
        for (i = 0; i < songlen; i++)
        {
            int patnum = mem[song + 2 + i];	//test song

            BYTE* pdata = mem + (patstart + patnum * patternsize);	//beginning of the pattern

            int songjump = -1;	//initialization for song jump
            ThisPatternFromRow = NextPatternFromRow;	//takes over from the previous pattern
            ThisPatternFromColumn = NextPatternFromColumn;	//takes over from the past
            NextPatternFromRow = 0;	//initialized to 0 for the next pattern
            NextPatternFromColumn = -1;	//initialized to -1 for the next pattern

            //pre-calculated tickrow [0..63] and speedrow [0..63]
            int tickrow[64], speedrow[64];
            int m, n;
            BOOL lrow = 0;
            ticks = LastRowTicks;		//previous ticks
            beats = LastRowBeats;		//previous beats
            LastRowTicks = -1;		//init
            LastRowBeats = -1;		//init
            for (m = ThisPatternFromRow; m < 64; m++)
            {
                for (n = 0; n < chnls; n++)
                {
                    BYTE* bdata = pdata + m * chnls * 4 + n * 4;
                    int effect = bdata[2] & 0x0f;
                    int param = bdata[3];
                    if (effect == 0x0f)
                    {	//speed
                        if (param <= 0x20)
                            ticks = (param > 0) ? param : 1; //speed 0 is not possible
                        else
                            beats = param;
                    }
                    else
                        if (effect == 0x0b || effect == 0x0d)
                        {	//pattern break or song jump
                            lrow = 1;	//you have to write it down and even this whole line 0..chnls
                        }
                }
                //recalculation of beats and ticks on ss
                int ss = (int)(((double)(125 * ticks)) / ((double)beats) + 0.5);
                if (ss < 1) ss = 1;
                else
                    if (ss > 255) ss = 255;

                tickrow[m] = ticks;
                speedrow[m] = ss;

                if ((lrow || m == 63) && LastRowTicks < 0 && LastRowBeats < 0)
                {
                    LastRowTicks = ticks;	//in the next pattern, ticks start with this value
                    LastRowBeats = beats;	//in the next pattern, beats start with this value
                }
            }

            //4 tracks in the pattern
            for (int ch = 0; ch < chnls; ch++)
            {
                BYTE* tdata = pdata + ch * 4;	//the beginning of the track data source
                TTrack* tr = g_Tracks.GetTrack(destnum);	//target track
                g_Tracks.ClearTrack(destnum);	//clean it first
                int dline;
                int sline;
                for (sline = ThisPatternFromRow, dline = 0; sline < 64; sline++, dline++)
                {
                    BYTE* bdata = tdata + sline * chnls * 4;		//pointer to 4 bytes block

                    ticks = tickrow[sline];	//common calculated value of ticks for the whole line

                    int period = bdata[1] | ((bdata[0] & 0x0f) << 8);			//12 bit
                    int sample = (((bdata[2] & 0xf0) >> 4) | (bdata[0] & 0x10)) & modsamples;	//0-31 / 0-15, not 0-255!
                    int effect = bdata[2] & 0x0f;
                    int param = bdata[3];
                    int sampleorig = sample;		//original as in the track
                    int pspeed;

                    if (sample == 0) sample = tins[ch];	//sample number 0 means the same as last used
                    else
                        tins[ch] = sample;	//saves the last used sample in this "column"

                    int note = -1, vol = -1;
                    if (period < 1 || period >= 4096)
                    {
                        //empty place (there is no note)
                    TonePortamento:
                        if (x_portamento && tporon[ch])
                        {	//the last time was portamento
                            int pnote = pertonote[tper[ch]];	//what note corresponds to the period
                            if (pnote != tnot[ch])
                            {
                                //portamento effect shifted the frequency to the level of other notes
                                note = pnote;
                                //the volume will be according to the current volume of what is on this channel, ie the form [ch]
                                vol = tvol[ch];	//0-64
                                tporon[ch] = 0;	//done for now (added note corresponds to change by portamento)
                                goto NoteByPortamento;
                            }
                        }
                    }
                    else
                    {
                        //there is a note
                        note = pertonote[period];
                        if (x_portamento && (effect == 0x03 || effect == 0x05))
                        {
                            //tone portamento 3xx or continue tone portamento 5
                            tporperiod[ch] = period;	//target portamento period
                            tporon[ch] = 1;
                            //addition
                            int aper = tper[ch];
                            int hfper = aper;
                            if (effect == 0x03)
                            {
                                if (param)
                                    pspeed = tporspeed[ch] = param;	//TONE portamento speed only for parameter 3xx
                                else
                                    pspeed = tporspeed[ch];	//if 300 => continue portamento is the last used speed
                            }
                            else //effect==0x05
                                pspeed = tporspeed[ch];	//effect 0x05 is continued portamento

                            if (aper < period)
                            {
                                hfper = aper + pspeed * (ticks - 1) / 2;
                                if (hfper > period) hfper = period;
                            }
                            else
                            {
                                hfper = aper - pspeed * (ticks - 1) / 2;
                                if (hfper < period) hfper = period;
                            }
                            if (pertonote[aper] != pertonote[hfper])
                            {
                                note = pertonote[hfper];
                                vol = tvol[ch];
                                tporon[ch] = 0;	//done for now (added note corresponds to change by portamento)
                                goto NoteByPortamento;
                            }
                            //
                            goto TonePortamento;	//and solve it as if it were an empty slot
                        }

                        tper[ch] = period;	//current period
                        tporon[ch] = 0;		//no portamento
                        vol = 0x40;			//the default is full volume (if you then overwrite it)
                        tvolslidedebt[ch] = 0; //debt volume slide = 0
                    NoteByPortamento:
                        tnot[ch] = note;		//last note
                        tr->note[dline] = note;
                        tr->instr[dline] = sample;
                        int	avol = AtariVolume(vol);		//conversion to atari volume
                        tr->volume[dline] = avol;	//or it will overwrite the Cxx parameter
                        imark[sample].used |= (1 << trackorder[ch]);	//sample is used on channel "ch"
                        if (note > imark[sample].maxnote) imark[sample].maxnote = note;
                        if (note < imark[sample].minnote) imark[sample].minnote = note;
                    }

                    //effect: PORTAMENTO
                    pspeed = 0;
                    if (effect == 0x01)	//1xx	Portamento Up
                    {
                        int cpor = pertable[CNotes::NOTESNUM - 1];	//the highest note that RMT can play
                        tporperiod[ch] = cpor;		//save the target portamento for this channel
                        pspeed = param;		//portamento speed
                        goto Effect3;
                    }
                    else
                        if (effect == 0x02)	//2xx	Portamento Down
                        {
                            int cpor = pertable[0];	//the lowest note that RMT can play
                            tporperiod[ch] = cpor;		//save the target portamento for this channel
                            pspeed = param;		//portamento speed
                            goto Effect3;
                        }
                        else
                            if (effect == 0x05)	//5xx	continue toneportamento (+ simultaneous volume slide, to work low)
                            {
                                pspeed = tporspeed[ch]; //takes over previous speed 
                                goto Effect3;	//same as effect 3, but without setting the portamento speed
                            }
                            else
                                if (effect == 0x03)	//3xx	TonePortamento
                                {
                                    if (param)
                                        pspeed = tporspeed[ch] = param;	//TONE portamento speed only for parameter 3xx
                                    else
                                        pspeed = tporspeed[ch];	//if 300 => continue portamento is the last used speed
                                Effect3:
                                    int cpor = tporperiod[ch]; //target portamento
                                    int aper = tper[ch];	//current period
                                    if (aper > cpor)
                                    {
                                        //portamento towards smaller values, ie up to higher tones
                                        aper -= pspeed * (ticks - 1);
                                        if (aper < cpor) aper = cpor;	//if it took place, then compare
                                    }
                                    else
                                        if (aper < cpor)
                                        {
                                            //portamento towards higher values, ie down to lower tones
                                            aper += pspeed * (ticks - 1);
                                            if (aper > cpor) aper = cpor;	//if it took place, then compare
                                        }

                                    tper[ch] = aper;
                                    tporon[ch] = 1;	//in the next step, the port will be resolved
                                }


                    //effects: VOLUME
                    if (effect == 0x0c)	//Cxx   setvolume xx=$00-$40
                    {
                        if (pass == 1)
                            param = (int)((double)param * imark[sample].trackvolumeincrease + 0.5);
                        if (param > 0x40) param = 0x40;	//maximum volume
                        tvol[ch] = param;
                        tvolslidedebt[ch] = 0;		//no debt
                        if (param > imark[sample].trackvolumemax) imark[sample].trackvolumemax = param;

                        int avol = AtariVolume(param);	//atari volume
                        tr->volume[dline] = avol;
                    }
                    else
                    {	//it is not Cxx => undefined volume
                        if (note >= 0 || sampleorig > 0)
                        {
                            //note without volume or sample without note => default maximum volume
                            int v = (vol >= 0) ? vol : 0x40;	//takes either "vol" from the portamento, or the default full
                            tvol[ch] = v;
                            imark[sample].trackvolumemax = v;
                        }
                    }

                    //effect NEXT
                    if (effect == 0x0a		//Axx	volumeslide xx = 0x decrease, xx = x0 increase
                        || effect == 0x05		//5xx	continue toneportamento (this has already done above) + volumeslide xx
                        || effect == 0x06		//6xx	continue vibrato + volumeslide xx => only volumeslide xx
                        )
                    {
                        int vol = tvol[ch];
                        double slidedivide = (note >= 0 || sampleorig > 0) ? 2 : 1; //first half is half
                        if ((param & 0xf0) == 0)
                        {
                            int voldec = (int)((double)(param & 0x0f) * (ticks - 1) * imark[sample].trackvolumeincrease / slidedivide + 0.5);
                            vol -= voldec;	//decrease
                            if (slidedivide == 2)	tvolslidedebt[ch] = -voldec; //debt volume slide
                        }
                        else
                        {
                            int volinc = (int)((double)((param & 0xf0) >> 4) * (ticks - 1) * imark[sample].trackvolumeincrease / slidedivide + 0.5);
                            vol += volinc;	//increase
                            if (slidedivide == 2)	tvolslidedebt[ch] = volinc; //debt volume slide
                        }

                        if (vol < 0) vol = 0;
                        else
                            if (vol > 0x40) vol = 0x40;

                        tvol[ch] = vol;
                        if (vol > imark[sample].trackvolumemax) imark[sample].trackvolumemax = vol;

                        int avol = AtariVolume(vol);		//atari volume
                        tr->volume[dline] = avol;
                    }
                    else
                        if (effect == 0x0f)	//Fxx   setspeed/tempo
                        {
                            /*
                            if (param<=0x20)
                                ticks=(param>0)? param : 1; //speed 0 is not possible
                            else
                                beats=param;

                            int ss= (int)(((double)(125 * ticks)) / ((double)beats) + 0.5);
                            if (ss<1) ss=1;
                            else
                            if (ss>255) ss=255;
                            */
                            int ss = speedrow[sline];		//use a common pre-calculated value for the whole row
                            tr->speed[dline] = ss;
                        }
                        else
                            if (effect == 0x0d)	//Dxx	pattern break (xx = goto xx line in the next pattern <- hm, we do not know)
                            {
                                //end the track on the first occurrence of Dxx from above
                                //and continue to position xx
                                if (tr->len == 64)
                                {
                                    tr->len = dline + 1;
                                    int nxp = ((int)(param / 16)) * 10 + (param % 16);		//it's there in the 10th system
                                    if (nxp >= 0 && nxp < 64 && NextPatternFromColumn == -1)
                                    {
                                        NextPatternFromRow = nxp;
                                        NextPatternFromColumn = ch;
                                    }
                                }
                            }
                            else
                                if (effect == 0x0b)	//Bxx	song jump
                                {
                                    if (tr->len == 64) tr->len = dline + 1;	//track break
                                    if (songjump < 0) songjump = param;
                                }

                    //the debt will increment if there is any and there is a free slot
                    if (tr->volume[dline] < 0)	//the volume is not specified
                    {
                        int mvol = tvol[ch];
                        mvol += tvolslidedebt[ch]; //adjust volume by debt
                        if (mvol < 0) mvol = 0;
                        else
                            if (mvol > 0x40) mvol = 0x40;
                        int avol = AtariVolume(mvol);
                        if (avol != AtariVolume(tvol[ch])) tr->volume[dline] = avol; //if it comes out differently than it was, it will add it
                        tvol[ch] = mvol;
                        tvolslidedebt[ch] = 0; //debt volume slide resolved
                    }

                }//line 0-64

                //if the shift (effect Dxx) has started, then it must shorten the length of the respective track
                if (ThisPatternFromColumn == ch && tr->len == 64 && dline < 64) tr->len = dline;

                int cit = -1;	//track number for the song
                if (!g_Tracks.IsEmptyTrack(destnum)) //is empty
                {
                    //Removes excess volume 0
                    g_Tracks.TrackOptimizeVol0(destnum);

                    //see if such a track already exists
                    for (int k = 0; k < destnum; k++)
                    {
                        if (g_Tracks.CompareTracks(k, destnum))
                        {
                            cit = k;	//found one
                            break;
                        }
                    }
                    if (cit < 0)
                    {
                        cit = destnum;	//was not found
                        destnum++;		//prepare for the next
                    }
                }

                m_song[dsline][trackorder[ch]] = cit;
                if (destnum >= TRACKSNUM)
                {
                    if (pass == 0) MessageBox(g_hwnd, "Out of RMT tracks. Tracks converting terminated.", "Warning", MB_ICONWARNING);
                    goto OutOfTracks;	//the tracks have reached the end
                }

            }//4 tracks in the pattern
            dsline++; //increment the target number of songlines(?)
            if (dsline >= SONGLEN)
            {
                if (pass == 0) MessageBox(g_hwnd, "Out of song lines. Song converting terminated.", "Warning", MB_ICONWARNING);
                goto OutOfSongLines;	//ran out of songlines
            }

            if (songjump >= 0 && songjump < SONGLEN)
            {
                m_songgo[dsline] = songjump;
                dsline++;
                if (dsline >= SONGLEN)
                {
                    if (pass == 0) MessageBox(g_hwnd, "Out of song lines. Song converting terminated.", "Warning", MB_ICONWARNING);
                    goto OutOfSongLines;
                }
            }
        }
    OutOfTracks:
    OutOfSongLines:

        //ALL PATTERNS OF SONG ARE DONE

        //prepare a track volume increase for each sample to make it the second time it arrives
        //increased the volume in the tracks
        if (pass == 0)
        {
            for (i = 1; i <= modsamples; i++)
            {
                TMODInstrumentMark* it = &imark[i];
                if (it->trackvolumemax > 0) it->trackvolumeincrease = (double)0x40 / (it->trackvolumemax);
            }
        }

        //---END OF TRANSITION 0/1---
    } //pass=0/1

    //corrects the jumps in the song
    int nog = 0;
    for (i = 0; i < dsline; i++)
    {
        int k;
        int go = m_songgo[i];
        if (go < 0) continue;
        for (k = 0; k <= go && k < SONGLEN; k++)	//looking for how much is from the beginning of the jump and for each found moves go by 1 step
        {
            if (m_songgo[k] >= 0) go++;
        }
        m_songgo[i] = go;	//writes that shifted jump
    }
    if (m_songgo[dsline - 1] < 0) { m_songgo[dsline] = restartpos; dsline++; } //loop at the beginning or where it wants according to header[951]

    //add to the instrument in the description MIN MAX range
    //and finds globally the lowest and highest used note of all instruments and in the whole song
    int glonomin = CNotes::NOTESNUM - 1;
    int glonomax = 0;
    for (i = 1; i <= modsamples; i++)
    {
        if (!imark[i].used) continue;
        int minnote = imark[i].minnote;
        int maxnote = imark[i].maxnote;
        if (minnote < glonomin) glonomin = minnote;
        if (maxnote > glonomax) glonomax = maxnote;
        //maximum volume in tracks
        int avol = AtariVolume(imark[i].trackvolumemax);	//atari volume
        CString s;
        s.Format("%s%s%X%02X", CNotes::GetNote(minnote), CNotes::GetNote(maxnote), avol, imark[i].used);
        strncpy(g_Instruments.GetName(i) + 23, s, 9);	//9 characters !! 23 + 9 = 32
    }

    if (x_shiftdownoctave) //an octave shift down for notes tuned too high (if possible)
    {
        if (glonomin >= 12 && glonomax > 36 + 5)
        {
            int noteshift = 256 - 12;	//1 octave lower
            for (i = 1; i <= modsamples; i++)
            {
                if (!imark[i].used) continue;
                TInstrument* ti = g_Instruments.GetInstrument(i);
                ti->noteTable[0] = (BYTE)(noteshift);
            }
        }
    }

    //imitation volume according to sample
    int smpfrom = patstart + (maxpat + 1) * patternsize;
    int samplen = 0;
    int nonemptysamples = 0;
    for (i = 1; i <= modsamples; i++, smpfrom += samplen)	//at the end of the loop, always move to the next sample
    {
        TMODInstrumentMark* im = &imark[i];
        TInstrument* rmti = g_Instruments.GetInstrument(i);

        samplen = im->samplen;

        if (samplen <= 2)	continue;		//zero length => empty sample

        BYTE* smpdata = new BYTE[samplen];

        if (!smpdata) continue;		//could not be allocated
        memset(smpdata, 0, samplen);	//clear

        in.clear();		//due to reaching the end when eof is set
        in.seekg(smpfrom, std::ios::beg);
        if ((int)in.tellg() != smpfrom)
        {
            CString s;
            s.Format("Can't seek sample #%02X data.", i);
            MessageBox(g_hwnd, (LPCTSTR)s, "Warning", MB_ICONWARNING);
        }
        else
        {
            in.read((char*)smpdata, samplen);
            if (in.gcount() != samplen)
            {
                CString s;
                s.Format("Can't read fully sample #%02X data.", i);
                MessageBox(g_hwnd, (LPCTSTR)s, "Warning", MB_ICONWARNING);
            }
        }

        int minnote = im->minnote;
        int period = pertable[minnote];
        int parts = samplen / period;
        if (parts < 1) parts = 1;
        if (im->replen > 2 || im->reppoint > 0)
        {
            //there is a loop
            if (parts > 32) parts = 32;
        }
        else
        {
            //there is no loop
            if (parts > 31) parts = 31; //32 parts reserved for silence
        }
        int blocksize = samplen / parts;
        int blockp = blocksize - 1;		//-1 to shift the boundaries between partitions by 1 to the left
        //and the last section was counted
        long sum = 0;
        BYTE lastsd = 0;
        long maxsum = 1;	//the maximum achievable amount in a sample block (it's 1 because it is divided)

        int ix = 0;
        long sumtab[32];
        for (int k = 0; k < samplen; k++)
        {
            sum += abs((int)smpdata[k] - (int)lastsd);
            lastsd = smpdata[k];		//last state of the curve
            if (k == blockp)			//borders between divisions
            {
                sumtab[ix] = sum;
                if (sum > maxsum) maxsum = sum;	//the highest
                sum = 0;
                blockp += blocksize;	//shifting the boundaries by the length of the section
                ix++;
                if (ix >= parts) break;
            }
        }

        double sampvol = (x_decreaseinstrument) ? ((double)im->volume / 0x3f) : 1;	//decimal number 0 to 1
        if (x_volumeincrease) sampvol /= im->trackvolumeincrease;		//decreases as you increase the volume in treks
        for (int k = 0; k < ix; k++)
        {
            int avol = (int)((double)16 * ((double)sumtab[k] / maxsum) * sampvol + 0.5);
            if (avol > 15) avol = 15;
            rmti->envelope[k][EnvelopeParameter::VOLUMEL] = rmti->envelope[k][EnvelopeParameter::VOLUMER] = avol;
            rmti->envelope[k][EnvelopeParameter::DISTORTION] = 0x0a;	//pure tone
        }

        rmti->parameters[PAR_ENV_LENGTH] = ix - 1;
        if (im->replen > 2 || im->reppoint > 0) //is there a loop?
        {
            //loop
            int ego = (int)((double)im->reppoint / blocksize + 0.5);
            if (ego > ix - 1) ego = ix - 1;
            rmti->parameters[PAR_ENV_GOTO] = ego;
            //and divides the end of the instrument according to the length of the loop
            int lopend = (int)((double)(im->reppoint + im->replen) / blocksize + 0.5);
            if (lopend > ix - 1) lopend = ix - 1;
            rmti->parameters[PAR_ENV_LENGTH];
        }
        else
        {
            //no loop (ix is max 31, so it can add a "silent loop" to the end)
            rmti->envelope[ix][EnvelopeParameter::VOLUMEL] = rmti->envelope[ix][EnvelopeParameter::VOLUMER] = 0; //silence at the end
            rmti->parameters[PAR_ENV_LENGTH] = ix;	//length of 1 vic
            rmti->parameters[PAR_ENV_GOTO] = ix;	//jump on the same thing
        }

        //Fourier
        /*

        if (x_fourier)
        {
#define	F_BLOCK		4096
#define F_SAMPLELEN	5*F_BLOCK
            Fft *myFFT=NULL;
            BYTE fsample[F_SAMPLELEN];	//bere prvnich 5 bloku => 20480 => cca 1 sekunda
            int sapos=0,safro=0,salen=im->samplen;
            while(sapos<F_SAMPLELEN)	// && im->replen>2
            {
                int lenb = (sapos+salen<F_SAMPLELEN)? salen : F_SAMPLELEN-sapos;
                memcpy(fsample+sapos,smpdata+safro,lenb);
                sapos+=lenb;
                if (im->replen>2 || im->reppoint>0)
                {
                    safro = im->reppoint;
                    salen= im->replen;
                }
            }

            int zaknota=0;
            if (rmti->par[PAR_TABLEN]==0) zaknota=rmti->tab[0];


            for(int i=0; i<flen/F_BLOCK; i++)
            {
                BYTE *bsample=fsample+i*F_BLOCK;
                if (myFFT) delete myFFT;
                myFFT = new Fft(4096,22050);
                if (!myFFT) break;
                for(int j=0; j<F_BLOCK; j++) myFFT->PutAt(j,bsample[j]-128);
                myFFT->Transform();

                int no[3];
                int p=myFFT->GetNotes(no[0],no[1],no[2]);
                if (p>0)
                {
                    for(int j=0; j<p; j++) rmti->tab[j]=zaknota+no[j]%12;
                    rmti->par[PAR_TABLEN]=p-1;
                    rmti->par[PAR_TABGO]=0;
                    rmti->par[PAR_TABTYPE]=0;	//tabulka not
                    rmti->par[PAR_TABMODE]=0;	//nastavovani
                    rmti->par[PAR_TABSPD]=1;
                }
                if (myFFT)
                {
                    delete myFFT;
                    myFFT=NULL;
                }
            }
        }
        //konec Fouriera
        */

        //number of non-empty samples
        nonemptysamples++;

        //free memory
        if (smpdata) { delete[] smpdata; smpdata = NULL; }

    }

    //checking the end of the module with the end of the last sample
    if (smpfrom != modulelength)
    {
        //is different
        CString s;
        s.Format("Bad length of module.\n(Last sample's end is at %i, but length of module is %i.)", smpfrom, modulelength);
        MessageBox(g_hwnd, s, "Warning", MB_ICONWARNING);
    }

    //and only at the end
    for (i = 1; i <= modsamples; i++)
    {
        //send to Atari
        g_Instruments.Update(i);
    }

    //CLEAR MEMORY 
    if (mem) { delete[] mem;	mem = NULL; }

    //FINAL DIALOGUE AFTER IMPORT
    CImportModFinishedDlg imfdlg;
    imfdlg.m_info.Format("%i tracks, %i instruments, %i songlines", destnum, nonemptysamples, dsline);

    //OPTIMIZATIONS
    if (x_optimizeloops)
    {
        int optitracks = 0, optibeats = 0;
        TracksAllBuildLoops(optitracks, optibeats);
        CString s;
        s.Format("\x0d\x0aOptimization: Loops in %i tracks (%i beats/lines)", optitracks, optibeats);
        imfdlg.m_info += s;
    }

    if (x_truncateunusedparts)
    {
        int clearedtracks = 0, truncatedtracks = 0, truncatedbeats = 0;
        SongClearUnusedTracksAndParts(clearedtracks, truncatedtracks, truncatedbeats);
        CString s;
        s.Format("\x0d\x0aOptimization: Cleared %i, truncated %i tracks (%i beats/lines)", clearedtracks, truncatedtracks, truncatedbeats);
        imfdlg.m_info += s;
    }

    if (imfdlg.DoModal() != IDOK)
    {
        //did not give Ok, so it deletes
        ClearSong(originalg_tracks4_8); //returns the original value
        MessageBox(g_hwnd, "Module import aborted.", "Import...", MB_ICONINFORMATION);
    }

    return 1;
}