#include "StdAfx.h"

#include "FrequencyTables.h"

bool CFrequencyTables::HasDispatchTable(const byte* memory) {
    // Checks the whole table, not just the entry about to be read: every page
    // MSB must name one of the five distortion pages, and every AUDC must
    // carry distortion bits only, the volume nibble being the player's to
    // fill in. Of the seven drivers RMT ships, only rmt_driver_v6.obx passes;
    // in the other six these bytes are their own frequency-table data, which
    // lies far outside $B0-$B4. It stays a plausibility check rather than a
    // driver-version test, though - see plan 40 for giving this the version
    // and a second layout to answer with.
    for (int i = 0; i < 8; i++) {
        const byte pageMsb = memory[RMTPLAYR_TABLES_MSB + i * 2];
        const byte audc = memory[RMTPLAYR_DISTORTIONS + i * 2];
        if (pageMsb < (RMTPLAYR_PAGE_DISTORTION_2 >> 8) || pageMsb > (RMTPLAYR_PAGE_DISTORTION_E >> 8)) {
            return false;
        }
        if ((audc & 0x0F) != 0) {
            return false;
        }
    }
    return true;
}

MemoryAddress CFrequencyTables::GetTableAddress(const byte* memory, int distortion, Mode mode) {
    if (distortion < 0x00 || distortion > 0x0E) {
        return 0;
    }

    if (mode == Mode::Clock15kHz) {
        // check_15khz: distortion C and E take the buzzy table, the rest the
        // pure one. These two are not in the dispatch table - the player
        // loads their addresses directly.
        return distortion >= 0x0C ? RMTPLAYR_FRQTABBUZZY_15KHZ : RMTPLAYR_FRQTABPURE_15KHZ;
    }

    if (!HasDispatchTable(memory)) {
        return 0;
    }

    // TABLES_MSB holds eight (page MSB, AUDC) pairs, indexed by distortion/2;
    // only the page MSB is of interest here, the AUDC the player writes to
    // POKEY is the caller's own business.
    MemoryAddress page = (MemoryAddress)(memory[RMTPLAYR_TABLES_MSB + (distortion >> 1) * 2] << 8);
    switch (mode) {
    case Mode::Clock179MHz:
        return page + 0x40;
    case Mode::Join16Bit:
        return page + 0x80;
    default:
        return page;
    }
}

CFrequencyTables::Mode CFrequencyTables::GetModeForChannel(int audctl, int track) {
    const bool clock15 = (audctl & 0x01) != 0;
    const bool join34 = (audctl & 0x08) != 0;
    const bool join12 = (audctl & 0x10) != 0;
    const bool ch3_179 = (audctl & 0x20) != 0;
    const bool ch1_179 = (audctl & 0x40) != 0;

    // A joined pair is addressed through its high byte, the channel above the
    // 1.79 MHz one.
    if ((join12 && ch1_179 && (track == 1 || track == 5)) || (join34 && ch3_179 && (track == 3 || track == 7))) {
        return Mode::Join16Bit;
    }
    if ((ch1_179 && (track == 0 || track == 4)) || (ch3_179 && (track == 2 || track == 6))) {
        return Mode::Clock179MHz;
    }
    return clock15 ? Mode::Clock15kHz : Mode::Clock64kHz;
}

int CFrequencyTables::GetDistortionAudc(const byte* memory, int distortion) {
    // $00 is a valid AUDC, so the entry's own bytes cannot tell a missing
    // dispatch table from distortion 0 - the page MSB beside it can, and
    // GetTableAddress() already checks it.
    if (!GetTableAddress(memory, distortion, Mode::Clock64kHz)) {
        return -1;
    }

    // DISTORTIONS is the second byte of each TABLES_MSB pair.
    return memory[RMTPLAYR_DISTORTIONS + (distortion >> 1) * 2];
}
