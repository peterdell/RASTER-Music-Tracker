#pragma once

#include "StdAfx.h"
#include "asap-patch.h"
#include "RmtAtariBinaries.h";

extern uint8_t const* GetFuResourceRMT4obx() {
    uint8_t* buffer;
    WORD size = 0;
    CRmtAtariBinaries::GetTrackerDriverBinary(PATCH16, buffer, size);
    return buffer;
}

extern uint8_t const* GetFuResourceRMT8obx() {
    return nullptr;
}