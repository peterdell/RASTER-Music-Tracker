#include "StdAfx.h"

#include "AtariIO.h"

#include "Tracks.h"
#include "Song.h"
#include "Instruments.h"
#include "Notes.h"

// CSong::ImportTMCParseHeader()/ImportTMCApply() (and their private helper
// CConvertTracks, used only by ImportTMCApply()) split from IO_Importer.cpp
// - see plans/IO_IMPORTER_PLAN.md. ImportTMC()'s options dialog needs the
// song name parsed from the file header to build its own text, so the real
// work is a two-phase split rather than a single Apply(): ParseHeader()
// does the unconditional real work needed before the dialog can be shown
// (ClearSong, load+validate the header, set the song name); Apply() does
// the rest of the real conversion, taking the dialog's flags as parameters
// and returning the stats its confirmation dialog displays. The
// dialog-showing thin wrapper (ImportTMC() itself) stays behind in
// IO_Importer.cpp. Only touches already-safe globals (g_Instruments,
// g_Tracks) plus CSong's own already-safe methods (ClearSong/SetTracks/
// TracksAllBuildLoops/SongClearUnusedTracksAndParts).

extern CInstruments g_Instruments;

struct TSourceTrack
{
    int note[64];
    int instr[64];
    int volumeL[64];
    int volumeR[64];
    int speed[64];
    int len;
    BOOL stereo;
};

struct TDestinationMark
{
    int fromtrack;
    int shift;
    BYTE leftright;
};

struct TInstrumentMark
{
    int maxvolL;
    int maxvolR;
    BOOL stereo;
};

class CConvertTracks
{
public:
    CConvertTracks();
    void SetRMTTracks(CTracks* tracks) { m_ctracks = tracks; };
    int Init();
    TSourceTrack* GetSTrack(int t) { return (t >= 0 && t < TRACKSNUM) ? &m_strack[t] : (TSourceTrack*)NULL; };
    TInstrumentMark* GetIMark(int instr) { return (instr >= 0 && instr < INSTRSNUM) ? &m_imark[instr] : (TInstrumentMark*)NULL; };

    int MakeOrFindTrackShiftLR(int from, int shift, BYTE lr);

private:
    TSourceTrack m_strack[TRACKSNUM];
    TDestinationMark m_dmark[TRACKSNUM];
    TInstrumentMark m_imark[INSTRSNUM];
    CTracks* m_ctracks;
};

CConvertTracks::CConvertTracks()
{
    m_ctracks = 0;
    Init();
}

int CConvertTracks::Init()
{
    for (int i = 0; i < TRACKSNUM; i++)
    {
        TSourceTrack* at = &m_strack[i];
        for (int j = 0; j < 64; j++)	at->note[j] = at->instr[j] = at->volumeL[j] = at->volumeR[j] = at->speed[j] = -1;
        at->len = -1;
        at->stereo = 0;

        TDestinationMark* dm = &m_dmark[i];
        dm->fromtrack = -1;
        dm->shift = 0;
        dm->leftright = 0;
    }
    for (int i = 0; i < INSTRSNUM; i++)
    {
        m_imark[i].maxvolL = 0;
        m_imark[i].maxvolR = 0;
        m_imark[i].stereo = 0;
    }
    return 1;
}

#define VOLUMES_L	1
#define VOLUMES_R	2

int CConvertTracks::MakeOrFindTrackShiftLR(int from, int shift, BYTE lr)
{
    //will try to find a matching track among the already created ones
    //or create a new one (as close as possible to the original track number).
    //Return its number, or -1

    int i;

    if (m_strack[from].len < 0) return -1;	//this source track is empty

    //will find the number where it will first create the new one
    int track = -1;

    if (m_dmark[from].fromtrack < 0)
    {
        track = from;		//the same number is free
    }
    else
    {
        //finds a free place as close as possible to the "from" track
        for (i = from + 1; i != from; i++)
        {
            if (i >= TRACKSNUM) i = 0;	//when he reaches the end, he starts from the beginning
            if (m_dmark[i].fromtrack < 0) break; //found a free track
        }
        if (i == from) return -1;		//did not find one
        track = i;	//this is empty, it will create the appropriate modification of the source track
    }

    //rewritten from source track to system track
    TSourceTrack* ts = &m_strack[from];
    if (!ts->stereo) lr = VOLUMES_L | VOLUMES_R; //if it's not a stereo track and it wants the right channel, give it the left channel anyway
    TTrack* td = m_ctracks->GetTrack(track);
    int activeinstr = -1;
    for (i = 0; i < ts->len; i++)
    {
        int note = ts->note[i];
        if (note >= 0)
        {
            note += shift;
            //while (note<0) note+=12;
            //while (note>=NOTESNUM) note-=12;
            while (note < 0) { note += 64; }
            while (note >= 64) { note -= 64; }
            if (note >= CNotes::NOTESNUM) { note = CNotes::NOTESNUM - 1; }
        }
        td->note[i] = note;

        int instr = ts->instr[i];
        td->instr[i] = instr;
        if (instr >= 0) activeinstr = instr;

        int volume = (lr & VOLUMES_L) ? ts->volumeL[i] : ts->volumeR[i];
        if (activeinstr >= 0 && volume > 0)
        {
            int maxvol = (lr & VOLUMES_L) ? m_imark[activeinstr].maxvolL : m_imark[activeinstr].maxvolR;
            if (maxvol - (15 - volume) <= 0) volume = 0;
        }
        td->volume[i] = volume;

        td->speed[i] = ts->speed[i];
    }
    td->len = ts->len;

    //search if this one does not match one that already exists
    for (i = 0; i < TRACKSNUM; i++)
    {
        if (track == i) continue;	//it will not compare itself with itself
        if (m_ctracks->CompareTracks(track, i))
        {
            //found the same
            //so the newly created one will be deleted
            m_ctracks->ClearTrack(track);
            //and return the former number
            return i; //found one
        }
    }

    //does not match the previous one

    TDestinationMark* dm = &m_dmark[track];
    dm->fromtrack = from;
    dm->shift = shift;
    dm->leftright = lr;
    return track;
}

//----------------------------------------------

bool CSong::ImportTMCParseHeader(std::istream& in, TImportTMCHeader& header)
{
    //delete the current song
    g_Tracks.SetMaxTrackLength(64);	// track length is 64
    ClearSong(8);			// standard TMC is 8 tracks, clear everything

    memset(header.mem, 0, sizeof(header.mem));
    WORD bto;

    int len = CAtariIO::LoadBinaryBlock(in, header.mem, header.bfrom, bto);

    if (len <= 0)
    {
        header.ok = false;
        return false;
    }

    //song name
    int j, k;
    char a;
    for (j = 0; j < 30 && (a = header.mem[header.bfrom + j]); j++)
    {
        if (a < 32 || a >= 127) a = ' ';
        m_songname[j] = a;
    }
    for (k = j; k < SONG_NAME_MAX_LEN; k++) m_songname[k] = ' '; //fill in the gaps

    header.ok = true;
    return true;
}

void CSong::ImportTMCApply(const TImportTMCHeader& header, BOOL usetable, BOOL optimizeloops, BOOL truncateunusedparts, TImportTMCResult& result)
{
    const unsigned char* mem = header.mem;
    WORD bfrom = header.bfrom;

    CConvertTracks cot;
    cot.SetRMTTracks(&g_Tracks);

    WORD instr_ptr[64], track_ptr[128];
    BOOL instr_used[64];
    WORD adr;
    BYTE c;
    int speco = 0;			//speedcorrection
    int i, j;

    //speeds
    m_mainSpeed = m_speed = mem[bfrom + 30] + 1;
    m_instrumentSpeed = mem[bfrom + 31];
    if (m_instrumentSpeed > 4)
    {
        speco = m_instrumentSpeed - 4;
        m_instrumentSpeed = 4;		//4x instrspeed maximum
    }
    else
        if (m_instrumentSpeed < 1) m_instrumentSpeed = 1;		//1x instrspeed minimum

    //instrument vectors
    for (i = 0; i < 64; i++)
    {
        instr_ptr[i] = mem[bfrom + 32 + i] + (mem[bfrom + 32 + 64 + i] << 8);
        instr_used[i] = 0; //find out when the track is used if it is used
    }
    //track vectors
    for (i = 0; i < 128; i++) track_ptr[i] = mem[bfrom + 32 + 128 + i] + (mem[bfrom + 32 + 128 + 128 + i] << 8);

    //tracks
    for (i = 0; i < 128; i++)
    {
        adr = track_ptr[i];
        if (mem[adr] == 0xff) continue;	//points to FF
        int line = 0;
        TSourceTrack& ts = *(cot.GetSTrack(i));
        int ains = 0, note = -1, volL = 15, volR = 15, speed = 0, space = 0;
        BOOL endt = 0;

        while (1)
        {
            if (line >= 64) { ts.len = 64; break; }

            c = mem[adr++];
            int txx = c & 0xc0;
            if (txx == 0x80)
            {
                //instrument
                ains = c & 0x3f;
                instr_used[ains] = 1;
            }
            else
                if (txx == 0x00)
                {
                    //note and volume
                    //note
                    note = (c & 0x3f) - 1;
                    if (note < 0) note = -1;
                    else
                    {
                        ts.note[line] = note;
                        ts.instr[line] = ains;
                    }
                    //volume
                    c = mem[adr++];
                    volL = 15 - ((c & 0xf0) >> 4);
                    volR = 15 - (c & 0x0f);
                    if (volL != volR) ts.stereo = 1;	//there is some different volume for L and R
                    if (volL != 0 || volR != 0)			//in TMC there may be no note if vol L and R are both equal to 0
                    {
                        ts.volumeL[line] = volL;
                        ts.volumeR[line] = volR;
                    }
                    line++;
                }
                else
                    if (txx == 0x40)
                    {
                        //note speed  (+possible volume)
                        //note
                        note = (c & 0x3f) - 1;
                        if (note < 0) note = -1;
                        else
                        {
                            ts.note[line] = note;
                            ts.instr[line] = ains;
                        }
                        //speed
                        c = mem[adr++];
                        speed = c & 0x0f;
                        if (speed == 0)
                        {
                            ts.len = line + 1;
                            endt = 1;
                            //break;			//speed = 0 => end of this track
                        }
                        else
                        {
                            ts.speed[line] = speed + 1;
                        }
                        if (c & 0x80)		//behind speed is also the volume
                        {
                            //volume
                            c = mem[adr++];
                            volL = 15 - ((c & 0xf0) >> 4);
                            volR = 15 - (c & 0x0f);
                            if (volL != volR) ts.stereo = 1;	//there is some different volume for L and R
                            if (volL != 0 || volR != 0)			//in TMC there may be no note if vol L and R are both equal to 0
                            {
                                ts.volumeL[line] = volL;
                                ts.volumeR[line] = volR;
                            }
                        }
                        if (endt) break;	//end of this track via speed = 0
                        line++;
                    }
                    else
                        if (txx == 0xc0)
                        {
                            //gap
                            space = (c & 0x3f) + 1;
                            if (space > 63 || line + space > 63)
                            {
                                if (line > 0) ts.len = 64;
                                break;	//space = 64 (ff) => end of this track
                            }
                            line += space;
                        }
        }
        //and on the next track
        line = 0;
    }

    //instruments
    int nonemptyinstruments = 0;
    for (i = 0; i < 64; i++)
    {
        TInstrument* ai = g_Instruments.GetInstrument(i);

        if (instr_used[i]) //this instrument is used somewhere in some track
        {
            //yes => name
            CString s;
            s.Format("TMC instrument imitation %02X", i);
            strncpy(ai->name, (LPCTSTR)s, s.GetLength());
        }

        int adr_e = instr_ptr[i];
        if (adr_e == 0) continue;	//undefined

        //defined

        nonemptyinstruments++;

        int adr_t = instr_ptr[i] + 63;
        int adr_p = instr_ptr[i] + 63 + 8;

        //audctl
        BYTE audctl1 = mem[adr_p + 1];
        BYTE audctl2 = mem[adr_p + 2];

        //ai.par[PAR_AUDCTL0...7]=audctl1;

        //envelope
        int c1, c2, c3;
        BOOL anyrightvolisntzero = 0;
        BOOL filteru = 0;					//is it using the filter?
        int lasttmccmd = -1;				//last command
        int lasttmcpar = 0;				//last parameter
        int cmd1_2 = 0;					//for command sequence 1 and 2
        int par1_2 = 0;					//parameter for adding frequency for command sequence 1 and 2
        int cmd2_2 = 0;					//sequence 2 and 2
        int par2_2 = 0;					//parameter for sequence 2 and 2
        int cmd6_6 = 0;					//sequence 6 and 6
        int par6_6 = 0;					//parameter for sequence 6 and 6
        int maxvolL = 0, maxvolR = 0, lastvol = 0;
        for (j = 0; j < 21; j++)
        {
            c1 = mem[adr_e + j * 3];
            c2 = mem[adr_e + j * 3 + 1];
            c3 = mem[adr_e + j * 3 + 2];

            int dist08 = (c1 >> 4) & 0x01;		//forced volume bit
            BYTE dist = (c1 >> 4) & 0x0e;		//distortions, in step of 2
            if (dist == 0x0e) dist = 0x0a;		//pure tones
            else
                if (dist == 0x06) dist = 0x02;		//distortion 2 sharp tones

            //now dist is 0,2,4,8,A,C (without 0x06 and 0x0e)

            int basstable = mem[adr_p + 7] & 0xc0;
            if (dist == 0x0c && (basstable == 0x80 || basstable == 0xc0)) dist = 0x0e;

            int com08 = (c2 >> 4) & 0x08;		//command 8x
            BYTE audctl = com08 ? audctl2 : audctl1;

            //16 bit bass?
            if (((audctl & 0x50) == 0x50 || (audctl & 0x28) == 0x28) && (dist == 0x0c))
                dist = 0x06;	//16bit bass

            //filter
            if (((audctl & 0x04) == 0x04 || (audctl & 0x02) == 0x02))
            {
                ai->envelope[j][EnvelopeParameter::FILTER] = 1;
                filteru = 1;
            }

            ai->envelope[j][EnvelopeParameter::DISTORTION] = dist;
            int vol = c1 & 0x0f;			//volumeL 0-F;
            ai->envelope[j][EnvelopeParameter::VOLUMEL] = lastvol = vol;			//lastvol is needed to correct the fading
            if (vol > maxvolL) maxvolL = vol;	//maximum volumeL of the whole envelope
            vol = c2 & 0x0f;			//volumeR 0-F
            ai->envelope[j][EnvelopeParameter::VOLUMER] = vol;
            if (vol > maxvolR) maxvolR = vol;	//maximum volumeR of the whole envelope
            if (vol > 0) anyrightvolisntzero = 1;	//some volumeR is> 0

            int tmccmd = (c2 >> 4) & 0x07;		//command
            int tmcpar = c3;
            int rmtcmd = tmccmd, rmtpar = tmcpar;

            //TMC to RMT command conversion
            switch (tmccmd)
            {
            case 0: //without effect
                rmtcmd = 0; rmtpar = 0;
                break;
            case 1: //P-> AUDF (same as TMC)
                cmd1_2 = 2;
                par1_2 = tmcpar;
                if (com08 || tmcpar == 0x00) //but cmd 9 or 1 with parameter 00 is volume only
                {
                    rmtcmd = 7;		//Volume only
                    rmtpar = 0x80;
                }
                break;
            case 2: //P+A->AUDF
                if (j > 0 && cmd1_2)
                {
                    //divide 2 xy into 1 ab, where ab = value at the previous left unit + increment
                    //it will work in chains for the other two, which is fine
                    rmtcmd = 1;
                    rmtpar = (BYTE)(par1_2 + tmcpar);
                    cmd1_2 = 2;	//to make it last again for the next envelope column
                    par1_2 = rmtpar;	//added frequency for next
                }
                else
                    if (j > 0 && cmd2_2)
                    {
                        //divide 2 xy to 2 ab, where ab = value at the previous left two + increment
                        rmtcmd = 2;
                        rmtpar = (BYTE)(par2_2 + tmcpar);
                        cmd2_2 = 2;
                        par2_2 = rmtpar;
                    }
                    else
                    {
                        //will remain the same
                        rmtcmd = 2;
                        rmtpar = tmcpar;
                        cmd2_2 = 2;
                        par2_2 = rmtpar;
                    }
                break;
            case 3: //P+N->AUDF
                //corresponds exactly to TMC command 2
                rmtcmd = 2; rmtpar = tmcpar;
                break;
            case 4: //P&RND->AUDF
                rmtcmd = 1;	//converts randomly selected frequencies to a fixed setting
                rmtpar = rand() & 0xff;
                break;
            case 5: //PN->AUDF  (plays a fixed note with that index)
                rmtcmd = 1;	//converts to a fixed frequency setting the corresponding note (according to distortion)
                //dist=0-e
                rmtpar = (256 - (tmcpar * 4)) & 0xff;	//JUST FOR THAT IT WILL BE ADVISORY !!!!!!!!!!!!!!!!!!!!!!!!!!!!
                break;
            case 6: //PN+A->AUDF
                if (j > 0 && cmd6_6)
                {
                    rmtcmd = 0;
                    rmtpar = (BYTE)(par6_6 + tmcpar);
                    cmd6_6 = 2;
                    par6_6 = rmtpar;
                }
                else
                {
                    rmtcmd = 0;
                    rmtpar = tmcpar;
                    cmd6_6 = 2;
                    par6_6 = rmtpar;
                }
                break;
            case 7: //PN+N->AUDF
                rmtcmd = 0;	//in RMT, the note shift is done by command 0
                break;
            }

            //bass shift
            if (dist == 0x0c && rmtcmd == 0) rmtpar += 8;

            //forced volume
            if (dist08) { rmtcmd = 7; rmtpar = 0x80; } //volume only

            ai->envelope[j][EnvelopeParameter::COMMAND] = rmtcmd;
            ai->envelope[j][EnvelopeParameter::X] = (rmtpar >> 4) & 0x0f;
            ai->envelope[j][EnvelopeParameter::Y] = rmtpar & 0x0f;

            lasttmccmd = tmccmd;
            lasttmcpar = tmcpar;
            if (cmd1_2 > 0) cmd1_2--;		//read
            if (cmd2_2 > 0) cmd2_2--;		//read
            if (cmd6_6 > 0) cmd6_6--;		//read

        } //0-20 column envelope

        //is all right volume = 0? => copies left to right
        if (!anyrightvolisntzero)
        {
            for (j = 0; j <= 21; j++) ai->envelope[j][EnvelopeParameter::VOLUMER] = ai->envelope[j][EnvelopeParameter::VOLUMEL];
            maxvolR = maxvolL;
        }

        //envelope length
        ai->parameters[PAR_ENV_LENGTH] = 20;			//the envelope is 21 columns
        ai->parameters[PAR_ENV_GOTO] = 20;

        TInstrumentMark* im = cot.GetIMark(i);
        im->maxvolL = maxvolL;
        im->maxvolR = maxvolR;
        im->stereo = anyrightvolisntzero;

        //table
        BOOL tableu = 0;	//table used
        for (j = 0; j < 8; j++)
        {
            int nut = mem[adr_t + j];
            if (nut >= 0x80 && nut <= 0xc0) nut += 0x40;
            if (nut >= 0x40 && nut <= 0x7f) nut -= 0x40;
            if (nut != 0) tableu = 1; //table is used for something
            ai->noteTable[j] = nut;
        }

        //parameters
        //table length and speed
        int tablen = (mem[adr_p + 8] >> 4) & 0x07;
        ai->parameters[PAR_TBL_LENGTH] = tablen;		//0-7
        ai->parameters[PAR_TBL_SPEED] = mem[adr_p + 8] & 0x0f;			//speed 0-15

        //other parameters

        //volume slide
        BYTE t1vslide = mem[adr_p + 3];
        //speco is the correction when the instrument decelerates from 5 and more to 4
        BYTE t2vslide = (t1vslide < 1) ? 0 : (BYTE)((double)15 / lastvol * (double)255 / ((double)t1vslide * (1 - ((double)speco) / 4)) + 0.5);
        if (t2vslide > 255) t2vslide = 255;
        else
            if (t2vslide < 0) t2vslide = 0;
        ai->parameters[PAR_VOL_FADEOUT] = t2vslide;
        ai->parameters[PAR_VOL_MIN] = 0;

        //vibrato or fshift
        BYTE pvib = mem[adr_p + 5] & 0x7f;
        BYTE pvib8 = mem[adr_p + 5] & 0x80;		//highest bit
        BYTE delay = mem[adr_p + 6];
        BYTE vibspe = mem[adr_p + 7] & 0x3f;	//vibrato speed
        BYTE vib = 0, fshift = 0;
        BOOL vpt = 0;			//vibrato through the table succeeded

        int posuntable = (int)((double)delay / (vibspe + 1) + 0.5);

        BOOL nobytable = 0;	//if it tries to convert to a table
        if (!usetable) nobytable = 1;	//it shouldn't try
        if (filteru) nobytable = 1;

        //what if the table is used, but only to move to the 0th place
        if (!nobytable && tableu && tablen == 0 && (pvib & 0x40))		//(pvib & 0x40) <- only if vibrato uses something, otherwise it doesn't make sense to redo it
        {
            //that is, it optimizes over the shift of all notes in the envelope
            int psn = ai->noteTable[0];	//0th place in the table
            for (j = 0; j < 21; j++)
            {
                if (ai->envelope[j][EnvelopeParameter::COMMAND] == 0) //music shift
                {
                    BYTE notenum = (ai->envelope[j][EnvelopeParameter::X] << 4) + ai->envelope[j][EnvelopeParameter::Y];
                    notenum += psn; //shifts
                    ai->envelope[j][EnvelopeParameter::X] = (notenum >> 4) & 0x0f;
                    ai->envelope[j][EnvelopeParameter::Y] = notenum & 0x0f;
                }
            }
            ai->noteTable[0] = 0; //so the parameter in the table is reset
            tableu = 0;	//and thus the table is free for further use
        }

        //and now the individual values of the "vibrato" parameter:

        if (pvib > 0x10 && pvib < 0x3f)
        {
            //vibrato
            int hn = pvib >> 4;		//vibrato type 1-3
            int dn = pvib & 0x0f;	//cut out vibrato 0-f

            /*
            vib = (int) ((double) (hn+(float)dn/4)+0.5);
            if (vib<0) vib=0;
            else
            if (vib>3) vib=3;
            */
            vib = 3;
            if (hn == 1) vib = 1 + vibspe;	//the most similar is this regardless of the magnitude of the oscillation, speed will move it to vib 2 or 3
            else
                if (hn == 2 && dn == 1) vib = 2 + vibspe; //for dn == 1 this corresponds exactly, the others are already corresponding to vib3
            if (vib > 3) vib = 3;	//if it read more than 3 after reading "vibspec"

            //if you do not use the table, try to use vibrato through the table
            if (!nobytable && !tableu)
            {
                if (hn == 1 && (dn > 2 || vibspe > 0))							//(dn>=4 || vibspe>0) )
                {
                    if (posuntable > NOTE_TABLE_MAX_LEN - 4) posuntable = NOTE_TABLE_MAX_LEN - 4; //did not give what is possible according to the delay
                    ai->noteTable[posuntable] = 0;
                    ai->noteTable[posuntable + 1] = (pvib8) ? (BYTE)(256 - dn) : dn;
                    ai->noteTable[posuntable + 2] = 0;
                    ai->noteTable[posuntable + 3] = (pvib8) ? dn : (BYTE)(256 - dn);
                    ai->parameters[PAR_TBL_LENGTH] = posuntable + 3;
                    ai->parameters[PAR_TBL_GOTO] = posuntable;
                    ai->parameters[PAR_TBL_TYPE] = 1;	//frequency table
                    ai->parameters[PAR_TBL_SPEED] = (vibspe < 0x3f) ? vibspe : 0x3f;
                    vpt = 1; //successful
                }
                else
                    if (hn == 2 && (dn > 2 || vibspe > 0))							//(dn>=4 || vibspe>0))
                    {
                        if (posuntable > NOTE_TABLE_MAX_LEN - 4) posuntable = NOTE_TABLE_MAX_LEN - 4; //did not give what is possible according to the delay
                        ai->noteTable[posuntable] = (pvib8) ? (BYTE)(256 - dn) : dn;
                        ai->noteTable[posuntable + 1] = 0;
                        ai->noteTable[posuntable + 2] = (pvib8) ? dn : (BYTE)(256 - dn);
                        ai->noteTable[posuntable + 3] = 0;
                        ai->parameters[PAR_TBL_LENGTH] = posuntable + 3;
                        ai->parameters[PAR_TBL_GOTO] = posuntable;
                        ai->parameters[PAR_TBL_TYPE] = 1;	//frequency table
                        int sp = dn * (vibspe + 1) - 1;
                        if (sp < 0) sp = 0;	else if (sp > 0x3f) sp = 0x3f;
                        ai->parameters[PAR_TBL_SPEED] = sp;
                        vpt = 1; //successful
                    }
                    else
                        if (hn == 3 && (dn > 2 || vibspe > 0))							//(dn>=4 || vibspe>0))
                        {
                            if (posuntable > NOTE_TABLE_MAX_LEN - 4) posuntable = NOTE_TABLE_MAX_LEN - 4; //did not give what is possible according to the delay
                            ai->noteTable[posuntable] = (pvib8) ? (BYTE)(dn * 4) : (BYTE)(256 - (dn * 4)); //for notes it is the other way around (add note = read frequency
                            ai->noteTable[posuntable + 1] = 0;
                            ai->noteTable[posuntable + 2] = (pvib8) ? (BYTE)(256 - (dn * 4)) : (BYTE)(dn * 4); //it is the other way around
                            ai->noteTable[posuntable + 3] = 0;
                            ai->parameters[PAR_TBL_LENGTH] = posuntable + 3;
                            ai->parameters[PAR_TBL_GOTO] = posuntable;
                            ai->parameters[PAR_TBL_TYPE] = 1;	//frequency table
                            int sp = dn * (vibspe + 1) - 1;
                            if (sp < 0) sp = 0;	else if (sp > 0x3f) sp = 0x3f;
                            ai->parameters[PAR_TBL_SPEED] = sp;
                            vpt = 1; //successful
                        }
            }
        }
        else
            if (pvib > 0x40 && pvib <= 0x4f)
            {
                //fshift down (added frq)
                fshift = pvib - 0x40;
                if (pvib8) fshift = (BYTE)(256 - fshift);

                //and now find out if it wouldn't do it through the table
                if (!nobytable && !tableu && vibspe > 0)
                {
                    if (posuntable > NOTE_TABLE_MAX_LEN - 2) posuntable = NOTE_TABLE_MAX_LEN - 2; //he didn't give up
                    ai->noteTable[posuntable] = fshift;
                    ai->noteTable[posuntable + 1] = fshift;
                    ai->parameters[PAR_TBL_LENGTH] = posuntable + 1;
                    ai->parameters[PAR_TBL_GOTO] = posuntable + 1;
                    ai->parameters[PAR_TBL_TYPE] = 1;	//frequency table
                    ai->parameters[PAR_TBL_MODE] = 1;	//read
                    ai->parameters[PAR_TBL_SPEED] = (vibspe < 0x3f) ? vibspe : 0x3f;
                    vpt = 1; //successful
                }
            }
            else
                if (pvib > 0x50 && pvib <= 0x5f)
                {
                    //shift in notes down (left) => shift in frequency 255/61 * shift_in_notes
                    fshift = (int)(((double)(255 / 61) * (pvib - 0x50)) + 0.5);
                    if (pvib8) fshift = (BYTE)(256 - fshift);

                    //and now find out if it wouldn't do it through the table

                    if (!nobytable && !tableu)				// && vibspe>0)
                    {
                        if (posuntable > NOTE_TABLE_MAX_LEN - 2) posuntable = NOTE_TABLE_MAX_LEN - 2; //it didn't give up
                        int nshift = pvib - 0x50;
                        if (!pvib8) nshift = (BYTE)(256 - nshift);		//for notes it is the opposite (5x is <- down, Dx is up ->)
                        ai->noteTable[posuntable] = nshift;
                        ai->noteTable[posuntable + 1] = nshift;
                        ai->parameters[PAR_TBL_LENGTH] = posuntable + 1;
                        ai->parameters[PAR_TBL_GOTO] = posuntable + 1;
                        ai->parameters[PAR_TBL_TYPE] = 0;	//note table
                        ai->parameters[PAR_TBL_MODE] = 1;	//read
                        ai->parameters[PAR_TBL_SPEED] = (vibspe < 0x3f) ? vibspe : 0x3f;
                        vpt = 1; //succesful
                    }
                }
                else
                    if (pvib > 0x60 && pvib <= 0x6f)
                    {
                        //tuned $ 60- $ 6f => -0 to -15 to frequency
                        //we don't know
                    }


        //verify whether the vibrato effect was done via table or "normal" via vibrato and fshift
        if (vpt)
        {
            //managed to do vibrato through the table, so it does not use vibrato, fshift or delay
            vib = 0;
            fshift = 0;
            delay = 0;
        }

        ai->parameters[PAR_VIBRATO] = vib;
        ai->parameters[PAR_FREQ_SHIFT] = fshift;
        if (vib == 0 && fshift == 0) delay = 0;
        else
            if ((vib > 0 || fshift > 0) && delay == 0) delay = 1;
        ai->parameters[PAR_DELAY] = delay;

        //optimalization
        //envelope length
        int lastnonzerovolumecol = -1;
        int lastchangecol = 0;
        for (int k = 0; k <= 20; k++)
        {
            if (ai->envelope[k][EnvelopeParameter::VOLUMEL] > 0 || ai->envelope[k][EnvelopeParameter::VOLUMER] > 0) lastnonzerovolumecol = k;
            if (k > 0)
            {
                for (int m = 0; m < ENVROWS; m++)
                {
                    if (ai->envelope[k][m] != ai->envelope[k - 1][m])	//is there anything else? (volumeL, R, distortion, ..., portamento)
                    {
                        lastchangecol = k;	//yeah, something else.
                        break;
                    }
                }
                //skipping break(?)
            }
        }

        if (lastnonzerovolumecol < 20)
        {
            ai->parameters[PAR_ENV_LENGTH] = ai->parameters[PAR_ENV_GOTO] = lastnonzerovolumecol + 1; //shortens to the last non-zero volume
        }

        if (lastchangecol < lastnonzerovolumecol	//the last arbitrary change took place in the last change col column
            && ai->parameters[PAR_VOL_FADEOUT] == 0				//only when the volume does not decrease
            )
        {
            ai->parameters[PAR_ENV_LENGTH] = ai->parameters[PAR_ENV_GOTO] = lastchangecol; //shorten it to the column where the last change of anything was
        }

        //table
        for (int v = 0; v <= ai->parameters[PAR_TBL_LENGTH]; v++)	//are there only zeros?
        {
            if (ai->noteTable[v] != 0) goto NoTableOptimize;
        }
        if (ai->parameters[PAR_TBL_LENGTH] >= 1 && ai->parameters[PAR_TBL_GOTO] == 0)
        {
            ai->parameters[PAR_TBL_LENGTH] = 0;
            ai->parameters[PAR_TBL_SPEED] = 0;
        }
    NoTableOptimize:


        //projected instrument into Atari's RAM
        g_Instruments.Update(i);
    } //and another instrument


    //song
    int line = 0;
    BYTE lr = 0;
    BOOL stereomodul = 0;
    int numoftracks = 0;
    int adrendsong = (instr_ptr[0] > 0) ? instr_ptr[0] : track_ptr[0];

    for (adr = bfrom + 32 + 128 + 256; adr < adrendsong; adr += 16, line++)
    {
        if ((mem[adr + 15] & 0x80) == 0x80)	//goto line?
        {
            //goto
            m_songgo[line] = mem[adr + 14] & 0x7f;
            continue;
        }
        for (i = 0; i < 8; i++)
        {
            int t = mem[adr + 15 - i * 2];
            char preladeni = (char)mem[adr + 14 - i * 2];
            if (t >= 0 && t < 128)
            {
                if (i < 4)
                {
                    lr = VOLUMES_L;
                }
                else
                {
                    //lr = VOLUMES_R;
                    lr = VOLUMES_L;
                    /*
                    int levy=mem[adr+15+8-i*2];
                    if (levy>=0 && levy<128 && cot.GetSTrack(t)->len<0)
                    {
                        t = levy; //pouzije pravou variaci leveho tracku
                        preladeni = (char)mem[adr+14+8-i*2];	//with the same tuning the lines have
                    }
                    */
                }

                int vyslednytrack = cot.MakeOrFindTrackShiftLR(t, preladeni, lr);

                m_song[line][i] = vyslednytrack;

                if (vyslednytrack > numoftracks) numoftracks = vyslednytrack;	//total number of tracks

                if (vyslednytrack >= 0 && i >= 4) stereomodul = 1;
            }
        }
    }
    //is there a goto in the end?
    if (m_songgo[line - 1] < 0) m_songgo[line + 1] = line;	//no, so it adds an endless loop to the end

    if (!stereomodul) //mono module
        SetTracks(4);

    result.numoftracks = numoftracks;
    result.nonemptyinstruments = nonemptyinstruments;
    result.songlines = line;

    result.optitracks = 0;
    result.optibeats = 0;
    if (optimizeloops)
    {
        TracksAllBuildLoops(result.optitracks, result.optibeats);
    }

    result.clearedtracks = 0;
    result.truncatedtracks = 0;
    result.truncatedbeats = 0;
    if (truncateunusedparts)
    {
        SongClearUnusedTracksAndParts(result.clearedtracks, result.truncatedtracks, result.truncatedbeats);
    }
}
