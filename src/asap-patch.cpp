#pragma once

#include "StdAfx.h"
#include "asap-patch.h"
#include "RmtAtariBinaries.h"

static TrackerDriverVersion AlternativeRMTPlayer = NONE;

extern uint8_t const* GetAlternativeRMTPlayer(const int channels, const uint8_t* original) {

    if (AlternativeRMTPlayer != NONE) {
        uint8_t* buffer;
        WORD size = 0;
        CRmtAtariBinaries::GetTrackerDriverBinary(PATCH16, buffer, size);
        return buffer;
    }
    return original;
}
