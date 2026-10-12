#include "StdAfx.h"

#include "FrequencyTables.h"

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

    // TABLES_MSB holds eight (page MSB, AUDC) pairs, indexed by distortion/2;
    // only the page MSB is of interest here, the AUDC the player writes to
    // POKEY is the caller's own business.
    const byte pageMsb = memory[RMTPLAYR_TABLES_MSB + (distortion >> 1) * 2];

    // A sanity check on the loaded driver, not a driver-version test: the
    // five distortion pages are the only ones the dispatch table can name, so
    // anything else means these bytes are not that table (no Patch 16 driver
    // loaded - the v1-family drivers keep their tables inside the player and
    // never read this region at all).
    if (pageMsb < (RMTPLAYR_PAGE_DISTORTION_2 >> 8) || pageMsb > (RMTPLAYR_PAGE_DISTORTION_E >> 8)) {
        return 0;
    }

    MemoryAddress page = (MemoryAddress)(pageMsb << 8);
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
