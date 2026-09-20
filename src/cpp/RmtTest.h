#pragma once

#include "Rmt.h"

class CRmtTest
{
public:

    CRmtTest();
    void RunFor(const CRmtApp& app, const CString fileName);

private:
    void SaveBinaries();
    void TestASAP(const CRmtApp& app, const CString fileName);
};

