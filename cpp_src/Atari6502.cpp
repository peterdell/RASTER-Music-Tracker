/*
	Atari6502.cpp
	CPU EMULATION INTERFACE + ATARI BINARY FILE FUNCTIONS
	(c) Raster/C.P.U. 2003
	Reworked by VinsCool, 2021-2022
*/

#include "stdafx.h"
#include <stdio.h>
#include <stdlib.h>
#include <fstream>	/* needed for Load/SaveBinaryFile */

#include "Atari6502.h"
#include "RmtAtariBinaries.h"
#include "General.h"
#include "global.h"

typedef void (* C6502_Initialise_PROC)(BYTE*);
typedef int  (* C6502_JSR_PROC)(WORD* , BYTE* , BYTE* , BYTE* , int* );
typedef void (* C6502_About_PROC)(char**, char**, char**);

C6502_Initialise_PROC C6502_Initialise;
C6502_JSR_PROC C6502_JSR;
C6502_About_PROC C6502_About;

void Atari6502_DeInit()
{
	g_is6502 = 0;

	if (g_c6502_dll)
	{
		FreeLibrary(g_c6502_dll);
		g_c6502_dll = NULL;
	}
	g_about6502 = "No Atari 6502 CPU emulation.";
}

int Atari6502_Init()
{
	if (g_c6502_dll) Atari6502_DeInit();	//just in case

	g_c6502_dll=LoadLibrary("sa_c6502.dll");
	if(!g_c6502_dll)
	{
		MessageBox(g_hwnd,"Warning:\n'sa_c6502.dll' library not found.\nTherefore, the Atari sound routines can't be performed.","LoadLibrary error",MB_ICONEXCLAMATION);
		Atari6502_DeInit();
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
		Atari6502_DeInit();
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

// Load an Atari executable to memory
int Atari_LoadOBX(int obx, unsigned char* mem, WORD& minadr, WORD& maxadr)
{
	int size;
	unsigned char* bin;

	switch (obx)
	{
	case IOTYPE_LZSS_XEX:
		bin = export_VUPlayer_LZSS; size = sizeof export_VUPlayer_LZSS;
		break;

	default:
		return 0;
	}

	return LoadDataAsBinaryFile(bin, size, mem, minadr, maxadr);
}

// Load RMT routine to $3400, setnoteinstrvol to $3d00, and setvol to $3e00
int Atari_LoadRMTRoutines()
{
	WORD min, max;
	int size;
	unsigned char* bin;

	switch (g_trackerDriverVersion)
	{
	case UNPATCHED:
	case UNPATCHED_WITH_TUNING: 
		bin = tracker_Unpatched; size = sizeof tracker_Unpatched; 
		break;

	case PATCH3_INSTRUMENTARIUM:
		bin = tracker_Patch3_Instrumentarium; size = sizeof tracker_Patch3_Instrumentarium;
		break;

	case PATCH6:
		bin = tracker_Patch6; size = sizeof tracker_Patch6;
		break;

	case PATCH8:
		bin = tracker_Patch8; size = sizeof tracker_Patch8;
		break;

	case PATCH16:
		bin = tracker_Patch16; size = sizeof tracker_Patch16;
		break;

	case PATCH_PRINCE_OF_PERSIA:
		bin = tracker_PatchPoP; size = sizeof tracker_PatchPoP;
		break;

	default:
		return 0;
	}

	return LoadDataAsBinaryFile(bin, size, g_atarimem, min, max);
}

int Atari_InitRMTRoutine()
{
	if (!g_is6502) return 0;

	g_Tuning.init_tuning();	//input the A-4 frequency for the tuning and generate all the lookup tables needed for the player routines

	WORD adr=RMT_INIT;
	BYTE a=0, x=0x00, y=0x3f;
	int cycles = (g_ntsc) ? MAXSCREENCYCLES_NTSC : MAXSCREENCYCLES_PAL;
	C6502_JSR(&adr,&a,&x,&y,&cycles);			//adr,A,X,Y
	for(int i=0; i<SONGTRACKS; i++) { g_rmtinstr[i]=-1; }

	return (int)a;
}

void Atari_PlayRMT()
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

void Atari_SetPokey()
{
	if (!g_is6502) return;

	WORD adr = RMT_SETPOKEY;
	BYTE a = 0, x = 0, y = 0;
	int cycles = (g_ntsc) ? MAXSCREENCYCLES_NTSC : MAXSCREENCYCLES_PAL;
	C6502_JSR(&adr, &a, &x, &y, &cycles);
}

void Atari_Silence()
{
	if (!g_is6502) return;

	//Silence routine
	WORD adr=RMT_SILENCE;
	BYTE a=0, x=0, y=0;
	int cycles = (g_ntsc) ? MAXSCREENCYCLES_NTSC : MAXSCREENCYCLES_PAL;
	C6502_JSR(&adr,&a,&x,&y,&cycles);			//adr,A,X,Y
}

void Atari_SetTrack_NoteInstrVolume(int t,int n,int i,int v)
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

void Atari_SetTrack_Volume(int t,int v)
{
	if (!g_is6502) return;

	WORD adr=RMT_ATA_SETVOLUME;
	BYTE a=v, x=t, y=0;
	int cycles = (g_ntsc) ? MAXSCREENCYCLES_NTSC : MAXSCREENCYCLES_PAL;
	C6502_JSR(&adr,&a,&x,&y,&cycles);			//adr,A,X,Y
}

void Atari_InstrumentTurnOff(int instr)
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

void Memory_Clear()
{
	memset(g_atarimem,0,65536);
}

bool LoadWord(std::ifstream& in, WORD& w)
{
	unsigned char a1, a2;
	char db, hb;
	if (in.eof()) return false;
	in.get(db);
	a1 = (unsigned char)db;
	if (in.eof()) return false;
	in.get(hb);
	a2 = (unsigned char)hb;
	w = a1 + (a2 << 8);
	return true;
}

/// <summary>
/// Load an Atari binary block. Max [4-6] byte header + the indicated number of bytes
/// HEADER, FROM, TO
/// HEADER is optional.
/// </summary>
/// <param name="in">Input stream</param>
/// <param name="memory">Buffer that will act as Atari memory</param>
/// <param name="fromAddr">Returns the address where the binary block was loaded (FROM)</param>
/// <param name="toAddr">Returns the end address, (first byte after the loaded block)</param>
/// <returns>Return number of bytes read. (0 if there was some error).</returns>
int LoadBinaryBlock(std::ifstream& in, unsigned char* memory, WORD& fromAddr, WORD& toAddr)
{
	if (!LoadWord(in, fromAddr)) return 0;
	if (fromAddr == 0xffff)
	{
		// Skip the binary block header (0xFFFF)
		if (!LoadWord(in, fromAddr)) return 0;
	}
	if (!LoadWord(in, toAddr)) return 0;

	// Sanity check that the end is not before the start.
	if (toAddr < fromAddr) return 0;

	// Load the indicated number of bytes into the specified memory area
	in.read((char*)memory + fromAddr, toAddr - fromAddr + 1);
	return toAddr-fromAddr+1;
}

int LoadBinaryFile(const char* fname, unsigned char* memory, WORD& minadr, WORD& maxadr)
{
	int fsize,blen;
	
	WORD bfrom,bto;

	std::ifstream fin(fname, std::ios::binary | std::ios::_Nocreate);
	if (!fin) return 0;
	fsize=0;
	minadr = 0xffff; maxadr=0; //the opposite limits of the minimum and maximum address
	while(!fin.eof())
	{
		blen = LoadBinaryBlock(fin,memory,bfrom,bto);
		if (blen<=0) break;
		if (bfrom<minadr) minadr=bfrom;
		if (bto>maxadr) maxadr=bto;
		fsize+=blen;
	}
	fin.close();
	return fsize;
}

int LoadDataAsBinaryFile(unsigned char *data, WORD size, unsigned char *memory,WORD& minadr,WORD& maxadr)
{
	if (!data) return 0;

	int blen;
	int akp=0;
	WORD bfrom,bto;

	minadr = 0xffff; maxadr=0; //the opposite limits of the minimum and maximum address
	while(akp<size)
	{
		bfrom = data[akp]|(data[akp+1]<<8);
		akp+=2;
		if (bfrom==0xffff) continue;
		bto = data[akp]|(data[akp+1]<<8);
		akp+=2;
		blen = bto-bfrom+1;
		if (blen<=0) break;
		memcpy(memory+bfrom,data+akp,blen);
		akp+=blen;
		if (bfrom<minadr) minadr=bfrom;
		if (bto>maxadr) maxadr=bto;
	}
	return akp;
}

/// <summary>
/// Save binary data to an output stream.
/// This is done in Atari file format.
/// 4-6 byte header with FFFF as the binary block indicator
/// followed by the START and the END addresses of the memory area.
/// </summary>
/// <param name="out">stream where data is written to</param>
/// <param name="memory">64K of memory</param>
/// <param name="fromAddr">Start address</param>
/// <param name="toAddr">End address (last byte of the data)</param>
/// <param name="withBinaryBlockHeader">True then the FROM,TO header will start with FFFF. Only required on the first block</param>
/// <returns>Total number of bytes output</returns>
int SaveBinaryBlock(std::ofstream& out, unsigned char* memory, WORD fromAddr, WORD toAddr, BOOL withBinaryBlockHeader)
{
	//from "fromadr" to "toadr" inclusive
	if (fromAddr > toAddr) return 0;
	if (withBinaryBlockHeader)
	{
		out.put((char)0xff);
		out.put((char)0xff);
	}
	out.put((unsigned char)(fromAddr & 0xff));
	out.put((unsigned char)(fromAddr >> 8));
	out.put((unsigned char)(toAddr & 0xff));
	out.put((unsigned char)(toAddr >> 8));
	out.write((char*)memory + fromAddr, toAddr - fromAddr + 1);
	return toAddr - fromAddr + 1;
}
