#include "C6502.h"

#include "stdafx.h"

//#include "Global.h" // TODO Get rid of this

// DDL procedure pointers.
typedef void (*SA_C6502_Initialise_PROC)(BYTE*);
typedef int  (*SA_C6502_JSR_PROC)(WORD*, BYTE*, BYTE*, BYTE*, int*);
typedef void (*SA_C6502_About_PROC)(char**, char**, char**);

SA_C6502_Initialise_PROC SA_C6502_Initialise;
SA_C6502_JSR_PROC SA_C6502_JSR;
SA_C6502_About_PROC SA_C6502_About;

// extern HINSTANCE g_c6502_dll = NULL;
//extern BOOL volatile g_is6502 = FALSE;

extern CString g_about6502;
extern byte* g_atarimem;

extern HWND g_hwnd;
/*
void C6502::DeInit()
{
    g_is6502 = 0;

    if (g_c6502_dll)
    {
        FreeLibrary(g_c6502_dll);
        g_c6502_dll = NULL;
    }
    g_about6502 = "No Atari 6502 CPU emulation.";
}

int C6502::Init()
{
    if (g_c6502_dll) { DeInit(); }//just in case

    g_c6502_dll = LoadLibrary("sa_c6502.dll");
    if (!g_c6502_dll)
    {
        MessageBox(g_hwnd, "Warning:\n'sa_c6502.dll' library not found.\nTherefore, the Atari sound routines can't be performed.", "LoadLibrary error", MB_ICONEXCLAMATION);
        DeInit();
        return 1;
    }

    CString wrn = "";

    C6502_Initialise = (C6502_Initialise_PROC)GetProcAddress(g_c6502_dll, "C6502_Initialise");
    if (!C6502_Initialise) wrn += "C6502_Initialise\n";

    C6502_JSR = (C6502_JSR_PROC)GetProcAddress(g_c6502_dll, "C6502_JSR");
    if (!C6502_JSR) wrn += "C6502_JSR\n";

    C6502_About = (C6502_About_PROC)GetProcAddress(g_c6502_dll, "C6502_About");
    if (!C6502_About) wrn += "C6502_About\n";

    if (wrn != "")
    {
        MessageBox(g_hwnd, "Error:\n'sa_c6502.dll' is not compatible.\nTherefore, the Atari sound routines can't be performed.\nIncompatibility with:" + wrn, "C6502 library error", MB_ICONEXCLAMATION);
        DeInit();
        return 1;
    }

    //Text for About dialog
    if (g_c6502_dll)
    {
        char* name, * author, * description;
        C6502_About(&name, &author, &description);
        g_about6502.Format("%s\n%s\n%s", name, author, description);
    }

    C6502_Initialise(g_atarimem);

    g_is6502 = 1;

    return 1;
}
*/

/*
void C6502::JSR(C6502::Address& adr, C6502::Register& a, C6502::Register& x, C6502::Register& y, C6502::CycleCount& cycles) {
    C6502_JSR(&adr, &a, &x, &y, &cycles);
}
*/



