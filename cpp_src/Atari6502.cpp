/*
	Atari6502.cpp
	CPU EMULATION INTERFACE + ATARI BINARY FILE FUNCTIONS
	(c) Raster/C.P.U. 2003
	Reworked by VinsCool, 2021-2022
*/

#include "stdafx.h"

#include "Atari6502.h"
#include "AtariIO.h"

#include "RmtAtariBinaries.h"
#include "General.h"
#include "global.h"

typedef void (* C6502_Initialise_PROC)(BYTE*);
typedef int  (* C6502_JSR_PROC)(WORD* , BYTE* , BYTE* , BYTE* , int* );
typedef void (* C6502_About_PROC)(char**, char**, char**);

C6502_Initialise_PROC C6502_Initialise;
C6502_JSR_PROC C6502_JSR;
C6502_About_PROC C6502_About;

void CAtari::DeInit()
{
	g_is6502 = 0;

	if (g_c6502_dll)
	{
		FreeLibrary(g_c6502_dll);
		g_c6502_dll = NULL;
	}
	g_about6502 = "No Atari 6502 CPU emulation.";
}

int CAtari::Init()
{
    if (g_c6502_dll) { DeInit(); }//just in case

	g_c6502_dll=LoadLibrary("sa_c6502.dll");
	if(!g_c6502_dll)
	{
		MessageBox(g_hwnd,"Warning:\n'sa_c6502.dll' library not found.\nTherefore, the Atari sound routines can't be performed.","LoadLibrary error",MB_ICONEXCLAMATION);
		DeInit();
		return 1;
	}

	CString wrn="";

	C6502_Initialise = (C6502_Initialise_PROC) GetProcAddress(g_c6502_dll,"C6502_Initialise");
	if(!C6502_Initialise) wrn +="C6502_Initialise\n";

	C6502_JSR = (C6502_JSR_PROC) GetProcAddress(g_c6502_dll,"C6502_JSR");
	if(!C6502_JSR) wrn +="C6502_JSR\n";

	C6502_About = (C6502_About_PROC) GetProcAddress(g_c6502_dll,"C6502_About");
	if(!C6502_About) wrn +="C6502_About\n";

	if (wrn!="")
	{
		MessageBox(g_hwnd,"Error:\n'sa_c6502.dll' is not compatible.\nTherefore, the Atari sound routines can't be performed.\nIncompatibility with:" +wrn,"C6502 library error",MB_ICONEXCLAMATION);
		DeInit();
		return 1;
	}

	//Text for About dialog
	if (g_c6502_dll)
	{
		char *name, *author, *description;
		C6502_About(&name,&author,&description);
		g_about6502.Format("%s\n%s\n%s",name,author,description);
	}

	C6502_Initialise(g_atarimem);

	g_is6502 = 1;

	return 1;
}


void CAtari::ClearMemory()
{
    memset(g_atarimem, 0, RAM_SIZE);
}

// Load an Atari executable to memory
int CAtari::LoadOBX(int obx, unsigned char* mem, WORD& minadr, WORD& maxadr)
{
	WORD size;
	byte* bin;

	switch (obx)
	{
	case IOTYPE_LZSS_XEX:
        if (!CRmtAtariBinaries::GetVUPlayerBinary(bin, size)) {
            return 0;
        }
		break;

	default:
		return 0;
	}

	return CAtariIO::LoadDataAsBinaryFile(bin, size, mem, minadr, maxadr);
}

// Load RMT routine to $3400, setnoteinstrvol to $3d00, and setvol to $3e00
int CAtari::LoadRMTRoutines()
{
	WORD min, max;
	WORD size;
	byte* bin;
    if (!CRmtAtariBinaries::GetTrackerDriverBinary(g_trackerDriverVersion, bin, size)) {
        return 0;
    }

	return CAtariIO::LoadDataAsBinaryFile(bin, size, g_atarimem, min, max);
}

int CAtari::InitRMTRoutine()
{
    if (!g_is6502) {
        return 0;
    }

	g_Tuning.init_tuning();	//input the A-4 frequency for the tuning and generate all the lookup tables needed for the player routines

	WORD adr=RMT_INIT;
	BYTE a=0, x=0x00, y=0x3f;
	int cycles = (g_ntsc) ? MAXSCREENCYCLES_NTSC : MAXSCREENCYCLES_PAL;
	C6502_JSR(&adr,&a,&x,&y,&cycles);			//adr,A,X,Y
	for(int i=0; i<SONGTRACKS; i++) { g_rmtinstr[i]=-1; }

	return (int)a;
}

void CAtari::PlayRMT()
{
	if (!g_is6502) return;

	WORD adr=RMT_P3; //(without SetPokey) one run of RMT routine but from rmt_p3 (wrap processing)
	BYTE a=0, x=0, y=0;
	int cycles = (g_ntsc) ? MAXSCREENCYCLES_NTSC : MAXSCREENCYCLES_PAL;
	if (g_prove < PROVE_EDIT_AND_JAM_MODES) // this is only good for tests, this trigger prevents the RMT driver running at all, leaving only SetPokey available
		C6502_JSR(&adr,&a,&x,&y,&cycles);			//adr,A,X,Y
	adr=RMT_SETPOKEY;
	a=x=y=0;
	C6502_JSR(&adr,&a,&x,&y,&cycles);			//adr,A,X,Y
}

void CAtari::SetPokey()
{
	if (!g_is6502) return;

	WORD adr = RMT_SETPOKEY;
	BYTE a = 0, x = 0, y = 0;
	int cycles = (g_ntsc) ? MAXSCREENCYCLES_NTSC : MAXSCREENCYCLES_PAL;
	C6502_JSR(&adr, &a, &x, &y, &cycles);
}

void CAtari::Silence()
{
	if (!g_is6502) return;

	//Silence routine
	WORD adr=RMT_SILENCE;
	BYTE a=0, x=0, y=0;
	int cycles = (g_ntsc) ? MAXSCREENCYCLES_NTSC : MAXSCREENCYCLES_PAL;
	C6502_JSR(&adr,&a,&x,&y,&cycles);			//adr,A,X,Y
}

void CAtari::SetTrack_NoteInstrVolume(int t,int n,int i,int v)
{
	if (!g_is6502) return;

	WORD adr=RMT_ATA_SETNOTEINSTR;
	BYTE a=n, x=t, y=i;
	int cycles = (g_ntsc) ? MAXSCREENCYCLES_NTSC : MAXSCREENCYCLES_PAL;
	C6502_JSR(&adr,&a,&x,&y,&cycles);			//adr,A,X,Y
	//
	adr=RMT_ATA_SETVOLUME;
	a=v; x=t; y=0;
	cycles = (g_ntsc) ? MAXSCREENCYCLES_NTSC : MAXSCREENCYCLES_PAL;
	C6502_JSR(&adr,&a,&x,&y,&cycles);			//adr,A,X,Y

	g_rmtinstr[t]=i;
}

void CAtari::SetTrack_Volume(int t,int v)
{
	if (!g_is6502) return;

	WORD adr=RMT_ATA_SETVOLUME;
	BYTE a=v, x=t, y=0;
	int cycles = (g_ntsc) ? MAXSCREENCYCLES_NTSC : MAXSCREENCYCLES_PAL;
	C6502_JSR(&adr,&a,&x,&y,&cycles);			//adr,A,X,Y
}

void CAtari::InstrumentTurnOff(int instr)
{
	if (!g_is6502) return;

	int cycles = (g_ntsc) ? MAXSCREENCYCLES_NTSC : MAXSCREENCYCLES_PAL;
	for(int i=0; i<SONGTRACKS; i++)
	{
		if (g_rmtinstr[i]==instr)
		{
			WORD adr=RMT_ATA_INSTROFF;
			BYTE a=0,x=i,y=0;
			C6502_JSR(&adr,&a,&x,&y,&cycles);			//mutes and turns off this instrument
			g_atarimem[0xd200+i*2+1+(i>=4)*16]=0;		//resets POKEY audctl memory
			g_rmtinstr[i]=-1;
		}
	}
}
