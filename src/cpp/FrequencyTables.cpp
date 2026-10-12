#include "StdAfx.h"

#include "FrequencyTables.h"

namespace {

// Whether this version's player is one of the v1 family, i.e. everything but
// the Patch 16 driver. They differ in plenty of other ways, but all of them
// read their note tables through tabbeganddistor.
bool IsLegacyLayout(TrackerDriverVersion version) {
    switch (version) {
    case TrackerDriverVersion::UNPATCHED:
    case TrackerDriverVersion::UNPATCHED_WITH_TUNING:
    case TrackerDriverVersion::PATCH3:
    case TrackerDriverVersion::PATCH6:
    case TrackerDriverVersion::PATCH8:
    case TrackerDriverVersion::PATCH_PRINCE_OF_PERSIA:
        return true;
    default:
        return false;
    }
}

} // namespace

bool CFrequencyTables::HasDispatchTable(const byte* memory, TrackerDriverVersion version) {
    if (version == TrackerDriverVersion::PATCH16) {
        // Checks the whole table, not just the entry about to be read: every
        // page MSB must name one of the five distortion pages, and every AUDC
        // must carry distortion bits only, the volume nibble being the
        // player's to fill in. Of the seven drivers RMT ships, only
        // rmt_driver_v6.obx passes; in the other six these bytes are their own
        // frequency-table data, which lies far outside $B0-$B4.
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

    if (IsLegacyLayout(version)) {
        // The offsets cannot be checked the same way: PATCH8 and
        // PATCH_PRINCE_OF_PERSIA use offsets that are not multiples of $40,
        // so any byte is a possible one. What can be checked is the AUDC
        // half, plus that the table is there at all - the Patch 16 driver
        // loads nothing into this region, so its bytes stay zero, and so do
        // an empty Atari's. A real table cannot be all zeros: that would play
        // every distortion as POKEY distortion 0 out of one table.
        bool anySet = false;
        for (int i = 0; i < 8; i++) {
            const byte offset = memory[LEGACY_TABBEGANDDISTOR + i * 2];
            const byte audc = memory[LEGACY_TABBEGANDDISTOR + 1 + i * 2];
            if ((audc & 0x0F) != 0) {
                return false;
            }
            if (offset != 0 || audc != 0) {
                anySet = true;
            }
        }
        return anySet;
    }

    return false; // TrackerDriverVersion::NONE, and anything added without a layout
}

MemoryAddress CFrequencyTables::GetTableAddress(const byte* memory, TrackerDriverVersion version, int distortion, Mode mode) {
    if (distortion < 0x00 || distortion > 0x0E) {
        return 0;
    }

    if (version == TrackerDriverVersion::PATCH16) {
        if (mode == Mode::Clock15kHz) {
            // check_15khz: distortion C and E take the buzzy table, the rest
            // the pure one. These two are not in the dispatch table - the
            // player loads their addresses directly.
            return distortion >= 0x0C ? RMTPLAYR_FRQTABBUZZY_15KHZ : RMTPLAYR_FRQTABPURE_15KHZ;
        }

        if (!HasDispatchTable(memory, version)) {
            return 0;
        }

        // TABLES_MSB holds eight (page MSB, AUDC) pairs, indexed by
        // distortion/2; only the page MSB is of interest here, the AUDC the
        // player writes to POKEY is the caller's own business.
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

    if (IsLegacyLayout(version)) {
        if (!HasDispatchTable(memory, version)) {
            return 0;
        }

        if (mode == Mode::Join16Bit) {
            return 0; // two tables a page apart - see GetTableAddress()'s docs
        }

        // Every other mode reads the same table, because that player has no
        // clock variants: it builds its pointer from `>frqtab` and the offset
        // in tabbeganddistor and indexes it by the note, whatever clock the
        // channel is running at.
        return (MemoryAddress)(LEGACY_FRQTAB + memory[LEGACY_TABBEGANDDISTOR + (distortion >> 1) * 2]);
    }

    return 0;
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

int CFrequencyTables::GetDistortionAudc(const byte* memory, TrackerDriverVersion version, int distortion) {
    // $00 is a valid AUDC, so the entry's own bytes cannot tell a missing
    // dispatch table from distortion 0 - the byte beside it can, and
    // GetTableAddress() already checks it. 64 kHz is the mode every layout
    // answers, so it is the one to ask with.
    if (!GetTableAddress(memory, version, distortion, Mode::Clock64kHz)) {
        return -1;
    }

    // Both layouts keep the AUDC in the second byte of each pair.
    const MemoryAddress table = version == TrackerDriverVersion::PATCH16 ? RMTPLAYR_DISTORTIONS : (MemoryAddress)(LEGACY_TABBEGANDDISTOR + 1);
    return memory[table + (distortion >> 1) * 2];
}
