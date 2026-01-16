#pragma once

// RMT tracker driver binaries versions.
// These binaries were designed for being ran within the emulated Atari setup created by Raster.
// Most of these were not official versions of the RMT driver, but patches with few changes.
//
enum class TrackerDriverVersion : int
{
    NONE = 0,
    UNPATCHED = 1,
    UNPATCHED_WITH_TUNING = 2,
    PATCH3 = 3,
    PATCH6 = 4,
    PATCH8 = 5,
    PATCH16 = 6,
    PATCH_PRINCE_OF_PERSIA = 7
};
