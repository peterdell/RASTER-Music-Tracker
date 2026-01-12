#pragma once

#include "StdAfx.h"

#include "TrackerDriverVersion.h"

class CRmtAtariBinaries
{

public:
    static bool GetTrackerDriverBinary(TrackerDriverVersion trackerDriverVersion, byte*& binary, WORD& size);
    static bool GetVUPlayerBinary(byte*& binary, WORD& size);
};

