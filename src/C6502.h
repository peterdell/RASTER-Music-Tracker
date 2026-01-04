/*
    Wrapper for "sa_c6502.dll" by Avery Lee (phaeron).
    See Altirra source code https://www.virtualdub.org/altirra.html in the folder "src/AltirraRMT6502".
*/

#pragma once

#include "StdAfx.h"

extern HINSTANCE g_c6502_dll;
extern BOOL volatile g_is6502;
extern CString g_about6502;

class C6502 {

public:

    typedef unsigned short  Address;
    typedef unsigned char Byte;
    typedef Byte Register;
    typedef int CycleCount;

    static int Init();
    static void DeInit();

    // The cycles parameter, is the maximum number of cycles to run. The method call reduces this value by the number of cycles actually run before RTS.
    static void JSR(Address &adr, Register &a, Register &x, Register &y, CycleCount &cycles);
};


