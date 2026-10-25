#pragma once

#include "StdAfx.h"

#include "Memory.h"
#include "TrackerDriverVersion.h"
#include "tracker_obx.h"
#include "legacy_obx.h" // The v1 family's generated C header file

// Which of the loaded player's AUDF tables a note is read from, and where that
// table is.
//
// There are two layouts, because there are two generations of driver, and the
// version decides which one to read (plans/40_DRIVER_TABLE_LAYOUTS_PLAN.md):
//
// PATCH16 (asm/Patch-16/rmtplayr.a65) chooses in two steps: its frqtab_hi_tab (named TABLES_MSB by Patch16 before plans/53 realigned the labels, tabbeganddistor until 2026-10-25)
// dispatch table maps the distortion to one of the five 256-byte pages at
// $B000, then store_tables_lsb picks a 64-byte slice by the channel's clock -
// 64 kHz at +$00, 1.79 MHz at +$40, 16-bit at +$80 - while check_15khz takes
// the two 15 kHz tables from PAGE_EXTRA_0 instead.
//
// The v1 family - every other driver RMT ships (asm/Legacy/rmt.a65) - has one
// page of 64-byte tables below the player instead, and its frqtab_lo_tab
// (tabbeganddistor until 2026-10-25) maps the distortion to a byte offset into that page. It has no clock
// variants at all: a 1.79 MHz or 15 kHz channel reads the very same table,
// which is why those two clocks play out of tune on those drivers rather than
// playing nothing.
//
// Both layouts are READ FROM THE DRIVER IN MEMORY rather than hardcoded here,
// because keeping a private copy is exactly how this went wrong twice:
// GetFrequency() still used the RMT 1.28 one-page offsets
// (plans/38_GETFREQUENCY_TABLE_OFFSETS_PLAN.md) and the MIDI input path still
// used the RMT 1.34 five-page addresses
// (plans/39_MIDI_TABLE_OFFSETS_PLAN.md). Read from the driver, the mapping
// lives once in the 6502 source, where build/check_drivers.sh already pins it
// to the shipped binary, and both this program and the Java port follow a
// reshuffle by themselves. For the v1 family that is not a convenience but a
// necessity: only v1's source is in this repository, and the five patched
// binaries each changed the table's contents - PATCH6 plays distortion $06
// with AUDC $A0 where v1 plays it with $C0, and PATCH8 and
// PATCH_PRINCE_OF_PERSIA do not even keep their tables on $40 boundaries.
class CFrequencyTables {
public:
    enum class Mode {
        Clock64kHz,
        Clock179MHz,
        Clock15kHz,
        Join16Bit,
    };

    // The v1-family layout, from asm/Legacy/rmt.a65. Both addresses are fixed
    // by an `org` relative to PLAYER ($3400), so they hold for every driver
    // built from that source, whatever its feature flags.
    //
    // frqtab_lo_tab/distortion_tab are eight (offset, AUDC) pairs indexed by
    // distortion / 2,
    // the offset being a byte offset into the frqtab page: the player loads
    // `>frqtab` as the pointer's high byte and the offset as its low byte, so
    // the table's address is simply LEGACY_FRQTAB + offset.
    //
    // Both come from legacy_obx.h, the header MADS generates from
    // asm/Legacy/rmt_ata.a65 (prefix RMT_ATA_ after its file name), so they
    // follow the 6502 source the way the PATCH16 addresses above do.
    // build/check_drivers.sh pins both headers to today's sources.
    static constexpr MemoryAddress LEGACY_FRQTAB_LO_TAB = RMT_ATA_TABBEGANDDISTOR;
    static constexpr MemoryAddress LEGACY_DISTORTION_TAB = RMT_ATA_TABBEGANDDISTOR + 1;
    static constexpr MemoryAddress LEGACY_FRQTAB = RMT_ATA_FRQTAB;

    /// <summary> Whether the driver in `memory` really carries the table this version's layout reads </summary>
    /// <remarks>
    /// A plausibility check on the bytes, not a test of the version: it is the
    /// version that selects the layout, and this only says whether the bytes
    /// where that layout expects its dispatch table could be one. It is what
    /// separates a loaded driver from an empty Atari, and - measured against
    /// all seven drivers RMT ships - a driver of one family from the other's
    /// layout.
    /// </remarks>
    static bool HasDispatchTable(const byte* memory, TrackerDriverVersion version);

    /// <summary> The address of the AUDF table the player reads for this distortion and mode </summary>
    /// <param name="memory"> the emulated Atari's memory, with a driver loaded </param>
    /// <param name="version"> which driver that is, and so which layout to read </param>
    /// <param name="distortion"> the instrument's RMT distortion, $00 to $0E </param>
    /// <returns>
    /// The table's address, or 0 when the loaded driver has no such table.
    /// The table holds 64 bytes, except in Join16Bit mode on PATCH16, where it
    /// holds 64 little-endian words. The v1 family answers 0 for Join16Bit:
    /// its 16-bit bass reads two separate tables (frqtabbasslo/frqtabbasshi)
    /// a page apart, which no single address can name, and which only the two
    /// drivers built from the checked-in source can be shown to still hold -
    /// the patched binaries have no source here to identify them against.
    /// </returns>
    static MemoryAddress GetTableAddress(const byte* memory, TrackerDriverVersion version, int distortion, Mode mode);

    /// <summary> Which clock or width a channel plays at, from the AUDCTL the driver is about to write </summary>
    /// <param name="audctl"> the AUDCTL value, as in the driver's v_audctl </param>
    /// <param name="track"> the POKEY channel, 0-3 for the first chip and 4-7 for the second </param>
    /// <remarks>
    /// Only channels 1 and 3 of each chip can run at 1.79 MHz (tracks 0/4 and
    /// 2/6), and a 16-bit pair is addressed through its high byte, which is
    /// the channel above (tracks 1/5 and 3/7) - so the same AUDCTL means
    /// different things to different channels. 1.79 MHz and 16-bit both
    /// override the 15 kHz clock, as they do in the player. What a mode then
    /// means for the tables is the layout's business, not this method's.
    /// </remarks>
    static Mode GetModeForChannel(int audctl, int track);

    /// <summary> The AUDC distortion bits the player writes to POKEY for this distortion </summary>
    /// <returns>
    /// The AUDC value, or -1 when the loaded driver has no dispatch table.
    /// Read from that table rather than computed, because it is not simply
    /// the distortion shifted up, and not even the same from driver to
    /// driver: PATCH16 plays distortion $06 with AUDC $A0 and $0E with $C0,
    /// while v1 plays $06 with $C0 and PATCH6 with $A0.
    /// </returns>
    static int GetDistortionAudc(const byte* memory, TrackerDriverVersion version, int distortion);
};
