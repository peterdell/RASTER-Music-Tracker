/*
    Atari6502.h
    CPU EMULATION INTERFACE + ATARI BINARY FILE FUNCTIONS
    (c) Raster/C.P.U. 2003
*/

#ifndef __ATARI6502__
#define __ATARI6502__

#include "tracker_obx.h"				// The ASM generated C header file

//bass16bit low byte, bass 0C, bass 0E, clean tones 0A and 0,2,4,8, bass16bit hi byte, this might require different addresses? What is this even used for anyway?
#define RMT_FRQTABLES	RMTPLAYR_PAGE_DISTORTION_2				

#define RMT_INIT		RMTPLAYR_RASTERMUSICTRACKER
#define RMT_PLAY		RMTPLAYR_RASTERMUSICTRACKER+3
#define RMT_P3			RMTPLAYR_RASTERMUSICTRACKER+6
#define RMT_SILENCE		RMTPLAYR_RASTERMUSICTRACKER+9
#define RMT_SETPOKEY	RMTPLAYR_RASTERMUSICTRACKER+12

#define RMT_ATA_SETNOTEINSTR	RMTPLAYR_GETINSTRUMENTY2
#define RMT_ATA_SETVOLUME		RMTPLAYR_SETINSTRUMENTVOLUME
#define RMT_ATA_INSTROFF		RMTPLAYR_STOPINSTRUMENT

//immediately after RMT_ATA_INSTROFF, there is some bytes left unused, these will be used as plaintext data to display the RMT driver version used
#define RMT_ATA_DRIVERVERSION	RMTPLAYR_DRIVERVERSION		

//maximum clock count for the entire screen in PAL (default) and NTSC region
#define MAXSCREENCYCLES_NTSC	114*262
#define MAXSCREENCYCLES_PAL 	114*312

class CAtari {


public:

    static int Init();
    static void DeInit();

    static void ClearMemory();
    static int LoadOBX(int obx, unsigned char* mem, WORD& minadr, WORD& maxadr);

    static int LoadRMTRoutines();
    static int InitRMTRoutine();
    static void PlayRMT();
    static void SetPokey();
    static void Silence();
    static void SetTrack_NoteInstrVolume(int t, int n, int i, int v);
    static void SetTrack_Volume(int t, int v);
    static void InstrumentTurnOff(int instr);


};

#endif
