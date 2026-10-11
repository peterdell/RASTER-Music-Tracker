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
