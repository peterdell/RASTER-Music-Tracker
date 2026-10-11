#pragma once

#include "StdAfx.h"

#include "Memory.h"
#include "tracker_obx.h"

// Which of the Patch 16 player's AUDF tables a note is read from, and where
// that table is.
//
// The player chooses in two steps (asm/Patch-16/rmtplayr.a65): its TABLES_MSB
// dispatch table maps the distortion to one of the five 256-byte pages, then
// store_tables_lsb picks a 64-byte slice by the channel's clock - 64 kHz at
// +$00, 1.79 MHz at +$40, 16-bit at +$80 - while check_15khz takes the two
// 15 kHz tables from PAGE_EXTRA_0 instead.
//
// The page half of that is READ FROM THE DRIVER IN MEMORY rather than
// hardcoded here, because keeping a private copy of the layout is exactly how
// this went wrong twice: GetFrequency() still used the RMT 1.28 one-page
// offsets (plans/38_GETFREQUENCY_TABLE_OFFSETS_PLAN.md) and the MIDI input
// path still uses the RMT 1.34 five-page addresses
// (plans/39_MIDI_TABLE_OFFSETS_PLAN.md). Read from the driver, the mapping
// lives once in the 6502 source, where build/check_drivers.sh already pins it
// to the shipped binary, and both this program and the Java port follow a
// page reshuffle by themselves.
class CFrequencyTables {
public:
    enum class Mode {
        Clock64kHz,
        Clock179MHz,
        Clock15kHz,
        Join16Bit,
    };

    /// <summary> The address of the AUDF table the player reads for this distortion and mode </summary>
    /// <param name="memory"> the emulated Atari's memory, with a Patch 16 driver loaded </param>
    /// <param name="distortion"> the instrument's RMT distortion, $00 to $0E </param>
    /// <returns>
    /// The table's address, or 0 when the loaded driver has no such table.
    /// The table holds 64 bytes, except in Join16Bit mode, where it holds 64
    /// little-endian words.
    /// </returns>
    static MemoryAddress GetTableAddress(const byte* memory, int distortion, Mode mode);
};
