#pragma once

// RMT tracker driver binaries versions.
// These binaries were designed for being ran within the emulated Atari setup created by Raster.
// Most of these were not official versions of the RMT driver, but patches with few changes.
//
enum class TrackerDriverVersion : int {
    NONE = 0,
    UNPATCHED = 1,
    UNPATCHED_WITH_TUNING = 2,
    PATCH3 = 3,
    PATCH6 = 4,
    PATCH8 = 5,
    PATCH16 = 6,
    PATCH_PRINCE_OF_PERSIA = 7
};

/// <summary> Whether the tuning settings reach this driver's frequency tables </summary>
/// <remarks>
/// Only Patch 16 keeps its tables in the $B000 page, which is the only page
/// the tuning system writes. Every other driver carries its four tables
/// inside the player, assembled into the binary, and plays exactly those
/// whatever the tuning dialog says - see doc/rmt_tracker_drivers.md, "The
/// Frequency Tables of the Other Drivers". So with those versions the tuning
/// is not merely approximate, it has no effect at all, and the dialog says so
/// by disabling it.
/// </remarks>
constexpr bool IsTuningSupported(const TrackerDriverVersion trackerDriverVersion)
{
    return trackerDriverVersion == TrackerDriverVersion::PATCH16;
}
