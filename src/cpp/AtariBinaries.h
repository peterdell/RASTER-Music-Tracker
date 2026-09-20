#pragma once

#include "StdAfx.h"

#include "TrackerDriverVersion.h"

class CRmtAtariBinaries
{

public:
    // TODO Return CByteArray*
    // Have instance and free at end of application
    static bool GetTrackerDriverBinary(TrackerDriverVersion trackerDriverVersion, byte*& binary, WORD& size);
    static bool GetVUPlayerBinary(byte*& binary, WORD& size);
};

