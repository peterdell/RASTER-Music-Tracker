/*
    Wrapper for "sa_c6502.dll"
    See Altirra source code "src/AltirraRMT6502"
*/

#pragma once

class SA_C502 {

public:

    typedef unsigned short  Address;
    typedef unsigned char Byte;
    typedef Byte Register;
    typedef int CycleCount;

    static int Init();
    static void DeInit();

    // static void JSR(Address &adr, Register &a, Register &x, Register &y, CycleCount &cycles);
};


